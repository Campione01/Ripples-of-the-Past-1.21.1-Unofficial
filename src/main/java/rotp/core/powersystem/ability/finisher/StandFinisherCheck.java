package rotp.core.powersystem.ability.finisher;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
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

		keepKeyOnRunningFinisher(context, movesetAbilities, abilities);
	}

	/**
	 * 1.16 StandEntityHeavyAttack.getFinisherVariationIfPresent returned the finisher variation while it was the Stand's
	 * current task (getCurrentTaskAction), whatever the finisher meter, unless it needed a part the Stand had lost: the
	 * base's key stays on the running variation ({@link #refuseRunningFinisher} then refuses it).
	 */
	protected void keepKeyOnRunningFinisher(Power<?> context, Map<String, Ability> movesetAbilities, AvailableAbilities abilities) {
		Ability running = getRunningFinisher(context, movesetAbilities);
		if (running == null) {
			return;
		}
		String baseAbilityName = getAbilityFinisherOf(running).baseAbilityName();
		if (abilities._inMoveset.containsKey(baseAbilityName) && !needsMissingPart(context, running)
				&& running.isAbilityAvailable(context)) {
			abilities._inMoveset.put(baseAbilityName, abilities.getCheckedContainerFor(context, running));
		}
	}

	/**
	 * 1.16 StandEntityAction.canClickDuringTask: the finisher variation pressed again while it is the Stand's task, by
	 * whichever key, was refused with no message and not queued (a heavy attack is not chainable). Called once the
	 * abilities are resolved.
	 */
	public void refuseRunningFinisher(Power<?> context, Moveset baseMoveset, AvailableAbilities abilities) {
		Ability running = getRunningFinisher(context, baseMoveset.abilities);
		if (running == null) {
			return;
		}
		for (AbilityConditionCheck container : abilities._inMoveset.values()) {
			if (container.ability == running && container.conditionCheck.isPositive()) {
				container.conditionCheck = ConditionCheck.NEGATIVE;
			}
		}
	}

	/** The finisher variation the summoned Stand is performing now, as it is in the moveset. */
	@Nullable
	protected Ability getRunningFinisher(Power<?> context, Map<String, Ability> movesetAbilities) {
		StandPower standPower = PowerClass.STAND.cast(context);
		StandEntity standEntity = standPower != null ? standPower.getSummonedStandEntity() : null;
		EntityActionInstance action = standEntity != null ? LivingComponentAction.getCurEntityAction(standEntity) : null;
		if (action == null || action.isOver() || !(action.ability instanceof Ability performed)) {
			return null;
		}
		Ability ability = movesetAbilities.get(performed.name());
		return ability != null && ability.getAbilityId().equals(performed.getAbilityId())
				&& getAbilityFinisherOf(ability) != null ? ability : null;
	}

	protected boolean needsMissingPart(Power<?> context, Ability ability) {
		StandPower standPower = PowerClass.STAND.cast(context);
		StandInstance standInstance = standPower != null ? standPower.getStandInstance().orElse(null) : null;
		if (standInstance != null) {
			for (StandPart part : StandPart.values()) {
				if (!standInstance.hasPart(part) && ability.isPartRequired(part)) {
					return true;
				}
			}
		}
		return false;
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
