package rotp.addons.planetwaves.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.planetwaves.entity.MeteoriteEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MeteoriteRenderer
        extends EntityRenderer<MeteoriteEntity> {
    private static final ItemStack FIRE_CHARGE =
            new ItemStack(Items.FIRE_CHARGE);

    private final ItemRenderer itemRenderer;

    public MeteoriteRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public ResourceLocation getTextureLocation(
            MeteoriteEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(
            MeteoriteEntity entity,
            net.minecraft.client.renderer.culling.Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        StandVisualContext visualContext =
                visualContext(entity, 1.0F);
        return ClientGlobals.canSeeStands
                && visualContext != null
                && visualContext.effectiveAlpha() > 0.0F
                && super.shouldRender(
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ);
    }

    @Override
    public void render(
            MeteoriteEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                visualContext(entity, partialTick);
        if (!ClientGlobals.canSeeStands
                || visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        poseStack.pushPose();
        poseStack.translate(
                0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        poseStack.mulPose(
                entityRenderDispatcher.cameraOrientation());
        float size = entity.getMeteorSize();
        poseStack.scale(size, size, size);
        itemRenderer.renderStatic(
                FIRE_CHARGE,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                alphaBuffer,
                entity.level(),
                entity.getId());
        poseStack.popPose();
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
    }

    private static StandVisualContext visualContext(
            MeteoriteEntity meteor,
            float partialTick) {
        var controller = meteor.resolveController();
        if (controller == null
                || !controller.isAlive()
                || controller.clientStuff == null) {
            return null;
        }
        float lifecycleAlpha =
                controller.clientStuff.getAlpha(
                        controller, partialTick);
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }
}
