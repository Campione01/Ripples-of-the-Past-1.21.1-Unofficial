package rotp.addons.diegosworld;

import rotp.core.init.ModItems;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.diegosworld.item.RevolverItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class DiegoModEvents {
    private DiegoModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                DiegosTheWorldAddon.THE_WORLD_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    public static void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != ModItems.MAIN_TAB.getKey()) {
            return;
        }
        ItemStack revolver =
                new ItemStack(DiegosTheWorldAddon.REVOLVER.get());
        RevolverItem.setAmmo(revolver, RevolverItem.MAX_AMMO);
        event.accept(revolver);
        event.accept(DiegosTheWorldAddon.BARBED_WIRE_ITEM.get());
    }
}
