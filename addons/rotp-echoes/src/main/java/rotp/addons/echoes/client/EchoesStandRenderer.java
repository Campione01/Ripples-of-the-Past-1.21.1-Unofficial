package rotp.addons.echoes.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.echoes.stand.EchoesStandEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class EchoesStandRenderer extends StandEntityRenderer<
        EchoesStandEntity, StandEntityRenderState, EchoesStandModel> {
    public EchoesStandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EchoesStandModel createStandModel(LayerDefinition definition) {
        return new EchoesStandModel(definition.bakeRoot());
    }

    @Override
    public void extractRenderState(
            EchoesStandEntity entity,
            StandEntityRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.yRot = entity.getSyncedHeadYaw();
        renderState.xRot = entity.getSyncedHeadPitch();
    }
}
