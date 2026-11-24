#!/bin/bash
# Regenerates the Kotlin sources of the PocketFlow port from the Java sources in
# upstream/PocketFlow-Android, using the converter in tools/j2k. Needs a JDK 17+.
#
#   tools/port_pocketflow.sh
#
# Converted so far: the data layer (store, finance rules, analytics, tools).
# The Activity and the custom views are not converted yet.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/upstream/PocketFlow-Android/app/src/main/java/com/tal/pocketflow"
OUT="$ROOT/PocketFlow-iOS/shared/src/commonMain/kotlin/com/tal/pocketflow"
TEST_OUT="$ROOT/PocketFlow-iOS/shared/src/jvmTest/kotlin"
MAIN="Finance ReportPeriod MovementFilter ChartCatalog Tools Store Analytics LauncherLayout"

STUBS=$(find "$ROOT/tools/j2k/stubs" -name '*.java' | sed 's/^/--stub=/')
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

FILES=""
for name in $MAIN; do FILES="$FILES $SRC/$name.java"; done
java "$ROOT/tools/j2k/J2K.java" "" "$WORK/main" $STUBS $FILES
# The stand-alone finance checks are converted too and run as a unit test.
java "$ROOT/tools/j2k/J2K.java" "" "$WORK/test" $STUBS $FILES "$ROOT/upstream/PocketFlow-Android/tests/FinanceTest.java"

find "$OUT" -maxdepth 1 -name '*.kt' -delete
cp "$WORK"/main/*.kt "$OUT/"
cp "$WORK/test/FinanceTest.kt" "$TEST_OUT/FinanceTest.kt"
echo "generated $(ls "$OUT"/*.kt | wc -l) files in ${OUT#$ROOT/}"
