package rotp.addons.reworked;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStands;
import rotp.core.item.KnifeItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands.magiciansred.MRFlameEntity;

import rotp.addons.reworked.state.ReworkedLivingState;
import rotp.addons.reworked.ability.ReworkedStandAbilities;
import rotp.addons.reworked.network.ReworkedManualInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = ReworkedAddon.MOD_ID)
public final class ReworkedGameEvents {
    private static final float END_POSITION_LIMIT = 2.5F;
    private static final float END_ROTATION_LIMIT = 15.0F;
    private static final float TW_RESOLVE_PER_TICK = 20.0F;
    private static final float ROAD_ROLLER_RESOLVE = 250.0F;
    private static final float KNIFE_RESOLVE = 8.0F;
    private static final float LEGACY_FLAME_DAMAGE = 0.6F;
    private static final float LEGACY_FLAME_KNOCKBACK_RATIO = 0.1F;
    private static final int LEGACY_FLAME_EFFECT_TICKS = 140;
    // 1.16 Reworked's MRFlameEntity.hurtTarget overwrite:
    // dealDamageAndSetOnFire(target, 7 s, stand fire)
    private static final int LEGACY_FLAME_FIRE_TICKS = 7 * 20;
    private static final double PROJECTILE_SCAN_RANGE = 5.0D;
    private static final float PROJECTILE_HORIZONTAL_SPEED = 0.6F;
    private static final double ARROW_DAMAGE_MULTIPLIER = 2.2D;
    private static final double PROJECTILE_HITBOX_INFLATION = 1.6D;
    private static final int PROJECTILE_PREDICTION_TICKS = 4;
    private static final int STAR_PLATINUM_AUTOMATIC_TS_TICKS = 8;
    private static final int THE_WORLD_AUTOMATIC_TS_TICKS = 4;

    private static final Map<TimeStopKey, CastSnapshot>
            THE_WORLD_CASTS = new HashMap<>();
    private static final Set<TimeStopKey>
            AUTOMATIC_TIME_STOP_STARTS = new HashSet<>();
    // Stops started by the moveset's "time_stop", the only ones the
    // legacy Reworked cooldown applies to.
    private static final Set<TimeStopKey>
            TIME_STOP_STARTS = new HashSet<>();
    // 1.16 onUserTSEnd/onUserTSCancel: what the user's TIME_STOP effect
    // had left when it went (0 when it ran out), by entity id.
    private static final Map<Integer, Integer>
            TIME_STOP_EFFECT_TICKS_LEFT = new HashMap<>();
    // The flame hit whose hurt has not reached onIncomingDamage yet, with the
    // fire its Slow Burn left (the core flame's own 10 s fire comes after).
    @Nullable
    private static FlameHit pendingFlameHit;

    private ReworkedGameEvents() {}

    // 1.16 Reworked's flame hurtTarget added Slow Burn and the heat before
    // the hurt, whether or not the hurt went through.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof MRFlameEntity flame)
                || flame.level().isClientSide()
                || !flame.isAlive()
                || !(event.getRayTraceResult()
                        instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        applyFlameSlowBurn(target, flame);
        pendingFlameHit = new FlameHit(flame.getId(), target.getId(),
                target.getRemainingFireTicks());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getSource().getDirectEntity()
                        instanceof MRFlameEntity flame)) {
            return;
        }
        event.setAmount(
                event.getAmount() * LEGACY_FLAME_DAMAGE);
        FlameHit hit = pendingFlameHit;
        if (hit != null && hit.flameId() == flame.getId()
                && hit.targetId() == event.getEntity().getId()) {
            pendingFlameHit = null;
            // The donor's 7 s replace the core flame's 10 s; a failed hurt
            // still puts back the fire Slow Burn left, as 1.16 did.
            event.getEntity().setRemainingFireTicks(hit.fireTicks());
            DamageUtil.setOnFire(
                    event.getEntity(), LEGACY_FLAME_FIRE_TICKS, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKnockBack(
            LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide()
                || event.getEntity().getLastDamageSource() == null
                || !(event.getEntity()
                        .getLastDamageSource()
                        .getDirectEntity()
                        instanceof MRFlameEntity)) {
            return;
        }
        event.setStrength(
                event.getStrength()
                        * LEGACY_FLAME_KNOCKBACK_RATIO);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAbilityKeyPress(
            RipplesAbilityKeyPressEvent event) {
        if (!(event.getPower() instanceof StandPower power)
                || power.getSummonedStandEntity() == null) {
            return;
        }
        // The World's TS punch recovery resolves in
        // ReworkedTheWorldTSPunchAbility.replaceWithSubAbility, before
        // the TS punch's own cooldown is checked.
        if (power.getPowerType() != ModStands.STAR_PLATINUM.get()
                || !isStarPlatinumBlinkPress(
                        event.getAbility().getAbilityId().nameInMoveset(),
                        event.inputMethod)) {
            return;
        }
        // The heavy key and the grab click resolve to the blink themselves
        // (replaceWithSubAbility); this swap serves a heavy_punch or grab
        // resolved from the moves of a tick before the windup, or sent by a
        // client that did not resolve the blink (ClAbilityInputPacket then
        // takes the requested ability, unrefused).
        String blink = ReworkedStandAbilities.getStarPlatinumBlinkRepeat(
                power.getSummonedStandEntity());
        Ability blinkAbility = blink != null ? power.getAbility(blink) : null;
        // 1.16 Action.getVisibleAction: a blink not unlocked (its time stop)
        // left the key its own action
        if (blinkAbility != null && blinkAbility.isAbilityAvailable(power)) {
            event.replaceAbility(blinkAbility);
        }
    }

    // A Shift re-press arrives as plain RMB heavy_punch, or with default
    // keys as the Shift+RMB grab click (core grab_charged_heavy mapping).
    static boolean isStarPlatinumBlinkPress(
            String nameInMoveset, InputMethod inputMethod) {
        return "heavy_punch".equals(nameInMoveset)
                || "grab".equals(nameInMoveset)
                        && inputMethod == InputMethod.CLICK;
    }

    @SubscribeEvent
    public static void onLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event) {
        ReworkedManualInput.clear(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)
                || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        handleProjectileThreatTimeStop(level, entity);
        if (entity instanceof Player) {
            ReworkedStandAbilities.unlockFlameBurstForCreative(
                    StandPower.get(entity));
        }

        MobEffectInstance slowBurn =
                entity.getEffect(ReworkedAddon.SLOW_BURN);
        if (slowBurn != null
                && entity.getRemainingFireTicks() <= 0) {
            entity.removeEffect(ReworkedAddon.SLOW_BURN);
            ReworkedAddon.state(entity).setHeat(0.0F);
        }

        if (!ReworkedConfig.ENABLE_TS_RESOLVE.get()) {
            return;
        }
        StandPower power = StandPower.get(entity);
        if (!isTheWorld(power)
                || !power.isAbilityUnlocked(
                        TimeStopLearning.TIME_STOP)
                // trained ticks, as 1.16 (Creative's template passed
                // with no learning and divided by zero below)
                || TimeStopLearning.getSavedTimeStopTicks(power) < 50
                || entity.isSprinting()
                // 1.16 addTWResolveInTS: func_233570_aj_ (isOnGround), no
                // sneak condition
                || !entity.onGround()
                || !entity.hasEffect(ModStatusEffects.TIME_STOP)) {
            return;
        }
        TimeStopState state =
                level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        if (!state.isTimeStopped(entity)
                || power.getCurTypeData() == null) {
            return;
        }
        float learningPoints = power.getCurTypeData()
                .getAbilityLearningProgressPoints(
                        TimeStopLearning.TIME_STOP);
        power.getResolveCounter().addResolveValue(
                power, TW_RESOLVE_PER_TICK / learningPoints);
    }

    private static void handleProjectileThreatTimeStop(
            ServerLevel level, LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        boolean starPlatinum = isEligibleStarPlatinum(power, entity);
        boolean theWorld = isEligibleTheWorld(power);
        if (!entity.isAlive() || !starPlatinum && !theWorld) {
            return;
        }

        Ability timeStop = power.getAbility(TimeStopLearning.TIME_STOP);
        if (timeStop == null
                || timeStop.abilityType
                        != ReworkedAddon.STAR_PLATINUM_TIME_STOP.get()
                        && timeStop.abilityType
                                != ReworkedAddon.THE_WORLD_TIME_STOP.get()
                || timeStop.isAbilityUnlocked(power)) {
            return;
        }

        ProjectileThreat threat =
                scanProjectileThreats(level, entity, power);
        if (threat == ProjectileThreat.ABORT) {
            return;
        }

        TimeStopState state =
                level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        ChunkPos center = new ChunkPos(entity.blockPosition());
        boolean timeStopped = state.isTimeStopped(center);
        if (!timeStopped && threat != ProjectileThreat.HIT) {
            return;
        }
        PowerSkillUnlocks.Result unlockResult =
                ReworkedAddon.POWER_SKILL_UNLOCKS.forceUnlock(
                        power, TimeStopLearning.TIME_STOP);
        boolean unlocked = switch (unlockResult) {
            case UNLOCKED, ALREADY_UNLOCKED -> true;
            default -> false;
        };
        if (!unlocked || !timeStop.isAbilityUnlocked(power)) {
            return;
        }
        if (timeStopped) {
            return;
        }

        int ticks = starPlatinum
                ? STAR_PLATINUM_AUTOMATIC_TS_TICKS
                : THE_WORLD_AUTOMATIC_TS_TICKS;
        String visualRoute = starPlatinum
                ? "star_platinum_time_stop"
                : "the_world_time_stop";
        TimeStopState.Instance instance =
                new TimeStopState.Instance(
                        entity.getId(),
                        ticks,
                        ticks,
                        center,
                        JojoModConfig.getCommonConfigInstance(false)
                                .timeStopChunkRange.get(),
                        entity.getId(),
                        visualRoute,
                        automaticStaminaCostTick(power, timeStop))
                        .withStandSkin(power)
                        .withRefundUnusedStartCost(false);
        TimeStopKey automaticStart = key(level, instance);
        AUTOMATIC_TIME_STOP_STARTS.add(automaticStart);
        boolean started;
        try {
            started = state.tryPutInstance(instance);
        }
        finally {
            AUTOMATIC_TIME_STOP_STARTS.remove(automaticStart);
        }
        if (started) {
            entity.addEffect(new MobEffectInstance(
                    ModStatusEffects.TIME_STOP,
                    ticks,
                    0,
                    false,
                    false,
                    true));
        }
    }

    // 1.16 ran the automatic stop on the RE_*_TIME_STOP it had just
    // unlocked, so TimeStop.getStaminaCostTicking priced it like a manual
    // one: 8.875 * 100 / trained ticks (5 at a first unlock).
    private static float automaticStaminaCostTick(
            StandPower power, Ability timeStop) {
        return TimeStopLearning.getTimeStopStaminaCostTick(
                        power, TimeStopLearning.getLearningName(timeStop))
                * ReworkedStandAbilities.TIME_STOP_STAMINA_COST_TICK
                / TimeStopLearning.BASE_STAMINA_COST_TICK;
    }

    private static boolean isEligibleStarPlatinum(
            StandPower power, LivingEntity entity) {
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == ModStands.STAR_PLATINUM.get()
                && power.getResolveLevel() >= 4
                && entity.hasEffect(ModStatusEffects.RESOLVE);
    }

    private static boolean isEligibleTheWorld(StandPower power) {
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == ModStands.THE_WORLD.get()
                && power.getResolveLevel() >= 2;
    }

    private static ProjectileThreat scanProjectileThreats(
            ServerLevel level,
            LivingEntity entity,
            StandPower power) {
        boolean shouldHit = false;
        for (Entity nearby : level.getEntities(
                (Entity) null,
                entity.getBoundingBox().inflate(PROJECTILE_SCAN_RANGE),
                candidate -> true)) {
            if (!(nearby instanceof Projectile projectile)) {
                continue;
            }
            Vec3 movement = projectile.getDeltaMovement();
            if (!(Math.abs(movement.x)
                            >= PROJECTILE_HORIZONTAL_SPEED)
                    && !(Math.abs(movement.z)
                            >= PROJECTILE_HORIZONTAL_SPEED)
                    || projectile.getOwner() == entity
                    || projectile.getOwner()
                            == power.getSummonedStandEntity()) {
                continue;
            }

            if (projectile instanceof AbstractArrow arrow) {
                if (entity.getHealth()
                        <= arrow.getBaseDamage()
                                * ARROW_DAMAGE_MULTIPLIER) {
                    CompoundTag arrowData =
                            arrow.saveWithoutId(new CompoundTag());
                    if (arrowData.getBoolean("inGround")) {
                        return ProjectileThreat.ABORT;
                    }
                }
                else {
                    return ProjectileThreat.ABORT;
                }
            }

            Vec3 predictedPosition = projectile.position().add(
                    projectile.getDeltaMovement().scale(
                            PROJECTILE_PREDICTION_TICKS));
            if (entity.getBoundingBox()
                    .inflate(PROJECTILE_HITBOX_INFLATION)
                    .contains(predictedPosition)) {
                shouldHit = true;
            }
        }
        return shouldHit
                ? ProjectileThreat.HIT
                : ProjectileThreat.CLEAR;
    }

    // 1.16 RE_*_TIME_STOP staminaCostTick(8.875) where the core's is 9;
    // both are then scaled by 100 / the stop's trained ticks.
    @SubscribeEvent
    public static void onTimeStopPreStart(
            TimeStopLifecycleEvent.PreStart event) {
        TimeStopState.Instance instance = event.getInstance();
        if (AUTOMATIC_TIME_STOP_STARTS.contains(
                        key(event.getLevel(), instance))
                || !(event.getLevel().getEntity(instance.userId())
                        instanceof LivingEntity user)) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (power == null
                || !(power.getAbility(TimeStopLearning.TIME_STOP)
                        instanceof ReworkedStandAbilities
                                .ReworkedTimeStopAbility)) {
            return;
        }
        event.setInstance(instance.withStaminaCostTick(
                instance.staminaCostTick()
                        * ReworkedStandAbilities
                                .TIME_STOP_STAMINA_COST_TICK
                        / TimeStopLearning.BASE_STAMINA_COST_TICK));
    }

    @SubscribeEvent
    public static void onTimeStopAdded(
            TimeStopLifecycleEvent.Added event) {
        TimeStopState.Instance instance = event.getInstance();
        Entity rawUser = event.getLevel()
                .getEntity(instance.userId());
        TimeStopKey startKey = key(event.getLevel(), instance);
        TIME_STOP_EFFECT_TICKS_LEFT.remove(instance.userId());
        boolean automatic = AUTOMATIC_TIME_STOP_STARTS.contains(
                key(event.getLevel(), instance));
        // Reworked's projectile stops run on the Stand's time_stop (1.16
        // GameplayEventHandler); a manual one is still that action's.
        if (automatic || rawUser instanceof LivingEntity starter
                && isStartedByTimeStop(starter)) {
            TIME_STOP_STARTS.add(startKey);
        }
        else {
            TIME_STOP_STARTS.remove(startKey);
        }
        if (!(rawUser instanceof LivingEntity user)) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (!isTheWorldInstance(instance, power)) {
            return;
        }
        if (automatic) {
            return;
        }
        THE_WORLD_CASTS.put(
                key(event.getLevel(), instance),
                new CastSnapshot(
                        user.position(),
                        user.getXRot(),
                        user.getYRot()));
    }

    @SubscribeEvent
    public static void onTimeStopRemoved(
            TimeStopLifecycleEvent.Removed event) {
        TimeStopState.Instance instance = event.getInstance();
        Entity rawUser = event.getLevel()
                .getEntity(instance.userId());
        Integer effectTicksLeft = TIME_STOP_EFFECT_TICKS_LEFT.remove(
                instance.userId());
        // 1.16 Reworked's TimeStopInstance.onRemoved overwrite cooled down
        // only the TimeStop action that started the stop (and its blink);
        // other Stands' stops keep the core cooldown on their own ability.
        boolean startedByTimeStop = TIME_STOP_STARTS.remove(
                key(event.getLevel(), instance));
        if (startedByTimeStop
                && rawUser instanceof LivingEntity user) {
            restoreLegacyTimeStopCooldown(
                    instance, user, StandPower.get(user));
        }
        CastSnapshot snapshot = THE_WORLD_CASTS.remove(
                key(event.getLevel(), instance));
        if (snapshot == null
                || !(rawUser instanceof LivingEntity user)
                || !ReworkedConfig.ENABLE_TS_RESOLVE.get()) {
            return;
        }
        StandPower power = StandPower.get(user);
        // 1.16 getTimeStopTicks was the trained ticks; Creative's
        // near-infinite template must not price the resolve.
        if (!isTheWorld(power)
                || TimeStopLearning.getSavedTimeStopTicks(power) < 50) {
            return;
        }

        float positionDifference =
                (float) user.position().distanceTo(snapshot.position());
        float xRotDifference =
                user.getXRot() - snapshot.xRot();
        float yRotDifference =
                user.getYRot() - snapshot.yRot();
        float rotationDifference = (float) Math.sqrt(
                xRotDifference * xRotDifference
                        + yRotDifference * yRotDifference);
        if (positionDifference > END_POSITION_LIMIT
                || rotationDifference > END_ROTATION_LIMIT) {
            return;
        }

        // 1.16 divided by the TIME_STOP effect's ticks left (1 once it ran
        // out), so a stop resumed early pays little; the instance's own
        // ticks are 0 after any resume.
        int ticksLeft = Math.max(effectTicksLeft != null
                ? effectTicksLeft
                : instance.ticksLeft(), 1);
        float visibleBoost =
                power.getResolveCounter()
                        .getTotalBoostVisible(user);
        float resolve =
                0.5F
                        * (END_POSITION_LIMIT - positionDifference)
                        * (END_ROTATION_LIMIT - rotationDifference)
                        / (float) Math.sqrt(visibleBoost)
                        * TimeStopLearning.getSavedTimeStopTicks(power)
                        / ticksLeft;
        power.getResolveCounter().addResolveValue(power, resolve);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        StandPower power = StandPower.get(player);
        if (!isTheWorld(power)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        TimeStopState state =
                level.getData(ModDataAttachmentTypes.TIME_STOP.get());

        if (stack.is(ModItems.ROAD_ROLLER.get())
                && state.isTimeStopped(player)
                && player.hasEffect(ModStatusEffects.TIME_STOP)) {
            power.getResolveCounter().addResolveValue(
                    power, ROAD_ROLLER_RESOLVE);
            return;
        }
        if (!stack.is(ModItems.KNIFE.get())
                || power.getSummonedStandEntity() == null
                || !ModStands.THE_WORLD.getId().equals(
                        power.getSummonedStandEntity()
                                .getStandType())) {
            return;
        }
        int knivesThrown = player.isShiftKeyDown()
                ? 1
                : Math.min(
                        stack.getCount(),
                        KnifeItem.MAX_KNIVES_THROW);
        if (knivesThrown > 1
                && state.isTimeStopped(player)) {
            power.getResolveCounter().addResolveValue(
                    power, knivesThrown * KNIFE_RESOLVE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEffectAdded(
            MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null
                || !instance.getEffect()
                        .is(ReworkedAddon.SLOW_BURN)) {
            return;
        }
        int ticksBack = instance.getAmplifier() < 6
                ? instance.getDuration()
                        / 20
                        / (7 - instance.getAmplifier())
                : 0;
        event.getEntity().setRemainingFireTicks(
                Math.max(
                        event.getEntity().getRemainingFireTicks(),
                        instance.getDuration() + ticksBack));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectRemoved(
            MobEffectEvent.Remove event) {
        MobEffectInstance removed = event.getEffectInstance();
        if (removed != null
                && removed.getEffect().is(ModStatusEffects.TIME_STOP)
                && !event.getEntity().level().isClientSide()) {
            TIME_STOP_EFFECT_TICKS_LEFT.put(
                    event.getEntity().getId(), removed.getDuration());
        }
        if (event.getEffect().is(ReworkedAddon.SLOW_BURN)) {
            ReworkedAddon.state(event.getEntity())
                    .setHeat(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectExpired(
            MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null
                && instance.getEffect().is(ModStatusEffects.TIME_STOP)
                && !event.getEntity().level().isClientSide()) {
            TIME_STOP_EFFECT_TICKS_LEFT.put(event.getEntity().getId(), 0);
        }
        if (instance != null
                && instance.getEffect()
                        .is(ReworkedAddon.SLOW_BURN)) {
            ReworkedAddon.state(event.getEntity())
                    .setHeat(0.0F);
        }
    }

    private static void applyFlameSlowBurn(
            LivingEntity target, MRFlameEntity flame) {
        ReworkedLivingState state = ReworkedAddon.state(target);
        int amplifier = (int) Math.floor(state.heat());
        target.addEffect(new MobEffectInstance(
                ReworkedAddon.SLOW_BURN,
                LEGACY_FLAME_EFFECT_TICKS,
                amplifier,
                false,
                false,
                true));

        LivingEntity source = flame.getOwner();
        if (source instanceof StandEntity stand) {
            source = stand.getUser();
        }
        StandPower sourcePower =
                source != null ? StandPower.get(source) : null;
        int multiplier = sourcePower != null
                && sourcePower.getResolveLevel() >= 2
                        ? 2
                        : 1;
        state.addHeatRegressive(
                1.75F * multiplier, multiplier);
    }

    private static void restoreLegacyTimeStopCooldown(
            TimeStopState.Instance instance,
            LivingEntity user,
            StandPower power) {
        if (power == null || !power.hasPower()) {
            return;
        }
        float cooldown = power.isUserCreative()
                ? 0.0F
                : TimeStopCooldowns.TIME_STOP_COOLDOWN_PER_TICK
                        * Math.max(instance.ticksPassed(), 0);
        boolean theWorld = isTheWorldInstance(instance, power);
        if (!theWorld
                && user.hasEffect(ModStatusEffects.RESOLVE)) {
            cooldown /= 3.0F;
        }

        float multiplier = 1.0F;
        if (theWorld) {
            float resolveRatio = power.getResolveCounter()
                    .getResolveRatio(power);
            multiplier =
                    (resolveRatio * resolveRatio
                            * -90.0F
                            + 105.0F)
                            / 22.5F;
        }
        else if (power.getPowerType()
                == ModStands.STAR_PLATINUM.get()) {
            multiplier = 3.0F;
        }

        power.setAbilityCooldown(
                TimeStopCooldowns.TIME_STOP,
                (int) (cooldown * multiplier));
        if (power.getMoveset().getAbility(
                TimeStopCooldowns.TIME_STOP_BLINK) != null) {
            power.setAbilityCooldown(
                    TimeStopCooldowns.TIME_STOP_BLINK,
                    (int) (cooldown
                            * TimeStopCooldowns
                                    .TIME_STOP_BLINK_COOLDOWN_RATIO
                            * multiplier));
        }
    }

    // The Added event fires while the starting action is still current, in
    // the user's Stand-power slot, the Stand out or not; the user's own slot
    // and the Stand's still match a time stop that leaves that slot.
    private static boolean isStartedByTimeStop(LivingEntity user) {
        StandPower power = StandPower.get(user);
        Ability timeStop = power != null && power.hasPower()
                ? power.getMoveset().getAbility(
                        TimeStopCooldowns.TIME_STOP)
                : null;
        if (timeStop == null) {
            return false;
        }
        StandEntity stand = power.getSummonedStandEntity();
        return isActionOf(
                        LivingComponentAction.getCurEntityAction(
                                user, PowerClass.STAND),
                        timeStop)
                || isActionOf(
                        LivingComponentAction.getCurEntityAction(user),
                        timeStop)
                || stand != null
                        && isActionOf(stand.getCurStandAction(), timeStop);
    }

    private static boolean isActionOf(
            EntityActionInstance action, Ability ability) {
        return action != null && action.ability == ability;
    }

    private static boolean isTheWorld(StandPower power) {
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == ModStands.THE_WORLD.get();
    }

    private static boolean isTheWorldInstance(
            TimeStopState.Instance instance,
            StandPower power) {
        return instance.standTypeId()
                .map(ModStands.THE_WORLD.getId()::equals)
                .orElseGet(() -> isTheWorld(power));
    }

    private static boolean isTimeStopped(
            Level level, LivingEntity user) {
        ChunkPos chunk = new ChunkPos(user.blockPosition());
        if (level.isClientSide()) {
            return TimeStopState.getClientDisplayInstance(chunk)
                    .isPresent();
        }
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel
                    .getData(ModDataAttachmentTypes.TIME_STOP.get())
                    .isTimeStopped(chunk);
        }
        return false;
    }

    private static TimeStopKey key(
            ServerLevel level,
            TimeStopState.Instance instance) {
        return new TimeStopKey(
                level.dimension(), instance.id());
    }

    private record TimeStopKey(
            ResourceKey<Level> dimension, int instanceId) {}

    private record CastSnapshot(
            Vec3 position, float xRot, float yRot) {}

    private record FlameHit(int flameId, int targetId, int fireTicks) {}

    private enum ProjectileThreat {
        CLEAR,
        HIT,
        ABORT
    }
}
