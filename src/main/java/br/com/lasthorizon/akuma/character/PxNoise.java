package br.com.lasthorizon.akuma.character;

/**
 * Ruído de valor determinístico (só depende das coordenadas), para manchas de pele, mechas de cabelo e dobras de
 * tecido. Nada aqui é aleatório: a mesma skin sai sempre igual, em qualquer computador. Java puro.
 */
final class PxNoise {
    private PxNoise() {}

    private static int hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 1442695041 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /** Valor 0..1 fixo para o ponto inteiro (x, y, z). */
    static double rnd(int x, int y, int z, int seed) {
        return (hash(x, y, z, seed) & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Ruído de valor suave em 3D, 0..1 (concentrado perto de 0,5). */
    static double v3(double x, double y, double z, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = smooth(x - xi), fy = smooth(y - yi), fz = smooth(z - zi);
        double c00 = lerp(rnd(xi, yi, zi, seed), rnd(xi + 1, yi, zi, seed), fx);
        double c10 = lerp(rnd(xi, yi + 1, zi, seed), rnd(xi + 1, yi + 1, zi, seed), fx);
        double c01 = lerp(rnd(xi, yi, zi + 1, seed), rnd(xi + 1, yi, zi + 1, seed), fx);
        double c11 = lerp(rnd(xi, yi + 1, zi + 1, seed), rnd(xi + 1, yi + 1, zi + 1, seed), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    /**
     * Duas oitavas, reescalado para ocupar melhor a faixa 0..1 (0,5 = meio). Boa para manchas: use limiares
     * como 0,62 (mancha) e 0,74 (mancha forte).
     */
    static double blotch(double x, double y, double z, int seed) {
        double n = 0.62 * v3(x, y, z, seed) + 0.38 * v3(x * 2.3 + 5.1, y * 2.3 + 1.7, z * 2.3 + 9.3, seed + 7);
        return Math.max(0, Math.min(1, 0.5 + (n - 0.5) * 1.9));
    }
}
