#!/bin/sh
# Compila e roda os testes de lógica (Java puro, sem Minecraft).
set -e
D="$(cd "$(dirname "$0")" && pwd)"; R="$D/../.."; A="$R/src/main/java/br/com/lasthorizon/akuma/character"
OUT="${TMPDIR:-/tmp}/lh_tests_cls"; rm -rf "$OUT"; mkdir -p "$OUT"
java -m jdk.compiler/com.sun.tools.javac.Main -d "$OUT" "$A/CharacterData.java" "$A/CharacterDraft.java" "$A/CharacterDestiny.java" "$A/CharacterOptions.java" "$A/SkinPainter.java" "$A/BodyShading.java" "$A/PxNoise.java" "$A/BaseSkin.java" "$A/BustShape.java" "$A/BodyScale.java" "$D/CharacterLogicTest.java"
java -Dlh.root="$R" -cp "$OUT:$R/src/main/resources" CharacterLogicTest
