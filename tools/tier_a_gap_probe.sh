#!/usr/bin/env bash
# Repro for the two native-mJVM ENGINE GAPs (jvmumps#32).
#   GAP-1  numeric subscript canonicalisation on SET
#   GAP-2  $$ extrinsic dispatch after a tagged-routine prelude
# Details: docs/FILEMAN-TIER-A-GAPS.md
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
LIBS="lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar"

"$JAVA_HOME/bin/javac" --release 21 -cp "build:$LIBS" -d build \
  src/main/java/jvmumps/*.java src/main/java/mjvm/*/*.java \
  src/test/java/jvmumps/GapProbe.java

exec "$JAVA_HOME/bin/java" -cp "build:$LIBS" jvmumps.GapProbe
