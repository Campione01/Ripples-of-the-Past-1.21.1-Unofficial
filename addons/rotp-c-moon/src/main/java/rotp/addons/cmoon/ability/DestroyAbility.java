package rotp.addons.cmoon.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;

import rotp.addons.cmoon.entity.CMoonStandEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class DestroyAbility
        extends StandEntityAbility {
    public static final int HOLD_TO_FIRE_TICKS = 10;
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate("attack1", false);

    public DestroyAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DestroyAction::new);
        // 1.16 holdToFire(10, false), no recovery.
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        standAutoSummonMode(AutoSummonMode.FULL);
        resolveLevelToUnlock(1);
        // 1.16 cooldown(10, 10): Resolve drops the second 10.
        cooldown(10, 10);
    }

    // 1.16 set the cooldown when the hold fired, and it fired only with
    // an entity target, so a cancelled hold leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 Destroy.checkTarget: never the user, never a Stand.
    public static ConditionCheck checkDestroyTarget(
            Entity target, LivingEntity user) {
        if (target != null && target == user) {
            return ConditionCheck.createNegative("cant_attack_self");
        }
        if (target instanceof StandEntity) {
            return ConditionCheck.createNegative("cant_attack_stand");
        }
        return ConditionCheck.POSITIVE;
    }

    // The press is refused on the aimed entity, before the charge.
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        return checkAimedTarget(context);
    }

    // 1.16 PowerBaseImpl.tickHeldAction asked the crosshair again on every
    // held tick; the hold, not holdOnly, ended once it was refused.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        return base.isPositive() ? checkAimedTarget(context) : base;
    }

    // 1.16 Destroy had TargetRequirement.ENTITY: no entity aimed was
    // refused silently, then Action.checkRangeAndTarget's 8 blocks from the
    // performer (4 without a line of sight), Destroy.checkTarget, and
    // StandEntityAction.checkStandTarget's living entity the Stand may
    // attack (silently).
    private ConditionCheck checkAimedTarget(Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        StandEntity stand = context instanceof StandPower power
                ? power.getSummonedStandEntity()
                : null;
        LivingEntity performer = stand != null ? stand : user;
        Entity aimed = getAimedEntity(performer);
        if (aimed == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (!ActionTargetRange.isEntityWithinRange(performer, aimed,
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        ConditionCheck destroyCheck = checkDestroyTarget(aimed, user);
        if (!destroyCheck.isPositive() || stand == null) {
            return destroyCheck;
        }
        return ConditionCheck.noMessage(aimed instanceof LivingEntity living
                && stand.canAttack(living));
    }

    // 1.16 PowerBaseImpl.checkTarget first ran StandEntityAction
    // .overrideVanillaMouseTarget: a pressed block without a collision shape
    // gave way to what the performer's collision ray met, so a torch or grass
    // with a mob behind it was the mob.
    public Entity getAimedEntity(LivingEntity performer) {
        LivingEntity user = performer instanceof StandEntity stand
                ? stand.getUser()
                : performer;
        // The aims only go from the user's client to the server, so that
        // client reads the aims it sends (1.16 checked its own mouse target).
        ActionTargetAim aim = performer.level().isClientSide()
                        && user != null
                        && user == ClientProxy.getClientPlayer()
                ? performer == user
                        ? ClientsideAim.playerAim
                        : ClientsideAim.standAim
                : LivingComponentAction.getAim(performer);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        if (target == null) {
            return null;
        }
        target = target.resolveEntityId(performer.level());
        return (user != null ? overridePressedTarget(user, target) : target)
                .getMainEntity();
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    // 1.16 Destroy.getTargetRequirement ENTITY and its checkTarget
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY
                && checkDestroyTarget(
                        target.getMainEntity(), standEntity.getUser())
                        .isPositive();
    }

    public static final class DestroyAction
            extends EntityActionInstance {
        public DestroyAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof CMoonStandEntity stand) {
                captureActionTargetFromAim(stand);
            }
        }

        // the server's copy of what the Stand aimed at as it was pressed
        @Override
        protected ActionTarget pressedTaskTarget() {
            return getActionTargetSnapshot(level());
        }

        // 1.16 holdToFire(10, false): letting go before the charge
        // completes cancels it.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        // 1.16 PowerBaseImpl.performAction set the cooldown as the hold
        // fired at an entity target, in the charge's last tick; the Stand's
        // task performed in its next tick.
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase != ActionPhase.PERFORM || level().isClientSide()
                    || !(getPerformer() instanceof CMoonStandEntity)) {
                return;
            }
            ActionTarget target = getActionTargetSnapshot(level());
            LivingEntity user = getPowerUser();
            if (target.getMainEntity() instanceof LivingEntity
                    && checkDestroyTarget(target.getMainEntity(), user)
                            .isPositive()
                    && user != null
                    && ability instanceof DestroyAbility destroy) {
                destroy.setCooldownOnUse(StandPower.get(user));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof CMoonStandEntity stand)) {
                return;
            }
            ActionTarget target =
                    getActionTargetSnapshot(level());
            // A buffered press can capture a Stand after the press check.
            if (!checkCapturedTarget(target)) {
                return;
            }
            if (target.getMainEntity()
                    instanceof LivingEntity living) {
                stand.setAutoAttackTarget(living);
            }
        }

        // Refused: a message, no auto-attack target, no cooldown.
        private boolean checkCapturedTarget(ActionTarget target) {
            LivingEntity owner = getPowerUser();
            ConditionCheck check =
                    checkDestroyTarget(target.getMainEntity(), owner);
            if (!check.isPositive() && owner != null) {
                ConditionCheck.sendActionFailedMessage(
                        ability instanceof DestroyAbility refused
                                ? refused : null,
                        check, owner);
            }
            return check.isPositive();
        }
    }
}
