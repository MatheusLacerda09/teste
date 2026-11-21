package br.com.lasthorizon.akuma.character;

/**
 * Como o peito do personagem feminino "diminui" com o busto escolhido na criação de personagem (o desenho 3D mora no
 * {@code BreastLayer}, que só usa o resultado daqui). Java puro, sem Minecraft, para poder ser testado em {@code tools/tests}.
 *
 * <p><b>O peito não encolhe: ele se recolhe.</b> A caixa do modelo (largura, altura e profundidade do Blockbench) continua
 * exatamente a mesma, então a largura não muda e os dois peitos continuam colados no meio, cobrindo o peitoral. O que muda com
 * o busto menor é só:
 * <ul>
 *   <li>o <b>ângulo</b>: a caixa fica menos inclinada para a frente (vai deitando para o corpo); e</li>
 *   <li>o <b>afundamento</b>: ela entra no peitoral, para dentro do corpo, o suficiente para a ponta de baixo da frente sair da
 *       superfície o tanto que o busto pede (o topo some dentro do corpo: o peito menor começa mais embaixo, em vez de uma tábua
 *       colada na clavícula).</li>
 * </ul>
 *
 * <p><b>De 100% até {@link #KNEE} (50%)</b> o ângulo é o do modelo vezes {@code s} elevado a {@link #TILT}, e a ponta de baixo sai
 * exatamente {@code s} vezes o que sai no modelo (50% sai metade).
 *
 * <p><b>Abaixo de {@link #KNEE}</b> a mesma regra deixava o peito quase liso: em 25% a ponta saía 0,6 px e em 10% só 0,24 px, uma faixa
 * branca chapada com um fio escuro embaixo. Mas a primeira correção (ângulo de 27° / 24° / 21° em 25% / 10% / 5%) afundava demais o peito
 * e o deixava inclinado demais: só a pontinha de baixo aparecia, e ele ficava pontudo, como uma prateleira. Por isso, a partir de 50%
 * (onde as duas regras se encontram, sem salto):
 * <ul>
 *   <li>a saliência cai como a raiz do busto ({@link #REACH_LOW}), não em linha reta: 25% sai 0,85 px, 10% sai 0,54 px e 5% sai 0,38 px; e</li>
 *   <li>o ângulo cai a partir do de 50% com {@link #TILT_LOW}: 22° em 25%, 14,6° em 10% e 10,7° em 5% (um pouco mais inclinado que a curva
 *       original, a raiz do busto, que dava 18,5° / 11,7° / 8°, e bem menos que os 24° / 18,5° / 15° da versão s^0,3).</li>
 * </ul>
 * Com menos ângulo a caixa afunda menos (0,65 / 0,47 / 0,36 px em vez de 1 px) e a frente aparece quase inteira (57% / 54% / 51% da caixa, em
 * vez de 47% / 34% / 26%), então o peito pequeno é uma rampa suave e não só uma ponta. A faixa escura de baixo (a face de baixo da caixa, que de
 * frente mostra {@code saliência x tangente do ângulo}) fica com 0,34 / 0,14 / 0,07 px. Resumo (ângulo / quanto a ponta de baixo sai): 100% 37° / 2,41 px,
 * 75% 33,9° / 1,81 px, 50% 30,1° / 1,20 px, 25% 22,0° / 0,85 px, 10% 14,6° / 0,54 px, 5% 10,7° / 0,38 px.
 *
 * <p>O ângulo também não pode cair demais: com a caixa quase deitada ela fica colada na superfície do corpo, como uma tábua, brigando em
 * profundidade com ela. E quanto mais inclinada e mais fundo, mais o fundo da caixa chega perto das costas do corpo: a trava de
 * {@link #retract} segura isso (com o modelo atual, de 3 px de fundo, ela não precisa agir em nenhum tamanho de 5% a 99%; a folga mínima
 * é de 0,47 px, em 29%).
 */
public final class BustShape {
    private BustShape() {
    }

    /** Expoente da curva do ângulo de 50% a 100%: ângulo = ângulo do modelo x busto^TILT (era 0,5, a raiz, e o peito menor ficava deitado demais). */
    public static final float TILT = 0.30F;
    /** Busto a partir do qual a caixa é desenhada como no modelo (acima disso o peito é o do Blockbench, sem mudar nada). */
    public static final float FULL = 0.999F;
    /** Busto onde as curvas mudam: de 100% até aqui vale TILT e a saliência proporcional; abaixo daqui, o peito pequeno (TILT_LOW e REACH_LOW). */
    public static final float KNEE = 0.5F;
    /**
     * Expoente do ângulo abaixo de {@link #KNEE}: ângulo = ângulo de 50% x (busto / 50%)^TILT_LOW. Quanto maior, mais deitado fica o peito
     * pequeno (0,15 deixava 27° em 25% e ficava pontudo; 0,45 dá 22° / 14,6° / 10,7° em 25% / 10% / 5%; 0,65 daria 19° / 10,5° / 6,7°).
     */
    public static final float TILT_LOW = 0.45F;
    /** Expoente da saliência abaixo de {@link #KNEE}: a ponta de baixo sai como a raiz do busto (0,5), não em linha reta (1), senão 25% e 10% ficam lisos. */
    public static final float REACH_LOW = 0.5F;
    /** Espessura do corpo (frente a costas) no modelo do jogo: o peito afundado nunca pode passar das costas. */
    public static final float BODY_DEPTH = 4.0F;
    /** Folga para a caixa não encostar na superfície de trás do corpo. */
    private static final float BACK_MARGIN = 0.1F;

    /** Fração do ângulo do modelo para o busto {@code s} (de 0 a 1): {@code s^TILT} de 50% a 100%, e abaixo disso continua dali com {@code TILT_LOW}. */
    public static float angleFactor(float s) {
        s = Math.max(0.0F, s);
        if (s >= KNEE) return (float) Math.pow(s, TILT);
        return (float) (Math.pow(KNEE, TILT) * Math.pow(s / KNEE, TILT_LOW));
    }

    /** Fração do que o peito do modelo sai do corpo para o busto {@code s} (de 0 a 1): {@code s} de 50% a 100%, e abaixo disso a raiz, a partir de 50%. */
    public static float outFactor(float s) {
        s = Math.max(0.0F, s);
        if (s >= KNEE) return s;
        return (float) (KNEE * Math.pow(s / KNEE, REACH_LOW));
    }

    /**
     * Ângulo e afundamento da caixa de um peito para o busto {@code s}.
     *
     * @param angle ângulo do modelo, em graus (inclinação para a frente, em torno do topo da caixa)
     * @param reach distância do pivô (topo da caixa) até a borda de baixo da frente: é ela que sai do corpo
     * @param depth profundidade da caixa (frente a trás)
     * @param s     busto de 0 a 1 (1 = o modelo)
     * @return {@code {ângulo em graus, afundamento em pixels para dentro do corpo}}; em 1 é o ângulo do modelo e nada de afundamento
     */
    public static float[] retract(float angle, float reach, float depth, float s) {
        if (s >= FULL) return new float[]{angle, 0.0F};
        s = Math.max(0.0F, s);
        float a = angle * angleFactor(s);                                          // graus
        float out = reach * (float) Math.sin(Math.toRadians(angle)) * outFactor(s);   // quanto a ponta de baixo sai da superfície
        float sink = reach * (float) Math.sin(Math.toRadians(a)) - out;            // para sair exatamente isso
        float room = BODY_DEPTH - depth * (float) Math.cos(Math.toRadians(a)) - BACK_MARGIN;   // até onde dá para afundar sem atravessar as costas
        return new float[]{a, Math.max(0.0F, Math.min(sink, room))};
    }

    /** Quanto a ponta de baixo da frente da caixa sai da superfície do corpo, em pixels (negativo = ainda dentro do corpo). */
    public static float protrusion(float angle, float reach, float sink) {
        return reach * (float) Math.sin(Math.toRadians(angle)) - sink;
    }
}
