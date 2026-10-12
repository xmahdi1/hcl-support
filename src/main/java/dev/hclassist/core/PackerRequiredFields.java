package dev.hclassist.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Data-driven Packer requirements, including alternative inputs and literal conditions. */
public final class PackerRequiredFields {
    public record Condition(String field, String value, String fallback) {}
    public record Rule(String name, String type, List<String> unlessAny, Condition when, String placeholder) {}
    public record Definition(String url, List<Rule> fields) {}
    public record Rules(Map<String, Definition> blocks) {
        public Rules { blocks = Map.copyOf(blocks); }
        public Rules overlay(Reader reader) {
            Map<String, Definition> merged = new LinkedHashMap<>(blocks);
            merged.putAll(read(reader).blocks);
            return new Rules(merged);
        }
    }
    private static final Rules BUNDLED = bundled();
    private PackerRequiredFields() {}
    public static Rules defaults() { return BUNDLED; }
    private static Rules bundled() {
        try (InputStream input = PackerRequiredFields.class.getResourceAsStream("/packer-required-fields.json")) {
            if (input == null) throw new IllegalStateException("Missing Packer requirements");
            return read(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static Rules read(Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        if (!json.has("version") || json.get("version").getAsInt() != 1)
            throw new IllegalArgumentException("Packer requirements must use version 1");
        Map<String, Definition> definitions = new LinkedHashMap<>();
        for (var entry : json.getAsJsonObject("blocks").entrySet()) {
            JsonObject definition = entry.getValue().getAsJsonObject();
            List<Rule> rules = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (JsonElement element : definition.getAsJsonArray("fields")) {
                JsonObject field = element.getAsJsonObject();
                String name = field.get("name").getAsString();
                if (!name.matches("[A-Za-z_][A-Za-z0-9_-]*") || !names.add(name))
                    throw new IllegalArgumentException("Invalid or duplicate field: " + name);
                String type = string(field, "type", "string");
                if (!Set.of("string", "bool", "number", "list", "map", "expression").contains(type))
                    throw new IllegalArgumentException("Unknown field type: " + type);
                List<String> alternatives = new ArrayList<>();
                if (field.has("unlessAny")) for (JsonElement alternative : field.getAsJsonArray("unlessAny")) alternatives.add(alternative.getAsString());
                Condition when = null;
                if (field.has("when")) {
                    JsonObject condition = field.getAsJsonObject("when");
                    when = new Condition(condition.get("field").getAsString(), condition.get("equals").getAsString(), string(condition, "default", null));
                }
                rules.add(new Rule(name, type, List.copyOf(alternatives), when, string(field, "placeholder", null)));
            }
            definitions.put(entry.getKey(), new Definition(string(definition, "url", ""), List.copyOf(rules)));
        }
        return new Rules(definitions);
    }
    private static String string(JsonObject object, String name, String fallback) {
        return object.has(name) ? object.get(name).getAsString() : fallback;
    }
    public static boolean supports(HclDocument.Body body) {
        if (body.object) return false;
        for (var current = body; current.parent != null; current = current.parent) {
            if (current.labels.isEmpty()) continue;
            if (current.name.equals("source") && current.parent.parent == null) return true;
            if (current.name.equals("provisioner") && current.parent.name.equals("build")) return true;
            if (current.name.equals("post-processor") && (current.parent.name.equals("build") || current.parent.name.equals("post-processors"))) return true;
        }
        return false;
    }
    public static RequiredFields.Plan missing(HclDocument doc, HclDocument.Body body) {
        return missing(doc, body, BUNDLED);
    }
    public static RequiredFields.Plan missing(HclDocument doc, HclDocument.Body body, Rules rules) {
        Definition definition = rules.blocks.get(body.context());
        if (definition == null || body.object) return new RequiredFields.Plan("", List.of());
        Set<String> present = new HashSet<>();
        body.attributes.forEach(a -> present.add(a.name()));
        body.children.forEach(b -> present.add(b.name));
        List<ProviderSchema.Field> fields = new ArrayList<>();
        Map<String, String> placeholders = new HashMap<>();
        for (Rule rule : definition.fields) {
            if (rule.unlessAny.stream().anyMatch(present::contains)) continue;
            if (rule.when != null) {
                String value = present.contains(rule.when.field) ? literal(doc, body, rule.when.field) : rule.when.fallback;
                // Expression values are not evaluated or treated as defaults.
                if (!rule.when.value.equals(value)) continue;
            }
            fields.add(new ProviderSchema.Field(rule.name, new JsonPrimitive(rule.type), true, false, false, false, "", null));
            if (rule.placeholder != null) placeholders.put(rule.name, rule.placeholder);
        }
        RequiredFields.Plan plan = RequiredFields.missing(new ProviderSchema.Block(fields, List.of(), ""), body);
        List<RequiredFields.Value> values = plan.values().stream().map(value -> new RequiredFields.Value(
                value.marker(), placeholders.getOrDefault(value.name(), value.defaultValue()), value.name())).toList();
        return new RequiredFields.Plan(plan.text(), values);
    }
    private static String literal(HclDocument doc, HclDocument.Body body, String name) {
        for (var attribute : body.attributes) if (attribute.name().equals(name)) {
            for (int i = 0; i + 2 < doc.tokens.size(); i++) if (doc.tokens.get(i).start() == attribute.start()) {
                var value = doc.tokens.get(i + 2);
                if (value.kind() != HclDocument.Kind.STRING && !Set.of("true", "false").contains(value.text())) return null;
                if (i + 3 < doc.tokens.size()) {
                    var next = doc.tokens.get(i + 3);
                    if (next.kind() != HclDocument.Kind.NEWLINE && next.kind() != HclDocument.Kind.COMMENT && !next.text().equals("}")) return null;
                }
                return doc.scalar(body, name);
            }
        }
        return null;
    }
}
