package rotp.addons.whitealbum.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.whitealbum.WhiteAlbumAddon;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

final class WhiteAlbumVisuals {
    static final ResourceLocation SUIT_DEFAULT =
            WhiteAlbumAddon.id(
                    "textures/entity/stand/white_album.png");
    static final ResourceLocation SUIT_SLIM =
            WhiteAlbumAddon.id(
                    "textures/entity/stand/white_album_slim.png");
    static final ResourceLocation SHIELD_DEFAULT =
            WhiteAlbumAddon.id(
                    "textures/entity/stand/ice_shield.png");
    static final ResourceLocation SHIELD_SLIM =
            WhiteAlbumAddon.id(
                    "textures/entity/stand/ice_shield_slim.png");

    private WhiteAlbumVisuals() {}

    static RenderState resolve(
            LivingEntity user,
            float partialTick) {
        if (!ClientGlobals.canSeeStands
                || user.isInvisible()
                || user.isSpectator()) {
            return null;
        }
        WhiteAlbumControllerEntity controller =
                WhiteAlbumAddon.controller(user);
        if (controller == null
                || controller.clientStuff == null) {
            return null;
        }
        float lifecycleAlpha =
                controller.clientStuff.getAlpha(
                        controller, partialTick);
        StandVisualContext context =
                StandVisualContext.world(
                        controller, lifecycleAlpha);
        return context.effectiveAlpha() > 0.0F
                ? new RenderState(controller, context)
                : null;
    }

    static ResourceLocation resolveTexture(
            WhiteAlbumControllerEntity controller,
            ResourceLocation baseTexture) {
        StandSkin skin =
                StandSkinsLoader.getInstance().getSkin(controller);
        return skin != null
                ? skin.getTexture(baseTexture)
                : baseTexture;
    }

    static <T extends LivingEntity, M extends HumanoidModel<T>>
            void prepareThirdPerson(
                    WhiteAlbumWearableModel<T> model,
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
        copyVisibility(model, parent);
    }

    static void renderModel(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            LivingEntity entity,
            WhiteAlbumControllerEntity controller,
            WhiteAlbumWearableModel<?> model,
            ResourceLocation texture,
            StandVisualContext context) {
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        context.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        texture,
                        context.classicOutline()));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        entity, 0.0F),
                0xFFFFFFFF);
        WhiteAlbumRenderDiagnostics.recordLayerDraw(
                entity,
                controller,
                model,
                texture,
                context);
    }

    static void renderFirstPersonArm(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            WhiteAlbumWearableModel<?> model,
            ResourceLocation texture,
            StandVisualContext context,
            float partialTick) {
        model.setAllVisible(true);
        FirstPersonModelLayer.setupForFirstPersonRender(
                cast(model), entity, partialTick);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        context.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        texture,
                        context.classicOutline()));
        FirstPersonModelLayer.renderArmAndOuter(
                model,
                side,
                poseStack,
                vertices,
                light,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                FirstPersonModelLayer
                        .isRipplesAnimPlaying(model));
    }

    @SuppressWarnings("unchecked")
    private static HumanoidModel<LivingEntity> cast(
            HumanoidModel<?> model) {
        return (HumanoidModel<LivingEntity>) model;
    }

    private static void copyVisibility(
            WhiteAlbumWearableModel<?> model,
            HumanoidModel<?> parent) {
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

    record RenderState(
            WhiteAlbumControllerEntity controller,
            StandVisualContext context) {}
}
