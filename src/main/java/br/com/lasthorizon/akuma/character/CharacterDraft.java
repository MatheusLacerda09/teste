package br.com.lasthorizon.akuma.character;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Rascunho da criação de personagem: o que o jogador já escolheu mas ainda não selou. Fica num arquivo pequeno de
 * texto no computador do jogador (nunca no mundo), para a criação não se perder se o jogo fechar ou travar no meio.
 * Apagado ao selar o personagem.
 *
 * <p>Java puro (sem classes do Minecraft). Um arquivo corrompido, de versão antiga ou com valores fora das faixas
 * nunca derruba nada: {@link #load} devolve {@code null} e a criação começa do zero.
 */
public final class CharacterDraft {
    /** As escolhas (o nome e o lema podem estar incompletos: ainda estavam sendo digitados). */
    public final CharacterData data;
    /** Abas já abertas e abas em que o jogador fez ao menos uma escolha (um bit por aba). */
    public final int visited, touched;
    /** Aba aberta quando o rascunho foi salvo. */
    public final int tab;
    /** Quantas vezes o jogador já sorteou a alma (o limite é aplicado por quem restaura o rascunho). */
    public final int soulRolls;

    public CharacterDraft(CharacterData data, int visited, int touched, int tab, int soulRolls) {
        this.data = data;
        this.visited = visited;
        this.touched = touched;
        this.tab = tab;
        this.soulRolls = soulRolls;
    }

    private static final String VERSION = "2";   // 2: a aba Origem saiu, então os números das abas mudaram (rascunhos antigos são descartados)

    public static void save(Path file, CharacterDraft d) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", VERSION);
        p.setProperty("name", d.data.name);
        p.setProperty("motto", d.data.motto);
        p.setProperty("epithet", d.data.epithet);
        StringBuilder sb = new StringBuilder();
        for (int v : d.data.ints()) sb.append(sb.length() == 0 ? "" : ",").append(v);
        p.setProperty("v", sb.toString());
        p.setProperty("visited", Integer.toString(d.visited));
        p.setProperty("touched", Integer.toString(d.touched));
        p.setProperty("tab", Integer.toString(d.tab));
        p.setProperty("soulRolls", Integer.toString(d.soulRolls));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, "Sea Sovereign: rascunho da criacao de personagem");
        }
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);   // escrita em arquivo temporário: nunca deixa o rascunho pela metade
        }
    }

    /** O rascunho salvo, ou {@code null} se não existe ou não serve. */
    public static CharacterDraft load(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 4096) return null;
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            if (!VERSION.equals(p.getProperty("version"))) return null;
            String[] parts = p.getProperty("v", "").split(",");
            if (parts.length < 8 || parts.length > CharacterData.INT_COUNT) return null;
            int[] v = new int[parts.length];
            for (int i = 0; i < v.length; i++) v[i] = Integer.parseInt(parts[i].trim());
            String name = cleanName(p.getProperty("name", ""));
            String motto = p.getProperty("motto", "");
            if (!CharacterOptions.validMotto(motto)) motto = "";
            String epithet = p.getProperty("epithet", "");
            if (!CharacterOptions.validEpithet(epithet)) epithet = "";
            CharacterData d = CharacterData.fromInts(name, motto, epithet, v);
            if (!d.indexesValid()) return null;
            int mask = (1 << 30) - 1;
            int visited = Integer.parseInt(p.getProperty("visited", "0")) & mask;
            int touched = Integer.parseInt(p.getProperty("touched", "0")) & mask;
            int tab = Integer.parseInt(p.getProperty("tab", "0"));
            int soulRolls = Integer.parseInt(p.getProperty("soulRolls", "0"));
            return new CharacterDraft(d, visited, touched, tab, soulRolls);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
        } catch (IOException ignored) {
            // sem permissão para apagar: o rascunho antigo só será sobrescrito na próxima criação
        }
    }

    /** Um nome que ainda está sendo digitado pode ser curto ou ter espaço na ponta; o que nunca volta é texto com caractere proibido ou grande demais. */
    private static String cleanName(String n) {
        if (n.codePointCount(0, n.length()) > CharacterOptions.NAME_MAX) return "";
        return CharacterOptions.firstBadChar(n).isEmpty() ? n : "";
    }
}
