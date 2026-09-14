package rotp.core.impl.stands.crazydiamond;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
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
	
	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		StandPower standPower = PowerClass.STAND.cast(context);		if (standPower == null) return null;
		LivingEntity user = standPower.getUser();				if (user == null || !CrazyDLeaveObjectPunchEffect.canUseItem(user.getOffhandItem())) return null;
		StandEntity stand = standPower.getSummonedStandEntity();	if (stand == null) return null;
		EntityActionInstance curAction = stand.getCurStandAction();	if (curAction == null) return null;

		if (curAction instanceof StandEntityHeavyPunch punch
				&& punch.getPunchModifiers().isEmpty()) {
			abilities.replaceOtherAbilityWith(standPower, "heavy_punch", this);
		}

		return null;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		LivingEntity user = standPower != null ? standPower.getUser() : null;
		if (user == null || !CrazyDLeaveObjectPunchEffect.canUseItem(user.getOffhandItem())) {
			return ConditionCheck.NEGATIVE;
		}
		if (!hasCompatibleHeavyPunch(standPower)) {
			return ConditionCheck.NEGATIVE;
		}
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
	}
	
	@Override
	public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
		if (!level.isClientSide()) {
			if (!CrazyDLeaveObjectPunchEffect.canUseItem(user.getOffhandItem())) return;
			StandPower standPower = StandPower.get(user);				if (standPower == null) return;
			StandEntity stand = standPower.getSummonedStandEntity();	if (stand == null) return;
			EntityActionInstance curAction = stand.getCurStandAction();	if (curAction == null) return;

			if (curAction instanceof StandEntityHeavyPunch punch
					&& punch.getPunchModifiers().isEmpty()) {
				if (!StandAbilityStamina.consumeOrMessage(this, standPower, user, STAMINA_COST)) {
					return;
				}
				StandEffectInstance punchEffect = ModStandAbilities.EFFECT_CD_PUNCH_LEAVE_OBJECT.get().create(level);
				standPower.userStandEffects.addEffect(punchEffect);
			}
		}
	}

	private static boolean hasCompatibleHeavyPunch(StandPower standPower) {
		StandEntity stand = standPower.getSummonedStandEntity();
		if (stand == null) {
			return false;
		}
		EntityActionInstance curAction = stand.getCurStandAction();
		return curAction instanceof StandEntityHeavyPunch punch && punch.getPunchModifiers().isEmpty();
	}

}
