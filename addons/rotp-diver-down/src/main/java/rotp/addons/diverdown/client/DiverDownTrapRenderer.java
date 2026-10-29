package rotp.addons.diverdown.client;

import java.util.UUID;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandOpacityPolicy;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** 1.16 DiverDownArmRenderer: the trap's arm on Diver Down's own texture. */
public final class DiverDownTrapRenderer
        extends SimpleEntityRenderer<
                KineticTrapEntity, DiverDownTrapModel> {
    public DiverDownTrapRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                DiverDownAddon.id(
                        "textures/entity/stand/diver_down.png"),
                false);
        initModel(new DiverDownTrapModel(
                context.bakeLayer(
                        DiverDownTrapModel.LAYER_LOCATION)));
        offsetModelByEntityHeight(false);
        shadowRadius = 0.0F;
    }

    @Override
    protected boolean shouldRender(KineticTrapEntity entity) {
        return alpha(entity, 1.0F) > 0.0F
                && super.shouldRender(entity);
    }

    // The owner's Diver Down lends the arm its fade; 1.16
    // DiverDownArmRenderer drew a firing arm with no Stand out as well.
    private static float alpha(
            KineticTrapEntity entity, float partialTick) {
        if (!ClientGlobals.canSeeStands) {
            return 0.0F;
        }
        StandVisualContext visualContext =
                DiverDownAdditionalVisuals.ownerContext(
                        entity.level(),
                        entity.getOwnerUuid(),
                        partialTick);
        if (visualContext != null) {
            return visualContext.effectiveAlpha();
        }
        UUID ownerUuid = entity.getOwnerUuid();
        return entity.isTriggered()
                ? StandOpacityPolicy.apply(
                        1.0F,
                        ownerUuid != null
                                ? entity.level().getPlayerByUUID(ownerUuid)
                                : null)
                : 0.0F;
    }

    // 1.16 getSkyLightLevel: the trap sits inside a block, so the arm takes
    // the brightest sky light of the air blocks around it when there are
    // more than two of them.
    @Override
    protected int getSkyLightLevel(
            KineticTrapEntity entity, BlockPos pos) {
        int airBlocks = 0;
        int brightest = 0;
        for (BlockPos around : BlockPos.betweenClosed(
                pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (entity.level().getBlockState(around).is(Blocks.AIR)) {
                airBlocks++;
                brightest = Math.max(
                        brightest,
                        super.getSkyLightLevel(entity, around));
            }
        }
        return airBlocks > 2
                ? brightest
                : super.getSkyLightLevel(entity, pos);
    }

    @Override
    protected void doRender(
            KineticTrapEntity entity,
            DiverDownTrapModel model,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float alpha = alpha(entity, partialTick);
        if (alpha <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(buffer, alpha);
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucentCull(
                        getTextureLocation(entity)));
        // 1.16 drew this model without the living renderers' flip, which
        // SimpleEntityRenderer applies: undo it
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        poseStack.popPose();
    }
}
