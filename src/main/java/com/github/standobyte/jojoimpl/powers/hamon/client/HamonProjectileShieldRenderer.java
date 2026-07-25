package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.client.rendertype.CustomRenderType;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonProjectileShieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class HamonProjectileShieldRenderer extends EntityRenderer<HamonProjectileShieldEntity> {
    private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/projectile_shield.png");
    private static final int COLOR = 0x80FFFFFF;

    public HamonProjectileShieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HamonProjectileShieldEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(HamonProjectileShieldEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer vertexBuilder = buffer.getBuffer(CustomRenderType.hamonProjectileShield(getTextureLocation(entity)));
        renderShieldQuad(entity, poseStack, vertexBuilder, LightTexture.FULL_BRIGHT);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderShieldQuad(HamonProjectileShieldEntity entity, PoseStack poseStack,
            VertexConsumer vertexBuilder, int packedLight) {
        float halfWidth = entity.getShieldWidth() * 0.5F;
        float halfHeight = entity.getShieldHeight() * 0.5F;
        Vec3 forward = Vec3.directionFromRotation(entity.getXRot(), entity.getYRot()).normalize();
        Vec3 upRef = Math.abs(forward.y) > 0.99D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = upRef.cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();

        Vec3 rightOffset = right.scale(halfWidth);
        Vec3 upOffset = up.scale(halfHeight);
        Vec3 pRU = rightOffset.add(upOffset);
        Vec3 pLU = rightOffset.reverse().add(upOffset);
        Vec3 pLD = rightOffset.reverse().subtract(upOffset);
        Vec3 pRD = rightOffset.subtract(upOffset);

        vertex(poseStack, vertexBuilder, packedLight, pRD, 1.0F, 1.0F);
        vertex(poseStack, vertexBuilder, packedLight, pLD, 0.0F, 1.0F);
        vertex(poseStack, vertexBuilder, packedLight, pLU, 0.0F, 0.0F);
        vertex(poseStack, vertexBuilder, packedLight, pRU, 1.0F, 0.0F);

        vertex(poseStack, vertexBuilder, packedLight, pLD, 1.0F, 1.0F);
        vertex(poseStack, vertexBuilder, packedLight, pRD, 0.0F, 1.0F);
        vertex(poseStack, vertexBuilder, packedLight, pRU, 0.0F, 0.0F);
        vertex(poseStack, vertexBuilder, packedLight, pLU, 1.0F, 0.0F);
    }

    private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight,
            Vec3 pos, float u, float v) {
        PoseStack.Pose pose = poseStack.last();
        vertexBuilder.addVertex(pose.pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(COLOR)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
