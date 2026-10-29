package rotp.addons.catchtherainbow.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class CatchTheRainbowRenderer extends StandEntityRenderer<
        CatchTheRainbowEntity,
        StandEntityRenderState,
        CatchTheRainbowModel> {
    public CatchTheRainbowRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public CatchTheRainbowModel createStandModel(
            LayerDefinition definition) {
        return new CatchTheRainbowModel(
                CatchTheRainbowModel.createBodyLayer().bakeRoot());
    }
}
