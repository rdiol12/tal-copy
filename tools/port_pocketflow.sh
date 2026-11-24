#!/bin/bash
# Regenerates the Kotlin sources of the PocketFlow port from the Java sources in
# upstream/PocketFlow-Android, using the converter in tools/j2k. Needs a JDK 17+.
#
#   J2K_CLASSPATH=<classes>:<kotlin-stdlib.jar>:<annotations.jar> tools/port_pocketflow.sh
#
# The Java code is type-checked against compiled Kotlin, so <classes> must hold the
# JVM build of (1) the look-alikes in platform/ and (2) the hand-written files in
# PocketFlow-iOS/shared/src/commonMain/kotlin/com/tal/pocketflow/ios that the Java
# code refers to (R, AppLock, AndroidOnly, ReminderJob). The Kotlin standard library and the
# JetBrains annotations jar complete the class path.
#
# tools/pocketflow/patch_sources.py lists the few edits made to the Java first
# (Google sign-in and the Android job scheduler are taken out).
#
# Not converted: AppLock (rewritten for Face ID / passcode), the Google Drive
# classes, ReminderJob (local notifications) and the widgets (WidgetKit, Swift).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/upstream/PocketFlow-Android/app/src/main/java/com/tal/pocketflow"
OUT="$ROOT/PocketFlow-iOS/shared/src/commonMain/kotlin/com/tal/pocketflow"
TEST_OUT="$ROOT/PocketFlow-iOS/shared/src/jvmTest/kotlin"
MAIN="${POCKETFLOW_FILES:-Finance ReportPeriod MovementFilter ChartCatalog Tools Store Analytics LauncherLayout AppTheme BackupFiles BackupTransfer ExchangeRates ChartView SeriesChartView LauncherPager LauncherGrid MainActivity}"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
python3 "$ROOT/tools/pocketflow/patch_sources.py" "$SRC" "$WORK/java" $MAIN
FILES=""
for name in $MAIN; do FILES="$FILES $WORK/java/$name.java"; done
# The stand-alone finance checks are converted too and run as a unit test.
"$ROOT/tools/j2k/convert.sh" "$WORK/out" $FILES "$ROOT/upstream/PocketFlow-Android/tests/FinanceTest.java"

find "$OUT" -maxdepth 1 -name '*.kt' -delete
mv "$WORK/out/FinanceTest.kt" "$TEST_OUT/FinanceTest.kt"
cp "$WORK"/out/*.kt "$OUT/"
echo "generated $(ls "$OUT"/*.kt | wc -l) files in ${OUT#$ROOT/}"
