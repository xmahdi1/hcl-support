package dev.hclassist.core;

import java.util.*;

/** Produces editable placeholders, never fabricated resource configuration. */
public final class RequiredFields {
    public record Value(String marker, String defaultValue, String name) {}
    public record Plan(String text, List<Value> values) {
        public boolean empty() { return text.isEmpty(); }
        public String preview() {
            String result = text;
            for (Value value : values) result = result.replace(value.marker, value.defaultValue);
            return result;
        }
    }
    public static Plan missing(ProviderSchema.Block schema, HclDocument.Body body) {
        if (schema == null) return new Plan("", List.of());
        Set<String> attributes = new HashSet<>();
        for (HclDocument.Attribute a : body.attributes) attributes.add(a.name());
        Map<String, Long> blocks = new HashMap<>();
        for (HclDocument.Body child : body.children) blocks.merge(child.name, 1L, Long::sum);
        StringBuilder text = new StringBuilder(); List<Value> values = new ArrayList<>();
        append(schema, attributes, blocks, "", text, values, 0);
        return new Plan(text.toString(), List.copyOf(values));
    }
    private static void append(ProviderSchema.Block schema, Set<String> existing, Map<String, Long> blocks,
                               String indent, StringBuilder text, List<Value> values, int depth) {
        if (depth > 12 || values.size() > 200) return;
        for (ProviderSchema.Field field : schema.fields()) {
            if (!field.required() || field.computedOnly() || existing.contains(field.name())) continue;
            text.append(indent).append(field.name()).append(" = ");
            if (field.nested() != null) {
                boolean collection = Set.of("list", "set").contains(field.typeName());
                boolean map = field.typeName().equals("map");
                if (collection) text.append('[');
                if (map) text.append("{ name = ");
                text.append("{\n"); append(field.nested(), Set.of(), Map.of(), indent + "  ", text, values, depth + 1);
                text.append(indent).append('}');
                if (collection) text.append(']');
                if (map) text.append('}');
                text.append('\n');
            } else {
                String marker = "$HCL_" + values.size() + "$";
                values.add(new Value(marker, field.placeholder(), field.name())); text.append(marker).append('\n');
            }
        }
        for (ProviderSchema.NestedBlock block : schema.blocks()) {
            long missing = Math.max(0, block.minimum() - blocks.getOrDefault(block.name(), 0L));
            for (int i = 0; i < Math.min(20, missing); i++) {
                text.append(indent).append(block.name());
                if (block.mode().equals("map")) {
                    String marker = "$HCL_" + values.size() + "$";
                    values.add(new Value(marker, "\"name\"", block.name())); text.append(' ').append(marker);
                }
                text.append(" {\n"); append(block.block(), Set.of(), Map.of(), indent + "  ", text, values, depth + 1);
                text.append(indent).append("}\n");
            }
        }
    }
}
