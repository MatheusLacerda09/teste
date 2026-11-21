package net.minecraft.client.resources.language;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stub: lê o arquivo de idioma (LANG=pt_br ou en_us, RES=pasta resources) e formata como o jogo. */
public class I18n {
    private static Map<String, String> map;

    public static String get(String key, Object... args) {
        if (map == null) {
            map = new HashMap<>();
            try {
                String res = System.getProperty("res", "src/main/resources"), lang = System.getProperty("lang", "pt_br");
                String s = Files.readString(new File(res, "assets/lasthorizon/lang/" + lang + ".json").toPath());
                Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(s);
                while (m.find()) map.put(m.group(1), m.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        String v = map.get(key);
        if (v == null) return key;
        return args.length == 0 ? v.replace("%%", "%") : String.format(v, args);
    }
}
