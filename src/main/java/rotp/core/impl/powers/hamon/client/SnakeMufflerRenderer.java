package rotp.core.impl.powers.hamon.client;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.SnakeMufflerEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class SnakeMufflerRenderer extends HamonExtendingRenderer<SnakeMufflerEntity, SnakeMufflerModel> {
    public SnakeMufflerRenderer(EntityRendererProvider.Context context) {
        super(context, SnakeMufflerModel.create(), JojoMod.resLoc("textures/entity/satiporoja_scarf.png"));
    }
}
