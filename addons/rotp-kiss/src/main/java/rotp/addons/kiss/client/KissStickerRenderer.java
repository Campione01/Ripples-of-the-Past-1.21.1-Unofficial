package rotp.addons.kiss.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;

import rotp.addons.kiss.KissAddon;
import rotp.addons.kiss.entity.KissStickerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class KissStickerRenderer extends SimpleEntityRenderer<
        KissStickerEntity, KissPlateModel<KissStickerEntity>> {
    public KissStickerRenderer(EntityRendererProvider.Context context) {
        super(context);
        initTexture(KissAddon.id("textures/action/kiss1.png"), true);
        initModel(new KissPlateModel<>(context.bakeLayer(KissPlateModel.LAYER)));
        offsetModelByEntityHeight(false);
        shadowRadius = 0.0F;
    }
}
