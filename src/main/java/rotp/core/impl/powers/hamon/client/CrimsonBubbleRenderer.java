package rotp.core.impl.powers.hamon.client;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.CrimsonBubbleEntity;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class CrimsonBubbleRenderer extends HamonModelRenderer<CrimsonBubbleEntity, CrimsonBubbleModel> {
    public CrimsonBubbleRenderer(EntityRendererProvider.Context context) {
        super(context, CrimsonBubbleModel.create(), JojoMod.resLoc("textures/entity/crimson_bubble.png"),
                RenderType::entityTranslucent);
    }
}
