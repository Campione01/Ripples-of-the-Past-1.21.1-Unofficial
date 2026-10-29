package rotp.addons.frosttraveler.particle;

import java.util.UUID;

import com.mojang.serialization.MapCodec;

import rotp.addons.frosttraveler.FrostTravelerAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record MeatParticleOptions(UUID ownerUuid)
        implements ParticleOptions {
    public static final MapCodec<MeatParticleOptions> CODEC =
            UUIDUtil.STRING_CODEC
                    .fieldOf("owner_uuid")
                    .xmap(
                            MeatParticleOptions::new,
                            MeatParticleOptions::ownerUuid);
    public static final StreamCodec<ByteBuf, MeatParticleOptions>
            STREAM_CODEC = UUIDUtil.STREAM_CODEC.map(
                    MeatParticleOptions::new,
                    MeatParticleOptions::ownerUuid);

    @Override
    public ParticleType<?> getType() {
        return FrostTravelerAddon.MEAT_PARTICLE.get();
    }

    public static void spawnTrail(
            Level level,
            Entity projectile,
            LivingEntity owner) {
        if (!level.isClientSide() || owner == null) {
            return;
        }
        Vec3 movement = projectile.getDeltaMovement();
        MeatParticleOptions options =
                new MeatParticleOptions(owner.getUUID());
        for (int index = 0; index < 2; index++) {
            level.addParticle(
                    options,
                    projectile.getX()
                            - movement.x
                            + projectile.getRandom().nextFloat()
                                    / 3.0F,
                    projectile.getY()
                            - movement.y
                            + projectile.getRandom().nextFloat()
                                    / 3.0F,
                    projectile.getZ()
                            - movement.z
                            + projectile.getRandom().nextFloat()
                                    / 3.0F,
                    movement.x,
                    movement.y,
                    movement.z);
        }
    }
}
