package rotp.addons.jumpingjackflash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.jumpingjackflash.JumpingJackFlashAddon;
import rotp.addons.jumpingjackflash.entity.GravityDomainEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class GravityDomainRenderer
        extends EntityRenderer<GravityDomainEntity> {
    private static final int LATITUDE_SEGMENTS = 18;
    private static final int LONGITUDE_SEGMENTS = 28;
    private static final ResourceLocation TEXTURE =
            JumpingJackFlashAddon.id(
                    "textures/entity/stand/jumping_jack_flash.png");

    public GravityDomainRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            GravityDomainEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            GravityDomainEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float radius = entity.currentRadius(partialTick);
        if (radius <= 0.01F) {
            return;
        }
        VertexConsumer vertices =
                buffer.getBuffer(RenderType.lightning());
        drawSphere(
                poseStack.last(),
                vertices,
                Math.max(0.01F, radius - 0.08F),
                255,
                255,
                255,
                34);
        drawSphere(
                poseStack.last(),
                vertices,
                radius,
                210,
                225,
                255,
                72);
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    private static void drawSphere(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            float radius,
            int red,
            int green,
            int blue,
            int alpha) {
        for (int latitude = 0;
                latitude < LATITUDE_SEGMENTS;
                latitude++) {
            float v0 = latitude / (float) LATITUDE_SEGMENTS;
            float v1 = (latitude + 1) / (float) LATITUDE_SEGMENTS;
            float phi0 = (v0 - 0.5F) * Mth.PI;
            float phi1 = (v1 - 0.5F) * Mth.PI;
            float y0 = Mth.sin(phi0) * radius;
            float y1 = Mth.sin(phi1) * radius;
            float ring0 = Mth.cos(phi0) * radius;
            float ring1 = Mth.cos(phi1) * radius;
            for (int longitude = 0;
                    longitude < LONGITUDE_SEGMENTS;
                    longitude++) {
                float angle0 = Mth.TWO_PI * longitude
                        / LONGITUDE_SEGMENTS;
                float angle1 = Mth.TWO_PI * (longitude + 1)
                        / LONGITUDE_SEGMENTS;
                vertex(
                        pose,
                        vertices,
                        Mth.cos(angle0) * ring0,
                        y0,
                        Mth.sin(angle0) * ring0,
                        red,
                        green,
                        blue,
                        alpha);
                vertex(
                        pose,
                        vertices,
                        Mth.cos(angle0) * ring1,
                        y1,
                        Mth.sin(angle0) * ring1,
                        red,
                        green,
                        blue,
                        alpha);
                vertex(
                        pose,
                        vertices,
                        Mth.cos(angle1) * ring1,
                        y1,
                        Mth.sin(angle1) * ring1,
                        red,
                        green,
                        blue,
                        alpha);
                vertex(
                        pose,
                        vertices,
                        Mth.cos(angle1) * ring0,
                        y0,
                        Mth.sin(angle1) * ring0,
                        red,
                        green,
                        blue,
                        alpha);
            }
        }
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            float x,
            float y,
            float z,
            int red,
            int green,
            int blue,
            int alpha) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(red, green, blue, alpha);
    }
}
