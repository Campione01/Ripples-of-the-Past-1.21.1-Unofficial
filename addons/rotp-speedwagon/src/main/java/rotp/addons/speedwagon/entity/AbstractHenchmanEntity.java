package rotp.addons.speedwagon.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.playerpower.PlayerPower;

import rotp.addons.speedwagon.SpeedwagonAddon;
import rotp.addons.speedwagon.power.SpeedwagonData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class AbstractHenchmanEntity
        extends TamableAnimal {
    private static final String SUMMONED_TAG = "AbilitySummon";
    // 1.16 farFromOwner(12.0): no following or owner fights past 12 blocks
    private static final double OWNER_RANGE = 12.0D;
    private boolean summonedFromAbility;
    private boolean hideOwnerForDeath;

    protected AbstractHenchmanEntity(
            EntityType<? extends AbstractHenchmanEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(
                4, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(
                5, new MeleeAttackGoal(this, 1.0D, true));
        goalSelector.addGoal(
                6,
                new HenchmanFollowOwnerGoal(
                        this, 1.0D, 10.0F, 2.0F));
        goalSelector.addGoal(
                8,
                new WaterAvoidingRandomStrollGoal(
                        this, 1.0D));
        goalSelector.addGoal(
                10,
                new LookAtPlayerGoal(
                        this, Player.class, 8.0F));
        goalSelector.addGoal(
                10, new RandomLookAroundGoal(this));

        targetSelector.addGoal(
                1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(
                2, new HenchmanOwnerHurtTargetGoal(this));
        targetSelector.addGoal(
                7,
                new NearestAttackableTargetGoal<>(
                        this,
                        AbstractSkeleton.class,
                        false));
    }

    public void setSummonedBy(LivingEntity owner) {
        setOwnerUUID(owner.getUUID());
        setTame(true, true);
        summonedFromAbility = true;
        setPersistenceRequired();
    }

    public boolean isSummonedFromAbility() {
        return summonedFromAbility;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel
                && summonedFromAbility
                && tickCount % 20 == 0) {
            UUID ownerId = getOwnerUUID();
            ServerPlayer owner = ownerId != null
                    ? serverLevel.getServer()
                            .getPlayerList()
                            .getPlayer(ownerId)
                    : null;
            if (owner != null
                    && PlayerPower.getPowerData(
                            owner, SpeedwagonAddon.SPEEDWAGON)
                            .isEmpty()) {
                discard();
            }
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isOwnedBy(target) && super.canAttack(target);
    }

    public boolean isFarFromOwner() {
        LivingEntity owner = getOwner();
        return owner != null
                && distanceToSqr(owner) > OWNER_RANGE * OWNER_RANGE;
    }

    // 1.16 henchmen were not tameable and never teleported to the owner
    @Override
    public boolean shouldTryTeleportToOwner() {
        return false;
    }

    // Gates both owner target goals, like the 1.16 henchman goals
    @Override
    public boolean wantsToAttack(
            LivingEntity target, LivingEntity owner) {
        return !isFarFromOwner()
                && super.wantsToAttack(target, owner);
    }

    // Null only inside die(), so TamableAnimal posts no owner death line
    @Nullable
    @Override
    public LivingEntity getOwner() {
        return hideOwnerForDeath ? null : super.getOwner();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) {
            notifyOwnerRemoved();
        }
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        notifyOwnerRemoved();
        hideOwnerForDeath = true;
        try {
            super.die(source);
        } finally {
            hideOwnerForDeath = false;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(SUMMONED_TAG, summonedFromAbility);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        summonedFromAbility = tag.getBoolean(SUMMONED_TAG);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(
            ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    private void notifyOwnerRemoved() {
        if (!summonedFromAbility
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID ownerId = getOwnerUUID();
        ServerPlayer owner = ownerId != null
                ? serverLevel.getServer()
                        .getPlayerList()
                        .getPlayer(ownerId)
                : null;
        if (owner == null) {
            return;
        }
        SpeedwagonData data = PlayerPower.getPowerData(
                owner, SpeedwagonAddon.SPEEDWAGON)
                .orElse(null);
        if (data != null) {
            data.untrackSummon(owner, getUUID());
        }
    }

    private static final class HenchmanOwnerHurtTargetGoal
            extends OwnerHurtTargetGoal {
        private final AbstractHenchmanEntity henchman;

        private HenchmanOwnerHurtTargetGoal(AbstractHenchmanEntity henchman) {
            super(henchman);
            this.henchman = henchman;
        }

        @Override
        public boolean canUse() {
            if (!super.canUse()) {
                return false;
            }
            LivingEntity owner = henchman.getOwner();
            LivingEntity attacked = owner != null ? owner.getLastHurtMob() : null;
            DamageSource lastDamage = attacked != null ? attacked.getLastDamageSource() : null;
            return lastDamage == null || lastDamage.getMsgId().startsWith("bloodDrain");
        }
    }

    // 1.16 HenchmanFollowOwnerGoal: walks only, stays put past 12 blocks
    private static final class HenchmanFollowOwnerGoal
            extends FollowOwnerGoal {
        private final AbstractHenchmanEntity henchman;
        private final double speed;
        private int recalcDelay;

        private HenchmanFollowOwnerGoal(
                AbstractHenchmanEntity henchman, double speed,
                float startDistance, float stopDistance) {
            super(henchman, speed, startDistance, stopDistance);
            this.henchman = henchman;
            this.speed = speed;
        }

        @Override
        public void start() {
            super.start();
            recalcDelay = 0;
        }

        @Override
        public void tick() {
            LivingEntity owner = henchman.getOwner();
            if (owner == null) {
                return;
            }
            henchman.getLookControl().setLookAt(
                    owner, 10.0F, henchman.getMaxHeadXRot());
            if (--recalcDelay <= 0) {
                recalcDelay = adjustedTickDelay(10);
                if (!henchman.isFarFromOwner()) {
                    henchman.getNavigation().moveTo(owner, speed);
                }
            }
        }
    }
}
