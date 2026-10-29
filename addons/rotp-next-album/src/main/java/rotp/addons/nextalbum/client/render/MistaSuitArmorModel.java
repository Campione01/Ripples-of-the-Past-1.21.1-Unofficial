package rotp.addons.nextalbum.client.render;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.item.MistaSuitArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class MistaSuitArmorModel
        extends GeoModel<MistaSuitArmorItem> {
    @Override
    public ResourceLocation getModelResource(
            MistaSuitArmorItem animatable) {
        return NextAlbumIds.id(animatable.modelPath());
    }

    @Override
    public ResourceLocation getTextureResource(
            MistaSuitArmorItem animatable) {
        return NextAlbumIds.id(animatable.texturePath());
    }

    @Override
    public ResourceLocation getAnimationResource(
            MistaSuitArmorItem animatable) {
        return NextAlbumIds.id(
                "animations/mista_suit_armor.animation.json");
    }
}
