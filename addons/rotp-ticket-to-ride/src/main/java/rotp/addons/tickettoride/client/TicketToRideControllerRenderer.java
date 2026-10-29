package rotp.addons.tickettoride.client;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderState;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.entity.TicketToRideStandEntity;
import rotp.addons.tickettoride.state.LoveTrainService;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class TicketToRideControllerRenderer
        extends StandEntityRenderer<
                TicketToRideStandEntity,
                StandEntityRenderState,
                StandEntityModel<
                        TicketToRideStandEntity,
                        StandEntityRenderState>> {
    private static final ResourceLocation STAND_TEXTURE =
            TicketToRideAddon.id(
                    "textures/entity/stand/item_stand.png");
    private static final ResourceLocation WALL_TEXTURE =
            TicketToRideAddon.id(
                    "textures/particle/"
                            + "love_train_light_wall.png");

    public TicketToRideControllerRenderer(
            EntityRendererProvider.Context context) {
        super(context, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(
            TicketToRideStandEntity entity) {
        return STAND_TEXTURE;
    }

    @Override
    public ResourceLocation getTextureLocation(
            StandEntityRenderState renderState) {
        return STAND_TEXTURE;
    }

    @Override
    public boolean shouldRender(
            TicketToRideStandEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return entity.getUser() != null
                || super.shouldRender(
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ);
    }

    @Override
    public void render(
            TicketToRideStandEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        // The donor renders the normal humanoid Stand shell at all times.
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                buffer,
                packedLight);

        LivingEntity user = entity.getUser();
        boolean effectPresent = user != null
                && user.hasEffect(TicketToRideAddon.LOVE_TRAIN);
        if (user == null
                || !effectPresent) {
            return;
        }
        // Love Train outlives the transparent controller's summon cycle.
        // Apply the configured Stand opacity without its retract alpha.
        StandVisualContext visualContext =
                StandVisualContext.world(
                        entity, 1.0F);
        if (visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(WALL_TEXTURE));
        float radius = LoveTrainService.visualRadius(user);
        int columns = Math.max(
                3,
                Math.min(
                        50,
                        Math.round(
                                (float) (Math.PI * 2.0D * radius
                                        / 1.5D))));
        int submittedVertices = 0;
        Vec3 userOffset = user.getPosition(partialTick)
                .subtract(entity.getPosition(partialTick));
        poseStack.pushPose();
        poseStack.translate(
                userOffset.x,
                userOffset.y,
                userOffset.z);
        for (int i = 0; i < columns; i++) {
            double angle = Math.PI * 2.0D * i / columns;
            double next = Math.PI * 2.0D * (i + 1) / columns;
            float x0 = (float) Math.cos(angle) * radius;
            float z0 = (float) Math.sin(angle) * radius;
            float x1 = (float) Math.cos(next) * radius;
            float z1 = (float) Math.sin(next) * radius;
            wall(
                    poseStack,
                    vertices,
                    x0,
                    z0,
                    x1,
                    z1,
                    3.5F);
            submittedVertices += 8;
        }
        poseStack.popPose();
        TicketToRideClientDiagnostics.recordWallSubmission(
                user,
                columns,
                submittedVertices,
                radius,
                visualContext.effectiveAlpha(),
                WALL_TEXTURE);
    }

    private static void wall(
            PoseStack poseStack,
            VertexConsumer vertices,
            float x0,
            float z0,
            float x1,
            float z1,
            float height) {
        vertex(poseStack, vertices, x0, 0.0F, z0, 0.0F, 1.0F);
        vertex(poseStack, vertices, x1, 0.0F, z1, 1.0F, 1.0F);
        vertex(poseStack, vertices, x1, height, z1, 1.0F, 0.0F);
        vertex(poseStack, vertices, x0, height, z0, 0.0F, 0.0F);
        vertex(poseStack, vertices, x1, 0.0F, z1, 0.0F, 1.0F);
        vertex(poseStack, vertices, x0, 0.0F, z0, 1.0F, 1.0F);
        vertex(poseStack, vertices, x0, height, z0, 1.0F, 0.0F);
        vertex(poseStack, vertices, x1, height, z1, 0.0F, 0.0F);
    }

    private static void vertex(
            PoseStack poseStack,
            VertexConsumer vertices,
            float x,
            float y,
            float z,
            float u,
            float v) {
        PoseStack.Pose pose = poseStack.last();
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(ClientUtil.MAX_LIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
