package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.LastHorizonMod;
import net.minecraftforge.api.distmarker.Dist;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos do cliente da criação de personagem. */
@Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CharacterClientEvents {
    private CharacterClientEvents() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) CharacterClient.onClientTick();
    }

    /** Jogador que aparece depois de o personagem dele já ter chegado: a hitbox nasce com a altura e a largura certas. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() && e.getEntity() instanceof net.minecraft.world.entity.player.Player p
                && br.com.lasthorizon.akuma.character.CharacterCache.get(p.getUUID()) != null) p.refreshDimensions();
    }

    // ---- sem UI até o jogador terminar o personagem ----

    /** Nada do HUD (hotbar, vida, mira, chat, F3...) enquanto a criação não termina. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Pre e) {
        if (CharacterClient.hideUi()) e.setCanceled(true);
    }

    /**
     * Sem HUD até o fim do selo; aí a barra de vida/fome/hotbar etc. sobem de baixo e aparecem aos poucos, junto com o braço
     * (mesmo progresso e mesma curva); a barra de chefe desce de cima.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (CharacterClient.hideUi()) {
            e.setCanceled(true);
            return;
        }
        float p = CharacterClient.handReveal();
        if (p < 0.0F || p >= 1.0F) return;
        ResourceLocation id = e.getOverlay().id();
        float dir = 0.0F;
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                || id.equals(VanillaGuiOverlay.JUMP_BAR.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id())) {
            dir = 1.0F;                       // vem de baixo
        } else if (id.equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) {
            dir = -1.0F;                      // vem de cima
        }
        e.getGuiGraphics().pose().pushPose();
        overlayPushed = true;
        if (dir != 0.0F) e.getGuiGraphics().pose().translate(0.0F, dir * 70.0F * uiRise(), 0.0F);
        float a = Math.max(0.0F, Math.min(1.0F, p * 1.6F));
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a * a * (3.0F - 2.0F * a));      // também aparece aos poucos (onde o jogo respeita)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onOverlayPost(RenderGuiOverlayEvent.Post e) {
        if (!overlayPushed) return;
        overlayPushed = false;
        e.getGuiGraphics().pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** O Pre desta classe empilhou a matriz do overlay atual (o Post desempilha). */
    private static boolean overlayPushed;

    /** 1 = escondido (fora da tela), 0 = no lugar, um pouco < 0 = passou e assenta: a mesma curva do braço. */
    private static float uiRise() {
        float p = CharacterClient.handReveal();
        if (p < 0.0F || p >= 1.0F) return 0.0F;
        float q = p - 1.0F, s = 1.70158F;
        return 1.0F - (1.0F + (s + 1.0F) * q * q * q + s * q * q);
    }

    /**
     * Nem a mão do jogador, até o fim do selo: aí o braço do personagem reaparece subindo de baixo (vindo um pouco do lado),
     * com um leve excesso no fim, enquanto a tela de finalização some.
     */
    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        float p = CharacterClient.handReveal();
        if (p < 0.0F) {
            if (CharacterClient.hideUi()) e.setCanceled(true);
            return;
        }
        if (p >= 1.0F || e.getHand() != InteractionHand.MAIN_HAND) return;     // a outra mão herda a mesma translação (mesma pilha)
        float rise = uiRise();                                                  // 1 = escondido embaixo, 0 = no lugar, <0 = passou um pouco
        float side = Minecraft.getInstance().options.mainHand().get() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        e.getPoseStack().translate(side * 0.35F * rise, -0.9F * rise, 0.0F);
    }

    /** Avisos (toasts de tutorial, conquistas...) são apagados antes de aparecer. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) CharacterClient.frame();      // a subida do braço/UI anda por quadro
        if (e.phase == TickEvent.Phase.START && CharacterClient.isLocked()) Minecraft.getInstance().getToasts().clear();
    }

    /** O jogador local fica parado enquanto cria (em servidor o mundo todo é congelado por CreationFreeze). */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() == mc.player && CharacterClient.isLocked()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        CharacterClient.reset();
    }
}
