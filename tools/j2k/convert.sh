#!/bin/bash
# Converts Java sources to Kotlin with J2K.java.
#   J2K_CLASSPATH=<platform classes>:<kotlin-stdlib.jar>:<annotations.jar> tools/j2k/convert.sh <output dir> <File.java>...
#
# The Java code is type-checked against the compiled Kotlin look-alikes in
# platform/ (android.*, org.json.*, compat.*), so those must be compiled for
# the JVM first and passed in J2K_CLASSPATH. java.io is redirected to compat.io
# before conversion because the Android look-alikes expose compat.io types.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$1"; shift
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
FILES=""
for f in "$@"; do
  # Keep the package directory layout so javac can resolve the sources.
  pkg=$(sed -n 's/^package \([a-zA-Z0-9_.]*\);.*/\1/p' "$f" | head -n 1 | tr . /)
  mkdir -p "$WORK/$pkg"
  sed -E 's/\bjava\.io\./compat.io./g' "$f" > "$WORK/$pkg/$(basename "$f")"
  FILES="$FILES $WORK/$pkg/$(basename "$f")"
done
java "$HERE/J2K.java" "${J2K_CLASSPATH:-}" "$OUT" $FILES
