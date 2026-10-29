package rotp.addons.ultimate_lifeform.network;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.client.UltimateLifeformClientEffects;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 played the user-only sounds on the user's client at the client's own position (Player.playSound, playLocalSound
 * in perform and in the held tick); a sound packet from the server would sit at the server's position of the user.
 */
public record UltimateUserSoundPayload(
        ResourceLocation sound,
        int source,
        float volume,
        float pitch) implements CustomPacketPayload {
    public static final Type<UltimateUserSoundPayload> TYPE =
            new Type<>(UltimateLifeformAddon.id("user_sound"));
    public static final StreamCodec<ByteBuf, UltimateUserSoundPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    UltimateUserSoundPayload::sound,
                    ByteBufCodecs.VAR_INT,
                    UltimateUserSoundPayload::source,
                    ByteBufCodecs.FLOAT,
                    UltimateUserSoundPayload::volume,
                    ByteBufCodecs.FLOAT,
                    UltimateUserSoundPayload::pitch,
                    UltimateUserSoundPayload::new);

    public static void send(
            ServerPlayer player,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch) {
        ResourceLocation id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        if (id != null) {
            PacketDistributor.sendToPlayer(
                    player,
                    new UltimateUserSoundPayload(
                            id, source.ordinal(), volume, pitch));
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TYPE,
                STREAM_CODEC,
                UltimateUserSoundPayload::handle);
    }

    private static void handle(
            UltimateUserSoundPayload payload,
            IPayloadContext context) {
        UltimateLifeformClientEffects.playAtOwnPosition(
                payload.sound, payload.source, payload.volume, payload.pitch);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
