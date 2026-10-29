package rotp.addons.shadowtheworld;

import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class ShadowTheWorldModEvents {
    private ShadowTheWorldModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                ShadowTheWorldAddon.SHADOW_THE_WORLD_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    // other clients need the effect to draw a knocked-down player lying
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ModStatusEffects.TRACKED_EFFECTS.add(
                ShadowTheWorldAddon.KNOCKDOWN));
    }

    public static void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != ModItems.MAIN_TAB.getKey()) {
            return;
        }
        event.accept(ShadowTheWorldAddon.DIO_NOTE_PAGE.get());
        event.accept(ShadowTheWorldAddon.DIO_NOTE.get());
    }
}
