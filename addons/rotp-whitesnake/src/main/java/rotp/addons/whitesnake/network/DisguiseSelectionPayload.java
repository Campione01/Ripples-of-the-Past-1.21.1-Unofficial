package rotp.addons.whitesnake.network;

import java.util.Objects;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disguise.DisguiseTypePolicy;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public record DisguiseSelectionPayload(
        UUID sessionId,
        ResourceLocation candidateEntityType)
        implements CustomPacketPayload {
    public static final int MAX_ID_LENGTH = 256;
    public static final Type<DisguiseSelectionPayload> TYPE =
            new Type<>(WhitesnakeAddon.id("select_disguise"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            DisguiseSelectionPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    DisguiseSelectionPayload::write,
                    DisguiseSelectionPayload::new);

    public DisguiseSelectionPayload {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(candidateEntityType);
        if (candidateEntityType.toString().length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Disguise entity type ID is too long");
        }
    }

    private DisguiseSelectionPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                parse(buffer.readUtf(MAX_ID_LENGTH)));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeUtf(candidateEntityType.toString(), MAX_ID_LENGTH);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToClient(
                OpenDisguiseSelectionPayload.TYPE,
                OpenDisguiseSelectionPayload.STREAM_CODEC,
                OpenDisguiseSelectionPayload::handle);
        registrar.playToServer(
                TYPE, STREAM_CODEC, DisguiseSelectionPayload::handle);
    }

    private static void handle(
            DisguiseSelectionPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                applySelection(
                        player,
                        payload.sessionId(),
                        payload.candidateEntityType());
            }
        });
    }

    private static void applySelection(
            ServerPlayer player,
            UUID sessionId,
            ResourceLocation candidateId) {
        if (!WhitesnakeAddon.isActiveWhitesnakeUser(
                player, "disguise")) {
            return;
        }

        StandPower power = StandPower.get(player);
        if (power == null
                || !(power.getSummonedStandEntity()
                        instanceof WhitesnakeEntity whitesnake)
                || whitesnake.getUser() != player
                || whitesnake.level() != player.level()) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (!whitesnake.canAcceptDisguiseSelection(
                sessionId, gameTime)) {
            return;
        }

        EntityType<?> candidate =
                BuiltInRegistries.ENTITY_TYPE
                        .getOptional(candidateId)
                        .orElse(null);
        if (candidate == null
                || !candidateId.equals(EntityType.getKey(candidate))
                || !DisguiseTypePolicy.isAllowed(
                        candidate, player.level())) {
            return;
        }
        if (!whitesnake.acquireDisguiseRequestPermit(gameTime)) {
            return;
        }
        whitesnake.applyDisguise(
                sessionId, candidateId, gameTime);
    }

    private static ResourceLocation parse(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException(
                    "Invalid disguise entity type ID");
        }
        return id;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
