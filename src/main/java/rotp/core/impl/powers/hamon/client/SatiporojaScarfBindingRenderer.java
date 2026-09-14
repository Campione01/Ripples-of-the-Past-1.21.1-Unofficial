package rotp.core.impl.powers.hamon.client;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.SatiporojaScarfBindingEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class SatiporojaScarfBindingRenderer extends HamonExtendingRenderer<SatiporojaScarfBindingEntity, SatiporojaScarfBindingModel> {
    public SatiporojaScarfBindingRenderer(EntityRendererProvider.Context context) {
        super(context, SatiporojaScarfBindingModel.create(), JojoMod.resLoc("textures/entity/satiporoja_scarf.png"));
    }
}
