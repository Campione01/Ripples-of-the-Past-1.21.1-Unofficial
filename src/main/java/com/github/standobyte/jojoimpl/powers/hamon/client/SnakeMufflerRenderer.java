package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SnakeMufflerEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class SnakeMufflerRenderer extends HamonExtendingRenderer<SnakeMufflerEntity, SnakeMufflerModel> {
    public SnakeMufflerRenderer(EntityRendererProvider.Context context) {
        super(context, SnakeMufflerModel.create(), JojoMod.resLoc("textures/entity/satiporoja_scarf.png"));
    }
}
