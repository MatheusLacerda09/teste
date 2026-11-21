package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.client.particle.GomuParticle;
import br.com.lasthorizon.akuma.client.particle.GomuParticleStyle;
import br.com.lasthorizon.akuma.item.GomuGomuNoMiItem;
import br.com.lasthorizon.akuma.registry.ModParticles;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientSetup {
    private ClientSetup() {}

    private static final ResourceLocation ROOT_ADVANCEMENT = new ResourceLocation(LastHorizonMod.MOD_ID, "root");
    private static java.lang.reflect.Field selectedTabField;
    private static boolean selectedTabSearched;

    /** A aba selecionada é a do mod? (se não der para descobrir, assume que sim) */
    public static boolean isOurTabSelected(AdvancementsScreen screen) {
        try {
            if (!selectedTabSearched) {
                selectedTabSearched = true;
                for (java.lang.reflect.Field f : AdvancementsScreen.class.getDeclaredFields()) {
                    if (f.getType().getSimpleName().equals("AdvancementTab")) {
                        f.setAccessible(true);
                        selectedTabField = f;
                        break;
                    }
                }
            }
            if (selectedTabField == null) return true;
            Object tab = selectedTabField.get(screen);
            if (tab == null) return false;
            for (java.lang.reflect.Field f : tab.getClass().getDeclaredFields()) {
                if (f.getType() == Advancement.class) {
                    f.setAccessible(true);
                    Object adv = f.get(tab);
                    return adv instanceof Advancement a && ROOT_ADVANCEMENT.equals(a.getId());
                }
            }
        } catch (Exception | LinkageError ignored) {
            // cai no padrão abaixo
        }
        return true;
    }

    /** Registro do componente de tooltip (barramento do mod). */
    @Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        /** Pré-carrega as texturas do fundo animado: assim a aba de conquistas abre sem travar na primeira vez. */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                var textures = Minecraft.getInstance().getTextureManager();
                for (ResourceLocation tex : SeaBackground.allTextures()) {
                    try {
                        textures.getTexture(tex);
                    } catch (Exception ignored) {
                        // se alguma falhar, ela carrega normalmente na hora de desenhar
                    }
                }
            });
        }

        @SubscribeEvent
        public static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(FruitTooltip.class, ClientFruitTooltip::new);
        }

        @SubscribeEvent
        public static void registerParticles(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.GOMU_BITE.get(), sprites -> new GomuParticle.Provider(sprites, GomuParticleStyle.BITE));
        }
    }

    /** Cores animadas de fundo/borda e posição FINAL do tooltip (barramento do Forge). */
    @Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onColor(RenderTooltipEvent.Color event) {
            if (!(event.getItemStack().getItem() instanceof GomuGomuNoMiItem)) {
                TooltipFrame.valid = false;
                return;
            }
            // fundo original do jogo; só a borda fica invisível (quem desenha é a moldura dourada)
            event.setBorderStart(0);
            event.setBorderEnd(0);

            int w = 0, h = 0;
            for (ClientTooltipComponent c : event.getComponents()) {
                w = Math.max(w, c.getWidth(event.getFont()));
                h += c.getHeight();
            }
            if (event.getComponents().size() > 1) h += 2;
            TooltipFrame.x = event.getX();
            TooltipFrame.y = event.getY();
            TooltipFrame.w = w;
            TooltipFrame.h = h;
            TooltipFrame.valid = true;
        }

        /** true enquanto o próprio mod fecha a tela (depois do fade-out), para o evento de fechar deixar passar. */
        private static boolean letCloseThrough;
        /** O fechamento (depois do fade-out) já foi agendado para o próximo tick. */
        private static boolean closeScheduled;

        /** Fundo animado (mar, sol e lua) na aba de conquistas do mod. */
        @SubscribeEvent
        public static void onAdvancementsBackground(ScreenEvent.BackgroundRendered event) {
            if (!(event.getScreen() instanceof AdvancementsScreen screen)) return;
            // só anima se esta for mesmo a tela aberta no momento (nada de desenhar/animar tela já fechada)
            if (Minecraft.getInstance().screen != screen) return;
            if (!isOurTabSelected(screen)) {
                SeaBackground.endFadeOut();   // trocou de aba no meio do fade-out: cancela
                closeScheduled = false;
                return;
            }
            // área interna da janela (alargada pelos mixins; ver WideAdvancements)
            int x = (screen.width - WideAdvancements.winW()) / 2 + WideAdvancements.BORDER_X;
            int y = (screen.height - WideAdvancements.winH()) / 2 + WideAdvancements.BORDER_TOP;
            SeaBackground.render(event.getGuiGraphics(), x, y, WideAdvancements.innerW(), WideAdvancements.innerH(), Util.getMillis());

            // fade-out terminou: agora sim fecha a tela (no próximo tick, fora do desenho)
            if (SeaBackground.fadeOutDone() && !closeScheduled) {
                closeScheduled = true;
                Minecraft mc = Minecraft.getInstance();
                mc.tell(() -> {
                    closeScheduled = false;
                    SeaBackground.endFadeOut();
                    if (mc.screen instanceof AdvancementsScreen) {
                        letCloseThrough = true;
                        try {
                            mc.setScreen(null);
                        } finally {
                            letCloseThrough = false;
                        }
                    }
                });
            }
        }

        /** A tela de conquistas fechou de verdade: para a animação do fundo e limpa todo o estado. */
        @SubscribeEvent
        public static void onScreenClosing(ScreenEvent.Closing event) {
            if (!(event.getScreen() instanceof AdvancementsScreen)) return;
            SeaBackground.stop();
            closeScheduled = false;
        }

        /**
         * Fade-out ao fechar: quando o jogador fecha a tela de conquistas (ESC ou a tecla de conquistas) com a aba do
         * mod aberta, o fechamento é adiado por {@link SeaBackground#FADE_OUT_MS} enquanto o cenário escurece.
         * Só vale para fechar de volta ao jogo (nova tela = null); morrer, desconectar etc. passam direto.
         */
        @SubscribeEvent
        public static void onScreenOpening(ScreenEvent.Opening event) {
            if (letCloseThrough || SeaBackground.FADE_OUT_MS <= 0L) return;
            if (event.getNewScreen() != null) return;
            if (!(event.getCurrentScreen() instanceof AdvancementsScreen screen)) return;
            if (!isOurTabSelected(screen)) return;
            event.setCanceled(true);
            SeaBackground.beginFadeOut();
        }

        private static int lerp(int a, int b, double f) {
            int r = 0;
            for (int shift = 24; shift >= 0; shift -= 8) {
                int ca = a >> shift & 0xFF, cb = b >> shift & 0xFF;
                r |= ((int) Math.round(ca + (cb - ca) * f) & 0xFF) << shift;
            }
            return r;
        }
    }
}
