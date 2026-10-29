package rotp.addons.ger.entity;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GerStandEntity extends StandEntity {
    private static final float EYE_HEIGHT_ABOVE_STANDING_EYE = 0.7F;

    public GerStandEntity(
            EntityType<? extends GerStandEntity> entityType,
            Level level) {
        super(entityType, level);
        // 1.16 GoldExprienceRequiemEntity: behind the user's left shoulder,
        // the mirror of the core default.
        offsetFromUser.idleOffset = new Vec3(0.75D, Y_OFFSET, -0.75D);
        offsetFromUser.idleRotations = StandOffsetFromUser.Rotations.HEAD;
        offsetFromUser.resetToIdle();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDefaultDimensions(pose);
        return dimensions.withEyeHeight(
                dimensions.eyeHeight() + EYE_HEIGHT_ABOVE_STANDING_EYE);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
