package br.com.lasthorizon.akuma.item;

import br.com.lasthorizon.akuma.client.FruitTooltip;
import br.com.lasthorizon.akuma.curse.GomuCurse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Item consumível da Gomu Gomu no Mi. */
public class GomuGomuNoMiItem extends Item {

    public GomuGomuNoMiItem(Properties properties) {
        super(properties.food(new net.minecraft.world.food.FoodProperties.Builder()
                .nutrition(2)
                .saturationMod(0.2F)
                .alwaysEat()
                .build()));
    }

    /** Nome em dourado e NEGRITO. */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static Component t(String key, ChatFormatting... styles) {
        return Component.translatable("tooltip.lasthorizon." + key).withStyle(styles);
    }

    /** Toda a descrição fica abaixo do nome: etiquetas, ícone, texto e vantagens/desvantagens. */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new FruitTooltip(List.of(
                FruitTooltip.Entry.badges(
                        new FruitTooltip.Badge(t("tag.devil_fruit", ChatFormatting.BOLD), 0xFF8E3FD1, 0),
                        new FruitTooltip.Badge(t("tag.paramecia", ChatFormatting.BOLD), 0xFFE8841A, 1),
                        new FruitTooltip.Badge(t("tag.legendary", ChatFormatting.BOLD), 0xFFD9A100, 2)),
                FruitTooltip.Entry.line(FruitTooltip.Icon.NONE, t("gomu_fruit", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)),
                FruitTooltip.Entry.line(FruitTooltip.Icon.RUBBER, t("rubber_body", ChatFormatting.LIGHT_PURPLE)),
                FruitTooltip.Entry.gap(4),
                FruitTooltip.Entry.line(FruitTooltip.Icon.NONE, t("fruit_hint", ChatFormatting.GRAY)),
                FruitTooltip.Entry.gap(3),
                FruitTooltip.Entry.line(FruitTooltip.Icon.UP, t("pro_elasticity", ChatFormatting.GOLD)),
                FruitTooltip.Entry.line(FruitTooltip.Icon.UP, t("pro_range", ChatFormatting.GOLD)),
                FruitTooltip.Entry.line(FruitTooltip.Icon.DOWN, t("con_swim", ChatFormatting.GOLD)),
                FruitTooltip.Entry.line(FruitTooltip.Icon.DOWN, t("con_dizzy", ChatFormatting.GOLD)),
                FruitTooltip.Entry.gap(2))));
    }

    /** Migalhas saindo da boca enquanto o jogador mastiga (roda nos dois lados; só o cliente desenha). */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide && remainingUseDuration % GomuEatingEffects.CHEW_INTERVAL_TICKS == 0) {
            GomuEatingEffects.chew(level, entity);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (level instanceof ServerLevel serverLevel) {
            GomuEatingEffects.finish(serverLevel, entity);
        }
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            // Maldição da fruta: marca o jogador, dá a Vertigem do Diabo (temporária) e liga a Maldição do Mar na água.
            GomuCurse.grant(serverPlayer);
        }
        if (!level.isClientSide && entity instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable("message.lasthorizon.fruit_eaten")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                    true);
        }
        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return super.use(level, player, hand);
    }
}
