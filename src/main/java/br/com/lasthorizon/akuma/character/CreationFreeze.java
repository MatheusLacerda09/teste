package br.com.lasthorizon.akuma.character;

import br.com.lasthorizon.akuma.LastHorizonMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mundo parado enquanto ninguém terminou o personagem.
 *
 * <p>No jogo solo a própria tela de criação pausa o jogo ({@code isPauseScreen}). Num servidor (ou mundo aberto para
 * a rede) não dá para pausar de verdade, então, enquanto há jogadores online e NENHUM deles tem personagem
 * finalizado, o servidor congela o que dá: todos os seres vivos (mobs, animais, aldeões e os próprios jogadores) deixam
 * de agir e a hora do dia não anda. Assim que um jogador finaliza o personagem o mundo volta a rodar para todos.
 * Só roda no servidor e não toca em classes do cliente.
 */
@Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreationFreeze {
    private CreationFreeze() {}

    private static boolean frozen;
    private static final Map<ResourceKey<Level>, Long> DAY_TIME = new HashMap<>();

    /** O mundo está parado agora? */
    public static boolean isFrozen() {
        return frozen;
    }

    /** No começo de cada tick: congela se há jogadores online e nenhum com personagem pronto. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean anyDone = false;
        for (ServerPlayer p : players) {
            if (CharacterStorage.get(p) != null) {
                anyDone = true;
                break;
            }
        }
        boolean now = !players.isEmpty() && !anyDone;
        if (now && !frozen) {                                     // acabou de congelar: guarda a hora de cada dimensão
            DAY_TIME.clear();
            for (ServerLevel level : server.getAllLevels()) DAY_TIME.put(level.dimension(), level.getDayTime());
        } else if (!now && frozen) {
            DAY_TIME.clear();
        }
        frozen = now;
    }

    /** Seres vivos não agem enquanto o mundo está parado (só no servidor). */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent e) {
        if (frozen && !e.getEntity().level().isClientSide) e.setCanceled(true);
    }

    /** A hora do dia fica onde estava quando o mundo parou. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (!frozen || e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        Long t = DAY_TIME.get(level.dimension());
        if (t != null && level.getDayTime() != t) level.setDayTime(t);
    }

    /** Fechou o mundo/servidor: não deixa o estado vazar para o próximo. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        frozen = false;
        DAY_TIME.clear();
    }
}
