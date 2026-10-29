package rotp.addons.tusk.particle;

import java.util.UUID;

import com.mojang.serialization.MapCodec;

import rotp.addons.tusk.TuskAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.StreamCodec;

public record NailSwipeParticleOptions(UUID ownerUuid)
        implements ParticleOptions {
    public static final MapCodec<NailSwipeParticleOptions> CODEC =
            UUIDUtil.STRING_CODEC
                    .fieldOf("owner_uuid")
                    .xmap(
                            NailSwipeParticleOptions::new,
                            NailSwipeParticleOptions::ownerUuid);
    public static final StreamCodec<ByteBuf, NailSwipeParticleOptions>
            STREAM_CODEC = UUIDUtil.STREAM_CODEC.map(
                    NailSwipeParticleOptions::new,
                    NailSwipeParticleOptions::ownerUuid);

    @Override
    public ParticleType<?> getType() {
        return TuskAddon.NAIL_SWIPE.get();
    }
}
