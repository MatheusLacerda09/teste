package br.com.lasthorizon.akuma.client.particle;

/**
 * Parâmetros visuais de cada tipo de partícula. Edite os números para ajustar o efeito.
 *
 * @param minLife/maxLife   duração em ticks
 * @param minSize/maxSize   tamanho (metade da largura do quadrado, em blocos)
 * @param gravity           1 = cai como item; negativo = flutua para cima
 * @param friction          0..1, quanto da velocidade se mantém a cada tick
 * @param spin              velocidade máxima de rotação (radianos por tick)
 * @param animated          true = troca de quadro ao longo da vida; false = escolhe um sprite aleatório
 * @param fadeOut           some gradualmente no fim da vida
 * @param shrink            encolhe ao longo da vida
 * @param fullBright        brilha no escuro (ignora a luz do local)
 */
public record GomuParticleStyle(int minLife, int maxLife, float minSize, float maxSize,
                                float gravity, float friction, float spin,
                                boolean animated, boolean fadeOut, boolean shrink, boolean fullBright) {

    /** Migalhas da fruta: caem com gravidade, giram e somem. */
    public static final GomuParticleStyle BITE =
            new GomuParticleStyle(18, 30, 0.14F, 0.22F, 0.55F, 0.96F, 0.25F, false, true, false, false);

}
