package rotp.addons.weatherreport.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;

import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.entity.WeatherReportFugaProjectileEntity;
import net.minecraft.client.model.PufferfishBigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class WeatherReportFugaProjectileRenderer
        extends SimpleEntityRenderer<
                WeatherReportFugaProjectileEntity,
                PufferfishBigModel<
                        WeatherReportFugaProjectileEntity>> {
    public WeatherReportFugaProjectileRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                WeatherReportAddon.id(
                        "textures/entity/projectiles/pufferfish.png"),
                false);
        initModel(new StaticPufferfishModel(
                context.bakeLayer(ModelLayers.PUFFERFISH_BIG)));
    }

    private static final class StaticPufferfishModel
            extends PufferfishBigModel<
                    WeatherReportFugaProjectileEntity> {
        private StaticPufferfishModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(
                WeatherReportFugaProjectileEntity entity,
                float limbSwing,
                float limbSwingAmount,
                float ageInTicks,
                float netHeadYaw,
                float headPitch) {
            // The canonical projectile model has no fin animation.
        }
    }
}
