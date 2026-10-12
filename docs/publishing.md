# Publishing HCL Assist

## Prepare the release

The project is licensed under MIT. Plugin metadata uses `xmahdi1` as the vendor and links to [xmahdi1/hcl-support](https://github.com/xmahdi1/hcl-support).

1. Publish the source to that repository, including `LICENSE`. Marketplace requires a source link when selecting an open-source license.
2. Set the same new version in `build.gradle`, `src/main/resources/META-INF/plugin.xml`, and the packaging scripts. Do not change `dev.hclassist` after the first Marketplace release.
3. Build against Community 2023.3:

   ```sh
   IDEA_HOME=/path/to/idea-IC-2023.3 ./build.sh
   ```

4. Run the compatibility matrix described in [compatibility.md](compatibility.md). Keep the supported build range tied to verified releases.
5. Install the ZIP in a clean IDE profile and check completion, required-field templates, documentation, schema refresh, and undo. Capture screenshots showing those features.

Current release artifact: `build/distributions/hcl-assist-0.2.2.zip`.

## Submit to JetBrains Marketplace

1. Sign in at [JetBrains Marketplace](https://plugins.jetbrains.com/).
2. Open your account menu and select **Upload plugin**.
3. Create or select your vendor profile and accept the Marketplace Developer Agreement if this is your first submission.
4. Upload `hcl-assist-0.2.2.zip`.
5. Select **MIT** as the license and provide `https://github.com/xmahdi1/hcl-support` as the source URL.
6. Set the issue tracker to `https://github.com/xmahdi1/hcl-support/issues`. Choose the relevant programming-language or infrastructure tags offered by the form.
7. Add screenshots of completion, required-field insertion, and documentation. The plugin icon is included in the archive.
8. Review the compatibility results, description, and change notes, then submit for review. Use a custom release channel if you want the first release to be a beta.

The plugin descriptor declares the dependency on **Terraform and HCL**. Marketplace and the IDE use this dependency and the build range when checking which installations can use the plugin.

For subsequent releases, increase the version, rebuild, repeat the checks, and upload the new ZIP from the plugin's versions page. Marketplace assigns the numeric plugin ID after the first upload.

## Suggested release notes for 0.2.2

- Required-field templates for Packer builders, provisioners, and shell-local post-processors.
- Conditional requirements and alternative inputs without overwriting existing values.
- Project-defined rules for third-party Packer plugins.

## Features introduced in 0.2.0

- Support for IntelliJ IDEA Community and Ultimate from 2023.3 through 2025.3.
- Required Terraform/OpenTofu resource fields inserted as editable live templates.
- Provider-specific attribute completion, documentation, and required/optional indicators.
- Provider schema refresh for initialized Terraform and OpenTofu modules.
- Contextual completion for Packer, Nomad, and Consul.

## References

- [Uploading a new plugin](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html)
- [Marketplace approval guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html)
- [Plugin listing guidelines](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html)
- [Plugin compatibility verification](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)
