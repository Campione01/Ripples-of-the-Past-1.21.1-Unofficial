package rotp.addons.heavensdoor.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.heavensdoor.state.BookStateRules;
import rotp.addons.heavensdoor.state.BookStateService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class TearOutAPageAbility
        extends StandEntityAbility {
    private static final ActionAnimIdentifier TEAR_PAGE_ANIM =
            ActionAnimIdentifier.getOrCreate("tearPage", false);

    public TearOutAPageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, TearPageAction::new);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                BookStateRules.TEAR_HOLD_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        // 1.16 heavens_door_tear_out_a_page required no Stand part
        cooldown(BookStateRules.TEAR_COOLDOWN_TICKS);
        resolveLevelToUnlock(2);
        // 1.16 heavens_door_tear_out_a_page cancelHeldOnGettingAttacked:
        // a hit during the charge drops it.
        setCancelHeldOnGettingAttacked();
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        // 1.16 TearOutAPage was a Stand entity action: the press summoned
        // Heaven's Door and went on; the core summons it after this check.
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 checkTarget refused before the charge started.
        ConditionCheck aimed = checkAimedTarget(this, user);
        return aimed.isPositive() ? check : aimed;
    }

    // 1.16 PowerBaseImpl.tickHeldAction checked the crosshair target again on every held tick; a hold of an action
    // that is not holdOnly stopped, silently or with checkTarget's reason, once no valid booked entity was aimed.
    // The tear itself still takes the task target (standPerform).
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        return checkAimedTarget(this, user);
    }

    private static ConditionCheck checkAimedTarget(
            TearOutAPageAbility tear, LivingEntity user) {
        LivingEntity performer = ActionTargetRange.standPerformer(user);
        LivingEntity target = aimedTarget(tear, user);
        if (target == null) {
            // 1.16 TargetRequirement.ENTITY: no entity aimed, silent.
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 Action.checkRangeAndTarget answered first: 8 blocks from the
        // Stand, 4 without a line of sight
        if (!ActionTargetRange.isEntityWithinRange(performer, target,
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        String refusal = BookStateService.tearRefusal(user, target);
        if (refusal != null) {
            return ConditionCheck.createNegative(refusal);
        }
        // 1.16 StandEntityAction.checkStandTarget: silently, only an entity
        // the Stand may attack; with the Stand away the press summoned it
        // first, so the same rules are asked of its user
        return performer instanceof StandEntity stand && !stand.canAttack(target)
                || performer == user && !HeavensDoorTargeting.standMayAttack(user, target)
                ? ConditionCheck.NEGATIVE
                : ConditionCheck.POSITIVE;
    }

    // 1.16 took the mouse target (HeavensDoorTargeting.pressedAim). For
    // Tear Out a Page, Remove Book and the finisher, Stand entity actions
    // that take an entity, a block without a collision shape there gave way
    // to what the performer's collision ray met (overrideVanillaMouseTarget).
    static LivingEntity aimedTarget(
            StandEntityAbility ability, LivingEntity user) {
        return HeavensDoorTargeting.livingTarget(ability.overridePressedTarget(
                user, HeavensDoorTargeting.pressedAim(user)));
    }

    // Open Book, a StandAction, kept the block
    static LivingEntity aimedTarget(LivingEntity user) {
        return HeavensDoorTargeting.livingTarget(
                HeavensDoorTargeting.pressedAim(user));
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return TEAR_PAGE_ANIM;
    }

    // 1.16 noAdheringToUserOffset: the Stand never took its user's offset
    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return true;
    }

    // 1.16 standKeepsTarget and checkTarget: a living target whose book
    // passes the tear rules; the entity checks are the core's
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        LivingEntity user = standEntity.getUser();
        return user != null
                && target.getEntity() instanceof LivingEntity living
                && BookStateService.tearRefusal(user, living) == null;
    }

    public static final class TearPageAction
            extends EntityActionInstance {
        public TearPageAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 onTaskSet: as the hold starts, the Stand stands one block
        // before its target on its user's side (unless manually controlled)
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            LivingEntity user = getPowerUser();
            if (level().isClientSide()
                    || user == null
                    || !(getPerformer() instanceof StandEntity stand)
                    || !(ability instanceof TearOutAPageAbility tear)) {
                return;
            }
            LivingEntity target = aimedTarget(tear, user);
            if (target == null) {
                return;
            }
            ActionTarget pressed = new ActionTarget(target);
            if (!tear.keepsTaskTarget(stand, pressed)) {
                return;
            }
            keepStandAimedAtTarget(pressed);
            if (!stand.isManuallyControlled()) {
                Vec3 toTarget = target.position()
                        .subtract(user.position()).normalize();
                stand.setPos(target.position().subtract(toTarget));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            // 1.16 standPerform tore the page of task.getTarget(), the one
            // the hold started at, wherever the user looks by now
            LivingEntity target = getTaskTarget(level()).getMainEntity()
                            instanceof LivingEntity held
                    ? held
                    : null;
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                message(user, "rotp_hd.message.cannot_tear_page");
                stopAction();
                return;
            }
            // 1.16 standPerform ended with this on every perform, whether
            // or not a page came off
            if (power != null && power.usesResolve()) {
                var resolve = power.getResolveCounter();
                resolve.addResolveValue(power,
                        resolve.getMaxResolveValue(power) * 0.005F);
            }
            boolean torn = BookStateService.tearPage(user, target);
            // 1.16 StandEntityTask paid staminaCost(100) and the cooldown after standPerform whatever it did
            StandAbilityStamina.consume(
                    ability,
                    power,
                    BookStateRules.TEAR_STAMINA_COST,
                    false);
            if (ability instanceof TearOutAPageAbility tear) {
                tear.setCooldownOnUse(power);
            }
            if (!torn) {
                // Changed during the charge: name the 1.16 reason.
                String refusal = target != null
                        ? BookStateService.tearRefusal(user, target)
                        : null;
                message(user, refusal != null
                        ? "jojo.message.action_condition." + refusal
                        : "rotp_hd.message.cannot_tear_page");
                stopAction();
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                stopAction();
            }
        }

        private void stopAction() {
            forceStop();
            if (!level().isClientSide()) {
                syncPhaseChanges();
            }
        }

        private static void message(
                LivingEntity user, String key) {
            if (user instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable(key), true);
            }
        }
    }
}
