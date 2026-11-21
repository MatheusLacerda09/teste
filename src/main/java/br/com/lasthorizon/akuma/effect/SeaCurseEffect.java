package br.com.lasthorizon.akuma.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Maldição do Mar: aparece enquanto o usuário da fruta está na água.
 * Nível I = metade do corpo na água (lento), nível II = corpo todo (mais lento),
 * nível III = fundo (puxado para baixo). A física está em
 * {@link br.com.lasthorizon.akuma.curse.GomuCurse}.
 */
public class SeaCurseEffect extends MobEffect {
    public SeaCurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E7BD6);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return List.of();
    }
}
