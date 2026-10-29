package rotp.addons.purplehaze.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class PurpleHazeStandRenderer extends StandEntityRenderer<
        PurpleHazeEntity,
        PurpleHazeRenderState,
        PurpleHazeStandModel> {
    public PurpleHazeStandRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        addLayer(new PurpleHazeAuraLayer(this));
    }

    @Override
    public PurpleHazeRenderState createRenderState() {
        return new PurpleHazeRenderState();
    }

    @Override
    public PurpleHazeStandModel createStandModel(
            LayerDefinition definition) {
        return new PurpleHazeStandModel(
                definition.bakeRoot());
    }

    @Override
    public void extractRenderState(
            PurpleHazeEntity entity,
            PurpleHazeRenderState renderState,
            float partialTick) {
        super.extractRenderState(
                entity,
                renderState,
                partialTick);
        renderState.capsuleCount = entity.getCapsuleCount();
        renderState.auraActive = entity.hasAura();
    }
}
