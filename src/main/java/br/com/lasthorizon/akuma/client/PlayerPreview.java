package br.com.lasthorizon.akuma.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Desenha o boneco 3D do retrato da criação de personagem: o próprio jogador (modelo e skin de verdade, feitos pelo
 * jogo) girado {@code spinDeg} graus em torno do eixo vertical e com a <b>cabeça seguindo o mouse</b>. Só a cabeça: o
 * corpo fica onde o jogador o deixou (o giro por arrastar mora no {@link CharacterMap}), e o pescoço tem limite, então
 * com o corpo de lado ou de costas a cabeça vira o máximo que der em direção ao mouse.
 *
 * <p>O estado do corpo e da cabeça do jogador é emprestado só durante o desenho e devolvido em seguida.
 * Só existe no cliente.
 */
public final class PlayerPreview {
    private static final float HEAD_YAW_MAX = 70.0F;       // quanto a cabeça gira em relação ao corpo
    private static final float HEAD_PITCH_MAX = 30.0F;

    private float headYaw, headPitch;                      // pose da cabeça (suavizada): relativa ao corpo / ao horizonte
    private long lastFrame;

    /**
     * Desenha o jogador com os pés em ({@code cx}, {@code feetY}).
     *
     * @param spinDeg giro do corpo: 0 = de frente para a tela, positivo = a frente vira para a DIREITA da tela
     * @param mx      mouse, nas mesmas coordenadas do desenho (espaço do mapa)
     */
    public void render(GuiGraphics g, LivingEntity e, int cx, int feetY, int scale, int mx, int my, float spinDeg) {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.0F : Mth.clamp((now - lastFrame) / 1.0E9F, 0.0F, 0.1F);
        lastFrame = now;

        // Na convenção do jogo (yBodyRot = 180 + a) "a" positivo vira o corpo para a ESQUERDA da tela; o giro do mapa é o oposto.
        float bodyYaw = -spinDeg;
        // para onde o mouse está, visto do boneco, na mesma convenção (positivo = esquerda da tela)
        float dist = Math.max(30.0F, scale * 0.9F);
        float eyeY = feetY - scale * 1.62F;
        float lookYaw = (float) Math.toDegrees(Math.atan((cx - mx) / dist));
        float lookPitch = (float) Math.toDegrees(Math.atan((my - eyeY) / dist)) * 0.55F;   // positivo = olhar para baixo
        float wantYaw = Mth.clamp(Mth.wrapDegrees(lookYaw - bodyYaw), -HEAD_YAW_MAX, HEAD_YAW_MAX);
        float wantPitch = Mth.clamp(lookPitch, -HEAD_PITCH_MAX, HEAD_PITCH_MAX);
        float k = 1.0F - (float) Math.exp(-dt * 16.0F);
        headYaw += (wantYaw - headYaw) * k;
        headPitch += (wantPitch - headPitch) * k;

        draw(g, e, cx, feetY, scale, 180.0F + bodyYaw);
    }

    private void draw(GuiGraphics g, LivingEntity e, int cx, int feetY, int scale, float bodyRot) {
        float headRot = bodyRot + headYaw;
        float oBody = e.yBodyRot, oBodyO = e.yBodyRotO, oHead = e.yHeadRot, oHeadO = e.yHeadRotO;
        float oYRot = e.getYRot(), oYRotO = e.yRotO, oXRot = e.getXRot(), oXRotO = e.xRotO;
        e.yBodyRot = bodyRot;
        e.yBodyRotO = bodyRot;
        e.yHeadRot = headRot;
        e.yHeadRotO = headRot;
        e.setYRot(headRot);
        e.yRotO = headRot;
        e.setXRot(headPitch);
        e.xRotO = headPitch;
        try {
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);   // sem isto o modelo sairia de cabeça para baixo
            float alpha = MapArt.baseAlpha();
            if (alpha >= 0.995F) {
                InventoryScreen.renderEntityInInventory(g, cx, feetY, scale, pose, new Quaternionf(), e);
            } else {
                renderFaded(g, e, cx, feetY, scale, pose, alpha);      // no fade final o boneco também some, junto com o resto da tela
            }
        } finally {
            e.yBodyRot = oBody;
            e.yBodyRotO = oBodyO;
            e.yHeadRot = oHead;
            e.yHeadRotO = oHeadO;
            e.setYRot(oYRot);
            e.yRotO = oYRotO;
            e.setXRot(oXRot);
            e.xRotO = oXRotO;
        }
    }

    // ------------------------------------------------------------------------------------------ fade do boneco

    /**
     * Igual ao {@code InventoryScreen.renderEntityInInventory} do jogo, mas com o modelo translúcido: o boneco usa render
     * types sem mistura de cores (e por isso ignora o alfa da tela), então cada um vira uma cópia com blend ligado e o
     * alfa entra na cor de cada vértice.
     */
    private static void renderFaded(GuiGraphics g, LivingEntity e, int x, int y, int scale, Quaternionf pose, float alpha) {
        g.flush();                                   // o que veio antes sai com a cor normal
        g.pose().pushPose();
        g.pose().translate((double) x, (double) y, 50.0D);
        g.pose().mulPoseMatrix(new Matrix4f().scaling((float) scale, (float) scale, (float) -scale));
        g.pose().mulPose(pose);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf cam = new Quaternionf();
        cam.conjugate();
        dispatcher.overrideCameraOrientation(cam);
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource real = g.bufferSource();
        MultiBufferSource faded = type -> new AlphaConsumer(real.getBuffer(fadedType(type)), alpha);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);          // o alfa já vai nos vértices: não aplicar duas vezes
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(e, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, g.pose(), faded, 15728880));
            g.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            g.pose().popPose();
            Lighting.setupFor3DItems();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);     // devolve o alfa da tela para o resto do quadro
        }
    }

    private static final Map<RenderType, RenderType> FADED = new HashMap<>();

    private static RenderType fadedType(RenderType base) {
        return FADED.computeIfAbsent(base, FadedType::new);
    }

    /** Cópia de um render type com mistura de cores (translucidez) ligada depois da configuração original. */
    private static final class FadedType extends RenderType {
        FadedType(RenderType base) {
            super("lasthorizon_fade", base.format(), base.mode(), base.bufferSize(), base.affectsCrumbling(), false,
                    () -> {
                        base.setupRenderState();
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                    },
                    base::clearRenderState);
        }
    }

    /** Multiplica o alfa de cada vértice por um valor fixo. */
    private static final class AlphaConsumer implements VertexConsumer {
        private final VertexConsumer d;
        private final float alpha;

        AlphaConsumer(VertexConsumer d, float alpha) {
            this.d = d;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            d.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            d.color(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            d.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            d.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            d.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            d.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            d.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            d.defaultColor(r, g, b, Math.round(a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            d.unsetDefaultColor();
        }
    }
}
