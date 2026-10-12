package dev.hclassist.ide;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import dev.hclassist.core.*;

public final class HclDocumentation extends AbstractDocumentationProvider {
    private static final class EntryElement extends FakePsiElement {
        final Schema.Entry entry;
        final PsiElement parent;
        EntryElement(Schema.Entry entry, PsiElement parent) { this.entry = entry; this.parent = parent; }
        @Override public PsiElement getParent() { return parent; }
        @Override public String getName() { return entry.name(); }
    }
    @Override public PsiElement getDocumentationElementForLookupItem(PsiManager manager, Object object, PsiElement element) {
        return object instanceof Schema.Entry e && element != null ? new EntryElement(e, element) : null;
    }
    @Override public String generateDoc(PsiElement element, PsiElement originalElement) {
        if (element instanceof EntryElement e) return render(e.entry);
        PsiElement target = originalElement == null ? element : originalElement;
        if (target == null || target.getContainingFile() == null) return null;
        PsiFile file = target.getContainingFile();
        if (!Dialect.supported(file.getName())) return null;
        String text = file.getText();
        HclDocument doc = new HclDocument(text);
        var state = ProjectSupport.state(file, text);
        int offset = Math.min(target.getTextOffset(), text.length());
        HclDocument.Token token = doc.tokens.stream().filter(t -> t.start() <= offset && t.end() > offset).findFirst().orElse(null);
        if (token == null) return null;
        var body = doc.bodyAt(offset);
        for (Schema.Entry e : state.schema().members(state.dialect(), body)) if (e.name().equals(token.text())) return render(e);
        for (Schema.Entry e : state.schema().functions(state.dialect())) if (e.name().equals(token.text())) return render(e);
        if (state.dialect() == Dialect.TERRAFORM) {
            var resource = ProviderSupport.get(file).resource(file, doc, body);
            var block = ProviderSchema.at(resource, body);
            if (block != null) for (var field : block.fields()) if (field.name().equals(token.text()))
                return render(new Schema.Entry("terraform", body.context(), field.name(), "attribute", field.typeName(),
                        (field.required() ? "Required. " : "Optional. ") + (field.sensitive() ? "Sensitive. " : "") + field.description(),
                        "https://registry.terraform.io/providers/" + resource.provider().replace("registry.terraform.io/", "") + "/latest/docs", ""));
        }
        // Block keywords are outside their opening brace and therefore resolve in the parent body.
        return null;
    }
    private static String render(Schema.Entry e) {
        return "<div class='definition'><pre>" + escape(e.name()) + " : " + escape(e.type()) +
                "</pre></div><div class='content'>" + escape(e.description()) +
                "</div><p><a href='" + escapeUrl(e.url()) + "'>Reference documentation</a></p>";
    }
    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static String escapeUrl(String s) {
        return s.startsWith("https://") ? escape(s).replace("'", "&#39;").replace("\"", "&quot;") : "https://github.com/hashicorp/hcl";
    }
}
