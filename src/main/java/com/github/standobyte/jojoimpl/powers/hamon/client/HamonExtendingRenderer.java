package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanExtendingBodyPartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class HamonExtendingRenderer<T extends PillarmanExtendingBodyPartEntity, M extends HamonRepeatingModel> extends EntityRenderer<T> {
    private final M model;
    private final ResourceLocation texture;

    public HamonExtendingRenderer(EntityRendererProvider.Context context, M model, ResourceLocation texture) {
        super(context);
        this.model = model;
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
            Vec3 entityPos = entity.getPosition(partialTick);
            Vec3 originPos = entity.getOriginPoint(partialTick);
            Vec3 extentVec = entityPos.subtract(originPos);
            float length = (float) extentVec.length();
            if (length > 1.0E-5F) {
                LivingEntity owner = entity.getOwner();
                if (owner != null) {
                    packedLight = entityRenderDispatcher.getPackedLightCoords(owner, partialTick);
                }
                poseStack.pushPose();
                Vec3 originRelativeToEntity = originPos.subtract(entityPos);
                poseStack.translate(originRelativeToEntity.x, originRelativeToEntity.y, originRelativeToEntity.z);
                model.setup(length, MathUtil.yRotDegFromVec(extentVec), MathUtil.xRotDegFromVec(extentVec));
                VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
                model.renderToBuffer(poseStack, vertexBuilder, packedLight, 0xFFFFFFFF);
                poseStack.popPose();
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
