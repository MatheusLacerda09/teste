package br.com.lasthorizon.akuma.mixin;

import br.com.lasthorizon.akuma.client.IconAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Anima os ícones das conquistas: toda vez que o Minecraft vai desenhar um caractere, se ele for um dos 5 ícones
 * ({@code -}) troca pelo frame atual. Funciona em qualquer texto (balão da conquista, chat, tooltips).
 * Todos os frames têm a mesma largura, então o layout do texto não muda.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontIconAnimMixin {

    /** {@code accept(int posicao, Style estilo, int caractere)}: o caractere é a 3ª variável (índice 3, pois 0 é o {@code this}). */
    @ModifyVariable(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", at = @At("HEAD"), argsOnly = true, index = 3)
    private int lh$animateIcons(int codePoint) {
        return IconAnim.map(codePoint);
    }
}
