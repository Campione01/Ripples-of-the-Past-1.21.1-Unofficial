package rotp.addons.stickyfingers.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.state.DetachedBodyPartRecord;
import rotp.addons.stickyfingers.state.DetachedPartType;
import rotp.addons.stickyfingers.state.ZipperCombatCloseReason;
import rotp.addons.stickyfingers.state.ZipperCombatData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class DetachedBodyPartEntity extends Entity {
    private static final String TARGET_ID_TAG = "TargetId";
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String PART_TAG = "Part";
    private static final String RETURNING_TAG = "Returning";
    private static final String CARRIED_TAG = "Carried";

    private static final EntityDataAccessor<Optional<UUID>>
            TARGET_UUID = SynchedEntityData.defineId(
                    DetachedBodyPartEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    DetachedBodyPartEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer>
            TARGET_ENTITY_ID = SynchedEntityData.defineId(
                    DetachedBodyPartEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer>
            PART = SynchedEntityData.defineId(
                    DetachedBodyPartEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean>
            RETURNING = SynchedEntityData.defineId(
                    DetachedBodyPartEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>
            CARRIED = SynchedEntityData.defineId(
                    DetachedBodyPartEntity.class,
                    EntityDataSerializers.BOOLEAN);

    public DetachedBodyPartEntity(
            EntityType<? extends DetachedBodyPartEntity> type,
            Level level) {
        super(type, level);
    }

    public void initialize(DetachedBodyPartRecord record) {
        entityData.set(
                TARGET_UUID, Optional.of(record.targetId()));
        entityData.set(
                OWNER_UUID, Optional.of(record.ownerId()));
        entityData.set(PART, record.part().ordinal());
        LivingEntity target = resolveTarget();
        entityData.set(
                TARGET_ENTITY_ID,
                target != null ? target.getId() : -1);
    }

    public DetachedPartType getPartType() {
        DetachedPartType part =
                DetachedPartType.fromOrdinal(
                        entityData.get(PART));
        return part != null ? part : DetachedPartType.HEAD;
    }

    @Nullable
    public UUID getTargetUuid() {
        return entityData.get(TARGET_UUID).orElse(null);
    }

    @Nullable
    public UUID getDetacherUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    @Nullable
    public LivingEntity resolveTarget() {
        LivingEntity target = resolveTargetForQuery();
        if (target != null
                && entityData.get(TARGET_ENTITY_ID)
                        != target.getId()) {
            entityData.set(TARGET_ENTITY_ID, target.getId());
        }
        return target;
    }

    @Nullable
    public LivingEntity resolveTargetForQuery() {
        UUID targetId = getTargetUuid();
        int entityId = entityData.get(TARGET_ENTITY_ID);
        Entity byId =
                entityId >= 0 ? level().getEntity(entityId) : null;
        if (byId instanceof LivingEntity living
                && (targetId == null
                        || living.getUUID().equals(targetId))) {
            return living;
        }
        if (targetId == null) {
            return null;
        }
        for (Player player : level().players()) {
            if (player.getUUID().equals(targetId)) {
                return player;
            }
        }
        if (level() instanceof ServerLevel serverLevel) {
            Entity byUuid = serverLevel.getEntity(targetId);
            if (byUuid instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    public boolean isReturning() {
        return entityData.get(RETURNING);
    }

    public boolean isCarried() {
        return entityData.get(CARRIED);
    }

    @Nullable
    public LivingEntity getCarrier() {
        return isCarried()
                && getVehicle() instanceof LivingEntity living
                        ? living
                        : null;
    }

    public static boolean releaseCarriedBy(
            LivingEntity carrier, boolean throwForward) {
        if (carrier.level().isClientSide()) {
            return false;
        }
        for (Entity passenger : carrier.getPassengers()) {
            if (!(passenger
                    instanceof DetachedBodyPartEntity part)
                    || part.getCarrier() != carrier) {
                continue;
            }
            part.stopRiding();
            if (throwForward) {
                part.setDeltaMovement(
                        carrier.getLookAngle().scale(1.2D));
            }
            return true;
        }
        return false;
    }

    public void startReturning() {
        if (isPassenger()) {
            stopRiding();
        }
        entityData.set(RETURNING, true);
        entityData.set(CARRIED, false);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(TARGET_UUID, Optional.empty());
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(TARGET_ENTITY_ID, -1);
        builder.define(PART, DetachedPartType.HEAD.ordinal());
        builder.define(RETURNING, false);
        builder.define(CARRIED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(
                TARGET_UUID,
                tag.hasUUID(TARGET_ID_TAG)
                        ? Optional.of(tag.getUUID(TARGET_ID_TAG))
                        : Optional.empty());
        entityData.set(
                OWNER_UUID,
                tag.hasUUID(OWNER_ID_TAG)
                        ? Optional.of(tag.getUUID(OWNER_ID_TAG))
                        : Optional.empty());
        DetachedPartType part;
        try {
            part = DetachedPartType.valueOf(
                    tag.getString(PART_TAG));
        } catch (IllegalArgumentException exception) {
            part = DetachedPartType.HEAD;
        }
        entityData.set(PART, part.ordinal());
        entityData.set(
                RETURNING, tag.getBoolean(RETURNING_TAG));
        entityData.set(
                CARRIED, tag.getBoolean(CARRIED_TAG));
        setNoGravity(isReturning() || isCarried());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID targetId = getTargetUuid();
        UUID ownerId = getDetacherUuid();
        if (targetId != null) {
            tag.putUUID(TARGET_ID_TAG, targetId);
        }
        if (ownerId != null) {
            tag.putUUID(OWNER_ID_TAG, ownerId);
        }
        tag.putString(PART_TAG, getPartType().name());
        tag.putBoolean(RETURNING_TAG, isReturning());
        tag.putBoolean(CARRIED_TAG, isCarried());
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel
                && !ZipperCombatData.get(serverLevel)
                        .isPartActive(getUUID())) {
            discard();
            return;
        }
        if (isCarried()) {
            tickCarried();
        } else if (isReturning()) {
            tickReturning();
        } else {
            tickGroundPhysics();
        }
    }

    @Override
    public InteractionResult interact(
            Player player, InteractionHand hand) {
        UUID targetId = getTargetUuid();
        boolean ownPart = targetId != null
                && targetId.equals(player.getUUID());
        // 1.16: a bow or crossbow leaves the click to the weapon (both sides)
        if (!ownPart && isPickupClick(player, hand)
                && player.getMainHandItem().getItem()
                        instanceof ProjectileWeaponItem) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (ownPart) {
            ZipperCombatData.get(serverLevel).recoverPart(
                    serverLevel,
                    getUUID(),
                    ZipperCombatCloseReason.RECOVERED);
            return InteractionResult.SUCCESS;
        }
        if (isPickupClick(player, hand)) {
            ZipperCombatData data =
                    ZipperCombatData.get(serverLevel);
            // 1.16 action-bar feedback: no arm, busy off hand, release hint
            if (data.isHandBlocked(
                    player, InteractionHand.OFF_HAND)) {
                player.displayClientMessage(Component.translatable(
                        "rotp_stfn.message.cant_pickup.no_arm"), true);
                return InteractionResult.FAIL;
            }
            if (!player.getOffhandItem().isEmpty()
                    || carriesPart(player)) {
                player.displayClientMessage(Component.translatable(
                        "body_part.carry.offhand",
                        getDisplayName()), true);
                return InteractionResult.PASS;
            }
            if (startRiding(player, true)) {
                entityData.set(CARRIED, true);
                entityData.set(RETURNING, false);
                setNoGravity(true);
                player.displayClientMessage(Component.translatable(
                        "body_part.hint.release",
                        Component.keybind("key.swapOffhand"),
                        getDisplayName()), true);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            startReturning();
        }
        return InteractionResult.sidedSuccess(
                level().isClientSide());
    }

    private boolean isPickupClick(
            Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                && !player.isShiftKeyDown()
                && !isPassenger();
    }

    // 1.16 counted an already carried part as a busy off hand
    private static boolean carriesPart(Player player) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof DetachedBodyPartEntity part
                    && part.getCarrier() == player) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) {
            return true;
        }
        LivingEntity target = resolveTarget();
        if (target == null
                || source.getEntity() == target
                || target.isInvulnerableTo(source)) {
            return false;
        }
        return target.hurt(source, amount);
    }

    @Override
    public boolean isPickable() {
        return !isCarried();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public void stopRiding() {
        super.stopRiding();
        if (!level().isClientSide()) {
            entityData.set(CARRIED, false);
        }
        setNoGravity(isReturning());
    }

    @Override
    public void onSyncedDataUpdated(
            EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (CARRIED.equals(key)) {
            setNoGravity(isCarried() || isReturning());
            if (!isCarried() && isPassenger()) {
                stopRiding();
            }
        }
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        if (isCarried()
                && vehicle instanceof LivingEntity carrier) {
            Vec3 passengerPoint =
                    vehicle.getPassengerRidingPosition(this)
                            .subtract(vehicle.position());
            return passengerPoint.subtract(
                    carryOffset(carrier.getYRot(), carrier));
        }
        return super.getVehicleAttachmentPoint(vehicle);
    }

    public static Vec3 carryOffset(
            float yRot, LivingEntity carrier) {
        HumanoidArm offHand =
                carrier.getMainArm().getOpposite();
        float width = carrier.getBbWidth();
        return new Vec3(
                width * (offHand == HumanoidArm.LEFT
                        ? 0.55D
                        : -0.55D),
                carrier.getBbHeight() * 0.35D,
                width * 0.75D)
                        .yRot(-yRot * Mth.DEG_TO_RAD);
    }

    private void tickCarried() {
        LivingEntity carrier = getCarrier();
        if (carrier == null) {
            if (!level().isClientSide()) {
                entityData.set(CARRIED, false);
            }
            setNoGravity(isReturning());
            return;
        }
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        setYRot(carrier.getYRot());
        yRotO = carrier.yRotO;
        if (level() instanceof ServerLevel serverLevel) {
            ZipperCombatData data =
                    ZipperCombatData.get(serverLevel);
            if (!carrier.getOffhandItem().isEmpty()
                    || data.isHandBlocked(
                            carrier,
                            InteractionHand.OFF_HAND)) {
                stopRiding();
            }
        }
    }

    private void tickReturning() {
        LivingEntity target = resolveTarget();
        if (target == null) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 destination = target.position().add(
                0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 offset = destination.subtract(position());
        if (offset.lengthSqr()
                <= StickyFingersPhase1Spec
                        .DETACHED_PART_RETURN_DISTANCE_SQR) {
            if (level() instanceof ServerLevel serverLevel) {
                ZipperCombatData.get(serverLevel).recoverPart(
                        serverLevel,
                        getUUID(),
                        ZipperCombatCloseReason.RETURNED);
            }
            return;
        }
        Vec3 velocity = offset.normalize().scale(
                StickyFingersPhase1Spec
                        .DETACHED_PART_RETURN_SPEED);
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);
    }

    private void tickGroundPhysics() {
        Vec3 velocity = getDeltaMovement();
        if (!isNoGravity()) {
            velocity = velocity.add(0.0D, -0.04D, 0.0D);
        }
        move(MoverType.SELF, velocity);
        velocity = getDeltaMovement();
        if (onGround()) {
            velocity = new Vec3(
                    velocity.x * 0.7D,
                    Math.abs(velocity.y) * 0.25D,
                    velocity.z * 0.7D);
        }
        setDeltaMovement(velocity.scale(0.98D));
    }
}
