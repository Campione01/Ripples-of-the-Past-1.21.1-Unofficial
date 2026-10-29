package rotp.addons.metallica;

import rotp.addons.metallica.state.MetallicaInvisibilityState.ClearReason;
import rotp.addons.metallica.state.MetallicaCompassService;
import rotp.addons.metallica.state.MetallicaIronService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import rotp.core.powersystem.standpower.entity.StandEntity;

@EventBusSubscriber(modid = MetallicaAddon.MOD_ID)
public final class MetallicaGameEvents {
    private MetallicaGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                MetallicaAddon.METALLICA_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onEntityTick(
            EntityTickEvent.Post event) {
        if (event.getEntity()
                instanceof ItemEntity itemEntity
                && !itemEntity.level().isClientSide()) {
            ItemStack restored =
                    itemEntity.getItem().copy();
            if (MetallicaCompassService.restoreTracked(
                    restored)) {
                itemEntity.setItem(restored);
            }
            return;
        }
        if (event.getEntity()
                instanceof ItemFrame itemFrame
                && !itemFrame.level().isClientSide()) {
            ItemStack restored =
                    itemFrame.getItem().copy();
            if (MetallicaCompassService.restoreTracked(
                    restored)) {
                itemFrame.setItem(restored);
            }
            return;
        }
        if (!(event.getEntity()
                instanceof LivingEntity living)
                || !(living.level()
                        instanceof ServerLevel)) {
            return;
        }
        if (living.hasData(MetallicaAddon.IRON_STATE)) {
            MetallicaAddon.ironState(living)
                    .tickServer(living);
        }
        if (living instanceof ServerPlayer player) {
            MetallicaCompassService.tickPlayer(player);
            if (player.hasData(
                    MetallicaAddon.INVISIBILITY_STATE)) {
                MetallicaAddon.invisibility(player)
                        .tickServer(player);
            }
            if (MetallicaAddon.controller(player) != null) {
                MetallicaIronService.tickActiveUser(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide()) {
            return;
        }
        if (living instanceof ServerPlayer player) {
            MetallicaCompassService.restorePlayer(player);
        }
        if (living.hasData(
                MetallicaAddon.INVISIBILITY_STATE)) {
            MetallicaAddon.invisibility(living).clear(
                    living, ClearReason.OWNER_DEATH);
        }
        clearIronState(living);
    }

    @SubscribeEvent
    public static void onPlayerClone(
            PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        clearPlayer(
                event.getOriginal(),
                ClearReason.OWNER_RESPAWN);
        clearPlayer(
                event.getEntity(),
                ClearReason.OWNER_RESPAWN);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            clearPlayer(
                    event.getEntity(),
                    ClearReason.OWNER_RESPAWN);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            clearPlayer(
                    event.getEntity(),
                    ClearReason.DIMENSION_CHANGE);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            clearPlayer(
                    event.getEntity(),
                    ClearReason.OWNER_LOGOUT);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            MetallicaCompassService.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(
            PlayerContainerEvent.Open event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            MetallicaCompassService.tickPlayer(
                    player, event.getContainer());
        }
    }

    @SubscribeEvent
    public static void onContainerClose(
            PlayerContainerEvent.Close event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            MetallicaCompassService.tickPlayer(
                    player, event.getContainer());
        }
    }

    @SubscribeEvent
    public static void onItemToss(
            ItemTossEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            ItemStack restored =
                    event.getEntity().getItem().copy();
            if (MetallicaCompassService.restoreTracked(
                    restored)) {
                event.getEntity().setItem(restored);
            }
        }
    }

    private static void clearPlayer(
            Player player, ClearReason reason) {
        if (player instanceof ServerPlayer serverPlayer) {
            MetallicaCompassService.restorePlayer(
                    serverPlayer);
        }
        if (player.hasData(
                MetallicaAddon.INVISIBILITY_STATE)) {
            MetallicaAddon.invisibility(player)
                    .clear(player, reason);
        }
        clearIronState(player);
    }

    private static void clearIronState(
            LivingEntity living) {
        if (living.hasData(MetallicaAddon.IRON_STATE)) {
            MetallicaAddon.ironState(living)
                    .clear(living);
        }
        if (living.hasEffect(
                MetallicaAddon.LACK_OF_IRON)) {
            living.removeEffect(
                    MetallicaAddon.LACK_OF_IRON);
        }
    }
}
