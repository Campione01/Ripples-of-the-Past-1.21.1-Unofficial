package rotp.addons.nextalbum.client.render;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.item.LuckPluckItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class LuckPluckModel extends GeoModel<LuckPluckItem> {
    @Override
    public ResourceLocation getModelResource(
            LuckPluckItem animatable) {
        return NextAlbumIds.id("geo/luck_pluck.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(
            LuckPluckItem animatable) {
        return NextAlbumIds.id("textures/item/luck_pluck.png");
    }

    @Override
    public ResourceLocation getAnimationResource(
            LuckPluckItem animatable) {
        return NextAlbumIds.id(
                "animations/luck_pluck.animation.json");
    }
}
