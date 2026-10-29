package rotp.addons.rhcp;

import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.rhcp.ability.RhcpAbility;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import rotp.addons.rhcp.network.RhcpStatePayload;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = RhcpAddon.MOD_ID)
public final class RhcpEvents {
    private RhcpEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                RhcpAddon.RHCP_ENTITY.get(),
                RhcpStandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RhcpState state = RhcpAddon.state(player);
        if (!RhcpAddon.isUser(player)) {
            RhcpService.cleanupTransient(player);
            if (state.clearAll()) {
                RhcpService.sync(player);
            }
            return;
        }
        if (player.tickCount % 40 == 0) {
            RhcpService.sync(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof RhcpStandEntity stand)
                || event.getAmount() <= 0.0F
                || stand.level().isClientSide()
                || !(stand.getUser() instanceof ServerPlayer player)) {
            return;
        }
        // 1.16 dodge: a Stand's hit that RHCP is fast enough for misses and
        // may start the counterattack or blitz (RhcpService.dodgeStandHit).
        if (RhcpService.dodgeStandHit(player, stand, event.getSource())) {
            event.setCanceled(true);
            RhcpService.sync(player);
            return;
        }
        // 1.16 RHCPEntity.actuallyHurt: a hit that lands ends the dodge.
        event.addReductionModifier(DamageContainer.Reduction.ARMOR,
                (container, reduction) -> {
                    EntityActionInstance action = stand.getCurStandAction();
                    if (RhcpAbility.modeOf(action) == RhcpAbility.Mode.DODGE
                            && action.getPhase() != ActionPhase.RECOVERY) {
                        action.startRecovery();
                    }
                    return reduction;
                });
        // rhcp_block is the core guard now (1.16 StandEntityBlock); its energy
        // cost is RhcpStandEntity.standDamageResistance.
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RhcpService.cleanupTransient(player);
            RhcpAddon.state(player).clearAll();
            RhcpService.sync(player);
        }
    }

    // Stand removed or replaced (stand give ... true, stand remove, disc
    // out): 1.16 lost the Stand entity and its battery with it.
    public static void onStandCleared(LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        if (user instanceof ServerPlayer player) {
            RhcpService.cleanupTransient(player);
        }
        if (RhcpAddon.state(user).clearAll()
                && user instanceof ServerPlayer player) {
            RhcpService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            RhcpService.cleanupTransient(original);
            RhcpAddon.state(original).clearAll();
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            RhcpAddon.state(player).clearAll();
            RhcpService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RhcpAddon.state(player).clearTransient();
            RhcpService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RhcpService.cleanupTransient(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RhcpService.cleanupTransient(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(
                    observer,
                    RhcpStatePayload.from(target));
        }
    }
}
