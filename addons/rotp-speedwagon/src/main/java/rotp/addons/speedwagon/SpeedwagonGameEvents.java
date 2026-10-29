package rotp.addons.speedwagon;

import java.util.Arrays;

import rotp.core.init.ModDamageTypes;
import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;

import rotp.addons.speedwagon.power.SpeedwagonData;
import rotp.addons.speedwagon.power.SpeedwagonTeaching;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(
        modid = SpeedwagonAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class SpeedwagonGameEvents {
    private static final boolean CORE_TARGET_RESOLVE_HOOK =
            Arrays.stream(PlayerPowerType.class.getMethods())
                    .anyMatch(method ->
                            method.getName().equals(
                                    "getTargetResolveMultiplier")
                                    && method.getParameterCount() == 2);

    private SpeedwagonGameEvents() {}

    @SubscribeEvent(
            priority = EventPriority.LOW,
            receiveCanceled = true)
    public static void onPlayerInteract(
            PlayerInteractEvent.EntityInteract event) {
        if (event.getCancellationResult() != InteractionResult.PASS
                || event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().isShiftKeyDown()
                || !(event.getEntity() instanceof ServerPlayer actor)
                || !(event.getTarget()
                        instanceof ServerPlayer target)) {
            return;
        }

        boolean handled = false;
        PlayerPower actorPower = PlayerPower.get(actor);
        SpeedwagonData targetData = PlayerPower.getPowerData(
                target, SpeedwagonAddon.SPEEDWAGON)
                .orElse(null);
        if (actorPower != null
                && !actorPower.hasPower()
                && targetData != null) {
            handled = SpeedwagonTeaching.requestFromPlayer(
                    actor, target);
        }
        else {
            SpeedwagonData actorData =
                    PlayerPower.getPowerData(
                            actor, SpeedwagonAddon.SPEEDWAGON)
                            .orElse(null);
            if (actorData != null
                    && actorData.hasPendingLearner(
                            target.getUUID())) {
                handled = SpeedwagonTeaching.teachPendingPlayer(
                        actor, target);
            }
        }

        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(
                    InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (CORE_TARGET_RESOLVE_HOOK) {
            return;
        }

        LivingEntity target = event.getEntity();
        LivingEntity targetUser = StandUtil.getStandUser(target);
        if (targetUser == null
                || PlayerPower.getPowerData(
                        targetUser,
                        SpeedwagonAddon.SPEEDWAGON)
                        .isEmpty()) {
            return;
        }

        DamageSource source = event.getSource();
        Entity sourceEntity = source.getEntity();
        float points = event.getAmount();
        if (source.is(ModDamageTypes.ADDS_RESOLVE)
                && sourceEntity
                        instanceof LivingEntity livingAttacker) {
            LivingEntity standUser =
                    StandUtil.getStandUser(livingAttacker);
            StandPower attackerStand =
                    StandPower.get(standUser);
            if (attackerStand != null
                    && attackerStand.hasPower()) {
                ResolveCounter.addResolve(
                        attackerStand, target, points);
            }
        }
        else if (sourceEntity
                instanceof LivingEntity livingAttacker) {
            StandPower attackerStand =
                    StandPower.get(livingAttacker);
            if (attackerStand != null
                    && attackerStand.isSummoned()) {
                ResolveCounter.addResolve(
                        attackerStand, target, points * 0.5F);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide()
                || SpeedwagonConfig.KEEP_SP_ON_DEATH.get()) {
            return;
        }
        PlayerPower.getPowerData(
                player, SpeedwagonAddon.SPEEDWAGON)
                .ifPresent(data -> data.cleanupForDeath(player));
    }
}
