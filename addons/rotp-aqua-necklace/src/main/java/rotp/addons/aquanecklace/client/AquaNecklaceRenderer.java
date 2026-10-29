package rotp.addons.aquanecklace.client;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;

import rotp.addons.aquanecklace.AquaNecklaceAddon;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class AquaNecklaceRenderer
        extends StandEntityRenderer<
                AquaNecklaceEntity,
                AquaNecklaceRenderState,
                AquaNecklaceModel> {
    private static final int SUMMON_POSE_TICKS = 20;
    private static final ActionAnimIdentifier VAPOR_IDLE_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "vapor_idle", true);
    private static final ResourceLocation SOLID_TEXTURE =
            AquaNecklaceAddon.id(
                    "textures/entity/aqua_necklace.png");
    private static final ResourceLocation LIQUID_TEXTURE =
            AquaNecklaceAddon.id(
                    "textures/entity/aqua_necklace_liquid.png");
    private static final ResourceLocation VAPOR_TEXTURE =
            AquaNecklaceAddon.id(
                    "textures/entity/aqua_necklace_vapor.png");

    public AquaNecklaceRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AquaNecklaceRenderState createRenderState() {
        return new AquaNecklaceRenderState();
    }

    @Override
    public AquaNecklaceModel createStandModel(
            LayerDefinition definition) {
        return new AquaNecklaceModel(definition.bakeRoot());
    }

    @Override
    public void extractRenderState(
            AquaNecklaceEntity entity,
            AquaNecklaceRenderState renderState,
            float partialTick) {
        super.extractRenderState(
                entity, renderState, partialTick);
        renderState.materialState =
                entity.getMaterialState();
        if (renderState.materialState
                        == AquaNecklaceEntity
                                .MaterialState.VAPOR
                && entity.getCurStandAction() == null
                && entity.tickCount > SUMMON_POSE_TICKS
                && renderState.skin != null) {
            RotpAnimDefinition vaporIdle =
                    renderState.skin.getStandAnimation(
                            animations ->
                                    animations.getNamedAnim(
                                            VAPOR_IDLE_ANIM));
            if (vaporIdle != null) {
                float idleTicks =
                        entity.tickCount
                                - entity.nonIdlePoseTimeStamp
                                + partialTick;
                renderState.action.animId =
                        VAPOR_IDLE_ANIM;
                renderState.action.pose =
                        vaporIdle.calcAnimPose(
                                AnimMolangVariables.extract(
                                        entity, partialTick),
                                null,
                                vaporIdle.getAnimTime(
                                        idleTicks),
                                1.0F);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(
            AquaNecklaceRenderState renderState) {
        ResourceLocation texture =
                switch (renderState.materialState) {
                    case VAPOR -> VAPOR_TEXTURE;
                    case LIQUID -> LIQUID_TEXTURE;
                    case SOLID -> SOLID_TEXTURE;
                };
        return renderState.skin != null
                ? renderState.skin.getTexture(
                        texture, MISSING_TEXTURE)
                : MISSING_TEXTURE;
    }
}
