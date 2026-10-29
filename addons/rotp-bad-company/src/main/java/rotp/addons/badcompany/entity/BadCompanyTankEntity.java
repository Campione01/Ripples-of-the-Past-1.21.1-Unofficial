package rotp.addons.badcompany.entity;

import javax.annotation.Nullable;

import rotp.addons.badcompany.BadCompanyAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BadCompanyTankEntity
        extends BadCompanyUnitEntity {
    public static final double COMBAT_RANGE = 40.0D;
    public static final int FIRE_INTERVAL = 40;
    public static final float MISSILE_SPEED = 1.6F;
    public static final float MISSILE_INACCURACY = 0.02F;
    public static final float MODEL_SCALE = 0.16666F;

    public BadCompanyTankEntity(
            EntityType<? extends BadCompanyTankEntity> type,
            Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 1.3D)
                // 1.16 BadTankEntity: armor 20, knockback resistance 6
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, COMBAT_RANGE);
    }

    @Override
    public BadCompanyUnitType unitType() {
        return BadCompanyUnitType.TANK;
    }

    @Override
    protected double combatRange() {
        return COMBAT_RANGE;
    }

    @Override
    protected int attackInterval() {
        return FIRE_INTERVAL;
    }

    @Override
    protected double navigationSpeed() {
        return 0.45D;
    }

    @Override
    protected boolean performRangedAttack(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target) {
        if (owner.distanceToSqr(target) <= 36.0D) {
            return false;
        }
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        if (coordinator == null) {
            return false;
        }
        BadCompanyMissileEntity missile =
                new BadCompanyMissileEntity(owner, level);
        missile.bindClosure(
                owner.getUUID(), coordinator.getUUID());
        missile.bindLifecycleProducer(owner);
        Vec3 direction = target.getBoundingBox()
                .getCenter()
                .subtract(getEyePosition());
        missile.setPos(getX(), getEyeY(), getZ());
        missile.shoot(
                direction.x,
                direction.y,
                direction.z,
                MISSILE_SPEED,
                MISSILE_INACCURACY);
        level.addFreshEntity(missile);
        coordinator.trackTransient(missile);
        level.playSound(
                null,
                blockPosition(),
                BadCompanyAddon.TANK_SHOOT.get(),
                SoundSource.PLAYERS,
                2.0F,
                0.8F + random.nextFloat() * 0.4F);
        return true;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return BadCompanyAddon.TANK_LIVING.get();
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return BadCompanyAddon.TANK_HURT.get();
    }

    @Override
    protected void playStepSound(
            BlockPos pos, BlockState state) {
        playSound(
                BadCompanyAddon.TANK_STEP.get(),
                1.0F,
                1.0F);
    }
}
