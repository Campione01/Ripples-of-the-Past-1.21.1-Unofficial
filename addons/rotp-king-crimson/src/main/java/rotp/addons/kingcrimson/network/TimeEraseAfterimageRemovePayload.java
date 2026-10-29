package rotp.addons.kingcrimson.network;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.KingCrimsonAfterimageClientController;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TimeEraseAfterimageRemovePayload(
        int[] entityIds) implements CustomPacketPayload {
    private static final int MAX_AFTERIMAGE_IDS = 65536;

    public static final Type<TimeEraseAfterimageRemovePayload> TYPE =
            new Type<>(KingCrimsonAddon.id(
                    "time_erase_afterimage_remove"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TimeEraseAfterimageRemovePayload> STREAM_CODEC =
                    StreamCodec.ofMember(
                            TimeEraseAfterimageRemovePayload::encode,
                            TimeEraseAfterimageRemovePayload::decode);

    public TimeEraseAfterimageRemovePayload {
        entityIds = entityIds.clone();
    }

    @Override
    public int[] entityIds() {
        return entityIds.clone();
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarIntArray(entityIds);
    }

    private static TimeEraseAfterimageRemovePayload decode(
            RegistryFriendlyByteBuf buffer) {
        return new TimeEraseAfterimageRemovePayload(
                buffer.readVarIntArray(MAX_AFTERIMAGE_IDS));
    }

    public static void handle(
            TimeEraseAfterimageRemovePayload payload,
            IPayloadContext context) {
        context.enqueueWork(() ->
                KingCrimsonAfterimageClientController.remove(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
