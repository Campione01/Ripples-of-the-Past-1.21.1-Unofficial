package rotp.addons.cmoon.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravitySource;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.CMoonEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CMoonStandEntity extends StandEntity
        implements DirectionalGravitySource {
    public static final float DESTROY_DAMAGE = 1.0F;
    public static final double SHIFT_RADIUS = 3.0D;
    public static final float SHIFT_PROJECTILE_STAMINA = 30.0F;
    public static final float SHIFT_MINIMUM_STAMINA = 10.0F;

    private static final ResourceLocation GRAVITY_SOURCE_ID =
            CMoonAddon.id("gravity_shift");
    private static final int GRAVITY_PRIORITY = 100;
    private static final EntityDataAccessor<Boolean> SHIFT_ACTIVE =
            SynchedEntityData.defineId(
                    CMoonStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SHIFT_DIRECTION =
            SynchedEntityData.defineId(
                    CMoonStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COUNTER_ACTIVE =
            SynchedEntityData.defineId(
                    CMoonStandEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>>
            AUTO_TARGET_UUID = SynchedEntityData.defineId(
                    CMoonStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    private LivingEntity boundGravityTarget;
    private Direction boundGravityDirection = Direction.DOWN;

    public CMoonStandEntity(
            EntityType<? extends CMoonStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHIFT_ACTIVE, false);
        builder.define(
                SHIFT_DIRECTION,
                Direction.DOWN.get3DDataValue());
        builder.define(COUNTER_ACTIVE, false);
        builder.define(AUTO_TARGET_UUID, Optional.empty());
    }

    public boolean isShiftActive() {
        return entityData.get(SHIFT_ACTIVE);
    }

    public Direction getGravityDirection() {
        Direction direction = Direction.from3DDataValue(
                entityData.get(SHIFT_DIRECTION));
        return direction.getAxis().isHorizontal()
                ? direction
                : Direction.DOWN;
    }

    @Override
    public Direction gravityDirection(Entity entity) {
        return entity == boundGravityTarget
                        && isGravityBindingValid(
                                boundGravityTarget)
                ? getGravityDirection()
                : Direction.DOWN;
    }

    public void setShiftActive(boolean active) {
        if (!level().isClientSide()) {
            LivingEntity user = getUser();
            boolean enabled = active && user != null;
            Direction direction = enabled
                    ? user.getDirection()
                    : Direction.DOWN;
            entityData.set(
                    SHIFT_DIRECTION,
                    direction.get3DDataValue(),
                    true);
            entityData.set(SHIFT_ACTIVE, enabled, true);
            reconcileGravityBinding(user);
        }
    }

    public boolean isCounterActive() {
        return entityData.get(COUNTER_ACTIVE);
    }

    public void setCounterActive(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(COUNTER_ACTIVE, active, true);
        }
    }

    public boolean hasAutoAttackTarget() {
        return entityData.get(AUTO_TARGET_UUID).isPresent();
    }

    public LivingEntity getAutoAttackTarget() {
        if (!(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity target = entityData.get(AUTO_TARGET_UUID)
                .map(server::getEntity)
                .orElse(null);
        return target instanceof LivingEntity living
                ? living
                : null;
    }

    public void setAutoAttackTarget(LivingEntity target) {
        if (!level().isClientSide()) {
            entityData.set(
                    AUTO_TARGET_UUID,
                    target != null
                            ? Optional.of(target.getUUID())
                            : Optional.empty(),
                    true);
            if (target != null) {
                stopRetraction();
            }
        }
    }

    public void clearAutoAttackTarget() {
        setAutoAttackTarget(null);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void setUserAndPower(
            LivingEntity user, StandPower power) {
        clearGravityBinding();
        super.setUserAndPower(user, power);
        reconcileGravityBinding(user);
    }

    @Override
    public void onSyncedDataUpdated(
            EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if (dataAccessor.equals(SHIFT_ACTIVE)
                || dataAccessor.equals(SHIFT_DIRECTION)) {
            reconcileGravityBinding(getUser());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            tickGravityShift();
            tickDestroyTarget();
        }
        reconcileGravityBinding(getUser());
    }

    private void tickGravityShift() {
        if (!isShiftActive()) {
            return;
        }
        LivingEntity user = getUser();
        StandPower power =
                user != null ? StandPower.get(user) : null;
        if (user == null
                || power == null
                || power.getSummonedStandEntity() != this) {
            setShiftActive(false);
            return;
        }
        if (power.getStamina() < SHIFT_MINIMUM_STAMINA) {
            setShiftActive(false);
            power.getPowerType().unsummon(user, power);
            return;
        }

        user.addEffect(new MobEffectInstance(
                CMoonAddon.CM_SHIFT,
                100,
                2,
                false,
                false,
                true));
        // 1.16 field_76430_j: hidden Jump Boost II
        user.addEffect(new MobEffectInstance(
                MobEffects.JUMP,
                100,
                1,
                false,
                false,
                false));
        // port-only sideways gravity: hidden slow fall keeps the
        // local drift slow and resets fall distance
        user.addEffect(new MobEffectInstance(
                MobEffects.SLOW_FALLING,
                100,
                1,
                false,
                false,
                false));

        for (Projectile projectile
                : level().getEntitiesOfClass(
                        Projectile.class,
                        user.getBoundingBox().inflate(SHIFT_RADIUS),
                        entity -> entity.isAlive()
                                && entity.getOwner() != user)) {
            if (!power.consumeStamina(
                    SHIFT_PROJECTILE_STAMINA)) {
                setShiftActive(false);
                break;
            }
            Vec3 incoming = projectile.getDeltaMovement();
            Vec3 reflected = incoming.lengthSqr() > 1.0E-6D
                    ? incoming.reverse()
                    : user.getLookAngle().normalize();
            projectile.setOwner(user);
            projectile.setDeltaMovement(reflected);
            projectile.hasImpulse = true;
            projectile.hurtMarked = true;
            if (projectile
                    instanceof ModdedProjectileEntity modded) {
                modded.setIsDeflected(
                        reflected, projectile.position());
            }
            if (level() instanceof ServerLevel server) {
                server.sendParticles(
                        CMoonAddon.HAMON_SPARK_PURPLE.get(),
                        projectile.getX(),
                        projectile.getY(),
                        projectile.getZ(),
                        4,
                        0.1D,
                        0.1D,
                        0.1D,
                        0.0D);
            }
        }
    }

    private void tickDestroyTarget() {
        if (!hasAutoAttackTarget()) {
            return;
        }
        LivingEntity user = getUser();
        LivingEntity target = getAutoAttackTarget();
        if (isManuallyControlled()
                || user == null
                || target == null
                || target == user
                || target instanceof StandEntity
                || target.level() != level()) {
            clearAutoAttackTarget();
            return;
        }
        if (target.isDeadOrDying()) {
            setDeltaMovement(Vec3.ZERO);
            if (target.getMaxHealth() >= 20.0F) {
                CMoonEvents.grantDestroyStaminaRegen(user);
            }
            return;
        }
        if (!target.isAlive()
                || target.distanceToSqr(user)
                        > getMaxRange() * getMaxRange()
                || target.isAlliedTo(user)
                || !canAttackEntity(target)) {
            clearAutoAttackTarget();
            return;
        }

        moveToDestroyTarget(target);
        double meleeDistance =
                target.getBbWidth() + getBbWidth() + 1.0D;
        boolean barrageMode = getFinisherMeter() > 0.3F;
        int interval = barrageMode ? 4 : 10;
        if (distanceToSqr(target)
                        <= meleeDistance * meleeDistance
                && tickCount % interval == 0) {
            target.hurt(
                    damageSources().mobAttack(this),
                    DESTROY_DAMAGE);
            playSound(
                    barrageMode
                            ? CMoonAddon
                                    .C_MOON_BARRAGE_SOUND.get()
                            : CMoonAddon.C_MOON_PUNCH_SOUND.get(),
                    1.0F,
                    1.0F);
        }
    }

    private void moveToDestroyTarget(LivingEntity target) {
        stopRetraction();
        Vec3 offset = target.position().subtract(position());
        double minDistance =
                target.getBbWidth() + getBbWidth() + 1.0D;
        if (offset.lengthSqr() > minDistance * minDistance) {
            setDeltaMovement(
                    offset.x / 10.0D,
                    offset.y / 16.0D,
                    offset.z / 10.0D);
        }
        else {
            setDeltaMovement(Vec3.ZERO);
        }
        lookAt(
                EntityAnchorArgument.Anchor.EYES,
                target.getEyePosition());
        hurtMarked = true;
    }

    @Override
    public void retract() {
        setCounterActive(false);
        clearAutoAttackTarget();
        super.retract();
    }

    @Override
    protected void startStandUnsummon() {
        // Ordinary action retraction does not end the persistent Shift toggle.
        if (!level().isClientSide()) {
            clearGravityBinding();
            setShiftActive(false);
        }
        super.startStandUnsummon();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        clearGravityBinding();
        setShiftActive(false);
        setCounterActive(false);
        clearAutoAttackTarget();
        super.remove(reason);
    }

    @Override
    public boolean isFollowingUser() {
        return !hasAutoAttackTarget()
                && super.isFollowingUser();
    }

    private void reconcileGravityBinding(
            LivingEntity user) {
        if (!isGravityBindingValid(user)) {
            clearGravityBinding();
            return;
        }

        Direction direction = getGravityDirection();
        if (direction == Direction.DOWN) {
            clearGravityBinding();
            return;
        }

        if (boundGravityTarget != user) {
            clearGravityBinding();
            boundGravityTarget = user;
            boundGravityDirection = direction;
            DirectionalGravityApi.bind(
                    user,
                    GRAVITY_SOURCE_ID,
                    GRAVITY_PRIORITY,
                    this);
        }
        else if (boundGravityDirection != direction) {
            boundGravityDirection = direction;
            DirectionalGravityApi.directionChanged(
                    user, GRAVITY_SOURCE_ID, this);
        }
    }

    private boolean isGravityBindingValid(
            LivingEntity user) {
        if (isRemoved()
                || !isShiftActive()
                || user == null
                || user.isRemoved()
                || !user.isAlive()
                || user.level() != level()
                || getUser() != user) {
            return false;
        }
        StandPower power = StandPower.get(user);
        return power != null
                && power.getSummonedStandEntity() == this;
    }

    private void clearGravityBinding() {
        if (boundGravityTarget != null) {
            DirectionalGravityApi.unbind(
                    boundGravityTarget,
                    GRAVITY_SOURCE_ID,
                    this);
            boundGravityTarget = null;
        }
        boundGravityDirection = Direction.DOWN;
    }
}
