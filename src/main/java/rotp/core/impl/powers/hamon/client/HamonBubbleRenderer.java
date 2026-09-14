package rotp.core.impl.powers.hamon.client;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.HamonBubbleEntity;
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
