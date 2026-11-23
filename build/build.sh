#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

download() {
    local url="$1" checksum="$2"
    local file="build/dependencies/${url##*/}"
    if [[ ! -f "$file" ]] || ! sha256sum --check --status <<< "$checksum  $file" >/dev/null 2>&1; then
        curl --fail --silent --show-error --location --retry 3 "$url" --output "$file.part" || return
        sha256sum --check <<< "$checksum  $file.part" >&2 || return
        mv "$file.part" "$file" || return
    fi
    printf '%s\n' "$file"
}

rm -rf build/classes build/testclasses
mkdir -p build/classes build/dependencies build/dist build/testclasses

forge=$(download 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar' \
    00d1ca02192c7efb87da95552ebf247021f1e1d642f86d0a03076314def11529)
launchwrapper=$(download 'https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar' \
    57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f)
asm=$(download 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar' \
    09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff)
asm9=$(download 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.9/asm-9.9.jar' \
    03d99a74ad1ee5c71334ef67437f4ef4fe3488caa7c96d8645abc73c8e2017d4)
asm9tree=$(download 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-tree/9.9/asm-tree-9.9.jar' \
    42178f3775c9c63f9e5e1446747d29b4eca4d91bd6e75e5c43cfa372a47d38c6)
asm9analysis=$(download 'https://repo.maven.apache.org/maven2/org/ow2/asm/asm-analysis/9.9/asm-analysis-9.9.jar' \
    6a15d28e8bd29ba4fd5bca4baf9b50e8fba2d7b51fbf78cfa0c875a7214c678b)
tinker=$(download 'https://github.com/GTNewHorizons/TinkersConstruct/releases/download/1.14.123-GTNH/TConstruct-1.14.123-GTNH.jar' \
    7e9ecb65757a594b2dc320ec5921d37392c8dabf490cd83bd1720f45b717f1c2)
openblocks=$(download 'https://github.com/GTNewHorizons/OpenBlocks/releases/download/1.12.22-GTNH/OpenBlocks-1.12.22-GTNH.jar' \
    bf7dc845b023e72ea6df24b70557cd42d6f3df91c5bc978d2561c711c963cb52)
tinker291=$(download 'https://github.com/GTNewHorizons/TinkersConstruct/releases/download/1.14.124-GTNH/TConstruct-1.14.124-GTNH.jar' \
    b0bc14d2b5fec783c2743e1a39184b13d1da4a6c09a96a202258a6abb9751f1d)
openblocks291=$(download 'https://github.com/GTNewHorizons/OpenBlocks/releases/download/1.12.24-GTNH/OpenBlocks-1.12.24-GTNH.jar' \
    4ee8e56af38dd66757c273ae4aa73fea737bf65279f3ca28c8b7cf3a8738d767)

classpath="$forge:$launchwrapper:$asm"
mapfile -t sources < <(find src/main/java -name '*.java' | sort)
javac --release 8 -cp "$classpath" -d build/classes "${sources[@]}"

metadata=src/main/resources/mcmod.info
version=$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))[0]["version"])' "$metadata")
artifact="build/dist/NoDeathHeads-GTNH-$version.jar"
candidate=build/testclasses/mod.jar
cp "$metadata" build/classes/mcmod.info
printf 'nodeathheads.version=%s\n' "$version" > build/classes/version.properties
jar --create --file "$candidate" --manifest src/main/resources/META-INF/MANIFEST.MF -C build/classes .

javac --release 8 -cp "$candidate:$classpath" -d build/testclasses test/NoDeathHeadsTest.java
for asm_runtime in "$asm" "$asm9:$asm9tree:$asm9analysis"; do
    java -cp "$candidate:build/testclasses:$forge:$launchwrapper:$asm_runtime" dev.gtnh.nodeathheads.NoDeathHeadsTest \
        "$candidate" "$tinker" "$openblocks" "$tinker291" "$openblocks291"
done
mv "$candidate" "$artifact"
sha256sum "$artifact"
