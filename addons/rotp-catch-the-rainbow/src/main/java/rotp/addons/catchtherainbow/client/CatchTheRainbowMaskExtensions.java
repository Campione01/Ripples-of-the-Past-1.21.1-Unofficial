package rotp.addons.catchtherainbow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class CatchTheRainbowMaskExtensions
        implements IClientItemExtensions {
    private CatchTheRainbowMaskModel hiddenModel;

    @Override
    public HumanoidModel<?> getHumanoidArmorModel(
            LivingEntity entity,
            ItemStack stack,
            EquipmentSlot slot,
            HumanoidModel<?> original) {
        if (slot != EquipmentSlot.HEAD) {
            return original;
        }
        if (hiddenModel == null) {
            hiddenModel = new CatchTheRainbowMaskModel(
                    Minecraft.getInstance()
                            .getEntityModels()
                            .bakeLayer(
                                    CatchTheRainbowMaskModel
                                            .LAYER_LOCATION));
        }
        hiddenModel.setAllVisible(false);
        return hiddenModel;
    }
}
