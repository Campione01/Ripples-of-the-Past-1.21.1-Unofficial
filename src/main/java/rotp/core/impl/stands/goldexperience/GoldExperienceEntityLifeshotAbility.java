package rotp.core.impl.stands.goldexperience;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.util.reflection.CommonReflection;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;

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

    /*
     * 1.16 GoldExperienceLifeshotPunch.attackRecoveryFollowup: the heavy key that resolved to the running Life Shot punch
     * (StandEntityHeavyAttack.replaceAction asked the variation's replaceAction; a key the punch gave back to the base
     * heavy for a lost part did not) gave its Life Shot, whichever heavy attack the punch was the finisher variation of,
     * only if the Life Shot's requirements passed (StandAction.canSetFollowUp); otherwise the key stayed the running
     * punch, whose press is refused. A refusal message the Life Shot gives stays on that press (owner: the port's
     * refusal messages are kept).
     */
    @Override
    public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
        StandPower standPower = PowerClass.STAND.cast(context);
        ToothKnockingHeavyPunch punch = getCurrentGoldExperiencePunch(standPower);
        if (abilities != null && punch != null && punch.canAcceptLifeshotFollowup() && isAbilityAvailable(context)) {
            String base = getLifeshotPunchBase(punch);
            Ability key = abilities.getContextVariation(base);
            if (key != null && key.getAbilityId().equals(punch.ability.getAbilityId())) {
                ConditionCheck check = checkConditions(context);
                if (check.isPositive()) {
                    abilities.replaceOtherAbilityWith(standPower, base, this);
                }
                else if (check.getWarning() != null) {
                    abilities.setConditionCheck(base, check);
                }
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    private static String getLifeshotPunchBase(ToothKnockingHeavyPunch punch) {
        return punch.ability instanceof Ability lifeshotPunch && lifeshotPunch.isStandFinisherOf != null
                ? lifeshotPunch.isStandFinisherOf.baseAbilityName()
                : "heavy_punch";
    }
    
    /*
     * During a Gold Experience punch the Life Shot is only the Life Shot punch's follow-up: once taken, 1.16 gave the key
     * back to the running punch, refused silently, and the Life Shot had no use of its own. A press a client sends from a
     * stale key (the server's direct fallback in ClAbilityInputPacket) is refused the same way.
     */
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        StandPower standPower = PowerClass.STAND.cast(context);
        ToothKnockingHeavyPunch punch = getCurrentGoldExperiencePunch(standPower);
        if (punch != null) {
            if (!punch.canAcceptLifeshotFollowup()) {
                return ConditionCheck.NEGATIVE;
            }
            ConditionCheck targetCheck = checkLifeshotTarget(punch.getLifeshotTarget());
            if (!targetCheck.isPositive()) {
                return targetCheck;
            }
            return super.checkSpecificConditions(context);
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
        return super.checkSpecificConditions(context);
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        StandPower standPower = StandPower.get(user);
        ToothKnockingHeavyPunch punch = getCurrentGoldExperiencePunch(standPower);
        if (punch != null) {
            // a second press checked against the keys as they were resolved earlier in the same tick
            if (!punch.canAcceptLifeshotFollowup()) {
                return null;
            }
            ConditionCheck targetCheck = checkLifeshotTarget(punch.getLifeshotTarget());
            if (!targetCheck.isPositive()) {
                ConditionCheck.sendActionFailedMessage(this, targetCheck, user);
                return null;
            }
            // the user's own client takes it too, so its key is the running punch again before the server's data comes
            if (punch.queueLifeshot() && !level.isClientSide()) {
                // 1.16 StandAction.onPerform paid the follow-up's cost on the click and ignored the result
                StandAbilityStamina.consume((Ability) this, standPower, STAMINA_COST, false);
                bufferingState.setActionSuccess();
            }
            return null;
        }
        return super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
    }

    /*
     * 1.16's Life Shot was a modifier of the running Life Shot punch (StandEntityActionModifier), never a queued
     * StandEntityAction, so its HUD drew it by its requirements, bright and a selected slot's frame green
     * (greenSelection: conditionCheck.isPositive()). During a Gold Experience punch its press is that punch's follow-up,
     * taken at once or refused (onKeyPress), and never waits in the input buffer behind it.
     */
    @Override
    public boolean isQueuedBehindStandTask(LivingEntity user) {
        return getCurrentGoldExperiencePunch(StandPower.get(user)) == null && super.isQueuedBehindStandTask(user);
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
        ActionTarget target = getSyncedLookTarget(user, level);
        if (target.isEmpty(level) || !isWithinLifeShotRange(target, user, level)) {
            return null;
        }
        Entity entity = target.getMainEntity();
        return entity instanceof LivingEntity living && living != user ? living : null;
    }

    // 1.16 casts no ray of its own: the target is the client's, its vanilla pick or the Stand's precision ray
    private static ActionTarget getSyncedLookTarget(LivingEntity user, Level level) {
        return GoldExperienceCreateLifeformAbility.getClientLookTarget(level, user);
    }

    // 1.16 Action.checkRangeAndTarget: from the Stand while it is out, less without a line of sight
    private static boolean isWithinLifeShotRange(ActionTarget target, LivingEntity user, Level level) {
        return ActionTargetRange.isTargetWithinRange(ActionTargetRange.standPerformer(user), target, level,
                REACH * REACH, REACH * REACH);
    }

    private static boolean isSyncedLifeShotTargetTooFar(LivingEntity user, Level level) {
        ActionTarget syncedTarget = getSyncedLookTarget(user, level);
        return !syncedTarget.isEmpty(level)
                && syncedTarget.getType() == ActionTarget.TargetType.ENTITY
                && !isWithinLifeShotRange(syncedTarget, user, level);
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
			applyLifeShotTo(target, user);
			// 1.16 paid the Life Shot's cost whatever it did to the target and ignored the result
			StandAbilityStamina.consume(ability, StandPower.get(user), STAMINA_COST, false);
		}

	}

    @Override
    public float getStaminaCost(Power<?> context) {
        return STAMINA_COST;
    }
}
