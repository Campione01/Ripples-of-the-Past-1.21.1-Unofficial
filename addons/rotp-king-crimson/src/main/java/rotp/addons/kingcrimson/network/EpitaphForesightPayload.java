package rotp.addons.kingcrimson.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.addons.kingcrimson.client.EpitaphForesightClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → Epitaph user: the foreseen positions of nearby creatures and the foreseen flight of projectiles. */
public record EpitaphForesightPayload(
        List<Creature> creatures,
        List<Flight> flights) implements CustomPacketPayload {
    private static final int MAX_POINTS = 64;

    public static final Type<EpitaphForesightPayload> TYPE =
            new Type<>(KingCrimsonAddon.id("epitaph_foresight"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            EpitaphForesightPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EpitaphForesightPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    int creatureCount = bounded(buffer.readVarInt(),
                            KingCrimsonEpitaphForesightSpec.MAX_CREATURES);
                    List<Creature> creatures = new ArrayList<>(creatureCount);
                    for (int i = 0; i < creatureCount; i++) {
                        creatures.add(new Creature(
                                buffer.readVarInt(),
                                buffer.readUUID(),
                                readVec(buffer)));
                    }
                    int flightCount = bounded(buffer.readVarInt(),
                            KingCrimsonEpitaphForesightSpec.MAX_PROJECTILES);
                    List<Flight> flights = new ArrayList<>(flightCount);
                    for (int i = 0; i < flightCount; i++) {
                        boolean hitsUser = buffer.readBoolean();
                        int pointCount = bounded(buffer.readVarInt(), MAX_POINTS);
                        List<Vec3> points = new ArrayList<>(pointCount);
                        for (int p = 0; p < pointCount; p++) {
                            points.add(readVec(buffer));
                        }
                        flights.add(new Flight(points, hitsUser));
                    }
                    return new EpitaphForesightPayload(creatures, flights);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        EpitaphForesightPayload payload) {
                    buffer.writeVarInt(payload.creatures().size());
                    for (Creature creature : payload.creatures()) {
                        buffer.writeVarInt(creature.entityId());
                        buffer.writeUUID(creature.entityUuid());
                        writeVec(buffer, creature.foreseen());
                    }
                    buffer.writeVarInt(payload.flights().size());
                    for (Flight flight : payload.flights()) {
                        buffer.writeBoolean(flight.hitsUser());
                        buffer.writeVarInt(flight.points().size());
                        for (Vec3 point : flight.points()) {
                            writeVec(buffer, point);
                        }
                    }
                }
            };

    public EpitaphForesightPayload {
        creatures = List.copyOf(creatures.size() > KingCrimsonEpitaphForesightSpec.MAX_CREATURES
                ? creatures.subList(0, KingCrimsonEpitaphForesightSpec.MAX_CREATURES)
                : creatures);
        flights = List.copyOf(flights.size() > KingCrimsonEpitaphForesightSpec.MAX_PROJECTILES
                ? flights.subList(0, KingCrimsonEpitaphForesightSpec.MAX_PROJECTILES)
                : flights);
    }

    private static int bounded(int size, int max) {
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid Epitaph foresight size: " + size);
        }
        return size;
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 vec) {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    public static void handle(
            EpitaphForesightPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> EpitaphForesightClient.accept(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Creature(int entityId, UUID entityUuid, Vec3 foreseen) {}

    public record Flight(List<Vec3> points, boolean hitsUser) {
        public Flight {
            points = List.copyOf(points.size() > MAX_POINTS ? points.subList(0, MAX_POINTS) : points);
        }
    }
}
