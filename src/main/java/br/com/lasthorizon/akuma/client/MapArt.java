package br.com.lasthorizon.akuma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/**
 * Arte do "mapa" da criação de personagem.
 *
 * <p>O mapa em si é uma textura pixel art ({@code textures/gui/map_sheet.png}, gerada por
 * {@code tools/art/make_map_sheet.py}): pergaminho rasgado com sombreado em pontilhado, dobras, curvas de nível e
 * costas, uma rota vermelha pontilhada com X pela dobra do meio, rosa dos ventos, navio, montanhas e dois rolos
 * de papel creme nas laterais (cilindro chapado com contorno marrom, cintura e espiral embaixo, por {@code tools/art/make_scrolls.py}). Em cima dela o código desenha, com {@code fill()}, tudo o que reage ao mouse: botões, abas de
 * marcador, amostras de cor, faixa de título, retrato e selo. Atrás do mapa não há nada além de um escurecimento da tela.
 *
 * <p>As coordenadas são do "espaço do mapa": (0,0) é o canto da textura ({@link #BW} x {@link #BH}); a folha ocupa
 * {@link #MX},{@link #MY} {@link #MW} x {@link #MH} e os rolos ficam em x = 24..40 e 368..384.
 */
public final class MapArt {
    private MapArt() {}

    public static final int BW = 392, BH = 224;
    /** A folha de pergaminho dentro da área total. */
    public static final int MX = 36, MY = 6, MW = 336, MH = 204;
    /** Dobra vertical do meio da folha. */
    public static final int FOLD = MX + MW / 2;
    /** Painéis: esquerdo (retrato, nome, finalizar) e direito (a aba escolhida): x do canto e largura. */
    public static final int LPX = 48, LPW = 144, RPX = 216, RPW = 144;

    private static final ResourceLocation SHEET = new ResourceLocation("lasthorizon", "textures/gui/map_sheet.png");

    // ------------------------------------------------------------------------------------------ paleta
    public static final int INK = 0xFF3B2815, INK_SOFT = 0xFF45301A, INK_FAINT = 0xFF523A20, CREAM = 0xFFF6E9C4;
    public static final int GOLD = 0xFFD9A441, GOLD_LIGHT = 0xFFFFE9A0, GOLD_DARK = 0xFF8A5A14;
    public static final int RED = 0xFFA82E22, GREEN = 0xFF2F7A3A, ORANGE = 0xFFB4640E, SLATE = 0xFF4E6A88;
    private static final int PAPER = 0xFFE8D3A2, DARK = 0xFF140A05, LEATHER = 0xFF3A2314;

    // ------------------------------------------------------------------------------------------ alfa global da tela

    /**
     * Opacidade de TODA a tela de criação (1 = normal; cai no fade final do selo). Quem mexe na cor do shader no meio do
     * quadro (marca-d'água) e quem desenha por fora dela (o boneco 3D) tem que respeitar este valor, senão fica de fora do fade.
     */
    private static float baseAlpha = 1.0F;

    public static void setBaseAlpha(float a) {
        baseAlpha = Math.max(0.0F, Math.min(1.0F, a));
    }

    public static float baseAlpha() {
        return baseAlpha;
    }

    // ------------------------------------------------------------------------------------------ primitivas

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int argb) {
        if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, argb);
    }

    /** Contorno de 1 px. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int argb) {
        rect(g, x, y, w, 1, argb);
        rect(g, x, y + h - 1, w, 1, argb);
        rect(g, x, y + 1, 1, h - 2, argb);
        rect(g, x + w - 1, y + 1, 1, h - 2, argb);
    }

    /** Retângulo sem os 4 pixels dos cantos (as faixas não se sobrepõem, então também serve com alfa). */
    public static void notched(GuiGraphics g, int x, int y, int w, int h, int argb) {
        rect(g, x + 1, y, w - 2, 1, argb);
        rect(g, x, y + 1, w, h - 2, argb);
        rect(g, x + 1, y + h - 1, w - 2, 1, argb);
    }

    /** Multiplica o alfa de uma cor por {@code k} (0 a 1). */
    public static int fade(int argb, double k) {
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0D, Math.min(1.0D, k)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Losango de 3 x 3 (um "mais" sem o miolo repetido), centrado em (cx, cy). */
    public static void diamond(GuiGraphics g, int cx, int cy, int argb) {
        rect(g, cx, cy - 1, 1, 3, argb);
        rect(g, cx - 1, cy, 3, 1, argb);
    }

    /**
     * Brilho de hover: halo de duas camadas em volta do retângulo (pulsa devagar) e um fio de luz logo dentro do topo.
     * Pensado para fundos escuros ou claros: o halo é ouro translúcido.
     */
    public static void hoverGlow(GuiGraphics g, int x, int y, int w, int h, long t) {
        double p = 0.5D + 0.5D * Math.sin(t / 260.0D);
        outline(g, x - 1, y - 1, w + 2, h + 2, argb(78 + (int) (42 * p), 0xFFD27A));
        outline(g, x - 2, y - 2, w + 4, h + 4, argb(20 + (int) (18 * p), 0xFFD27A));
        rect(g, x + 2, y + 1, w - 4, 1, argb(70, 0xFFF0C0));
    }

    /**
     * Reflexo diagonal que cruza o retângulo de tempos em tempos enquanto o mouse está em cima (recortado no retângulo).
     * Descansa a maior parte do ciclo, como o reflexo do botão de finalizar.
     */
    public static void sweep(GuiGraphics g, int x, int y, int w, int h, long t, int alpha) {
        long period = 1700L;
        double u = (t % period) / 620.0D;
        if (u >= 1.0D || w < 8 || h < 6) return;
        double e = u * u * (3.0D - 2.0D * u);
        int head = x - 6 - h + (int) Math.round(e * (w + 12 + h));
        for (int r = 0; r < h; r++) {
            int c = head + (h - 1 - r);
            for (int d = -2; d <= 2; d++) {
                int px = c + d;
                if (px < x || px >= x + w) continue;
                rect(g, px, y + r, 1, 1, argb(alpha * (3 - Math.abs(d)) / 3, 0xFFFFFF));
            }
        }
    }

    /** Disco de raio r (pixel art) centrado em (cx, cy). */
    public static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5D));
            rect(g, cx - half, cy + dy, half * 2 + 1, 1, argb);
        }
    }

    /** Triângulo "play" apontando para a esquerda ou a direita, com size linhas (ímpar). */
    public static void arrow(GuiGraphics g, int x, int y, int size, boolean left, int argb) {
        int mid = size / 2;
        for (int r = 0; r < size; r++) {
            int len = mid + 1 - Math.abs(r - mid);
            rect(g, left ? x + (mid + 1 - len) : x, y + r, len, 1, argb);
        }
    }

    public static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gg = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    /** Ouro que pulsa (mesmo ritmo do tooltip e da moldura das conquistas). */
    public static int goldPulse(long t) {
        double wave = 0.5D - 0.5D * Math.cos((t % SeaFrame.PERIOD_MS) / (double) SeaFrame.PERIOD_MS * Math.PI * 2.0D);
        return lerp(GOLD, GOLD_LIGHT, wave);
    }

    // ------------------------------------------------------------------------------------------ sprites

    private static int pal(char c) {
        return switch (c) {
            case 'K' -> 0xFF1E120A;
            case 'H' -> GOLD_LIGHT;
            case 'G' -> GOLD;
            case 'S' -> GOLD_DARK;
            case 'W' -> 0xFFFFFFFF;
            default -> 0;
        };
    }

    /** Desenha um sprite de letras (K contorno, H luz, G ouro, S sombra, W branco), espelhável. Uma fill por trecho. */
    public static void sprite(GuiGraphics g, String[] rows, int x, int y, boolean flipX, boolean flipY) {
        int w = rows[0].length(), h = rows.length;
        for (int r = 0; r < h; r++) {
            String row = rows[flipY ? h - 1 - r : r];
            int c = 0;
            while (c < w) {
                char ch = row.charAt(flipX ? w - 1 - c : c);
                int run = 1;
                while (c + run < w && row.charAt(flipX ? w - 1 - (c + run) : c + run) == ch) run++;
                int col = pal(ch);
                if (col != 0) rect(g, x + c, y + r, run, 1, col);
                c += run;
            }
        }
    }

    /** Sprite de uma cor só: 'X' é pintado, o resto fica transparente. */
    public static void mono(GuiGraphics g, String[] rows, int x, int y, int argb) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != 'X') { c++; continue; }
                int run = 1;
                while (c + run < row.length() && row.charAt(c + run) == 'X') run++;
                rect(g, x + c, y + r, run, 1, argb);
                c += run;
            }
        }
    }

    private static final String[] CURL = {              // arabesco dos cantos do cartão (10 x 10)
            ".GGGGGGGGG",
            "GGSSSSSSSS",
            "GS.G......",
            "GS.GG.....",
            "GS..GG....",
            "GS...G....",
            "GS........",
            "GS........",
            "GS........",
            "G........."};

    private static final String[] CORNER = {            // cantoneira do retrato (12 x 12)
            "KKKKKKKKKK..",
            "KHHHHHHHHK..",
            "KHGGGGGGGS..",
            "KHGSSSSSS...",
            "KHGS........",
            "KHGS.GG.....",
            "KHGS.GHG....",
            "KHGS.GGG....",
            "KHGS........",
            "KHG.........",
            "KKS.........",
            "............"};

    public static final String[] CHECK = {"......X", ".....XX", "X...XX.", "XX.XX..", ".XXX...", "..X...."};
    public static final String[] MARS = {".....XXXX", ".......XX", "......X.X", "..XXXX..X", ".X...X...", ".X...X...", ".X...X...", "..XXX...."};
    public static final String[] VENUS = {"...XXX...", "..X...X..", "..X...X..", "..X...X..", "...XXX...", "....X....", "....X....", "..XXXXX..", "....X...."};
    private static final String[] S_OK = {"....X", "...X.", "X.X..", ".X..."};
    private static final String[] S_BAD = {"X...X", ".X.X.", "..X..", ".X.X.", "X...X"};
    private static final String[] S_WARN = {"..X..", "..X..", "..X..", ".....", "..X.."};
    private static final String[] S_INFO = {"..X..", ".....", "..X..", "..X..", "..X.."};
    private static final String[] STAR = {"....X....", "....X....", "...XXX...", "..XXXXX..", "XXXXXXXXX", "..XXXXX..", "...XXX...", "....X....", "....X...."};
    private static final String[] ARROW_SMALL = {"X.", "XX", "XXX", "XX", "X."};

    /** Ícones dos botões do retrato (10 x 10): lupa com mais, lupa com menos e seta circular (girar o personagem). */
    public static final String[] PLUS = {".XXXXX....", "X.....X...", "X..X..X...", "X.XXX.X...", "X..X..X...", "X.....X...", ".XXXXXXX..", "......XXX.", ".......XXX", "........XX"};
    public static final String[] MINUS = {".XXXXX....", "X.....X...", "X.....X...", "X.XXX.X...", "X.....X...", "X.....X...", ".XXXXXXX..", "......XXX.", ".......XXX", "........XX"};
    public static final String[] TURN = {"..XXXXXX..", ".X......X.", "X....XXXXX", "X.....XXX.", "X......X..", "X.........", "X........X", ".X......X.", "..X....X..", "...XXXX..."};
    /** Ícone pixel-art de limpar/refazer: seta circular no sentido horário com a ponta cheia (9 x 9). */
    public static final String[] REFRESH = {"..XXXX...", ".X....XX.", "X.....XXX", "X....XXXX", "X........", "X.......X", ".X.....X.", "..X...X..", "...XXX..."};
    /** Seta curva anti-horária (mantida para outros elementos legados). */
    public static final String[] RESET = {"..XXX..", ".X...X.", "X.....X", "X.....X", "XX...X.", "XXX.XX.", "..XXX.."};
    private static final String[] G_CHECK = {"..X", "X.X", ".X."};
    private static final String[] G_BANG = {"X", "X", "X", ".", "X"};

    /** Estados de uma etapa da criação: cinza (não visitada), dourado (visitada), vermelho (falta algo obrigatório), verde (concluída). */
    public static final int ST_GRAY = 0, ST_GOLD = 1, ST_RED = 2, ST_GREEN = 3;
    private static final int[] ST_FILL = {0xFF8C8372, 0xFFD9A441, 0xFFC0392B, 0xFF3F9A52};
    private static final int[] ST_DARK = {0xFF55503F, 0xFF8A5A14, 0xFF6E160F, 0xFF1D5A2A};

    public static int stateColor(int state) {
        return ST_FILL[state];
    }

    /** Pedra redonda de 7 x 7 que mostra o estado de uma etapa (centrada em cx, cy); vermelho pulsa devagar. */
    public static void gem(GuiGraphics g, int cx, int cy, int state, long t) {
        int fill = ST_FILL[state];
        if (state == ST_RED) fill = lerp(fill, 0xFFFF7A66, 0.5D * (0.5D + 0.5D * Math.sin(t / 240.0D)));
        disc(g, cx, cy, 3, 0xFF1E120A);
        disc(g, cx, cy, 2, state == ST_GRAY ? 0xFFB9AE96 : fill);
        if (state == ST_GRAY) disc(g, cx, cy, 1, ST_FILL[0]);
        else rect(g, cx - 1, cy - 2, 2, 1, argb(150, 0xFFFFFF));
        if (state == ST_GREEN) mono(g, G_CHECK, cx - 1, cy - 1, 0xFFFFFFFF);
        else if (state == ST_RED) mono(g, G_BANG, cx, cy - 2, 0xFFFFFFFF);
    }

    /** Brilho de quatro pontas que cresce e some ({@code u} de 0 a 1; fora disso não desenha nada). */
    public static void sparkle(GuiGraphics g, int cx, int cy, double u, int rgb) {
        if (u <= 0.0D || u >= 1.0D) return;
        int r = 1 + (int) Math.round(5.0D * Math.sin(Math.PI * Math.min(1.0D, u * 1.15D)));
        int a = (int) (255 * (1.0D - u));
        rect(g, cx - r, cy, 2 * r + 1, 1, argb(a, rgb));
        rect(g, cx, cy - r, 1, 2 * r + 1, argb(a, rgb));
        rect(g, cx - 1, cy - 1, 3, 3, argb(a / 2, 0xFFFFFF));
        rect(g, cx, cy, 1, 1, argb(a, 0xFFFFFF));
        int d = Math.max(1, r / 2);                                                  // pontinhas nas diagonais
        rect(g, cx - d, cy - d, 1, 1, argb(a * 2 / 3, rgb));
        rect(g, cx + d, cy - d, 1, 1, argb(a * 2 / 3, rgb));
        rect(g, cx - d, cy + d, 1, 1, argb(a * 2 / 3, rgb));
        rect(g, cx + d, cy + d, 1, 1, argb(a * 2 / 3, rgb));
    }

    /** Plaquinha escura sem sombra: mantém o texto nítido e deixa a animação da barra respirar. */
    public static void darkPlate(GuiGraphics g, int x, int y, int w, int h, long t) {
        notched(g, x, y, w, h, 0xFF1E120A);
        notched(g, x + 1, y + 1, w - 2, h - 2, GOLD_DARK);
        notched(g, x + 2, y + 2, w - 4, h - 4, 0xFF2B1A0E);
        rect(g, x + 3, y + 1, w - 6, 1, goldPulse(t));
        rect(g, x + 3, y + 3, w - 6, 1, argb(46, 0xFFE9A0));
    }

    /**
     * Faixa de luz inclinada que atravessa a barra de progresso: varre da esquerda para a direita em ~1,1 s e descansa
     * o resto do ciclo. {@code front = false} é a luz larga e dourada que passa por trás das etapas; {@code front = true}
     * é o reflexo fino e branco que passa por cima delas. Tudo recortado na própria barra (nada vaza para o painel).
     */
    public static void progressSheen(GuiGraphics g, int x, int y, int w, int h, long t, boolean complete, boolean front) {
        long period = complete ? 2600L : 3400L;
        double u = (t % period) / 1100.0D;
        if (u >= 1.0D) return;
        double e = u * u * (3.0D - 2.0D * u);                                  // começa e termina devagar
        int hw = front ? 3 : 8;
        int head = x - hw - h + (int) Math.round(e * (w + 2 * hw + h));
        int rgb = front ? 0xFFFFFF : complete ? 0xB8F0A0 : 0xFFE9A0;
        int peak = front ? 80 : 70;
        for (int r = 0; r < h; r++) {
            int c = head + (h - 1 - r);                                        // a faixa se inclina como "/"
            for (int d = -hw; d <= hw; d++) {
                int px = c + d;
                if (px < x || px >= x + w) continue;
                double k = 1.0D - Math.abs(d) / (hw + 1.0D);
                rect(g, px, y + r, 1, 1, argb((int) (peak * k * k), rgb));
            }
        }
    }

    /** Reflexo que passa pelo ícone da aba aberta de tempos em tempos (uma diagonal fina de 2 px). */
    public static void glint(GuiGraphics g, int x, int y, int size, long t, int seed) {
        long ph = (t + seed * 420L) % 3600L;
        if (ph >= 520L) return;
        int c = (int) Math.round(ph / 520.0D * (size * 1.5D + 2.0D)) - 2;
        for (int r = 0; r < size; r++) {
            for (int k = 0; k < 2; k++) {
                int px = c - r + k;
                if (px >= 0 && px < size) rect(g, x + px, y + r, 1, 1, argb(k == 0 ? 105 : 60, 0xFFFFFF));
            }
        }
    }

    /** Botão lateral plano: sem sombra, com aro vivo apenas quando o cursor está sobre ele. */
    public static void clearButton(GuiGraphics g, int x, int y, int size, int state, long t, boolean delayedHover) {
        boolean hover = state == HOVER || state == SELECTED;
        boolean pressed = state == SELECTED;
        int edge = hover ? lerp(GOLD, GOLD_LIGHT, 0.5D + 0.5D * Math.sin(t / 220.0D)) : GOLD_DARK;
        int fill = pressed ? 0xFF3A2410 : hover ? 0xFF5D3B19 : 0xFF2B1A0E;
        notched(g, x, y, size, size, 0xFF1E120A);                       // contorno escuro igual ao da plaquinha da barra
        notched(g, x + 1, y + 1, size - 2, size - 2, edge);
        notched(g, x + 2, y + 2, size - 4, size - 4, fill);
        if (!pressed) rect(g, x + 3, y + 2, size - 6, 1, argb(hover ? 150 : 70, 0xFFE9A0));      // brilho de cima
        if (hover) rect(g, x + 3, y + size - 3, size - 6, 1, argb(60, 0xFFE9A0));                // e um fio de luz embaixo
        if (delayedHover) {
            outline(g, x - 1, y - 1, size + 2, size + 2, argb(170 + (int) (50 * (0.5D + 0.5D * Math.sin(t / 160.0D))), GOLD_LIGHT));
        }
    }

    /**
     * Seta do botão de limpar. Parada ela só tem sombra; com o mouse em cima sobe e desce 1 px; ao clicar gira uma volta
     * em quatro tempos (quartos de volta, que mantêm os pixels nítidos) freando no fim, com um brilho no centro.
     * {@code spin} vai de 0 a 1 durante o giro (fora disso, passe um valor negativo).
     */
    public static void clearIcon(GuiGraphics g, int x, int y, int size, int state, long t, double spin) {
        boolean hover = state == HOVER || state == SELECTED;
        int col = state == SELECTED ? GOLD : hover ? GOLD_LIGHT : CREAM;
        int ix = x + (size - 9) / 2, iy = y + (size - 9) / 2;
        int q = 0;
        if (spin >= 0.0D && spin < 1.0D) q = (int) Math.floor(4.0D * (1.0D - Math.pow(1.0D - spin, 3)));
        else if (hover) iy += state == SELECTED ? 1 : -(int) Math.round(0.5D + 0.5D * Math.sin(t / 150.0D));
        monoTurn(g, REFRESH, ix, iy + 1, 0xFF140A05, q);                // sombrinha
        monoTurn(g, REFRESH, ix, iy, col, q);
        if (spin >= 0.0D && spin < 1.0D) sparkle(g, x + size / 2, y + size / 2, spin, 0xFFE9A0);
    }

    /** Sprite de uma cor só (quadrado) girado em {@code quarters} quartos de volta no sentido horário. */
    public static void monoTurn(GuiGraphics g, String[] rows, int x, int y, int argb, int quarters) {
        int h = rows.length, w = rows[0].length(), q = ((quarters % 4) + 4) % 4;
        if (q == 0) { mono(g, rows, x, y, argb); return; }
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                if (rows[r].charAt(c) != 'X') continue;
                int nx = q == 1 ? h - 1 - r : q == 2 ? w - 1 - c : r;
                int ny = q == 1 ? c : q == 2 ? h - 1 - r : w - 1 - c;
                rect(g, x + nx, y + ny, 1, 1, argb);
            }
        }
    }

    /**
     * Segmento da barra de progresso (uma etapa), na cor da bandeira da aba: concluída = cor viva com brilho e um visto;
     * visitada = a mesma cor apagada; ainda não vista = pedra escura; obrigatória que falta = vermelho pulsando com "!".
     * A etapa atual leva um contorno de ouro que pulsa e fica um pixel mais alta.
     */
    public static void segment(GuiGraphics g, int x, int y, int w, int h, int state, boolean current, boolean hover, long t, int tabRgb) {
        segment(g, x, y, w, h, state, current, hover, t, tabRgb, Long.MAX_VALUE / 4);
    }

    /** Tempo que a etapa leva para se encher de cor ao ser concluída. */
    public static final long SEG_FILL_MS = 420L;

    /** Como acima; {@code sinceDone} é o tempo desde que a etapa ficou verde (ela se enche da esquerda para a direita). */
    public static void segment(GuiGraphics g, int x, int y, int w, int h, int state, boolean current, boolean hover, long t, int tabRgb, long sinceDone) {
        int tab = 0xFF000000 | tabRgb;
        boolean filling = state == ST_GREEN && sinceDone >= 0L && sinceDone < SEG_FILL_MS;
        int fill, edge;
        switch (state) {
            case ST_GREEN -> { fill = lerp(tab, 0xFFFFFFFF, 0.12D); edge = lerp(tab, 0xFF000000, 0.55D); }
            case ST_GOLD -> { fill = lerp(tab, 0xFF2B1A0E, 0.58D); edge = lerp(tab, 0xFF000000, 0.7D); }
            case ST_RED -> { fill = lerp(0xFFB52A1C, 0xFFFF7A66, 0.5D * (0.5D + 0.5D * Math.sin(t / 240.0D))); edge = 0xFF5A0F08; }
            default -> { fill = 0xFF4A4034; edge = 0xFF2A231A; }
        }
        if (hover && !current) fill = lerp(fill, 0xFFFFFFFF, 0.22D);
        if (hover && !current) outline(g, x - 2, y - 2, w + 4, h + 4, argb(70 + (int) (30 * Math.sin(t / 200.0D)), 0xFFD27A));      // halo de ouro
        // a etapa atual NÃO cresce nem vaza para os vizinhos: o contorno escuro dela vira um aro de ouro que respira
        notched(g, x - 1, y - 1, w + 2, h + 2, current ? goldPulse(t) : hover ? GOLD_LIGHT : 0xFF1E120A);
        notched(g, x, y, w, h, edge);
        notched(g, x + 1, y + 1, w - 2, h - 2, fill);
        if (filling) {                                                                                  // enchendo: a parte ainda vazia fica apagada
            int cut = (int) Math.round((double) sinceDone / SEG_FILL_MS * (w - 2));
            rect(g, x + 1 + cut, y + 1, w - 2 - cut, h - 2, lerp(tab, 0xFF2B1A0E, 0.58D));
            rect(g, x + cut, y + 1, 1, h - 2, argb(200, 0xFFFFFF));                               // a ponta da onda de cor
        }
        rect(g, x + 1, y + 1, w - 2, 1, argb(state == ST_GRAY ? 40 : 120, 0xFFFFFF));                   // brilho de cima
        if (current) {                                                                                  // reflexo que passeia pela etapa atual
            int sx = x + 1 + (int) ((t / 70L) % (w + 6)) - 3;
            for (int k = 0; k < 2; k++) {
                int px = sx + k;
                if (px > x && px < x + w - 1) rect(g, px, y + 1, 1, h - 2, argb(k == 0 ? 90 : 50, 0xFFFFFF));
            }
        }
        // ícones do segmento (12 x 8): ambos têm largura par e ficam centrados na vertical entre o brilho de cima e a borda
        int iy = y + 1 + (h - 2 - 4) / 2;
        if (state == ST_GREEN) {
            if (!filling) mono(g, SEG_CHECK, x + (w - 6) / 2, iy, 0xFFFFFFFF);
            else if (sinceDone > SEG_FILL_MS * 2 / 3) mono(g, SEG_CHECK, x + (w - 6) / 2, iy + 1, 0xFFFFFFFF);   // o visto "assenta" no fim
        } else if (state == ST_RED) mono(g, SEG_BANG, x + (w - 2) / 2, iy, 0xFFFFFFFF);
    }

    /** Visto (6 x 4) e exclamação (2 x 4) dos segmentos da barra de progresso. */
    private static final String[] SEG_CHECK = {".....X", "....X.", "X..X..", ".XX..."};
    private static final String[] SEG_BANG = {"XX", "XX", "..", "XX"};

    /**
     * Poço escuro do contador "n/6". O aro (1 px) fica no limite da plaquinha e o miolo tem 9 linhas, o bastante para o
     * dígito de 7 com uma linha de folga em cima e embaixo. Pronto: o aro vira verde e respira devagar.
     */
    public static void counterWell(GuiGraphics g, int x, int y, int w, int h, boolean complete, long t) {
        int ring = complete ? lerp(0xFF2F7A3A, 0xFF9CE08A, 0.55D + 0.45D * Math.sin(t / 420.0D)) : GOLD_DARK;
        notched(g, x, y, w, h, ring);
        notched(g, x + 1, y + 1, w - 2, h - 2, complete ? 0xFF16301A : 0xFF150C06);
        rect(g, x + 2, y + 1, w - 4, 1, argb(complete ? 50 : 26, 0xFFFFFF));
    }

    /** Camada de pergaminho clara e translúcida que acalma o desenho do mapa atrás de uma página de texto. */
    public static void wash(GuiGraphics g, int x, int y, int w, int h) {
        notched(g, x, y, w, h, argb(38, 0x5A3E1C));              // borda leve
        notched(g, x + 1, y + 1, w - 2, h - 2, argb(168, 0xEEDDB0));
        rect(g, x + 3, y + 2, w - 6, 1, argb(90, 0xFFF8DC));
        for (int k = 0; k < 90; k++) {                                          // grão do papel: pintinhas fixas, claras e escuras
            int gx = x + 3 + hsh(k, 11) % (w - 6), gy = y + 3 + hsh(k, 12) % (h - 6);
            rect(g, gx, gy, 1, 1, k % 3 == 0 ? argb(40, 0xFFFFFF) : argb(26, 0x7A5A2E));
        }
        rect(g, x + 3, y + h - 3, w - 6, 1, argb(46, 0x7A5A2E));
        int[][] cn = {{x + 3, y + 3, 1, 1}, {x + w - 6, y + 3, -1, 1}, {x + 3, y + h - 6, 1, -1}, {x + w - 6, y + h - 6, -1, -1}};
        for (int[] c : cn) {                                                    // cantoneiras em L de latão nos quatro cantos
            int ax = c[2] > 0 ? c[0] : c[0] + 2, ay = c[3] > 0 ? c[1] : c[1] + 2;
            rect(g, c[0], c[1], 3, 3, argb(0, 0));
            rect(g, c[2] > 0 ? c[0] : c[0] - 0, ay, 4, 1, argb(190, 0xB8862A));
            rect(g, ax, c[3] > 0 ? c[1] : c[1] - 1, 1, 4, argb(190, 0xB8862A));
        }
    }

    /** Pequena seta (chevron cheio) para a esquerda ou a direita, para as pontas da faixa de título. */
    public static void tailArrow(GuiGraphics g, int x, int y, boolean left, int argb) {
        arrow(g, x, y, 7, left, argb);
    }

    /** Ícone de status redondo de 9 x 9 (0 ok verde, 1 erro vermelho, 2 aviso laranja, 3 dica azul-acinzentada). */
    public static void status(GuiGraphics g, int x, int y, int kind) {
        int[] col = {GREEN, RED, ORANGE, SLATE};
        disc(g, x + 4, y + 4, 4, 0xFF1E120A);
        disc(g, x + 4, y + 4, 3, col[kind]);
        rect(g, x + 2, y + 2, 2, 1, argb(90, 0xFFFFFF));           // brilho
        String[] s = kind == 0 ? S_OK : kind == 1 ? S_BAD : kind == 2 ? S_WARN : S_INFO;
        mono(g, s, x + 2, y + (kind == 0 ? 3 : 2), 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------------------------------ fundo e mapa

    private static final int[] MOTES;

    static {
        Random r = new Random(20261029L);
        MOTES = new int[4 * 26];
        for (int i = 0; i < 26; i++) {
            MOTES[i * 4] = r.nextInt(1000);
            MOTES[i * 4 + 1] = r.nextInt(2000);
            MOTES[i * 4 + 2] = 5 + r.nextInt(12);
            MOTES[i * 4 + 3] = r.nextInt(628);
        }
    }

    /** Escurece a tela (sem nenhum cenário atrás do mapa), com cantos mais escuros e um pouco de poeira dourada. */
    public static void backdrop(GuiGraphics g, int w, int h, long t) {
        backdrop(g, w, h, t, 1.0F);
    }

    /** O fundo escuro atrás do mapa; {@code k} (0..1) multiplica a opacidade de tudo (usado no fade final do selo). */
    public static void backdrop(GuiGraphics g, int w, int h, long t, float k) {
        if (k <= 0.0F) return;
        g.fill(0, 0, w, h, argb((int) Math.round(200 * k), 0x07050A));
        int step = Math.max(4, Math.min(w, h) / 20);
        for (int i = 0; i < 12; i++) {
            int a = (int) (110 * k * Math.pow(1.0D - i / 12.0D, 1.8D));
            int o = i * step;
            g.fill(o, o, w - o, o + step, argb(a, 0x000000));
            g.fill(o, h - o - step, w - o, h - o, argb(a, 0x000000));
            g.fill(o, o + step, o + step, h - o - step, argb(a, 0x000000));
            g.fill(w - o - step, o + step, w - o, h - o - step, argb(a, 0x000000));
        }
        for (int i = 0; i < MOTES.length; i += 4) {
            double rise = (t / 1000.0D * MOTES[i + 2] + MOTES[i + 1] / 1000.0D * h) % (h + 20);
            int y = h - (int) rise;
            int x = (int) (MOTES[i] / 1000.0D * w + Math.sin(t / 1400.0D + MOTES[i + 3] / 100.0D) * 7);
            int a = (int) (k * (30 + (int) (60 * (0.5D + 0.5D * Math.sin(t / 500.0D + MOTES[i + 3])))));
            int sz = (i / 4) % 3 == 0 ? 2 : 1;
            g.fill(x, y, x + sz, y + sz, argb(a, 0xFFD27A));
        }
    }

    /** O mapa inteiro: rolos, folha, sombra e decoração (uma textura só). */
    public static void sheet(GuiGraphics g, int ox, int oy, long t) {
        g.flush();                                  // desenha os retângulos que esperam no lote ANTES do blit (ordem das camadas) e antes do enableBlend
        RenderSystem.enableBlend();                 // um fill() antes deste blit desliga o blend: sem isto a sombra sai preta
        g.blit(SHEET, ox, oy, BW, BH, 0.0F, 0.0F, BW, BH, BW, BH);
    }

    // ------------------------------------------------------------------------------------------ peças da interface

    /** Divisória com losango no meio (cor de tinta clara). */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        int cx = x + w / 2;
        rect(g, x, y + 2, w / 2 - 6, 1, 0xFF8A6E48);
        rect(g, cx + 6, y + 2, w - w / 2 - 6, 1, 0xFF8A6E48);
        rect(g, x, y + 1, 1, 3, 0xFF8A6E48);
        rect(g, x + w - 1, y + 1, 1, 3, 0xFF8A6E48);
        rect(g, cx - 1, y, 3, 5, GOLD_DARK);
        rect(g, cx - 2, y + 1, 5, 3, GOLD_DARK);
        rect(g, cx, y + 1, 1, 3, GOLD_LIGHT);
        rect(g, cx - 1, y + 2, 3, 1, GOLD_LIGHT);
    }

    /** Estados das placas. */
    public static final int NORMAL = 0, HOVER = 1, SELECTED = 2, DISABLED = 3;

    /** Retângulo com os cantos cortados em escada de {@code c} pixels (1 ou 2); só com cores opacas. */
    public static void chamfer(GuiGraphics g, int x, int y, int w, int h, int argb, int c) {
        if (c <= 1) { notched(g, x, y, w, h, argb); return; }
        rect(g, x + 2, y, w - 4, h, argb);
        rect(g, x + 1, y + 1, w - 2, h - 2, argb);
        rect(g, x, y + 2, w, h - 4, argb);
    }

    /**
     * Botão em relevo: contorno escuro com cantos cortados, borda, preenchimento em duas faixas (mais claro em cima),
     * fio de luz em cima e de sombra embaixo e rebites nos cantos dos botões largos. Normal = pergaminho; mouse em cima =
     * borda de ouro e brilho; escolhido = couro escuro com ouro pulsando; desabilitado = cinza.
     */
    public static void button(GuiGraphics g, int x, int y, int w, int h, int state, long t) {
        int top, bot, light, shade, border;
        switch (state) {
            case SELECTED -> { top = 0xFF553619; bot = 0xFF2E1A0D; light = 0xFF7A5230; shade = 0xFF140A05; border = goldPulse(t); }
            case HOVER -> { top = 0xFFFFF0C8; bot = 0xFFEFD698; light = 0xFFFFFCE8; shade = 0xFFB08A48; border = lerp(GOLD, GOLD_LIGHT, 0.5D + 0.5D * Math.sin(t / 260.0D)); }
            case DISABLED -> { top = 0xFFD2C8AE; bot = 0xFFBDB294; light = 0xFFE2DAC4; shade = 0xFF8F8670; border = 0xFF8A8270; }
            default -> { top = 0xFFEEDBA8; bot = 0xFFD9BF86; light = 0xFFFFF4D2; shade = 0xFF9C7440; border = 0xFF8A6430; }
        }
        rect(g, x + 1, y + 2, w, h, 0x38000000);                      // sombra sobre o papel
        if (state == HOVER) hoverGlow(g, x, y, w, h, t);
        chamfer(g, x, y, w, h, 0xFF24140A, 2);
        chamfer(g, x + 1, y + 1, w - 2, h - 2, border, 2);
        chamfer(g, x + 2, y + 2, w - 4, h - 4, bot, 1);
        rect(g, x + 2, y + 2, w - 4, (h - 4) / 2 + 1, top);
        if (state == HOVER) rect(g, x + 3, y + h - 4, w - 6, 1, argb(70, 0xFFE9A0));      // calor de baixo: o botão "acende"
        rect(g, x + 3, y + 2, w - 6, 1, light);                          // luz em cima
        rect(g, x + 2, y + 3, 1, h - 7, argb(90, light));               // luz na esquerda
        rect(g, x + 3, y + h - 3, w - 6, 1, shade);                      // sombra embaixo
        rect(g, x + w - 3, y + 3, 1, h - 7, argb(70, shade));           // sombra na direita
        if (w >= 34 && h >= 14) {                                         // rebites
            int rv = state == SELECTED || state == HOVER ? GOLD : 0xFF8A6430;
            rect(g, x + 4, y + 4, 1, 1, rv);
            rect(g, x + w - 5, y + 4, 1, 1, rv);
            rect(g, x + 4, y + h - 5, 1, 1, rv);
            rect(g, x + w - 5, y + h - 5, 1, 1, rv);
        }
        if (state == HOVER) sweep(g, x + 2, y + 2, w - 4, h - 4, t, 90);
    }

    /** Botão quadrado com uma seta (chevron de 2 px de espessura) para a esquerda ou a direita. */
    public static void arrowButton(GuiGraphics g, int x, int y, int w, int h, boolean left, int state, long t) {
        button(g, x, y, w, h, state, t);
        int col = state == HOVER ? 0xFF7A4A10 : state == DISABLED ? 0xFF8F8670 : INK;
        int cx = x + w / 2 + (left ? -1 : 0), cy = y + h / 2 - 3;
        for (int r = 0; r < 7; r++) {
            int d = r <= 3 ? r : 6 - r;
            int px = left ? cx + 1 - d : cx - 1 + d;
            rect(g, px, cy + r, 2, 1, col);
        }
    }

    /**
     * Botão grande de finalizar: fita de couro carmim com moldura de ouro dupla, costura pontilhada, rebites
     * de ouro e brilho diagonal que passa. Com o mouse em cima acende, ganha halo e
     * faíscas nos cantos. Sem nome ({@code enabled} falso) fica de pedra cinza, sem brilho. O medalhão com o selo e o
     * texto são desenhados pelo chamador por cima.
     */
    public static void finishButton(GuiGraphics g, int x, int y, int w, int h, boolean enabled, boolean hover, long t) {
        finishButton(g, x, y, w, h, enabled ? 1.0D : 0.0D, 0.0D, hover, t);
    }

    /**
     * O mesmo botão, mas com a transição entre os dois estados: {@code mix} vai de 0 (pedra cinza, sem nome) a 1
     * (fita vermelha, pronto) e as cores passam de uma para a outra; {@code flash} (0 a 1) é o clarão que passa pelo
     * botão no instante em que ele fica vermelho.
     */
    public static void finishButton(GuiGraphics g, int x, int y, int w, int h, double mix, double flash, boolean hover, long t) {
        boolean on = mix > 0.5D, hv = hover && on;
        int top = lerp(0xFFA39A88, hv ? 0xFFD4463A : 0xFFB5342A, mix);
        int bot = lerp(0xFF7A7264, hv ? 0xFF8E2018 : 0xFF6A140E, mix);
        int gold = lerp(0xFF7A705C, hv ? GOLD_LIGHT : goldPulse(t), mix);
        int goldDk = lerp(0xFF5E5646, GOLD_DARK, mix);
        rect(g, x + 1, y + 3, w, h, 0x55000000);
        if (hv) hoverGlow(g, x, y, w, h, t);
        chamfer(g, x, y, w, h, 0xFF1A0E06, 2);
        chamfer(g, x + 1, y + 1, w - 2, h - 2, goldDk, 2);
        rect(g, x + 3, y + 1, w - 6, 1, gold);                                            // moldura de ouro: luz em cima, sombra embaixo
        rect(g, x + 1, y + 3, 1, h - 6, lerp(0xFF8A8270, GOLD, mix));
        rect(g, x + w - 2, y + 3, 1, h - 6, goldDk);
        chamfer(g, x + 2, y + 2, w - 4, h - 4, 0xFF1A0E06, 1);
        chamfer(g, x + 3, y + 3, w - 6, h - 6, bot, 1);
        rect(g, x + 3, y + 3, w - 6, (h - 6) / 2, top);                                   // metade de cima mais clara
        rect(g, x + 4, y + 3, w - 8, 1, argb((int) (70 + 60 * mix), 0xFFE9D0));
        rect(g, x + 4, y + h - 4, w - 8, 1, argb(110, 0x000000));
        int seam = (int) (70 + 80 * mix);
        for (int sx = x + 34; sx < x + w - 8; sx += 4) {                                  // costura pontilhada (começa depois do medalhão)
            rect(g, sx, y + 5, 2, 1, argb(seam, 0xFFD27A));
            rect(g, sx, y + h - 6, 2, 1, argb(seam, 0xFFD27A));
        }
        // rebites de ouro
        rect(g, x + w - 8, y + 3, 2, 2, gold);
        rect(g, x + w - 8, y + h - 5, 2, 2, goldDk);
        if (mix > 0.05D) {                                                 // brilho diagonal que passa (some junto com a cor)
            int span = w + 24, sx = x - 12 + (int) (((t % 3600L) / 3600.0D) * span);
            for (int r = 0; r < h - 6; r++) {
                int a = Math.max(x + 3, sx - r / 2), b = Math.min(x + w - 3, sx - r / 2 + 6);
                if (b > a) rect(g, a, y + 3 + r, b - a, 1, argb((int) ((hv ? 70 : 46) * mix), 0xFFFFFF));
            }
            if (hv) {                                                       // faíscas nos cantos
                sparkle(g, x + w - 6, y + 4, (t % 1300L) / 700.0D, 0xFFE9A0);
                sparkle(g, x + 36, y + h - 5, ((t + 650L) % 1300L) / 700.0D, 0xFFE9A0);
            }
        }
        if (flash > 0.0D) {                                                // clarão ao ficar vermelho: uma faixa de luz varre o botão e o resto acende
            double p = 1.0D - flash;
            int bx = x + 3 + (int) (p * (w + 10)) - 10;
            for (int r = 0; r < h - 6; r++) {
                int a = Math.max(x + 3, bx - r / 2), b = Math.min(x + w - 3, bx - r / 2 + 9);
                if (b > a) rect(g, a, y + 3 + r, b - a, 1, argb((int) (150 * flash), 0xFFF4C8));
            }
            rect(g, x + 3, y + 3, w - 6, h - 6, argb((int) (60 * flash * flash), 0xFF8A70));
        }
    }

    /** Campo do nome: placa escura com borda que muda de cor (neutra, verde, vermelha, laranja). */
    public static void field(GuiGraphics g, int x, int y, int w, int h, int borderRgb, boolean focused) {
        notched(g, x, y, w, h, 0xFF1E120A);
        notched(g, x + 1, y + 1, w - 2, h - 2, 0xFF000000 | borderRgb);
        notched(g, x + 2, y + 2, w - 4, h - 4, focused ? 0xFF2E1B0F : 0xFF24160C);
        rect(g, x + 3, y + 3, w - 6, 1, argb(40, 0x000000));
    }

    /** Amostra de cor; escolhida = moldura dourada dupla e um visto. */
    public static void swatch(GuiGraphics g, int x, int y, int w, int h, int rgb, boolean selected, boolean hover, long t) {
        rect(g, x, y, w, h, 0xFF1E120A);
        rect(g, x + 1, y + 1, w - 2, h - 2, 0xFF000000 | rgb);
        rect(g, x + 1, y + 1, w - 2, 1, argb(60, 0xFFFFFF));
        rect(g, x + 1, y + h - 2, w - 2, 1, argb(60, 0x000000));
        if (selected) {
            outline(g, x - 2, y - 2, w + 4, h + 4, goldPulse(t));
            outline(g, x - 1, y - 1, w + 2, h + 2, 0xFF1E120A);
            int cx = x + w / 2 - 3, cy = y + h / 2 - 3;
            mono(g, CHECK, cx + 1, cy + 1, argb(200, 0x000000));
            mono(g, CHECK, cx, cy, 0xFFFFFFFF);
        } else if (hover) {
            rect(g, x + 1, y + 1, w - 2, h - 2, argb(34, 0xFFFFFF));                       // a cor "acende" um pouco
            outline(g, x - 1, y - 1, w + 2, h + 2, lerp(GOLD, GOLD_LIGHT, 0.5D + 0.5D * Math.sin(t / 220.0D)));
            outline(g, x - 2, y - 2, w + 4, h + 4, argb(95, 0xFFD27A));                    // halo de ouro por fora
            int c = Math.max(2, Math.min(w, h) / 3);                                         // cantoneiras de luz em dois cantos opostos
            rect(g, x - 1, y - 1, c, 1, GOLD_LIGHT);
            rect(g, x - 1, y - 1, 1, c, GOLD_LIGHT);
            rect(g, x + w - c + 1, y + h, c, 1, GOLD_LIGHT);
            rect(g, x + w, y + h - c + 1, 1, c, GOLD_LIGHT);
        }
    }

    /** Plaquinha de título do personagem (versão antiga: gema âmbar). */
    public static void nameplate(GuiGraphics g, int cx, int y, int w, int h, long t) {
        nameplate(g, cx, y, w, h, t, 0xE8A33A);
    }

    /** Losango de pixel art de raio r: contorno, preenchimento e um brilho no canto de cima. */
    private static void gemDiamond(GuiGraphics g, int cx, int cy, int r, int edge, int fill, int hi) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            rect(g, cx - half - 1, cy + dy, 1, 1, edge);
            rect(g, cx + half + 1, cy + dy, 1, 1, edge);
            if (half >= 0) rect(g, cx - half, cy + dy, half * 2 + 1, 1, fill);
        }
        rect(g, cx, cy - r - 1, 1, 1, edge);
        rect(g, cx, cy + r + 1, 1, 1, edge);
        rect(g, cx - 1, cy - 1, 1, 1, hi);
    }

    /**
     * Placa do nome: couro escuro com moldura de ouro dupla, rebites nos cantos, brasão no meio da borda de cima,
     * medalhões com uma gema de estado (vermelha sem nome, verde com nome válido) e um brilho que passa devagar.
     * Sem setas nas pontas. {@code gemRgb} é a cor da gema.
     */
    public static void nameplate(GuiGraphics g, int cx, int y, int w, int h, long t, int gemRgb) {
        int x = cx - w / 2, cy = y + h / 2;
        int gold = goldPulse(t);
        rect(g, x + 1, y + 3, w, h, 0x55000000);                                         // sombra no papel
        chamfer(g, x, y, w, h, 0xFF1A0E06, 2);                                           // contorno escuro
        chamfer(g, x + 1, y + 1, w - 2, h - 2, GOLD_DARK, 2);                            // moldura de ouro (mais clara em cima)
        rect(g, x + 3, y + 1, w - 6, 1, gold);
        rect(g, x + 1, y + 3, 1, h - 6, GOLD);
        rect(g, x + w - 2, y + 3, 1, h - 6, GOLD_DARK);
        rect(g, x + 3, y + h - 2, w - 6, 1, 0xFF6A400C);
        chamfer(g, x + 2, y + 2, w - 4, h - 4, 0xFF1A0E06, 1);                           // vão escuro entre as duas molduras
        chamfer(g, x + 3, y + 3, w - 6, h - 6, 0xFF2E1C0F, 1);                           // couro
        rect(g, x + 4, y + h / 2, w - 8, h / 2 - 3, 0xFF24150B);                         // metade de baixo mais escura
        rect(g, x + 4, y + 4, w - 8, 1, argb(70, 0xFFE9A0));                             // fio de luz
        outline(g, x + 5, y + 5, w - 10, h - 10, argb(90, 0xD9A441));                    // segunda moldura, fina, por dentro
        // brilho que passa devagar por dentro (recortado na placa)
        double u = (t % 5200L) / 900.0D;
        if (u < 1.0D) {
            int head = x + 6 + (int) Math.round(u * (w - 12));
            for (int r = 0; r < h - 8; r++) {
                int px = head - r / 2;
                if (px > x + 5 && px < x + w - 6) rect(g, px, y + 4 + r, 2, 1, argb(46, 0xFFFFFF));
            }
        }
        // medalhões laterais com a gema de estado + fiozinho de ouro até o texto (no lugar das antigas setas)
        int gem = 0xFF000000 | gemRgb, gemHi = lerp(gem, 0xFFFFFFFF, 0.55D), gemEdge = lerp(gem, 0xFF000000, 0.65D);
        int[] mx = {x + 12, x + w - 13};
        for (int k = 0; k < 2; k++) {
            int dir = k == 0 ? 1 : -1;
            gemDiamond(g, mx[k], cy, 3, 0xFF1A0E06, gold, GOLD_LIGHT);
            gemDiamond(g, mx[k], cy, 1, gemEdge, gem, gemHi);
            rect(g, mx[k] + dir * 6, cy, 1, 1, GOLD_DARK);
            rect(g, mx[k] + dir * 8, cy, 5, 1, argb(150, 0xD9A441));
            rect(g, mx[k] + dir * 8, cy + 1, 5, 1, argb(60, 0x000000));
            rect(g, mx[k] + dir * 14, cy, 1, 1, GOLD);
        }
        // rebites de ouro nos quatro cantos
        int[][] rv = {{x + 1, y + 1}, {x + w - 4, y + 1}, {x + 1, y + h - 4}, {x + w - 4, y + h - 4}};
        for (int[] q : rv) {
            rect(g, q[0] - 1, q[1] - 1, 5, 5, 0xFF1A0E06);
            rect(g, q[0], q[1], 3, 3, GOLD);
            rect(g, q[0], q[1], 2, 2, GOLD_LIGHT);
            rect(g, q[0] + 2, q[1] + 2, 1, 1, GOLD_DARK);
        }
        // brasão: coroa de ouro no meio da borda de cima e uma ponta menor embaixo
        rect(g, cx - 6, y - 1, 13, 4, 0xFF1A0E06);
        rect(g, cx - 5, y - 1, 11, 3, GOLD_DARK);
        rect(g, cx - 4, y - 1, 9, 2, gold);
        rect(g, cx - 2, y - 3, 5, 3, 0xFF1A0E06);
        rect(g, cx - 1, y - 3, 3, 2, GOLD);
        rect(g, cx, y - 3, 1, 1, GOLD_LIGHT);
        rect(g, cx - 4, y + h - 2, 9, 3, 0xFF1A0E06);
        rect(g, cx - 3, y + h - 2, 7, 2, GOLD_DARK);
        rect(g, cx - 1, y + h - 2, 3, 1, gold);
    }

    /**
     * Faixa de título (vermelha, com rabo de andorinha nas duas pontas e fios de ouro), centrada em cx.
     * O texto é desenhado pelo chamador por cima.
     */
    public static void banner(GuiGraphics g, int cx, int y, int w, int h, long t) {
        banner(g, cx, y, w, h, t, 0xA82E22);
    }

    /** Faixa de título na cor {@code tintRgb} (cada aba tem a sua; o vermelho original é 0xA82E22). */
    public static void banner(GuiGraphics g, int cx, int y, int w, int h, long t, int tintRgb) {
        int main = lerp(0xFF000000 | tintRgb, 0xFF000000, 0.15D), light = lerp(main, 0xFFFFFFFF, 0.22D);
        int dark = lerp(main, 0xFF000000, 0.35D), tailCol = lerp(main, 0xFF000000, 0.22D);
        int x = cx - w / 2, tail = 9, drop = 3;
        for (int side = 0; side < 2; side++) {                                 // rabos, mais escuros e um pouco mais baixos
            boolean left = side == 0;
            for (int r = 0; r < h; r++) {
                double d = Math.abs(r - (h - 1) / 2.0D) / ((h - 1) / 2.0D);
                int notch = (int) Math.round(tail * 0.75D * (1.0D - d));
                int x0 = left ? x - tail : x + w - 3, len = tail + 3;
                int a = left ? x0 : x0 + notch, b = left ? x0 + len - notch : x0 + len;
                rect(g, a, y + drop + r, b - a, 1, 0xFF1E120A);
                if (b - a > 2) rect(g, a + 1, y + drop + r, b - a - 2, 1, r == 0 || r == h - 1 ? 0xFF1E120A : tailCol);
            }
        }
        notched(g, x, y, w, h, 0xFF1E120A);
        notched(g, x + 1, y + 1, w - 2, h - 2, main);
        rect(g, x + 2, y + 2, w - 4, 1, light);
        rect(g, x + 2, y + h - 3, w - 4, 1, dark);
        int gold = goldPulse(t);
        rect(g, x + 4, y + 3, w - 8, 1, gold);
        rect(g, x + 4, y + h - 4, w - 8, 1, gold);
        // brilho que passa devagar pelo fio de cima
        int span = w - 20, gx = x + 6 + (int) (((t % 4600L) / 4600.0D) * span);
        rect(g, gx, y + 3, 8, 1, argb(230, 0xFFFFFF));
    }

    // ------------------------------------------------------------------------------------------ bandeiras (abas)

    /** Formatos da ponta solta da bandeira: rabo de andorinha, ponta de flecha, bandeira rasgada e xadrez (aleatório). */
    public static final int FLAG_SWALLOW = 0, FLAG_POINTED = 1, FLAG_TATTERED = 2, FLAG_CHECKER = 3;
    private static final int FLAG_EDGE = 0xFF24140A;
    private static final int[] TATTER = {0, 2, 1, 3, 0, 2, 4, 1, 3, 0, 2, 1, 4, 2, 0, 3, 1, 4, 2};

    /** Altura do pano (sem a onda) e coluna do ícone: o chamador desenha o ícone de 16 x 16 em {@link #flagIconX}. */
    public static int flagIconX(int x, int w) {
        return x + (w - 3) - 3 - 16;
    }

    /** O pano existe neste pixel (coluna c, linha r)? c = 0 é a ponta solta; r = 0 é a beirada de cima. */
    private static boolean cloth(int shape, int c, int r, int cw, int ch) {
        if (c < 0 || c >= cw || r < 0 || r >= ch) return false;
        double d = Math.abs(2.0D * r - (ch - 1)) / (ch - 1);             // 0 no meio, 1 nas beiradas
        return switch (shape) {
            case FLAG_SWALLOW -> c >= (int) Math.ceil(6.0D * (1.0D - d) - 0.01D);
            case FLAG_POINTED -> c >= (int) Math.floor(7.0D * d + 0.01D);
            case FLAG_TATTERED -> c >= TATTER[r % TATTER.length];
            default -> true;
        };
    }

    /** Faz a onda do pano: deslocamento vertical da coluna c (zero junto do mastro, máximo na ponta solta). */
    private static int flagWave(int c, int cw, double amp, long t, int seed) {
        return (int) Math.round(flagWaveExact(c, cw, amp, t, seed));
    }

    private static double flagWaveExact(int c, int cw, double amp, long t, int seed) {
        double f = Math.pow((cw - 1 - c) / (double) (cw - 1), 1.15D);
        return Math.sin(t / 300.0D + c * 0.5D + seed * 1.7D) * amp * f;
    }

    /**
     * Aba em forma de bandeira de pixel art presa a um mastro de madeira na borda do mapa: a bandeira ondula com o
     * vento (mais forte quando escolhida ou com o mouse em cima), tem dobras que andam junto com a onda, barra
     * dourada, ponta de formato próprio e sombra. {@code (x, y)} é o canto de cima à esquerda do espaço da aba, {@code w}
     * inclui o mastro (as 3 últimas colunas). O ícone (16 x 16) é desenhado pelo chamador em {@link #flagIconX};
     * devolve o quanto o pano está deslocado na coluna do ícone, para o ícone acompanhar a onda.
     */
    public static int flag(GuiGraphics g, int x, int y, int w, int h, int rgb, int shape, int seed,
                           boolean selected, boolean hover, long t, double boost) {
        int cw = w - 3, ch = h - 5, y0 = y + 3;
        double amp = (selected ? 2.2D : hover ? 1.7D : 1.0D) + 3.0D * boost;      // boost (0 a 1): o chacoalhão ao clicar
        int base = 0xFF000000 | rgb;
        if (selected || hover) base = lerp(base, 0xFFFFFFFF, hover && !selected ? 0.06D : 0.10D);
        int top = lerp(base, 0xFFFFFFFF, 0.22D), mid = base;                    // só quatro tons da mesma cor: luz, pano, sombra e barra
        int low = lerp(base, 0xFF000000, 0.24D), hem = lerp(base, 0xFF000000, 0.50D);
        int edge = selected ? goldPulse(t) : hover ? 0xFFB8862A : FLAG_EDGE;
        // o ícone (16 colunas) é rígido, o pano não: ele acompanha a MÉDIA da onda sob ele, para ficar no meio do pano ao ondular
        double sum = 0.0D;
        for (int k = 0; k < 16; k++) sum += flagWaveExact(cw - 3 - 16 + k, cw, amp, t, seed);
        int iconDy = (int) Math.round(sum / 16.0D);

        // onda de cada coluna, calculada uma vez só (a sombra e o pano usam a mesma)
        int[] offs = new int[cw];
        for (int c = 0; c < cw; c++) offs[c] = flagWave(c, cw, amp, t, seed);
        byte[][] mask = clothMask(shape, cw, ch);          // 0 = fora, 1 = pano, 2 = contorno (feito uma vez por formato e tamanho)

        // sombra projetada pelo pano no mapa: colunas vizinhas iguais (mesma onda e mesmo recorte) viram um retângulo só
        for (int c0 = 0; c0 < cw; ) {
            int c1 = c0 + 1;
            while (c1 < cw && offs[c1] == offs[c0] && java.util.Arrays.equals(mask[c1 + 1], mask[c0 + 1])) c1++;
            byte[] m = mask[c0 + 1];
            int r0 = -1;
            for (int r = 0; r <= ch; r++) {
                boolean in = m[r + 1] == 1;
                if (in && r0 < 0) r0 = r;
                if ((!in || r == ch) && r0 >= 0) {
                    rect(g, x + c0 + 2, y0 + offs[c0] + r0 + 2, c1 - c0, r - r0, 0x40000000);
                    r0 = -1;
                }
            }
            c0 = c1;
        }
        // mastro de madeira (peça só do pano)
        int px = x + w - 3;
        rect(g, px - 1, y + 1, 4, h + 1, FLAG_EDGE);
        rect(g, px, y + 2, 1, h - 1, 0xFFEBB890);
        rect(g, px + 1, y + 2, 1, h - 1, 0xFF9A5E34);
        // o pano, coluna por coluna (contorno escuro, depois a cor); colunas vizinhas idênticas saem juntas, num retângulo só
        int[] col = new int[ch + 2], prev = new int[ch + 2];
        int gStart = 0, gOff = 0;
        boolean open = false;
        for (int c = -1; c <= cw; c++) {
            boolean last = c == cw;
            if (!last) {
                int off = offs[Math.max(c, 0)];
                double fold = Math.sin(t / 300.0D + Math.max(c, 0) * 0.5D + seed * 1.7D);
                byte[] m = mask[c + 1];
                for (int r = -1; r <= ch; r++) {
                    int v = 0;
                    byte k = m[r + 1];
                    if (k == 1) v = clothColor(shape, c, r, cw, ch, base, top, mid, low, hem, fold, selected, t, seed);
                    else if (k == 2) v = edge;
                    col[r + 1] = v;
                }
                if (open && off == gOff && java.util.Arrays.equals(col, prev)) continue;      // igual à anterior: o grupo só cresce
                if (open) flagColumns(g, x + gStart, y0 + gOff, c - gStart, prev, ch);
                gStart = c;
                gOff = off;
                open = true;
                int[] tmp = prev; prev = col; col = tmp;
            } else if (open) {
                flagColumns(g, x + gStart, y0 + gOff, c - gStart, prev, ch);
            }
        }
        // argolas de latão que prendem o pano ao mastro
        rect(g, px - 1, y0 + 2, 4, 2, FLAG_EDGE);
        rect(g, px, y0 + 2, 2, 1, 0xFFFFE9A0);
        rect(g, px, y0 + 3, 2, 1, 0xFFB8862A);
        rect(g, px - 1, y0 + ch - 4, 4, 2, FLAG_EDGE);
        rect(g, px, y0 + ch - 4, 2, 1, 0xFFFFE9A0);
        rect(g, px, y0 + ch - 3, 2, 1, 0xFFB8862A);
        return iconDy;
    }

    /** Desenha um grupo de {@code n} colunas iguais: um retângulo por trecho de cor (de cima para baixo). */
    private static void flagColumns(GuiGraphics g, int x, int top, int n, int[] col, int ch) {
        int r0 = 0;
        for (int i = 1; i <= ch + 2; i++) {
            if (i == ch + 2 || col[i] != col[r0]) {
                if (col[r0] != 0) rect(g, x, top + r0 - 1, n, i - r0, col[r0]);
                r0 = i;
            }
        }
    }

    private static final java.util.Map<Integer, byte[][]> MASKS = new java.util.HashMap<>();

    /**
     * Recorte do pano por formato e tamanho: {@code [c + 1][r + 1]} vale 1 onde há pano, 2 no contorno (vizinho de pano)
     * e 0 fora. Só depende de forma e tamanho, então é calculado uma vez e guardado (o pano só muda de cor e de onda).
     */
    private static byte[][] clothMask(int shape, int cw, int ch) {
        int key = (shape << 24) | (cw << 12) | ch;
        byte[][] m = MASKS.get(key);
        if (m != null) return m;
        if (MASKS.size() > 96) MASKS.clear();             // a bandeira cresce e encolhe ao animar: não deixa a lista crescer sem fim
        m = new byte[cw + 2][ch + 2];
        for (int c = -1; c <= cw; c++) {
            for (int r = -1; r <= ch; r++) {
                if (cloth(shape, c, r, cw, ch)) m[c + 1][r + 1] = 1;
                else if (cloth(shape, c - 1, r, cw, ch) || cloth(shape, c + 1, r, cw, ch)
                        || cloth(shape, c, r - 1, cw, ch) || cloth(shape, c, r + 1, cw, ch)) m[c + 1][r + 1] = 2;
            }
        }
        MASKS.put(key, m);
        return m;
    }

    private static int clothColor(int shape, int c, int r, int cw, int ch, int base, int top, int mid, int low,
                                  int hem, double fold, boolean selected, long t, int seed) {
        if (shape == FLAG_CHECKER) {                                          // xadrez creme e marrom, como a bandeira de chegada
            boolean dark = ((c / 2) + (r / 2)) % 2 == 0;
            int v = dark ? 0xFF5A0F2A : 0xFFF3E2C4;
            if (fold < -0.6D) v = lerp(v, 0xFF000000, 0.14D);
            return v;
        }
        int v = r == 0 ? top : r < ch / 3 ? mid : r >= ch - 2 ? hem : r >= ch * 2 / 3 ? low : base;
        if (r == ch - 3) {
            v = selected ? goldPulse(t) : 0xFFE9B44C;       // único detalhe de outra cor: o fio de ouro da barra
            int gp = (int) ((t / 38L + seed * 23L) % (cw + 40)) - 6;           // um brilho corre pelo fio de ouro, de bandeira em bandeira
            int dd = Math.abs(c - gp);
            if (dd == 0) v = 0xFFFFFFFF;
            else if (dd <= 2) v = lerp(v, 0xFFFFFFFF, 0.55D);
        }
        if (r > 0 && r < ch - 3) {                                          // dobras do pano: a crista clareia, o vale escurece
            if (fold > 0.62D) v = lerp(v, 0xFFFFFFFF, 0.09D);
            else if (fold < -0.62D) v = lerp(v, 0xFF000000, 0.16D);
        }
        return v;
    }

    /** Lados do ponteiro do balão: sem ponteiro, em cima, embaixo, à esquerda, à direita (o lado da caixa que aponta para o mouse). */
    public static final int TAIL_NONE = -1, TAIL_TOP = 0, TAIL_BOTTOM = 1, TAIL_LEFT = 2, TAIL_RIGHT = 3;

    /** Caixa de dica (balão escuro com fio de ouro), inteira e sem ponteiro. */
    public static void tipBox(GuiGraphics g, int x, int y, int w, int h, long t) {
        tipBox(g, x, y, w, h, t, 1.0D, TAIL_NONE, 0, false);
    }

    /**
     * Balão de dica: placa de couro escuro com moldura dupla (contorno e fio de ouro que pulsa), faixa quente atrás do
     * título, cantoneiras de ouro, sombra só por fora (as camadas não se sobrepõem, então o alfa {@code a} de 0 a 1 faz
     * o balão aparecer de verdade) e um ponteirinho de 4 px no lado que olha para o mouse, em {@code tailPos} (x, se o
     * lado for em cima/embaixo; y, se for esquerda/direita; em coordenadas da tela do mapa).
     */
    public static void tipBox(GuiGraphics g, int x, int y, int w, int h, long t, double a, int tailSide, int tailPos, boolean header) {
        final int dark = fade(0xFF1E120A, a), ring = fade(goldPulse(t), a), body = fade(0xFF2B1A0E, a);
        // sombra: só as faixas que ficam fora da caixa
        rect(g, x + w, y + 3, 2, h, fade(0x58000000, a));
        rect(g, x + 2, y + h, w - 2, 3, fade(0x58000000, a));
        // moldura (contorno, fio de ouro) e miolo, cada um na sua faixa
        notchedRing(g, x, y, w, h, dark);
        notchedRing(g, x + 1, y + 1, w - 2, h - 2, ring);
        rect(g, x + 1, y + 1, 1, 1, dark);
        rect(g, x + w - 2, y + 1, 1, 1, dark);
        rect(g, x + 1, y + h - 2, 1, 1, dark);
        rect(g, x + w - 2, y + h - 2, 1, 1, dark);
        notched(g, x + 2, y + 2, w - 4, h - 4, body);
        rect(g, x + 2, y + 2, 1, 1, ring);
        rect(g, x + w - 3, y + 2, 1, 1, ring);
        rect(g, x + 2, y + h - 3, 1, 1, ring);
        rect(g, x + w - 3, y + h - 3, 1, 1, ring);
        if (header) {                                                                         // faixa quente do título
            rect(g, x + 3, y + 3, w - 6, 11, fade(argb(40, 0xE9A040), a));
        }
        rect(g, x + 3, y + 3, w - 6, 1, fade(argb(56, 0xFFE9A0), a));                          // luz de cima
        // cantoneiras de ouro (um L de 3 px em cada canto, por dentro da moldura)
        int gold = fade(argb(200, 0xFFE9A0), a);
        rect(g, x + 4, y + 4, 3, 1, gold);
        rect(g, x + 4, y + 5, 1, 2, gold);
        rect(g, x + w - 7, y + 4, 3, 1, gold);
        rect(g, x + w - 5, y + 5, 1, 2, gold);
        rect(g, x + 4, y + h - 5, 3, 1, gold);
        rect(g, x + 4, y + h - 7, 1, 2, gold);
        rect(g, x + w - 7, y + h - 5, 3, 1, gold);
        rect(g, x + w - 5, y + h - 7, 1, 2, gold);
        if (tailSide >= 0) tipTail(g, x, y, w, h, tailSide, tailPos, dark, ring, body);
    }

    /** Como {@link #notched}, mas só o aro de 1 px (os cantos ficam de fora). */
    private static void notchedRing(GuiGraphics g, int x, int y, int w, int h, int argb) {
        rect(g, x + 1, y, w - 2, 1, argb);
        rect(g, x + 1, y + h - 1, w - 2, 1, argb);
        rect(g, x, y + 1, 1, h - 2, argb);
        rect(g, x + w - 1, y + 1, 1, h - 2, argb);
    }

    /**
     * Ponteiro do balão: triângulo de pixels (4 de altura) com contorno escuro, fio de ouro e miolo igual ao da caixa;
     * o miolo abre a moldura, então a ponta "sai" do balão.
     */
    private static void tipTail(GuiGraphics g, int x, int y, int w, int h, int side, int pos, int dark, int ring, int body) {
        boolean horizontal = side == TAIL_TOP || side == TAIL_BOTTOM;
        int len = horizontal ? w : h;
        int c = Math.max(7, Math.min(len - 8, pos - (horizontal ? x : y)));      // centro ao longo da borda, longe dos cantos
        for (int d = 1; d <= 4; d++) tailRow(g, x, y, w, h, side, c, d, 4 - d, dark);                   // contorno
        for (int d = 0; d <= 3; d++) tailRow(g, x, y, w, h, side, c, d, 3 - d, ring);                   // fio de ouro
        for (int d = -1; d <= 2; d++) tailRow(g, x, y, w, h, side, c, d, d < 0 ? 3 : d == 0 ? 2 : 2 - d, body);   // miolo (abre a moldura)
    }

    /** Uma fileira do ponteiro: {@code d} pixels para fora da borda (negativo = para dentro) e meia-largura {@code half}. */
    private static void tailRow(GuiGraphics g, int x, int y, int w, int h, int side, int c, int d, int half, int col) {
        int along = c - half, size = 2 * half + 1;
        switch (side) {
            case TAIL_TOP -> rect(g, x + along, y - d, size, 1, col);
            case TAIL_BOTTOM -> rect(g, x + along, y + h - 1 + d, size, 1, col);
            case TAIL_LEFT -> rect(g, x - d, y + along, 1, size, col);
            default -> rect(g, x + w - 1 + d, y + along, 1, size, col);
        }
    }

    /**
     * Divisor do balão: fio de ouro que some nas pontas, com um losango no meio. {@code a} é o alfa do balão.
     */
    public static void tipDivider(GuiGraphics g, int x, int y, int w, double a) {
        int cx = x + w / 2;
        for (int i = 0; i < w; i++) {
            int edge = Math.min(i, w - 1 - i);
            if (Math.abs(x + i - cx) < 5) continue;                                 // abre espaço para o losango
            rect(g, x + i, y, 1, 1, fade(argb(Math.min(170, 40 + edge * 18), 0xD9A441), a));
        }
        diamond(g, cx, y, fade(GOLD_LIGHT, a));
        rect(g, cx - 3, y, 1, 1, fade(GOLD, a));
        rect(g, cx + 3, y, 1, 1, fade(GOLD, a));
    }

    private static final java.util.Map<Long, int[]> FIBERS = new java.util.HashMap<>();

    /** Posições (x << 16 | y) das fibras do papel do retrato: dependem só do lugar e do tamanho, então ficam guardadas. */
    private static int[] fibers(int x, int y, int w, int h) {
        long key = ((long) (x & 0xFFFF) << 48) | ((long) (y & 0xFFFF) << 32) | ((long) (w & 0xFFFF) << 16) | (h & 0xFFFFL);
        int[] f = FIBERS.get(key);
        if (f != null) return f;
        if (FIBERS.size() > 16) FIBERS.clear();
        int n = 0;
        int[] tmp = new int[Math.max(16, (w * h) / 20)];
        for (int fy = y + 11; fy < y + h - 11; fy++) {
            for (int fx = x + 11; fx < x + w - 11; fx++) {
                if (hsh(fx, fy) % 29 == 0) {
                    if (n == tmp.length) tmp = java.util.Arrays.copyOf(tmp, n * 2);
                    tmp[n++] = (fx << 16) | fy;
                }
            }
        }
        f = java.util.Arrays.copyOf(tmp, n);
        FIBERS.put(key, f);
        return f;
    }

    /**
     * Fundo do retrato: o próprio papel do mapa, como uma ficha de identificação. Graticule fino (como as linhas de
     * latitude e longitude do mapa), régua de altura na esquerda e, em volta do boneco, um pontilhado vermelho que
     * anda devagar, com um X em cada canto, como a rota marcada no mapa.
     */
    public static void idBackdrop(GuiGraphics g, int x, int y, int w, int h, long t) {
        // o papel é da cor do pergaminho do mapa (E3C78D a E9D098 na textura), com um fio mais escuro por dentro e fibras
        final int outer = 0xFFDDBE83, paper = 0xFFEAD29B;      // fora do pontilhado: o tom médio do mapa; dentro: um tom mais claro, a "área marcada"
        notched(g, x + 8, y + 8, w - 16, h - 16, outer);
        outline(g, x + 10, y + 10, w - 20, h - 20, argb(120, 0xA9824A));
        {
            int mx0 = x + w / 2 - 30, mx1 = x + w / 2 + 30, my0 = y + 12, my1 = y + h - 10;
            rect(g, mx0, my0, mx1 - mx0 + 1, my1 - my0 + 1, paper);
        }
        int fiber = argb(34, 0x8A6430);
        for (int xy : fibers(x, y, w, h)) rect(g, xy >>> 16, xy & 0xFFFF, 1, 1, fiber);      // fibras do papel: posições fixas, calculadas uma vez
        int ix = x + 11, iy = y + 11, iw = w - 22, ih = h - 22;
        int grid = argb(34, 0x8A6430);
        for (int gx = ix + 5; gx < ix + iw; gx += 12) rect(g, gx, iy, 1, ih, grid);          // meridianos
        for (int gy = iy + 5; gy < iy + ih; gy += 12) rect(g, ix, gy, iw, 1, grid);          // paralelos
        // régua de altura (tracinhos a cada 5 px; um maior a cada 25)
        int ruler = argb(150, 0x7A5628);
        for (int k = 0, ry = y + 16; ry <= y + h - 16; k++, ry += 5) rect(g, ix + 1, ry, k % 5 == 0 ? 5 : 3, 1, ruler);
        // pontilhado vermelho em volta do boneco
        int cx = x + w / 2, half = 30;
        int x0 = cx - half, x1 = cx + half, y0 = y + 12, y1 = y + h - 10;
        int red = argb(235, RED & 0xFFFFFF);
        int per = 2 * ((x1 - x0) + (y1 - y0));
        int phase = (int) ((t / 150L) % 4L);
        int runSide = -1, runX = 0, runY = 0, runLen = 0;                          // pontos vizinhos do mesmo lado saem num retângulo só
        int wTop = x1 - x0, hSide = y1 - y0;
        for (int s = 0; s <= per; s++) {
            boolean lit = s < per && ((s - phase) % 4 + 4) % 4 < 2;                  // 2 acesos, 2 apagados; a fase faz o desfile
            int px = 0, py = 0, side = 0;
            if (lit) {
                int e = s;
                if (e < wTop) { px = x0 + e; py = y0; side = 0; }
                else if ((e -= wTop) < hSide) { px = x1; py = y0 + e; side = 1; }
                else if ((e -= hSide) < wTop) { px = x1 - e; py = y1; side = 2; }
                else { e -= wTop; px = x0; py = y1 - e; side = 3; }
            }
            if (lit && runLen > 0 && side == runSide) {
                runLen++;
                continue;
            }
            if (runLen > 0) {
                switch (runSide) {
                    case 0 -> rect(g, runX, runY, runLen, 1, red);
                    case 1 -> rect(g, runX, runY, 1, runLen, red);
                    case 2 -> rect(g, runX - runLen + 1, runY, runLen, 1, red);
                    default -> rect(g, runX, runY - runLen + 1, 1, runLen, red);
                }
                runLen = 0;
            }
            if (lit) {
                runSide = side;
                runX = px;
                runY = py;
                runLen = 1;
            }
        }
        int[][] corners = {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}};
        for (int[] c : corners) {                                              // X de "aqui está" em cada canto
            for (int d = -2; d <= 2; d++) {
                rect(g, c[0] + d, c[1] + d, 1, 1, RED);
                rect(g, c[0] + d, c[1] - d, 1, 1, RED);
            }
        }
    }

    /**
     * Quadro do retrato no estilo da tela de inventário de referência: papel creme liso, moldura rosada de 2 px
     * com um laço quadrado vazado em cada canto (as linhas saem do laço), fio de luz por dentro e um fio fino
     * bege com os cantos cortados. Sem chão nem plataforma: o boneco é desenhado por cima, depois.
     */
    public static void portrait(GuiGraphics g, int x, int y, int w, int h, long t) {
        // paleta tirada do próprio pergaminho do mapa (D6B478 / DDBE83 / E3C78D / E9D098), moldura em marrom de couro
        final int cream = 0xFFE3C78D, edge = 0xFFD2AF72, frame = 0xFF9A6B42, light = 0xFFD9A872, fine = 0xFFC9A468, ink = 0xFF5A3A26;
        // sombra do papel sobre o mapa
        rect(g, x + 2, y + 3, w, h, 0x4A000000);
        // contorno escuro e papel com a borda um tom mais velha (vinheta), cantos cortados
        notched(g, x - 1, y - 1, w + 2, h + 2, ink);
        notched(g, x, y, w, h, edge);
        notched(g, x + 2, y + 2, w - 4, h - 4, 0xFFDDBE83);
        notched(g, x + 4, y + 4, w - 8, h - 8, cream);
        // fio fino interno, com os cantos cortados em diagonal
        int ix = x + 10, iy = y + 10, iw = w - 20, ih = h - 20, c = 4;
        rect(g, ix + c, iy, iw - 2 * c, 1, fine);
        rect(g, ix + c, iy + ih - 1, iw - 2 * c, 1, fine);
        rect(g, ix, iy + c, 1, ih - 2 * c, fine);
        rect(g, ix + iw - 1, iy + c, 1, ih - 2 * c, fine);
        for (int k = 0; k < c; k++) {
            rect(g, ix + c - 1 - k, iy + k, 1, 1, fine);
            rect(g, ix + iw - c + k, iy + k, 1, 1, fine);
            rect(g, ix + c - 1 - k, iy + ih - 1 - k, 1, 1, fine);
            rect(g, ix + iw - c + k, iy + ih - 1 - k, 1, 1, fine);
        }
        idBackdrop(g, x, y, w, h, t);
        // linhas da moldura (2 px) com fio de luz por dentro; começam depois do laço de cada canto
        int a = x + 5, b = y + 5, e = x + w - 5, f = y + h - 5;
        int s0 = 11;
        rect(g, x + s0, b, w - 2 * s0, 2, frame);
        rect(g, x + s0, f - 2, w - 2 * s0, 2, frame);
        rect(g, a, y + s0, 2, h - 2 * s0, frame);
        rect(g, e - 2, y + s0, 2, h - 2 * s0, frame);
        rect(g, x + s0, b + 2, w - 2 * s0, 1, light);
        rect(g, x + s0, f - 3, w - 2 * s0, 1, light);
        rect(g, a + 2, y + s0, 1, h - 2 * s0, light);
        rect(g, e - 3, y + s0, 1, h - 2 * s0, light);
        // laço quadrado vazado (6 x 6, com um furo de 2 x 2) em cada canto
        int[][] knots = {{a, b}, {e - 6, b}, {a, f - 6}, {e - 6, f - 6}};
        for (int[] q : knots) {
            rect(g, q[0], q[1], 6, 6, frame);
            rect(g, q[0] + 1, q[1] + 1, 4, 4, cream);
            rect(g, q[0] + 2, q[1] + 2, 2, 2, frame);
            rect(g, q[0] + 2, q[1] + 2, 1, 1, light);
        }
    }

    /** Sombrinha suave sob os pés do boneco (só um oval translúcido; não há plataforma). */
    public static void groundShadow(GuiGraphics g, int cx, int cy, int rx, int ry) {
        ellipse(g, cx, cy, rx, ry, argb(70, 0x1A0C04));
        ellipse(g, cx, cy, rx * 2 / 3, Math.max(1, ry * 2 / 3), argb(70, 0x1A0C04));
    }

    private static int hsh(int a, int b) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7FFFFFFF;
    }

    /** Retângulo recortado na faixa horizontal [minX, maxX) (para as nuvens e ondas que passam pela cena sem vazar). */
    private static void crect(GuiGraphics g, int x, int y, int w, int h, int argb, int minX, int maxX) {
        int x0 = Math.max(x, minX), x1 = Math.min(x + w, maxX);
        if (x1 > x0) rect(g, x0, y, x1 - x0, h, argb);
    }

    /**
     * A cena do retrato: entardecer no mar visto do convés. Céu em faixas de cor que trocam de uma para a outra com
     * pontilhado (dithering), sol baixo com halo, nuvens à deriva, mar com reflexo cintilante e carneirinhos, e o convés de
     * madeira onde o personagem pisa. Tudo é desenhado em retângulos (sem textura) e anima devagar com {@code t}.
     */
    public static void scene(GuiGraphics g, int x, int y, int w, int h, long t) {
        int hy = y + h * 56 / 100, deckY = y + h - 24, maxX = x + w;
        int[] sky = {0xFF232C58, 0xFF35407A, 0xFF5D5496, 0xFFA25F86, 0xFFDD8479, 0xFFF6BE78};
        int bandH = (hy - y) / sky.length;
        for (int i = 0; i < sky.length; i++) {
            int by = y + i * bandH, bh = i == sky.length - 1 ? hy - by : bandH;
            rect(g, x, by, w, bh, sky[i]);
            if (i > 0) {                                                       // pontilhado na emenda: a cor anterior se mistura em 2 fileiras
                for (int px = 0; px < w; px += 2) rect(g, x + px, by, 1, 1, sky[i - 1]);
                for (int px = 1; px < w; px += 4) rect(g, x + px, by + 1, 1, 1, sky[i - 1]);
            }
        }
        for (int k = 0; k < 9; k++) {                                          // estrelas piscando no alto
            int sx = x + 4 + hsh(k, 1) % (w - 8), sy = y + 3 + hsh(k, 2) % (bandH * 2);
            double tw = 0.5D + 0.5D * Math.sin(t / 420.0D + k * 1.7D);
            rect(g, sx, sy, 1, 1, argb(90 + (int) (150 * tw), 0xFFF4D8));
        }
        int sunX = x + w * 68 / 100;
        disc(g, sunX, hy, 13, argb(46, 0xFFC278));                             // halo
        disc(g, sunX, hy, 10, argb(70, 0xFFC278));
        disc(g, sunX, hy, 8, 0xFFFFB866);
        disc(g, sunX, hy, 6, 0xFFFFD98A);
        rect(g, sunX - 3, hy - 6, 3, 1, 0xFFFFF1C2);
        for (int k = 0; k < 3; k++) {                                          // nuvens: corpo claro e barriga escura, à deriva
            int cx = x + (int) ((t / 190L + k * 53L) % (w + 64)) - 32, cy = y + 8 + k * 12 + (k == 1 ? 6 : 0);
            int top = k == 1 ? 0xFFF3B79A : 0xFFD88FA0, bot = k == 1 ? 0xFFC9708A : 0xFF8A5E92;
            crect(g, cx + 4, cy - 2, 14, 2, top, x, maxX);
            crect(g, cx, cy, 26, 3, top, x, maxX);
            crect(g, cx + 2, cy + 3, 22, 1, bot, x, maxX);
        }
        // mar
        int[] sea = {0xFF3A8798, 0xFF22607F, 0xFF174566, 0xFF102F50};
        int seaH = deckY - hy, sb = Math.max(1, seaH / sea.length);
        for (int i = 0; i < sea.length; i++) {
            int by = hy + i * sb, bh = i == sea.length - 1 ? deckY - by : sb;
            rect(g, x, by, w, bh, sea[i]);
            if (i > 0) {
                for (int px = 0; px < w; px += 2) rect(g, x + px, by, 1, 1, sea[i - 1]);
                for (int px = 1; px < w; px += 4) rect(g, x + px, by + 1, 1, 1, sea[i - 1]);
            }
        }
        rect(g, x, hy, w, 1, 0xFFFFD79A);                                      // linha do horizonte acesa
        for (int r = hy + 1; r < deckY; r++) {                                 // reflexo do sol: cresce com a distância e cintila
            int half = 2 + (r - hy) / 3;
            if (((r + t / 150L) % 3L) != 0L) {
                int off = (int) Math.round(2.0D * Math.sin(t / 310.0D + r * 0.9D));
                rect(g, sunX - half + off, r, half * 2, 1, argb(150, 0xFFE6A0));
            }
        }
        for (int j = 0; j < (deckY - hy) / 5; j++) {                           // carneirinhos: traços claros que andam devagar
            int ry = hy + 4 + j * 5;
            for (int k = 0; k < 3; k++) {
                int bx = x + (int) ((hsh(j, k) % w + t * (1 + j % 3) / 330L) % (w + 8)) - 4;
                crect(g, bx, ry, 3 + hsh(k, j) % 4, 1, argb(130, 0xBFE8F0), x, maxX);
            }
        }
        // convés de madeira
        int dh = y + h - deckY;
        rect(g, x, deckY, w, dh, 0xFF7C4E2C);
        rect(g, x, deckY, w, 1, 0xFF2E190B);
        rect(g, x, deckY + 1, w, 1, 0xFFB98350);
        for (int r = 0; r * 7 + 2 < dh; r++) {
            int ry = deckY + 2 + r * 7, ph = Math.min(6, y + h - ry);
            if (ph <= 0) break;
            rect(g, x, ry, w, ph, (r & 1) == 0 ? 0xFF8A5A33 : 0xFF7F5230);
            rect(g, x, ry, w, 1, argb(60, 0xFFD9A0));
            if (ph == 6) rect(g, x, ry + 6, w, 1, 0xFF3F230F);
            for (int sx = x + ((r & 1) * 13) + 9; sx < maxX; sx += 26) rect(g, sx, ry, 1, ph, 0xFF3F230F);
            for (int k = 0; k < 3; k++) rect(g, x + hsh(r, k) % Math.max(1, w - 4), ry + 2 + k % 3, 3, 1, argb(70, 0x3F230F));   // veios
        }
        // vinheta: as bordas escurecem um pouco
        rect(g, x, y, w, 2, argb(70, 0x0A0614));
        rect(g, x, y, 2, h, argb(46, 0x0A0614));
        rect(g, maxX - 2, y, 2, h, argb(46, 0x0A0614));
        rect(g, x, y + h - 2, w, 2, argb(60, 0x0A0614));
    }

    /** Elipse cheia de cor única (pixel art), centrada em (cx, cy) com raios rx e ry. */
    public static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int argb) {
        if (rx <= 0 || ry <= 0) return;
        for (int dy = -ry; dy <= ry; dy++) {
            int half = (int) Math.round(rx * Math.sqrt(Math.max(0.0D, 1.0D - (dy * dy) / (double) (ry * ry + 0.0001D))));
            rect(g, cx - half, cy + dy, half * 2 + 1, 1, argb);
        }
    }

    /** Selo de cera vermelho com a estrela dos ventos em relevo; r é o raio, press 0..1 deixa a borda mais achatada. */
    public static void seal(GuiGraphics g, int cx, int cy, int r, double press) {
        int[][] blobs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}, {0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int br = Math.max(2, (int) (r * (0.36D + 0.08D * press)));
        for (int i = 0; i < blobs.length; i++) {
            int d = (int) (r * 0.78D) + (i % 2 == 0 ? 0 : -1);
            disc(g, cx + blobs[i][0] * d * (i < 4 ? 7 : 10) / 10, cy + blobs[i][1] * d * (i < 4 ? 7 : 10) / 10, br, 0xFF7A1A12);
        }
        disc(g, cx, cy, r, 0xFF7A1A12);
        disc(g, cx, cy, r - 1, 0xFFA82E22);
        disc(g, cx, cy, r - 3, 0xFF8E2016);
        disc(g, cx, cy, r - 4, 0xFFB63023);
        disc(g, cx - r / 3, cy - r / 3, Math.max(1, r / 5), argb(90, 0xFFFFFF));
        int ex = cx - 4, ey = cy - 4;
        mono(g, STAR, ex + 1, ey + 1, 0xFF5E120C);
        mono(g, STAR, ex, ey, 0xFFE8604A);
    }

    /** Papel avulso preso com um prego (janelinha de confirmação): bordas queimadas, vinheta e arabescos de ouro. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, long t) {
        rect(g, x + 3, y + 5, w, h, 0x66000000);
        notched(g, x, y, w, h, 0xFF2A1708);
        notched(g, x + 1, y + 1, w - 2, h - 2, 0xFF6B4521);
        notched(g, x + 2, y + 2, w - 4, h - 4, 0xFF93662F);
        notched(g, x + 3, y + 3, w - 6, h - 6, 0xFFB78845);
        notched(g, x + 4, y + 4, w - 8, h - 8, PAPER);
        int[] vig = {70, 44, 24, 10};
        for (int k = 0; k < vig.length; k++) outline(g, x + 5 + k, y + 5 + k, w - 10 - 2 * k, h - 10 - 2 * k, argb(vig[k], 0xA77C3E));
        outline(g, x + 9, y + 9, w - 18, h - 18, 0xFFB08A48);
        outline(g, x + 11, y + 11, w - 22, h - 22, argb(110, 0xB08A48));
        sprite(g, CURL, x + 8, y + 8, false, false);
        sprite(g, CURL, x + w - 18, y + 8, true, false);
        sprite(g, CURL, x + 8, y + h - 18, false, true);
        sprite(g, CURL, x + w - 18, y + h - 18, true, true);
        // prego no topo
        disc(g, x + w / 2, y + 4, 3, 0xFF1E120A);
        disc(g, x + w / 2, y + 4, 2, 0xFF7A7A82);
        rect(g, x + w / 2 - 1, y + 3, 1, 1, 0xFFD2D2DA);
    }

    /** Seta pequena para a direita (usada nas dicas). */
    public static void chevron(GuiGraphics g, int x, int y, int argb) {
        mono(g, ARROW_SMALL, x, y, argb);
    }
}
