package rotp.addons.thelock;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.thelock.item.TheLockItem;
import rotp.addons.thelock.state.LockStateService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = TheLockAddon.MOD_ID)
public final class TheLockEvents {
    private TheLockEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                TheLockAddon.THE_LOCK_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()
                && living.hasData(TheLockAddon.LOCK_STATE)) {
            LockStateService.tick(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        TheLockItem.enforceOwnedItem(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()
                || event.getAmount() <= 0.0F) {
            return;
        }
        LivingEntity attacker =
                resolveLivingAttacker(event.getSource());
        if (attacker != null) {
            LockStateService.addAttackGuilt(
                    attacker, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        LockStateService.chargeChat(
                event.getPlayer(), event.getRawText());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        LockStateService.clear(entity);
        LockStateService.clearOwnedBy(
                entity.getServer(), entity.getUUID());
        TheLockItem.purgeAll(entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        LockStateService.clear(original);
        LockStateService.clear(player);
        LockStateService.clearOwnedBy(
                player.getServer(), original.getUUID());
        TheLockItem.purgeAll(original);
        TheLockItem.purgeAll(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        TheLockItem.purgeAll(player);
        if (player.hasData(TheLockAddon.LOCK_STATE)) {
            LockStateService.tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        LockStateService.clear(player);
        LockStateService.clearOwnedBy(
                player.getServer(), player.getUUID());
        TheLockItem.purgeAll(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        LockStateService.clear(player);
        LockStateService.clearOwnedBy(
                player.getServer(), player.getUUID());
        TheLockItem.purgeAll(player);
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity target
                && target.hasData(TheLockAddon.LOCK_STATE)) {
            TheLockAddon.syncLockState(player, target);
        }
    }

    private static LivingEntity resolveLivingAttacker(
            DamageSource source) {
        LivingEntity direct = resolveLiving(source.getEntity());
        if (direct != null) {
            return direct;
        }
        return resolveLiving(source.getDirectEntity());
    }

    private static LivingEntity resolveLiving(Entity entity) {
        if (entity instanceof StandEntity stand) {
            return stand.getUser();
        }
        return entity instanceof LivingEntity living
                ? living
                : null;
    }
}
