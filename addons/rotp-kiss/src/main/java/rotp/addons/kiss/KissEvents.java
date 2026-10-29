package rotp.addons.kiss;

import rotp.addons.kiss.service.KissService;
import rotp.addons.kiss.service.KissTokenService;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = KissAddon.MOD_ID)
public final class KissEvents {
    private KissEvents() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(
                KissAddon.KISS_ENTITY.get(),
                rotp.core.powersystem.standpower.entity
                        .StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.tickCount % 10 == 0
                && (!KissAddon.isKissUser(player)
                        || rotp.core.powersystem.standpower
                                .StandPower.get(player) == null
                        || !rotp.core.powersystem.standpower
                                .StandPower.get(player).isSummoned())) {
            KissService.cleanupOwner(player.getServer(), player.getUUID());
            KissTokenService.clearTokens(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().getServer() == null) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            KissService.cleanupOwner(player.getServer(), player.getUUID());
            KissTokenService.clearTokens(player);
        }
    }

    @SubscribeEvent
    public static void onFinisherDamage(LivingDamageEvent.Post event) {
        // 1.16 KissFinisherPunchInstance.afterAttack: no head for a Stand target
        if (event.getNewDamage() <= 0.0F
                || event.getEntity() instanceof StandEntity
                || !(event.getSource().getDirectEntity() instanceof StandEntity stand)
                || !(stand.getUser() instanceof ServerPlayer owner)
                || !KissAddon.isKissUser(owner)) {
            return;
        }
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        if (action == null
                || action.ability.getAbilityId() == null
                || !KissAddon.FINISHER.equals(
                        action.ability.getAbilityId().nameInMoveset())) {
            return;
        }
        KissTokenService.grantEntityToken(owner, event.getEntity());
        if (event.getEntity().isDeadOrDying()) {
            KissTokenService.removeEntityTokens(owner, event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        if (original.getServer() != null) {
            KissService.cleanupOwner(original.getServer(), original.getUUID());
        }
        if (original instanceof ServerPlayer originalPlayer) {
            KissTokenService.clearTokens(originalPlayer);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            KissTokenService.clearTokens(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.getServer() != null) {
            KissService.cleanupOwner(player.getServer(), player.getUUID());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            KissTokenService.clearTokens(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.getServer() != null) {
            KissService.cleanupOwner(player.getServer(), player.getUUID());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            KissTokenService.clearTokens(serverPlayer);
        }
    }
}
