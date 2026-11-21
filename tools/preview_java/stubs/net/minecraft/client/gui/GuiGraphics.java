package net.minecraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Stub só para o preview: imita fill/blit/setColor/scissor do GuiGraphics do Minecraft 1.20.1 (amostragem NEAREST). */
public class GuiGraphics {
    public final BufferedImage canvas;
    private final String resRoot;
    private final Map<String, BufferedImage> cache = new HashMap<>();
    private float cr = 1, cg = 1, cb = 1, ca = 1;
    private int sx1 = 0, sy1 = 0, sx2, sy2;

    public GuiGraphics(int w, int h, String resRoot) {
        this.canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        this.resRoot = resRoot;
        sx2 = w; sy2 = h;
    }

    private final PoseStack pose = new PoseStack();
    public PoseStack pose() { return pose; }

    /** Texto do preview: fonte AWT sem suavização (só para conferir posição e largura, não a aparência exata). */
    public int drawString(Font font, String text, int x, int y, int argb, boolean shadow) {
        java.awt.Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        float sc = pose.s();
        g2.setFont(new java.awt.Font("DejaVu Sans", java.awt.Font.PLAIN, Math.max(1, Math.round(8 * sc))));
        float cx = pose.tx() + sc * x, by = pose.ty() + sc * y;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            if (shadow) { g2.setColor(new java.awt.Color(0x3F000000 | 0, true)); g2.drawString(ch, Math.round(cx) + 1, Math.round(by + 8 * sc)); }
            g2.setColor(new java.awt.Color(argb, true));
            g2.drawString(ch, Math.round(cx), Math.round(by + 7 * sc));
            cx += sc * Font.adv(text.charAt(i));
        }
        g2.dispose();
        return Math.round(cx - pose.tx());
    }

    /** Como no jogo: desenha o que estiver no lote (aqui tudo é desenhado na hora, então não há nada a fazer). */
    public void flush() {}
    public void drawManaged(Runnable r) { r.run(); }

    public void setColor(float r, float g, float b, float a) { cr = r; cg = g; cb = b; ca = a; }
    public void enableScissor(int x1, int y1, int x2, int y2) { sx1 = x1; sy1 = y1; sx2 = x2; sy2 = y2; }
    public void disableScissor() { sx1 = 0; sy1 = 0; sx2 = canvas.getWidth(); sy2 = canvas.getHeight(); }

    private void put(int x, int y, float r, float g, float b, float a) {
        if (x < sx1 || y < sy1 || x >= sx2 || y >= sy2 || x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight()) return;
        int d = canvas.getRGB(x, y);
        float dr = ((d >> 16) & 255) / 255f, dg = ((d >> 8) & 255) / 255f, db = (d & 255) / 255f;
        float or = r * a + dr * (1 - a), og = g * a + dg * (1 - a), ob = b * a + db * (1 - a);
        canvas.setRGB(x, y, 0xFF000000 | (Math.round(or * 255) << 16) | (Math.round(og * 255) << 8) | Math.round(ob * 255));
    }

    public void fill(int x1, int y1, int x2, int y2, int argb) {
        fillImpl(x1, y1, x2, y2, argb);
        RenderSystem.disableBlend();   // o RenderType.gui() desliga o blend ao terminar o lote
    }

    private void fillImpl(int x1, int y1, int x2, int y2, int argb) {
        float a = ((argb >>> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        double[] bb = pose.bounds(x1, y1, x2, y2);
        boolean id = pose.identity();
        for (int y = (int) Math.floor(bb[1]); y < Math.ceil(bb[3]); y++)
            for (int x = (int) Math.floor(bb[0]); x < Math.ceil(bb[2]); x++) {
                if (!id) {
                    java.awt.geom.Point2D p = pose.inverse(x + 0.5, y + 0.5);
                    if (p.getX() < x1 || p.getX() >= x2 || p.getY() < y1 || p.getY() >= y2) continue;
                }
                put(x, y, r, g, b, a);
            }
    }

    private BufferedImage tex(ResourceLocation rl) {
        return cache.computeIfAbsent(rl.toString(), k -> {
            try {
                File f = new File(resRoot, "assets/" + rl.ns + "/" + rl.path);
                BufferedImage im = ImageIO.read(f);
                if (im == null) throw new RuntimeException("sem imagem: " + f);
                return im;
            } catch (Exception e) { throw new RuntimeException("textura não encontrada: " + rl, e); }
        });
    }

    public void blit(ResourceLocation rl, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
        BufferedImage im = tex(rl);
        if (im.getWidth() != tw || im.getHeight() != th)
            throw new RuntimeException("tamanho de textura errado em " + rl + ": arquivo " + im.getWidth() + "x" + im.getHeight() + " mas o código diz " + tw + "x" + th);
        if (u < 0 || v < 0 || u + uw > tw || v + vh > th)
            throw new RuntimeException("região fora da textura em " + rl + ": u=" + u + " v=" + v + " " + uw + "x" + vh);
        double[] bb = pose.bounds(x, y, x + w, y + h);
        for (int py = (int) Math.floor(bb[1]); py < Math.ceil(bb[3]); py++) {
            for (int px = (int) Math.floor(bb[0]); px < Math.ceil(bb[2]); px++) {
                java.awt.geom.Point2D p = pose.inverse(px + 0.5, py + 0.5);
                if (p.getX() < x || p.getX() >= x + w || p.getY() < y || p.getY() >= y + h) continue;
                int sx = Math.min(tw - 1, (int) (u + (p.getX() - x) * uw / w));
                int sy = Math.min(th - 1, (int) (v + (p.getY() - y) * vh / h));
                int pix = im.getRGB(sx, sy);
                float ta = ((pix >>> 24) & 255) / 255f;
                if (ta <= 0) continue;
                float tr = ((pix >> 16) & 255) / 255f, tg = ((pix >> 8) & 255) / 255f, tb = (pix & 255) / 255f;
                put(px, py, tr * cr, tg * cg, tb * cb, RenderSystem.blend ? ta * ca * RenderSystem.color[3] : 1f);
            }
        }
    }
}
