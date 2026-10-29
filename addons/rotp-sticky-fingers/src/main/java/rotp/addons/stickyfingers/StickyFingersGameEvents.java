package rotp.addons.stickyfingers;

import java.util.ArrayList;

import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;

import rotp.addons.stickyfingers.ability.StickyFingersHideInMobAbility;
import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.state.ZipperCloseReason;
import rotp.addons.stickyfingers.state.ZipperCombatCloseReason;
import rotp.addons.stickyfingers.state.ZipperCombatData;
import rotp.addons.stickyfingers.state.ZipperLeaseData;
import rotp.addons.stickyfingers.storage.ZipperStorageData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = StickyFingersAddon.MOD_ID)
public final class StickyFingersGameEvents {
    private StickyFingersGameEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ZipperLeaseData.get(level).tick(level);
            ZipperCombatData.get(level).tick(level);
        }
        ZipperStorageData.get(event.getServer())
                .tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) {
            return;
        }
        if (!StickyFingersAddon.isStickyFingersActive(player)) {
            if (LivingComponentPossession
                    .getEntityPossessedBy(player) != null) {
                LivingComponentPossession.setPossessionTarget(
                        player, null, null);
            }
            ZipperLeaseData.get(player.serverLevel())
                    .closeOwned(
                            player.serverLevel(),
                            player.getUUID(),
                            ZipperCloseReason.ABILITY_LOST);
            closeCombatAcrossServer(
                    player,
                    ZipperCombatCloseReason.ABILITY_LOST,
                    false);
            ZipperStorageData.get(player.getServer())
                    .closeViewer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ZipperCombatData.get(player.serverLevel())
                    .reconcileLiving(
                            player.serverLevel(), player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget()
                        instanceof LivingEntity target
                && target.level()
                        instanceof ServerLevel level) {
            ZipperCombatData.get(level)
                    .syncPresentationTo(
                            observer, target.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingEntityJoin(
            EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity()
                        instanceof LivingEntity target)) {
            return;
        }
        for (ServerLevel serverLevel :
                level.getServer().getAllLevels()) {
            if (serverLevel != level) {
                ZipperCombatData.get(serverLevel)
                        .closeTargeted(
                                serverLevel,
                                target.getUUID(),
                                ZipperCombatCloseReason
                                        .DIMENSION_CHANGE);
            }
        }
        ZipperCombatData.get(level)
                .reconcileLiving(level, target);
        ZipperStorageData.get(level.getServer())
                .closeEntitySessions(
                        level.getServer(), target.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            closeAcrossServer(
                    player, ZipperCloseReason.OWNER_LOGOUT);
            closeCombatAcrossServer(
                    player,
                    ZipperCombatCloseReason.OWNER_LOGOUT,
                    true);
            closeStorageSessions(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            closeAcrossServer(
                    player, ZipperCloseReason.DIMENSION_CHANGE);
            closeCombatAcrossServer(
                    player,
                    ZipperCombatCloseReason.DIMENSION_CHANGE,
                    true);
            closeStorageSessions(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            closeAcrossServer(
                    player, ZipperCloseReason.OWNER_DEATH);
            closeCombatAcrossServer(
                    player,
                    ZipperCombatCloseReason.OWNER_DEATH,
                    true);
            closeStorageSessions(player, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            closeAcrossServer(
                    player, ZipperCloseReason.OWNER_DEATH);
            closeCombatAcrossServer(
                    player,
                    ZipperCombatCloseReason.OWNER_DEATH,
                    true);
        } else if (entity.level() instanceof ServerLevel level) {
            for (ServerLevel serverLevel :
                    level.getServer().getAllLevels()) {
                ZipperCombatData.get(serverLevel)
                        .closeTargeted(
                                serverLevel,
                                entity.getUUID(),
                                ZipperCombatCloseReason
                                        .TARGET_DEATH);
            }
        }
        if (entity.level() instanceof ServerLevel level) {
            ZipperStorageData.get(level.getServer())
                    .onTargetDeath(level, entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingEntityLeave(
            EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity()
                        instanceof LivingEntity target)) {
            return;
        }
        ZipperStorageData data =
                ZipperStorageData.get(level.getServer());
        data.closeEntitySessions(
                level.getServer(), target.getUUID());
        if (target.getHealth() > 0.0F
                && target.getRemovalReason() != null
                && target.getRemovalReason().shouldDestroy()) {
            data.onTargetDiscarded(level, target);
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.getEntity().hasEffect(
                StickyFingersAddon.ZIPPER_EFFECT)) {
            event.setAmount(
                    event.getAmount()
                            * StickyFingersPhase1Spec
                                    .ZIPPER_EFFECT_DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(
            LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (event.getNewDamage() > 0.0F) {
            var possessors = LivingComponentPossession
                    .getEntitiesPossessing(target);
            if (possessors != null) {
                for (LivingComponentPossession possession :
                        new ArrayList<>(possessors)) {
                    if (StickyFingersHideInMobAbility
                            .POSSESSION_TYPE
                            .equals(possession.possessionType)) {
                        possession.stopPossession();
                    }
                }
            }
        }
        if (event.getNewDamage()
                        > StickyFingersPhase1Spec
                                .ZIP_WOUNDS_BREAK_DAMAGE
                && target.level() instanceof ServerLevel level
                && target.hasEffect(
                        StickyFingersAddon.ZIP_WOUNDS_EFFECT)) {
            ZipperCombatData.get(level)
                    .breakWounds(level, target);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(
            AttackEntityEvent event) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }
        if (ZipperCombatData.get(player.serverLevel())
                .isHandBlocked(
                        player, InteractionHand.MAIN_HAND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onSwapHands(
            LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof ServerPlayer player
                && DetachedBodyPartEntity.releaseCarriedBy(
                        player, false)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos chunkPosition = event.getChunk().getPos();
        ZipperStorageData.get(level.getServer())
                .onChunkUnload(level, event.getChunk());
        level.getServer().execute(
                () -> ZipperLeaseData.get(level)
                        .onChunkUnload(level, chunkPosition));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(
            BlockEvent.BreakEvent event) {
        if (!event.isCanceled()
                && event.getLevel()
                        instanceof ServerLevel level) {
            ZipperStorageData.get(level.getServer())
                    .onBlockBroken(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos chunkPosition = event.getChunk().getPos();
        level.getServer().execute(
                () -> ZipperLeaseData.get(level)
                        .onChunkLoad(chunkPosition));
    }

    private static void closeAcrossServer(
            ServerPlayer player, ZipperCloseReason reason) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            ZipperLeaseData.get(level)
                    .closeOwned(
                            level, player.getUUID(), reason);
        }
    }

    private static void closeCombatAcrossServer(
            ServerPlayer player,
            ZipperCombatCloseReason reason,
            boolean includeTargeted) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            ZipperCombatData data =
                    ZipperCombatData.get(level);
            data.closeOwned(
                    level, player.getUUID(), reason);
            if (includeTargeted) {
                data.closeTargeted(
                        level, player.getUUID(), reason);
            }
        }
        if (includeTargeted) {
            ZipperCombatData.get(player.serverLevel())
                    .reconcileLiving(
                            player.serverLevel(), player);
        }
    }

    private static void closeStorageSessions(
            ServerPlayer player, boolean includeTargeted) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ZipperStorageData data =
                ZipperStorageData.get(server);
        data.closeViewer(player);
        if (includeTargeted) {
            data.closeEntitySessions(
                    server, player.getUUID());
        }
    }
}
