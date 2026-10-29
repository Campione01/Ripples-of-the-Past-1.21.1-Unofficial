package rotp.addons.weatherreport.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.weatherreport.ability.WeatherReportCloudShieldAbility;
import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class WeatherReportStandRenderer extends StandEntityRenderer<
        WeatherReportStandEntity,
        WeatherReportStandRenderState,
        WeatherReportStandModel> {
    public WeatherReportStandRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public WeatherReportStandRenderState createRenderState() {
        return new WeatherReportStandRenderState();
    }

    @Override
    public WeatherReportStandModel createStandModel(
            LayerDefinition definition) {
        return new WeatherReportStandModel(definition.bakeRoot());
    }

    @Override
    public void extractRenderState(
            WeatherReportStandEntity entity,
            WeatherReportStandRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.icicleArmHidden =
                entity.isIcicleArmHidden();
        EntityActionInstance action = entity.getCurStandAction();
        renderState.cloudShieldActive = action != null
                && action.ability
                        instanceof WeatherReportCloudShieldAbility
                && action.getPhase() == ActionPhase.PERFORM;
    }
}
