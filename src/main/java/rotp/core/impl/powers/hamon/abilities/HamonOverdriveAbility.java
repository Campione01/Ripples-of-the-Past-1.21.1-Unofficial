package rotp.core.impl.powers.hamon.abilities;

import javax.annotation.Nullable;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.util.functions.UtilFunctions;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 1.16 HamonOverdrive, the parent of both Metal Silver Overdrive variants. It had no hold and no phases: the click
 * performed at once, so the default phases (no windup, one perform tick, no recovery) stay.
 */
public class HamonOverdriveAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 600.0F;
	private static final float BASE_DAMAGE = 2.0F;
	// 1.16 Action.getMaxRangeSqEntityTarget
	private static final double MAX_RANGE_SQ_ENTITY_TARGET = 64.0D;
	private static final String SHIFT_VARIATION = "hamon_beat";
	private static final String METAL_SILVER = "metal_silver_overdrive";
	private static final String METAL_SILVER_WEAPON = "metal_silver_overdrive_weapon";

	public HamonOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, OverdriveInstance::new);
	}

	// 1.16 HamonOverdrive.replaceAction: with Metal Silver Overdrive learned, the click becomes its weapon variant
	// (a weapon in the main hand) or the unarmed one (an armoured or armed target).
	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		LivingEntity user = context != null ? context.getUser() : null;
		HamonData hamon = getHamonData(context);
		if (user == null || hamon == null || !hamon.isSkillLearned(ModHamonSkills.METAL_SILVER_OVERDRIVE.get())) {
			return this;
		}
		if (HamonAbilityHelpers.isItemWeapon(user.getMainHandItem())) {
			Ability weapon = context.getMoveset().getAbility(METAL_SILVER_WEAPON);
			if (weapon == null) {
				return this;
			}
			// 1.16: the weapon variant had no Shift variation, so Shift did not turn it into Overdrive Beat.
			if (abilities != null && abilities.getContextVariationContainer(SHIFT_VARIATION) != null) {
				abilities.replaceOtherAbilityWith(context, SHIFT_VARIATION, weapon);
			}
			return weapon;
		}
		if (HamonMetalSilverOverdriveAbility.targetedByMSO(getAimTarget(user, user.level()))) {
			Ability metalSilver = context.getMoveset().getAbility(METAL_SILVER);
			return metalSilver != null ? metalSilver : this;
		}
		return this;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		LivingEntity user = context != null ? context.getUser() : null;
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		// 1.16 PowerBaseImpl.checkRequirements asked for the target before the energy and the hands.
		ConditionCheck targetCheck = checkTarget(user, getAimTarget(user, user.level()));
		return targetCheck.isPositive() ? super.checkSpecificConditions(context) : targetCheck;
	}

	// 1.16 TargetRequirement.ENTITY and Action.checkRangeAndTarget
	protected ConditionCheck checkTarget(LivingEntity user, ActionTarget target) {
		if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)
				|| livingTarget == user) {
			return ConditionCheck.NEGATIVE;
		}
		return ActionTargetRange.isEntityWithinRange(user, target.getEntity(), MAX_RANGE_SQ_ENTITY_TARGET)
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("target_too_far");
	}

	// 1.16 needsFreeMainHand (MCUtil.isHandFree: gloves count as a free hand).
	@Override
	protected ConditionCheck checkHeldItems(LivingEntity user) {
		return UtilFunctions.isHandFree(user, InteractionHand.MAIN_HAND)
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("hand");
	}

	protected float energyCost() {
		return ENERGY_COST;
	}

	protected HamonSkill unlockingSkill() {
		return ModHamonSkills.OVERDRIVE.get();
	}

	// 1.16 HamonOverdrive.dealDamage
	protected boolean dealDamage(LivingEntity target, LivingEntity user, float damage) {
		return HamonAbilityHelpers.hamonHurt(target, user, damage);
	}

	static ActionTarget getAimTarget(LivingEntity user, Level level) {
		// The user's aim only goes from its client to the server, so its own client reads the aim it sends.
		if (level.isClientSide() && user == ClientProxy.getClientPlayer()) {
			return ClientsideAim.playerAim.getTarget().resolveEntityId(level);
		}
		var aim = LivingComponentAction.getAim(user);
		ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
		return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
	}

	public static class OverdriveInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private boolean capturedPreRuntimeState;
		private float preRuntimeEnergy;
		private float preRuntimeEfficiency;

		public OverdriveInstance(EntityActionType ability) { super(ability); }

		@Nullable
		private HamonOverdriveAbility overdrive() {
			return ability instanceof HamonOverdriveAbility overdrive ? overdrive : null;
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			HamonOverdriveAbility overdrive = overdrive();
			if (user == null || overdrive == null) {
				return;
			}
			captureActionTargetFromAim(user);
		}

		@Override
		protected void _onTick() {
			capturePreRuntimeState();
			super._onTick();
		}

		// The energy and the efficiency of the click, before the runtime spends the cost.
		private void capturePreRuntimeState() {
			HamonOverdriveAbility overdrive = overdrive();
			LivingEntity user = getPowerUser();
			if (capturedPreRuntimeState || overdrive == null || user == null || getPhase() != ActionPhase.PERFORM
					|| getPhaseTick() >= 1 || level().isClientSide()) {
				return;
			}
			HamonData hamon = overdrive.getHamonData(overdrive.getUserPower(user));
			if (hamon != null) {
				preRuntimeEnergy = hamon.getEnergy();
				preRuntimeEfficiency = hamon.getActionEfficiency(overdrive.energyCost(), true, overdrive.unlockingSkill(), user);
				capturedPreRuntimeState = true;
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			LivingEntity user = getPowerUser();
			HamonOverdriveAbility overdrive = overdrive();
			if (user == null || overdrive == null) {
				return;
			}
			if (level.isClientSide()) {
				// The punch is made by the server; the client's own attack strength follows it.
				if (user instanceof Player player) {
					player.resetAttackStrengthTicker();
				}
				return;
			}
			ActionTarget target = getActionTargetSnapshot(level);
			if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)) {
				return;
			}
			HamonData hamon = overdrive.getHamonData(overdrive.getUserPower(user));
			if (hamon == null) {
				return;
			}
			float cost = overdrive.energyCost();
			float efficiency = preRuntimeEfficiency > 0.0F ? preRuntimeEfficiency
					: hamon.getActionEfficiency(cost, true, overdrive.unlockingSkill(), user);
			user.swing(InteractionHand.MAIN_HAND, true);
			if (overdrive.dealDamage(livingTarget, user, BASE_DAMAGE * efficiency)) {
				float pointsEnergy = capturedPreRuntimeState ? preRuntimeEnergy : hamon.getEnergy();
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, Math.min(cost, pointsEnergy) * efficiency);
				hamon.syncOnUpdate(user);
			}
			// 1.16 sent the technique before the attack: the Hamon hit lands first, at the click's attack strength.
			HamonAbilityHelpers.doUserPunch(user, target.getEntity());
		}
	}
}
