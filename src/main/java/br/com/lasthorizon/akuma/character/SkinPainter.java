package br.com.lasthorizon.akuma.character;

/**
 * Monta a skin 64 x 64 (formato moderno; braços de 4 px "Steve" ou de 3 px "Alex" no feminino) de um {@link CharacterData}, pixel a pixel.
 * Devolve ARGB (0xAARRGGBB); pixels sem nada ficam com alfa 0 (as camadas externas do jogo são transparentes).
 *
 * <p>Java puro: não usa nada do Minecraft, então dá para rodar fora do jogo e ver o PNG.
 *
 * <p>Cada parte do corpo é uma caixa; a skin guarda as 6 faces dela lado a lado ({@link Part#rect}). As funções
 * pintam por face e coordenada local (0,0 = canto superior esquerdo da face, como se a face estivesse de frente
 * para quem olha). Nas faces laterais, o lado que encosta na FRENTE do corpo é o x maior na face RIGHT e o
 * x menor na face LEFT (é assim que a skin do Minecraft é desenhada).
 */
public final class SkinPainter {
    public static final int SIZE = 64;

    enum Face {TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK}

    /** Caixa na skin: canto (u,v), largura w, altura h e profundidade d. */
    record Part(int u, int v, int w, int h, int d) {
        int[] rect(Face f) {
            return switch (f) {
                case TOP -> new int[]{u + d, v, w, d};
                case BOTTOM -> new int[]{u + d + w, v, w, d};
                case RIGHT -> new int[]{u, v + d, d, h};
                case FRONT -> new int[]{u + d, v + d, w, h};
                case LEFT -> new int[]{u + d + w, v + d, d, h};
                case BACK -> new int[]{u + 2 * d + w, v + d, w, h};
            };
        }
    }

    // camada base
    static final Part HEAD = new Part(0, 0, 8, 8, 8), BODY = new Part(16, 16, 8, 12, 4),
            RLEG = new Part(0, 16, 4, 12, 4), LLEG = new Part(16, 48, 4, 12, 4);
    // camada externa (casaco, mangas, calças, chapéu)
    static final Part HAT = new Part(32, 0, 8, 8, 8), JACKET = new Part(16, 32, 8, 12, 4),
            RPANTS = new Part(0, 32, 4, 12, 4), LPANTS = new Part(0, 48, 4, 12, 4);

    /** Braços e mangas: 4 px de largura (modelo "default") ou 3 px (modelo "slim", usado no feminino). */
    final Part RARM, LARM, RSLEEVE, LSLEEVE;
    private final boolean female;
    /** Braços de 3 px (modelo "slim")? Vem do formato do PNG base, não do gênero (ver {@link BaseSkin#slim}). */
    private final boolean slim;

    private static final Face[] SIDES = {Face.RIGHT, Face.FRONT, Face.LEFT, Face.BACK};
    private static final Face[] ALL = Face.values();

    private final int[] px = new int[SIZE * SIZE];
    /** Pixels pintados como olho (a barba e as sobrancelhas respeitam). */
    private final boolean[] eyeMask = new boolean[SIZE * SIZE];
    private boolean protectEyes;
    /** Rosto 8 x 8 (índice y * 8 + x) onde as marcas novas NÃO pintam: pixels que são olho em qualquer fase do piscar ou do olhar. Só vale dentro de {@link #extraMarks}. */
    private boolean[] markZone;
    /** Fase do piscar: 0 olhos abertos, 1 meio fechados, 2 fechados (ver {@link #paintBlink} e {@link #paintHalf}). */
    private int blink;
    /** Para onde os olhos olham: x (-1 esquerda da tela, 0 frente, 1 direita) e y (-1 cima, 0 frente, 1 baixo). Ver {@link #shiftGaze}. */
    private int gazeX, gazeY;
    /** Cópia da skin só com o corpo nu e sombreado (antes das roupas). */
    private int[] base = new int[SIZE * SIZE];
    /** Pixels da roupa de baixo que já vem desenhada na skin base (cueca, top): as cicatrizes do corpo nunca pintam neles. Vazio quando a roupa é pintada por código. */
    private boolean[] underwear = new boolean[SIZE * SIZE];
    /** Cor escolhida para a pintura (Pintura facial, Lágrima pintada, Pintura azul); 0 = a cor original de cada uma. */
    private int paintRgb;

    private SkinPainter(boolean female) {
        this.female = female;
        this.slim = BaseSkin.slim(female);
        int aw = slim ? 3 : 4;
        RARM = new Part(40, 16, aw, 12, 4);
        LARM = new Part(32, 48, aw, 12, 4);
        RSLEEVE = new Part(40, 32, aw, 12, 4);
        LSLEEVE = new Part(48, 48, aw, 12, 4);
    }

    /** O modelo 3D deste personagem é o "slim" (braços de 3 px)? Tem de bater com o formato da skin que {@link #paint} gera. */
    public static boolean isSlim(CharacterData d) {
        return BaseSkin.slim(d.gender == 1);
    }

    /** Pinta a skin completa. */
    public static int[] paint(CharacterData d) {
        return paint(d, 0);
    }

    /** A mesma skin com os olhos fechados (a textura do piscar): o resto é idêntico, pixel por pixel. */
    public static int[] paintBlink(CharacterData d) {
        return paint(d, 2);
    }

    /** A mesma skin com os olhos meio fechados (entre abertos e fechados, na ida e na volta do piscar). */
    public static int[] paintHalf(CharacterData d) {
        return paint(d, 1);
    }

    private static int[] paint(CharacterData d, int blink) {
        return paint(d, blink, 0, 0);
    }

    /**
     * A skin com o piscar {@code blink} (0 aberto, 1 meio fechado, 2 fechado) e os olhos olhando para ({@code gx}, {@code gy}):
     * cada um de -1 a 1 (x: -1 esquerda de quem vê, 1 direita; y: -1 cima, 1 baixo). Só os olhos mudam, o resto é idêntico.
     */
    public static int[] paint(CharacterData d, int blink, int gx, int gy) {
        SkinPainter p = new SkinPainter(d.gender == 1);
        p.paintRgb = d.paint == 0 ? 0 : 0xFF000000 | CharacterOptions.PAINT[d.paint];
        p.blink = blink;
        p.gazeX = Integer.signum(gx);
        p.gazeY = Integer.signum(gy);
        int skin = 0xFF000000 | CharacterOptions.SKIN[d.skin];
        int hair = 0xFF000000 | CharacterOptions.HAIR[d.hair];
        int eyes = 0xFF000000 | CharacterOptions.EYES[d.eyes];
        int cloth = 0xFF000000 | CharacterOptions.OUTFIT_COLOR[d.outfitColor];
        int[] body = p.female ? BaseSkin.female(CharacterOptions.SKIN[d.skin], CharacterOptions.OUTFIT_COLOR[d.outfitColor])
                : BaseSkin.male(CharacterOptions.SKIN[d.skin], CharacterOptions.OUTFIT_COLOR[d.outfitColor]);
        if (body != null) {                                       // corpo desenhado à mão (masculino ou feminino), só recolorido (pele e roupa de baixo)
            p.customBody = true;
            p.underwear = BaseSkin.underwear(p.female);
            System.arraycopy(body, 0, p.px, 0, body.length);
        } else {                                                  // skin base ausente: corpo pintado por código
            for (Part b : new Part[]{HEAD, BODY, p.RARM, p.LARM, RLEG, LLEG}) p.all(b, skin);
            BodyShading.apply(p, p.female, skin);                // volume e músculos (sem manchas nem marcas)
        }
        p.base = p.px.clone();                                    // pele já sombreada: as roupas devolvem daqui o que deixam à mostra
        p.outfit(d.outfit, cloth, skin);
        p.fabricPass();                                           // volume, luz e dobras no tecido
        p.bodyScars(d.adorn);                                     // cicatrizes do corpo: só em pele nua, depois da roupa
        int[] before = p.px.clone();
        p.hair(d.hairStyle, hair);
        p.texturize(before, 3);                                   // mechas: tons claros e escuros ao longo do fio
        before = p.px.clone();
        p.fluff(d.hairStyle, hair);                               // volume: tufos na camada externa
        p.texturize(before, 5);
        p.face(d.adorn, d.faceX, d.eyeStyle, skin, hair, eyes);
        before = p.px.clone();
        p.protectEyes = true;                                     // barba e bigode nunca cobrem os olhos
        p.beard(d.beard, hair);
        p.protectEyes = false;
        p.texturize(before, 9);
        p.accessory(d.accessory, cloth);
        p.adornments(d.adorn, d.eyeStyle, eyes, d.metal, d.earMetalL, d.earMetalR, d.lip, d.earL, d.earR);
        return p.px;
    }

    // ------------------------------------------------------------------------------------------ roupas

    private void outfit(int style, int cloth, int skin) {
        final int dark = shade(cloth, 0.78), black = 0xFF2B2B2B, brown = 0xFF4A2F1A, boots = 0xFF3A2515,
                gold = 0xFFE0B040, white = 0xFFF2F2F2;
        switch (style) {
            case 0 -> { // camisa de pirata: camisa, cinto com fivela, calça escura e botas
                all(BODY, cloth);
                for (Part a : new Part[]{RARM, LARM}) {
                    all(a, cloth);
                    bareRows(a, 10, 11);                       // mãos de fora
                    bareFace(a, Face.BOTTOM);
                }
                rows(BODY, 9, 10, brown);
                set(BODY, Face.FRONT, 3, 9, gold); set(BODY, Face.FRONT, 4, 9, gold);
                set(BODY, Face.FRONT, 3, 10, gold); set(BODY, Face.FRONT, 4, 10, gold);
                for (int y = 0; y <= 2; y++) { bareSet(BODY, BODY, Face.FRONT, 3, y); bareSet(BODY, BODY, Face.FRONT, 4, y); }
                set(BODY, Face.FRONT, 2, 3, dark); set(BODY, Face.FRONT, 5, 3, dark);   // cordinha do colarinho
                for (Part l : new Part[]{RLEG, LLEG}) {
                    all(l, black);
                    rows(l, 8, 11, boots);
                    face(l, Face.BOTTOM, boots);
                }
            }
            case 1 -> { // colete aberto sobre o peito nu, faixa na cintura, bermuda e sandálias
                int sash = 0xFFC0392B;
                rows(BODY, 10, 11, sash);
                for (int y = 0; y <= 9; y++) {               // colete na camada externa: abre no meio
                    for (int x = 0; x < 8; x++) {
                        if (x == 3 || x == 4) continue;
                        set(JACKET, Face.FRONT, x, y, cloth);
                    }
                    for (int x = 0; x < 8; x++) set(JACKET, Face.BACK, x, y, cloth);
                    for (int x = 0; x < 4; x++) { set(JACKET, Face.RIGHT, x, y, cloth); set(JACKET, Face.LEFT, x, y, cloth); }
                }
                face(JACKET, Face.TOP, cloth);
                for (int y = 0; y <= 9; y++) { set(JACKET, Face.FRONT, 2, y, dark); set(JACKET, Face.FRONT, 5, y, dark); }
                for (Part l : new Part[]{RLEG, LLEG}) {
                    rows(l, 0, 5, 0xFF3B4A6B);
                    rows(l, 11, 11, boots);
                    face(l, Face.BOTTOM, boots);
                }
                for (Part a : new Part[]{RARM, LARM}) rows(a, 0, 1, dark);   // ombreira do colete
            }
            case 2 -> { // marinheiro: listras, gola, lenço vermelho, calça branca
                for (int y = 0; y < 12; y++) {
                    int c = (y % 2 == 0) ? white : cloth;
                    rowsOf(BODY, y, c);
                }
                for (Part a : new Part[]{RARM, LARM}) {
                    all(a, white);
                    bareRows(a, 10, 11);
                    bareFace(a, Face.BOTTOM);
                    rows(a, 7, 7, cloth);                      // punho listrado
                }
                for (int x = 0; x < 8; x++) { set(BODY, Face.FRONT, x, 0, cloth); set(BODY, Face.FRONT, x, 1, cloth); }
                bareSet(BODY, BODY, Face.FRONT, 3, 0); bareSet(BODY, BODY, Face.FRONT, 4, 0);
                set(BODY, Face.FRONT, 3, 1, 0xFFC0392B); set(BODY, Face.FRONT, 4, 1, 0xFFC0392B);
                set(BODY, Face.FRONT, 3, 2, 0xFFC0392B); set(BODY, Face.FRONT, 4, 2, 0xFFC0392B);
                for (Part l : new Part[]{RLEG, LLEG}) {
                    all(l, 0xFFEDEDED);
                    rows(l, 10, 11, 0xFF1B1B1B);
                    face(l, Face.BOTTOM, 0xFF1B1B1B);
                }
            }
            case 3 -> { // casaco longo: camisa escura por baixo, casaco na camada externa até o joelho
                all(BODY, black);
                for (Part a : new Part[]{RARM, LARM}) { all(a, black); bareRows(a, 10, 11); bareFace(a, Face.BOTTOM); }
                for (Part l : new Part[]{RLEG, LLEG}) {
                    all(l, black);
                    rows(l, 9, 11, 0xFF1B1B1B);
                    face(l, Face.BOTTOM, 0xFF1B1B1B);
                }
                for (Face f : ALL) face(JACKET, f, cloth);
                for (Part s : new Part[]{RSLEEVE, LSLEEVE}) {
                    for (Face f : SIDES) {
                        int[] r = s.rect(f);
                        fillRect(r[0], r[1], r[2], 10, dark);
                    }
                    face(s, Face.TOP, dark);
                }
                for (Part pnt : new Part[]{RPANTS, LPANTS}) {
                    for (Face f : SIDES) {
                        int[] r = pnt.rect(f);
                        fillRect(r[0], r[1], r[2], 6, cloth);   // saia do casaco
                    }
                }
                for (int y = 0; y < 12; y++) { set(JACKET, Face.FRONT, 3, y, dark); set(JACKET, Face.FRONT, 4, y, dark); }
                for (int y : new int[]{2, 5, 8}) set(JACKET, Face.FRONT, 3, y, gold);
                for (int x = 0; x < 8; x++) set(JACKET, Face.FRONT, x, 0, shade(cloth, 0.62));   // gola
                bareSet(JACKET, BODY, Face.FRONT, 3, 0); bareSet(JACKET, BODY, Face.FRONT, 4, 0);
                rows(JACKET, 9, 9, shade(cloth, 0.62));
            }
            case 5 -> starter(cloth);                    // traje de partida: peças simples e discretas
            default -> { // casual: camiseta de manga curta, calça jeans, tênis
                all(BODY, cloth);
                for (Part a : new Part[]{RARM, LARM}) rows(a, 0, 3, cloth);
                for (Part a : new Part[]{RARM, LARM}) face(a, Face.TOP, cloth);
                for (int x = 3; x <= 4; x++) bareSet(BODY, BODY, Face.FRONT, x, 0);
                for (Part l : new Part[]{RLEG, LLEG}) {
                    all(l, 0xFF3B5B8C);
                    rows(l, 10, 11, 0xFFEDEDED);
                    face(l, Face.BOTTOM, 0xFFEDEDED);
                }
            }
        }
    }

    /**
     * Traje de partida (estilo 5), o padrão de quem começa: peças simples na cor da roupa, sem estampa.
     * Feminino: top com alças e calcinha; masculino: calção até a metade da coxa. Só usa a camada base da skin.
     */
    private void starter(int cloth) {
        final int dark = clothTone(cloth, -2), mid = clothTone(cloth, -1), light = clothTone(cloth, 1);
        if (customBody) {
            // a roupa de baixo já vem na skin base (feminina ou masculina), recolorida na cor da roupa: nada a pintar
            starterMale = !female;
        } else if (female) {
            // top: duas copas, gargalo em V na frente, faixa que dá a volta e alças
            for (int x : new int[]{1, 2, 5, 6}) set(BODY, Face.FRONT, x, 2, x == 1 || x == 6 ? light : cloth);
            rows(BODY, 3, 3, cloth);
            for (int x : new int[]{3, 4}) { set(BODY, Face.FRONT, x, 3, dark); set(BODY, Face.BACK, x, 3, light); }
            set(BODY, Face.FRONT, 1, 3, mid); set(BODY, Face.FRONT, 6, 3, mid);          // sombra sob cada copa
            set(BODY, Face.RIGHT, 3, 2, mid); set(BODY, Face.LEFT, 0, 2, mid);          // lateral da copa
            for (int x : new int[]{2, 5}) {
                set(BODY, Face.FRONT, x, 0, mid); set(BODY, Face.FRONT, x, 1, mid);       // alças
                set(BODY, Face.BACK, x, 0, mid); set(BODY, Face.BACK, x, 1, mid); set(BODY, Face.BACK, x, 2, cloth);
            }
            face(BODY, Face.BOTTOM, cloth);
            // calcinha: cós, frente em V e atrás inteira
            rows(BODY, 10, 10, light);
            for (int x = 1; x <= 6; x++) set(BODY, Face.FRONT, x, 11, cloth);
            set(BODY, Face.FRONT, 3, 11, mid); set(BODY, Face.FRONT, 4, 11, mid);
            for (int x = 0; x < 8; x++) set(BODY, Face.BACK, x, 11, cloth);
            for (int x = 0; x < 4; x++) { set(BODY, Face.RIGHT, x, 11, cloth); set(BODY, Face.LEFT, x, 11, cloth); }
            for (int x = 2; x <= 3; x++) set(RLEG, Face.FRONT, x, 0, mid);
            for (int x = 0; x <= 1; x++) set(LLEG, Face.FRONT, x, 0, mid);
            for (Part l : new Part[]{RLEG, LLEG}) {
                for (int x = 0; x < 4; x++) set(l, Face.BACK, x, 0, cloth);
                face(l, Face.TOP, cloth);
            }
            for (int x = 0; x < 4; x++) { set(RLEG, Face.LEFT, x, 0, mid); set(LLEG, Face.RIGHT, x, 0, mid); }
            for (Part l : new Part[]{RLEG, LLEG}) shadeRows(l, 1, 1, 0.93);               // sombra da borda sobre a coxa
            shadeRows(BODY, 9, 9, 0.95);                                                   // e sob o top
        } else {
            // cueca justa de cinza escuro (como a da referência): cós, corpo com dobras claras e uma faixa preta na barra
            // cinza-escuro quase preto da referência (44,45,50 com sombra 33,29,34); outras cores seguem a escolha
            final int slate = blend(cloth, 0xFF2C2D32, lumOf(cloth) < 0.25 ? 0.9 : 0.0);
            final int body = slate, band = blend(slate, 0xFF000000, 0.55), top = clothTone(slate, 1);
            starterMale = true;
            rows(BODY, 10, 10, top);
            rows(BODY, 11, 11, body);
            face(BODY, Face.BOTTOM, body);
            for (Part l : new Part[]{RLEG, LLEG}) {
                rows(l, 0, 3, body);
                rows(l, 4, 4, band);                                                       // faixa preta da barra
                face(l, Face.TOP, body);
                shadeRows(l, 5, 5, 0.88);                                                  // sombra da barra sobre a perna
                shadeRows(l, 6, 6, 0.95);
            }
            shadeRows(BODY, 9, 9, 0.95);
        }
    }

    private boolean starterMale;
    /** O corpo veio da skin base desenhada à mão ({@link BaseSkin}) em vez de ser pintado por código. */
    private boolean customBody;

    private static double lumOf(int c) {
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0;
    }

    /** Escurece (k &lt; 1) as linhas r0..r1 das faces laterais de uma parte, na pele já pintada (só onde ainda é pele nua). */
    private void shadeRows(Part part, int r0, int r1, double k) {
        for (Face f : SIDES) {
            int[] r = part.rect(f);
            for (int y = r0; y <= r1 && y < r[3]; y++) for (int x = 0; x < r[2]; x++) {
                int idx = (r[1] + y) * SIZE + r[0] + x, c = px[idx];
                if ((c >>> 24) == 0 || c != base[idx]) continue;
                int n = (c & 0xFF000000) | (cl(((c >> 16) & 255) * k) << 16) | (cl(((c >> 8) & 255) * k * 0.98) << 8) | cl((c & 255) * k * 0.96);
                base[idx] = n;                                                              // continua sendo "pele nua" para o resto do pintor
                px[idx] = n;
            }
        }
    }

    // ------------------------------------------------------------------------------------------ cabelo

    private void hair(int style, int hair) {
        if (style == 4) return;                                   // careca
        final int side = shade(hair, 0.88), back = shade(hair, 0.80);
        // calota: topo, nuca e laterais altas
        face(HEAD, Face.TOP, hair);
        for (int x = 0; x < 8; x++) {
            set(HEAD, Face.FRONT, x, 0, hair);
            set(HEAD, Face.FRONT, x, 1, hair);
        }
        set(HEAD, Face.FRONT, 0, 2, hair); set(HEAD, Face.FRONT, 7, 2, hair);
        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x < 8; x++) { set(HEAD, Face.RIGHT, x, y, side); set(HEAD, Face.LEFT, x, y, side); }
        }
        int backRows = (style == 1) ? 7 : 4;                      // cabelo longo cobre a nuca inteira
        for (int y = 0; y <= backRows; y++) for (int x = 0; x < 8; x++) set(HEAD, Face.BACK, x, y, back);
        int sideRows = (style == 1) ? 7 : 4;
        for (int y = 3; y <= sideRows; y++) {                     // metade de trás das laterais
            for (int x = 0; x <= 3; x++) set(HEAD, Face.RIGHT, x, y, side);
            for (int x = 4; x <= 7; x++) set(HEAD, Face.LEFT, x, y, side);
        }
        switch (style) {
            case 1 -> {                                           // longo: mechas na frente e caindo nas costas
                for (int y = 2; y <= 4; y++) { set(HEAD, Face.FRONT, 0, y, hair); set(HEAD, Face.FRONT, 7, y, hair); }
                for (int y = 0; y <= 4; y++) for (int x = 1; x <= 6; x++) set(JACKET, Face.BACK, x, y, back);
                for (int y = 0; y <= 1; y++) { set(JACKET, Face.RIGHT, 3, y, side); set(JACKET, Face.LEFT, 0, y, side); }
            }
            case 2 -> {                                           // espetado: pontas na camada externa
                for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) if ((x + y) % 2 == 0) set(HAT, Face.TOP, x, y, hair);
                for (int x = 0; x < 8; x += 2) { set(HAT, Face.FRONT, x, 0, hair); set(HAT, Face.BACK, x + 1 > 7 ? x : x + 1, 0, back); }
                for (int x = 0; x < 8; x += 2) { set(HAT, Face.RIGHT, x, 0, side); set(HAT, Face.LEFT, x, 0, side); }
            }
            case 3 -> {                                           // franja: mecha caindo sobre a testa
                for (int x = 0; x <= 4; x++) set(HEAD, Face.FRONT, x, 2, hair);
                set(HEAD, Face.FRONT, 1, 3, hair); set(HEAD, Face.FRONT, 0, 3, hair);
                for (int x = 0; x < 8; x++) set(HAT, Face.FRONT, x, 0, hair);
            }
            case 5 -> {                                           // rabo de cavalo: preso no alto, cai nas costas
                for (int y = 0; y <= 7; y++) for (int x = 0; x < 8; x++) set(HEAD, Face.BACK, x, y, back);
                for (int y = 0; y <= 6; y++) for (int x = 3; x <= 4; x++) set(JACKET, Face.BACK, x, y, y % 3 == 2 ? shade(hair, 0.9) : back);
                for (int x = 3; x <= 4; x++) { set(HAT, Face.BACK, x, 1, hair); set(HAT, Face.BACK, x, 2, hair); }
                set(HAT, Face.BACK, 3, 3, 0xFFC0392B); set(HAT, Face.BACK, 4, 3, 0xFFC0392B);   // laço
            }
            case 6 -> {                                           // trança: uma trança comprida e fina atrás
                for (int y = 0; y <= 7; y++) for (int x = 0; x < 8; x++) set(HEAD, Face.BACK, x, y, back);
                for (int y = 0; y <= 10; y++) {
                    int c = y % 2 == 0 ? back : shade(hair, 0.68);
                    set(JACKET, Face.BACK, 3, y, c); set(JACKET, Face.BACK, 4, y, y % 2 == 0 ? shade(hair, 0.68) : back);
                }
                set(JACKET, Face.BACK, 3, 11, 0xFFC0392B); set(JACKET, Face.BACK, 4, 11, 0xFFC0392B);
            }
            case 7 -> {                                           // topete: cabelo alto e volumoso na frente
                for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) set(HAT, Face.TOP, x, y, hair);
                for (int x = 0; x < 8; x++) { set(HAT, Face.FRONT, x, 0, hair); set(HAT, Face.FRONT, x, 1, side); }
                for (int x = 1; x <= 6; x++) set(HAT, Face.FRONT, x, 2, back);
                for (int y = 0; y <= 1; y++) for (int x = 0; x < 8; x++) { set(HAT, Face.RIGHT, x, y, side); set(HAT, Face.LEFT, x, y, side); }
                for (int x = 0; x < 8; x++) set(HAT, Face.BACK, x, 0, back);
            }
            default -> { /* curto: só a calota */ }
        }
    }

    /**
     * Dá textura de fio aos pixels que acabaram de ser pintados (os que mudaram em relação a {@code before}): mechas
     * verticais mais claras e mais escuras da mesma cor, e um brilho em faixa no alto da cabeça.
     */
    private void texturize(int[] before, int seed) {
        for (Part part : new Part[]{HEAD, HAT, JACKET}) for (Face f : ALL) {
            int[] r = part.rect(f);
            for (int j = 0; j < r[3]; j++) for (int i = 0; i < r[2]; i++) {
                int idx = (r[1] + j) * SIZE + r[0] + i;
                if (px[idx] == before[idx] || (px[idx] >>> 24) == 0) continue;
                double[] q = pos(part, f, i, j);
                double n = f == Face.TOP
                        ? PxNoise.v3(q[0] * 2.0, q[2] * 0.45, 0, seed)                    // no topo os fios correm da frente para trás
                        : PxNoise.v3(q[0] * 2.0 + q[2] * 2.0, q[1] * 0.38, 0, seed);        // nos lados, de cima para baixo
                int lvl = (int) Math.round((n - 0.5) * 3.6);
                if (f == Face.TOP && Math.abs((q[0] - 1.2) - (3.4 - q[2]) * 0.9) < 0.95) lvl += 1;   // faixa de brilho
                px[idx] = clothTone(px[idx], Math.max(-2, Math.min(2, lvl)));
            }
        }
    }

    /**
     * Volume do cabelo: tufos soltos na camada externa (que o jogo desenha um pouco maior que a cabeça) e uma franja
     * irregular na testa. Os estilos espetado e topete já têm a camada externa própria.
     */
    private void fluff(int style, int hair) {
        if (style == 4) return;                                   // careca
        final int back = shade(hair, 0.80), side = shade(hair, 0.88);
        if (style != 2 && style != 7) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {                      // topo: manchas de tufo, mais cheio nas bordas
                boolean edge = x == 0 || x == 7 || y == 0 || y == 7;
                if (PxNoise.rnd(x, y, 1, 21) < (edge ? 0.66 : 0.36)) set(HAT, Face.TOP, x, y, hair);
            }
            for (int x = 0; x < 8; x++) if (PxNoise.rnd(x, 0, 2, 22) < 0.62) set(HAT, Face.FRONT, x, 0, hair);
            for (int x = 0; x < 8; x++) if (PxNoise.rnd(x, 1, 2, 23) < 0.28) set(HAT, Face.FRONT, x, 1, hair);
            int rows = (style == 1) ? 5 : 3;
            for (int y = 0; y < rows; y++) for (int x = 0; x < 8; x++) if (PxNoise.rnd(x, y, 3, 24) < 0.60 - y * 0.10) set(HAT, Face.BACK, x, y, back);
            for (int y = 0; y <= 1; y++) for (int x = 0; x < 8; x++) {
                if (PxNoise.rnd(x, y, 4, 25) < 0.55 - y * 0.18) set(HAT, Face.RIGHT, x, y, side);
                if (PxNoise.rnd(x, y, 5, 26) < 0.55 - y * 0.18) set(HAT, Face.LEFT, x, y, side);
            }
        }
        if (style != 3) {                                         // franja irregular: algumas mechas descem mais na testa
            for (int x = 1; x <= 6; x++) if (PxNoise.rnd(x, 2, 6, 27) > 0.55) set(HEAD, Face.FRONT, x, 2, hair);
        }
    }

    // ------------------------------------------------------------------------------------------ barba e acessórios

    /** Barba na cor do cabelo (0 nenhuma, 1 bigode, 2 cavanhaque, 3 barba cheia). */
    private void beard(int style, int hair) {
        if (style == 0) return;
        int b = shade(hair, 0.82);
        switch (style) {
            case 1 -> {                                           // bigode: dois traços ao lado do nariz
                for (int x : new int[]{1, 2, 5, 6}) set(HEAD, Face.FRONT, x, 5, b);
                set(HEAD, Face.FRONT, 2, 6, b); set(HEAD, Face.FRONT, 5, 6, b);
            }
            case 2 -> {                                           // cavanhaque: só embaixo da boca
                for (int x = 3; x <= 4; x++) { set(HEAD, Face.FRONT, x, 7, b); }
                set(HEAD, Face.FRONT, 2, 7, b); set(HEAD, Face.FRONT, 5, 7, b);
            }
            default -> {                                          // barba cheia: queixo, bochechas e laterais
                for (int x = 0; x < 8; x++) { set(HEAD, Face.FRONT, x, 7, b); if (x != 3 && x != 4) set(HEAD, Face.FRONT, x, 6, b); }   // a boca (3 e 4, linha 6) fica de fora
                for (int x : new int[]{0, 1, 6, 7}) set(HEAD, Face.FRONT, x, 5, b);
                for (int y = 5; y <= 7; y++) for (int x = 4; x <= 7; x++) set(HEAD, Face.RIGHT, x, y, b);
                for (int y = 5; y <= 7; y++) for (int x = 0; x <= 3; x++) set(HEAD, Face.LEFT, x, y, b);
                for (int x = 0; x < 8; x++) set(HEAD, Face.BOTTOM, x, 0, b);
            }
        }
    }

    /** Acessório (1 bandana na cor da roupa, 2 chapéu de pirata, 3 tapa-olho, 4 óculos, 5 brinco). */
    private void accessory(int kind, int cloth) {
        final int black = 0xFF1B1B1B, gold = 0xFFE0B040;
        switch (kind) {
            case 1 -> {                                           // bandana: faixa na testa e nó atrás
                int dk = shade(cloth, 0.75);
                for (int y = 1; y <= 2; y++) {
                    for (int x = 0; x < 8; x++) { set(HAT, Face.FRONT, x, y, y == 1 ? cloth : dk); set(HAT, Face.BACK, x, y, y == 1 ? cloth : dk); }
                    for (int x = 0; x < 8; x++) { set(HAT, Face.RIGHT, x, y, y == 1 ? cloth : dk); set(HAT, Face.LEFT, x, y, y == 1 ? cloth : dk); }
                }
                set(HAT, Face.BACK, 3, 3, cloth); set(HAT, Face.BACK, 4, 3, cloth); set(HAT, Face.BACK, 2, 4, dk); set(HAT, Face.BACK, 5, 4, dk);
            }
            case 2 -> {                                           // chapéu de pirata: bicórnio escuro com galão dourado
                for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) set(HAT, Face.TOP, x, y, black);
                for (Face f : SIDES) for (int x = 0; x < 8; x++) { set(HAT, f, x, 0, black); set(HAT, f, x, 1, f == Face.FRONT ? gold : black); }
                for (int x = 0; x < 8; x++) set(HAT, Face.BOTTOM, x, 0, shade(black, 1.4));
                for (Face f : new Face[]{Face.FRONT, Face.BACK}) { set(HAT, f, 0, 2, black); set(HAT, f, 7, 2, black); }
                set(HAT, Face.FRONT, 3, 0, 0xFFEDEDED); set(HAT, Face.FRONT, 4, 0, 0xFFEDEDED);   // caveirinha
            }
            case 3 -> {                                           // tapa-olho no olho direito, com tira
                for (int x = 0; x < 8; x++) set(HAT, Face.FRONT, x, 2, black);
                set(HAT, Face.RIGHT, 7, 2, black); set(HAT, Face.LEFT, 0, 2, black);
                for (int x = 5; x <= 6; x++) for (int y = 3; y <= 5; y++) set(HAT, Face.FRONT, x, y, black);
            }
            case 4 -> {                                           // óculos redondos de aro dourado
                for (int x : new int[]{1, 2, 5, 6}) { set(HAT, Face.FRONT, x, 3, gold); set(HAT, Face.FRONT, x, 5, gold); }
                for (int x : new int[]{0, 3, 4, 7}) set(HAT, Face.FRONT, x, 4, gold);
            }
            case 5 -> {                                           // brinco dourado nos dois lados
                set(HAT, Face.RIGHT, 4, 5, gold); set(HAT, Face.RIGHT, 4, 6, gold);
                set(HAT, Face.LEFT, 3, 5, gold); set(HAT, Face.LEFT, 3, 6, gold);
            }
            default -> { }
        }
    }

    /**
     * Adereços do rosto (bits 0 a 11 da máscara), pintados NA TEXTURA da cabeça (a camada de baixo, a mesma da pele): nada fica
     * saltado para fora do modelo. Só usam pixels que o desenho do olho escolhido não usa (a sobrancelha e a joia acompanham
     * {@link #eyeTop}) e nunca cobrem pixel de olho, então não dançam quando o olho anima. Pintam por cima da barba e do cabelo.
     */
    private void adornments(long mask, int eyeStyle, int iris, int metal, int earMetalL, int earMetalR, int lip, int earL, int earR) {
        final long extra = ((1L << CharacterOptions.PIERCE_EXTRA) - 1) << CharacterOptions.PIERCE_BIT_BASE;
        final long newOnes = (1L << (CharacterOptions.EXTRA_BIT_BASE + 16)) | (1L << (CharacterOptions.EXTRA_BIT_BASE + 18)) | (1L << (CharacterOptions.EXTRA_BIT_BASE + 20));
        if ((mask & (((1L << CharacterOptions.ADORNMENTS) - 1) | extra | newOnes)) == 0 && earL == 0 && earR == 0) return;
        // cor dos lábios (batom e brilho): índice 0 = clássico (vermelho de sempre; o brilho fica transparente)
        final int lipRgb = 0xFF000000 | CharacterOptions.LIP[Math.max(0, Math.min(CharacterOptions.LIPS - 1, lip))];
        final int lipLo = shade(lipRgb, 0.82);
        // metal dos piercings (escolha do jogador): prata ou dourado, cada um com um tom claro e um escuro
        final int silver = metalLt(metal), silverDk = metalDk(metal);
        final int gold = silver, goldDk = silverDk;
        // metal dos piercings de cada orelha: escolha própria de cada lado (a esquerda pode ser dourada e a direita prateada) e separada da dos piercings do rosto
        final int earLtL = metalLt(earMetalL), earDkL = metalDk(earMetalL), earLtR = metalLt(earMetalR), earDkR = metalDk(earMetalR);
        final int tr = eyeTop(eyeStyle, 1), tmin = Math.min(eyeTop(eyeStyle, 0), tr);
        final boolean prev = protectEyes;
        protectEyes = true;                                       // set() não mexe em pixel de olho
        for (int k = 0; k < CharacterOptions.ADORNMENTS; k++) {
            if ((mask & (1L << k)) == 0) continue;
            switch (k) {
                case 0 -> {                                       // piercing na ponta da sobrancelha direita
                    int row = Math.max(0, tr - 1);
                    set(HEAD, Face.FRONT, 6, row, silver);
                    if (row >= 1) set(HEAD, Face.FRONT, 6, row - 1, silverDk);
                }
                case 1 -> set(HEAD, Face.FRONT, 4, 5, silver);        // piercing na narina direita
                case 2 -> {                                       // argola no septo
                    set(HEAD, Face.FRONT, 3, 5, gold);
                    set(HEAD, Face.FRONT, 4, 5, goldDk);
                }
                case 3 -> set(HEAD, Face.FRONT, 4, 7, silver);        // piercing no lábio, abaixo da boca
                case 4 -> {                                       // batom: lábios vermelhos
                    set(HEAD, Face.FRONT, 3, 6, lipRgb);
                    set(HEAD, Face.FRONT, 4, 6, lip == 0 ? 0xFF931826 : lipLo);
                }
                case 5 -> {                                       // esparadrapo cruzando o nariz
                    for (int x = 2; x <= 5; x++) set(HEAD, Face.FRONT, x, 5, x == 3 || x == 4 ? 0xFFF6E7C8 : 0xFFE3C390);
                }
                case 6 -> {                                       // curativo na bochecha esquerda (2 x 2 em xadrez: faixa diagonal)
                    set(HEAD, Face.FRONT, 0, 5, 0xFFE3C390); set(HEAD, Face.FRONT, 1, 5, 0xFFF6E7C8);
                    set(HEAD, Face.FRONT, 0, 6, 0xFFF6E7C8); set(HEAD, Face.FRONT, 1, 6, 0xFFE3C390);
                }
                case 7 -> {                                       // joia na testa, na cor dos olhos (acima do olho mais alto)
                    int row = Math.max(0, Math.min(1, tmin - 3));
                    set(HEAD, Face.FRONT, 3, row, blend(iris, 0xFFFFFFFF, 0.45));
                    set(HEAD, Face.FRONT, 4, row, shade(iris, 0.85));
                }
                case 8 -> {                                       // lágrima pintada sob o olho direito
                    set(HEAD, Face.FRONT, 6, 5, paintRgb != 0 ? blend(paintRgb, 0xFFFFFFFF, 0.30) : 0xFF63B3F0);
                    set(HEAD, Face.FRONT, 6, 6, paintRgb != 0 ? paintRgb : 0xFF3A7BD5);
                }
                case 9 -> {                                       // bochechas coradas
                    for (int x : new int[]{1, 2, 5, 6}) set(HEAD, Face.FRONT, x, 5, blend(skinAt(x, 5), 0xFFE8667A, 0.42));
                }
                case 10 -> set(HEAD, Face.FRONT, 5, 6, shade(skinAt(5, 6), 0.42));   // pinta
                default -> {                                      // purpurina nas maçãs do rosto
                    set(HEAD, Face.FRONT, 1, 5, 0xFFFFE9A0); set(HEAD, Face.FRONT, 0, 6, 0xFFFFFFFF);
                    set(HEAD, Face.FRONT, 6, 5, 0xFFFFFFFF); set(HEAD, Face.FRONT, 7, 6, 0xFFFFE9A0);
                }
            }
        }
        for (int q = 0; q < CharacterOptions.PIERCE_EXTRA; q++) {         // piercings extras: o mesmo metal escolhido
            if ((mask & (1L << (CharacterOptions.PIERCE_BIT_BASE + q))) == 0) continue;
            switch (q) {
                case 0 -> {                                       // sobrancelha esquerda
                    int row = Math.max(0, eyeTop(eyeStyle, 0) - 1);
                    set(HEAD, Face.FRONT, 1, row, silver);
                    if (row >= 1) set(HEAD, Face.FRONT, 1, row - 1, silverDk);
                }
                case 1 -> set(HEAD, Face.FRONT, 3, 5, silver);        // narina esquerda
                case 2 -> {                                       // ponte do nariz: barrinha entre os olhos, com uma bolinha mais escura
                    set(HEAD, Face.FRONT, 3, 4, silver);
                    set(HEAD, Face.FRONT, 4, 4, silverDk);
                }
                case 3 -> set(HEAD, Face.FRONT, 2, 5, silver);        // Monroe: acima do lábio, do lado esquerdo
                case 4 -> set(HEAD, Face.FRONT, 3, 7, silver);        // labret: no meio do queixo
                case 5 -> {                                       // um em cada canto da boca, embaixo
                    set(HEAD, Face.FRONT, 2, 7, silver);
                    set(HEAD, Face.FRONT, 5, 7, silverDk);
                }
                default -> {                                      // sobrancelha dupla (direita): dois aros
                    int row = Math.max(0, tr - 1);
                    set(HEAD, Face.FRONT, 5, row, silver);
                    set(HEAD, Face.FRONT, 7, row, silverDk);
                    if (row >= 1) { set(HEAD, Face.FRONT, 5, row - 1, silverDk); set(HEAD, Face.FRONT, 7, row - 1, silver); }
                }
            }
        }
        // itens novos que são "coisa por cima da pele" (e não marca na pele): bits 42 (curativo na testa), 44 (batom escuro) e 46 (brilho labial)
        if ((mask & (1L << (CharacterOptions.EXTRA_BIT_BASE + 16))) != 0) {           // curativo na testa (2 x 2 em xadrez, acima do olho direito)
            final boolean[] z = eyeZone(eyeStyle);                                    // nunca em pixel que vira olho ao piscar ou olhar (alguns olhos têm cílios na linha 1)
            final int[][] patch = {{5, 0, 0xFFE3C390}, {6, 0, 0xFFF6E7C8}, {5, 1, 0xFFF6E7C8}, {6, 1, 0xFFE3C390}};
            for (int[] q : patch) if (!z[q[1] * 8 + q[0]]) set(HEAD, Face.FRONT, q[0], q[1], q[2]);
        }
        if ((mask & (1L << (CharacterOptions.EXTRA_BIT_BASE + 18))) != 0) {           // batom escuro: lábios quase pretos, de vinho
            set(HEAD, Face.FRONT, 3, 6, 0xFF4A1830);
            set(HEAD, Face.FRONT, 4, 6, 0xFF3A1226);
        }
        if ((mask & (1L << (CharacterOptions.EXTRA_BIT_BASE + 20))) != 0) {           // brilho labial: um reflexo claro por cima do lábio que já está pintado
            final boolean tint = lip != 0 && (mask & (1L << CharacterOptions.LIP_BIT_STICK)) == 0;     // sem batom, o brilho colorido tinge o lábio
            final int up = tint ? blend(headPx(3, 6), lipRgb, 0.55) : headPx(3, 6), lo = tint ? blend(headPx(4, 6), lipLo, 0.55) : headPx(4, 6);
            set(HEAD, Face.FRONT, 3, 6, blend(up, 0xFFFFFFFF, 0.38));
            set(HEAD, Face.FRONT, 4, 6, blend(lo, 0xFFFFFFFF, 0.24));
        }
        // orelhas: cada uma com a sua lista e o seu metal (esquerda do personagem = face LEFT da cabeça, direita = face RIGHT)
        earFace = Face.LEFT;
        for (int q = 0; q < CharacterOptions.EAR_ITEMS; q++) if ((earL & (1 << q)) != 0) earItem(q, earLtL, earDkL);
        earFace = Face.RIGHT;
        for (int q = 0; q < CharacterOptions.EAR_ITEMS; q++) if ((earR & (1 << q)) != 0) earItem(q, earLtR, earDkR);
        protectEyes = prev;
    }

    /** Tom claro do metal dos piercings (0 prata, 1 dourado; qualquer outro valor já foi recusado antes). A tela usa o mesmo tom nas amostras dos botões de metal. */
    public static int metalLt(int metal) {
        return metal == 1 ? 0xFFF0C95A : 0xFFD8DDE6;
    }

    /** Tom escuro do metal dos piercings (0 prata, 1 dourado). */
    public static int metalDk(int metal) {
        return metal == 1 ? 0xFFB07F22 : 0xFF8D95A6;
    }

    /** A lateral da cabeça da orelha que está sendo pintada agora: LEFT (orelha esquerda do personagem) ou RIGHT (direita). */
    private Face earFace = Face.LEFT;

    /**
     * Pixel de brinco na orelha de {@link #earFace}: {@code dx} afasta para a frente do rosto (negativo = para trás), {@code y} é a linha.
     * No lado direito a frente fica nos x maiores e no esquerdo nos menores.
     */
    private void ear(int dx, int y, int argb) {
        set(HEAD, earFace, earFace == Face.RIGHT ? 4 + dx : 3 - dx, y, argb);
    }

    /** Um item de orelha ({@link CharacterOptions#EAR_ITEMS}): {@code lt} e {@code dk} são o metal escolhido; pérola, gemas, pena e dente têm cor própria. */
    private void earItem(int q, int lt, int dk) {
        final int pearl = 0xFFF4EFE8, pearlDk = 0xFFCFC8BC, bone = 0xFFF2EFE4, boneDk = 0xFFC9C3B2;
        switch (q) {
            case 0 -> { ear(0, 5, lt); ear(1, 6, lt); ear(0, 7, dk); ear(-1, 6, dk); }                // argola: aro em losango no lóbulo
            case 1 -> ear(0, 5, lt);                                                                  // pino no lóbulo
            case 2 -> { ear(0, 3, lt); ear(0, 2, dk); }                                               // hélix: alto da orelha
            case 3 -> ear(1, 4, lt);                                                                  // tragus: pontinha na frente da orelha
            case 4 -> { ear(0, 5, lt); ear(0, 6, dk); ear(0, 3, lt); ear(1, 4, dk); }                // orelha completa: lóbulo duplo, hélix e tragus
            case 5 -> { ear(0, 5, lt); ear(0, 6, dk); ear(0, 7, lt); }                               // pendente: gota longa
            case 6 -> { ear(-1, 3, lt); ear(0, 3, dk); ear(1, 3, lt); }                               // transversal: barra atravessando a cartilagem
            case 7 -> { ear(0, 5, lt); ear(1, 6, lt); ear(1, 7, lt); ear(0, 7, dk); ear(-1, 7, dk); ear(-1, 6, dk); }   // argola grande: aro largo que desce do lóbulo
            case 8 -> { ear(0, 5, lt); ear(0, 6, pearl); ear(0, 7, pearlDk); }                        // pérola pendurada
            case 9 -> { ear(0, 5, 0xFFE8505E); ear(0, 6, 0xFF9E1F2E); }                               // rubi: gema vermelha no lóbulo
            case 10 -> { ear(0, 5, 0xFF5AA0F0); ear(0, 6, 0xFF2A5FB0); }                             // safira: gema azul, cor do mar
            case 11 -> { ear(0, 5, lt); ear(0, 6, dk); ear(0, 7, lt); ear(-1, 6, lt); ear(1, 6, lt); }   // cruz pendente
            case 12 -> { ear(0, 3, lt); ear(1, 4, dk); ear(0, 5, lt); ear(1, 6, dk); }                // argolas empilhadas ao longo da borda
            case 13 -> { ear(-2, 2, dk); ear(-1, 3, lt); ear(0, 3, lt); ear(1, 4, lt); ear(2, 4, dk); }   // industrial: barra comprida na diagonal
            case 14 -> { ear(0, 5, lt); ear(0, 6, 0xFF3FA7A0); ear(0, 7, 0xFF2B7F8F); ear(1, 6, 0xFFE8D5A8); }   // pena
            case 15 -> { ear(0, 5, lt); ear(-1, 6, bone); ear(0, 6, bone); ear(1, 6, boneDk); ear(0, 7, boneDk); }   // dente de tubarão
            case 16 -> { ear(0, 5, 0xFF6B4423); ear(1, 5, 0xFF4A2F18); ear(0, 6, 0xFF4A2F18); ear(1, 6, 0xFF6B4423); }   // alargador de madeira
            default -> { ear(-1, 4, lt); ear(0, 4, dk); }                                             // concha: pino no meio da orelha
        }
    }

    // ------------------------------------------------------------------------------------------ cicatrizes do corpo

    /**
     * Cicatrizes do corpo (bits {@link CharacterOptions#BODY_SCAR_BASE} em diante), inspiradas nas skins de referência: cortes vermelhos no
     * peito e nos braços, riscos no abdômen, ralados nas pernas, ferida no ombro, tecido cicatrizado e manchas roxas. Só pintam a camada de
     * baixo e só onde ainda há pele nua (o pixel é igual ao do corpo sem roupa): roupa e camada externa ficam como estão, e nenhum pixel
     * da cabeça muda. Tudo é fixo (sem sorteio), então a mesma skin sai sempre igual.
     */
    private void bodyScars(long mask) {
        if ((mask >>> CharacterOptions.BODY_SCAR_BASE) == 0) return;
        final int sk = skinAt(1, 6) & 0xFFFFFF;
        final double luma = 0.299 * (sk >> 16 & 255) + 0.587 * (sk >> 8 & 255) + 0.114 * (sk & 255);
        final boolean dark = luma < 95;                                     // peles escuras: o vermelho some, então a cicatriz vira um tom mais claro e rosado
        final int[] bc = burnColors(dark);                                  // tecido de queimadura: {carne viva, fundo, tecido, brilho}
        final int red = dark ? 0xFFC9606A : 0xFFB0303A;
        final int scab = dark ? 0xFF8A4038 : 0xFF7A2E2A;
        final int lilac = dark ? 0xFF9C84C4 : 0xFF8468B0, plum = dark ? 0xFF7660A0 : 0xFF5E4690, fade = dark ? 0xFFA8A060 : 0xFF9E9040;
        final Part[] arms = {RARM, LARM};
        for (int b = 0; b < CharacterOptions.BODY_SCAR_COUNT; b++) {
            if ((mask & (1L << (CharacterOptions.BODY_SCAR_BASE + b))) == 0) continue;
            switch (b) {
                case 0 -> {                                                       // cortes no peito: um talho velho e levantado, na diagonal, e outro recente e aberto, paralelo
                    bodyStroke(BODY, Face.FRONT, new int[][]{{0, 0}, {1, 1}, {2, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}}, SC_RAISED);
                    bodyStroke(BODY, Face.FRONT, new int[][]{{4, 0}, {5, 1}, {6, 2}, {6, 3}, {7, 4}}, SC_FRESH);
                }
                case 1 -> {                                                       // cortes no abdômen: três riscos de garras, escalonados
                    bodyStroke(BODY, Face.FRONT, new int[][]{{0, 5}, {1, 6}, {1, 7}, {2, 8}}, SC_RAISED);
                    bodyStroke(BODY, Face.FRONT, new int[][]{{3, 6}, {4, 7}, {4, 8}, {5, 9}}, SC_FRESH);
                    bodyStroke(BODY, Face.FRONT, new int[][]{{6, 5}, {7, 6}, {7, 7}}, SC_RAISED);
                }
                case 2 -> {                                                       // cortes nos braços: três talhos de quem se defendeu, nos dois braços (o do meio recente)
                    for (Part a : arms) {
                        final int w = a.w();
                        final boolean left = a == LARM;
                        final int[] rows = {2, 5, 8};
                        for (int i = 0; i < rows.length; i++) {
                            final int r = rows[i];
                            final int[][] pts = w == 4 ? new int[][]{{0, r}, {1, r + 1}, {2, r + 1}, {3, r + 2}} : new int[][]{{0, r}, {1, r + 1}, {2, r + 2}};
                            if (left) for (int[] q : pts) q[0] = w - 1 - q[0];
                            bodyStroke(a, Face.FRONT, pts, i == 1 ? SC_FRESH : SC_RAISED);
                        }
                    }
                }
                case 3 -> {                                                       // ralados nas pernas: crostas nos joelhos (escura no meio, vermelha em volta) e um risco na canela, uma perna diferente da outra
                    final int[][] kneeR = {{1, 5}, {2, 5}, {1, 6}, {2, 6}, {3, 6}, {2, 7}}, kneeL = {{0, 6}, {1, 6}, {0, 7}, {1, 7}, {1, 8}};
                    scarPatch(RLEG, Face.FRONT, kneeR, scab, 0.66, red, 0.36);
                    scarPatch(LLEG, Face.FRONT, kneeL, scab, 0.66, red, 0.36);
                    bodyStroke(RLEG, Face.FRONT, new int[][]{{0, 9}, {1, 10}, {2, 10}}, SC_RAISED);
                    bodyStroke(LLEG, Face.FRONT, new int[][]{{3, 2}, {3, 3}, {2, 4}, {2, 5}}, SC_RAISED);
                }
                case 4 -> {                                                       // ferida no ombro esquerdo (direita de quem vê): crosta escura no meio, carne viva em volta, borda arroxeada
                    scarPatch(LARM, Face.FRONT, new int[][]{{1, 0}, {2, 0}, {3, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {1, 2}, {2, 2}}, 0xFFC4606A, 0.62, 0xFF8A5A82, 0.34);
                    scarCut(LARM, Face.FRONT, new int[][]{{2, 1}, {1, 1}}, scab, 0.86);
                    scarPatch(LARM, Face.LEFT, new int[][]{{1, 0}, {2, 0}, {1, 1}}, 0xFFC4606A, 0.50, 0xFF8A5A82, 0.26);
                    scarPatch(LARM, Face.BACK, new int[][]{{0, 0}, {1, 0}, {1, 1}}, 0xFFC4606A, 0.44, 0xFF8A5A82, 0.22);
                    scarPatch(BODY, Face.FRONT, new int[][]{{6, 0}, {7, 0}, {7, 1}}, 0xFFC4606A, 0.36, 0xFF8A5A82, 0.20);
                }
                case 5 -> {                                                       // pele cicatrizada: manchas lisas, claras e brilhantes (queimaduras antigas), com borda repuxada
                    scarPatch(BODY, Face.FRONT, new int[][]{{0, 2}, {1, 2}, {1, 3}, {2, 3}, {1, 4}}, bc[2], 0.60, bc[3], 0.34);
                    scarPatch(BODY, Face.FRONT, new int[][]{{5, 0}, {6, 0}, {6, 1}, {7, 1}, {6, 2}}, bc[2], 0.56, bc[3], 0.32);
                    scarPatch(BODY, Face.FRONT, new int[][]{{3, 6}, {4, 6}, {4, 7}, {5, 7}, {4, 8}}, bc[2], 0.58, bc[3], 0.34);
                    scarPatch(BODY, Face.BACK, new int[][]{{1, 1}, {2, 1}, {2, 2}, {5, 4}, {6, 4}, {6, 5}, {3, 8}, {4, 8}}, bc[2], 0.58, bc[3], 0.32);
                    for (Part a : arms) {
                        scarPatch(a, Face.FRONT, new int[][]{{1, 2}, {2, 2}, {2, 3}, {1, 6}, {2, 6}, {1, 7}}, bc[2], 0.58, bc[3], 0.32);
                        scarPatch(a, Face.BACK, new int[][]{{0, 4}, {1, 4}, {1, 5}}, bc[2], 0.54, bc[3], 0.30);
                    }
                    scarPatch(RLEG, Face.FRONT, new int[][]{{1, 2}, {2, 2}, {2, 3}, {1, 8}, {2, 8}}, bc[2], 0.56, bc[3], 0.30);
                    scarPatch(LLEG, Face.FRONT, new int[][]{{0, 4}, {1, 4}, {1, 5}, {2, 9}}, bc[2], 0.56, bc[3], 0.30);
                }
                case 6 -> {                                                       // hematomas: roxo no miolo, esmaecendo para o amarelo-esverdeado nas bordas
                    scarPatch(BODY, Face.FRONT, new int[][]{{4, 0}, {5, 0}, {6, 0}, {4, 1}, {5, 1}, {6, 1}, {7, 1}, {5, 2}, {6, 2}, {7, 2}, {6, 3}}, plum, 0.46, fade, 0.26);
                    scarPatch(BODY, Face.FRONT, new int[][]{{1, 5}, {2, 5}, {1, 6}, {2, 6}, {3, 6}, {2, 7}}, plum, 0.42, fade, 0.24);
                    scarPatch(BODY, Face.BACK, new int[][]{{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 2}, {2, 3}, {5, 6}, {6, 6}, {5, 7}}, plum, 0.44, fade, 0.24);
                    scarPatch(LARM, Face.FRONT, new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}, {1, 1}, {2, 1}, {3, 1}, {2, 2}, {3, 2}, {2, 3}}, plum, 0.46, fade, 0.26);
                    scarPatch(LARM, Face.LEFT, new int[][]{{0, 0}, {1, 0}, {1, 1}, {2, 2}}, plum, 0.40, fade, 0.22);
                    scarPatch(RARM, Face.FRONT, new int[][]{{0, 5}, {1, 5}, {1, 6}, {2, 6}, {1, 7}}, plum, 0.38, fade, 0.22);
                    scarPatch(LLEG, Face.FRONT, new int[][]{{0, 2}, {1, 2}, {1, 3}, {2, 3}}, plum, 0.36, fade, 0.2);
                    for (int[] q : new int[][]{{5, 1}, {1, 6}}) scarPx(BODY, Face.FRONT, q[0], q[1], lilac, 0.30);   // um núcleo mais claro no meio de cada mancha grande
                }
                default -> {                                                      // cortes nas costas: dois talhos que se cruzam em X (um velho, um recente) e um risco curado embaixo
                    bodyStroke(BODY, Face.BACK, new int[][]{{1, 0}, {2, 1}, {3, 2}, {4, 3}, {5, 4}, {6, 5}}, SC_RAISED);
                    bodyStroke(BODY, Face.BACK, new int[][]{{6, 0}, {5, 1}, {4, 2}, {3, 3}, {2, 4}, {1, 5}}, SC_FRESH);
                    bodyStroke(BODY, Face.BACK, new int[][]{{2, 8}, {3, 8}, {4, 8}, {5, 8}}, SC_HEALED);
                    bodyStroke(RARM, Face.BACK, new int[][]{{0, 1}, {1, 2}, {2, 3}}, SC_RAISED);
                }
            }
        }
    }

    /** Cicatriz do corpo seguindo {@code pts} na face {@code f} da peça {@code part}: o mesmo desenho do rosto ({@link #stroke}), só em pele nua. */
    private void bodyStroke(Part part, Face f, int[][] pts, int kind) {
        stroke((x, y, c, k) -> scarPx(part, f, x, y, c, k), scarPal(), pts, kind);
    }

    /** Um pixel de cicatriz do corpo: mistura a pele nua que já está ali com {@code color} ({@code k} de 0 a 1). Roupa (inclusive a de baixo da skin base) e pixel já marcado ficam como estão. */
    private void scarPx(Part part, Face f, int x, int y, int color, double k) {
        final int[] r = part.rect(f);
        if (x < 0 || y < 0 || x >= r[2] || y >= r[3]) return;
        final int idx = (r[1] + y) * SIZE + r[0] + x;
        if (underwear[idx]) return;                               // cueca/top da skin base: é roupa, não pele
        if (covered(part, f, x, y)) return;                       // camada externa (casaco, manga, calça) por cima: nada de cicatriz por baixo
        if (px[idx] != base[idx]) return;
        px[idx] = blend(base[idx], color, k);
    }

    /** Existe um pixel da camada externa (casaco, manga, calça) cobrindo este pixel da camada de baixo? */
    private boolean covered(Part part, Face f, int x, int y) {
        final Part over = part == BODY ? JACKET : part == RARM ? RSLEEVE : part == LARM ? LSLEEVE : part == RLEG ? RPANTS : part == LLEG ? LPANTS : null;
        if (over == null) return false;
        final int[] r = over.rect(f);
        if (x >= r[2] || y >= r[3]) return false;
        return (px[(r[1] + y) * SIZE + r[0] + x] >>> 24) != 0;
    }

    /** Linha de cicatriz, sem inchaço em volta (cicatriz já curada). */
    private void scarLineBody(Part part, Face f, int[][] pts, int color, double k) {
        for (int[] p : pts) scarPx(part, f, p[0], p[1], color, k);
    }

    /** Corte: o risco e, nos vizinhos de cima, de baixo e dos lados, um inchaço rosado fraco (pele repuxada em volta do corte). */
    private void scarCut(Part part, Face f, int[][] pts, int color, double k) {
        final int[][] near = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        final java.util.Set<Long> core = new java.util.HashSet<>();
        for (int[] p : pts) core.add(((long) p[0] << 32) | (p[1] & 0xFFFFFFFFL));
        for (int[] p : pts) {
            for (int[] n : near) {
                final int nx = p[0] + n[0], ny = p[1] + n[1];
                if (!core.contains(((long) nx << 32) | (ny & 0xFFFFFFFFL))) scarPx(part, f, nx, ny, 0xFFE0888E, 0.16);
            }
        }
        for (int[] p : pts) scarPx(part, f, p[0], p[1], color, k);
    }

    /** Mancha: o miolo ({@code k1}) e, em volta, uma borda mais fraca ({@code k2}) na cor da borda. */
    private void scarPatch(Part part, Face f, int[][] pts, int core, double k1, int edge, double k2) {
        final java.util.Set<Long> in = new java.util.HashSet<>();
        for (int[] p : pts) in.add(((long) p[0] << 32) | (p[1] & 0xFFFFFFFFL));
        for (int[] p : pts) {
            for (int[] n : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
                final int nx = p[0] + n[0], ny = p[1] + n[1];
                if (!in.contains(((long) nx << 32) | (ny & 0xFFFFFFFFL))) scarPx(part, f, nx, ny, edge, k2);
            }
        }
        for (int[] p : pts) scarPx(part, f, p[0], p[1], core, k1);
    }

    // ------------------------------------------------------------------------------------------ rosto

    /** Pixel (x, y) da face da cabeça, já com o sombreado da pele (antes das roupas e do rosto). */
    private int skinAt(int x, int y) {
        int[] r = HEAD.rect(Face.FRONT);
        return base[(r[1] + y) * SIZE + r[0] + x];
    }


    /** Cor que está agora no pixel (x, y) da face da frente da cabeça (com o que já foi pintado por cima da pele). */
    private int headPx(int x, int y) {
        int[] r = HEAD.rect(Face.FRONT);
        return px[(r[1] + y) * SIZE + r[0] + x];
    }

    /** Tom da pele que já está no pixel (x, y) do rosto, mais claro (nível &gt; 0) ou mais escuro (&lt; 0); {@code warm} puxa para o rosado. */
    private int tn(int x, int y, int level, double warm) {
        return BodyShading.tone(skinAt(x, y) & 0xFFFFFF, level, warm);
    }

    /** Mistura a pele do pixel (x, y) do rosto com {@code c} ({@code t} de 0 a 1): marcas que acompanham o tom de pele. */
    private int mixSkin(int x, int y, int c, double t) {
        return blend(skinAt(x, y), c, t);
    }

    /**
     * Desenhos dos estilos de olhos, só os olhos: 3 linhas (linhas 2, 3 e 4 do rosto) de 8 colunas cada, para cada estilo
     * de {@link CharacterOptions#EYE_STYLES}. O estilo 0 (olho padrão) é pintado à parte e aqui fica vazio.
     * Letras: {@code k} contorno/cílios (escuro), {@code w} branco do olho, {@code I} íris (parte de cima, mais escura),
     * {@code i} íris (parte de baixo, mais clara), {@code c} pálpebra (sombra cinza da pele), {@code l} tom claro da íris, {@code h} brilho, {@code .} nada.
     * A esquerda de quem olha é x = 0..3 e a direita x = 4..7 (o canto de fora do olho fica no x = 0/1 e 6/7).
     */
    static final String[][] EYE_STYLE_GRID = {
            {"........", "........", "........"},                       // 0 Padrão (pintado à parte)
            {"........", "........", ".I....I."},                       // 1 Pontinhos
            {"........", "..I..I..", "..i..i.."},                       // 2 Gotas
            {"........", ".kk..kk.", ".wi..iw."},                       // 3 Cílios
            {".kk..kk.", ".cI..Ic.", ".wi..iw."},                       // 4 Intensos
            {"........", ".kI..Ik.", "..i..i.."},                       // 5 Felinos
            {"........", ".kk..kk.", "kwi..iwk"},                       // 6 Asas
            {".kk..kk.", "kcI..Ick", "kwi..iwk"},                       // 7 Marcantes
            {"........", "kkk..kkk", "wii..iiw"},                       // 8 Amendoados
            {"kkk..kkk", "cII..IIc", "wii..iiw"},                       // 9 Grandes
            {"kkk..kkk", "kII..IIk", "wii..iiw"},                       // 10 Dramáticos
            {"........", ".kk..kk.", "..i..i.."},                       // 11 Sonolentos
            {".kk..kk.", ".Ih..hI.", ".II..II."},                       // 12 Brilhantes
            {".kk..kk.", "wII..IIw", "wii..iiw"},                       // 13 Arregalados
            {"........", "kk....kk", "kI....Ik"},                       // 14 Contorno
    };

    /**
     * Estilos de olhos novos (do 15 em diante), desenhados em 5 linhas (linhas 1 a 5 do rosto) de 8 colunas, com as mesmas
     * letras de {@link #EYE_STYLE_GRID} e mais {@code l} = tom claro da íris (reflexo na pálpebra ou borda do olho).
     * A linha de baixo do olho (branco/íris) fica sempre na linha 4 do rosto, como nos estilos antigos.
     */
    static final String[][] EYE_STYLE_GRID_EXTRA = {
            {"........", "........", "........", ".i....i.", "........"},   // 15 Miúdos
            {"........", "........", "........", ".kk..kk.", "........"},   // 16 Fechados
            {"........", "........", ".I....I.", ".i....i.", "........"},   // 17 Afastados
            {"........", "........", ".k....k.", ".k....k.", "........"},   // 18 Traços Laterais
            {"........", "........", "..k..k..", "..k..k..", "........"},   // 19 Traços Centrais
            {"........", "........", "kk....kk", ".i....i.", "........"},   // 20 Encapuzados
            {"........", "........", "kk....kk", "ki....ik", "........"},   // 21 Emoldurados
            {"........", "........", "kkk..kkk", "kwi..iwk", "........"},   // 22 Sérios
            {"........", "........", "kkk..kkk", "iki..iki", "........"},   // 23 Pupilas
            {"........", "kk....kk", "kI....Ik", ".i....i.", "........"},   // 24 Apertados
            {"........", "..k..k..", ".kk..kk.", ".wi..iw.", "........"},   // 25 Pontudos
            {"........", ".kk..kk.", ".kI..Ik.", ".ki..ik.", "........"},   // 26 Escurecidos
            {"........", "kk....kk", "kI....Ik", "ki....ik", "........"},   // 27 Cercados
            {"........", ".kk..kk.", ".lI..Il.", ".wi..iw.", "........"},   // 28 Suaves
            {"........", ".kk..kk.", ".Iw..wI.", ".wi..iw.", "........"},   // 29 Clarão
            {"........", "kk....kk", "iw....wi", "wi....iw", "........"},   // 30 Colados
            {"........", ".kk..kk.", ".wI..Iw.", ".wi..iw.", "........"},   // 31 Doces
            {"........", ".kk..kk.", ".II..II.", ".ii..ii.", "........"},   // 32 Redondos
            {"........", ".kk..kk.", ".Iw..wI.", ".ii..ii.", "........"},   // 33 Faíscas
            {"........", "..k..k..", "kkk..kkk", ".wi..iw.", "........"},   // 34 Arqueados
            {"........", "..k..k..", "kkk..kkk", "kwi..iwk", "........"},   // 35 Arco Alado
            {"........", "..k..k..", "kkk..kkk", "wii..iiw", "........"},   // 36 Arco Largo
            {"........", ".kk..kk.", "kwI..Iwk", ".wi..iw.", "........"},   // 37 Graciosos
            {"........", ".kk..kk.", "kII..IIk", ".ii..ii.", "........"},   // 38 Expressivos
            {"........", ".kk..kk.", "kIw..wIk", ".ii..ii.", "........"},   // 39 Cintilantes
            {"........", ".kk..kk.", "klI..Ilk", "kwi..iwk", "........"},   // 40 Delicados
            {"........", ".kk..kk.", "kIw..wIk", "kwi..iwk", "........"},   // 41 Luminosos
            {"........", ".kk..kk.", "kwI..Iwk", "kwi..iwk", "........"},   // 42 Clássicos
            {"........", "kkk..kkk", "lII..IIl", "wii..iiw", "........"},   // 43 Amplos
            {"........", "kkk..kkk", "lIw..wIl", "wii..iiw", "........"},   // 44 Radiantes
            {"........", "kkk..kkk", "kIw..wIk", "wii..iiw", "........"},   // 45 Fulgurantes
            {"........", "kkk..kkk", "lIk..kIl", "wik..kiw", "........"},   // 46 Pupila de Gato
            {"........", "kkk..kkk", "kIk..kIk", "wik..kiw", "........"},   // 47 Predadores
            {"........", "kkk..kkk", "wII..IIw", "wii..iiw", "........"},   // 48 Cristalinos
            {"..k..k..", "kkk..kkk", "kIw..wIk", "wii..iiw", "........"},   // 49 Acentuados
            {"..k..k..", "kkk..kkk", "kIk..kIk", "wik..kiw", "........"},   // 50 Ameaçadores
            {"ccc..ccc", "wII..IIw", "wii..iiw", "www..www", "........"},   // 51 Esbugalhados (sombra da pálpebra neutra; a pupila 2x2 é que leva a cor da íris)
    };

    /** Pixel de olho: pinta na face da frente da cabeça e marca o pixel como olho (barba e sobrancelhas não passam por cima). */
    private void eyePx(int x, int y, int argb) {
        set(HEAD, Face.FRONT, x, y, argb);
        int[] r = HEAD.rect(Face.FRONT);
        eyeMask[(r[1] + y) * SIZE + r[0] + x] = true;
    }

    /** Pinta o estilo de olhos {@code style} com a íris {@code iris}; branco, pálpebra e contorno seguem o tom da pele que já está pintada. */
    private void eyes(int style, int iris, int hairDark) {
        final int baseStyles = EYE_STYLE_GRID.length, total = baseStyles + EYE_STYLE_GRID_EXTRA.length;
        final int line = blend(0xFF1E2028, hairDark, 0.30);                // contorno: quase preto, puxado de leve para a cor do cabelo
        if (style <= 0 || style >= total) {                      // olho padrão: branco de fora, íris de dentro, uma linha só
            if (blink == 2) {                                    // fechado: uma linha suave nas mesmas colunas
                for (int x : new int[]{1, 2, 5, 6}) eyePx(x, 4, mixSkin(x, 4, line, 0.46));
                return;
            }
            int whiteL = mixSkin(1, 4, 0xFFF4EFEC, 0.92), whiteR = mixSkin(6, 4, 0xFFF4EFEC, 0.80);
            int[] cs = {whiteL, iris, shade(iris, 0.90), shade(whiteR, 0.88)};
            if (gazeX > 0) { int a = cs[2]; cs[2] = cs[3]; cs[3] = a; }        // olhando para a direita: a íris do olho direito vai para fora
            else if (gazeX < 0) { int a = cs[0]; cs[0] = cs[1]; cs[1] = a; }   // para a esquerda: a do olho esquerdo
            int[] xs = {1, 2, 5, 6};
            for (int q = 0; q < 4; q++) {
                int c = cs[q];
                if (blink == 1) c = blend(c, mixSkin(xs[q], 4, line, 0.45), 0.40);   // meio fechado: a pálpebra desce sobre o olho e o escurece de leve
                eyePx(xs[q], 4, c);
            }
            return;
        }
        final int irisDark = shade(iris, 0.78), irisLight = blend(iris, 0xFFFFFFFF, 0.14);
        final String[] rows = shiftGaze(style < baseStyles ? EYE_STYLE_GRID[style] : EYE_STYLE_GRID_EXTRA[style - baseStyles], gazeX, gazeY);
        final int top = style < baseStyles ? 2 : 1;                         // os antigos têm 3 linhas (2 a 4), os novos 5 (1 a 5)
        // Piscar: o desenho inteiro do olho (cílios, pontas, asas, contorno, branco, íris) é achatado para baixo, em direção à linha
        // de baixo do olho: no meio fechado ele fica com metade da altura e no fechado vira uma linha só (ver squash).
        final char[][] t = blink == 0 ? asChars(rows) : squash(rows, blink == 1 ? 0.5 : 0.0);
        final boolean[] oneRow = blink == 1 ? singleEyeRow(rows) : null;
        for (int j = 0; j < rows.length; j++) for (int x = 0; x < 8; x++) {
            int y = top + j, c;
            boolean eyeLetter = true;
            switch (t[j][x]) {
                case 'k', 'P' -> { c = line; eyeLetter = false; }                  // contorno, cílio, asa / pupila
                case 'S' -> { c = mixSkin(x, y, line, 0.46); eyeLetter = false; }  // linha suave da pálpebra fechada
                case 'w' -> c = mixSkin(x, y, 0xFFF4EFEC, x < 4 ? 0.92 : 0.82);
                case 'I' -> c = x < 4 ? irisDark : shade(irisDark, 0.94);
                case 'i' -> c = x < 4 ? irisLight : shade(irisLight, 0.94);
                case 'l' -> c = blend(iris, 0xFFFFFFFF, 0.62D);
                case 'c' -> c = mixSkin(x, y, line, 0.28);
                case 'h' -> c = 0xFFF8F4F0;
                default -> { continue; }
            }
            if (oneRow != null && oneRow[x] && eyeLetter) c = blend(c, mixSkin(x, y, line, 0.45), 0.40);   // olho de uma linha só: escurece pela metade
            eyePx(x, y, c);
        }
    }

    /**
     * Move a íris e a pupila dentro do olho: para o lado ({@code dx}) e para cima/baixo ({@code dy}), trocando de lugar com o branco
     * do olho ({@code w}) que está do lado para onde vão (nunca passam do contorno nem para fora do olho). Cada olho anda no
     * seu lado do rosto. Se não há branco para onde ir, aquele olho fica como está. A pupila (pixel escuro dentro do olho) vira
     * {@code P} para o piscar saber que ela é olho, não cílio. Sem olhar desviado devolve o desenho como está.
     */
    private static String[] shiftGaze(String[] rows, int dx, int dy) {
        if (dx == 0 && dy == 0) return rows;
        final boolean[][] cells = eyeCells(rows);
        final char[][] t = asChars(rows);
        for (int j = 0; j < rows.length; j++) for (int x = 0; x < 8; x++) if (cells[j][x] && t[j][x] == 'k') t[j][x] = 'P';
        final String mover = "IiP";
        if (dx != 0) {
            for (int j = 0; j < rows.length; j++) {
                for (int half = 0; half < 2; half++) {
                    int lo = half * 4, hi = lo + 3;
                    boolean free = true;                       // sem contorno nesta linha: a íris solta também anda sobre a pele
                    for (int x = lo; x <= hi; x++) if (rows[j].charAt(x) == 'k') free = false;
                    if (dx > 0) {
                        for (int x = hi - 1; x >= lo; x--) if (mover.indexOf(t[j][x]) >= 0 && (t[j][x + 1] == 'w' || free && t[j][x + 1] == '.')) { char to = t[j][x + 1]; t[j][x + 1] = t[j][x]; t[j][x] = to; }
                    } else {
                        for (int x = lo + 1; x <= hi; x++) if (mover.indexOf(t[j][x]) >= 0 && (t[j][x - 1] == 'w' || free && t[j][x - 1] == '.')) { char to = t[j][x - 1]; t[j][x - 1] = t[j][x]; t[j][x] = to; }
                    }
                }
            }
        }
        if (dy != 0) {
            for (int x = 0; x < 8; x++) {
                boolean free = true;                           // coluna sem contorno: a íris solta também anda sobre a pele
                for (String r : rows) if (r.charAt(x) == 'k') free = false;
                if (dy > 0) {
                    for (int j = rows.length - 2; j >= 0; j--) if (mover.indexOf(t[j][x]) >= 0 && (t[j + 1][x] == 'w' || free && t[j + 1][x] == '.')) { char to = t[j + 1][x]; t[j + 1][x] = t[j][x]; t[j][x] = to; }
                } else {
                    for (int j = 1; j < rows.length; j++) if (mover.indexOf(t[j][x]) >= 0 && (t[j - 1][x] == 'w' || free && t[j - 1][x] == '.')) { char to = t[j - 1][x]; t[j - 1][x] = t[j][x]; t[j][x] = to; }
                }
            }
        }
        final String[] out = new String[rows.length];
        for (int j = 0; j < rows.length; j++) out[j] = new String(t[j]);
        return out;
    }

    private static char[][] asChars(String[] rows) {
        char[][] t = new char[rows.length][];
        for (int j = 0; j < rows.length; j++) t[j] = rows[j].toCharArray();
        return t;
    }

    /** Colunas em que o olho (branco, íris...) tem uma linha só: aí o meio fechado escurece o olho em vez de cobrir. */
    private static boolean[] singleEyeRow(String[] rows) {
        boolean[][] cells = eyeCells(rows);
        boolean[] one = new boolean[8];
        for (int x = 0; x < 8; x++) {
            int n = 0;
            for (int j = 0; j < rows.length; j++) if (cells[j][x]) n++;
            one[x] = n == 1;
        }
        return one;
    }

    /**
     * Achata o desenho do olho em direção à linha de baixo do olho, {@code lid}: cada pixel vai para {@code lid} menos a sua
     * distância vezes {@code factor} (arredondada para cima, então o que está colado na linha de baixo só sai do lugar ao fechar
     * de vez). Com {@code factor} 0 tudo cai na linha {@code lid}: o que era olho some e fica uma linha só. Tudo faz parte do
     * movimento: cílios, pontas, asas, contorno e olho; só a sobrancelha do estilo de rosto (pintada à parte) fica parada.
     * Onde dois pixels caem no mesmo lugar, o escuro (cílio, contorno) vale mais que o olho, e entre os do olho vale o de baixo.
     * Letras novas: {@code P} = pupila (escura, mas é olho: some ao fechar), {@code S} = linha suave de uma coluna sem cílio.
     */
    private static char[][] squash(String[] rows, double factor) {
        final boolean[][] cells = eyeCells(rows);
        int lid = -1, lastAny = -1;
        for (int j = 0; j < rows.length; j++) for (int x = 0; x < 8; x++) {
            if (cells[j][x]) lid = Math.max(lid, j);
            if (rows[j].charAt(x) != '.') lastAny = Math.max(lastAny, j);
        }
        if (lid < 0) lid = lastAny;                                   // só traços (sem olho desenhado)
        final char[][] t = new char[rows.length][8];
        for (char[] r : t) java.util.Arrays.fill(r, '.');
        if (lid < 0) return t;
        for (int x = 0; x < 8; x++) {
            boolean any = false, dark = false;
            for (int j = 0; j < rows.length; j++) {
                char ch = rows[j].charAt(x);
                if (ch == '.') continue;
                any = true;
                if (ch == 'k' && !cells[j][x]) dark = true;
            }
            if (!any) continue;
            if (factor <= 0.0) { t[lid][x] = dark ? 'k' : 'S'; continue; }
            for (int j = 0; j < rows.length; j++) {                    // de cima para baixo: o de baixo vale mais entre os do olho
                char ch = rows[j].charAt(x);
                if (ch == '.') continue;
                int d = lid - j;
                int nj = d >= 0 ? lid - (int) Math.ceil(d * factor) : lid + (int) Math.ceil(-d * factor);
                boolean isDark = ch == 'k' && !cells[j][x];
                if (t[nj][x] == 'k') continue;
                t[nj][x] = isDark ? 'k' : ch == 'k' ? 'P' : ch;
            }
        }
        return t;
    }

    /**
     * Quais pixels do desenho {@code rows} são "olho" (branco, íris, pálpebra clara, brilho) ou pupila. Pupila = pixel escuro com
     * parte do olho do lado de fora dele (o lado do canto), para não confundir com as asas.
     */
    private static boolean[][] eyeCells(String[] rows) {
        final String inner = "wIilchP";
        boolean[][] m = new boolean[rows.length][8];
        for (int j = 0; j < rows.length; j++) {
            for (int x = 0; x < 8; x++) {
                char ch = rows[j].charAt(x);
                boolean eye = inner.indexOf(ch) >= 0, pupil = false;
                if (ch == 'k') {
                    int dir = x < 4 ? -1 : 1, lo = x < 4 ? 0 : 4, hi = x < 4 ? 3 : 7;
                    for (int q = x + dir; q >= lo && q <= hi; q += dir) if (inner.indexOf(rows[j].charAt(q)) >= 0) pupil = true;
                }
                m[j][x] = eye || pupil;
            }
        }
        return m;
    }

    /**
     * Linha de cima do desenho do olho de {@code eyeStyle} (0 = primeira linha do rosto) num dos lados: {@code side} 0 = esquerda
     * de quem olha, 1 = direita. É por ela que as sobrancelhas, cicatrizes e marcas do rosto se posicionam, para combinarem com
     * QUALQUER estilo de olhos (olho padrão e estilos sem desenho: linha 4). Não depende do piscar nem do olhar, então as marcas
     * nunca dançam quando o olho anima.
     */
    static int eyeTop(int eyeStyle, int side) {
        final int baseStyles = EYE_STYLE_GRID.length, total = baseStyles + EYE_STYLE_GRID_EXTRA.length;
        if (eyeStyle <= 0 || eyeStyle >= total) return 4;
        final String[] rows = eyeStyle < baseStyles ? EYE_STYLE_GRID[eyeStyle] : EYE_STYLE_GRID_EXTRA[eyeStyle - baseStyles];
        final int top = eyeStyle < baseStyles ? 2 : 1;
        for (int j = 0; j < rows.length; j++)
            for (int x = side * 4; x < side * 4 + 4; x++) if (rows[j].charAt(x) != '.') return top + j;
        return 4;
    }

    /**
     * Marca no rosto (pele da cabeça, frente): mistura a pele que já estava ali com {@code color} ({@code k} de 0 a 1). Só pinta pele
     * nua: cabelo, franja e barba que já estão no pixel ficam como estão. Pixel de olho só é tocado com {@code overEye} (a cicatriz que
     * cruza o olho avermelha o branco do olho, nunca apaga o desenho); sem isso o olho fica intacto.
     */
    private void mark(int x, int y, int color, double k, boolean overEye) {
        if (x < 0 || x > 7 || y < 0 || y > 7) return;
        if (markZone != null && markZone[y * 8 + x]) return;
        final int[] r = HEAD.rect(Face.FRONT);
        final int idx = (r[1] + y) * SIZE + r[0] + x;
        if (eyeMask[idx]) {
            if (overEye) px[idx] = blend(px[idx], color, k * 0.55);
            return;
        }
        if (px[idx] != base[idx]) return;                         // cabelo ou outra coisa por cima: a marca não passa
        px[idx] = blend(base[idx], color, k);
    }

    private void mark(int x, int y, int color, double k) {
        mark(x, y, color, k, false);
    }

    /** Escurece/clareia a pele do pixel (como {@link #tn}), só em pele nua. */
    private void markTone(int x, int y, int level, double warm) {
        if (x < 0 || x > 7 || y < 0 || y > 7) return;
        if (markZone != null && markZone[y * 8 + x]) return;
        final int[] r = HEAD.rect(Face.FRONT);
        final int idx = (r[1] + y) * SIZE + r[0] + x;
        if (eyeMask[idx] || px[idx] != base[idx]) return;
        px[idx] = 0xFF000000 | BodyShading.tone(base[idx] & 0xFFFFFF, level, warm);
    }

    /** O desenho da linha de cima do olho (letras da grade) deste lado: colunas ocupadas e se tem contorno escuro ({@code k}). {cmin, cmax, dark} ou null se não há desenho (olho padrão). */
    private static int[] eyeTopCols(int eyeStyle, int side) {
        final int baseStyles = EYE_STYLE_GRID.length, total = baseStyles + EYE_STYLE_GRID_EXTRA.length;
        if (eyeStyle <= 0 || eyeStyle >= total) return null;
        final String[] rows = eyeStyle < baseStyles ? EYE_STYLE_GRID[eyeStyle] : EYE_STYLE_GRID_EXTRA[eyeStyle - baseStyles];
        final int top = eyeTop(eyeStyle, side) - (eyeStyle < baseStyles ? 2 : 1);
        int cmin = 9, cmax = -1, dark = 0;
        for (int x = side * 4; x < side * 4 + 4; x++) {
            char ch = rows[top].charAt(x);
            if (ch == '.') continue;
            cmin = Math.min(cmin, x);
            cmax = Math.max(cmax, x);
            if (ch == 'k') dark = 1;
        }
        return new int[]{cmin, cmax, dark};
    }

    /**
     * Sobrancelha "Determinado" de um lado, COLADA no desenho do olho escolhido: uma faixa logo acima da linha de cima do olho, cobrindo
     * as mesmas colunas e mais uma em direção ao nariz, com a ponta de fora subindo (o olhar fechado e decidido). Se o olho já tem
     * contorno/cílios escuros em cima, a sobrancelha usa a MESMA cor do contorno e os dois viram um traço só; nos olhos sem contorno ela
     * usa a cor do cabelo. Cada estilo de olho, portanto, ganha a sua própria sobrancelha, emendada nele.
     */
    private void joinedBrow(int eyeStyle, int side, int line, int hairDark) {
        final int t = eyeTop(eyeStyle, side);
        int cmin, cmax;
        boolean dark;
        final int[] c = eyeTopCols(eyeStyle, side);
        if (c == null) { cmin = side == 0 ? 1 : 5; cmax = cmin + 1; dark = false; }      // olho padrão: 2 pixels (1 e 2 / 5 e 6)
        else { cmin = c[0]; cmax = c[1]; dark = c[2] == 1; }
        final int dir = side == 0 ? 1 : -1;
        final int outer = dir > 0 ? cmin : cmax, inner = dir > 0 ? cmax : cmin;           // canto de fora e de dentro do desenho
        final int nose = side == 0 ? 2 : 5;                                               // até onde a sobrancelha pode ir além do olho em direção ao nariz: sobra um vão no meio, as duas não viram uma só
        final int col = dark ? line : hairDark;
        final double k = dark ? 0.95 : 0.82;
        final int row = t - 1;                                                            // colada em cima do olho
        int xEnd = dir > 0 ? Math.max(inner, Math.min(nose, inner + 1)) : Math.min(inner, Math.max(nose, inner - 1));   // uma coluna além do olho, rumo ao nariz (nunca encurta a faixa sobre o olho)
        for (int x = Math.min(outer, xEnd); x <= Math.max(outer, xEnd); x++) mark(x, row, col, k);
        mark(outer - dir, row - 1, col, k);                                               // ponta de fora sobe (em diagonal, emendada na faixa)
        mark(outer, row - 1, col, k);
    }

    // ------------------------------------------------------------------------------------------ cicatrizes (sistema único: rosto e corpo)

    /** Onde o desenho de uma cicatriz põe um pixel: {@code mark} no rosto, {@code scarPx} no corpo (os dois só pintam pele nua). */
    private interface Put {
        void at(int x, int y, int color, double k);
    }

    /** Tipos de cicatriz: curada (tecido claro com relevo), levantada (rosada e inchada), ferida fresca (vermelha, com sangue) e sutura (com fios). */
    private static final int SC_HEALED = 0, SC_RAISED = 1, SC_FRESH = 2, SC_STITCH = 3;

    /** Cores de cicatriz derivadas do tom de pele (peles claras: tecido mais rosado e escuro; escuras: mais claro e quente, o vermelho sozinho some). */
    private record ScarPal(int tissue, int hi, int shade, int wound, int swell, int blood, int thread, int knot) { }

    private ScarPal scarPal() {
        final int sk = skinAt(1, 6) & 0xFFFFFF;
        final double luma = 0.299 * (sk >> 16 & 255) + 0.587 * (sk >> 8 & 255) + 0.114 * (sk & 255);
        final int skin = 0xFF000000 | sk;
        if (luma >= 165)
            return new ScarPal(blend(skin, 0xFFC96A6A, 0.58), blend(skin, 0xFFFFF4EC, 0.62), blend(skin, 0xFF4A1E22, 0.50),
                    0xFFB02A32, 0xFFE58C88, 0xFF7A1318, 0xFF2A2224, 0xFF5A3A3C);
        if (luma >= 95)
            return new ScarPal(blend(skin, 0xFFF0BBAA, 0.58), blend(skin, 0xFFFFE8D8, 0.62), blend(skin, 0xFF2E1214, 0.46),
                    0xFFA4222C, 0xFFDC7C7A, 0xFF741218, 0xFF241A1A, 0xFF4E3636);
        return new ScarPal(blend(skin, 0xFFDDA08E, 0.64), blend(skin, 0xFFF4CDBC, 0.62), blend(skin, 0xFF000000, 0.50),
                0xFFD2565E, 0xFFC87682, 0xFFB03C44, 0xFFD6CBB6, 0xFFA89C88);
    }

    /**
     * Desenha uma cicatriz seguindo os pontos {@code pts} (em ordem). Cada tipo tem o seu desenho: o miolo, a ponta que afina nas duas
     * extremidades, o relevo (luz de um lado, sombra do outro), e nas feridas o inchaço em volta e o sangue que escorre; nas suturas os
     * fios atravessando. Os pixels já pintados não são repintados, então a ordem é: miolo, fios, sombra, luz.
     */
    private void stroke(Put put, ScarPal pal, int[][] pts, int kind) {
        final int n = pts.length;
        final int core = switch (kind) { case SC_FRESH -> pal.wound(); case SC_RAISED -> pal.swell(); case SC_STITCH -> pal.swell(); default -> pal.tissue(); };
        for (int i = 0; i < n; i++) {
            final double taper = n > 2 && (i == 0 || i == n - 1) ? 0.72 : 1.0;
            final double jitter = ((pts[i][0] * 7 + pts[i][1] * 13) & 1) == 0 ? 0.0 : -0.05;
            final int c = (i & 1) == 1 && (kind == SC_HEALED) ? blend(core, pal.hi(), 0.22) : core;     // o tecido alterna dois tons: nunca fica chapado
            put.at(pts[i][0], pts[i][1], c, Math.min(0.97, (kind == SC_FRESH ? 0.94 : 0.92) * taper + jitter));
        }
        if (kind == SC_STITCH) {                                              // fios: um de cada lado da costura, em linhas alternadas
            for (int i = 0; i < n; i += 2) {
                put.at(pts[i][0] - 1, pts[i][1], pal.thread(), 0.74);
                put.at(pts[i][0] + 1, pts[i][1], pal.thread(), 0.74);
            }
        }
        for (int i = 0; i < n; i++) {
            final int[] q = pts[i], nx = i < n - 1 ? pts[i + 1] : null, pv = i > 0 ? pts[i - 1] : null;
            final int dy = nx != null ? nx[1] - q[1] : pv != null ? q[1] - pv[1] : 1, dx = nx != null ? nx[0] - q[0] : pv != null ? q[0] - pv[0] : 0;
            final boolean horizontal = n > 1 && dy == 0 && dx != 0;
            final int hx = horizontal ? q[0] : q[0] - 1, hy = horizontal ? q[1] - 1 : q[1];
            final int sx = horizontal ? q[0] : q[0] + 1, sy = horizontal ? q[1] + 1 : q[1];
            if (kind == SC_FRESH) {
                put.at(hx, hy, pal.swell(), 0.46);                            // pele inchada dos dois lados do corte
                put.at(sx, sy, pal.swell(), 0.34);
            } else {
                put.at(sx, sy, pal.shade(), kind == SC_STITCH ? 0.24 : 0.30);  // sombra do relevo (um lado só: a cicatriz fica fina e nítida)
            }
        }
        if (kind != SC_FRESH && kind != SC_STITCH) {
            for (int i = 0; i < n; i++) {
                final int[] q = pts[i];
                final boolean horizontal = n > 1 && (i < n - 1 ? pts[i + 1][1] == q[1] : pts[i - 1][1] == q[1]);
                put.at(horizontal ? q[0] : q[0] - 1, horizontal ? q[1] - 1 : q[1], pal.hi(), 0.20);   // luz no alto do relevo (bem leve)
            }
        }
        if (kind == SC_FRESH) {                                               // sangue: escorre do ponto mais baixo
            int low = 0;
            for (int i = 1; i < n; i++) if (pts[i][1] >= pts[low][1]) low = i;
            put.at(pts[low][0], pts[low][1] + 1, pal.blood(), 0.84);
            put.at(pts[low][0], pts[low][1] + 2, pal.blood(), 0.46);
        }
    }

    /** Cicatriz do rosto: só pinta pele nua e nunca em olho (ver {@link #mark}). */
    private void faceScar(int[][] pts, int kind) {
        stroke(this::mark, scarPal(), pts, kind);
    }

    /** Cicatriz curada de rosto (mantém o nome antigo). */
    private void scarLine(int[][] core) {
        faceScar(core, SC_HEALED);
    }

    /**
     * Rosto 8 x 8 (linhas 0 a 7), tudo derivado da pele que já está pintada: nada tem cor fixa que destoe do tom escolhido.
     * Só olhos e boca; as marcas vêm da máscara de itens do rosto ({@link CharacterOptions#FACE_BIT_BASE}: bit 11 + estilo 1 a 7) e
     * podem ser várias ao mesmo tempo. Todas se medem pelo desenho do estilo de olhos escolhido ({@link #eyeTop}): ficam acima, abaixo
     * ou ao lado do olho e nunca cobrem nem alteram o desenho dele.
     */
    private void face(long mask, int faceX, int eyeStyle, int skin, int hair, int eyes) {
        final int hairDark = shade(hair, 0.62);
        // ---- olhos: o estilo escolhido (só os pixels dos olhos; o resto do rosto fica como a skin base desenhada à mão)
        eyes(eyeStyle, eyes, hairDark);
        final int tl = eyeTop(eyeStyle, 0), tr = eyeTop(eyeStyle, 1), tmin = Math.min(tl, tr);
        final int line = blend(0xFF1E2028, hairDark, 0.30);                // o mesmo contorno que eyes() usa
        // ---- boca: dois pixels de lábio (mais suaves no masculino, mais rosados no feminino), sem sombras nos cantos
        int lipBase = female ? 0xFFC0485E : 0xFFB5645A;
        double lipK = female ? 0.62 : 0.55;
        int lipU = mixSkin(3, 6, lipBase, lipK), lipL = mixSkin(4, 6, lipBase, lipK + 0.08);
        set(HEAD, Face.FRONT, 3, 6, lipU); set(HEAD, Face.FRONT, 4, 6, shade(lipL, 0.92));
        if (female && eyeStyle == 0) {                            // cílios do olho padrão: ponta escura no canto externo de cada olho
            int lash = mixSkin(0, 4, hairDark, 0.75);
            eyePx(0, 4, lash); eyePx(7, 4, lash);
        }
        for (int style = 1; style < CharacterOptions.FACE_STYLES; style++) {
            if ((mask & (1L << (CharacterOptions.FACE_BIT_BASE + style))) == 0) continue;
            switch (style) {
                case 1 -> {                                       // determinado: sobrancelha colada no desenho de cada olho
                    joinedBrow(eyeStyle, 0, line, hairDark);
                    joinedBrow(eyeStyle, 1, line, hairDark);
                }
                case 2 -> {                                       // cicatriz que cruza o olho direito: um talho em diagonal do alto da testa à bochecha; o olho fica intacto (a cicatriz passa por cima e por baixo dele)
                    faceScar(new int[][]{{7, tr - 3}, {7, tr - 2}, {6, tr - 1}}, SC_HEALED);
                    faceScar(new int[][]{{6, tr + 1}, {5, tr + 2}, {5, tr + 3}, {4, tr + 4}}, SC_HEALED);
                }
                case 3 -> {                                       // sardas: pontos mais quentes na ponte do nariz e nas bochechas
                    int[][] pts = {{3, 4}, {1, 5}, {2, 5}, {5, 5}, {6, 5}, {0, 5}, {7, 5}, {2, 6}, {5, 6}};
                    for (int[] q : pts) markTone(q[0], q[1], (q[0] + q[1]) % 2 == 0 ? -2 : -1, 0.9);
                }
                case 4 -> {                                       // marcas de expressão: testa (acima do olho mais alto), pés de galinha e linhas ao lado da boca
                    final int fr = Math.min(1, tmin - 3);
                    if (fr >= 0) for (int x = 2; x <= 5; x++) markTone(x, fr, -1, 0.2);
                    markTone(0, 4, -1, 0.3); markTone(7, 4, -1, 0.3);
                    markTone(2, 5, -1, 0.3); markTone(5, 5, -1, 0.3);
                    markTone(2, 6, -2, 0.3); markTone(5, 6, -2, 0.3);
                }
                case 5 -> {                                       // pintura facial: duas faixas vermelhas sob os olhos (linha 5), com a ponta externa mais escura
                    final int paint = paintRgb != 0 ? paintRgb : 0xFFC0392B;
                    for (int x : new int[]{0, 1, 2}) mark(x, 5, x == 0 ? shade(paint, 0.82) : x == 2 ? shade(paint, 1.08) : paint, 0.92);
                    for (int x : new int[]{5, 6, 7}) mark(x, 5, x == 7 ? shade(paint, 0.82) : x == 5 ? shade(paint, 0.92) : paint, 0.92);
                }
                case 6 -> {                                       // cicatriz comprida na bochecha esquerda: um talho em diagonal, afinando nas pontas
                    faceScar(new int[][]{{0, 4}, {0, 5}, {1, 6}, {2, 7}}, SC_HEALED);
                }
                case 7 -> {                                       // olhar cansado: olheiras arroxeadas sob o olho e pálpebra pesada acima dele
                    for (int x : new int[]{1, 2, 5, 6}) mark(x, 5, 0xFF6E4A66, 0.38);
                    for (int x : new int[]{0, 7}) mark(x, 5, 0xFF6E4A66, 0.16);
                    for (int x : new int[]{1, 2}) mark(x, Math.max(0, tl - 1), 0xFF6E4A66, 0.14);
                    for (int x : new int[]{5, 6}) mark(x, Math.max(0, tr - 1), 0xFF6E4A66, 0.14);
                }
                default -> { }
            }
        }
        extraMarks(mask, eyeStyle, eyes);
        faceXMarks(faceX, eyeStyle, hairDark);
    }

    /**
     * Faces novas ({@link CharacterOptions#FACEX_BASE}): marcas no rosto que se medem pelo desenho do olho escolhido, como as outras
     * ({@link #mark}: só pele nua, nunca em olho ou cabelo). As de 8 a 12 usam a cor da pintura ({@link #paintRgb}; sem escolha, a cor de cada uma).
     */
    private void faceXMarks(int fx, int eyeStyle, int hairDark) {
        if (fx == 0) return;
        final int tl = eyeTop(eyeStyle, 0), tr = eyeTop(eyeStyle, 1);
        final int sk = skinAt(1, 6) & 0xFFFFFF;
        final double luma = 0.299 * (sk >> 16 & 255) + 0.587 * (sk >> 8 & 255) + 0.114 * (sk & 255);
        final int ink = luma < 95 ? 0xFF8FA8C8 : 0xFF22272F, tooth = 0xFFF4F0E8;
        markZone = eyeZone(eyeStyle);
        for (int q = 0; q < CharacterOptions.FACEX_ITEMS; q++) {
            if ((fx & (1 << q)) == 0) continue;
            switch (q) {
                case 0 -> { markTone(2, 6, -2, 0.1); markTone(5, 6, -2, 0.1); markTone(2, 7, -1, 0.1); markTone(5, 7, -1, 0.1); }   // covinhas
                case 1, 2 -> { }                                                                           // reservados (eram sobrancelhas, retiradas): nada a pintar
                case 3 -> {                                                                                // bigodes de gato: dois riscos em cada bochecha
                    for (int x : new int[]{0, 1}) { mark(x, 5, ink, 0.60); mark(x, 7, ink, 0.60); }
                    for (int x : new int[]{6, 7}) { mark(x, 5, ink, 0.60); mark(x, 7, ink, 0.60); }
                    mark(3, 5, 0xFFE0707A, 0.55); mark(4, 5, 0xFFE0707A, 0.55);                              // focinho rosado
                }
                case 4 -> { mark(3, 6, tooth, 0.92); mark(4, 6, tooth, 0.92); markTone(2, 6, -2, 0.0); markTone(5, 6, -2, 0.0); }   // sorriso de dentes
                case 5 -> { mark(3, 6, tooth, 0.92); mark(4, 6, 0xFFE0B040, 0.96); markTone(2, 6, -2, 0.0); markTone(5, 6, -2, 0.0); }   // dente de ouro
                case 6 -> { markTone(5, 5, -1, 0.0); markTone(6, 6, -2, 0.0); markTone(2, 7, -1, 0.0); }   // sorriso torto: um canto da boca sobe
                case 7 -> {                                                                                // bochechas fundas: sombra sob as maçãs e brilho nelas
                    markTone(1, 6, -2, 0.0); markTone(2, 7, -2, 0.0); markTone(5, 7, -2, 0.0); markTone(6, 6, -2, 0.0);
                    markTone(1, 5, 1, 0.1); markTone(6, 5, 1, 0.1);
                }
                case 8 -> { final int c = paintRgb != 0 ? paintRgb : 0xFF1E1E24; for (int x = 0; x < 8; x++) mark(x, 5, c, 0.88); }   // faixa de guerra de lado a lado
                case 9 -> {                                                                                // listra no olho: uma linha vertical por cima do olho direito
                    final int c = paintRgb != 0 ? paintRgb : 0xFFD9472B;
                    for (int y = 2; y <= 7; y++) mark(6, y, c, 0.9);
                }
                case 10 -> { final int c = paintRgb != 0 ? paintRgb : 0xFFD9472B; mark(3, 2, c, 0.92); mark(4, 2, c, 0.92); }   // marca na testa
                case 11 -> {                                                                               // raio na bochecha direita
                    final int c = paintRgb != 0 ? paintRgb : 0xFFF3C531;
                    mark(6, 5, c, 0.95); mark(5, 6, c, 0.95); mark(6, 6, c, 0.95); mark(5, 7, c, 0.95);
                }
                default -> {                                                                               // coração na bochecha direita
                    final int c = paintRgb != 0 ? paintRgb : 0xFFE0508C;
                    mark(5, 5, c, 0.95); mark(7, 5, c, 0.95); mark(5, 6, c, 0.95); mark(6, 6, c, 0.95); mark(7, 6, c, 0.95); mark(6, 7, c, 0.95);
                }
            }
        }
        markZone = null;
    }

    /**
     * Cicatrizes, marcas e tatuagens novas (bits {@link CharacterOptions#EXTRA_BIT_BASE} em diante, e = bit - 26). Todas se medem pelo
     * desenho do olho escolhido ({@link #eyeTop}) e usam {@link #mark}: só pintam pele nua, nunca em cima de olho, cabelo ou barba, e
     * seguem o tom de pele escolhido. Os bits 42, 44 e 46 (curativo na testa, batom escuro e brilho labial) ficam em {@link #adornments}.
     */
    private void extraMarks(long mask, int eyeStyle, int iris) {
        final int tl = eyeTop(eyeStyle, 0), tr = eyeTop(eyeStyle, 1);
        final int sk = skinAt(1, 6) & 0xFFFFFF;                    // tinta escura nas peles claras; nas escuras (onde a tinta preta some) uma tinta azul-acinzentada clara
        final double luma = 0.299 * (sk >> 16 & 255) + 0.587 * (sk >> 8 & 255) + 0.114 * (sk & 255);
        final int ink = luma < 95 ? 0xFF8FA8C8 : 0xFF22272F;
        markZone = eyeZone(eyeStyle);                              // as marcas novas nunca encostam em pixel que vira olho ao piscar ou olhar: ficam paradas
        for (int e = 0; e < CharacterOptions.EXTRA_COUNT; e++) {
            if ((mask & (1L << (CharacterOptions.EXTRA_BIT_BASE + e))) == 0) continue;
            switch (e) {
                case 0 -> faceScar(new int[][]{{3, tl - 3}, {2, tl - 2}, {2, tl - 1}, {2, tl}}, SC_HEALED);   // talho que corta a sobrancelha esquerda
                case 1 -> faceScar(new int[][]{{3, 5}, {3, 6}, {3, 7}, {2, 7}}, SC_HEALED);                  // lábio partido: desce do nariz, cruza o lábio (que fica intacto) e vira no queixo
                case 2 -> faceScar(new int[][]{{1, 7}, {2, 7}, {3, 7}, {4, 7}, {5, 6}}, SC_HEALED);          // cicatriz no queixo, subindo para o canto da boca
                case 3 -> {                                                                      // garras: três riscos paralelos e levantados na bochecha esquerda
                    faceScar(new int[][]{{0, 4}, {0, 5}, {1, 6}, {1, 7}}, SC_RAISED);
                    faceScar(new int[][]{{2, 5}, {2, 6}, {3, 7}}, SC_RAISED);
                }
                case 4 -> faceScar(new int[][]{{5, 0}, {5, 1}, {4, 2}, {4, 3}}, SC_HEALED);                  // cicatriz na testa, em diagonal
                case 5 -> burnPatch(BURN_CHEEK, luma < 95, 5);                                   // queimadura: pele repuxada, rosada e brilhante na bochecha direita
                case 6 -> faceScar(new int[][]{{1, 5}, {1, 6}, {1, 7}}, SC_STITCH);                // pontos: costura vertical com fios cruzando
                case 7 -> {                                                                      // marca de nascença: mancha vinho na bochecha direita
                    final int wine = 0xFF6E2F2A;
                    mark(7, 5, wine, 0.72); mark(7, 6, wine, 0.62); mark(6, 6, wine, 0.46); mark(6, 5, wine, 0.28);
                }
                case 8 -> {                                                                      // tatuagem tribal: zigue-zague de tinta na bochecha esquerda
                    mark(0, 5, ink, 0.85); mark(1, 5, ink, 0.85); mark(1, 6, ink, 0.85);
                    mark(2, 6, ink, 0.85); mark(2, 7, ink, 0.85);
                }
                case 9 -> {                                                                      // três pontinhos tatuados sob o olho esquerdo
                    mark(1, 5, ink, 0.85); mark(2, 6, ink, 0.85); mark(1, 7, ink, 0.85);
                }
                case 10 -> {                                                                     // listras azuis, cor do mar, nas duas bochechas
                    final int blue = paintRgb != 0 ? paintRgb : 0xFF2F6FDB;
                    for (int y = 5; y <= 7; y++) {
                        mark(0, y, blue, 0.88); mark(2, y, blue, y == 7 ? 0.70 : 0.88);
                        mark(5, y, blue, y == 7 ? 0.70 : 0.88); mark(7, y, blue, 0.88);
                    }
                }
                case 11 -> {                                                                     // estrela pintada na bochecha direita: cruz amarela com o centro claro
                    final int gold = 0xFFF3C531;
                    mark(6, 5, gold, 0.95); mark(5, 6, gold, 0.95); mark(7, 6, gold, 0.95); mark(6, 7, gold, 0.95);
                    mark(6, 6, 0xFFFFF2B0, 0.95);
                }
                case 12 -> {                                                                     // barba por fazer: pontinhos escuros no queixo e no maxilar (em xadrez)
                    for (int y = 6; y <= 7; y++) for (int x = 1; x <= 6; x++) if (((x + y) & 1) == 0) markTone(x, y, -2, 0.0);
                    markTone(0, 6, -1, 0.0); markTone(7, 6, -1, 0.0);
                }
                case 13 -> {                                                                     // olho roxo: hematoma em volta do olho direito, amarelando nas bordas
                    final int bruise = 0xFF5B3F86, fade = 0xFF9E8F3A;
                    mark(6, 5, bruise, 0.55); mark(7, 5, bruise, 0.50); mark(5, 5, bruise, 0.38);
                    mark(7, 4, bruise, 0.45); mark(5, tr - 1, bruise, 0.22); mark(6, tr - 1, bruise, 0.22);
                    mark(7, 6, fade, 0.32); mark(5, 6, fade, 0.26); mark(6, 6, fade, 0.22);
                }
                case 14 -> {                                                                     // fuligem: borrões de pólvora espalhados
                    final int soot = 0xFF262626;
                    mark(1, 5, soot, 0.38); mark(2, 5, soot, 0.25); mark(6, 6, soot, 0.40);
                    mark(5, 7, soot, 0.30); mark(2, 7, soot, 0.22); mark(7, 5, soot, 0.28); mark(0, 6, soot, 0.20);
                }
                case 15 -> faceScar(new int[][]{{5, 5}, {6, 6}, {7, 6}}, SC_FRESH);              // corte recente: vermelho vivo, pele inchada em volta e um fio de sangue escorrendo
                case 17 -> {                                                                     // sombra nos olhos: uma linha na cor da íris acima de cada olho
                    for (int side = 0; side < 2; side++) {
                        final int t = side == 0 ? tl : tr;
                        final int[] c = eyeTopCols(eyeStyle, side);
                        final int cmin = c == null ? (side == 0 ? 1 : 5) : c[0], cmax = c == null ? cmin + 1 : c[1];
                        for (int x = cmin; x <= cmax; x++) mark(x, shadowRow(x, t - 1), iris, 0.66);
                        final int ex = side == 0 ? cmin - 1 : cmax + 1;
                        mark(ex, shadowRow(ex, t - 1), iris, 0.40);                              // a ponta de fora estica um pouco
                    }
                }
                case 19 -> {                                                                     // rosto gelado: ponta do nariz e bochechas vermelhas de frio (a linha 6 nunca é olho)
                    mark(3, 5, 0xFFE0707A, 0.55); mark(4, 5, 0xFFE0707A, 0.62);
                    for (int x : new int[]{1, 2, 5, 6}) mark(x, 6, 0xFFE0707A, 0.36);
                }
                default -> { }                                                                   // 16, 18 e 20 são pintados em adornments()
            }
        }
        burnMarks(mask, luma < 95);
        markZone = null;
    }

    /**
     * Queimaduras grandes do rosto (bits {@link CharacterOptions#BURN_EYE_BIT} e {@link CharacterOptions#BURN_FACE_BIT}). Letras do desenho:
     * {@code R} carne viva, {@code D} parte mais funda, {@code T} pele repuxada em volta, {@code P} tecido já curado e claro, {@code .} nada.
     * Só pintam pele nua (cabelo, barba e olhos ficam intactos, como em {@link #mark}) e transbordam um pouco para a lateral da cabeça.
     */
    private static final String[] BURN_EYE = {
            "TT......",
            "TPT.TT..",
            "PRPTPPT.",
            "TRDRPRPT",
            "PDDDRPT.",
            "TRDRPT..",
            ".TPPT...",
            "..TT...."};
    private static final String[] BURN_FACE = {
            "....PTT.",
            "...TPRTT",
            "..TPRDPT",
            ".TPRDDRP",
            ".TTDDRPT",
            "..TPRDPT",
            "..TTPRT.",
            "....TT.."};
    /** Queimadura pequena da bochecha direita (a classe Cicatrizes: Queimadura). */
    private static final String[] BURN_CHEEK = {
            "........",
            "........",
            "........",
            "......T.",
            ".....TPT",
            "....TPRP",
            "....PRDT",
            ".....TPT"};

    private void burnMarks(long mask, boolean darkSkin) {
        if ((mask & (1L << CharacterOptions.BURN_EYE_BIT)) != 0) {
            burnPatch(BURN_EYE, darkSkin, 11);
            // têmpora: a queimadura passa para a lateral da cabeça (o lado que encosta na frente é o x maior da face RIGHT)
            final int[] c = burnColors(darkSkin);
            burnSide(Face.RIGHT, 7, 3, c[2], 0.45); burnSide(Face.RIGHT, 7, 4, c[0], 0.60); burnSide(Face.RIGHT, 7, 5, c[0], 0.52);
            burnSide(Face.RIGHT, 6, 4, c[2], 0.42); burnSide(Face.RIGHT, 6, 5, c[2], 0.34); burnSide(Face.RIGHT, 5, 4, c[3], 0.30);
        }
        if ((mask & (1L << CharacterOptions.BURN_FACE_BIT)) != 0) {
            burnPatch(BURN_FACE, darkSkin, 23);
            final int[] c = burnColors(darkSkin);
            // lado direito de quem olha: o que encosta na frente é o x menor da face LEFT
            burnSide(Face.LEFT, 0, 2, c[2], 0.40); burnSide(Face.LEFT, 0, 3, c[0], 0.58); burnSide(Face.LEFT, 0, 4, c[1], 0.62);
            burnSide(Face.LEFT, 0, 5, c[0], 0.60); burnSide(Face.LEFT, 0, 6, c[0], 0.55); burnSide(Face.LEFT, 0, 7, c[2], 0.45);
            burnSide(Face.LEFT, 1, 4, c[0], 0.50); burnSide(Face.LEFT, 1, 5, c[2], 0.42); burnSide(Face.LEFT, 1, 6, c[0], 0.46);
            burnSide(Face.LEFT, 2, 5, c[2], 0.34); burnSide(Face.LEFT, 2, 6, c[2], 0.32); burnSide(Face.LEFT, 1, 3, c[3], 0.30);
        }
    }

    /** Cores da queimadura {red, deep, tissue, pale}; nas peles escuras o vermelho some, então vira tons mais claros e rosados. */
    private static int[] burnColors(boolean darkSkin) {
        return darkSkin ? new int[]{0xFFD07A84, 0xFFA25058, 0xFFD9A090, 0xFFEBCABE} : new int[]{0xFFC4606A, 0xFF8E3C3C, 0xFFE4A094, 0xFFF4D4C8};
    }

    /**
     * Pinta uma queimadura de desenho 8 x 8 (letras: {@code D} parte mais funda, {@code R} carne viva, {@code P} tecido novo e brilhante,
     * {@code T} borda repuxada, {@code .} nada). O miolo é liso e vai clareando para a borda (queimadura de verdade tem degradê, não
     * ruído); só a borda tem um leve pontilhado, e um brilho de pele esticada cai no alto de cada mancha clara.
     */
    private void burnPatch(String[] grid, boolean darkSkin, int seed) {
        final int[] c = burnColors(darkSkin);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            final char ch = grid[y].charAt(x);
            if (ch == '.') continue;
            final double v = PxNoise.rnd(x, y, 7, seed);                      // 0 a 1, sempre o mesmo para o mesmo pixel
            switch (ch) {
                case 'D' -> mark(x, y, c[1], 0.66);
                case 'R' -> mark(x, y, c[0], 0.62 + 0.06 * v);
                case 'P' -> mark(x, y, y > 0 && grid[y - 1].charAt(x) == '.' ? blend(c[3], 0xFFFFFFFF, 0.35) : c[3], 0.54);
                default -> mark(x, y, c[2], v < 0.5 ? 0.34 : 0.26);            // borda: pontilhado suave
            }
        }
    }

    /** Pixel de queimadura nas laterais da cabeça: só em pele nua. */
    private void burnSide(Face f, int x, int y, int color, double k) {
        final int[] r = HEAD.rect(f);
        if (x < 0 || y < 0 || x >= r[2] || y >= r[3]) return;
        final int idx = (r[1] + y) * SIZE + r[0] + x;
        if (px[idx] != base[idx]) return;
        px[idx] = blend(base[idx], color, k);
    }

    /** Linha da sombra dos olhos na coluna {@code x}: {@code row} ou, se ali o olho se mexe (piscar, olhar), a primeira linha acima livre. */
    private int shadowRow(int x, int row) {
        while (row > 0 && x >= 0 && x <= 7 && markZone != null && markZone[row * 8 + x]) row--;
        return row;
    }

    private static final java.util.concurrent.ConcurrentHashMap<Integer, boolean[]> EYE_ZONES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Os pixels do rosto (8 x 8, índice y * 8 + x) que são olho em ALGUMA fase do piscar (aberto, meio, fechado) ou direção do olhar
     * do estilo de olhos {@code eyeStyle}. Calculado desenhando o olho em cada estado e juntando os pixels marcados; fica em cache.
     */
    private static boolean[] eyeZone(int eyeStyle) {
        return EYE_ZONES.computeIfAbsent(eyeStyle, st -> {
            final boolean[] zone = new boolean[64];
            final int[] r = HEAD.rect(Face.FRONT);
            for (int b = 0; b <= 2; b++) for (int gx = -1; gx <= 1; gx++) for (int gy = -1; gy <= 1; gy++) {
                SkinPainter q = new SkinPainter(false);
                q.blink = b; q.gazeX = gx; q.gazeY = gy;
                q.eyes(st, 0xFF2B2B2B, 0xFF111111);
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) if (q.eyeMask[(r[1] + y) * SIZE + r[0] + x]) zone[y * 8 + x] = true;
            }
            return zone;
        });
    }

    // ------------------------------------------------------------------------------------------ utilidades

    /** Pinta a face {@code f} com um mapa de sombreado ({@link BodyShading}); {@code flip} espelha o mapa na horizontal. */
    void stamp(Part part, Face f, String[] map, int skin, boolean flip) {
        int[] r = part.rect(f);
        if (map.length != r[3]) throw new IllegalStateException("mapa com " + map.length + " linhas, face com " + r[3] + " (" + f + ")");
        for (int y = 0; y < r[3]; y++) {
            if (map[y].length() != r[2]) throw new IllegalStateException("mapa com linha de " + map[y].length() + ", face com " + r[2] + " (" + f + ", linha " + y + ")");
            for (int x = 0; x < r[2]; x++) {
                int mx = flip ? r[2] - 1 - x : x;
                px[(r[1] + y) * SIZE + r[0] + x] = BodyShading.colorAt(map, mx, y, skin);
            }
        }
    }

    /** Devolve a pele nua (sombreada) nas linhas r0..r1 das 4 faces laterais da parte. */
    private void bareRows(Part part, int r0, int r1) {
        for (Face f : SIDES) {
            int[] r = part.rect(f);
            for (int y = r0; y <= r1 && y < r[3]; y++)
                for (int x = 0; x < r[2]; x++) px[(r[1] + y) * SIZE + r[0] + x] = base[(r[1] + y) * SIZE + r[0] + x];
        }
    }

    /** Devolve a pele nua (sombreada) na face inteira. */
    private void bareFace(Part part, Face f) {
        int[] r = part.rect(f);
        for (int y = 0; y < r[3]; y++)
            for (int x = 0; x < r[2]; x++) px[(r[1] + y) * SIZE + r[0] + x] = base[(r[1] + y) * SIZE + r[0] + x];
    }

    /** Põe em (x, y) de {@code dst} a pele nua que está em (x, y) de {@code src} (mesma face): serve para abrir um decote numa camada externa. */
    private void bareSet(Part dst, Part src, Face f, int x, int y) {
        int[] rs = src.rect(f), rd = dst.rect(f);
        if (x < 0 || y < 0 || x >= rd[2] || y >= rd[3]) return;
        px[(rd[1] + y) * SIZE + rd[0] + x] = base[(rs[1] + y) * SIZE + rs[0] + x];
    }

    /** Lê um pixel da folha da skin (coordenadas absolutas). */
    int getPx(int x, int y) {
        return px[y * SIZE + x];
    }

    /** Canto (x0, y0, z0) da parte no sistema de {@link #pos}. */
    private double[] origin(Part part) {
        double x0, y0 = 0, z0 = 0;
        int u = part.u(), v = part.v();
        if (u == 0 && v == 0 || u == 32 && v == 0) { x0 = 0; y0 = -8; z0 = -2; }                       // cabeça e chapéu
        else if (u == 16 && (v == 16 || v == 32)) x0 = 0;                                                // tronco e casaco
        else if (u == 40 && (v == 16 || v == 32)) x0 = -part.w();                                        // braço direito e manga
        else if ((u == 32 || u == 48) && v == 48) x0 = 8;                                                // braço esquerdo e manga
        else if (u == 0 && (v == 16 || v == 32)) { x0 = 0; y0 = 12; }                                   // perna direita e calça
        else { x0 = 4; y0 = 12; }                                                                        // perna esquerda e calça
        return new double[]{x0, y0, z0};
    }

    /**
     * Posição 3D do centro do pixel (i, j) da face {@code f} de {@code part}, num sistema comum a todas as partes:
     * x cresce para a direita de quem olha de frente (o braço direito do personagem fica em x &lt; 0), y cresce para baixo
     * (topo do tronco = 0, pés = 24) e z vai da frente do tronco (0) para as costas (4). A camada externa usa a mesma
     * posição da camada de baixo. Serve para ruído e sombras que continuam de uma face para a vizinha.
     */
    double[] pos(Part part, Face f, int i, int j) {
        double[] o = origin(part);
        double x0 = o[0], y0 = o[1], z0 = o[2];
        double x1 = x0 + part.w(), y1 = y0 + part.h(), z1 = z0 + part.d();
        double a = i + 0.5, b = j + 0.5;
        return switch (f) {
            case FRONT -> new double[]{x0 + a, y0 + b, z0};
            case BACK -> new double[]{x1 - a, y0 + b, z1};
            case RIGHT -> new double[]{x0, y0 + b, z1 - a};
            case LEFT -> new double[]{x1, y0 + b, z0 + a};
            case TOP -> new double[]{x0 + a, y0, z1 - b};
            case BOTTOM -> new double[]{x0 + a, y1, z0 + b};
        };
    }

    // ------------------------------------------------------------------------------------------ tecido

    /** Luz vinda da esquerda de quem olha e da frente (mesma do sombreado da pele). */
    private static final double LX = -0.54, LZ = -0.84;

    /** Luz (−1 a 1) num ponto da superfície de uma parte, tratando-a como um cilindro achatado: esquerda e frente claras, direita e costas escuras. */
    private double cylLight(Part part, double[] q) {
        double[] o = origin(part);
        double ex = (q[0] - (o[0] + part.w() / 2.0)) / (part.w() / 2.0), ez = (q[2] - (o[2] + part.d() / 2.0)) / (part.d() / 2.0);
        double len = Math.hypot(ex, ez);
        return len < 1e-6 ? 0 : ex / len * LX + ez / len * LZ;
    }

    /** Dobra de tecido no ponto: -1 = vinco escuro, +1 = borda clara do vinco, 0 = liso. Linhas finas que correm em diagonal. */
    private static int fold(double[] q, int seed, boolean boost) {
        double f = PxNoise.v3(q[0] * 0.60 + q[1] * 0.42, q[1] * 0.34 - q[0] * 0.28, q[2] * 0.60, seed);
        double d = Math.abs(f - 0.5);
        if (d < (boost ? 0.020 : 0.014)) return -1;
        if (f > 0.5 && d < (boost ? 0.075 : 0.036)) return 1;
        return 0;
    }

    /** Clareia (nível &gt; 0, rumo ao branco) ou escurece (nível &lt; 0, mais saturado) a cor de um tecido. */
    static int clothTone(int argb, int level) {
        if (level == 0) return argb;
        double r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        if (level > 0) {
            double t = 0.11 * level;
            r += (255 - r) * t; g += (250 - g) * t; b += (244 - b) * t;
        } else {
            int n = -level;
            double avg = (r + g + b) / 3.0, sat = 1.0 + 0.08 * n, f = 1.0 - 0.15 * n;
            r = (avg + (r - avg) * sat) * f; g = (avg + (g - avg) * sat) * f; b = (avg + (b - avg) * sat) * f;
        }
        return (argb & 0xFF000000) | (cl(r) << 16) | (cl(g) << 8) | cl(b);
    }

    private static int cl(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v)));
    }

    /**
     * Dá volume a tudo que as roupas pintaram (em qualquer camada): luz de cilindro, dobras e um tom mais claro no topo
     * das partes. A pele nua devolvida das roupas (igual a {@link #base}) fica como está.
     */
    private void fabricPass() {
        Part[] all = {BODY, JACKET, RARM, LARM, RSLEEVE, LSLEEVE, RLEG, LLEG, RPANTS, LPANTS};
        for (Part part : all) for (Face f : ALL) {
            int[] r = part.rect(f);
            for (int j = 0; j < r[3]; j++) for (int i = 0; i < r[2]; i++) {
                int idx = (r[1] + j) * SIZE + r[0] + i;
                int c = px[idx];
                if ((c >>> 24) == 0 || c == base[idx]) continue;
                double[] q = pos(part, f, i, j);
                double lvl = f == Face.TOP ? 1.0 : f == Face.BOTTOM ? -1.0 : Math.max(-1.0, Math.min(1.0, cylLight(part, q) * 1.25 - 0.1));
                int fo = (f == Face.TOP || f == Face.BOTTOM || q[1] < 8.5) ? 0 : fold(q, 41, starterMale);   // vincos só do quadril para baixo (calção, calça, barra do casaco)
                px[idx] = clothTone(c, Math.max(-2, Math.min(1, (int) Math.round(lvl) + fo)));
            }
        }
    }

    /** Escreve um pixel direto na folha da skin (coordenadas absolutas). */
    void setPx(int x, int y, int argb) {
        px[y * SIZE + x] = argb;
    }

    void set(Part part, Face f, int x, int y, int argb) {
        int[] r = part.rect(f);
        if (x < 0 || y < 0 || x >= r[2] || y >= r[3]) return;
        if (protectEyes && eyeMask[(r[1] + y) * SIZE + r[0] + x]) return;
        px[(r[1] + y) * SIZE + r[0] + x] = argb;
    }

    private void face(Part part, Face f, int argb) {
        int[] r = part.rect(f);
        fillRect(r[0], r[1], r[2], r[3], argb);
    }

    private void all(Part part, int argb) {
        for (Face f : ALL) face(part, f, argb);
    }

    /** Linhas r0..r1 (inclusive) das 4 faces laterais. */
    private void rows(Part part, int r0, int r1, int argb) {
        for (Face f : SIDES) {
            int[] r = part.rect(f);
            for (int y = r0; y <= r1 && y < r[3]; y++) fillRect(r[0], r[1] + y, r[2], 1, argb);
        }
    }

    private void rowsOf(Part part, int y, int argb) {
        rows(part, y, y, argb);
    }

    private void fillRect(int x, int y, int w, int h, int argb) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) px[j * SIZE + i] = argb;
    }

    /** Multiplica o brilho de uma cor (mantém o alfa). */
    static int shade(int argb, double k) {
        int r = (int) Math.min(255, ((argb >> 16) & 0xFF) * k);
        int g = (int) Math.min(255, ((argb >> 8) & 0xFF) * k);
        int b = (int) Math.min(255, (argb & 0xFF) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static int blend(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
