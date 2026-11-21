package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.character.CharacterCache;
import br.com.lasthorizon.akuma.character.CharacterData;
import br.com.lasthorizon.akuma.character.CharacterNetwork;
import br.com.lasthorizon.akuma.character.SkinPainter;
import com.mojang.blaze3d.platform.NativeImage;
import br.com.lasthorizon.akuma.character.CharacterDestiny;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado do personagem no CLIENTE: as texturas de skin geradas (uma por jogador) e o pedido do servidor para abrir
 * a tela de criação. Os dados em si ficam em {@link CharacterCache}.
 */
public final class CharacterClient {
    private CharacterClient() {}

    /**
     * A skin de um jogador: UMA textura que o jogo desenha, e as versões já pintadas dela (uma para cada combinação de piscar e
     * olhar, pintadas só quando aparecem pela primeira vez). Trocar de versão é copiar 64 x 64 pixels para a textura.
     */
    private static final class Skin {
        final ResourceLocation loc;
        final DynamicTexture tex;
        final CharacterData data;
        final int[][] frames = new int[27][];
        int shown = 0;                                   // a versão que está na textura agora

        Skin(ResourceLocation loc, DynamicTexture tex, CharacterData data) {
            this.loc = loc;
            this.tex = tex;
            this.data = data;
        }
    }

    private static final Map<UUID, Skin> TEX = new HashMap<>();
    /** O servidor pediu a criação e o jogador ainda não finalizou: a tela é (re)aberta sempre que a tela atual é nenhuma. */
    private static boolean pendingOpen = false;
    private static int tick = 0;
    /** De "o servidor pediu a criação" até "o meu personagem foi confirmado": sem HUD, sem toasts, sem tutorial. */
    private static boolean locked = false;
    /** Ticks esperando a confirmação com a tela fechada (se o servidor recusou, a tela reabre). */
    private static int stuckTicks = 0;
    /** O tutorial do jogo foi desligado por nós e precisa voltar ao passo que estava. */
    /** Quando o braço do personagem começou a reaparecer (fim do selo); -1 = sem animação (a mão segue a regra do bloqueio). */
    private static long handRevealStart = -1L;
    /** Quanto da subida já passou, em ms: só avança nos quadros (com limite por quadro), então um travamento não pula a animação. */
    private static long handRevealMs = 0L;
    private static long lastFrameNanos = 0L;
    /** Duração da subida do braço. */
    private static final long HAND_REVEAL_MS = 1700L;
    /** Já avisou o servidor que o braço e a UI terminaram de aparecer (a conquista vem daí). */
    private static boolean revealSent = false;
    private static boolean tutorialHeld = false;
    private static TutorialSteps savedTutorial = TutorialSteps.NONE;

    /** O jogador ainda não terminou o personagem? Enquanto sim, nenhuma UI do jogo aparece. */
    public static boolean isLocked() {
        return locked || Minecraft.getInstance().screen instanceof CharacterCreationScreen;
    }

    /** Esconder HUD e mão? Sim enquanto bloqueado, exceto durante o reaparecimento no fim do selo (aí eles voltam animados). */
    public static boolean hideUi() {
        return isLocked() && handReveal() < 0.0F;
    }

    /** O braço e a UI terminaram de aparecer; avisa o servidor para dar a conquista (aparece para todos). */
    public static void notifyRevealed() {
        CharacterNetwork.CHANNEL.sendToServer(new CharacterNetwork.Revealed());
    }

    /** Skin gerada deste jogador, ou null (usa a skin normal da conta). Chamado pelo mixin a cada quadro: só um get. */
    public static ResourceLocation textureFor(UUID id) {
        Skin skin = TEX.get(id);
        if (skin == null) return null;
        int blink = 0, gx = 0, gy = 0;
        if (!hiddenFace(id)) {                           // na primeira pessoa o rosto do jogador não aparece: não gasta nada
            blink = blinkPhase(id);
        }
        int state = blink * 9 + (gy + 1) * 3 + (gx + 1);
        if (state != skin.shown) show(skin, state);
        return skin.loc;
    }

    /** O rosto deste jogador não está à vista (sou eu em primeira pessoa)? Só o braço aparece, e ele não pisca nem olha. */
    private static boolean hiddenFace(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && id.equals(mc.player.getUUID()) && mc.options.getCameraType().isFirstPerson()
                && !(mc.screen instanceof CharacterCreationScreen);
    }

    /** Põe na textura a versão {@code state} (piscar * 9 + olhar), pintando-a se ainda não existe. */
    private static void show(Skin skin, int state) {
        int blink = state / 9, cell = state % 9;
        int[] px = skin.frames[state];
        if (px == null) px = skin.frames[state] = SkinPainter.paint(skin.data, blink, cell % 3 - 1, cell / 3 - 1);
        NativeImage img = skin.tex.getPixels();
        if (img == null) return;
        for (int y = 0; y < SkinPainter.SIZE; y++) {
            for (int x = 0; x < SkinPainter.SIZE; x++) {
                int c = px[y * SkinPainter.SIZE + x];
                img.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));   // ARGB -> ABGR
            }
        }
        skin.tex.upload();
        skin.shown = state;
    }

    /**
     * Fase do piscar deste jogador agora: 0 aberto, 1 meio fechado, 2 fechado. Cada um pisca no seu ritmo (período de 3,2 a
     * 6,2 s e um começo diferente, tirados do UUID, para ninguém piscar junto); um piscar dura 160 ms (meio, fechado, meio)
     * e, de vez em quando, vem outro logo em seguida.
     */
    private static int blinkPhase(UUID id) {
        int h = id.hashCode() * 0x9E3779B1;
        long period = 3200L + ((h >>> 8) & 0x7FFF) % 3000L;
        long now = System.currentTimeMillis() + ((h >>> 3) & 0xFFFFF);
        long t = now % period, cycle = now / period;
        int phase = blinkAt(t);
        if (phase == 0 && cycle % 3L == 0L) phase = blinkAt(t - 280L);          // o segundo piscar
        return phase;
    }

    /** O piscar visto {@code u} ms depois de começar: 40 ms meio fechado, 80 ms fechado, 40 ms meio fechado. */
    private static int blinkAt(long u) {
        if (u < 0L || u >= 160L) return 0;
        return u < 40L || u >= 120L ? 1 : 2;
    }

    /** Servidor -> cliente: personagem de um jogador definido (data != null) ou removido (data == null). */
    public static void onSync(UUID id, CharacterData data) {
        if (data == null || !data.isValid()) {
            drop(id);
        } else {
            setPreview(id, data);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && id.equals(mc.player.getUUID())) {                       // o meu foi finalizado
                pendingOpen = false;
                locked = false;
            }
        }
        refreshName(id);
        Minecraft mc2 = Minecraft.getInstance();
        if (mc2.level != null) {
            net.minecraft.world.entity.player.Player pl = mc2.level.getPlayerByUUID(id);
            if (pl != null) pl.refreshDimensions();            // hitbox nova (a altura escolhida; a largura acompanha)
        }
    }

    /** Servidor -> cliente: "abra a tela de criação". */
    public static void requestOpen() {
        pendingOpen = true;
        locked = true;
        handRevealStart = -1L;
        revealSent = false;
    }

    /** O fade do selo chegou à hora de o braço reaparecer: começa a subida (só uma vez por criação). */
    public static void beginHandReveal() {
        if (handRevealStart < 0L) {
            handRevealStart = System.currentTimeMillis();
            handRevealMs = 0L;
            lastFrameNanos = System.nanoTime();
            revealSent = false;
        }
    }

    /**
     * Uma vez por quadro: avança a subida do braço e da UI. Cada quadro conta no máximo 50 ms, então se o jogo travar
     * (mundo carregando) a animação continua de onde estava em vez de pular direto para o fim e aparecer tudo de uma vez.
     */
    public static void frame() {
        if (handRevealStart < 0L || handRevealMs >= HAND_REVEAL_MS) return;
        long now = System.nanoTime();
        long dt = Math.max(0L, Math.min(50L, (now - lastFrameNanos) / 1_000_000L));
        lastFrameNanos = now;
        handRevealMs = Math.min(HAND_REVEAL_MS, handRevealMs + dt);
    }

    /**
     * O mundo pode aparecer: o servidor já confirmou o meu personagem e o terreno em volta já chegou e foi desenhado.
     * (Se o mundo ainda está carregando quando a tela some, o jogo pisca e tudo surge de uma vez.)
     */
    public static boolean revealReady() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.levelRenderer == null) return false;
        if (locked) return false;                                  // o servidor ainda não confirmou o personagem
        BlockPos pos = mc.player.blockPosition();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return mc.levelRenderer.isChunkCompiled(pos);
    }

    /** Progresso da subida do braço (0..1), ou -1 se não há animação em andamento nem concluída. */
    public static float handReveal() {
        if (handRevealStart < 0L) return -1.0F;
        return Math.min(1.0F, handRevealMs / (float) HAND_REVEAL_MS);
    }

    /** O jogador confirmou a criação (a tela vai fechar): não reabrir enquanto o servidor responde. */
    public static void markFinishing() {
        pendingOpen = false;
    }

    /**
     * Mensagem de boas-vindas no chat, logo depois que a tela de criação fecha. É escrita aqui, no cliente de quem criou
     * (não vem do servidor): só essa pessoa a vê, nunca os outros jogadores, e ela chega só quando a tela já fechou.
     */
    public static void sayWelcome(CharacterData d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || d == null || !d.isValid()) return;
        String k = "character.lasthorizon.";
        MutableComponent rule = Component.literal("\u2726 " + "\u2500".repeat(14) + " \u2726").withStyle(ChatFormatting.GOLD);
        mc.gui.getChat().addMessage(rule);
        mc.gui.getChat().addMessage(Component.translatable(k + "sealed").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        mc.gui.getChat().addMessage(Component.translatable(k + "sealed.welcome",
                Component.literal(d.name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)).withStyle(ChatFormatting.WHITE));
        if (!d.epithet.isEmpty()) {
            mc.gui.getChat().addMessage(Component.literal("\u2014 " + d.epithet).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        }
        mc.gui.getChat().addMessage(Component.translatable(k + "sheet.soul",
                Component.translatable(k + CharacterDestiny.of(d).key()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        if (!d.motto.isEmpty()) {
            mc.gui.getChat().addMessage(Component.literal("\u201C" + d.motto + "\u201D").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        }
        mc.gui.getChat().addMessage(Component.translatable(k + "created.wish").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        mc.gui.getChat().addMessage(rule);
    }

    /** Mostra estas escolhas na skin do jogador agora (também usada pela pré-visualização da tela de criação). */
    public static void setPreview(UUID id, CharacterData d) {
        CharacterCache.put(id, d);
        ResourceLocation loc = new ResourceLocation(LastHorizonMod.MOD_ID, "character/" + id);
        int[] open = SkinPainter.paint(d);
        DynamicTexture tex = new DynamicTexture(toImage(open));
        Minecraft.getInstance().getTextureManager().register(loc, tex);          // substitui e libera a anterior
        Skin skin = new Skin(loc, tex, d);
        skin.frames[4] = open;                     // aberto e olhando em frente: já está pintado
        skin.shown = 4;
        TEX.put(id, skin);
    }

    private static NativeImage toImage(int[] argb) {
        NativeImage img = new NativeImage(SkinPainter.SIZE, SkinPainter.SIZE, true);
        for (int y = 0; y < SkinPainter.SIZE; y++) {
            for (int x = 0; x < SkinPainter.SIZE; x++) {
                int c = argb[y * SkinPainter.SIZE + x];
                img.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));   // ARGB -> ABGR
            }
        }
        return img;
    }

    private static void drop(UUID id) {
        CharacterCache.remove(id);
        Skin skin = TEX.remove(id);
        if (skin != null) Minecraft.getInstance().getTextureManager().release(skin.loc);
    }

    /** O nome do jogador é guardado em cache pelo jogo: manda recalcular quando o personagem muda. */
    private static void refreshName(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player p = mc.level.getPlayerByUUID(id);
        if (p != null) p.refreshDisplayName();
    }

    /** A cada tick do cliente: abre a tela de criação quando pedido e mantém os nomes em dia. */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (pendingOpen) {
            Screen s = mc.screen;
            if (s == null || s instanceof ReceivingLevelScreen) mc.setScreen(new CharacterCreationScreen());
        }
        holdTutorial(mc);
        if (!revealSent && handReveal() >= 1.0F) {          // braço e UI terminaram de aparecer: o servidor dá a conquista
            revealSent = true;
            notifyRevealed();
        }
        if (locked && !pendingOpen && mc.screen == null) {            // finalizou mas o servidor não confirmou (recusou?): reabre
            if (++stuckTicks > 100) {
                stuckTicks = 0;
                pendingOpen = true;
                handRevealStart = -1L;
            }
        } else {
            stuckTicks = 0;
        }
        if (++tick >= 20) {      // o nome de jogadores que apareceram depois da sincronização
            tick = 0;
            for (Player p : mc.level.players()) {
                CharacterData d = CharacterCache.get(p.getUUID());
                if (d != null && !p.getDisplayName().getString().equals(d.name)) p.refreshDisplayName();
            }
        }
    }

    /** Enquanto a criação não termina o tutorial (W, A, S, D, mouse...) fica desligado; depois volta ao passo em que estava. */
    private static void holdTutorial(Minecraft mc) {
        boolean lock = isLocked();
        if (lock && !tutorialHeld) {
            tutorialHeld = true;
            savedTutorial = mc.options.tutorialStep;
            if (savedTutorial != TutorialSteps.NONE) mc.getTutorial().setStep(TutorialSteps.NONE);
        } else if (!lock && tutorialHeld) {
            releaseTutorial(mc);
        }
    }

    private static void releaseTutorial(Minecraft mc) {
        if (!tutorialHeld) return;
        tutorialHeld = false;
        if (savedTutorial != TutorialSteps.NONE && mc.options.tutorialStep == TutorialSteps.NONE) mc.getTutorial().setStep(savedTutorial);
        savedTutorial = TutorialSteps.NONE;
    }

    /** Saiu do mundo/servidor: limpa tudo (o personagem é por mundo). */
    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        releaseTutorial(mc);                                  // não deixa o tutorial desligado para sempre nas opções
        locked = false;
        handRevealStart = -1L;
        handRevealMs = 0L;
        stuckTicks = 0;
        for (Skin skin : TEX.values()) mc.getTextureManager().release(skin.loc);
        TEX.clear();
        CharacterCache.clear();
        pendingOpen = false;
        tick = 0;
    }
}
