package dev.hclassist.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import dev.hclassist.core.*;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

final class ProjectSupport {
    private static final Logger LOG = Logger.getInstance(ProjectSupport.class);
    static final Schema BUNDLED = Schema.bundled();
    record State(Dialect dialect, Schema schema, boolean autoRequired) {}
    static State state(PsiFile file, String text) {
        Dialect dialect = Dialect.detect(file.getName(), text);
        if (dialect == Dialect.GENERIC && file.getContainingDirectory() != null) {
            Set<Dialect> nearby = new HashSet<>();
            for (PsiFile sibling : file.getContainingDirectory().getFiles()) {
                ProgressManager.checkCanceled();
                if (sibling.equals(file) || !Dialect.supported(sibling.getName()) ||
                        sibling.getVirtualFile() != null && sibling.getVirtualFile().getLength() > 512_000) continue;
                Dialect found = Dialect.detect(sibling.getName(), sibling.getText());
                if (found != Dialect.GENERIC) nearby.add(found);
            }
            if (nearby.size() == 1) dialect = nearby.iterator().next();
        }
        Schema schema = BUNDLED;
        boolean autoRequired = true;
        String basePath = file.getProject().getBasePath();
        VirtualFile root = basePath == null ? null : com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath);
        if (root != null) {
            VirtualFile config = root.findChild(".hcl-assist.properties");
            if (config != null) {
                try {
                    Properties props = new Properties(); props.load(new StringReader(content(config)));
                    autoRequired = Boolean.parseBoolean(props.getProperty("requiredFields.autoInsert", "true"));
                    String relative = file.getVirtualFile() == null ? file.getName() :
                            com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(file.getVirtualFile(), root, '/');
                    String forced = props.getProperty("dialect");
                    // Specific filename patterns override the default. Sorted order makes overlaps deterministic.
                    for (String key : new TreeSet<>(props.stringPropertyNames())) {
                        if (key.startsWith("files.") && relative != null &&
                                FileSystems.getDefault().getPathMatcher("glob:" + key.substring(6)).matches(Path.of(relative))) forced = props.getProperty(key);
                    }
                    if (forced != null) dialect = Dialect.valueOf(forced.strip().toUpperCase(Locale.ROOT));
                } catch (IOException | IllegalArgumentException e) { LOG.warn("Invalid .hcl-assist.properties", e); }
            }
            VirtualFile custom = root.findChild(".hcl-assist.tsv");
            if (custom != null) {
                try { schema = schema.overlay(content(custom)); }
                catch (IllegalArgumentException e) { LOG.warn("Invalid .hcl-assist.tsv", e); }
            }
        }
        return new State(dialect, schema, autoRequired);
    }
    static PackerRequiredFields.Rules packerRequirements(PsiFile file) {
        var rules = PackerRequiredFields.defaults();
        String base = file.getProject().getBasePath();
        VirtualFile root = base == null ? null : com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(base);
        VirtualFile custom = null;
        VirtualFile directory = file.getVirtualFile() == null ? null : file.getVirtualFile().getParent();
        while (directory != null) {
            custom = directory.findChild(".hcl-assist.packer.json");
            if (custom != null || directory.equals(root)) break;
            directory = directory.getParent();
        }
        if (custom == null && root != null) custom = root.findChild(".hcl-assist.packer.json");
        if (custom != null && custom.getLength() <= 1_000_000) {
            try { return rules.overlay(new StringReader(content(custom))); }
            catch (RuntimeException e) { LOG.warn("Invalid .hcl-assist.packer.json", e); }
        }
        return rules;
    }
    record Declaration(PsiFile file, HclDocument.Symbol symbol) {}
    static List<Declaration> declarations(PsiFile file, HclDocument current, Dialect dialect) {
        List<Declaration> result = new ArrayList<>();
        for (HclDocument.Symbol symbol : current.symbols()) result.add(new Declaration(file, symbol));
        if (file.getContainingDirectory() == null) return result;
        for (PsiFile sibling : file.getContainingDirectory().getFiles()) {
            ProgressManager.checkCanceled();
            if (sibling.equals(file) || !Dialect.supported(sibling.getName()) ||
                    sibling.getVirtualFile() != null && sibling.getVirtualFile().getLength() > 512_000) continue;
            String text = sibling.getText();
            Dialect other = state(sibling, text).dialect;
            // Generic variables/locals files belong to the surrounding directory's dialect.
            if (other != dialect && other != Dialect.GENERIC) continue;
            HclDocument doc = new HclDocument(text);
            for (HclDocument.Symbol symbol : doc.symbols()) result.add(new Declaration(sibling, symbol));
        }
        return result;
    }
    private static String content(VirtualFile file) {
        var document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) return document.getText();
        try { return new String(file.contentsToByteArray(), file.getCharset()); }
        catch (IOException e) { LOG.warn("Could not read " + file.getName(), e); return ""; }
    }
}
