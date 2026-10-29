package rotp.addons.greenday.entity;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.greenday.GreenDayAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class ShootingArmEntity
        extends ModdedProjectileEntity {
    public static final float BASE_DAMAGE = 5.0F;
    public static final int PASSENGER_DAMAGE_INTERVAL = 25;
    public static final float PASSENGER_DAMAGE = 2.0F;
    public static final int ARM_EFFECT_REFRESH_TICKS = 40;
    public static final int ORPHAN_GRACE_TICKS = 200;
    public static final int MIN_RECOVERY_AGE_TICKS = 5;
    // 1.16 getPassengersRidingOffset
    public static final double PASSENGERS_RIDING_OFFSET = -1.0D;

    private static final EntityDataAccessor<Boolean> RIGHT =
            SynchedEntityData.defineId(
                    ShootingArmEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(
                    ShootingArmEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private int lifeTicks;
    private int orphanTicks;
    private String removalCause = "active";
    private String removalDiagnostics = "not_removed";

    public ShootingArmEntity(
            EntityType<? extends ShootingArmEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(false);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RIGHT, true);
        builder.define(STUCK, false);
    }

    @Override
    public void tick() {
        if (isStuck()) {
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
        }
        else {
            setNoGravity(false);
        }
        if (!level().isClientSide()) {
            LivingEntity owner = getOwner();
            if (owner != null && !owner.isAlive()) {
                markRemovalCause("base_owner_dead");
            }
        }
        super.tick();
        lifeTicks++;
        // 1.16: the move itself is constant-velocity; the arm then takes
        // its own fall off the motion while it is not stuck
        if (!isStuck()) {
            setDeltaMovement(getDeltaMovement().add(
                    0.0D, -getGravityAcceleration(), 0.0D));
        }

        if (!(level() instanceof ServerLevel serverLevel)
                || !isAlive()) {
            return;
        }
        LivingEntity owner = getOwner();
        if (owner == null) {
            orphanTicks++;
            if (orphanTicks > ORPHAN_GRACE_TICKS) {
                markRemovalCause("orphan_timeout");
                discard();
            }
            return;
        }
        orphanTicks = 0;
        StandPower power = StandPower.get(owner);
        if (!owner.isAlive()) {
            markRemovalCause("owner_dead");
            discard();
            return;
        }
        if (power == null) {
            markRemovalCause("owner_power_missing");
            discard();
            return;
        }
        if (power.getPowerType() != GreenDayAddon.GREEN_DAY.get()) {
            markRemovalCause("owner_power_type_mismatch");
            discard();
            return;
        }

        owner.addEffect(new MobEffectInstance(
                isRight()
                        ? GreenDayAddon.RIGHT_ARMLESS
                        : GreenDayAddon.LEFT_ARMLESS,
                ARM_EFFECT_REFRESH_TICKS,
                0,
                false,
                false,
                false));
        if (lifeTicks % PASSENGER_DAMAGE_INTERVAL == 0) {
            for (Entity passenger : getPassengers()) {
                // 1.16: DamageSource.IN_WALL
                passenger.hurt(
                        serverLevel.damageSources().inWall(),
                        PASSENGER_DAMAGE);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (!level().isClientSide()) {
            Vec3 normal = Vec3.atLowerCornerOf(
                    hit.getDirection().getNormal());
            setPos(hit.getLocation().add(normal.scale(0.01D)));
            setDeltaMovement(Vec3.ZERO);
            setStuck(true);
            setNoGravity(true);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide() || !isAlive()) {
            return;
        }
        Entity target = hit.getEntity();
        LivingEntity owner = getOwner();
        // 1.16: a burning arm ignites the target; undone on a failed hit
        boolean entityHurt = hurtTarget(target, owner);
        int prevTargetFireTimer = target.getRemainingFireTicks();
        if (isOnFire()) {
            target.igniteForSeconds(5);
        }
        if (!entityHurt) {
            target.setRemainingFireTicks(prevTargetFireTimer);
        }
        target.playSound(
                GreenDayAddon.GD_KICK.get(),
                0.5F,
                1.0F);
        // 1.16: an arm that carries somebody still hurts what it flies
        // into; only an empty arm picks its target up, with the plain
        // startRiding that a sneaking target refuses
        if (getPassengers().isEmpty()) {
            target.startRiding(this);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    /*
     * 1.16 Entity.positionRider put a passenger's feet at the vehicle's Y
     * plus getPassengersRidingOffset() plus the passenger's own
     * getMyRidingOffset(). The 1.21 attachment points do not carry those
     * values, so the feet are placed directly.
     */
    @Override
    protected void positionRider(
            Entity passenger, MoveFunction callback) {
        if (hasPassenger(passenger)) {
            Vec3 feet = getPassengerRidingPosition(passenger);
            callback.accept(passenger, feet.x, feet.y, feet.z);
        }
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return position().add(
                0.0D,
                PASSENGERS_RIDING_OFFSET
                        + legacyRidingOffset(passenger),
                0.0D);
    }

    // the 1.16.5 getMyRidingOffset overrides
    public static double legacyRidingOffset(Entity passenger) {
        if (passenger instanceof Player) {
            return -0.35D;
        }
        if (passenger instanceof AbstractSkeleton) {
            return -0.6D;
        }
        if (passenger instanceof Zombie zombie) {
            return zombie.isBaby() ? 0.0D : -0.45D;
        }
        if (passenger instanceof AbstractPiglin piglin) {
            return piglin.isBaby() ? -0.05D : -0.45D;
        }
        if (passenger instanceof PatrollingMonster) {
            return -0.45D;
        }
        if (passenger instanceof Animal) {
            return 0.14D;
        }
        if (passenger instanceof Endermite
                || passenger instanceof Silverfish) {
            return 0.1D;
        }
        if (passenger instanceof ArmorStand stand) {
            return stand.isMarker() ? 0.0D : 0.1F;
        }
        return 0.0D;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void playerTouch(Player player) {
        if (!level().isClientSide() && leftOwner) {
            returnToOwner(player, "player_touch");
        }
    }

    @Override
    public InteractionResult interact(
            Player player, InteractionHand hand) {
        if (player == getOwner()
                && lifeTicks >= MIN_RECOVERY_AGE_TICKS) {
            returnToOwner(player, "interact");
            return InteractionResult.sidedSuccess(
                    level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    private boolean returnToOwner(Player player, String route) {
        if (player != getOwner()
                || lifeTicks < MIN_RECOVERY_AGE_TICKS) {
            return false;
        }
        if (!level().isClientSide()) {
            ejectPassengers();
            player.take(this, 1);
            // 1.16 played this on the owner's own client;
            // Player.playSound on a server skips that player
            level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
            markRemovalCause("returned_to_owner_" + route);
            discard();
        }
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide() && !isInvulnerableTo(source)) {
            markRemovalCause("damaged_" + source);
        }
        return super.hurt(source, amount);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            if ("active".equals(removalCause)) {
                markRemovalCause("external_remove");
            }
            LivingEntity owner = getOwner();
            StandPower power = owner != null
                    ? StandPower.get(owner)
                    : null;
            removalDiagnostics = removalCause
                    + ", reason=" + reason
                    + ", lifeTicks=" + lifeTicks
                    + ", tickCount=" + tickCount
                    + ", orphanTicks=" + orphanTicks
                    + ", stuck=" + isStuck()
                    + ", passengers=" + getPassengers().size()
                    + ", pos=" + position()
                    + ", velocity=" + getDeltaMovement()
                    + ", owner=" + (owner != null ? owner.getUUID() : null)
                    + ", ownerAlive=" + (owner != null && owner.isAlive())
                    + ", ownerPowerPresent=" + (power != null)
                    + ", ownerPowerIsGreenDay="
                    + (power != null
                            && power.getPowerType()
                                    == GreenDayAddon.GREEN_DAY.get());
        }
        if (!level().isClientSide()) {
            LivingEntity owner = getOwner();
            if (owner != null) {
                owner.removeEffect(
                        isRight()
                                ? GreenDayAddon.RIGHT_ARMLESS
                                : GreenDayAddon.LEFT_ARMLESS);
            }
        }
        super.remove(reason);
    }

    private void markRemovalCause(String cause) {
        if ("active".equals(removalCause)) {
            removalCause = cause;
        }
    }

    public String removalDiagnostics() {
        return removalDiagnostics;
    }

    public boolean isRight() {
        return entityData.get(RIGHT);
    }

    public void setRight(boolean right) {
        entityData.set(RIGHT, right);
    }

    public boolean isStuck() {
        return entityData.get(STUCK);
    }

    public void setStuck(boolean stuck) {
        entityData.set(STUCK, stuck);
    }

    @Override
    public int ticksLifespan() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.0F;
    }

    // applied in tick(): the base move stays constant-velocity as in 1.16
    @Override
    protected double getGravityAcceleration() {
        return 0.08D;
    }

    // 1.16 never turned a constant-velocity projectile: the arm keeps
    // its launch rotation
    @Override
    protected void rotateTowardsMovement(float rotationSpeed) {}

    @Override
    public boolean standDamage() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Right", isRight());
        tag.putBoolean("Stuck", isStuck());
        tag.putInt("LifeTicks", lifeTicks);
        tag.putInt("OrphanTicks", orphanTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRight(tag.getBoolean("Right"));
        setStuck(tag.getBoolean("Stuck"));
        lifeTicks = Math.max(0, tag.getInt("LifeTicks"));
        orphanTicks = Math.max(0, tag.getInt("OrphanTicks"));
        setNoGravity(isStuck());
    }
}
