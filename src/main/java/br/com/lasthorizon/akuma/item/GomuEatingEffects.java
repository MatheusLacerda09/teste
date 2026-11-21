package br.com.lasthorizon.akuma.item;

import br.com.lasthorizon.akuma.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos de partículas ao comer a Gomu Gomu no Mi.
 * Todas as "variáveis" para ajustar o efeito estão nas constantes abaixo.
 */
public final class GomuEatingEffects {
    private GomuEatingEffects() {}

    // ---------- Mastigando (enquanto segura o botão de comer) ----------
    /** A cada quantos ticks sai um punhado de migalhas (20 ticks = 1 s). */
    public static final int CHEW_INTERVAL_TICKS = 3;
    /** Migalhas por punhado. */
    public static final int CHEW_CRUMBS = 2;
    /** Velocidade das migalhas para frente. */
    public static final double CHEW_FORWARD_SPEED = 0.06D;
    /** Impulso para cima das migalhas. */
    public static final double CHEW_UP_SPEED = 0.05D;
    /** Dispersão lateral aleatória. */
    public static final double CHEW_SPREAD = 0.06D;

    // ---------- Ao engolir (fim de comer) ----------
    /** Migalhas que explodem da boca. */
    public static final int BURST_CRUMBS = 14;
    public static final double BURST_SPEED = 0.18D;
    /** Chamado no CLIENTE enquanto o jogador mastiga. */
    public static void chew(Level level, LivingEntity entity) {
        RandomSource r = level.getRandom();
        Vec3 look = entity.getLookAngle();
        Vec3 mouth = entity.getEyePosition().add(look.scale(0.45D)).add(0.0D, -0.18D, 0.0D);
        for (int i = 0; i < CHEW_CRUMBS; i++) {
            level.addParticle(ModParticles.GOMU_BITE.get(), mouth.x, mouth.y, mouth.z,
                    look.x * CHEW_FORWARD_SPEED + (r.nextDouble() - 0.5D) * CHEW_SPREAD,
                    CHEW_UP_SPEED * (0.5D + r.nextDouble()),
                    look.z * CHEW_FORWARD_SPEED + (r.nextDouble() - 0.5D) * CHEW_SPREAD);
        }
    }

    /** Chamado no SERVIDOR quando termina de comer: só uma explosão de migalhas da boca. */
    public static void finish(ServerLevel level, LivingEntity entity) {
        RandomSource r = level.getRandom();
        Vec3 look = entity.getLookAngle();
        Vec3 mouth = entity.getEyePosition().add(look.scale(0.45D)).add(0.0D, -0.18D, 0.0D);
        for (int i = 0; i < BURST_CRUMBS; i++) {
            double vx = look.x * BURST_SPEED + (r.nextDouble() - 0.5D) * BURST_SPEED;
            double vy = (r.nextDouble() * 0.5D + 0.1D) * BURST_SPEED;
            double vz = look.z * BURST_SPEED + (r.nextDouble() - 0.5D) * BURST_SPEED;
            level.sendParticles(ModParticles.GOMU_BITE.get(), mouth.x, mouth.y, mouth.z, 0, vx, vy, vz, 1.0D);
        }
    }
}
