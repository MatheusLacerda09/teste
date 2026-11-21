import br.com.lasthorizon.akuma.character.CharacterData;
import br.com.lasthorizon.akuma.character.SkinPainter;
import br.com.lasthorizon.akuma.client.CharacterMap;
import br.com.lasthorizon.akuma.client.MapArt;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renderiza o mapa de criação de personagem de verdade (o CharacterMap.java do mod) para PNG, simulando cliques.
 * Uso: MapPreview <outDir> passo...   com passos: name=Luffy  click=x,y  mouse=x,y  press=x,y  drag=dx  release  sleep=ms  shot=arquivo
 * (o boneco do preview é um sprite de frente: só a plataforma e as dicas mostram o giro; o 3D de verdade só roda no jogo)
 */
public class MapPreview {
    static CharacterData cur = CharacterData.defaults();
    static boolean sent;
    static int drafts;
    static CharacterMap book0;
    static String motto = "", epithet = "";
    static int mCursor = -1;
    static int[] mSel;
    static StringBuilder snd = new StringBuilder();

    public static void main(String[] a) throws Exception {
        String out = a[0];
        new File(out).mkdirs();
        String res = System.getProperty("res", "src/main/resources");
        int W = MapArt.BW, H = MapArt.BH;
        Font font = new Font();
        CharacterMap book = new CharacterMap(new CharacterMap.Hooks() {
            public void changed(CharacterData d) { cur = d; }
            public void drawPlayer(GuiGraphics g, int cx, int feetY, int scale, int mx, int my, float spinDeg) { figure(g, cx, feetY, scale * 1.95F / 32F); }
            public void clipPortrait(GuiGraphics g, int x, int y, int w, int h, boolean on) { if (on) g.enableScissor(x, y, x + w, y + h); else g.disableScissor(); }
            public void setNameText(String s) { book0.setName(s); cur = cur.withName(s); }
            public void setMottoText(String s) { book0.setMotto(s); motto = s; }
            public boolean shiftDown() { return false; }
            public void saveDraft(br.com.lasthorizon.akuma.character.CharacterDraft d) { drafts++; }
            public void drawNameBox(GuiGraphics g, int mx, int my) {
                String n = cur.name;
                g.drawString(font, n, CharacterMap.NAME_X + 6, CharacterMap.NAME_Y + 5, 0xFFF6E9C4, true);
            }
            public String mottoText() { return motto; }
            public int mottoCursor() { return mCursor < 0 ? motto.length() : Math.min(mCursor, motto.length()); }
            public int[] mottoSelection() { return mSel; }
            public void moveMottoCursor(int pos) { mCursor = pos; mSel = null; System.out.println("cursor -> " + pos); }
            public void focusMottoBox() { mCursor = -1; mSel = null; }
            public void setEpithetText(String s) { book0.setEpithet(s); epithet = s; }
            public void drawEpithetBox(GuiGraphics g, int mx, int my) {
                g.drawString(font, epithet, CharacterMap.EPI_X + 6, CharacterMap.EPI_Y + 5, 0xFFF6E9C4, true);
            }
            public void epithetBoxVisible(boolean v) {}
            public boolean epithetBoxFocused() { return false; }
            public void sound(int kind, float pitch) { snd.append(" ").append(kind).append("@").append(String.format("%.2f", pitch)); }
            public boolean worldReady() { return true; }
            public void mottoBoxVisible(boolean v) {}
            public void nameBoxVisible(boolean v) {}
            public void focusNameBox() {}
            public boolean nameBoxFocused() { return true; }
            public void finalizeNow(CharacterData d) { sent = true; System.out.println("finalizeNow: " + d.name); }
        });
        book0 = book;
        int mx = -50, my = -50;
        for (int i = 1; i < a.length; i++) {
            String[] kv = a[i].split("=", 2);
            switch (kv[0]) {
                case "name" -> { book.setName(kv[1]); cur = cur.withName(kv[1]); }
                case "epithet" -> { book.setEpithet(kv[1]); epithet = kv[1]; }
                case "snd" -> { System.out.println("sons:" + snd); snd.setLength(0); }
                case "motto" -> { book.setMotto(kv[1]); motto = kv[1]; }
                case "type" -> { motto = motto + kv[1]; book.setMotto(motto); mCursor = -1; }
                case "sel" -> { String[] p = kv[1].split(","); mSel = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])}; }
                case "close" -> System.out.println("close -> " + book.closeMottoEditor() + " open=" + book.mottoEditorOpen());
                case "mouse" -> { String[] p = kv[1].split(","); mx = Integer.parseInt(p[0]); my = Integer.parseInt(p[1]); }
                case "click" -> {
                    String[] p = kv[1].split(",");
                    mx = Integer.parseInt(p[0]); my = Integer.parseInt(p[1]);
                    GuiGraphics g0 = new GuiGraphics(W, H, res);
                    book.render(g0, font, mx, my);                 // precisa de um quadro antes (fonte e estado)
                    System.out.println("click " + kv[1] + " -> " + book.click(mx, my) + " stage=" + book.stage() + " mottoOpen=" + book.mottoEditorOpen());
                }
                case "press" -> { String[] p = kv[1].split(","); System.out.println("press -> " + book.beginTurn(Integer.parseInt(p[0]), Integer.parseInt(p[1]))); }
                case "drag" -> book.turnBy(Double.parseDouble(kv[1]));
                case "release" -> book.endTurn();
                case "scroll" -> System.out.println("scroll -> " + book.scroll(mx, my, Double.parseDouble(kv[1])));
                case "right" -> { String[] p = kv[1].split(","); System.out.println("right -> " + book.rightClick(Integer.parseInt(p[0]), Integer.parseInt(p[1]))); }
                case "drafts" -> System.out.println("drafts saved: " + drafts);
                case "sleep" -> Thread.sleep(Long.parseLong(kv[1]));
                case "legend" -> {           // força a alma lendária (só para ver o visual no preview)
                    GuiGraphics g0 = new GuiGraphics(W, H, res);
                    book.render(g0, font, mx, my);
                    Class<?> c = CharacterMap.class;
                    java.lang.reflect.Field ft = c.getDeclaredField("traits"); ft.setAccessible(true);
                    int[] tr = (int[]) ft.get(book); int[] lg = {0, 4, 0, 4, 0, 4}; System.arraycopy(lg, 0, tr, 0, 6);
                    java.lang.reflect.Field fr = c.getDeclaredField("rollDone"); fr.setAccessible(true); fr.set(book, true);
                    java.lang.reflect.Field fd = c.getDeclaredField("doneAt"); fd.setAccessible(true); fd.set(book, 1L);
                    java.lang.reflect.Field fo = c.getDeclaredField("touched"); fo.setAccessible(true); fo.setInt(book, fo.getInt(book) | (1 << CharacterMap.TAB_SOUL));
                }
                case "set" -> {              // set=campo:valor  muda um campo (int ou long) do CharacterMap direto, por reflexão (ex.: set=tab:1 set=bodyGroup:2 set=itemClass:2)
                    String[] p = kv[1].split(":");
                    java.lang.reflect.Field f = CharacterMap.class.getDeclaredField(p[0]);
                    f.setAccessible(true);
                    if (f.getType() == long.class) f.setLong(book, Long.parseLong(p[1])); else f.setInt(book, Integer.parseInt(p[1]));
                }
                case "shot" -> {
                    GuiGraphics g = new GuiGraphics(W, H, res);
                    MapArt.backdrop(g, W, H, 0L);
                    book.render(g, font, mx, my);
                    ImageIO.write(g.canvas, "png", new File(out, kv[1] + ".png"));
                    System.out.println("shot " + kv[1] + " done=" + book.isDone());
                }
                default -> throw new IllegalArgumentException(a[i]);
            }
        }
    }

    /** Boneco de frente montado da skin gerada (camada base + externa), só para conferir o visual no preview. */
    static void figure(GuiGraphics g, int cx, int feetY, float k) {
        int[] px = SkinPainter.paint(cur);
        boolean slim = cur.gender == 1;
        int aw = slim ? 3 : 4;
        // {skinU, skinV, w, h, destX, destY} em unidades do boneco (cabeça no topo, 32 de altura)
        int[][] parts = {
                {4, 20, 4, 12, 4, 20}, {20, 52, 4, 12, 8, 20},                      // pernas (direita do jogador à esquerda)
                {20, 20, 8, 12, 4, 8}, {44, 20, aw, 12, 4 - aw, 8}, {36, 52, aw, 12, 12, 8},
                {8, 8, 8, 8, 4, 0},
                {4, 36, 4, 12, 4, 20}, {4, 52, 4, 12, 8, 20},
                {20, 36, 8, 12, 4, 8}, {44, 36, aw, 12, 4 - aw, 8}, {52, 52, aw, 12, 12, 8},
                {40, 8, 8, 8, 4, 0}};
        float x0 = cx - 8 * k, y0 = feetY - 32 * k;
        for (int[] p : parts) {
            for (int j = 0; j < p[3]; j++) for (int i = 0; i < p[2]; i++) {
                int c = px[(p[1] + j) * 64 + p[0] + i];
                if ((c >>> 24) == 0) continue;
                g.fill(Math.round(x0 + (p[4] + i) * k), Math.round(y0 + (p[5] + j) * k), Math.round(x0 + (p[4] + i + 1) * k), Math.round(y0 + (p[5] + j + 1) * k), c);
            }
        }
    }
}
