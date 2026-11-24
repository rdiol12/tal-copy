#!/bin/bash
# Regenerates the Kotlin sources of the PocketFlow port from the Java sources in
# upstream/PocketFlow-Android, using the converter in tools/j2k. Needs a JDK 17+.
#
#   J2K_CLASSPATH=<platform classes>:<kotlin-stdlib.jar>:<annotations.jar> tools/port_pocketflow.sh
#
# J2K_CLASSPATH must contain the JVM build of the look-alikes in platform/ (the
# Java code is type-checked against them), the Kotlin standard library and the
# JetBrains annotations jar.
#
# Converted so far: the data layer (store, finance rules, analytics, tools).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/upstream/PocketFlow-Android/app/src/main/java/com/tal/pocketflow"
OUT="$ROOT/PocketFlow-iOS/shared/src/commonMain/kotlin/com/tal/pocketflow"
TEST_OUT="$ROOT/PocketFlow-iOS/shared/src/jvmTest/kotlin"
MAIN="${POCKETFLOW_FILES:-Finance ReportPeriod MovementFilter ChartCatalog Tools Store Analytics LauncherLayout}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
FILES=""
for name in $MAIN; do FILES="$FILES $SRC/$name.java"; done
# The stand-alone finance checks are converted too and run as a unit test.
"$ROOT/tools/j2k/convert.sh" "$WORK/out" $FILES "$ROOT/upstream/PocketFlow-Android/tests/FinanceTest.java"

find "$OUT" -maxdepth 1 -name '*.kt' -delete
mv "$WORK/out/FinanceTest.kt" "$TEST_OUT/FinanceTest.kt"
cp "$WORK"/out/*.kt "$OUT/"
echo "generated $(ls "$OUT"/*.kt | wc -l) files in ${OUT#$ROOT/}"
