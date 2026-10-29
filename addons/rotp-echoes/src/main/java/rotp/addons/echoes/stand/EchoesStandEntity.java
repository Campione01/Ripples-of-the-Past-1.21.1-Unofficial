package rotp.addons.echoes.stand;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class EchoesStandEntity extends StandEntity {
    private static final EntityDataAccessor<Float> USER_HEAD_YAW =
            SynchedEntityData.defineId(
                    EchoesStandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> USER_HEAD_PITCH =
            SynchedEntityData.defineId(
                    EchoesStandEntity.class, EntityDataSerializers.FLOAT);

    // The donors keep the 1.16 default offset, so the core default stays.
    public EchoesStandEntity(
            EntityType<? extends EchoesStandEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(USER_HEAD_YAW, 0.0F);
        builder.define(USER_HEAD_PITCH, 0.0F);
    }

    @Override
    public void copyStandUserRotation(LivingEntity user) {
        super.copyStandUserRotation(user);
        if (!level().isClientSide()) {
            // Relative to the Stand's own body, which an action can turn
            // away from the user's.
            entityData.set(USER_HEAD_YAW, Mth.wrapDegrees(
                    user.yHeadRot - yBodyRot));
            entityData.set(USER_HEAD_PITCH, user.getXRot());
        }
    }

    public float getSyncedHeadYaw() {
        return entityData.get(USER_HEAD_YAW);
    }

    public float getSyncedHeadPitch() {
        return entityData.get(USER_HEAD_PITCH);
    }
}
