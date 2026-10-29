package rotp.addons.nextalbum.client.render;

import rotp.addons.nextalbum.item.LuckPluckItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class LuckPluckRenderer
        extends GeoItemRenderer<LuckPluckItem> {
    public LuckPluckRenderer() {
        super(new LuckPluckModel());
    }
}
