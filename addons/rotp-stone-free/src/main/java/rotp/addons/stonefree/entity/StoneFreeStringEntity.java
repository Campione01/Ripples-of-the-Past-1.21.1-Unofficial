package rotp.addons.stonefree.entity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.ClientProxy;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.ImmobilizeEffect;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.ability.StoneFreeExtendedPunchAbility;
import rotp.addons.stonefree.state.StoneFreeState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

public final class StoneFreeStringEntity
        extends OwnerBoundProjectileEntity {
    public static final double STRING_RANGE = 24.0D;
    public static final double GRAPPLE_FLIGHT_SPEED = 4.0D;
    public static final double GRAPPLE_PULL_SPEED = 2.0D;
    public static final double ENTITY_GRAPPLE_PULL_SPEED = 1.5D;
    public static final double STAND_GRAPPLE_PULL_SPEED = 1.0D;
    public static final double STOP_DISTANCE = 2.0D;
    public static final double CAPTURE_PULL_SPEED = 1.0D;
    public static final double CAPTURE_HOLD_DISTANCE = 4.0D;
    public static final int HOLDING_EXTRA_TICKS = 25;
    public static final int STAND_STRING_TICKS = 24;
    public static final float USER_GRAPPLE_STAMINA_PER_TICK = 1.0F;
    public static final int BARRIER_ALERT_TICKS = 40;
    public static final int BARRIER_HEALTH = 3;

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>>
            USER_UUID = SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer>
            VISUAL_OWNER_ID = SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BINDING =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ATTACHED_ENTITY =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ATTACHED_TO_BLOCK =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos>
            BLOCK_ATTACHED_TO = SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> BARRIER_ORIGIN =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos>
            BARRIER_TERMINAL = SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean>
            BARRIER_ATTACHED = SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BARRIER_HP =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ALERT_TICKS =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RIPPED =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>
            GRAPPLE_RETRACTING = SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean>
            CAPTURE_PULLING = SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.BOOLEAN);
    // 1.16 SFStringEntity and OwnerBoundProjectileEntity sent these with
    // the spawn data: the client moves the Stand's string the same way.
    private static final EntityDataAccessor<Integer> STAND_BIND_TICKS =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> Y_ROT_OFFSET =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> X_ROT_OFFSET =
            SynchedEntityData.defineId(
                    StoneFreeStringEntity.class,
                    EntityDataSerializers.FLOAT);

    private final Set<Entity> draggedEntities = new HashSet<>();
    private boolean capture;
    @Nullable
    private UUID attachedEntityUuid;
    private double extensionDistance;
    private boolean activeStringRegistered;
    private boolean rippedHurtUser;
    private boolean dealtDamage;

    public StoneFreeStringEntity(
            EntityType<? extends StoneFreeStringEntity> type,
            Level level) {
        super(type, level);
    }

    public StoneFreeStringEntity(
            Kind kind,
            StoneFreeStandEntity shooter,
            Level level) {
        super(typeFor(kind), shooter, level);
        entityData.set(KIND, kind.ordinal());
        entityData.set(VISUAL_OWNER_ID, shooter.getId());
        if (kind == Kind.STAND_STRING) {
            entityData.set(BINDING, true);
        }
        LivingEntity user = shooter.getUser();
        if (user != null) {
            entityData.set(
                    USER_UUID,
                    Optional.of(user.getUUID()));
        }
        if (kind == Kind.USER_BARRIER) {
            setOwner(user);
        }
    }

    public Kind kind() {
        int index = Mth.clamp(
                entityData.get(KIND),
                0,
                Kind.values().length - 1);
        return Kind.values()[index];
    }

    // 1.16 SFStringEntity always catches: the Stand's string has no other form.
    public void setBinding(boolean binding) {
        entityData.set(BINDING, binding || kind() == Kind.STAND_STRING);
    }

    // 1.16 StoneFreeStringBind.addProjectile: the Stand's bind string lives
    // as long as the bind task performs; a capture string keeps its 24.
    public void setStandBindTicks(int ticks) {
        entityData.set(STAND_BIND_TICKS, Math.max(1, ticks));
    }

    private int standBindTicks() {
        return entityData.get(STAND_BIND_TICKS);
    }

    // 1.16 SFStringEntity yRotOffset / xRotOffset: a capture string goes
    // out at its angle from the Stand's look.
    public void setRotOffsets(float yRotOffset, float xRotOffset) {
        entityData.set(Y_ROT_OFFSET, yRotOffset);
        entityData.set(X_ROT_OFFSET, xRotOffset);
    }

    // 1.16 SFStringEntity isCapture: a string of the capture drags its
    // catch to the Stand; the Stand's plain bind string does not.
    public void setCapture() {
        capture = true;
        entityData.set(CAPTURE_PULLING, true);
    }

    public void configureOpenBarrier(BlockPos origin) {
        entityData.set(BARRIER_ORIGIN, origin.immutable());
        entityData.set(BARRIER_TERMINAL, origin.immutable());
        entityData.set(BARRIER_ATTACHED, false);
        setPos(Vec3.atCenterOf(origin));
        setDeltaMovement(Vec3.ZERO);
    }

    public boolean attachBarrier(BlockPos terminal) {
        if (kind() != Kind.USER_BARRIER
                || entityData.get(BARRIER_ATTACHED)) {
            return false;
        }
        entityData.set(BARRIER_TERMINAL, terminal.immutable());
        entityData.set(BARRIER_ATTACHED, true);
        setPos(Vec3.atCenterOf(terminal));
        setDeltaMovement(Vec3.ZERO);
        return true;
    }

    public boolean isBarrierAttached() {
        return entityData.get(BARRIER_ATTACHED);
    }

    public BlockPos getBarrierOrigin() {
        return entityData.get(BARRIER_ORIGIN);
    }

    public boolean isBarrierAlerted() {
        return entityData.get(ALERT_TICKS) > 0;
    }

    // 1.16 SFUBarrierEntity.isGlowing: an alert raised in stopped time shows once time has resumed.
    public boolean showsBarrierAlert() {
        return isBarrierAlerted() && !isTimeStoppedAroundThis();
    }

    // 1.16 SFUBarrierEntity.isGlowing and getTeamColor: only its user sees
    // an alerted barrier outlined, in red.
    @Override
    public boolean isCurrentlyGlowing() {
        return level().isClientSide()
                && kind() == Kind.USER_BARRIER
                && showsBarrierAlert()
                && isBarrierOfClientPlayer()
                || super.isCurrentlyGlowing();
    }

    private boolean isBarrierOfClientPlayer() {
        Player player = ClientProxy.getClientPlayer();
        return player != null && isBarrierFor(player.getUUID());
    }

    @Override
    public int getTeamColor() {
        return kind() == Kind.USER_BARRIER && isBarrierAlerted()
                ? 0xFF0000
                : super.getTeamColor();
    }

    public boolean wasRipped() {
        return entityData.get(RIPPED);
    }

    public int barrierHealth() {
        return entityData.get(BARRIER_HP);
    }

    @Nullable
    public StoneFreeStandEntity getVisualOwner() {
        Entity entity = level().getEntity(
                entityData.get(VISUAL_OWNER_ID));
        if (entity instanceof StoneFreeStandEntity stand) {
            return stand;
        }
        LivingEntity owner = getOwner();
        return owner instanceof StoneFreeStandEntity stand
                ? stand
                : null;
    }

    @Nullable
    public LivingEntity getCurrentBarrierUser() {
        if (kind() != Kind.USER_BARRIER) {
            return null;
        }
        UUID userId = entityData.get(USER_UUID).orElse(null);
        if (userId == null) {
            return null;
        }
        LivingEntity user = level().getPlayerByUUID(userId);
        if (user == null) {
            // Non-player users retain the existing id route, but never the cached object.
            LivingEntity cached = StoneFreeServices.userOf(getOwner());
            Entity current = cached != null
                    ? level().getEntity(cached.getId())
                    : null;
            user = current instanceof LivingEntity living ? living : null;
        }
        return user != null
                        && userId.equals(user.getUUID())
                        && user.level() == level()
                        && level().getEntity(user.getId()) == user
                        && user.isAlive()
                        && !user.isRemoved()
                ? user
                : null;
    }

    public boolean isActiveUserStringFor(UUID user) {
        return kind().activeUserString
                && isOwnedBy(user)
                && isAlive()
                && !isRemoved();
    }

    public boolean isUserGrappleFor(UUID user) {
        return kind() == Kind.USER_GRAPPLE
                && isOwnedBy(user);
    }

    public boolean isBarrierFor(UUID user) {
        return kind() == Kind.USER_BARRIER
                && isOwnedBy(user);
    }

    private boolean isOwnedBy(UUID user) {
        return user != null
                && entityData.get(USER_UUID)
                        .filter(user::equals)
                        .isPresent();
    }

    /**
     * Thrown strings end at {@link #STRING_RANGE}; a placed barrier, both grapples, the Stand's own string and the
     * user's attack and bind strings (1.16 SF(U)StringEntity, which come back by themselves) have no such limit.
     */
    public boolean isRangeLimited() {
        return !kind().held && kind() != Kind.STAND_STRING
                && kind() != Kind.USER_STRING;
    }

    // The user's attack, bind, sweep, whip or pick string still out: the arms
    // that threw it stay with it (StoneFreeStringAbility.retractsStandAfterAction).
    public boolean isUserStringComingBackFor(UUID user) {
        return comesBackToUser()
                && isOwnedBy(user)
                && isAlive()
                && !isRemoved();
    }

    // 1.16 SFUStringEntity, SFUStringSweepEntity, SFUStringWhipEntity and
    // SFUStringSpikeEntity: the user's own strings that go out and come back.
    private boolean comesBackToUser() {
        return kind().isUserString() && !kind().held;
    }

    @Override
    public int ticksLifespan() {
        return switch (kind()) {
            case STAND_STRING -> standBindTicks() + holdingExtraTicks();
            case USER_STRING -> entityData.get(BINDING)
                    ? 24 + holdingExtraTicks()
                    : 16;
            case USER_SWEEP, USER_WHIP -> 10;
            // 1.16 SFExtendedPunchEntity: the extended punch's
            // getStandActionTicks(null, null), its standPerformDuration.
            case EXTENDED_PUNCH -> StoneFreeExtendedPunchAbility.PERFORM_TICKS;
            case USER_SPIKE -> 16;
            case STAND_GRAPPLE, USER_GRAPPLE ->
                    isAttachedToBlock()
                                    || getEntityAttachedTo() != null
                            ? Integer.MAX_VALUE
                            : 40;
            case USER_BARRIER -> Integer.MAX_VALUE;
        };
    }

    // 1.16 SF(U)StringEntity.ticksLifespan: a string that holds a catch
    // lives 25 ticks longer, the Stand's bind string as well.
    private int holdingExtraTicks() {
        return entityData.get(ATTACHED_ENTITY) >= 0
                ? HOLDING_EXTRA_TICKS
                : 0;
    }

    @Override
    protected float getBaseDamage() {
        return switch (kind()) {
            case USER_STRING ->
                    entityData.get(BINDING) ? 0.307F : 2.0F;
            case USER_SWEEP -> 1.0F;
            case USER_WHIP -> 5.6F;
            case EXTENDED_PUNCH -> 5.1429F;
            case USER_SPIKE -> 7.0F;
            case STAND_STRING, STAND_GRAPPLE, USER_GRAPPLE,
                    USER_BARRIER -> 0.0F;
        };
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return switch (kind()) {
            case EXTENDED_PUNCH -> 5.0F;
            case USER_SPIKE -> 1.0F;
            default -> 0.0F;
        };
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    // 1.16 SFStringEntity and SFUStringEntity only; the sweep, whip, spike
    // and extended punch kept the plain hurt.
    @Override
    protected boolean shouldHurtThroughInvulTicks() {
        return kind() == Kind.STAND_STRING || kind() == Kind.USER_STRING;
    }

    // 1.16 SFUStringEntity.knockbackMultiplier: the user's attack string's hit
    // pushed nothing. The sweep, whip and spike kept the full knockback.
    @Override
    protected float knockbackMultiplier() {
        return kind() == Kind.USER_STRING ? 0.0F : super.knockbackMultiplier();
    }

    @Override
    protected boolean shouldFollowOwner() {
        return false;
    }

    @Override
    public boolean isBodyPart() {
        return true;
    }

    @Override
    public boolean ownerInvisibility() {
        return false;
    }

    // 1.16 SFUBarrierEntity.canUpdate was always true: a placed string still notices who crosses it while
    // its user stands frozen.
    @Override
    public boolean canTickInStoppedTime() {
        return kind() == Kind.USER_BARRIER;
    }

    // 1.16 SFUBarrierEntity.canBeCollidedWith was false: a loose barrier end
    // at the user's eye must not eat the second press's block aim.
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected Vec3 getOwnerRelativeOffset() {
        return switch (kind()) {
            case USER_STRING, STAND_STRING -> DEFAULT_POS_OFFSET;
            case USER_SWEEP, USER_SPIKE, USER_GRAPPLE, USER_BARRIER ->
                    new Vec3(-0.22D, -0.75D, 0.25D);
            case USER_WHIP -> new Vec3(-0.3D, -0.75D, 0.25D);
            case STAND_GRAPPLE -> new Vec3(-0.3D, -0.2D, 0.4D);
            case EXTENDED_PUNCH -> new Vec3(-0.3D, -0.2D, 0.25D);
        };
    }

    @Override
    protected Vec3 getXRotOffset() {
        // Only the donor SF(U)StringEntity FRONT_OFFSET follows the owner's pitch.
        return switch (kind()) {
            case USER_STRING -> new Vec3(-0.22D, -0.47D, 0.25D);
            case STAND_STRING -> new Vec3(-0.3D, 0.1D, 0.4D);
            default -> Vec3.ZERO;
        };
    }

    /**
     * Donor user strings are shot by the user, not by the Stand: they start at the user's hand and
     * follow the user's own look. Keeping the Stand controller as the owner (stamina, damage
     * attribution) but aiming from the user stops a Stand idling beside the user from shifting the
     * string's origin and path onto nearby blocks.
     */
    private LivingEntity aimSource() {
        LivingEntity user = kind().isUserString() ? resolveUser() : null;
        return user != null ? user : getOwner();
    }

    @Override
    public Vec3 getOriginPoint(float partialTick) {
        if (kind() == Kind.USER_BARRIER) {
            return Vec3.atCenterOf(entityData.get(BARRIER_ORIGIN));
        }
        LivingEntity user = kind().isUserString() ? resolveUser() : null;
        if (user != null) {
            return getPos(
                    user,
                    partialTick,
                    Mth.lerp(partialTick, user.yBodyRotO, user.yBodyRot),
                    Mth.lerp(partialTick, user.xRotO, user.getXRot()));
        }
        return super.getOriginPoint(partialTick);
    }

    @Override
    public void tick() {
        clearDraggedMotion();
        recoverPersistentOwner();
        resolveAttachedEntity();
        if (kind() == Kind.USER_BARRIER && isTimeStoppedAroundThis()) {
            tickBarrierInStoppedTime();
            return;
        }
        if (kind() == Kind.USER_BARRIER && isBarrierAlerted()) {
            // 1.16 SFUBarrierEntity.tick: while its alert counts, a barrier
            // neither moves nor looks for contact.
            if (!level().isClientSide() && tickBarrierAlert()) {
                return;
            }
        }
        else {
            super.tick();
        }
        drainUserGrappleStamina();
        if (!isAlive()) {
            return;
        }

        if (!level().isClientSide()) {
            registerActiveUserString();
            LivingEntity user = resolveUser();
            if (user == null
                    || !StoneFreeServices.isStoneFreeUser(user)) {
                if (user != null || kind() != Kind.USER_BARRIER) {
                    // 1.16 SFUBarrierEntity.tick: every barrier of a user who
                    // lost Stone Free hurts that user as it goes.
                    if (user != null && kind() == Kind.USER_BARRIER) {
                        DamageUtil.hurtThroughInvulTicks(
                                user, user.damageSources().generic(), 0.12F);
                    }
                    discard();
                }
                return;
            }
            StoneFreeStandEntity controller =
                    StoneFreeServices.controller(user);
            if (controller != null) {
                entityData.set(
                        VISUAL_OWNER_ID, controller.getId());
                if (kind() != Kind.USER_BARRIER
                        && getOwner() != controller) {
                    setOwner(controller);
                }
            }

            if (kind() != Kind.USER_BARRIER) {
                if (kind() == Kind.USER_GRAPPLE
                        && (isBeyondTracking(user)
                                || hasLeftEntityTicking())) {
                    discard();
                    return;
                }
                tickAttachment(user);
                if (isRangeLimited()
                        && getOriginPoint(1.0F)
                                .distanceToSqr(position())
                                > STRING_RANGE * STRING_RANGE) {
                    discard();
                }
            }
        }
        else if (kind() == Kind.STAND_GRAPPLE) {
            dragStandGrappleCatch();
        }
        else if (entityData.get(CAPTURE_PULLING)) {
            dragCaptureCatch();
        }
    }

    // As the core's HGBarrierEntity asks it.
    private boolean isTimeStoppedAroundThis() {
        if (level().isClientSide()) {
            return TimeStopState.getClientDisplayInstance(
                    new ChunkPos(blockPosition())).isPresent();
        }
        return level() instanceof ServerLevel serverLevel
                && serverLevel.hasData(ModDataAttachmentTypes.TIME_STOP.get())
                && serverLevel.getData(ModDataAttachmentTypes.TIME_STOP.get())
                        .isTimeStopped(this);
    }

    /**
     * 1.16 SFUBarrierEntity.tick with time stopped around the string: the first entity on its ray starts the alert,
     * which waits for time to resume. No point is taken and the entity is not stopped. The user's own Stone Free
     * passes, as it does in normal time.
     */
    private void tickBarrierInStoppedTime() {
        if (level().isClientSide() || isBarrierAlerted()) {
            return;
        }
        StoneFreeStandEntity ownStand =
                StoneFreeServices.controller(resolveUser());
        for (HitResult hit : rayTrace()) {
            if (hit instanceof EntityHitResult entityHit
                    && entityHit.getEntity() != ownStand
                    && !EventHooks.onProjectileImpact(this, hit)) {
                entityData.set(ALERT_TICKS, BARRIER_ALERT_TICKS);
                break;
            }
        }
    }

    // 1.16 SFUGrapplingStringEntity.tick: the user's string takes 1 stamina
    // every tick it is out, on both sides, and stays out on empty stamina.
    private void drainUserGrappleStamina() {
        if (kind() != Kind.USER_GRAPPLE || getOwner() == null) {
            return;
        }
        StandPower power = userStandPower.get();
        if (power != null) {
            power.consumeStamina(USER_GRAPPLE_STAMINA_PER_TICK, true);
        }
    }

    // 1.16 SFUGrapplingStringEntity.tick: more than 24 blocks from its
    // player, a grapple ends once it is level with the edge of the
    // server's view distance. That edge was also where entities stopped
    // ticking; 1.21 ticks them inside the simulation distance, so the
    // nearer of the two edges counts.
    private boolean isBeyondTracking(LivingEntity user) {
        if (!(user instanceof Player)
                || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        double distanceSqr = distanceToSqr(user);
        if (distanceSqr <= 576.0D) {
            return false;
        }
        double height = getY() - user.getY();
        PlayerList players = serverLevel.getServer().getPlayerList();
        int warningDistance = Math.min(
                players.getViewDistance(),
                players.getSimulationDistance()) * 16 - 4;
        return distanceSqr - height * height
                > (double) warningDistance * warningDistance;
    }

    // A string whose end is outside the entity-ticking area is not ticked
    // again: it would neither run out nor pull, and never be removed.
    private boolean hasLeftEntityTicking() {
        return level() instanceof ServerLevel serverLevel
                && !serverLevel.isPositionEntityTicking(blockPosition());
    }

    private void recoverPersistentOwner() {
        if (level().isClientSide()
                || !(kind().held
                        || kind() == Kind.USER_BARRIER)) {
            return;
        }
        if (kind() == Kind.USER_BARRIER) {
            // Saved barriers may still name the old summoned controller.
            LivingEntity user = resolveUser();
            if (getOwner() != user) {
                setOwner(user);
            }
            return;
        }
        LivingEntity owner = getOwner();
        if (owner != null && owner.isAlive()) {
            return;
        }
        LivingEntity user = resolveUser();
        StoneFreeStandEntity controller =
                StoneFreeServices.ensureArmsController(user);
        if (controller != null) {
            setOwner(controller);
            entityData.set(
                    VISUAL_OWNER_ID, controller.getId());
        }
        else {
            setOwner(null);
        }
    }

    private void registerActiveUserString() {
        if (activeStringRegistered
                || !kind().activeUserString) {
            return;
        }
        LivingEntity user = resolveUser();
        if (user != null) {
            StoneFreeState.get(user)
                    .addActiveUserString(user);
            activeStringRegistered = true;
        }
    }

    private void tickAttachment(LivingEntity user) {
        LivingEntity attached = getEntityAttachedTo();
        if (entityData.get(ATTACHED_ENTITY) >= 0
                && (attached == null || !attached.isAlive())) {
            discard();
            return;
        }
        if (attached != null) {
            if (kind() == Kind.USER_GRAPPLE) {
                // 1.16 SFUGrapplingStringEntity: the user is pulled to the
                // string's end on its catch, at the catch's middle; the
                // string also lets go once the two stand within two blocks.
                if (completeUserGrapple(
                        user.getRootVehicle(),
                        position().subtract(user.position()),
                        ENTITY_GRAPPLE_PULL_SPEED)) {
                    return;
                }
                if (attached.position().distanceToSqr(user.position())
                        < STOP_DISTANCE * STOP_DISTANCE) {
                    discard();
                    return;
                }
            }
            else if (kind() == Kind.STAND_GRAPPLE) {
                dragStandGrappleCatch();
            }
            else if (entityData.get(BINDING)) {
                if (entityData.get(CAPTURE_PULLING)) {
                    dragCaptureCatch();
                }
                // The user's bind and a capture string that holds its
                // catch only stay on it (1.16 SF(U)StringEntity).
                // The port's stun and pull (D-rotp-stone-free-10) end with
                // the bind's own ticks; the string stays on for the extra.
                else if (kind() == Kind.STAND_STRING && !capture
                        && tickCount <= standBindTicks()) {
                    stunAndPullStandBindCatch(attached);
                }
            }
        }
        else if (kind() == Kind.USER_GRAPPLE
                && isAttachedToBlock()) {
            completeUserGrapple(
                    user.getRootVehicle(),
                    Vec3.atCenterOf(getBlockAttachedTo())
                            .subtract(user.position()),
                    GRAPPLE_PULL_SPEED);
        }
    }

    // Not in 1.16, whose Stand bind string only stayed on its catch.
    private void stunAndPullStandBindCatch(LivingEntity attached) {
        attached.addEffect(new MobEffectInstance(
                ModStatusEffects.STUN,
                5,
                0,
                false,
                false,
                false));
        LivingEntity owner = getOwner();
        if (owner != null
                && attached.distanceToSqr(owner) > 4.0D) {
            pullEntity(
                    attached,
                    owner.position()
                            .subtract(attached.position()),
                    0.35D);
        }
    }

    /**
     * 1.16 SFStringEntity.tick for a capture string, on both sides as there: the catch is dragged to the Stand at
     * 1.0 a tick. Within reach it is immobilised for the rest of the string's life and no longer dragged. Reach is
     * 4 blocks from a Stand that has a user; for any other owner the donor measures that distance as 0, so the
     * catch is held where it is. The effect is given by the server only: a client's own copy would not be synced.
     */
    private void dragCaptureCatch() {
        LivingEntity attached = getEntityAttachedTo();
        LivingEntity owner = getOwner();
        if (attached == null || !attached.isAlive() || owner == null) {
            return;
        }
        Vec3 toOwner = owner.position().subtract(attached.position());
        boolean standWithUser = owner instanceof StandEntity stand
                && stand.getUser() != null;
        if (!standWithUser
                || toOwner.lengthSqr()
                        < CAPTURE_HOLD_DISTANCE * CAPTURE_HOLD_DISTANCE) {
            if (!level().isClientSide()) {
                attached.addEffect(new MobEffectInstance(
                        ModStatusEffects.IMMOBILIZE,
                        ticksLifespan() - tickCount));
                entityData.set(CAPTURE_PULLING, false);
            }
            return;
        }
        dragTarget(
                attached,
                toOwner.normalize().scale(CAPTURE_PULL_SPEED));
        attached.fallDistance = 0.0F;
    }

    /**
     * 1.16 SFUBarrierEntity.tick, the counting alert: a ripped barrier hurts its user once, and breaks with its
     * sound only when the alert runs out.
     *
     * @return whether the alert ran out on this tick, which then does nothing else
     */
    private boolean tickBarrierAlert() {
        int alert = entityData.get(ALERT_TICKS) - 1;
        entityData.set(ALERT_TICKS, alert);
        if (alert <= 0) {
            if (wasRipped()) {
                playSound(
                        StoneFreeAddon.STONE_FREE_BARRIER_RIPPED.get(),
                        1.0F,
                        1.0F);
                discard();
            }
            return true;
        }
        if (wasRipped() && !rippedHurtUser) {
            rippedHurtUser = true;
            LivingEntity user = resolveUser();
            if (user != null) {
                DamageUtil.hurtThroughInvulTicks(
                        user, user.damageSources().generic(), 0.12F);
            }
        }
        return false;
    }

    /**
     * 1.16 SFUBarrierEntity.onHitEntity: whatever the placed string's ray meets is stopped and starts the 40 tick
     * alert; only a Stand body takes one of the three points, and none while an alert counts. The user's own
     * Stone Free passes without either.
     */
    private void onBarrierContact(Entity target) {
        LivingEntity user = resolveUser();
        if (!isBarrierAttached() || user == null) {
            return;
        }
        target.setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()
                || target == StoneFreeServices.controller(user)
                || isBarrierAlerted()) {
            return;
        }
        if (isStandBody(target)) {
            int health = Math.max(0, entityData.get(BARRIER_HP) - 1);
            entityData.set(BARRIER_HP, health);
            if (health == 0) {
                entityData.set(RIPPED, true);
            }
        }
        entityData.set(ALERT_TICKS, BARRIER_ALERT_TICKS);
    }

    // 1.16 SFUBarrierEntity.isStand
    private static boolean isStandBody(Entity entity) {
        return entity instanceof LivingEntity living
                && (living instanceof StandEntity
                        || living.hasEffect(
                                ModStatusEffects.INTEGRATED_STAND));
    }

    /**
     * 1.16 SFUGrapplingStringEntity.moveToBlockAttached and moveToEntityAttached: more than two blocks from the
     * string's end the user's motion is set outright; within two blocks the string lets go and leaves that motion
     * as it is. The user is never one of the dragged entities, whose motion the string clears.
     */
    private boolean completeUserGrapple(
            Entity target, Vec3 direction, double speed) {
        if (direction.lengthSqr() > STOP_DISTANCE * STOP_DISTANCE) {
            target.setDeltaMovement(
                    direction.normalize().scale(speed));
            target.fallDistance = 0.0F;
            target.hurtMarked = true;
            return false;
        }
        discard();
        return true;
    }

    private boolean pullEntity(
            Entity target, Vec3 direction, double speed) {
        if (direction.lengthSqr()
                < STOP_DISTANCE * STOP_DISTANCE) {
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
            return true;
        }
        target.setDeltaMovement(
                direction.normalize().scale(speed));
        target.fallDistance = 0.0F;
        target.hurtMarked = true;
        draggedEntities.add(target);
        return false;
    }

    /**
     * 1.16 SFGrapplingStringEntity.tick, on both sides as there (the catch's own client moves it too): the Stand
     * pulls its catch at 1.0 a tick and lets go of it within two blocks.
     */
    private void dragStandGrappleCatch() {
        LivingEntity attached = getEntityAttachedTo();
        LivingEntity owner = getOwner();
        if (attached == null || !attached.isAlive() || owner == null) {
            return;
        }
        Vec3 toStand = owner.position().subtract(attached.position());
        if (toStand.lengthSqr() < STOP_DISTANCE * STOP_DISTANCE) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        dragTarget(
                attached,
                toStand.normalize().scale(STAND_GRAPPLE_PULL_SPEED));
        attached.fallDistance = 0.0F;
    }

    // 1.16 OwnerBoundProjectileEntity.dragTarget: what moves is the catch's
    // root vehicle, and a Stand that has a user is dragged twice.
    private void dragTarget(Entity target, Vec3 pull) {
        Entity dragged = target.getRootVehicle();
        dragEntity(dragged, pull);
        if (dragged instanceof StandEntity stand
                && stand.getUser() != null) {
            dragEntity(dragged, pull);
        }
    }

    // 1.16 doDragEntity: an effect that resets its bearer's motion every
    // tick would undo a pull, so that bearer is moved outright.
    private void dragEntity(Entity entity, Vec3 pull) {
        if (entity instanceof LivingEntity living) {
            for (Holder<MobEffect> effect
                    : living.getActiveEffectsMap().keySet()) {
                if (effect.value() instanceof ImmobilizeEffect immobilize
                        && immobilize.resetsDeltaMovement()) {
                    entity.move(MoverType.PLAYER, pull);
                    return;
                }
            }
        }
        entity.setDeltaMovement(pull);
        entity.hurtMarked = true;
        draggedEntities.add(entity);
    }

    private void clearDraggedMotion() {
        draggedEntities.forEach(entity -> {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hurtMarked = true;
        });
        draggedEntities.clear();
    }

    @Override
    protected void moveProjectile() {
        if (kind() == Kind.USER_BARRIER) {
            Vec3 endpoint = isBarrierAttached()
                    ? Vec3.atCenterOf(
                            entityData.get(BARRIER_TERMINAL))
                    : dynamicUserEndpoint();
            setStaticPosition(endpoint);
            return;
        }

        LivingEntity attached = getEntityAttachedTo();
        if (attached != null) {
            setStaticPosition(new Vec3(
                    attached.getX(),
                    attached.getY(0.5D),
                    attached.getZ()));
            return;
        }
        if (isAttachedToBlock()) {
            setStaticPosition(
                    Vec3.atCenterOf(getBlockAttachedTo()));
            return;
        }
        if (kind() == Kind.STAND_GRAPPLE
                || kind() == Kind.USER_GRAPPLE) {
            moveGrapple();
            return;
        }
        if (kind() == Kind.STAND_STRING || kind() == Kind.USER_STRING
                || kind() == Kind.USER_SWEEP || kind() == Kind.USER_WHIP
                || kind() == Kind.USER_SPIKE || kind() == Kind.EXTENDED_PUNCH) {
            moveBoundToOwner();
            return;
        }
        super.moveProjectile();
    }

    /**
     * 1.16 OwnerBoundProjectileEntity.moveBoundToOwner with SF(U)StringEntity.movementSpeed, 16 / lifespan: the
     * string goes out along its owner's look (a capture string at its angle from it) at that speed times its speed
     * factor until half its lifespan, then comes back as fast and is gone once back, so it reaches 8 blocks times
     * the speed factor. A block it meets turns it back at once (afterBlockHit). The user's attack and bind strings
     * (SFUStringEntity) belonged to the user, from whose hand and look they went, and so did the sweep and the whip
     * (SFUStringSweepEntity, SFUStringWhipEntity: 1.5 a tick over 10 ticks, 7.5 blocks out while they swing) and the
     * pick's spike (SFUStringSpikeEntity: 1.5 a tick over 16 ticks, 9 blocks out, held there 4 ticks). The Stand's
     * extended punch (SFExtendedPunchEntity) goes 1.5 a tick times the Stand's speed factor over 10 ticks, 3 steps
     * out, held there 4 ticks.
     */
    private void moveBoundToOwner() {
        LivingEntity owner = kind().isUserString() ? aimSource() : getOwner();
        if (owner == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        setRot(owner.getYRot(), owner.getXRot());
        float speed = boundMovementSpeed();
        int fullLength = boundTimeAtFullLength();
        double factor = boundSpeedFactor();
        boolean back = entityData.get(GRAPPLE_RETRACTING);
        // 1.16 updateMotionFlags with the same speed out and back: forward
        // until the mark, held at full length for timeAtFullLength, then
        // retracting. Its maxDistance() widened the float to double, so the
        // mark divides in double (18 ticks: 8, where a float division gives 9).
        double maxDistance = speed * speed * (ticksLifespan() - fullLength) / (speed + speed);
        int mark = (int) (maxDistance / speed);
        double distance = back
                ? (float) (extensionDistance - speed * factor)
                : tickCount <= mark
                        ? (float) (extensionDistance + speed * factor)
                        : (float) extensionDistance;
        if (!back && tickCount >= mark + fullLength) {
            entityData.set(GRAPPLE_RETRACTING, true);
            back = true;
        }
        if (back && distance <= 0.0D) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        extensionDistance = distance;
        setStaticPosition(boundOrigin(owner).add(
                Vec3.directionFromRotation(
                                owner.getXRot() + boundXRotOffset(),
                                owner.getYRot() + boundYRotOffset())
                        .scale(distance)));
    }

    // 1.16 movementSpeed() (and retractSpeed()): SFUStringSweepEntity,
    // SFUStringWhipEntity, SFUStringSpikeEntity and SFExtendedPunchEntity
    // 1.5, SF(U)StringEntity 16 / lifespan.
    private float boundMovementSpeed() {
        return kind() == Kind.USER_SWEEP || kind() == Kind.USER_WHIP
                        || kind() == Kind.USER_SPIKE
                        || kind() == Kind.EXTENDED_PUNCH
                ? 1.5F
                : 16.0F / ticksLifespan();
    }

    // 1.16 SFUStringSpikeEntity and SFExtendedPunchEntity.timeAtFullLength;
    // 0 for the others.
    private int boundTimeAtFullLength() {
        return kind() == Kind.USER_SPIKE || kind() == Kind.EXTENDED_PUNCH
                ? 4
                : 0;
    }

    // 1.16 SFUStringSweepEntity.updateDistance turned the sweep's yaw from
    // 67.5 to -67.5 and SFUStringWhipEntity's the whip's pitch from -87.5 to
    // 47.5, by 135 / lifespan on each tick before it moved.
    private float boundYRotOffset() {
        return kind() == Kind.USER_SWEEP
                ? Math.max(67.5F - 135.0F / ticksLifespan() * tickCount, -67.5F)
                : entityData.get(Y_ROT_OFFSET);
    }

    private float boundXRotOffset() {
        return kind() == Kind.USER_WHIP
                ? Math.min(-87.5F + 135.0F / ticksLifespan() * tickCount, 47.5F)
                : entityData.get(X_ROT_OFFSET);
    }

    // 1.16 ownerPosition(1.0F, false) of the string's owner.
    private Vec3 boundOrigin(LivingEntity owner) {
        return owner == getOwner()
                ? ownerPosition(1.0F, false)
                : getPos(
                        owner,
                        1.0F,
                        Mth.lerp(1.0F, owner.yRotO, owner.getYRot()),
                        Mth.lerp(1.0F, owner.xRotO, owner.getXRot()));
    }

    // 1.16 SFU*Entity strings were added with world.addFreshEntity, not by a
    // Stand: the user's strings move with no Stand speed factor.
    private double boundSpeedFactor() {
        return kind().isUserString() ? 1.0D : speedFactor;
    }

    private void moveGrapple() {
        LivingEntity owner = getOwner();
        if (owner == null) {
            return;
        }
        // 1.16 SF(U)GrapplingStringEntity: 4 blocks a tick with no limit
        // on its length; only the 40 tick lifespan ends a loose grapple.
        // One drawing back from a block ends when it is back at the hand.
        if (entityData.get(GRAPPLE_RETRACTING)) {
            extensionDistance -= GRAPPLE_FLIGHT_SPEED;
            if (extensionDistance <= 0.0D) {
                if (!level().isClientSide()) {
                    discard();
                }
                return;
            }
        }
        else {
            extensionDistance += GRAPPLE_FLIGHT_SPEED;
        }
        LivingEntity aim = aimSource();
        setStaticPosition(
                getOriginPoint(1.0F).add(
                        Vec3.directionFromRotation(
                                        aim.getXRot(),
                                        aim.getYRot())
                                .scale(extensionDistance)));
    }

    private Vec3 dynamicUserEndpoint() {
        LivingEntity user = resolveUser();
        return user != null
                ? user.getEyePosition().add(0.15D, -1.4D, 0.0D)
                : position();
    }

    private void setStaticPosition(Vec3 position) {
        Vec3 previous = position();
        setDeltaMovement(position.subtract(previous));
        xo = previous.x;
        yo = previous.y;
        zo = previous.z;
        xOld = previous.x;
        yOld = previous.y;
        zOld = previous.z;
        setPos(position);
    }

    // 1.16 ProjectileEntity.onHit with SFUBarrierEntity's two overrides: a
    // barrier does nothing to the blocks on its line, and whatever else
    // is on it only trips the placed barrier.
    @Override
    protected void onHit(HitResult result) {
        if (kind() != Kind.USER_BARRIER) {
            super.onHit(result);
        }
        else if (result instanceof EntityHitResult hit) {
            onBarrierContact(hit.getEntity());
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (kind() == Kind.USER_BARRIER) {
            // 1.16 SFUBarrierEntity kept the base rule: anything a
            // projectile can hit, living or not, except its user.
            return super.canHitEntity(entity);
        }
        if (getEntityAttachedTo() != null
                || !(entity instanceof LivingEntity)
                || !super.canHitEntity(entity)) {
            return false;
        }
        LivingEntity owner = getOwner();
        if (entity == owner) {
            return false;
        }
        if (owner instanceof StandEntity stand) {
            return entity != stand.getUser()
                    && stand.canAttackEntity(entity);
        }
        return true;
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        // 1.16 SFExtendedPunchEntity.hurtTarget: the light punch sound for
        // every mob it tried to hurt, before the hurt
        if (kind() == Kind.EXTENDED_PUNCH) {
            playSound(
                    StoneFreeAddon.STONE_FREE_PUNCH_LIGHT.get(),
                    1.0F,
                    1.0F);
        }
        boolean grapple = kind() == Kind.STAND_GRAPPLE
                || kind() == Kind.USER_GRAPPLE;
        if (grapple
                && entityData.get(BINDING)
                && target instanceof LivingEntity living
                && !JojoModUtil.isTargetBlocking(living)) {
            if (!level().isClientSide()) {
                attachToEntity(living);
                playSound(
                        StoneFreeAddon
                                .STONE_FREE_GRAPPLE_CATCH.get(),
                        1.0F,
                        1.0F);
            }
            // 1.16 hurtTarget returned true for a catch, and DamagingEntity
            // .onHitEntity then marked it as hurt by the user of the Stand
            // that owned the string. The user's own string belonged to the
            // user, not to a Stand, and marked nothing.
            return kind() == Kind.STAND_GRAPPLE;
        }
        // 1.16 SF(U)StringEntity.hurtTarget: a bind's first contact catches
        // and is never a hit, whether or not its target can be hurt. As
        // with the grapples, only the Stand's own string marks its catch.
        // The ray returns every target on the string in one tick; it caught
        // only while nothing was attached, and 1.16 refused the rest through
        // dealtDamage, which a catch sets.
        if (!grapple
                && isBind()
                && target instanceof LivingEntity living) {
            if (getEntityAttachedTo() != null) {
                return false;
            }
            if (!level().isClientSide()) {
                catchWithBind(living);
            }
            return kind() == Kind.STAND_STRING;
        }
        // 1.16 SFUStringEntity.hurtTarget and SFUStringWhipEntity.hurtTarget:
        // the user's attack string and whip hurt once a throw.
        if ((kind() == Kind.USER_STRING || kind() == Kind.USER_WHIP)
                && dealtDamage) {
            return false;
        }
        // 1.16 SF(U)GrapplingStringEntity.hurtTarget: a grapple catches
        // or does nothing; it never strikes what it meets.
        return !grapple && super.hurtTarget(target, owner);
    }

    // 1.16 SFUStringEntity.afterEntityHit: the user's attack string that hurt
    // what it met turns back at once. SFUStringWhipEntity.afterEntityHit: the
    // whip that hurt swings on but hurts nothing more.
    // SFExtendedPunchEntity.afterEntityHit: the punch turns back from any mob
    // it met, hurt or not.
    @Override
    protected void afterEntityHit(EntityHitResult hit, boolean entityHurt) {
        if (kind() == Kind.EXTENDED_PUNCH) {
            entityData.set(GRAPPLE_RETRACTING, true);
        }
        else if (entityHurt && kind() == Kind.USER_STRING
                && !entityData.get(BINDING)) {
            dealtDamage = true;
            entityData.set(GRAPPLE_RETRACTING, true);
        }
        else if (entityHurt && kind() == Kind.USER_WHIP) {
            dealtDamage = true;
        }
    }

    private boolean isBind() {
        return kind() == Kind.STAND_STRING
                || (kind() == Kind.USER_STRING && entityData.get(BINDING));
    }

    private void catchWithBind(LivingEntity target) {
        attachToEntity(target);
        // 1.16 SFUStringEntity.afterEntityHit: the user's bind holds its
        // catch for the rest of the string's life.
        if (kind() == Kind.USER_STRING) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.IMMOBILIZE,
                    ticksLifespan() - tickCount));
        }
        playSound(
                StoneFreeAddon.STONE_FREE_GRAPPLE_CATCH.get(),
                1.0F,
                1.0F);
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hit, boolean blockDestroyed) {
        if (level().isClientSide()) {
            return;
        }
        // 1.16 OwnerBoundProjectileEntity.afterBlockHit, which the Stand's
        // string and grapple and the user's SFUStringEntity, sweep, whip and
        // spike kept: they draw back from a block they met.
        if (kind() == Kind.STAND_STRING
                || kind() == Kind.STAND_GRAPPLE
                || kind() == Kind.USER_STRING
                || kind() == Kind.USER_SWEEP
                || kind() == Kind.USER_WHIP
                || kind() == Kind.USER_SPIKE) {
            if (!blockDestroyed) {
                entityData.set(GRAPPLE_RETRACTING, true);
            }
        }
        // 1.16 SFExtendedPunchEntity.afterBlockHit: the light punch sound and
        // the punch turns back from a block even when it broke it.
        else if (kind() == Kind.EXTENDED_PUNCH) {
            playSound(
                    StoneFreeAddon.STONE_FREE_PUNCH_LIGHT.get(),
                    1.0F,
                    1.0F);
            entityData.set(GRAPPLE_RETRACTING, true);
        }
        else if (kind() == Kind.USER_GRAPPLE
                && !entityData.get(BINDING)
                && !blockDestroyed
                && !isAttachedToBlock()) {
            attachToBlock(hit.getBlockPos());
            playSound(
                    StoneFreeAddon
                            .STONE_FREE_GRAPPLE_CATCH.get(),
                    1.0F,
                    1.0F);
        }
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, @Nullable HitResult target) {
        // 1.16 OwnerBoundProjectileEntity.breakProjectile did nothing: the
        // Stand's string and extended punch and the user's attack, bind,
        // sweep, whip and spike strings are not broken by what they meet.
        if (level().isClientSide()
                || kind() == Kind.USER_BARRIER
                || kind() == Kind.STAND_STRING
                || kind() == Kind.USER_STRING
                || kind() == Kind.USER_SWEEP
                || kind() == Kind.USER_WHIP
                || kind() == Kind.USER_SPIKE
                || kind() == Kind.EXTENDED_PUNCH
                || getEntityAttachedTo() != null
                || isAttachedToBlock()) {
            return;
        }
        if (kind() == Kind.STAND_GRAPPLE
                || kind() == Kind.USER_GRAPPLE) {
            if (targetType == TargetType.EMPTY) {
                discard();
            }
            return;
        }
        discard();
    }

    @Nullable
    public LivingEntity getEntityAttachedTo() {
        int id = entityData.get(ATTACHED_ENTITY);
        Entity entity = id >= 0 ? level().getEntity(id) : null;
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    private void attachToEntity(LivingEntity target) {
        attachedEntityUuid = null;
        entityData.set(ATTACHED_ENTITY, target.getId());
        setStaticPosition(new Vec3(
                target.getX(),
                target.getY(0.5D),
                target.getZ()));
    }

    private void resolveAttachedEntity() {
        if (!level().isClientSide()
                && attachedEntityUuid != null
                && entityData.get(ATTACHED_ENTITY) < 0
                && level() instanceof ServerLevel serverLevel) {
            Entity entity =
                    serverLevel.getEntity(attachedEntityUuid);
            if (entity instanceof LivingEntity living) {
                attachToEntity(living);
            }
        }
    }

    public boolean isAttachedToBlock() {
        return entityData.get(ATTACHED_TO_BLOCK);
    }

    private void attachToBlock(BlockPos block) {
        entityData.set(BLOCK_ATTACHED_TO, block.immutable());
        entityData.set(ATTACHED_TO_BLOCK, true);
        setStaticPosition(Vec3.atCenterOf(block));
    }

    private BlockPos getBlockAttachedTo() {
        return entityData.get(BLOCK_ATTACHED_TO);
    }

    @Nullable
    private LivingEntity resolveUser() {
        LivingEntity owner = StoneFreeServices.userOf(getOwner());
        if (owner != null) {
            return owner;
        }
        if (!(level() instanceof ServerLevel level)) {
            return null;
        }
        Optional<UUID> userId = entityData.get(USER_UUID);
        if (userId.isEmpty()) {
            return null;
        }
        Entity entity = level.getEntity(userId.get());
        if (entity == null) {
            entity = level.getPlayerByUUID(userId.get());
        }
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    @Override
    public void remove(RemovalReason reason) {
        clearDraggedMotion();
        if (!level().isClientSide()
                && reason.shouldDestroy()) {
            LivingEntity user = resolveUser();
            if (user != null) {
                StoneFreeState state = StoneFreeState.get(user);
                if (activeStringRegistered
                        && kind().activeUserString) {
                    state.removeActiveUserString(user);
                }
                if (kind() == Kind.USER_GRAPPLE) {
                    state.clearUserGrapple(user, getUUID());
                    retractIdleArms(user);
                }
                if (comesBackToUser()) {
                    retractIdleArms(user);
                }
                if (kind() == Kind.USER_BARRIER) {
                    state.clearOpenBarrier(user, getUUID());
                    if (isBarrierAttached()) {
                        state.releaseString(user, true);
                    }
                }
            }
        }
        super.remove(reason);
    }

    // The arms that threw the user's grapple or a string that comes back stay
    // out for it (StoneFreeStringAbility.retractsStandAfterAction) and go with it.
    private static void retractIdleArms(LivingEntity user) {
        StoneFreeStandEntity arms = StoneFreeServices.controller(user);
        if (arms != null) {
            arms.leaveAfterUserString();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", kind().ordinal());
        entityData.get(USER_UUID)
                .ifPresent(value -> tag.putUUID("StoneFreeUser", value));
        tag.putBoolean("Binding", entityData.get(BINDING));
        tag.putBoolean("Capture", capture);
        tag.putBoolean(
                "CapturePulling", entityData.get(CAPTURE_PULLING));
        LivingEntity attached = getEntityAttachedTo();
        UUID attachedId = attached != null
                ? attached.getUUID()
                : attachedEntityUuid;
        if (attachedId != null) {
            tag.putUUID("AttachedEntity", attachedId);
        }
        tag.putBoolean(
                "AttachedToBlock",
                entityData.get(ATTACHED_TO_BLOCK));
        tag.putLong(
                "AttachedBlock",
                entityData.get(BLOCK_ATTACHED_TO).asLong());
        tag.putLong(
                "BarrierOrigin",
                entityData.get(BARRIER_ORIGIN).asLong());
        tag.putLong(
                "BarrierTerminal",
                entityData.get(BARRIER_TERMINAL).asLong());
        tag.putBoolean(
                "BarrierAttached",
                entityData.get(BARRIER_ATTACHED));
        tag.putInt("BarrierHealth", entityData.get(BARRIER_HP));
        tag.putInt("AlertTicks", entityData.get(ALERT_TICKS));
        tag.putBoolean("Ripped", entityData.get(RIPPED));
        tag.putBoolean("RippedHurtUser", rippedHurtUser);
        tag.putInt("StandBindTicks", standBindTicks());
        tag.putDouble("ExtensionDistance", extensionDistance);
        tag.putBoolean(
                "Retracting",
                entityData.get(GRAPPLE_RETRACTING));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                KIND,
                Mth.clamp(
                        tag.getInt("Kind"),
                        0,
                        Kind.values().length - 1));
        entityData.set(
                USER_UUID,
                tag.hasUUID("StoneFreeUser")
                        ? Optional.of(
                                tag.getUUID("StoneFreeUser"))
                        : Optional.empty());
        // A Stand string saved before the Stand's non-binding form was removed loads as the ordinary one.
        entityData.set(
                BINDING,
                tag.getBoolean("Binding") || kind() == Kind.STAND_STRING);
        capture = tag.getBoolean("Capture");
        entityData.set(
                CAPTURE_PULLING, tag.getBoolean("CapturePulling"));
        entityData.set(ATTACHED_ENTITY, -1);
        attachedEntityUuid = tag.hasUUID("AttachedEntity")
                ? tag.getUUID("AttachedEntity")
                : null;
        entityData.set(
                ATTACHED_TO_BLOCK,
                tag.getBoolean("AttachedToBlock"));
        entityData.set(
                BLOCK_ATTACHED_TO,
                BlockPos.of(tag.getLong("AttachedBlock")));
        entityData.set(
                BARRIER_ORIGIN,
                BlockPos.of(tag.getLong("BarrierOrigin")));
        entityData.set(
                BARRIER_TERMINAL,
                BlockPos.of(tag.getLong("BarrierTerminal")));
        entityData.set(
                BARRIER_ATTACHED,
                tag.getBoolean("BarrierAttached"));
        entityData.set(
                BARRIER_HP,
                Mth.clamp(
                        tag.getInt("BarrierHealth"),
                        0,
                        BARRIER_HEALTH));
        entityData.set(
                ALERT_TICKS,
                Math.max(0, tag.getInt("AlertTicks")));
        entityData.set(RIPPED, tag.getBoolean("Ripped"));
        rippedHurtUser = tag.getBoolean("RippedHurtUser");
        entityData.set(STAND_BIND_TICKS, tag.contains("StandBindTicks")
                ? Math.max(1, tag.getInt("StandBindTicks"))
                : STAND_STRING_TICKS);
        extensionDistance = Math.max(
                0.0D, tag.getDouble("ExtensionDistance"));
        entityData.set(
                GRAPPLE_RETRACTING, tag.getBoolean("Retracting"));
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
        builder.define(USER_UUID, Optional.empty());
        builder.define(VISUAL_OWNER_ID, -1);
        builder.define(BINDING, false);
        builder.define(ATTACHED_ENTITY, -1);
        builder.define(ATTACHED_TO_BLOCK, false);
        builder.define(BLOCK_ATTACHED_TO, BlockPos.ZERO);
        builder.define(BARRIER_ORIGIN, BlockPos.ZERO);
        builder.define(BARRIER_TERMINAL, BlockPos.ZERO);
        builder.define(BARRIER_ATTACHED, false);
        builder.define(BARRIER_HP, BARRIER_HEALTH);
        builder.define(ALERT_TICKS, 0);
        builder.define(RIPPED, false);
        builder.define(GRAPPLE_RETRACTING, false);
        builder.define(CAPTURE_PULLING, false);
        builder.define(STAND_BIND_TICKS, STAND_STRING_TICKS);
        builder.define(Y_ROT_OFFSET, 0.0F);
        builder.define(X_ROT_OFFSET, 0.0F);
    }

    @SuppressWarnings("unchecked")
    private static EntityType<StoneFreeStringEntity> typeFor(
            Kind kind) {
        return switch (kind) {
            case STAND_STRING ->
                    StoneFreeAddon.SF_STRING.get();
            case USER_STRING ->
                    StoneFreeAddon.SFU_STRING.get();
            case USER_SWEEP ->
                    StoneFreeAddon.SFU_STRING_SWEEP.get();
            case USER_WHIP ->
                    StoneFreeAddon.SFU_STRING_WHIP.get();
            case EXTENDED_PUNCH ->
                    StoneFreeAddon.SF_EXTENDED_PUNCH.get();
            case USER_SPIKE ->
                    StoneFreeAddon.SFU_STRING_SPIKE.get();
            case STAND_GRAPPLE ->
                    StoneFreeAddon.SF_GRAPPLING_STRING.get();
            case USER_GRAPPLE ->
                    StoneFreeAddon.SFU_GRAPPLING_STRING.get();
            case USER_BARRIER ->
                    StoneFreeAddon.SFU_BARRIER.get();
        };
    }

    public enum Kind {
        STAND_STRING(false, false),
        USER_STRING(true, false),
        USER_SWEEP(true, false),
        USER_WHIP(true, false),
        EXTENDED_PUNCH(false, false),
        USER_SPIKE(true, false),
        STAND_GRAPPLE(false, true),
        USER_GRAPPLE(true, true),
        USER_BARRIER(false, true);

        private final boolean activeUserString;
        private final boolean held;

        Kind(boolean activeUserString, boolean held) {
            this.activeUserString = activeUserString;
            this.held = held;
        }

        /** Strings the user throws personally (donor SFU*StringEntity shot by the user). */
        public boolean isUserString() {
            return activeUserString;
        }
    }
}
