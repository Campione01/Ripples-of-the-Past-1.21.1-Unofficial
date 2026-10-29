package rotp.addons.hermitpurple.entity;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.ImmobilizeEffect;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.hermitpurple.HermitHamon;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HermitGrappleVineEntity
        extends OwnerBoundProjectileEntity {
    private static final EntityDataAccessor<Integer> ATTACHED_ENTITY =
            SynchedEntityData.defineId(
                    HermitGrappleVineEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BIND_ENTITIES =
            SynchedEntityData.defineId(
                    HermitGrappleVineEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> COMMAND_GRAB =
            SynchedEntityData.defineId(
                    HermitGrappleVineEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> HAMON_DAMAGE =
            SynchedEntityData.defineId(
                    HermitGrappleVineEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean>
            ATTACHED_TO_BLOCK =
            SynchedEntityData.defineId(
                    HermitGrappleVineEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos>
            BLOCK_ATTACHED_TO =
            SynchedEntityData.defineId(
                    HermitGrappleVineEntity.class,
                    EntityDataSerializers.BLOCK_POS);

    private static final double MAX_OWNER_DISTANCE =
            HermitPurpleSpec.HELD_VINE_RANGE + 1.0D;

    private final Set<Entity> draggedEntities = new HashSet<>();
    private double distance;
    private int ticksTargetClose;

    public HermitGrappleVineEntity(
            EntityType<? extends HermitGrappleVineEntity> type,
            LivingEntity shooter,
            Level level,
            boolean bindEntities,
            boolean commandGrab,
            float hamonDamage) {
        super(type, shooter, level);
        setBindEntities(bindEntities);
        entityData.set(COMMAND_GRAB, commandGrab);
        entityData.set(HAMON_DAMAGE, Math.max(hamonDamage, 0.0F));
    }

    public HermitGrappleVineEntity(
            EntityType<? extends HermitGrappleVineEntity> type,
            Level level) {
        super(type, level);
    }

    public void setBindEntities(boolean bindEntities) {
        entityData.set(BIND_ENTITIES, bindEntities);
    }

    public boolean bindsEntities() {
        return entityData.get(BIND_ENTITIES);
    }

    public boolean isCommandGrab() {
        return entityData.get(COMMAND_GRAB);
    }

    @Override
    public int ticksLifespan() {
        boolean attached = entityData.get(ATTACHED_ENTITY) >= 0
                || isAttachedToBlock();
        if (isCommandGrab()) {
            return attached
                    ? HermitPurpleSpec.GRAB_ATTACHED_TICKS
                    : HermitPurpleSpec.GRAB_FLIGHT_TICKS;
        }
        // Attached grapples last as long as the held action.
        return attached
                ? Integer.MAX_VALUE
                : HermitPurpleSpec.GRAPPLE_FLIGHT_TICKS;
    }

    @Override
    protected float getBaseDamage() {
        return 0.0F;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.0F;
    }

    @Override
    public boolean standDamage() {
        return true;
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

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected Vec3 getOwnerRelativeOffset() {
        return new Vec3(0.0D, -0.25D, 0.35D);
    }

    @Nullable
    private LivingEntity physicalUser() {
        LivingEntity owner = getOwner();
        return owner instanceof StandEntity stand ? stand.getUser() : owner;
    }

    // 1.16 getOwnerRelativeOffset, added to the user's eyes: (0, -0.2, 0)
    // for HPGrapplingVineEntity and (0, -0.25, 0) for HPVineGrabEntity.
    private Vec3 userOrigin(Vec3 eyePosition) {
        return eyePosition.add(
                0.0D, isCommandGrab() ? -0.25D : -0.2D, 0.0D);
    }

    @Override
    protected Vec3 ownerPosition(float partialTick, boolean useBodyRotation) {
        LivingEntity user = physicalUser();
        return user != null
                ? userOrigin(user.getEyePosition(partialTick))
                : getPosition(partialTick);
    }

    @Override
    protected HitResult[] rayTrace() {
        LivingEntity user = physicalUser();
        if (user == null) {
            return new HitResult[0];
        }
        // Collision runs before movement, so both must use the same next endpoint.
        if (getEntityAttachedTo() == null && !isAttachedToBlock()) {
            double nextDistance = Math.min(
                    HermitPurpleSpec.HELD_VINE_RANGE,
                    distance + movementSpeed() * speedFactor);
            Vec3 next = flightPosition(user, nextDistance);
            setDeltaMovement(next.subtract(position()));
        }
        return super.rayTrace();
    }

    private Vec3 flightPosition(LivingEntity user, double flightDistance) {
        return userOrigin(user.getEyePosition()).add(
                Vec3.directionFromRotation(user.getXRot(), user.getYRot())
                        .scale(flightDistance));
    }

    @Override
    public void tick() {
        clearDraggedMotion();
        super.tick();
        if (!isAlive()) {
            return;
        }
        if (!level().isClientSide()
                && (!isValidOwnerAndAction()
                        || !level().hasChunkAt(blockPosition())
                        || ownerDistanceSqr()
                                > MAX_OWNER_DISTANCE
                                        * MAX_OWNER_DISTANCE)) {
            discard();
            return;
        }

        LivingEntity target = getEntityAttachedTo();
        if (!level().isClientSide()
                && entityData.get(ATTACHED_ENTITY) >= 0
                && target == null) {
            discard();
            return;
        }
        if (target != null) {
            if (level().isClientSide()) {
                pullOnClient(target);
            }
            else {
                updateBoundTarget(target);
            }
        }
    }

    // 1.16 HPGrapplingVineEntity and HPVineGrabEntity ran the drag on both
    // sides: a caught player is pulled by the vine on its own client, and
    // only the server lets go.
    private void pullOnClient(LivingEntity target) {
        LivingEntity user = physicalUser();
        if (user == null || !target.isAlive()) {
            return;
        }
        Vec3 toOwner = user.position().subtract(target.position());
        double distanceSqr = toOwner.lengthSqr();
        if (isCommandGrab()) {
            if (distanceSqr <= HermitPurpleSpec.GRAB_RELEASE_DISTANCE
                            * HermitPurpleSpec.GRAB_RELEASE_DISTANCE
                    && distanceSqr > HermitPurpleSpec.GRAB_PULL_STOP_DISTANCE
                            * HermitPurpleSpec.GRAB_PULL_STOP_DISTANCE) {
                dragTarget(target, toOwner.normalize().scale(
                        HermitPurpleSpec.GRAB_PULL_SPEED));
            }
        }
        else if (distanceSqr >= HermitPurpleSpec.BIND_PULL_STOP_DISTANCE
                * HermitPurpleSpec.BIND_PULL_STOP_DISTANCE) {
            dragTarget(target, toOwner.normalize().scale(
                    HermitPurpleSpec.BIND_PULL_SPEED));
        }
    }

    private boolean isValidOwnerAndAction() {
        LivingEntity owner = getOwner();
        if (!(owner instanceof HermitPurpleStandEntity stand)
                || stand.getUser() == null
                || !HermitPurpleAddon.isHermitPurpleUser(
                        stand.getUser())) {
            return false;
        }
        EntityActionInstance action = stand.getCurStandAction();
        if (action == null
                || action.getPhase() != ActionPhase.PERFORM
                || action.ability.getAbilityId() == null) {
            return false;
        }
        String expected = isCommandGrab()
                ? HermitPurpleAddon.HP_GRAB_NAME
                : bindsEntities()
                        ? HermitPurpleAddon.HP_VINE_ENTITY_NAME
                        : HermitPurpleAddon.HP_VINE_NAME;
        return expected.equals(
                action.ability.getAbilityId().nameInMoveset());
    }

    private double ownerDistanceSqr() {
        LivingEntity owner = getOwner();
        LivingEntity origin =
                owner instanceof StandEntity stand
                        && stand.getUser() != null
                        ? stand.getUser()
                        : owner;
        // Flight is measured from the vine origin; the anchor sits at a block centre.
        return origin != null
                ? userOrigin(origin.getEyePosition())
                        .distanceToSqr(position())
                : Double.POSITIVE_INFINITY;
    }

    private void updateBoundTarget(LivingEntity target) {
        LivingEntity owner = getOwner();
        if (!target.isAlive() || owner == null) {
            discard();
            return;
        }
        // 1.16: only HPVineGrabEntity immobilizes; HPGrapplingVineEntity
        // just drags the bound entity.
        if (isCommandGrab()) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.IMMOBILIZE,
                    HermitPurpleSpec
                            .BIND_EFFECT_REFRESH_TICKS,
                    0,
                    false,
                    false,
                    false));
        }
        float hamonDamage = entityData.get(HAMON_DAMAGE);
        LivingEntity user =
                owner instanceof StandEntity stand
                        ? stand.getUser()
                        : owner;
        if (!isCommandGrab()
                && hamonDamage > 0.0F
                && user != null) {
            HermitHamon.hurt(
                    target,
                    user,
                    Math.min(0.05F, hamonDamage),
                    false);
        }
        if (user == null) {
            discard();
            return;
        }
        Vec3 toOwner = user.position()
                .subtract(target.position());
        double distanceSqr = toOwner.lengthSqr();
        if (isCommandGrab()) {
            // 1.16 HPVineGrabEntity: lets go beyond 10 blocks, drags beyond
            // 4, and lets go after 100 close ticks outside time stop.
            if (distanceSqr > HermitPurpleSpec.GRAB_RELEASE_DISTANCE
                    * HermitPurpleSpec.GRAB_RELEASE_DISTANCE) {
                discard();
                return;
            }
            if (distanceSqr > HermitPurpleSpec.GRAB_PULL_STOP_DISTANCE
                    * HermitPurpleSpec.GRAB_PULL_STOP_DISTANCE) {
                dragTarget(
                        target,
                        toOwner.normalize().scale(
                                HermitPurpleSpec.GRAB_PULL_SPEED));
                ticksTargetClose = 0;
            }
            else if (ticksTargetClose++
                            > HermitPurpleSpec.GRAB_CLOSE_RELEASE_TICKS
                    && !isInTimeStop()) {
                discard();
                return;
            }
        }
        else {
            // 1.16 HPGrapplingVineEntity: drags at 1 and lets go within 2.
            if (distanceSqr < HermitPurpleSpec.BIND_PULL_STOP_DISTANCE
                    * HermitPurpleSpec.BIND_PULL_STOP_DISTANCE) {
                discard();
                return;
            }
            dragTarget(
                    target,
                    toOwner.normalize().scale(
                            HermitPurpleSpec.BIND_PULL_SPEED));
        }
        target.fallDistance = 0.0F;
    }

    private boolean isInTimeStop() {
        return level() instanceof ServerLevel serverLevel
                && serverLevel.getData(
                                ModDataAttachmentTypes.TIME_STOP.get())
                        .isTimeStopped(this);
    }

    @Override
    protected void moveProjectile() {
        LivingEntity target = getEntityAttachedTo();
        if (target != null) {
            setPos(
                    target.getX(),
                    target.getY(0.5D),
                    target.getZ());
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (isAttachedToBlock()) {
            BlockPos blockPos = getBlockPosAttachedTo();
            setPos(
                    blockPos.getX() + 0.5D,
                    blockPos.getY() + 0.5D,
                    blockPos.getZ() + 0.5D);
            setDeltaMovement(Vec3.ZERO);
            pullOwnerToBlock();
            return;
        }
        moveOwnerBoundVine();
    }

    private void moveOwnerBoundVine() {
        LivingEntity user = physicalUser();
        if (user == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        if (distance
                >= HermitPurpleSpec.HELD_VINE_RANGE) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        setRot(user.getYRot(), user.getXRot());
        distance = Math.min(
                HermitPurpleSpec.HELD_VINE_RANGE,
                distance + movementSpeed() * speedFactor);
        Vec3 next = flightPosition(user, distance);
        if (!level().hasChunkAt(BlockPos.containing(next))) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        Vec3 current = position();
        setDeltaMovement(next.subtract(current));
        xo = current.x;
        yo = current.y;
        zo = current.z;
        xOld = current.x;
        yOld = current.y;
        zOld = current.z;
        setPos(next);
    }

    private double movementSpeed() {
        return isCommandGrab()
                ? HermitPurpleSpec.GRAB_VINE_SPEED
                : HermitPurpleSpec.GRAPPLE_VINE_SPEED;
    }

    private void pullOwnerToBlock() {
        LivingEntity user = physicalUser();
        if (user == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        Vec3 toAnchor = position().subtract(user.position());
        if (toAnchor.lengthSqr()
                <= HermitPurpleSpec
                        .BIND_PULL_STOP_DISTANCE
                        * HermitPurpleSpec
                                .BIND_PULL_STOP_DISTANCE) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        // 1.16 HPGrapplingVineEntity.moveToBlockAttached pulled the user on
        // both sides; the user's own client moves it at the full 2.0.
        Entity pulled = user.getRootVehicle();
        if (level().isClientSide()
                && !pulled.isControlledByLocalInstance()) {
            return;
        }
        pulled.setDeltaMovement(
                toAnchor.normalize().scale(
                        HermitPurpleSpec
                                .GRAPPLE_PULL_SPEED));
        pulled.fallDistance = 0.0F;
        markForMotionPacket(pulled);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!(entity instanceof LivingEntity)
                || !super.canHitEntity(entity)) {
            return false;
        }
        LivingEntity owner = getOwner();
        if (entity == owner) {
            return false;
        }
        if (owner instanceof StandEntity stand) {
            LivingEntity user = stand.getUser();
            return entity != user
                    && (!bindsEntities()
                            || stand.canAttackEntity(entity));
        }
        return true;
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        if (!level().isClientSide()
                && bindsEntities()
                && getEntityAttachedTo() == null
                && target instanceof LivingEntity living
                && !JojoModUtil.isTargetBlocking(living)) {
            attachToEntity(living);
            playSound(
                    HermitPurpleAddon.HP_GRAB_CATCH.get(),
                    0.8F,
                    1.0F);
            // 1.16 HPGrapplingVineEntity: a charged catch gives 200+strength
            // Strength and Control points, then one Hamon hit with sparks.
            float hamonDamage = entityData.get(HAMON_DAMAGE);
            LivingEntity user =
                    owner instanceof StandEntity stand
                            ? stand.getUser()
                            : owner;
            if (!isCommandGrab()
                    && hamonDamage > 0.0F
                    && user != null) {
                HermitHamon.data(user).ifPresent(hamon -> {
                    float cost =
                            200.0F + hamon.getHamonStrengthLevel();
                    hamon.hamonPointsFromAction(
                            rotp.core.impl.powers.hamon.HamonData
                                    .HamonStat.STRENGTH,
                            cost);
                    hamon.hamonPointsFromAction(
                            rotp.core.impl.powers.hamon.HamonData
                                    .HamonStat.CONTROL,
                            cost);
                });
                return HermitHamon.hurt(
                        living, user, hamonDamage, false);
            }
        }
        return false;
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hitResult,
            boolean blockDestroyed) {
        if (level().isClientSide()) {
            return;
        }
        // 1.16 HPGrapplingVineEntity: both grapples light lamps and charge
        // iron (200 ticks) or living blocks before the entity grapple goes.
        // The command grab (1.16 HPVineGrabEntity) had no block effects.
        if (!isCommandGrab()) {
            HermitVineProjectileEntity.applyBlockHitEffects(
                    this, getOwner(), hitResult.getBlockPos(), 200);
        }
        // 1.16 HPGrapplingVineEntity.afterBlockHit and, for the command
        // grab, OwnerBoundProjectileEntity.afterBlockHit: a block the vine
        // broke neither catches nor removes it.
        if (blockDestroyed) {
            return;
        }
        if (bindsEntities()) {
            discard();
            return;
        }
        BlockPos blockPos = hitResult.getBlockPos();
        // 1.16 HPGrapplingVineEntity: a barrier block removes the vine, and
        // the per-tick hit of an attached vine neither moves the anchor nor
        // repeats the catch sound.
        if (level().getBlockState(blockPos).is(Blocks.BARRIER)) {
            discard();
            return;
        }
        if (isAttachedToBlock()) {
            return;
        }
        if (!level().hasChunkAt(blockPos)) {
            discard();
            return;
        }
        attachToBlockPos(blockPos);
        playSound(
                HermitPurpleAddon.HP_GRAPPLE.get(),
                1.0F,
                1.0F);
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult target) {
        // 1.16 OwnerBoundProjectileEntity.breakProjectile is empty: the block
        // grapple flies on through a mob it cannot bind, and afterBlockHit
        // alone decides what a block does to the vine.
        if (targetType == TargetType.BLOCK
                || targetType == TargetType.ENTITY && !bindsEntities()) {
            return;
        }
        if (!isAttachedToBlock()
                && getEntityAttachedTo() == null
                && !level().isClientSide()) {
            discard();
        }
    }

    @Nullable
    public LivingEntity getEntityAttachedTo() {
        int id = entityData.get(ATTACHED_ENTITY);
        if (id < 0) {
            return null;
        }
        Entity entity = level().getEntity(id);
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    private void attachToEntity(LivingEntity target) {
        entityData.set(ATTACHED_ENTITY, target.getId());
        setPos(
                target.getX(),
                target.getY(0.5D),
                target.getZ());
        setDeltaMovement(Vec3.ZERO);
    }

    public boolean isAttachedToBlock() {
        return entityData.get(ATTACHED_TO_BLOCK);
    }

    private void attachToBlockPos(BlockPos blockPos) {
        entityData.set(BLOCK_ATTACHED_TO, blockPos);
        entityData.set(ATTACHED_TO_BLOCK, true);
        setDeltaMovement(Vec3.ZERO);
    }

    private BlockPos getBlockPosAttachedTo() {
        return entityData.get(BLOCK_ATTACHED_TO);
    }

    private void dragTarget(Entity target, Vec3 impulse) {
        Entity root = target.getRootVehicle();
        dragEntity(root, impulse);
        if (root instanceof StandEntity stand
                && stand.getUser() != null) {
            dragEntity(stand.getUser().getRootVehicle(), impulse);
        }
    }

    private void dragEntity(Entity entity, Vec3 impulse) {
        if (level().isClientSide()
                && !entity.isControlledByLocalInstance()) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            for (Holder<MobEffect> effect
                    : living.getActiveEffectsMap().keySet()) {
                if (effect.is(ModStatusEffects.IMMOBILIZE)
                        && effect.value()
                                instanceof ImmobilizeEffect immobilize
                        && immobilize.resetsDeltaMovement()) {
                    entity.move(MoverType.PLAYER, impulse);
                    return;
                }
            }
        }
        entity.setDeltaMovement(impulse);
        draggedEntities.add(entity);
        entity.fallDistance = 0.0F;
        markForMotionPacket(entity);
    }

    // 1.16 zeroes the motion on both sides, and the client vine stops a
    // caught player itself.
    private void clearDraggedMotion() {
        draggedEntities.forEach(entity -> {
            entity.setDeltaMovement(Vec3.ZERO);
            markForMotionPacket(entity);
        });
        draggedEntities.clear();
    }

    // A player, or a vehicle a player steers, moves on its own client, where
    // this vine pulls and stops it. A server motion packet would reach that
    // client a tick late with the pull already cut by the server's friction.
    private void markForMotionPacket(Entity entity) {
        if (!level().isClientSide()
                && !(entity instanceof Player)
                && !(entity.getControllingPassenger() instanceof Player)) {
            entity.hurtMarked = true;
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        clearDraggedMotion();
    }

    @Override
    public void remove(RemovalReason reason) {
        clearDraggedMotion();
        if (getOwner()
                instanceof HermitPurpleStandEntity controller) {
            controller.forgetHeldVine(this);
            // 1.16 HPVineGrabEntity.onRemovedFromWorld: removing the grab
            // vine sets hp_grab's timer directly, 75 with Resolve, 150 without.
            if (!level().isClientSide()
                    && isCommandGrab()
                    && controller.getUser() instanceof Player player) {
                StandPower power = StandPower.get(player);
                if (power != null) {
                    power.setAbilityCooldown(
                            HermitPurpleAddon.HP_GRAB_NAME,
                            player.hasEffect(ModStatusEffects.RESOLVE)
                                    ? HermitPurpleSpec
                                            .GRAB_COOLDOWN_RESOLVE_TICKS
                                    : HermitPurpleSpec
                                            .GRAB_COOLDOWN_TICKS);
                }
            }
        }
        super.remove(reason);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeDouble(distance);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        distance = buffer.readDouble();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACHED_ENTITY, -1);
        builder.define(BIND_ENTITIES, false);
        builder.define(COMMAND_GRAB, false);
        builder.define(HAMON_DAMAGE, 0.0F);
        builder.define(ATTACHED_TO_BLOCK, false);
        builder.define(BLOCK_ATTACHED_TO, BlockPos.ZERO);
    }
}
