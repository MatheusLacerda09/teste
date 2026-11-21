package br.com.lasthorizon.akuma.command;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.character.CharacterData;
import br.com.lasthorizon.akuma.character.CharacterDestiny;
import br.com.lasthorizon.akuma.character.CharacterEvents;
import br.com.lasthorizon.akuma.character.CharacterOptions;
import br.com.lasthorizon.akuma.character.CharacterStorage;
import br.com.lasthorizon.akuma.curse.GomuCurse;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Commands of the mod (all in English; the feedback text is translatable in the lang files).
 * <pre>
 *   /sheet                         show your own character sheet
 *   /sheet &lt;player&gt;                (op) show another player's character sheet
 *   /character reset &lt;player&gt;      (op) erase the character and reopen character creation
 *   /devilfruit grant &lt;player&gt;      (op) mark the player as a Devil Fruit user, as if they ate the fruit
 *   /devilfruit revoke &lt;player&gt;     (op) remove the mark and the sea curse effects
 *   /devilfruit status &lt;player&gt;     (op) tell whether the player carries the mark
 * </pre>
 * "op" = permission level 2.
 */
@Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID)
public final class ModCommands {
    private ModCommands() {}

    private static final int OP = 2;
    private static final String CHAR = "character.lasthorizon.";
    private static final String CMD = "commands.lasthorizon.";

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();

        d.register(Commands.literal("sheet")
                .executes(ctx -> sheet(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(s -> s.hasPermission(OP))
                        .executes(ctx -> sheet(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));

        d.register(Commands.literal("character").requires(s -> s.hasPermission(OP))
                .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            CharacterEvents.reset(target);
                            ctx.getSource().sendSuccess(() -> Component.translatable(CHAR + "reset", target.getDisplayName()), true);
                            return 1;
                        }))));

        d.register(Commands.literal("devilfruit").requires(s -> s.hasPermission(OP))
                .then(Commands.literal("grant").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            GomuCurse.grant(target);
                            ctx.getSource().sendSuccess(() -> Component.translatable(CMD + "devilfruit.grant", target.getDisplayName()), true);
                            return 1;
                        })))
                .then(Commands.literal("revoke").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            GomuCurse.revoke(target);
                            ctx.getSource().sendSuccess(() -> Component.translatable(CMD + "devilfruit.revoke", target.getDisplayName()), true);
                            return 1;
                        })))
                .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            boolean marked = GomuCurse.isMarked(target);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    CMD + (marked ? "devilfruit.status.marked" : "devilfruit.status.clean"), target.getDisplayName()), false);
                            return marked ? 1 : 0;
                        }))));
    }

    /** Writes the character sheet to whoever ran the command (the owner, or the admin looking at someone else). */
    private static int sheet(CommandSourceStack src, ServerPlayer owner) {
        CharacterData d = CharacterStorage.get(owner);
        if (d == null) {
            src.sendFailure(Component.translatable(CHAR + (src.getEntity() == owner ? "sheet.none" : "sheet.none.other"), owner.getDisplayName()));
            return 0;
        }
        String k = CHAR;
        CharacterDestiny dest = CharacterDestiny.of(d);
        send(src, Component.translatable(k + "sheet.title", d.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!d.epithet.isEmpty()) send(src, Component.literal("— " + d.epithet).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        send(src, Component.translatable(k + "sheet.basic", d.age,
                Component.translatable(k + "age.phase." + CharacterOptions.agePhase(d.age)),
                Component.translatable(k + "sea." + d.sea)).withStyle(ChatFormatting.YELLOW));
        send(src, Component.translatable(k + "sheet.soul", Component.translatable(k + dest.key())));
        send(src, Component.translatable(k + dest.strengthKey()).withStyle(ChatFormatting.GREEN));
        send(src, Component.translatable(k + dest.flawKey()).withStyle(ChatFormatting.RED));
        if (!d.motto.isEmpty()) send(src, Component.literal("“" + d.motto + "”").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        return 1;
    }

    private static void send(CommandSourceStack src, Component c) {
        src.sendSuccess(() -> c, false);
    }
}
