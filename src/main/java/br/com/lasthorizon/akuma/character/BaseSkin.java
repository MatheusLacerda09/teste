package br.com.lasthorizon.akuma.character;

import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Corpos base desenhados à mão e o recolorido deles para as paletas de {@link CharacterOptions}:
 * <ul>
 *   <li>masculino: {@code assets/lasthorizon/textures/skin/base_male.png} (skin 64 x 64, braços de 4 px);</li>
 *   <li>feminino: {@code assets/lasthorizon/textures/skin/base_female.png} (skin 64 x 64, braços FINOS de 3 px, o modelo "slim").
 *       Os peitos não estão na skin: são uma camada 3D ({@code BreastLayer}) que lê o peito desta mesma skin.</li>
 * </ul>
 *
 * <p>O desenho nunca é alterado, só a cor de cada pixel:
 * <ul>
 *   <li><b>Pele</b> (pixels quentes da skin): cada pixel guarda a razão por canal entre ele e o tom principal da skin
 *       ({@link #SKIN_REF}); o tom escolhido na paleta é multiplicado por essa razão. Assim o tom principal vira exatamente a
 *       cor da paleta e os músculos, sombras e marcas continuam com o mesmo contraste relativo.</li>
 *   <li><b>Cueca</b> (pixels neutros, branco e cinza): o brilho de cada pixel em relação ao tom {@link #CLOTH_REF} multiplica a
 *       cor da roupa escolhida, mantendo dobras, sombras e a barra.</li>
 * </ul>
 * Para trocar o corpo, basta substituir o PNG por outro com as mesmas regiões (pele em tons quentes, cueca em tons de cinza).
 * Java puro (usa só a biblioteca padrão): roda fora do jogo.
 */
final class BaseSkin {
    private BaseSkin() {}

    static final String RESOURCE = "/assets/lasthorizon/textures/skin/base_male.png";
    static final String RESOURCE_FEMALE = "/assets/lasthorizon/textures/skin/base_female.png";

    /** Tom principal da pele na skin desenhada (o mais usado) e tom "cheio" do tecido (cinza claro das dobras). */
    private static final int SKIN_REF_R = 208, SKIN_REF_G = 170, SKIN_REF_B = 151;
    private static final double CLOTH_REF = 239.0;
    /** Diferença máxima entre os canais para o pixel contar como neutro (cueca); a pele tem mais de 40. */
    private static final int NEUTRAL_SPREAD = 20;

    private static int[] male, female;
    private static boolean tried, triedFemale;

    /** O corpo masculino como ARGB 64 x 64, ou {@code null} se o PNG não pôde ser lido (aí o corpo é pintado por código). */
    private static synchronized int[] male() {
        if (!tried) {
            tried = true;
            male = load(RESOURCE);
        }
        return male;
    }

    /** O corpo feminino (braços finos) como ARGB 64 x 64, ou {@code null} se o PNG não pôde ser lido. */
    private static synchronized int[] female() {
        if (!triedFemale) {
            triedFemale = true;
            female = load(RESOURCE_FEMALE);
        }
        return female;
    }

    /**
     * O corpo base desenhado tem braços finos (3 px, modelo "slim") ou grossos (4 px, "default")? Decidido pelo próprio PNG,
     * não pelo gênero: a face de trás do braço direito chega até x = 55 só no braço de 4 px (no de 3 px ela termina em x = 53).
     * Assim a skin e o modelo 3D do jogo sempre concordam, qualquer que seja o formato que o PNG tenha. Sem PNG, vale o gênero.
     */
    static boolean slim(boolean female) {
        int[] a = female ? female() : male();
        if (a == null) return female;
        for (int y = 20; y < 32; y++) {
            if ((a[y * SkinPainter.SIZE + 55] >>> 24) != 0) return false;
        }
        return true;
    }

    private static int[] load(String resource) {
        try (InputStream in = BaseSkin.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("recurso ausente: " + resource);
            BufferedImage img = javax.imageio.ImageIO.read(in);
            if (img == null || img.getWidth() != SkinPainter.SIZE || img.getHeight() != SkinPainter.SIZE)
                throw new IllegalStateException("a skin base precisa ter 64 x 64 pixels");
            int[] px = new int[SkinPainter.SIZE * SkinPainter.SIZE];
            img.getRGB(0, 0, SkinPainter.SIZE, SkinPainter.SIZE, px, 0, SkinPainter.SIZE);
            return px;
        } catch (Exception e) {
            System.getLogger("lasthorizon").log(System.Logger.Level.WARNING,
                    "Skin base não carregada (" + resource + "); o corpo será pintado por código.", e);
            return null;
        }
    }

    /**
     * Corpo masculino com a pele {@code skinRgb} e a cueca {@code clothRgb} (RGB sem alfa, valores de {@link CharacterOptions}).
     * Devolve uma cópia nova (ARGB), ou {@code null} se a skin base não está disponível.
     */
    static int[] male(int skinRgb, int clothRgb) {
        return recolor(male(), skinRgb, clothRgb);
    }

    /** Corpo feminino (braços finos de 3 px) com a pele {@code skinRgb} e a roupa de baixo {@code clothRgb}; mesma regra do masculino. */
    static int[] female(int skinRgb, int clothRgb) {
        return recolor(female(), skinRgb, clothRgb);
    }

    /**
     * Pixels da skin base que são ROUPA DE BAIXO (os neutros: branco e cinza), 64 x 64 (índice y * 64 + x). As cicatrizes do corpo
     * usam isto para nunca pintar em cima da cueca ou do top, que já vêm desenhados na skin base. Tudo falso se o PNG não existe.
     */
    static boolean[] underwear(boolean female) {
        final int[] src = female ? female() : male();
        final boolean[] out = new boolean[SkinPainter.SIZE * SkinPainter.SIZE];
        if (src == null) return out;
        for (int i = 0; i < src.length; i++) {
            final int c = src[i];
            if ((c >>> 24) == 0) continue;
            final int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            out[i] = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= NEUTRAL_SPREAD;
        }
        return out;
    }

    private static int[] recolor(int[] src, int skinRgb, int clothRgb) {
        if (src == null) return null;
        int[] out = new int[src.length];
        for (int i = 0; i < src.length; i++) {
            int c = src[i], a = c >>> 24;
            if (a == 0) continue;                                                    // fora das partes do corpo: continua vazio
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            double[] n;
            if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= NEUTRAL_SPREAD) {
                double k = (r + g + b) / 3.0 / CLOTH_REF;                            // cueca: brilho relativo
                n = fit(((clothRgb >> 16) & 255) * k, ((clothRgb >> 8) & 255) * k, (clothRgb & 255) * k);
            } else {                                                                 // pele: razão por canal em relação ao tom principal
                n = new double[]{((skinRgb >> 16) & 255) * r / (double) SKIN_REF_R,
                        ((skinRgb >> 8) & 255) * g / (double) SKIN_REF_G,
                        (skinRgb & 255) * b / (double) SKIN_REF_B};
            }
            out[i] = (a << 24) | (cl(n[0]) << 16) | (cl(n[1]) << 8) | cl(n[2]);
        }
        return out;
    }

    /** Se algum canal passa de 255, reduz os três juntos para não mudar o matiz. */
    private static double[] fit(double r, double g, double b) {
        double m = Math.max(r, Math.max(g, b));
        double s = m > 255 ? 255 / m : 1;
        return new double[]{r * s, g * s, b * s};
    }

    private static int cl(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v)));
    }
}
