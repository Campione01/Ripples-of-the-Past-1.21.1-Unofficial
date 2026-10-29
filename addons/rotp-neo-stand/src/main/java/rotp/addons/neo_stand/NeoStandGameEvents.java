package rotp.addons.neo_stand;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.addons.neo_stand.ability.BulletTimeService;
import rotp.addons.neo_stand.ability.MovementHistory;
import rotp.addons.neo_stand.ability.TimeStopTeleportationAbility;
import rotp.core.api.timestop.TimeStopLifecycleEvent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = NeoStandAddon.MOD_ID)
public final class NeoStandGameEvents {
    private static final Map<UUID, Integer> PENDING_RESUMMONS =
            new HashMap<>();

    private NeoStandGameEvents() {}

    public static void scheduleResummon(ServerPlayer player) {
        PENDING_RESUMMONS.put(player.getUUID(), 1);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        BulletTimeService.tickServer(event.getServer());
        Iterator<Map.Entry<UUID, Integer>> iterator =
                PENDING_RESUMMONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (entry.getValue() > 1) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }
            iterator.remove();
            ServerPlayer player = event.getServer()
                    .getPlayerList()
                    .getPlayer(entry.getKey());
            resummon(player);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        BulletTimeService.tickEntity(event.getEntity());
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()) {
            MovementHistory.record(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        TimeStopTeleportationAbility.cancelFor(event.getEntity());
        PENDING_RESUMMONS.remove(
                event.getEntity().getUUID());
        if (event.getEntity() instanceof LivingEntity living) {
            BulletTimeService.cancel(living);
            MovementHistory.clear(living.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BulletTimeService.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        TimeStopTeleportationAbility.cancelFor(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            BulletTimeService.cancel(player);
            BulletTimeService.syncTo(player);
            MovementHistory.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onTransportTimeStopRemoved(TimeStopLifecycleEvent.Removed event) {
        TimeStopTeleportationAbility.timeStopRemoved(event);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            TimeStopTeleportationAbility.cancelFor(event.getEntity());
            MovementHistory.clear(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MovementHistory.clearAll();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTransportDamage(LivingIncomingDamageEvent event) {
        if (TimeStopTeleportationAbility.protects(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTransportKnockback(LivingKnockBackEvent event) {
        if (TimeStopTeleportationAbility.protects(event.getEntity())) event.setCanceled(true);
    }

    private static void resummon(ServerPlayer player) {
        if (player == null
                || !player.isAlive()
                || player.isRemoved()) {
            return;
        }
        StandPower power = StandPower.get(player);
        StandInstance stand = power != null
                ? power.getStandInstance().orElse(null)
                : null;
        if (power == null
                || power.isSummoned()
                || stand == null
                || !NeoStandAddon.SHADOW_THE_WORLD.equals(
                        stand.getStandId())
                || !(stand.getStandType()
                        instanceof EntityStandType type)) {
            return;
        }
        type.summon(player, power);
    }
}
