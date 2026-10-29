package rotp.addons.ger;

import rotp.core.api.power.AbilityRefusals;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class GoldExperienceRequiemModEvents {
    private GoldExperienceRequiemModEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                GoldExperienceRequiemAddon.GER_STAND_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    public static void commonSetup(FMLCommonSetupEvent event) {
        AbilityRefusals.register(
                GoldExperienceRequiemAddon.id("disabler"),
                GoldExperienceRequiemGameEvents::refuseWhileDisabled);
    }
}
