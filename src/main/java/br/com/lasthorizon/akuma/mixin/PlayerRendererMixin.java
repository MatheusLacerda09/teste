package br.com.lasthorizon.akuma.mixin;

import br.com.lasthorizon.akuma.character.BodyScale;
import br.com.lasthorizon.akuma.character.CharacterCache;
import br.com.lasthorizon.akuma.character.CharacterData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Altura do personagem (criação de personagem): o modelo inteiro (cabeça, tronco, braços e pernas) cresce ou encolhe na MESMA proporção nos
 * três eixos, então a largura acompanha a altura e nada fica esticado. A conta é a do {@link BodyScale}. A escala vale só para o corpo: a
 * placa de nome é desenhada depois e não passa por aqui, então não distorce. A hitbox é ajustada à parte (CharacterEvents.onSize).
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
    private void lh$size(AbstractClientPlayer player, PoseStack pose, float partialTick, CallbackInfo ci) {
        CharacterData d = CharacterCache.get(player.getUUID());
        if (d == null) return;
        float s = BodyScale.of(d.height);
        if (s == 1.0F) return;
        pose.scale(s, s, s);
    }
}
