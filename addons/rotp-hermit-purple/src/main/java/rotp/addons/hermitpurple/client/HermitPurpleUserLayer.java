package rotp.addons.hermitpurple.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.powersystem.standpower.StandPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public final class HermitPurpleUserLayer<
        T extends LivingEntity,
        M extends HumanoidModel<T>> extends RenderLayer<T, M>
        implements FirstPersonModelLayer {
    private static final ResourceLocation DEFAULT_TEXTURE =
            HermitPurpleAddon.id(
                    "textures/entity/stand/hermito_purple.png");
    private static final ResourceLocation SLIM_TEXTURE =
            HermitPurpleAddon.id(
                    "textures/entity/stand/"
                            + "hermito_purple_slim.png");
    private static final ResourceLocation DEFAULT_THORNS =
            HermitPurpleAddon.id(
                    "textures/entity/stand/hermito_torns.png");
    private static final ResourceLocation SLIM_THORNS =
            HermitPurpleAddon.id(
                    "textures/entity/stand/"
                            + "hermito_torns_slim.png");

    private final boolean slim;

    public HermitPurpleUserLayer(
            RenderLayerParent<T, M> parent,
            boolean slim) {
        super(parent);
        this.slim = slim;
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
        if (!ClientGlobals.canSeeStands
                || entity.isInvisible()) {
            return;
        }
        HermitPurpleStandEntity controller =
                HermitPurpleAddon.controller(entity);
        StandVisualContext visualContext =
                HermitPurpleVisuals.context(
                        entity, partialTick);
        if (controller == null
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }

        renderOverlay(
                poseStack,
                buffer,
                packedLight,
                entity,
                texture(entity, baseTexture()),
                visualContext.effectiveAlpha());
        if (controller.isStandBlocking()) {
            renderOverlay(
                    poseStack,
                    buffer,
                    packedLight,
                    entity,
                    texture(entity, thornsTexture()),
                    visualContext.effectiveAlpha());
        }
    }

    private void renderOverlay(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T entity,
            ResourceLocation texture,
            float alpha) {
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(buffer, alpha);
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(texture));
        getParentModel().renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        entity, 0.0F),
                0xFFFFFFFF);
    }

    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        if (!ClientGlobals.canSeeStands
                || entity.isInvisible()
                || !(entityRenderer.getModel()
                        instanceof HumanoidModel<?> model)) {
            return;
        }
        HermitPurpleStandEntity controller =
                HermitPurpleAddon.controller(entity);
        StandVisualContext visualContext =
                HermitPurpleVisuals.context(
                        entity, partialTick);
        if (controller == null
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }

        renderFirstPersonOverlay(
                side,
                poseStack,
                buffer,
                light,
                entity,
                model,
                texture(entity, baseTexture()),
                visualContext.effectiveAlpha(),
                partialTick);
        if (controller.isStandBlocking()) {
            renderFirstPersonOverlay(
                    side,
                    poseStack,
                    buffer,
                    light,
                    entity,
                    model,
                    texture(entity, thornsTexture()),
                    visualContext.effectiveAlpha(),
                    partialTick);
        }
    }

    private static void renderFirstPersonOverlay(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            HumanoidModel<?> model,
            ResourceLocation texture,
            float alpha,
            float partialTick) {
        FirstPersonModelLayer.setupForFirstPersonRender(
                model, entity, partialTick);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(buffer, alpha);
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(texture));
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

    private ResourceLocation baseTexture() {
        return slim ? SLIM_TEXTURE : DEFAULT_TEXTURE;
    }

    private ResourceLocation thornsTexture() {
        return slim ? SLIM_THORNS : DEFAULT_THORNS;
    }

    private static ResourceLocation texture(
            LivingEntity entity,
            ResourceLocation baseTexture) {
        StandPower power = StandPower.get(entity);
        StandSkin skin =
                power != null
                        ? StandSkinsLoader.getInstance()
                                .getSkin(power)
                        : null;
        return skin != null
                ? skin.getTexture(baseTexture, baseTexture)
                : baseTexture;
    }
}
