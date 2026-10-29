package rotp.addons.scr.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandSkinUiLayer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.scr.SilverChariotRequiemAddon;
import rotp.addons.scr.entity.SilverChariotRequiemEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class SilverChariotRequiemSpecialLayer
        extends RenderLayer<
                SilverChariotRequiemEntity,
                StandEntityModel<
                        SilverChariotRequiemEntity,
                        SilverChariotRequiemRenderState>>
        implements StandSkinUiLayer {
    public static final ResourceLocation LEGACY_ROW80_SKIN =
            ResourceLocation.fromNamespaceAndPath(
                    SilverChariotRequiemAddon.MOD_ID, "row80_legacy");
    private static final ResourceLocation OUTLINE_MODEL =
            SilverChariotRequiemAddon.id(
                    "silver_chariot_requiem_outline");
    private static final ResourceLocation OUTLINE_TEXTURE =
            SilverChariotRequiemAddon.id(
                    "textures/entity/"
                            + "silver_chariot_requiem_outline.png");
    private static final ResourceLocation ARROW_BEETLE_MODEL =
            SilverChariotRequiemAddon.id(
                    "silver_chariot_requiem_arrow_beetle");
    private static final ResourceLocation ARROW_BEETLE_TEXTURE =
            SilverChariotRequiemAddon.id(
                    "textures/entity/"
                            + "silver_chariot_requiem_arrow_beetle.png");

    public SilverChariotRequiemSpecialLayer(
            RenderLayerParent<
                    SilverChariotRequiemEntity,
                    StandEntityModel<
                            SilverChariotRequiemEntity,
                            SilverChariotRequiemRenderState>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            SilverChariotRequiemEntity entity,
            float walkAnimationPosition,
            float walkAnimationSpeed,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!(RenderStateCrutches.currentStandEntityRenderState
                instanceof SilverChariotRequiemRenderState state)) {
            return;
        }
        float lifecycleAlpha = entity.clientStuff != null
                ? entity.clientStuff.getAlpha(entity, partialTick)
                : state.alpha;
        StandVisualContext visualContext =
                StandVisualContext.world(
                        entity, lifecycleAlpha);
        renderLayers(
                poseStack,
                buffer,
                state,
                visualContext,
                true);
    }

    @Override
    public void renderForStandSkinUI(
            PoseStack poseStack,
            MultiBufferSource buffer,
            StandEntityRenderState renderState) {
        if (!(renderState
                instanceof SilverChariotRequiemRenderState state)) {
            return;
        }
        renderLayers(
                poseStack,
                buffer,
                state,
                state.visualContext != null
                        ? state.visualContext
                        : StandVisualContext.preview(),
                false);
    }

    private static void renderLayers(
            PoseStack poseStack,
            MultiBufferSource buffer,
            SilverChariotRequiemRenderState renderState,
            StandVisualContext visualContext,
            boolean alphaAlreadyApplied) {
        if (renderState.skin == null
                || LEGACY_ROW80_SKIN.equals(
                        renderState.skin.skinId)
                || visualContext.effectiveAlpha() <= 0.0F
                || renderState.isInvisibleToPlayer
                        && !renderState.visibleForSpectator
                || renderState.obstructionRenderMode
                        != StandEntityRenderState
                                .ObstructionRenderMode.NONE) {
            return;
        }

        renderModel(
                poseStack,
                buffer,
                renderState,
                visualContext,
                OUTLINE_MODEL,
                OUTLINE_TEXTURE,
                false,
                alphaAlreadyApplied);
        renderModel(
                poseStack,
                buffer,
                renderState,
                visualContext,
                ARROW_BEETLE_MODEL,
                ARROW_BEETLE_TEXTURE,
                true,
                alphaAlreadyApplied);
    }

    private static void renderModel(
            PoseStack poseStack,
            MultiBufferSource buffer,
            SilverChariotRequiemRenderState renderState,
            StandVisualContext visualContext,
            ResourceLocation modelId,
            ResourceLocation textureId,
            boolean cull,
            boolean alphaAlreadyApplied) {
        StandSkin skin = renderState.skin;
        StandEntityModel<
                SilverChariotRequiemEntity,
                SilverChariotRequiemRenderState> model =
                skin.getModel(
                        modelId,
                        definition -> new SilverChariotRequiemStandModel(
                                definition.bakeRoot()));
        if (model == null) {
            return;
        }

        model.setupAnim(renderState);
        ResourceLocation texture =
                skin.getTexture(textureId, textureId);
        RenderType renderType = cull
                ? ModRenderTypes.standTranslucentCull(texture)
                : SilverChariotRequiemRenderTypes.outline(texture);
        MultiBufferSource alphaBuffer = alphaAlreadyApplied
                ? buffer
                : AlphaMultiBufferSource.wrap(
                        buffer, visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(renderType);
        model.renderToBuffer(
                poseStack,
                vertices,
                ClientUtil.MAX_LIGHT,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }
}
