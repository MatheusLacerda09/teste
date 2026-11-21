package br.com.lasthorizon.akuma.client;

import net.minecraft.client.Minecraft;

/**
 * Medidas da janela de conquistas alargada (o vanilla é 252 x 140, com área interna de 234 x 113).
 *
 * <p>Os mixins em {@code br.com.lasthorizon.akuma.mixin} trocam os números fixos do jogo por estes valores.
 * A janela cresce até {@link #MAX_W} x {@link #MAX_H} e encolhe sozinha em telas pequenas (nunca abaixo do
 * tamanho do vanilla), deixando folga nas bordas da tela (e espaço em cima para as abas).
 *
 * <p>Para mudar o tamanho é só editar {@link #MAX_W} e {@link #MAX_H}. O fundo animado é desenhado em
 * {@link SeaBackground#W} x {@link SeaBackground#H} (interior de 400 x 230); se aumentar a janela além disso,
 * rode de novo {@code tools/build_backgrounds.py} com {@code W, H} novos em {@code tools/ocean.py}.
 */
public final class WideAdvancements {
    private WideAdvancements() {}

    public static final int VANILLA_W = 252, VANILLA_H = 140;
    public static final int MAX_W = 400, MAX_H = 230;
    /** Folga nas bordas da tela. Em cima e embaixo é maior porque as abas ficam 28 px acima da janela. */
    public static final int MARGIN_X = 16, MARGIN_Y = 36;
    /** Bordas da moldura: 9 px nas laterais e embaixo, 18 px em cima (onde fica o título). */
    public static final int BORDER_X = 9, BORDER_TOP = 18, BORDER_BOTTOM = 9;

    public static int winW() {
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.max(VANILLA_W, Math.min(MAX_W, sw - 2 * MARGIN_X));
    }

    public static int winH() {
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.max(VANILLA_H, Math.min(MAX_H, sh - 2 * MARGIN_Y));
    }

    /** Área interna (onde ficam as conquistas). */
    public static int innerW() {
        return winW() - 2 * BORDER_X;
    }

    public static int innerH() {
        return winH() - BORDER_TOP - BORDER_BOTTOM;
    }
}
