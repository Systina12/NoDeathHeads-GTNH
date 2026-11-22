#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/classes build/stubs/classes build/testclasses
python3 build/write_stubs.py
find build/stubs/src -name '*.java' > build/stubs-sources.txt
javac --release 8 -d build/stubs/classes @build/stubs-sources.txt
find src/main/java -name '*.java' > build/mod-sources.txt
javac --release 8 -cp build/stubs/classes -d build/classes @build/mod-sources.txt
javac --release 8 -cp build/stubs/classes:build/classes -d build/testclasses test/NoDeathHeadsTest.java
java -cp build/stubs/classes:build/classes:build/testclasses dev.gtnh.nodeathheads.NoDeathHeadsTest
python3 build/reobf_srg.py
cp src/main/resources/mcmod.info build/classes/mcmod.info
jar --create --file NoDeathHeads-GTNH-1.0.0.jar -C build/classes .
jar --list --file NoDeathHeads-GTNH-1.0.0.jar
