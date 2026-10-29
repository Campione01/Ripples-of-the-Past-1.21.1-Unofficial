package rotp.addons.speedwagon;

import rotp.core.init.ModItems;

import rotp.addons.speedwagon.entity.Henchman2Entity;
import rotp.addons.speedwagon.entity.HenchmanEntity;
import rotp.addons.speedwagon.entity.InstructorEntity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class SpeedwagonModEvents {
    private SpeedwagonModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                SpeedwagonAddon.HENCHMAN.get(),
                HenchmanEntity.createAttributes().build());
        event.put(
                SpeedwagonAddon.HENCHMAN2.get(),
                Henchman2Entity.createAttributes().build());
        event.put(
                SpeedwagonAddon.INSTRUCTOR.get(),
                InstructorEntity.createAttributes().build());
    }

    public static void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != ModItems.MAIN_TAB.getKey()) {
            return;
        }
        event.accept(SpeedwagonAddon.HENCHMAN_SPAWN_EGG.get());
        event.accept(SpeedwagonAddon.HENCHMAN2_SPAWN_EGG.get());
        event.accept(SpeedwagonAddon.INSTRUCTOR_SPAWN_EGG.get());
    }
}
