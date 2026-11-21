package br.com.lasthorizon.akuma.mixin;

import br.com.lasthorizon.akuma.client.WideAdvancements;
import br.com.lasthorizon.akuma.client.ClientSetup;
import br.com.lasthorizon.akuma.client.SeaFrame;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Alarga a tela de conquistas. O vanilla escreve o tamanho (252 x 140, interior 234 x 113) direto no código;
 * aqui cada número é trocado pelo tamanho de {@link WideAdvancements}, e a moldura (window.png) é montada
 * em 9 pedaços para esticar sem borrar.
 */
@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin {

    // ---- posição da janela (centralizada): render() e mouseClicked() ----

    @ModifyConstant(method = "render", constant = @Constant(intValue = 252))
    private int lh$renderW(int original) {
        return WideAdvancements.winW();
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 140))
    private int lh$renderH(int original) {
        return WideAdvancements.winH();
    }

    @ModifyConstant(method = "mouseClicked", constant = @Constant(intValue = 252))
    private int lh$clickW(int original) {
        return WideAdvancements.winW();
    }

    @ModifyConstant(method = "mouseClicked", constant = @Constant(intValue = 140))
    private int lh$clickH(int original) {
        return WideAdvancements.winH();
    }

    // ---- área interna: fundo preto "sem conquistas" e textos centralizados ----

    @ModifyConstant(method = "renderInside", constant = @Constant(intValue = 234))
    private int lh$insideW(int original) {
        return WideAdvancements.innerW();
    }

    @ModifyConstant(method = "renderInside", constant = @Constant(intValue = 113))
    private int lh$insideH(int original) {
        return WideAdvancements.innerH();
    }

    @ModifyConstant(method = "renderInside", constant = @Constant(intValue = 117))
    private int lh$insideCx(int original) {
        return WideAdvancements.innerW() / 2;
    }

    @ModifyConstant(method = "renderInside", constant = @Constant(intValue = 56))
    private int lh$insideCy(int original) {
        return WideAdvancements.innerH() / 2;
    }

    // ---- moldura dourada do mod: por cima da moldura cinza, só na aba Sea Sovereign ----

    @Inject(method = "renderWindow", at = @At("TAIL"))
    private void lh$goldFrame(GuiGraphics g, int x, int y, CallbackInfo ci) {
        if (!ClientSetup.isOurTabSelected((AdvancementsScreen) (Object) this)) return;
        SeaFrame.render(g, x + WideAdvancements.BORDER_X, y + WideAdvancements.BORDER_TOP,
                WideAdvancements.innerW(), WideAdvancements.innerH(), Util.getMillis());
    }

    // ---- moldura: o vanilla desenha window.png (252 x 140); aqui ela é esticada em 9 pedaços ----

    @Redirect(method = "renderWindow",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void lh$frame(GuiGraphics g, ResourceLocation tex, int x, int y, int u, int v, int w, int h) {
        int ww = WideAdvancements.winW(), wh = WideAdvancements.winH();
        if (ww == WideAdvancements.VANILLA_W && wh == WideAdvancements.VANILLA_H) {
            g.blit(tex, x, y, u, v, w, h);
            return;
        }
        final int bx = WideAdvancements.BORDER_X, bt = WideAdvancements.BORDER_TOP, bb = WideAdvancements.BORDER_BOTTOM;
        final int srcMidW = WideAdvancements.VANILLA_W - 2 * bx;               // 234
        final int srcMidH = WideAdvancements.VANILLA_H - bt - bb;              // 113
        final int srcRight = WideAdvancements.VANILLA_W - bx;                  // 243
        final int srcBottom = WideAdvancements.VANILLA_H - bb;                 // 131
        final int midW = ww - 2 * bx, midH = wh - bt - bb;
        // cantos
        g.blit(tex, x, y, 0, 0, bx, bt);
        g.blit(tex, x + ww - bx, y, srcRight, 0, bx, bt);
        g.blit(tex, x, y + wh - bb, 0, srcBottom, bx, bb);
        g.blit(tex, x + ww - bx, y + wh - bb, srcRight, srcBottom, bx, bb);
        // bordas de cima e de baixo (repetem pedaços de até 234 px)
        for (int dx = 0; dx < midW; dx += srcMidW) {
            int cw = Math.min(srcMidW, midW - dx);
            g.blit(tex, x + bx + dx, y, bx, 0, cw, bt);
            g.blit(tex, x + bx + dx, y + wh - bb, bx, srcBottom, cw, bb);
        }
        // bordas laterais (repetem pedaços de até 113 px)
        for (int dy = 0; dy < midH; dy += srcMidH) {
            int ch = Math.min(srcMidH, midH - dy);
            g.blit(tex, x, y + bt + dy, 0, bt, bx, ch);
            g.blit(tex, x + ww - bx, y + bt + dy, srcRight, bt, bx, ch);
        }
    }
}
