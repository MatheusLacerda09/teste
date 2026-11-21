package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.LastHorizonMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;

/** Desenha etiquetas, linhas com ícone animado e a moldura ornamentada animada do tooltip. */
public class ClientFruitTooltip implements ClientTooltipComponent {
    private static final ResourceLocation ICONS = new ResourceLocation(LastHorizonMod.MOD_ID, "textures/gui/tooltip_icons.png");
    private static final ResourceLocation ORN = new ResourceLocation(LastHorizonMod.MOD_ID, "textures/gui/tooltip_ornaments.png");
    private static final int BADGE_PAD = 6, BADGE_H = 11, BADGE_GAP = 3, BADGE_MARGIN = 3;
    private static final int LINE_H = 11, ICON = 9, ICON_GAP = 3;

    private final FruitTooltip data;

    public ClientFruitTooltip(FruitTooltip data) {
        this.data = data;
    }

    private int entryHeight(FruitTooltip.Entry e) {
        if (!e.badges().isEmpty()) return BADGE_H + BADGE_MARGIN * 2;
        if (e.text() != null) return LINE_H;
        return e.gap();
    }

    private static final int BADGE_ICON = 11;

    private static int badgeW(Font font, FruitTooltip.Badge b) {
        return BADGE_ICON + font.width(b.label()) + 5;
    }

    private int entryWidth(Font font, FruitTooltip.Entry e) {
        if (!e.badges().isEmpty()) {
            int w = 0;
            for (FruitTooltip.Badge b : e.badges()) w += badgeW(font, b) + BADGE_GAP;
            return w - BADGE_GAP;
        }
        if (e.text() != null) {
            return font.width(e.text()) + (e.icon() != FruitTooltip.Icon.NONE ? ICON + ICON_GAP : 0);
        }
        return 0;
    }

    @Override
    public int getHeight() {
        int h = 0;
        for (FruitTooltip.Entry e : data.entries()) h += entryHeight(e);
        return h;
    }

    @Override
    public int getWidth(Font font) {
        int w = 0;
        for (FruitTooltip.Entry e : data.entries()) w = Math.max(w, entryWidth(font, e));
        return w;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        RenderSystem.enableBlend();
        int cy = y;
        for (FruitTooltip.Entry e : data.entries()) {
            if (!e.badges().isEmpty()) {
                drawBadges(font, g, e, x, cy + BADGE_MARGIN);
            } else if (e.text() != null) {
                int tx = x;
                if (e.icon() != FruitTooltip.Icon.NONE) {
                    drawAnimatedIcon(g, e.icon(), x, cy);
                    tx += ICON + ICON_GAP;
                }
                g.drawString(font, e.text(), tx, cy + 1, 0xFFFFFFFF, true);
            }
            cy += entryHeight(e);
        }
        drawFrameOrnaments(g);
    }

    /** Todos os ícones seguem a mesma batida: borracha estica, setas sobem/descem juntas. */
    private void drawAnimatedIcon(GuiGraphics g, FruitTooltip.Icon icon, int x, int y) {
        int u = switch (icon) { case RUBBER -> 0; case UP -> 10; default -> 20; };
        double s = TooltipAnim.signed();
        switch (icon) {
            case RUBBER -> {
                float sx = (float) (1.0D + 0.18D * s);
                float sy = (float) (1.0D - 0.12D * s);
                g.pose().pushPose();
                // a arte da borracha ocupa só as linhas 1..5 do quadro: desce 1 px e gira em torno do centro dela
                g.pose().translate(x + 4.5F, y + 1F + 3.5F, 0F);
                g.pose().scale(sx, sy, 1F);
                g.pose().translate(-4.5F, -3.5F, 0F);
                g.blit(ICONS, 0, 0, u, 0, ICON, ICON, 32, 16);
                g.pose().popPose();
            }
            case UP -> g.blit(ICONS, x, y - (int) Math.round(s), u, 0, ICON, ICON, 32, 16);
            default -> g.blit(ICONS, x, y + (int) Math.round(s), u, 0, ICON, ICON, 32, 16);
        }
    }

    /** Ícones 5 x 5 das etiquetas (0 = redemoinho da Akuma no Mi, 1 = diamante da Paramecia, 2 = estrela da Lendária). */
    private static final String[][] GLYPH = {
            {".###.", "#...#", "#.#.#", "#..##", ".###."},
            {"..#..", ".###.", "#####", ".###.", "..#.."},
            {"..#..", "..#..", "#####", ".###.", "##.##"}};

    /**
     * Etiquetas no estilo dos pacotes de cargo em pixel art: contorno de 1 px na cor escurecida, miolo com luz em cima
     * e sombra embaixo, quinas cortadas, pequenos entalhes nas pontas, ícone e texto claros (cor da etiqueta
     * clareada) com sombra escura. Animação pixelada: brilho diagonal em degraus de 1 px (16 quadros por batida) e
     * faísca em cruz que pisca.
     */
    private void drawBadges(Font font, GuiGraphics g, FruitTooltip.Entry e, int x, int top) {
        int cx = x;
        final int step = (int) (TooltipAnim.phase() * 16.0D);        // 0..15, quadros "pixelados"
        for (FruitTooltip.Badge b : e.badges()) {
            int w = badgeW(font, b), h = BADGE_H;
            int c = b.color();
            int light = shade(c, 1.25D), dark = shade(c, 0.78D), line = shade(c, 0.42D), deep = shade(c, 0.22D);
            int tint = mixWhite(c, 0.80D);
            int r = cx + w, bt = top + h;
            g.fill(cx + 1, bt, r - 1, bt + 1, 0x77000000);                 // sombra projetada
            g.fill(cx + 1, top, r - 1, top + 1, line);                    // contorno (quinas cortadas)
            g.fill(cx + 1, bt - 1, r - 1, bt, line);
            g.fill(cx, top + 1, cx + 1, bt - 1, line);
            g.fill(r - 1, top + 1, r, bt - 1, line);
            g.fill(cx + 1, top + 1, r - 1, bt - 1, c);                    // miolo
            g.fill(cx + 1, top + 1, r - 1, top + 2, light);               // luz de cima
            g.fill(cx + 1, top + 2, r - 1, top + 3, shade(c, 1.10D));
            g.fill(cx + 1, bt - 2, r - 1, bt - 1, dark);                  // sombra de baixo
            g.fill(cx + 1, top + 1, cx + 2, top + 2, line);               // cantinhos arredondados
            g.fill(r - 2, top + 1, r - 1, top + 2, line);
            g.fill(cx + 1, bt - 2, cx + 2, bt - 1, line);
            g.fill(r - 2, bt - 2, r - 1, bt - 1, line);
            g.fill(cx + 3, top + 1, cx + 4, top + 2, deep);               // entalhes nas pontas
            g.fill(r - 4, top + 1, r - 3, top + 2, deep);
            g.fill(cx + 3, bt - 2, cx + 4, bt - 1, deep);
            g.fill(r - 4, bt - 2, r - 3, bt - 1, deep);
            // ícone 5 x 5 (sombra escura + cor clara)
            String[] gl = GLYPH[Math.floorMod(b.glyph(), GLYPH.length)];
            for (int gy = 0; gy < 5; gy++) {
                for (int gx = 0; gx < 5; gx++) {
                    if (gl[gy].charAt(gx) != '#') continue;
                    g.fill(cx + 4 + gx, top + 3 + gy, cx + 5 + gx, top + 4 + gy, deep);
                    g.fill(cx + 3 + gx, top + 2 + gy, cx + 4 + gx, top + 3 + gy, tint);
                }
            }
            // brilho diagonal pixelado
            int span = w - 4;
            int sx = cx + 2 + (int) ((step / 16.0D * 1.6D - 0.3D) * span);
            for (int ry = 1; ry < h - 1; ry++) {
                int px = sx - ry;
                for (int k = 0; k < 2; k++) {
                    int xx = px + k;
                    if (xx > cx && xx < r - 1) g.fill(xx, top + ry, xx + 1, top + ry + 1, k == 0 ? 0x70FFFFFF : 0x38FFFFFF);
                }
            }
            // faísca em cruz, só em alguns quadros
            if (step >= 5 && step <= 9) {
                int sxp = r - 6, syp = top + 3;
                int al = (step == 7 ? 0xFF : 0x90) << 24 | 0xFFFFFF;
                g.fill(sxp, syp - 1, sxp + 1, syp + 2, al);
                g.fill(sxp - 1, syp, sxp + 2, syp + 1, al);
            }
            // texto claro com sombra escura (como nos pacotes de cargo)
            g.drawString(font, b.label(), cx + BADGE_ICON + 1, top + 3, deep, false);
            g.drawString(font, b.label(), cx + BADGE_ICON, top + 2, tint, false);
            cx += w + BADGE_GAP;
        }
    }

    private static int mixWhite(int c, double k) {
        int rr = (int) ((c >> 16 & 0xFF) + (255 - (c >> 16 & 0xFF)) * k), gg = (int) ((c >> 8 & 0xFF) + (255 - (c >> 8 & 0xFF)) * k),
                bb = (int) ((c & 0xFF) + (255 - (c & 0xFF)) * k);
        return 0xFF000000 | rr << 16 | gg << 8 | bb;
    }

    private static int shade(int c, double k) {
        int rr = Math.min(255, (int) ((c >> 16 & 0xFF) * k)), gg = Math.min(255, (int) ((c >> 8 & 0xFF) * k)),
                bb = Math.min(255, (int) ((c & 0xFF) * k));
        return 0xFF000000 | rr << 16 | gg << 8 | bb;
    }

    /** Canto "chave grega" 7 x 7 (G ouro, S sombra, R detalhe vermelho). Os outros 3 cantos são espelhos. */
    private static final String[] CORNER = {
            "GGGGGGG",
            "GSSSSSS",
            "GS.....",
            "GS.GGG.",
            "GS.GRG.",
            "GS.GGG.",
            "GS....."};
    /** Ornamento do meio das bordas (7 x 5): losango. */
    private static final String[] MID = {"...G...", "..GSG..", ".GSRSG.", "..GSG..", "...G..."};

    /**
     * Moldura em linha dupla com cantos de "chave grega": filete externo dourado que pulsa, vão escuro, filete
     * interno vermelho, cantos e losangos no meio. Bem diferente da moldura da aba de conquistas.
     */
    private void drawFrameOrnaments(GuiGraphics g) {
        if (!TooltipFrame.valid) return;
        int x0 = TooltipFrame.fx(), y0 = TooltipFrame.fy(), x1 = x0 + TooltipFrame.fw(), y1 = y0 + TooltipFrame.fh();
        double wave = TooltipAnim.wave();
        int gold = 0xFF000000 | lerp(0xD9A441, 0xFFE9A0, wave), shade = 0xFF000000 | lerp(0x8A5A14, 0xC98A2A, wave);
        int red = 0xFF000000 | lerp(0xA02A3A, 0xE0586A, wave);
        int bg = 0xF0100010;
        // faixa entre a borda externa e o fundo do jogo (3 px)
        g.fill(x0, y0, x1, y0 + 3, bg);
        g.fill(x0, y1 - 3, x1, y1, bg);
        g.fill(x0, y0 + 3, x0 + 3, y1 - 3, bg);
        g.fill(x1 - 3, y0 + 3, x1, y1 - 3, bg);
        // linha externa dourada (0) e interna vermelha (2)
        g.fill(x0, y0, x1, y0 + 1, gold);
        g.fill(x0, y1 - 1, x1, y1, gold);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, gold);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, gold);
        g.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 3, red);
        g.fill(x0 + 2, y1 - 3, x1 - 2, y1 - 2, red);
        g.fill(x0 + 2, y0 + 3, x0 + 3, y1 - 3, red);
        g.fill(x1 - 3, y0 + 3, x1 - 2, y1 - 3, red);

        drawTravelingShine(g, x0, y0, x1 - x0, y1 - y0);

        int cw = CORNER[0].length();
        int[] pal = {gold, shade, red};
        sprite(g, CORNER, x0 - 1, y0 - 1, false, false, pal);
        sprite(g, CORNER, x1 - cw + 1, y0 - 1, true, false, pal);
        sprite(g, CORNER, x0 - 1, y1 - cw + 1, false, true, pal);
        sprite(g, CORNER, x1 - cw + 1, y1 - cw + 1, true, true, pal);
        int mx = (x0 + x1) / 2 - 3;
        sprite(g, MID, mx, y0 - 2, false, false, pal);
        sprite(g, MID, mx, y1 - 3, false, false, pal);
    }

    private static void sprite(GuiGraphics g, String[] rows, int x, int y, boolean flipX, boolean flipY, int[] pal) {
        int h = rows.length, w = rows[0].length();
        for (int ry = 0; ry < h; ry++) {
            for (int rx = 0; rx < w; rx++) {
                char ch = rows[ry].charAt(rx);
                if (ch == '.') continue;
                int col = ch == 'G' ? pal[0] : ch == 'S' ? pal[1] : pal[2];
                int px = x + (flipX ? w - 1 - rx : rx), py = y + (flipY ? h - 1 - ry : ry);
                g.fill(px, py, px + 1, py + 1, col);
            }
        }
    }

    private static int lerp(int a, int b, double f) {
        int r = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int ca = a >> shift & 0xFF, cb = b >> shift & 0xFF;
            r |= ((int) Math.round(ca + (cb - ca) * f) & 0xFF) << shift;
        }
        return r;
    }

    /** Rastro dourado que dá exatamente UMA volta na borda por batida. */
    private void drawTravelingShine(GuiGraphics g, int bx, int by, int bw, int bh) {
        int perimeter = 2 * (bw + bh) - 4;
        int head = (int) (TooltipAnim.phase() * perimeter);
        final int trail = 18;
        for (int i = 0; i < trail; i++) {
            int s = ((head - i) % perimeter + perimeter) % perimeter;
            int px, py;
            if (s < bw) { px = bx + s; py = by; }
            else if (s < bw + bh - 1) { px = bx + bw - 1; py = by + (s - bw + 1); }
            else if (s < 2 * bw + bh - 2) { px = bx + bw - 1 - (s - (bw + bh - 2)); py = by + bh - 1; }
            else { px = bx; py = by + bh - 1 - (s - (2 * bw + bh - 3)); }
            int alpha = 230 - i * (230 / trail);
            g.fill(px, py, px + 1, py + 1, (alpha << 24) | 0xFFF0B0);
        }
    }
}
