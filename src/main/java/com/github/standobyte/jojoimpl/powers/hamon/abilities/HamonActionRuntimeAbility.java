package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.EntityActionAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.ability.controls.InputMethod;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;

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
		return ConditionCheck.POSITIVE;
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
		if (user.level().isClientSide()) {
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

		public HamonRuntimeActionInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected void _onTick() {
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
				if (getPhaseTick() >= hamonAbility.getHamonHoldToFireTicks()) {
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
			if (hamonAbility != null && hamonAbility.isHamonHoldToFire() && newPhase == ActionPhase.WINDUP) {
				userWalkSpeed = hamonAbility.heldWalkSpeed;
			}
			else {
				userWalkSpeed = hamonAbility != null && newPhase == ActionPhase.PERFORM ? hamonAbility.heldWalkSpeed : 1.0F;
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM && getPhase() != ActionPhase.WINDUP) {
				return;
			}
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			LivingEntity user = getPowerUser();
			Power<?> context = hamonAbility != null && user != null ? hamonAbility.getUserPower(user) : null;
			HamonData hamon = hamonAbility != null ? hamonAbility.getHamonData(context) : null;
			if (!level().isClientSide() && getPhase() == ActionPhase.WINDUP && hamonAbility != null
					&& hamonAbility.isHamonHoldToFire()) {
				if (user == null || hamon == null || !hamonAbility.consumeHeldRuntimeTick(user, ticksHeld)) {
					forceStop();
					syncPhaseChanges();
					return;
				}
				hamonAbility.syncHeldRuntimeTick(user, hamon, ticksHeld);
				ticksHeld++;
				return;
			}
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			if (!level().isClientSide()) {
				if (hamonAbility == null || user == null || hamon == null
						|| !hamonAbility.consumeHeldRuntimeTick(user, ticksHeld)) {
					forceStop();
					syncPhaseChanges();
					return;
				}
			}
			if (hamonAbility != null && user != null && hamon != null) {
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
