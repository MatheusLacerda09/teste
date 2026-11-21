package br.com.lasthorizon.akuma.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Moldura dourada da aba de conquistas do Sea Sovereign, no mesmo estilo da borda do tooltip da Gomu Gomu no Mi:
 * ouro que pulsa entre {@code D9A441} e {@code FFE9A0}, contorno roxo-escuro, ornamentos em losango e um brilho que
 * corre pelas bordas. Tudo é desenhado com {@code fill()} (pixel art por retângulos), sem textura.
 *
 * <p>Chamada por {@code AdvancementsScreenMixin} no fim de {@code renderWindow}, por cima da moldura cinza do
 * jogo e do fundo, mas por baixo dos tooltips das conquistas.
 */
public final class SeaFrame {
    private SeaFrame() {}

    /** Duração de uma batida do brilho (ms); igual à do tooltip (TooltipAnim.PERIOD_MS). */
    public static final long PERIOD_MS = 2400L;
    /** Quantos px a moldura avança para fora da área interna (cobre a borda de dentro da moldura cinza). */
    private static final int PAD = 2;
    /** Larguras mínimas para desenhar os ornamentos do meio das bordas. */
    private static final int MIN_MID_W = 120, MIN_MID_H = 90;

    private static final int DARK = 0x120818;

    /** Canto superior esquerdo 16 x 16: D contorno, H luz, G ouro, S sombra. Os outros 3 cantos são espelhos. */
    private static final String[] CORNER = {
            "DDDDDDDDDDDDDD..",
            "DHHHHHHHHHHHHD..",
            "DHGGGGGGGGGGGS..",
            "DHGSSSSSSSSS....",
            "DHGSDDDDDDDD....",
            "DHGSDS..........",
            "DHGSD.GDGD......",
            "DHGSD.DGGGD.....",
            "DHGSD.GGHGG.....",
            "DHGSD.DGGGD.....",
            "DHGSD..DGD......",
            "DHGSD...........",
            "DHG.............",
            "DDS.............",
            "................",
            "................"};

    /** Ornamento do meio das bordas de cima e de baixo (27 x 9): losango com abas. */
    private static final String[] MID = {
            ".............D.............",
            "............DGD............",
            "....D......DGGGD......D....",
            "......SS..DGGHGGD..SS......",
            "SSSGGGGGGDGGHHHGGDGGGGGGSSS",
            "......SS..DGGHGGD..SS......",
            "....D......DGGGD......D....",
            "............DGD............",
            ".............D............."};

    /**
     * @param ix,iy  canto superior esquerdo da área interna da janela (px de tela)
     * @param iw,ih  tamanho da área interna
     * @param timeMs relógio em ms
     */
    public static void render(GuiGraphics g, int ix, int iy, int iw, int ih, long timeMs) {
        double wave = 0.5D - 0.5D * Math.cos((timeMs % PERIOD_MS) / (double) PERIOD_MS * Math.PI * 2.0D);
        int gold = lerp(0xD9A441, 0xFFE9A0, wave), shade = lerp(0x8A5A14, 0xC98A2A, wave);
        int light = lerp(0xFFE9A0, 0xFFFFFF, wave * wave);
        int x0 = ix - PAD, y0 = iy - PAD, x1 = ix + iw + PAD, y1 = iy + ih + PAD;   // x1, y1 exclusivos

        // sombra interna (vinheta roxa-escura), mais forte colada na moldura
        int[] vig = {112, 72, 40, 16};
        for (int k = 0; k < vig.length; k++) {
            int o = 4 + k, c = argb(vig[k], 0x0A0510);
            g.fill(x0 + o, y0 + o, x1 - o, y0 + o + 1, c);
            g.fill(x0 + o, y1 - o - 1, x1 - o, y1 - o, c);
            g.fill(x0 + o, y0 + o + 1, x0 + o + 1, y1 - o - 1, c);
            g.fill(x1 - o - 1, y0 + o + 1, x1 - o, y1 - o - 1, c);
        }

        // 4 anéis: contorno escuro, ouro, sombra dourada, contorno escuro fraco
        int[] ring = {argb(255, DARK), argb(255, gold), argb(255, shade), argb(170, DARK)};
        for (int k = 0; k < 4; k++) {
            g.fill(x0, y0 + k, x1, y0 + k + 1, ring[k]);
            g.fill(x0, y1 - k - 1, x1, y1 - k, ring[k]);
            g.fill(x0 + k, y0 + k + 1, x0 + k + 1, y1 - k - 1, ring[k]);
            g.fill(x1 - k - 1, y0 + k + 1, x1 - k, y1 - k - 1, ring[k]);
        }

        // brilho que corre pelas bordas de cima (para a direita) e de baixo (para a esquerda)
        int span = Math.max(1, x1 - x0 - 32);
        double u = (timeMs % 5200L) / 5200.0D;
        int gx = x0 + 16 + (int) (u * span);
        glint(g, gx, y0 + 1, x0 + 16, x1 - 16, argb(190, light));
        glint(g, x1 - 16 - (gx - (x0 + 16)) - 10, y1 - 2, x0 + 16, x1 - 16, argb(190, light));

        // cantos
        int cw = CORNER[0].length();
        int[] pal = {gold, shade, light};
        sprite(g, CORNER, x0, y0, false, false, pal);
        sprite(g, CORNER, x1 - cw, y0, true, false, pal);
        sprite(g, CORNER, x0, y1 - cw, false, true, pal);
        sprite(g, CORNER, x1 - cw, y1 - cw, true, true, pal);

        // ornamentos do meio (só se a janela for grande o bastante)
        if (iw >= MIN_MID_W) {
            int mx = (x0 + x1) / 2 - MID[0].length() / 2;
            sprite(g, MID, mx, y0 + 1 - MID.length / 2, false, false, pal);
            sprite(g, MID, mx, y1 - 2 - MID.length / 2, false, true, pal);
        }
        if (ih >= MIN_MID_H) {
            // laterais: losangos pequenos (o MID girado seria grande demais), 7 x 7
            int my = (y0 + y1) / 2;
            diamond(g, x0 + 1, my, gold, light);
            diamond(g, x1 - 2, my, gold, light);
        }
    }

    /** Losango de raio 3 com contorno escuro, centrado em (cx, cy). */
    private static void diamond(GuiGraphics g, int cx, int cy, int gold, int light) {
        for (int dy = -4; dy <= 4; dy++) {
            int half = 4 - Math.abs(dy);
            for (int dx = -half; dx <= half; dx++) {
                int d = Math.abs(dx) + Math.abs(dy);
                int c = d == 4 ? argb(255, DARK) : d <= 1 ? argb(255, light) : argb(255, gold);
                g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
            }
        }
    }

    private static void glint(GuiGraphics g, int x, int y, int minX, int maxX, int color) {
        int a = Math.max(x, minX), b = Math.min(x + 10, maxX);
        if (b > a) g.fill(a, y, b, y + 1, color);
    }

    /** Desenha um sprite de caracteres, juntando pixels vizinhos de mesma cor numa linha só. */
    private static void sprite(GuiGraphics g, String[] rows, int x, int y, boolean flipX, boolean flipY, int[] pal) {
        int h = rows.length, w = rows[0].length();
        for (int ry = 0; ry < h; ry++) {
            String row = rows[ry];
            int dy = y + (flipY ? h - 1 - ry : ry);
            int c0 = 0;
            while (c0 < w) {
                char ch = row.charAt(c0);
                int c1 = c0 + 1;
                while (c1 < w && row.charAt(c1) == ch) c1++;
                if (ch != '.') {
                    int col = switch (ch) {
                        case 'G' -> argb(255, pal[0]);
                        case 'S' -> argb(255, pal[1]);
                        case 'H' -> argb(255, pal[2]);
                        default -> argb(255, DARK);
                    };
                    int dx0 = flipX ? x + w - c1 : x + c0, dx1 = flipX ? x + w - c0 : x + c1;
                    g.fill(dx0, dy, dx1, dy + 1, col);
                }
                c0 = c1;
            }
        }
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, double f) {
        int r = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int ca = a >> shift & 0xFF, cb = b >> shift & 0xFF;
            r |= ((int) Math.round(ca + (cb - ca) * f) & 0xFF) << shift;
        }
        return r;
    }
}
