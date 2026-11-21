package br.com.lasthorizon.akuma.registry;

import br.com.lasthorizon.akuma.LastHorizonMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, LastHorizonMod.MOD_ID);

    /** Pedaços da fruta mordida (migalhas amarelo-esverdeadas e de casca roxa). */
    public static final RegistryObject<SimpleParticleType> GOMU_BITE =
            PARTICLES.register("gomu_bite", () -> new SimpleParticleType(false));


    public static void register(IEventBus eventBus) {
        PARTICLES.register(eventBus);
    }
}
