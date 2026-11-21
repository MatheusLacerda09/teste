package br.com.lasthorizon.akuma.character;

import br.com.lasthorizon.akuma.LastHorizonMod;
import br.com.lasthorizon.akuma.client.CharacterClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Rede da criação de personagem:
 * <ul>
 *   <li>servidor -> cliente {@link OpenCreator}: "abra a tela de criação" (jogador sem personagem);</li>
 *   <li>cliente -> servidor {@link Finalize}: as escolhas finais (o servidor valida tudo e só aceita uma vez);</li>
 *   <li>cliente -> servidor {@link Revealed}: terminou a animação de saída (o servidor dá a conquista);</li>
 *   <li>servidor -> clientes {@link Sync}: personagem de um jogador (ou remoção), para todos verem a skin/nome.</li>
 * </ul>
 */
public final class CharacterNetwork {
    private CharacterNetwork() {}

    private static final String PROTOCOL = "19";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LastHorizonMod.MOD_ID, "character"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.registerMessage(id++, OpenCreator.class, OpenCreator::encode, OpenCreator::decode, OpenCreator::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Finalize.class, Finalize::encode, Finalize::decode, Finalize::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, Sync.class, Sync::encode, Sync::decode, Sync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Revealed.class, Revealed::encode, Revealed::decode, Revealed::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendTo(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static void broadcast(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    // ------------------------------------------------------------------------------------------

    private static void write(FriendlyByteBuf buf, CharacterData d) {
        buf.writeUtf(d.name, 64);
        buf.writeUtf(d.motto, 256);
        buf.writeUtf(d.epithet, 128);
        for (int v : d.ints()) buf.writeVarInt(v);
    }

    private static CharacterData read(FriendlyByteBuf buf) {
        String name = buf.readUtf(64);
        String motto = buf.readUtf(256);
        String epithet = buf.readUtf(128);
        int[] v = new int[CharacterData.INT_COUNT];
        for (int i = 0; i < v.length; i++) v[i] = buf.readVarInt();
        return CharacterData.fromInts(name, motto, epithet, v);
    }

    /** Servidor -> cliente: abre a tela de criação. */
    public static final class OpenCreator {
        void encode(FriendlyByteBuf buf) {}

        static OpenCreator decode(FriendlyByteBuf buf) {
            return new OpenCreator();
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> CharacterClient::requestOpen));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: escolhas finais. */
    public static final class Finalize {
        final CharacterData data;

        public Finalize(CharacterData data) {
            this.data = data;
        }

        void encode(FriendlyByteBuf buf) {
            write(buf, data);
        }

        static Finalize decode(FriendlyByteBuf buf) {
            return new Finalize(read(buf));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null) CharacterEvents.finalizeFor(sender, data);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: a tela de finalização fechou e o braço e a UI já reapareceram (hora de dar a conquista). */
    public static final class Revealed {
        void encode(FriendlyByteBuf buf) {}

        static Revealed decode(FriendlyByteBuf buf) {
            return new Revealed();
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null && CharacterStorage.get(sender) != null) br.com.lasthorizon.akuma.JourneyAdvancementHandler.award(sender);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: o personagem de um jogador (data == null: removido). */
    public static final class Sync {
        final UUID player;
        final CharacterData data;

        public Sync(UUID player, CharacterData data) {
            this.player = player;
            this.data = data;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(player);
            buf.writeBoolean(data != null);
            if (data != null) write(buf, data);
        }

        static Sync decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            return new Sync(id, buf.readBoolean() ? read(buf) : null);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CharacterClient.onSync(player, data)));
            ctx.get().setPacketHandled(true);
        }
    }
}
