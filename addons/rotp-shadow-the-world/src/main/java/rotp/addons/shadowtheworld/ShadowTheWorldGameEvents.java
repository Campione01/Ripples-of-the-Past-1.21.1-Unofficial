package rotp.addons.shadowtheworld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.shadowtheworld.ability.ShadowEvolutionAbility;
import rotp.addons.shadowtheworld.ability.ShadowHeavyPunchAbility;
import rotp.addons.shadowtheworld.network.ShadowStandPanelPayload;
import rotp.addons.shadowtheworld.state.ShadowEvolutionState;
import rotp.addons.shadowtheworld.state.ShadowKnifeReleaseTracker;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ShadowTheWorldAddon.MOD_ID)
public final class ShadowTheWorldGameEvents {
    public static final int STAND_PANEL_DELAY_TICKS = 5;
    private static final Map<UUID, Integer> PENDING_STAND_PANEL =
            new HashMap<>();

    private ShadowTheWorldGameEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tickStandPanel(player);
        ShadowEvolutionState state =
                ShadowTheWorldAddon.state(player);
        if (state.tickFireProtection()) {
            player.clearFire();
        }
        if (!state.isEvolving()) {
            return;
        }
        int ticksLeft = state.evolutionTicks();
        ServerLevel level = player.serverLevel();
        if (ticksLeft > 0 && ticksLeft % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                level.sendParticles(
                        ParticleTypes.PORTAL,
                        player.getX()
                                + (player.getRandom()
                                                .nextDouble()
                                        - 0.5D)
                                        * 5.0D,
                        player.getY()
                                + (player.getRandom()
                                                .nextDouble()
                                        - 0.5D)
                                        * 4.0D,
                        player.getZ()
                                + (player.getRandom()
                                                .nextDouble()
                                        - 0.5D)
                                        * 5.0D,
                        1,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.2D);
            }
        }
        if (ticksLeft > 0
                && ticksLeft % 15 == 0
                && player.getRandom().nextFloat() < 0.15F) {
            spawnVisualLightning(level, player, false);
        }
        if (ticksLeft == 200) {
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    ShadowTheWorldAddon.EVOLUTION_BGM.get(),
                    player.getSoundSource(),
                    1.0F,
                    1.0F);
        }
        if (state.tickEvolution(player)) {
            spawnFinalLightningCircle(level, player);
            ShadowEvolutionAbility.completeEvolution(player);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof KnifeEntity knife
                && !knife.level().isClientSide()) {
            ShadowKnifeReleaseTracker.tick(knife);
        }
    }

    // 1.16 SHADOWWORLDEvolution: no item, block or entity use
    // while the night evolution runs
    public static boolean isRitualLocked(Player player) {
        return ShadowTheWorldAddon.state(player).isEvolving();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRitualRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (isRitualLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRitualRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (isRitualLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRitualEntityInteract(
            PlayerInteractEvent.EntityInteract event) {
        if (isRitualLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        ShadowEvolutionState state =
                ShadowTheWorldAddon.state(player);
        if (!state.counterActive(
                player.level().getGameTime())) {
            return;
        }
        event.setCanceled(true);
        state.consumeCounter();
        Entity directAttacker = event.getSource().getEntity();
        LivingEntity attacker =
                directAttacker instanceof StandEntity stand
                        ? stand.getUser()
                        : directAttacker
                                instanceof LivingEntity living
                                        ? living
                                        : null;
        if (attacker == null) {
            return;
        }
        // 1.16 teleportBehind: world.playSound(null, ...)
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ShadowTheWorldAddon
                        .SHADOW_WORLD_TIME_STOP_BLINK.get(),
                player.getSoundSource(),
                1.0F,
                0.7F);
        Vec3 look = attacker.getLookAngle().normalize();
        player.teleportTo(
                attacker.getX() - look.x * 1.5D,
                attacker.getY(),
                attacker.getZ() - look.z * 1.5D);
        player.lookAt(
                Anchor.EYES,
                attacker.getEyePosition());
        counterHeavyPunch(player, attacker, state);
    }

    private static void counterHeavyPunch(
            Player player,
            LivingEntity attacker,
            ShadowEvolutionState state) {
        if (!state.usedNetherStar()) {
            return;
        }
        StandPower power = StandPower.get(player);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity()
                : null;
        if (stand == null
                || !stand.isAlive()
                || stand.distanceTo(attacker)
                        > stand.getMaxRange()) {
            return;
        }
        stand.lookAt(
                Anchor.EYES,
                attacker.getEyePosition());
        // 1.16: power.clickAction(SHADOW_WORLD_HEAVY_PUNCH, ...)
        if (power.getAbility(ShadowTheWorldAddon.HEAVY)
                instanceof ShadowHeavyPunchAbility heavyPunch) {
            heavyPunch.clickAt(player, power, attacker);
        }
    }

    @SubscribeEvent
    public static void onTimeStopPreStart(
            TimeStopLifecycleEvent.PreStart event) {
        if (!isShadowTimeStop(event)) {
            return;
        }
        TimeStopState.Instance instance = event.getInstance();
        event.setInstance(instance.withStaminaCostTick(
                instance.staminaCostTick()
                        * ShadowTheWorldAddon
                                .DONOR_TIME_STOP_STAMINA_COST_TICK
                        / TimeStopLearning.BASE_STAMINA_COST_TICK));
    }

    @SubscribeEvent
    public static void onTimeStopAdded(
            TimeStopLifecycleEvent.Added event) {
        if (!isShadowTimeStop(event)
                || !(event.getLevel().getEntity(
                                event.getInstance().userId())
                        instanceof LivingEntity user)) {
            return;
        }
        event.getLevel().playSound(
                null,
                user.blockPosition(),
                ShadowTheWorldAddon
                        .SHADOW_WORLD_TIME_STOP.get(),
                SoundSource.AMBIENT,
                5.0F,
                1.0F);
    }

    // The resume sound and lines are the RESUME_SOUND / RESUME_VOICE cues
    // of the Shadow time-stop policy, not the stop's removal: the core ends
    // a manual resume 11 ticks after the press.

    private static boolean isShadowTimeStop(
            TimeStopLifecycleEvent event) {
        return event.getInstance().standTypeId().isPresent()
                && event.getInstance().standTypeId().get()
                        .equals(ShadowTheWorldAddon
                                .id("shadowtheworld"));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_STAND_PANEL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        ShadowTheWorldAddon
                .state(event.getEntity())
                .copyFrom(
                        event.getEntity(),
                        ShadowTheWorldAddon.state(
                                event.getOriginal()));
    }

    private static void spawnVisualLightning(
            ServerLevel level,
            LivingEntity user,
            boolean center) {
        double angle =
                level.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = center
                ? 0.0D
                : 3.0D
                        + level.getRandom().nextDouble() * 4.0D;
        LightningBolt lightning =
                EntityType.LIGHTNING_BOLT.create(level);
        if (lightning == null) {
            return;
        }
        lightning.moveTo(
                user.getX() + Math.cos(angle) * radius,
                user.getY(),
                user.getZ() + Math.sin(angle) * radius);
        lightning.setVisualOnly(true);
        level.addFreshEntity(lightning);
    }

    // 1.16 spawnFinalLightningCircle (night and quick evolution): 8 + 1
    // visual bolts, thunder 5.0 and impact 3.0
    public static void spawnFinalLightningCircle(
            ServerLevel level, LivingEntity user) {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0D;
            LightningBolt lightning =
                    EntityType.LIGHTNING_BOLT.create(level);
            if (lightning == null) {
                continue;
            }
            lightning.moveTo(
                    user.getX() + Math.cos(angle) * 3.0D,
                    user.getY(),
                    user.getZ() + Math.sin(angle) * 3.0D);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }
        spawnVisualLightning(level, user, true);
        level.playSound(
                null,
                user.getX(),
                user.getY(),
                user.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER,
                5.0F,
                0.8F + level.getRandom().nextFloat() * 0.2F);
        level.playSound(
                null,
                user.getX(),
                user.getY(),
                user.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.WEATHER,
                3.0F,
                0.6F + level.getRandom().nextFloat() * 0.2F);
    }

    // 1.16 OpenStandPanelPacket: 5 ticks after an evolution the user's
    // HUD switches to the Stand controls
    public static void scheduleStandPanel(ServerPlayer player) {
        PENDING_STAND_PANEL.put(
                player.getUUID(),
                player.server.getTickCount()
                        + STAND_PANEL_DELAY_TICKS);
    }

    private static void tickStandPanel(ServerPlayer player) {
        if (PENDING_STAND_PANEL.isEmpty()) {
            return;
        }
        Integer due = PENDING_STAND_PANEL.get(player.getUUID());
        if (due == null || player.server.getTickCount() < due) {
            return;
        }
        PENDING_STAND_PANEL.remove(player.getUUID());
        PacketDistributor.sendToPlayer(
                player, ShadowStandPanelPayload.INSTANCE);
    }
}
