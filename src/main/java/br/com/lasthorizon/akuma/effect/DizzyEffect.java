package br.com.lasthorizon.akuma.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Vertigem do Diabo: tontura temporária (a náusea que entorta a tela é adicionada junto,
 * veja {@link br.com.lasthorizon.akuma.curse.GomuCurse#applyDizzy}).
 */
public class DizzyEffect extends MobEffect {
    public DizzyEffect() {
        super(MobEffectCategory.HARMFUL, 0x9B4FDB);
    }
}
