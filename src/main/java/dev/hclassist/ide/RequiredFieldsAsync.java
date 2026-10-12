package dev.hclassist.ide;

import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import dev.hclassist.core.*;

final class RequiredFieldsAsync {
    static void insert(PsiFile file, Editor editor, boolean automatic) {
        var project = file.getProject();
        var document = editor.getDocument();
        long stamp = document.getModificationStamp();
        int caret = editor.getCaretModel().getOffset();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            var result = ReadAction.compute(() -> {
                if (project.isDisposed() || !file.isValid() || document.getModificationStamp() != stamp) return null;
                HclDocument doc = new HclDocument(document.getText());
                var body = doc.bodyAt(caret);
                if (automatic && (!body.attributes.isEmpty() || !body.children.isEmpty())) return null;
                if (automatic && !ProjectSupport.state(file, doc.text).autoRequired()) return null;
                return new Result(body, RequiredFieldTemplates.plan(file, doc, body));
            });
            if (result == null) return;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed() || editor.isDisposed() || !document.isWritable() || document.getModificationStamp() != stamp || editor.getCaretModel().getOffset() != caret) return;
                if (!result.plan.empty()) RequiredFieldTemplates.insert(project, editor, result.body, result.plan);
                else if (!automatic) Messages.showInfoMessage(project, "No missing required fields were found. Packer templates use bundled requirements or .hcl-assist.packer.json. For Terraform/OpenTofu metadata, use Tools → HCL Assist → Refresh Provider Schema.", "HCL Assist");
            }, ModalityState.defaultModalityState());
        });
    }
    private record Result(HclDocument.Body body, RequiredFields.Plan plan) {}
}
