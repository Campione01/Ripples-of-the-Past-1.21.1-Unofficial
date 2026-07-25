package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonTurquoiseBlueOverdriveEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class TurquoiseBlueOverdriveRenderer extends EntityRenderer<HamonTurquoiseBlueOverdriveEntity> {
    private static final ResourceLocation EMPTY_TEXTURE = JojoMod.resLoc("textures/entity/projectiles/hamon_bubble_barrier.png");

    public TurquoiseBlueOverdriveRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HamonTurquoiseBlueOverdriveEntity entity) {
        return EMPTY_TEXTURE;
    }

    @Override
    public void render(HamonTurquoiseBlueOverdriveEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        }
    }
}
