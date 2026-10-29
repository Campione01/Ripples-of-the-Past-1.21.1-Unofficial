package rotp.addons.ultimate_lifeform.entity;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * 1.16 UltimateLifeformKillerRabbitEntity: an ordinary rabbit (no killer bunny name or armor) with its own goals, an
 * 8-damage bite, following and teleporting to its owner, and after its 600 ticks (or below 3 health) a walk back to
 * the owner, where it vanishes off the count without a health debuff.
 */
public final class UltimateRabbitEntity extends Rabbit {
    @Nullable private UUID ownerUuid;
    // the ticks left of the 600 (1.16 counted ticksAlive up to 600)
    private int lifeTicks = 600;
    private double distanceFromOwner;
    private boolean deathReported;
    private boolean restoredFromDisk;

    public UltimateRabbitEntity(
            EntityType<? extends Rabbit> type, Level level) {
        super(type, level);
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
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.4D, true));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Wolf.class, true));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Rabbit.class, true));
        goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        playSound(SoundEvents.RABBIT_ATTACK, 1.0F,
                (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
        return target.hurt(damageSources().mobAttack(this), 8.0F);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof UltimateRabbitEntity other) {
            return !java.util.Objects.equals(ownerUuid, other.ownerUuid);
        }
        if (target instanceof Wolf wolf) {
            return !java.util.Objects.equals(wolf.getOwnerUUID(), ownerUuid);
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
            data.setSummonedRabbitsLifetime(lifeTicks);
        }
        if (!owner.isAlive()) {
            kill();
            return;
        }
        if (owner.onGround() && !closerThan(owner, 32.0D)) {
            teleportToOwner(owner);
        }
        if (owner.onGround() && !closerThan(owner, 8.0D)) {
            getNavigation().moveTo(owner, 1.0D);
        }
        distanceFromOwner = distanceToSqr(owner);
        if (lifeTicks > 0) {
            lifeTicks--;
        }
        if (lifeTicks <= 0 || getHealth() < 3.0F) {
            getNavigation().moveTo(owner, 1.0D);
            if (closerThan(owner, 1.0D)) {
                discard();
                if (data != null) {
                    data.summonedRabbitReturned();
                    data.syncOnUpdate(owner);
                }
            }
        }
    }

    private boolean farFromOwner(double distance) {
        return distanceFromOwner > distance * distance;
    }

    // 1.16 wantsToAttack: a player only where the owner may harm it
    private static boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !(target instanceof Player targetPlayer)
                || !(owner instanceof Player ownerPlayer)
                || ownerPlayer.canHarmPlayer(targetPlayer)
                || target == owner
                || target instanceof TamableAnimal pet && pet.getOwner() == owner;
    }

    // 1.16 copied the tamed animals' teleport to the owner
    private void teleportToOwner(LivingEntity owner) {
        BlockPos center = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int x = center.getX() + randomIntInclusive(-3, 3);
            int y = center.getY() + randomIntInclusive(-1, 1);
            int z = center.getZ() + randomIntInclusive(-3, 3);
            if (maybeTeleportTo(owner, x, y, z)) {
                return;
            }
        }
    }

    private boolean maybeTeleportTo(LivingEntity owner, int x, int y, int z) {
        if (Math.abs(x - owner.getX()) < 2.0D && Math.abs(z - owner.getZ()) < 2.0D) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (WalkNodeEvaluator.getPathTypeStatic(this, pos) != PathType.WALKABLE
                || level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock
                || !level().noCollision(this, getBoundingBox().move(pos.subtract(blockPosition())))) {
            return false;
        }
        moveTo(x + 0.5D, y, z + 0.5D, getYRot(), getXRot());
        getNavigation().stop();
        return true;
    }

    private int randomIntInclusive(int min, int max) {
        return random.nextInt(max - min + 1) + min;
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
                        data.summonedRabbitDied();
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

    // 1.16 RabbitOwnerHurtByTargetGoal: the owner's last attacker, within 12
    // blocks of the owner
    private final class OwnerHurtByTargetGoal extends TargetGoal {
        @Nullable private LivingEntity attacker;
        private int timestamp;

        OwnerHurtByTargetGoal() {
            super(UltimateRabbitEntity.this, false);
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
            LivingEntity owner = getOwner();
            if (owner != null) {
                timestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    // 1.16 RabbitOwnerHurtTargetGoal: the mob the owner last hurt, once its
    // last damage is gone (or was a blood drain)
    private final class OwnerHurtTargetGoal extends TargetGoal {
        @Nullable private LivingEntity attacked;
        private int timestamp;

        OwnerHurtTargetGoal() {
            super(UltimateRabbitEntity.this, false);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = getOwner();
            if (owner == null || farFromOwner(12.0D)) {
                return false;
            }
            attacked = owner.getLastHurtMob();
            DamageSource attackedBy = attacked != null
                    ? attacked.getLastDamageSource()
                    : null;
            return owner.getLastHurtMobTimestamp() != timestamp
                    && (attackedBy == null
                            || attackedBy.getMsgId().startsWith("bloodDrain"))
                    && canAttack(attacked, TargetingConditions.DEFAULT)
                    && wantsToAttack(attacked, owner);
        }

        @Override
        public void start() {
            mob.setTarget(attacked);
            LivingEntity owner = getOwner();
            if (owner != null) {
                timestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }
}
