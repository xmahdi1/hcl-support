package dev.hclassist.ide;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import dev.hclassist.core.Dialect;
import org.jetbrains.annotations.NotNull;

public final class HclTypedHandler extends TypedHandlerDelegate {
    @Override public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (c == '{' && Dialect.supported(file.getName()) && editor.getDocument().getTextLength() <= 512_000) {
            var doc = new dev.hclassist.core.HclDocument(editor.getDocument().getText());
            var body = doc.bodyAt(editor.getCaretModel().getOffset());
            if (RequiredFieldTemplates.supports(body))
                RequiredFieldsAsync.insert(file, editor, true);
        }
        return Result.CONTINUE;
    }
    @Override public @NotNull Result checkAutoPopup(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (Dialect.supported(file.getName()) && (Character.isLetter(c) || c == '_' || c == '.'))
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        return Result.CONTINUE;
    }
}
