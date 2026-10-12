package dev.hclassist.ide;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import dev.hclassist.core.*;
import java.io.*;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.PROJECT)
public final class ProviderSupport {
    private static final Logger LOG = Logger.getInstance(ProviderSupport.class);
    private record Cache(long stamp, ProviderSchema schema) {}
    private final Map<String, Cache> snapshots = new ConcurrentHashMap<>();
    private final Map<String, SoftReference<ProviderSchema>> metadata = new ConcurrentHashMap<>();
    static ProviderSupport get(PsiFile file) { return file.getProject().getService(ProviderSupport.class); }

    ProviderSchema schema(PsiFile file, HclDocument doc, String type) {
        ProviderSchema local = snapshot(file);
        String prefix = type.contains("_") ? type.substring(0, type.indexOf('_')) : type;
        String source = source(doc, prefix);
        if (!source.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return local;
        var ref = metadata.get(source);
        ProviderSchema bundled = ref == null ? null : ref.get();
        if (bundled == null) {
            bundled = bundled(source);
            metadata.put(source, new SoftReference<>(bundled));
        }
        return bundled.overlay(local);
    }
    private ProviderSchema snapshot(PsiFile file) {
        VirtualFile directory = file.getVirtualFile() == null ? null : file.getVirtualFile().getParent();
        String base = file.getProject().getBasePath();
        while (directory != null) {
            VirtualFile snapshot = directory.findChild(".hcl-assist.providers.json");
            if (snapshot != null && snapshot.getLength() <= 100_000_000) {
                var document = FileDocumentManager.getInstance().getCachedDocument(snapshot);
                long stamp = document == null ? snapshot.getModificationStamp() : document.getModificationStamp();
                Cache cached = snapshots.get(snapshot.getPath());
                if (cached != null && cached.stamp == stamp) return cached.schema;
                ProviderSchema schema = ProviderSchema.empty();
                try (Reader in = document == null ? new InputStreamReader(snapshot.getInputStream(), StandardCharsets.UTF_8) : new StringReader(document.getText())) {
                    schema = ProviderSchema.read(in);
                } catch (IOException | RuntimeException e) { LOG.warn("Cannot read provider schema " + snapshot.getPath(), e); }
                snapshots.put(snapshot.getPath(), new Cache(stamp, schema));
                return schema;
            }
            if (directory.getPath().equals(base)) break;
            directory = directory.getParent();
        }
        return ProviderSchema.empty();
    }
    private static ProviderSchema bundled(String source) {
        var plugin = PluginManagerCore.getPlugin(PluginId.getId("org.intellij.plugins.hcl"));
        if (plugin == null || plugin.getPluginClassLoader() == null) return ProviderSchema.empty();
        ClassLoader loader = plugin.getPluginClassLoader();
        for (String name : List.of("terraform/model/providers/" + source + ".json", "terraform/model/providers/" + source.substring(source.indexOf('/') + 1) + ".json")) {
            URL url = loader.getResource(name);
            if (url == null) continue;
            try (Reader in = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) { return ProviderSchema.read(in); }
            catch (IOException | RuntimeException e) { LOG.debug("Unsupported bundled provider metadata: " + name, e); }
        }
        return ProviderSchema.empty();
    }
    static HclDocument.Body declaration(HclDocument.Body body) {
        while (body.parent != null && body.parent.parent != null) body = body.parent;
        return body;
    }
    ProviderSchema.Resource resource(PsiFile file, HclDocument doc, HclDocument.Body body) {
        HclDocument.Body root = declaration(body);
        if (!Set.of("resource", "data", "provider").contains(root.name) || root.labels.isEmpty()) return null;
        String type = root.labels.get(0);
        return schema(file, doc, type).resource(root.name, type);
    }
    static String source(HclDocument doc, String prefix) {
        for (HclDocument.Body terraform : doc.root.children) if (terraform.name.equals("terraform")) {
            for (HclDocument.Body required : terraform.children) if (required.name.equals("required_providers")) {
                for (HclDocument.Body provider : required.children) if (provider.name.equals(prefix)) {
                    String source = doc.scalar(provider, "source");
                    String[] parts = source.split("/");
                    if (parts.length >= 2) return parts[parts.length - 2] + "/" + parts[parts.length - 1];
                }
            }
        }
        return "hashicorp/" + prefix;
    }
}
