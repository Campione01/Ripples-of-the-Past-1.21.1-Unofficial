package rotp.addons.tusk.particle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class NailSwipeParticleType
        extends ParticleType<NailSwipeParticleOptions> {
    public NailSwipeParticleType() {
        super(false);
    }

    @Override
    public MapCodec<NailSwipeParticleOptions> codec() {
        return NailSwipeParticleOptions.CODEC;
    }

    @Override
    public StreamCodec<
            ? super RegistryFriendlyByteBuf,
            NailSwipeParticleOptions> streamCodec() {
        return NailSwipeParticleOptions.STREAM_CODEC;
    }
}
