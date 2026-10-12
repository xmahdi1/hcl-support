package dev.hclassist.ide;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import dev.hclassist.core.*;
import org.jetbrains.annotations.NotNull;

public final class HclCompletion extends CompletionContributor {
    public HclCompletion() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override protected void addCompletions(@NotNull CompletionParameters p, @NotNull ProcessingContext context,
                                                    @NotNull CompletionResultSet result) {
                var file = p.getOriginalFile();
                if (!Dialect.supported(file.getName())) return;
                String text = p.getEditor().getDocument().getText();
                if (text.length() > 512_000) return;
                var doc = new HclDocument(text);
                var state = ProjectSupport.state(file, text);
                if (state.dialect() == Dialect.TERRAFORM) providerCompletions(file, doc, p.getOffset(), result);
                var symbols = ProjectSupport.declarations(file, doc, state.dialect()).stream().map(ProjectSupport.Declaration::symbol).toList();
                for (var s : CompletionEngine.complete(file.getName(), doc, p.getOffset(), state.dialect(), state.schema(), symbols)) {
                    Object lookupObject = s.entry() == null ? s.name() : s.entry();
                    var item = LookupElementBuilder.create(lookupObject, s.name()).withTypeText("HCL Assist", true)
                            .withTailText("  " + s.detail(), true).withInsertHandler((insertion, ignored) -> insert(insertion, s));
                    result.addElement(PrioritizedLookupElement.withPriority(item, 50));
                }
            }
        });
    }
    private static void providerCompletions(com.intellij.psi.PsiFile file, HclDocument doc, int offset, CompletionResultSet result) {
        var position = doc.position(offset);
        // First quoted label: suggest available resource/data-source types.
        for (int i = 1; i < doc.tokens.size(); i++) {
            var token = doc.tokens.get(i);
            if (token.kind() != HclDocument.Kind.STRING || token.start() >= offset || token.end() < offset) continue;
            String kind = doc.tokens.get(i - 1).text();
            if (!java.util.Set.of("resource", "data", "provider").contains(kind)) continue;
            String partial = doc.text.substring(token.start() + 1, offset);
            var schema = ProviderSupport.get(file).schema(file, doc, partial);
            for (var resource : schema.resources(kind))
                result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(resource.type()).withTypeText("Provider schema", true), 70));
            return;
        }
        if (position.blocked() || position.expression()) return;
        var resource = ProviderSupport.get(file).resource(file, doc, position.body());
        var block = ProviderSchema.at(resource, position.body());
        if (block == null) return;
        for (var field : block.fields()) {
            if (field.computedOnly() || position.body().attributes.stream().anyMatch(a -> a.name().equals(field.name()) && a.start() != position.prefixStart())) continue;
            String description = (field.required() ? "Required. " : "Optional. ") + (field.sensitive() ? "Sensitive. " : "") + field.description();
            var entry = new Schema.Entry("terraform", position.body().context(), field.name(), "attribute", field.typeName(), description,
                    "https://registry.terraform.io/providers/" + resource.provider().replace("registry.terraform.io/", "") + "/latest/docs", "");
            var suggestion = new CompletionEngine.Suggestion(field.name(), field.name() + " = $CARET" + field.placeholder(), field.typeName(), entry);
            var item = LookupElementBuilder.create(entry, field.name()).withBoldness(field.required()).withStrikeoutness(field.deprecated())
                    .withTailText(field.required() ? "  required" : "  optional", true).withTypeText(field.typeName(), true)
                    .withInsertHandler((context, ignored) -> insert(context, suggestion));
            result.addElement(PrioritizedLookupElement.withPriority(item, field.required() ? 100 : 60));
        }
        for (var nested : block.blocks()) {
            var entry = new Schema.Entry("terraform", position.body().context(), nested.name(), "block", "block", nested.block().description(),
                    "https://registry.terraform.io/providers/" + resource.provider().replace("registry.terraform.io/", "") + "/latest/docs", "");
            var suggestion = new CompletionEngine.Suggestion(nested.name(), entry.insertion(), "block", entry);
            result.addElement(LookupElementBuilder.create(entry, nested.name()).withTailText(nested.minimum() > 0 ? "  required block" : "  block", true)
                    .withInsertHandler((context, ignored) -> insert(context, suggestion)));
        }
    }
    private static void insert(InsertionContext context, CompletionEngine.Suggestion suggestion) {
        Document document = context.getDocument();
        int start = context.getStartOffset(), end = context.getTailOffset();
        String template = suggestion.insertion();
        int next = end;
        while (next < document.getTextLength() && Character.isWhitespace(document.getCharsSequence().charAt(next))) next++;
        if (suggestion.entry() != null && next < document.getTextLength()) {
            char c = document.getCharsSequence().charAt(next);
            if (suggestion.entry().kind().equals("attribute") && c == '=' ||
                suggestion.entry().kind().equals("function") && c == '(' ||
                suggestion.entry().kind().equals("block") && (c == '{' || c == '"')) template = suggestion.name();
        }
        int line = document.getLineNumber(start);
        String linePrefix = document.getCharsSequence().subSequence(document.getLineStartOffset(line), start).toString();
        String indent = linePrefix.substring(0, linePrefix.length() - linePrefix.stripLeading().length());
        template = template.replace("\n", "\n" + indent);
        int marker = template.indexOf("$CARET");
        String replacement = template.replace("$CARET", "");
        document.replaceString(start, end, replacement);
        context.setTailOffset(start + replacement.length());
        context.getEditor().getCaretModel().moveToOffset(start + (marker < 0 ? replacement.length() : marker));
        context.setAddCompletionChar(false);
    }
}
