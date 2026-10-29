package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.core.network.s2c.StandEntitySoundPacket;

/**
 * A server player in the server's player list and level, whose outgoing packets a test reads:
 * what a client at that spot would be told to play or show.
 */
final class RecordingServerPlayer implements AutoCloseable {
    final ServerPlayer player;
    private final Recorder connection;
    private final EmbeddedChannel channel;
    private boolean closed;

    RecordingServerPlayer(GameTestHelper helper, String name, Vec3 pos) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        connection = new Recorder();
        channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setPos(pos);
        clear();
    }

    void clear() {
        connection.packets.clear();
    }

    <T> List<T> packets(Class<T> type) {
        List<T> sent = new ArrayList<>();
        for (Packet<?> packet : connection.packets) {
            if (type.isInstance(packet)) {
                sent.add(type.cast(packet));
            }
        }
        return sent;
    }

    List<ClientboundSoundPacket> sounds(SoundEvent sound) {
        return packets(ClientboundSoundPacket.class).stream()
                .filter(played -> played.getSound().value() == sound)
                .toList();
    }

    List<ClientboundLevelParticlesPacket> particles() {
        return packets(ClientboundLevelParticlesPacket.class);
    }

    List<StandEntitySoundPacket> standSounds(SoundEvent sound) {
        List<StandEntitySoundPacket> sounds = new ArrayList<>();
        for (Packet<?> packet : connection.packets) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof StandEntitySoundPacket standSound
                    && standSound.sound().value() == sound) {
                sounds.add(standSound);
            }
        }
        return sounds;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            player.connection.disconnect(Component.literal("C-Moon packet test complete"));
        }
        finally {
            channel.finishAndReleaseAll();
        }
    }

    private static final class Recorder extends Connection {
        final List<Packet<?>> packets = new ArrayList<>();

        Recorder() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
            record(packet);
            super.send(packet, listener, flush);
        }

        private void record(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(this::record);
            }
            else {
                packets.add(packet);
            }
        }
    }
}
