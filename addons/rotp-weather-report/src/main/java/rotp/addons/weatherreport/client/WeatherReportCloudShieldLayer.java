package rotp.addons.weatherreport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** The 1.16 {@code WeatherReportEffectCloudLayer}: clouds circling the user while the cloud shield effect lasts. */
public final class WeatherReportCloudShieldLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation CLOUDS =
            WeatherReportAddon.id("textures/entity/layer/clouds.png");

    private final WeatherReportCloudsModel cloudModel;

    public WeatherReportCloudShieldLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            WeatherReportCloudsModel cloudModel) {
        super(parent);
        this.cloudModel = cloudModel;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!player.isAlive()
                || !player.hasEffect(WeatherReportAddon.CLOUD_SHIELD_EFFECT)) {
            return;
        }
        cloudModel.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
        getParentModel().copyPropertiesTo(cloudModel);
        cloudModel.setAllVisible(true);
        cloudModel.hat.visible = false;
        cloudModel.jacket.visible = false;
        cloudModel.leftSleeve.visible = false;
        cloudModel.rightSleeve.visible = false;
        cloudModel.leftPants.visible = false;
        cloudModel.rightPants.visible = false;
        cloudModel.updateRotation();
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(CLOUDS));
        cloudModel.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                0xFFFFFFFF);
    }
}
