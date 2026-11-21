package br.com.lasthorizon.akuma.registry;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.item.GomuGomuNoMiItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LastHorizonMod.MOD_ID);

    public static final RegistryObject<Item> GOMU_GOMU_NO_MI = ITEMS.register(
            "gomu_fruit",
            () -> new GomuGomuNoMiItem(new Item.Properties().stacksTo(1))
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
