package rotp.addons.waytoheaven;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.waytoheaven.evolution.HeavenEvolutionService;
import rotp.addons.waytoheaven.item.SinnerBoneItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WayToHeavenAddon.MOD_ID)
public final class WayToHeavenGameEvents {
    private WayToHeavenGameEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity()
                        instanceof Player player)
                || !event.getSource().is(DamageTypes.FALL)
                || !HeavenEvolutionService
                        .hasFallImmunity(player)) {
            return;
        }
        if (HeavenEvolutionService
                .consumeFallImmunity(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            HeavenEvolutionService
                    .clearFallImmunity(player);
        }
        if (!event.getEntity().getType().is(
                WayToHeavenAddon.CRIMINALS)) {
            return;
        }

        Player killer =
                resolvePlayer(event.getSource());
        if (killer != null) {
            SinnerBoneItem.incrementFirst(killer);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()
                && HeavenEvolutionService
                        .hasFallImmunity(player)
                && !HeavenEvolutionService.isStandUser(
                        player,
                        WayToHeavenAddon
                                .MADE_IN_HEAVEN_ID)) {
            HeavenEvolutionService
                    .clearFallImmunity(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(
            PlayerEvent.Clone event) {
        HeavenEvolutionService.clearFallImmunity(
                event.getOriginal());
        HeavenEvolutionService.clearFallImmunity(
                event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        HeavenEvolutionService.clearFallImmunity(
                event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        HeavenEvolutionService.clearFallImmunity(
                event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRegisterCommands(
            RegisterCommandsEvent event) {
        WayToHeavenCommands.register(
                event.getDispatcher());
    }

    private static Player resolvePlayer(
            DamageSource source) {
        Player player = asPlayer(source.getEntity());
        return player != null
                ? player
                : asPlayer(source.getDirectEntity());
    }

    private static Player asPlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof StandEntity stand
                && stand.getUser() instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile
                && projectile.getOwner() != entity) {
            return asPlayer(projectile.getOwner());
        }
        return null;
    }
}
