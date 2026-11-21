import br.com.lasthorizon.akuma.client.SeaBackground;
import net.minecraft.client.gui.GuiGraphics;
import javax.imageio.ImageIO;
import java.io.File;

/** Renderiza o fundo de verdade (o SeaBackground.java do mod) para PNGs. Uso: Preview <resRoot> <outDir> <tempoMs>... */
public class Preview {
    public static void main(String[] a) throws Exception {
        String res = a[0], out = a[1];
        new File(out).mkdirs();
        int iw = SeaBackground.W, ih = SeaBackground.H;   // PREVIEW_SIZE=234x113 simula uma janela menor (corte)
        String sz = System.getenv("PREVIEW_SIZE");
        if (sz != null) { iw = Integer.parseInt(sz.split("x")[0]); ih = Integer.parseInt(sz.split("x")[1]); }
        for (int i = 2; i < a.length; i++) {
            long t = Long.parseLong(a[i]);
            GuiGraphics g = new GuiGraphics(iw, ih, res);
            if (System.getenv("PREVIEW_FADE") == null) {   // espera o fade-in terminar (renderiza ~1 s em tempo real)
                for (int k = 0; k < 24; k++) { SeaBackground.render(g, 0, 0, iw, ih, t); Thread.sleep(50); }
            }
            SeaBackground.render(g, 0, 0, iw, ih, t);
            ImageIO.write(g.canvas, "png", new File(out, "t" + String.format("%09d", t) + ".png"));
        }
    }
}
