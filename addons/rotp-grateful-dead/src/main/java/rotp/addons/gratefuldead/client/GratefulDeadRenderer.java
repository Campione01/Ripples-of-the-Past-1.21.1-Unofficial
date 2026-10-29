package rotp.addons.gratefuldead.client;

import java.util.function.Consumer;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.entity.GratefulDeadStandEntity;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class GratefulDeadRenderer
        extends StandEntityRenderer<
                GratefulDeadStandEntity,
                GratefulDeadRenderState,
                StandEntityModel<
                        GratefulDeadStandEntity,
                        GratefulDeadRenderState>> {
    private static final float LEGACY_MODEL_SCALE = 0.8F;

    public GratefulDeadRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        addLayer(new AgingStandLayer(this));
    }

    @Override
    public GratefulDeadRenderState createRenderState() {
        return new GratefulDeadRenderState();
    }

    @Override
    protected void scale(
            GratefulDeadStandEntity entity,
            PoseStack poseStack,
            float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(
                LEGACY_MODEL_SCALE,
                LEGACY_MODEL_SCALE,
                LEGACY_MODEL_SCALE);
    }

    @Override
    public void extractRenderState(
            GratefulDeadStandEntity entity,
            GratefulDeadRenderState renderState,
            float partialTick) {
        super.extractRenderState(
                entity, renderState, partialTick);
        LivingEntity user = entity.getUser();
        renderState.controllerAging =
                user != null
                                && user.hasData(
                                        GratefulDeadAddon
                                                .LIVING_AGING)
                        ? LivingAgingState.get(user)
                                .progress()
                        : 0.0F;
        if (renderState.visualContext == null) {
            renderState.visualContext =
                    StandVisualContext.world(
                            entity, renderState.alpha);
        }
    }

    @Override
    public void doRender(
            GratefulDeadStandEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light) {
        if (!(RenderStateCrutches
                        .currentStandEntityRenderState
                instanceof GratefulDeadRenderState state)) {
            super.doRender(
                    entity,
                    entityYaw,
                    partialTicks,
                    poseStack,
                    bufferSource,
                    light);
            return;
        }
        StandVisualContext visualContext =
                state.visualContext != null
                        ? state.visualContext
                        : StandVisualContext.world(
                                entity, state.alpha);
        float effectiveAlpha =
                visualContext.effectiveAlpha();
        if (effectiveAlpha <= 0.0F) {
            return;
        }
        float stateAlpha = state.alpha;
        state.alpha = 1.0F;
        model.setAlpha(1.0F);
        try {
            super.doRender(
                    entity,
                    entityYaw,
                    partialTicks,
                    poseStack,
                    AlphaMultiBufferSource.wrap(
                            bufferSource, effectiveAlpha),
                    light);
        }
        finally {
            state.alpha = stateAlpha;
            model.setAlpha(stateAlpha);
        }
    }

    @Override
    protected RenderType getRenderType(
            GratefulDeadStandEntity entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        ResourceLocation texture =
                getTextureLocation(entity);
        if (RenderStateCrutches
                        .currentStandEntityRenderState
                instanceof GratefulDeadRenderState state) {
            if (state.obstructionRenderMode
                    == StandEntityRenderState
                            .ObstructionRenderMode
                            .CLASSIC_OUTLINE) {
                return null;
            }
            StandVisualContext visualContext =
                    state.visualContext;
            if (visualContext != null
                    && visualContext.effectiveAlpha()
                            <= 0.0F) {
                return glowing
                        ? RenderType.outline(texture)
                        : null;
            }
        }
        if (translucent || bodyVisible) {
            return ModRenderTypes.standTranslucent(
                    texture);
        }
        return glowing
                ? RenderType.outline(texture)
                : null;
    }

    @Override
    public void renderForStandSkinUI(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Consumer<GratefulDeadRenderState>
                    extractRenderState) {
        super.renderForStandSkinUI(
                poseStack,
                AlphaMultiBufferSource.wrap(
                        bufferSource, 1.0F),
                extractRenderState);
    }

}
