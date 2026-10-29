package rotp.addons.sexpistols.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import rotp.addons.sexpistols.registry.SexPistolsParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BulletHoleParticleOptions(
        Direction direction, BlockPos blockPos)
        implements ParticleOptions {
    public static final MapCodec<BulletHoleParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Direction.CODEC.fieldOf("direction")
                            .forGetter(
                                    BulletHoleParticleOptions::direction),
                    BlockPos.CODEC.fieldOf("block_pos")
                            .forGetter(
                                    BulletHoleParticleOptions::blockPos))
                    .apply(instance, BulletHoleParticleOptions::new));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            BulletHoleParticleOptions> STREAM_CODEC =
            StreamCodec.ofMember(
                    BulletHoleParticleOptions::write,
                    BulletHoleParticleOptions::new);

    private BulletHoleParticleOptions(
            RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readEnum(Direction.class),
                buffer.readBlockPos());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(direction);
        buffer.writeBlockPos(blockPos);
    }

    @Override
    public ParticleType<?> getType() {
        return SexPistolsParticles.BULLET_HOLE.get();
    }
}
