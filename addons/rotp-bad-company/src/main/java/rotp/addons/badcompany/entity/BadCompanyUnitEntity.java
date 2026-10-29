package rotp.addons.badcompany.entity;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.badcompany.BadCompanyAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public abstract class BadCompanyUnitEntity
        extends PathfinderMob
        implements BadCompanyOwnedEntity {
    public static final int ORPHAN_GRACE_TICKS = 100;
    // 1.16 FollowOwnerGoal start distance: an idle unit this far from its
    // owner was teleported back.
    public static final double TELEPORT_DISTANCE = 30.0D;
    // 1.16 BadCompanyUnitEntity.tick: a unit ordered to stay close was
    // teleported back to its owner beyond 10 + 10 blocks, fighting or
    // going to a block alike.
    public static final double STAY_CLOSE_TELEPORT_DISTANCE = 20.0D;

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    BadCompanyUnitEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            COORDINATOR_UUID = SynchedEntityData.defineId(
                    BadCompanyUnitEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> SLOT =
            SynchedEntityData.defineId(
                    BadCompanyUnitEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHOOTING =
            SynchedEntityData.defineId(
                    BadCompanyUnitEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private int orphanTicks;
    private int attackCooldown;
    private int shootingTicks;
    @Nullable
    private LivingEntity combatTarget;
    @Nullable
    private BadCompanyStandEntity cachedCoordinator;
    private boolean destinationRevisionInitialized;
    private long completedDestinationRevision;

    protected BadCompanyUnitEntity(
            EntityType<? extends BadCompanyUnitEntity> type,
            Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(COORDINATOR_UUID, Optional.empty());
        builder.define(SLOT, -1);
        builder.define(SHOOTING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(
                7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public final void bindClosure(
            UUID ownerUuid,
            UUID coordinatorUuid,
            int slot) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(
                OWNER_UUID, Optional.ofNullable(ownerUuid));
        entityData.set(
                COORDINATOR_UUID,
                Optional.ofNullable(coordinatorUuid));
        entityData.set(
                SLOT,
                Mth.clamp(slot, 0, unitType().cap() - 1));
        cachedCoordinator = null;
        orphanTicks = 0;
        destinationRevisionInitialized = false;
        completedDestinationRevision = 0L;
    }

    public final void initializeDestinationRevision(long revision) {
        if (!destinationRevisionInitialized) {
            // Newly created units do not inherit an older go-to order.
            completedDestinationRevision = revision;
            destinationRevisionInitialized = true;
        }
    }

    @Override
    public final boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    @Override
    public final boolean isBoundTo(UUID coordinatorUuid) {
        return coordinatorUuid != null
                && entityData.get(COORDINATOR_UUID)
                        .filter(coordinatorUuid::equals)
                        .isPresent();
    }

    public final int slot() {
        return entityData.get(SLOT);
    }

    public final boolean isShooting() {
        return entityData.get(SHOOTING);
    }

    public abstract BadCompanyUnitType unitType();

    protected abstract double combatRange();

    protected abstract int attackInterval();

    // Cooldown set after a shot; retargeting keeps attackInterval().
    protected int attackIntervalAfterShot(LivingEntity target) {
        return attackInterval();
    }

    protected abstract boolean performRangedAttack(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target);

    protected double navigationSpeed() {
        return 0.6D;
    }

    protected double formationHeightOffset() {
        return 0.0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (shootingTicks > 0
                && --shootingTicks == 0) {
            entityData.set(SHOOTING, false);
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        LivingEntity owner = resolveOwner();
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        if (!hasValidClosure(owner, coordinator)) {
            combatTarget = null;
            getNavigation().stop();
            if (++orphanTicks >= ORPHAN_GRACE_TICKS) {
                discard();
            }
            return;
        }
        orphanTicks = 0;
        coordinator.observeMember(this);

        long destinationRevision = coordinator.destinationRevision(unitType());
        initializeDestinationRevision(destinationRevision);
        Optional<BlockPos> destination = coordinator.destination(unitType())
                .filter(position -> completedDestinationRevision != destinationRevision);
        if (destination.isPresent()
                && position().distanceToSqr(
                        Vec3.atBottomCenterOf(destination.get())) < 1.0D) {
            completedDestinationRevision = destinationRevision;
            destination = Optional.empty();
        }
        boolean waiting = coordinator.isWaiting(unitType())
                && destination.isEmpty();
        if (waiting) {
            addEffect(new MobEffectInstance(
                    BadCompanyAddon.SAT,
                    5,
                    0,
                    false,
                    false,
                    false));
        } else if (destination.isPresent()) {
            removeEffect(BadCompanyAddon.SAT);
        }

        Vec3 desired = destination
                .map(Vec3::atBottomCenterOf)
                .orElseGet(() -> coordinator.formationPosition(
                        owner, unitType(), slot())
                        .add(0.0D, formationHeightOffset(), 0.0D));
        moveForOrder(owner, coordinator, desired, waiting, destination.isPresent());

        if (coordinator.isVolleyMode()
                && unitType() == BadCompanyUnitType.SOLDIER) {
            combatTarget = null;
            return;
        }

        if (combatTarget == null
                || !isValidTarget(owner, combatTarget)
                || tickCount % attackInterval()
                        == Math.floorMod(
                                slot(), attackInterval())) {
            combatTarget = chooseTarget(
                    (ServerLevel) level(),
                    owner,
                    coordinator);
        }
        if (combatTarget != null
                && hasLineOfSight(combatTarget)
                && distanceToSqr(combatTarget)
                        <= combatRange() * combatRange()) {
            getLookControl().setLookAt(
                    combatTarget, 30.0F, 30.0F);
            if (attackCooldown <= 0
                    && performRangedAttack(
                            (ServerLevel) level(),
                            owner,
                            combatTarget)) {
                attackCooldown =
                        attackIntervalAfterShot(combatTarget);
                markShooting(4);
            }
        }
    }

    private void moveForOrder(
            LivingEntity owner,
            BadCompanyStandEntity coordinator,
            Vec3 desired,
            boolean waiting,
            boolean goingToDestination) {
        double distanceToOwner = distanceToSqr(owner);
        boolean stayingClose = coordinator.isStayingClose(unitType());
        double teleportDistance = stayingClose
                ? STAY_CLOSE_TELEPORT_DISTANCE
                : TELEPORT_DISTANCE;
        if (distanceToOwner
                > teleportDistance * teleportDistance) {
            // A stay-close recall lands at the owner's formation slot even
            // with a go-to destination set; the unit then walks out again.
            Vec3 recall = stayingClose
                    ? coordinator.formationPosition(
                                    owner, unitType(), slot())
                            .add(0.0D, formationHeightOffset(), 0.0D)
                    : desired;
            if (level().hasChunkAt(BlockPos.containing(recall))) {
                teleportTo(recall.x, recall.y, recall.z);
            }
        }
        if (waiting) {
            holdPosition();
            return;
        }
        double threshold = coordinator.isStayingClose(unitType())
                || goingToDestination
                || coordinator.isVolleyMode()
                        ? 0.16D
                        : 100.0D;
        if (position().distanceToSqr(desired) > threshold) {
            getNavigation().moveTo(
                    desired.x,
                    desired.y,
                    desired.z,
                    navigationSpeed());
        } else {
            getNavigation().stop();
        }
    }

    public final void holdPosition() {
        getNavigation().stop();
        setDeltaMovement(
                0.0D,
                Math.min(0.0D, getDeltaMovement().y),
                0.0D);
    }

    public final void clearCombatTarget() {
        combatTarget = null;
        setTarget(null);
    }

    protected final void markShooting(int ticks) {
        shootingTicks = Math.max(shootingTicks, ticks);
        entityData.set(SHOOTING, true);
    }

    @Nullable
    private LivingEntity chooseTarget(
            ServerLevel level,
            LivingEntity owner,
            BadCompanyStandEntity coordinator) {
        LivingEntity commanded =
                coordinator.commandTarget(unitType());
        if (isValidTarget(owner, commanded)) {
            return commanded;
        }
        // 1.16 HurtByTargetGoal: fire back at whoever hit this unit.
        LivingEntity selfAttacker = getLastHurtByMob();
        if (isValidTarget(owner, selfAttacker)) {
            return selfAttacker;
        }
        LivingEntity retaliation = owner.getLastHurtByMob();
        if (isValidTarget(owner, retaliation)) {
            return retaliation;
        }
        LivingEntity attacked = owner.getLastHurtMob();
        if (isValidTarget(owner, attacked)) {
            return attacked;
        }
        // 1.16 scanned every IMob, not only monsters (slimes, ghasts, ...).
        return level.getEntitiesOfClass(
                        Mob.class,
                        getBoundingBox().inflate(combatRange()),
                        mob -> mob instanceof Enemy
                                && isValidTarget(owner, mob))
                .stream()
                .min(Comparator.comparingDouble(
                        this::distanceToSqr))
                .orElse(null);
    }

    protected final boolean isValidTarget(
            LivingEntity owner,
            @Nullable LivingEntity candidate) {
        return candidate != null
                && candidate.isAlive()
                && candidate.level() == level()
                && !candidate.isAlliedTo(owner)
                && !isAlliedTo(candidate)
                && distanceToSqr(candidate)
                        <= combatRange() * combatRange()
                && owner.distanceToSqr(candidate)
                        <= BadCompanyStandEntity.LEGACY_MAX_RANGE
                                * BadCompanyStandEntity.LEGACY_MAX_RANGE;
    }

    private boolean hasValidClosure(
            @Nullable LivingEntity owner,
            @Nullable BadCompanyStandEntity coordinator) {
        if (owner == null
                || coordinator == null
                || !coordinator.isAlive()
                || !coordinator.isActiveFor(owner)
                || !isOwnedBy(owner.getUUID())
                || !isBoundTo(coordinator.getUUID())) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.getPowerType()
                        == BadCompanyAddon.BAD_COMPANY.get()
                && power.getSummonedStandEntity()
                        == coordinator;
    }

    @Nullable
    protected final LivingEntity resolveOwner() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return entityData.get(OWNER_UUID)
                .map(serverLevel::getEntity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .orElse(null);
    }

    @Nullable
    @Override
    public final BadCompanyStandEntity resolveCoordinator() {
        UUID coordinatorUuid = entityData.get(COORDINATOR_UUID)
                .orElse(null);
        if (coordinatorUuid == null) {
            return null;
        }
        if (cachedCoordinator != null
                && !cachedCoordinator.isRemoved()
                && cachedCoordinator.level() == level()
                && coordinatorUuid.equals(
                        cachedCoordinator.getUUID())) {
            return cachedCoordinator;
        }
        Entity resolved = level() instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(coordinatorUuid)
                : level().getEntitiesOfClass(
                        BadCompanyStandEntity.class,
                        getBoundingBox().inflate(
                                BadCompanyStandEntity
                                        .LEGACY_MAX_RANGE + 8.0D),
                        stand -> coordinatorUuid.equals(
                                stand.getUUID()))
                        .stream()
                        .findFirst()
                        .orElse(null);
        cachedCoordinator = resolved
                instanceof BadCompanyStandEntity stand
                        ? stand
                        : null;
        return cachedCoordinator;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(
                DamageTypeTags.BYPASSES_INVULNERABILITY)
                || DamageUtil.canHurtStands(source)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean isAlliedTo(Entity entity) {
        UUID ownerUuid = entityData.get(OWNER_UUID)
                .orElse(null);
        if (ownerUuid != null) {
            if (ownerUuid.equals(entity.getUUID())) {
                return true;
            }
            if (entity instanceof BadCompanyOwnedEntity owned
                    && owned.isOwnedBy(ownerUuid)) {
                return true;
            }
        }
        return super.isAlliedTo(entity);
    }

    @Override
    public void remove(RemovalReason reason) {
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        if (!level().isClientSide() && coordinator != null) {
            coordinator.onMemberRemoved(this, reason);
        }
        cachedCoordinator = null;
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(OWNER_UUID)
                .ifPresent(uuid ->
                        tag.putUUID("BadCompanyOwner", uuid));
        entityData.get(COORDINATOR_UUID)
                .ifPresent(uuid ->
                        tag.putUUID(
                                "BadCompanyCoordinator", uuid));
        tag.putInt("BadCompanySlot", slot());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("BadCompanyOwner")
                        ? Optional.of(
                                tag.getUUID("BadCompanyOwner"))
                        : Optional.empty());
        entityData.set(
                COORDINATOR_UUID,
                tag.hasUUID("BadCompanyCoordinator")
                        ? Optional.of(tag.getUUID(
                                "BadCompanyCoordinator"))
                        : Optional.empty());
        entityData.set(
                SLOT,
                Mth.clamp(
                        tag.getInt("BadCompanySlot"),
                        -1,
                        Math.max(0, unitType().cap() - 1)));
        cachedCoordinator = null;
    }
}
