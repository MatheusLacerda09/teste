package net.minecraft.client.gui;

/** Stub: larguras aproximadas da fonte padrão do Minecraft (avanço em pixels, já com o espaço entre letras). */
public class Font {
    public int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += adv(s.charAt(i));
        return w;
    }

    static int adv(char c) {
        if ("i!.,:;'|\u00B7\u2022".indexOf(c) >= 0) return 2;
        if (c == 'l' || c == '`') return 3;
        if (c == 't' || c == 'I' || c == ' ' || c == '[' || c == ']' || c == '*') return 4;
        if ("fk<>()\"{}\u201C\u201D".indexOf(c) >= 0) return 5;
        if (c == '@' || c == '~') return 7;
        return 6;
    }
}
