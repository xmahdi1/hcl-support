package dev.hclassist.core;

import java.util.Locale;
import java.util.regex.Pattern;

public enum Dialect {
    TERRAFORM, PACKER, NOMAD, CONSUL, GENERIC;
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static Dialect detect(String filename, String text) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".tf") || name.endsWith(".tfvars") || name.endsWith(".tofu") || name.endsWith(".tofuvars")) return TERRAFORM;
        if (name.endsWith(".pkr.hcl") || name.endsWith(".pkrvars.hcl")) return PACKER;
        if (name.endsWith(".nomad") || name.endsWith(".nomad.hcl") || name.endsWith(".nomadvars.hcl")) return NOMAD;
        HclDocument doc = new HclDocument(text);
        for (HclDocument.Body b : doc.root.children) {
            if (b.name.equals("job")) return NOMAD;
            if (b.name.equals("packer") || b.name.equals("source") && b.labels.size() == 2 || b.name.equals("build")) return PACKER;
            if (b.name.equals("terraform") || b.name.equals("resource") || b.name.equals("provider")) return TERRAFORM;
        }
        if (name.contains("consul") || doc.root.attributes.stream().anyMatch(a ->
                java.util.Set.of("datacenter", "retry_join", "server", "bind_addr", "bootstrap_expect").contains(a.name()))) return CONSUL;
        return GENERIC;
    }
    public static boolean supported(String name) {
        return Pattern.compile("(?i).+\\.(hcl|tf|tfvars|tofu|tofuvars|nomad)").matcher(name).matches();
    }
    public static boolean variablesFile(String name) {
        return name.endsWith(".tfvars") || name.endsWith(".tofuvars") || name.endsWith(".pkrvars.hcl") || name.endsWith(".nomadvars.hcl");
    }
}
