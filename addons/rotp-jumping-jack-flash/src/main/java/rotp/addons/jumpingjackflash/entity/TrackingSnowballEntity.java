package rotp.addons.jumpingjackflash.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.addons.jumpingjackflash.JumpingJackFlashAddon;
import rotp.addons.jumpingjackflash.state.GravityLeaseService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TrackingSnowballEntity
        extends ThrowableItemProjectile {
    public static final float SPEED = 1.5F;
    public static final float DAMAGE = 4.0F;
    public static final int LIFETIME_TICKS = 100;

    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(
                    TrackingSnowballEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    public TrackingSnowballEntity(
            EntityType<? extends TrackingSnowballEntity> type,
            Level level) {
        super(type, level);
    }

    public void setTarget(LivingEntity target) {
        entityData.set(
                TARGET_UUID,
                target != null
                        ? Optional.of(target.getUUID())
                        : Optional.empty());
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            if (!(getOwner() instanceof LivingEntity owner)
                    || !owner.isAlive()
                    || owner.level() != level()
                    || !JumpingJackFlashAddon.isUser(owner)) {
                discard();
                return;
            }
            LivingEntity target = target();
            if (target != null
                    && target.isAlive()
                    && target.level() == level()) {
                Vec3 toTarget = target.getBoundingBox().getCenter()
                        .subtract(position());
                if (toTarget.lengthSqr() > 1.0D) {
                    setDeltaMovement(toTarget.normalize().scale(SPEED));
                }
            }
        }
        super.tick();
        if (!level().isClientSide() && tickCount > LIFETIME_TICKS) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel serverLevel)
                || !(hit.getEntity() instanceof LivingEntity target)
                || !(getOwner() instanceof LivingEntity owner)
                || !JumpingJackFlashAddon.isUser(owner)
                || target == owner) {
            return;
        }
        GravityLeaseService.applySnowball(target, owner.getUUID());
        target.hurt(
                serverLevel.damageSources().thrown(this, owner),
                DAMAGE);
        serverLevel.sendParticles(
                ParticleTypes.EXPLOSION,
                target.getX(),
                target.getY() + target.getBbHeight() * 0.5D,
                target.getZ(),
                50,
                1.5D,
                1.5D,
                1.5D,
                0.02D);
        serverLevel.playSound(
                null,
                target.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                1.0F,
                1.2F);
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide()) {
            discard();
        }
    }

    private LivingEntity target() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity target = entityData.get(TARGET_UUID)
                .map(serverLevel::getEntity)
                .orElse(null);
        return target instanceof LivingEntity living ? living : null;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET_UUID, Optional.empty());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(TARGET_UUID)
                .ifPresent(uuid -> tag.putUUID("Target", uuid));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                TARGET_UUID,
                tag.hasUUID("Target")
                        ? Optional.of(tag.getUUID("Target"))
                        : Optional.empty());
    }
}
