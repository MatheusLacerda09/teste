package br.com.lasthorizon.akuma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Ícones da criação de personagem: um único atlas de 32 x 32 por ícone ({@code textures/gui/map_icons32.png}, 8 colunas).
 * Nos lugares pequenos ({@link #draw}) o mesmo desenho é reduzido para 16 x 16 na hora de desenhar; nos grandes
 * ({@link #drawBig}) ele sai no tamanho real.
 *
 * <p>São pixel art colorida com contorno escuro, feita por {@code tools/art/make_map_icons.py}; a ordem dos ícones
 * lá (lista {@code NAMES}) é a ordem das constantes daqui. Para trocar um desenho, edite o script e gere de novo.
 */
public final class MapIcons {
    private MapIcons() {}

    public static final int SIZE = 16;
    public static final int BIG = 32;
    private static final int COLS = 8, ROWS = 6;
    private static final ResourceLocation ATLAS_BIG = new ResourceLocation("lasthorizon", "textures/gui/map_icons32.png");

    // abas
    public static final int TAB_IDENTITY = 0, TAB_BODY = 1, TAB_HAIR = 2, TAB_OUTFIT = 3, TAB_ORIGIN = 4, TAB_RANDOM = 5;
    // títulos de seção
    public static final int H_NAME = 6, H_SKIN = 7, H_EYES = 8, H_FACE = 9, H_DROP = 10, H_HAIR = 11, H_OUTFIT = 12, H_PALETTE = 13;
    // gênero
    public static final int G_MARS = 14, G_VENUS = 15, G_BOTH = 16;
    // origens
    public static final int O_PIRATE = 17, O_MARINE = 18, O_NAV = 19, O_COOK = 20;
    // bônus das origens
    public static final int B_SWORD = 21, B_SHIELD = 22, B_BOOT = 23, B_HEART = 24;
    // abas Alma, Passado e Sonho; títulos de idade, mar natal e lema
    public static final int TAB_SOUL = 25, TAB_PAST = 26, H_AGE = 28, H_SEA = 29, H_MOTTO = 30;

    // epíteto, barba e acessório; um ícone para cada eixo da alma (farol, balança, raposa, gaivota, caneca, nuvem)
    public static final int H_EPITHET = 31, H_BEARD = 32, H_ACCESSORY = 33;
    public static final int[] SOUL_AXIS = {34, 35, 36, 37, 38, 39};
    public static final int H_RACE = 40, H_RACES = 41;   // punho (card da raça Humana); globo (título da aba Raça)

    /** Desenha o ícone {@code id} com o canto de cima à esquerda em ({@code x}, {@code y}). */
    public static void draw(GuiGraphics g, int id, int x, int y) {
        g.flush();                                  // desenha os retângulos que esperam no lote ANTES do blit (ordem das camadas) e antes do enableBlend
        RenderSystem.enableBlend();                 // um fill() antes deste blit desliga o blend: sem isto o fundo sai opaco
        g.blit(ATLAS_BIG, x, y, SIZE, SIZE, (float) ((id % COLS) * BIG), (float) ((id / COLS) * BIG), BIG, BIG, COLS * BIG, ROWS * BIG);
    }

    /**
     * Ícone animado: {@code scale} cresce/encolhe em volta do centro e {@code tilt} (graus) inclina. Com escala 1 e sem
     * inclinação cai no desenho normal, para os pixels continuarem alinhados quando o ícone está parado.
     */
    public static void drawAnim(GuiGraphics g, int id, int x, int y, float scale, float tilt) {
        if (Math.abs(scale - 1.0F) < 0.01F && Math.abs(tilt) < 0.4F) {
            draw(g, id, x, y);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x + SIZE / 2.0F, y + SIZE / 2.0F, 0.0F);
        if (tilt != 0.0F) g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));
        g.pose().scale(scale, scale, 1.0F);
        g.pose().translate(-SIZE / 2.0F, -SIZE / 2.0F, 0.0F);
        draw(g, id, 0, 0);
        g.pose().popPose();
    }

    /**
     * Ícone achatado/esticado: largura e altura saem em pixels inteiros (nada borrado), o ícone fica centrado na
     * horizontal e apoiado embaixo, como um boneco de borracha pulando.
     */
    public static void drawSquash(GuiGraphics g, int id, int x, int y, double sx, double sy) {
        int w = Math.max(2, (int) Math.round(SIZE * sx)), h = Math.max(2, (int) Math.round(SIZE * sy));
        g.flush();                                  // desenha os retângulos que esperam no lote ANTES do blit (ordem das camadas) e antes do enableBlend
        RenderSystem.enableBlend();
        g.blit(ATLAS_BIG, x + (SIZE - w) / 2, y + (SIZE - h), w, h, (float) ((id % COLS) * BIG), (float) ((id / COLS) * BIG), BIG, BIG, COLS * BIG, ROWS * BIG);
    }

    /** Desenha o ícone com o movimento de {@link IconMotion}. */
    public static void drawPose(GuiGraphics g, int id, int x, int y, IconMotion.Pose p) {
        if (p.sx() == 1.0D && p.sy() == 1.0D) draw(g, id, x + p.dx(), y + p.dy());
        else drawSquash(g, id, x + p.dx(), y + p.dy(), p.sx(), p.sy());
    }

    // ---- ícones animados das bandeiras: atlas {@code map_icons_anim.png}, uma linha por ícone e {@link #ANIM_FRAMES} quadros por linha
    // (gerado por tools/art/make_icon_anim.py; a ordem de ANIM_ICON tem que ser a de ROWS lá). O desenho muda por dentro
    // (cabelo ondula, chama pisca, ouro brilha), o ícone não sai do lugar.
    private static final ResourceLocation ATLAS_ANIM = new ResourceLocation("lasthorizon", "textures/gui/map_icons_anim.png");
    public static final int ANIM_FRAMES = 8;
    private static final int[] ANIM_ICON = {TAB_IDENTITY, TAB_BODY, TAB_HAIR, TAB_OUTFIT, TAB_SOUL, TAB_PAST, TAB_RANDOM};
    /** Duração de cada quadro (ms) por ícone: a chama é rápida, o corpo respira devagar. */
    private static final long[] ANIM_MS = {150L, 210L, 130L, 140L, 115L, 150L, 130L};

    /** Linha do ícone {@code id} no atlas de animação, ou -1 se ele não tem animação. */
    private static int animRow(int id) {
        for (int i = 0; i < ANIM_ICON.length; i++) if (ANIM_ICON[i] == id) return i;
        return -1;
    }

    /**
     * Desenha o ícone {@code id} com a animação do instante {@code timeMs}. Cada ícone começa num ponto diferente do ciclo,
     * para as bandeiras não piscarem todas juntas. Ícone sem animação sai parado, como em {@link #draw}.
     */
    public static void drawLive(GuiGraphics g, int id, int x, int y, long timeMs) {
        int row = animRow(id);
        if (row < 0) {
            draw(g, id, x, y);
            return;
        }
        int frame = (int) (((timeMs + row * 173L) / ANIM_MS[row]) % ANIM_FRAMES);
        g.flush();                                  // desenha os retângulos que esperam no lote ANTES do blit (ordem das camadas) e antes do enableBlend
        RenderSystem.enableBlend();
        g.blit(ATLAS_ANIM, x, y, SIZE, SIZE, (float) (frame * BIG), (float) (row * BIG), BIG, BIG, ANIM_FRAMES * BIG, ANIM_ICON.length * BIG);
    }

    /** O ícone no tamanho real, 32 x 32. */
    public static void drawBig(GuiGraphics g, int id, int x, int y) {
        g.flush();                                  // desenha os retângulos que esperam no lote ANTES do blit (ordem das camadas) e antes do enableBlend
        RenderSystem.enableBlend();
        g.blit(ATLAS_BIG, x, y, BIG, BIG, (float) ((id % COLS) * BIG), (float) ((id / COLS) * BIG), BIG, BIG, COLS * BIG, ROWS * BIG);
    }
}
