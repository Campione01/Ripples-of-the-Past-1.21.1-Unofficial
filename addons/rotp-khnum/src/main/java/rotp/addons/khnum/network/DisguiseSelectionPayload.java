package rotp.addons.khnum.network;

import java.util.UUID;

import rotp.addons.khnum.KhnumAddon;
import rotp.addons.khnum.disguise.DisguiseMode;
import rotp.addons.khnum.disguise.DisguisePolicy;
import rotp.addons.khnum.entity.KhnumControllerEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DisguiseSelectionPayload(
        byte modeId,
        String selection) implements CustomPacketPayload {
    public static final int MAX_SELECTION_LENGTH = 256;
    public static final Type<DisguiseSelectionPayload> TYPE =
            new Type<>(KhnumAddon.id("select_disguise"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, DisguiseSelectionPayload>
            STREAM_CODEC = StreamCodec.ofMember(
                    DisguiseSelectionPayload::write,
                    DisguiseSelectionPayload::new);

    public DisguiseSelectionPayload {
        if (selection == null
                || selection.length() > MAX_SELECTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid disguise selection length");
        }
    }

    private DisguiseSelectionPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readByte(),
                buffer.readUtf(MAX_SELECTION_LENGTH));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(modeId);
        buffer.writeUtf(selection, MAX_SELECTION_LENGTH);
    }

    public static void handle(
            DisguiseSelectionPayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        DisguiseMode mode = DisguiseMode.byId(payload.modeId());
        KhnumControllerEntity controller =
                KhnumAddon.controller(player);
        long gameTime = player.level().getGameTime();
        if (controller == null
                || !controller.canAcceptSelection(mode, gameTime)
                || !controller.acquireSelectionPermit(gameTime)) {
            return;
        }
        switch (mode) {
            case ENTITY -> applyEntity(
                    controller, player, payload.selection());
            case PLAYER -> applyPlayer(
                    controller, player, payload.selection());
            case NONE -> {
            }
        }
    }

    private static void applyEntity(
            KhnumControllerEntity controller,
            ServerPlayer player,
            String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        EntityType<?> type = DisguisePolicy.entityType(
                id, player.level());
        if (id != null && type != null
                && id.equals(EntityType.getKey(type))) {
            controller.applyEntityDisguise(
                    id, type.getDimensions());
        }
    }

    private static void applyPlayer(
            KhnumControllerEntity controller,
            ServerPlayer player,
            String value) {
        try {
            ServerPlayer target = player.server
                    .getPlayerList()
                    .getPlayer(UUID.fromString(value));
            if (target != null) {
                controller.applyPlayerDisguise(target);
            }
        }
        catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
