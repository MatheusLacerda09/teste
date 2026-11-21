package com.mojang.blaze3d.systems;
/**
 * Stub só para o preview fora do Minecraft. Imita o estado de blend do OpenGL: no Minecraft 1.20.1 qualquer
 * GuiGraphics.fill() desliga o blend ao terminar (o RenderType do GUI faz disableBlend), então um blit com
 * transparência feito depois disso sai OPACO se o código não chamar enableBlend() antes.
 */
public final class RenderSystem {
    public static boolean blend = false;
    public static void enableBlend() { blend = true; }
    public static void disableBlend() { blend = false; }
    public static void defaultBlendFunc() {}
    public static float[] color = {1f, 1f, 1f, 1f};
    public static void setShaderColor(float r, float g, float b, float a) { color = new float[]{r, g, b, a}; }
}
