package rotp.addons.frosttraveler.particle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class MeatParticleType
        extends ParticleType<MeatParticleOptions> {
    public MeatParticleType() {
        super(false);
    }

    @Override
    public MapCodec<MeatParticleOptions> codec() {
        return MeatParticleOptions.CODEC;
    }

    @Override
    public StreamCodec<
            ? super RegistryFriendlyByteBuf,
            MeatParticleOptions> streamCodec() {
        return MeatParticleOptions.STREAM_CODEC;
    }
}
