package dev.hclassist.core;

import com.google.gson.*;
import java.io.Reader;
import java.util.*;

/** Reads the public providers-schema format, independent of JetBrains' changing Terraform APIs. */
public final class ProviderSchema {
    public record Field(String name, JsonElement type, boolean required, boolean computedOnly,
                        boolean sensitive, boolean deprecated, String description, Block nested) {
        public String typeName() {
            if (type == null || type.isJsonNull()) return nested != null ? "object" : "dynamic";
            if (type.isJsonPrimitive()) return type.getAsString();
            return type.isJsonArray() && type.getAsJsonArray().size() > 0 ? type.getAsJsonArray().get(0).getAsString() : "dynamic";
        }
        public String placeholder() {
            return switch (typeName()) {
                case "string" -> "\"\"";
                case "number" -> "0";
                case "bool" -> "false";
                case "list", "set", "tuple" -> "[]";
                case "map", "object" -> "{}";
                default -> "null";
            };
        }
    }
    public record NestedBlock(String name, int minimum, String mode, Block block) {}
    public record Block(List<Field> fields, List<NestedBlock> blocks, String description) {}
    public record Resource(String kind, String type, String provider, Block block) {}
    private final Map<String, Resource> resources;
    public ProviderSchema(Map<String, Resource> resources) { this.resources = Map.copyOf(resources); }
    public static ProviderSchema empty() { return new ProviderSchema(Map.of()); }
    public List<Resource> resources(String kind) {
        return resources.values().stream().filter(r -> r.kind.equals(kind)).sorted(Comparator.comparing(Resource::type)).toList();
    }
    public Resource resource(String kind, String type) { return resources.get(kind + ":" + type); }
    public ProviderSchema overlay(ProviderSchema other) {
        Map<String, Resource> result = new LinkedHashMap<>(resources); result.putAll(other.resources);
        return new ProviderSchema(result);
    }
    public static ProviderSchema read(Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        if (json.has("schemas")) json = json.getAsJsonObject("schemas"); // JetBrains metadata envelope
        if (json.has("format_version") && !json.get("format_version").getAsString().startsWith("1."))
            throw new IllegalArgumentException("Unsupported providers schema format");
        Map<String, Resource> result = new LinkedHashMap<>();
        JsonObject providers = object(json, "provider_schemas");
        for (var provider : providers.entrySet()) {
            JsonObject definition = provider.getValue().getAsJsonObject();
            String[] source = provider.getKey().split("/");
            String local = source[source.length - 1];
            if (definition.has("provider")) {
                Block block = block(object(definition.getAsJsonObject("provider"), "block"), 0);
                result.put("provider:" + local, new Resource("provider", local, provider.getKey(), block));
            }
            for (String kind : List.of("resource", "data")) {
                for (var entry : object(definition, kind.equals("resource") ? "resource_schemas" : "data_source_schemas").entrySet()) {
                    String key = kind + ":" + entry.getKey();
                    result.put(key, new Resource(kind, entry.getKey(), provider.getKey(), block(object(entry.getValue().getAsJsonObject(), "block"), 0)));
                }
            }
        }
        return new ProviderSchema(result);
    }
    private static Block block(JsonObject object, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Provider schema nesting is too deep");
        List<Field> fields = new ArrayList<>();
        for (var attr : object(object, "attributes").entrySet()) {
            JsonObject value = attr.getValue().getAsJsonObject();
            JsonElement type = value.get("type");
            Block nested = value.has("nested_type") ? block(object(value, "nested_type"), depth + 1) : typeBlock(type, depth + 1);
            if (value.has("nested_type")) {
                String mode = string(object(value, "nested_type"), "nesting_mode");
                type = new JsonPrimitive(mode.equals("single") ? "object" : mode);
            }
            fields.add(new Field(attr.getKey(), type, bool(value, "required"),
                    bool(value, "computed") && !bool(value, "optional") && !bool(value, "required"),
                    bool(value, "sensitive"), bool(value, "deprecated"), string(value, "description"), nested));
        }
        fields.sort(Comparator.comparing(Field::required).reversed().thenComparing(Field::name));
        List<NestedBlock> blocks = new ArrayList<>();
        for (var child : object(object, "block_types").entrySet()) {
            JsonObject value = child.getValue().getAsJsonObject();
            blocks.add(new NestedBlock(child.getKey(), value.has("min_items") ? value.get("min_items").getAsInt() : 0,
                    string(value, "nesting_mode"), block(object(value, "block"), depth + 1)));
        }
        blocks.sort(Comparator.comparing(NestedBlock::name));
        return new Block(List.copyOf(fields), List.copyOf(blocks), string(object, "description"));
    }
    private static Block typeBlock(JsonElement type, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Provider type nesting is too deep");
        if (type == null || !type.isJsonArray() || type.getAsJsonArray().size() < 2) return null;
        JsonArray array = type.getAsJsonArray();
        String name = array.get(0).getAsString();
        if (Set.of("list", "set", "map").contains(name)) return typeBlock(array.get(1), depth + 1);
        if (!name.equals("object") || !array.get(1).isJsonObject()) return null;
        List<Field> fields = new ArrayList<>();
        for (var field : array.get(1).getAsJsonObject().entrySet())
            fields.add(new Field(field.getKey(), field.getValue(), true, false, false, false, "", typeBlock(field.getValue(), depth + 1)));
        fields.sort(Comparator.comparing(Field::name));
        return new Block(List.copyOf(fields), List.of(), "");
    }
    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }
    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isBoolean() && object.get(key).getAsBoolean();
    }
    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
    public static Block at(Resource resource, HclDocument.Body body) {
        if (resource == null) return null;
        List<String> path = new ArrayList<>();
        HclDocument.Body current = body;
        while (current.parent != null && current.parent.parent != null) { path.add(0, current.name); current = current.parent; }
        Block block = resource.block;
        for (String segment : path) {
            Block next = null;
            for (NestedBlock nested : block.blocks) if (nested.name.equals(segment)) next = nested.block;
            for (Field field : block.fields) if (field.name.equals(segment) && field.nested != null) next = field.nested;
            if (next == null) return null;
            block = next;
        }
        return block;
    }
}
