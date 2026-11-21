package br.com.lasthorizon.akuma.mixin;

import br.com.lasthorizon.akuma.client.WideAdvancements;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Balão (hover) de uma conquista: decide se abre para cima ou para baixo usando a altura real da área interna. */
@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin {

    @ModifyConstant(method = "drawHover", constant = @Constant(intValue = 113))
    private int lh$hoverH(int original) {
        return WideAdvancements.innerH();
    }
}
