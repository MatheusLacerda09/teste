package br.com.lasthorizon.akuma.character;

/**
 * Altura do personagem (criação de personagem) transformada na escala do modelo. Java puro, sem Minecraft: o mixin do modelo (cliente),
 * a hitbox (cliente e servidor) e o retrato da tela usam a mesma conta, e {@code tools/tests} confere.
 *
 * <p>A altura é escolhida em blocos, de 1,5 a 2,5 (o jogador padrão tem 1,8). A largura NÃO é uma escolha: acompanha a altura, porque a escala
 * é a mesma nos três eixos e vale para o corpo inteiro (cabeça, tronco, braços e pernas). É isso que impede o personagem de ficar esticado, como
 * ficava quando altura e largura eram duas barras com escalas diferentes em cada eixo (comprido e fino com a cabeça estreita, ou achatado com a
 * cabeça larga). Uma escala igual nos três eixos também passa por qualquer rotação sem distorcer: braços e pernas mantêm o comprimento ao andar
 * e a cabeça não se deforma ao virar.
 */
public final class BodyScale {
    private BodyScale() {
    }

    /** Escala do personagem para a altura em centésimos de bloco (150 a 250; fora da faixa é preso nela). A altura padrão (180) é a escala 1, exata. */
    public static float of(int height) {
        int h = Math.max(CharacterOptions.HEIGHT_MIN, Math.min(CharacterOptions.HEIGHT_MAX, height));
        return h == CharacterOptions.HEIGHT_DEFAULT ? 1.0F : h / (float) CharacterOptions.HEIGHT_DEFAULT;
    }

    /** Sem ajuste nenhum (altura padrão): o mixin e a hitbox nem mexem em nada. */
    public static boolean isStandard(int height) {
        return of(height) == 1.0F;
    }
}
