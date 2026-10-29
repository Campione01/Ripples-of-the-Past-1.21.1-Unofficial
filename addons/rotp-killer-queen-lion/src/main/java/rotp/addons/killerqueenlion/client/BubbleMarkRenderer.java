package rotp.addons.killerqueenlion.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.entity.BubbleMarkEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class BubbleMarkRenderer
        extends SimpleEntityRenderer<BubbleMarkEntity, BubbleMarkModel> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    KillerQueenLionAddon.id("bubble_mark"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "textures/entity/projectiles/hamon_bubble.png");

    public BubbleMarkRenderer(EntityRendererProvider.Context context) {
        super(context);
        initTexture(TEXTURE, true);
        initModel(new BubbleMarkModel(context.bakeLayer(LAYER)));
        offsetModelByEntityHeight(false);
        shadowRadius = 0.0F;
    }
}
