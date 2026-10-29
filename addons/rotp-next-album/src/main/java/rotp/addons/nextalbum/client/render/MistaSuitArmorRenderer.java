package rotp.addons.nextalbum.client.render;

import rotp.addons.nextalbum.item.MistaSuitArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public final class MistaSuitArmorRenderer
        extends GeoArmorRenderer<MistaSuitArmorItem> {
    public MistaSuitArmorRenderer() {
        super(new MistaSuitArmorModel());
    }

    @Override
    public void prepForRender(
            Entity entity,
            ItemStack stack,
            EquipmentSlot slot,
            HumanoidModel<?> baseModel,
            MultiBufferSource bufferSource,
            float partialTick,
            float limbSwing,
            float limbSwingAmount,
            float netHeadYaw,
            float headPitch) {
        ((MistaSuitArmorItem) stack.getItem())
                .setRenderingSlimVariant(
                        entity instanceof AbstractClientPlayer player
                                && player.getSkin().model()
                                        == PlayerSkin.Model.SLIM);
        super.prepForRender(
                entity,
                stack,
                slot,
                baseModel,
                bufferSource,
                partialTick,
                limbSwing,
                limbSwingAmount,
                netHeadYaw,
                headPitch);
    }

    @Override
    public GeoBone getRightBootBone(
            GeoModel<MistaSuitArmorItem> model) {
        return model.getBone("armorRightLeg").orElse(null);
    }

    @Override
    public GeoBone getLeftBootBone(
            GeoModel<MistaSuitArmorItem> model) {
        return model.getBone("armorLeftLeg").orElse(null);
    }
}
