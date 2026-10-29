package rotp.addons.lovers.client.render;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.lovers.entity.LoversStandEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class LoversRenderer extends StandEntityRenderer<
        LoversStandEntity, StandEntityRenderState, LoversModel> {
    private static final LayerDefinition DONOR_MODEL =
            LoversModel.createBodyLayer();

    public LoversRenderer(EntityRendererProvider.Context context) {
        super(context, 0.0F);
    }

    @Override
    public LoversModel createStandModel(
            LayerDefinition ignoredSkinDefinition) {
        return new LoversModel(DONOR_MODEL.bakeRoot());
    }
}
