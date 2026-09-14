package rotp.core.impl.powers.hamon.client;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.HamonBubbleCutterEntity;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class HamonBubbleCutterRenderer extends HamonModelRenderer<HamonBubbleCutterEntity, HamonBubbleCutterModel> {
    public HamonBubbleCutterRenderer(EntityRendererProvider.Context context) {
        super(context, HamonBubbleCutterModel.create(), JojoMod.resLoc("textures/entity/projectiles/hamon_bubble_cutter.png"),
                RenderType::entityCutoutNoCull);
    }
}
