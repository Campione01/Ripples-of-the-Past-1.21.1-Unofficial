package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.CrimsonBubbleEntity;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class CrimsonBubbleRenderer extends HamonModelRenderer<CrimsonBubbleEntity, CrimsonBubbleModel> {
    public CrimsonBubbleRenderer(EntityRendererProvider.Context context) {
        super(context, CrimsonBubbleModel.create(), JojoMod.resLoc("textures/entity/crimson_bubble.png"),
                RenderType::entityTranslucent);
    }
}
