package rotp.addons.emperor;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.emperor.item.EmperorItem;
import rotp.addons.emperor.state.EmperorModeState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = EmperorAddon.MOD_ID)
public final class EmperorEvents {
    private EmperorEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                EmperorAddon.EMPEROR_STAND_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        EmperorItem.enforceOwnedGun(event.getEntity());
    }

    @SubscribeEvent
    public static void onItemEntityJoin(
            EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ItemEntity itemEntity
                && itemEntity.getItem().is(
                        EmperorAddon.EMPEROR_ITEM.get())) {
            itemEntity.discard();
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            unsummonAndPurge(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        EmperorModeState state = EmperorAddon.modeState(player);
        state.copyFrom(EmperorAddon.modeState(event.getOriginal()));
        EmperorItem.purgeAll(event.getOriginal());
        EmperorItem.purgeAll(player);
        EmperorAddon.syncMode(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        EmperorItem.purgeAll(player);
        EmperorAddon.syncMode(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        EmperorItem.purgeAll(player);
        EmperorAddon.syncMode(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        unsummonAndPurge(player);
        EmperorAddon.syncMode(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        unsummonAndPurge(event.getEntity());
    }

    private static void unsummonAndPurge(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(player);
        if (power != null
                && power.getPowerType()
                        == EmperorAddon.THE_EMPEROR.get()) {
            EmperorAddon.THE_EMPEROR.get()
                    .forceUnsummon(player, power);
        } else {
            EmperorItem.purgeAll(player);
        }
        if (player instanceof ServerPlayer) {
            EmperorAddon.syncMode(player);
        }
    }
}
