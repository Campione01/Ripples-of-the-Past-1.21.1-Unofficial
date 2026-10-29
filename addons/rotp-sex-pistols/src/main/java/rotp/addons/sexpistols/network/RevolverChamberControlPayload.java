package rotp.addons.sexpistols.network;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RevolverChamberControlPayload(int operation)
        implements CustomPacketPayload {
    public static final int CLOSE_CYLINDER = 0;
    public static final int SWITCH_FORWARD = 1;
    public static final int SWITCH_BACKWARD = 2;

    public static final Type<RevolverChamberControlPayload> TYPE =
            new Type<>(SexPistolsIds.id(
                    "revolver_chamber_control"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RevolverChamberControlPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    RevolverChamberControlPayload::write,
                    RevolverChamberControlPayload::new);

    private RevolverChamberControlPayload(
            RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(operation);
    }

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                TYPE,
                STREAM_CODEC,
                RevolverChamberControlPayload::handle);
    }

    private static void handle(
            RevolverChamberControlPayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RevolverItem)
                || !RevolverItem.state(stack).reloadMode()) {
            return;
        }
        switch (payload.operation()) {
            case CLOSE_CYLINDER -> RevolverItem.closeReloadMode(
                    player.serverLevel(), player, stack);
            case SWITCH_FORWARD -> RevolverItem.switchSelectedChamber(
                    player.serverLevel(), player, stack, true);
            case SWITCH_BACKWARD -> RevolverItem.switchSelectedChamber(
                    player.serverLevel(), player, stack, false);
            default -> {
                // Unknown operations are intentionally ignored.
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
