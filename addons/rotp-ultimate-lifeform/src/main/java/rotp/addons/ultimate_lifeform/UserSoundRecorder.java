package rotp.addons.ultimate_lifeform;

import java.util.function.Consumer;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import rotp.addons.ultimate_lifeform.network.UltimateUserSoundPayload;

/**
 * The user-only sounds of the Ultimate Lifeform go to the user's client as a payload (1.16 played them there at the
 * client's own position); a GameTest player records them in place of its connection.
 */
final class UserSoundRecorder {
    private UserSoundRecorder() {}

    // at: where the server had the player when the sound was sent
    record Heard(SoundEvent sound, SoundSource source, float volume, float pitch, Vec3 at) {
        String line() {
            return sound.getLocation() + " " + source.getName() + " " + volume + " " + pitch;
        }
    }

    static void attach(ServerPlayer player, Consumer<Heard> sink) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(player.getServer(), connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet) {
                record(player, packet, sink);
            }

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
                record(player, packet, sink);
            }
        };
    }

    private static void record(ServerPlayer player, Packet<?> packet, Consumer<Heard> sink) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            bundle.subPackets().forEach(part -> record(player, part, sink));
        }
        else if (packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof UltimateUserSoundPayload sound) {
            sink.accept(new Heard(BuiltInRegistries.SOUND_EVENT.get(sound.sound()),
                    SoundSource.values()[sound.source()], sound.volume(), sound.pitch(), player.position()));
        }
    }
}
