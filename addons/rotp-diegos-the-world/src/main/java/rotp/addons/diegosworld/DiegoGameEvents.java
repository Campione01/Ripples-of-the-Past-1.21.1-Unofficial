package rotp.addons.diegosworld;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.item.StoneMaskItem;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;

import rotp.addons.diegosworld.entity.DiegosWorldStandEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

@EventBusSubscriber(modid = DiegosTheWorldAddon.MOD_ID)
public final class DiegoGameEvents {
    private static final Map<UUID, Entity> LAST_RIDDEN_VEHICLE =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> HEAVY_PUNCH_IN_TIME_STOP =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SUPPRESS_INHERITED_DIO_VOICE =
            new ConcurrentHashMap<>();

    private DiegoGameEvents() {}

    @SubscribeEvent
    public static void onRightClickKnife(
            PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        DiegosWorldStandEntity stand = activeStand(player);
        if (stand == null || !stack.is(ModItems.KNIFE.get())) {
            return;
        }
        if (stack.getCount() == 1
                && player.getItemBySlot(EquipmentSlot.HEAD)
                        .getItem() instanceof StoneMaskItem) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(
                InteractionResult.sidedSuccess(
                        player.level().isClientSide()));
        if (player.level().isClientSide()) {
            return;
        }

        int knivesThrown = player.isShiftKeyDown()
                ? 1
                : Math.min(stack.getCount(), 8);
        for (int i = 0; i < knivesThrown; i++) {
            ItemStack projectileStack =
                    new ItemStack(ModItems.KNIFE.get());
            KnifeEntity knife = new KnifeEntity(
                    player.level(), player, projectileStack);
            knife.pickup = AbstractArrow.Pickup.ALLOWED;
            knife.setTimeStopFlightTicks(5);
            knife.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    1.5F,
                    i == 0 ? 1.0F : 16.0F);
            player.level().addFreshEntity(knife);
        }
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                knivesThrown == 1
                        ? ModSoundEvents.KNIFE_THROW.get()
                        : ModSoundEvents.KNIVES_THROW.get(),
                SoundSource.PLAYERS,
                0.0F,
                0.4F / (player.getRandom().nextFloat()
                        * 0.4F + 0.8F));
        player.getCooldowns().addCooldown(
                ModItems.KNIFE.get(), knivesThrown * 3);
        if (!player.getAbilities().instabuild) {
            stack.shrink(knivesThrown);
        }
        stand.onKnivesThrow(
                player.level(), player, stack, knivesThrown);
    }

    // Donor staminaCostTick(7.5) against the core's 9, both scaled by
    // 100 / the stop's trained ticks; the donor start cost is the core's 225.
    @SubscribeEvent
    public static void onTimeStopPreStart(
            TimeStopLifecycleEvent.PreStart event) {
        TimeStopState.Instance instance = event.getInstance();
        if (!instance.standTypeId()
                .map(DiegosTheWorldAddon.id("the_world")::equals)
                .orElse(false)) {
            return;
        }
        event.setInstance(instance.withStaminaCostTick(
                instance.staminaCostTick()
                        * DiegosTheWorldAddon
                                .DONOR_TIME_STOP_STAMINA_COST_TICK
                        / TimeStopLearning.BASE_STAMINA_COST_TICK));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTimeStopEffectExpired(
            MobEffectEvent.Expired event) {
        MobEffectInstance effect = event.getEffectInstance();
        LivingEntity entity = event.getEntity();
        if (effect == null
                || !effect.getEffect().is(ModStatusEffects.TIME_STOP)
                || !(entity.level()
                        instanceof ServerLevel level)
                || !isDiegoUser(entity)) {
            return;
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        ChunkPos chunk = new ChunkPos(entity.blockPosition());
        if (state.isTimeStopped(chunk)
                && state.getTimeStopTicks(chunk) >= 40) {
            JojoModUtil.sayVoiceLine(
                    entity,
                    DiegosTheWorldAddon.DIEGO_CANT_MOVE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTimeStopExpired(
            TimeStopLifecycleEvent.Removed event) {
        if (event.getReason()
                        != TimeStopLifecycleEvent.RemovalReason.EXPIRED
                || !(event.getLevel().getEntity(
                                event.getInstance().userId())
                        instanceof LivingEntity entity)
                || !isDiegoUser(entity)) {
            return;
        }
        TimeStopState state = event.getLevel().getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        ChunkPos chunk = new ChunkPos(entity.blockPosition());
        if (state.getTimeStopTicks(chunk) >= 40) {
            JojoModUtil.sayVoiceLine(
                    entity,
                    DiegosTheWorldAddon.DIEGO_CANT_MOVE);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        expireInheritedDioVoiceMarker(
                player.getUUID(), level.getGameTime());
        if (!isDiegoUser(player)) {
            return;
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        boolean timeStopped = state.isTimeStopped(player);
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            LAST_RIDDEN_VEHICLE.put(player.getUUID(), vehicle);
            if (timeStopped
                    && vehicle instanceof LivingEntity livingVehicle
                    && !livingVehicle.hasEffect(
                            ModStatusEffects.TIME_STOP)) {
                livingVehicle.addEffect(new MobEffectInstance(
                        ModStatusEffects.TIME_STOP,
                        20,
                        0,
                        false,
                        false,
                        true));
            }
        }
        else {
            Entity lastVehicle =
                    LAST_RIDDEN_VEHICLE.remove(player.getUUID());
            if (timeStopped
                    && lastVehicle != null
                    && lastVehicle.isAlive()
                    && player.distanceToSqr(lastVehicle) > 9.0D) {
                lastVehicle.teleportTo(
                        player.getX(),
                        player.getY(),
                        player.getZ());
                lastVehicle.fallDistance = 0.0F;
                player.fallDistance = 0.0F;
                player.startRiding(lastVehicle, true);
            }
        }
        if (player.tickCount % 200 == 0) {
            long now = level.getGameTime();
            HEAVY_PUNCH_IN_TIME_STOP.entrySet().removeIf(
                    entry -> now - entry.getValue() > 300L);
        }
    }

    private static void expireInheritedDioVoiceMarker(
            UUID userId, long gameTime) {
        Long markerTime = SUPPRESS_INHERITED_DIO_VOICE.get(userId);
        if (markerTime != null && gameTime - markerTime > 1L) {
            SUPPRESS_INHERITED_DIO_VOICE.remove(userId, markerTime);
        }
    }

    public static void markHeavyPunchInTimeStop(
            StandEntity stand, LivingEntity target) {
        if (!(stand.level() instanceof ServerLevel level)
                || stand.getUser() == null
                || !isDiegoUser(stand.getUser())) {
            return;
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        if (state.isTimeStopped(target)) {
            HEAVY_PUNCH_IN_TIME_STOP.put(
                    target.getUUID(), level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        LivingEntity user = attacker instanceof StandEntity stand
                ? stand.getUser()
                : attacker instanceof LivingEntity living
                        ? living
                        : null;
        if (attacker instanceof DiegosWorldStandEntity stand
                && user != null
                && isDiegoUser(user)
                && LivingComponentAction.getCurEntityAction(stand)
                        instanceof TheWorldTSPunch) {
            HEAVY_PUNCH_IN_TIME_STOP.remove(target.getUUID());
            // The core says its DIO line past 4 blocks (replaced below);
            // donor THEWORLDTSHeavyAttack said Diego's at any distance.
            if (stand.distanceToSqr(user) > 16.0D) {
                SUPPRESS_INHERITED_DIO_VOICE.put(
                        user.getUUID(), target.level().getGameTime());
            }
            else {
                JojoModUtil.sayVoiceLine(
                        user,
                        DiegosTheWorldAddon.DIEGO_THIS_IS_THE_WORLD);
            }
            return;
        }
        Long hitTime = HEAVY_PUNCH_IN_TIME_STOP.remove(
                target.getUUID());
        if (hitTime == null
                || !(target.level() instanceof ServerLevel level)
                || level.getGameTime() - hitTime > 300L) {
            return;
        }
        if (user != null && isDiegoUser(user)) {
            JojoModUtil.sayVoiceLine(
                    user,
                    DiegosTheWorldAddon.DIEGO_THIS_IS_THE_WORLD);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayLevelSound(
            PlayLevelSoundEvent.AtPosition event) {
        replaceInheritedDioVoice(event, event.getPosition());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayLevelSound(
            PlayLevelSoundEvent.AtEntity event) {
        replaceInheritedDioVoice(event, event.getEntity().position());
    }

    private static void replaceInheritedDioVoice(
            PlayLevelSoundEvent event, Vec3 position) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.getSound() == null
                || event.getSound().value()
                        != ModSoundEvents.DIO_THIS_IS_THE_WORLD.get()
                || event.getSource() != SoundSource.VOICE) {
            return;
        }
        long now = level.getGameTime();
        SUPPRESS_INHERITED_DIO_VOICE.entrySet().removeIf(
                entry -> now - entry.getValue() > 1L);
        for (Map.Entry<UUID, Long> entry
                : SUPPRESS_INHERITED_DIO_VOICE.entrySet()) {
            Entity user = level.getEntity(entry.getKey());
            if (user != null
                    && user.position().distanceToSqr(position) < 1.0E-6D
                    && SUPPRESS_INHERITED_DIO_VOICE.remove(
                            entry.getKey(), entry.getValue())) {
                event.setSound(
                        DiegosTheWorldAddon.DIEGO_THIS_IS_THE_WORLD);
                return;
            }
        }
    }

    private static boolean isDiegoUser(LivingEntity user) {
        StandPower power = StandPower.get(user);
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == DiegosTheWorldAddon.THE_WORLD.get();
    }

    private static DiegosWorldStandEntity activeStand(
            Player player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == DiegosTheWorldAddon.THE_WORLD.get()
                && power.getSummonedStandEntity()
                        instanceof DiegosWorldStandEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }
}
