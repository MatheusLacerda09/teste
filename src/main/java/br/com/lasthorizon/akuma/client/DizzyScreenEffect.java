package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.registry.ModEffects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Faz a Vertigem do Diabo entortar a tela do mesmo jeito que a Náusea, mas sem precisar
 * da Náusea do jogo. O jogo entorta a tela usando {@code spinningEffectIntensity} do jogador
 * (sobe devagar, desce nos últimos 3 s). Aqui fazemos a mesma curva para a nossa Vertigem.
 * Só roda no cliente.
 */
@Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID, value = Dist.CLIENT)
public final class DizzyScreenEffect {
    private DizzyScreenEffect() {}

    /** Quanto a tela entorta a mais a cada tick (a Náusea normal usa 0.006). */
    public static final float RAMP_PER_TICK = 0.006F;
    /** Nos últimos ticks o jogo já diminui sozinho, então não mexemos (60 = 3 s). */
    public static final int FADE_TICKS = 60;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof LocalPlayer player)) return;

        MobEffectInstance dizzy = player.getEffect(ModEffects.DIZZY.get());
        if (dizzy == null || dizzy.getDuration() <= FADE_TICKS) return;

        player.spinningEffectIntensity = Math.min(1.0F, player.oSpinningEffectIntensity + RAMP_PER_TICK);
    }

    /**
     * Sem a Náusea, o jogo desenharia a tela roxa do portal do Nether junto com o giro.
     * Escondemos esse desenho enquanto a Vertigem estiver ativa.
     */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PORTAL.id())) return;
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.DIZZY.get()) && !player.hasEffect(MobEffects.CONFUSION)) {
            event.setCanceled(true);
        }
    }
}
