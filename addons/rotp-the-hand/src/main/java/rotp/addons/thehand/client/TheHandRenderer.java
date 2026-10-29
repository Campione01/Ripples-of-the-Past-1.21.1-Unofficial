package rotp.addons.thehand.client;

import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.thehand.ability.TheHandCombat;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class TheHandRenderer extends StandEntityRenderer<
        StandEntity,
        StandEntityRenderState,
        StandEntityModel<StandEntity, StandEntityRenderState>> {
    private static final String ERASE_EFFECT = "erase_effect";

    public TheHandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // 1.16 TheHandModel showed the erase effect exactly while the Stand was
    // erasing, whatever clip played: a punch, kick or summon clip without
    // the bone would otherwise leave it at its rest scale, shown.
    @Override
    public void extractRenderState(
            StandEntity entity,
            StandEntityRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        AnimFramePose pose = renderState.action.pose;
        if (pose == null) {
            return;
        }
        float scaleOffset = TheHandCombat.isErasing(
                entity.getCurStandAction()) ? 0.0F : -1.0F;
        AnimFramePose.ModelPartFrame frame = pose.getIfPresent(ERASE_EFFECT);
        if (frame != null
                && frame.scaleOffset.x() == scaleOffset
                && frame.scaleOffset.y() == scaleOffset
                && frame.scaleOffset.z() == scaleOffset) {
            return;
        }
        pose = pose.deepCopy();
        pose.getForModelPart(ERASE_EFFECT)
                .scaleOffset.set(scaleOffset, scaleOffset, scaleOffset);
        renderState.action.pose = pose;
    }
}
