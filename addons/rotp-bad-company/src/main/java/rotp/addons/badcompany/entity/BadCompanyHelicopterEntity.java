package rotp.addons.badcompany.entity;

import javax.annotation.Nullable;

import rotp.addons.badcompany.BadCompanyAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BadCompanyHelicopterEntity
        extends BadCompanyUnitEntity {
    public static final double COMBAT_RANGE = 30.0D;
    public static final int FIRE_INTERVAL = 5;
    public static final int MAX_FIRE_INTERVAL = 40;
    public static final int MISSILE_CAP = 6;
    public static final int MISSILE_RELOAD_TICKS = 600;
    public static final float PROJECTILE_SPEED = 1.6F;
    public static final float PROJECTILE_INACCURACY = 0.02F;
    public static final float MODEL_SCALE = 0.16666F;

    private static final EntityDataAccessor<Integer> MISSILES =
            SynchedEntityData.defineId(
                    BadCompanyHelicopterEntity.class,
                    EntityDataSerializers.INT);

    private int reloadTicks;

    public BadCompanyHelicopterEntity(
            EntityType<? extends BadCompanyHelicopterEntity> type,
            Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 70, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                // 1.16 BadHelicopterEntity: armor 10, knockback resistance 4, flying speed 1.0
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, COMBAT_RANGE)
                .add(Attributes.FLYING_SPEED, 1.0D);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MISSILES, MISSILE_CAP);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation =
                new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public void tick() {
        setNoGravity(true);
        super.tick();
        if (!level().isClientSide() && missiles() <= 0) {
            if (++reloadTicks >= MISSILE_RELOAD_TICKS) {
                entityData.set(MISSILES, MISSILE_CAP);
                reloadTicks = 0;
            }
        } else if (missiles() > 0) {
            reloadTicks = 0;
        }
    }

    public int missiles() {
        return entityData.get(MISSILES);
    }

    @Override
    public BadCompanyUnitType unitType() {
        return BadCompanyUnitType.HELICOPTER;
    }

    @Override
    protected double combatRange() {
        return COMBAT_RANGE;
    }

    @Override
    protected int attackInterval() {
        return FIRE_INTERVAL;
    }

    // 1.16 RangedAttackGoal(5, 40, 30): 5 ticks point-blank to 40 at 30 blocks.
    @Override
    protected int attackIntervalAfterShot(LivingEntity target) {
        return Mth.floor(
                (float) Math.sqrt(distanceToSqr(target))
                        / (float) COMBAT_RANGE
                        * (MAX_FIRE_INTERVAL - FIRE_INTERVAL)
                        + FIRE_INTERVAL);
    }

    @Override
    protected double navigationSpeed() {
        return 0.8D;
    }

    @Override
    protected double formationHeightOffset() {
        return 2.0D;
    }

    @Override
    protected boolean performRangedAttack(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target) {
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        if (coordinator == null) {
            return false;
        }
        Vec3 direction = target.getBoundingBox()
                .getCenter()
                .subtract(getEyePosition());
        BadCompanyBulletEntity bullet =
                new BadCompanyBulletEntity(owner, level);
        bullet.bindClosure(
                owner.getUUID(), coordinator.getUUID());
        bullet.bindLifecycleProducer(this);
        bullet.setPos(getX(), getEyeY(), getZ());
        bullet.shoot(
                direction.x,
                direction.y,
                direction.z,
                PROJECTILE_SPEED,
                PROJECTILE_INACCURACY);
        level.addFreshEntity(bullet);
        coordinator.trackTransient(bullet);
        level.playSound(
                null,
                blockPosition(),
                BadCompanyAddon.SOLDIER_SHOOT.get(),
                SoundSource.PLAYERS,
                1.0F,
                0.8F + random.nextFloat() * 0.4F);

        if (missiles() > 0
                && owner.distanceToSqr(target) > 36.0D
                && random.nextFloat() < 0.2F) {
            BadCompanyMissileEntity missile =
                    new BadCompanyMissileEntity(owner, level);
            missile.bindClosure(
                    owner.getUUID(), coordinator.getUUID());
            missile.bindLifecycleProducer(this);
            missile.setPos(getX(), getEyeY(), getZ());
            missile.shoot(
                    direction.x,
                    direction.y,
                    direction.z,
                    PROJECTILE_SPEED,
                    PROJECTILE_INACCURACY);
            level.addFreshEntity(missile);
            coordinator.trackTransient(missile);
            entityData.set(MISSILES, missiles() - 1);
            level.playSound(
                    null,
                    blockPosition(),
                    BadCompanyAddon.TANK_SHOOT.get(),
                    SoundSource.PLAYERS,
                    2.0F,
                    0.8F + random.nextFloat() * 0.4F);
        }
        return true;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return BadCompanyAddon.HELICOPTER.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 30;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return BadCompanyAddon.TANK_HURT.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BadCompanyMissiles", missiles());
        tag.putInt("BadCompanyReload", reloadTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                MISSILES,
                Math.max(
                        0,
                        Math.min(
                                MISSILE_CAP,
                                tag.getInt(
                                        "BadCompanyMissiles"))));
        reloadTicks = Math.max(
                0, tag.getInt("BadCompanyReload"));
    }
}
