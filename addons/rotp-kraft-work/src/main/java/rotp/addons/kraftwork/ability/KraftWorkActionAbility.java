package rotp.addons.kraftwork.ability;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.customobjects.entity_projectile.KnifeEntity;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.kraftwork.KraftWorkAddon;
import rotp.addons.kraftwork.KraftWorkSpec;
import rotp.addons.kraftwork.entity.KraftWorkBlockMarkerEntity;
import rotp.addons.kraftwork.entity.KraftWorkItemProjectile;
import rotp.addons.kraftwork.entity.KraftWorkProjectileFactory;
import rotp.addons.kraftwork.network.KraftWorkPayloads;
import rotp.addons.kraftwork.state.KraftWorkLockService;
import rotp.addons.kraftwork.state.KraftWorkLockService.ReleaseFilter;
import rotp.addons.kraftwork.state.KraftWorkUserState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class KraftWorkActionAbility
        extends StandEntityAbility {
    private static final ActionAnimIdentifier IDLE_ANIM =
            ActionAnimIdentifier.getOrCreate("idle", true);
    private static BooleanSupplier clientStandHud = () -> true;

    public enum Mode {
        LOCK_ARMOR,
        PLACE_PROJECTILE,
        LOCK_SELF,
        LOCK_TARGET,
        RELEASE_TARGET,
        ENERGY_ACCUMULATION,
        RELEASE_PROJECTILE,
        RELEASE_ALL,
        RELEASE_READY,
        RELEASE_NOT_READY,
        RELEASE_ENDER_PEARL,
        RELEASE_BENEFICIAL,
        ADVANCED_RELEASE,
        BLOCKING_ITEMS
    }

    private final Mode mode;

    public KraftWorkActionAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new KraftWorkAction(type, mode));
        this.mode = mode;
        usageGroup = AbilityUsageGroup.UTILITY;
        standAutoSummonMode(performedByItsUser(mode)
                ? AutoSummonMode.DISABLED
                : AutoSummonMode.FULL);
        // 1.16 kraft_work_lock_target is holdToFire(10, false) with no
        // cooldown: a charge that a release cancels, then the lock.
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                mode == Mode.LOCK_TARGET
                        ? KraftWorkSpec.LOCK_TARGET_HOLD_TICKS
                        : 0);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        // 1.16 InitStands :141-154: StandActions, which left no Stand task
        // once they had performed; energy accumulation, a StandEntityAction,
        // gets its windup and recovery per press (initActionFromConfig)
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        if (mode != Mode.ENERGY_ACCUMULATION
                && mode != Mode.LOCK_TARGET) {
            cooldown(mode == Mode.PLACE_PROJECTILE ? 10 : 4);
        }
        switch (mode) {
            case LOCK_SELF, LOCK_TARGET, RELEASE_TARGET ->
                    resolveLevelToUnlock(1);
            case PLACE_PROJECTILE,
                    ENERGY_ACCUMULATION,
                    RELEASE_PROJECTILE,
                    RELEASE_ALL,
                    RELEASE_READY,
                    RELEASE_NOT_READY,
                    RELEASE_ENDER_PEARL,
                    RELEASE_BENEFICIAL,
                    ADVANCED_RELEASE ->
                    resolveLevelToUnlock(2);
            case BLOCKING_ITEMS -> resolveLevelToUnlock(3);
            case LOCK_ARMOR -> {
            }
        }
        // 1.16 built every release action and the blocking-items toggle
        // with ignoresPerformerStun(), so a stunned Stand can still release.
        switch (mode) {
            case RELEASE_TARGET,
                    RELEASE_PROJECTILE,
                    RELEASE_ALL,
                    RELEASE_READY,
                    RELEASE_NOT_READY,
                    RELEASE_ENDER_PEARL,
                    RELEASE_BENEFICIAL,
                    ADVANCED_RELEASE,
                    BLOCKING_ITEMS -> setIgnoresPerformerStun();
            default -> {
            }
        }
    }

    // 1.16 InitStands :141-154 gave these StandActions no autoSummonStand():
    // a press with Kraft Work not out summoned nothing, and the user's power
    // performed them (StandAction.getPerformer :191-193, onClick :263-267).
    // Energy accumulation was a StandEntityAction; Lock Armor (:139), a
    // StandEntityActionModifier, only joined the heavy punch's task.
    private static boolean performedByItsUser(Mode mode) {
        return mode != Mode.ENERGY_ACCUMULATION
                && mode != Mode.LOCK_ARMOR;
    }

    // 1.16's power held and performed them, never Kraft Work's task: they go
    // off beside its punch or barrage, Kraft Work out or not
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return performedByItsUser(mode);
    }

    // 1.16 KraftWorkLockTarget :268-270 and KraftWorkPlaceProjectile :562-564
    // opted out of their user's continuous player action
    @Override
    public boolean canBeUsedDuringPlayerAction(
            EntityActionInstance playerAction) {
        return mode != Mode.LOCK_TARGET
                && mode != Mode.PLACE_PROJECTILE
                && super.canBeUsedDuringPlayerAction(playerAction);
    }

    // StandEntityAbility.onKeyPress starts nothing without a Stand
    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (!level.isClientSide()
                && performedByItsUser(mode)
                && StandUtil.getSummonedStand(user) == null) {
            return setOrBufferAction(level, user, user, inputMethod,
                    extraClientInput, clickHoldResolveTime, bufferingState);
        }
        if (mode != Mode.ENERGY_ACCUMULATION || level.isClientSide()) {
            return super.onKeyPress(level, user, extraClientInput,
                    inputMethod, clickHoldResolveTime, bufferingState);
        }
        // 1.16's held use key clicked again every 4 ticks from the key
        // press (rightClickDelay), the Stand's task or its summon lock
        // holding back or not: the action counts its clicks from that press,
        // a replayed one from the press it was buffered for
        KraftWorkUserState state = KraftWorkAddon.userState(user);
        if (!bufferingState.isBuffered()) {
            state.setEnergyPressTime(level.getGameTime());
        }
        HeldInput input = super.onKeyPress(level, user, extraClientInput,
                inputMethod, clickHoldResolveTime, bufferingState);
        if (input instanceof KraftWorkAction action) {
            action.countClicksFromPress(state.energyPressTime());
        }
        return input;
    }

    // the press check meets the user's stun with Kraft Work not out
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        LivingEntity stand = super.getPerformer(user);
        return stand == null && performedByItsUser(mode) ? user : stand;
    }

    // 1.16 KraftWorkEnergyAccumulation.getStandWindupTicks (:73-77): a light
    // attack windup at a quarter of the speed, halved unless it followed
    // itself; the recovery (:97-100) is set again as it begins
    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level,
            LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        // 1.16 KraftWorkLockTarget.getHoldDurationToFire is 0 with a block
        // under the crosshair: the press performed the lock, not a hold, so
        // no release could cancel it.
        if (mode == Mode.LOCK_TARGET && powerUser != null) {
            ActionTarget target = KraftWorkAction.lockAim(powerUser);
            if (target.getMainEntity() == null
                    && target.getBlockPos() != null) {
                action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0.0F);
            }
        }
        if (mode == Mode.ENERGY_ACCUMULATION
                && !level.isClientSide()
                && performer instanceof StandEntity stand) {
            EntityActionInstance running = stand.getCurStandAction();
            boolean chained = running != null && running.ability == this;
            action.phasesLength.put(ActionPhase.WINDUP,
                    (float) energyWindup(stand, !chained));
            action.phasesLength.put(ActionPhase.RECOVERY,
                    (float) energyRecovery(stand));
        }
    }

    public static int energyWindup(StandEntity stand, boolean firstPress) {
        return StandStatFormulas.getLightAttackWindup(
                stand.getAttackSpeed() / 4.0D, 0.0F, 0.0F, firstPress);
    }

    public static int energyRecovery(StandEntity stand) {
        return StandStatFormulas.getLightAttackRecovery(
                        stand.getAttackSpeed(), stand.getFinisherMeter())
                * (stand.isArmsOnlyMode() ? 2 : 4);
    }

    // 1.16 kraft_work_lock_target was a StandAction its user's power
    // performed in the tick the hold fired, the 10th from the press; only
    // energy accumulation was a Stand task, which moves on a tick later.
    @Override
    public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
        return mode != Mode.ENERGY_ACCUMULATION
                || super.chargeFiresAsItsLengthEnds(action);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        if (mode == Mode.ENERGY_ACCUMULATION) {
            return super.getEntityAnim(action);
        }
        return action == null
                        || action.getPerformer() instanceof StandEntity
                ? IDLE_ANIM
                : null;
    }

    // 1.16 Lock Target and Release Target read as Lock Block and Release
    // Block, with their _alt icons, while the aim rests on a block. Lock
    // Block shows only where the lock would hold (1.16's crosshair reach
    // always did); Release keeps its 15-block ray. 1.16 Place Projectile
    // reads Place Item, with its _item icon, for a non-projectile off hand.
    @Override
    public Component getName(Power<?> context) {
        return aimsAtBlock(context)
                ? abilityName(context, ".alt")
                : placesPlainItem(context)
                        ? abilityName(context, ".item")
                        : super.getName(context);
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return aimsAtBlock(context)
                ? super.getSpriteName(context) + "_alt"
                : placesPlainItem(context)
                        ? super.getSpriteName(context) + "_item"
                        : super.getSpriteName(context);
    }

    private boolean placesPlainItem(Power<?> context) {
        return mode == Mode.PLACE_PROJECTILE
                && context != null
                && context.getUser() != null
                && !KraftWorkProjectileFactory.isProjectileItem(
                        context.getUser().getOffhandItem(),
                        context.getUser());
    }

    private boolean aimsAtBlock(Power<?> context) {
        if ((mode != Mode.LOCK_TARGET && mode != Mode.RELEASE_TARGET)
                || context == null
                || context.getUser() == null) {
            return false;
        }
        LivingEntity user = context.getUser();
        BlockPos blockPos = (mode == Mode.LOCK_TARGET
                        ? KraftWorkAction.lockAim(user)
                        : KraftWorkAction.releaseAim(user))
                .getBlockPos();
        return blockPos != null
                && (mode == Mode.RELEASE_TARGET
                        || KraftWorkBlockMarkerEntity.withinLockRange(
                                user, blockPos));
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || power.getPowerType()
                        != KraftWorkAddon.KRAFT_WORK.get()) {
            return base.isPositive()
                    ? ConditionCheck.NEGATIVE
                    : base;
        }
        LivingEntity user = power.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck specific = specificTargetCheck(user);
        return specific.isPositive() ? base : specific;
    }

    // 1.16 PowerBaseImpl.tickHeldAction ran KraftWorkLockTarget's checks on
    // every held tick, a failure ending the hold with its message, but in
    // the tick the charge fired stopHeldAction(true) ended it unfired and
    // silently: the core's recheck does both, that tick by
    // EntityActionInstance.isChargeFiringTick.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        LivingEntity user = context.getUser();
        return mode == Mode.LOCK_TARGET
                        && action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && user != null
                ? lockTargetCondition(user)
                : super.checkHeldSpecificConditions(action, context);
    }

    private ConditionCheck specificTargetCheck(
            LivingEntity user) {
        return switch (mode) {
            case PLACE_PROJECTILE -> {
                if (!(user instanceof Player player)
                        || user.getOffhandItem().isEmpty()) {
                    yield ConditionCheck.NEGATIVE;
                }
                if (KraftWorkProjectileFactory.isUseless(
                        user.getOffhandItem())) {
                    yield ConditionCheck.createNegative(
                            "useless_item");
                }
                if (player.getCooldowns().isOnCooldown(
                        user.getOffhandItem().getItem())) {
                    yield ConditionCheck.createNegative(
                            "item_cooldown");
                }
                yield ConditionCheck.POSITIVE;
            }
            case LOCK_SELF -> {
                if (!(user instanceof Player player)) {
                    yield ConditionCheck.NEGATIVE;
                }
                if (player.getAbilities().instabuild) {
                    yield ConditionCheck.createNegative(
                            "you_are_not_in_survival");
                }
                yield ConditionCheck.noMessage(
                        KraftWorkAddon.userState(user)
                                        .selfLocked()
                                || KraftWorkLockService
                                                .selfLockMask(user)
                                        != 0);
            }
            case LOCK_TARGET ->
                    lockTargetCondition(user);
            case ENERGY_ACCUMULATION ->
                    ConditionCheck.noMessage(user instanceof Player);
            case LOCK_ARMOR -> ConditionCheck.NEGATIVE;
            default -> ConditionCheck.POSITIVE;
        };
    }

    private static ConditionCheck lockTargetCondition(
            LivingEntity user) {
        if (!(user instanceof Player)) {
            return ConditionCheck.NEGATIVE;
        }
        ActionTarget target = KraftWorkAction.lockAim(user);
        if (!withinActionRange(user, target)) {
            return ConditionCheck.NEGATIVE;
        }
        Entity entity = target.getMainEntity();
        // 1.16 KraftWorkLockTarget refused any target whose PositionLocking
        // was set, whole or equipment locked. hasData first: state() would
        // attach lock state to every aimed entity.
        if (entity != null
                && entity.hasData(KraftWorkAddon.LOCK_STATE)
                && KraftWorkLockService.state(entity).locked()) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 took any armor stand, bare or dressed, and froze it whole.
        if (entity instanceof ArmorStand) {
            return ConditionCheck.POSITIVE;
        }
        if (entity instanceof LivingEntity living) {
            if (living instanceof StandEntity
                    || living instanceof Player player
                            && player.getAbilities().instabuild) {
                return ConditionCheck.NEGATIVE;
            }
            return KraftWorkLockService.hasEquipmentToLock(living)
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.createNegative(
                            "target_without_equipment");
        }
        // 1.16 (:133-134) refused every projectile silently; the port's
        // addition takes one only in flight, so a knife stuck in a block,
        // which stops the aim as 1.16's pickable knife did, is refused
        if (entity != null) {
            return ConditionCheck.noMessage(
                    KraftWorkLockService.isLockableNonLiving(entity)
                            && (!(entity instanceof Projectile)
                                    || KraftWorkAction.isInFlight(entity)));
        }
        // A block beyond the marker's keep range would lose its lock on the
        // marker's first tick; 1.16's crosshair never reached that far.
        return ConditionCheck.noMessage(
                target.getBlockPos() != null
                        && !user.level().isEmptyBlock(
                                target.getBlockPos())
                        && KraftWorkBlockMarkerEntity.withinLockRange(
                                user, target.getBlockPos()));
    }

    // 1.16 Action.checkRangeAndTarget, on the press and on every held tick:
    // an entity past 8 blocks of the performer (4 unseen) or a block past 10
    // became no target (TargetRequirement.NONE), which
    // KraftWorkLockTarget.checkSpecificConditions refused silently.
    private static boolean withinActionRange(
            LivingEntity user, ActionTarget target) {
        LivingEntity performer = ActionTargetRange.standPerformer(user);
        Entity entity = target.getMainEntity();
        if (entity != null) {
            return ActionTargetRange.isEntityWithinRange(
                    performer, entity,
                    ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ);
        }
        return target.getBlockPos() == null
                || ActionTargetRange.isBlockWithinRange(
                        performer, user.level(), target.getBlockPos(),
                        ActionTargetRange.DEFAULT_BLOCK_RANGE_SQ);
    }

    private static float baseCost(Mode mode) {
        return switch (mode) {
            case PLACE_PROJECTILE ->
                    KraftWorkSpec.PLACE_STAMINA;
            case LOCK_TARGET ->
                    KraftWorkSpec.LOCK_TARGET_STAMINA;
            case RELEASE_TARGET ->
                    KraftWorkSpec.RELEASE_TARGET_STAMINA;
            case ENERGY_ACCUMULATION ->
                    KraftWorkSpec.ENERGY_STAMINA;
            default -> 0.0F;
        };
    }

    public static int chargeNearbyProjectiles(
            ServerLevel level,
            StandEntity stand) {
        double reach = stand.getAttributeValue(
                Attributes.ENTITY_INTERACTION_RANGE);
        double maxIncomingDot = Mth.cos((float) Math.toRadians(
                30.0D
                        + Mth.clamp(stand.getPrecision(), 0.0D, 16.0D)
                                * 30.0D / 16.0D));
        Vec3 look = stand.getLookAngle().normalize();
        int charged = 0;
        for (Projectile projectile : level.getEntitiesOfClass(
                Projectile.class,
                stand.getBoundingBox().inflate(reach),
                Entity::isAlive)) {
            Vec3 reverseMotion =
                    projectile.getDeltaMovement().reverse();
            if (reverseMotion.lengthSqr() > 1.0E-7D
                    && look.dot(reverseMotion.normalize())
                            > maxIncomingDot) {
                continue;
            }
            if (KraftWorkLockService.addEnergy(projectile)) {
                charged++;
                // 1.16 KraftWorkEnergyAccumulation :92 played it at the
                // charged projectile, for every player
                level.playSound(
                        null,
                        projectile,
                        KraftWorkAddon
                                .KRAFT_WORK_GIVING_KINETIC_ENERGY
                                .get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F / (level.random.nextFloat() * 0.4F + 1.2F)
                                + 0.5F);
            }
        }
        return charged;
    }

    public static void installClientStandHud(BooleanSupplier standHud) {
        clientStandHud = Objects.requireNonNull(standHud);
    }

    // 1.16 InputHandler.pickMouseTarget :699-704 let the Stand's
    // clientHitResult change the crosshair target only while the Stand was
    // the HUD's current power; the server has it from the user's client
    private static boolean standHudShown(LivingEntity user) {
        if (user.level().isClientSide()) {
            return clientStandHud.getAsBoolean();
        }
        return !(user instanceof Player player)
                || KraftWorkPayloads.standHudShown(player);
    }

    public static final class KraftWorkAction
            extends EntityActionInstance {
        // vanilla Minecraft.startUseItem: rightClickDelay 4
        private static final int USE_KEY_REPEAT_TICKS = 4;

        private static final long NO_PRESS_TIME = Long.MIN_VALUE;

        private final Mode mode;
        private boolean recoveryBegan;
        private int ticksSincePress;
        private long replayedPressTime = NO_PRESS_TIME;
        private boolean queuedClick;

        public KraftWorkAction(
                EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        // the game time of the key press that started this
        void countClicksFromPress(long pressTime) {
            replayedPressTime = pressTime;
        }

        // 1.16 kraft_work_energy_accumulation standOffsetFront(): 0.5 in
        // front at the default y in full mode, turned by the view yaw
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            if (mode == Mode.ENERGY_ACCUMULATION) {
                setStandOffset(
                        0.0D,
                        0.5D,
                        StandOffsetFromUser.Rotations.HEAD,
                        false);
            }
        }

        // 1.16 ran the other modes as StandActions of the user's power,
        // which a new Stand's summon lock did not hold back (StandEntity.tick
        // :1270-1281 waited only for a Stand task, energy accumulation here)
        @Override
        public boolean isHeldBackBySummonLock() {
            return mode == Mode.ENERGY_ACCUMULATION;
        }

        @Override
        public void actionPerformStart() {
            if (mode == Mode.ENERGY_ACCUMULATION) {
                performEnergy();
            }
            else {
                performOnce();
            }
        }

        // 1.16 KraftWorkEnergyAccumulation.standPerform (:79-95) on its one
        // perform tick, with the tick cost StandEntityTask paid in PERFORM,
        // its result ignored
        private void performEnergy() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            StandEntity stand = power != null
                    ? power.getSummonedStandEntity()
                    : null;
            if (user == null || stand == null) {
                return;
            }
            StandAbilityStamina.consume(
                    ability,
                    power,
                    KraftWorkSpec.ENERGY_STAMINA,
                    true);
            if (level() instanceof ServerLevel serverLevel) {
                chargeNearbyProjectiles(serverLevel, stand);
            }
        }

        // 1.16 getStandRecoveryTicks (:97-100) was read as the recovery
        // began; setPhase takes the length after this call.
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            if (mode == Mode.ENERGY_ACCUMULATION
                    && newPhase == ActionPhase.RECOVERY) {
                recoveryBegan = true;
                if (!level().isClientSide()
                        && getPerformer() instanceof StandEntity stand) {
                    phasesLength.put(ActionPhase.RECOVERY,
                            (float) energyRecovery(stand));
                }
            }
        }

        @Override
        public void actionTick() {
            if (mode == Mode.LOCK_TARGET) {
                lockTargetChargeTick();
                return;
            }
            if (mode == Mode.ENERGY_ACCUMULATION) {
                energyClickTick();
            }
        }

        // 1.16 had no hold here: a held use key clicked again every 4 ticks
        // after the press. A click in the windup or perform was queued
        // (canBeQueued) and replaced the action as its recovery began
        // (StandEntity.checkInputBuffer), even with the key let go by then;
        // a click in the recovery replaced it at once (isCancelable,
        // isChainable), and a click on the recovery's first tick left the
        // queued one for the next action. Otherwise the recovery runs, as
        // long as 1.16 read it as it began.
        private void energyClickTick() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            // a fresh press's first tick is the one after the press
            if (replayedPressTime != NO_PRESS_TIME) {
                ticksSincePress = (int) Math.max(0L, level().getGameTime()
                        - replayedPressTime - 1L);
                replayedPressTime = NO_PRESS_TIME;
            }
            int tick = ticksSincePress++;
            boolean click = tick > 0
                    && tick % USE_KEY_REPEAT_TICKS == 0
                    && user != null
                    && AbilityInput.isHeldByKey(user, this);
            if (getPhase() != ActionPhase.RECOVERY) {
                queuedClick |= click;
                return;
            }
            boolean began = recoveryBegan;
            recoveryBegan = false;
            if (click || began && queuedClick) {
                if (!click) {
                    queuedClick = false;
                }
                phasesLength.put(ActionPhase.WINDUP,
                        (float) energyWindup(stand, false));
                setPhaseStart(ActionPhase.WINDUP);
                syncPhaseChanges();
            }
            else if (began) {
                syncPhaseChanges();
            }
        }

        // The held recheck has run (checkHeldSpecificConditions); a block
        // under the crosshair needs no hold (getHoldDurationToFire 0), so it
        // locks at once.
        private void lockTargetChargeTick() {
            if (getPhase() != ActionPhase.BUTTON_CHARGE
                    || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null
                    && lockAim(user).getMainEntity() == null
                    && lockTargetCondition(user).isPositive()) {
                setPhaseStart(ActionPhase.WINDUP);
                syncPhaseChanges();
            }
        }

        @Override
        public void onButtonStopHold() {
            if (mode == Mode.LOCK_TARGET
                    && getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        // 1.16 cancelHeldOnGettingAttacked: a damaging hit with a direct
        // entity dropped the lock its user's power held, Kraft Work out or not.
        public static void cancelLockTargetCharge(LivingEntity user) {
            stopLockTargetCharge(LivingComponentAction.getCurEntityAction(
                    user, PowerClass.STAND));
        }

        private static void stopLockTargetCharge(
                EntityActionInstance current) {
            if (current instanceof KraftWorkAction action
                    && action.mode == Mode.LOCK_TARGET
                    && action.getPhase() == ActionPhase.BUTTON_CHARGE) {
                action.forceStop();
                action.syncPhaseChanges();
            }
        }

        // 1.16 KraftWorkEnergyAccumulation.isCancelable (:102-104) and
        // isChainable (:106-108): any action, itself too, in its recovery
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return mode == Mode.ENERGY_ACCUMULATION
                    ? getPhase() == ActionPhase.RECOVERY
                    : super.canBeCancelledInto(cancellingAbility);
        }

        private void performOnce() {
            if (!(level() instanceof ServerLevel level)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null) {
                return;
            }
            if (mode == Mode.ADVANCED_RELEASE) {
                if (user instanceof ServerPlayer player) {
                    KraftWorkPayloads.open(player);
                }
                return;
            }
            if (mode == Mode.RELEASE_TARGET) {
                releaseTarget(level, user, power);
                return;
            }
            if (mode == Mode.LOCK_TARGET) {
                lockTarget(level, user, power);
                return;
            }

            float cost = mode == Mode.LOCK_SELF
                    ? selfActionCost(user)
                    : mode == Mode.PLACE_PROJECTILE
                            ? baseCost(mode)
                                    * KraftWorkProjectileFactory.itemsToPlace(
                                            user, user.getOffhandItem())
                            : baseCost(mode);
            // 1.16 StandAction.onPerform paid first and acted on any bar.
            if (cost > 0.0F) {
                StandAbilityStamina.consume(
                        ability,
                        power,
                        cost,
                        false);
            }

            switch (mode) {
                case PLACE_PROJECTILE ->
                        placeProjectile(level, user, power);
                case LOCK_SELF -> toggleSelfLock(user);
                case RELEASE_PROJECTILE ->
                        release(user, ReleaseFilter.NEARBY);
                case RELEASE_ALL ->
                        release(user, ReleaseFilter.ALL);
                case RELEASE_READY ->
                        release(user, ReleaseFilter.READY);
                case RELEASE_NOT_READY ->
                        release(user, ReleaseFilter.NOT_READY);
                case RELEASE_ENDER_PEARL ->
                        release(user, ReleaseFilter.ENDER_PEARL);
                case RELEASE_BENEFICIAL ->
                        release(user, ReleaseFilter.BENEFICIAL);
                case BLOCKING_ITEMS -> {
                    KraftWorkUserState state =
                            KraftWorkAddon.userState(user);
                    state.setBlockingItems(
                            !state.blockingItems());
                }
                case LOCK_ARMOR,
                        LOCK_TARGET,
                        RELEASE_TARGET,
                        ENERGY_ACCUMULATION,
                        ADVANCED_RELEASE -> {
                }
            }
        }

        // 1.16 fired the held lock only while the target still qualified,
        // on any stamina, then paid 10 per locked piece; a lock that took
        // nothing costs nothing.
        private void lockTarget(
                ServerLevel level,
                LivingEntity user,
                StandPower power) {
            ConditionCheck check = lockTargetCondition(user);
            if (!check.isPositive()) {
                ConditionCheck.sendActionFailedMessage(
                        ability instanceof Ability value ? value : null,
                        check,
                        user);
                return;
            }
            int count = lockAimed(level, user);
            if (count > 0) {
                StandAbilityStamina.consume(
                        ability,
                        power,
                        KraftWorkSpec.LOCK_TARGET_STAMINA * count,
                        false);
            }
        }

        // 1.16 KraftWorkReleaseTarget freed an entity this user had locked,
        // or every lock on the aimed block (Release Block); with nothing to
        // free it did not fire, so it costs nothing. It fired on any bar.
        private void releaseTarget(
                ServerLevel level,
                LivingEntity user,
                StandPower power) {
            if (releaseAimed(level, user)) {
                StandAbilityStamina.consume(
                        ability,
                        power,
                        KraftWorkSpec.RELEASE_TARGET_STAMINA,
                        false);
            }
        }

        private static float selfActionCost(
                LivingEntity user) {
            KraftWorkUserState state =
                    KraftWorkAddon.userState(user);
            int mask = state.selfLocked()
                    ? KraftWorkLockService.state(user)
                            .equipmentMask()
                    : KraftWorkLockService.selfLockMask(user);
            return Integer.bitCount(mask)
                    * (state.selfLocked() ? 10.0F : 1.0F);
        }

        private static void placeProjectile(
                ServerLevel level,
                LivingEntity user,
                StandPower power) {
            ItemStack held = user.getOffhandItem();
            // with Kraft Work not out the user places it alone, as 1.16
            // placed every item from its user
            StandEntity stand = power.getSummonedStandEntity();
            if (held.isEmpty()) {
                return;
            }
            KraftWorkProjectileFactory.placeHeld(level, user, stand, held);
        }

        private static void toggleSelfLock(
                LivingEntity user) {
            KraftWorkUserState state =
                    KraftWorkAddon.userState(user);
            if (state.selfLocked()) {
                KraftWorkLockService.releaseTarget(user, user);
                state.setSelfLocked(false);
                return;
            }
            int mask = KraftWorkLockService.selfLockMask(user);
            if (KraftWorkLockService.lockSelf(user, mask) > 0) {
                state.setSelfLocked(true);
            }
        }

        private static int lockAimed(
                ServerLevel level, LivingEntity user) {
            ActionTarget target = lockAim(user);
            Entity entity = target.getMainEntity();
            if (entity instanceof LivingEntity living
                    && !(living instanceof ArmorStand)) {
                return KraftWorkLockService.lockEquipment(
                        user, living);
            }
            if (entity != null) {
                return KraftWorkLockService.lockWhole(
                                user, entity)
                        ? 1
                        : 0;
            }
            if (target.getBlockPos() == null
                    || level.isEmptyBlock(target.getBlockPos())) {
                return 0;
            }
            AABB block = new AABB(target.getBlockPos());
            level.getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            block)
                    .forEach(Entity::discard);
            KraftWorkBlockMarkerEntity marker =
                    new KraftWorkBlockMarkerEntity(
                            KraftWorkAddon.KW_BLOCK.get(),
                            level);
            marker.initialize(user, target.getBlockPos());
            level.addFreshEntity(marker);
            return 1;
        }

        // As in 1.16, a block lock is freed whoever placed it.
        private static boolean releaseAimed(
                ServerLevel level, LivingEntity user) {
            ActionTarget target = releaseAim(user);
            Entity entity = target.getMainEntity();
            if (entity != null) {
                return KraftWorkLockService.releaseTarget(
                        user, entity);
            }
            BlockPos blockPos = target.getBlockPos();
            if (blockPos == null || level.isEmptyBlock(blockPos)) {
                return false;
            }
            List<KraftWorkBlockMarkerEntity> markers =
                    level.getEntitiesOfClass(
                            KraftWorkBlockMarkerEntity.class,
                            new AABB(blockPos));
            markers.forEach(Entity::discard);
            return !markers.isEmpty();
        }

        // 1.16's release actions played nothing themselves: each released
        // projectile plays its own sound (KraftWorkLockService.release)
        private static void release(
                LivingEntity user, ReleaseFilter filter) {
            KraftWorkLockService.releaseProjectiles(user, filter);
        }

        // 1.16 Lock Target took its user's crosshair target (KraftWorkLockTarget
        // :116, :168, :287-289, InputHandler.mouseTarget): the vanilla pick
        // from the camera entity, Kraft Work under manual control, and with
        // Kraft Work out, where that met nothing, the precision ray
        // EntityStandType.clientHitResult (:120-151) cast from the same eyes
        // as far as the user's reach, or under manual control Kraft Work's
        // own (by the aim assist setting, as the core's Stand aims)
        private static ActionTarget lockAim(
                LivingEntity user) {
            StandEntity stand = StandUtil.getSummonedStand(user);
            LivingEntity aiming = stand != null && stand.isManuallyControlled()
                    ? stand
                    : user;
            ActionTarget pick = crosshairPick(user, aiming);
            // 1.16 Action.checkTarget emptied a target that was the user
            if (pick.getMainEntity() == user) {
                return ActionTarget.EMPTY;
            }
            if (stand == null
                    || pick.getType() != ActionTarget.TargetType.EMPTY
                    || !standHudShown(user)) {
                return pick;
            }
            if (aiming == stand) {
                return StandEntityPunchAbility.manualStandAim(
                        stand, ActionTarget.EMPTY);
            }
            return StandEntityPunchAbility.aimAssist(user)
                    ? StandEntityPunchAbility.userPrecisionRayTarget(
                            stand,
                            user,
                            entity -> StandEntityPunchAbility
                                    .canUserAimTarget(stand, entity))
                    : StandPrecisionRay.crosshair(
                            user,
                            stand,
                            StandEntityPunchAbility.userAimReach(
                                    user, stand),
                            true);
        }

        // vanilla GameRenderer.pick: the first entity or block on the
        // crosshair ray, an entity within the player's entity reach and a
        // block within its block reach, else nothing, even with a block
        // behind an entity out of reach; the player's reach from the camera
        // entity's eyes
        private static ActionTarget crosshairPick(
                LivingEntity user, LivingEntity aiming) {
            if (!(user instanceof Player player)) {
                return ActionTarget.EMPTY;
            }
            double blockRange = player.blockInteractionRange();
            double entityRange = player.entityInteractionRange();
            double range = Math.max(blockRange, entityRange);
            Vec3 eyes = aiming.getEyePosition();
            Vec3 look = aiming.getLookAngle();
            BlockHitResult block = outlineBlock(aiming, eyes, look, range);
            // vanilla's pick looked for an entity only as far as that block
            double entityReach = block.getType() == HitResult.Type.MISS
                            || !StandSkillWalls.wallsHideEntities(aiming)
                    ? range
                    : Math.sqrt(eyes.distanceToSqr(block.getLocation()));
            ActionTarget entityHit = HitResultUtil.clip(
                    eyes,
                    look,
                    0.0D,
                    entityReach,
                    player.level(),
                    entity -> isAimable(entity, player, aiming),
                    aiming,
                    0.0D,
                    false);
            ActionTarget hit =
                    entityHit.getType() == ActionTarget.TargetType.ENTITY
                            ? entityHit
                            : ActionTarget.fromVanilla(block);
            if (hit.getType() == ActionTarget.TargetType.EMPTY) {
                return hit;
            }
            double hitRange =
                    hit.getType() == ActionTarget.TargetType.ENTITY
                            ? entityRange
                            : blockRange;
            double distanceSqr = hit.getClipPos()
                    .map(eyes::distanceToSqr)
                    .orElseGet(() -> hit.getBoundingBox(player.level())
                            .map(box -> box.distanceToSqr(eyes))
                            .orElse(0.0D));
            return distanceSqr < hitRange * hitRange
                    ? hit
                    : ActionTarget.EMPTY;
        }

        // 1.16 KraftWorkReleaseTarget cast its own 15-block ray from the
        // user's eyes (DETECTION_RANGE, JojoModUtil.rayTrace), not the
        // crosshair: an entity on it first, else the block by its outline
        private static ActionTarget releaseAim(
                LivingEntity user) {
            Vec3 eyes = user.getEyePosition();
            Vec3 look = user.getLookAngle();
            ActionTarget hit = HitResultUtil.clip(
                    eyes,
                    look,
                    KraftWorkSpec.LOCK_RANGE,
                    KraftWorkSpec.LOCK_RANGE,
                    user.level(),
                    entity -> isReleaseAimable(entity, user),
                    user,
                    0.0D);
            return hit.getType() == ActionTarget.TargetType.ENTITY
                    ? hit
                    : ActionTarget.fromVanilla(outlineBlock(
                            user, eyes, look, KraftWorkSpec.LOCK_RANGE));
        }

        // vanilla's pick (Entity.pick) and 1.16's JojoModUtil.rayTrace met a
        // block by its outline (BlockMode.OUTLINE): grass, a flower or a
        // torch on the ray is the aim, not the block behind it
        private static BlockHitResult outlineBlock(
                Entity aiming, Vec3 eyes, Vec3 look, double range) {
            return aiming.level().clip(new ClipContext(
                    eyes,
                    eyes.add(look.scale(range)),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    aiming));
        }

        // what 1.16's crosshair (vanilla GameRenderer.pick: no spectator,
        // isPickable, a dying mob too) took for Lock Target, never the user's
        // own Stand (StandEntity.isPickable :2408-2412): the aim goes through
        // Kraft Work to what is behind it. A projectile in flight, which Lock
        // Target locks in the port, stays aimable, but not a vanilla one
        // already held in place (a placed arrow, one the guard stopped): the
        // crosshair passed it and Lock Target refuses it anyway. A knife,
        // held or stuck, was pickable (ItemProjectileEntity.isPickable
        // :200-202), which the core's knife maps to canBeHitByProjectile:
        // the aim stops at it and Lock Target refuses it unless it flies.
        // From Kraft Work's eyes the user is in the way.
        private static boolean isAimable(
                Entity entity, LivingEntity user, Entity aiming) {
            return entity != aiming
                    && !entity.isSpectator()
                    && (entity.isPickable()
                            || entity instanceof KnifeEntity
                            || isInFlight(entity) && !isLocked(entity))
                    && !isUsersOwnStand(entity, user)
                    && !sharesRide(entity, aiming);
        }

        // 1.16 Release Target's server ray (JojoModUtil
        // .rayTraceMultipleEntities :157) took what isPickable: no vanilla
        // projectile, but a knife (ItemProjectileEntity.isPickable) and a
        // placed item that breaks on impact (KWItemEntity.isPickable
        // :230-232, RemoveAfterHitting, a field its clients never had). It
        // met the user's own Stand; passing it is kept until the owner
        // decides.
        private static boolean isReleaseAimable(
                Entity entity, LivingEntity user) {
            return entity != user
                    && !entity.isSpectator()
                    && (entity.isPickable()
                            || entity instanceof KnifeEntity
                            || breaksOnImpact(entity))
                    && !isUsersOwnStand(entity, user)
                    && !sharesRide(entity, user);
        }

        private static boolean breaksOnImpact(Entity entity) {
            return !entity.level().isClientSide()
                    && entity instanceof KraftWorkItemProjectile item
                    && item.removeAfterHitting();
        }

        // the lock state is not synced: a client sees a held projectile
        // hang still with no gravity (KraftWorkLockService.freeze)
        private static boolean isLocked(Entity entity) {
            if (entity.level().isClientSide()) {
                return entity.isNoGravity()
                        && entity.getDeltaMovement().lengthSqr() < 1.0E-7D;
            }
            return entity.hasData(KraftWorkAddon.LOCK_STATE)
                    && KraftWorkLockService.state(entity).locked();
        }

        // the port's addition covers projectiles in flight only; 1.16's
        // crosshair never took an arrow, so one stuck in a block is passed
        // over (the core makes inGround public, at build and at runtime)
        private static boolean isInFlight(Entity entity) {
            return entity instanceof Projectile
                    && !(entity instanceof AbstractArrow arrow
                            && arrow.inGround);
        }

        private static boolean isUsersOwnStand(
                Entity entity, LivingEntity user) {
            return entity instanceof StandEntity stand
                    && stand.getUser() == user;
        }

        // vanilla's pick (1.16 ProjectileHelper.getEntityHitResult, and
        // JojoModUtil.rayTraceMultipleEntities :168 for Release Target's
        // ray) passed over the aiming entity's mount and its other passengers
        private static boolean sharesRide(
                Entity entity, Entity aiming) {
            return entity.getRootVehicle() == aiming.getRootVehicle()
                    && !entity.canRiderInteract();
        }
    }
}
