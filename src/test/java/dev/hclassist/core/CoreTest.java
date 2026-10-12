package dev.hclassist.core;

import java.util.*;

/** Regression tests, run with ./gradlew coreTest. */
public final class CoreTest {
    private static int checks;
    private static void packerRequiredFields() {
        for (String communicator : List.of("", "ssh", "none", "winrm", "var.communicator")) {
            String value = communicator.startsWith("var.") ? communicator : "\"" + communicator + "\"";
            HclDocument doc = new HclDocument("source \"virtualbox-iso\" \"name\" {\n"
                    + (communicator.isEmpty() ? "" : "communicator = " + value + "\n") + "}");
            String preview = PackerRequiredFields.missing(doc, doc.root.children.get(0)).preview();
            check(preview.contains("iso_url = \"\"") && preview.contains("iso_checksum = \"\""), "Packer ISO inputs");
            check(preview.contains("ssh_username") == (communicator.isEmpty() || communicator.equals("ssh")), "Conditional SSH username");
            check(preview.contains("winrm_username") == communicator.equals("winrm"), "Conditional WinRM username");
        }
        HclDocument existing = new HclDocument("source \"virtualbox-iso\" \"name\" { iso_urls = [\"keep.iso\"]\n iso_checksum = \"sha256:keep\"\n ssh_username = \"keep\"\n }");
        check(PackerRequiredFields.missing(existing, existing.root.children.get(0)).empty(), "Complete Packer source is unchanged");
        HclDocument nested = new HclDocument("source \"virtualbox-iso\" \"name\" { nested { } }");
        check(PackerRequiredFields.missing(nested, nested.root.children.get(0).children.get(0)).empty(), "No source fields in nested blocks");
        HclDocument unknown = new HclDocument("source \"custom\" \"name\" {}");
        check(PackerRequiredFields.missing(unknown, unknown.root.children.get(0)).empty(), "Unknown builders are unchanged");
    }
    private static void packerRequirementRules() {
        String[][] cases = {
            {"source \"amazon-ebs\" \"image\"", "", "ami_name,region,instance_type,source_ami,ssh_username", "access_key,secret_key,winrm_username"},
            {"source \"amazon-ebs\" \"image\"", "source_ami_filter { owners = [\"amazon\"] }\ncommunicator = \"none\"", "ami_name,region,instance_type", "source_ami,ssh_username"},
            {"source \"docker\" \"image\"", "", "image,commit", "ssh_username,discard,export_path"},
            {"source \"docker\" \"image\"", "build { path = \"Dockerfile\" }\nexport_path = \"image.tar\"", "", "image,commit,discard"},
            {"source \"qemu\" \"image\"", "iso_urls = [\"disk.img\"]\ncommunicator = \"winrm\"", "iso_checksum,winrm_username", "iso_url,ssh_username"},
            {"build { provisioner \"shell\"", "inline = [\"echo ready\"]", "", "script,scripts"},
            {"build { provisioner \"shell-local\"", "command = \"echo ready\"", "", "script"},
            {"build { provisioner \"powershell\"", "scripts = [\"setup.ps1\"]", "", "script,inline"},
            {"build { provisioner \"file\"", "content = \"keep\"", "destination", "source,sources"},
            {"build { provisioner \"file\"", "sources = [\"keep\"]\ndestination = \"/tmp/\"", "", "source,destination"},
            {"build { provisioner \"ansible\"", "", "playbook_file", "script"},
            {"build { post-processor \"shell-local\"", "", "script", "inline"},
            {"build { post-processors { post-processor \"shell-local\"", "command = \"keep\"", "", "script"},
            {"source \"docker\" \"image\" { build", "", "path", "image,commit"}
        };
        for (String[] scenario : cases) {
            String text = scenario[0] + " {\n" + scenario[1] + "\n";
            int offset = text.length();
            text += "}".repeat((int) text.chars().filter(c -> c == '{').count() - (int) text.chars().filter(c -> c == '}').count());
            HclDocument doc = new HclDocument(text);
            var body = doc.bodyAt(offset);
            var plan = PackerRequiredFields.missing(doc, body);
            String preview = plan.preview();
            for (String required : scenario[2].split(",")) if (!required.isEmpty())
                check(preview.contains(required + " ="), scenario[0] + " requires " + required);
            for (String excluded : scenario[3].split(",")) if (!excluded.isEmpty())
                check(!preview.contains(excluded + " ="), scenario[0] + " excludes " + excluded);
            String inserted = text.substring(0, body.start) + "\n" + preview + text.substring(body.start);
            HclDocument complete = new HclDocument(inserted);
            check(PackerRequiredFields.missing(complete, complete.bodyAt(body.start + 1)).empty(), "Packer insertion is idempotent: " + scenario[0]);
        }
        var custom = PackerRequiredFields.defaults().overlay(new java.io.StringReader("""
            {"version":1,"blocks":{"source:custom":{"fields":[{"name":"endpoint"},{"name":"replicas","type":"number"}]}}}
            """));
        HclDocument doc = new HclDocument("source \"custom\" \"example\" {}");
        String preview = PackerRequiredFields.missing(doc, doc.root.children.get(0), custom).preview();
        check(preview.contains("endpoint = \"\"") && preview.contains("replicas = 0"), "Third-party builder requirements");
        check(custom.blocks().containsKey("source:amazon-ebs"), "Project rules preserve unrelated bundled requirements");
        for (var entry : PackerRequiredFields.defaults().blocks().entrySet()) {
            check(entry.getValue().url().startsWith("https://developer.hashicorp.com/packer/"), "Requirements have an official reference");
        }
        boolean rejected = false;
        try { PackerRequiredFields.read(new java.io.StringReader("{\"version\":2,\"blocks\":{}}")); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Unsupported requirement format is rejected");
    }
    public static void main(String[] args) {
        packerRequiredFields();
        packerRequirementRules();
        check(Dialect.detect("image.pkr.hcl", "") == Dialect.PACKER, "Packer extension");
        check(Dialect.detect("main.tofu", "") == Dialect.TERRAFORM, "OpenTofu extension");
        check(Dialect.detect("job.nomad.hcl", "") == Dialect.NOMAD, "Nomad extension");
        check(Dialect.detect("agent.hcl", "datacenter = \"dc1\"") == Dialect.CONSUL, "Consul attributes");
        check(Dialect.detect("other.hcl", "# job \"no\" {}\nmessage = \"build {}\"") == Dialect.GENERIC, "Comments and strings do not select dialect");
        has("image.pkr.hcl", "<caret>", "build", "source", "packer");
        lacks("image.pkr.hcl", "<caret>", "resource", "job");
        has("job.nomad", "job \"web\" { group \"api\" { task \"server\" { <caret> } } }", "driver", "config", "resources");
        lacks("job.nomad", "job \"web\" { group \"api\" { task \"server\" { <caret> } } }", "datacenters", "group");
        has("agent.hcl", "datacenter = \"dc1\"\nacl { <caret> }", "enabled", "default_policy", "tokens");
        has("main.tf", "resource \"aws_instance\" \"web\" { <caret> }", "for_each", "lifecycle");
        has("image.pkr.hcl", "source \"amazon-ebs\" \"ubuntu\" { <caret> }", "region", "ami_name", "ssh_username");
        lacks("image.pkr.hcl", "source \"docker\" \"image\" { <caret> }", "region", "ami_name");
        has("image.pkr.hcl", "source \"amazon-ebs\" \"ubuntu\" { source_ami_filter { <caret> } }", "filters", "owners", "most_recent");
        has("image.pkr.hcl", "build { provisioner \"shell\" { <caret> } }", "inline", "scripts");
        lacks("image.pkr.hcl", "build { provisioner \"shell\" { inline = []\n<caret> } }", "inline");
        has("image.pkr.hcl", "build { provisioner \"shell\" { in<caret>line = [] } }", "inline");
        has("image.pkr.hcl", "source \"ama<caret>\" \"ubuntu\" {}", "amazon-ebs");
        has("image.pkr.hcl", "source \"ama<caret>", "amazon-ebs");
        has("image.pkr.hcl", "source \"<caret>", "amazon-ebs", "docker");
        has("image.pkr.hcl", "build { provisioner \"sh<caret>\" {} }", "shell");
        empty("image.pkr.hcl", "# build <caret>");
        empty("image.pkr.hcl", "/* source { <caret> */");
        empty("image.pkr.hcl", "build { name = \"literal <caret>\" }");
        empty("image.pkr.hcl", "locals { script = <<-EOF\nsource \"fake\" { <caret>\nEOF\n}");
        has("image.pkr.hcl", "variable \"region\" {}\nlocals { x = var.<caret> }", "region");
        has("image.pkr.hcl", "variable \"region\" {}\nlocals { x = \"${var.<caret>}\" }", "region");
        has("main.tf", "locals { a = 1\n b = local.<caret> }", "a");
        has("image.pkr.hcl", "source \"amazon-ebs\" \"ubuntu\" {}\nbuild { sources = [\"source.amazon-ebs.<caret>\"] }", "ubuntu");
        has("image.pkr.hcl", "source \"amazon-ebs\" \"ubuntu\" {}\nbuild { source \"source.amazon-ebs.<caret>\" {} }", "ubuntu");
        has("job.nomad", "job \"web\" { type = \"<caret>\" }", "service", "batch", "system");
        has("job.nomad", "job \"web\" { group \"api\" { task \"server\" { driver = \"docker\"\nconfig { <caret> } } } }", "image", "ports");
        lacks("job.nomad", "job \"web\" { group \"api\" { task \"server\" { driver = \"exec\"\nconfig { <caret> } } } }", "image", "ports");
        has("job.nomad", "job \"web\" { group \"api\" { task \"server\" { driver = \"exec\"\nconfig { <caret> } } } }", "command", "args");
        empty("image.pkr.hcl", "locals { x = \"literal <caret>");
        empty("image.pkr.hcl", "locals { x = \"$${var.<caret>}\" }");
        has("image.pkr.hcl", "variable \"region\" {}\nlocals { x = \"${var.<caret>", "region");
        has("image.pkr.hcl", "variable \"region\" {}\nlocals { x = \"${format(\"%s\", var.<caret>)}\" }", "region");
        has("unknown.hcl", "<caret>", "job", "build", "resource");
        has("job.nomad", "variables { image = \"nginx\" }\njob \"web\" { meta { image = var.<caret> } }", "image");
        has("job.nomad", "variable \"image\" { <caret> }", "validation");
        lacks("job.nomad", "variable \"image\" { <caret> }", "sensitive");
        has("main.tf", "locals { value = <caret> }", "jsonencode", "true", "false");
        lacks("main.tf", "locals { value = var.<caret> }", "jsonencode", "true");
        has("main.tf", "variable \"example\" { type = <caret> }", "string", "list", "object");
        has("main.tf", "locals { value = [\n<caret>\n] }", "jsonencode");
        has("image.pkrvars.hcl", "variable \"region\" {}\n<caret>", "region");
        has("image.pkrvars.hcl", "region = <caret>", "jsonencode", "true");
        has("image.pkr.hcl", "build { provisioner \"shell\" { inline = [\"echo {}\"]\n<caret>", "scripts");
        HclDocument doc = new HclDocument("locals { a = 1\n a = 2\n nested = { a = 3 } }");
        check(doc.root.children.get(0).attributes.size() == 3, "Nested object has a separate attribute scope");
        check(doc.root.children.get(0).children.get(0).name.equals("nested"), "Object context");
        check(doc.root.children.get(0).children.get(0).object, "Object expressions are not block bodies");
        check(!doc.root.children.get(0).object, "Locals is a block body");
        String custom = "packer\tsource:custom\tendpoint\tattribute\tstring\tCustom endpoint.\thttps://example.com\t\n";
        Schema overlay = Schema.bundled().overlay(custom);
        HclDocument customDoc = new HclDocument("source \"custom\" \"x\" { }");
        check(overlay.members(Dialect.PACKER, customDoc.root.children.get(0)).stream().anyMatch(e -> e.name().equals("endpoint")), "Project schema overlay");
        boolean rejected = false;
        try { Schema.bundled().overlay("bad row"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Malformed schema rejected");
        for (Dialect dialect : Dialect.values()) {
            HclDocument root = new HclDocument("");
            for (Schema.Entry entry : Schema.bundled().members(dialect, root.root)) {
                check(!entry.insertion().isBlank(), "Nonempty template " + entry.name());
                check(entry.url().startsWith("https://"), "Documentation URL " + entry.name());
            }
        }
        checks += ProviderSchemaTest.run();
        System.out.println("Passed " + checks + " checks.");
    }
    private static Set<String> names(String filename, String marked) {
        int caret = marked.indexOf("<caret>");
        check(caret >= 0, "Fixture has caret");
        HclDocument doc = new HclDocument(marked.replace("<caret>", ""));
        Set<String> result = new HashSet<>();
        for (var s : CompletionEngine.complete(filename, doc, caret, Dialect.detect(filename, doc.text), Schema.bundled(), doc.symbols())) result.add(s.name());
        return result;
    }
    private static void has(String filename, String marked, String... expected) {
        Set<String> actual = names(filename, marked);
        for (String item : expected) check(actual.contains(item), "Expected " + item + " in " + marked + " but got " + actual);
    }
    private static void lacks(String filename, String marked, String... unexpected) {
        Set<String> actual = names(filename, marked);
        for (String item : unexpected) check(!actual.contains(item), "Unexpected " + item + " in " + marked);
    }
    private static void empty(String filename, String marked) { check(names(filename, marked).isEmpty(), "No suggestions in " + marked); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
