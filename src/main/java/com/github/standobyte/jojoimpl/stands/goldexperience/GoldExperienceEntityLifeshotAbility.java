package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.ability.input.ActionInputBuffer.BufferingState;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.HeldInput;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.util.reflection.CommonReflection;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.level.Level;

public class GoldExperienceEntityLifeshotAbility extends NoPoseStandEntityAbility {
    public static final int CONSCIOUSNESS_ENTITY_ID = 0x3a4a9b10;
    public static final int MAX_DURATION = 100;
    public static final int RESIST_TICKS = 1200;
    public static final float RESIST_TICK_DOWN = 0.125F;
    public static final int REDUCTION_SHORT_DELAY = 40;
    public static final int REDUCTION_LONG_DELAY = 20;
    public static final int STAMINA_COST = 50;
    private static final double REACH = 8.0D;

    public GoldExperienceEntityLifeshotAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, LifeshotAction::new);
        isSubAbility = true;
    }

    @Override
    public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
        StandPower standPower = PowerClass.STAND.cast(context);
        if (abilities != null && hasCompatibleLifeshotPunch(standPower)) {
            abilities.replaceOtherAbilityWith(standPower, "heavy_punch", this);
        }
        return super.replaceWithSubAbility(context, abilities);
    }
    
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        StandPower standPower = PowerClass.STAND.cast(context);
        ToothKnockingHeavyPunch punch = getCurrentGoldExperiencePunch(standPower);
        if (punch != null && punch.canAcceptLifeshotFollowup()) {
            ConditionCheck targetCheck = checkLifeshotTarget(punch.getLifeshotTarget());
            if (!targetCheck.isPositive()) {
                return targetCheck;
            }
            ConditionCheck check = super.checkSpecificConditions(context);
            return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
        }

        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (isSyncedLifeShotTargetTooFar(user, user.level())) {
            return ConditionCheck.createNegative("target_too_far");
        }
        LivingEntity target = getLifeShotTarget(user, user.level());
        if (target == null) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck targetCheck = checkLifeshotTarget(target);
        if (!targetCheck.isPositive()) {
            return targetCheck;
        }
        ConditionCheck check = super.checkSpecificConditions(context);
        return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        StandPower standPower = StandPower.get(user);
        ToothKnockingHeavyPunch punch = getCurrentGoldExperiencePunch(standPower);
        if (punch != null && punch.canAcceptLifeshotFollowup()) {
            if (!level.isClientSide()) {
                ConditionCheck targetCheck = checkLifeshotTarget(punch.getLifeshotTarget());
                if (!targetCheck.isPositive()) {
                    ConditionCheck.sendActionFailedMessage(this, targetCheck, user);
                    return null;
                }
                if (!StandAbilityStamina.canPay(standPower, STAMINA_COST)) {
                    StandAbilityStamina.consumeOrMessage((Ability) this, standPower, user, STAMINA_COST);
                    return null;
                }
                if (punch.queueLifeshot()) {
                    bufferingState.setActionSuccess();
                }
            }
            return null;
        }
        return super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
    }

    private static boolean hasCompatibleLifeshotPunch(StandPower standPower) {
        ToothKnockingHeavyPunch punch = getCurrentGoldExperiencePunch(standPower);
        return punch != null && punch.canAcceptLifeshotFollowup();
    }

    private static ToothKnockingHeavyPunch getCurrentGoldExperiencePunch(StandPower standPower) {
        StandEntity stand = standPower != null ? standPower.getSummonedStandEntity() : null;
        EntityActionInstance curAction = stand != null ? stand.getCurStandAction() : null;
        return curAction instanceof ToothKnockingHeavyPunch punch ? punch : null;
    }

    private static ConditionCheck checkLifeshotTarget(LivingEntity target) {
        if (target == null) {
            return ConditionCheck.NEGATIVE;
        }
        return StandUtil.getStandUser(target) == target
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("only_stand_user");
    }
    
    private static LivingEntity getLifeShotTarget(LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user, level);
        if (!syncedTarget.isEmpty(level)
                && HitResultUtil.isTargetWithinRange(syncedTarget, user, level, REACH, REACH)) {
            Entity syncedEntity = syncedTarget.getMainEntity();
            return syncedEntity instanceof LivingEntity living && living != user ? living : null;
        }
        ActionTarget target = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                REACH,
                REACH,
                level,
                entity -> entity instanceof LivingEntity && entity != user,
                user,
                0);
        Entity entity = target.getMainEntity();
        return entity instanceof LivingEntity living ? living : null;
    }

    private static ActionTarget getSyncedLookTarget(LivingEntity user, Level level) {
        var aim = LivingComponentAction.getAim(user);
        if (aim == null) {
            return ActionTarget.EMPTY;
        }
        ActionTarget target = aim.getTarget();
        return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
    }

    private static boolean isSyncedLifeShotTargetTooFar(LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user, level);
        return !syncedTarget.isEmpty(level)
                && syncedTarget.getType() == ActionTarget.TargetType.ENTITY
                && !HitResultUtil.isTargetWithinRange(syncedTarget, user, level, REACH, REACH);
    }

    public static boolean applyLifeShotTo(LivingEntity target, LivingEntity user) {
        if (StandUtil.getStandUser(target) != target) {
            return false;
        }
        if (!target.getType().is(EntityTypeTags.UNDEAD) && !target.hasEffect(ModStatusEffects.SENSORY_OVERLOAD)) {
            giveEffectWithResist(target);
            return true;
        }
        if (target instanceof ZombieVillager zombieVillager) {
            CommonReflection.startConverting(zombieVillager, user.getUUID(), target.getRandom().nextInt(2401) + 3600);
            return true;
        }
        return false;
    }

    public static void giveEffectWithResist(LivingEntity target) {
        int duration = GELifeshotState.get(target).onLifeShot(MAX_DURATION);
        if (duration > 0) {
            target.addEffect(new MobEffectInstance(ModStatusEffects.SENSORY_OVERLOAD, duration, 0, false, false, false));
        }
        else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, false));
        }
    }

    public static class LifeshotAction extends EntityActionInstance {
        public LifeshotAction(EntityActionType ability) {
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
			LivingEntity target = getLifeShotTarget(user, level);
			if (target == null) {
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.canPay(standPower, STAMINA_COST)) {
				StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
				return;
			}
			if (applyLifeShotTo(target, user)) {
				StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
			}
		}

	}
}
