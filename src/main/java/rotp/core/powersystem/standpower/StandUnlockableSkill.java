package rotp.core.powersystem.standpower;

import java.util.Optional;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.powersystem.unlockableskill.UnlockableSkill;

import net.minecraft.network.chat.Component;

public class StandUnlockableSkill extends UnlockableSkill {
	public int expToUnlock;

	public StandUnlockableSkill(String name) {
		super(name);
	}
	
	public static final ConditionCheck NOT_ENOUGH_EXP = ConditionCheck.createNegative(Component.translatable("jojo_ripples.stand_skills.not_enough_exp"));
	@Override
	public ConditionCheck canUnlockFromMenu(Power<?> userPower, PowerData data) {
		ConditionCheck prerequisiteCheck = super.canUnlockFromMenu(userPower, data);
		if (!prerequisiteCheck.isPositive()) {
			return prerequisiteCheck;
		}

		for (String abilityName : unlocksAbilities) {
			Ability ability = userPower != null && userPower.getMoveset() != null ? userPower.getMoveset().getAbility(abilityName) : null;
			if (ability != null) {
				ConditionCheck resolveCheck = ability.getResolveUnlockConditionCheck(userPower);
				if (!resolveCheck.isPositive()) {
					return resolveCheck;
				}
			}
		}
		
		if (((StandTypePersistentData) data).getExp() < this.expToUnlock) {
			return NOT_ENOUGH_EXP;
		}
		
		return ConditionCheck.POSITIVE;
	}
	
	public int getDevPotentialCosmeticPoints(StandPower userPower, StandTypePersistentData data, boolean isUnlocked) {
		if (isUnlocked) return 0;
		if (expToUnlock > 0) return expToUnlock;
		if (hidden) return 250;
		return 1;
	}
	
	// Initialization methods
	
	@Deprecated
	public StandUnlockableSkill setPointsToUnlock(int skillPoints) {
		return this;
	}
	
	public StandUnlockableSkill setExpToUnlock(int exp) {
		this.expToUnlock = exp;
		return this;
	}
	
	@Override
	public StandUnlockableSkill setIsStartingSkill() {
		super.setIsStartingSkill();
		setExpToUnlock(0);
		return this;
	}
	
	
	public static StandUnlockableSkill unlockableAbility(String name, int exp) {
		StandUnlockableSkill skill = new StandUnlockableSkill(name);
		skill.withAbility(name);
		skill.setExpToUnlock(exp);
		return skill;
	}
	
	public static StandUnlockableSkill startingAbility(String name) {
		StandUnlockableSkill skill = new StandUnlockableSkill(name);
		skill.withAbility(name);
		skill.setIsStartingSkill();
		return skill;
	}
	
	@Deprecated
	public static StandUnlockableSkill tiedToMainSkill(String name, String mainSkill) {
		StandUnlockableSkill skill = new StandUnlockableSkill(name);
		skill.withAbility(name);
		skill.mainSkill = Optional.of(mainSkill);
		return skill;
	}

}
