package rotp.addons.killerqueen.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.EntityWithStandSkin;
import rotp.core.init.ModEntityDataSerializers;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.state.KillerQueenAuthority;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.event.EventHooks;

public final class SheerHeartAttackEntity extends Monster
        implements EntityWithStandSkin {
    public static final int SWELL_TICKS = 15;
    public static final int POST_EXPLOSION_IDLE_TICKS = 200;
    public static final float EXPLOSION_RADIUS = 3.0F;
    // 1.16 SheerHeart leaves FOLLOW_RANGE at the mob default
    public static final double TARGET_RANGE = 16.0D;
    public static final double MAX_OWNER_DISTANCE = 140.0D;
    // 1.16 SheerHeartSwelGoal: start within 3 blocks, keep up to 7
    public static final double SWELL_START_DISTANCE_SQR = 9.0D;
    public static final double SWELL_KEEP_DISTANCE_SQR = 49.0D;

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    SheerHeartAttackEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            STAND_UUID = SynchedEntityData.defineId(
                    SheerHeartAttackEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<
            Optional<ResourceLocation>> SELECTED_SKIN =
            SynchedEntityData.defineId(
                    SheerHeartAttackEntity.class,
                    ModEntityDataSerializers
                            .RESOURCE_LOCATION_OPTIONAL.get());
    private static final EntityDataAccessor<Integer> SWELL =
            SynchedEntityData.defineId(
                    SheerHeartAttackEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>
            DISABLED_TICKS = SynchedEntityData.defineId(
                    SheerHeartAttackEntity.class,
                    EntityDataSerializers.INT);

    // Swell direction set by SwellGoal (server only)
    private int swellDir = -1;

    public SheerHeartAttackEntity(
            EntityType<? extends SheerHeartAttackEntity> type,
            Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, TARGET_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(STAND_UUID, Optional.empty());
        builder.define(SELECTED_SKIN, Optional.empty());
        builder.define(SWELL, 0);
        builder.define(DISABLED_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Holds MOVE over the melee chase while swelling
        goalSelector.addGoal(1, new SwellGoal());
        goalSelector.addGoal(
                2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(
                5,
                new WaterAvoidingRandomStrollGoal(
                        this, 0.8D));
        goalSelector.addGoal(
                6,
                new LookAtPlayerGoal(
                        this, Player.class, 8.0F));
        goalSelector.addGoal(
                7, new RandomLookAroundGoal(this));
        // 1.16 SheerHeart target selectors, same classes and priorities
        targetSelector.addGoal(
                1,
                new NearestAttackableTargetGoal<>(
                        this, Player.class, true));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtByTargetGoal());
        targetSelector.addGoal(2, new OwnerHurtTargetGoal());
        targetSelector.addGoal(
                3,
                new NearestAttackableTargetGoal<>(
                        this, Monster.class, true));
    }

    public void bind(
            ServerPlayer owner,
            StandEntity stand,
            @Nullable LivingEntity initialTarget) {
        if (!(level() instanceof ServerLevel level)
                || !KillerQueenAuthority.validController(
                        owner, stand)) {
            discard();
            return;
        }
        // 1.16 SheerHeartAttack.standPerform: nothing takes a bomb as a target
        setInvulnerable(true);
        entityData.set(
                OWNER_UUID,
                Optional.of(owner.getUUID()),
                true);
        entityData.set(
                STAND_UUID,
                Optional.of(stand.getUUID()),
                true);
        entityData.set(
                SELECTED_SKIN,
                stand.getStandSkin(),
                true);
        moveTo(
                stand.getX(),
                stand.getY(),
                stand.getZ(),
                stand.getYRot(),
                stand.getXRot());
        if (isValidTarget(owner, initialTarget)) {
            setTarget(initialTarget);
        }
    }

    @Nullable
    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    @Nullable
    public UUID getStandUuid() {
        return entityData.get(STAND_UUID).orElse(null);
    }

    @Nullable
    public LivingEntity getKillerQueenOwner() {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null) {
            return null;
        }
        if (level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(ownerUuid);
            return entity instanceof LivingEntity living
                    ? living
                    : null;
        }
        return level().getPlayerByUUID(ownerUuid);
    }

    public boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    public int swellTicks() {
        return entityData.get(SWELL);
    }

    public int postExplosionIdleTicks() {
        return entityData.get(DISABLED_TICKS);
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel serverLevel) {
            updateSummonLink(serverLevel);
        }
        super.tick();
    }

    @Override
    protected void customServerAiStep() {
        // 1.16 SheerHeart has no owner check in its AI: with its owner
        // away from its level it goes on hunting by itself.
        LivingEntity owner = getKillerQueenOwner();
        int disabled = entityData.get(DISABLED_TICKS);
        if (disabled > 0) {
            entityData.set(DISABLED_TICKS, disabled - 1);
            entityData.set(SWELL, 0);
            getNavigation().stop();
            return;
        }

        // New targets come only from the target selectors; an aimed
        // target that stopped being attackable is just dropped.
        LivingEntity target = getTarget();
        if (target != null && !isValidTarget(owner, target)) {
            setTarget(null);
            target = null;
        }
        updateSwell(owner, target);
        super.customServerAiStep();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return isValidTarget(getKillerQueenOwner(), target)
                && super.canAttack(target);
    }

    // 1.16 SheerHeart.wantsToAttack
    private static boolean wantsToAttack(
            LivingEntity target, LivingEntity owner) {
        return !(target instanceof Player targetPlayer)
                || !(owner instanceof Player ownerPlayer)
                || ownerPlayer.canHarmPlayer(targetPlayer);
    }

    @Override
    public PlayerTeam getTeam() {
        LivingEntity owner = getKillerQueenOwner();
        return owner != null ? owner.getTeam() : super.getTeam();
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        LivingEntity owner = getKillerQueenOwner();
        if (owner == null) {
            return super.isAlliedTo(entity);
        }
        return entity == owner || owner.isAlliedTo(entity);
    }

    // 1.16 SheerHeart.canAttack spares its owner by UUID; an absent
    // owner leaves the bomb to the mob rules alone.
    private boolean isValidTarget(
            @Nullable LivingEntity owner,
            @Nullable LivingEntity target) {
        return target != null
                && target != this
                && target.isAlive()
                && !target.getUUID().equals(getOwnerUuid())
                && !(target instanceof StandEntity)
                && !(target
                        instanceof SheerHeartAttackEntity sheer
                        && sheer.isOwnedBy(getOwnerUuid()))
                && (owner == null
                        ? super.canAttack(target)
                        : owner.canAttack(target)
                                && JojoModUtil.canHarm(owner, target));
    }

    private void updateSwell(
            @Nullable LivingEntity owner,
            @Nullable LivingEntity target) {
        int swell = entityData.get(SWELL);
        if (swellDir <= 0) {
            if (swell > 0) {
                entityData.set(SWELL, swell - 1);
            }
            return;
        }
        if (swell == 0) {
            playSound(
                    KillerQueenAddon.SHEER_HEART.get(),
                    1.0F,
                    0.5F);
        }
        swell++;
        entityData.set(SWELL, swell);
        if (swell < SWELL_TICKS) {
            return;
        }
        // 1.16 explodeCreeper: the owner's blast, or the bomb's own without him
        level().explode(
                owner != null ? owner : this,
                getX(),
                getY(),
                getZ(),
                EXPLOSION_RADIUS,
                explosionInteraction());
        swellDir = -1;
        entityData.set(SWELL, 0);
        entityData.set(
                DISABLED_TICKS,
                POST_EXPLOSION_IDLE_TICKS);
        // 1.16 SheerHeart.explodeCreeper removes a bomb whose blast left its target dead
        if (target != null && !target.isAlive()) {
            discard();
            if (owner instanceof ServerPlayer player) {
                KillerQueenStateService.releaseSheerHeart(
                        player, getUUID());
            }
        }
    }

    // 1.16: breaks blocks only with the break-blocks rule and mob griefing
    private Level.ExplosionInteraction explosionInteraction() {
        return JojoModUtil.breakingBlocksEnabled(level())
                        && EventHooks.canEntityGrief(level(), this)
                ? Level.ExplosionInteraction.MOB
                : Level.ExplosionInteraction.NONE;
    }

    // 1.16 SheerHeartSwelGoal: stands still while swelling
    private final class SwellGoal extends Goal {
        @Nullable
        private LivingEntity swellTarget;

        private SwellGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (entityData.get(DISABLED_TICKS) > 0) {
                return false;
            }
            if (swellDir > 0) {
                return true;
            }
            LivingEntity target = getTarget();
            return target != null
                    && distanceToSqr(target)
                            < SWELL_START_DISTANCE_SQR
                    && getSensing().hasLineOfSight(target);
        }

        // Saved target stale (dead or replaced): restart on the current one
        @Override
        public boolean canContinueToUse() {
            return entityData.get(DISABLED_TICKS) <= 0
                    && (swellDir > 0
                            || (getTarget() == swellTarget && canUse()));
        }

        @Override
        public void start() {
            getNavigation().stop();
            swellTarget = getTarget();
        }

        @Override
        public void stop() {
            swellTarget = null;
            swellDir = -1;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = swellTarget;
            swellDir = target != null
                            && target.isAlive()
                            && distanceToSqr(target)
                                    <= SWELL_KEEP_DISTANCE_SQR
                            && getSensing().hasLineOfSight(target)
                    ? 1
                    : -1;
        }
    }

    // 1.16 MasterHurtByTargetGoal. Its flag is MOVE, not TARGET, as in
    // the donor: no other target goal holds MOVE, so the owner's
    // attacker is taken whatever the bomb is hunting.
    private final class OwnerHurtByTargetGoal extends TargetGoal {
        @Nullable
        private LivingEntity attacker;
        private int timestamp;

        private OwnerHurtByTargetGoal() {
            super(SheerHeartAttackEntity.this, false);
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = getKillerQueenOwner();
            if (owner == null) {
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
            LivingEntity owner = getKillerQueenOwner();
            if (owner != null) {
                timestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    // 1.16 MasterHurtTargetGoal: the owner's victim, once its last
    // damage is forgotten (older than 40 ticks) or was a blood drain.
    private final class OwnerHurtTargetGoal extends TargetGoal {
        @Nullable
        private LivingEntity attacked;
        private int timestamp;

        private OwnerHurtTargetGoal() {
            super(SheerHeartAttackEntity.this, false);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = getKillerQueenOwner();
            if (owner == null) {
                return false;
            }
            attacked = owner.getLastHurtMob();
            DamageSource attackedBy = attacked != null
                    ? attacked.getLastDamageSource()
                    : null;
            return owner.getLastHurtMobTimestamp() != timestamp
                    && (attackedBy == null
                            || attackedBy.getMsgId()
                                    .startsWith("bloodDrain"))
                    && canAttack(attacked, TargetingConditions.DEFAULT)
                    && wantsToAttack(attacked, owner);
        }

        @Override
        public void start() {
            mob.setTarget(attacked);
            LivingEntity owner = getKillerQueenOwner();
            if (owner != null) {
                timestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    // 1.16 SheerHeart is a saved mob of its own that removes itself only
    // when its blast kills its target, besides the vanilla mob despawn
    // (Peaceful, no player near), which never looks at its owner: without
    // an owner, in a dimension it reached by itself, or beside an owner who
    // died, logged out or lost Killer Queen, it lives on and hunts. Its tick
    // only keeps his summon.
    private void updateSummonLink(ServerLevel level) {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null
                || !(level.getEntity(ownerUuid)
                        instanceof ServerPlayer player)) {
            return;
        }
        // 1.16 isSHpresent looked only 140 blocks around the user: a bomb
        // farther away no longer holds his summon
        if (!player.getBoundingBox()
                .inflate(MAX_OWNER_DISTANCE)
                .intersects(getBoundingBox())) {
            KillerQueenStateService.releaseSheerHeart(
                    player, getUUID());
            return;
        }
        if (!KillerQueenAuthority.validSheerHeartLink(
                level, ownerUuid, getUUID())) {
            KillerQueenStateService.reclaimSheerHeart(player, this);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return target instanceof LivingEntity living
                && isValidTarget(getKillerQueenOwner(), living)
                && super.doHurtTarget(target);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return KillerQueenAddon.SHEER_HEART.get();
    }

    @Override
    protected void playStepSound(
            BlockPos position, BlockState state) {
        playSound(
                KillerQueenAddon.SHEER_HEART_STEP.get(),
                0.15F,
                1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER_UUID)
                .ifPresent(uuid -> tag.putUUID("Owner", uuid));
        entityData.get(STAND_UUID)
                .ifPresent(uuid -> tag.putUUID("Stand", uuid));
        getStandSkin().ifPresent(skin ->
                tag.putString("StandSkin", skin.toString()));
        tag.putInt("Swell", entityData.get(SWELL));
        tag.putInt(
                "DisabledTicks",
                entityData.get(DISABLED_TICKS));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // The port once kept the bomb from despawning; an old save still
        // carries that flag, which 1.16's ordinary despawning mob never had
        // (only a name tag made it persistent)
        if (!tag.contains("CustomName")) {
            tag.remove("PersistenceRequired");
        }
        super.readAdditionalSaveData(tag);
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(
                STAND_UUID,
                tag.hasUUID("Stand")
                        ? Optional.of(tag.getUUID("Stand"))
                        : Optional.empty());
        ResourceLocation skin = ResourceLocation.tryParse(
                tag.getString("StandSkin"));
        entityData.set(
                SELECTED_SKIN, Optional.ofNullable(skin));
        entityData.set(SWELL, tag.getInt("Swell"));
        entityData.set(
                DISABLED_TICKS,
                tag.getInt("DisabledTicks"));
    }

    @Override
    public ResourceLocation getStandType() {
        return KillerQueenAddon.id("killer_queen");
    }

    @Override
    public Optional<ResourceLocation> getStandSkin() {
        return entityData.get(SELECTED_SKIN);
    }
}
