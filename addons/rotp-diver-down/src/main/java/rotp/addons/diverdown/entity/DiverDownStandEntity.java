package rotp.addons.diverdown.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.diverdown.ability.WallGlideAbility;
import rotp.addons.diverdown.state.DiverDownAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class DiverDownStandEntity extends StandEntity {
    private static final EntityDataAccessor<Integer>
            EMBEDDED_TARGET_ID = SynchedEntityData.defineId(
                    DiverDownStandEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>>
            EMBEDDED_TARGET_UUID = SynchedEntityData.defineId(
                    DiverDownStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    public DiverDownStandEntity(
            EntityType<? extends DiverDownStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EMBEDDED_TARGET_ID, 0);
        builder.define(EMBEDDED_TARGET_UUID, Optional.empty());
    }

    public boolean isEmbedded() {
        return entityData.get(EMBEDDED_TARGET_ID) > 0
                || entityData.get(EMBEDDED_TARGET_UUID).isPresent();
    }

    public LivingEntity getEmbeddedTarget() {
        Entity entity = null;
        if (level() instanceof ServerLevel serverLevel) {
            Optional<UUID> target =
                    entityData.get(EMBEDDED_TARGET_UUID);
            if (target.isPresent()) {
                entity = serverLevel.getEntity(target.get());
            }
        } else {
            int id = entityData.get(EMBEDDED_TARGET_ID);
            if (id > 0) {
                entity = level().getEntity(id);
            }
        }
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    public boolean setEmbeddedTarget(LivingEntity target) {
        if (level().isClientSide()) {
            return false;
        }
        LivingEntity owner = getUser();
        if (!DiverDownAuthority.validController(
                        owner, this, null)
                || !DiverDownAuthority.canAffect(
                        owner, target, this)) {
            clearEmbeddedTarget();
            return false;
        }
        entityData.set(EMBEDDED_TARGET_ID, target.getId(), true);
        entityData.set(
                EMBEDDED_TARGET_UUID,
                Optional.of(target.getUUID()),
                true);
        updateEmbeddedPosition(target);
        return true;
    }

    public void clearEmbeddedTarget() {
        if (!level().isClientSide()) {
            entityData.set(EMBEDDED_TARGET_ID, 0, true);
            entityData.set(
                    EMBEDDED_TARGET_UUID,
                    Optional.empty(),
                    true);
        }
        setInvisible(false);
        updateNoPhysics();
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity target = getEmbeddedTarget();
        if (!level().isClientSide() && isEmbedded()) {
            LivingEntity owner = getUser();
            if (!DiverDownAuthority.validController(
                            owner, this, null)
                    || !DiverDownAuthority.canAffect(
                            owner, target, this)
                    || target.distanceToSqr(owner)
                            > getMaxRange() * getMaxRange()
                    || isBeingRetracted()) {
                clearEmbeddedTarget();
                target = null;
            }
        }
        if (target != null && isEmbedded()) {
            updateEmbeddedPosition(target);
        } else if (!isEmbedded()) {
            setInvisible(false);
            updateNoPhysics();
        }
    }

    @Override
    public void retract() {
        clearEmbeddedTarget();
        super.retract();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (level() instanceof ServerLevel serverLevel) {
            DiverDownAuthority.clearForStand(
                    serverLevel, getUUID());
        }
        clearEmbeddedTarget();
        super.remove(reason);
    }

    @Override
    public boolean isFollowingUser() {
        return !isEmbedded() && super.isFollowingUser();
    }

    // 1.16 DiverDownEntity.updatePosition :126-142: while its task is the wall glide, Diver Down stands halfway into each
    // non-air block of the eight around its user's feet and head (the last one wins), at the user's height, and stays
    // where it is when there is none
    @Override
    public void updatePosition(LivingEntity user) {
        EntityActionInstance action = getCurStandAction();
        if (user != null && user.isAlive() && !isBeingRetracted()
                && action != null && action.ability instanceof WallGlideAbility) {
            Vec3 userPos = user.position();
            BlockPos feet = user.blockPosition();
            Vec3 inBlock = userPos.subtract(feet.getX(), feet.getY(), feet.getZ());
            for (BlockPos beside : List.of(feet.north(), feet.south(), feet.east(), feet.west(),
                    feet.above().north(), feet.above().south(), feet.above().east(), feet.above().west())) {
                if (!level().getBlockState(beside).is(Blocks.AIR)) {
                    Vec3 diverPos = Vec3.atLowerCornerOf(beside).add(inBlock);
                    setPos((userPos.x + diverPos.x) / 2.0D, userPos.y, (userPos.z + diverPos.z) / 2.0D);
                }
            }
            return;
        }
        super.updatePosition(user);
    }

    // 1.16 DiverDownEntity: Diver Down passes through blocks, also under
    // manual control and while it acts.
    @Override
    protected boolean shouldHaveNoPhysics() {
        return true;
    }

    @Override
    public Vec3 collideNextPos(Vec3 pos) {
        return pos;
    }

    private void updateEmbeddedPosition(LivingEntity target) {
        Vec3 targetPosition = target.position();
        setPos(
                targetPosition.x,
                targetPosition.y,
                targetPosition.z);
        setYRot(target.getYRot());
        setXRot(target.getXRot());
        setDeltaMovement(Vec3.ZERO);
        setInvisible(true);
        setNoPhysics(true);
        hurtMarked = true;
    }
}
