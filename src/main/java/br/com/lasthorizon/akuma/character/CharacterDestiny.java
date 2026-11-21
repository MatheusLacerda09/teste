package br.com.lasthorizon.akuma.character;

/**
 * O "destino" que a personalidade revela: o eixo em que o personagem mais se afastou do meio dá o arquétipo da alma
 * (título, força e contradição). Se todos os eixos estão no meio, a alma é "equilibrada".
 * Java puro (sem classes do Minecraft). Os textos ficam no lang: {@code character.lasthorizon.<key>},
 * {@code <key>.strength} e {@code <key>.flaw} (a variante 0 usa essas chaves; as outras terminam em {@code .1}, {@code .2}...).
 *
 * <p>Variantes: cada alma tem {@link #VARIANTS} textos de força e {@link #VARIANTS} de contradição; qual aparece sai dos
 * seis traços (sem nada novo para salvar: cliente e servidor chegam sempre no mesmo texto). Se TODOS os traços estão
 * no máximo (extremos), a alma é a lendária ({@code soul.legend}).
 *
 * <p>Eixos (lado 0 = esquerda, lado 1 = direita): 0 Cauteloso/Destemido, 1 Calculista/Compassivo, 2 Astuto/Honrado,
 * 3 Livre/Leal, 4 Solitário/Camarada, 5 Realista/Sonhador.
 */
public final class CharacterDestiny {
    /** Eixo dominante (0 a 5) e lado (0 ou 1); {@code axis == -1}: equilibrado. */
    public final int axis, side;
    /** Qual texto de força e de contradição desta alma (0 a {@link #VARIANTS} - 1). */
    public final int strengthVariant, flawVariant;
    /** Todos os seis traços no máximo. */
    private final boolean legendary;

    /** Quantos textos de força (e de contradição) cada alma tem. */
    public static final int VARIANTS = 4;

    private CharacterDestiny(int axis, int side, int strengthVariant, int flawVariant, boolean legendary) {
        this.axis = axis;
        this.side = side;
        this.strengthVariant = strengthVariant;
        this.flawVariant = flawVariant;
        this.legendary = legendary;
    }

    public static CharacterDestiny of(CharacterData d) {
        int[] t = new int[CharacterOptions.TRAITS];
        for (int i = 0; i < t.length; i++) t[i] = d.trait(i);
        return of(t);
    }

    public static CharacterDestiny of(int[] traits) {
        int best = -1, bestPull = 0;
        for (int i = 0; i < CharacterOptions.TRAITS; i++) {
            int pull = Math.abs(traits[i] - CharacterOptions.TRAIT_MID);
            if (pull > bestPull) {            // empate: vale o primeiro eixo da lista
                bestPull = pull;
                best = i;
            }
        }
        boolean legend = true;
        int h = 17;
        for (int i = 0; i < CharacterOptions.TRAITS; i++) {
            if (Math.abs(traits[i] - CharacterOptions.TRAIT_MID) != CharacterOptions.TRAIT_MID) legend = false;
            h = h * 31 + traits[i] + 1;
        }
        h ^= h >>> 7;
        int sv = legend ? 0 : Math.floorMod(h, VARIANTS), fv = legend ? 0 : Math.floorMod(h >>> 5, VARIANTS);
        if (best < 0) return new CharacterDestiny(-1, 0, sv, fv, false);
        return new CharacterDestiny(best, traits[best] > CharacterOptions.TRAIT_MID ? 1 : 0, sv, fv, legend);
    }

    public boolean balanced() {
        return axis < 0;
    }

    /** Alma lendária: todos os traços no máximo. */
    public boolean legendary() {
        return legendary;
    }

    /** Parte da chave de tradução (depois de {@code character.lasthorizon.}) com o título da alma. */
    public String key() {
        return legendary ? "soul.legend" : axis < 0 ? "soul.balanced" : "soul." + axis + "." + side;
    }

    /** Chave do texto de força (a variante 0 é a chave original, sem sufixo). */
    public String strengthKey() {
        return key() + ".strength" + (strengthVariant == 0 ? "" : "." + strengthVariant);
    }

    /** Chave do texto de contradição. */
    public String flawKey() {
        return key() + ".flaw" + (flawVariant == 0 ? "" : "." + flawVariant);
    }
}
