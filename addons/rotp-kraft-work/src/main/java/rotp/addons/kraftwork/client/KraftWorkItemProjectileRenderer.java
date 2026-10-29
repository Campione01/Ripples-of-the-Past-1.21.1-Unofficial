package rotp.addons.kraftwork.client;

import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.kraftwork.entity.KraftWorkItemProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 1.16's KWItemRenderer was a plain SpriteRenderer (scale 1.0, full
 * bright): every client draws a placed item, whether or not it sees Stands
 * and whether or not the Stand that placed it is still out. This is that
 * renderer's 1.21.1 form, vanilla's ThrownItemRenderer.
 */
public final class KraftWorkItemProjectileRenderer
        extends EntityRenderer<KraftWorkItemProjectile> {
    private static final float SCALE = 1.0F;
    private static final double MIN_CAMERA_DISTANCE_SQR = 12.25D;
    private final ItemRenderer itemRenderer;

    public KraftWorkItemProjectileRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public ResourceLocation getTextureLocation(
            KraftWorkItemProjectile entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    protected int getBlockLightLevel(
            KraftWorkItemProjectile entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(
            KraftWorkItemProjectile entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        if (entity.tickCount < 2
                && entityRenderDispatcher.camera.getEntity()
                        .distanceToSqr(entity)
                        < MIN_CAMERA_DISTANCE_SQR) {
            return;
        }
        KraftWorkItemRenderDiagnostics.CountingMultiBufferSource
                counting = KraftWorkItemRenderDiagnostics.observing()
                        ? KraftWorkItemRenderDiagnostics.counting(buffer)
                        : null;
        MultiBufferSource itemBuffer = counting != null
                ? counting
                : buffer;
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.mulPose(
                entityRenderDispatcher.cameraOrientation());
        itemRenderer.renderStatic(
                entity.getItem(),
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                itemBuffer,
                entity.level(),
                entity.getId());
        poseStack.popPose();
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight);
        StandEntity controller = entity.resolveController();
        if (controller != null) {
            KraftWorkItemRenderDiagnostics.recordCompletedDraw(
                    entity,
                    controller,
                    packedLight,
                    counting != null
                            ? counting.submittedVertices()
                            : 0);
        }
    }
}
