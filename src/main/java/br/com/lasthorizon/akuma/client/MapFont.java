package br.com.lasthorizon.akuma.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Texto do mapa da criação de personagem: sempre a fonte padrão do Minecraft. As "variações" são só jeitos de
 * desenhar a mesma fonte, como no jogo. Todos os estilos usam uma única passada nítida; a hierarquia vem
 * da cor, do tamanho e do posicionamento, evitando o borrão do antigo negrito deslocado.
 */
public final class MapFont {
    private MapFont() {}

    public static final int VANILLA = -1, BODY = 0, BOLD = 1, TITLE = 2, NOTE = 3;

    private static boolean heavy(int kind) {
        return false;
    }

    /** Largura em pixels de {@code s}, sempre igual à métrica real da fonte. */
    public static int width(Font font, String s, int kind) {
        return font.width(s) + (heavy(kind) ? 1 : 0);
    }

    /**
     * Desenha {@code s} com o canto de cima à esquerda em (x, y). Sem sombra nem halo.
     */
    public static void draw(GuiGraphics g, Font font, String s, int x, int y, int argb, int kind) {
        drawRaw(g, font, s, x, y, argb, kind);
    }

    /** Sem halo nem sombra (para quem já desenha a própria sombra ou contorno). */
    public static void drawRaw(GuiGraphics g, Font font, String s, int x, int y, int argb, int kind) {
        g.flush();                                   // retângulos pendentes do lote saem antes do texto (ordem das camadas)
        g.drawString(font, s, x, y, argb, false);
        g.flush();                                   // e o texto sai já, para o próximo lote não se misturar com ele
    }

    /** Mantido por compatibilidade: a fonte do jogo tem todas as letras que o nome permite. */
    public static int kindFor(String s, int kind) {
        return kind;
    }
}
