package rotp.addons.gratefuldead.ability;

import java.util.Optional;
import java.util.UUID;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.StandPrecisionRay;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.effect.AgingAuraEffect;
import rotp.addons.gratefuldead.service.AgingService;
import rotp.addons.gratefuldead.state.BlockAgingData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class AgingAbility extends EntityActionAbility {
    public enum Mode {
        TOUCH,
        AURA,
        REMOVE
    }

    private final Mode mode;

    public AgingAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(
                abilityType,
                abilityId,
                type -> new AgingAction(type, mode));
        this.mode = mode;
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
        if (mode == Mode.TOUCH) {
            setButtonHoldPhase(ActionPhase.PERFORM);
        }
        else {
            cooldown(mode == Mode.AURA ? 10 : 40);
        }
    }

    public boolean isTouchMode() {
        return mode == Mode.TOUCH;
    }

    // 1.16 the_grateful_dead_aging_touch, _aging_aura and _remove_aging
    // (025 InitStands :109-111) were StandActions its user's power
    // performed, the Stand out or not
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 StandAction.getPerformer :191-193: the summoned Stand, else the
    // user, whose stun PowerBaseImpl.checkRequirements :264 checked at the
    // press and on every held tick
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null ? stand : user;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        return setOrBufferAction(level, user, getPerformer(user),
                inputMethod, extraClientInput, clickHoldResolveTime,
                bufferingState);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action, Level level,
            LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (mode == Mode.TOUCH) {
            StandPower power = powerUser != null ? StandPower.get(powerUser) : null;
            double durability = power != null && power.getPowerType() != null
                    ? power.getPowerType().getStandStats().durability(power.getStatsDevelopment())
                    : 8.0D;
            action.phasesLength.put(ActionPhase.PERFORM,
                    (float) Math.max(40, (int) (60.0D + durability * 6.0D)));
        }
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck parent =
                super.checkSpecificConditions(context);
        if (!parent.isPositive()) {
            return parent;
        }
        if (!(context instanceof StandPower power
                && power.getPowerType()
                        == GratefulDeadAddon
                                .THE_GRATEFUL_DEAD.get())) {
            return ConditionCheck.NEGATIVE;
        }
        return mode == Mode.TOUCH
                ? checkTouch(context.getUser())
                : ConditionCheck.POSITIVE;
    }

    // 1.16 re-ran these checks on every held tick.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action,
            Power<?> context) {
        ConditionCheck parent =
                super.checkHeldSpecificConditions(
                        action, context);
        if (!parent.isPositive() || mode != Mode.TOUCH) {
            return parent;
        }
        return checkTouch(context.getUser());
    }

    // 1.16 TheGratefulDeadAgingTouchAction refusals, same target as performTouch.
    private static ConditionCheck checkTouch(
            LivingEntity user) {
        if (user == null) {
            return ConditionCheck.POSITIVE;
        }
        Level level = user.level();
        ActionTarget target = touchTarget(user, level);
        Entity entity = target.getMainEntity();
        BlockPos targetPos = entity != null
                ? entity.blockPosition()
                : target.getBlockPos();
        boolean hit = targetPos != null;
        boolean ageable;
        if (entity instanceof ItemEntity item) {
            ageable = AgingService.isAgeableItem(
                    item.getItem());
        }
        else if (entity instanceof LivingEntity living) {
            ageable = AgingService.isAgeableLiving(
                    AgingService.canonicalLivingTarget(
                            living));
        }
        else {
            ageable = entity == null && hit
                    && AgingService.isAgeableBlock(
                            level.getBlockState(targetPos));
        }
        String refusal = TouchRefusal.of(
                hasAgeableHeldItem(user),
                AgingService.isCold(
                        level, user.blockPosition()),
                hit,
                hit && AgingService.isCold(
                        level, targetPos),
                ageable);
        return refusal == null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(refusal);
    }

    // 1.16 took the touch's target from its client's crosshair
    // (InputHandler.pickMouseTarget): the vanilla pick, and behind a miss
    // with the Stand out the Stand's precision ray
    // (EntityStandType.clientHitResult). Action.checkRangeAndTarget
    // :162-201 measured it from the performer, the summoned Stand, else
    // the user (an entity 8 blocks, 4 unseen, a block 10), and
    // PowerBaseImpl.checkTarget emptied a target out of range
    // (TargetRequirement.NONE).
    private static ActionTarget touchTarget(
            LivingEntity user, Level level) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        ActionTarget target = crosshairTarget(user, level);
        if (target.getType() == ActionTarget.TargetType.EMPTY
                && stand != null) {
            target = StandEntityPunchAbility.aimAssist(user)
                    ? StandPrecisionRay.trace(user, stand)
                    : StandPrecisionRay.crosshair(user, stand,
                            StandPrecisionRay.reach(user, stand), true);
            target = target.resolveEntityId(level);
        }
        return ActionTargetRange.isTargetWithinRange(
                stand != null ? stand : user, target, level)
                ? target
                : ActionTarget.EMPTY;
    }

    // The aims only go from the user's client to the server, so that
    // client reads the one it sends.
    private static ActionTarget crosshairTarget(
            LivingEntity user, Level level) {
        ActionTargetAim aim = level.isClientSide()
                && user == ClientProxy.getClientPlayer()
                ? ClientsideAim.playerAim
                : LivingComponentAction.getAim(user);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        return target != null
                ? target.resolveEntityId(level)
                : ActionTarget.EMPTY;
    }

    private static boolean hasAgeableHeldItem(
            LivingEntity user) {
        for (InteractionHand hand :
                InteractionHand.values()) {
            if (AgingService.isAgeableItem(
                    user.getItemInHand(hand))) {
                return true;
            }
        }
        return false;
    }

    /** 1.16 checkTarget then checkConditions, as they ran together. */
    public static final class TouchRefusal {
        public static final String COLD =
                "aging_touch_cold_biome";
        public static final String NOT_LIVING =
                "aging_touch_not_living";

        private TouchRefusal() {}

        /** The refusal key, or null when the touch may go on. */
        public static String of(
                boolean heldAgeableItem,
                boolean userCold,
                boolean targetHit,
                boolean targetCold,
                boolean targetAgeable) {
            if (heldAgeableItem) {
                // The kept target is still checked for cold.
                return userCold || targetHit && targetCold
                        ? COLD
                        : null;
            }
            if (targetHit && !targetCold && targetAgeable) {
                return null;
            }
            // A refused target became EMPTY: the user's spot decides.
            return userCold ? COLD : NOT_LIVING;
        }
    }

    @Override
    public Component getName(Power<?> context) {
        if (mode != Mode.AURA
                || !(context instanceof StandPower power)) {
            return super.getName(context);
        }
        boolean active = power.userStandEffects
                .getEffectOfType(
                        GratefulDeadAddon
                                .AGING_AURA_EFFECT.get())
                .isPresent();
        return Component.translatable(
                getTranslationKey(
                        active
                                ? ".deactivate"
                                : ".activate"));
    }

    public static final class AgingAction
            extends EntityActionInstance {
        private final Mode mode;

        public AgingAction(
                EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        @Override
        public void onButtonStopHold() {
            if (mode == Mode.TOUCH) {
                startRecovery();
            }
        }

        @Override
        public void actionPerformStart() {
            if (mode != Mode.TOUCH) {
                performToggle();
            }
        }

        @Override
        public void actionTick() {
            if (mode == Mode.TOUCH
                    && getPhase() == ActionPhase.PERFORM) {
                performTouch();
            }
        }

        private void performToggle() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null
                    ? StandPower.get(user)
                    : null;
            if (user == null || power == null) {
                return;
            }
            Optional<AgingAuraEffect> active =
                    power.userStandEffects
                            .getEffectOfType(
                                    GratefulDeadAddon
                                            .AGING_AURA_EFFECT
                                            .get());
            if (mode == Mode.AURA) {
                if (active.isPresent()) {
                    power.userStandEffects.removeEffect(
                            active.get());
                    AgingService.requestFade(
                            level().getServer(),
                            user.getUUID());
                }
                else {
                    power.userStandEffects.addEffect(
                            GratefulDeadAddon
                                    .AGING_AURA_EFFECT
                                    .get()
                                    .create(level())
                                    .withStand(power));
                }
            }
            else if (mode == Mode.REMOVE) {
                active.ifPresent(
                        power.userStandEffects::removeEffect);
                AgingService.requestFade(
                        level().getServer(),
                        user.getUUID());
            }
        }

        private void performTouch() {
            if (!(level()
                    instanceof ServerLevel level)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            UUID owner = user.getUUID();
            long generation = AgingService.generation(
                    level.getServer(), owner);
            float ownerSpeed =
                    AgingService.biomeAgingMultiplier(
                            level, user.blockPosition());
            if (ageHeldItem(
                    user,
                    owner,
                    generation,
                    ownerSpeed)) {
                return;
            }

            ActionTarget target = touchTarget(user, level);
            Entity entity = target.getMainEntity();
            float targetSpeed = entity != null
                    ? AgingService.biomeAgingMultiplier(
                            level, entity.blockPosition())
                    : target.getBlockPos() != null
                            ? AgingService
                                    .biomeAgingMultiplier(
                                            level,
                                            target.getBlockPos())
                            : 0.0F;
            if (targetSpeed <= 0.0F) {
                return;
            }
            if (entity instanceof LivingEntity living) {
                AgingService.ageLiving(
                        living,
                        owner,
                        generation,
                        AgingService.TOUCH_LIVING_DELTA
                                * targetSpeed);
            }
            else if (entity instanceof ItemEntity item) {
                ItemStack aged = AgingService.ageItem(
                        item.getItem(),
                        owner,
                        generation,
                        AgingService.TOUCH_ITEM_DELTA
                                * targetSpeed);
                if (aged.isEmpty()) {
                    item.discard();
                }
                else {
                    item.setItem(aged);
                }
            }
            else if (target.getBlockPos() != null) {
                BlockAgingData.get(level).age(
                        level,
                        target.getBlockPos(),
                        owner,
                        generation,
                        AgingService.TOUCH_BLOCK_DELTA
                                * targetSpeed);
            }
        }

        private static boolean ageHeldItem(
                LivingEntity user,
                UUID owner,
                long generation,
                float speedMultiplier) {
            for (InteractionHand hand :
                    InteractionHand.values()) {
                ItemStack stack =
                        user.getItemInHand(hand);
                if (!AgingService.isAgeableItem(stack)) {
                    continue;
                }
                if (speedMultiplier > 0.0F) {
                    user.setItemInHand(
                            hand,
                            AgingService.ageItem(
                                    stack,
                                    owner,
                                    generation,
                                    AgingService
                                            .TOUCH_ITEM_DELTA
                                            * speedMultiplier));
                }
                return true;
            }
            return false;
        }
    }
}
