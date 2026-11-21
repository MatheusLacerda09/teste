package br.com.lasthorizon.akuma.client;

import net.minecraft.Util;

/**
 * Relógio único das animações do tooltip: tudo (borda, brilho, estrelas, ícones, etiquetas)
 * usa a mesma fase, então pulsa na mesma batida e o pico acontece ao mesmo tempo.
 */
public final class TooltipAnim {
    private TooltipAnim() {}

    /** Duração de uma batida completa, em milissegundos. Aumente para ficar mais lento. */
    public static final long PERIOD_MS = 2400L;

    /** Fase de 0.0 a 1.0 dentro da batida atual. */
    public static double phase() {
        return (Util.getMillis() % PERIOD_MS) / (double) PERIOD_MS;
    }

    /** Pulso suave: 0 no início, 1 no meio da batida, 0 no fim. */
    public static double wave() {
        return 0.5D - 0.5D * Math.cos(phase() * Math.PI * 2.0D);
    }

    /** Mesmo pulso, de -1 a +1. */
    public static double signed() {
        return wave() * 2.0D - 1.0D;
    }
}
