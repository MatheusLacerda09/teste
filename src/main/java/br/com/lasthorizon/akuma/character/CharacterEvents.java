package br.com.lasthorizon.akuma.character;

import br.com.lasthorizon.akuma.LastHorizonMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lado do servidor da criação de personagem: quem ainda não criou recebe o pedido para abrir a tela ao entrar; as
 * escolhas chegam por {@link CharacterNetwork.Finalize} e são gravadas no jogador; o personagem é reenviado a todos
 * (skin e nome) e os bônus da origem são reaplicados quando preciso. Roda nos dois lados só para o nome exibido.
 */
@Mod.EventBusSubscriber(modid = LastHorizonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CharacterEvents {
    private CharacterEvents() {}

    /** Aceita (uma única vez por jogador) as escolhas finais vindas do cliente. */
    public static void finalizeFor(ServerPlayer sp, CharacterData d) {
        if (CharacterStorage.get(sp) != null) return;          // já finalizado: não dá para refazer
        if (d == null || !d.isValid()) return;                 // o servidor nunca confia no cliente
        CharacterStorage.set(sp, d);
        CharacterAttributes.apply(sp, d);
        sp.refreshDisplayName();
        sp.refreshDimensions();                                // a altura escolhida passa a valer na hitbox (a largura acompanha)
        CharacterNetwork.broadcast(new CharacterNetwork.Sync(sp.getUUID(), d));
        // a mensagem de boas-vindas no chat é montada no CLIENTE de quem criou (CharacterClient.sayWelcome): só essa pessoa a vê
    }

    /** Apaga o personagem e pede para o jogador criar outro (comando de administrador). */
    public static void reset(ServerPlayer sp) {
        CharacterStorage.clear(sp);
        CharacterAttributes.apply(sp, null);
        sp.refreshDisplayName();
        sp.refreshDimensions();
        CharacterNetwork.broadcast(new CharacterNetwork.Sync(sp.getUUID(), null));
        CharacterNetwork.sendTo(sp, new CharacterNetwork.OpenCreator());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.getServer() == null) return;
        // 1) o jogador que entrou fica sabendo do personagem de quem já está online
        for (ServerPlayer other : sp.getServer().getPlayerList().getPlayers()) {
            if (other == sp) continue;
            CharacterData od = CharacterStorage.get(other);
            if (od != null) CharacterNetwork.sendTo(sp, new CharacterNetwork.Sync(other.getUUID(), od));
        }
        // 2) o próprio: já tem personagem (reaplica e avisa todos) ou precisa criar
        CharacterData mine = CharacterStorage.get(sp);
        CharacterAttributes.apply(sp, mine);
        if (mine != null) {
            sp.refreshDisplayName();
            sp.refreshDimensions();
            CharacterNetwork.broadcast(new CharacterNetwork.Sync(sp.getUUID(), mine));
        } else {
            CharacterNetwork.sendTo(sp, new CharacterNetwork.OpenCreator());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            CharacterAttributes.apply(sp, CharacterStorage.get(sp));
            sp.refreshDimensions();
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) CharacterAttributes.apply(sp, CharacterStorage.get(sp));
    }

    /** Hitbox e altura dos olhos seguem a altura do personagem (servidor e cliente calculam igual, pelo {@link BodyScale}); a largura acompanha a altura. */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof Player p) || p.level() == null) return;
        CharacterData d = p.level().isClientSide ? CharacterCache.get(p.getUUID()) : CharacterStorage.get(p);
        if (d == null) return;
        float s = BodyScale.of(d.height);
        if (s == 1.0F) return;
        EntityDimensions dim = e.getNewSize().scale(s, s);
        e.setNewSize(dim, true);                           // true: a altura dos olhos acompanha
    }

    /** Morte e volta do End criam um jogador novo: copia o registro do personagem para ele. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CharacterStorage.copy(e.getOriginal(), e.getEntity());
    }

    /** Nome do personagem no lugar do nome da conta (sobre a cabeça, no chat). */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat e) {
        Player p = e.getEntity();
        CharacterData d = p.level().isClientSide ? CharacterCache.get(p.getUUID()) : CharacterStorage.get(p);
        if (d != null) e.setDisplayname(Component.literal(d.name));
    }

    /** Enquanto não criou o personagem o jogador está de olho na tela de criação: ninguém o machuca. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && CharacterStorage.get(sp) == null
                && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
        }
    }
}
