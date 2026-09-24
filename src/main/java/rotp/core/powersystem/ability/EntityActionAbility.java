package rotp.core.powersystem.ability;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.client.ui.hud_power.WindupIndicator;
import rotp.core.config.MolangValue;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public class EntityActionAbility extends Ability implements EntityActionType {
	protected Function<EntityActionType, ? extends EntityActionInstance> createActionObj;
	protected ActionAnimIdentifier anim;
	protected boolean ignoresPerformerStun = false;

	/**
	 * @deprecated You can use the other constructor, so that you don't have to override {@link EntityActionAbility#createActionObj()}
	 */
	@Deprecated
	public EntityActionAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		this(abilityType, abilityId, EntityActionInstance::new);
	}
	
	public EntityActionAbility(AbilityType<?> abilityType, AbilityId abilityId, 
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId);
		this.createActionObj = createActionObj;
		anim = ActionAnimIdentifier.getOrCreate(abilityId);
	}
	
	@Override
	public EntityActionInstance createActionObj() {
		return createActionObj.apply(this);
	}
	
	@Override
	public boolean ignoresPerformerStun() {
		return ignoresPerformerStun;
	}
	
	public EntityActionAbility setIgnoresPerformerStun() {
		this.ignoresPerformerStun = true;
		return this;
	}
	
	
	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput, 
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		if (level.isClientSide()) return null;
		return setOrBufferAction(level, user, user, inputMethod, extraClientInput, clickHoldResolveTime, bufferingState);
	}
	
	@Override
	public ConditionCheck checkMainModLogicConditions(Power<?> context) {
		ConditionCheck check = super.checkMainModLogicConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		
		LivingEntity user = context.getUser();
		LivingEntity performer = user != null ? getPerformer(user) : null;
		if (!ignoresPerformerStun() && performer != null && ModStatusEffects.isStunned(performer)) {
			return ConditionCheck.createNegative("stun");
		}
		if (performer != null && !performer.isAlive()) {
			return ConditionCheck.NEGATIVE;
		}
		return check;
	}
	
	@Nullable
	public HeldInput setOrBufferAction(Level level, LivingEntity user, LivingEntity performer, 
			InputMethod inputMethod, FriendlyByteBuf extraClientInput, 
			float skipWindupTime, BufferingState bufferingState) {
		if (user == null || inputMethod == null) return null;
		EntityActionInstance action = initActionOnAbilityUse(level, user, performer, extraClientInput);
		if (action == null) return null;
		action.setStartedByClick(inputMethod == InputMethod.CLICK);
		
		LivingComponentAction actionComponent = LivingComponentAction.getComponent(performer);
		// A paused action (performer frozen in stopped time) does not advance, so it must not hold back new input.
		boolean toBuffer = action.ability.shouldBufferInput(actionComponent)
				&& !actionComponent.isActionPausedInStoppedTime();
		
		HeldInput actionOrQueue = null;
		if (toBuffer) {
			if (bufferingState.canBuffer()) {
				bufferingState.setToBuffer();
			}
		}
		else {
			if (skipWindupTime > 0) {
				action.skipWindupTime(performer, skipWindupTime);
			}
			actionOrQueue = actionComponent.setAction(action, user, SyncType.TRACKING_AND_SELF);
			bufferingState.setActionSuccess();
		}
		return actionOrQueue;
	}
	
	
	/**
	 * Is used to initialize some EntityActionInstance values, 
	 * that are either configurable (stuff like phases length), 
	 * or are context-dependent.
	 */
	@ApiStatus.OverrideOnly
	public void initActionFromConfig(EntityActionInstance action, Level level, 
			LivingEntity powerUser, LivingEntity performer) {
		var map = action.phasesLength;
		if (!map.containsKey(ActionPhase.BUTTON_CHARGE))	map.put(ActionPhase.BUTTON_CHARGE,	buttonChargePhase.getAsFloat());
		if (!map.containsKey(ActionPhase.WINDUP))			map.put(ActionPhase.WINDUP,			windupPhase.getAsFloat());
		if (!map.containsKey(ActionPhase.PERFORM))			map.put(ActionPhase.PERFORM,		performPhase.getAsFloat());
		if (!map.containsKey(ActionPhase.RECOVERY))			map.put(ActionPhase.RECOVERY,		recoveryPhase.getAsFloat());
	}

	protected MolangValue buttonChargePhase = new MolangValue.Literal(0);
	protected MolangValue windupPhase = new MolangValue.Literal(0);
	protected MolangValue performPhase = new MolangValue.Literal(1);
	protected MolangValue recoveryPhase = new MolangValue.Literal(0);
	
	public void setDefaultPhaseLength(ActionPhase phase, float length) {
		switch (phase) {
			case BUTTON_CHARGE -> buttonChargePhase = new MolangValue.Literal(length);
			case WINDUP -> windupPhase = new MolangValue.Literal(length);
			case PERFORM -> performPhase = new MolangValue.Literal(length);
			case RECOVERY -> recoveryPhase = new MolangValue.Literal(length);
		}
	}
	
	@Nullable protected ActionPhase buttonHoldingPhase;
	public void setButtonHoldPhase(@Nonnull ActionPhase buttonHoldingPhase) {
		this.buttonHoldingPhase = buttonHoldingPhase;
		setDefaultPhaseLength(buttonHoldingPhase, 999999);
	}

	public boolean canBeStoppedByOriginalHoldCancel(EntityActionInstance action) {
		ActionPhase phase = action != null ? action.getPhase() : null;
		return phase != null && phase != ActionPhase.RECOVERY
				&& (buttonHoldingPhase != null || buttonChargePhase.getAsFloat() > 0
						|| action.hasCustomButtonStopHoldHandler());
	}
	
	
	protected boolean cancelHeldOnGettingAttacked = false;
	
	/**
	 * 1.16 Action.cancelHeldOnGettingAttacked: a hit the user takes while the action is held stops the hold.
	 */
	public EntityActionAbility setCancelHeldOnGettingAttacked() {
		this.cancelHeldOnGettingAttacked = true;
		return this;
	}
	
	/**
	 * Whether this hit stops the held action. Asked only for a hit with a direct entity;
	 * dmgAmount is what the user took after armor and absorption, as in 1.16's LivingDamageEvent.
	 */
	public boolean cancelHeldOnGettingAttacked(EntityActionInstance action, DamageSource dmgSource, float dmgAmount) {
		return cancelHeldOnGettingAttacked;
	}
	
	/**
	 * Whether the action is still held the way a 1.16 held action was:
	 * charging before it fires, or in its button-holding phase.
	 */
	public boolean isActionHeld(EntityActionInstance action) {
		ActionPhase phase = action.getPhase();
		return phase != null && (phase == ActionPhase.BUTTON_CHARGE || phase == buttonHoldingPhase);
	}
	
	/**
	 * Whether releasing the key still reaches an action a CLICK input started. 1.16 performed an action without a
	 * hold on the click and never let the release cancel it; a click-bound action that holds (a button charge,
	 * a button-holding phase, or whatever {@link #isActionHeld} counts) keeps the release as a held one does.
	 */
	public boolean keyReleaseReachesClickAction(EntityActionInstance action) {
		return buttonHoldingPhase != null
				|| action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) > 0
				|| isActionHeld(action);
	}
	
	/**
	 * 1.16 stopHeldAction(false): the hold ends as releasing the key ends it,
	 * except that a charge which has not fired yet is dropped instead of firing.
	 */
	public void stopHeldActionOnGettingAttacked(EntityActionInstance action) {
		if (action.getPhase() == ActionPhase.BUTTON_CHARGE) {
			action.forceStop();
		}
		else {
			action.onKeyRelease(action.getPowerUser());
		}
		action.syncPhaseChanges();
	}
	
	
	/**
	 * 1.16 PowerBaseImpl.tickHeldAction re-ran checkRequirements on every tick of a held action, and stopHeldAction(true)
	 * ran it once more before a released hold fired. Rechecked here, per tick and on release: the power is usable,
	 * the performer is alive and, unless the action ignores it, not stunned, and the Stand still has the parts the
	 * action needs (1.16 StandAction partsRequired). {@link #checkHeldSpecificConditions} adds the ability's own
	 * (items, target, stamina and so on); an ability that does not override it rechecks none of those.
	 * Not rechecked: the cooldown and a busy performer, which the running hold already went through.
	 */
	public ConditionCheck checkHeldActionConditions(EntityActionInstance action, Power<?> context) {
		if (!context.canUsePower()) {
			return ConditionCheck.NEGATIVE;
		}
		LivingEntity performer = action.getPerformer();
		if (performer != null) {
			if (!ignoresPerformerStun() && ModStatusEffects.isStunned(performer)) {
				return ConditionCheck.createNegative("stun");
			}
			if (!performer.isAlive()) {
				return ConditionCheck.NEGATIVE;
			}
		}
		// 1.16 StandAction.checkConditions ran on every held tick: a lost Stand part ends the hold.
		ConditionCheck partsCheck = checkStandPartsRequired(context);
		if (!partsCheck.isPositive()) {
			return partsCheck;
		}
		return checkHeldSpecificConditions(action, context);
	}
	
	/**
	 * What 1.16 Action.checkConditions checked again during a hold: the held items, soap, a target and so on.
	 * Unlike {@link #checkConditions}, it must not ask again what the running hold already went through
	 * (its cooldown, a busy performer).
	 */
	protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
		return ConditionCheck.POSITIVE;
	}
	
	/**
	 * 1.16 stopHeldAction(false) after a failed check; by default the hold stops as a hit that cancels it stops it.
	 */
	public void stopHeldActionOnFailedCheck(EntityActionInstance action) {
		stopHeldActionOnGettingAttacked(action);
	}
	
	/**
	 * 1.16 stopHeldAction(true): a released hold that no longer passes its checks ends without firing, and without
	 * a message. The checks are those of {@link #checkHeldActionConditions}. The server decides; the client follows
	 * the synced phase. Only release code that calls this rechecks; the core Stand releases do not.
	 */
	public boolean canFireReleasedHold(EntityActionInstance action) {
		LivingEntity user = action.getPowerUser();
		if (user == null || user.level().isClientSide() || abilityId.powerClass() == null) {
			return true;
		}
		Power<?> context = getUserPower(user);
		return context == null || checkHeldActionConditions(action, context).isPositive();
	}
	
	/**
	 * Server side, every tick the held action ticks (and while its Stand is stunned). Only a hold whose key is still
	 * down is checked, as 1.16 kept a held action only while its key was held.
	 * @return whether a failed check stopped the hold
	 */
	@ApiStatus.Internal
	public boolean stopHeldActionIfConditionsFail(EntityActionInstance action) {
		LivingEntity user = action.getPowerUser();
		if (action.isOver() || user == null || user.level().isClientSide() || abilityId.powerClass() == null
				|| !isActionHeld(action) || !AbilityInput.isHeldByKey(user, action)) {
			return false;
		}
		Power<?> context = getUserPower(user);
		if (context == null) {
			return false;
		}
		ConditionCheck check = checkHeldActionConditions(action, context);
		if (check.isPositive() || check.shouldContinueHold()) {
			return false;
		}
		ConditionCheck.sendActionFailedMessage(this, check, user);
		stopHeldActionOnFailedCheck(action);
		// 1.16 always ended the hold, even for an action whose release leaves it held.
		if (!action.isOver() && isActionHeld(action)) {
			action.forceStop();
			action.syncPhaseChanges();
		}
		return true;
	}
	
	
	/**
	 * 1.16 StandEntityAction.stopOnHeavyAttack: whether a heavy attack that hurts the Stand performing this action
	 * sends the action into its recovery (StandEntity.stopTaskWithRecovery). The melee barrage said yes.
	 */
	public boolean stopsOnHeavyAttack(EntityActionInstance action) {
		return false;
	}
	
	/**
	 * 1.16 HeavyPunchInstance.afterAttack, called by a heavy attack that hurt the target: a Stand whose action
	 * {@link #stopsOnHeavyAttack stops on a heavy attack} goes into its recovery.
	 */
	public static void onHitByHeavyAttack(Entity target) {
		if (target instanceof StandEntity targetStand && targetStand.isAlive() && !targetStand.level().isClientSide()) {
			EntityActionInstance action = LivingComponentAction.getCurEntityAction(targetStand);
			if (action != null && !action.isOver() && action.ability instanceof EntityActionAbility ability
					&& ability.stopsOnHeavyAttack(action)) {
				action.setPhaseStart(ActionPhase.RECOVERY);
				action.syncPhaseChanges();
			}
		}
	}
	
	
	protected boolean resetsAttackStrengthOnPerform = false;
	
	/**
	 * 1.16 Action.Builder.swingHand() on an action without the user's own punch: performing it (Action.onPerform)
	 * reset a player's attack strength, so a vanilla hit right after it is a weak one.
	 */
	public EntityActionAbility setResetsAttackStrengthOnPerform() {
		this.resetsAttackStrengthOnPerform = true;
		return this;
	}
	
	public boolean resetsAttackStrengthOnPerform() {
		return resetsAttackStrengthOnPerform;
	}
	
	/**
	 * 1.16 GameplayEventHandler.onLivingDamage -> PowerBaseImpl.onUserGettingAttacked: a hit with a direct entity
	 * stops the user's held actions (its own and its Stand's) whose cancelHeldOnGettingAttacked says so.
	 * A hit that a shield or an incoming-damage reduction brought to nothing never reached that event.
	 */
	@ApiStatus.Internal
	public static void onUserGettingAttacked(LivingDamageEvent.Post event) {
		LivingEntity user = event.getEntity();
		DamageSource dmgSource = event.getSource();
		if (user.level().isClientSide() || dmgSource.getDirectEntity() == null
				|| damageBeforeArmor(event) <= 0) {
			return;
		}
		float dmgAmount = event.getNewDamage();
		stopHeldActionOf(user, user, dmgSource, dmgAmount);
		StandPower standPower = StandPower.get(user);
		StandEntity standEntity = standPower != null ? standPower.getSummonedStandEntity() : null;
		if (standEntity != null && standEntity != user) {
			stopHeldActionOf(standEntity, user, dmgSource, dmgAmount);
		}
	}
	
	private static void stopHeldActionOf(LivingEntity performer, LivingEntity user, DamageSource dmgSource, float dmgAmount) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(performer);
		// A hit on the Stand itself is not its user's (a linked hit reaches the user as its own damage).
		if (action != null && !action.isOver() && action.getPowerUser() == user
				&& action.ability instanceof EntityActionAbility ability
				&& ability.isActionHeld(action)
				&& ability.cancelHeldOnGettingAttacked(action, dmgSource, dmgAmount)) {
			ability.stopHeldActionOnGettingAttacked(action);
		}
	}
	
	// What entered actuallyHurt (1.16's LivingHurtEvent result): the damage taken plus what armor, enchantments,
	// effects and absorption took off.
	private static float damageBeforeArmor(LivingDamageEvent.Post event) {
		return event.getNewDamage()
				+ event.getReduction(DamageContainer.Reduction.ARMOR)
				+ event.getReduction(DamageContainer.Reduction.ENCHANTMENTS)
				+ event.getReduction(DamageContainer.Reduction.MOB_EFFECTS)
				+ event.getReduction(DamageContainer.Reduction.ABSORPTION);
	}
	

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return anim;
	}
	
	
	protected LivingEntity getPerformer(LivingEntity user) {
		return user;
	}
	
	@Override
	public WindupIndicator cl_windupIndicator(LivingEntity clientPlayer, WindupIndicator indicator, float partialTick) {
		boolean hasButtonCharge = buttonChargePhase.getAsFloat() > 0;
		float windupHoldToFire = getWindupHoldToFireIndicatorLength();
		indicator.maxValue = hasButtonCharge || windupHoldToFire > 0 ? 1 : 0;
		indicator.value = -1;
		
		if (indicator.maxValue > 0) {
			LivingEntity performer = getPerformer(clientPlayer);
			if (performer != null) {
				EntityActionInstance curAction = LivingComponentAction.getCurEntityAction(performer);
				if (isSameAbilityAction(curAction)) {
					ActionPhase phase = curAction.getPhase();
					if (phase == null) {
						return null;
					}
					if (phase == ActionPhase.BUTTON_CHARGE && hasButtonCharge) {
						indicator.maxValue = curAction.getAnimPhaseLength();
						if (indicator.maxValue > 0) {
							indicator.value = curAction.getAnimPhaseTick(partialTick);
						}
					}
					else if (phase == ActionPhase.WINDUP && windupHoldToFire > 0) {
						indicator.maxValue = windupHoldToFire;
						indicator.value = Math.min(curAction.getAnimPhaseTick(partialTick), windupHoldToFire);
					}
					else if (hasButtonCharge && windupHoldToFire <= 0) {
						indicator.maxValue = curAction.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
						indicator.value = indicator.maxValue;
					}
					else {
						return null;
					}
				}
			}
			return indicator;
		}
		
		return super.cl_windupIndicator(clientPlayer, indicator, partialTick);
	}

	protected boolean isSameAbilityAction(@Nullable EntityActionInstance action) {
		if (action == null) {
			return false;
		}
		EntityActionType actionAbility = action.ability;
		return actionAbility == this
				|| actionAbility instanceof Ability ability && ability.getAbilityId().equals(getAbilityId());
	}

	protected float getWindupHoldToFireIndicatorLength() {
		return 0;
	}

}
