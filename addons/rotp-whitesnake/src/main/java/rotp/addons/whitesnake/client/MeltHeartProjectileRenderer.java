package rotp.addons.whitesnake.client;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.entity.projectile.MeltHeartProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class MeltHeartProjectileRenderer
        extends SimpleEntityRenderer<
                MeltHeartProjectile, MeltHeartProjectileModel> {
    public MeltHeartProjectileRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                WhitesnakeAddon.id(
                        "textures/entity/projectiles/melt_heart.png"),
                true);
        initModel(new MeltHeartProjectileModel(
                context.bakeLayer(
                        MeltHeartProjectileModel.LAYER_LOCATION)));
    }

    @Override
    public void render(
            MeltHeartProjectile entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                visualContext(entity, partialTick);
        if (visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha()),
                packedLight);
    }

    @Override
    protected void doRender(
            MeltHeartProjectile entity,
            MeltHeartProjectileModel model,
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

    @Nullable
    private static StandVisualContext visualContext(
            MeltHeartProjectile projectile,
            float partialTick) {
        StandEntity controller = projectile.getOwner()
                instanceof StandEntity stand ? stand : null;
        if (controller == null || !controller.isAlive()) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff != null
                ? controller.clientStuff.getAlpha(
                        controller, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }
}
