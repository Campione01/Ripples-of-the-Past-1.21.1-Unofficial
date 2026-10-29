package rotp.addons.tusk;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.tusk.network.TuskNailStateSyncPayload;
import rotp.addons.tusk.state.TuskNailState;
import rotp.addons.tusk.state.TuskOpenSpaceLeaseData;
import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TuskAddon.MOD_ID)
public final class TuskGameEvents {
    private TuskGameEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        TuskNailState state = TuskAddon.nailState(player);
        boolean changed = TuskAddon.isTuskUser(player)
                ? state.tickServer(TuskAddon.nailRegenTicks(player))
                : state.reset();
        StandPower power = StandPower.get(player);
        if (TuskAddon.isAct4User(player)
                && player.getVehicle() instanceof Horse
                && player.hasEffect(ModStatusEffects.RESOLVE)
                && power != null
                && power.getResolveLevel() >= 4) {
            changed = state.setInfiniteRotationCharged(true)
                    || changed;
        }
        if (changed && player instanceof ServerPlayer serverPlayer) {
            TuskAddon.syncNailState(serverPlayer);
        }
        if (player instanceof ServerPlayer serverPlayer
                && !TuskAddon.isPortalActUser(player)) {
            TuskPortalOperations.get(serverPlayer.server)
                    .closeOwned(player.getUUID());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            emptyArmHand(serverPlayer);
        }
    }

    // 1.16: while the arm is out through a wormhole, that hand's item drops.
    static boolean emptyArmHand(ServerPlayer player) {
        TuskPortalOperations operations =
                TuskPortalOperations.get(player.server);
        if (!operations.hasActiveArm(player)) {
            return false;
        }
        InteractionHand hand = operations.armHandFor(player);
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }
        ItemStack dropped = held.copy();
        player.setItemInHand(hand, ItemStack.EMPTY);
        ItemEntity item = new ItemEntity(
                player.level(),
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                dropped);
        item.setPickUpDelay(40);
        player.level().addFreshEntity(item);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TuskPortalOperations.get(event.getServer())
                .tick(event.getServer());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            TuskOpenSpaceLeaseData.get(level).tick(level);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        TuskNailState state = TuskAddon.nailState(player);
        if (TuskAddon.isTuskUser(player)) {
            state.copyFrom(TuskAddon.nailState(event.getOriginal()));
        } else {
            state.reset();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            TuskAddon.syncNailState(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        syncOwnedState(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        closeOperation(event.getEntity());
        syncOwnedState(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        closeOperation(event.getEntity());
        syncOwnedState(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        closeOperation(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            closeOperation(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer trackingPlayer
                && event.getTarget() instanceof Player trackedPlayer) {
            TuskNailState state = TuskAddon.nailState(trackedPlayer);
            PacketDistributor.sendToPlayer(
                    trackingPlayer,
                    new TuskNailStateSyncPayload(
                            trackedPlayer.getId(),
                            state.nails(),
                            state.regenTicksRemaining(),
                            state.isInfiniteRotationCharged()));
        }
    }

    private static void syncOwnedState(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!TuskAddon.isTuskUser(player)) {
            TuskAddon.nailState(player).reset();
        }
        TuskAddon.syncNailState(serverPlayer);
    }

    private static void closeOperation(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            TuskPortalOperations.get(serverPlayer.server)
                    .closeOwned(player.getUUID());
        }
    }
}
