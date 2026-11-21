package br.com.lasthorizon.akuma.character;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * A criação de personagem não dá mais nenhum bônus (a escolha de Origem foi removida: o jogador decide o que quer
 * ser depois de criar o personagem). Esta classe só limpa os modificadores que versões antigas aplicaram, para que
 * nenhum jogador fique com um bônus de origem que não existe mais.
 */
public final class CharacterAttributes {
    private CharacterAttributes() {}

    private static final UUID ID_PIRATE = UUID.fromString("b0a3e1f2-6c0d-4a55-9f0e-5a1d00000001");
    private static final UUID ID_MARINE = UUID.fromString("b0a3e1f2-6c0d-4a55-9f0e-5a1d00000002");
    private static final UUID ID_NAVIGATOR = UUID.fromString("b0a3e1f2-6c0d-4a55-9f0e-5a1d00000003");
    private static final UUID ID_COOK = UUID.fromString("b0a3e1f2-6c0d-4a55-9f0e-5a1d00000004");

    /** Remove os bônus de origem antigos (o personagem {@code d} não influencia mais nada). */
    public static void apply(ServerPlayer p, CharacterData d) {
        clear(p, Attributes.ATTACK_DAMAGE, ID_PIRATE);
        clear(p, Attributes.ARMOR, ID_MARINE);
        clear(p, Attributes.MOVEMENT_SPEED, ID_NAVIGATOR);
        clear(p, Attributes.MAX_HEALTH, ID_COOK);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void clear(ServerPlayer p, Attribute attr, UUID id) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }
}
