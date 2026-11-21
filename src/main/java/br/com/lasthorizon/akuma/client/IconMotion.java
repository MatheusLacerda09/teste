package br.com.lasthorizon.akuma.client;

/**
 * Movimento dos ícones das abas e do dado da criação de personagem. Só contas (sem Minecraft): cada função devolve
 * um {@link Pose} com deslocamento em pixels inteiros e escala de achatar/esticar. Nada de girar o ícone em ângulos
 * quebrados: pixel art rotacionada borra, então o movimento é pulo, amassar ao pousar e virar como uma moeda.
 */
public final class IconMotion {
    private IconMotion() {}

    public record Pose(int dx, int dy, double sx, double sy) {}

    public static final Pose REST = new Pose(0, 0, 1.0D, 1.0D);
    public static final long HOP_MS = 560L, FLIP_MS = 640L, ROLL_MS = 780L;

    private static double easeOut(double u) {
        return 1.0D - Math.pow(1.0D - u, 3);
    }

    /** Pulinho ao abrir a aba: sobe esticando, desce e amassa ao pousar (a escala é ancorada nos pés do ícone). */
    public static Pose hop(long since) {
        if (since < 0L || since >= HOP_MS) return REST;
        double p = (double) since / HOP_MS;
        if (p < 0.30D) {
            double e = Math.sin(p / 0.30D * Math.PI / 2.0D);
            return new Pose(0, -(int) Math.round(5.0D * e), 1.0D - 0.08D * e, 1.0D + 0.16D * e);
        }
        if (p < 0.62D) {
            double u = (p - 0.30D) / 0.32D;
            return new Pose(0, -(int) Math.round(5.0D * Math.cos(u * Math.PI / 2.0D)), 0.92D + 0.08D * u, 1.16D - 0.16D * u);
        }
        double u = (p - 0.62D) / 0.38D;
        double k = Math.sin(u * Math.PI) * (1.0D - u);
        return new Pose(0, 0, 1.0D + 0.20D * k, 1.0D - 0.26D * k);
    }

    /** Aba concluída: o ícone vira como uma moeda (uma volta) enquanto dá um pulo. */
    public static Pose flip(long since) {
        if (since < 0L || since >= FLIP_MS) return REST;
        double p = (double) since / FLIP_MS;
        double w = Math.abs(Math.cos(2.0D * Math.PI * easeOut(p)));
        return new Pose(0, -(int) Math.round(4.0D * Math.sin(Math.PI * p)), w, 1.0D);
    }

    /** Dado rolado: duas voltas que vão freando e dois quiques que vão diminuindo. */
    public static Pose roll(long since) {
        if (since < 0L || since >= ROLL_MS) return REST;
        double p = (double) since / ROLL_MS;
        double w = Math.abs(Math.cos(4.0D * Math.PI * easeOut(p)));
        return new Pose(0, -(int) Math.round(6.0D * (1.0D - p) * Math.abs(Math.sin(2.0D * Math.PI * p))), w, 1.0D);
    }

    /** Aba aberta: o ícone flutua devagar (1 px para cima e para baixo, em degraus). */
    public static Pose idle(long t, int seed) {
        return Math.sin(t / 420.0D + seed * 1.7D) > 0.2D ? new Pose(0, -1, 1.0D, 1.0D) : REST;
    }

    /** Mouse em cima de uma aba fechada: o ícone fica saltitando. */
    public static Pose excited(long t, int seed) {
        double q = ((t + seed * 130L) % 700L) / 700.0D;
        double arc = 4.0D * q * (1.0D - q);
        return new Pose(0, -(int) Math.round(2.2D * arc), 1.0D - 0.04D * arc, 1.0D + 0.06D * arc);
    }

    /** Mouse em cima do dado: ele chacoalha em rajadas curtas. */
    public static Pose rattle(long t) {
        if (t % 900L >= 480L) return REST;
        return new Pose((t / 70L) % 2L == 0L ? -1 : 1, 0, 1.0D, 1.0D);
    }
}
