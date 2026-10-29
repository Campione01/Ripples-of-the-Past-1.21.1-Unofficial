package rotp.addons.badcompany.entity;

import rotp.addons.badcompany.BadCompanyAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BadCompanySoldierEntity
        extends BadCompanyUnitEntity {
    public static final double COMBAT_RANGE = 30.0D;
    public static final int AUTONOMOUS_FIRE_INTERVAL = 20;
    public static final float BULLET_SPEED = 1.6F;
    public static final float BULLET_INACCURACY = 0.02F;
    public static final float MODEL_SCALE = 0.3333F;

    private static final EntityDataAccessor<Boolean> PARACHUTING =
            SynchedEntityData.defineId(
                    BadCompanySoldierEntity.class,
                    EntityDataSerializers.BOOLEAN);

    public BadCompanySoldierEntity(
            EntityType<? extends BadCompanySoldierEntity> type,
            Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 1.0D)
                .add(Attributes.FOLLOW_RANGE, COMBAT_RANGE);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PARACHUTING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            boolean parachuting = !onGround()
                    && getDeltaMovement().y < -0.15D;
            entityData.set(PARACHUTING, parachuting);
            if (parachuting) {
                addEffect(new MobEffectInstance(
                        MobEffects.SLOW_FALLING,
                        10,
                        0,
                        false,
                        false,
                        false));
            }
        }
    }

    public boolean isParachuting() {
        return entityData.get(PARACHUTING);
    }

    @Override
    public ItemStack getMainHandItem() {
        return new ItemStack(BadCompanyAddon.BAD_GUN.get());
    }

    @Override
    public BadCompanyUnitType unitType() {
        return BadCompanyUnitType.SOLDIER;
    }

    @Override
    protected double combatRange() {
        return COMBAT_RANGE;
    }

    @Override
    protected int attackInterval() {
        return AUTONOMOUS_FIRE_INTERVAL;
    }

    @Override
    protected boolean performRangedAttack(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target) {
        Vec3 direction = target.getBoundingBox()
                .getCenter()
                .subtract(getEyePosition());
        return fire(owner, direction, true);
    }

    public boolean fire(
            LivingEntity standOwner,
            Vec3 direction,
            boolean individualSound) {
        if (!(level() instanceof ServerLevel serverLevel)
                || standOwner == null
                || direction == null
                || direction.lengthSqr() <= 1.0E-7D
                || !isOwnedBy(standOwner.getUUID())
                || resolveCoordinator() == null) {
            return false;
        }
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        BadCompanyBulletEntity bullet =
                new BadCompanyBulletEntity(
                        standOwner, serverLevel);
        bullet.bindClosure(
                standOwner.getUUID(),
                coordinator.getUUID());
        bullet.bindLifecycleProducer(this);
        Vec3 normalized = direction.normalize();
        Vec3 muzzle = getEyePosition()
                .add(normalized.scale(0.2D));
        bullet.setPos(muzzle.x, muzzle.y, muzzle.z);
        bullet.shoot(
                normalized.x,
                normalized.y,
                normalized.z,
                BULLET_SPEED,
                BULLET_INACCURACY);
        serverLevel.addFreshEntity(bullet);
        coordinator.trackTransient(bullet);
        markShooting(4);

        if (individualSound) {
            serverLevel.playSound(
                    null,
                    blockPosition(),
                    BadCompanyAddon.SOLDIER_SHOOT.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    0.8F + random.nextFloat() * 0.4F);
        }
        return true;
    }

    @Override
    protected void playStepSound(
            BlockPos pos, BlockState state) {
        playSound(
                BadCompanyAddon.SOLDIER_STEP.get(),
                0.5F,
                1.0F);
    }
}
