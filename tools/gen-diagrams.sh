#!/bin/bash
# Generate PlantUML diagrams from mJVM Java source.
# Output: docs/diagrams/*.svg
set -e

DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$DIR/docs/diagrams"
mkdir -p "$OUT"

echo "=== Generating package dependency diagram ==="
cat > "$OUT/packages.puml" << 'PUML'
@startuml packages
!theme plain
skinparam packageStyle rectangle
skinparam linetype ortho

package "mjvm.cli" as cli
package "mjvm.compiler" as compiler
package "mjvm.runtime" as runtime
package "mjvm.ir" as ir
package "jvmumps" as jvmumps
package "jvmumps.store" as store

cli --> runtime
cli --> compiler
cli --> ir
cli --> jvmumps
compiler --> ir
compiler --> jvmumps
runtime --> ir
runtime --> jvmumps
store --> jvmumps
jvmumps --> jvmumps : internal
@enduml
PUML

echo "=== Generating MStore class diagram ==="
cat > "$OUT/mstore.puml" << 'PUML'
@startuml mstore
!theme plain
skinparam classAttributeIconSize 0

class MStore {
  - locals: ConcurrentHashMap<String, Node>
  - globals: ConcurrentHashMap<String, Node>
  - keyCache: ConcurrentHashMap<String, String[]>
  + get(path, global): MValue
  + set(path, global, v): void
  + order(path, global, dir): String
  + increment(path, global, amount): MValue
  + kill(path, global): void
  + data(path, global): int
  + queryNext(path, global, dir): String
}

class Node {
  - value: MValue
  - hasValue: boolean
  - kids: ConcurrentHashMap<String, Node>
  - sortedKeysCache: String[]
  - kidsDirty: boolean
  + kid(s): Node
  + markDirty(): void
  + sortedKeys(): String[]
}

class "StoreApi" as API {
  {interface} get()
  {interface} set()
  {interface} order()
  {interface} increment()
  {interface} kill()
  {interface} data()
  {interface} queryNext()
}

class "StoreCore" as Core {
  {abstract} get()
  {abstract} set()
  {abstract} increment()
  {abstract} kill()
}

MStore --> Node
MStore ..|> API
MStore ..|> Core
@enduml
PUML

echo "=== Generating IR instruction flow ==="
cat > "$OUT/ir-flow.puml" << 'PUML'
@startuml ir-flow
!theme plain
skinparam linetype ortho

rectangle "M Source" as src
rectangle "MFrontend" as fe
rectangle "IR (List<Ir>)" as ir
rectangle "IrMachine" as machine
rectangle "MRuntime" as rt
rectangle "MStore" as store

src --> fe : compileProgram()
fe --> ir : List<Ir>
ir --> machine : run()
machine --> rt : evalExpr()
rt --> store : get/set/order
@enduml
PUML

echo "=== Rendering SVG ==="
cd "$OUT"
java -jar "$DIR/lib/plantuml-1.2025.4.jar" -tsvg -Playout=smetana *.puml
echo "Diagrams written to $OUT/"
ls -la "$OUT"/*.svg
