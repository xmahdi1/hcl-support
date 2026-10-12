package dev.hclassist.ide;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import dev.hclassist.core.*;
import org.jetbrains.annotations.NotNull;

/** Also handles blocks created by completion, where the opening brace already exists. */
public final class HclEnterHandler extends EnterHandlerDelegateAdapter {
    @Override public @NotNull Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext context) {
        if (Dialect.supported(file.getName()) && editor.getDocument().getTextLength() <= 512_000) {
            var doc = new HclDocument(editor.getDocument().getText());
            var body = doc.bodyAt(editor.getCaretModel().getOffset());
            if (RequiredFieldTemplates.supports(body))
                RequiredFieldsAsync.insert(file, editor, true);
        }
        return Result.Continue;
    }
}
