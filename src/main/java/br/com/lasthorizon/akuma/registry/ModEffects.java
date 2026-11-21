package br.com.lasthorizon.akuma.registry;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.effect.DizzyEffect;
import br.com.lasthorizon.akuma.effect.SeaCurseEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, LastHorizonMod.MOD_ID);

    /** "Vertigem do Diabo" - tontura temporária com náusea ao comer a fruta. */
    public static final RegistryObject<MobEffect> DIZZY =
            EFFECTS.register("dizzy", DizzyEffect::new);

    /** "Maldição do Mar" - infinita enquanto o usuário está na água. */
    public static final RegistryObject<MobEffect> SEA_CURSE =
            EFFECTS.register("sea_curse", SeaCurseEffect::new);

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
