#!/bin/sh
# Renderiza a tela de criação (CharacterMap de verdade, com stubs do Minecraft) num PNG, para conferir textos e layout sem abrir o jogo.
# Uso: tools/preview_java/face/run.sh <saida.png> <aba> <subaba corpo> <classe rosto> <metal> <lado orelha> "<bits marcados>" [escala] [mouseX mouseY]
#   abas: 0 Identidade 1 Corpo 2 Cabelo 3 Roupa 4 Alma 5 Passado.  Variáveis: LANG_=en_us, EARMETAL=0|1 (metal da orelha esquerda), EARMETALR=0|1 (da direita), EARL / EARR (itens de cada orelha, máscara de bits), EAREDIT=0|1 (orelha editada), HEIGHT=150..250 (altura em centésimos de bloco), BUST=0..100, GENDER=0|1, CONFIRM=1 (resumo final), SLEEP=ms.
set -e
D="$(cd "$(dirname "$0")" && pwd)"; R="$D/../../.."; J="$R/src/main/java/br/com/lasthorizon/akuma"; C="${TMPDIR:-/tmp}/lh_face_cls"
rm -rf "$C"; mkdir -p "$C"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$C" $(find "$D/../stubs" -name '*.java') $J/client/CharacterMap.java $J/client/MapArt.java $J/client/MapIcons.java $J/client/MapFont.java $J/client/IconMotion.java $J/client/IconAnim.java $J/client/SeaFrame.java $J/character/CharacterData.java $J/character/CharacterDraft.java $J/character/CharacterDestiny.java $J/character/CharacterOptions.java $J/character/SkinPainter.java $J/character/BodyShading.java $J/character/BodyScale.java $J/character/PxNoise.java $J/character/BaseSkin.java "$D/FaceShot.java"
OUT="$1"; shift
java -Dlang="${LANG_:-pt_br}" -Dres="$R/src/main/resources" -cp "$C:$R/src/main/resources" FaceShot "$R/src/main/resources" "$OUT" "$@"
