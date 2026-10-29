package rotp.addons.killerqueenlion.client;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.entity.BubbleBombEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class BubbleBombRenderer
        extends KillerQueenProjectileRenderer<BubbleBombEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    KillerQueenLionAddon.id("bubble_bomb"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "textures/entity/projectiles/hamon_bubble.png");

    public BubbleBombRenderer(
            EntityRendererProvider.Context context) {
        super(context, LAYER, TEXTURE, false);
    }
}
