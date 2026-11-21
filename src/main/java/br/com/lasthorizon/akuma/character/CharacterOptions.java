package br.com.lasthorizon.akuma.character;

/**
 * Opções da criação de personagem. Tudo é guardado como ÍNDICE (cores = posição nas paletas abaixo), então o
 * servidor valida só checando faixas e nunca confia numa cor ou texto vindo do cliente.
 * Java puro (sem classes do Minecraft): também roda fora do jogo, nos testes de pintura da skin.
 */
public final class CharacterOptions {
    private CharacterOptions() {}

    /**
     * Paletas. A ordem é a ordem dos índices salvos nos mundos: cores novas só entram NO FIM de cada lista, para o
     * personagem de quem já criou continuar igual. Os nomes (dica ao passar o mouse) ficam no lang:
     * {@code character.lasthorizon.color.<skin|hair|eyes|outfit>.<índice>}.
     */
    public static final int[] SKIN = {0xFFE0BD, 0xF1C27D, 0xE0AC69, 0xC68642, 0x8D5524, 0x6B4226, 0x4A2C1A, 0xFAD9C9,
            0xF3D2B3, 0xB5793F, 0x7B4B2A, 0x37200F};
    public static final int[] HAIR = {0x1B1B1B, 0x4A2F1A, 0x7A4A21, 0xB07A3A, 0xE3C16F, 0xC8452A, 0xD8D8D8, 0x2F6FDB, 0x2DB87A, 0xD23C8A,
            0x8C1C13, 0x7C7C88, 0x35B7C4, 0x7B4BC8};
    public static final int[] EYES = {0x2B2B2B, 0x5B3A1E, 0x2E7DD1, 0x3A9A5B, 0x7A7A7A, 0xB02E2E,
            0xD9A03A, 0x7A4BC8, 0x8FB8D8, 0x1F6F6A,
            0xE0257A, 0xE0702B, 0x9BD33F, 0xEDEDED, 0xE8C13A, 0x24408E};      // cores novas só entram no fim (índices antigos não mudam)
    public static final int[] OUTFIT_COLOR = {0xF2F2F2, 0xC0392B, 0x2E5FA8, 0x2F8F5B, 0x8E5BB5, 0xE0A030, 0x2B2B2B, 0x7A4A21,
            0x1F3A5F, 0xE07A9B, 0x2A8C8C, 0x6B6B2F};

    /** Quantidade de estilos de cabelo, rosto e roupa (nomes em lang: character.lasthorizon.hair_style.N etc.). A roupa 5 é o traje de partida (o padrão de quem começa); roupas novas só entram no fim. */
    public static final int HAIR_STYLES = 8, FACE_STYLES = 8, OUTFITS = 6;
    /** Estilos de olhos (nomes em lang: character.lasthorizon.eye_style.N). O 0 é o olho de sempre; estilos novos só entram no fim. Os desenhos estão em {@link SkinPainter#EYE_STYLE_GRID} (0 a 14) e {@link SkinPainter#EYE_STYLE_GRID_EXTRA} (15 em diante). */
    public static final int EYE_STYLES = 52;
    /**
     * Estilos de olhos que NÃO aparecem na grade da tela por serem parecidos com outros (o mais antigo de cada grupo fica):
     * Sonolentos (11), Miúdos (15), Traços Laterais (18), Emoldurados (21), Suaves (28), Doces (31), Delicados (40),
     * Clássicos (42), Amplos (43) e Cristalinos (48). Continuam existindo e desenhando igual, para o personagem de quem já
     * escolheu um deles não mudar; só deixam de ser oferecidos.
     */
    private static final int[] EYE_STYLES_HIDDEN = {11, 15, 18, 21, 28, 31, 40, 42, 43, 48};
    /** Ordem e conjunto dos estilos de olhos mostrados na tela (posição na grade -> valor guardado). */
    public static final int[] EYE_STYLE_ORDER = visibleEyeStyles();

    private static int[] visibleEyeStyles() {
        int[] tmp = new int[EYE_STYLES];
        int n = 0;
        for (int i = 0; i < EYE_STYLES; i++) {
            boolean hidden = false;
            for (int h : EYE_STYLES_HIDDEN) if (h == i) hidden = true;
            if (!hidden) tmp[n++] = i;
        }
        return java.util.Arrays.copyOf(tmp, n);
    }

    /** Roupa que vem escolhida na criação (traje de partida). */
    public static final int OUTFIT_DEFAULT = 5;
    /** Estilo de cabelo que vem escolhido na criação: 4 = careca. */
    public static final int HAIR_STYLE_DEFAULT = 4;
    /**
     * Ordem em que as opções aparecem nas grades da tela (primeira casa -> valor guardado). Só muda a exibição: os valores
     * guardados são os mesmos de sempre. Cabelo: careca (4) primeiro; roupa: traje de partida (5), a roupa de baixo, primeiro.
     */
    public static final int[] HAIR_STYLE_ORDER = {4, 0, 1, 2, 3, 5, 6, 7};
    public static final int[] OUTFIT_ORDER = {5, 0, 1, 2, 3, 4};
    /** Barba (0 nenhuma, 1 bigode, 2 cavanhaque, 3 barba cheia) e acessório (0 nenhum, 1 bandana, 2 chapéu de pirata, 3 tapa-olho, 4 óculos, 5 brinco). Estilos novos só entram no fim. */
    public static final int BEARDS = 4, ACCESSORIES = 6;

    /**
     * Itens do rosto (aba Corpo > Rosto): faces e adereços numa lista só; o jogador marca até {@link #ADORN_MAX} de
     * {@link #FACE_ITEMS}, de qualquer tipo. Guardados numa máscara de bits (bit k = item k), sempre pintados na textura da cabeça.
     * Bits 0 a 11 = adereços (nomes em lang: character.lasthorizon.adorn.K): 0 Piercing na sobrancelha, 1 Piercing no nariz,
     * 2 Argola no septo, 3 Piercing no lábio, 4 Batom, 5 Esparadrapo no nariz, 6 Curativo na bochecha, 7 Joia na testa,
     * 8 Lágrima pintada, 9 Bochechas coradas, 10 Pinta, 11 Purpurina.
     * Bits 12 a 18 = as faces com marca (estilo de rosto F de 1 a 7 = bit {@link #FACE_BIT_BASE} + F; nomes face_style.F): Determinado,
     * Cicatriz, Sardas, Marcas de expressão, Pintura facial, Cicatriz na bochecha, Olhar cansado. O estilo 0 (Padrão) é não marcar nada.
     * Itens novos só entram no fim (bits novos).
     */
    public static final int ADORNMENTS = 12, FACE_BIT_BASE = 11, FACE_ITEMS = 64, ADORN_MAX = 5;

    /**
     * Itens novos (bits 26 a 46, nomes em lang: extra.N com N = bit - 26). A máscara agora é um {@code long} (os 26 bits antigos não
     * guardam mais nada de novo): cicatrizes 26 Na sobrancelha, 27 No lábio, 28 No nariz, 29 Garras, 30 Na testa, 31 Queimadura,
     * 32 Pontos, 41 Corte recente; marcas 33 Marca de nascença, 34 Tatuagem tribal, 35 Pontos tatuados, 36 Pintura azul, 37 Estrela,
     * 38 Barba por fazer, 39 Olho roxo, 40 Fuligem; outros 42 Curativo na testa, 43 Sombra nos olhos, 44 Batom escuro, 45 Nariz corado,
     * 46 Brilho labial. Itens novos só entram no fim.
     */
    public static final int EXTRA_BIT_BASE = 26, EXTRA_COUNT = 28;

    /**
     * Piercings extras (bits 19 a 25, nomes em lang: pierce.N com N = bit - 19): 0 Sobrancelha esquerda, 1 Narina esquerda,
     * 2 Ponte do nariz, 3 Monroe (acima do lábio), 4 Labret (queixo), 5 Piercings nos cantos da boca, 6 Sobrancelha dupla.
     */
    public static final int PIERCE_BIT_BASE = 19, PIERCE_EXTRA = 7;

    /**
     * Piercings de orelha (bits 47 a 52, nomes em lang: extra.N com N = bit - 26, de 21 a 26): 47 Argola, 48 Pino no lóbulo, 49 Hélix (cartilagem),
     * 50 Tragus, 51 Orelha completa (lóbulo duplo, hélix e tragus), 52 Brinco pendente. São pintados nas laterais da cabeça, nas duas orelhas,
     * e têm o metal próprio de cada orelha (prata ou dourado, {@code earMetalL} e {@code earMetalR}: uma orelha pode ser dourada e a outra
     * prateada), separado do metal dos piercings do rosto.
     */
    public static final int EAR_BIT_BASE = 47, EAR_COUNT = 7;

    /**
     * Cicatrizes do CORPO (bits 54 a 61, nomes em lang: extra.N com N = bit - 26, de 28 a 35): 54 Cortes no peito, 55 Cortes no abdômen,
     * 56 Cortes nos braços, 57 Ralados nas pernas, 58 Ferida no ombro, 59 Pele cicatrizada, 60 Hematomas, 61 Cortes nas costas. Ao contrário
     * dos outros itens, são pintadas no tronco, nos braços e nas pernas (só em pele nua: nunca por cima de roupa), não no rosto.
     * O bit 61 é o último que cabe: a parte alta da máscara ({@code v[23]}) é um int positivo.
     */
    public static final int BODY_SCAR_BASE = 54, BODY_SCAR_COUNT = 8;
    /**
     * Queimaduras grandes do rosto (nomes em lang: extra.36 e extra.37): 62 Queimadura no olho, 63 Rosto queimado. O bit 63 é o sinal do
     * {@code long}, por isso a máscara pode ser negativa e é guardada em dois números ({@code v[23]} até o bit 62, {@code v[27]} o bit 63).
     * A máscara está CHEIA: um item novo precisa de um segundo campo.
     */
    public static final int BURN_EYE_BIT = 62, BURN_FACE_BIT = 63;
    public static boolean isBodyScar(int bit) {
        return bit >= BODY_SCAR_BASE && bit < BODY_SCAR_BASE + BODY_SCAR_COUNT;
    }
    /** Em quais orelhas os piercings de orelha aparecem (uma escolha só para todos eles): 0 as duas, 1 só a esquerda, 2 só a direita. O 53 é o Transversal (barra atravessando a cartilagem). */
    public static final int EAR_SIDES = 3;

    /**
     * Itens das orelhas: cada orelha tem a SUA lista (esquerda e direita do personagem, {@code earL} e {@code earR} em {@link CharacterData}),
     * que COMEÇAM do mesmo limite dos itens do rosto ({@link #ADORN_MAX}): conta a quantidade de itens do rosto mais os itens de orelha
     * diferentes (o mesmo item nas duas orelhas, um par de brincos, conta uma vez). Máscara de bits (bit k = item k, nomes em lang: ear_item.K):
     * 0 Argola, 1 Pino no lóbulo, 2 Hélix, 3 Tragus, 4 Orelha completa, 5 Brinco pendente, 6 Transversal, 7 Argola grande, 8 Pérola,
     * 9 Rubi, 10 Safira, 11 Cruz, 12 Argolas empilhadas, 13 Industrial, 14 Pena, 15 Dente de tubarão, 16 Alargador, 17 Concha.
     * Os bits 47 a 53 da máscara do rosto eram os 7 primeiros (valendo para as duas orelhas, ou só uma, conforme {@code earSide}):
     * personagens antigos são convertidos ao carregar (ver {@link CharacterData}).
     */
    public static final int EAR_ITEMS = 18;
    public static final int[] EAR_ORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
    public static boolean validEar(int mask) {
        return mask >= 0 && (mask >>> EAR_ITEMS) == 0;
    }
    /** Quantos itens o personagem usa no total: os do rosto mais os itens de orelha diferentes (um par igual nas duas orelhas conta uma vez). */
    public static int itemCount(long adorn, int earL, int earR, int faceX) {
        return Long.bitCount(adorn) + Integer.bitCount(earL | earR) + Integer.bitCount(faceX);
    }

    /**
     * Faces novas (a máscara principal está cheia, então ficam num campo próprio, {@code faceX} em {@link CharacterData}; nomes em lang: facex.N).
     * Na tela têm o número {@link #FACEX_BASE} + N (acima dos 64 bits da máscara). 0 Covinhas, 1 e 2 (sobrancelhas, retiradas: o número fica reservado),
     * 3 Bigodes de gato, 4 Sorriso, 5 Dente de ouro, 6 Sorriso torto, 7 Bochechas fundas, 8 Faixa de guerra, 9 Listra no olho, 10 Marca na testa,
     * 11 Raio, 12 Coração. As de 8 a 12 usam a cor da pintura. Contam no mesmo limite de {@link #ADORN_MAX} itens.
     */
    public static final int FACEX_BASE = 64, FACEX_ITEMS = 13;
    public static boolean validFaceX(int mask) {
        return mask >= 0 && (mask >>> FACEX_ITEMS) == 0;
    }

    /**
     * Cor da pintura (Pintura facial, Lágrima pintada e Pintura azul; nomes em lang: color.paint.N). O índice 0 é o \"original\": cada item
     * mantém a cor de sempre (vermelho, azul claro e azul), então quem já criou o personagem não muda. Cores novas só entram NO FIM.
     */
    public static final int[] PAINT = {0x8E8E9A, 0xC0392B, 0xE8801E, 0xF3C531, 0x2E9A4B, 0x2F6FDB, 0x37C2D8, 0x7B3FA0, 0xE0508C, 0xF2F2F2, 0x1E1E24};
    public static final int PAINTS = PAINT.length;
    /** Bits que usam a cor da pintura: Lágrima pintada (8), Pintura facial (16) e Pintura azul (36). */
    public static final int PAINT_BIT_TEAR = 8, PAINT_BIT_FACE = 16, PAINT_BIT_STRIPES = 36;
    public static boolean usesPaint(long mask, int faceX) {
        return (mask & ((1L << PAINT_BIT_TEAR) | (1L << PAINT_BIT_FACE) | (1L << PAINT_BIT_STRIPES))) != 0 || (faceX >>> 8) != 0;   // faces novas 8 a 12
    }

    /** Metal dos piercings (0 prata, 1 dourado). */
    public static final int METALS = 2;

    /**
     * Cor do batom e do brilho labial (nomes em lang: color.lip.N). O índice 0 é o \"clássico\": o batom vermelho de sempre e o brilho
     * transparente, então quem já criou o personagem não muda. Cores novas só entram NO FIM. Valem para o Batom (bit 4) e o Brilho labial (bit 46);
     * o Batom escuro (bit 44) tem a cor própria de vinho.
     */
    public static final int[] LIP = {0xB3202F, 0xE0607E, 0xE8735A, 0xC48A7A, 0x7A1F3D, 0xC2307E, 0x7B3FA0, 0x3A6FD0, 0x2E9A6B, 0x24161C};
    public static final int LIPS = LIP.length;
    /** Itens que usam a cor dos lábios (bits do Batom e do Brilho labial). */
    public static final int LIP_BIT_STICK = 4, LIP_BIT_GLOSS = 46;
    public static boolean usesLip(long mask) {
        return (mask & ((1L << LIP_BIT_STICK) | (1L << LIP_BIT_GLOSS))) != 0;
    }

    /**
     * Classes dos itens do rosto na tela (nomes em lang: item_class.C): 0 Faces, 1 Piercings (a cor do metal, prata ou dourado, vale para
     * todos eles), 2 Cicatrizes, 3 Marcas e tatuagens, 4 Outros, 5 Orelhas (com a própria escolha de metal), 6 Corpo (cicatrizes do corpo: não é uma aba pequena, é a página Corpo, ao lado de Pele, Olhos e Rosto). Cada linha lista os bits da classe, na ordem de exibição (no máximo 18 por
     * classe: três fileiras). Só a exibição: o que vale é a máscara.
     */
    public static final int ITEM_CLASSES = 6, ITEM_CLASS_BODY = 6;
    public static final int[][] ITEM_CLASS_ORDER = {
            {12, 14, 15, 16, 18, 64, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76},
            {0, 19, 25, 1, 20, 21, 2, 22, 3, 23, 24},
            {13, 17, 26, 27, 28, 29, 30, 31, 32, 41, 62, 63},
            {8, 10, 33, 34, 35, 36, 37, 39, 40},
            {4, 5, 6, 7, 9, 11, 38, 42, 43, 44, 45, 46},
            {},
            {54, 55, 56, 57, 58, 59, 60, 61}};                       // 6 Corpo: as cicatrizes do corpo                                                     // 5 Orelhas: usam a lista própria (EAR_ORDER), uma por orelha

    /** Máscara de itens do rosto válida: só bits conhecidos e no máximo {@link #ADORN_MAX} marcados. */
    public static boolean validAdorn(long mask) {
        return Long.bitCount(mask) <= ADORN_MAX;                  // os 64 bits da máscara são itens conhecidos
    }

    /**
     * Ordem dos itens na grade da tela (posição -> bit): as faces primeiro e depois os adereços. Só a exibição; os bits guardados
     * nunca mudam.
     */
    public static final int[] FACE_ITEM_ORDER = {12, 13, 14, 15, 16, 17, 18, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63};

    /** Gêneros: 0 masculino, 1 feminino (muda o modelo do corpo - braços finos - e detalhes do rosto). */
    public static final int GENDERS = 2;

    public static final int NAME_MIN = 2, NAME_MAX = 16;

    /** Idade permitida: de 16 a 100 anos. */
    public static final int AGE_MIN = 16, AGE_MAX = 100, AGE_DEFAULT = 25;
    /** Raças da criação de personagem (índice 0 = Humana). Só a primeira está disponível; as outras vagas aparecem trancadas na tela. */
    public static final int RACES = 1, RACE_SLOTS = 6;
    /**
     * Altura do personagem em centésimos de bloco: de 150 (1,5 bloco, o mínimo) a 250 (2,5 blocos, o máximo); 180 (1,8 bloco, a altura do jogador
     * do jogo) é o padrão e a escala 1. A largura não é uma escolha: acompanha a altura (o corpo inteiro cresce na mesma proporção, ver
     * {@link BodyScale}). Busto em % do tamanho atual (100 = o atual, o máximo; só no corpo feminino).
     */
    public static final int HEIGHT_MIN = 150, HEIGHT_MAX = 250, HEIGHT_DEFAULT = 180, BUST_MIN = 0, BUST_MAX = 100, BUST_DEFAULT = 100;
    /** Mares onde o personagem nasceu: 0 Leste, 1 Oeste, 2 Norte, 3 Sul, 4 Grand Line. */
    public static final int SEAS = 5;
    /** Personalidade: 6 eixos com 5 posições cada (0 = todo no lado esquerdo, 2 = equilíbrio, 4 = todo no direito). */
    public static final int TRAITS = 6, TRAIT_STEPS = 5, TRAIT_MID = 2;
    /** Chance (em %) de uma rolagem da alma dar o "jackpot": os seis traços no máximo (cada um num dos dois extremos). */
    public static final int JACKPOT_PERCENT = 3;

    /** Sorteia os seis traços. Com chance pequena ({@link #JACKPOT_PERCENT}) todos saem no máximo, cada eixo num lado aleatório. */
    public static int[] rollTraits(java.util.Random r) {
        int[] t = new int[TRAITS];
        boolean jackpot = r.nextInt(100) < JACKPOT_PERCENT;
        for (int i = 0; i < TRAITS; i++) t[i] = jackpot ? (r.nextBoolean() ? 0 : TRAIT_STEPS - 1) : r.nextInt(TRAIT_STEPS);
        return t;
    }
    /** Lema (frase de efeito, opcional). */
    public static final int MOTTO_MAX = 48;
    public static final int EPITHET_MAX = 24;

    /**
     * Nomes para o botão de nome aleatório (todos servem em {@link #validName}). São nomes comuns, sem personagens
     * conhecidos, para o jogador usar como ponto de partida e mudar à vontade.
     */
    public static final String[] RANDOM_NAMES = {
            "Kaito", "Mara Vento", "Bento", "Salema", "Rafael Maré", "Tomás", "Iara", "Dante Costa", "Nara", "Otto",
            "Lúcio", "Vera Corsária", "Haruki", "Sora", "Ren", "Akemi", "Takeo", "Mizuki", "Kenta", "Yuna",
            "Joaquim", "Cecília", "Bruno Âncora", "Lia", "Teo", "Marina", "Caio", "Aurora", "Nilo", "Pérola",
            "Ícaro", "Dandara", "Ravi", "Selene", "Gael", "Maya", "Zeca", "Lara Brisa", "Hugo", "Naomi",
            "Fenix", "Isis", "Kiro", "Luna", "Orion", "Tide", "Basílio", "Cora", "Davi Sal", "Elisa"};

    /** Um nome do sorteio, diferente de {@code current} quando possível (assim o botão sempre muda algo). */
    public static String randomName(java.util.Random r, String current) {
        String n = RANDOM_NAMES[r.nextInt(RANDOM_NAMES.length)];
        if (n.equals(current)) n = RANDOM_NAMES[(java.util.Arrays.asList(RANDOM_NAMES).indexOf(n) + 1) % RANDOM_NAMES.length];
        return n;
    }

    /** Fase da vida pela idade: 0 jovem, 1 adulto, 2 calejado, 3 veterano. */
    public static int agePhase(int age) {
        return age < 25 ? 0 : age < 45 ? 1 : age < 65 ? 2 : 3;
    }

    /** Epíteto (apelido de aventureiro, opcional): até 24 caracteres, mesmas regras do lema. */
    public static boolean validEpithet(String e) {
        if (e == null || e.codePointCount(0, e.length()) > EPITHET_MAX) return false;
        return validMotto(e);
    }

    /** Lema válido: até 48 caracteres, sem caracteres de controle nem o § (códigos de cor do jogo). Pode ser vazio. */
    public static boolean validMotto(String m) {
        if (m == null) return false;
        if (m.codePointCount(0, m.length()) > MOTTO_MAX) return false;
        if (!m.equals(m.strip())) return false;
        for (int i = 0; i < m.length(); ) {
            int cp = m.codePointAt(i);
            if (Character.isISOControl(cp) || cp == 0xA7) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    /** Motivos pelos quais um nome não serve (para a tela explicar o problema em vez de só travar o botão). */
    public static final int NAME_OK = 0, NAME_EMPTY = 1, NAME_SHORT = 2, NAME_LONG = 3, NAME_EDGE_SPACE = 4, NAME_BAD_CHAR = 5;

    private static final String ALLOWED = "[\\p{L}\\p{N} '\\-]";

    /** Qual é o problema do nome (NAME_OK se não há). Mesmas regras de {@link #validName}. */
    public static int nameProblem(String n) {
        if (n == null || n.isEmpty()) return NAME_EMPTY;
        if (!n.equals(n.strip())) return NAME_EDGE_SPACE;
        if (!firstBadChar(n).isEmpty()) return NAME_BAD_CHAR;
        int len = n.codePointCount(0, n.length());
        if (len < NAME_MIN) return NAME_SHORT;
        if (len > NAME_MAX) return NAME_LONG;
        return NAME_OK;
    }

    /**
     * Mesma regra de {@link #ALLOWED} ({@code [\\p{L}\\p{N} '\\-]}), sem compilar uma regex por letra: o nome é validado
     * várias vezes por quadro na tela de criação. Letras = categorias L*, números = Nd, Nl e No.
     */
    private static boolean allowedChar(int cp) {
        if (cp == ' ' || cp == '\'' || cp == '-') return true;
        return switch (Character.getType(cp)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER,
                 Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true;
            default -> false;
        };
    }

    /** Primeiro caractere não permitido do nome ou "" se todos servem. */
    public static String firstBadChar(String n) {
        if (n == null) return "";
        for (int i = 0; i < n.length(); ) {
            int cp = n.codePointAt(i);
            if (!allowedChar(cp)) return new String(Character.toChars(cp));
            i += Character.charCount(cp);
        }
        return "";
    }

    /** Nome válido: 2 a 16 caracteres, só letras (com acento), números, espaço, apóstrofo e hífen; sem espaço nas pontas. */
    public static boolean validName(String n) {
        return nameProblem(n) == NAME_OK;
    }
}
