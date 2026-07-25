package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class HamonModelRenderer<T extends Entity, M extends EntityModel<T>> extends EntityRenderer<T> {
    protected final M model;
    private final ResourceLocation texture;
    private final Function<ResourceLocation, RenderType> renderType;

    public HamonModelRenderer(EntityRendererProvider.Context context, M model, ResourceLocation texture,
            Function<ResourceLocation, RenderType> renderType) {
        super(context);
        this.model = model;
        this.texture = texture;
        this.renderType = renderType;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
            poseStack.pushPose();
            poseStack.scale(1.0F, -1.0F, -1.0F);
            model.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + partialTick, entityYaw,
                    Mth.lerp(partialTick, entity.xRotO, entity.getXRot()));
            transformModel(entity, partialTick, poseStack);
            VertexConsumer vertexBuilder = buffer.getBuffer(renderType.apply(getTextureLocation(entity)));
            model.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY,
                    getRenderColor(entity, partialTick));
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    protected void transformModel(T entity, float partialTick, PoseStack poseStack) {
    }

    protected int getRenderColor(T entity, float partialTick) {
        return 0xFFFFFFFF;
    }
}
