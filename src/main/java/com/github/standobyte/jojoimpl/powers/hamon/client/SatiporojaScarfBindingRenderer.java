package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SatiporojaScarfBindingEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class SatiporojaScarfBindingRenderer extends HamonExtendingRenderer<SatiporojaScarfBindingEntity, SatiporojaScarfBindingModel> {
    public SatiporojaScarfBindingRenderer(EntityRendererProvider.Context context) {
        super(context, SatiporojaScarfBindingModel.create(), JojoMod.resLoc("textures/entity/satiporoja_scarf.png"));
    }
}
