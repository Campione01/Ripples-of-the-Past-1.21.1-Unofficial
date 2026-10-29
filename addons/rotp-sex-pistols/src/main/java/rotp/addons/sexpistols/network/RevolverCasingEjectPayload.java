package rotp.addons.sexpistols.network;

import java.util.ArrayList;
import java.util.List;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.casing.CasingEjectionSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RevolverCasingEjectPayload(
        int entityId, List<CasingEjectionSpec> specs)
        implements CustomPacketPayload {
    public static final Type<RevolverCasingEjectPayload> TYPE =
            new Type<>(SexPistolsIds.id(
                    SexPistolsIds.REVOLVER_CASING_EJECT_PATH));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RevolverCasingEjectPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    RevolverCasingEjectPayload::write,
                    RevolverCasingEjectPayload::new);

    private RevolverCasingEjectPayload(
            RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), readSpecs(buffer));
    }

    public RevolverCasingEjectPayload {
        specs = List.copyOf(specs);
        if (specs.size() > CasingEjectionSpec.MAX_CASING_COUNT) {
            throw new IllegalArgumentException(
                    "Too many revolver casing specs");
        }
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeVarInt(specs.size());
        specs.forEach(spec -> writeSpec(buffer, spec));
    }

    private static List<CasingEjectionSpec> readSpecs(
            RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0
                || size > CasingEjectionSpec.MAX_CASING_COUNT) {
            throw new IllegalArgumentException(
                    "Invalid revolver casing spec count: " + size);
        }
        List<CasingEjectionSpec> specs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            specs.add(readSpec(buffer));
        }
        return specs;
    }

    private static CasingEjectionSpec readSpec(
            RegistryFriendlyByteBuf buffer) {
        return new CasingEjectionSpec(
                buffer.readLong(),
                buffer.readLong(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readUnsignedByte(),
                buffer.readFloat());
    }

    private static void writeSpec(
            RegistryFriendlyByteBuf buffer,
            CasingEjectionSpec spec) {
        buffer.writeLong(spec.id());
        buffer.writeLong(spec.spawnGameTime());
        buffer.writeDouble(spec.x());
        buffer.writeDouble(spec.y());
        buffer.writeDouble(spec.z());
        buffer.writeDouble(spec.xVelocity());
        buffer.writeDouble(spec.yVelocity());
        buffer.writeDouble(spec.zVelocity());
        buffer.writeVarInt(spec.lifetime());
        buffer.writeFloat(spec.size());
        buffer.writeByte(spec.landingSound());
        buffer.writeFloat(spec.landingPitch());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
