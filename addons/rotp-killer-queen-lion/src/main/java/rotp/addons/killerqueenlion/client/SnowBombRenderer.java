package rotp.addons.killerqueenlion.client;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.entity.SnowBombEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class SnowBombRenderer
        extends KillerQueenProjectileRenderer<SnowBombEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    KillerQueenLionAddon.id("snow_bomb"), "main");

    public SnowBombRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                LAYER,
                KillerQueenLionAddon.id(
                        "textures/entity/snow_bomb.png"),
                true);
    }
}
