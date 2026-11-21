package br.com.lasthorizon.akuma;

import br.com.lasthorizon.akuma.character.CharacterStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dá a primeira conquista ("O Início da Jornada") e avisa no chat em azul para todos os jogadores (o chat padrão do
 * Minecraft forçaria verde). Quem está criando o personagem ganha só depois de terminar: o cliente avisa quando o
 * braço e a UI já reapareceram ({@link br.com.lasthorizon.akuma.character.CharacterNetwork.Revealed}) e aí o servidor chama
 * {@link #award}. Ao entrar no mundo, só ganha quem já tem personagem (e ainda não tinha a conquista).
 */
@Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID)
public final class JourneyAdvancementHandler {
    private static final ResourceLocation ROOT = new ResourceLocation(LastHorizonMod.MOD_ID, "root");
    private static final String CRITERION = "entrou_no_mundo";
    /** Cor da conquista no chat. Troque por ChatFormatting.WHITE se preferir branco. */
    private static final ChatFormatting CHAT_COLOR = ChatFormatting.AQUA;

    private JourneyAdvancementHandler() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (CharacterStorage.get(player) == null) return;     // ainda vai criar o personagem: a conquista vem depois
        award(player);
    }

    /** Dá a conquista (uma vez só) e avisa todos no chat. */
    public static void award(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Advancement advancement = server.getAdvancements().getAdvancement(ROOT);
        if (advancement == null || advancement.getDisplay() == null) return;
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) return;

        player.getAdvancements().award(advancement, CRITERION);

        DisplayInfo display = advancement.getDisplay();
        MutableComponent title = display.getTitle().copy().withStyle(CHAT_COLOR);
        MutableComponent hover = Component.empty()
                .append(title.copy())
                .append("\n")
                .append(display.getDescription());
        MutableComponent bracketed = ComponentUtils.wrapInSquareBrackets(title)
                .withStyle(style -> style
                        .withColor(CHAT_COLOR)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("chat.type.advancement.task", player.getDisplayName(), bracketed),
                false);
    }
}
