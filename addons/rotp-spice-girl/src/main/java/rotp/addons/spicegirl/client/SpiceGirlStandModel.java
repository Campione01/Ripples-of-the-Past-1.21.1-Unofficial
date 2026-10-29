package rotp.addons.spicegirl.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;

import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

public final class SpiceGirlStandModel extends StandEntityModel<
        SpiceGirlStandEntity, StandEntityRenderState> {
    static final float SUMMON_ANIMATION_TICKS = 20.0F;

    public SpiceGirlStandModel(ModelPart root) {
        super(root);
    }

    @Override
    public Vec3 prepareMotionTilt(
            SpiceGirlStandEntity entity, float partialTick) {
        return suppressMotionTilt(entity.tickCount, partialTick)
                ? Vec3.ZERO
                : super.prepareMotionTilt(entity, partialTick);
    }

    static boolean suppressMotionTilt(
            int tickCount, float partialTick) {
        return tickCount + partialTick <= SUMMON_ANIMATION_TICKS;
    }
}
