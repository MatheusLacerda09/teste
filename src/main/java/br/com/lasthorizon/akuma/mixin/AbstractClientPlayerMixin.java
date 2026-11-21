package br.com.lasthorizon.akuma.mixin;

import br.com.lasthorizon.akuma.character.CharacterCache;
import br.com.lasthorizon.akuma.character.CharacterData;
import br.com.lasthorizon.akuma.character.SkinPainter;
import br.com.lasthorizon.akuma.client.CharacterClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Troca a skin do jogador pela gerada na criação de personagem (se ele tiver uma), tanto no corpo, para quem vê, quanto
 * na mão em primeira pessoa. O modelo é "default" (braços de 4 px) ou "slim" (3 px), sempre o formato da skin gerada (lido do PNG base).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void lh$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation tex = CharacterClient.textureFor(((AbstractClientPlayer) (Object) this).getUUID());
        if (tex != null) cir.setReturnValue(tex);
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void lh$model(CallbackInfoReturnable<String> cir) {
        java.util.UUID id = ((AbstractClientPlayer) (Object) this).getUUID();
        if (CharacterClient.textureFor(id) == null) return;
        CharacterData d = CharacterCache.get(id);
        if (d == null) return;
        cir.setReturnValue(SkinPainter.isSlim(d) ? "slim" : "default");   // o modelo segue o formato do PNG base (braço de 3 ou 4 px)
    }
}
