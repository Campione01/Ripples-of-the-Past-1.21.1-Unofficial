package rotp.core.impl.powers.hamon.abilities;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.hamon.HamonData;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class HamonActionRuntimeAbility extends EntityActionAbility {
	private float energyCost;
	private float heldTickEnergyCost;
	private float heldWalkSpeed = 1.0F;
	private int hamonCooldownTicks;
	private int hamonHoldToFireTicks;
	private boolean hamonContinueHoldingAfterFire;
	@Nullable private HamonData.HamonStat hamonStat;
	@Nullable private Supplier<? extends SoundEvent> hamonShout;
	private final Map<String, Supplier<? extends SoundEvent>> hamonTechniqueShouts = new HashMap<>();

	public HamonActionRuntimeAbility(AbilityType<?> abilityType, AbilityId abilityId,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId, createActionObj);
	}

	public HamonActionRuntimeAbility hamonEnergyCost(float energyCost) {
		this.energyCost = Math.max(energyCost, 0.0F);
		return this;
	}

	public HamonActionRuntimeAbility hamonHeldTickEnergyCost(float heldTickEnergyCost) {
		this.heldTickEnergyCost = Math.max(heldTickEnergyCost, 0.0F);
		return this;
	}

	public HamonActionRuntimeAbility hamonHeldWalkSpeed(float heldWalkSpeed) {
		this.heldWalkSpeed = Math.max(heldWalkSpeed, 0.0F);
		return this;
	}

	public HamonActionRuntimeAbility hamonHoldToFire(int ticksToFire, boolean continueHolding, int windupTicks, float performTicks) {
		this.hamonHoldToFireTicks = Math.max(ticksToFire, 0);
		this.hamonContinueHoldingAfterFire = continueHolding;
		setDefaultPhaseLength(ActionPhase.WINDUP, Math.max(windupTicks, ticksToFire));
		if (continueHolding) {
			setButtonHoldPhase(ActionPhase.PERFORM);
		}
		else {
			buttonHoldingPhase = null;
			setDefaultPhaseLength(ActionPhase.PERFORM, Math.max(performTicks, 1.0F));
		}
		return this;
	}

	public HamonActionRuntimeAbility hamonCooldown(int cooldownTicks) {
		this.hamonCooldownTicks = Math.max(cooldownTicks, 0);
		return this;
	}

	public HamonActionRuntimeAbility hamonStat(HamonData.HamonStat stat) {
		this.hamonStat = stat;
		return this;
	}

	public HamonActionRuntimeAbility hamonShout(Supplier<? extends SoundEvent> shout) {
		this.hamonShout = shout;
		return this;
	}

	public HamonActionRuntimeAbility hamonTechniqueShout(String techniqueName, Supplier<? extends SoundEvent> shout) {
		if (techniqueName != null && shout != null) {
			hamonTechniqueShouts.put(techniqueName, shout);
		}
		return this;
	}

	public HamonActionRuntimeAbility hamonRuntime(float energyCost, int cooldownTicks, HamonData.HamonStat stat) {
		return hamonEnergyCost(energyCost).hamonCooldown(cooldownTicks).hamonStat(stat);
	}

	public HamonActionRuntimeAbility hamonHeldRuntime(float heldTickEnergyCost, float heldWalkSpeed) {
		setHamonHeldPerformPhase();
		return hamonHeldTickEnergyCost(heldTickEnergyCost).hamonHeldWalkSpeed(heldWalkSpeed);
	}

	protected void setHamonHeldPerformPhase() {
		setButtonHoldPhase(ActionPhase.PERFORM);
	}

	@Override
	public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
		return false;
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		HamonData hamon = getHamonData(context);
		if (hamon == null) {
			return ConditionCheck.NEGATIVE;
		}
		LivingEntity user = context != null ? context.getUser() : null;
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (hamon.getBloodstreamEfficiency(user) <= 0.0F) {
			return ConditionCheck.createNegative("hamon_no_bloodstream");
		}
		if (JojoDefinitions.isDyingBody(user)) {
			return ConditionCheck.createNegative("dying_hamon");
		}
		if (hamon.isMeditating()) {
			return ConditionCheck.NEGATIVE;
		}
		if (hamon.isAbilityOnCooldown(name())) {
			return ConditionCheck.createNegative("cooldown");
		}
		if (!hasHamonEnergy(context, hamon)) {
			return ConditionCheck.createNegative("no_energy_hamon");
		}
		// 1.16 Action.checkHeldItems came after the energy check.
		return checkHeldItems(user);
	}

	protected boolean consumeRuntimeOnPerform(LivingEntity user) {
		Power<?> context = getUserPower(user);
		HamonData hamon = getHamonData(context);
		if (hamon == null) {
			return false;
		}
		if (hamon.isAbilityOnCooldown(name()) || !hasHamonEnergy(context, hamon)) {
			return false;
		}
		if (isCreative(context)) {
			playHamonShout(user, hamon);
			return true;
		}

		boolean changed = false;
		if (energyCost > 0.0F) {
			float pointsEnergy = Math.min(energyCost, hamon.getEnergy());
			float efficiency = hamon.getActionEfficiency(energyCost, false, null, user);
			if (hamon.getHamonEnergyUsageEfficiency(energyCost, true, user) <= 0.0F) {
				return false;
			}
			changed = true;
			if (hamonStat != null) {
				hamon.hamonPointsFromAction(hamonStat, pointsEnergy * efficiency);
			}
		}

		int cooldown = getHamonCooldown(context, -1);
		if (cooldown > 0) {
			hamon.setAbilityCooldown(name(), cooldown);
			changed = true;
		}
		if (changed) {
			hamon.syncOnUpdate(user);
		}
		playHamonShout(user, hamon);
		return true;
	}

	protected void playHamonShout(LivingEntity user, HamonData hamon) {
		// a hold-to-fire technique said its line when it was pressed (HamonRuntimeActionInstance._onTick)
		if (pressShoutPlayed(user)) {
			return;
		}
		sayHamonShout(user, hamon);
	}

	private boolean pressShoutPlayed(LivingEntity user) {
		return isHamonHoldToFire()
				&& LivingComponentAction.getCurEntityAction(user) instanceof HamonRuntimeActionInstance action
				&& action.hamonAbility() == this && action.pressShoutPlayed;
	}

	private void sayHamonShout(LivingEntity user, HamonData hamon) {
		if (user.level().isClientSide()) {
			return;
		}
		// 1.16 Action#playVoiceLine: no line while sneaking, unless the technique was a SHIFT variation
		if (skipsShoutWhileSneaking(user)) {
			return;
		}
		Supplier<? extends SoundEvent> shoutSupplier = null;
		String techniqueName = hamon.getCharacterTechniqueName();
		if (!techniqueName.isEmpty()) {
			shoutSupplier = hamonTechniqueShouts.get(techniqueName);
		}
		if (shoutSupplier == null) {
			shoutSupplier = hamonShout;
		}
		if (shoutSupplier != null) {
			SoundEvent shout = shoutSupplier.get();
			if (shout != null) {
				JojoModUtil.sayVoiceLine(user, shout);
			}
		}
	}

	protected int getHamonCooldown(Power<?> context, int ticksHeld) {
		if (isCreative(context)) {
			return 0;
		}
		return hamonCooldownTicks;
	}

	protected float getHeldTickEnergyCost(Power<?> context, int ticksHeld) {
		if (isCreative(context)) {
			return 0.0F;
		}
		return heldTickEnergyCost;
	}

	protected boolean isHamonHoldToFire() {
		return hamonHoldToFireTicks > 0;
	}

	protected int getHamonHoldToFireTicks() {
		return hamonHoldToFireTicks;
	}

	@Override
	protected float getWindupHoldToFireIndicatorLength() {
		return hamonHoldToFireTicks;
	}

	// A hold-to-fire technique charges in its windup, as 1.16 held it before it fired.
	@Override
	public boolean isActionHeld(EntityActionInstance action) {
		return isHamonHoldToFire() && action.getPhase() == ActionPhase.WINDUP || super.isActionHeld(action);
	}

	protected boolean consumeHeldRuntimeTick(LivingEntity user, int ticksHeld) {
		Power<?> context = getUserPower(user);
		HamonData hamon = getHamonData(context);
		if (hamon == null || hamon.isMeditating()) {
			return false;
		}
		float tickCost = getHeldTickEnergyCost(context, ticksHeld);
		return tickCost <= 0.0F || isCreative(context) || hamon.consumeEnergy(tickCost, user);
	}

	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
	}

	// 1.16 held a technique from the press until it fired or was released: a hold-to-fire one holds while it
	// charges (and after firing only if it keeps holding), any other held one while it performs.
	protected boolean isHeldTickPhase(@Nullable ActionPhase phase) {
		if (phase == ActionPhase.WINDUP) {
			return isHamonHoldToFire();
		}
		return phase == ActionPhase.PERFORM && (!isHamonHoldToFire() || hamonContinueHoldingAfterFire);
	}

	/**
	 * 1.16 PowerBaseImpl.tickHeldAction re-checked the held action's requirements on every server held tick,
	 * before its hold tick; a failed check ended the hold with its message. By default nothing is re-checked.
	 */
	protected ConditionCheck checkHeldTickConditions(HamonHeldActionInstance action, LivingEntity user, Power<?> context) {
		return ConditionCheck.POSITIVE;
	}

	// 1.16 HamonAction, NonStandAction and Action.checkConditions as they ran again on every held tick and before a
	// released hold fired: the bloodstream, a dying body, meditation, some energy or breath left, then the held items.
	@Override
	protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
		HamonData hamon = getHamonData(context);
		LivingEntity user = context.getUser();
		if (hamon == null || user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (hamon.getBloodstreamEfficiency(user) <= 0.0F) {
			return ConditionCheck.createNegative("hamon_no_bloodstream");
		}
		if (JojoDefinitions.isDyingBody(user)) {
			return ConditionCheck.createNegative("dying_hamon");
		}
		if (hamon.isMeditating()) {
			return ConditionCheck.NEGATIVE;
		}
		if (!hasHeldEnergy(context, hamon)) {
			return ConditionCheck.createNegative("no_energy_hamon");
		}
		return checkHeldItems(user);
	}

	// NonStandAction.checkEnergy during a hold asked for the cost plus at least one held tick; Hamon energy passes
	// while any energy or breath is left.
	protected boolean hasHeldEnergy(Power<?> context, HamonData hamon) {
		float needed = energyCost + getHeldTickEnergyCost(context, 0);
		LivingEntity user = context != null ? context.getUser() : null;
		return needed <= 0.0F || isCreative(context) || hamon.hasEnergy(needed, user);
	}

	/**
	 * 1.16 Action.checkHeldItems: the free hands or the soap a technique needs, on the press and on every held tick.
	 */
	protected ConditionCheck checkHeldItems(LivingEntity user) {
		return ConditionCheck.POSITIVE;
	}

	// 1.16 stopHeldAction(false) never fired a hold-to-fire technique: a stopped charge is dropped.
	@Override
	public void stopHeldActionOnGettingAttacked(EntityActionInstance action) {
		if (isHamonHoldToFire() && action.getPhase() == ActionPhase.WINDUP) {
			action.forceStop();
			action.syncPhaseChanges();
			return;
		}
		super.stopHeldActionOnGettingAttacked(action);
	}

	protected void syncHeldRuntimeTick(LivingEntity user, HamonData hamon, int ticksHeld) {
		if (ticksHeld % 5 == 0) {
			hamon.syncOnUpdate(user);
		}
	}

	protected float getHamonEnergyNeeded(Power<?> context, int ticksHeld) {
		float cost = energyCost;
		if (isHamonHoldToFire()) {
			cost += getHeldTickEnergyCost(context, ticksHeld) * Math.max(getHamonHoldToFireTicks() - ticksHeld, 1);
		}
		return cost;
	}

	protected boolean hasHamonEnergy(Power<?> context, HamonData hamon) {
		float needed = getHamonEnergyNeeded(context, 0);
		LivingEntity user = context != null ? context.getUser() : null;
		return needed <= 0.0F || isCreative(context) || hamon.hasEnergy(needed, user);
	}

	@Nullable
	protected HamonData getHamonData(Power<?> context) {
		if (context == null) {
			return null;
		}
		PowerData data = context.getDataForAbility(this);
		return data instanceof HamonData hamon ? hamon : null;
	}

	protected boolean isCreative(Power<?> context) {
		return context != null && context.getUser() instanceof Player player && player.getAbilities().instabuild;
	}

	public static class HamonRuntimeActionInstance extends EntityActionInstance {
		private boolean runtimeApplied;
		private boolean pressShoutPlayed;

		public HamonRuntimeActionInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected void _onTick() {
			HamonActionRuntimeAbility pressed = hamonAbility();
			// 1.16 PowerBaseImpl.onClickAction said a hold-to-fire technique's line on the press, not when it fired
			if (!pressShoutPlayed && pressed != null && pressed.isHamonHoldToFire()
					&& getPhase() == ActionPhase.WINDUP && !level().isClientSide()) {
				pressShoutPlayed = true;
				LivingEntity user = getPowerUser();
				HamonData hamon = user != null ? pressed.getHamonData(pressed.getUserPower(user)) : null;
				if (hamon != null) {
					pressed.sayHamonShout(user, hamon);
				}
			}
			if (!runtimeApplied && getPhase() == ActionPhase.PERFORM && getPhaseTick() < 1) {
				runtimeApplied = true;
				LivingEntity user = getPowerUser();
				if (!level().isClientSide() && user != null && hamonAbility() != null
						&& !hamonAbility().consumeRuntimeOnPerform(user)) {
					forceStop();
					syncPhaseChanges();
					return;
				}
			}
			super._onTick();
		}

		@Nullable
		protected HamonActionRuntimeAbility hamonAbility() {
			return ability instanceof HamonActionRuntimeAbility hamonAbility ? hamonAbility : null;
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			if (hamonAbility != null && hamonAbility.isHamonHoldToFire()
					&& (newPhase == ActionPhase.WINDUP
							|| newPhase == ActionPhase.PERFORM && hamonAbility.hamonContinueHoldingAfterFire)) {
				userWalkSpeed = hamonAbility.heldWalkSpeed;
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		protected boolean resolveHamonHoldToFireRelease() {
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			if (hamonAbility != null && hamonAbility.isHamonHoldToFire() && getPhase() == ActionPhase.WINDUP) {
				// 1.16 stopHeldAction(true) checked the requirements once more before the released charge fired.
				if (getPhaseTick() >= hamonAbility.getHamonHoldToFireTicks() && hamonAbility.canFireReleasedHold(this)) {
					setPhaseStart(ActionPhase.PERFORM);
				}
				else {
					forceStop();
				}
				syncPhaseChanges();
				return true;
			}
			return false;
		}

		@Override
		public void onButtonStopHold() {
			resolveHamonHoldToFireRelease();
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			userWalkSpeed = 1.0F;
		}
	}

	public static class HamonHeldActionInstance extends HamonRuntimeActionInstance {
		protected int ticksHeld;

		public HamonHeldActionInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			userWalkSpeed = hamonAbility != null && hamonAbility.isHeldTickPhase(newPhase) ? hamonAbility.heldWalkSpeed : 1.0F;
		}

		// 1.16 onHoldTick ran on every held tick, a hold-to-fire technique's charge included; once it fired, it was no longer held.
		@Override
		public void actionTick() {
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			if (hamonAbility == null || !hamonAbility.isHeldTickPhase(getPhase())) {
				return;
			}
			LivingEntity user = getPowerUser();
			Power<?> context = user != null ? hamonAbility.getUserPower(user) : null;
			HamonData hamon = hamonAbility.getHamonData(context);
			if (!level().isClientSide()) {
				if (user == null || hamon == null) {
					forceStop();
					syncPhaseChanges();
					return;
				}
				ConditionCheck check = hamonAbility.checkHeldTickConditions(this, user, context);
				if (!check.isPositive()) {
					ConditionCheck.sendActionFailedMessage(hamonAbility, check, user);
					forceStop();
					syncPhaseChanges();
					return;
				}
				if (!hamonAbility.consumeHeldRuntimeTick(user, ticksHeld)) {
					forceStop();
					syncPhaseChanges();
					return;
				}
			}
			if (user != null && hamon != null) {
				hamonAbility.onHeldTick(this, user, context, hamon, ticksHeld);
				if (!level().isClientSide()) {
					hamonAbility.syncHeldRuntimeTick(user, hamon, ticksHeld);
				}
			}
			ticksHeld++;
		}

		@Override
		public void onButtonStopHold() {
			if (resolveHamonHoldToFireRelease()) {
				return;
			}
			forceStop();
			syncPhaseChanges();
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			userWalkSpeed = 1.0F;
		}
	}
}
