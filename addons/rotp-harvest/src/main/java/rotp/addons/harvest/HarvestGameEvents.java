package rotp.addons.harvest;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.harvest.entity.HarvestStandEntity;
import rotp.addons.harvest.stand.HarvestStandData;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = HarvestAddon.MOD_ID)
public final class HarvestGameEvents {
    private HarvestGameEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        cleanup(event.getOriginal());
        // 1.16's LivingUtilCap was never copied to the new player entity
        // (death, return from the End); the Stand's data may be shared with it
        forgetSwarmState(event.getOriginal());
        forgetSwarmState(event.getEntity());
    }

    private static void forgetSwarmState(LivingEntity user) {
        HarvestStandData data = user != null
                && !user.level().isClientSide()
                        ? HarvestStandData.of(user)
                        : null;
        if (data != null) {
            data.forget();
        }
    }

    private static void cleanup(LivingEntity owner) {
        if (owner == null || owner.level().isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(owner);
        if (power != null
                && power.getPowerType()
                        == HarvestAddon.HARVEST_STAND.get()
                && power.getSummonedStandEntity()
                        instanceof HarvestStandEntity coordinator) {
            coordinator.cleanupDependents();
        }
    }
}
