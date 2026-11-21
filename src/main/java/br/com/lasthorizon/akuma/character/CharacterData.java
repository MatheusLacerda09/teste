package br.com.lasthorizon.akuma.character;

import java.util.Arrays;
import java.util.Objects;

/**
 * Personagem criado por um jogador. Imutável. Todos os campos (menos nome e lema) são índices/valores de
 * {@link CharacterOptions}. O povo é sempre humano (não há campo de raça).
 */
public final class CharacterData {
    public final String name, motto;
    /** Apelido de aventureiro (opcional; vazio = sem epíteto). */
    public final String epithet;
    public final int skin, hair, eyes, hairStyle, faceStyle, outfit, outfitColor, gender;
    /** Barba e acessório (registros antigos, sem estes campos, ficam com 0 = nenhum). */
    public final int beard, accessory;
    /** Estilo dos olhos (registros antigos, sem este campo, ficam com 0 = o olho padrão). */
    public final int eyeStyle;
    /** Itens do rosto (faces com marca e adereços): máscara de bits com no máximo {@link CharacterOptions#ADORN_MAX} marcados (registros antigos: 0 = nenhum). */
    public final long adorn;
    /** Metal dos piercings: 0 prata, 1 dourado (registros antigos: 0). */
    public final int metal;
    /**
     * Metal dos piercings de cada orelha (esquerda e direita do personagem): 0 prata, 1 dourado. Cada orelha tem o seu, então uma pode ser
     * dourada e a outra prateada. Registros antigos tinham um metal só para as duas orelhas (e os mais antigos, o dos piercings do rosto):
     * ficam com esse mesmo metal nas duas, então a skin deles não muda.
     */
    public final int earMetalL, earMetalR;
    /** Cor do batom e do brilho labial (índice de {@link CharacterOptions#LIP}; registros antigos: 0 = clássico). */
    public final int lip;
    /** Orelhas dos piercings de orelha: 0 as duas, 1 só a esquerda, 2 só a direita (registros antigos: 0). */
    public final int earSide;
    /** Itens de cada orelha (esquerda e direita do personagem): máscaras de bits de {@link CharacterOptions#EAR_ITEMS} itens (registros antigos: convertidos dos bits 47 a 53 de {@link #adorn}). */
    public final int earL, earR;
    /** Cor da pintura (índice de {@link CharacterOptions#PAINT}; registros antigos: 0 = a cor original de cada item). */
    public final int paint;
    /** Faces novas ({@link CharacterOptions#FACEX_BASE}): máscara de {@link CharacterOptions#FACEX_ITEMS} bits (registros antigos: 0). */
    public final int faceX;
    /**
     * Altura em centésimos de bloco ({@link CharacterOptions#HEIGHT_MIN} a {@link CharacterOptions#HEIGHT_MAX}; a largura acompanha) e tamanho do busto em % do atual
     * (só no corpo feminino). Registros antigos: altura padrão e busto 100; os que guardavam a altura em % do padrão (80 a 140) são convertidos em {@link #fromInts}.
     */
    public final int height, bust;
    /** Raça (índice em {@link CharacterOptions#RACES}); por enquanto só 0 = Humana. Registros antigos: 0. */
    public final int race;
    /** Idade em anos (16 a 100) e mar natal. O índice v[11] permanece reservado para compatibilidade. */
    public final int age, sea;
    private final int[] traits;

    private CharacterData(String name, String motto, String epithet, int[] v) {
        this.name = name;
        this.epithet = epithet == null ? "" : epithet;
        this.motto = motto == null ? "" : motto;
        this.skin = v[0];
        this.hair = v[1];
        this.eyes = v[2];
        this.hairStyle = v[3];
        // Faces e adereços viraram uma lista só (máscara de bits): o estilo de rosto antigo (1 a 7) vira o bit dele e o campo fica em 0.
        final boolean oldFace = v[4] > 0 && v[4] < CharacterOptions.FACE_STYLES;
        this.faceStyle = oldFace ? 0 : v[4];
        this.outfit = v[5];
        this.outfitColor = v[6];
        // v[7] era a origem (removida): a posição continua reservada para registros antigos e novos serem iguais
        this.gender = v[8];
        this.age = v[9];
        this.sea = v[10];
        this.beard = v[18];
        this.accessory = v[19];
        this.eyeStyle = v[20];
        // A máscara é um long: v[21] guarda os bits 0 a 30, v[23] os bits 31 a 62 e v[27] o bit 63 (registros antigos não têm v[23] nem v[27]: 0).
        long mask = (v[21] & 0x7FFFFFFFL) | ((v[23] & 0xFFFFFFFFL) << 31) | ((long) (v[27] & 1) << 63);
        // Piercings de orelha antigos (bits 47 a 53, valendo para as duas orelhas ou só uma): viram as listas de cada orelha e saem da máscara.
        final long legacyEars = ((1L << CharacterOptions.EAR_COUNT) - 1) << CharacterOptions.EAR_BIT_BASE;
        final int legacy = (int) ((mask & legacyEars) >>> CharacterOptions.EAR_BIT_BASE);
        mask &= ~legacyEars;
        this.earL = v[28] | (legacy != 0 && v[25] != 2 ? legacy : 0);      // earSide: 0 as duas, 1 só a esquerda, 2 só a direita
        this.paint = v[30];
        this.faceX = v[31];
        this.height = v[32];
        // v[33] era a largura (removida: ela acompanha a altura): a posição continua reservada para registros antigos e novos serem iguais
        this.bust = v[34];
        this.race = v[35];
        this.earR = v[29] | (legacy != 0 && v[25] != 1 ? legacy : 0);
        this.adorn = oldFace ? mask | (1L << (CharacterOptions.FACE_BIT_BASE + v[4])) : mask;
        this.metal = v[22];
        this.lip = v[24];
        this.earSide = v[25];
        this.earMetalL = v[EAR_METAL_L_INDEX];
        this.earMetalR = v[EAR_METAL_R_INDEX];
        this.traits = Arrays.copyOfRange(v, 12, 12 + CharacterOptions.TRAITS);
    }

    /** Posição (0 a 4) do eixo de personalidade {@code i}. */
    public int trait(int i) {
        return traits[i];
    }

    /** Valores iniciais: aparência padrão (careca, traje de partida), 25 anos, mar do Leste e personalidade equilibrada. */
    private static int[] defaultInts() {
        return new int[]{1, 1, 1, CharacterOptions.HAIR_STYLE_DEFAULT, 0, CharacterOptions.OUTFIT_DEFAULT, 6, 0, 0, CharacterOptions.AGE_DEFAULT, 0, 0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                CharacterOptions.HEIGHT_DEFAULT, 0, CharacterOptions.BUST_DEFAULT, 0, 0};
    }

    /** Valores iniciais da tela de criação. */
    public static CharacterData defaults() {
        return new CharacterData("", "", "", defaultInts());
    }

    /** Todos os índices dentro das faixas e o nome/lema válidos? (usado pelo servidor antes de aceitar qualquer coisa) */
    public boolean isValid() {
        return CharacterOptions.validName(name) && CharacterOptions.validMotto(motto) && CharacterOptions.validEpithet(epithet) && indexesValid();
    }

    /** Só as escolhas numéricas dentro das faixas (sem olhar o nome nem o lema): serve para conferir um rascunho. */
    public boolean indexesValid() {
        for (int i = 0; i < CharacterOptions.TRAITS; i++) if (!in(traits[i], CharacterOptions.TRAIT_STEPS)) return false;
        if (race < 0 || race >= CharacterOptions.RACES) return false;
        if (height < CharacterOptions.HEIGHT_MIN || height > CharacterOptions.HEIGHT_MAX || bust < CharacterOptions.BUST_MIN || bust > CharacterOptions.BUST_MAX) return false;
        return in(skin, CharacterOptions.SKIN.length) && in(hair, CharacterOptions.HAIR.length)
                && in(eyes, CharacterOptions.EYES.length) && in(hairStyle, CharacterOptions.HAIR_STYLES)
                && in(faceStyle, CharacterOptions.FACE_STYLES) && in(outfit, CharacterOptions.OUTFITS)
                && in(outfitColor, CharacterOptions.OUTFIT_COLOR.length)
                && in(gender, CharacterOptions.GENDERS) && in(sea, CharacterOptions.SEAS) && in(beard, CharacterOptions.BEARDS) && in(accessory, CharacterOptions.ACCESSORIES) && in(eyeStyle, CharacterOptions.EYE_STYLES) && CharacterOptions.validAdorn(adorn) && CharacterOptions.validEar(earL) && CharacterOptions.validEar(earR) && CharacterOptions.validFaceX(faceX) && CharacterOptions.itemCount(adorn, earL, earR, faceX) <= CharacterOptions.ADORN_MAX && in(paint, CharacterOptions.PAINTS) && in(metal, CharacterOptions.METALS) && in(earMetalL, CharacterOptions.METALS) && in(earMetalR, CharacterOptions.METALS) && in(lip, CharacterOptions.LIPS) && in(earSide, CharacterOptions.EAR_SIDES)
                && age >= CharacterOptions.AGE_MIN && age <= CharacterOptions.AGE_MAX;
    }

    private static boolean in(int v, int n) {
        return v >= 0 && v < n;
    }

    public CharacterData withName(String n) {
        return new CharacterData(n, motto, epithet, ints());
    }

    /** Campos numéricos na ordem fixa usada na rede e no NBT. */
    public int[] ints() {
        int[] v = new int[INT_COUNT];
        int[] head = {skin, hair, eyes, hairStyle, faceStyle, outfit, outfitColor, 0, gender, age, sea, 0}; // v[11] reservado para versões antigas
        v[18] = beard;
        v[19] = accessory;
        v[20] = eyeStyle;
        v[21] = (int) (adorn & 0x7FFFFFFFL);
        v[23] = (int) (adorn >>> 31);                             // bits 31 a 62 (o 63 vai em v[27])
        v[27] = (int) (adorn >>> 63);
        v[28] = earL;
        v[29] = earR;
        v[30] = paint;
        v[31] = faceX;
        v[32] = height;
        v[33] = 0;                                                // reservado (era a largura)
        v[34] = bust;
        v[35] = race;
        v[22] = metal;
        v[24] = lip;
        v[25] = earSide;
        v[EAR_METAL_L_INDEX] = earMetalL;
        v[EAR_METAL_R_INDEX] = earMetalR;
        System.arraycopy(head, 0, v, 0, head.length);
        System.arraycopy(traits, 0, v, head.length, CharacterOptions.TRAITS);
        return v;
    }

    public static final int INT_COUNT = 37;
    /** Posição do metal da orelha esquerda e da direita nos números (o metal dos piercings do rosto é a 22). A esquerda já era a 26 quando o metal valia para as duas; a direita veio depois, no fim. */
    private static final int EAR_METAL_L_INDEX = 26, EAR_METAL_R_INDEX = 36;

    /**
     * Monta o personagem a partir dos números. Aceita registros antigos (salvos antes do gênero, da idade, do mar,
     * ou da personalidade): o que falta recebe o valor inicial.
     */
    public static CharacterData fromInts(String name, String motto, int[] v) {
        return fromInts(name, motto, "", v);
    }

    /** Igual, com o epíteto (registros antigos não têm: ficam sem). */
    public static CharacterData fromInts(String name, String motto, String epithet, int[] v) {
        int[] full = defaultInts();
        System.arraycopy(v, 0, full, 0, Math.min(v.length, INT_COUNT));
        if (v.length <= EAR_METAL_L_INDEX) full[EAR_METAL_L_INDEX] = full[22];                  // registro anterior à escolha do metal das orelhas: elas continuam no metal que já tinham
        if (v.length <= EAR_METAL_R_INDEX) full[EAR_METAL_R_INDEX] = full[EAR_METAL_L_INDEX];   // registro anterior ao metal de cada orelha: a direita segue a esquerda (o metal era um só)
        full[HEIGHT_INDEX] = heightFromLegacy(full[HEIGHT_INDEX]);
        return new CharacterData(name, motto, epithet, full);
    }

    /** Posição da altura nos números. */
    private static final int HEIGHT_INDEX = 32;
    /** Menor valor da altura nos registros antigos (80 a 140, em % do padrão): de 80 a 149 era %, de 150 a 250 é em centésimos de bloco, então as duas faixas não se encostam. */
    private static final int LEGACY_HEIGHT_MIN = 80;

    /** Registros antigos guardavam a altura em % do personagem padrão (100 = 1,8 bloco): vira centésimos de bloco, preso na faixa nova. O que já está em bloco, ou fora das duas faixas (inválido), fica como está. */
    static int heightFromLegacy(int h) {
        if (h < LEGACY_HEIGHT_MIN || h >= CharacterOptions.HEIGHT_MIN) return h;
        return Math.max(CharacterOptions.HEIGHT_MIN, Math.min(CharacterOptions.HEIGHT_MAX, Math.round(h * (CharacterOptions.HEIGHT_DEFAULT / 100.0F))));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CharacterData d)) return false;
        return name.equals(d.name) && motto.equals(d.motto) && epithet.equals(d.epithet) && Arrays.equals(ints(), d.ints());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, motto, epithet, Arrays.hashCode(ints()));
    }
}
