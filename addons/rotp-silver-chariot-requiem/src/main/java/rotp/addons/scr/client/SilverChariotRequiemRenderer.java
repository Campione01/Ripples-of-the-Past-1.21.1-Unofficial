package rotp.addons.scr.client;

import java.util.function.Consumer;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.scr.entity.SilverChariotRequiemEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

public final class SilverChariotRequiemRenderer
        extends StandEntityRenderer<
                SilverChariotRequiemEntity,
                SilverChariotRequiemRenderState,
                StandEntityModel<
                        SilverChariotRequiemEntity,
                        SilverChariotRequiemRenderState>> {
    public SilverChariotRequiemRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        addLayer(new SilverChariotRequiemSpecialLayer(this));
    }

    @Override
    public StandEntityModel<
            SilverChariotRequiemEntity,
            SilverChariotRequiemRenderState> createStandModel(
                    LayerDefinition definition) {
        ModelPart resourceModel = definition.bakeRoot();
        if (resourceModel.hasChild(
                SilverChariotRequiemLegacyModel.MARKER)) {
            return new SilverChariotRequiemStandModel(
                    SilverChariotRequiemLegacyModel
                            .createLayer()
                            .bakeRoot());
        }
        return new SilverChariotRequiemStandModel(resourceModel);
    }

    @Override
    public SilverChariotRequiemRenderState createRenderState() {
        return new SilverChariotRequiemRenderState();
    }

    @Override
    public void extractRenderState(
            SilverChariotRequiemEntity entity,
            SilverChariotRequiemRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        if (renderState.visualContext == null) {
            renderState.visualContext = StandVisualContext.world(
                    entity, renderState.alpha);
        }
    }

    @Override
    public void doRender(
            SilverChariotRequiemEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light) {
        if (!(RenderStateCrutches.currentStandEntityRenderState
                instanceof SilverChariotRequiemRenderState state)) {
            super.doRender(
                    entity,
                    entityYaw,
                    partialTicks,
                    poseStack,
                    bufferSource,
                    ClientUtil.MAX_LIGHT);
            return;
        }

        StandVisualContext visualContext =
                state.visualContext != null
                        ? state.visualContext
                        : StandVisualContext.world(
                                entity, state.alpha);
        float effectiveAlpha = visualContext.effectiveAlpha();
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
                    ClientUtil.MAX_LIGHT);
        }
        finally {
            state.alpha = stateAlpha;
            model.setAlpha(stateAlpha);
        }
    }

    @Override
    protected RenderType getRenderType(
            SilverChariotRequiemEntity entity,
            boolean bodyVisible,
            boolean translucent,
            boolean glowing) {
        ResourceLocation texture = getTextureLocation(entity);
        if (RenderStateCrutches.currentStandEntityRenderState
                instanceof SilverChariotRequiemRenderState state) {
            if (state.obstructionRenderMode
                    == StandEntityRenderState
                            .ObstructionRenderMode.CLASSIC_OUTLINE) {
                return null;
            }
            StandVisualContext visualContext =
                    state.visualContext;
            if (visualContext != null
                    && visualContext.effectiveAlpha() <= 0.0F) {
                return glowing ? RenderType.outline(texture) : null;
            }
        }
        if (translucent || bodyVisible) {
            return ModRenderTypes.standTranslucentCull(texture);
        }
        return glowing ? RenderType.outline(texture) : null;
    }

    @Override
    public void renderForStandSkinUI(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Consumer<SilverChariotRequiemRenderState>
                    extractRenderState) {
        super.renderForStandSkinUI(
                poseStack,
                AlphaMultiBufferSource.wrap(bufferSource, 1.0F),
                extractRenderState);
    }
}
