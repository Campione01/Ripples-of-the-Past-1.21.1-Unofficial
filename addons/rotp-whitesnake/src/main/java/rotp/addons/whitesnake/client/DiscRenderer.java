package rotp.addons.whitesnake.client;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.entity.projectile.DiscProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class DiscRenderer
        extends SimpleEntityRenderer<DiscProjectile, DiscModel> {
    public DiscRenderer(EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                WhitesnakeAddon.id(
                        "textures/entity/projectiles/disc.png"),
                false);
        initModel(new DiscModel(
                context.bakeLayer(DiscModel.LAYER_LOCATION)));
        offsetModelByEntityHeight(false);
    }

    @Override
    public void render(
            DiscProjectile entity,
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
            DiscProjectile entity,
            DiscModel model,
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
            DiscProjectile projectile,
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
