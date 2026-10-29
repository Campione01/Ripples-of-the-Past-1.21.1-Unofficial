package rotp.addons.purplehaze.particle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public final class PurpleHazeVirusParticleType
        extends ParticleType<PurpleHazeVirusParticleOptions> {
    public PurpleHazeVirusParticleType() {
        super(false);
    }

    @Override
    public MapCodec<PurpleHazeVirusParticleOptions> codec() {
        return PurpleHazeVirusParticleOptions.CODEC;
    }

    @Override
    public StreamCodec<
            ? super RegistryFriendlyByteBuf,
            PurpleHazeVirusParticleOptions> streamCodec() {
        return PurpleHazeVirusParticleOptions.STREAM_CODEC;
    }
}
