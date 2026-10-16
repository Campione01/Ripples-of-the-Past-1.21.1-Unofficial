package rotp.core.impl.stands.goldexperience;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.api.healing.GoldExperienceExternalHealingTarget;
import rotp.core.api.healing.GoldExperienceExternalHealingTargets;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class GoldExperienceHealOtherAbility extends NoPoseStandEntityAbility {
    private static final String HEALING_ITEM_ABILITY = "healing_item";
    private static final double TARGET_REACH = 8.0D;
    private static final int STAMINA_COST = 20;

    public GoldExperienceHealOtherAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, HealOtherAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 10);
    }

    @Override
    public Component getName(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user != null) {
            ResolvedHealingTarget resolved =
                    findLookTarget(user, user.level());
            if (resolved != null && resolved.rawTarget() != user) {
                LivingEntity target = resolved.healingTarget();
                String postfix = target.isDeadOrDying() ? ".dying" : GoldExperienceHealAbility.getHealPostfix(target);
                String translationPostfix = postfix.isEmpty() ? ".target" : postfix;
                return abilityName(
                        context,
                        translationPostfix,
                        resolved.rawTarget().getDisplayName());
            }
        }
        return super.getName(context);
    }

    @Override
    public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
        if (abilities != null) {
            LivingEntity user = context.getUser();
            if (user != null) {
                if (isSyncedHealTargetTooFar(user, user.level())) {
                    return super.replaceWithSubAbility(context, abilities);
                }
                if (hasBlockingSyncedEntityTarget(user, user.level())) {
                    return super.replaceWithSubAbility(context, abilities);
                }
                ResolvedHealingTarget target =
                        findLookTarget(user, user.level());
                if (target == null || target.rawTarget() == user) {
                    Ability healingItem = abilities.getContextVariation(HEALING_ITEM_ABILITY);
                    if (healingItem != null) {
                        return healingItem;
                    }
                }
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    // the entity it was pressed at, which its task holds and the Stand turns to (1.16 TargetRequirement.ENTITY)
    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        ResolvedHealingTarget pressed = powerUser != null ? findLookTarget(powerUser, level) : null;
        if (pressed != null) {
            action.standRotationTarget = new ActionTarget(pressed.rawTarget());
        }
    }

    // 1.16 GoldExperienceHealOther.getTargetRequirement: ENTITY
    @Override
    protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY;
    }

    // the port heals allies 1.16's canStandTargetEntity refused, so its task keeps any entity it can heal
    @Override
    protected boolean keepsTaskTargetEntity(StandEntity standEntity, Entity entity) {
        LivingEntity user = standEntity.getUser();
        return user != null && resolveHealingTarget(entity, user) != null;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user != null) {
            if (isSyncedHealTargetTooFar(user, user.level())) {
                return ConditionCheck.createNegative("target_too_far");
            }
            if (hasBlockingSyncedEntityTarget(user, user.level())) {
                return ConditionCheck.NEGATIVE;
            }
            ResolvedHealingTarget resolved =
                    findLookTarget(user, user.level());
            if (resolved != null && resolved.rawTarget() != user) {
                LivingEntity target = resolved.healingTarget();
                boolean stuckProjectiles =
                        GoldExperienceHealAbility
                                .hasStuckProjectiles(target);
                ConditionCheck targetCheck = stuckProjectiles
                        ? GoldExperienceHealAbility
                                .checkCanHealTargetBeforeMaterial(
                                        target,
                                        user,
                                        resolved.classificationOwner())
                        : GoldExperienceHealAbility
                                .checkCanHealTarget(
                                        target,
                                        user,
                                        resolved.classificationOwner());
                if (!targetCheck.isPositive()) {
                    return targetCheck;
                }
                if (target.isDeadOrDying() || stuckProjectiles) {
                    return super.checkSpecificConditions(context);
                }
            }
            else {
                return super.checkSpecificConditions(context);
            }
            ConditionCheck check = GoldExperienceHealAbility.checkHealingMaterial(user);
            if (!check.isPositive()) {
                return check;
            }
        }
        return super.checkSpecificConditions(context);
    }

    public static class HealOtherAction extends EntityActionInstance {
        public HealOtherAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }

            // 1.16 standPerform healed the entity its task held (task.getTarget()), not one aimed at again
            ActionTarget taskTarget = getTaskTarget(level);
            ResolvedHealingTarget resolved = taskTarget.getType() == ActionTarget.TargetType.ENTITY
                    ? resolveHealingTarget(taskTarget.getEntity(), user)
                    : null;
            if (resolved == null) {
                return;
            }

            LivingEntity target = resolved.healingTarget();
            boolean stuckProjectiles =
                    GoldExperienceHealAbility
                            .hasStuckProjectiles(target);
            ConditionCheck targetCheck = stuckProjectiles
                    ? GoldExperienceHealAbility
                            .checkCanHealTargetBeforeMaterial(
                                    target,
                                    user,
                                    resolved.classificationOwner())
                    : GoldExperienceHealAbility
                            .checkCanHealTarget(
                                    target,
                                    user,
                                    resolved.classificationOwner());
            if (!targetCheck.isPositive()) {
                ConditionCheck.sendActionFailedMessage(null, targetCheck, user);
                return;
            }
            StandPower standPower = StandPower.get(user);
            if (target.isDeadOrDying()) {
                GoldExperienceHealAbility.applyGoldExperienceDeadTargetHeal(target, standPower);
            }
            else if (stuckProjectiles) {
                GoldExperienceHealAbility.applyGoldExperienceStuckProjectileHeal(target, standPower);
            }
            else {
                if (!GoldExperienceHealAbility.spendHealingMaterial(user)) {
                    return;
                }
                GoldExperienceHealAbility.applyGoldExperienceHeal(target, standPower);
            }
            // 1.16 StandEntityTask paid after the heal and ignored the result: a short bar just drops to 0
            StandAbilityStamina.consume(ability, standPower, STAMINA_COST, false);
        }

    }

    private static ResolvedHealingTarget findLookTarget(
            LivingEntity user, Level level) {
        ActionTarget target = getSyncedLookTarget(user, level);
        if (target.isEmpty(level) || !isWithinHealRange(target, user, level)) {
            return null;
        }
        return resolveHealingTarget(target.getMainEntity(), user);
    }

    private static ResolvedHealingTarget resolveHealingTarget(
            Entity rawTarget, LivingEntity healer) {
        if (rawTarget == null || rawTarget == healer) {
            return null;
        }
        if (rawTarget instanceof LivingEntity living) {
            return new ResolvedHealingTarget(
                    rawTarget, living, living);
        }
        GoldExperienceExternalHealingTarget external =
                GoldExperienceExternalHealingTargets.resolve(
                        rawTarget, healer);
        return external != null
                ? new ResolvedHealingTarget(
                        external.rawTarget(),
                        external.classificationOwner(),
                        external.healingTarget())
                : null;
    }

    // 1.16 casts no ray of its own: the target is the client's, its vanilla pick or the Stand's precision ray
    private static ActionTarget getSyncedLookTarget(LivingEntity user, Level level) {
        return GoldExperienceCreateLifeformAbility.getClientLookTarget(level, user);
    }

    // 1.16 Action.checkRangeAndTarget: from the Stand while it is out, less without a line of sight
    private static boolean isWithinHealRange(ActionTarget target, LivingEntity user, Level level) {
        return ActionTargetRange.isTargetWithinRange(ActionTargetRange.standPerformer(user), target, level,
                TARGET_REACH * TARGET_REACH, TARGET_REACH * TARGET_REACH);
    }

    private static boolean isSyncedHealTargetTooFar(LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user, level);
        return !syncedTarget.isEmpty(level)
                && syncedTarget.getType() == ActionTarget.TargetType.ENTITY
                && !isWithinHealRange(syncedTarget, user, level);
    }

    private static boolean hasBlockingSyncedEntityTarget(LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user, level);
        if (syncedTarget.isEmpty(level) || syncedTarget.getType() != ActionTarget.TargetType.ENTITY
                || !isWithinHealRange(syncedTarget, user, level)) {
            return false;
        }
        Entity syncedEntity = syncedTarget.getMainEntity();
        return syncedEntity != user
                && resolveHealingTarget(syncedEntity, user) == null;
    }

    private record ResolvedHealingTarget(
            Entity rawTarget,
            LivingEntity classificationOwner,
            LivingEntity healingTarget) {
    }
}
