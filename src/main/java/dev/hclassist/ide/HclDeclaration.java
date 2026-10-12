package dev.hclassist.ide;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import dev.hclassist.core.*;

public final class HclDeclaration implements GotoDeclarationHandler {
    @Override public PsiElement[] getGotoDeclarationTargets(PsiElement source, int offset, Editor editor) {
        if (source == null || source.getContainingFile() == null) return null;
        var file = source.getContainingFile();
        if (!Dialect.supported(file.getName())) return null;
        String text = editor.getDocument().getText();
        offset = Math.min(offset, text.length());
        int start = offset, end = offset;
        while (start > 0 && referenceChar(text.charAt(start - 1))) start--;
        while (end < text.length() && referenceChar(text.charAt(end))) end++;
        String reference = text.substring(start, end);
        var doc = new HclDocument(text);
        var state = ProjectSupport.state(file, text);
        final String name = reference;
        var targets = ProjectSupport.declarations(file, doc, state.dialect()).stream()
                .filter(d -> name.equals(d.symbol().name()) || name.startsWith(d.symbol().name() + "."))
                .map(d -> d.file().findElementAt(d.symbol().offset())).filter(java.util.Objects::nonNull).toArray(PsiElement[]::new);
        return targets.length == 0 ? null : targets;
    }
    private static boolean referenceChar(char c) { return HclDocument.isWord(c) || c == '.'; }
}
