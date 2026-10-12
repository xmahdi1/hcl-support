#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
: "${IDEA_HOME:?Set IDEA_HOME to an IntelliJ IDEA SDK installation}"
mkdir -p build/classes/java/main build/classes/java/test build/distributions build/package/hcl-assist/lib
find src/main/java -name '*.java' -print > build/main-sources.txt
javac -proc:none --release 17 -cp "$IDEA_HOME/lib/*" -d build/classes/java/main @build/main-sources.txt
cp -R src/main/resources/. build/classes/java/main/
cp LICENSE build/classes/java/main/META-INF/LICENSE
find src/test/java -name '*.java' -print > build/test-sources.txt
javac -proc:none --release 17 -cp "build/classes/java/main:$IDEA_HOME/lib/*" -d build/classes/java/test @build/test-sources.txt
java -ea -cp "build/classes/java/main:build/classes/java/test:$IDEA_HOME/lib/*" dev.hclassist.core.CoreTest
rm -f build/package/hcl-assist/lib/hcl-assist-*.jar
jar --create --file build/package/hcl-assist/lib/hcl-assist-0.2.2.jar -C build/classes/java/main .
# jar uses ZIP format; the descriptor and library layout match IntelliJ's plugin installer.
jar --create --no-manifest --file build/distributions/hcl-assist-0.2.2.zip -C build/package hcl-assist
printf 'Plugin: %s/build/distributions/hcl-assist-0.2.2.zip\n' "$PWD"
