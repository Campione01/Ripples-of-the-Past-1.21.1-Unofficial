package rotp.addons.tusk.entity;

import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class WormholeArmEntity extends TuskOperationEntity {
    public static final double AIM_RANGE = 100.0D;
    private static final String LEFT_ARM_TAG = "LeftArm";
    private static final String NAILS_SHOT_TAG = "NailsShot";
    private static final EntityDataAccessor<Boolean> LEFT_ARM =
            SynchedEntityData.defineId(
                    WormholeArmEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private int nailsShot;

    public WormholeArmEntity(
            EntityType<? extends WormholeArmEntity> entityType,
            Level level) {
        super(entityType, level);
    }

    public WormholeArmEntity(
            Level level,
            UUID operationUuid,
            UUID ownerUuid,
            long expiresAt,
            boolean leftArm) {
        this(TuskAddon.WORMHOLE_ARM_ENTITY.get(), level);
        initializeOperation(operationUuid, ownerUuid, expiresAt);
        setLeftArm(leftArm);
    }

    public boolean isLeftArm() {
        return entityData.get(LEFT_ARM);
    }

    public HumanoidArm armSide() {
        return isLeftArm() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public void setLeftArm(boolean leftArm) {
        entityData.set(LEFT_ARM, leftArm);
    }

    public int getNailsShot() {
        return nailsShot;
    }

    public void countNailShot() {
        nailsShot++;
    }

    @Override
    public TuskPortalOperations.MemberRole memberRole() {
        return TuskPortalOperations.MemberRole.ARM;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        TuskPortalOperations operations =
                TuskPortalOperations.get(serverLevel.getServer());
        ServerPlayer owner = operations.resolveOwner(this);
        if (owner == null) {
            return;
        }
        // 1.16: no nails left, or as many nails shot through the arm as are left.
        int nails = TuskAddon.nailState(owner).nails();
        if (nails <= 0 || nailsShot > 0 && nails - nailsShot == 0) {
            operations.closeOperation(getOperationUuid());
            return;
        }
        boolean leftArm = operations.armSideFor(owner)
                == HumanoidArm.LEFT;
        if (leftArm != isLeftArm()) {
            setLeftArm(leftArm);
            operations.updateArmSide(this);
        }
        lookAt(
                EntityAnchorArgument.Anchor.EYES,
                aimTarget(owner));
        // 1.16: an arm that is not being moved keeps only its vertical motion, which a shot adds to its speed.
        setDeltaMovement(getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
    }

    // 1.16 JojoModUtil.rayTrace(owner, 100, not a projectile): the nearest pickable entity on the whole ray,
    // else the exact point where the ray meets a block outline, else the ray's end.
    private static Vec3 aimTarget(ServerPlayer owner) {
        Vec3 start = owner.getEyePosition();
        Vec3 look = owner.getViewVector(1.0F);
        List<ActionTarget> entities = HitResultUtil.clipMultipleEntities(
                start,
                look,
                AIM_RANGE,
                owner.level(),
                owner,
                entity -> !(entity instanceof Projectile),
                0.0D,
                0.0D);
        if (!entities.isEmpty()) {
            ActionTarget nearest = entities.getFirst();
            return nearest.getClipPos().orElseGet(nearest::getCenterPos);
        }
        return owner.level().clip(new ClipContext(
                start,
                start.add(look.scale(AIM_RANGE)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                owner)).getLocation();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LEFT_ARM, false);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(LEFT_ARM_TAG, isLeftArm());
        tag.putInt(NAILS_SHOT_TAG, nailsShot);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setLeftArm(tag.getBoolean(LEFT_ARM_TAG));
        nailsShot = tag.getInt(NAILS_SHOT_TAG);
    }
}
