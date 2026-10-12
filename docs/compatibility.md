# Compatibility

HCL Assist targets Java 17 and IntelliJ Platform builds **233 through 253**. The recommended release SDK is Community 2023.3.8, the oldest supported SDK.

## 0.2.2 regression checks

All 354 core checks and all 20 editor integration tests passed. Editor tests ran against the packaged 0.2.2 ZIP on Ultimate 2025.3.2. New cases cover Amazon EBS, QEMU, Docker and its nested build block, shell/file/Ansible provisioners, post-processor sequences, project-defined plugin requirements, and preservation of an existing AMI filter.

The archive targets Java 17 and was compiled using the locally available 2025.3.2 SDK. The older SDKs used for 0.2.0 were no longer present; this patch has not been separately verified on those IDEs.

## 0.2.1 regression checks

The core suite passes 282 checks, and all 10 editor integration tests pass on Ultimate 2025.3.2. The editor suite now includes four Packer VirtualBox ISO cases: opening-brace insertion, Enter insertion, preserving `iso_urls` with `communicator = "none"`, and WinRM username insertion. The 0.2.1 archive targets Java 17 and was compiled using the locally available 2025.3.2 SDK. The older SDKs used below were temporary downloads and were no longer present for this patch's verification.

## 0.2.0 verified releases

| IDE | Build | Source compilation | JetBrains Plugin Verifier | Editor tests |
| --- | --- | --- | --- | --- |
| IntelliJ IDEA Community 2023.3.8 | IC-233.15619.7 | Passed | Compatible | 6 passed |
| IntelliJ IDEA Community 2024.3.6 | IC-243.26574.91 | Passed | Compatible | Fixture startup failed |
| IntelliJ IDEA Ultimate 2025.3.2 | IU-253.30387.90 | Passed | Compatible | 6 passed |

Verification used Plugin Verifier 1.410 and compatible versions of **Terraform and HCL**. HCL Assist had no binary compatibility problems or deprecated API reports in this matrix. Intermediate releases fall within the declared range; they have not each been tested individually.

Binary verification checks API references and dependency resolution. It does not replace editor behavior tests or a manual installation check.

## Core tests

Both build commands run the core suite:

```sh
IDEA_HOME=/path/to/idea-IC-2023.3 ./build.sh
# or
./gradlew coreTest -PideaHome=/path/to/idea-IC-2023.3
```

The core suite passed 264 checks in 0.2.0, 282 in 0.2.1, and 354 in 0.2.2. The tests cover dialect detection, nested and incomplete HCL, completion, interpolation, custom schemas, provider schemas, required nested fields, repeated blocks, and preservation of existing values.

## Editor integration tests

The headless integration suite checks completion and required-field live templates using IntelliJ's test fixtures:

```sh
IDEA_HOME=/path/to/idea-IC-2023.3 \
HCL_PLUGIN_DIR=/path/to/compatible/terraform \
./scripts/integration-test.sh
```

The script requires Bash, Python 3, curl, and a JDK. On its first run it downloads JUnit, Hamcrest, and OpenTest4J into `build/test-libs`; these are test dependencies and are not included in the plugin. Build the release ZIP before running it.

The test sandbox is under `build/integration`. It uses its own configuration, system, and plugin directories. Set `HCL_TEST_SANDBOX` to an absolute path to use a separate sandbox for each IDE.

The original six tests cover Packer completion, Nomad driver filtering, insertion on opening a brace and pressing Enter, the intention's preservation of existing fields, and project provider-schema overrides. They passed on Community 2023.3.8 and Ultimate 2025.3.2.

Community 2024.3.6's headless fixture failed during application initialization with `InstanceNotRegisteredException: SettingsController`, before editor assertions could run. Its editor behavior is therefore unverified by this suite; its binary compatibility check passed. A manual installation check remains necessary for this release.

## Run Plugin Verifier

Download the verifier from its [official releases](https://github.com/JetBrains/intellij-plugin-verifier/releases). Create a text file with absolute paths to the HCL Assist ZIP and the compatible official Terraform plugin archives or directories. Providing dependencies in this list lets the verifier resolve them offline.

```text
/absolute/path/to/hcl-assist-0.2.0.zip
/absolute/path/to/terraform-233.zip
/absolute/path/to/terraform-243.zip
/absolute/path/to/terraform-253
```

```sh
java -jar verifier-cli-all.jar check-plugin @verification-plugins.txt \
  /path/to/idea-IC-2023.3 \
  /path/to/idea-IC-2024.3 \
  /path/to/idea-IU-2025.3 \
  -offline -verification-reports-dir build/verification
```

Read the reports under `*/plugins/dev.hclassist/0.2.0/`. Official dependency plugins are also checked when supplied as inputs and may have separate warnings or results for IDE versions outside their own supported ranges.

## Extending support

Before widening the range in `plugin.xml`, compile against the new oldest SDK, run the core and editor suites, and verify the release ZIP against the new targets. The mandatory HCL dependency must also have a compatible build for each target IDE.
