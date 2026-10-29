package rotp.addons.mandom;

import rotp.core.init.ModItems;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.mandom.state.MandomRewindService;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MandomAddon.MOD_ID)
public final class MandomEvents {
    private MandomEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                MandomAddon.MANDOM_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    public static void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModItems.MAIN_TAB.getKey()) {
            event.accept(MandomAddon.RINGO_CLOCK.get());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MandomRewindService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityLeave(
            EntityLeaveLevelEvent event) {
        MandomRewindService.entityLeft(
                event.getEntity(), event.getLevel());
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MandomRewindService.recordBlockInteraction(
                    level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(
            ServerStoppingEvent event) {
        MandomRewindService.clear(event.getServer());
    }
}
