package rotp.addons.tusk;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = TuskAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class TuskEvents {
    private TuskEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                TuskAddon.TUSK_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ModStatusEffects.TRACKED_EFFECTS.add(
                TuskAddon.INFINITE_ROTATION));
    }
}
