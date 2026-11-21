package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Sons da aba de conquistas do Sea Sovereign: mar, vento e as passarinhos dos bandos que cruzam o céu.
 *
 * <p>Tudo é dirigido pelo próprio desenho do fundo ({@link SeaBackground#render}):
 * <ul>
 *   <li>enquanto o fundo é desenhado, {@link #update} é chamado a cada quadro ("batimento"). Os loops de mar e
 *       vento sobem suavemente quando a aba abre e <b>somem sozinhos</b> (fade-out + parada) assim que o batimento
 *       cessa: fechar a tela, trocar de aba, alt-tab. Não precisa de nenhum evento de fechar;</li>
 *   <li>a tempestade (paleta 3 de {@code SeaMoods}) levanta o vento e o mar; à noite o mar fica um pouco mais
 *       baixo;</li>
 *   <li>quando um bando de pássaros passa, {@link #bird} toca um canto com o pan acompanhando a posição do
 *       pássaro da frente na tela (esquerda/direita).</li>
 * </ul>
 *
 * <p>Os sons saem na categoria "Ambiente" do jogo (controle de volume em Opções &gt; Música e sons). Ajustes
 * rápidos de volume: {@link #WAVES_BASE}, {@link #WAVES_STORM}, {@link #WIND_BASE}, {@link #WIND_STORM},
 * {@link #BIRD_NEAR}, {@link #BIRD_FAR}.
 */
public final class SeaAmbience {
    private SeaAmbience() {}

    /** Volume do mar: calmo, quanto a tempestade soma, e quanto a noite tira (0 a 1). */
    public static final double WAVES_BASE = 0.75D, WAVES_STORM = 0.25D, WAVES_NIGHT = 0.08D;
    /** Volume do vento: calmo e quanto a tempestade soma. */
    public static final double WIND_BASE = 0.30D, WIND_STORM = 0.55D;
    /** Volume dos passarinhos: bando perto e bando distante. */
    public static final float BIRD_NEAR = 0.60F, BIRD_FAR = 0.30F;
    /** Quanto tempo sem receber {@link #update} até os loops começarem a sumir (ms). */
    private static final long ALIVE_MS = 400L;
    /** Velocidade do fade dos loops, em "volume cheio" por segundo: sobe em ~1,2 s e desce em ~0,25 s. */
    private static final double RISE_PER_S = 0.8D, FALL_PER_S = 4.0D;
    /** Intervalo mínimo entre duas tentativas de iniciar o mesmo loop (ms), para nunca duplicar. */
    private static final long RESTART_MS = 1_000L;

    private static long heartbeat = 0L;
    private static double master = 0.0D, storm = 0.0D, dark = 0.0D;
    private static Loop waves, wind;
    private static long wavesStart = 0L, windStart = 0L;

    /**
     * Chamado a cada quadro em que o fundo é desenhado: mantém (e inicia, se preciso) os loops de mar e vento.
     *
     * @param masterGain 0 a 1: fade-in/fade-out do cenário (o som acompanha)
     * @param stormAmount 0 a 1: o quanto o céu está em tempestade
     * @param darkAmount 0 a 1: o quanto o céu está escuro (noite)
     */
    public static void update(double masterGain, double stormAmount, double darkAmount) {
        if (SeaBackground.isFadingOut()) return;   // fechando: o som já está saindo, não deixa reviver
        long now = System.currentTimeMillis();
        heartbeat = now;
        master = masterGain;
        storm = stormAmount;
        dark = darkAmount;
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        if (needsStart(waves, sm) && now - wavesStart > RESTART_MS) {
            wavesStart = now;
            waves = new Loop(ModSounds.SEA_WAVES.get(), false);
            sm.play(waves);
        }
        if (needsStart(wind, sm) && now - windStart > RESTART_MS) {
            windStart = now;
            wind = new Loop(ModSounds.SEA_WIND.get(), true);
            sm.play(wind);
        }
    }

    private static boolean needsStart(Loop l, SoundManager sm) {
        return l == null || l.isStopped() || !sm.isActive(l);
    }

    /** Corta os loops já (com um fade curto): chamado quando a tela de conquistas fecha ou começa a fechar. */
    public static void release() {
        heartbeat = 0L;
    }

    /**
     * Um canto de passarinho.
     *
     * @param pan -1 (todo à esquerda) a 1 (todo à direita)
     * @param far bando distante: mais baixo e mais agudo
     * @param gain 0 a 1, já com fade e noite aplicados por quem chama
     * @param jitter variação de tom (0 a 1), sorteada por quem chama para ser repetível
     */
    public static void bird(double pan, boolean far, double gain, float jitter) {
        float vol = (float) (gain * (far ? BIRD_FAR : BIRD_NEAR));
        if (vol <= 0.01F) return;
        float pitch = (far ? 1.10F : 0.95F) + 0.12F * jitter;
        Minecraft.getInstance().getSoundManager().play(new Chirp(Math.max(-1.0D, Math.min(1.0D, pan)), vol, pitch));
    }

    /** Loop de ambiente: estéreo, preso ao ouvinte (sem distância), com volume que segue a tempestade. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final boolean isWind;
        private double cur = 0.0D;
        private long lastNs = 0L;

        Loop(SoundEvent event, boolean isWind) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            this.isWind = isWind;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
        }

        /** Começa mudo (a subida é feita no tick); sem isso o jogo descarta o som de volume 0. */
        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            long nowNs = System.nanoTime();
            double dt = lastNs == 0L ? 0.05D : Math.min(0.25D, (nowNs - lastNs) / 1.0e9D);
            lastNs = nowNs;
            // só soa enquanto a tela de conquistas é mesmo a tela aberta e o fundo está sendo desenhado
            boolean alive = System.currentTimeMillis() - heartbeat < ALIVE_MS
                    && Minecraft.getInstance().screen instanceof AdvancementsScreen;
            double target = 0.0D;
            if (alive) {
                target = isWind ? WIND_BASE + WIND_STORM * storm
                        : WAVES_BASE + WAVES_STORM * storm - WAVES_NIGHT * dark;
                target = Math.max(0.0D, Math.min(1.0D, target)) * master;
            }
            // o volume anda em velocidade constante (por tempo, não por tick): nunca dá salto nem cauda longa
            if (target > cur) cur = Math.min(target, cur + RISE_PER_S * dt);
            else cur = Math.max(target, cur - FALL_PER_S * dt);
            if (!alive && cur <= 0.002D) {
                stop();
                return;
            }
            this.volume = (float) cur;
        }
    }

    /** Canto de passarinho: mono, posição relativa ao ouvinte (pan) e sem queda por distância. */
    private static final class Chirp extends AbstractSoundInstance {
        Chirp(double pan, float volume, float pitch) {
            super(ModSounds.BIRD.get(), SoundSource.AMBIENT, RandomSource.create());
            this.volume = volume;
            this.pitch = pitch;
            this.relative = true;
            this.x = pan * 3.5D;     // + = direita do jogador
            this.y = 1.5D;
            this.z = -2.0D;          // um pouco à frente
        }
    }
}
