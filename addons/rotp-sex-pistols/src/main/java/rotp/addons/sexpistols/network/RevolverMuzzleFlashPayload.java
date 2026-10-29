package rotp.addons.sexpistols.network;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RevolverMuzzleFlashPayload(
        int entityId,
        boolean aiming,
        float random,
        boolean applyRecoil)
        implements CustomPacketPayload {
    public static final Type<RevolverMuzzleFlashPayload> TYPE =
            new Type<>(SexPistolsIds.id(
                    SexPistolsIds.REVOLVER_MUZZLE_FLASH_PATH));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RevolverMuzzleFlashPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    RevolverMuzzleFlashPayload::write,
                    RevolverMuzzleFlashPayload::new);

    private RevolverMuzzleFlashPayload(
            RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeBoolean(aiming);
        buffer.writeFloat(random);
        buffer.writeBoolean(applyRecoil);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
