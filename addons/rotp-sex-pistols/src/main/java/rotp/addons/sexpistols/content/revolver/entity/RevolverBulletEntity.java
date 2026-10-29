package rotp.addons.sexpistols.content.revolver.entity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.init.ModEntityDataSerializers;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.customobjects.EntityWithStandSkin;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.sexpistols.config.SexPistolsConfig;
import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsJoyful;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsMemberState;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsStamina;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsTargetMode;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsTransferOrder;
import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.network.RevolverVisualPayloads;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsEntities;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RevolverBulletEntity
        extends ModdedProjectileEntity
        implements EntityWithStandSkin {
    private static final BigInteger INVALID_OWNER_EPOCH =
            BigInteger.valueOf(-1L);
    public static final int MAX_LIFETIME_TICKS = 120;
    public static final float SPEED = 5.9F;
    public static final double CLOSE_DAMAGE_END = 5.0D;
    public static final double STABLE_DAMAGE_END = 50.0D;
    public static final float CLOSE_DAMAGE = 10.0F;
    public static final float STABLE_DAMAGE = 5.0F;
    public static final float MIN_DAMAGE = 1.0F;
    public static final double FAR_DAMAGE_DROP_RATE = 0.28D;
    public static final double EFFECTIVE_GRAVITY = 0.018D;
    public static final double DRAG = 0.99D;
    public static final int MAX_RICOCHETS = 2;
    public static final long BLOCK_DAMAGE_MEMORY_TICKS = 100L;
    public static final int MAX_TRACKED_BLOCK_DAMAGE = 512;
    public static final int TIME_STOP_SLOWDOWN_TICKS = 3;
    private static final double RICOCHET_MIN_SPEED_SQR = 0.04D;
    private static final double RICOCHET_SURFACE_OFFSET = 0.18D;
    private static final double RICOCHET_TRAVEL_BIAS = 0.06D;
    public static final double TARGET_SEARCH_RANGE = 32.0D;
    public static final double TRANSFER_SEARCH_RANGE = 18.0D;
    public static final float MEMBER_KICK_STAMINA = 10.0F;
    public static final int ATTACHED_KICK_START_TICK = 3;
    public static final int ATTACHED_KICK_INTERVAL_TICKS = 6;
    public static final int PIERCING_LIFETIME_TICKS = 80;
    public static final int PIERCING_MAX_BLOCKS = 4;
    public static final float PIERCING_DAMAGE = 8.0F;
    public static final float PIERCING_CRITICAL_MULTIPLIER = 1.45F;
    public static final float PIERCING_KNOCKBACK = 1.5F;
    public static final double PIERCING_TRACE_RADIUS = 0.65D;
    public static final float PIERCING_BLOCK_HARDNESS = 1.6F;
    public static final int PIERCING_COLLAPSE_TICKS = 60;
    public static final int SPLITTING_FRAGMENT_LIFETIME_TICKS = 45;
    public static final float SPLITTING_FRAGMENT_DAMAGE = 3.0F;
    public static final double SPLITTING_TARGET_SEARCH_RANGE = 30.0D;
    public static final double SPLITTING_TARGET_SPACING = 8.0D;
    public static final int ENCIRCLEMENT_DURATION_TICKS = 40;
    public static final int ENCIRCLEMENT_RESOLVE_BONUS_TICKS = 20;
    public static final double ENCIRCLEMENT_RADIUS = 3.2D;
    private static final ResourceKey<MobEffect>
            NEXT_ALBUM_COLLAPSE = ResourceKey.create(
                    Registries.MOB_EFFECT,
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_nextalbum", "collapse"));
    // 1.16 IndirectStandEntityDamageSource("revolver_bullet"); tags in data/.
    public static final ResourceKey<DamageType>
            STAND_REVOLVER_BULLET_DAMAGE = ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(
                            SexPistolsIds.MOD_ID,
                            SexPistolsIds.REVOLVER_BULLET_PATH));
    public static final float ENCIRCLEMENT_DAMAGE = 0.75F;
    public static final float ENCIRCLEMENT_FINISH_DAMAGE = 3.0F;
    public static final double ENCIRCLEMENT_KICK_SPEED = 4.2D;
    public static final float ENCIRCLEMENT_UPKEEP_PER_MEMBER = 1.35F;

    private static final EntityDataAccessor<Boolean> REDIRECTED =
            SynchedEntityData.defineId(
                    RevolverBulletEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> SPECIAL_SHOT_TYPE =
            SynchedEntityData.defineId(
                    RevolverBulletEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> SPECIAL_ACTIVE =
            SynchedEntityData.defineId(
                    RevolverBulletEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SPECIAL_CRITICAL =
            SynchedEntityData.defineId(
                    RevolverBulletEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TIME_STOP_ACTIVE =
            SynchedEntityData.defineId(
                    RevolverBulletEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte>
            TIME_STOP_SLOWDOWN_REMAINING =
            SynchedEntityData.defineId(
                    RevolverBulletEntity.class,
                    EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<
            java.util.Optional<ResourceLocation>> STAND_SKIN =
            SynchedEntityData.defineId(
                    RevolverBulletEntity.class,
                    ModEntityDataSerializers
                            .RESOURCE_LOCATION_OPTIONAL.get());
    private static final int MAX_TRACE_POINTS = 24;
    private static final int MAX_TRACKED_PIERCING_TARGETS = 64;
    private static final Map<BlockImpactKey, BlockDamageRecord>
            BLOCK_DAMAGE = new HashMap<>();

    public final List<Vec3> tracePos = new LinkedList<>();

    private double travelDistance;
    @Nullable
    private UUID sexPistolsOwnerUuid;
    private BigInteger ownerLifecycleEpoch = INVALID_OWNER_EPOCH;
    private boolean suppressOwnerLifecycleRelease;
    private int redirectedMemberMask;
    private int resolveRicochetCount;
    private int ricochetCount;
    private int attachedMemberMask;
    private int resolveAttachmentCount;
    private int autoRedirectCooldown;
    @Nullable
    private UUID lockedTargetUuid;
    @Nullable
    private UUID lastTransferMemberUuid;
    private boolean piercingInitialized;
    private int piercingTicks;
    private int piercingBlocksPierced;
    private final Set<UUID> piercingHitTargets = new HashSet<>();
    private boolean splittingFragment;
    @Nullable
    private UUID splittingTargetUuid;
    private int splittingFragmentTicks;
    private Vec3 splittingPathStart = Vec3.ZERO;
    private Vec3 splittingPathEnd = Vec3.ZERO;
    private Vec3 splittingCurve = Vec3.ZERO;
    private Vec3 splittingForward = Vec3.ZERO;
    private int splittingPathTicks;
    @Nullable
    private UUID splittingVolleyUuid;
    private boolean splittingTrackerReleased;
    private boolean encirclementActive;
    private int encirclementTicks;
    private int encirclementMemberMask;
    private Vec3 encirclementCenter = Vec3.ZERO;
    private int encirclementSourceSlot = -1;
    private int encirclementTargetSlot = -1;
    private int encirclementKickCooldown;
    private boolean encirclementMembersReturned;
    @Nullable
    private Vec3 timeStopStoredMotion;
    @Nullable
    private Vec3 timeStopHitMotion;

    public RevolverBulletEntity(
            EntityType<? extends RevolverBulletEntity> type,
            Level level) {
        super(type, level);
    }

    public RevolverBulletEntity(
            EntityType<? extends RevolverBulletEntity> type,
            LivingEntity shooter,
            Level level) {
        super(type, shooter, level);
        sexPistolsOwnerUuid = shooter.getUUID();
        if (level instanceof ServerLevel serverLevel) {
            ownerLifecycleEpoch = RevolverBulletOwnerEpochs
                    .get(serverLevel.getServer())
                    .current(sexPistolsOwnerUuid);
        }
        StandPower standPower = StandPower.get(shooter);
        if (standPower != null
                && standPower.getPowerType()
                        == SexPistolsStands.SEX_PISTOLS.get()) {
            setStandSkin(standPower.getSelectedSkin());
        }
    }

    public static int discardOwnedBy(
            ServerLevel level, UUID ownerUuid) {
        if (ownerUuid == null) {
            return 0;
        }
        RevolverBulletOwnerEpochs.get(level.getServer())
                .advance(ownerUuid);
        List<RevolverBulletEntity> owned = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RevolverBulletEntity bullet
                    && bullet.isOwnedBy(ownerUuid)) {
                owned.add(bullet);
            }
        }
        owned.forEach(Entity::discard);
        return owned.size();
    }

    private boolean isOwnedBy(UUID ownerUuid) {
        LivingEntity owner = getOwner();
        if (owner != null) {
            sexPistolsOwnerUuid = owner.getUUID();
        }
        return ownerUuid.equals(sexPistolsOwnerUuid);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(REDIRECTED, false);
        builder.define(SPECIAL_SHOT_TYPE, (byte) 0);
        builder.define(SPECIAL_ACTIVE, false);
        builder.define(SPECIAL_CRITICAL, false);
        builder.define(TIME_STOP_ACTIVE, false);
        builder.define(
                TIME_STOP_SLOWDOWN_REMAINING,
                (byte) 0);
        builder.define(STAND_SKIN, java.util.Optional.empty());
    }

    @Override
    public ResourceLocation getStandType() {
        return SexPistolsIds.SEX_PISTOLS;
    }

    @Override
    public java.util.Optional<ResourceLocation> getStandSkin() {
        return entityData.get(STAND_SKIN);
    }

    public void setStandSkin(
            java.util.Optional<ResourceLocation> standSkin) {
        entityData.set(
                STAND_SKIN,
                standSkin != null
                        ? standSkin
                        : java.util.Optional.empty());
    }

    @Override
    public void tick() {
        LivingEntity owner = getOwner();
        if (!level().isClientSide() && owner != null) {
            sexPistolsOwnerUuid = owner.getUUID();
        }
        if (!level().isClientSide()
                && level() instanceof ServerLevel serverLevel) {
            if (sexPistolsOwnerUuid == null
                    || !RevolverBulletOwnerEpochs
                            .get(serverLevel.getServer())
                            .isCurrent(
                                    sexPistolsOwnerUuid,
                                    ownerLifecycleEpoch)) {
                suppressOwnerLifecycleRelease = true;
                discard();
                return;
            }
            if (tickTimeStopState(serverLevel)) {
                return;
            }
            if (specialShotType() == ShotType.PIERCING
                    && !piercingInitialized) {
                startPiercingShot(serverLevel);
            }
            if (splittingFragment) {
                tickSplittingFragmentGuidance();
            }
            if (!isAlive()) {
                return;
            }
        }
        Vec3 before = position();
        super.tick();
        if (!isAlive()) {
            return;
        }
        travelDistance += before.distanceTo(position());
        if (level().isClientSide()) {
            appendTrace(position());
        }
        else if (level() instanceof ServerLevel serverLevel) {
            if (removeIfUnsafeSexPistolsDistance(serverLevel)) {
                return;
            }
            switch (specialShotType()) {
                case PIERCING ->
                        tickPiercingShot(serverLevel, before);
                case ENCIRCLEMENT -> {
                    if (encirclementActive) {
                        tickEncirclement(serverLevel, before);
                    }
                }
                case SPLITTING -> {
                    if (!splittingFragment) {
                        tickAttachedMemberGuidance(serverLevel);
                    }
                }
                case NONE ->
                        tickAttachedMemberGuidance(serverLevel);
            }
        }
    }

    private boolean removeIfUnsafeSexPistolsDistance(
            ServerLevel level) {
        LivingEntity owner = getOwner();
        if (owner == null) {
            return false;
        }
        double distanceSqr = distanceToSqr(owner);
        if (distanceSqr
                < RevolverBulletDistanceSafety
                        .CHUNK_LOAD_CHECK_DISTANCE_SQR) {
            return false;
        }
        Vec3 nextPosition = position().add(getDeltaMovement());
        if (RevolverBulletDistanceSafety.shouldDiscard(
                distanceSqr,
                level.hasChunkAt(blockPosition()),
                level.hasChunkAt(BlockPos.containing(nextPosition)))) {
            discard();
            return true;
        }
        return false;
    }

    private boolean tickTimeStopState(ServerLevel level) {
        boolean stopped = level
                .getData(ModDataAttachmentTypes.TIME_STOP.get())
                .isTimeStopped(this);
        if (!stopped) {
            if (entityData.get(TIME_STOP_ACTIVE)) {
                Vec3 resumedMotion = timeStopHitMotion != null
                        ? timeStopHitMotion
                        : timeStopStoredMotion;
                if (resumedMotion != null) {
                    setDeltaMovement(resumedMotion);
                    hasImpulse = true;
                }
                timeStopStoredMotion = null;
                timeStopHitMotion = null;
                entityData.set(TIME_STOP_ACTIVE, false);
                entityData.set(
                        TIME_STOP_SLOWDOWN_REMAINING,
                        (byte) 0);
            }
            return false;
        }

        if (!entityData.get(TIME_STOP_ACTIVE)) {
            timeStopStoredMotion = getDeltaMovement();
            timeStopHitMotion = null;
            entityData.set(TIME_STOP_ACTIVE, true);
            entityData.set(
                    TIME_STOP_SLOWDOWN_REMAINING,
                    (byte) TIME_STOP_SLOWDOWN_TICKS);
        }
        if (timeStopStoredMotion == null
                || timeStopStoredMotion.lengthSqr() < 1.0E-8D) {
            timeStopStoredMotion = getDeltaMovement();
        }

        int remaining = Byte.toUnsignedInt(entityData.get(
                TIME_STOP_SLOWDOWN_REMAINING));
        if (remaining <= 0) {
            setDeltaMovement(Vec3.ZERO);
            hasImpulse = true;
            return true;
        }
        double slowdown = (double) remaining
                / TIME_STOP_SLOWDOWN_TICKS;
        setDeltaMovement(timeStopStoredMotion.scale(
                slowdown * slowdown));
        entityData.set(
                TIME_STOP_SLOWDOWN_REMAINING,
                (byte) (remaining - 1));
        hasImpulse = true;
        return false;
    }

    public boolean canMoveInStoppedTime() {
        return !entityData.get(TIME_STOP_ACTIVE)
                || Byte.toUnsignedInt(entityData.get(
                        TIME_STOP_SLOWDOWN_REMAINING)) > 0;
    }

    public boolean isTimeStopDecelerating() {
        return entityData.get(TIME_STOP_ACTIVE);
    }

    public int timeStopSlowdownTicksRemaining() {
        return Byte.toUnsignedInt(entityData.get(
                TIME_STOP_SLOWDOWN_REMAINING));
    }

    public int ricochetCount() {
        return ricochetCount;
    }

    public int resolveRicochetCount() {
        return resolveRicochetCount;
    }

    public double ballisticGravity() {
        return getGravityAcceleration();
    }

    public double ballisticDrag() {
        return getInertia();
    }

    public TimeStopPhase timeStopPhase() {
        if (!entityData.get(TIME_STOP_ACTIVE)) {
            return TimeStopPhase.NORMAL;
        }
        return timeStopSlowdownTicksRemaining() > 0
                ? TimeStopPhase.DECELERATING
                : TimeStopPhase.FROZEN;
    }

    public static int trackedSoftBlockDamageCount() {
        return BLOCK_DAMAGE.size();
    }

    public static int trackedSoftBlockDamageCount(
            ResourceKey<Level> dimension) {
        if (dimension == null) {
            return 0;
        }
        return (int) BLOCK_DAMAGE.keySet().stream()
                .filter(key -> key.dimension.equals(dimension))
                .count();
    }

    public static int softBlockHitCount(
            ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0;
        }
        BlockDamageRecord record = BLOCK_DAMAGE.get(
                new BlockImpactKey(
                        level.dimension(), pos.asLong()));
        return record != null ? record.hits : 0;
    }

    public static int clearSoftBlockDamage(
            ServerLevel level) {
        if (level == null) {
            return 0;
        }
        int cleared = 0;
        Iterator<Map.Entry<BlockImpactKey, BlockDamageRecord>>
                iterator = BLOCK_DAMAGE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockImpactKey, BlockDamageRecord> entry =
                    iterator.next();
            if (!entry.getKey().dimension.equals(level.dimension())) {
                continue;
            }
            level.destroyBlockProgress(
                    entry.getValue().breakerId,
                    BlockPos.of(entry.getKey().blockPosLong),
                    -1);
            iterator.remove();
            cleared++;
        }
        return cleared;
    }

    public static int clearAllSoftBlockDamage() {
        int cleared = BLOCK_DAMAGE.size();
        BLOCK_DAMAGE.clear();
        return cleared;
    }

    public void setSpecialShotType(ShotType shotType) {
        ShotType normalized =
                shotType != null ? shotType : ShotType.NONE;
        entityData.set(
                SPECIAL_SHOT_TYPE,
                (byte) normalized.ordinal());
    }

    public ShotType specialShotType() {
        return ShotType.byId(
                entityData.get(SPECIAL_SHOT_TYPE));
    }

    public boolean isSpecialActive() {
        return entityData.get(SPECIAL_ACTIVE);
    }

    public boolean isSpecialCritical() {
        return entityData.get(SPECIAL_CRITICAL);
    }

    public void setAttachedMemberMask(int memberMask) {
        attachedMemberMask = memberMask & 0x3F;
        resolveAttachmentCount = Math.max(
                resolveAttachmentCount,
                Integer.bitCount(attachedMemberMask));
    }

    public int attachedMemberMask() {
        return attachedMemberMask;
    }

    @Override
    protected boolean constVelocity() {
        return false;
    }

    @Override
    protected double getGravityAcceleration() {
        return EFFECTIVE_GRAVITY;
    }

    @Override
    protected double getInertia() {
        return DRAG;
    }

    @Override
    protected HitResult[] rayTrace() {
        return specialShotType() == ShotType.PIERCING
                ? new HitResult[0]
                : super.rayTrace();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (specialShotType() == ShotType.ENCIRCLEMENT
                && encirclementActive) {
            LivingEntity owner = getOwner();
            if (owner == null) {
                return false;
            }
            if (entity instanceof SexPistolsEntity member) {
                int slot = SexPistolsEntities.slotForNumber(
                        member.getMemberNumber());
                return slot >= 0
                        && (encirclementMemberMask
                                & 1 << slot) != 0
                        && member.isOwnedBy(owner);
            }
            return entity instanceof LivingEntity living
                    && isValidEncirclementTarget(
                            owner, living);
        }
        if (entity instanceof SexPistolsEntity member) {
            LivingEntity owner = getOwner();
            int slot = SexPistolsEntities.slotForNumber(
                    member.getMemberNumber());
            return owner != null
                    && slot >= 0
                    && (redirectedMemberMask & 1 << slot) == 0
                    && member.isOwnedBy(owner)
                    && member.canRedirectBullet();
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (entityData.get(TIME_STOP_ACTIVE)) {
            if (!level().isClientSide()) {
                timeStopHitMotion = getDeltaMovement();
                setDeltaMovement(Vec3.ZERO);
                entityData.set(
                        TIME_STOP_SLOWDOWN_REMAINING,
                        (byte) 0);
                hasImpulse = true;
            }
            return;
        }
        if (specialShotType() == ShotType.ENCIRCLEMENT) {
            handleEncirclementEntityHit(hit);
            return;
        }
        if (hit.getEntity() instanceof SexPistolsEntity member) {
            if (level() instanceof ServerLevel serverLevel) {
                redirectFromMember(
                        serverLevel,
                        member,
                        hit.getLocation(),
                        true,
                        false);
            }
            return;
        }
        if (level().isClientSide() || !isAlive()) {
            return;
        }

        Entity target = hit.getEntity();
        LivingEntity owner = getOwner();
        float damage = getDamageAmount();
        float healthBefore = target instanceof LivingEntity living
                ? living.getHealth()
                : 0.0F;
        boolean entityHurt = DamageUtil.hurtThroughInvulTicks(
                target, getDamageSource(owner), damage);
        if (target instanceof LivingEntity living) {
            float actualDamage = Math.max(
                    0.0F, healthBefore - living.getHealth());
            if (!isEffectiveActualDamage(
                    living, actualDamage, damage)) {
                if (tryRicochetOffEntity(hit, living)) {
                    return;
                }
                discard();
                return;
            }
        }
        afterEntityHit(hit, entityHurt);
        playSound(
                SoundEvents.ARROW_HIT,
                0.5F,
                1.2F + random.nextFloat() * 0.2F);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (specialShotType() == ShotType.ENCIRCLEMENT) {
            if (level() instanceof ServerLevel serverLevel) {
                if (!encirclementActive) {
                    startEncirclement(
                            serverLevel, hit.getLocation());
                }
                else {
                    redirectEncirclementMotion();
                }
            }
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = serverLevel.getBlockState(pos);
        if (state.isAir()) {
            setPos(hit.getLocation());
            return;
        }

        float hardness = state.getDestroySpeed(serverLevel, pos);
        Vec3 motion = getDeltaMovement();
        cleanupStaleBlockDamage(serverLevel);
        serverLevel.levelEvent(2001, pos, Block.getId(state));
        ImpactTier tier = classifyImpact(
                serverLevel, pos, state, hardness);
        LivingEntity owner = getOwner();
        boolean blockDestructionEnabled =
                SexPistolsConfig.bulletDestroysBlocks();
        boolean canDestroy = blockDestructionEnabled
                && owner != null
                && hardness >= 0.0F
                && JojoModUtil.canEntityDestroy(
                        serverLevel, pos, state, owner);

        if (!blockDestructionEnabled
                && (tier == ImpactTier.FRAGILE_BREAK
                        || tier == ImpactTier.SOFT_BREAK)) {
            tier = ImpactTier.HARD_SURFACE;
        }

        if (tier == ImpactTier.FRAGILE_BREAK && canDestroy) {
            clearBlockDamage(serverLevel, pos);
            JojoModUtil.destroyBlock(
                    serverLevel,
                    pos,
                    JojoModUtil.dropBrokenBlock(owner),
                    owner);
            discard();
            return;
        }
        if (tier == ImpactTier.SOFT_BREAK && canDestroy) {
            int requiredHits = getRequiredHits(
                    serverLevel, pos, state, hardness);
            applySoftBlockDamage(
                    serverLevel,
                    pos,
                    state,
                    owner,
                    requiredHits);
            discard();
            return;
        }

        clearBlockDamage(serverLevel, pos);
        boolean ricochetAttempted = tier
                == ImpactTier.FORCED_RICOCHET;
        double ricochetSpeedScale = 0.88D;
        float ricochetPitch = 1.35F;
        if (!ricochetAttempted
                && tier == ImpactTier.HARD_SURFACE
                && shouldRicochetOnHardSurface(
                        serverLevel,
                        pos,
                        state,
                        motion,
                        hit.getDirection())) {
            ricochetAttempted = true;
            ricochetSpeedScale = 0.72D;
            ricochetPitch = 1.55F;
        }
        boolean ricocheted = ricochetAttempted
                && tryRicochet(
                        motion,
                        hit.getDirection(),
                        hit.getLocation(),
                        ricochetSpeedScale,
                        ricochetPitch);
        switch (RevolverBallistics.terminalImpact(
                ricochetAttempted, ricocheted)) {
            case RICOCHETED -> {
                return;
            }
            case DISCARD -> {
                discard();
                return;
            }
            case BULLET_HOLE -> {
            }
        }

        spawnBulletHole(serverLevel, hit);
        playSound(
                state.getSoundType(serverLevel, pos, this)
                        .getHitSound(),
                0.28F,
                1.0F + random.nextFloat() * 0.2F);
        discard();
    }

    private ImpactTier classifyImpact(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            float hardness) {
        if (hardness < 0.0F || isAlwaysRicochetBlock(state)) {
            return ImpactTier.FORCED_RICOCHET;
        }
        SoundType sound = state.getSoundType(level, pos, this);
        boolean hard = sound == SoundType.METAL
                || sound == SoundType.STONE;
        if (sound == SoundType.GLASS
                || state.is(BlockTags.LEAVES)
                || !hard && hardness <= 0.35F) {
            return ImpactTier.FRAGILE_BREAK;
        }
        if (hard) {
            return ImpactTier.HARD_SURFACE;
        }
        if (hardness <= 1.2F
                || hardness <= 2.4F
                        && state.getFluidState().isEmpty()) {
            return ImpactTier.SOFT_BREAK;
        }
        return ImpactTier.HARD_SURFACE;
    }

    private static boolean isAlwaysRicochetBlock(
            BlockState state) {
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL);
    }

    private int getRequiredHits(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            float hardness) {
        SoundType sound = state.getSoundType(level, pos, this);
        if (sound == SoundType.WOOD) {
            return hardness > 1.0F ? 3 : 2;
        }
        if (hardness <= 1.0F) {
            return 2;
        }
        if (hardness <= 2.2F) {
            return 3;
        }
        return 4;
    }

    private boolean applySoftBlockDamage(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            LivingEntity owner,
            int requiredHits) {
        BlockImpactKey key = new BlockImpactKey(
                level.dimension(), pos.asLong());
        int stateId = Block.getId(state);
        BlockDamageRecord record = BLOCK_DAMAGE.get(key);
        if (record == null || record.blockStateId != stateId) {
            reserveBlockDamageCapacity(level);
            record = new BlockDamageRecord(
                    computeBreakerId(level, pos), stateId);
            BLOCK_DAMAGE.put(key, record);
        }
        record.hits++;
        record.lastHitGameTime = level.getGameTime();
        if (record.hits >= requiredHits) {
            level.destroyBlockProgress(record.breakerId, pos, -1);
            BLOCK_DAMAGE.remove(key);
            return JojoModUtil.destroyBlock(
                    level,
                    pos,
                    JojoModUtil.dropBrokenBlock(owner),
                    owner);
        }
        int crackStage = Mth.clamp(
                (int) ((float) record.hits
                        * 10.0F / requiredHits) - 1,
                0,
                9);
        level.destroyBlockProgress(
                record.breakerId, pos, crackStage);
        return false;
    }

    private static void clearBlockDamage(
            ServerLevel level, BlockPos pos) {
        BlockImpactKey key = new BlockImpactKey(
                level.dimension(), pos.asLong());
        BlockDamageRecord record = BLOCK_DAMAGE.remove(key);
        if (record != null) {
            level.destroyBlockProgress(record.breakerId, pos, -1);
        }
    }

    private static void cleanupStaleBlockDamage(
            ServerLevel level) {
        long gameTime = level.getGameTime();
        Iterator<Map.Entry<BlockImpactKey, BlockDamageRecord>>
                iterator = BLOCK_DAMAGE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockImpactKey, BlockDamageRecord> entry =
                    iterator.next();
            BlockImpactKey key = entry.getKey();
            if (!key.dimension.equals(level.dimension())) {
                continue;
            }
            BlockPos pos = BlockPos.of(key.blockPosLong);
            BlockDamageRecord record = entry.getValue();
            BlockState current = level.getBlockState(pos);
            boolean expired = gameTime - record.lastHitGameTime
                    > SexPistolsConfig.blockDamageMemoryTicks();
            boolean changed = current.isAir()
                    || Block.getId(current) != record.blockStateId;
            if (expired || changed) {
                level.destroyBlockProgress(
                        record.breakerId, pos, -1);
                iterator.remove();
            }
        }
    }

    private static void reserveBlockDamageCapacity(
            ServerLevel level) {
        if (BLOCK_DAMAGE.size() < MAX_TRACKED_BLOCK_DAMAGE) {
            return;
        }
        Map.Entry<BlockImpactKey, BlockDamageRecord> oldest =
                BLOCK_DAMAGE.entrySet().stream()
                        .min(Comparator.comparingLong(
                                entry -> entry.getValue()
                                        .lastHitGameTime))
                        .orElse(null);
        if (oldest == null) {
            return;
        }
        BLOCK_DAMAGE.remove(oldest.getKey());
        if (oldest.getKey().dimension.equals(level.dimension())) {
            level.destroyBlockProgress(
                    oldest.getValue().breakerId,
                    BlockPos.of(oldest.getKey().blockPosLong),
                    -1);
        }
    }

    private static int computeBreakerId(
            ServerLevel level, BlockPos pos) {
        int hash = level.dimension().location().hashCode();
        return 31 * hash + Long.hashCode(pos.asLong());
    }

    private void spawnBulletHole(
            ServerLevel level, BlockHitResult hit) {
        RevolverVisualPayloads.sendBulletHole(
                this,
                hit.getLocation(),
                hit.getDirection(),
                hit.getBlockPos());
    }

    private boolean shouldRicochetOnHardSurface(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Vec3 motion,
            Direction face) {
        if (motion.lengthSqr() < 1.0E-6D) {
            return false;
        }
        Vec3 normal = directionVector(face);
        double incidence = Math.abs(
                motion.normalize().dot(normal));
        SoundType sound = state.getSoundType(level, pos, this);
        double threshold = sound == SoundType.METAL
                ? 0.42D
                : 0.36D;
        return incidence < threshold;
    }

    private boolean tryRicochet(
            Vec3 motion,
            Direction face,
            Vec3 hitPosition,
            double speedScale,
            float pitchBase) {
        if (ricochetCount >= SexPistolsConfig.maxRicochetCount()
                || motion.lengthSqr() < RICOCHET_MIN_SPEED_SQR) {
            return false;
        }
        Vec3 normal = directionVector(face);
        Vec3 reflected = reflect(motion, normal);
        if (reflected.lengthSqr() < 1.0E-6D) {
            return false;
        }
        double speed = Math.max(
                Math.sqrt(RICOCHET_MIN_SPEED_SQR),
                motion.length() * speedScale);
        Vec3 bounced = reflected.normalize().scale(speed);
        return applyRicochet(
                bounced,
                normal,
                hitPosition,
                0.3F,
                pitchBase);
    }

    private boolean tryRicochetOffEntity(
            EntityHitResult hit,
            LivingEntity target) {
        Vec3 motion = getDeltaMovement();
        if (ricochetCount >= SexPistolsConfig.maxRicochetCount()
                || motion.lengthSqr() < RICOCHET_MIN_SPEED_SQR) {
            return false;
        }
        Vec3 normal = getDeflectingEntityWallNormal(
                target, motion, hit.getLocation());
        Vec3 bounced = getGuardDeflectedMotion(
                target, motion, normal, hit.getLocation());
        if (bounced.lengthSqr() < RICOCHET_MIN_SPEED_SQR) {
            return false;
        }
        return applyRicochet(
                bounced,
                normal,
                hit.getLocation(),
                0.38F,
                1.45F);
    }

    private boolean applyRicochet(
            Vec3 bounced,
            Vec3 normal,
            Vec3 hitPosition,
            float volume,
            float pitchBase) {
        if (bounced.lengthSqr() < RICOCHET_MIN_SPEED_SQR) {
            return false;
        }
        Vec3 ricochetPosition = hitPosition
                .add(normal.scale(RICOCHET_SURFACE_OFFSET))
                .add(bounced.normalize().scale(
                        RICOCHET_TRAVEL_BIAS));
        noPhysics = false;
        setNoGravity(false);
        setPos(ricochetPosition);
        setDeltaMovement(bounced);
        hasImpulse = true;
        tracePos.clear();
        ricochetCount++;
        resolveRicochetCount++;
        entityData.set(REDIRECTED, true);
        setIsDeflected(bounced, hitPosition);
        playSound(
                SoundEvents.ARROW_HIT,
                volume,
                pitchBase + random.nextFloat() * 0.2F);
        return true;
    }

    private Vec3 getDeflectingEntityWallNormal(
            LivingEntity target,
            Vec3 motion,
            Vec3 hitPosition) {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 incoming = motion.lengthSqr() > 1.0E-6D
                ? motion.normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 normal = position().subtract(targetCenter);
        LivingEntity owner = getOwner();
        if (normal.lengthSqr() < 1.0E-6D && owner != null) {
            normal = owner.getEyePosition().subtract(targetCenter);
        }
        if (normal.lengthSqr() < 1.0E-6D) {
            normal = incoming.reverse();
        }
        normal = normal.normalize();
        if (incoming.dot(normal) > 0.0D) {
            normal = normal.reverse();
        }
        Vec3 hitOffset = hitPosition.subtract(targetCenter);
        Vec3 horizontalOffset = new Vec3(
                hitOffset.x, 0.0D, hitOffset.z);
        if (horizontalOffset.lengthSqr() > 1.0E-6D) {
            normal = normal.scale(0.82D)
                    .add(horizontalOffset.normalize().scale(0.18D))
                    .normalize();
        }
        return normal;
    }

    private Vec3 getGuardDeflectedMotion(
            LivingEntity target,
            Vec3 motion,
            Vec3 wallNormal,
            Vec3 hitPosition) {
        Vec3 reflected = reflect(motion, wallNormal);
        double speed = Math.max(
                Math.sqrt(RICOCHET_MIN_SPEED_SQR),
                motion.length());
        Vec3 incoming = motion.lengthSqr() > 1.0E-6D
                ? motion.normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 hitOffset = hitPosition.subtract(
                target.getBoundingBox().getCenter());
        Vec3 side = wallNormal.cross(
                new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = incoming.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        double sideSign = hitOffset.dot(side) >= 0.0D
                ? 1.0D
                : -1.0D;
        Vec3 guardSlide = side.scale(sideSign * 0.34D)
                .add(0.0D, 0.18D, 0.0D);
        Vec3 defendedDirection = reflected.normalize().scale(0.72D)
                .add(wallNormal.scale(0.22D))
                .add(guardSlide)
                .normalize();
        double forward = defendedDirection.dot(incoming);
        if (forward > -0.08D) {
            defendedDirection = defendedDirection
                    .subtract(incoming.scale(forward + 0.18D))
                    .normalize();
        }
        return defendedDirection.scale(speed);
    }

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ());
    }

    private static Vec3 reflect(Vec3 motion, Vec3 normal) {
        return motion.subtract(normal.scale(
                2.0D * motion.dot(normal)));
    }

    private static boolean isEffectiveActualDamage(
            LivingEntity target,
            float actualDamage,
            float baseDamage) {
        return baseDamage <= 0.0F
                || actualDamage >= baseDamage * 0.2F
                || target.getHealth() <= 0.0F
                || !target.isAlive();
    }

    private boolean redirectFromMember(
            ServerLevel level,
            SexPistolsEntity member,
            Vec3 hitPosition,
            boolean consumeKickStamina,
            boolean returnAfterKick) {
        LivingEntity owner = getOwner();
        if (owner == null
                || !member.isOwnedBy(owner)
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            discard();
            return false;
        }

        StandPower standPower = StandPower.get(owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        if (standPower == null
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !standPower.isSummoned()
                || !coordinator.isSummoned()
                || consumeKickStamina
                        && !SexPistolsStamina.tryConsume(
                                standPower,
                                owner,
                                MEMBER_KICK_STAMINA)) {
            discard();
            return false;
        }

        int slot = SexPistolsEntities.slotForNumber(
                member.getMemberNumber());
        redirectedMemberMask |= 1 << slot;
        resolveRicochetCount++;

        LivingEntity target = findTarget(
                level,
                owner,
                hitPosition,
                coordinator.targetMode(),
                specialShotType() == ShotType.SPLITTING);
        if (specialShotType() == ShotType.SPLITTING
                && !splittingFragment
                && target != null
                && splitFromMember(
                        level,
                        owner,
                        standPower,
                        coordinator,
                        member,
                        target,
                        hitPosition,
                        returnAfterKick)) {
            return true;
        }
        SexPistolsEntity relay = findRelay(
                level,
                owner,
                member,
                target,
                coordinator,
                hitPosition);

        Vec3 aimPoint;
        if (relay != null) {
            aimPoint = relay.getBoundingBox().getCenter();
            lockedTargetUuid =
                    target != null ? target.getUUID() : null;
            lastTransferMemberUuid = member.getUUID();
        }
        else if (target != null) {
            aimPoint = target.getBoundingBox().getCenter();
            lockedTargetUuid = target.getUUID();
            lastTransferMemberUuid = member.getUUID();
        }
        else {
            Vec3 fallback = getDeltaMovement();
            if (fallback.lengthSqr() <= 1.0E-7D) {
                fallback = owner.getLookAngle();
            }
            aimPoint = hitPosition.add(fallback.normalize());
            lockedTargetUuid = null;
            lastTransferMemberUuid = member.getUUID();
        }

        Vec3 direction = aimPoint.subtract(hitPosition);
        if (direction.lengthSqr() <= 1.0E-7D) {
            direction = owner.getLookAngle();
        }
        direction = direction.normalize();
        double speed = Math.max(
                0.25D, getDeltaMovement().length());
        Vec3 movement = direction.scale(speed);
        boolean critical = rollSpecialCritical(owner);
        if (critical) {
            movement = movement.scale(1.15D);
            playSound(
                    SoundEvents.ARROW_HIT_PLAYER,
                    0.7F,
                    1.65F + random.nextFloat() * 0.2F);
        }
        Vec3 exitPosition = hitPosition.add(
                direction.scale(0.35D));

        setPos(exitPosition);
        setDeltaMovement(movement);
        entityData.set(REDIRECTED, true);
        setIsDeflected(movement, hitPosition);
        member.performKick(
                direction, returnAfterKick, critical);
        return true;
    }

    private void tickAttachedMemberGuidance(
            ServerLevel level) {
        if (attachedMemberMask == 0
                || tickCount < ATTACHED_KICK_START_TICK
                || getDeltaMovement().lengthSqr()
                        <= 1.0E-7D) {
            return;
        }
        if (autoRedirectCooldown > 0) {
            autoRedirectCooldown--;
            if (autoRedirectCooldown > 0) {
                return;
            }
        }

        LivingEntity owner = getOwner();
        if (owner == null
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            releaseRemainingAttachedMembers();
            return;
        }
        StandPower standPower = StandPower.get(owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        // 1.16 getLoadedKickStand asked the swarm effect for the member with
        // the Stand out or put away; releasing it brings the swarm out.
        if (standPower == null
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !coordinator.isSwarmEstablished()) {
            releaseRemainingAttachedMembers();
            return;
        }

        int slot = Integer.numberOfTrailingZeros(
                attachedMemberMask);
        int bit = 1 << slot;
        if ((coordinator.inFlightMemberMask() & bit) == 0) {
            attachedMemberMask &= ~bit;
            autoRedirectCooldown = 1;
            return;
        }
        if (!SexPistolsStamina.tryConsume(
                standPower, owner, MEMBER_KICK_STAMINA)) {
            autoRedirectCooldown =
                    ATTACHED_KICK_INTERVAL_TICKS;
            return;
        }

        Vec3 kickPosition = position();
        SexPistolsEntity member =
                coordinator.releaseInFlightMember(
                        level,
                        owner,
                        slot,
                        kickPosition);
        attachedMemberMask &= ~bit;
        if (member == null) {
            autoRedirectCooldown = 1;
            return;
        }
        redirectFromMember(
                level,
                member,
                kickPosition,
                false,
                true);
        autoRedirectCooldown =
                ATTACHED_KICK_INTERVAL_TICKS;
    }

    private void releaseRemainingAttachedMembers() {
        int remaining = attachedMemberMask;
        attachedMemberMask = 0;
        if (remaining == 0
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity owner = getOwner();
        if (owner == null
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return;
        }
        SexPistolsAttachments.coordinator(owner)
                .releaseInFlightMembers(
                        serverLevel,
                        owner,
                        remaining,
                        owner.position().add(
                                0.0D,
                        owner.getBbHeight() * 0.65D,
                                0.0D));
    }

    private void startPiercingShot(ServerLevel level) {
        piercingInitialized = true;
        piercingTicks = 0;
        piercingBlocksPierced = 0;
        piercingHitTargets.clear();
        entityData.set(SPECIAL_ACTIVE, true);
        noPhysics = true;
        setNoGravity(true);

        LivingEntity owner = getOwner();
        Vec3 motion = getDeltaMovement();
        if (owner == null
                || motion.lengthSqr() <= 1.0E-7D
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            discard();
            return;
        }

        boolean critical = rollSpecialCritical(owner);
        entityData.set(SPECIAL_CRITICAL, critical);
        if (critical) {
            motion = motion.scale(1.15D);
            setDeltaMovement(motion);
        }
        releasePiercingMembers(level, owner, motion);
    }

    private void releasePiercingMembers(
            ServerLevel level,
            LivingEntity owner,
            Vec3 motion) {
        int memberMask = attachedMemberMask;
        attachedMemberMask = 0;
        if (memberMask == 0
                || motion.lengthSqr() <= 1.0E-7D) {
            return;
        }

        StandPower standPower = StandPower.get(owner);
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        Vec3 direction = motion.normalize();
        Vec3 side = direction.cross(
                new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() <= 1.0E-7D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 lift = side.cross(direction).normalize();
        int count = Math.max(
                1, Integer.bitCount(memberMask));
        int released = 0;
        for (int slot = 0;
                slot < SexPistolsCoordinator.MEMBER_COUNT;
                slot++) {
            if ((memberMask & 1 << slot) == 0) {
                continue;
            }
            double spread =
                    released - (count - 1) * 0.5D;
            Vec3 kickPoint = position()
                    .subtract(direction.scale(0.35D))
                    .add(side.scale(spread * 0.18D))
                    .add(lift.scale(
                            (released & 1) == 0
                                    ? 0.12D
                                    : -0.08D));
            boolean kick = SexPistolsStamina.tryConsume(
                    standPower, owner, MEMBER_KICK_STAMINA);
            SexPistolsEntity member =
                    coordinator.releaseInFlightMember(
                            level, owner, slot, kickPoint);
            if (member != null) {
                if (kick) {
                    member.performKick(
                            direction,
                            true,
                            entityData.get(SPECIAL_CRITICAL));
                }
                else {
                    member.setMemberState(
                            SexPistolsMemberState.FORMATION);
                }
            }
            released++;
        }
        playSound(
                SexPistolsSounds.randomRicochet(random),
                0.8F,
                1.05F + random.nextFloat() * 0.1F);
    }

    private void tickPiercingShot(
            ServerLevel level, Vec3 start) {
        piercingTicks++;
        Vec3 motion = getDeltaMovement();
        if (piercingTicks > PIERCING_LIFETIME_TICKS
                || !isTimeStopDecelerating()
                        && motion.lengthSqr() < 0.05D) {
            discard();
            return;
        }

        Vec3 end = position();
        BlockHitResult blockHit = level.clip(
                new ClipContext(
                        start,
                        end,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        this));
        Vec3 targetEnd =
                blockHit.getType() == HitResult.Type.BLOCK
                        ? blockHit.getLocation()
                        : end;
        hitPiercingTargets(
                level, start, targetEnd, motion);
        if (!isAlive()) {
            return;
        }
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            handlePiercingBlockHit(
                    level, blockHit, motion);
        }
    }

    private void hitPiercingTargets(
            ServerLevel level,
            Vec3 start,
            Vec3 end,
            Vec3 motion) {
        LivingEntity owner = getOwner();
        if (owner == null
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return;
        }
        SexPistolsTargetMode mode =
                SexPistolsAttachments.coordinator(owner)
                        .targetMode();
        AABB area = new AABB(start, end)
                .inflate(PIERCING_TRACE_RADIUS);
        List<LivingEntity> targets =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        target -> isValidTarget(
                                    owner,
                                    target,
                                    mode,
                                    true)
                                && target.isPickable()
                                && !piercingHitTargets
                                        .contains(
                                                target.getUUID())
                                && segmentIntersectsInflatedBox(
                                        start,
                                        end,
                                        target.getBoundingBox(),
                                        PIERCING_TRACE_RADIUS));
        for (LivingEntity target : targets) {
            if (piercingHitTargets.size()
                    >= MAX_TRACKED_PIERCING_TARGETS) {
                break;
            }
            piercingHitTargets.add(target.getUUID());
            damagePiercingTarget(
                    level, owner, target, motion);
        }
    }

    private void damagePiercingTarget(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target,
            Vec3 motion) {
        if (target instanceof Player player
                && player.isBlocking()
                && !player.getUseItem().isEmpty()) {
            player.getCooldowns().addCooldown(
                    player.getUseItem().getItem(), 100);
            player.stopUsingItem();
            level.broadcastEntityEvent(player, (byte) 30);
        }
        if (target instanceof StandEntity standTarget) {
            standTarget.breakStandBlocking(
                    StandStatFormulas.getGuardBreakTicks(
                            standTarget.getDurability()));
        }

        float configuredPiercingDamage = configuredDamage(
                PIERCING_DAMAGE);
        float damage = entityData.get(SPECIAL_CRITICAL)
                ? configuredPiercingDamage
                        * PIERCING_CRITICAL_MULTIPLIER
                : configuredPiercingDamage;
        DamageUtil.hurtThroughInvulTicks(
                target, getDamageSource(owner), damage);
        if (motion.lengthSqr() > 1.0E-7D) {
            Vec3 direction = motion.normalize();
            target.knockback(
                    PIERCING_KNOCKBACK,
                    -direction.x,
                    -direction.z);
            KnockbackCollisionImpact.getHandler(target)
                    .onPunchSetKnockbackImpact(
                            direction.scale(
                                    PIERCING_KNOCKBACK),
                            owner)
                    .withImpactExplosion(
                            0.5F,
                            null,
                            configuredPiercingDamage * 0.3F);
        }
        if (entityData.get(SPECIAL_CRITICAL)
                && owner.hasEffect(ModStatusEffects.RESOLVE)) {
            level.registryAccess()
                    .lookupOrThrow(Registries.MOB_EFFECT)
                    .get(NEXT_ALBUM_COLLAPSE)
                    .ifPresent(effect -> target.addEffect(
                            new MobEffectInstance(
                                    effect,
                                    PIERCING_COLLAPSE_TICKS,
                                    0,
                                    false,
                                    true,
                                    true)));
        }
        level.sendParticles(
                ParticleTypes.CRIT,
                target.getX(),
                target.getY()
                        + target.getBbHeight() * 0.5D,
                target.getZ(),
                4,
                0.12D,
                0.12D,
                0.12D,
                0.0D);
    }

    private void handlePiercingBlockHit(
            ServerLevel level,
            BlockHitResult hit,
            Vec3 motion) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            setPos(hit.getLocation());
            return;
        }
        LivingEntity owner = getOwner();
        float hardness = state.getDestroySpeed(level, pos);
        boolean canDestroy = SexPistolsConfig.bulletDestroysBlocks()
                && owner != null
                && piercingBlocksPierced
                        < PIERCING_MAX_BLOCKS
                && hardness >= 0.0F
                && hardness <= PIERCING_BLOCK_HARDNESS
                && JojoModUtil.canEntityDestroy(
                        level, pos, state, owner);
        if (canDestroy
                && JojoModUtil.destroyBlock(
                        level,
                        pos,
                        JojoModUtil.dropBrokenBlock(owner),
                        owner)) {
            piercingBlocksPierced++;
            Vec3 direction = motion.lengthSqr() > 1.0E-7D
                    ? motion.normalize()
                    : hit.getLocation()
                            .subtract(position())
                            .normalize();
            if (direction.lengthSqr() <= 1.0E-7D) {
                direction = new Vec3(
                        hit.getDirection().getStepX(),
                        hit.getDirection().getStepY(),
                        hit.getDirection().getStepZ())
                                .reverse();
            }
            double newSpeed = Math.max(
                    0.25D,
                    motion.length()
                            - (0.22D
                                    + Math.max(
                                            0.0F,
                                            hardness)
                                            * 0.22D));
            setDeltaMovement(
                    direction.scale(newSpeed));
            setPos(hit.getLocation()
                    .add(direction.scale(0.22D)));
            playSound(
                    SoundEvents.STONE_BREAK,
                    0.35F,
                    1.2F + random.nextFloat() * 0.15F);
            return;
        }

        setPos(hit.getLocation());
        playSound(
                SoundEvents.STONE_HIT,
                0.35F,
                0.8F + random.nextFloat() * 0.2F);
        discard();
    }

    private void handleEncirclementEntityHit(
            EntityHitResult hit) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!encirclementActive) {
            startEncirclement(
                    serverLevel, hit.getLocation());
            return;
        }
        Entity hitEntity = hit.getEntity();
        LivingEntity owner = getOwner();
        if (hitEntity instanceof SexPistolsEntity member
                && owner != null
                && member.isOwnedBy(owner)) {
            kickEncirclementFromMember(
                    serverLevel, member);
            return;
        }
        if (owner != null
                && hitEntity instanceof LivingEntity target
                && isValidEncirclementTarget(
                        owner, target)) {
            damageEncirclementTarget(
                    serverLevel,
                    owner,
                    target,
                    configuredDamage(ENCIRCLEMENT_DAMAGE));
        }
    }

    private void startEncirclement(
            ServerLevel level, Vec3 center) {
        LivingEntity owner = getOwner();
        if (owner == null
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            discard();
            return;
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        int requestedMask = attachedMemberMask
                & coordinator.inFlightMemberMask()
                & 0x3F;
        attachedMemberMask = 0;
        encirclementCenter = center;
        encirclementTicks = 0;
        encirclementSourceSlot = -1;
        encirclementTargetSlot = -1;
        encirclementKickCooldown = 0;
        encirclementMembersReturned = false;
        encirclementMemberMask = 0;
        noPhysics = true;
        setNoGravity(true);
        setPos(center);

        for (int slot = 0;
                slot < SexPistolsCoordinator.MEMBER_COUNT;
                slot++) {
            if ((requestedMask & 1 << slot) == 0) {
                continue;
            }
            Vec3 position = findSafeEncirclementPosition(
                    level, center,
                    randomEncirclementSpherePoint(center));
            SexPistolsEntity member =
                    coordinator.releaseInFlightMember(
                            level,
                            owner,
                            slot,
                            position);
            if (member == null) {
                continue;
            }
            member.setPos(position);
            member.setCommandTarget(position);
            member.setMemberState(
                    SexPistolsMemberState.ENCIRCLEMENT);
            encirclementMemberMask |= 1 << slot;
        }
        if (encirclementMemberMask == 0) {
            discard();
            return;
        }

        encirclementActive = true;
        entityData.set(SPECIAL_ACTIVE, true);
        SexPistolsEntity first =
                randomEncirclementMember(
                        level, owner, null);
        if (first != null) {
            kickEncirclementFromMember(level, first);
        }
        else {
            redirectEncirclementMotion();
        }
        playSound(
                SoundEvents.FIREWORK_ROCKET_BLAST,
                0.6F,
                1.45F + random.nextFloat() * 0.2F);
    }

    private void tickEncirclement(
            ServerLevel level, Vec3 travelStart) {
        LivingEntity owner = getOwner();
        if (owner == null
                || !owner.isAlive()
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            returnEncirclementMembers(level, owner);
            encirclementActive = false;
            entityData.set(SPECIAL_ACTIVE, false);
            discard();
            return;
        }
        StandPower standPower = StandPower.get(owner);
        float upkeep = ENCIRCLEMENT_UPKEEP_PER_MEMBER
                * Math.max(
                        1,
                        Integer.bitCount(
                                encirclementMemberMask));
        if (owner.hasEffect(ModStatusEffects.RESOLVE)) {
            upkeep *= 0.5F;
        }
        if (!SexPistolsStamina.tryConsume(
                standPower, owner, upkeep)) {
            cancelEncirclement(level, owner);
            return;
        }

        encirclementTicks++;
        Vec3 current = position();
        if (encirclementTicks % 8 == 0) {
            traceEncirclementDamage(
                    level,
                    owner,
                    travelStart,
                    current,
                    configuredDamage(ENCIRCLEMENT_DAMAGE),
                    true);
        }
        keepEncirclementMembersFixed(level, owner);
        suspendEncirclementTargets(level, owner);
        level.sendParticles(
                ParticleTypes.CRIT,
                getX(),
                getY(),
                getZ(),
                2,
                0.06D,
                0.06D,
                0.06D,
                0.0D);

        SexPistolsEntity targetMember =
                encirclementMember(
                        level,
                        owner,
                        encirclementTargetSlot);
        if (targetMember != null
                && (encirclementKickCooldown <= 0
                        || current.distanceToSqr(
                                targetMember
                                        .getBoundingBox()
                                        .getCenter())
                                <= 0.7225D)) {
            kickEncirclementFromMember(
                    level, targetMember);
        }
        else if (encirclementKickCooldown > 0) {
            encirclementKickCooldown--;
        }
        if (targetMember == null
                && encirclementTicks % 8 == 0) {
            SexPistolsEntity fallback =
                    randomEncirclementMember(
                            level, owner, null);
            if (fallback != null) {
                kickEncirclementFromMember(
                        level, fallback);
            }
            else {
                redirectEncirclementMotion();
            }
        }

        if (position().distanceToSqr(encirclementCenter)
                > 22.528D) {
            setPos(encirclementCenter);
            SexPistolsEntity fallback =
                    randomEncirclementMember(
                            level, owner, null);
            if (fallback != null) {
                kickEncirclementFromMember(
                        level, fallback);
            }
            else {
                redirectEncirclementMotion();
            }
        }

        int duration = ENCIRCLEMENT_DURATION_TICKS
                + (owner.hasEffect(ModStatusEffects.RESOLVE)
                        ? ENCIRCLEMENT_RESOLVE_BONUS_TICKS
                        : 0);
        if (encirclementTicks >= duration) {
            finishEncirclement(level, owner);
        }
    }

    private void kickEncirclementFromMember(
            ServerLevel level,
            SexPistolsEntity member) {
        LivingEntity owner = getOwner();
        if (owner == null
                || !member.isOwnedBy(owner)) {
            return;
        }
        StandPower standPower = StandPower.get(owner);
        if (!SexPistolsStamina.tryConsume(
                standPower, owner, MEMBER_KICK_STAMINA)) {
            encirclementKickCooldown = 8;
            return;
        }

        SexPistolsEntity target =
                randomEncirclementMember(
                        level, owner, member);
        Vec3 from = member.getBoundingBox().getCenter();
        Vec3 to = target != null
                ? target.getBoundingBox().getCenter()
                : randomEncirclementPoint();
        Vec3 motion = to.subtract(from);
        if (motion.lengthSqr() <= 1.0E-7D) {
            to = encirclementCenter.add(
                    random.nextDouble() - 0.5D,
                    0.45D + random.nextDouble(),
                    random.nextDouble() - 0.5D);
            motion = to.subtract(from);
        }
        motion = motion.normalize()
                .scale(ENCIRCLEMENT_KICK_SPEED);
        setPos(from);
        setDeltaMovement(motion);
        entityData.set(REDIRECTED, true);
        setIsDeflected(motion, from);
        encirclementSourceSlot =
                SexPistolsEntities.slotForNumber(
                        member.getMemberNumber());
        encirclementTargetSlot = target != null
                ? SexPistolsEntities.slotForNumber(
                        target.getMemberNumber())
                : -1;
        encirclementKickCooldown =
                target != null ? 2 : 8;
        member.performKick(motion, false);
        member.setMemberState(
                SexPistolsMemberState.ENCIRCLEMENT);
        if (encirclementTicks % 8 == 0) {
            traceEncirclementDamage(
                    level,
                    owner,
                    from,
                    to,
                    configuredDamage(ENCIRCLEMENT_DAMAGE),
                    true);
        }
    }

    private void redirectEncirclementMotion() {
        Vec3 target = randomEncirclementPoint();
        Vec3 motion = target.subtract(position());
        if (motion.lengthSqr() <= 1.0E-7D) {
            motion = new Vec3(
                    random.nextDouble() - 0.5D,
                    random.nextDouble() - 0.25D,
                    random.nextDouble() - 0.5D);
        }
        setDeltaMovement(
                motion.normalize().scale(
                        1.35D
                                + random.nextDouble()
                                        * 0.85D));
        entityData.set(REDIRECTED, true);
        setIsDeflected(getDeltaMovement(), position());
    }

    private void traceEncirclementDamage(
            ServerLevel level,
            LivingEntity owner,
            Vec3 from,
            Vec3 to,
            float damage,
            boolean burst) {
        AABB area = new AABB(from, to).inflate(0.85D);
        List<LivingEntity> targets =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        target -> isValidEncirclementTarget(
                                    owner, target)
                                && segmentIntersectsInflatedBox(
                                        from,
                                        to,
                                        target.getBoundingBox(),
                                        0.85D));
        for (LivingEntity target : targets) {
            damageEncirclementTarget(
                    level, owner, target, damage);
        }
        if (!burst) {
            return;
        }
        AABB burstArea = new AABB(
                encirclementCenter.x
                        - ENCIRCLEMENT_RADIUS,
                encirclementCenter.y - 1.75D,
                encirclementCenter.z
                        - ENCIRCLEMENT_RADIUS,
                encirclementCenter.x
                        + ENCIRCLEMENT_RADIUS,
                encirclementCenter.y + 1.75D,
                encirclementCenter.z
                        + ENCIRCLEMENT_RADIUS);
        for (LivingEntity target :
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        burstArea,
                        candidate ->
                                isValidEncirclementTarget(
                                        owner, candidate))) {
            damageEncirclementTarget(
                    level, owner, target, damage);
        }
    }

    private void damageEncirclementTarget(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target,
            float damage) {
        if (!target.isAlive()) {
            return;
        }
        target.hurt(getDamageSource(owner), damage);
        suspendEncirclementTarget(target);
        level.sendParticles(
                ParticleTypes.ENCHANTED_HIT,
                target.getX(),
                target.getY()
                        + target.getBbHeight() * 0.5D,
                target.getZ(),
                2,
                0.12D,
                0.12D,
                0.12D,
                0.0D);
    }

    private void suspendEncirclementTargets(
            ServerLevel level, LivingEntity owner) {
        AABB area = AABB.ofSize(
                encirclementCenter,
                ENCIRCLEMENT_RADIUS * 2.0D,
                ENCIRCLEMENT_RADIUS * 2.0D,
                ENCIRCLEMENT_RADIUS * 2.0D);
        for (LivingEntity target :
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        candidate ->
                                isValidEncirclementTarget(
                                        owner, candidate))) {
            suspendEncirclementTarget(target);
        }
    }

    private void suspendEncirclementTarget(
            LivingEntity target) {
        double hoverY = encirclementCenter.y + 1.05D;
        double yMotion = target.getY() < hoverY
                ? 0.08D
                : Mth.clamp(
                        target.getDeltaMovement().y,
                        -0.02D,
                        0.02D);
        target.setDeltaMovement(0.0D, yMotion, 0.0D);
        target.fallDistance = 0.0F;
        target.hasImpulse = true;
    }

    private boolean isValidEncirclementTarget(
            LivingEntity owner, LivingEntity target) {
        if (target instanceof StandEntity
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return false;
        }
        return isValidTarget(
                owner,
                target,
                SexPistolsAttachments.coordinator(owner)
                        .targetMode(),
                false);
    }

    private void keepEncirclementMembersFixed(
            ServerLevel level, LivingEntity owner) {
        for (SexPistolsEntity member :
                encirclementMembers(level, owner)) {
            member.setCommandTarget(member.position());
            member.setMemberState(
                    SexPistolsMemberState.ENCIRCLEMENT);
        }
    }

    private List<SexPistolsEntity> encirclementMembers(
            ServerLevel level, LivingEntity owner) {
        if (!owner.hasData(
                SexPistolsAttachments
                        .SEX_PISTOLS_COORDINATOR)) {
            return Collections.emptyList();
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        List<SexPistolsEntity> members =
                new ArrayList<>(
                        Integer.bitCount(
                                encirclementMemberMask));
        for (int slot = 0;
                slot < SexPistolsCoordinator.MEMBER_COUNT;
                slot++) {
            if ((encirclementMemberMask & 1 << slot) == 0) {
                continue;
            }
            SexPistolsEntity member = coordinator.member(
                    level,
                    SexPistolsIds.SEX_PISTOLS_NUMBERS[slot]);
            if (member != null
                    && member.isOwnedBy(owner)) {
                members.add(member);
            }
        }
        return members;
    }

    @Nullable
    private SexPistolsEntity encirclementMember(
            ServerLevel level,
            LivingEntity owner,
            int slot) {
        if (slot < 0
                || slot
                        >= SexPistolsCoordinator.MEMBER_COUNT
                || (encirclementMemberMask & 1 << slot) == 0
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return null;
        }
        SexPistolsEntity member =
                SexPistolsAttachments.coordinator(owner)
                        .member(
                                level,
                                SexPistolsIds
                                        .SEX_PISTOLS_NUMBERS[
                                                slot]);
        return member != null
                        && member.isOwnedBy(owner)
                ? member
                : null;
    }

    @Nullable
    private SexPistolsEntity randomEncirclementMember(
            ServerLevel level,
            LivingEntity owner,
            @Nullable SexPistolsEntity excluded) {
        List<SexPistolsEntity> members =
                new ArrayList<>(
                        encirclementMembers(level, owner));
        members.remove(excluded);
        return members.isEmpty()
                ? null
                : members.get(
                        random.nextInt(members.size()));
    }

    private Vec3 randomEncirclementPoint() {
        double angle = random.nextDouble()
                * Math.PI * 2.0D;
        double radius = ENCIRCLEMENT_RADIUS
                * (0.35D + random.nextDouble() * 0.65D);
        return encirclementCenter.add(
                Math.cos(angle) * radius,
                0.2D + random.nextDouble() * 1.25D,
                Math.sin(angle) * radius);
    }

    private Vec3 randomEncirclementSpherePoint(
            Vec3 center) {
        double theta = random.nextDouble()
                * Math.PI * 2.0D;
        double phi = Math.acos(
                2.0D * random.nextDouble() - 1.0D);
        double radius = ENCIRCLEMENT_RADIUS
                * Math.cbrt(random.nextDouble());
        return center.add(
                Math.sin(phi) * Math.cos(theta) * radius,
                Math.cos(phi) * radius,
                Math.sin(phi) * Math.sin(theta) * radius);
    }

    private Vec3 findSafeEncirclementPosition(
            ServerLevel level,
            Vec3 center,
            Vec3 wanted) {
        for (int attempt = 0; attempt < 32; attempt++) {
            Vec3 candidate = attempt == 0
                    ? wanted
                    : randomEncirclementSpherePoint(center);
            if (isSafeEncirclementPosition(
                    level, candidate)) {
                return candidate;
            }
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            Vec3 candidate = center.add(
                    (random.nextDouble() - 0.5D) * 1.5D,
                    0.35D + attempt * 0.25D,
                    (random.nextDouble() - 0.5D) * 1.5D);
            if (isSafeEncirclementPosition(
                    level, candidate)) {
                return candidate;
            }
        }
        return center.add(0.0D, 0.65D, 0.0D);
    }

    private boolean isSafeEncirclementPosition(
            ServerLevel level, Vec3 position) {
        return level.noCollision(new AABB(
                position.x - 0.24D,
                position.y,
                position.z - 0.24D,
                position.x + 0.24D,
                position.y + 0.72D,
                position.z + 0.24D));
    }

    @Nullable
    private LivingEntity randomEncirclementTarget(
            ServerLevel level, LivingEntity owner) {
        AABB area = new AABB(
                encirclementCenter.x
                        - ENCIRCLEMENT_RADIUS,
                encirclementCenter.y - 1.0D,
                encirclementCenter.z
                        - ENCIRCLEMENT_RADIUS,
                encirclementCenter.x
                        + ENCIRCLEMENT_RADIUS,
                encirclementCenter.y
                        + ENCIRCLEMENT_RADIUS + 1.5D,
                encirclementCenter.z
                        + ENCIRCLEMENT_RADIUS);
        List<LivingEntity> targets =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        target ->
                                isValidEncirclementTarget(
                                        owner, target));
        return targets.isEmpty()
                ? null
                : targets.get(
                        random.nextInt(targets.size()));
    }

    private void finishEncirclement(
            ServerLevel level, LivingEntity owner) {
        List<SexPistolsEntity> members =
                encirclementMembers(level, owner);
        int shots = Math.max(1, members.size());
        boolean dealtDamage = false;
        for (int index = 0; index < shots; index++) {
            LivingEntity target =
                    randomEncirclementTarget(level, owner);
            if (target == null) {
                break;
            }
            SexPistolsEntity member = members.isEmpty()
                    ? null
                    : members.get(index % members.size());
            Vec3 from = member != null
                    ? member.getBoundingBox().getCenter()
                    : position();
            Vec3 targetCenter =
                    target.getBoundingBox().getCenter();
            if (member != null) {
                StandPower standPower = StandPower.get(owner);
                if (!SexPistolsStamina.tryConsume(
                        standPower, owner, MEMBER_KICK_STAMINA)) {
                    continue;
                }
                member.performKick(
                        targetCenter.subtract(from), false);
            }
            if (!dealtDamage) {
                damageEncirclementTarget(
                        level,
                        owner,
                        target,
                        configuredDamage(
                                ENCIRCLEMENT_FINISH_DAMAGE));
                dealtDamage = true;
            }
            setPos(from);
            Vec3 motion = targetCenter.subtract(from);
            if (motion.lengthSqr() > 1.0E-7D) {
                setDeltaMovement(
                        motion.normalize().scale(
                                ENCIRCLEMENT_KICK_SPEED));
            }
        }
        returnEncirclementMembers(level, owner);
        encirclementActive = false;
        entityData.set(SPECIAL_ACTIVE, false);
        discard();
    }

    private void cancelEncirclement(
            ServerLevel level, LivingEntity owner) {
        returnEncirclementMembers(level, owner);
        encirclementActive = false;
        entityData.set(SPECIAL_ACTIVE, false);
        discard();
    }

    private void returnEncirclementMembers(
            ServerLevel level,
            @Nullable LivingEntity owner) {
        if (encirclementMembersReturned) {
            return;
        }
        if (owner != null
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(owner)
                    .returnEncirclementMembers(
                            level,
                            owner,
                            encirclementMemberMask);
        }
        encirclementMembersReturned = true;
    }

    private static boolean segmentIntersectsInflatedBox(
            Vec3 from,
            Vec3 to,
            AABB box,
            double inflate) {
        AABB inflated = box.inflate(inflate);
        return inflated.contains(from)
                || inflated.contains(to)
                || inflated.clip(from, to).isPresent();
    }

    private boolean splitFromMember(
            ServerLevel level,
            LivingEntity owner,
            StandPower standPower,
            SexPistolsCoordinator coordinator,
            SexPistolsEntity member,
            LivingEntity target,
            Vec3 splitOrigin,
            boolean returnAfterKick) {
        if (!isValidTarget(
                owner,
                target,
                coordinator.targetMode(),
                true)) {
            return false;
        }

        Vec3 incoming = getDeltaMovement();
        Vec3 targetPoint = target.getBoundingBox().getCenter();
        Vec3 direction = targetPoint.subtract(splitOrigin);
        if (direction.lengthSqr() <= 1.0E-7D) {
            direction = incoming.lengthSqr() > 1.0E-7D
                    ? incoming
                    : owner.getLookAngle();
        }
        direction = direction.normalize();
        boolean critical = rollSpecialCritical(owner);
        entityData.set(SPECIAL_CRITICAL, critical);
        if (critical) {
            boolean resolve =
                    owner.hasEffect(ModStatusEffects.RESOLVE);
            float chance = resolve ? 0.7F : 0.35F;
            if (owner.getRandom().nextFloat() < chance) {
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.BLEEDING,
                        100,
                        resolve ? 1 : 0,
                        false,
                        true,
                        true));
            }
        }

        member.performKick(
                direction, returnAfterKick, critical);
        entityData.set(REDIRECTED, true);
        setIsDeflected(
                direction.scale(
                        Math.max(0.25D, incoming.length())),
                splitOrigin);

        int memberSlot = SexPistolsEntities.slotForNumber(
                member.getMemberNumber());
        if (memberSlot >= 0) {
            attachedMemberMask &= ~(1 << memberSlot);
        }
        int remainingMask = attachedMemberMask;
        attachedMemberMask = 0;
        splitIntoFragments(
                level,
                owner,
                coordinator,
                target,
                splitOrigin,
                incoming);
        releaseSplittingMembers(
                level,
                owner,
                standPower,
                coordinator,
                remainingMask,
                splitOrigin,
                incoming);
        level.sendParticles(
                ParticleTypes.CRIT,
                splitOrigin.x,
                splitOrigin.y,
                splitOrigin.z,
                12,
                0.18D,
                0.12D,
                0.18D,
                0.08D);
        playSound(
                SoundEvents.FIREWORK_ROCKET_BLAST,
                0.45F,
                1.55F + random.nextFloat() * 0.25F);
        discard();
        return true;
    }

    private void splitIntoFragments(
            ServerLevel level,
            LivingEntity owner,
            SexPistolsCoordinator coordinator,
            LivingEntity primaryTarget,
            Vec3 origin,
            Vec3 incomingMotion) {
        boolean resolve =
                owner.hasEffect(ModStatusEffects.RESOLVE);
        int minCount = resolve ? 4 : 2;
        int maxCount = resolve ? 6 : 5;
        int fragmentCount = minCount
                + random.nextInt(maxCount - minCount + 1);
        UUID volleyId = coordinator.beginSplittingVolley(
                owner, fragmentCount);
        List<LivingEntity> targets =
                splittingFragmentTargets(
                        level,
                        owner,
                        primaryTarget,
                        origin,
                        fragmentCount,
                        coordinator.targetMode());
        for (int index = 0;
                index < fragmentCount;
                index++) {
            LivingEntity target = targets.isEmpty()
                    ? primaryTarget
                    : targets.get(index % targets.size());
            if (!spawnSplittingFragment(
                    level,
                    owner,
                    target,
                    origin,
                    incomingMotion,
                    index,
                    fragmentCount,
                    volleyId)) {
                coordinator.releaseSplittingFragment(
                        owner, volleyId);
            }
        }
    }

    private List<LivingEntity> splittingFragmentTargets(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity primaryTarget,
            Vec3 origin,
            int fragmentCount,
            SexPistolsTargetMode targetMode) {
        List<LivingEntity> targets =
                new ArrayList<>(fragmentCount);
        if (primaryTarget.isAlive()) {
            targets.add(primaryTarget);
        }
        AABB search = AABB.ofSize(
                origin,
                SPLITTING_TARGET_SEARCH_RANGE * 2.0D,
                SPLITTING_TARGET_SEARCH_RANGE * 2.0D,
                SPLITTING_TARGET_SEARCH_RANGE * 2.0D);
        List<LivingEntity> candidates =
                new ArrayList<>(level.getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        candidate -> candidate != primaryTarget
                                && isValidTarget(
                                        owner,
                                        candidate,
                                        targetMode,
                                        true)));
        candidates.sort(Comparator.comparingDouble(
                candidate -> candidate.distanceToSqr(origin)));
        double minDistanceSqr =
                SPLITTING_TARGET_SPACING
                        * SPLITTING_TARGET_SPACING;
        for (LivingEntity candidate : candidates) {
            boolean spaced = true;
            for (LivingEntity selected : targets) {
                if (candidate.distanceToSqr(selected)
                        < minDistanceSqr) {
                    spaced = false;
                    break;
                }
            }
            if (spaced) {
                targets.add(candidate);
                if (targets.size() >= fragmentCount) {
                    break;
                }
            }
        }
        return targets;
    }

    private boolean spawnSplittingFragment(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target,
            Vec3 origin,
            Vec3 incomingMotion,
            int index,
            int total,
            @Nullable UUID volleyId) {
        Vec3 incomingDirection =
                incomingMotion.lengthSqr() > 1.0E-7D
                        ? incomingMotion.normalize()
                        : target.getBoundingBox().getCenter()
                                .subtract(origin)
                                .normalize();
        Vec3 targetPoint = target.getBoundingBox().getCenter()
                .add(0.0D, target.getEyeHeight() * 0.08D, 0.0D);
        Vec3 targetDirection = targetPoint.subtract(origin);
        if (targetDirection.lengthSqr() <= 1.0E-7D) {
            targetDirection = incomingDirection;
        }
        targetDirection = targetDirection.normalize();
        Vec3 side = targetDirection.cross(
                new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() <= 1.0E-7D) {
            side = incomingDirection.cross(
                    new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() <= 1.0E-7D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 lift = side.cross(targetDirection).normalize();
        double spread = index - (total - 1) * 0.5D;
        Vec3 spawnPosition = origin
                .add(incomingDirection.scale(0.18D))
                .add(targetDirection.scale(0.18D))
                .add(side.scale(spread * 0.08D))
                .add(lift.scale(
                        (index & 1) == 0 ? 0.06D : -0.03D));
        double speed = Math.max(
                1.35D,
                incomingMotion.length()
                        * (0.5D + random.nextDouble() * 0.08D));
        double distance = Math.max(
                1.0D, spawnPosition.distanceTo(targetPoint));
        double curveSign = Math.abs(spread) > 0.01D
                ? Math.signum(spread)
                : random.nextBoolean() ? 1.0D : -1.0D;
        double curveMagnitude = Mth.clamp(
                distance * 0.11D,
                0.45D,
                total <= 2 ? 1.15D : 1.7D);
        double liftMagnitude = Mth.clamp(
                distance * 0.035D,
                0.12D,
                0.55D) * (random.nextDouble() - 0.35D);
        Vec3 curve = side.scale(curveSign * curveMagnitude)
                .add(lift.scale(liftMagnitude));
        Vec3 control = spawnPosition.add(targetPoint)
                .scale(0.5D)
                .add(curve);
        double pathDistance =
                spawnPosition.distanceTo(control)
                        + control.distanceTo(targetPoint);
        int pathTicks = Mth.clamp(
                (int) Math.ceil(pathDistance / speed),
                5,
                20);
        Vec3 firstPoint = quadraticBezier(
                spawnPosition,
                control,
                targetPoint,
                1.0D / pathTicks);
        Vec3 initialMotion =
                firstPoint.subtract(spawnPosition);
        if (initialMotion.lengthSqr() <= 1.0E-7D) {
            initialMotion = targetDirection.scale(speed);
        }
        Vec3 exitDirection =
                targetPoint.subtract(control);
        if (exitDirection.lengthSqr() <= 1.0E-7D) {
            exitDirection = targetDirection;
        }

        RevolverBulletEntity fragment =
                new RevolverBulletEntity(
                        SexPistolsEntities.REVOLVER_BULLET.get(),
                        owner,
                        level);
        fragment.setPos(spawnPosition);
        fragment.setDeltaMovement(initialMotion);
        fragment.resolveRicochetCount =
                resolveRicochetCount + 1;
        fragment.setSplittingFragment(
                target,
                spawnPosition,
                targetPoint,
                curve,
                exitDirection,
                pathTicks,
                volleyId);
        return level.addFreshEntity(fragment);
    }

    private void setSplittingFragment(
            LivingEntity target,
            Vec3 start,
            Vec3 end,
            Vec3 curve,
            Vec3 forward,
            int pathTicks,
            @Nullable UUID volleyId) {
        splittingFragment = true;
        splittingTargetUuid = target.getUUID();
        splittingFragmentTicks = 0;
        splittingPathStart = start;
        splittingPathEnd = end;
        splittingCurve = curve;
        splittingForward = forward.lengthSqr() > 1.0E-7D
                ? forward.normalize()
                : Vec3.ZERO;
        splittingPathTicks = pathTicks;
        splittingVolleyUuid = volleyId;
        splittingTrackerReleased = false;
        setSpecialShotType(ShotType.SPLITTING);
        entityData.set(SPECIAL_ACTIVE, true);
    }

    private void tickSplittingFragmentGuidance() {
        splittingFragmentTicks++;
        if (splittingFragmentTicks
                > SPLITTING_FRAGMENT_LIFETIME_TICKS) {
            discard();
            return;
        }
        int pathTicks = Math.max(1, splittingPathTicks);
        if (splittingFragmentTicks <= pathTicks) {
            double progress = Mth.clamp(
                    (double) splittingFragmentTicks
                            / pathTicks,
                    0.0D,
                    1.0D);
            Vec3 control = splittingPathStart
                    .add(splittingPathEnd)
                    .scale(0.5D)
                    .add(splittingCurve);
            Vec3 nextPoint = quadraticBezier(
                    splittingPathStart,
                    control,
                    splittingPathEnd,
                    progress);
            Vec3 step = nextPoint.subtract(position());
            if (step.lengthSqr() > 1.0E-7D) {
                setDeltaMovement(step);
            }
        }
        else if (splittingFragmentTicks == pathTicks + 1) {
            Vec3 exit = splittingForward.lengthSqr()
                            > 1.0E-7D
                    ? splittingForward
                    : getDeltaMovement();
            if (exit.lengthSqr() > 1.0E-7D) {
                double speed = Mth.clamp(
                        getDeltaMovement().length(),
                        1.15D,
                        3.4D);
                setDeltaMovement(
                        exit.normalize().scale(speed));
            }
        }
    }

    private static Vec3 quadraticBezier(
            Vec3 start,
            Vec3 control,
            Vec3 end,
            double progress) {
        double inverse = 1.0D - progress;
        return start.scale(inverse * inverse)
                .add(control.scale(
                        2.0D * inverse * progress))
                .add(end.scale(progress * progress));
    }

    private void releaseSplittingMembers(
            ServerLevel level,
            LivingEntity owner,
            StandPower standPower,
            SexPistolsCoordinator coordinator,
            int memberMask,
            Vec3 kickPoint,
            Vec3 motion) {
        if (memberMask == 0) {
            return;
        }
        Vec3 direction = motion.lengthSqr() > 1.0E-7D
                ? motion.normalize()
                : owner.getLookAngle();
        Vec3 side = direction.cross(
                new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() <= 1.0E-7D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        int count = Math.max(
                1, Integer.bitCount(memberMask));
        int released = 0;
        for (int slot = 0;
                slot < SexPistolsCoordinator.MEMBER_COUNT;
                slot++) {
            if ((memberMask & 1 << slot) == 0) {
                continue;
            }
            double spread = released - (count - 1) * 0.5D;
            Vec3 memberPosition = kickPoint
                    .subtract(direction.scale(0.22D))
                    .add(side.scale(spread * 0.22D))
                    .add(0.0D,
                            0.08D + (released % 2) * 0.08D,
                            0.0D);
            boolean kick = SexPistolsStamina.tryConsume(
                    standPower, owner, MEMBER_KICK_STAMINA);
            SexPistolsEntity releasedMember =
                    coordinator.releaseInFlightMember(
                            level,
                            owner,
                            slot,
                            memberPosition);
            if (releasedMember != null) {
                if (kick) {
                    releasedMember.performKick(
                            direction, true);
                }
                else {
                    releasedMember.setMemberState(
                            SexPistolsMemberState.FORMATION);
                }
            }
            released++;
        }
    }

    private boolean rollSpecialCritical(
            LivingEntity owner) {
        float chance = SexPistolsJoyful.criticalChance(owner);
        return owner.getRandom().nextFloat() < chance;
    }

    public float resolveGainMultiplier() {
        if (encirclementActive
                || specialShotType() == ShotType.ENCIRCLEMENT) {
            return 1.25F;
        }
        float multiplier = 5.0F;
        if (resolveAttachmentCount > 0
                && specialShotType() == ShotType.NONE
                && !splittingFragment) {
            multiplier *= resolveAttachmentCount * 0.5F;
        }
        float ricochetMultiplier = 1.0F;
        for (int i = 0; i < resolveRicochetCount; i++) {
            ricochetMultiplier = Math.min(
                    5.0F, ricochetMultiplier * 1.25F);
        }
        return multiplier * ricochetMultiplier;
    }

    @Nullable
    private LivingEntity findTarget(
            ServerLevel level,
            LivingEntity owner,
            Vec3 origin,
            SexPistolsTargetMode mode) {
        return findTarget(
                level, owner, origin, mode, false);
    }

    @Nullable
    private LivingEntity findTarget(
            ServerLevel level,
            LivingEntity owner,
            Vec3 origin,
            SexPistolsTargetMode mode,
            boolean allowEnemyStands) {
        double targetSearchRange =
                SexPistolsConfig.targetSearchRange();
        Entity locked = lockedTargetUuid != null
                ? level.getEntity(lockedTargetUuid)
                : null;
        if (locked instanceof LivingEntity living
                && living.distanceToSqr(origin)
                        <= targetSearchRange
                                * targetSearchRange
                && isValidTarget(
                        owner,
                        living,
                        mode,
                        allowEnemyStands)
                && hasLineOfSight(
                        level, this, origin,
                        living.getBoundingBox().getCenter())) {
            return living;
        }

        AABB search = AABB.ofSize(
                origin,
                targetSearchRange * 2.0D,
                targetSearchRange * 2.0D,
                targetSearchRange * 2.0D);
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        candidate ->
                                isValidTarget(
                                         owner,
                                         candidate,
                                         mode,
                                         allowEnemyStands)
                                && hasLineOfSight(
                                        level,
                                        this,
                                        origin,
                                        candidate.getBoundingBox()
                                                .getCenter()))
                .stream()
                .min(Comparator.comparingDouble(
                        candidate ->
                                candidate.distanceToSqr(origin)))
                .orElse(null);
    }

    @Nullable
    private SexPistolsEntity findRelay(
            ServerLevel level,
            LivingEntity owner,
            SexPistolsEntity source,
            @Nullable LivingEntity target,
            SexPistolsCoordinator coordinator,
            Vec3 origin) {
        SexPistolsTransferOrder order =
                coordinator.transferOrder();
        if (order == SexPistolsTransferOrder.NONE) {
            return null;
        }
        double transferSearchRange =
                SexPistolsConfig.transferAssistRange();

        List<SexPistolsEntity> candidates =
                coordinator.loadedMembers(level).stream()
                        .filter(member -> member != source)
                        .filter(SexPistolsEntity::canRedirectBullet)
                        .filter(member -> lastTransferMemberUuid == null
                                || !member.getUUID().equals(
                                        lastTransferMemberUuid))
                        .filter(member -> {
                            int slot =
                                    SexPistolsEntities
                                            .slotForNumber(
                                                    member
                                                            .getMemberNumber());
                            return slot >= 0
                                    && (redirectedMemberMask
                                            & 1 << slot) == 0;
                        })
                        .filter(member ->
                                member.distanceToSqr(origin)
                                        <= transferSearchRange
                                                * transferSearchRange)
                        .filter(member -> hasLineOfSight(
                                level,
                                source,
                                origin,
                                member.getBoundingBox()
                                        .getCenter()))
                        .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        if (order == SexPistolsTransferOrder.NUMBER) {
            return candidates.stream()
                    .filter(member ->
                            member.getMemberNumber()
                                    > source.getMemberNumber())
                    .min(Comparator
                            .comparingInt(
                                    SexPistolsEntity
                                            ::getMemberNumber)
                            .thenComparingDouble(
                                    member ->
                                            member.distanceToSqr(
                                                    origin)))
                    .orElse(null);
        }

        if (target == null) {
            return null;
        }
        Vec3 targetPosition =
                target.getBoundingBox().getCenter();
        double sourceDistance =
                source.distanceToSqr(targetPosition);
        return candidates.stream()
                .filter(member ->
                        member.distanceToSqr(targetPosition)
                                < sourceDistance)
                .min(Comparator.comparingDouble(
                        member ->
                                member.distanceToSqr(
                                        targetPosition)))
                .orElse(null);
    }

    private static boolean isValidTarget(
            LivingEntity owner,
            LivingEntity target,
            SexPistolsTargetMode mode) {
        return isValidTarget(
                owner, target, mode, false);
    }

    private static boolean isValidTarget(
            LivingEntity owner,
            LivingEntity target,
            SexPistolsTargetMode mode,
            boolean allowEnemyStands) {
        if (!target.isAlive()
                || target == owner
                || target instanceof SexPistolsEntity
                || target instanceof Player player
                        && player.isSpectator()) {
            return false;
        }
        if (target instanceof StandEntity stand) {
            return allowEnemyStands
                    && stand.getUser() != owner;
        }
        return switch (mode) {
            case PLAYERS -> target instanceof Player player
                    && !owner.isAlliedTo(player)
                    && (!(owner instanceof Player ownerPlayer)
                            || ownerPlayer.canHarmPlayer(player));
            case HOSTILE -> target instanceof Monster;
            case FRIENDLY -> !(target instanceof Player)
                    && !(target instanceof Monster);
            case ALL -> !(target instanceof Player player)
                    || !(owner instanceof Player ownerPlayer)
                    || ownerPlayer.canHarmPlayer(player);
        };
    }

    private static boolean hasLineOfSight(
            ServerLevel level,
            Entity context,
            Vec3 start,
            Vec3 end) {
        return level.clip(new ClipContext(
                        start,
                        end,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        context))
                .getType() == HitResult.Type.MISS;
    }

    private void appendTrace(Vec3 position) {
        if (!tracePos.isEmpty()
                && tracePos.get(tracePos.size() - 1)
                        .distanceToSqr(position) < 0.0625D) {
            return;
        }
        tracePos.add(position);
        while (tracePos.size() > MAX_TRACE_POINTS) {
            tracePos.remove(0);
        }
    }

    @Override
    public boolean hasDeflectedVisuals() {
        return true;
    }

    @Override
    public void setIsDeflected(
            @Nullable Vec3 deflectVec,
            Vec3 deflectPos) {
        super.setIsDeflected(deflectVec, deflectPos);
        if (level().isClientSide() && deflectVec != null) {
            setDeltaMovement(deflectVec);
            appendTrace(deflectPos);
        }
    }

    @Override
    protected void afterEntityHit(
            EntityHitResult hit,
            boolean entityHurt) {
        if (splittingFragment
                && entityHurt
                && hit.getEntity()
                        instanceof LivingEntity target
                && (!target.isAlive()
                        || target.getHealth() <= 0.0F)) {
            LivingEntity owner = getOwner();
            if (owner != null
                    && owner.hasData(
                            SexPistolsAttachments
                                    .SEX_PISTOLS_COORDINATOR)) {
                SexPistolsAttachments.coordinator(owner)
                        .recordSplittingFragmentKill(
                                owner,
                                splittingVolleyUuid,
                                target);
            }
        }
        if (entityHurt && entityData.get(REDIRECTED)) {
            playSound(
                    SexPistolsSounds.randomRicochetHit(random),
                    0.8F,
                    0.95F + random.nextFloat() * 0.1F);
            if (level() instanceof ServerLevel serverLevel) {
                Vec3 position = hit.getLocation();
                serverLevel.sendParticles(
                        ParticleTypes.CRIT,
                        position.x,
                        position.y,
                        position.z,
                        4,
                        0.08D,
                        0.08D,
                        0.08D,
                        0.03D);
            }
        }
    }

    @Override
    public int ticksLifespan() {
        if (splittingFragment) {
            return SPLITTING_FRAGMENT_LIFETIME_TICKS;
        }
        return switch (specialShotType()) {
            case PIERCING -> PIERCING_LIFETIME_TICKS;
            case ENCIRCLEMENT ->
                    MAX_LIFETIME_TICKS
                            + ENCIRCLEMENT_DURATION_TICKS
                            + ENCIRCLEMENT_RESOLVE_BONUS_TICKS;
            case NONE, SPLITTING -> MAX_LIFETIME_TICKS;
        };
    }

    @Override
    protected float getBaseDamage() {
        float damage;
        if (splittingFragment) {
            damage = configuredDamage(
                    SPLITTING_FRAGMENT_DAMAGE);
        }
        else {
            damage = RevolverBallistics.distanceScaledDamage(
                    travelDistance,
                    SexPistolsConfig.closeDamageDropDistance(),
                    SexPistolsConfig.stableDamageEndDistance(),
                    SexPistolsConfig.minDistanceDamage(),
                    SexPistolsConfig.bulletDamageMultiplier(),
                    CLOSE_DAMAGE,
                    STABLE_DAMAGE,
                    FAR_DAMAGE_DROP_RATE);
        }
        LivingEntity owner = getOwner();
        return owner != null
                ? SexPistolsJoyful.applyBulletDamage(
                        owner, damage)
                : damage;
    }

    private static float configuredDamage(float baseDamage) {
        return RevolverBallistics.scaledDamage(
                baseDamage,
                SexPistolsConfig.bulletDamageMultiplier());
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.3F;
    }

    @Override
    public boolean standDamage() {
        return entityData.get(REDIRECTED)
                || specialShotType() != ShotType.NONE;
    }

    // 1.16: every bullet of a Stand user is Stand damage, kicked or not.
    @Override
    protected ResourceKey<DamageType> getDamageTypeKey() {
        if (standDamage() || ownerHasStand()) {
            return STAND_REVOLVER_BULLET_DAMAGE;
        }
        return super.getDamageTypeKey();
    }

    private boolean ownerHasStand() {
        LivingEntity owner = getOwner();
        StandPower standPower = owner != null
                ? StandPower.get(owner)
                : null;
        return standPower != null && standPower.hasPower();
    }

    @Override
    public boolean onlyVisibleToStandUsers() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("BulletTravelDistance", travelDistance);
        getStandSkin().ifPresent(value ->
                tag.putString(
                        "SexPistolsSkin",
                        value.toString()));
        if (sexPistolsOwnerUuid != null) {
            tag.putUUID(
                    "SexPistolsOwner",
                    sexPistolsOwnerUuid);
            tag.putString(
                    "SexPistolsOwnerEpoch",
                    ownerLifecycleEpoch.toString());
        }
        tag.putInt(
                "SexPistolsRedirectedMask",
                redirectedMemberMask);
        tag.putInt(
                "SexPistolsResolveRicochets",
                resolveRicochetCount);
        tag.putInt("BulletRicochetCount", ricochetCount);
        tag.putInt(
                "SexPistolsAttachedMask",
                attachedMemberMask);
        tag.putInt(
                "SexPistolsResolveAttachmentCount",
                resolveAttachmentCount);
        tag.putInt(
                "SexPistolsAutoRedirectCooldown",
                autoRedirectCooldown);
        tag.putBoolean(
                "SexPistolsRedirected",
                entityData.get(REDIRECTED));
        tag.putInt(
                "SexPistolsSpecialShotType",
                specialShotType().ordinal());
        tag.putBoolean(
                "SexPistolsSpecialActive",
                entityData.get(SPECIAL_ACTIVE));
        tag.putBoolean(
                "SexPistolsSpecialCritical",
                entityData.get(SPECIAL_CRITICAL));
        tag.putBoolean(
                "TimeStopBullet",
                entityData.get(TIME_STOP_ACTIVE));
        tag.putInt(
                "TimeStopSlowdownTicks",
                timeStopSlowdownTicksRemaining());
        if (timeStopStoredMotion != null) {
            tag.putBoolean("TimeStopStoredMotionPresent", true);
            putVec3(
                    tag,
                    "TimeStopStoredMotion",
                    timeStopStoredMotion);
        }
        if (timeStopHitMotion != null) {
            tag.putBoolean("TimeStopHitMotionPresent", true);
            putVec3(
                    tag,
                    "TimeStopHitMotion",
                    timeStopHitMotion);
        }
        if (lockedTargetUuid != null) {
            tag.putUUID(
                    "SexPistolsLockedTarget",
                    lockedTargetUuid);
        }
        if (lastTransferMemberUuid != null) {
            tag.putUUID(
                    "SexPistolsLastTransferMember",
                    lastTransferMemberUuid);
        }

        tag.putBoolean(
                "PiercingInitialized",
                piercingInitialized);
        tag.putInt("PiercingTicks", piercingTicks);
        tag.putInt(
                "PiercingBlocksPierced",
                piercingBlocksPierced);
        tag.putInt(
                "PiercingHitTargetCount",
                piercingHitTargets.size());
        int piercingTargetIndex = 0;
        for (UUID targetUuid : piercingHitTargets) {
            tag.putUUID(
                    "PiercingHitTarget"
                            + piercingTargetIndex++,
                    targetUuid);
        }

        tag.putBoolean(
                "SplittingFragment",
                splittingFragment);
        tag.putInt(
                "SplittingFragmentTicks",
                splittingFragmentTicks);
        tag.putInt(
                "SplittingPathTicks",
                splittingPathTicks);
        putVec3(
                tag,
                "SplittingPathStart",
                splittingPathStart);
        putVec3(
                tag,
                "SplittingPathEnd",
                splittingPathEnd);
        putVec3(
                tag,
                "SplittingCurve",
                splittingCurve);
        putVec3(
                tag,
                "SplittingForward",
                splittingForward);
        tag.putBoolean(
                "SplittingTrackerReleased",
                splittingTrackerReleased);
        if (splittingTargetUuid != null) {
            tag.putUUID(
                    "SplittingTarget",
                    splittingTargetUuid);
        }
        if (splittingVolleyUuid != null) {
            tag.putUUID(
                    "SplittingVolley",
                    splittingVolleyUuid);
        }

        tag.putBoolean(
                "EncirclementActive",
                encirclementActive);
        tag.putInt(
                "EncirclementTicks",
                encirclementTicks);
        tag.putInt(
                "EncirclementMemberMask",
                encirclementMemberMask);
        putVec3(
                tag,
                "EncirclementCenter",
                encirclementCenter);
        tag.putInt(
                "EncirclementSourceSlot",
                encirclementSourceSlot);
        tag.putInt(
                "EncirclementTargetSlot",
                encirclementTargetSlot);
        tag.putInt(
                "EncirclementKickCooldown",
                encirclementKickCooldown);
        tag.putBoolean(
                "EncirclementMembersReturned",
                encirclementMembersReturned);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        travelDistance = Math.max(
                0.0D,
                tag.getDouble("BulletTravelDistance"));
        ResourceLocation standSkin = tag.contains(
                "SexPistolsSkin")
                ? ResourceLocation.tryParse(
                        tag.getString("SexPistolsSkin"))
                : null;
        setStandSkin(java.util.Optional.ofNullable(standSkin));
        sexPistolsOwnerUuid =
                tag.hasUUID("SexPistolsOwner")
                        ? tag.getUUID("SexPistolsOwner")
                        : null;
        ownerLifecycleEpoch = readOwnerLifecycleEpoch(tag);
        redirectedMemberMask =
                tag.getInt("SexPistolsRedirectedMask")
                        & 0x3F;
        resolveRicochetCount = Math.max(
                0,
                tag.getInt("SexPistolsResolveRicochets"));
        ricochetCount = Mth.clamp(
                tag.getInt("BulletRicochetCount"),
                0,
                SexPistolsConfig.maxRicochetCount());
        attachedMemberMask =
                tag.getInt("SexPistolsAttachedMask")
                        & 0x3F;
        resolveAttachmentCount = Math.max(
                Integer.bitCount(attachedMemberMask),
                tag.getInt(
                        "SexPistolsResolveAttachmentCount"));
        autoRedirectCooldown = Math.max(
                0,
                tag.getInt(
                        "SexPistolsAutoRedirectCooldown"));
        entityData.set(
                REDIRECTED,
                tag.getBoolean("SexPistolsRedirected"));
        setSpecialShotType(ShotType.byId(
                tag.getInt("SexPistolsSpecialShotType")));
        entityData.set(
                SPECIAL_ACTIVE,
                tag.getBoolean("SexPistolsSpecialActive"));
        entityData.set(
                SPECIAL_CRITICAL,
                tag.getBoolean("SexPistolsSpecialCritical"));
        entityData.set(
                TIME_STOP_ACTIVE,
                tag.getBoolean("TimeStopBullet"));
        entityData.set(
                TIME_STOP_SLOWDOWN_REMAINING,
                (byte) Mth.clamp(
                        tag.getInt("TimeStopSlowdownTicks"),
                        0,
                        TIME_STOP_SLOWDOWN_TICKS));
        timeStopStoredMotion = tag.getBoolean(
                "TimeStopStoredMotionPresent")
                        ? getVec3(tag, "TimeStopStoredMotion")
                        : null;
        timeStopHitMotion = tag.getBoolean(
                "TimeStopHitMotionPresent")
                        ? getVec3(tag, "TimeStopHitMotion")
                        : null;
        lockedTargetUuid =
                tag.hasUUID("SexPistolsLockedTarget")
                        ? tag.getUUID(
                                "SexPistolsLockedTarget")
                        : null;
        lastTransferMemberUuid =
                tag.hasUUID(
                        "SexPistolsLastTransferMember")
                        ? tag.getUUID(
                                "SexPistolsLastTransferMember")
                        : null;

        piercingInitialized =
                tag.getBoolean("PiercingInitialized");
        piercingTicks = Math.max(
                0, tag.getInt("PiercingTicks"));
        piercingBlocksPierced = Mth.clamp(
                tag.getInt("PiercingBlocksPierced"),
                0,
                PIERCING_MAX_BLOCKS);
        piercingHitTargets.clear();
        int piercingTargetCount = Mth.clamp(
                tag.getInt("PiercingHitTargetCount"),
                0,
                MAX_TRACKED_PIERCING_TARGETS);
        for (int index = 0;
                index < piercingTargetCount;
                index++) {
            String key = "PiercingHitTarget" + index;
            if (tag.hasUUID(key)) {
                piercingHitTargets.add(
                        tag.getUUID(key));
            }
        }

        splittingFragment =
                tag.getBoolean("SplittingFragment");
        splittingTargetUuid =
                tag.hasUUID("SplittingTarget")
                        ? tag.getUUID("SplittingTarget")
                        : null;
        splittingFragmentTicks = Math.max(
                0,
                tag.getInt("SplittingFragmentTicks"));
        splittingPathTicks = Mth.clamp(
                tag.getInt("SplittingPathTicks"),
                0,
                200);
        splittingPathStart = getVec3(
                tag, "SplittingPathStart");
        splittingPathEnd = getVec3(
                tag, "SplittingPathEnd");
        splittingCurve = getVec3(
                tag, "SplittingCurve");
        splittingForward = getVec3(
                tag, "SplittingForward");
        splittingVolleyUuid =
                tag.hasUUID("SplittingVolley")
                        ? tag.getUUID("SplittingVolley")
                        : null;
        splittingTrackerReleased =
                tag.getBoolean(
                        "SplittingTrackerReleased");

        encirclementActive =
                tag.getBoolean("EncirclementActive");
        encirclementTicks = Math.max(
                0, tag.getInt("EncirclementTicks"));
        encirclementMemberMask =
                tag.getInt("EncirclementMemberMask")
                        & 0x3F;
        encirclementCenter = getVec3(
                tag, "EncirclementCenter");
        encirclementSourceSlot =
                tag.getInt("EncirclementSourceSlot");
        encirclementTargetSlot =
                tag.getInt("EncirclementTargetSlot");
        encirclementKickCooldown = Math.max(
                0,
                tag.getInt(
                        "EncirclementKickCooldown"));
        encirclementMembersReturned =
                tag.getBoolean(
                        "EncirclementMembersReturned");
        if (encirclementMembersReturned) {
            encirclementActive = false;
        }
        if (encirclementActive) {
            entityData.set(SPECIAL_ACTIVE, true);
        }
        if (specialShotType() == ShotType.PIERCING
                || encirclementActive) {
            noPhysics = true;
            setNoGravity(true);
        }
        if (splittingFragment) {
            setNoGravity(true);
        }
    }

    private static void putVec3(
            CompoundTag tag,
            String key,
            Vec3 vector) {
        tag.putDouble(key + "X", vector.x);
        tag.putDouble(key + "Y", vector.y);
        tag.putDouble(key + "Z", vector.z);
    }

    private static Vec3 getVec3(
            CompoundTag tag, String key) {
        return new Vec3(
                tag.getDouble(key + "X"),
                tag.getDouble(key + "Y"),
                tag.getDouble(key + "Z"));
    }

    private static BigInteger readOwnerLifecycleEpoch(
            CompoundTag tag) {
        if (!tag.contains(
                "SexPistolsOwnerEpoch",
                net.minecraft.nbt.Tag.TAG_STRING)) {
            return INVALID_OWNER_EPOCH;
        }
        try {
            BigInteger epoch = new BigInteger(
                    tag.getString("SexPistolsOwnerEpoch"));
            return epoch.signum() >= 0
                    ? epoch
                    : INVALID_OWNER_EPOCH;
        }
        catch (NumberFormatException ignored) {
            return INVALID_OWNER_EPOCH;
        }
    }

    private void releaseSplittingTracker() {
        if (splittingTrackerReleased) {
            return;
        }
        splittingTrackerReleased = true;
        LivingEntity owner = getOwner();
        if (owner != null
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(owner)
                    .releaseSplittingFragment(
                            owner, splittingVolleyUuid);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()
                && reason.shouldDestroy()
                && !suppressOwnerLifecycleRelease) {
            if (attachedMemberMask != 0) {
                releaseRemainingAttachedMembers();
            }
            if (splittingFragment) {
                releaseSplittingTracker();
            }
            if (encirclementActive
                    && !encirclementMembersReturned
                    && level() instanceof
                            ServerLevel serverLevel) {
                returnEncirclementMembers(
                        serverLevel, getOwner());
            }
        }
        tracePos.clear();
        super.remove(reason);
    }

    public enum TimeStopPhase {
        NORMAL,
        DECELERATING,
        FROZEN
    }

    private enum ImpactTier {
        FRAGILE_BREAK,
        SOFT_BREAK,
        HARD_SURFACE,
        FORCED_RICOCHET
    }

    private record BlockImpactKey(
            ResourceKey<Level> dimension,
            long blockPosLong) {}

    private static final class BlockDamageRecord {
        private final int breakerId;
        private final int blockStateId;
        private int hits;
        private long lastHitGameTime;

        private BlockDamageRecord(
                int breakerId, int blockStateId) {
            this.breakerId = breakerId;
            this.blockStateId = blockStateId;
        }
    }
}
