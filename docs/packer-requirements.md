# Packer required-field templates

Templates start when you type `{` or press Enter in an empty supported block. Use **Alt+Enter → Insert missing required HCL fields** for an existing block and **Tab** to edit placeholders.

## Bundled coverage

| Block | Inserted inputs |
| --- | --- |
| `source "amazon-ebs"` | `ami_name`, `region`, `instance_type`, `source_ami` or existing `source_ami_filter`, communicator username |
| `source "docker"` | `image` or existing `build`, `commit = true` unless an output option exists |
| Docker `build` block | `path` |
| `source "qemu"`, `source "virtualbox-iso"` | `iso_url` or existing `iso_urls`, `iso_checksum`, communicator username |
| `provisioner "shell"`, `"powershell"` | `script` unless `inline` or `scripts` exists |
| `provisioner "shell-local"` | `script` unless `inline`, `scripts`, or `command` exists |
| `provisioner "file"` | `source` unless `sources` or `content` exists, plus `destination` |
| `provisioner "ansible"` | `playbook_file` |
| `post-processor "shell-local"` | `script` unless `inline`, `scripts`, or `command` exists; supports post-processor sequences |

The bundled [rule file](../src/main/resources/packer-required-fields.json) links each definition to its official HashiCorp reference. Manifest post-processors have no required configuration to insert. Unknown plugin types are left unchanged unless the project supplies rules.

For builders using the standard communicator, absent `communicator` defaults to SSH. Explicit `ssh` inserts `ssh_username`; `winrm` inserts `winrm_username`; `none` inserts neither. Expression-valued communicator settings are not evaluated. Docker uses its own communicator.

Templates provide editable starting points, not a complete validated build. They do not insert cloud credentials, overwrite existing values, evaluate variables, or resolve inherited build-source configuration. A present alternative is preserved even if its value is incomplete or invalid. Run `packer validate` after completing the values.

## Project-defined requirements

Add `.hcl-assist.packer.json` in the module directory or IntelliJ project root to define third-party builders, provisioners, or post-processors. The nearest ancestor rule file is used, with the project root as a fallback. Definitions replace the bundled rules for the same context; unrelated contexts are retained.

```json
{
  "version": 1,
  "blocks": {
    "source:custom-cloud": {
      "url": "https://example.org/custom-cloud/configuration",
      "fields": [
        { "name": "endpoint" },
        { "name": "image_id", "unlessAny": ["image_filter"] },
        { "name": "replicas", "type": "number", "placeholder": "1" },
        {
          "name": "ssh_username",
          "when": { "field": "communicator", "equals": "ssh", "default": "ssh" }
        }
      ]
    }
  }
}
```

Context paths follow the completion schema: `source:builder`, `build/provisioner:type`, `build/post-processor:type`, or `build/post-processors/post-processor:type`. Nested block contexts such as `source:docker/build` can also define fields. Attribute object values do not trigger templates.

Each field name must be unique within a definition. Supported types are `string` (default), `number`, `bool`, `list`, `map`, and `expression`. `placeholder` is literal HCL insertion text. `unlessAny` skips insertion if any named attribute or child block exists. `when` compares a literal field value; its optional `default` applies only when the condition field is absent.

These rules control templates. Add matching entries to `.hcl-assist.tsv` for custom completion and documentation. Invalid rule files fall back to bundled requirements and report a warning in the IDE log. Files larger than 1 MB are ignored.
