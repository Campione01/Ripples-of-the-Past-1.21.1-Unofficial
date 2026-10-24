package rotp.core.impl.stands.crazydiamond;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class CrazyDLeaveObjectPunchInput extends Ability {
	private static final float STAMINA_COST = 50.0F;

	public CrazyDLeaveObjectPunchInput(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		isSubAbility = true;
	}
	
	// 1.16 CRAZY_DIAMOND_HEAVY_PUNCH.attackRecoveryFollowup(CRAZY_DIAMOND_LEAVE_OBJECT): a follow-up of the base heavy only
	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(context);		if (standPower == null) return null;
		LivingEntity user = standPower.getUser();				if (user == null || !CrazyDLeaveObjectPunchEffect.canUseItem(user.getOffhandItem())) return null;
		StandEntityHeavyPunch punch = CrazyDiamondHeavyPunchAbility.runningHeavyPunch(standPower, false);
		// 1.16 StandAction.canSetFollowUp: checkRequirements, which refused a stunned Stand, before the key took the follow-up
		if (abilities != null && punch != null && CrazyDiamondHeavyPunchAbility.heavyKeyResolvesTo(abilities, punch)
				&& checkPerformerStun(standPower).isPositive()) {
			abilities.replaceOtherAbilityWith(standPower, "heavy_punch", this);
		}
		return null;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		ConditionCheck stun = checkPerformerStun(context);
		if (!stun.isPositive()) {
			return stun;
		}
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		if (user == null || !CrazyDLeaveObjectPunchEffect.canUseItem(user.getOffhandItem())) {
			return ConditionCheck.NEGATIVE;
		}
		if (!hasCompatibleHeavyPunch(standPower)) {
			return ConditionCheck.NEGATIVE;
		}
		return super.checkSpecificConditions(context);
	}
	
	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (!level.isClientSide()) {
			if (!CrazyDLeaveObjectPunchEffect.canUseItem(user.getOffhandItem())) return;
			StandPower standPower = StandPower.get(user);				if (standPower == null) return;

			if (CrazyDiamondHeavyPunchAbility.runningHeavyPunch(standPower, false) != null) {
				// 1.16 StandAction.onPerform paid the follow-up's cost on the click and ignored the result
				StandAbilityStamina.consume(this, standPower, STAMINA_COST, false);
				StandEffectInstance punchEffect = ModStandAbilities.EFFECT_CD_PUNCH_LEAVE_OBJECT.get().create(level);
				standPower.userStandEffects.addEffect(punchEffect);
			}
		}
	}

	private static boolean hasCompatibleHeavyPunch(StandPower standPower) {
		return CrazyDiamondHeavyPunchAbility.runningHeavyPunch(standPower, false) != null;
	}

	@Override
	public float getStaminaCost(Power<?> context) {
		return STAMINA_COST;
	}
}
