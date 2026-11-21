package br.com.lasthorizon.akuma.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Guarda o personagem NO PRÓPRIO JOGADOR (NBT persistente do Forge, "ForgeData"). Esse dado é gravado junto com o
 * jogador dentro do mundo, o que dá exatamente o comportamento pedido:
 * <ul>
 *   <li>fechar e reabrir o mundo (ou o servidor) mantém o personagem;</li>
 *   <li>mundo novo = jogador novo = sem personagem, tem que criar de novo;</li>
 *   <li>no multiplayer cada jogador (UUID) tem o seu próprio registro.</li>
 * </ul>
 * Só o SERVIDOR lê e grava aqui; o cliente usa {@link CharacterCache}.
 */
public final class CharacterStorage {
    private CharacterStorage() {}

    private static final String KEY = "lasthorizon_character";

    /** O personagem finalizado do jogador, ou null se ainda não criou (ou se o dado salvo estiver inválido). */
    public static CharacterData get(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEY, 10)) return null;
        CompoundTag t = root.getCompound(KEY);
        if (!t.getBoolean("done")) return null;
        int[] v = t.getIntArray("v");
        if (v.length < 8 || v.length > CharacterData.INT_COUNT) return null;       // registros antigos (sem gênero, idade, mar, sonho, personalidade) são completados com o valor inicial
        CharacterData d = CharacterData.fromInts(t.getString("name"), t.getString("motto"), t.getString("epithet"), v);
        return d.isValid() ? d : null;
    }

    public static void set(Player p, CharacterData d) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("done", true);
        t.putString("name", d.name);
        t.putString("motto", d.motto);
        t.putString("epithet", d.epithet);
        t.putIntArray("v", d.ints());
        p.getPersistentData().put(KEY, t);
    }

    public static void clear(Player p) {
        p.getPersistentData().remove(KEY);
    }

    /** Copia o registro de um jogador para outro (morte, volta do End). */
    public static void copy(Player from, Player to) {
        CompoundTag root = from.getPersistentData();
        if (root.contains(KEY, 10)) to.getPersistentData().put(KEY, root.getCompound(KEY).copy());
    }
}
