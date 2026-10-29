package rotp.addons.stonefree.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class StoneFreeStandRenderer extends StandEntityRenderer<
        StoneFreeStandEntity,
        StoneFreeRenderState,
        StoneFreeStandModel> {
    public StoneFreeStandRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public StoneFreeRenderState createRenderState() {
        return new StoneFreeRenderState();
    }

    @Override
    public StoneFreeStandModel createStandModel(
            LayerDefinition definition) {
        return new StoneFreeStandModel(
                definition.bakeRoot());
    }

    @Override
    public void extractRenderState(
            StoneFreeStandEntity entity,
            StoneFreeRenderState renderState,
            float partialTick) {
        super.extractRenderState(
                entity, renderState, partialTick);
        renderState.forearmPresent = entity.hasForearm();
        renderState.shortForearm =
                entity.hasShortForearm();
        renderState.captureActive =
                entity.isCaptureActive();
    }
}
