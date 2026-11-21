#!/bin/sh
# Compila o SeaBackground real do mod com stubs do Minecraft e renderiza PNGs.  Uso: tools/preview_java/run.sh <outDir> <tempoMs>...
set -e
D="$(cd "$(dirname "$0")" && pwd)"; R="$D/../.."
OUT="$1"; shift
rm -rf /tmp/seabg_cls && mkdir -p /tmp/seabg_cls
CL="$R/src/main/java/br/com/lasthorizon/akuma/client"
java -m jdk.compiler/com.sun.tools.javac.Main -d /tmp/seabg_cls $(find "$D/stubs" -name '*.java') "$CL/SeaBackground.java" "$CL/SeaMoods.java" "$D/Preview.java"
java -cp /tmp/seabg_cls Preview "$R/src/main/resources" "$OUT" "$@"
