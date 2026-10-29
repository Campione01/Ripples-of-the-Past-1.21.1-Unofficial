package rotp.addons.catchtherainbow.client;

import java.util.UUID;

import rotp.core.api.client.render.EntityPostRenderExtensions;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.client.RainBladeGeometryReceipt.ModelGeometryReceipt;
import rotp.addons.catchtherainbow.entity.RainBladeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class RainBladeRenderer
        extends SimpleEntityRenderer<
                RainBladeEntity, RainBladeModel> {
    private static long frustumAcceptCount;
    private static long lastFrustumFrame = Long.MIN_VALUE;
    private static long drawCount;
    private static long lastDrawFrame = Long.MIN_VALUE;
    private static UUID lastEntityUuid;
    private static ResourceLocation lastTexture;
    private static long lastGeometryFrame = Long.MIN_VALUE;
    private static UUID lastGeometryEntityUuid;
    private static ModelGeometryReceipt lastModelGeometry =
            ModelGeometryReceipt.empty();

    public RainBladeRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                CatchTheRainbowAddon.id(
                        "textures/entity/projectiles/"
                                + "catch_the_rainbow_rain_blade.png"),
                false);
        initModel(new RainBladeModel(
                context.bakeLayer(
                        RainBladeModel.LAYER_LOCATION)));
        offsetModelByEntityHeight(false);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(
            RainBladeEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        boolean accepted = super.shouldRender(
                entity, frustum, cameraX, cameraY, cameraZ);
        if (accepted) {
            synchronized (RainBladeRenderer.class) {
                frustumAcceptCount++;
                lastFrustumFrame =
                        EntityPostRenderExtensions.currentFrameId();
                lastEntityUuid = entity.getUUID();
            }
        }
        return accepted;
    }

    @Override
    public void render(
            RainBladeEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                buffer,
                packedLight);
        synchronized (RainBladeRenderer.class) {
            drawCount++;
            lastDrawFrame = EntityPostRenderExtensions.currentFrameId();
            lastEntityUuid = entity.getUUID();
            lastTexture = getTextureLocation(entity);
        }
    }

    public static synchronized void resetDiagnostics() {
        frustumAcceptCount = 0L;
        lastFrustumFrame = Long.MIN_VALUE;
        drawCount = 0L;
        lastDrawFrame = Long.MIN_VALUE;
        lastEntityUuid = null;
        lastTexture = null;
        lastGeometryFrame = Long.MIN_VALUE;
        lastGeometryEntityUuid = null;
        lastModelGeometry = ModelGeometryReceipt.empty();
    }

    static synchronized void recordModelGeometry(
            UUID entityUuid,
            ModelGeometryReceipt receipt) {
        lastGeometryFrame =
                EntityPostRenderExtensions.currentFrameId();
        lastGeometryEntityUuid = entityUuid;
        lastModelGeometry = receipt != null
                ? receipt : ModelGeometryReceipt.empty();
    }

    public static synchronized DiagnosticSnapshot diagnosticSnapshot() {
        return new DiagnosticSnapshot(
                frustumAcceptCount,
                lastFrustumFrame,
                drawCount,
                lastDrawFrame,
                lastEntityUuid,
                lastTexture,
                lastGeometryFrame,
                lastGeometryEntityUuid,
                lastModelGeometry);
    }

    public record DiagnosticSnapshot(
            long frustumAcceptCount,
            long lastFrustumFrame,
            long drawCount,
            long lastDrawFrame,
            UUID lastEntityUuid,
            ResourceLocation lastTexture,
            long lastGeometryFrame,
            UUID lastGeometryEntityUuid,
            ModelGeometryReceipt modelGeometry) {}
}
