package br.com.lasthorizon.akuma.registry;

import br.com.lasthorizon.akuma.LastHorizonMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons do mod. Os arquivos .ogg e as variações ficam em assets/lasthorizon/sounds.json (gerados por tools/build_sounds.py). */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LastHorizonMod.MOD_ID);

    /** Mar quebrando (loop de 7,68 s = 2 ciclos de onda do fundo animado da aba de conquistas). */
    public static final RegistryObject<SoundEvent> SEA_WAVES = reg("ambient.sea_waves");
    /** Vento (loop de 16 s, com rajadas). */
    public static final RegistryObject<SoundEvent> SEA_WIND = reg("ambient.sea_wind");
    /** Passarinhos cantando (7 frases: pios, trinados, assobios, gorjeio), tocados quando um bando cruza a tela. */
    public static final RegistryObject<SoundEvent> BIRD = reg("bird.songbird");

    // Tela de criação de personagem (sintetizados por tools/build_ui_sounds.py; tocados só no cliente, sem posição no mundo).
    public static final RegistryObject<SoundEvent> UI_TICK = reg("ui.map_tick");
    public static final RegistryObject<SoundEvent> UI_PICK = reg("ui.map_pick");
    public static final RegistryObject<SoundEvent> UI_PAGE = reg("ui.map_page");
    public static final RegistryObject<SoundEvent> UI_ROLL = reg("ui.map_roll");
    public static final RegistryObject<SoundEvent> UI_CHIME = reg("ui.map_chime");
    public static final RegistryObject<SoundEvent> UI_SEAL = reg("ui.map_seal");
    public static final RegistryObject<SoundEvent> UI_DENY = reg("ui.map_deny");
    public static final RegistryObject<SoundEvent> UI_GEM = reg("ui.map_gem");
    public static final RegistryObject<SoundEvent> UI_OPEN = reg("ui.map_open");
    public static final RegistryObject<SoundEvent> UI_FLAP = reg("ui.map_flap");
    public static final RegistryObject<SoundEvent> UI_HOVER = reg("ui.map_hover");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(LastHorizonMod.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
