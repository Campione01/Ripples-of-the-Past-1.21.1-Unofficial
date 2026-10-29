package rotp.addons.ger.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerRtzClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class GerRtzPayloads {
    public static final int MAX_TRAIL_ENTITIES = 256;
    public static final int MAX_TRAIL_POINTS = 120;

    private GerRtzPayloads() {}

    public static void register(
            RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
                MarkStatePayload.TYPE,
                MarkStatePayload.STREAM_CODEC,
                MarkStatePayload::handle);
        registrar.playToClient(
                StartPayload.TYPE,
                StartPayload.STREAM_CODEC,
                StartPayload::handle);
        registrar.playToClient(
                StopPayload.TYPE,
                StopPayload.STREAM_CODEC,
                StopPayload::handle);
    }

    public record MarkStatePayload(
            UUID owner, boolean available)
            implements CustomPacketPayload {
        public static final Type<MarkStatePayload> TYPE =
                new Type<>(GoldExperienceRequiemAddon.id(
                        "rtz_mark_state"));
        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                MarkStatePayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public MarkStatePayload decode(
                            RegistryFriendlyByteBuf buffer) {
                        return new MarkStatePayload(
                                buffer.readUUID(),
                                buffer.readBoolean());
                    }

                    @Override
                    public void encode(
                            RegistryFriendlyByteBuf buffer,
                            MarkStatePayload payload) {
                        buffer.writeUUID(payload.owner());
                        buffer.writeBoolean(payload.available());
                    }
                };

        private static void handle(
                MarkStatePayload payload,
                IPayloadContext context) {
            GerRtzClientState.syncMark(
                    context.player(),
                    payload.owner(),
                    payload.available());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GhostTrail(
            int entityId, List<Vec3> points) {
        public GhostTrail {
            points = List.copyOf(points);
            if (points.size() > MAX_TRAIL_POINTS) {
                throw new IllegalArgumentException(
                        "RTZ ghost trail exceeds donor bound");
            }
        }
    }

    public record StartPayload(
            UUID owner,
            int sourceEntityId,
            int expandTicks,
            int keepTicks,
            int closeTicks,
            int fadeInTicks,
            int fadeOutTicks,
            int elapsedTicks,
            int mode,
            Vec3 center,
            List<GhostTrail> trails)
            implements CustomPacketPayload {
        public static final Type<StartPayload> TYPE =
                new Type<>(GoldExperienceRequiemAddon.id(
                        "rtz_start"));
        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                StartPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public StartPayload decode(
                            RegistryFriendlyByteBuf buffer) {
                        UUID owner = buffer.readUUID();
                        int sourceEntityId = buffer.readVarInt();
                        int expandTicks = buffer.readVarInt();
                        int keepTicks = buffer.readVarInt();
                        int closeTicks = buffer.readVarInt();
                        int fadeInTicks = buffer.readVarInt();
                        int fadeOutTicks = buffer.readVarInt();
                        int elapsedTicks = buffer.readVarInt();
                        int mode = buffer.readVarInt();
                        Vec3 center = new Vec3(
                                buffer.readDouble(),
                                buffer.readDouble(),
                                buffer.readDouble());
                        int trailCount = buffer.readVarInt();
                        if (trailCount < 0
                                || trailCount > MAX_TRAIL_ENTITIES) {
                            throw new IllegalArgumentException(
                                    "Invalid RTZ ghost trail count");
                        }
                        List<GhostTrail> trails =
                                new ArrayList<>(trailCount);
                        for (int trailIndex = 0;
                                trailIndex < trailCount;
                                trailIndex++) {
                            int entityId = buffer.readVarInt();
                            int pointCount = buffer.readVarInt();
                            if (pointCount < 0
                                    || pointCount
                                            > MAX_TRAIL_POINTS) {
                                throw new IllegalArgumentException(
                                        "Invalid RTZ ghost point count");
                            }
                            List<Vec3> points =
                                    new ArrayList<>(pointCount);
                            for (int pointIndex = 0;
                                    pointIndex < pointCount;
                                    pointIndex++) {
                                points.add(new Vec3(
                                        buffer.readDouble(),
                                        buffer.readDouble(),
                                        buffer.readDouble()));
                            }
                            trails.add(new GhostTrail(
                                    entityId, points));
                        }
                        return new StartPayload(
                                owner,
                                sourceEntityId,
                                expandTicks,
                                keepTicks,
                                closeTicks,
                                fadeInTicks,
                                fadeOutTicks,
                                elapsedTicks,
                                mode,
                                center,
                                trails);
                    }

                    @Override
                    public void encode(
                            RegistryFriendlyByteBuf buffer,
                            StartPayload payload) {
                        if (payload.trails().size()
                                > MAX_TRAIL_ENTITIES) {
                            throw new IllegalArgumentException(
                                    "RTZ ghost trail payload too large");
                        }
                        buffer.writeUUID(payload.owner());
                        buffer.writeVarInt(payload.sourceEntityId());
                        buffer.writeVarInt(payload.expandTicks());
                        buffer.writeVarInt(payload.keepTicks());
                        buffer.writeVarInt(payload.closeTicks());
                        buffer.writeVarInt(payload.fadeInTicks());
                        buffer.writeVarInt(payload.fadeOutTicks());
                        buffer.writeVarInt(payload.elapsedTicks());
                        buffer.writeVarInt(payload.mode());
                        buffer.writeDouble(payload.center().x);
                        buffer.writeDouble(payload.center().y);
                        buffer.writeDouble(payload.center().z);
                        buffer.writeVarInt(payload.trails().size());
                        for (GhostTrail trail : payload.trails()) {
                            buffer.writeVarInt(trail.entityId());
                            buffer.writeVarInt(trail.points().size());
                            for (Vec3 point : trail.points()) {
                                buffer.writeDouble(point.x);
                                buffer.writeDouble(point.y);
                                buffer.writeDouble(point.z);
                            }
                        }
                    }
                };

        public StartPayload {
            if (expandTicks < 0
                    || keepTicks < 1
                    || closeTicks < 0
                    || fadeInTicks < 0
                    || fadeOutTicks < 0
                    || elapsedTicks < 0
                    || mode < 0
                    || mode > 1) {
                throw new IllegalArgumentException(
                        "Invalid RTZ presentation timing");
            }
            trails = List.copyOf(trails);
        }

        private static void handle(
                StartPayload payload,
                IPayloadContext context) {
            GerRtzClientState.start(payload);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StopPayload(UUID owner)
            implements CustomPacketPayload {
        public static final Type<StopPayload> TYPE =
                new Type<>(GoldExperienceRequiemAddon.id(
                        "rtz_stop"));
        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                StopPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public StopPayload decode(
                            RegistryFriendlyByteBuf buffer) {
                        return new StopPayload(buffer.readUUID());
                    }

                    @Override
                    public void encode(
                            RegistryFriendlyByteBuf buffer,
                            StopPayload payload) {
                        buffer.writeUUID(payload.owner());
                    }
                };

        private static void handle(
                StopPayload payload,
                IPayloadContext context) {
            GerRtzClientState.stop(payload.owner());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
