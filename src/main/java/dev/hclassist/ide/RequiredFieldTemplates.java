package dev.hclassist.ide;

import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import dev.hclassist.core.*;

final class RequiredFieldTemplates {
    static boolean supports(HclDocument.Body body) {
        return java.util.Set.of("resource", "data", "provider").contains(ProviderSupport.declaration(body).name)
                || PackerRequiredFields.supports(body);
    }
    static RequiredFields.Plan plan(PsiFile file, HclDocument doc, HclDocument.Body body) {
        if (ProjectSupport.state(file, doc.text).dialect() == Dialect.PACKER)
            return PackerRequiredFields.missing(doc, body, ProjectSupport.packerRequirements(file));
        var resource = ProviderSupport.get(file).resource(file, doc, body);
        return RequiredFields.missing(ProviderSchema.at(resource, body), body);
    }
    static void insert(Project project, Editor editor, HclDocument.Body body, RequiredFields.Plan plan) {
        if (plan.empty()) return;
        var document = editor.getDocument();
        int line = document.getLineNumber(body.nameOffset);
        String prefix = document.getCharsSequence().subSequence(document.getLineStartOffset(line), body.nameOffset).toString();
        String indent = prefix.substring(0, prefix.length() - prefix.stripLeading().length()) + "  ";
        String text = "\n" + indent + plan.text().replace("\n", "\n" + indent).stripTrailing();
        if (body.end >= document.getTextLength() || document.getCharsSequence().charAt(body.end) == '}') text += "\n" + indent.substring(0, indent.length() - 2);
        String insertion = text;
        WriteCommandAction.runWriteCommandAction(project, "Insert required HCL fields", null, () -> {
            editor.getCaretModel().moveToOffset(body.start);
            Template template = TemplateManager.getInstance(project).createTemplate("", "HCL Assist");
            int position = 0;
            for (RequiredFields.Value value : plan.values()) {
                int marker = insertion.indexOf(value.marker(), position);
                if (marker < 0) continue;
                template.addTextSegment(insertion.substring(position, marker));
                String name = value.marker().substring(1, value.marker().length() - 1);
                template.addVariable(name, new TextExpression(value.defaultValue()), new TextExpression(value.defaultValue()), true);
                position = marker + value.marker().length();
            }
            template.addTextSegment(insertion.substring(position));
            template.addEndVariable();
            template.setToReformat(true);
            TemplateManager.getInstance(project).startTemplate(editor, template);
        });
    }
}
