package dev.hclassist.ide;

import com.intellij.codeInspection.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import dev.hclassist.core.*;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public final class HclInspection extends LocalInspectionTool {
    @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean onTheFly) {
        return new PsiElementVisitor() {
            @Override public void visitFile(@NotNull PsiFile file) {
                if (!Dialect.supported(file.getName()) || file.getTextLength() > 512_000) return;
                inspect(new HclDocument(file.getText()).root, holder, file);
            }
        };
    }
    private static void inspect(HclDocument.Body body, ProblemsHolder holder, PsiFile file) {
        Set<String> seen = new HashSet<>();
        for (HclDocument.Attribute a : body.attributes) if (!body.object && !seen.add(a.name()))
            holder.registerProblem(file, new TextRange(a.start(), a.end()), "Duplicate attribute '" + a.name() + "'");
        for (HclDocument.Body child : body.children) inspect(child, holder, file);
    }
}
