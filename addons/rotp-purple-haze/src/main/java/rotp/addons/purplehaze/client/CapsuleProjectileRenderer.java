package rotp.addons.purplehaze.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.entity.CapsuleProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class CapsuleProjectileRenderer
        extends SimpleEntityRenderer<
                CapsuleProjectileEntity,
                CapsuleProjectileModel> {
    public CapsuleProjectileRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                PurpleHazeAddon.id(
                        "textures/entity/capsule_projectile.png"),
                true);
        initModel(new CapsuleProjectileModel(
                CapsuleProjectileModel
                        .createBodyLayer()
                        .bakeRoot()));
    }

    @Override
    public void render(
            CapsuleProjectileEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext context =
                PurpleHazeVisuals.fromOwner(
                        entity.getOwner(),
                        partialTick);
        if (!PurpleHazeVisuals.canRender(context)) {
            return;
        }
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        buffer,
                        context.effectiveAlpha()),
                packedLight);
    }

    @Override
    protected void doRender(
            CapsuleProjectileEntity entity,
            CapsuleProjectileModel model,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        renderModel(
                entity,
                model,
                partialTick,
                poseStack,
                buffer.getBuffer(
                        ModRenderTypes.standTranslucentCull(
                                getTextureLocation(entity))),
                packedLight);
    }
}
