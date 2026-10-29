package rotp.addons.ultimate_lifeform.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 UltimateLifeformPhantomEntity: a size-0 phantom with attack damage 10 that never burns by day, with its own
 * copy of the phantom AI: it orbits 20 blocks above its owner while idle, takes the owner's attackers and the mobs the
 * owner absorbs as targets, re-anchors at the owner beyond 32 blocks and teleports above it beyond 64, and after its
 * 600 ticks (or below 3 health) swoops back to the owner and vanishes there off the count without a health debuff.
 */
public final class UltimatePhantomEntity extends Phantom {
    @Nullable private UUID ownerUuid;
    // the ticks left of the 600 (1.16 counted ticksalive up to 600)
    private int lifeTicks = 600;
    private double distanceFromOwner;
    private boolean deathReported;
    private boolean restoredFromDisk;
    // 1.16 kept its own phantom AI state (the vanilla one is private)
    private Vec3 moveTargetPoint = Vec3.ZERO;
    private BlockPos anchorPoint = BlockPos.ZERO;
    private AttackPhase attackPhase = AttackPhase.CIRCLE;
    @Nullable private LivingEntity passiveTarget;

    public UltimatePhantomEntity(
            EntityType<? extends Phantom> type, Level level) {
        super(type, level);
        moveControl = new OwnMoveControl(this);
    }

    public void setSummonedBy(
            LivingEntity owner, int lifetime) {
        ownerUuid = owner.getUUID();
        lifeTicks = Math.max(lifetime, 0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal());
        targetSelector.addGoal(2, new OwnerHurtTargetGoal());
        goalSelector.addGoal(1, new PickAttackGoal());
        goalSelector.addGoal(2, new SweepAttackGoal());
        goalSelector.addGoal(3, new OrbitPointGoal());
        goalSelector.addGoal(2, new PassiveOrbitPointGoal());
    }

    // 1.16 UltimateLifeformPhantomEntity.isSunBurnTick: never burns by day
    @Override
    public boolean isSunBurnTick() {
        return false;
    }

    public BlockPos getAnchorPoint() {
        return anchorPoint;
    }

    public boolean isSwooping() {
        return attackPhase == AttackPhase.SWOOP;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof UltimateRabbitEntity rabbit) {
            return ownerUuid == null || !rabbit.isOwnedBy(ownerUuid);
        }
        if (target instanceof TamableAnimal pet) {
            return !java.util.Objects.equals(pet.getOwnerUUID(), ownerUuid);
        }
        return !isOwnedBy(target) && super.canAttack(target);
    }

    @Override
    public void tick() {
        LivingEntity owner = getOwner();
        if (!level().isClientSide()
                && (restoredFromDisk || owner == null)) {
            discard();
            return;
        }
        super.tick();
        if (level().isClientSide() || owner == null) {
            return;
        }
        UltimateLifeformData data = PlayerPower.getPowerData(
                owner, UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElse(null);
        if (data != null) {
            data.setSummonedPhantomsLifetime(lifeTicks);
        }
        passiveTarget = owner;
        if (!owner.isAlive()) {
            kill();
            return;
        }
        if (owner.onGround() && !closerThan(owner, 64.0D)) {
            setPos(owner.position().add(0.0D, 1.9D, 0.0D));
        }
        if (!closerThan(owner, 32.0D)) {
            anchorPoint = owner.blockPosition();
            attackPhase = AttackPhase.CIRCLE;
        }
        distanceFromOwner = distanceToSqr(owner);
        if (lifeTicks > 0) {
            lifeTicks--;
        }
        if ((lifeTicks <= 0 || getHealth() < 3.0F) && getTarget() == null) {
            setTarget(owner);
            lookAt(owner, 1.0F, 1.0F);
            anchorPoint = owner.blockPosition();
            attackPhase = AttackPhase.SWOOP;
            if (closerThan(owner, 2.0D)) {
                discard();
                if (data != null) {
                    data.summonedPhantomReturned();
                    data.syncOnUpdate(owner);
                }
            }
        }
    }

    private boolean farFromOwner(double distance) {
        return distanceFromOwner > distance * distance;
    }

    private static boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !(target instanceof Player targetPlayer)
                || !(owner instanceof Player ownerPlayer)
                || ownerPlayer.canHarmPlayer(targetPlayer);
    }

    @Nullable
    public LivingEntity getOwner() {
        if (ownerUuid == null
                || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity owner = serverLevel.getEntity(ownerUuid);
        return owner instanceof LivingEntity living ? living : null;
    }

    public boolean isOwnedBy(UUID owner) {
        return owner.equals(ownerUuid);
    }

    private boolean isOwnedBy(LivingEntity entity) {
        return ownerUuid != null && ownerUuid.equals(entity.getUUID());
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        LivingEntity owner = getOwner();
        return entity == owner
                || owner != null && owner.isAlliedTo(entity)
                || super.isAlliedTo(entity);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        reportDeath();
    }

    private void reportDeath() {
        if (deathReported || level().isClientSide()) {
            return;
        }
        deathReported = true;
        LivingEntity owner = getOwner();
        if (owner != null) {
            PlayerPower.getPowerData(
                    owner,
                    UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                    .ifPresent(data -> {
                        data.summonedPhantomDied();
                        data.syncOnUpdate(owner);
                    });
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) {
            tag.putUUID("UltimateOwner", ownerUuid);
        }
        tag.putInt("UltimateLifetime", lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Player power data rebuilds the batch; retire legacy entity snapshots.
        restoredFromDisk = true;
        ownerUuid = tag.hasUUID("UltimateOwner")
                ? tag.getUUID("UltimateOwner")
                : null;
        lifeTicks = Math.max(
                tag.getInt("UltimateLifetime"), 0);
    }

    private enum AttackPhase {
        CIRCLE,
        SWOOP
    }

    // 1.16 PhantomOwnerHurtByTargetGoal (within 12 blocks of the owner)
    private final class OwnerHurtByTargetGoal extends TargetGoal {
        @Nullable private LivingEntity attacker;
        private int timestamp;

        OwnerHurtByTargetGoal() {
            super(UltimatePhantomEntity.this, false);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = getOwner();
            if (owner == null || farFromOwner(12.0D)) {
                return false;
            }
            attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != timestamp
                    && canAttack(attacker, TargetingConditions.DEFAULT)
                    && wantsToAttack(attacker, owner);
        }

        @Override
        public void start() {
            mob.setTarget(attacker);
            passiveTarget = null;
            LivingEntity owner = getOwner();
            if (owner != null) {
                timestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    // 1.16 PhantomOwnerHurtTargetGoal (within 64 blocks, the last damage gone
    // or an absorption)
    private final class OwnerHurtTargetGoal extends TargetGoal {
        @Nullable private LivingEntity attacked;
        private int timestamp;

        OwnerHurtTargetGoal() {
            super(UltimatePhantomEntity.this, false);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = getOwner();
            if (owner == null || farFromOwner(64.0D)) {
                return false;
            }
            attacked = owner.getLastHurtMob();
            DamageSource attackedBy = attacked != null
                    ? attacked.getLastDamageSource()
                    : null;
            return owner.getLastHurtMobTimestamp() != timestamp
                    && (attackedBy == null
                            || attackedBy.getMsgId().startsWith("absorb"))
                    && canAttack(attacked, TargetingConditions.DEFAULT)
                    && wantsToAttack(attacked, owner);
        }

        @Override
        public void start() {
            mob.setTarget(attacked);
            passiveTarget = null;
            LivingEntity owner = getOwner();
            if (owner != null) {
                timestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    private abstract class MoveGoal extends Goal {
        MoveGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        protected boolean touchingTarget() {
            return moveTargetPoint.distanceToSqr(getX(), getY(), getZ()) < 4.0D;
        }
    }

    // the vanilla 1.16 phantom sweep, the target its owner included
    private final class SweepAttackGoal extends MoveGoal {
        @Override
        public boolean canUse() {
            return getTarget() != null && attackPhase == AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (target instanceof Player player
                    && (player.isSpectator() || player.isCreative())) {
                return false;
            }
            if (!canUse()) {
                return false;
            }
            if (tickCount % 20 == 0) {
                List<Cat> cats = level().getEntitiesOfClass(Cat.class,
                        getBoundingBox().inflate(16.0D), EntitySelector.ENTITY_STILL_ALIVE);
                if (!cats.isEmpty()) {
                    for (Cat cat : cats) {
                        cat.hiss();
                    }
                    return false;
                }
            }
            return true;
        }

        @Override
        public void stop() {
            setTarget(null);
            attackPhase = AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            moveTargetPoint = new Vec3(target.getX(), target.getY(0.5D), target.getZ());
            if (getBoundingBox().inflate(0.2F).intersects(target.getBoundingBox())) {
                doHurtTarget(target);
                attackPhase = AttackPhase.CIRCLE;
                if (!isSilent()) {
                    level().levelEvent(1039, blockPosition(), 0);
                }
            }
            else if (horizontalCollision || hurtTime > 0) {
                attackPhase = AttackPhase.CIRCLE;
            }
        }
    }

    // the vanilla 1.16 attack strategy; its owner counts as a target too
    private final class PickAttackGoal extends Goal {
        private int nextSweepTick;

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null
                    && (UltimatePhantomEntity.this.canAttack(target, TargetingConditions.DEFAULT)
                            || getOwner() == target);
        }

        @Override
        public void start() {
            nextSweepTick = 10;
            attackPhase = AttackPhase.CIRCLE;
            setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            anchorPoint = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, anchorPoint)
                    .above(10 + random.nextInt(20));
        }

        @Override
        public void tick() {
            if (attackPhase == AttackPhase.CIRCLE) {
                --nextSweepTick;
                if (nextSweepTick <= 0) {
                    attackPhase = AttackPhase.SWOOP;
                    setAnchorAboveTarget();
                    nextSweepTick = (8 + random.nextInt(4)) * 20;
                    playSound(SoundEvents.PHANTOM_SWOOP, 10.0F, 0.95F + random.nextFloat() * 0.1F);
                }
            }
        }

        private void setAnchorAboveTarget() {
            LivingEntity target = getTarget();
            if (target != null) {
                anchorPoint = target.blockPosition().above(20 + random.nextInt(20));
                if (anchorPoint.getY() < level().getSeaLevel()) {
                    anchorPoint = new BlockPos(anchorPoint.getX(), level().getSeaLevel() + 1, anchorPoint.getZ());
                }
            }
        }
    }

    // 1.16 PassiveOrbitPointGoal: idle, the anchor 20 blocks above the owner
    private final class PassiveOrbitPointGoal extends OrbitPointGoal {
        @Override
        public boolean canUse() {
            return passiveTarget == null || attackPhase == AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            super.tick();
            if (getTarget() == null && passiveTarget != null) {
                anchorPoint = passiveTarget.blockPosition().above(20);
            }
        }
    }

    private class OrbitPointGoal extends MoveGoal {
        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        @Override
        public boolean canUse() {
            return getTarget() == null || attackPhase == AttackPhase.CIRCLE;
        }

        @Override
        public void start() {
            distance = 5.0F + random.nextFloat() * 10.0F;
            height = -4.0F + random.nextFloat() * 9.0F;
            clockwise = random.nextBoolean() ? 1.0F : -1.0F;
            selectNext();
        }

        @Override
        public void tick() {
            if (random.nextInt(350) == 0) {
                height = -4.0F + random.nextFloat() * 9.0F;
            }
            if (random.nextInt(250) == 0) {
                ++distance;
                if (distance > 15.0F) {
                    distance = 5.0F;
                    clockwise = -clockwise;
                }
            }
            if (random.nextInt(450) == 0) {
                angle = random.nextFloat() * 2.0F * (float) Math.PI;
                selectNext();
            }
            if (touchingTarget()) {
                selectNext();
            }
            if (moveTargetPoint.y < getY() && !level().isEmptyBlock(blockPosition().below(1))) {
                height = Math.max(1.0F, height);
                selectNext();
            }
            if (moveTargetPoint.y > getY() && !level().isEmptyBlock(blockPosition().above(1))) {
                height = Math.min(-1.0F, height);
                selectNext();
            }
        }

        private void selectNext() {
            if (BlockPos.ZERO.equals(anchorPoint)) {
                anchorPoint = blockPosition();
            }
            angle += clockwise * 15.0F * (float) (Math.PI / 180.0);
            moveTargetPoint = Vec3.atLowerCornerOf(anchorPoint).add(
                    distance * Mth.cos(angle), -4.0F + height, distance * Mth.sin(angle));
        }
    }

    // the vanilla 1.16 phantom flight towards its own move target
    private final class OwnMoveControl extends MoveControl {
        private float speed = 0.1F;

        OwnMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (horizontalCollision) {
                setYRot(getYRot() + 180.0F);
                speed = 0.1F;
            }
            double dx = moveTargetPoint.x - getX();
            double dy = moveTargetPoint.y - getY();
            double dz = moveTargetPoint.z - getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(horizontal) > 1.0E-5F) {
                double scale = 1.0D - Math.abs(dy * 0.7F) / horizontal;
                dx *= scale;
                dz *= scale;
                horizontal = Math.sqrt(dx * dx + dz * dz);
                double length = Math.sqrt(dx * dx + dz * dz + dy * dy);
                float oldYaw = getYRot();
                float targetYaw = (float) Mth.atan2(dz, dx);
                float yaw = Mth.wrapDegrees(getYRot() + 90.0F);
                float wanted = Mth.wrapDegrees(targetYaw * (180.0F / (float) Math.PI));
                setYRot(Mth.approachDegrees(yaw, wanted, 4.0F) - 90.0F);
                yBodyRot = getYRot();
                if (Mth.degreesDifferenceAbs(oldYaw, getYRot()) < 3.0F) {
                    speed = Mth.approach(speed, 1.8F, 0.005F * (1.8F / speed));
                }
                else {
                    speed = Mth.approach(speed, 0.2F, 0.025F);
                }
                float pitch = (float) (-(Mth.atan2(-dy, horizontal) * 180.0F / (float) Math.PI));
                setXRot(pitch);
                float heading = getYRot() + 90.0F;
                double mx = speed * Mth.cos(heading * (float) (Math.PI / 180.0)) * Math.abs(dx / length);
                double mz = speed * Mth.sin(heading * (float) (Math.PI / 180.0)) * Math.abs(dz / length);
                double my = speed * Mth.sin(pitch * (float) (Math.PI / 180.0)) * Math.abs(dy / length);
                Vec3 motion = getDeltaMovement();
                setDeltaMovement(motion.add(new Vec3(mx, my, mz).subtract(motion).scale(0.2D)));
            }
        }
    }
}
