package rotp.addons.yellowtemperance.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

final class YellowTemperanceVisuals {
    static final ResourceLocation DEFAULT_TEXTURE =
            YellowTemperanceAddon.id(
                    "textures/entity/stand/yellow_temperance.png");
    static final ResourceLocation SLIM_TEXTURE =
            YellowTemperanceAddon.id(
                    "textures/entity/stand/yellow_temperance_slim.png");
    static final ResourceLocation EROSION_TEXTURE =
            YellowTemperanceAddon.id(
                    "textures/entity/layer/erosion_256.png");

    private YellowTemperanceVisuals() {}

    static RenderState resolve(
            LivingEntity user, float partialTick) {
        if (!ClientGlobals.canSeeStands
                || user.isInvisible()
                || user.isSpectator()) {
            return null;
        }
        YellowTemperanceControllerEntity controller =
                YellowTemperanceAddon.controller(user);
        return resolveController(controller, partialTick);
    }

    static RenderState resolveController(
            YellowTemperanceControllerEntity controller,
            float partialTick) {
        if (controller == null || controller.clientStuff == null) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff.getAlpha(
                controller, partialTick);
        StandVisualContext context = StandVisualContext.world(
                controller, lifecycleAlpha);
        return context.effectiveAlpha() > 0.0F
                ? new RenderState(controller, context)
                : null;
    }

    static <T extends LivingEntity, M extends HumanoidModel<T>>
            void prepareThirdPerson(
                    YellowTemperanceWearableModel<T> model,
                    M parent,
                    T entity,
                    float limbSwing,
                    float limbSwingAmount,
                    float partialTick,
                    float ageInTicks,
                    float netHeadYaw,
                    float headPitch) {
        model.prepareMobModel(
                entity,
                limbSwing,
                limbSwingAmount,
                partialTick);
        parent.copyPropertiesTo(model);
        model.setupAnim(
                entity,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
        model.head.visible = parent.head.visible;
        model.hat.visible = parent.hat.visible;
        model.body.visible = parent.body.visible;
        model.jacket.visible = parent.body.visible;
        model.rightArm.visible = parent.rightArm.visible;
        model.rightSleeve.visible = parent.rightArm.visible;
        model.leftArm.visible = parent.leftArm.visible;
        model.leftSleeve.visible = parent.leftArm.visible;
        model.rightLeg.visible = parent.rightLeg.visible;
        model.rightPants.visible = parent.rightLeg.visible;
        model.leftLeg.visible = parent.leftLeg.visible;
        model.leftPants.visible = parent.leftLeg.visible;
    }

    static void renderModel(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            LivingEntity entity,
            YellowTemperanceWearableModel<?> model,
            ResourceLocation texture,
            StandVisualContext context) {
        MultiBufferSource alphaBuffer = AlphaMultiBufferSource.wrap(
                buffer, context.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        texture, context.classicOutline()));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                0xFFFFFFFF);
    }

    static void renderFirstPersonArm(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            YellowTemperanceWearableModel<?> model,
            ResourceLocation texture,
            StandVisualContext context,
            float partialTick) {
        model.setAllVisible(true);
        FirstPersonModelLayer.setupForFirstPersonRender(
                cast(model), entity, partialTick);
        MultiBufferSource alphaBuffer = AlphaMultiBufferSource.wrap(
                buffer, context.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        texture, context.classicOutline()));
        FirstPersonModelLayer.renderArmAndOuter(
                model,
                side,
                poseStack,
                vertices,
                light,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                FirstPersonModelLayer.isRipplesAnimPlaying(model));
    }

    @SuppressWarnings("unchecked")
    private static HumanoidModel<LivingEntity> cast(
            HumanoidModel<?> model) {
        return (HumanoidModel<LivingEntity>) model;
    }

    record RenderState(
            YellowTemperanceControllerEntity controller,
            StandVisualContext context) {}
}
