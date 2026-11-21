package br.com.lasthorizon.akuma.client;

/**
 * Animação dos ícones de texto das conquistas (fruta, poder, caveira, leme e bússola).
 *
 * <p>A fonte do Minecraft não anima texturas, então o {@code icons.png} guarda todos os frames:
 * a linha N do PNG é o frame N, e cada linha tem os 5 ícones em sequência. Os caracteres seguem a mesma ordem
 * ({@code -} = frame 0, {@code -} = frame 1, ...). Os JSONs das conquistas continuam
 * usando só {@code -}; {@code FontIconAnimMixin} troca pelo frame certo na hora de desenhar.
 *
 * <p>Se mudar {@link #FRAMES}, mude também o número de linhas do PNG e a lista {@code chars} de {@code font/icons.json}
 * (o script {@code tools/build_advancement_icons.py} gera o PNG).
 */
public final class IconAnim {
    private IconAnim() {}

    /** Primeiro caractere (frame 0, ícone 0) e quantos ícones existem por frame. */
    public static final int BASE = 0xE000;
    public static final int ICONS = 5;
    /** Quantos frames o PNG tem (6, como os ícones de efeito). */
    public static final int FRAMES = 6;
    /** Duração de cada frame em ms (os ícones de efeito usam 3-4 ticks = 150-200 ms). */
    public static final long FRAME_MS = 150L;
    /** Defasagem entre ícones (em frames), para eles não pulsarem todos juntos. Use 0 para sincronizar. */
    public static final int STAGGER = 1;

    /** Devolve o caractere do frame atual para {@code codePoint}; qualquer outro caractere passa direto. */
    public static int map(int codePoint) {
        int icon = codePoint - BASE;
        if (icon < 0 || icon >= ICONS) return codePoint;
        long step = System.currentTimeMillis() / FRAME_MS;
        int frame = (int) ((step + (long) icon * STAGGER) % FRAMES);
        return BASE + frame * ICONS + icon;
    }
}
