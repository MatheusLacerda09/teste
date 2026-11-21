package br.com.lasthorizon.akuma.curse;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A maldição da Gomu Gomu no Mi:
 * <ul>
 *   <li>O jogador que comeu a fruta fica MARCADO para sempre (a marca sobrevive à morte).</li>
 *   <li><b>Maldição do Mar</b> ({@code sea_curse}): efeito de duração infinita, ligado só enquanto
 *       está na água. O nível dele (I/II/III) diz a profundidade e é ele que comanda a física
 *       nos dois lados (cliente e servidor).</li>
 *   <li><b>Vertigem do Diabo</b> ({@code dizzy}): tontura TEMPORÁRIA com náusea de verdade
 *       (tela zonza). Vem só ao comer a fruta.</li>
 *   <li>No fundo do mar (25 s seguidos) vem só a <b>Náusea</b> normal do jogo, sem a Vertigem.</li>
 * </ul>
 * Todos os números para ajustar estão nas constantes abaixo.
 */
@Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID)
public final class GomuCurse {
    private GomuCurse() {}

    // ---------- Profundidade (em blocos de água acima dos pés do jogador) ----------
    /** A partir daqui: nível I - metade do corpo na água (~1 bloco). */
    public static final double DEPTH_HALF = 0.5D;
    /** A partir daqui: nível II - corpo todo na água (~2 blocos). */
    public static final double DEPTH_FULL = 1.5D;
    /** A partir daqui: nível III - fundo, é puxado para baixo (mais de 2 blocos). */
    public static final double DEPTH_SINK = 2.3D;
    /** Folga para mudar de nível (evita o ícone ficar trocando quando está na beirada). */
    public static final double HYSTERESIS = 0.25D;
    /** Ticks fora da água antes de tirar o efeito do mar (evita piscar ao pular na superfície). */
    public static final int DRY_GRACE_TICKS = 15;

    // ---------- Lentidão: multiplicador de velocidade horizontal por tick (menor = mais lento) ----------
    public static final double SLOW_HALF = 0.85D;
    public static final double SLOW_FULL = 0.70D;
    public static final double SLOW_SINK = 0.55D;
    /** Nível II: velocidade máxima para cima (nadar para subir fica bem difícil). */
    public static final double FULL_MAX_UP = 0.05D;
    /** Nível III: velocidade com que o mar puxa para baixo (blocos por tick). */
    public static final double SINK_SPEED = 0.07D;

    // ---------- Tontura (20 ticks = 1 segundo) ----------
    /** Duração da tontura ao comer a fruta. */
    public static final int EAT_DIZZY_TICKS = 100;       // 5 s
    /** Tempo seguido no fundo do mar até vir a tontura. */
    public static final int DEEP_SEA_TICKS = 500;        // 25 s
    /** Duração da náusea causada pelo fundo do mar. */
    public static final int DEEP_NAUSEA_TICKS = 200;     // 10 s

    /** Marca guardada no jogador (copiada ao morrer). */
    public static final String TAG = "LastHorizonUser";
    private static final String DEEP_KEY = "LastHorizonDeepTicks";
    private static final String DRY_KEY = "LastHorizonDryTicks";

    /** Duração "infinita" de efeito (MobEffectInstance.INFINITE_DURATION). */
    private static final int INFINITE = -1;

    // ---------- API ----------

    public static boolean isMarked(Player player) {
        return player.getPersistentData().getBoolean(TAG);
    }

    /** Chamado no servidor quando o jogador termina de comer a fruta. */
    public static void grant(ServerPlayer player) {
        player.getPersistentData().putBoolean(TAG, true);
        applyDizzy(player, EAT_DIZZY_TICKS);
    }

    /** Chamado pelo comando /devilfruit revoke: tira a marca e os efeitos da maldição. */
    public static void revoke(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(TAG);
        data.remove(DEEP_KEY);
        data.remove(DRY_KEY);
        player.removeEffect(ModEffects.SEA_CURSE.get());
        player.removeEffect(ModEffects.DIZZY.get());
    }

    /**
     * Vertigem do Diabo: UM efeito só. Quem entorta a tela é o cliente
     * ({@link br.com.lasthorizon.akuma.client.DizzyScreenEffect}), então não há Náusea junto.
     */
    public static void applyDizzy(Player player, int ticks) {
        player.addEffect(new MobEffectInstance(ModEffects.DIZZY.get(), ticks, 0, false, false, true));
    }

    /** Só a náusea normal do jogo (com ícone), sem a Vertigem do Diabo. */
    public static void applyNausea(Player player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, 0, false, false, true));
    }

    /** Quantos blocos de água há do pé do jogador até a superfície (0 se o pé está fora da água). */
    public static double waterDepth(Player player) {
        Level level = player.level();
        BlockPos feet = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        if (!level.getFluidState(feet).is(FluidTags.WATER)) {
            return 0.0D;
        }
        BlockPos.MutableBlockPos pos = feet.mutable();
        int guard = 0;
        while (guard++ < 64 && level.getFluidState(pos.above()).is(FluidTags.WATER)) {
            pos.move(Direction.UP);
        }
        FluidState top = level.getFluidState(pos);
        return pos.getY() + top.getHeight(level, pos) - player.getY();
    }

    /** 0 = fora da água, 1 = metade do corpo, 2 = corpo todo, 3 = fundo. */
    public static int stageFor(double depth) {
        if (depth >= DEPTH_SINK) return 3;
        if (depth >= DEPTH_FULL) return 2;
        if (depth >= DEPTH_HALF) return 1;
        return 0;
    }

    /** Igual a {@link #stageFor(double)}, mas só desce de nível se passar da folga. */
    private static int stageWithHysteresis(double depth, int currentStage) {
        int raw = stageFor(depth);
        if (raw < currentStage) {
            double threshold = currentStage == 3 ? DEPTH_SINK : currentStage == 2 ? DEPTH_FULL : DEPTH_HALF;
            if (depth >= threshold - HYSTERESIS) return currentStage;
        }
        return raw;
    }

    // ---------- Eventos ----------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        boolean client = player.level().isClientSide;
        // No cliente só mexemos no jogador local (os outros são só desenhados).
        if (client && !player.isLocalPlayer()) return;

        if (!client && isMarked(player)) {
            serverUpkeep(player);
        }

        // A física é comandada pelo efeito do mar (sincronizado com o cliente).
        MobEffectInstance sea = player.getEffect(ModEffects.SEA_CURSE.get());
        if (sea == null) return;
        if (player.isSpectator() || player.getAbilities().flying || player.isPassenger()) return;
        if (waterDepth(player) < 0.1D) return;

        int stage = Math.max(1, Math.min(3, sea.getAmplifier() + 1));
        applyWaterPhysics(player, stage);
    }

    /** Só no servidor: liga/desliga o efeito do mar e conta o tempo no fundo. */
    private static void serverUpkeep(Player player) {
        CompoundTag data = player.getPersistentData();
        boolean exempt = player.isSpectator() || player.getAbilities().flying;

        MobEffectInstance current = player.getEffect(ModEffects.SEA_CURSE.get());
        int currentStage = current == null ? 0 : current.getAmplifier() + 1;
        int stage = exempt ? 0 : stageWithHysteresis(waterDepth(player), currentStage);

        // --- efeito do mar: duração infinita (não pisca) e só troca quando o nível muda ---
        if (stage > 0) {
            data.putInt(DRY_KEY, 0);
            if (stage != currentStage) {
                if (current != null) player.removeEffect(ModEffects.SEA_CURSE.get());
                player.addEffect(new MobEffectInstance(ModEffects.SEA_CURSE.get(), INFINITE, stage - 1, false, false, true));
            }
        } else if (current != null) {
            int dry = data.getInt(DRY_KEY) + 1;
            data.putInt(DRY_KEY, dry);
            if (dry >= DRY_GRACE_TICKS) {
                player.removeEffect(ModEffects.SEA_CURSE.get());
                data.putInt(DRY_KEY, 0);
            }
        }

        // --- 25 s seguidos no fundo do mar => só náusea ---
        if (stage == 3) {
            int deep = data.getInt(DEEP_KEY) + 1;
            if (deep >= DEEP_SEA_TICKS) {
                applyNausea(player, DEEP_NAUSEA_TICKS);
                deep = 0;
            }
            data.putInt(DEEP_KEY, deep);
        } else if (data.getInt(DEEP_KEY) != 0) {
            data.putInt(DEEP_KEY, 0);
        }
    }

    private static void applyWaterPhysics(Player player, int stage) {
        Vec3 v = player.getDeltaMovement();
        double slow = stage == 1 ? SLOW_HALF : stage == 2 ? SLOW_FULL : SLOW_SINK;
        double y = v.y;
        if (stage == 2) {
            y = Math.min(y, FULL_MAX_UP);
        } else if (stage == 3) {
            y = -SINK_SPEED;
        }
        player.setDeltaMovement(v.x * slow, y, v.z * slow);
        player.setSprinting(false);
    }

    /** A marca da fruta sobrevive à morte. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(TAG)) {
            event.getEntity().getPersistentData().putBoolean(TAG, true);
        }
    }
}
