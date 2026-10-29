package rotp.addons.sexpistols.particle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class BulletHoleParticleType
        extends ParticleType<BulletHoleParticleOptions> {
    public BulletHoleParticleType() {
        super(true);
    }

    @Override
    public MapCodec<BulletHoleParticleOptions> codec() {
        return BulletHoleParticleOptions.CODEC;
    }

    @Override
    public StreamCodec<
            ? super RegistryFriendlyByteBuf,
            BulletHoleParticleOptions> streamCodec() {
        return BulletHoleParticleOptions.STREAM_CODEC;
    }
}
