package rotp.addons.d4c;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(
        modid = D4CAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public final class D4CEvents {
    private D4CEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                D4CAddon.D4C_ENTITY.get(),
                StandEntity.createAttributes().build());
        event.put(
                D4CAddon.SUMMONED_PLAYER.get(),
                rotp.addons.d4c.entity
                        .D4CMinionEntity.createAttributes().build());
        event.put(
                D4CAddon.ANNIHILATION_CLONE.get(),
                rotp.addons.d4c.entity
                        .D4CAnnihilationCloneEntity
                        .createAttributes()
                        .build());
        event.put(
                D4CAddon.ANNIHILATION_PLAYER_CLONE.get(),
                rotp.addons.d4c.entity
                        .D4CAnnihilationCloneEntity
                        .createAttributes()
                        .build());
        event.put(
                D4CAddon.SUMMON_KICK_ENTITY.get(),
                rotp.addons.d4c.entity
                        .D4CSummonKickEntity
                        .createAttributes()
                        .build());
    }

    // 1.16 sent Love Train and its light wall to trackers; the core
    // tracker sync covers add/remove and late StartTracking resync.
    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModStatusEffects.TRACKED_EFFECTS.add(D4CAddon.LOVE_TRAIN);
            ModStatusEffects.TRACKED_EFFECTS.add(
                    D4CAddon.LOVE_TRAIN_LIGHT_WALL);
        });
    }
}
