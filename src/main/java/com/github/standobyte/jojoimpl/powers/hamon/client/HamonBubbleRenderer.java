package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class HamonBubbleRenderer extends HamonModelRenderer<HamonBubbleEntity, HamonBubbleModel> {
    public HamonBubbleRenderer(EntityRendererProvider.Context context) {
        super(context, HamonBubbleModel.create(), JojoMod.resLoc("textures/entity/projectiles/hamon_bubble.png"),
                RenderType::entityCutoutNoCull);
    }

    @Override
    protected void transformModel(HamonBubbleEntity entity, float partialTick, PoseStack poseStack) {
        float size = HamonBubbleModel.entityScale(entity);
        poseStack.scale(size, size, size);
    }
}
