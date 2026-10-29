package rotp.addons.killerqueen.client;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.entity.SnowBombEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class SnowBombRenderer
        extends KillerQueenProjectileRenderer<SnowBombEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    KillerQueenAddon.id("snow_bomb"), "main");

    public SnowBombRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                LAYER,
                KillerQueenAddon.id(
                        "textures/entity/snow_bomb.png"),
                true);
    }
}
