#!/usr/bin/env bash
# Print jdeps transitive package closure for StoreApi backends (jvmumps#119–#123).
# Usage: tools/store_dep_closure.sh
set -euo pipefail
cd "$(dirname "$0")/.."
CP="build:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar"
mkdir -p build
javac --release 21 -cp "$CP" -d build $(find src/main/java -name "*.java") >/dev/null
echo "=== make depgraph (cycles) ==="
python3 tools/depgraph.py --build build --jdeps-out build/dep.dot --check
for cls in MStore SqlStore Cdb2Store StoreApi LockQueue; do
  echo
  echo "=== $cls ==="
  jdeps --multi-release 21 -verbose:package -cp "$CP" "build/jvmumps/${cls}.class" 2>/dev/null \
    | awk 'NF' | head -20
done
echo
echo "Transitive closure: MStore=java.base · SqlStore=java.base+java.sql(+sqlite-jdbc) · Cdb2Store=java.base + native cdb2pipe"
