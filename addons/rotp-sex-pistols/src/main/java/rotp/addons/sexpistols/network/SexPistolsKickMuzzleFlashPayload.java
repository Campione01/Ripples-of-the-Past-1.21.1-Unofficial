package rotp.addons.sexpistols.network;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SexPistolsKickMuzzleFlashPayload(
        int entityId,
        boolean bulletAnchor,
        float random,
        boolean critical)
        implements CustomPacketPayload {
    public static final Type<SexPistolsKickMuzzleFlashPayload> TYPE =
            new Type<>(SexPistolsIds.id(
                    SexPistolsIds.KICK_MUZZLE_FLASH_PATH));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SexPistolsKickMuzzleFlashPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    SexPistolsKickMuzzleFlashPayload::write,
                    SexPistolsKickMuzzleFlashPayload::new);

    private SexPistolsKickMuzzleFlashPayload(
            RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeBoolean(bulletAnchor);
        buffer.writeFloat(random);
        buffer.writeBoolean(critical);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
