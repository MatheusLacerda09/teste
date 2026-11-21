package br.com.lasthorizon.akuma.client;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.character.BustShape;
import br.com.lasthorizon.akuma.character.CharacterCache;
import br.com.lasthorizon.akuma.character.CharacterData;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Peitos do personagem feminino: só isto é 3D, o resto do corpo é a skin comum de braços finos.
 *
 * <p>O formato vem do modelo do Blockbench ({@code art/models/peitos_sea_sovereign.bbmodel}): cada peito é uma caixa
 * inclinada para a frente (o topo fica rente ao peitoral, a parte de baixo sai), e há uma cópia levemente maior para a camada
 * externa da skin (casaco, colete). As medidas estão em {@link #BASE} e {@link #OUTER}, na MESMA unidade e nos MESMOS eixos do
 * Blockbench (y para cima, 24 = ombro, +x = direita do personagem, frente = norte) e as faces seguem a MESMA convenção de UV do
 * Blockbench, então o que se vê no Blockbench é o que aparece no jogo: o que se muda no modelo copia-se direto para cá.
 *
 * <p>As caixas leem a PRÓPRIA skin do jogador (frente do corpo: u 20..27, v 20..31), então o peito tem sempre a cor da pele,
 * do top ou da roupa que o personagem está usando. Nenhuma textura extra.
 *
 * <p><b>Armadura:</b> com peitoral vestido, os peitos continuam aparecendo: as mesmas caixas, infladas 1 pixel (a espessura da
 * armadura), lendo a textura da própria armadura (a mesma da armadura de vanilla, com tinta e camada "overlay" da armadura de
 * couro), para qualquer armadura que use textura no formato do jogador. Armadura com modelo próprio (sem textura nesse formato)
 * continua escondendo os peitos, porque a forma dela não é conhecida.
 *
 * <p><b>Busto menor</b> (barra da criação de personagem): as caixas NÃO encolhem, então os dois peitos continuam do mesmo tamanho e
 * colados no meio; cada uma só fica menos inclinada e afunda no peitoral ({@link BustShape}), e o que sai do corpo diminui com o
 * busto: na proporção dele de 100% a 50% e, abaixo de 50%, mais devagar (a raiz), para o peito pequeno (25%, 10%) continuar com
 * volume e com a faixa escura de baixo visível. 100% é o modelo do Blockbench; abaixo de 5% não aparece peito (peitoral liso).
 *
 * <p>Só aparece para personagens do gênero feminino (criados na tela de criação) e com o jogador visível.
 */
public final class BreastLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /**
     * Uma caixa do modelo (coordenadas do Blockbench): cantos {@code from}/{@code to}, pivô da rotação (x = 0), ângulo em torno do
     * eixo X, folga ({@code inflate}) e a região da skin (u0, v0, u1, v1) de cada face, como no Blockbench (a imagem de cada face
     * tem o canto superior esquerdo em (u0, v0) e o inferior direito em (u1, v1); valores invertidos espelham a imagem). A face de
     * trás (para dentro do corpo) não é desenhada.
     */
    private record Box(float x0, float y0, float z0, float x1, float y1, float z1, float pivotY, float pivotZ, float rotX, float inflate,
                       float[] north, float[] east, float[] west, float[] up, float[] down) {
        Box inflated(float g) {
            return new Box(x0, y0, z0, x1, y1, z1, pivotY, pivotZ, rotX, g, north, east, west, up, down);
        }

        /**
         * A mesma caixa para o busto {@code s} (0 a 1; 1 = o modelo), SEM mudar o tamanho dela (a largura continua a do modelo, então os
         * dois peitos seguem colados no meio): ela só fica menos inclinada e afunda no peitoral, de modo que a ponta de baixo sai do
         * corpo {@code s} vezes o que sai no modelo. A conta está em {@link BustShape}, que tem os testes.
         */
        Box retracted(float s) {
            if (s >= BustShape.FULL) return this;
            float[] q = BustShape.retract(rotX, pivotY - y0, z1 - z0, s);   // {ângulo, afundamento}
            return new Box(x0, y0, z0 + q[1], x1, y1, z1 + q[1], pivotY, pivotZ + q[1], q[0], inflate, north, east, west, up, down);
        }
    }

    /**
     * Camada base. Cada peito tem x entre -4 e 0 (o que fica à direita de quem olha de frente) ou entre 0 e 4.
     *
     * <p>Regra das texturas: NENHUMA face estica um desenho da skin (esticar 3 colunas em 4 pixels dava colunas de largura desigual,
     * com cara de pixel duplicado). Cada face lê uma região do tamanho exato dela (1 pixel da skin = 1 pixel do modelo) ou, no eixo em
     * que a face é mais funda que a região, uma linha ou coluna só de pixels, que fica repetida ao longo da face (sem desenho para
     * duplicar). A frente lê v 20..24 (2 linhas de pele e as linhas do top, alinhadas com o resto do corpo) em 4 x 4. As laterais leem
     * a coluna da ponta ou do meio (as linhas da frente seguem para o lado). A face de baixo lê a linha v 24 inteira (4 colunas): a
     * primeira abaixo do peito, justo a que ela esconde do corpo, então nada do que aparece embaixo se repete. O topo (escondido
     * dentro do corpo) lê a linha v 20. O teste {@code CharacterLogicTest} confere a regra.
     */
    private static final Box[] BASE = {
            new Box(-4, 19.8F, -2, 0, 23.8F, 1, 23.8F, -2, 37, 0,
                    new float[]{24, 20, 28, 24}, new float[]{24, 20, 25, 24}, new float[]{27, 20, 28, 24},
                    new float[]{28, 21, 24, 20}, new float[]{28, 24, 24, 25}),
            new Box(0, 19.8F, -2, 4, 23.8F, 1, 23.8F, -2, 37, 0,
                    new float[]{20, 20, 24, 24}, new float[]{20, 20, 21, 24}, new float[]{23, 20, 24, 24},
                    new float[]{24, 21, 20, 20}, new float[]{24, 24, 20, 25}),
    };
    /** Camada externa (lê a frente do casaco: v 36 + linha; a face de baixo, a linha v 40); mesma inclinação e posição da base, só com a folga do casaco. */
    private static final Box[] OUTER = {
            new Box(-4, 19.8F, -2, 0, 23.8F, 1, 23.8F, -2, 37, 0.26F,
                    new float[]{24, 36, 28, 40}, new float[]{24, 36, 25, 40}, new float[]{27, 36, 28, 40},
                    new float[]{28, 37, 24, 36}, new float[]{28, 40, 24, 41}),
            new Box(0, 19.8F, -2, 4, 23.8F, 1, 23.8F, -2, 37, 0.26F,
                    new float[]{20, 36, 24, 40}, new float[]{20, 36, 21, 40}, new float[]{23, 36, 24, 40},
                    new float[]{24, 37, 20, 36}, new float[]{24, 40, 20, 41}),
    };
    /** Folga sobre a armadura de peitoral (que é o corpo inflado em 1); 1,02 para os peitos não brigarem em profundidade com ela. */
    private static final float ARMOR_INFLATE = 1.02F;

    public BreastLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        CharacterData d = CharacterCache.get(player.getUUID());
        if (d == null || d.gender != 1) return;                          // só o feminino da criação de personagem
        if (d.bust < 5) return;                                          // busto no mínimo: peito liso
        final float bs = d.bust / 100.0F;
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST) {
            renderOverArmor(pose, buffers, light, overlay, player, chest, armor, bs);
            return;
        }

        ResourceLocation skin = player.getSkinTextureLocation();         // a skin gerada (o mixin devolve a do personagem)
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);                  // acompanha o corpo: agachar, nadar, girar...
        PoseStack.Pose p = pose.last();
        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        for (Box b : BASE) draw(p, base, light, overlay, b.retracted(bs), 64.0F, 64.0F, 255, 255, 255);
        if (player.isModelPartShown(PlayerModelPart.JACKET)) {
            VertexConsumer outer = buffers.getBuffer(RenderType.entityTranslucent(skin));
            for (Box b : OUTER) draw(p, outer, light, overlay, b.retracted(bs), 64.0F, 64.0F, 255, 255, 255);
        }
        pose.popPose();
    }

    /** Peitos sobre o peitoral: as caixas da base infladas na espessura da armadura, com a textura dela. */
    private void renderOverArmor(PoseStack pose, MultiBufferSource buffers, int light, int overlay, AbstractClientPlayer player,
                                 ItemStack chest, ArmorItem armor, float bs) {
        ResourceLocation tex = armorTexture(player, chest, armor, null);
        if (tex == null) return;                                         // armadura sem textura no formato do jogador: não dá para saber a forma
        int[] size = textureSize(tex);
        if (size == null) return;

        int r = 255, g = 255, b = 255;
        boolean dyed = chest.getItem() instanceof DyeableLeatherItem;
        if (dyed) {
            int c = ((DyeableLeatherItem) chest.getItem()).getColor(chest);
            r = (c >> 16) & 255; g = (c >> 8) & 255; b = c & 255;
        }

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        PoseStack.Pose p = pose.last();
        VertexConsumer vc = ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(tex), false, chest.hasFoil());
        for (Box box : BASE) draw(p, vc, light, overlay, box.retracted(bs).inflated(ARMOR_INFLATE), size[0], size[1], r, g, b);
        if (dyed) {                                                      // couro tem a camada "overlay" (costuras), sem tinta
            ResourceLocation over = armorTexture(player, chest, armor, "overlay");
            int[] os = over == null ? null : textureSize(over);
            if (os != null) {
                VertexConsumer ov = ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(over), false, false);
                for (Box box : BASE) draw(p, ov, light, overlay, box.retracted(bs).inflated(ARMOR_INFLATE + 0.01F), os[0], os[1], 255, 255, 255);
            }
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------------------ textura da armadura

    private static final Map<ResourceLocation, int[]> SIZES = new ConcurrentHashMap<>();
    private static final int[] MISSING = new int[0];

    /** Textura da camada 1 da armadura (corpo), no mesmo caminho que o jogo usa; {@code null} se não existe. */
    private static ResourceLocation armorTexture(AbstractClientPlayer player, ItemStack stack, ArmorItem armor, String type) {
        try {
            String path = stack.getItem().getArmorTexture(stack, player, EquipmentSlot.CHEST, type);   // armaduras de outros mods
            if (path == null) {
                String name = armor.getMaterial().getName(), domain = "minecraft";
                int i = name.indexOf(':');
                if (i != -1) { domain = name.substring(0, i); name = name.substring(i + 1); }
                path = domain + ":textures/models/armor/" + name + "_layer_1" + (type == null ? "" : "_" + type) + ".png";
            }
            ResourceLocation loc = new ResourceLocation(path);
            return textureSize(loc) == null ? null : loc;
        } catch (Exception e) {
            return null;
        }
    }

    /** Largura e altura da textura (com cache), ou {@code null} se ela não existe. */
    private static int[] textureSize(ResourceLocation loc) {
        int[] s = SIZES.computeIfAbsent(loc, l -> {
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(l);
                if (res.isEmpty()) return MISSING;
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    return new int[]{img.getWidth(), img.getHeight()};
                }
            } catch (Exception e) {
                return MISSING;
            }
        });
        return s == MISSING ? null : s;
    }

    // ------------------------------------------------------------------------------------------------ geometria

    /** Uma caixa, com as faces e a orientação de UV do Blockbench (x do Blockbench = direita do personagem). */
    private static void draw(PoseStack.Pose p, VertexConsumer vc, int light, int overlay, Box b, float texW, float texH, int cr, int cg, int cb) {
        final float g = b.inflate;
        final float x0 = b.x0 - g, y0 = b.y0 - g, z0 = b.z0 - g, x1 = b.x1 + g, y1 = b.y1 + g, z1 = b.z1 + g;
        final double a = Math.toRadians(b.rotX);
        final float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
        final float vs = 64.0F * texH / texW;                            // v em "pixels de skin 64 x 64" -> 0..1 em qualquer proporção
        // norte (frente, z menor): visto de fora, a esquerda da imagem é o x maior
        face(p, vc, light, overlay, b, sin, cos, b.north, vs, cr, cg, cb, new float[]{x1, y1, z0}, new float[]{x0, y1, z0}, new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, 0, 0, -1);
        // leste (x maior): a esquerda da imagem é o sul (z maior)
        face(p, vc, light, overlay, b, sin, cos, b.east, vs, cr, cg, cb, new float[]{x1, y1, z1}, new float[]{x1, y1, z0}, new float[]{x1, y0, z0}, new float[]{x1, y0, z1}, 1, 0, 0);
        // oeste (x menor): a esquerda da imagem é o norte
        face(p, vc, light, overlay, b, sin, cos, b.west, vs, cr, cg, cb, new float[]{x0, y1, z0}, new float[]{x0, y1, z1}, new float[]{x0, y0, z1}, new float[]{x0, y0, z0}, -1, 0, 0);
        // topo: borda de cima da imagem = norte, esquerda = x menor
        face(p, vc, light, overlay, b, sin, cos, b.up, vs, cr, cg, cb, new float[]{x0, y1, z0}, new float[]{x1, y1, z0}, new float[]{x1, y1, z1}, new float[]{x0, y1, z1}, 0, 1, 0);
        // embaixo: igual ao topo (borda de cima da imagem = norte, esquerda = x menor)
        face(p, vc, light, overlay, b, sin, cos, b.down, vs, cr, cg, cb, new float[]{x0, y0, z0}, new float[]{x1, y0, z0}, new float[]{x1, y0, z1}, new float[]{x0, y0, z1}, 0, -1, 0);
    }

    /** Uma face: cantos em ordem sup. esq., sup. dir., inf. dir., inf. esq. (como a imagem da face no Blockbench). */
    private static void face(PoseStack.Pose p, VertexConsumer vc, int light, int overlay, Box b, float sin, float cos, float[] uv, float vs,
                             int cr, int cg, int cb, float[] tl, float[] tr, float[] br, float[] bl, float hx, float hy, float hz) {
        float[] a = toModel(b, sin, cos, tl), bb = toModel(b, sin, cos, tr), c = toModel(b, sin, cos, br), d = toModel(b, sin, cos, bl);
        float[] h = dirToModel(sin, cos, hx, hy, hz);
        quad(p, vc, light, overlay, a, bb, c, d, uv[0], uv[1], uv[2], uv[3], vs, cr, cg, cb, h[0], h[1], h[2]);
    }

    /**
     * Ponto do Blockbench (y para cima, +x = direita do personagem) -> espaço do corpo do jogo (y para baixo, x espelhado),
     * já com a rotação em torno do pivô.
     */
    private static float[] toModel(Box b, float sin, float cos, float[] q) {
        float dy = q[1] - b.pivotY, dz = q[2] - b.pivotZ;
        float y = b.pivotY + dy * cos - dz * sin, z = b.pivotZ + dy * sin + dz * cos;
        return new float[]{-q[0], 24.0F - y, z};
    }

    private static float[] dirToModel(float sin, float cos, float x, float y, float z) {
        return new float[]{-x, -(y * cos - z * sin), y * sin + z * cos};
    }

    /**
     * Um quadrilátero com a região (u0,v0)-(u1,v1) da textura (em pixels de uma skin 64 x 64); os cantos vêm em ordem: sup. esq.,
     * sup. dir., inf. dir., inf. esq. {@code (hx, hy, hz)} só diz para que lado a face aponta (para a normal de luz sair virada
     * para fora).
     */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, int light, int overlay,
                             float[] a, float[] b, float[] c, float[] d, float u0, float v0, float u1, float v1, float vs,
                             int cr, int cg, int cb, float hx, float hy, float hz) {
        Matrix4f m = p.pose();
        Matrix3f nm = p.normal();
        Vector3f e1 = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        Vector3f e2 = new Vector3f(d[0] - a[0], d[1] - a[1], d[2] - a[2]);
        Vector3f n = e1.cross(e2, new Vector3f());
        if (n.lengthSquared() < 1.0E-8F) return;
        n.normalize();
        if (n.x() * hx + n.y() * hy + n.z() * hz < 0.0F) n.negate();
        vertex(m, nm, vc, a, u0, v0, vs, n, light, overlay, cr, cg, cb);
        vertex(m, nm, vc, b, u1, v0, vs, n, light, overlay, cr, cg, cb);
        vertex(m, nm, vc, c, u1, v1, vs, n, light, overlay, cr, cg, cb);
        vertex(m, nm, vc, d, u0, v1, vs, n, light, overlay, cr, cg, cb);
    }

    private static void vertex(Matrix4f m, Matrix3f nm, VertexConsumer vc, float[] pos, float u, float v, float vs, Vector3f n,
                               int light, int overlay, int cr, int cg, int cb) {
        vc.vertex(m, pos[0] / 16.0F, pos[1] / 16.0F, pos[2] / 16.0F)
                .color(cr, cg, cb, 255)
                .uv(u / 64.0F, v / vs)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(nm, n.x(), n.y(), n.z())
                .endVertex();
    }

    /** Registro (barramento do mod, só no cliente). */
    @Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {}

        /** Entra nos dois modelos de jogador do jogo: "default" (braço grosso) e "slim" (braço fino, o do feminino). */
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (String type : event.getSkins()) {
                EntityRenderer<? extends Player> renderer = event.getSkin(type);
                if (renderer instanceof PlayerRenderer player) player.addLayer(new BreastLayer(player));
            }
        }

        /** Esquece os tamanhos de textura guardados quando os recursos recarregam (F3+T, troca de pacote). */
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) rm -> SIZES.clear());
        }
    }
}
