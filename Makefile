# JvMumps J0 — build + corpus runner (no Gradle required)
# Portable JDK: prefer $JAVA_HOME, else javac on PATH (Linux), else Homebrew (Mac).
ifeq ($(origin JAVA_HOME),environment)
  JAVAC := $(JAVA_HOME)/bin/javac
  JAVA  := $(JAVA_HOME)/bin/java
  JAVADOC := $(JAVA_HOME)/bin/javadoc
else ifneq ($(wildcard /opt/homebrew/opt/openjdk/bin/javac),)
  JAVA_HOME ?= /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
  JAVAC := $(JAVA_HOME)/bin/javac
  JAVA  := $(JAVA_HOME)/bin/java
  JAVADOC := $(JAVA_HOME)/bin/javadoc
else
  JAVAC := javac
  JAVA  := java
  JAVADOC := javadoc
endif
RELEASE ?= 21
OUT   := build
LIBS  := lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-nop.jar:lib/h2.jar:lib/abcl.jar:lib/abcl-contrib.jar:lib/mapdb.jar:lib/elsa.jar:lib/lz4-java.jar:lib/guava.jar:lib/kotlin-stdlib.jar:lib/eclipse-collections-api.jar:lib/eclipse-collections.jar
CP    := $(OUT):$(LIBS)
CORPUS ?= ../pymumps/tests/conformance.json
# FileMan Tier A (jvmumps#32) — env, never hardcoded in tests.
FILEMAN_CORPUS ?= $(HOME)/_fileman
export FILEMAN_CORPUS

# All *Smoke test mains (src/test/java/jvmumps/*Smoke.java). J0 takes CORPUS.
# Exclude SQLite/Comdb2 backend tests (frozen backends, #201)
EXCLUDE_SMOKE := Cdb2OrderParitySmoke J7LiveSmoke SqlCorpusSmoke SqlEngineSmoke SqlLockMeasureSmoke SqlLockMultiKeySmoke SqlLockStressSmoke OrderHangSmoke LimitProbeSmoke
SMOKE_CLASSES := $(filter-out $(EXCLUDE_SMOKE),$(patsubst src/test/java/jvmumps/%.java,%,$(wildcard src/test/java/jvmumps/*Smoke.java)))
SMOKE_NOARG   := $(filter-out J0Smoke,$(SMOKE_CLASSES))
# Integration tests removed (SqlStore frozen, #201)
ITEST_CLASSES :=

.PHONY: all smoke smokes-core itest run clean depgraph javadoc

all: $(OUT)/jvmumps/CorpusRunner.class

$(OUT)/jvmumps/CorpusRunner.class: src/main/java/jvmumps/*.java src/main/java/jvmumps/store/*.java
	mkdir -p $(OUT)
	$(JAVAC) --release 21 -cp $(LIBS) -d $(OUT) src/main/java/jvmumps/*.java src/main/java/jvmumps/store/*.java

# Full unit/smoke suite — every *Smoke class (jvmumps#64).
smoke: all mjvm
	$(JAVAC) --release 21 -cp $(CP) -d $(OUT) src/test/java/jvmumps/*.java
	@set -e; \
	echo "SMOKE COUNT $(words $(SMOKE_CLASSES))"; \
	$(JAVA) -cp $(CP) jvmumps.J0Smoke $(CORPUS); \
	for c in $(SMOKE_NOARG); do \
	  echo "== $$c =="; \
	  $(JAVA) -cp $(CP) jvmumps.$$c $(CORPUS); \
	done; \
	echo "SMOKE ALL OK $(words $(SMOKE_CLASSES))"

# Fast subset (local loop) — does not replace smoke/CI.
smokes-core: all mjvm
	$(JAVAC) --release 21 -cp $(CP) -d $(OUT) src/test/java/jvmumps/*.java
	$(JAVA) -cp $(CP) jvmumps.J0Smoke $(CORPUS)
	$(JAVA) -cp $(CP) jvmumps.J1Smoke
	$(JAVA) -cp $(CP) jvmumps.J2Smoke
	$(JAVA) -cp $(CP) jvmumps.J6LockSmoke
	$(JAVA) -cp $(CP) jvmumps.JTxLockSemSmoke
	$(JAVA) -cp $(CP) jvmumps.J12SymLitSmoke
	$(JAVA) -cp $(CP) jvmumps.J7PersistSmoke

# Integration tests removed (SqlStore frozen, #201)
itest: all
	@echo "ITEST SKIP — SqlStore/Comdb2 frozen (#201)"
	$(JAVAC) --release 21 -cp $(CP) -d $(OUT) src/test/java/jvmumps/*.java
	@set -e; \
	for c in $(ITEST_CLASSES); do \
	  echo "== itest $$c =="; \
	  $(JAVA) -cp $(CP) jvmumps.$$c $(CORPUS); \
	done; \
	echo "ITEST OK $(words $(ITEST_CLASSES))"

depgraph: all
	@mkdir -p build
	python3 tools/depgraph.py --build build --jdeps-out build/dep.dot --check
	@echo "depgraph OK (no package cycles)"

javadoc: all
	$(JAVADOC) --release 21 -d docs/javadoc -cp $(CP) -quiet $$(find src/main/java -name '*.java')

run: all
	$(JAVA) -cp $(CP) jvmumps.CorpusRunner $(CORPUS)

.PHONY: all smoke run clean javadoc depgraph mjvm bench install uninstall man dist

mjvm: all
	$(JAVAC) --release 21 -cp $(CP) -d $(OUT) src/main/java/mjvm/*/*.java

bench: mjvm all
	$(JAVAC) --release 21 -cp $(CP) -d $(OUT) src/test/java/jvmumps/*.java
	@echo "=== MjvmBench ==="
	$(JAVA) -cp $(CP) mjvm.bench.MjvmBench 100000
	@echo "=== PerfMatrix ==="
	$(JAVA) -cp $(CP) mjvm.bench.PerfMatrix 50000
	@echo "=== Footprint ==="
	$(JAVA) -cp $(CP) mjvm.bench.FootprintBench 200 50000
	@echo "=== StoreLock ==="
	$(JAVA) -cp $(CP) jvmumps.StoreLockBench 2000 32
	@echo "=== LimitProbes ==="
	$(JAVA) -cp $(CP) jvmumps.LimitProbeSmoke

install:
	PREFIX=$(PREFIX) ./install.sh

uninstall:
	rm -f $(PREFIX)/bin/mjvm $(PREFIX)/share/man/man1/mjvm.1 $(PREFIX)/share/man/man7/jvmumps.7
	rm -rf $(PREFIX)/lib/mjvm

man:
	man ./man/mjvm.1

dist: mjvm
	mkdir -p dist/mjvm-0.1.0
	cp -R build lib bin man docs install.sh README.md CRITICAL_PATH.md dist/mjvm-0.1.0/
	cd dist && tar czf mjvm-0.1.0.tar.gz mjvm-0.1.0
	@echo "dist/mjvm-0.1.0.tar.gz"

clean:
	rm -rf $(OUT) dist
