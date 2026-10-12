package dev.hclassist.core;

import java.io.StringReader;

final class ProviderSchemaTest {
    private static int checks;
    static int run() {
        checks = 0;
        String json = """
            {"format_version":"1.0", "provider_schemas": {
              "registry.terraform.io/example/test": {
                "provider":{"block":{"attributes":{"token":{"type":"string","optional":true,"sensitive":true}}}},
                "resource_schemas":{"test_widget":{"block":{
                  "attributes":{
                    "id":{"type":"string","computed":true},
                    "name":{"type":"string","required":true},
                    "enabled":{"type":"bool","required":true},
                    "size":{"type":"number","required":true},
                    "tags":{"type":["map","string"],"optional":true},
                    "secret":{"type":"string","optional":true,"sensitive":true},
                    "old":{"type":"string","optional":true,"deprecated":true},
                    "settings":{"required":true,"nested_type":{"nesting_mode":"single","attributes":{
                      "endpoint":{"type":"string","required":true},"port":{"type":"number","optional":true}}}},
                    "targets":{"required":true,"nested_type":{"nesting_mode":"list","attributes":{
                      "address":{"type":"string","required":true}}}},
                    "legacy_object":{"required":true,"type":["object",{"key":"string"}]}
                  },
                  "block_types":{
                    "auth":{"nesting_mode":"single","min_items":1,"block":{"attributes":{"method":{"type":"string","required":true}}}},
                    "rule":{"nesting_mode":"list","min_items":2,"block":{"attributes":{"action":{"type":"string","required":true}}}},
                    "optional_block":{"nesting_mode":"list","block":{"attributes":{"value":{"type":"string","required":true}}}}
                  }
                }}},
                "data_source_schemas":{"test_widget":{"block":{"attributes":{"name":{"type":"string","required":true}}}}
              }
            }}}
            """;
        ProviderSchema schema = ProviderSchema.read(new StringReader(json));
        check(schema.resources("resource").size() == 1, "Resource discovery");
        check(schema.resource("data", "test_widget") != null, "Data source discovery");
        check(schema.resource("provider", "test") != null, "Provider discovery");
        check(schema.resource("resource", "unknown") == null, "Unknown type is allowed");
        HclDocument doc = new HclDocument("resource \"test_widget\" \"x\" { }");
        var resource = schema.resource("resource", "test_widget");
        var body = doc.root.children.get(0);
        var plan = RequiredFields.missing(resource.block(), body);
        String preview = plan.preview();
        check(preview.contains("name = \"\""), "Required string placeholder");
        check(preview.contains("enabled = false"), "Required bool placeholder");
        check(preview.contains("size = 0"), "Required number placeholder");
        check(!preview.contains("id ="), "Computed-only attributes excluded");
        check(!preview.contains("tags ="), "Optional attributes excluded");
        check(!preview.contains("secret ="), "No optional sensitive attributes inserted");
        check(preview.contains("settings = {\n  endpoint = \"\"\n}"), "Required nested attribute object");
        check(preview.contains("targets = [{\n  address = \"\"\n}]"), "Required nested list attribute");
        check(preview.contains("legacy_object = {\n  key = \"\"\n}"), "Legacy object type");
        check(preview.contains("auth {\n  method = \"\"\n}"), "Required nested block");
        check(preview.split("rule \\{", -1).length - 1 == 2, "Respect minimum repeated block count");
        check(!preview.contains("optional_block"), "Optional block excluded");
        check(plan.values().stream().map(RequiredFields.Value::marker).distinct().count() == plan.values().size(), "Unique live template fields");
        check(plan.values().size() == 9, "Every leaf field can be edited with Tab");
        HclDocument existing = new HclDocument("resource \"test_widget\" \"x\" { name = var.name\nauth { method = \"token\" }\nrule { action = \"allow\" } }");
        var missing = RequiredFields.missing(resource.block(), existing.root.children.get(0)).preview();
        check(!missing.contains("name ="), "Existing values preserved");
        check(!missing.contains("auth {"), "Existing required block preserved");
        check(missing.split("rule \\{", -1).length - 1 == 1, "Only missing repeated blocks inserted");
        HclDocument nested = new HclDocument("resource \"test_widget\" \"x\" { auth { } }");
        var nestedBody = nested.root.children.get(0).children.get(0);
        check(ProviderSchema.at(resource, nestedBody).fields().get(0).name().equals("method"), "Resolve nested block schema");
        check(RequiredFields.missing(null, body).empty(), "No metadata means no invented fields");
        check(ProviderSchema.read(new StringReader("{\"schemas\":" + json + "}")).resource("resource", "test_widget") != null, "JetBrains metadata envelope");
        boolean rejected = false;
        try { ProviderSchema.read(new StringReader("{\"format_version\":\"2.0\"}")); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Unsupported major schema format rejected");
        HclDocument generated = new HclDocument("resource \"test_widget\" \"x\" {\n" + preview + "}");
        check(RequiredFields.missing(resource.block(), generated.root.children.get(0)).empty(), "Applying the template twice is idempotent");
        return checks;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
