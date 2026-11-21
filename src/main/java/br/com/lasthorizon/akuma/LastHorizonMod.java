package br.com.lasthorizon.akuma;

import br.com.lasthorizon.akuma.character.CharacterNetwork;
import br.com.lasthorizon.akuma.registry.ModCreativeTabs;
import br.com.lasthorizon.akuma.registry.ModEffects;
import br.com.lasthorizon.akuma.registry.ModItems;
import br.com.lasthorizon.akuma.registry.ModParticles;
import br.com.lasthorizon.akuma.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(LastHorizonMod.MOD_ID)
public class LastHorizonMod {
    public static final String MOD_ID = "lasthorizon";

    public LastHorizonMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modEventBus);
        ModEffects.register(modEventBus);
        ModParticles.register(modEventBus);
        ModSounds.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(LastHorizonMod::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CharacterNetwork::register);
    }
}
