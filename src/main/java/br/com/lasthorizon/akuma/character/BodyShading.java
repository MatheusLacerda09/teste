package br.com.lasthorizon.akuma.character;

import br.com.lasthorizon.akuma.character.SkinPainter.Face;
import br.com.lasthorizon.akuma.character.SkinPainter.Part;

/**
 * Pintura do corpo (pele) no estilo das skins "de autor" do Minecraft: volume e músculos desenhados à mão pixel a pixel,
 * sombras que puxam para o rosado (nunca cinza), luz creme nas partes altas e manchas rosadas irregulares (ruído
 * determinístico em 3D, então a mancha continua certinha na dobra entre uma face e outra) mais fortes nos cotovelos,
 * joelhos e ombros. Cada face das partes do corpo tem um mapa em texto, um caractere por pixel, que diz quanto aquele
 * pixel fica mais claro ou mais escuro que a cor de pele escolhida. A cor da paleta continua sendo a cor "base" (nível 0).
 *
 * <pre>
 *   H +2 (luz forte)   h +1 (luz)   . 0 (cor da paleta)   s -1 (sombra)   S -2 (sombra forte)   X -3 (sombra funda)
 * </pre>
 *
 * Nas faces laterais o mapa é desenhado com x = 0 na borda que encosta na FRENTE da parte (a face RIGHT é
 * espelhada ao pintar). A luz vem de cima e da esquerda de quem olha. Java puro (sem Minecraft).
 */
final class BodyShading {
    private BodyShading() {}

    // ------------------------------------------------------------------------------------------ cor

    /** A cor de pele {@code skin} (RGB) clareada (nível &gt; 0) ou escurecida (nível &lt; 0). Sombras puxam para o quente. */
    static int tone(int skin, int level) {
        return tone(skin, level, 0);
    }

    /** Igual, com {@code warm} (0 a 1): quanto a pele fica rosada/avermelhada ali (sombras, cotovelos, joelhos). */
    static int tone(int skin, int level, double warm) {
        int r = (skin >> 16) & 0xFF, g = (skin >> 8) & 0xFF, b = skin & 0xFF;
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        double dark = 1.0 - lum;                                  // pele escura precisa de passos maiores para o volume aparecer
        double rr = r, gg = g, bb = b;
        if (level > 0) {
            // pele clara já está perto do teto do vermelho: multiplicar só deixaria amarela. Por isso o ganho multiplicativo
            // cresce com a escuridão da pele e o resto da luz vai rumo a um pêssego claro (mais azul que verde).
            double kd = level * (0.075 * dark + 0.012);
            rr *= 1 + kd; gg *= 1 + kd * 0.90; bb *= 1 + kd * 0.88;
            double k = level * 0.050 * lum;
            rr += (255 - rr) * k; gg += (234 - gg) * k; bb += (230 - bb) * k;
            double lift = level * dark * dark;                       // pele escura: o ganho multiplicativo é pequeno em valor absoluto, então sobe um pouco no aditivo
            rr += 11 * lift; gg += 8 * lift; bb += 6 * lift;
        } else if (level < 0) {
            int n = -level;
            double f = 1.0 - n * (0.085 + 0.045 * dark);           // sombra: escurece e perde azul/verde (fica quente, não cinza)
            rr *= f * (1.0 + 0.012 * n); gg *= f * (1.0 - 0.030 * n); bb *= f * (1.0 - 0.060 * n);
        }
        if (warm > 0) {                                           // rosado: puxa para um vermelho de pele
            double k = Math.min(1.0, warm) * (0.14 + 0.08 * lum);
            rr += (205 - rr) * k; gg += (84 - gg) * k; bb += (74 - bb) * k;
        }
        return 0xFF000000 | (clamp((int) Math.round(rr)) << 16) | (clamp((int) Math.round(gg)) << 8) | clamp((int) Math.round(bb));
    }

    /** Mancha rosada de força {@code k} (0 a 1) sobre uma cor de pele já sombreada: tira verde e um pouco de azul, mantém o vermelho. */
    static int rosy(int c, double k) {
        if (k <= 0) return c;
        double lum = (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0;
        double r = ((c >> 16) & 255) * (1 - 0.045 * k);
        double g = ((c >> 8) & 255) * (1 - (0.050 + 0.050 * lum) * k);
        double b = (c & 255) * (1 - (0.045 + 0.030 * lum) * k);
        return 0xFF000000 | (clamp((int) Math.round(r)) << 16) | (clamp((int) Math.round(g)) << 8) | clamp((int) Math.round(b));
    }

    /** Mancha clara (brilho creme) de força {@code k} (0 a 1). */
    static int pale(int c, double k) {
        if (k <= 0) return c;
        double lum = (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0;
        double t = k * (0.045 + 0.035 * lum);
        double r = ((c >> 16) & 255), g = ((c >> 8) & 255), b = (c & 255);
        r += (255 - r) * t; g += (222 - g) * t; b += (214 - b) * t;
        return 0xFF000000 | (clamp((int) Math.round(r)) << 16) | (clamp((int) Math.round(g)) << 8) | clamp((int) Math.round(b));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int level(char c) {
        return switch (c) {
            case 'H' -> 2;
            case 'h' -> 1;
            case 's' -> -1;
            case 'S' -> -2;
            case 'X' -> -3;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------------------------------ tronco

    /**
     * Torso masculino, frente (x = 0 é o lado direito do personagem): clavículas, dois peitorais separados pelo
     * esterno, linha sob o peito, abdômen em 2 colunas x 3 fileiras e cintura.
     */
    private static final String[] M_BODY_FRONT = {
            "hhsXXshh",
            ".hHssHh.",
            "shhsshhs",
            "shHssHhs",
            "sSSSSSSs",
            "shhsshhs",
            "ssSSSSss",
            "shhsshhs",
            "ssSSSSss",
            "shhsshhs",
            "sssSSsss",
            "ssssssss"};
    private static final String[] M_BODY_BACK = {
            "hhhSShhh",
            ".hHssHh.",
            "shhsshhs",
            "shHssHhs",
            "ssSsshss",
            "sshSShss",
            "sshSShss",
            "shhSShhs",
            "shhSShhs",
            "ssh..hss",
            "sssSSsss",
            "ssssssss"};
    private static final String[] F_BODY_FRONT = {
            "hhsXXshh",
            ".hhssHh.",
            "shHssHhs",
            ".hhssh h",
            ".ssSSss.",
            "s..hh..s",
            "ss.hh.ss",
            "ss.ss.ss",
            "ss.ss.ss",
            "s.hssh.s",
            "ssssssss",
            "ssssssss"};
    private static final String[] F_BODY_BACK = {
            "hhhSShhh",
            ".h.ss.h.",
            "s.hssh.s",
            "s..ss..s",
            "...ss...",
            "s..ss..s",
            "ss.ss.ss",
            "ss.ss.ss",
            "s..ss..s",
            "s..ss..s",
            "ssssssss",
            "ssssssss"};
    /** Lado do torso, igual nos dois gêneros (x = 0 é a borda da frente). */
    private static final String[] BODY_SIDE = {
            "hhss", "h.ss", "h.ss", "h.ss", "h.ss", "..ss", "..ss", "..ss", "..ss", "..ss", "sssS", "sssS"};
    private static final String[] BODY_TOP = {"hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhhhh"};
    private static final String[] BODY_BOTTOM = {"ssssssss", "ssssssss", "ssssssss", "ssssssss"};
    private static final String[] TOP4 = {"hhhh", "hhhh", "hhhh", "hhhh"};
    private static final String[] BOTTOM4 = {"ssss", "ssss", "ssss", "ssss"};
    private static final String[] TOP3 = {"hhh", "hhh", "hhh", "hhh"};
    private static final String[] BOTTOM3 = {"sss", "sss", "sss", "sss"};

    // ------------------------------------------------------------------------------------------ braços

    private static final String[] M_ARM_FRONT = {
            "hhhs",
            "hHhs",
            ".hhs",
            ".hhs",
            ".Hhs",
            ".hhs",
            "sssS",
            ".h.s",
            ".hhs",
            ".h.s",
            ".h.s",
            "...s"};
    private static final String[] M_ARM_BACK = {
            "hhhs",
            "hHhs",
            ".hhs",
            ".Hhs",
            ".hss",
            ".hss",
            "sssS",
            ".h.s",
            ".hhs",
            ".h.s",
            ".h.s",
            "...s"};
    private static final String[] M_ARM_OUTER = {
            "hh.s", "hh.s", "hh.s", "h..s", "h..s", "h..s", "h..s", "h..s", "h..s", "...s", "...s", "...s"};
    private static final String[] M_ARM_INNER = {
            "ssss", "ssss", "s..s", "...s", "...s", "...s", "...s", "...s", "...s", "...s", "...s", "...s"};

    private static final String[] F_ARM_FRONT = {
            "hhs",
            "hhs",
            ".hs",
            ".hs",
            ".hs",
            ".hs",
            "ssS",
            ".hs",
            ".hs",
            ".hs",
            ".hs",
            "..s"};
    private static final String[] F_ARM_BACK = {
            "hhs",
            "hhs",
            ".hs",
            ".hs",
            ".hs",
            ".hs",
            "ssS",
            ".hs",
            ".hs",
            ".hs",
            ".hs",
            "..s"};
    private static final String[] F_ARM_OUTER = {
            "hh.s", "hh.s", "h..s", "h..s", "h..s", "h..s", "h..s", "h..s", "h..s", "...s", "...s", "...s"};
    private static final String[] F_ARM_INNER = {
            "ssss", "ssss", "s..s", "...s", "...s", "...s", "...s", "...s", "...s", "...s", "...s", "...s"};

    // ------------------------------------------------------------------------------------------ pernas

    private static final String[] M_LEG_FRONT = {
            "...s",
            ".hhs",
            ".Hhs",
            ".hhs",
            ".hhs",
            ".h.s",
            ".Hhs",
            "ssSs",
            ".hhs",
            ".h.s",
            ".h.s",
            "...s"};
    private static final String[] M_LEG_BACK = {
            "...s",
            "...s",
            ".h.s",
            ".hhs",
            ".Hhs",
            ".hhs",
            ".h.s",
            "ssSs",
            ".Hhs",
            ".hhs",
            ".h.s",
            "...s"};
    private static final String[] M_LEG_OUTER = {
            "h..s", "h..s", "h..s", "h..s", "h..s", "h..s", "h..s", "h..s", "h..s", "...s", "...s", "...s"};
    private static final String[] LEG_INNER = {
            "ssss", "s..s", "s..s", "s..s", "s..s", "s..s", "s..s", "s..s", "s..s", "s..s", "s..s", "ssss"};

    private static final String[] F_LEG_FRONT = {
            "...s",
            ".hhs",
            ".hhs",
            ".hhs",
            ".h.s",
            ".h.s",
            ".Hhs",
            "ssSs",
            ".hhs",
            ".h.s",
            ".h.s",
            "...s"};
    private static final String[] F_LEG_BACK = {
            "...s",
            "...s",
            ".h.s",
            ".hhs",
            ".hhs",
            ".h.s",
            ".h.s",
            "ssSs",
            ".hhs",
            ".hhs",
            ".h.s",
            "...s"};
    private static final String[] F_LEG_OUTER = M_LEG_OUTER;

    // ------------------------------------------------------------------------------------------ cabeça

    private static final String[] HEAD_FRONT = {
            "s.hhhh.s",
            ".hhhhhh.",
            "..hhhh..",
            "...hh...",
            "...hh...",
            ".h....h.",
            "s......s",
            "ss....ss"};
    private static final String[] HEAD_SIDE = {
            "hhh.....", "hh......", "h.......", "h......s", "......ss", ".....sss", "....ssss", "...sssss"};
    private static final String[] HEAD_BACK = {
            "........", "........", "........", "........", "........", "s......s", "s......s", "ss....ss"};
    private static final String[] HEAD_TOP = {
            "........", ".hhhhhh.", ".hhhhhh.", ".hhhhhh.", ".hhhhhh.", ".hhhhhh.", ".hhhhhh.", "........"};
    private static final String[] HEAD_BOTTOM = {
            "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ssssssss"};


    // ------------------------------------------------------------------------------------------ aplicação

    /** Pinta o corpo todo (cabeça, tronco, braços, pernas) com o sombreado, a partir da cor de pele {@code skin}. */
    static void apply(SkinPainter p, boolean female, int skin) {
        int c = skin & 0xFFFFFF;
        // cabeça
        p.stamp(SkinPainter.HEAD, Face.FRONT, HEAD_FRONT, c, false);
        p.stamp(SkinPainter.HEAD, Face.RIGHT, HEAD_SIDE, c, true);
        p.stamp(SkinPainter.HEAD, Face.LEFT, HEAD_SIDE, c, false);
        p.stamp(SkinPainter.HEAD, Face.BACK, HEAD_BACK, c, false);
        p.stamp(SkinPainter.HEAD, Face.TOP, HEAD_TOP, c, false);
        p.stamp(SkinPainter.HEAD, Face.BOTTOM, HEAD_BOTTOM, c, false);
        // tronco
        Part body = SkinPainter.BODY;
        int aw = female ? 3 : 4;
        p.stamp(body, Face.FRONT, female ? F_BODY_FRONT : M_BODY_FRONT, c, false);
        p.stamp(body, Face.BACK, female ? F_BODY_BACK : M_BODY_BACK, c, false);
        p.stamp(body, Face.RIGHT, BODY_SIDE, c, true);
        p.stamp(body, Face.LEFT, BODY_SIDE, c, false);
        p.stamp(body, Face.TOP, BODY_TOP, c, false);
        p.stamp(body, Face.BOTTOM, BODY_BOTTOM, c, false);
        // braços: o direito tem o lado de fora na face RIGHT; o esquerdo, na LEFT
        String[] af = female ? F_ARM_FRONT : M_ARM_FRONT, ab = female ? F_ARM_BACK : M_ARM_BACK;
        String[] ao = female ? F_ARM_OUTER : M_ARM_OUTER, ai = female ? F_ARM_INNER : M_ARM_INNER;
        String[] at = female ? TOP3 : TOP4, abt = female ? BOTTOM3 : BOTTOM4;
        Part ra = p.RARM, la = p.LARM;
        p.stamp(ra, Face.FRONT, af, c, false);
        p.stamp(ra, Face.BACK, ab, c, false);
        p.stamp(ra, Face.RIGHT, ao, c, true);
        p.stamp(ra, Face.LEFT, ai, c, false);
        p.stamp(ra, Face.TOP, at, c, false);
        p.stamp(ra, Face.BOTTOM, abt, c, false);
        p.stamp(la, Face.FRONT, af, c, false);
        p.stamp(la, Face.BACK, ab, c, false);
        p.stamp(la, Face.RIGHT, ai, c, true);
        p.stamp(la, Face.LEFT, ao, c, false);
        p.stamp(la, Face.TOP, at, c, false);
        p.stamp(la, Face.BOTTOM, abt, c, false);
        // pernas
        String[] lf = female ? F_LEG_FRONT : M_LEG_FRONT, lb = female ? F_LEG_BACK : M_LEG_BACK;
        String[] lo = female ? F_LEG_OUTER : M_LEG_OUTER;
        for (Part leg : new Part[]{SkinPainter.RLEG, SkinPainter.LLEG}) {
            boolean right = leg == SkinPainter.RLEG;
            p.stamp(leg, Face.FRONT, lf, c, false);
            p.stamp(leg, Face.BACK, lb, c, false);
            p.stamp(leg, Face.RIGHT, right ? lo : LEG_INNER, c, true);
            p.stamp(leg, Face.LEFT, right ? LEG_INNER : lo, c, false);
            p.stamp(leg, Face.TOP, TOP4, c, false);
            p.stamp(leg, Face.BOTTOM, BOTTOM4, c, false);
        }
        finish(p, female, c);
    }

    // ------------------------------------------------------------------------------------------ manchas e detalhes

    private static final int SEED = 11;

    /** Quanto o ponto {@code q} (ver {@link SkinPainter#pos}) é "articulação": cotovelo, joelho, ombro, mão, pé. Rosa a mais nesses pontos. */
    private static double joint(double[] q, int aw) {
        double x = q[0], y = q[1], j = 0;
        boolean arm = y >= 0 && y < 12 && (x < 0 || x >= 8);
        if (arm) {
            j += Math.exp(-Math.pow((y - 6.8) / 0.9, 2));                  // cotovelo
            j += 0.8 * Math.exp(-Math.pow((y - 0.2) / 1.0, 2));            // ombro
            j += 0.9 * Math.max(0, (y - 10.0) / 2.0);                      // mão
        } else if (y >= 12) {
            j += Math.exp(-Math.pow((y - 18.6) / 1.0, 2));                 // joelho
            j += 0.8 * Math.max(0, (y - 22.0) / 2.0);                      // tornozelo e pé
        }
        return j;
    }

    /**
     * Pele masculina no estilo da skin de referência: em vez de manchas "de doença", a pele varia em quatro tons suaves
     * (claro, médio, rosado, rosado fundo) em aglomerados de 2 a 4 pixels, com as mesmas proporções e razões de cor medidas
     * na skin de referência (31% / 39% / 24% / 6%; o rosado fundo tira mais verde e azul). Cotovelos, joelhos e ombros
     * puxam um tom a mais.
     */
    private static int maleTone(int c, SkinPainter p, double[] q, Face f, boolean arm, int aw) {
        double v = 0.68 * PxNoise.blotch(q[0] / 2.0, q[1] / 2.0, q[2] / 2.0, SEED + 5)
                 + 0.32 * PxNoise.blotch(q[0] / 1.1 + 4, q[1] / 1.1, q[2] / 1.1, SEED + 9)
                 + joint(q, aw) * 0.10;
        int t = v < Q[0] ? 0 : v < Q[1] ? 1 : v < Q[2] ? 2 : 3;
        if (f == Face.BOTTOM) t = Math.min(t, 1);
        return mul(c, TONE[t][0], TONE[t][1], TONE[t][2]);
    }

    private static final double[][] TONE = {{1.0, 1.0, 1.0}, {0.95, 0.92, 0.91}, {0.91, 0.83, 0.83}, {0.83, 0.72, 0.72}};
    /** Limiares do ruído para 31% / 70% / 94% da superfície (calculados uma vez, sempre iguais). */
    private static final double[] Q = quantiles();

    private static double[] quantiles() {
        double[] vals = new double[4096];
        int n = 0;
        for (int z = 0; z < 4; z++) for (int y = 0; y < 24; y++) for (int x = -4; x < 12; x++) {
            if (n >= vals.length) break;
            double X = x + 0.5, Y = y + 0.5, Z = z + 0.5;
            vals[n++] = 0.68 * PxNoise.blotch(X / 2.0, Y / 2.0, Z / 2.0, SEED + 5) + 0.32 * PxNoise.blotch(X / 1.1 + 4, Y / 1.1, Z / 1.1, SEED + 9);
        }
        double[] v = java.util.Arrays.copyOf(vals, n);
        java.util.Arrays.sort(v);
        return new double[]{v[(int) (n * 0.31)], v[(int) (n * 0.70)], v[(int) (n * 0.94)]};
    }

    private static int mul(int c, double r, double g, double b) {
        return 0xFF000000 | (clamp((int) Math.round(((c >> 16) & 255) * r)) << 16) | (clamp((int) Math.round(((c >> 8) & 255) * g)) << 8) | clamp((int) Math.round((c & 255) * b));
    }

    /** Manchas rosadas e claras por cima do sombreado, orelhas e bochechas. */
    private static void finish(SkinPainter p, boolean female, int skin) {
        int aw = female ? 3 : 4;
        Part[] parts = {SkinPainter.HEAD, SkinPainter.BODY, p.RARM, p.LARM, SkinPainter.RLEG, SkinPainter.LLEG};
        for (Part part : parts) {
            boolean head = part == SkinPainter.HEAD;
            for (Face f : Face.values()) {
                int[] r = part.rect(f);
                for (int j = 0; j < r[3]; j++) for (int i = 0; i < r[2]; i++) {
                    double[] q = p.pos(part, f, i, j);
                    int c = p.getPx(r[0] + i, r[1] + j);
                    double n = PxNoise.blotch(q[0] / 2.8, q[1] / 2.8, q[2] / 2.8, SEED);
                    double a = (n - 0.60) * 5.0 + joint(q, aw) * (head ? 0 : 0.7);
                    double k = a > 1.0 ? 1.0 : a > 0.35 ? 0.5 : 0.0;
                    if (head) k *= 0.55;                                                   // rosto mais limpo
                    if (f == Face.BOTTOM) k *= 0.5;
                    if (!female && !head) {
                        c = maleTone(c, p, q, f, part == p.LARM || part == p.RARM, aw);
                    } else {
                        c = rosy(c, k);
                        if (k == 0 && n < 0.36) c = pale(c, n < 0.28 ? 1.0 : 0.55);
                    }
                    p.setPx(r[0] + i, r[1] + j, c);
                }
            }
        }
        // cabeça: bochechas rosadas, orelhas e sombra no pescoço
        for (int x : new int[]{1, 6}) { int[] r = SkinPainter.HEAD.rect(Face.FRONT); int c = p.getPx(r[0] + x, r[1] + 5); p.setPx(r[0] + x, r[1] + 5, rosy(c, 0.55)); }
        int[] rr = SkinPainter.HEAD.rect(Face.RIGHT), ll = SkinPainter.HEAD.rect(Face.LEFT);
        for (int y = 4; y <= 5; y++) {
            p.setPx(rr[0] + 4, rr[1] + y, tone(skin, y == 4 ? -1 : -2, 0.7));
            p.setPx(ll[0] + 3, ll[1] + y, tone(skin, y == 4 ? -1 : -2, 0.7));
        }
    }

    /** Cor do pixel do mapa {@code map} em (x, y). */
    static int colorAt(String[] map, int x, int y, int skin) {
        return tone(skin, level(map[y].charAt(x)));
    }
}
