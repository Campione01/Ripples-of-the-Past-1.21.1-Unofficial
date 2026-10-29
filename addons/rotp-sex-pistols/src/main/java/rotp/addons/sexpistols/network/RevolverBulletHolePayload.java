package rotp.addons.sexpistols.network;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record RevolverBulletHolePayload(
        Vec3 position,
        Direction direction,
        BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<RevolverBulletHolePayload> TYPE =
            new Type<>(SexPistolsIds.id("bullet_hole_visual"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RevolverBulletHolePayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    RevolverBulletHolePayload::write,
                    RevolverBulletHolePayload::new);

    private RevolverBulletHolePayload(
            RegistryFriendlyByteBuf buffer) {
        this(
                new Vec3(
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble()),
                buffer.readEnum(Direction.class),
                buffer.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
        buffer.writeEnum(direction);
        buffer.writeBlockPos(blockPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
