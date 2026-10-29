package rotp.addons.weatherreport;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.weatherreport.entity.WeatherReportFugaSummonerEntity;
import rotp.addons.weatherreport.network.WeatherSelectionSessions;
import rotp.addons.weatherreport.state.FuguStormTerminalData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = WeatherReportAddon.MOD_ID)
public final class WeatherReportEvents {
    private WeatherReportEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                WeatherReportAddon.WEATHER_REPORT_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            clear(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExperienceDrop(
            LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Pufferfish pufferfish
                && !pufferfish.level().isClientSide()
                && pufferfish.getPersistentData().getBoolean(
                        WeatherReportFugaSummonerEntity
                                .SUMMONED_BY_RAIN_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Pufferfish pufferfish
                && !pufferfish.level().isClientSide()
                && pufferfish.getPersistentData().getBoolean(
                        WeatherReportFugaSummonerEntity
                                .SUMMONED_BY_RAIN_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFuguDependentTick(
            EntityTickEvent.Post event) {
        Entity dependent = event.getEntity();
        if (!(dependent.level()
                        instanceof ServerLevel serverLevel)) {
            return;
        }
        CompoundTag data = dependent.getPersistentData();
        boolean puffer = dependent instanceof Pufferfish
                && data.getBoolean(
                        WeatherReportFugaSummonerEntity
                                .SUMMONED_BY_RAIN_TAG);
        boolean cloud = dependent instanceof AreaEffectCloud
                && data.getBoolean(
                        WeatherReportFugaSummonerEntity
                                .FUGU_CLOUD_TAG);
        if (!puffer && !cloud) {
            return;
        }
        if (!data.hasUUID(
                WeatherReportFugaSummonerEntity
                        .DEPENDENT_COORDINATOR_TAG)) {
            dependent.discard();
            return;
        }

        Entity owner = serverLevel.getEntity(data.getUUID(
                WeatherReportFugaSummonerEntity
                        .DEPENDENT_COORDINATOR_TAG));
        WeatherReportFugaSummonerEntity coordinator =
                owner instanceof WeatherReportFugaSummonerEntity summoner
                        && summoner.isAlive() ? summoner : null;
        if (owner != null && coordinator == null
                || !WeatherReportFugaSummonerEntity.retainDependentLifetime(
                        serverLevel, dependent, coordinator)) {
            dependent.discard();
            return;
        }
        if (coordinator == null) {
            return;
        }
        boolean retained = puffer
                ? coordinator.ownsPufferfish(
                                (Pufferfish) dependent)
                        && coordinator.retainPufferfish(
                                serverLevel,
                                (Pufferfish) dependent)
                : coordinator.ownsCloud(
                                (AreaEffectCloud) dependent)
                        && coordinator.retainCloud(
                                serverLevel,
                                (AreaEffectCloud) dependent);
        if (!retained) {
            dependent.discard();
        }
    }

    @SubscribeEvent
    public static void onFuguTerminalTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            FuguStormTerminalData.pruneIfPresent(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WeatherSelectionSessions.clearAll(
                event.getServer().getTickCount());
    }

    private static void clear(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            WeatherSelectionSessions.clear(serverPlayer);
        }
    }
}
