package rotp.addons.killerqueen;

import rotp.addons.killerqueen.btd.BitesTheDustAwakening;
import rotp.addons.killerqueen.btd.BitesTheDustService;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KillerQueenAddon.MOD_ID)
public final class KillerQueenEvents {
    private KillerQueenEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                KillerQueenAddon.KILLER_QUEEN_ENTITY.get(),
                rotp.core.powersystem.standpower.entity
                        .StandEntity.createAttributes().build());
        event.put(
                KillerQueenAddon.SHEER_HEART_ENTITY.get(),
                rotp.addons.killerqueen.entity
                        .SheerHeartAttackEntity.createAttributes()
                        .build());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            BitesTheDustAwakening.enforceStandArrowGate(player);
            KillerQueenStateService.validateOwner(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        BitesTheDustService.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            BitesTheDustAwakening.unlockFromStandArrowHit(
                    player, event.getSource().getDirectEntity());
        }
        BitesTheDustService.handleIncomingDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onStandArrowUse(
            PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            BitesTheDustAwakening.unlockFromCrouchingArrowUse(
                    player, event.getItemStack());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()
                || entity.getServer() == null) {
            return;
        }
        BitesTheDustService.entityDied(entity);
        if (entity instanceof Player player) {
            KillerQueenStateService.clearAll(player);
        }
    }

    // A dead player respawns under the same UUID, and the donor bomb is a bare UUID that only detonation, the quit
    // action or a new plant changes; 1.16 PunchBomb.entityInRange still found a killed mob's body, so a dead mob
    // frees its owner only once the body leaves the level.
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof LivingEntity entity
                && !(entity instanceof Player)
                && entity.isDeadOrDying()) {
            KillerQueenStateService.clearPrimaryBombReferences(
                    entity.getServer(), entity.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        KillerQueenStateService.clearAll(original);
        KillerQueenStateService.clearAll(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KillerQueenStateService.validateOwner(player);
            BitesTheDustService.reconnectOwner(player);
            BitesTheDustService.trackPlayerAcrossMarkers(player);
            if (player.hasData(
                    KillerQueenAddon.KILLER_QUEEN_STATE)) {
                KillerQueenAddon.syncState(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            BitesTheDustService.trackPlayerAcrossMarkers(
                    serverPlayer);
            BitesTheDustService.playerChangedDimension(
                    serverPlayer);
        }
        boolean synced =
                KillerQueenStateService.clearSessionState(player);
        // The client's new player starts with an empty state
        if (!synced
                && player instanceof ServerPlayer serverPlayer
                && serverPlayer.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            KillerQueenAddon.syncState(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            BitesTheDustService.playerLoggedOut(serverPlayer);
        }
        KillerQueenStateService.clearSessionState(player);
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof Player owner
                && owner.hasData(
                        KillerQueenAddon.KILLER_QUEEN_STATE)) {
            KillerQueenAddon.syncState(observer, owner);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BitesTheDustService.levelUnloaded(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(
            ServerStoppingEvent event) {
        BitesTheDustService.clear(event.getServer());
    }
}
