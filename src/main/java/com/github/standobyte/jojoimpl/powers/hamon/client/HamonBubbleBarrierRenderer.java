package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleBarrierEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class HamonBubbleBarrierRenderer extends HamonModelRenderer<HamonBubbleBarrierEntity, HamonBubbleBarrierModel> {
    public HamonBubbleBarrierRenderer(EntityRendererProvider.Context context) {
        super(context, HamonBubbleBarrierModel.create(), JojoMod.resLoc("textures/entity/projectiles/hamon_bubble_barrier.png"),
                RenderType::entityTranslucent);
    }

    @Override
    protected void transformModel(HamonBubbleBarrierEntity entity, float partialTick, PoseStack poseStack) {
        float size = entity.getSize(partialTick);
        if (size < 1.0F) {
            poseStack.scale(size, size, size);
        }
    }
}
