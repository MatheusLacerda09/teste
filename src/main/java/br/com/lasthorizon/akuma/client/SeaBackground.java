package br.com.lasthorizon.akuma.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/**
 * Fundo animado da aba de conquistas do Sea Sovereign (v2, 382 x 203 px, 1 pixel de arte = 1 px de GUI).
 *
 * <p>UM cenário só (mar + céu + nuvens de pixel art 16-bit) que vai mudando de cor aos poucos, sem trocar de
 * imagem nem fazer fade entre cenas: dia, céu azul, turquesa, tempestade, pôr do sol, sol grande, crepúsculo,
 * noite violeta, lua cheia, via láctea, amanhecer e de novo o dia (paletas em {@code SeaMoods}, gerada por
 * {@code tools/build_backgrounds.py}).
 *
 * <p>Os PNGs são máscaras brancas que aqui são pintadas com a cor do momento:
 * <ul>
 *   <li>céu em faixas duras com dither entre elas, via láctea, estrelas (poeira + brilhantes que cintilam),
 *       estrela cadente;</li>
 *   <li>sol (com miolo e aneis de brilho) e lua (com crateras, sombra e contorno) que andam pelo céu;</li>
 *   <li>3 camadas de nuvens (finas, médias e bancos no horizonte) com 7 tons cada, arrastadas devagar pelo
 *       vento em velocidades diferentes (parallax). As nuvens passam na frente do sol e da lua;</li>
 *   <li>mar em 32 quadros em loop (5 tons, cristas, sombras, traços, espuma), reflexo ondulado das nuvens e
 *       caminho de brilho do sol/lua que segue o astro;</li>
 *   <li>de vez em quando, um bando de pássaros.</li>
 * </ul>
 *
 * <p>IMPORTANTE (blend): no Minecraft 1.20.1 todo {@code GuiGraphics.fill()} desliga o blend do OpenGL ao
 * terminar. Um blit com transparência feito depois disso sai OPACO (os raios viravam uma mancha chapada, as
 * estrelas e a lua apareciam de repente). Por isso {@link #tint} religa o blend antes de TODO blit.
 *
 * <p>Ajustes rápidos: {@link #FRAME_MS}, {@link #HOLD_MS}, {@link #MORPH_MS}, {@link #FADE_MS},
 * {@link #FADE_OUT_MS}, {@link #WIND_FAR}, {@link #WIND_MID}, {@link #WIND_NEAR}, {@link #BIRD_SLOT_MS},
 * {@link #BIRD_CHANCE}.
 */
public final class SeaBackground {
    private SeaBackground() {}

    /** Tamanho da arte = área interna da janela larga de conquistas (400 x 230 menos as bordas). */
    public static final int W = 382, H = 203;
    /** Quadros por ciclo de animação do mar (tem que ser igual a F em tools/ocean.py). */
    public static final int FRAMES = 32;
    /** Duração de cada quadro do mar (ms). Ciclo da água = FRAMES * FRAME_MS (~3,8 s). */
    public static final long FRAME_MS = 120L;
    /** Quanto tempo o cenário fica parado numa paleta antes de começar a mudar (ms). */
    public static final long HOLD_MS = 1_500L;
    /** Quanto tempo a mudança gradual para a próxima paleta leva (ms). */
    public static final long MORPH_MS = 20_500L;
    /** Velocidade do vento nas nuvens (pixels por segundo): finas, médias e bancos do horizonte. */
    public static final double WIND_FAR = 2.5D, WIND_MID = 5.0D, WIND_NEAR = 8.0D;
    /** Duração do fade-in (ms) quando a aba aparece: o cenário sai do escuro suavemente (curto, para não atrasar). */
    public static final long FADE_MS = 110L;
    /** Duração do fade-out (ms) ao fechar a tela de conquistas: o cenário escurece antes de sumir. 0 desliga. */
    public static final long FADE_OUT_MS = 110L;
    /** Quão escuro o cenário fica no começo do fade-in / fim do fade-out (0 a 1). Menos que 1 = nunca vira tela preta. */
    public static final double FADE_DARK = 0.55D;
    /** A cada intervalo deste tamanho, sorteia-se se um bando de pássaros vai passar. */
    public static final long BIRD_SLOT_MS = 14_000L;
    /** Chance (0-100) de passar um bando em cada intervalo. */
    public static final int BIRD_CHANCE = 65;
    /** A cada intervalo deste tamanho, sorteia-se se uma estrela cadente cruza o céu (só de noite). */
    public static final long METEOR_SLOT_MS = 9_000L;
    public static final int METEOR_CHANCE = 45;

    /** Horizonte (px) e altura do mar. */
    private static final int HZ = 140, SH = H - HZ;
    /** Largura das tiras de nuvem (repetem sem emenda) e tamanho da folha do mar (4 x 8 quadros de 234 x 35). */
    private static final int STRIP = 764, SEA_SW = W * 4, SEA_SH = SH * 8;
    private static final int BANDS = 20, BAND_H = HZ / BANDS;
    /** Escala dos tamanhos em relação ao desenho-base de 234 x 113 (usada em tamanhos fixos em px). */
    private static final double K = W / 234.0D, KY = HZ / 78.0D;

    private static final String[] SEA_NAMES = {"s0", "s1", "s2", "s3", "s4", "shadow", "crest", "foam"};
    private static final ResourceLocation[] SEA = new ResourceLocation[SEA_NAMES.length];
    /** Tons das nuvens: 0 borda, 1 luz, 2 claro, 3 meio, 4 sombra, 5 núcleo, 6 brilho de baixo (arquivos n1..n7). */
    private static final ResourceLocation[] NEAR = new ResourceLocation[7], MID = new ResourceLocation[7];
    private static final ResourceLocation[] FAR = new ResourceLocation[2];
    private static final ResourceLocation[] REF_NEAR = new ResourceLocation[2], REF_MID = new ResourceLocation[2];
    private static final ResourceLocation[] NEB = new ResourceLocation[3];
    private static final ResourceLocation[] STARS1 = new ResourceLocation[3], STARS2 = new ResourceLocation[3];
    private static final ResourceLocation[] RAYS = {tex("rays0"), tex("rays1")};
    private static final int RAYS_SIZE = 353;
    private static final ResourceLocation PROPS = new ResourceLocation("lasthorizon", "textures/gui/seabg/props.png");

    static {
        for (int i = 0; i < SEA.length; i++) SEA[i] = tex("sea_" + SEA_NAMES[i]);
        for (int i = 0; i < 7; i++) {
            NEAR[i] = tex("n" + (i + 1));
            MID[i] = tex("m" + (i + 1));
        }
        for (int i = 0; i < 2; i++) {
            FAR[i] = tex("f" + i);
        }
        REF_NEAR[0] = tex("rn_l");
        REF_NEAR[1] = tex("rn_d");
        REF_MID[0] = tex("rm_l");
        REF_MID[1] = tex("rm_d");
        for (int i = 0; i < 3; i++) {
            NEB[i] = tex("neb" + i);
            STARS1[i] = tex("stars1_" + i);
            STARS2[i] = tex("stars2_" + i);
        }
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("lasthorizon", "textures/gui/seabg/layer_" + name + ".png");
    }

    // posições (u, v, largura, altura) dentro de props.png (512 x 256)
    private static final int[] P_RING = {0, 0, 37, 37}, P_MOON = {38, 0, 37, 37}, P_CRATER = {76, 0, 37, 37},
            P_MSHADE = {114, 0, 37, 37}, P_SUN = {152, 0, 29, 29}, P_CORE = {182, 0, 19, 19},
            P_GLOW = {0, 40, 193, 193}, P_DITH_A = {0, 240, W, 1}, P_DITH_B = {0, 242, W, 1};

    // fade-in: reinicia sempre que a aba volta a ser desenhada depois de uma pausa (abrir a tela, trocar de aba)
    // (começam em 0, NÃO em Long.MIN_VALUE: "now - MIN_VALUE" estoura o long, o fade-in nunca começava e o cenário ficava escuro)
    private static long fadeStart = 0L, lastFrame = 0L;
    // fade-out: começa quando o jogador fecha a tela (ver ClientSetup) e termina em FADE_OUT_MS
    private static long fadeOutStart = Long.MIN_VALUE;

    /** Começa o fade-out (o cenário escurece em {@link #FADE_OUT_MS}). */
    public static void beginFadeOut() {
        if (fadeOutStart == Long.MIN_VALUE) fadeOutStart = System.currentTimeMillis();
        SeaAmbience.release();   // o som também começa a sair agora
    }

    /** Já está escurecendo para fechar? */
    public static boolean isFadingOut() {
        return fadeOutStart != Long.MIN_VALUE;
    }

    /** O fade-out terminou (pode fechar a tela de verdade)? */
    public static boolean fadeOutDone() {
        return fadeOutStart != Long.MIN_VALUE && System.currentTimeMillis() - fadeOutStart >= FADE_OUT_MS;
    }

    /** Cancela/zera o fade-out. */
    public static void endFadeOut() {
        fadeOutStart = Long.MIN_VALUE;
    }

    /**
     * Para o fundo de vez (chamado quando a tela de conquistas fecha): zera fade-in/fade-out e o relógio do último
     * quadro. A animação só existe enquanto {@link #render} é chamado, então depois disso não sobra nenhum
     * processamento; na próxima abertura ela recomeça do fade-in.
     */
    public static void stop() {
        fadeOutStart = Long.MIN_VALUE;
        fadeStart = 0L;
        lastFrame = 0L;
        soundPrevMs = 0L;
        SeaAmbience.release();
    }

    /** Índice do momento "tempestade" em SeaMoods (céu cinza): é quando o vento e o mar ficam mais fortes. */
    private static final int STORM_MOOD = 3;
    /** 0 a 1: quanto o céu está em tempestade agora (para o som). */
    private static double stormAmount = 0.0D;
    /** Tempo (ms) do quadro anterior, para os cantos dos passarinhos só tocarem em animação contínua (0 = parado). */
    private static long soundPrevMs = 0L;

    // paleta e números do momento atual (interpolados a cada quadro)
    private static final int[] C = new int[SeaMoods.NC];
    /** Paletas já convertidas para OKLab (L, a, b): a mistura de cores no meio da transição fica limpa, sem cinza barrento. */
    private static final double[][][] LAB = new double[SeaMoods.COUNT][SeaMoods.NC][3];

    static {
        for (int k = 0; k < SeaMoods.COUNT; k++) {
            for (int i = 0; i < SeaMoods.NC; i++) toOklab(SeaMoods.COLORS[k][i], LAB[k][i]);
        }
    }
    private static final double[] N = new double[SeaMoods.NN];

    // estrelas que brilham em cruz (px)
    private static final int[] TW_X = new int[16], TW_Y = new int[16];

    static {
        Random r = new Random(77L);
        for (int i = 0; i < TW_X.length; i++) {
            TW_X[i] = 6 + r.nextInt(W - 12);
            TW_Y[i] = 6 + r.nextInt((int) (52 * KY));
        }
    }

    // ------------------------------------------------------------------------------------------

    /** Desenha o cenário na área cheia ({@link #W} x {@link #H}). */
    public static void render(GuiGraphics g, int ox, int oy, long timeMs) {
        render(g, ox, oy, W, H, timeMs);
    }

    /**
     * Desenha o cenário numa área de {@code innerW x innerH} (a área interna da janela de conquistas). Se a
     * janela for menor que a arte (telas pequenas), a arte é cortada: centrada na horizontal e presa embaixo,
     * para o horizonte e o mar sempre aparecerem.
     *
     * @param cx canto esquerdo da área (px de tela)
     * @param cy canto superior da área (px de tela)
     */
    public static void render(GuiGraphics g, int cx, int cy, int innerW, int innerH, long timeMs) {
        int ox = cx + (innerW - W) / 2, oy = cy + innerH - H;
        long now = System.currentTimeMillis();
        // janela minimizada / sem foco (alt-tab): não gasta GPU/CPU com a animação, só um fundo escuro simples
        if (!net.minecraft.client.Minecraft.getInstance().isWindowActive()) {
            lastFrame = 0L;                      // ao voltar, o fade-in recomeça
            g.fill(cx, cy, cx + innerW, cy + innerH, 0xFF05060E);
            return;
        }
        if (now - lastFrame > 250L) {            // a aba ficou um tempo sem ser desenhada: recomeça do escuro
            fadeStart = now;
            fadeOutStart = Long.MIN_VALUE;
            soundPrevMs = 0L;
        }
        lastFrame = now;
        mood(timeMs);
        int frame = (int) ((timeMs / FRAME_MS) % FRAMES);
        double t = timeMs / 1000.0D;
        int offFar = wind(t, WIND_FAR, 60), offMid = wind(t, WIND_MID, 240), offNear = wind(t, WIND_NEAR, 0);
        double dark = darkness();
        // fade-in (vindo do escuro) e fade-out (indo para o escuro ao fechar); o som segue o mesmo fade
        double fade = smooth((now - fadeStart) / (double) FADE_MS);
        if (fadeOutStart != Long.MIN_VALUE && FADE_OUT_MS > 0L) {
            fade *= 1.0D - smooth((now - fadeOutStart) / (double) FADE_OUT_MS);
        }

        RenderSystem.enableBlend();
        g.enableScissor(cx, cy, cx + innerW, cy + innerH);

        // 1. céu em faixas duras com dither entre elas
        sky(g, ox, oy);

        // 2. via láctea, estrelas e estrela cadente (aparecem aos poucos conforme o céu escurece)
        double na = N[SeaMoods.NEB_A] * ramp(dark, 0.35D, 0.35D);
        if (na > 0.004D) {
            double breathe = 0.94D + 0.06D * Math.sin(t * 0.7D);
            full(g, NEB[0], ox, oy, C[SeaMoods.NEB0], na * 0.95D * breathe);
            full(g, NEB[1], ox, oy, C[SeaMoods.NEB1], na * 0.95D * breathe);
            full(g, NEB[2], ox, oy, C[SeaMoods.NEB2], na * 0.85D * breathe);
        }
        // as estrelas mais fracas só aparecem quando já está bem escuro; as mais brilhantes acendem primeiro
        double[] thDust = {0.62D, 0.40D, 0.18D}, thBright = {0.55D, 0.32D, 0.10D};
        for (int i = 0; i < 3; i++) {
            full(g, STARS2[i], ox, oy, C[SeaMoods.STAR2], N[SeaMoods.STAR_B] * ramp(dark, thDust[i], 0.30D) * (0.90D + 0.10D * Math.sin(t * 0.9D + 1.0D + i)));
            full(g, STARS1[i], ox, oy, C[SeaMoods.STAR1], N[SeaMoods.STAR_A] * ramp(dark, thBright[i], 0.30D) * (0.85D + 0.15D * Math.sin(t * 1.3D + i * 2.0D)));
        }
        double starVis = N[SeaMoods.STAR_A] * ramp(dark, 0.10D, 0.35D);
        sparkles(g, ox, oy, t, starVis);
        meteor(g, ox, oy, timeMs, starVis);

        // 3. lua e sol, com brilho suave e raios de luz (as nuvens passam na frente)
        int moonX = (int) Math.round(N[SeaMoods.MOON_X]), moonY = (int) Math.round(N[SeaMoods.MOON_Y]);
        int sunX = (int) Math.round(N[SeaMoods.SUN_X]), sunY = HZ - (int) Math.round(N[SeaMoods.SUN_Y]);
        rays(g, ox, oy, t, moonX, moonY, C[SeaMoods.MGLOW], N[SeaMoods.MOON_A] * 0.75D, 0.0D);
        rays(g, ox, oy, t, sunX, sunY, C[SeaMoods.GLOW], N[SeaMoods.GLOW_A] * N[SeaMoods.DISC_A] * 0.90D, 2.1D);
        prop(g, ox, oy, P_GLOW, moonX, moonY, C[SeaMoods.MGLOW], N[SeaMoods.MOON_A] * 0.55D);
        prop(g, ox, oy, P_RING, moonX, moonY, C[SeaMoods.RING], N[SeaMoods.MOON_A]);
        prop(g, ox, oy, P_MOON, moonX, moonY, C[SeaMoods.MOON], N[SeaMoods.MOON_A]);
        prop(g, ox, oy, P_CRATER, moonX, moonY, C[SeaMoods.CRATER], N[SeaMoods.MOON_A]);
        prop(g, ox, oy, P_MSHADE, moonX, moonY, C[SeaMoods.MSHADE], N[SeaMoods.MOON_A]);
        prop(g, ox, oy, P_GLOW, sunX, sunY, C[SeaMoods.GLOW], N[SeaMoods.GLOW_A] * N[SeaMoods.DISC_A] * 0.85D);
        prop(g, ox, oy, P_SUN, sunX, sunY, C[SeaMoods.SUN], N[SeaMoods.DISC_A]);
        prop(g, ox, oy, P_CORE, sunX, sunY, C[SeaMoods.CORE], N[SeaMoods.DISC_A]);

        // 4. nuvens finas, médias, névoa do horizonte e bancos grandes (cada faixa se mexe numa velocidade)
        int skyB = C[SeaMoods.SKY2];
        strip(g, FAR[0], ox, oy, 0, 0, HZ, HZ, offFar, mix(C[SeaMoods.ST0], skyB, 0.12D), 1.0F);
        strip(g, FAR[1], ox, oy, 0, 0, HZ, HZ, offFar, mix(C[SeaMoods.ST1], skyB, 0.12D), 1.0F);
        for (int i = 0; i < 7; i++) {
            strip(g, MID[i], ox, oy, 0, 0, HZ, HZ, offMid, mix(C[SeaMoods.C0 + i], C[SeaMoods.SKY3], 0.16D), 1.0F);
        }
        for (int j = 0; j < 14; j++) {
            int y = HZ - 1 - j;
            g.fill(ox, oy + y, ox + W, oy + y + 1, argb((int) (255 * N[SeaMoods.HAZE_A] * (0.80D - j * 0.055D)), C[SeaMoods.HAZE]));
        }
        for (int i = 0; i < 7; i++) {
            strip(g, NEAR[i], ox, oy, 0, 0, HZ, HZ, offNear, C[SeaMoods.C0 + i], 1.0F);
        }

        // 5. mar: 5 tons de profundidade (faixas ondulantes) + sombra, crista e espuma
        for (int i = 0; i < 5; i++) sea(g, ox, oy, i, frame, C[SeaMoods.S0 + i], 1.0F);
        sea(g, ox, oy, 5, frame, C[SeaMoods.SHADOW], 0.32F);
        sea(g, ox, oy, 6, frame, C[SeaMoods.CREST], 0.44F);
        sea(g, ox, oy, 7, frame, C[SeaMoods.FOAM], 0.90F);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 6. reflexo ondulado das nuvens e caminho de brilho do sol/lua
        reflection(g, ox, oy, t, REF_MID, offMid, 0.50D);
        reflection(g, ox, oy, t, REF_NEAR, offNear, 1.0D);
        glitter(g, ox, oy, t);
        g.fill(ox, oy + HZ, ox + W, oy + HZ + 1, argb((int) (255 * 0.65D), C[SeaMoods.HAZE]));

        // 7. pássaros
        drawBirds(g, ox, oy, timeMs, C[SeaMoods.BIRD], fade, dark);

        // 8. sons (mar, vento, passarinhos): acompanham o cenário
        SeaAmbience.update(fade, stormAmount, dark);

        // 9. fade-in / fade-out
        if (fade < 0.999D) g.fill(cx, cy, cx + innerW, cy + innerH, argb((int) Math.round(255 * FADE_DARK * (1.0D - fade)), 0x05060E));
        g.disableScissor();
    }

    /** Posições do sol e da lua: seguem uma curva contínua pelos momentos, sem parar nem voltar. */
    private static final int[] POS = {SeaMoods.SUN_X, SeaMoods.SUN_Y, SeaMoods.MOON_X, SeaMoods.MOON_Y};

    /**
     * Interpola a paleta: segura {@link #HOLD_MS} numa cor e então muda devagar para a próxima. As cores e as
     * intensidades usam esse tempo; o sol e a lua seguem uma curva suave (Catmull-Rom) que não pára nunca.
     */
    private static void mood(long timeMs) {
        long seg = HOLD_MS + MORPH_MS;
        long p = Math.floorMod(timeMs, seg * SeaMoods.COUNT);
        int k = (int) (p / seg);
        long into = p % seg;
        double f = 0.0D;
        if (into > HOLD_MS) {
            f = (into - HOLD_MS) / (double) MORPH_MS;
            // meio termo entre linear e suave: sem o "arranca e para" que deixava a troca dia/noite travada
            f = 0.35D * f + 0.65D * (f * f * (3.0D - 2.0D * f));
        }
        int n = SeaMoods.COUNT;
        // som: a tempestade cresce na transição para o momento 3 e some na transição para o 4
        stormAmount = k == STORM_MOOD ? 1.0D - f : (k == STORM_MOOD - 1 ? f : 0.0D);
        double[][] a = LAB[k], b = LAB[(k + 1) % n];
        for (int i = 0; i < C.length; i++) C[i] = mixLab(a[i], b[i], f);
        double[] na = SeaMoods.NUMS[k], nb = SeaMoods.NUMS[(k + 1) % n];
        for (int i = 0; i < N.length; i++) N[i] = na[i] + (nb[i] - na[i]) * f;
        double u = into / (double) seg;
        double[] p0 = SeaMoods.NUMS[(k + n - 1) % n], p3 = SeaMoods.NUMS[(k + 2) % n];
        for (int i : POS) N[i] = catmull(p0[i], na[i], nb[i], p3[i], u);
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        return 0.5D * ((2.0D * p1) + (-p0 + p2) * t + (2.0D * p0 - 5.0D * p1 + 4.0D * p2 - p3) * t * t
                + (-p0 + 3.0D * p1 - 3.0D * p2 + p3) * t * t * t);
    }

    /** Deslocamento do vento em px, em aritmética de long para nunca estourar mesmo com o jogo aberto por dias. */
    private static int wind(double t, double speed, int start) {
        return (int) (((long) (t * speed * K) + start) % STRIP);
    }

    /** O quanto o céu está escuro (0 = dia claro, 1 = noite), pela luminosidade real da paleta atual. */
    private static double darkness() {
        int c = mix(C[SeaMoods.SKY0], C[SeaMoods.SKY2], 0.5D);
        double lum = (0.2126D * ((c >> 16) & 0xFF) + 0.7152D * ((c >> 8) & 0xFF) + 0.0722D * (c & 0xFF)) / 255.0D;
        return Math.max(0.0D, Math.min(1.0D, (0.52D - lum) / 0.26D));
    }

    /** Sobe suavemente de 0 a 1 depois de {@code start}, ao longo de {@code width}. */
    private static double ramp(double v, double start, double width) {
        double x = Math.max(0.0D, Math.min(1.0D, (v - start) / width));
        return x * x * (3.0D - 2.0D * x);
    }

    /** Raios de luz: duas folhas de raios que brilham em alternância devagar (efeito de luz atravessando o céu). */
    private static void rays(GuiGraphics g, int ox, int oy, double t, int cx, int cy, int rgb, double a, double phase) {
        if (a <= 0.004D) return;
        double ga = a * (0.80D + 0.20D * Math.sin(t * 0.35D + phase));
        double gb = a * (0.80D + 0.20D * Math.sin(t * 0.35D + phase + Math.PI));
        int x = cx - RAYS_SIZE / 2, y = cy - RAYS_SIZE / 2;
        for (int i = 0; i < 2; i++) {
            tint(g, rgb, (float) Math.min(1.0D, i == 0 ? ga : gb));
            g.blit(RAYS[i], ox + x, oy + y, RAYS_SIZE, RAYS_SIZE, 0.0F, 0.0F, RAYS_SIZE, RAYS_SIZE, RAYS_SIZE, RAYS_SIZE);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------------------------------
    // céu

    private static int skyColor(int band) {
        double f = (band + 0.5D) / BANDS;
        if (f < 0.35D) return mix(C[SeaMoods.SKY0], C[SeaMoods.SKY1], f / 0.35D);
        if (f < 0.70D) return mix(C[SeaMoods.SKY1], C[SeaMoods.SKY2], (f - 0.35D) / 0.35D);
        return mix(C[SeaMoods.SKY2], C[SeaMoods.SKY3], (f - 0.70D) / 0.30D);
    }

    /** 20 faixas de 4 px; entre duas faixas uma trama xadrez de 2 linhas suaviza a passagem de cor. */
    private static void sky(GuiGraphics g, int ox, int oy) {
        int[] col = new int[BANDS];
        for (int i = 0; i < BANDS; i++) {
            col[i] = skyColor(i);
            g.fill(ox, oy + i * BAND_H, ox + W, oy + i * BAND_H + BAND_H, 0xFF000000 | col[i]);
        }
        for (int i = 1; i < BANDS; i++) {
            int y = i * BAND_H;
            tint(g, col[i], 1.0F);
            g.blit(PROPS, ox, oy + y - 1, W, 1, (float) P_DITH_A[0], (float) P_DITH_A[1], W, 1, 512, 256);
            tint(g, col[i - 1], 1.0F);
            g.blit(PROPS, ox, oy + y, W, 1, (float) P_DITH_B[0], (float) P_DITH_B[1], W, 1, 512, 256);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Estrelas que brilham em cruz, por cima das estrelas fixas. */
    private static void sparkles(GuiGraphics g, int ox, int oy, double t, double w) {
        if (w <= 0.004D) return;
        for (int i = 0; i < TW_X.length; i++) {
            double s = Math.sin(t * (0.8D + (i % 5) * 0.29D) + i * 1.9D);
            if (s < 0.05D) continue;
            int al = (int) (255 * w * Math.min(1.0D, s * 1.15D));
            int x = TW_X[i], y = TW_Y[i];
            rect(g, ox, oy, x, y, x + 1, y + 1, argb(al, 0xFFFFFF));
            if (s > 0.45D) {
                int c = argb(al * 3 / 5, C[SeaMoods.STAR1]);
                rect(g, ox, oy, x - 1, y, x, y + 1, c);
                rect(g, ox, oy, x + 1, y, x + 2, y + 1, c);
                rect(g, ox, oy, x, y - 1, x + 1, y, c);
                rect(g, ox, oy, x, y + 1, x + 1, y + 2, c);
                if (s > 0.8D && i % 3 == 0) {
                    int c2 = argb(al / 3, C[SeaMoods.STAR1]);
                    rect(g, ox, oy, x - 3, y, x - 1, y + 1, c2);
                    rect(g, ox, oy, x + 2, y, x + 4, y + 1, c2);
                    rect(g, ox, oy, x, y - 3, x + 1, y - 1, c2);
                    rect(g, ox, oy, x, y + 2, x + 1, y + 4, c2);
                    rect(g, ox, oy, x - 1, y - 1, x + 2, y + 2, argb(al / 4, 0xFFFFFF));
                }
            }
        }
    }

    /** Estrela cadente: sorteada pelo relógio, só aparece quando o céu está estrelado. */
    private static void meteor(GuiGraphics g, int ox, int oy, long timeMs, double w) {
        if (w < 0.4D) return;
        long slot = timeMs / METEOR_SLOT_MS;
        Random r = new Random(slot * 0x632BE59BD9B4E019L + 99L);
        if (r.nextInt(100) >= METEOR_CHANCE) return;
        double local = (timeMs - slot * METEOR_SLOT_MS) / 1000.0D - 1.0D - r.nextInt(3000) / 1000.0D;
        if (local < 0.0D || local > 0.85D) return;
        boolean right = r.nextBoolean();
        double sx = 30 + r.nextInt(W - 150), sy = 6 + r.nextInt((int) (24 * KY));
        double dirx = right ? 1.0D : -1.0D, diry = 0.42D;
        double head = local * 240.0D;
        double fade = 1.0D - local / 0.85D;
        for (int k = 0; k < 24; k++) {
            double d = head - k * 1.6D;
            if (d < 0) break;
            int x = (int) (sx + dirx * d), y = (int) (sy + diry * d);
            int al = (int) (255 * w * fade * (1.0D - k / 24.0D));
            rect(g, ox, oy, x, y, x + 1, y + 1, argb(al, k < 2 ? 0xFFFFFF : C[SeaMoods.STAR1]));
        }
    }

    // ------------------------------------------------------------------------------------------
    // mar

    private static void sea(GuiGraphics g, int ox, int oy, int idx, int frame, int rgb, float a) {
        if (a <= 0.004F) return;
        tint(g, rgb, a);
        g.blit(SEA[idx], ox, oy + HZ, W, SH, (float) ((frame & 3) * W), (float) ((frame >> 2) * SH), W, SH, SEA_SW, SEA_SH);
    }

    /** Reflexo das nuvens: o céu espelhado, de 2 em 2 linhas, com ondinhas e mais fraco quanto mais fundo. */
    private static void reflection(GuiGraphics g, int ox, int oy, double t, ResourceLocation[] tex, int off, double k) {
        int light = mix(C[SeaMoods.C2], C[SeaMoods.S1], 0.30D), dark = mix(C[SeaMoods.C4], C[SeaMoods.S1], 0.30D);
        for (int r = 0; r < SH; r += 2) {
            int rows = Math.min(2, SH - r);
            int shift = (int) Math.round(2.4D * Math.sin(r * 0.62D + t * 1.9D) + 1.1D * Math.sin(r * 1.5D - t * 2.7D));
            double a = 0.26D * k * (1.0D - r / (double) SH * 0.65D) * (((r >> 1) % 3 == 2) ? 0.40D : 1.0D);
            strip(g, tex[0], ox, oy, HZ + r, r, rows, SH, off + shift, light, (float) a);
            strip(g, tex[1], ox, oy, HZ + r, r, rows, SH, off + shift, dark, (float) a);
        }
    }

    /** Caminho de brilho: traços horizontais que se alargam para baixo e cintilam, seguindo o sol ou a lua. */
    private static void glitter(GuiGraphics g, int ox, int oy, double t) {
        double ws = Math.max(0.0D, N[SeaMoods.DISC_A]) * (N[SeaMoods.SUN_Y] > -6.0D ? 1.0D : 0.0D);
        double wm = Math.max(0.0D, N[SeaMoods.MOON_A]);
        double sum = ws + wm;
        double power = N[SeaMoods.GLIT_A] * Math.min(1.0D, sum);
        if (power <= 0.01D) return;
        double gx = (N[SeaMoods.SUN_X] * ws + N[SeaMoods.MOON_X] * wm) / sum;
        int tf = (int) (t * 7.0D);
        for (int d = 1; d < SH; d++) {
            if (d % 3 == 0) continue;
            Random r = new Random(d * 7919L + (tf >> (d > 14 ? 0 : 1)) * 104729L);
            double hw = 4.0D + d * 0.80D;
            double len = hw * (0.30D + 0.70D * r.nextDouble());
            int cx = (int) Math.round(gx + (r.nextDouble() - 0.5D) * hw * 0.5D + Math.sin(t * 1.4D + d) * 2.0D);
            int al = (int) (255 * power * (0.50D + 0.50D * r.nextDouble()) * (1.0D - d / (double) SH * 0.30D));
            int y = HZ + d;
            int x1 = cx - (int) len, x2 = cx + (int) len + 1;
            if (d > 6 && r.nextInt(100) < 55) {
                int gap = 2 + r.nextInt(3), mid = cx + r.nextInt(3) - 1;
                rect(g, ox, oy, x1, y, mid - gap, y + 1, argb(al, C[SeaMoods.GLITTER]));
                rect(g, ox, oy, mid + gap, y, x2, y + 1, argb(al, C[SeaMoods.GLITTER]));
            } else {
                rect(g, ox, oy, x1, y, x2, y + 1, argb(al, C[SeaMoods.GLITTER]));
            }
            if (r.nextInt(100) < 28) {
                int px = cx + (int) ((r.nextDouble() - 0.5D) * hw * 2.4D);
                rect(g, ox, oy, px, y, px + 1 + r.nextInt(3), y + 1, argb(Math.min(255, al + 40), 0xFFFFFF));
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // desenho de máscaras

    /** Pinta uma máscara de 234 x 78 (estrelas, via láctea) sobre o céu. */
    private static void full(GuiGraphics g, ResourceLocation tex, int ox, int oy, int rgb, double a) {
        if (a <= 0.004D) return;
        tint(g, rgb, (float) a);
        g.blit(tex, ox, oy, W, HZ, 0.0F, 0.0F, W, HZ, W, HZ);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Pinta uma faixa de uma tira de nuvens (468 px, repete sem emenda), deslocada em {@code u} px:
     * mostra 234 px a partir da coluna u, dando a volta se passar do fim.
     */
    private static void strip(GuiGraphics g, ResourceLocation tex, int ox, int oy, int dy, int v, int h, int texH,
                              int u, int rgb, float a) {
        if (a <= 0.004F) return;
        tint(g, rgb, a);
        u = Math.floorMod(u, STRIP);
        int first = Math.min(W, STRIP - u);
        g.blit(tex, ox, oy + dy, first, h, (float) u, (float) v, first, h, STRIP, texH);
        if (first < W) {
            g.blit(tex, ox + first, oy + dy, W - first, h, 0.0F, (float) v, W - first, h, STRIP, texH);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Desenha um sprite de props.png centrado em (cx, cy). */
    private static void prop(GuiGraphics g, int ox, int oy, int[] r, int cx, int cy, int rgb, double a) {
        if (a <= 0.004D) return;
        int x = cx - r[2] / 2, y = cy - r[3] / 2;
        tint(g, rgb, (float) Math.min(1.0D, a));
        g.blit(PROPS, ox + x, oy + y, r[2], r[3], (float) r[0], (float) r[1], r[2], r[3], 512, 256);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Define a cor/alfa do próximo blit. Também religa o blend: o fill() do Minecraft desliga, e sem blend a
     * transparência das máscaras (raios, brilho, estrelas, reflexos...) é ignorada e tudo sai chapado.
     */
    private static void tint(GuiGraphics g, int rgb, float a) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, a);
    }

    // ------------------------------------------------------------------------------------------
    // Pássaros
    //
    // Cada pixel do sprite vale uma célula (2 px nos bandos próximos, 1 px nos distantes). O corpo fica
    // sempre na mesma linha e coluna em todos os quadros, só as asas mudam, e o movimento anda em passos
    // inteiros de célula, então não há tremida nem "pulo" entre quadros.

    /** 9 x 5 células. Quadros: 0 asas no alto, 1 asas abertas, 2 asas caídas. O corpo não sai do lugar. */
    private static final String[][] BIG = {
            {"#.......#", ".##...##.", "..##.##..", "...###...", "........."},
            {".........", "##.....##", ".###.###.", "...###...", "........."},
            {".........", ".........", "..#####..", ".##.#.##.", "##.....##"}};
    /** Ordem dos quadros num ciclo de batida (sobe, abre, desce, abre). */
    private static final int[] FLAP = {0, 1, 2, 1};

    /**
     * Sorteia, de forma determinística pelo relógio, bandos que cruzam a tela a cada
     * {@link #BIRD_SLOT_MS}: de 1 a 5 pássaros em V, indo para a esquerda ou para a direita. Um
     * bando dura menos que um intervalo, então nunca se sobrepõe ao seguinte.
     */
    private static void drawBirds(GuiGraphics g, int ox, int oy, long timeMs, int color, double fade, double dark) {
        long slot = timeMs / BIRD_SLOT_MS;
        // cantos só em animação contínua: depois de uma pausa (aba escondida, alt-tab) não toca os que "ficaram para trás"
        boolean live = soundPrevMs != 0L && timeMs - soundPrevMs < 400L;
        for (long s = slot - 1; s <= slot; s++) {
            Random r = new Random(s * 0x9E3779B97F4A7C15L + 12345L);
            if (r.nextInt(100) >= BIRD_CHANCE) continue;
            int count = 1 + r.nextInt(5);
            boolean right = r.nextBoolean();
            boolean far = r.nextInt(3) == 0;
            int laneY = 16 + r.nextInt((int) (44 * KY));
            double speed = ((far ? 14 : 20) + r.nextInt(10)) * K;                     // px/s
            double slope = (r.nextInt(5) - 2) * 1.2D;                           // sobe ou desce devagar (px/s)
            double fps = 6.0D + r.nextInt(3);                                   // batidas de asa
            double delay = r.nextInt(3000) / 1000.0D;
            double elapsed = (timeMs - s * BIRD_SLOT_MS) / 1000.0D - delay;
            if (elapsed < 0) continue;
            if (live) {
                double before = (soundPrevMs - s * BIRD_SLOT_MS) / 1000.0D - delay;
                birdCalls(s, count, right, far, speed, before, elapsed, fade, dark);
            }

            String[][] sprite = BIG;
            int cell = far ? 1 : 2;
            int sw = sprite[0][0].length() * cell, sh = sprite[0].length * cell;

            for (int i = 0; i < count; i++) {
                int lag = ((i + 1) / 2) * (far ? 14 : 24);
                int dy = i == 0 ? 0 : (i % 2 == 1 ? 1 : -1) * ((i + 1) / 2) * (far ? 6 : 10);
                double travel = elapsed * speed;
                int x = right ? (int) (-30 + travel) - lag : (int) (W + 30 - travel) + lag;
                int y = laneY + dy + (int) (elapsed * slope);
                if (cell == 2) {
                    x &= ~1;
                    y &= ~1;
                }
                if (x < 0 || x - sw > W) continue;                               // só some quando saiu inteiro da tela
                int frame = FLAP[(int) (elapsed * fps + i * 2) % FLAP.length];
                drawSprite(g, ox, oy, sprite[frame], cell, x - sw, y - sh, color);
            }
        }
        soundPrevMs = timeMs;
    }

    /**
     * Cantos de passarinho de um bando: de 2 a 4 por bando (mais pássaros, mais cantos), em instantes sorteados pela
     * semente do próprio bando (então são sempre os mesmos instantes, mesmo com o quadro variando). Um canto toca
     * quando o relógio do bando cruza o seu instante entre dois quadros, e só se o pássaro da frente estiver
     * na tela, com o pan na posição dele.
     */
    private static void birdCalls(long s, int count, boolean right, boolean far, double speed,
                                  double before, double elapsed, double fade, double dark) {
        Random rc = new Random(s * 0x2545F4914F6CDD1DL + 4242L);
        int calls = 2 + count / 2;
        double enter = 30.0D / speed;                        // o líder já está dentro da tela
        double span = Math.max(1.0D, (W + 30.0D) / speed - enter - 1.5D);
        for (int c = 0; c < calls; c++) {
            double at = enter + rc.nextDouble() * span;
            float jitter = rc.nextFloat();
            if (!(before < at && at <= elapsed)) continue;
            double travel = at * speed;
            double xc = right ? -30 + travel : W + 30 - travel;   // posição do pássaro da frente
            if (xc < 0 || xc > W) continue;
            double gain = fade * (1.0D - 0.45D * dark);          // de noite os pássaros ficam mais baixos
            SeaAmbience.bird((xc / W - 0.5D) * 2.0D, far, gain, jitter);
        }
    }

    /** Todas as texturas do fundo (para pré-carregar na abertura do jogo e não travar ao abrir a aba). */
    public static java.util.List<ResourceLocation> allTextures() {
        java.util.List<ResourceLocation> l = new java.util.ArrayList<>();
        java.util.Collections.addAll(l, SEA);
        java.util.Collections.addAll(l, NEAR);
        java.util.Collections.addAll(l, MID);
        java.util.Collections.addAll(l, FAR);
        java.util.Collections.addAll(l, REF_NEAR);
        java.util.Collections.addAll(l, REF_MID);
        java.util.Collections.addAll(l, NEB);
        java.util.Collections.addAll(l, STARS1);
        java.util.Collections.addAll(l, STARS2);
        java.util.Collections.addAll(l, RAYS);
        l.add(PROPS);
        return l;
    }

    /** Desenha um sprite em células, juntando pixels vizinhos numa linha só. */
    private static void drawSprite(GuiGraphics g, int ox, int oy, String[] rows, int cell, int x, int y, int color) {
        for (int ry = 0; ry < rows.length; ry++) {
            int start = -1;
            String row = rows[ry];
            for (int cx = 0; cx <= row.length(); cx++) {
                boolean on = cx < row.length() && row.charAt(cx) == '#';
                if (on && start < 0) start = cx;
                if (!on && start >= 0) {
                    rect(g, ox, oy, x + start * cell, y + ry * cell, x + cx * cell, y + ry * cell + cell, argb(255, color));
                    start = -1;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // utilitários

    /** Retângulo em coordenadas locais, recortado para dentro da área. */
    private static void rect(GuiGraphics g, int ox, int oy, int x1, int y1, int x2, int y2, int color) {
        x1 = Math.max(x1, 0);
        y1 = Math.max(y1, 0);
        x2 = Math.min(x2, W);
        y2 = Math.min(y2, H);
        if (x2 <= x1 || y2 <= y1) return;
        g.fill(ox + x1, oy + y1, ox + x2, oy + y2, color);
    }

    /** Degrau suave 0..1 (smoothstep), com o valor preso entre 0 e 1. */
    private static double smooth(double x) {
        x = x < 0.0D ? 0.0D : Math.min(x, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double toLinear(int c8) {
        double v = c8 / 255.0D;
        return v <= 0.04045D ? v / 12.92D : Math.pow((v + 0.055D) / 1.055D, 2.4D);
    }

    private static int to8(double lin) {
        lin = lin < 0.0D ? 0.0D : Math.min(lin, 1.0D);
        double v = lin <= 0.0031308D ? 12.92D * lin : 1.055D * Math.pow(lin, 1.0D / 2.4D) - 0.055D;
        return (int) Math.round(v * 255.0D);
    }

    /** RGB (0xRRGGBB) para OKLab: o espaço de cor em que misturar fica parecido com o que o olho espera. */
    private static void toOklab(int rgb, double[] o) {
        double r = toLinear((rgb >> 16) & 0xFF), gr = toLinear((rgb >> 8) & 0xFF), b = toLinear(rgb & 0xFF);
        double l = Math.cbrt(0.4122214708D * r + 0.5363325363D * gr + 0.0514459929D * b);
        double m = Math.cbrt(0.2119034982D * r + 0.6806995451D * gr + 0.1073969566D * b);
        double s = Math.cbrt(0.0883024619D * r + 0.2817188376D * gr + 0.6299787005D * b);
        o[0] = 0.2104542553D * l + 0.7936177850D * m - 0.0040720468D * s;
        o[1] = 1.9779984951D * l - 2.4285922050D * m + 0.4505937099D * s;
        o[2] = 0.0259040371D * l + 0.7827717662D * m - 0.8086757660D * s;
    }

    /** Mistura duas cores já em OKLab e devolve 0xRRGGBB. */
    private static int mixLab(double[] a, double[] b, double f) {
        f = f < 0 ? 0 : Math.min(f, 1);
        double L = a[0] + (b[0] - a[0]) * f, A = a[1] + (b[1] - a[1]) * f, B = a[2] + (b[2] - a[2]) * f;
        double l = L + 0.3963377774D * A + 0.2158037573D * B;
        double m = L - 0.1055613458D * A - 0.0638541728D * B;
        double s = L - 0.0894841775D * A - 1.2914855480D * B;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        int r = to8(4.0767416621D * l - 3.3077115913D * m + 0.2309699292D * s);
        int g = to8(-1.2684380046D * l + 2.6097574011D * m - 0.3413193965D * s);
        int bl = to8(-0.0041960863D * l - 0.7034186147D * m + 1.7076147010D * s);
        return (r << 16) | (g << 8) | bl;
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, double f) {
        f = f < 0 ? 0 : Math.min(f, 1);
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int gr = (int) Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return (r << 16) | (gr << 8) | bl;
    }
}
