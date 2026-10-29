package rotp.addons.khnum;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.khnum.disguise.DisguiseRetaliationService;
import rotp.addons.khnum.disguise.DisguiseScaleService;
import rotp.addons.khnum.entity.KhnumControllerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = KhnumAddon.MOD_ID)
public final class KhnumGameEvents {
    private KhnumGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                KhnumAddon.CONTROLLER.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        LivingEntity attacker = resolveLiving(
                event.getSource().getEntity());
        if (attacker instanceof Player player) {
            KhnumControllerEntity controller =
                    KhnumAddon.controller(player);
            if (controller != null && controller.isDisguised()) {
                DisguiseRetaliationService.mark(
                        mob,
                        player,
                        mob.level().getGameTime());
            }
        }
    }

    @SubscribeEvent
    public static void onMobTargetChange(
            LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(event.getNewAboutToBeSetTarget()
                        instanceof Player player)) {
            return;
        }
        KhnumControllerEntity controller =
                KhnumAddon.controller(player);
        if (controller != null
                && controller.isDisguised()
                && !DisguiseRetaliationService.canRetaliate(
                        mob,
                        player,
                        mob.level().getGameTime())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        clearForLifecycle(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && !event.getLevel().isClientSide()) {
            DisguiseScaleService.clearLegacyPersistence(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clearForLifecycle(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        clearForLifecycle(event.getOriginal());
        clearForLifecycle(event.getEntity());
    }

    private static void clearForLifecycle(LivingEntity living) {
        if (living == null || living.level().isClientSide()) {
            return;
        }
        KhnumControllerEntity controller =
                KhnumAddon.controller(living);
        if (controller != null) {
            controller.clearForLifecycle();
        }
        DisguiseScaleService.reset(living);
        if (living instanceof Player player) {
            DisguiseRetaliationService.clear(player);
        }
    }

    private static LivingEntity resolveLiving(Entity entity) {
        if (entity instanceof StandEntity stand) {
            return stand.getUser();
        }
        if (entity instanceof LivingEntity living) {
            return living;
        }
        return null;
    }
}
