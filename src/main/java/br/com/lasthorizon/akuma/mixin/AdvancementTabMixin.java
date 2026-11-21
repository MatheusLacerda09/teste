package br.com.lasthorizon.akuma.mixin;

import br.com.lasthorizon.akuma.client.WideAdvancements;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Conteúdo de uma aba: recorte, rolagem, centralização e quantos ladrilhos de fundo (16 x 16) desenhar,
 * tudo com o tamanho de {@link WideAdvancements} em vez de 234 x 113.
 */
@Mixin(AdvancementTab.class)
public abstract class AdvancementTabMixin {

    // ---- drawContents: recorte, centralização e ladrilhos do fundo ----

    @ModifyConstant(method = "drawContents", constant = @Constant(intValue = 234))
    private int lh$contentW(int original) {
        return WideAdvancements.innerW();
    }

    @ModifyConstant(method = "drawContents", constant = @Constant(intValue = 113))
    private int lh$contentH(int original) {
        return WideAdvancements.innerH();
    }

    @ModifyConstant(method = "drawContents", constant = @Constant(intValue = 117))
    private int lh$centerX(int original) {
        return WideAdvancements.innerW() / 2;
    }

    @ModifyConstant(method = "drawContents", constant = @Constant(intValue = 56))
    private int lh$centerY(int original) {
        return WideAdvancements.innerH() / 2;
    }

    /** Ladrilhos na horizontal: vanilla desenha de -1 até 15 (234 / 16 + 1). */
    @ModifyConstant(method = "drawContents", constant = @Constant(intValue = 15))
    private int lh$tilesX(int original) {
        return WideAdvancements.innerW() / 16 + 1;
    }

    /** Ladrilhos na vertical: vanilla desenha de -1 até 8 (113 / 16 + 1). */
    @ModifyConstant(method = "drawContents", constant = @Constant(intValue = 8))
    private int lh$tilesY(int original) {
        return WideAdvancements.innerH() / 16 + 1;
    }

    // ---- drawTooltips: escurecimento ao passar o mouse e área onde o mouse conta ----

    @ModifyConstant(method = "drawTooltips", constant = @Constant(intValue = 234))
    private int lh$tipW(int original) {
        return WideAdvancements.innerW();
    }

    @ModifyConstant(method = "drawTooltips", constant = @Constant(intValue = 113))
    private int lh$tipH(int original) {
        return WideAdvancements.innerH();
    }

    // ---- scroll: limites de arrastar ----

    @ModifyConstant(method = "scroll", constant = @Constant(intValue = 234))
    private int lh$scrollW(int original) {
        return WideAdvancements.innerW();
    }

    @ModifyConstant(method = "scroll", constant = @Constant(intValue = 113))
    private int lh$scrollH(int original) {
        return WideAdvancements.innerH();
    }
}
