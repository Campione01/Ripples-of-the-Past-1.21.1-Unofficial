package rotp.addons.ger.client;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.entity.GerBeamEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GerBeamRenderer
        extends EntityRenderer<GerBeamEntity> {
    private static final ResourceLocation TEXTURE =
            GoldExperienceRequiemAddon.id(
                    "textures/effect/lifeshot.png");
    private static final double WIDTH = 0.18D;

    public GerBeamRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            GerBeamEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            GerBeamEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                GerVisuals.forOwnerUuid(
                        entity.level(),
                        entity.getOwnerUuid(),
                        partialTick);
        float remaining = (float) (
                entity.getExpiresAt()
                        - (entity.level().getGameTime()
                                + partialTick));
        float fade = Mth.clamp(remaining / 8.0F, 0.0F, 1.0F);
        if (visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F
                || fade <= 0.0F) {
            return;
        }

        Vec3 delta = entity.getEnd().subtract(entity.position());
        if (delta.lengthSqr() <= 1.0E-6D) {
            return;
        }
        Vec3 direction = delta.normalize();
        Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() <= 1.0E-6D) {
            side = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize().scale(WIDTH);
        Vec3 up = direction.cross(side).normalize().scale(WIDTH);

        MultiBufferSource standBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha() * fade);
        VertexConsumer vertices = standBuffer.getBuffer(
                ModRenderTypes.standTranslucent(TEXTURE));
        quad(poseStack, vertices, delta, side);
        quad(poseStack, vertices, delta, up);
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                standBuffer,
                ClientUtil.MAX_LIGHT);
    }

    private static void quad(
            PoseStack poseStack,
            VertexConsumer vertices,
            Vec3 end,
            Vec3 halfWidth) {
        vertex(poseStack, vertices, halfWidth.scale(-1), 0.0F, 0.0F);
        vertex(poseStack, vertices, halfWidth, 0.0F, 1.0F);
        vertex(poseStack, vertices, end.add(halfWidth), 1.0F, 1.0F);
        vertex(
                poseStack,
                vertices,
                end.subtract(halfWidth),
                1.0F,
                0.0F);
    }

    private static void vertex(
            PoseStack poseStack,
            VertexConsumer vertices,
            Vec3 point,
            float u,
            float v) {
        PoseStack.Pose pose = poseStack.last();
        vertices.addVertex(
                        pose.pose(),
                        (float) point.x,
                        (float) point.y,
                        (float) point.z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(ClientUtil.MAX_LIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public boolean shouldRender(
            GerBeamEntity entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ) {
        return super.shouldRender(
                        entity,
                        frustum,
                        cameraX,
                        cameraY,
                        cameraZ)
                || frustum.isVisible(new AABB(
                        entity.position(),
                        entity.getEnd()).inflate(1.0D));
    }
}
