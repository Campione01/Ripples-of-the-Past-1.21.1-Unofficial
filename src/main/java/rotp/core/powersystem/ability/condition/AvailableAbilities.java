package rotp.core.powersystem.ability.condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.finisher.StandFinisherCheck;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.LivingEntity;

public class AvailableAbilities {
	public final Map<String, AbilityConditionCheck> _inMoveset = new HashMap<>();
	public final Map<String, Ability> inMovesetAndCanBeUsed = new HashMap<>();
	public StandFinisherCheck standFinisherCheckLast = new StandFinisherCheck();
	
	public AvailableAbilities() {}
	
	public void update(Power<?> context, Moveset baseMoveset) {
		_inMoveset.clear();
		
		// Filtering out the abilities that are currently available (unlocked / make sense in the context)
		
		Map<String, Ability> abilities = baseMoveset.abilities;
		for (var baseAbilityEntry : abilities.entrySet()) {
			Ability ability = baseAbilityEntry.getValue();
			if (ability.isAbilityAvailable(context)) {
				AbilityConditionCheck container = getContainerFor(ability);
				_inMoveset.put(baseAbilityEntry.getKey(), container);
			}
		}
		
		// Checking usage conditions on all of the abilities (this would make the ability gray out in the HUD if you currently can't use it for some reason)
		
		Collection<AbilityConditionCheck> visibleIter = this.__visibleIter; // to avoid ConcurrentModificationException
		visibleIter.clear();
		visibleIter.addAll(_inMoveset.values());
		for (AbilityConditionCheck ability : visibleIter) {
			ability.ability.onConditionCheck(context, this, ability);
		}
		
		// Finisher stuff to replace base attacks with finishers
		
		standFinisherCheckLast.update(context, baseMoveset, this);
		
		// Ability replacing with dynamic polymorphism
		
		for (var abilityEntry : new ArrayList<>(_inMoveset.entrySet())) {
			AbilityConditionCheck abilityContainer = abilityEntry.getValue();
			Ability contextVariation = abilityContainer.ability.replaceWithSubAbility(context, this);
			if (contextVariation != null && contextVariation != abilityContainer.ability && contextVariation.isAbilityAvailable(context)) {
				_inMoveset.put(abilityEntry.getKey(), getCheckedContainerFor(context, contextVariation));
			}
		}
		standFinisherCheckLast.refuseRunningFinisher(context, baseMoveset, this);
		refuseRunningActionPressedAgain(context);

		inMovesetAndCanBeUsed.clear();
		for (var abilityEntry : _inMoveset.entrySet()) {
			AbilityConditionCheck ability = abilityEntry.getValue();
			if (ability.conditionCheck.isPositive()) {
				inMovesetAndCanBeUsed.put(abilityEntry.getKey(), ability.ability);
			}
		}
	}
	private final Collection<AbilityConditionCheck> __visibleIter = new ArrayList<>();

	/**
	 * 1.16 StandEntityAction.canClickDuringTask: the Stand's current action's own ability pressed again, by whichever key,
	 * was refused with no message and not queued unless the action was chainable. Refused here where the action would
	 * let that press cancel it and says it was not chainable ({@link EntityActionInstance#refusesOwnAbilityPressedAgain});
	 * a press the action holds back is buffered instead, as before. While the power still holds that action by the key,
	 * 1.16 drew its slot usable (checkTaskCancelling skipped the held action) and refused the press in
	 * PowerBaseImpl.onClickAction instead: {@link ConditionCheck#NEGATIVE_OWN_HELD_PRESSED_AGAIN}.
	 */
	private void refuseRunningActionPressedAgain(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		StandEntity standEntity = standPower != null ? standPower.getSummonedStandEntity() : null;
		EntityActionInstance action = standEntity != null ? LivingComponentAction.getCurEntityAction(standEntity) : null;
		if (action == null || action.isOver() || !action.refusesOwnAbilityPressedAgain()
				|| !action.canBeCancelledInto(action.ability)) {
			return;
		}
		ConditionCheck refusal = AbilityInput.getHeldAction(standPower.getUser(), PowerClass.STAND) == action
				? ConditionCheck.NEGATIVE_OWN_HELD_PRESSED_AGAIN : ConditionCheck.NEGATIVE;
		for (AbilityConditionCheck container : _inMoveset.values()) {
			if (container.ability == action.ability && container.conditionCheck.isPositive()) {
				container.conditionCheck = refusal;
			}
		}
	}

	
	public void replaceOtherAbilityWith(Power<?> context, String baseAbilityName, Ability subAbility) {
		if (subAbility.isAbilityAvailable(context)) {
			AbilityConditionCheck container = getCheckedContainerFor(context, subAbility);
			_inMoveset.put(baseAbilityName, container);
		}
	}
	
	public void setConditionCheck(String baseAbilityName, ConditionCheck check) {
		AbilityConditionCheck container = _inMoveset.get(baseAbilityName);
		if (container != null) {
			container.conditionCheck = check;
		}
	}
	
	@Nonnull
	public ConditionCheck getConditionCheck(Ability ability) {
		return getConditionCheck(ability.name());
	}

	@Nonnull
	public ConditionCheck getConditionCheck(String baseAbilityName) {
		AbilityConditionCheck container = _inMoveset.get(baseAbilityName);
		return container != null ? container.conditionCheck : ConditionCheck.NEGATIVE;
	}
	
	@Deprecated @Nullable public AbilityConditionCheck getAbilityResolved(Ability baseAbility) { return _inMoveset.get(baseAbility.name()); }
	@Deprecated @Nullable public AbilityConditionCheck getAbilityResolved(String baseAbilityName) { return _inMoveset.get(baseAbilityName); }
	@Nullable public AbilityConditionCheck getContextVariationContainer(Ability baseAbility) { return _inMoveset.get(baseAbility.name()); }
	@Nullable public AbilityConditionCheck getContextVariationContainer(String baseAbilityName) { return _inMoveset.get(baseAbilityName); }
	@Nullable public Ability getContextVariation(String baseAbilityName) { 
		AbilityConditionCheck container = _inMoveset.get(baseAbilityName);
		return container != null ? container.ability : null;
	}

	@Nullable
	public Ability getContextVariationOrDisable(
			String baseAbilityName, String variationName) {
		Ability variation = getContextVariation(variationName);
		if (variation == null) {
			setConditionCheck(baseAbilityName, ConditionCheck.NEGATIVE);
		}
		return variation;
	}

	
	private final Map<AbilityId, AbilityConditionCheck> __cache = new HashMap<>();
	
	@ApiStatus.Internal
	public AbilityConditionCheck getContainerFor(Ability ability) {
		AbilityConditionCheck container = __cache.compute(ability.abilityId, (id, existing) -> {
			if (existing == null) return new AbilityConditionCheck(ability);
			else {
				existing.clear();
				return existing;
			}
		});
		container.ability = ability;
		return container;
	}
	
	@ApiStatus.Internal
	public AbilityConditionCheck getCheckedContainerFor(Power<?> context, Ability ability) {
		AbilityConditionCheck container = getContainerFor(ability);
		ability.onConditionCheck(context, this, container);
		return container;
	}

	public AbilityConditionCheck getDisplayOnlyContainerFor(Ability ability, ConditionCheck conditionCheck) {
		AbilityConditionCheck container = getContainerFor(ability);
		container.conditionCheck = conditionCheck;
		return container;
	}
	
	@ApiStatus.Internal
	public static class AbilityConditionCheck {
		public Ability ability;
		public ConditionCheck conditionCheck;
		public int clientInputState;
		
		private AbilityConditionCheck(Ability ability) {
			this.ability = ability;
			this.conditionCheck = ConditionCheck.POSITIVE;
			this.clientInputState = 1;
		}
		
		private void clear() {
			this.conditionCheck = ConditionCheck.POSITIVE;
			this.clientInputState = 1;
		}

		/**
		 * The condition the slot is drawn by, as 1.16's renderActionIcon drew it from checkRequirements (the icon's tint
		 * and a finisher's green frame): its own check, usable where it only refuses the held action's own press again
		 * ({@link ConditionCheck#NEGATIVE_OWN_HELD_PRESSED_AGAIN}), or a refusal while its power's held action holds its
		 * press back (Ability#isHeldBackByHeldAction, power by power), which the press itself still waits out in the input
		 * buffer.
		 */
		public ConditionCheck drawnCondition(@Nullable LivingEntity user) {
			ConditionCheck check = conditionCheck == ConditionCheck.NEGATIVE_OWN_HELD_PRESSED_AGAIN
					? ConditionCheck.POSITIVE : conditionCheck;
			if (!check.isPositive()) {
				return check;
			}
			return user != null && ability.isHeldBackByHeldAction(user) ? ConditionCheck.NEGATIVE : check;
		}
	}
	
}
