#!/bin/sh
# Compila o CharacterMap real do mod com stubs do Minecraft e renderiza PNGs do mapa.
# Uso: tools/preview_java/run_map.sh <outDir> passo...   (passos em MapPreview.java; LANG=en_us troca o idioma)
set -e
D="$(cd "$(dirname "$0")" && pwd)"; R="$D/../.."
OUT="$1"; shift
rm -rf /tmp/book_cls && mkdir -p /tmp/book_cls
A="$R/src/main/java/br/com/lasthorizon/akuma"
java -m jdk.compiler/com.sun.tools.javac.Main -d /tmp/book_cls $(find "$D/stubs" -name '*.java') \
  "$A/client/CharacterMap.java" "$A/client/MapArt.java" "$A/client/MapIcons.java" "$A/client/MapFont.java" "$A/client/SeaFrame.java" "$A/client/IconMotion.java" \
  "$A/character/CharacterData.java" "$A/character/CharacterDraft.java" "$A/character/CharacterDestiny.java" "$A/character/CharacterOptions.java" "$A/character/SkinPainter.java" "$A/character/BodyShading.java" "$A/character/BodyScale.java" "$A/character/PxNoise.java" "$A/character/BaseSkin.java" "$D/MapPreview.java"
java -Dres="$R/src/main/resources" -Dlang="${LANG_CODE:-pt_br}" -cp "/tmp/book_cls:$R/src/main/resources" MapPreview "$OUT" "$@"
