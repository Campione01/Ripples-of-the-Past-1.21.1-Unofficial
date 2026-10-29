package rotp.addons.nextalbum.client.render;

import rotp.addons.nextalbum.item.AjaStoneNecklaceItem;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public final class AjaStoneNecklaceRenderer
        extends GeoArmorRenderer<AjaStoneNecklaceItem> {
    public AjaStoneNecklaceRenderer() {
        super(new AjaStoneNecklaceModel());
    }

    @Override
    protected void applyBoneVisibilityBySlot(
            EquipmentSlot currentSlot) {
        setAllBonesVisible(false);
        setBoneVisible(body, true);
    }
}
