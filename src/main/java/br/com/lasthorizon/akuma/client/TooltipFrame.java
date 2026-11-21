package br.com.lasthorizon.akuma.client;

/** Posição/tamanho do tooltip atual, guardados pelo evento Color para o desenho do fundo e da moldura. */
public final class TooltipFrame {
    private TooltipFrame() {}
    public static int x, y, w, h;
    public static boolean valid;

    /** Quanto a moldura avança para fora do texto (px). */
    public static final int OUT = 6;

    /** Retângulo da moldura (canto e tamanho). */
    public static int fx() { return x - OUT; }
    public static int fy() { return y - OUT; }
    public static int fw() { return w + OUT * 2; }
    public static int fh() { return h + OUT * 2; }
}
