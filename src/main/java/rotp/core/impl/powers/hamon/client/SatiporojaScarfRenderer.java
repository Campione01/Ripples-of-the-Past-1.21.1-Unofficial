package rotp.core.impl.powers.hamon.client;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.SatiporojaScarfEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class SatiporojaScarfRenderer extends HamonExtendingRenderer<SatiporojaScarfEntity, SatiporojaScarfModel> {
    public SatiporojaScarfRenderer(EntityRendererProvider.Context context) {
        super(context, SatiporojaScarfModel.create(), JojoMod.resLoc("textures/entity/satiporoja_scarf.png"));
    }
}
