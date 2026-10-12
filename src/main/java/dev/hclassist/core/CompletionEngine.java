package dev.hclassist.core;

import java.util.*;

public final class CompletionEngine {
    public record Suggestion(String name, String insertion, String detail, Schema.Entry entry) {}
    public static List<Suggestion> complete(String filename, HclDocument doc, int offset, Dialect dialect,
                                            Schema schema, List<HclDocument.Symbol> symbols) {
        HclDocument.Position p = doc.position(offset);
        List<Suggestion> out = new ArrayList<>();
        if (inCommentOrHeredoc(doc, offset)) return out;
        String before = doc.text.substring(0, offset);
        for (Schema.Entry e : schema.members(dialect, p.body())) {
            if (!e.type().startsWith("enum:")) continue;
            String pattern = "(?s).*\\b" + java.util.regex.Pattern.quote(e.name()) + "\\s*=\\s*(\"?)[\\w-]*$";
            var matcher = java.util.regex.Pattern.compile(pattern).matcher(before);
            if (matcher.matches()) {
                for (String value : e.type().substring(5).split(","))
                    out.add(new Suggestion(value, matcher.group(1).equals("\"") ? value : "\"" + value + "\"", "Allowed value", null));
                return out;
            }
        }
        // Labels are strings, but have their own finite vocabulary.
        String labelKind = labelKind(doc, offset);
        if (labelKind != null && !(labelKind.equals("source") && p.body().name.equals("build"))) {
            List<String> labels = switch (dialect.id() + ":" + labelKind) {
                case "packer:source" -> List.of("amazon-ebs", "docker", "qemu", "virtualbox-iso", "vsphere-iso", "azure-arm", "googlecompute");
                case "packer:provisioner" -> List.of("shell", "shell-local", "file", "ansible", "powershell", "windows-restart");
                case "packer:post-processor" -> List.of("manifest", "shell-local", "docker-tag", "docker-push", "compress");
                default -> List.of();
            };
            for (String label : labels) out.add(new Suggestion(label, label, "Plugin type", null));
            return out;
        }
        boolean sourceString = p.string() && dialect == Dialect.PACKER &&
                (p.body().name.equals("build") || p.body().name.equals("source")) &&
                before.matches("(?s).*\\b(?:sources\\s*=\\s*\\[[^]]*|source\\s*)\"[^\"]*$");
        if (sourceString) {
            int quote = before.lastIndexOf('"');
            String typed = before.substring(quote + 1);
            for (HclDocument.Symbol s : symbols) if (s.name().startsWith("source.")) {
                String qualified = s.name();
                int dot = typed.lastIndexOf('.');
                if (dot >= 0 && !qualified.startsWith(typed.substring(0, dot + 1))) continue;
                String tail = dot < 0 ? qualified : qualified.substring(dot + 1);
                out.add(new Suggestion(tail, tail, "Packer source", null));
            }
            return out;
        }
        if (p.blocked()) return out;
        if (Dialect.variablesFile(filename) && p.body().parent == null && !p.expression()) {
            for (HclDocument.Symbol s : symbols) if (s.name().startsWith("var.")) {
                String name = s.name().substring(4);
                if (p.body().attributes.stream().noneMatch(a -> a.name().equals(name) && a.start() != p.prefixStart()))
                    out.add(new Suggestion(name, name + " = $CARET", "Input variable", null));
            }
            return out;
        }
        if (p.expression()) {
            int end = p.prefixStart(); int start = end;
            while (start > 0 && (HclDocument.isWord(doc.text.charAt(start - 1)) || doc.text.charAt(start - 1) == '.')) start--;
            String qualifier = doc.text.substring(start, end);
            for (HclDocument.Symbol s : symbols) {
                if (!s.name().startsWith(qualifier)) continue;
                String tail = s.name().substring(qualifier.length());
                out.add(new Suggestion(tail, tail, s.description(), null));
            }
            if (!qualifier.isEmpty()) return out;
            for (Schema.Entry e : schema.functions(dialect)) out.add(new Suggestion(e.name(), e.insertion(), e.type(), e));
            for (String literal : List.of("true", "false", "null")) out.add(new Suggestion(literal, literal, "Literal", null));
            for (String type : List.of("string", "number", "bool", "list", "set", "map", "object", "tuple", "any")) {
                if (before.matches("(?s).*\\btype\\s*=\\s*[^\n]*$"))
                    out.add(new Suggestion(type, type + (Set.of("list", "set", "map", "object", "tuple").contains(type) ? "($CARET)" : ""), "Type constraint", null));
            }
            return out;
        }
        String context = p.body().context();
        if (dialect == Dialect.NOMAD && p.body().name.equals("config") && p.body().parent != null) {
            String driver = doc.scalar(p.body().parent, "driver");
            if (!driver.isEmpty()) context += ":" + driver;
        }
        List<Schema.Entry> members = schema.members(dialect, context);
        if (dialect == Dialect.GENERIC && p.body().parent == null) {
            Map<String, Schema.Entry> combined = new LinkedHashMap<>();
            for (Dialect candidate : List.of(Dialect.TERRAFORM, Dialect.PACKER, Dialect.NOMAD, Dialect.CONSUL))
                for (Schema.Entry e : schema.members(candidate, "root")) if (e.kind().equals("block")) combined.putIfAbsent(e.name(), e);
            for (Schema.Entry e : members) combined.put(e.name(), e);
            members = new ArrayList<>(combined.values());
        }
        for (Schema.Entry e : members) {
            if (e.kind().equals("attribute") && p.body().attributes.stream().anyMatch(a -> a.name().equals(e.name()) && a.start() != p.prefixStart())) continue;
            out.add(new Suggestion(e.name(), e.insertion(), e.kind() + " · " + e.type(), e));
        }
        return out;
    }
    private static boolean inCommentOrHeredoc(HclDocument doc, int offset) {
        return doc.tokens.stream().anyMatch(t -> t.start() < offset && offset <= t.end() &&
                (t.kind() == HclDocument.Kind.COMMENT || t.kind() == HclDocument.Kind.HEREDOC));
    }
    private static String labelKind(HclDocument doc, int offset) {
        for (int i = 1; i < doc.tokens.size(); i++) {
            HclDocument.Token t = doc.tokens.get(i);
            if (t.kind() == HclDocument.Kind.STRING && t.start() < offset &&
                    (offset < t.end() || offset == t.end() && (t.text().length() == 1 || !t.text().endsWith("\"")))) {
                HclDocument.Token prev = doc.tokens.get(i - 1);
                if (prev.kind() == HclDocument.Kind.WORD && Set.of("source", "provisioner", "post-processor").contains(prev.text())) return prev.text();
            }
        }
        return null;
    }
}
