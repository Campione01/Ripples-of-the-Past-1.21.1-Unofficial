package rotp.addons.nextalbum.client.render;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.item.AjaStoneNecklaceItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class AjaStoneNecklaceModel
        extends GeoModel<AjaStoneNecklaceItem> {
    @Override
    public ResourceLocation getModelResource(
            AjaStoneNecklaceItem animatable) {
        return NextAlbumIds.id(
                "geo/aja_stone_necklace.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(
            AjaStoneNecklaceItem animatable) {
        return NextAlbumIds.id(
                "textures/models/armor/"
                        + "aja_stone_necklace.png");
    }

    @Override
    public ResourceLocation getAnimationResource(
            AjaStoneNecklaceItem animatable) {
        return NextAlbumIds.id(
                "animations/aja_stone_necklace.animation.json");
    }
}
