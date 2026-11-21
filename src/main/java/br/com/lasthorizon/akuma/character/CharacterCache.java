package br.com.lasthorizon.akuma.character;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Personagens conhecidos pelo CLIENTE (o seu e os dos outros jogadores), preenchido pelos pacotes de sincronização.
 * Fica numa classe comum (sem código de tela) para o nome e a skin poderem ser lidos sem risco no servidor dedicado;
 * no servidor o mapa simplesmente fica vazio (lá a fonte é {@link CharacterStorage}).
 */
public final class CharacterCache {
    private CharacterCache() {}

    private static final Map<UUID, CharacterData> MAP = new ConcurrentHashMap<>();

    public static CharacterData get(UUID id) {
        return MAP.get(id);
    }

    public static void put(UUID id, CharacterData d) {
        MAP.put(id, d);
    }

    public static void remove(UUID id) {
        MAP.remove(id);
    }

    public static void clear() {
        MAP.clear();
    }
}
