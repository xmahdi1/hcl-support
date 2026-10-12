package dev.hclassist.ide;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import dev.hclassist.core.*;
import org.jetbrains.annotations.NotNull;

public final class InsertRequiredFieldsIntention extends PsiElementBaseIntentionAction {
    @Override public @NotNull String getText() { return "Insert missing required HCL fields"; }
    @Override public @NotNull String getFamilyName() { return "HCL Assist"; }
    @Override public boolean startInWriteAction() { return false; }
    @Override public com.intellij.psi.PsiElement getElementToMakeWritable(@NotNull com.intellij.psi.PsiFile file) { return file; }
    @Override public @NotNull com.intellij.codeInsight.intention.preview.IntentionPreviewInfo generatePreview(
            @NotNull Project project, @NotNull Editor editor, @NotNull com.intellij.psi.PsiFile file) {
        return com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.EMPTY;
    }
    @Override public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        var file = element.getContainingFile();
        if (file == null || !Dialect.supported(file.getName())) return false;
        HclDocument doc = new HclDocument(editor.getDocument().getText());
        var body = doc.bodyAt(editor.getCaretModel().getOffset());
        // Cheap availability check. Schema loading happens only when the action is invoked.
        return RequiredFieldTemplates.supports(body);
    }
    @Override public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        var file = element.getContainingFile();
        if (file == null) return;
        RequiredFieldsAsync.insert(file, editor, false);
    }
}
