package rotp.addons.spicegirl.client;

import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class SpiceGirlStandRenderer extends StandEntityRenderer<
        SpiceGirlStandEntity,
        StandEntityRenderState,
        SpiceGirlStandModel> {
    public SpiceGirlStandRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SpiceGirlStandModel createStandModel(
            LayerDefinition definition) {
        return new SpiceGirlStandModel(definition.bakeRoot());
    }
}
