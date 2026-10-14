package rotp.core.powersystem.ability.finisher;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import it.unimi.dsi.fastutil.floats.Float2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.floats.Float2ObjectSortedMap;

public class StandFinisherCheck {
	protected final Map<String, Float2ObjectSortedMap<AbilityConditionCheck>> curFinishers = new HashMap<>();
	
	public void update(Power<?> context, Moveset baseMoveset, AvailableAbilities abilities) {
		Map<String, Float2ObjectSortedMap<AbilityConditionCheck>> finishers = this.curFinishers;
		finishers.values().forEach(Map::clear);

		float curFinisherValue = getFinisherValue(context);
		
		Map<String, Ability> movesetAbilities = baseMoveset.abilities;
		for (var baseAbilityEntry : movesetAbilities.entrySet()) {
			Ability ability = baseAbilityEntry.getValue();
			AbilityStandFinisherData isFinisherOf = getAbilityFinisherOf(ability);
			if (isFinisherOf != null && (curFinisherValue >= 0 && isFinisherOf.minFinisherValue() <= curFinisherValue
					|| replacesBaseMissingPart(context, movesetAbilities.get(isFinisherOf.baseAbilityName()), ability))) {
				Float2ObjectSortedMap<AbilityConditionCheck> finishersOf = finishers.computeIfAbsent(isFinisherOf.baseAbilityName(), 
						__ -> new Float2ObjectRBTreeMap<>());
				finishersOf.put(isFinisherOf.minFinisherValue(), abilities.getCheckedContainerFor(context, ability));
			}
		}
		
		for (var finisherEntries : finishers.entrySet()) {
			String baseAbilityName = finisherEntries.getKey();
			Float2ObjectSortedMap<AbilityConditionCheck> finishersOf = finisherEntries.getValue();
			for (var finisherEntry : finishersOf.reversed().entrySet()) {
				AbilityConditionCheck ability = finisherEntry.getValue();
				if (ability.ability.isAbilityAvailable(context) && ability.conditionCheck.isPositive()) {
					abilities._inMoveset.put(baseAbilityName, ability);
					break;
				}
			}
		}
	}
	
	/**
	 * 1.16 StandEntityHeavyAttack.getFinisherVariationIfPresent: a Stand that lost a part its base attack needs, but
	 * none its finisher variation needs, uses the variation whatever its finisher meter (The World's kick with the
	 * arms taken).
	 */
	protected boolean replacesBaseMissingPart(Power<?> context, @Nullable Ability baseAbility, Ability finisher) {
		StandPower standPower = PowerClass.STAND.cast(context);
		StandInstance standInstance = standPower != null ? standPower.getStandInstance().orElse(null) : null;
		if (baseAbility == null || standInstance == null) {
			return false;
		}
		boolean baseMissesPart = false;
		for (StandPart part : StandPart.values()) {
			if (!standInstance.hasPart(part)) {
				if (finisher.isPartRequired(part)) {
					return false;
				}
				if (baseAbility.isPartRequired(part)) {
					baseMissesPart = true;
				}
			}
		}
		return baseMissesPart;
	}
	
	protected float getFinisherValue(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower != null) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null) {
				return standEntity.getFinisherMeter();
			}
		}
		
		return -1;
	}
	
	protected AbilityStandFinisherData getAbilityFinisherOf(Ability ability) {
		return ability.isStandFinisherOf;
	}
	
}
