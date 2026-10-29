package rotp.addons.emperor.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class EmperorStandEntity extends StandEntity {
    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(
                    EmperorStandEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    public EmperorStandEntity(
            EntityType<? extends EmperorStandEntity> entityType,
            Level level) {
        super(entityType, level);
        // 1.16 EmperorEntity.offsetDefault (0, 0, 0); its y was also the y
        // of every task offset that set none.
        Y_OFFSET = 0.0D;
        offsetFromUser = new StandOffsetFromUser(
                this,
                new Vec3(0.0D, Y_OFFSET, 0.0D),
                StandOffsetFromUser.Rotations.HEAD)
                .grabOffset(offsetFromUser.grabIdleOffset);
        noPhysics = true;
        setInvisible(true);
    }

    public boolean hasTarget() {
        return entityData.get(TARGET_UUID).isPresent();
    }

    public LivingEntity getTargetEntity() {
        Optional<UUID> targetUuid = entityData.get(TARGET_UUID);
        if (targetUuid.isEmpty()
                || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(targetUuid.get());
        return entity instanceof LivingEntity living ? living : null;
    }

    public void setTargetEntity(LivingEntity target) {
        if (!level().isClientSide()) {
            entityData.set(
                    TARGET_UUID,
                    target != null
                            ? Optional.of(target.getUUID())
                            : Optional.empty());
        }
    }

    public void clearTarget() {
        setTargetEntity(null);
    }

    @Override
    public void tick() {
        super.tick();
        setInvisible(true);
        noPhysics = true;
        if (!level().isClientSide() && hasTarget()) {
            LivingEntity user = getUser();
            LivingEntity target = getTargetEntity();
            if (user == null
                    || target == null
                    || !target.isAlive()
                    || target.level() != level()
                    || !JojoModUtil.canHarm(user, target)) {
                clearTarget();
            }
        }
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET_UUID, Optional.empty());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }
}
