import br.com.lasthorizon.akuma.client.CharacterMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;

public class FaceShot {
    static void set(Object o, String f, Object v) throws Exception {
        Field fl = o.getClass().getDeclaredField(f); fl.setAccessible(true);
        if (v instanceof Integer i && fl.getType() == int.class) fl.setInt(o, i);
        else if (v instanceof Long l && fl.getType() == long.class) fl.setLong(o, l);
        else fl.set(o, v);
    }
    // args: res outPng tab bodyGroup itemClass metal earSide adornBitsCsv [scale]
    public static void main(String[] a) throws Exception {
        String res = a[0];
        CharacterMap.Hooks hk = (CharacterMap.Hooks) Proxy.newProxyInstance(FaceShot.class.getClassLoader(), new Class<?>[]{CharacterMap.Hooks.class},
            (p, m, args) -> {
                Class<?> r = m.getReturnType();
                if (m.getName().equals("mottoText")) return "";
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == String.class) return "";
                return null;
            });
        CharacterMap map = new CharacterMap(hk);
        set(map, "tab", Integer.parseInt(a[2]));
        set(map, "bodyGroup", Integer.parseInt(a[3]));
        set(map, "itemClass", Integer.parseInt(a[4]));
        set(map, "metal", Integer.parseInt(a[5]));
        set(map, "earSide", Integer.parseInt(a[6]));
        // porte: HEIGHT = altura em centésimos de bloco (150 a 250, padrão 180), BUST = busto em % (0 a 100), GENDER = 0 masculino / 1 feminino (o busto só aparece no feminino)
        // orelhas: EARMETAL = metal da esquerda, EARMETALR = metal da direita (0 prata, 1 dourado), EARL / EARR = itens de cada orelha (máscara), EAREDIT = orelha editada (0 esquerda, 1 direita)
        for (String[] e : new String[][]{{"EARMETAL", "earMetalL"}, {"EARMETALR", "earMetalR"}, {"EARL", "earL"}, {"EARR", "earR"}, {"EAREDIT", "earEdit"}, {"HEIGHT", "height"}, {"BUST", "bust"}, {"GENDER", "gender"}})
            if (System.getenv(e[0]) != null) set(map, e[1], Integer.parseInt(System.getenv(e[0])));
        long mask = 0;
        if (!a[7].isEmpty()) for (String s : a[7].split(",")) mask |= 1L << Integer.parseInt(s);
        set(map, "adorn", mask);
        if (System.getenv("CONFIRM") != null) { map.setName("Monkey D Luffy"); map.setMotto("Quem nao arrisca o mar nunca ve o horizonte nem o sol"); map.setEpithet("Chapeu de Palha do Leste"); set(map, "stage", 1); set(map, "stageStart", System.currentTimeMillis() - 4000L); }
        int sc = a.length > 8 ? Integer.parseInt(a[8]) : 3;
        int W = 392, H = 224;
        int mxx = a.length > 9 ? Integer.parseInt(a[9]) : -100, myy = a.length > 10 ? Integer.parseInt(a[10]) : -100;
        BufferedImage out = null;
        for (int k = 0; k < 3; k++) {
            GuiGraphics g = new GuiGraphics(W, H, res);
            map.render(g, new Font(), mxx, myy);
            out = g.canvas;
            if (k < 2) Thread.sleep(Long.parseLong(System.getenv().getOrDefault("SLEEP","900")));
        }
        BufferedImage big = new BufferedImage(W * sc, H * sc, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H * sc; y++) for (int x = 0; x < W * sc; x++) big.setRGB(x, y, out.getRGB(x / sc, y / sc) | 0xFF000000);
        ImageIO.write(big, "png", new File(a[1]));
    }
}
