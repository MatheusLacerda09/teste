package br.com.lasthorizon.akuma.registry;

import br.com.lasthorizon.akuma.LastHorizonMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LastHorizonMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> AKUMA_NO_MI = TABS.register(
            "akuma_no_mi",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lasthorizon.akuma_no_mi"))
                    .icon(() -> new ItemStack(ModItems.GOMU_GOMU_NO_MI.get()))
                    .displayItems((parameters, output) -> output.accept(ModItems.GOMU_GOMU_NO_MI.get()))
                    .build()
    );

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
