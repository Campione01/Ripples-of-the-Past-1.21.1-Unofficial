package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleCutterEntity;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class HamonBubbleCutterRenderer extends HamonModelRenderer<HamonBubbleCutterEntity, HamonBubbleCutterModel> {
    public HamonBubbleCutterRenderer(EntityRendererProvider.Context context) {
        super(context, HamonBubbleCutterModel.create(), JojoMod.resLoc("textures/entity/projectiles/hamon_bubble_cutter.png"),
                RenderType::entityCutoutNoCull);
    }
}
