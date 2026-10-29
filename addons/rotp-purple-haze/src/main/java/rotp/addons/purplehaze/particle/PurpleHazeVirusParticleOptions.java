package rotp.addons.purplehaze.particle;

import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import rotp.addons.purplehaze.PurpleHazeAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PurpleHazeVirusParticleOptions(
        int controllerId,
        UUID ownerUuid) implements ParticleOptions {
    public static final MapCodec<PurpleHazeVirusParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    com.mojang.serialization.Codec.INT
                            .fieldOf("controller_id")
                            .forGetter(
                                    PurpleHazeVirusParticleOptions
                                            ::controllerId),
                    UUIDUtil.STRING_CODEC
                            .fieldOf("owner_uuid")
                            .forGetter(
                                    PurpleHazeVirusParticleOptions
                                            ::ownerUuid))
                    .apply(
                            instance,
                            PurpleHazeVirusParticleOptions::new));
    public static final StreamCodec<
            ByteBuf,
            PurpleHazeVirusParticleOptions> STREAM_CODEC =
                    StreamCodec.composite(
                            ByteBufCodecs.VAR_INT,
                            PurpleHazeVirusParticleOptions
                                    ::controllerId,
                            UUIDUtil.STREAM_CODEC,
                            PurpleHazeVirusParticleOptions
                                    ::ownerUuid,
                            PurpleHazeVirusParticleOptions::new);

    @Override
    public ParticleType<?> getType() {
        return PurpleHazeAddon.PURPLE_HAZE_VIRUS_PARTICLE.get();
    }
}
