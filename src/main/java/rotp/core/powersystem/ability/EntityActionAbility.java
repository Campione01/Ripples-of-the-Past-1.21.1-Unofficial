package rotp.core.powersystem.ability;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.client.ui.hud_power.WindupIndicator;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.config.MolangValue;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
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
import net.minecraft.world.entity.player.Player;
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
		
		LivingEntity slotEntity = takesUsersStandPowerSlot() ? user : performer;
		LivingComponentAction actionComponent = LivingComponentAction.getComponent(slotEntity, abilityId.powerClass());
		boolean toBuffer = holdsBackInput(action.ability, user, performer, actionComponent);

		HeldInput actionOrQueue = null;
		if (toBuffer) {
			if (bufferingState.canBuffer()) {
				bufferingState.setToBuffer();
			}
		}
		else {
			if (skipWindupTime > 0) {
				action.skipWindupTime(slotEntity, skipWindupTime);
			}
			actionOrQueue = actionComponent.setAction(action, user, SyncType.TRACKING_AND_SELF);
			bufferingState.setActionSuccess();
		}
		return actionOrQueue;
	}

	/**
	 * Whether a buffered press of this ability would still be buffered now, as {@link #setOrBufferAction} decides:
	 * the current action of the slot it takes holds it back, a held action of this ability's power holds back a player's
	 * held press, or its performer's held action in another slot holds it back (a port of a 1.16 StandAction in the user's
	 * Stand-power slot, {@link #performsInUsersStandPowerSlot}). The input buffer waits instead of replaying it.
	 */
	@ApiStatus.Internal
	public boolean wouldBufferInput(LivingEntity user) {
		LivingEntity performer = user != null ? getPerformer(user) : null;
		LivingEntity slotEntity = takesUsersStandPowerSlot() ? user : performer;
		return slotEntity != null && holdsBackInput(this, user, performer,
				LivingComponentAction.getComponent(slotEntity, abilityId.powerClass()));
	}

	private static boolean holdsBackInput(EntityActionType ability, LivingEntity user, @Nullable LivingEntity performer,
			LivingComponentAction performerAction) {
		// A paused action (performer frozen in stopped time) does not advance, so it must not hold back new input.
		return ability.shouldBufferInput(performerAction) && !performerAction.isActionPausedInStoppedTime()
				|| isHeldPressHeldBack(ability, user)
				|| isHeldBackByPerformersHeldAction(ability, user, performer, performerAction);
	}

	/*
	 * A port of a 1.16 StandAction held in its user's Stand-power slot is its Stand's action too (getPerformer): a press
	 * that Stand performs waits behind it in the input buffer while it lets no other through and does not give way to the
	 * press, as behind the Stand's own action (owner decision N2; 1.16 refused such a press while the power held it).
	 */
	private static boolean isHeldBackByPerformersHeldAction(EntityActionType ability, LivingEntity user,
			@Nullable LivingEntity performer, LivingComponentAction performerAction) {
		if (performer == null || !(ability instanceof EntityActionAbility pressed) || pressed.abilityId.powerClass() == null) {
			return false;
		}
		EntityActionInstance held = AbilityInput.getHeldAction(user, pressed.abilityId.powerClass());
		return held != null && held != performerAction.getAction() && held.getPerformer() == performer
				&& !held.heldAllowsOtherAbilities() && !held.canBeCancelledInto(ability);
	}

	/**
	 * Whether this ability is a port of a 1.16 StandAction, which its user's Stand power held and performed
	 * (PowerBaseImpl.heldActionData), not a Stand's task (a StandEntityAction): its action lives in the user's Stand-power
	 * slot (LivingComponentAction#getComponent(LivingEntity, PowerClass)) whether the Stand is out or not, beside the
	 * Stand's own slot, which keeps only the Stand's tasks, so it goes off at once while the Stand runs a task (a held
	 * hold-type task's hold still ends, as 1.16's stopHeldAction ended it). Its performer
	 * (EntityActionInstance#getPerformer: the stun, range and aim) is the user's summoned Stand while it is out, else the
	 * user, as 1.16 StandAction.getPerformer; the action's own performer field stays the slot's entity. A press the Stand
	 * performs waits behind such a held action while it lets none through (owner decision N2). It is no task: its Stand's
	 * summon lock and stun do not hold it back (EntityActionInstance#isHeldBackBySummonLock and #isHeldBackByStandStun
	 * stay for a port that still runs on its Stand), and the Stand takes a pose from it only if it asks
	 * (EntityActionInstance#posesItsStand). False by default; the time stop returns true. Only for a Stand-power ability.
	 * RotpAddonApi#FEATURE_STAND_ACTION_PORT_SLOT_V1.
	 */
	@ApiStatus.OverrideOnly
	public boolean performsInUsersStandPowerSlot() {
		return false;
	}

	private boolean takesUsersStandPowerSlot() {
		return abilityId.powerClass() == PowerClass.STAND && performsInUsersStandPowerSlot();
	}

	/** Whether a press of this ability takes the slot that holds this action and the action holds it back there. */
	@ApiStatus.Internal
	public boolean waitsBehind(LivingEntity user, @Nullable EntityActionInstance action) {
		return action != null && getCurActionOfSlot(user) == action && !action.canBeCancelledInto(this);
	}

	// the action in the slot a press of this ability by this user takes, if any
	@Nullable
	private EntityActionInstance getCurActionOfSlot(LivingEntity user) {
		LivingEntity slotEntity = takesUsersStandPowerSlot() ? user : getPerformer(user);
		return slotEntity != null ? LivingComponentAction.getCurEntityAction(slotEntity, abilityId.powerClass()) : null;
	}

	/**
	 * Whether a player's press of this ability waits in the input buffer because its power holds an action, the
	 * held-press rule of {@link #setOrBufferAction} alone (not the performer's current action, which a press may end
	 * itself). A press that ends the Stand's current action before it sets its own (the time stop) asks this first, so
	 * that it does not end the hold 1.16's client never sent it to.
	 */
	@ApiStatus.Internal
	public boolean isHeldPressHeldBack(LivingEntity user) {
		return isHeldPressHeldBack(this, user);
	}

	/*
	 * 1.16's client sent no press of a held action (getHoldDurationMax above 0) while the action's power held one
	 * (ActionsOverlayGui.onActionClick, power by power), whatever the held action let through; the input buffer keeps
	 * such a press until the hold ends (owner decision N2). A mob's press went to the server and through.
	 */
	private static boolean isHeldPressHeldBack(EntityActionType ability, LivingEntity user) {
		if (!(user instanceof Player) || !(ability instanceof EntityActionAbility pressed) || !pressed.isHoldAction()) {
			return false;
		}
		PowerClass<?> powerClass = pressed.abilityId.powerClass();
		return powerClass != null && AbilityInput.getHeldAction(user, powerClass) != null;
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

	/**
	 * Whether this ability's action is held from its press, as a 1.16 action whose getHoldDurationMax was above 0 (built
	 * with holdType or holdToFire): a button charge or a button-holding phase. An ability that holds its action another
	 * way ({@link #isActionHeld}) returns true.
	 */
	public boolean isHoldAction() {
		return buttonHoldingPhase != null || buttonChargePhase.getAsFloat() > 0;
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
	 * Whether the action is held the way 1.16 held it, from its press until it fired or was let go
	 * (PowerBaseImpl.setHeldAction to stopHeldAction): the held recheck and the getting-attacked cancel cover it now.
	 * Besides the phases {@link #isActionHeld} counts, the phases before the button-holding phase count, and the phases
	 * before the first phase with a length that isActionHeld counts (an override's), such as a windup played before a
	 * held perform.
	 */
	public boolean isHeldSincePress(EntityActionInstance action) {
		if (isActionHeld(action)) {
			return true;
		}
		ActionPhase phase = action.getPhase();
		if (phase == null) {
			return false;
		}
		return buttonHoldingPhase != null && phase.ordinal() < buttonHoldingPhase.ordinal()
				|| action.isBeforeFirstHeldPhase(this);
	}

	/**
	 * Whether the press of this ability that started this action leaves the ability's cooldown to the action's hold, as
	 * 1.16 set a held action's cooldown only as it fired (PowerBaseImpl.performAction, a hold-to-fire action) or as its
	 * hold ended (stopHeldAction, a hold-only action, with the ticks it was held), never at its press: see
	 * EntityActionInstance#takeCooldownAfterHold. Asked on the server right after the press set the action, only when
	 * the press would set the cooldown ({@link #shouldSetCooldownOnKeyPress}). By default an action held from its press
	 * ({@link #isHeldSincePress}); an ability whose 1.16 action was not held returns false to keep the press's cooldown.
	 */
	public boolean setsCooldownAfterHold(EntityActionInstance action) {
		return isHeldSincePress(action);
	}

	/** The action a press of this ability started, if it takes the ability's cooldown after its hold, else null. */
	@ApiStatus.Internal
	@Nullable
	public static EntityActionInstance getHoldTakingCooldown(Ability ability, @Nullable HeldInput pressed) {
		return pressed instanceof EntityActionInstance action && action.ability == ability && !action.isOver()
				&& ability instanceof EntityActionAbility entityAbility && entityAbility.setsCooldownAfterHold(action)
				? action : null;
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
	 * ran it once more before a released or full-length hold fired. Rechecked here, per tick and before such a hold fires,
	 * in 1.16's order: the power is usable, the ability is not on a cooldown set during the hold (1.16 isActionOnCooldown,
	 * no message; see {@link #getHeldCooldownSets}), the performer is alive and, unless the action ignores it, not
	 * stunned, what the action asks of its performer ({@link #checkHeldPerformerConditions}: a Stand's conditions, 1.16
	 * StandEntityAction.checkStandConditions), the Stand still has the parts the action needs (1.16 StandAction
	 * partsRequired), then {@link #checkHeldSpecificConditions}, the ability's own (items, target, stamina and so on;
	 * an ability that does not override it rechecks none of those), and last whether the ability is still unlocked
	 * (1.16 isUnlocked, "not_unlocked"), without its Resolve level ({@link #isAbilityStillUnlocked}). Not rechecked: a
	 * busy performer, which the running hold already went through.
	 */
	public ConditionCheck checkHeldActionConditions(EntityActionInstance action, Power<?> context) {
		if (!context.canUsePower()) {
			return ConditionCheck.NEGATIVE;
		}
		if (isCooldownSetDuringHold(action, context)) {
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
		ConditionCheck performerCheck = checkHeldPerformerConditions(action, context);
		if (!performerCheck.isPositive()) {
			return performerCheck;
		}
		// 1.16 StandAction.checkConditions ran on every held tick: a lost Stand part ends the hold.
		ConditionCheck partsCheck = checkStandPartsRequired(context);
		if (!partsCheck.isPositive()) {
			return partsCheck;
		}
		ConditionCheck check = checkHeldSpecificConditions(action, context);
		if (!check.isPositive()) {
			return check;
		}
		return isAbilityStillUnlocked(context) ? check : ConditionCheck.createNegative("not_unlocked");
	}

	/*
	 * 1.16 set an action's own cooldown only as it fired (PowerBaseImpl.performAction) or as a hold-only action's hold
	 * ended (stopHeldAction), so the held recheck's isActionOnCooldown saw only a cooldown set from elsewhere. The port
	 * leaves a held action's to its hold too (setsCooldownAfterHold), but an ability that keeps the press's cooldown has
	 * it set at its press, and an action may set its own as it runs: a cooldown counts only if it was set since the hold
	 * last saw it, which it does when first rechecked and after each of its own ticks (EntityActionInstance). Server side.
	 */
	private boolean isCooldownSetDuringHold(EntityActionInstance action, Power<?> context) {
		LivingEntity user = context.getUser();
		if (user == null || user.level().isClientSide()) {
			return false;
		}
		int sets = getHeldCooldownSets(context);
		if (action.ownCooldownSets() == EntityActionInstance.OWN_COOLDOWN_UNSEEN) {
			action.setOwnCooldownSets(sets);
			return false;
		}
		return sets >= 0 && sets != action.ownCooldownSets();
	}

	/**
	 * For the held cooldown recheck ({@link #checkHeldActionConditions}): how many times this ability's cooldown has been
	 * set in the power, while it is cooling down there, and -1 while it is not. By default a Stand ability's StandPower
	 * cooldown, unless its user turned Stand cooldowns off, as the press asks it ({@link #checkMainModLogicConditions}).
	 * An ability whose power keeps its own cooldowns returns -1 unless it gives those here.
	 */
	protected int getHeldCooldownSets(Power<?> context) {
		if (context instanceof StandPower standPower
				&& !PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(standPower)
				&& standPower.isAbilityOnCooldown(name())) {
			return standPower.getAbilityCooldownSets(name());
		}
		return -1;
	}

	/** The cooldown sets the action made in its own tick become its own; called by EntityActionInstance. Server side. */
	@ApiStatus.Internal
	public void noteOwnCooldownSets(EntityActionInstance action) {
		LivingEntity user = action.getPowerUser();
		if (user == null || user.level().isClientSide() || abilityId.powerClass() == null) {
			return;
		}
		Power<?> context = getUserPower(user);
		if (context != null) {
			action.setOwnCooldownSets(getHeldCooldownSets(context));
		}
	}

	/**
	 * What the held recheck asks of the action's performer before the Stand parts, as 1.16 StandEntityAction.checkConditions
	 * asked checkStandConditions before StandAction's; nothing by default. StandEntityAbility asks its Stand's press
	 * conditions again ({@code checkStandEntityConditions}).
	 */
	protected ConditionCheck checkHeldPerformerConditions(EntityActionInstance action, Power<?> context) {
		return ConditionCheck.POSITIVE;
	}

	/**
	 * What 1.16 Action.checkConditions checked again during a hold: the held items, soap, a target and so on.
	 * Unlike {@link #checkConditions}, it must not ask again what the running hold already went through (its cooldown,
	 * which {@link #checkHeldActionConditions} asks its own way, a busy performer).
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
	 * the synced phase. Release code that calls this rechecks: in the core, the Bearing Shot, the charged heavy
	 * punch, the grab throw, the time stop and the Hamon holds (held Hamon moves and Sunlight Yellow Overdrive); a held
	 * charge that fires as its length ends is rechecked through {@link #stopFullChargeIfConditionsFail}.
	 * Also, as 1.16 checkConditions did (!user.canUpdate()), a user frozen in someone else's stopped time cannot fire
	 * the release unless the ability works in stopped time. Only here: 1.16 never ticked a frozen user's hold.
	 */
	public boolean canFireReleasedHold(EntityActionInstance action) {
		LivingEntity user = action.getPowerUser();
		if (user == null || user.level().isClientSide() || abilityId.powerClass() == null) {
			return true;
		}
		Power<?> context = getUserPower(user);
		if (context == null) {
			return true;
		}
		if (!canBeUsedInStoppedTime(context) && isUserStoppedInTime(context)) {
			return false;
		}
		return checkHeldActionConditions(action, context).isPositive();
	}
	
	/**
	 * Server side, every tick the held action ticks (and while its Stand is stunned or summon-locked:
	 * EntityActionInstance#_tickHeldInSummonLock), from the press on
	 * ({@link #isHeldSincePress}), but the tick a charge that ends its hold fires in ({@link #chargeEndsTheHold}), which
	 * {@link #stopFullChargeIfConditionsFail} checks instead. Only a hold whose key is still down is checked, as 1.16 kept
	 * a held action only while its key was held.
	 * @return whether a failed check stopped the hold
	 */
	@ApiStatus.Internal
	public boolean stopHeldActionIfConditionsFail(EntityActionInstance action) {
		LivingEntity user = action.getPowerUser();
		if (action.isOver() || user == null || user.level().isClientSide() || abilityId.powerClass() == null
				|| !AbilityInput.isHeldByKey(user, action) || !isHeldSincePress(action)) {
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
		endHoldLeftHeld(action);
		return true;
	}

	// 1.16 stopHeldAction always ended the hold, even for an action whose release leaves it held.
	private void endHoldLeftHeld(EntityActionInstance action) {
		if (!action.isOver() && isHeldSincePress(action)) {
			action.forceStop();
			action.syncPhaseChanges();
		}
	}

	/**
	 * 1.16 PowerBaseImpl.tickHeldAction ended a hold that reached its length with stopHeldAction(true), which checked the
	 * requirements once more and, when they failed, fired nothing, silently (the Stand's task was stopped): a held charge
	 * that ends by its length and fires as it does is checked here as a released hold is ({@link #canFireReleasedHold}),
	 * by {@link EntityActionInstance} before it leaves the charge, and in its last tick instead of the held recheck when
	 * the hold ends with it ({@link #chargeEndsTheHold}). Server side, only while a key holds the action.
	 * @return whether the failed check ended the action
	 */
	@ApiStatus.Internal
	public boolean stopFullChargeIfConditionsFail(EntityActionInstance action) {
		LivingEntity user = action.getPowerUser();
		if (action.isOver() || user == null || user.level().isClientSide() || abilityId.powerClass() == null
				|| !AbilityInput.isHeldByKey(user, action) || canFireReleasedHold(action)) {
			return false;
		}
		action.forceStop();
		action.syncPhaseChanges();
		return true;
	}

	/**
	 * Whether a held charge (a held phase before the perform, whose next phase is not held) takes effect in the tick it
	 * fires in, as a 1.16 holdToFire(N, false) action its user performed did: PowerBaseImpl.tickHeldAction fired it on the
	 * N-th held tick from the press, after N - 1 held ticks, and performed it there, so the port's charge of N ticks
	 * leaves on its (N - 1)-th tick and performs on the N-th. True by default: a non-Stand power's charge, and a 1.16
	 * holdToFire(N, true) modelled as a held charge going on into a held perform that acts from its first tick
	 * (Atmospheric Rift, Divine Sandstorm: 1.16 holdTick sent the first wave on the N-th held tick; see
	 * {@link #chargeEndsTheHold}). A charge the Stand's task performs runs its N full ticks and starts its next phase in
	 * the next tick (StandEntityAbility). Either way its last tick is checked as the firing tick
	 * (EntityActionInstance#isChargeFiringTick).
	 */
	public boolean chargeFiresAsItsLengthEnds(EntityActionInstance action) {
		return true;
	}

	/**
	 * Whether a held charge that fires as its length ends ends its hold there too, as
	 * a 1.16 holdToFire(N, false) action's did (getHoldDurationMax was N): PowerBaseImpl.tickHeldAction gave the N-th tick
	 * to stopHeldAction(true), whose check fired nothing on a failure and said nothing, so the charge's last tick is
	 * checked by {@link #stopFullChargeIfConditionsFail} alone, without the held recheck and its message
	 * (EntityActionInstance#isChargeFiringTick). True by default. A 1.16 holdToFire(N, true) modelled as a charge going on
	 * into a perform that acts from its first tick (Atmospheric Rift, Divine Sandstorm) returns false: its hold went on
	 * past N (getHoldDurationMax Integer.MAX_VALUE), so its N-th held tick was an ordinary one, checked with the message.
	 */
	public boolean chargeEndsTheHold(EntityActionInstance action) {
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
	 * 1.16 GameplayEventHandler.onLivingDamage -> PowerBaseImpl.onUserGettingAttacked, power by power: a hit with a direct
	 * entity stops the user's held actions (its own, its Stand-power slot's and its Stand's) whose
	 * cancelHeldOnGettingAttacked says so, held as 1.16's
	 * getHeldAction() was, from the press on ({@link #isHeldSincePress}) while a key holds them, and the hold ends.
	 * A hit that a shield or an incoming-damage reduction brought to nothing, or that a counter cancelled at
	 * LivingHurtEvent (DamageSourceModified#isCountered), never reached that event.
	 */
	@ApiStatus.Internal
	public static void onUserGettingAttacked(LivingDamageEvent.Post event) {
		LivingEntity user = event.getEntity();
		DamageSource dmgSource = event.getSource();
		if (user.level().isClientSide() || dmgSource.getDirectEntity() == null
				|| damageBeforeArmor(event) <= 0 || DamageSourceModified.isCountered(dmgSource, user)) {
			return;
		}
		float dmgAmount = event.getNewDamage();
		stopHeldActionOf(LivingComponentAction.getCurEntityAction(user), user, dmgSource, dmgAmount);
		stopHeldActionOf(LivingComponentAction.getCurEntityAction(user, PowerClass.STAND), user, dmgSource, dmgAmount);
		StandPower standPower = StandPower.get(user);
		StandEntity standEntity = standPower != null ? standPower.getSummonedStandEntity() : null;
		if (standEntity != null && standEntity != user) {
			stopHeldActionOf(LivingComponentAction.getCurEntityAction(standEntity), user, dmgSource, dmgAmount);
		}
	}
	
	private static void stopHeldActionOf(@Nullable EntityActionInstance action, LivingEntity user, DamageSource dmgSource,
			float dmgAmount) {
		// A hit on the Stand itself is not its user's (a linked hit reaches the user as its own damage).
		if (action != null && !action.isOver() && action.getPowerUser() == user
				&& action.ability instanceof EntityActionAbility ability
				&& AbilityInput.isHeldByKey(user, action) && ability.isHeldSincePress(action)
				&& ability.cancelHeldOnGettingAttacked(action, dmgSource, dmgAmount)) {
			ability.stopHeldActionOnGettingAttacked(action);
			ability.endHoldLeftHeld(action);
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
			EntityActionInstance curAction = getCurActionOfSlot(clientPlayer);
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
			return indicator;
		}
		
		return super.cl_windupIndicator(clientPlayer, indicator, partialTick);
	}

	@Override
	public boolean isHeldReadyToFire(LivingEntity user) {
		EntityActionInstance action = user != null ? getCurActionOfSlot(user) : null;
		return isSameAbilityAction(action) && !action.isOver() && isActionHeldReadyToFire(action);
	}

	/**
	 * Whether the action has been held for its hold-to-fire length and waits for the release ({@link #isHeldReadyToFire}):
	 * its button charge, or a windup that holds to fire ({@link #getWindupHoldToFireIndicatorLength}), held at its full
	 * length while the action is still held ({@link #isActionHeld}). A charge that fires as it ends never is, as 1.16
	 * fired a holdToFire(n, false) action without a longer hold on its n-th held tick. An ability whose hold goes on in
	 * another phase once it can fire overrides this.
	 */
	public boolean isActionHeldReadyToFire(EntityActionInstance action) {
		if (!isActionHeld(action)) {
			return false;
		}
		ActionPhase phase = action.getPhase();
		if (phase == ActionPhase.BUTTON_CHARGE) {
			return action.getCurPhaseLength() > 0 && action.getPhaseTick() >= action.getCurPhaseLength();
		}
		float windupHoldToFire = getWindupHoldToFireIndicatorLength();
		return phase == ActionPhase.WINDUP && windupHoldToFire > 0 && action.getPhaseTick() >= windupHoldToFire;
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
