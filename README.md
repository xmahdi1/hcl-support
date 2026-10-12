# HCL Assist

HCL editing assistance for IntelliJ IDEA Community and Ultimate, covering Terraform, OpenTofu, Packer, Nomad, and Consul.

HCL Assist extends JetBrains' **Terraform and HCL** plugin with contextual completion, provider-aware resource templates, and documentation. The official plugin provides syntax highlighting, parsing, and formatting.

## Features

- Block, attribute, and function completion based on the surrounding configuration.
- Required Terraform resource and Packer block fields inserted as live templates, with Tab navigation between values.
- Required and optional provider attributes shown in completion, with documentation and deprecation indicators.
- Packer builder and provisioner suggestions, including source references.
- Nomad job, group, task, and driver-specific configuration suggestions.
- Consul agent, service, ACL, TLS, and networking configuration suggestions.
- Navigation to variable, local, resource, module, and Packer source declarations in the same directory.
- Duplicate attribute inspection.
- Custom schemas and dialect overrides for project-specific HCL formats.

## Installation

1. Install **Terraform and HCL** from the IntelliJ plugin settings.
2. Build `hcl-assist-0.2.2.zip` locally, or download it from a published project release.
3. Open **Settings → Plugins → gear → Install Plugin from Disk** and select the ZIP.

Supported IntelliJ versions: **2023.3 through 2025.3**. Version 0.2.0 passed binary verification against Community 2023.3.8, Community 2024.3.6, and Ultimate 2025.3.2. Version 0.2.2 passed editor tests on Ultimate 2025.3.2. See [compatibility results](docs/compatibility.md) for the exact test matrix.

## Usage

Completion opens while typing. Use **Ctrl+Space** to request suggestions, **Ctrl+Q** for documentation, and **Ctrl+B** to navigate to a declaration. Shortcuts depend on your keymap.

### Resource templates

Packer templates cover Amazon EBS, Docker, QEMU, and VirtualBox ISO sources; shell, shell-local, PowerShell, file, and Ansible provisioners; and shell-local post-processors. Docker's nested `build` block also inserts its Dockerfile path. Type an opening brace or press Enter in an empty body to insert fields. Use **Tab** to move between placeholders.

Templates preserve existing fields and respect alternatives such as `iso_urls`, `source_ami_filter`, Dockerfile builds, inline commands, and file content. SSH and WinRM usernames follow the communicator setting. For a choice of inputs, the template inserts one editable option rather than every alternative.

See [Packer requirement rules](docs/packer-requirements.md) for coverage and project-specific extensions.

When you open an empty Terraform resource, data source, or provider block, HCL Assist inserts the required fields reported by its provider schema. Press **Tab** to move between placeholders. Existing attributes and blocks are preserved.

For an existing block, place the cursor inside it and use **Alt+Enter → Insert missing required HCL fields**. Pressing Enter inside an empty block also starts the template.

Provider metadata comes from the official Terraform plugin. For schemas matching the providers installed in your module:

1. Run `terraform init` or `tofu init` in the module.
2. Open a configuration file in that module.
3. Select **Tools → HCL Assist → Refresh Provider Schema**.

The schema is saved as `.hcl-assist.providers.json` in the module directory and takes precedence over bundled metadata. You can also generate it directly:

```sh
terraform providers schema -json > .hcl-assist.providers.json
# OpenTofu:
tofu providers schema -json > .hcl-assist.providers.json
```

Inserted values are editable placeholders. Complete them before validating or applying the configuration. If no schema is available, HCL Assist leaves the block unchanged.

### Project configuration

Add `.hcl-assist.properties` at the IntelliJ project root:

```properties
# Optional default dialect
dialect=packer

# Overrides for paths relative to the project root
files.jobs/*.hcl=nomad
files.consul/*.hcl=consul
files.infrastructure/*.tf=terraform

# Optional settings
requiredFields.autoInsert=true
terraform.executable=terraform
```

Available dialects are `terraform`, `packer`, `nomad`, `consul`, and `generic`. OpenTofu uses the Terraform dialect. Set `terraform.executable=tofu` to use OpenTofu for the refresh action.

File patterns use Java glob syntax. Matching patterns override the default; overlapping patterns are evaluated in sorted order. Recognized file extensions and top-level blocks select a dialect automatically when no override is set.

### Custom schemas

Place `.hcl-assist.tsv` at the project root to add completion and documentation for custom builders, drivers, or HCL tools. Rows contain eight tab-separated fields:

| Field | Description |
| --- | --- |
| Dialect | `terraform`, `packer`, `nomad`, `consul`, `generic`, or `common` |
| Context | Block path, such as `source:amazon-ebs` or `build/provisioner:shell` |
| Name | Attribute, block, or function name |
| Kind | `attribute`, `block`, or `function` |
| Type | `string`, `number`, `bool`, `list`, `map`, `expression`, or `enum:value1,value2` |
| Description | Documentation shown in the editor |
| URL | Reference documentation URL |
| Template | Optional insertion text; supports `\n`, `\t`, and `$CARET` |

Use `root` for top-level entries and `expression` for functions. `:*` matches any first block label; `*` matches one path segment. Keep the final tab when the template field is empty.

See the [example schema](examples/.hcl-assist.tsv) and [bundled schemas](src/main/resources/schemas.tsv).

## Build

Requires JDK 21 and an installed IntelliJ IDEA SDK. Release builds use the oldest supported SDK, **Community 2023.3**, and target Java 17 bytecode.

```sh
export IDEA_HOME=/path/to/idea-IC-2023.3
./gradlew buildPlugin
```

The JDK-only build produces the same plugin layout:

```sh
IDEA_HOME=/path/to/idea-IC-2023.3 ./build.sh
```

Output: `build/distributions/hcl-assist-0.2.2.zip`.

See [compatibility testing](docs/compatibility.md) and [publishing](docs/publishing.md) for release checks and Marketplace submission.

## Scope

Provider schemas supply Terraform/OpenTofu resource fields. Packer, Nomad, and Consul use bundled schemas that can be extended per project. Unknown fields are allowed.

Declaration navigation stays within the current directory. Files larger than 512 KB, JSON HCL variants, and completion inside heredocs are outside the current scope.

## License

[MIT](LICENSE).
