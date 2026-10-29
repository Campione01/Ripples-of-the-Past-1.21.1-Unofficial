package rotp.addons.greenday.client;

import rotp.core.api.client.render.FirstPersonPostArmLayers;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.greenday.GreenDayAddon;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class GreenDayMoldOverlayRenderer {
    private GreenDayMoldOverlayRenderer() {}

    public static void renderFirstPersonMold(
            FirstPersonPostArmLayers.Context arm) {
        if (!(arm.entity() instanceof AbstractClientPlayer player)
                || player.isSpectator()) {
            return;
        }
        MobEffectInstance mold =
                player.getEffect(GreenDayAddon.MOLD);
        if (mold == null) {
            return;
        }
        StandVisualContext context =
                GreenDayVisuals.fromMoldTarget(
                        player, arm.partialTick());
        if (!GreenDayVisuals.canRender(context)) {
            return;
        }

        int stage = Math.min(mold.getAmplifier() / 2, 4) + 1;
        ResourceLocation texture = GreenDayAddon.id(
                "textures/entity/layer/mold/t64x64/"
                        + stage + ".png");
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        arm.buffer(),
                        context.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        texture,
                        context.classicOutline()));
        arm.renderArm(vertices);
    }

    /**
     * The mold overlay on the wearer's own body.
     *
     * <p>This used to run from {@code RenderLivingEvent.Post}, but NeoForge fires that event after
     * {@code LivingEntityRenderer.render} has already popped the pose it set up, so the model was re-rendered outside
     * the entity's transform and never appeared. 1.16's Green Day add-on hung its MoldLayer on the renderer;
     * a {@link RenderLayer} is the same thing here and runs inside the body transform.
     */
    public static final class Layer<
            T extends LivingEntity, M extends EntityModel<T>>
            extends RenderLayer<T, M> {
        public Layer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(
                PoseStack poseStack,
                MultiBufferSource buffer,
                int packedLight,
                T entity,
                float limbSwing,
                float limbSwingAmount,
                float partialTick,
                float ageInTicks,
                float netHeadYaw,
                float headPitch) {
            MobEffectInstance mold =
                    entity.getEffect(GreenDayAddon.MOLD);
            if (mold == null || entity.isInvisible()) {
                return;
            }
            StandVisualContext context =
                    GreenDayVisuals.fromMoldTarget(
                            entity, partialTick);
            if (!GreenDayVisuals.canRender(context)) {
                return;
            }

            int stage = Math.min(mold.getAmplifier() / 2, 4) + 1;
            ResourceLocation texture = GreenDayAddon.id(
                    "textures/entity/layer/mold/t64x64/"
                            + stage + ".png");
            MultiBufferSource alphaBuffer =
                    AlphaMultiBufferSource.wrap(
                            buffer, context.effectiveAlpha());
            VertexConsumer vertices = alphaBuffer.getBuffer(
                    ModRenderTypes.standTranslucent(
                            texture,
                            context.classicOutline()));
            poseStack.pushPose();
            poseStack.scale(1.002F, 1.002F, 1.002F);
            getParentModel().renderToBuffer(
                    poseStack,
                    vertices,
                    packedLight,
                    LivingEntityRenderer.getOverlayCoords(
                            entity, 0.0F),
                    0xFFFFFFFF);
            poseStack.popPose();
        }
    }
}
