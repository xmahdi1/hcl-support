package dev.hclassist.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Tab-separated, additive schemas. Unknown provider/driver fields are intentionally allowed. */
public final class Schema {
    public record Entry(String dialect, String context, String name, String kind, String type,
                        String description, String url, String template) {
        public String insertion() {
            if (!template.isEmpty()) return template.replace("\\n", "\n").replace("\\t", "  ");
            if (kind.equals("block")) return name + " {\n  $CARET\n}";
            if (kind.equals("function")) return name + "($CARET)";
            if (type.startsWith("enum:")) return name + " = \"$CARET\"";
            return name + " = " + switch (type) {
                case "string" -> "\"$CARET\"";
                case "bool" -> "$CARETfalse";
                case "number" -> "$CARET0";
                case "list" -> "[$CARET]";
                case "map" -> "{\n  $CARET\n}";
                default -> "$CARET";
            };
        }
    }
    private final List<Entry> entries;
    public Schema(List<Entry> entries) { this.entries = List.copyOf(entries); }
    public static Schema bundled() {
        try (InputStream stream = Schema.class.getResourceAsStream("/schemas.tsv")) {
            if (stream == null) throw new IllegalStateException("Missing bundled schemas");
            return new Schema(read(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public Schema overlay(String content) {
        try {
            List<Entry> merged = new ArrayList<>(entries); merged.addAll(read(new StringReader(content)));
            return new Schema(merged);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static List<Entry> read(Reader reader) throws IOException {
        List<Entry> result = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String line; int number = 0;
            while ((line = in.readLine()) != null) {
                number++;
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                if (f.length != 8) throw new IllegalArgumentException("Schema line " + number + " must have 8 tab-separated fields");
                if (!Set.of("attribute", "block", "function").contains(f[3])) throw new IllegalArgumentException("Invalid schema kind at line " + number);
                result.add(new Entry(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7]));
            }
        }
        return result;
    }
    public List<Entry> members(Dialect dialect, HclDocument.Body body) {
        return members(dialect, body.context());
    }
    public List<Entry> members(Dialect dialect, String context) {
        LinkedHashMap<String, Entry> result = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (!e.dialect.equals("common") && !e.dialect.equals(dialect.id())) continue;
            if (matches(e.context, context)) result.put(e.name, e);
        }
        return new ArrayList<>(result.values());
    }
    public List<Entry> functions(Dialect dialect) {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Entry e : entries) if (e.kind.equals("function") &&
                (e.dialect.equals("common") || e.dialect.equals(dialect.id()))) result.put(e.name, e);
        return new ArrayList<>(result.values());
    }
    private static boolean matches(String pattern, String context) {
        if (pattern.equals(context)) return true;
        String[] wanted = pattern.split("/"), actual = context.split("/");
        if (wanted.length != actual.length) return false;
        for (int i = 0; i < wanted.length; i++) {
            if (wanted[i].equals("*")) continue;
            if (wanted[i].endsWith(":*")) {
                if (!actual[i].startsWith(wanted[i].substring(0, wanted[i].length() - 1))) return false;
            } else if (!wanted[i].equals(actual[i])) return false;
        }
        return true;
    }
}
