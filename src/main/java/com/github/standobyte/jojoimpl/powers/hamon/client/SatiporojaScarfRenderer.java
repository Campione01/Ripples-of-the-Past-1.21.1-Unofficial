package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SatiporojaScarfEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class SatiporojaScarfRenderer extends HamonExtendingRenderer<SatiporojaScarfEntity, SatiporojaScarfModel> {
    public SatiporojaScarfRenderer(EntityRendererProvider.Context context) {
        super(context, SatiporojaScarfModel.create(), JojoMod.resLoc("textures/entity/satiporoja_scarf.png"));
    }
}
