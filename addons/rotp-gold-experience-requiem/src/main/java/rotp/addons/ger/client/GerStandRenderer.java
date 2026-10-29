package rotp.addons.ger.client;

import rotp.core.client.entityrender.parsemodel.loader.RotpGeckoModelLoader;
import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.standskin.StandSkin;
import rotp.core.core.JojoMod;
import rotp.core.util.objects_java.LazyNullable;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.entity.GerStandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class GerStandRenderer extends StandEntityRenderer<
        GerStandEntity,
        StandEntityRenderState,
        StandEntityModel<GerStandEntity, StandEntityRenderState>> {
    private static final ResourceLocation DONOR_TEXTURE =
            GoldExperienceRequiemAddon.id(
                    "textures/entity/stand/ger_stand.png");

    public GerStandRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        // GER validation must not run against the core fallback model.
        this.missingSkinModel = LazyNullable.of(() -> {
            LayerDefinition defaultModel =
                    RotpGeckoModelLoader.getInstance()
                            .getModelDefinition(JojoMod.resLoc(
                                    "stand_default"));
            return defaultModel != null
                    ? super.createStandModel(defaultModel)
                    : null;
        });
    }

    @Override
    public GerStandModel createStandModel(
                    LayerDefinition definition) {
        return new GerStandModel(
                GerStandModelValidator.bakeAndValidate(definition));
    }

    @Override
    public ResourceLocation getTextureLocation(
            StandEntityRenderState renderState) {
        return texture(renderState.skin);
    }

    @Override
    public ResourceLocation getTextureLocation(
            GerStandEntity entity) {
        return texture(getStandSkin(entity));
    }

    @Override
    public void doRender(
            GerStandEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int light) {
        String diagnosticPhase = entity.tickCount >= 500
                ? "rtz"
                : entity.tickCount >= 25
                ? "idle"
                : null;
        GerStandModel.DiagnosticContext previousDiagnostic =
                diagnosticPhase != null
                        ? GerStandModel.pushDiagnostic(
                                diagnosticPhase,
                                entity.getId(),
                                entity.tickCount)
                        : null;
        try {
            super.doRender(
                    entity,
                    entityYaw,
                    partialTicks,
                    poseStack,
                    bufferSource,
                    light);
        }
        finally {
            if (diagnosticPhase != null) {
                GerStandModel.restoreDiagnostic(previousDiagnostic);
            }
        }
    }

    private static ResourceLocation texture(StandSkin skin) {
        return skin != null
                ? skin.getTexture(DONOR_TEXTURE, DONOR_TEXTURE)
                : DONOR_TEXTURE;
    }
}
