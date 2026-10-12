#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
: "${IDEA_HOME:?Set IDEA_HOME to a Community IDE SDK with testFramework.jar}"
: "${HCL_PLUGIN_DIR:?Set HCL_PLUGIN_DIR to a compatible official Terraform and HCL plugin directory}"
test_dir="${HCL_TEST_SANDBOX:-$PWD/build/integration}"
test_libs="$PWD/build/test-libs"
mkdir -p "$test_libs"
if [[ ! -f "$test_libs/junit-4.13.2.jar" ]]; then
  curl -L --fail --silent --show-error https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar -o "$test_libs/junit-4.13.2.jar"
fi
if [[ ! -f "$test_libs/hamcrest-core-1.3.jar" ]]; then
  curl -L --fail --silent --show-error https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar -o "$test_libs/hamcrest-core-1.3.jar"
fi
if [[ ! -f "$test_libs/opentest4j-1.3.0.jar" ]]; then
  curl -L --fail --silent --show-error https://repo.maven.apache.org/maven2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar -o "$test_libs/opentest4j-1.3.0.jar"
fi
mkdir -p "$test_dir/classes" "$test_dir/plugins/hcl-assist/lib"
# Use the release ZIP so these tests exercise the artifact that users install.
find "$test_dir/plugins/hcl-assist/lib" -maxdepth 1 -name 'hcl-assist-*.jar' -delete
unzip -oq build/distributions/hcl-assist-0.2.2.zip -d "$test_dir/plugins"
mkdir -p "$test_dir/plugins/terraform"
cp -R "$HCL_PLUGIN_DIR/." "$test_dir/plugins/terraform/"
javac -proc:none --release 17 -cp "$test_libs/*:$IDEA_HOME/lib/*:$IDEA_HOME/plugins/junit/lib/*" -d "$test_dir/classes" src/integrationTest/java/dev/hclassist/IntegrationTest.java
mapfile -t module_options < <(python3 - "$IDEA_HOME/product-info.json" <<'PY'
import json, sys
from pathlib import Path
product = json.load(open(sys.argv[1]))
for option in product['launch'][0].get('additionalJvmArguments', []):
    if option.startswith(('--add-', '-D')):
        print(option.replace('$IDE_HOME', str(Path(sys.argv[1]).parent)))
PY
)
"$IDEA_HOME/jbr/bin/java" -ea -Xmx2g \
  "${module_options[@]}" \
  -Djna.boot.library.path="$IDEA_HOME/lib/jna/amd64" \
  -Djava.awt.headless=true \
  -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader \
  -Didea.home.path="$IDEA_HOME" \
  -Didea.config.path="$test_dir/config" \
  -Didea.system.path="$test_dir/system" \
  -Didea.plugins.path="$test_dir/plugins" \
  -Didea.log.path="$test_dir/log" \
  -Didea.is.unit.test=true \
  -cp "$test_dir/classes:$test_libs/*:$IDEA_HOME/lib/*:$IDEA_HOME/plugins/junit/lib/*" \
  org.junit.runner.JUnitCore dev.hclassist.IntegrationTest
