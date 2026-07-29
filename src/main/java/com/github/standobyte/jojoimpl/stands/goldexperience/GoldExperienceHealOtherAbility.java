package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.api.healing.GoldExperienceExternalHealingTarget;
import com.github.standobyte.jojo.api.healing.GoldExperienceExternalHealingTargets;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

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
                    ConditionCheck check = super.checkSpecificConditions(context);
                    return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
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
        ConditionCheck check = super.checkSpecificConditions(context);
        return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
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

            ResolvedHealingTarget resolved =
                    GoldExperienceHealOtherAbility
                            .findLookTarget(user, level);
            if (resolved == null || resolved.rawTarget() == user) {
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
            if (!StandAbilityStamina.canPay(standPower, STAMINA_COST)) {
                StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
                return;
            }
            if (target.isDeadOrDying()) {
                GoldExperienceHealAbility.applyGoldExperienceDeadTargetHeal(target, standPower);
                StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
                return;
            }
            if (stuckProjectiles) {
                if (GoldExperienceHealAbility.applyGoldExperienceStuckProjectileHeal(target, standPower)) {
                    StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
                }
                return;
            }
            if (!GoldExperienceHealAbility.spendHealingMaterial(user)) {
                return;
            }
            GoldExperienceHealAbility.applyGoldExperienceHeal(target, standPower);
            StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
        }

    }

    private static ResolvedHealingTarget findLookTarget(
            LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user, level);
        if (!syncedTarget.isEmpty(level)
                && HitResultUtil.isTargetWithinRange(syncedTarget, user, level, TARGET_REACH, TARGET_REACH)) {
            Entity syncedEntity = syncedTarget.getMainEntity();
            return resolveHealingTarget(syncedEntity, user);
        }
        ActionTarget target = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                TARGET_REACH,
                TARGET_REACH,
                level,
                entity -> resolveHealingTarget(entity, user) != null,
                user,
                0.0D);
        Entity entity = target.getEntity();
        return resolveHealingTarget(entity, user);
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

    private static ActionTarget getSyncedLookTarget(LivingEntity user, Level level) {
        var aim = LivingComponentAction.getAim(user);
        if (aim == null) {
            return ActionTarget.EMPTY;
        }
        ActionTarget target = aim.getTarget();
        return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
    }

    private static boolean isSyncedHealTargetTooFar(LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user, level);
        return !syncedTarget.isEmpty(level)
                && syncedTarget.getType() == ActionTarget.TargetType.ENTITY
                && !HitResultUtil.isTargetWithinRange(syncedTarget, user, level, TARGET_REACH, TARGET_REACH);
    }

    private static boolean hasBlockingSyncedEntityTarget(LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user, level);
        if (syncedTarget.isEmpty(level) || syncedTarget.getType() != ActionTarget.TargetType.ENTITY
                || !HitResultUtil.isTargetWithinRange(syncedTarget, user, level, TARGET_REACH, TARGET_REACH)) {
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
