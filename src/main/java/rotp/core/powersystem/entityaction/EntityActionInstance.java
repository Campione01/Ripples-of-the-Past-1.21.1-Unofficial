package rotp.core.powersystem.entityaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.entityattachment.syncheddata.SynchedDataHelper;
import rotp.core.init.ModDamageTypes;
import rotp.core.network.NetworkPayloadValidation;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionTargetPacket;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.MathUtil;
import rotp.core.util.functions_network.NetworkUtil;
import rotp.core.util.objects_mc.EntityResolver;
import rotp.core.compat.v1_21_4.missingmethods._Vec3;

import it.unimi.dsi.fastutil.objects.Object2FloatArrayMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

// TODO (entity action) test the phase lengths stuff (with partial lengths and lengths < 1)
public class EntityActionInstance implements HeldInput {
	/** Is used in network code, to make sure server and client are on the same page when sending changes to the action's phases from server */
	@ApiStatus.Internal public int id;
	@ApiStatus.Internal private long networkGeneration;
	private boolean restoringInputLifecycle;
	@Nonnull public final EntityActionType ability;
	@ApiStatus.Internal public Object2FloatMap<ActionPhase> phasesLength = new Object2FloatArrayMap<>();
	@ApiStatus.Internal @Nullable public Object2FloatMap<ActionPhase> skippedWindupPhase = null;
	
	public SynchedDataHelper synchedData = new SynchedDataHelper(this, () -> this.level().isClientSide());
	
	@Nonnull protected ActionPhase phase;
	// the last phase with a length the action entered, as 1.16's task phase (StandEntityTask.setPhase took only a phase with
	// ticks, and moveToPhase passed over one without): what its modifiers see as its phase changes and as it ends
	@Nullable private ActionPhase taskPhase;
	protected int curPhaseTick;
	protected float curPhaseLength;
	protected float phasePartialTick;
	protected boolean stoppedHolding = false;
	private boolean startedByClick;
	private int ownCooldownSets = OWN_COOLDOWN_UNSEEN;
	private int heldCooldownTicks = NO_HELD_COOLDOWN;
	private boolean heldCooldownOnFire;
	private int ticksHeldInSummonLock;

	protected LivingEntity performer;
	// the performer's slot it is set in (LivingComponentAction#slotId), which its sync packets carry
	int actionSlot;
	protected EntityResolver powerUser = new EntityResolver();
	
	@Nullable public ActionTarget standRotationTarget;
	public AimingEntity aimAs = AimingEntity.CAMERA_ENTITY;
	@Nullable protected ActionTarget actionTargetSnapshot;
	@Nullable private ActionTarget taskTarget;
	private boolean startingPerform;
	@Nullable private ActionTarget refusedStrike;
	@Nullable private ActionTarget rotationBeforeRefusedStrike;
	@Nullable private Vec3 standOffsetSync;
	@Nullable private StandOffsetFromUser.Rotations standOffsetSyncRotations;
	private boolean standOffsetSyncCanInvertSide = true;
	
	/** Stores the target of the punch action, to be able to communicate with other internal systems, like Stand effects */
	@Nullable public ActionTarget punchedTarget;
	public float lastDamageDealtToLiving;
	@Nullable protected List<StandEffectInstance> punchModifiers;
	
	public float userWalkSpeed = 1;
	
	public EntityActionInstance(EntityActionType ability) {
		this.ability = ability;
	}

	@ApiStatus.Internal
	public long networkGeneration() {
		return networkGeneration;
	}

	@ApiStatus.Internal
	public void setNetworkGeneration(long generation) {
		this.networkGeneration = generation;
	}

	@ApiStatus.Internal
	public InputLifecycleSnapshot captureInputLifecycle() {
		return new InputLifecycleSnapshot(
				phase,
				curPhaseTick,
				curPhaseLength,
				phasePartialTick,
				stoppedHolding,
				new Object2FloatArrayMap<>(phasesLength),
				skippedWindupPhase != null
						? new Object2FloatArrayMap<>(skippedWindupPhase)
						: null);
	}

	@ApiStatus.Internal
	public void restoreInputLifecycle(
			InputLifecycleSnapshot snapshot,
			@Nullable EntityActionInstance failedReplacement) {
		this.phase = snapshot.phase();
		this.curPhaseTick = snapshot.curPhaseTick();
		this.curPhaseLength = snapshot.curPhaseLength();
		this.phasePartialTick = snapshot.phasePartialTick();
		this.stoppedHolding = snapshot.stoppedHolding();
		this.phasesLength = new Object2FloatArrayMap<>(
				snapshot.phasesLength());
		this.skippedWindupPhase = snapshot.skippedWindupPhase() != null
				? new Object2FloatArrayMap<>(snapshot.skippedWindupPhase())
				: null;
		restoringInputLifecycle = true;
		try {
			_onActionStarted(failedReplacement);
		}
		finally {
			restoringInputLifecycle = false;
		}
	}

	/**
	 * Whether onActionSet runs again for an action a failed input transaction puts back: what it spent or took once
	 * when it was first set must not be spent again.
	 */
	public boolean isRestoringInputLifecycle() {
		return restoringInputLifecycle;
	}

	@ApiStatus.Internal
	public record InputLifecycleSnapshot(
			@Nullable ActionPhase phase,
			int curPhaseTick,
			float curPhaseLength,
			float phasePartialTick,
			boolean stoppedHolding,
			Object2FloatMap<ActionPhase> phasesLength,
			@Nullable Object2FloatMap<ActionPhase> skippedWindupPhase) {}
	
	/**
	 * After the phase lengths have been initialized properly, this sets up the action's starting phase
	 */
	public void setStartingPhase() {
		for (ActionPhase phase : ActionPhase.values()) {
			if (!phasesLength.containsKey(phase)) {
				phasesLength.put(phase, 0f);
			}
		}
		setPhaseStart(ActionPhase.values()[0]);
	}
	
	public void skipWindupTime(LivingEntity performer, float time) {
		if (time > 0) {
			switch (phase) {
				case BUTTON_CHARGE, WINDUP -> {
					if (LivingComponentAction.getCurEntityAction(performer,
							ability instanceof EntityActionAbility pressed ? pressed.getAbilityId().powerClass() : null) != null) {
						/* 
						 * Skipping too much makes the light punch animations look too choppy.
						 * On the other hand, this mechanic encourages timing the input clicking:
						 * if the player spams clicks, the inputs get buffered and the punches not get any windup skip,
						 * however if they click after the punch PERFORM phase is over, they still get some windup skipping.
						 * So if they time the inputs just after the punch, the combo speed gets faster.
						 * At the start of a combo (action == null) they get full windup skipping time, 
						 * to not slow down the initial jab just because the silly dev felt like adding the click/hold input system.
						 */
						time = Math.min(time, curPhaseLength / 4);
					}
					time = Math.min(time, curPhaseLength - 1);
					setSkipWindupPhase(phase, time);
				}
				default -> {}
			}
		}
	}
	
	@ApiStatus.Internal
	public void setSkipWindupPhase(ActionPhase phase, float time) {
		skippedWindupPhase = new Object2FloatArrayMap<>();
		skippedWindupPhase.put(phase, time);
		if (this.phase == phase) {
			float tick = getPhaseTick() + time;
			this.curPhaseTick = (int) tick;
			this.phasePartialTick = tick - this.curPhaseTick;
		}
	}
	
	public void extraClientInput(FriendlyByteBuf input) {}
	

	/**
	 * Is called before the action is synched from the server.
	 */
	@ApiStatus.OverrideOnly
	public void onActionSet(@Nullable EntityActionInstance prevAction) {
		
	}

	@ApiStatus.OverrideOnly
	public void actionTick() {
		
	}

	@ApiStatus.OverrideOnly
	public void actionPerformStart() {
		
	}

	@ApiStatus.OverrideOnly
	public void actionPerformEnd() {
		
	}

	@ApiStatus.OverrideOnly
	public void onSetPhase(ActionPhase newPhase) {
		
	}

	@ApiStatus.OverrideOnly
	public void onActionCleared(@Nullable EntityActionInstance newAction) {
		
	}
	
	@ApiStatus.OverrideOnly
	public void onButtonStopHold() {
		
	}

	public boolean hasCustomButtonStopHoldHandler() {
		try {
			return getClass().getMethod("onButtonStopHold").getDeclaringClass() != EntityActionInstance.class;
		}
		catch (NoSuchMethodException e) {
			return false;
		}
	}
	
	/**
	 * Whether a press of this ability, while this action is its performer's action, starts at once and replaces this
	 * action; otherwise the press waits in the input buffer until the action ends. By default only a hold-type Stand action
	 * gives way ({@link #givesWayToOtherAbilities}), to every ability but its own. Whatever this says, a player's press of
	 * an ability that holds waits while that ability's power holds an action, as 1.16's client sent none
	 * (ActionsOverlayGui.onActionClick; EntityActionAbility#wouldBufferInput).
	 */
	@ApiStatus.OverrideOnly
	public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
		return cancellingAbility != ability && givesWayToOtherAbilities();
	}

	/**
	 * Whether this action is a Stand's hold-type action as 1.16 had them: a StandEntityAction built with holdType (held
	 * from its press with nothing to fire, so heldAllowsOtherAction was true), performed by the Stand and held in its
	 * perform. 1.16 let every other action of the Stand's power through it: one the Stand performs replaced it at once
	 * ({@link #givesWayToOtherAbilities}), any other ended its hold as letting go of its key does (PowerBaseImpl
	 * .stopHeldAction(false); AbilityInput). False by default; an action whose 1.16 counterpart was one returns true.
	 */
	@ApiStatus.OverrideOnly
	public boolean isHoldTypeStandAction() {
		return false;
	}

	/**
	 * 1.16 Action.heldAllowsOtherAction: whether the power that holds this action (AbilityInput#getHeldAction) lets its
	 * other abilities through the hold. While it does not, 1.16 PowerBaseImpl.checkRequirements refused every other action
	 * of that power, silently, and its HUD drew their slots unavailable: here a press of the power's other abilities that
	 * this action's performer does not take is refused, one it takes waits in the input buffer as the performer's action
	 * decides (owner decision N2), and the HUD draws both unavailable (Ability#isHeldBackByHeldAction). The other power is
	 * never affected. By default a hold-type Stand action ({@link #isHoldTypeStandAction}); the non-Stand holds whose 1.16
	 * action allowed others (Vampirism's Blood Drain and Freeze, Zombie Devour, Pillar Man Absorption) return true.
	 */
	@ApiStatus.OverrideOnly
	public boolean heldAllowsOtherAbilities() {
		return isHoldTypeStandAction();
	}

	/**
	 * 1.16 StandEntityAction.isCancelable for a hold-type Stand action ({@link #isHoldTypeStandAction}) and for a Stand
	 * action that charged before it fired (a hold-to-fire one) once it has fired: any other Stand action pressed before its
	 * recovery replaces it at once, if it has no recovery (1.16 asked for none for a pressed action; one with a recovery
	 * queued it, as the input buffer does here).
	 */
	public boolean givesWayToOtherAbilities() {
		return phase != null && phase != ActionPhase.RECOVERY && phasesLength.getFloat(ActionPhase.RECOVERY) <= 0
				&& (isHoldTypeStandAction() || isFiredStandCharge());
	}

	/*
	 * Past its charge and no longer held (1.16: no longer the power's held action). A perform that came straight after the
	 * charge gives way only after its first tick, so the shot is not lost (the port's rule: 1.16 ran that tick in the
	 * Stand's tick after the fire, and isCancelable let a press between the two cancel it). After a windup the action does
	 * not hold (1.16 standWindupDuration after the hold), the perform began a tick after the windup's last, where 1.16's
	 * isCancelable let a press cancel it before it performed.
	 */
	private boolean isFiredStandCharge() {
		return ability instanceof StandEntityAbility standAbility && phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) > 0
				&& phase.ordinal() > ActionPhase.BUTTON_CHARGE.ordinal() && !standAbility.isHeldSincePress(this)
				&& !(phase == ActionPhase.PERFORM && getPhaseTick() < 1 && !firedThroughWindup(standAbility));
	}

	// a windup with a length that the ability does not hold, asked without entering it (onSetPhase does not run)
	private boolean firedThroughWindup(EntityActionAbility entityAbility) {
		if (phasesLength.getFloat(ActionPhase.WINDUP) <= 0) {
			return false;
		}
		ActionPhase current = phase;
		phase = ActionPhase.WINDUP;
		try {
			return !entityAbility.isHeldSincePress(this);
		}
		finally {
			phase = current;
		}
	}

	/**
	 * 1.16 StandEntityAction.canClickDuringTask: whether this action's own ability, pressed again while this action is
	 * the Stand's current action, is refused (no message, not buffered) where {@link #canBeCancelledInto} would let that
	 * press cancel it. 1.16 refused it unless the action was chainable (its light attacks, Hierophant Green's emerald
	 * splash, Silver Chariot's dash attack); a press this action holds back is buffered as any other.
	 */
	@ApiStatus.OverrideOnly
	public boolean refusesOwnAbilityPressedAgain() {
		return false;
	}

	/**
	 * Whether its new Stand's summon lock holds this action back, as 1.16 StandEntity.tick held back a Stand's task (a
	 * StandEntityAction) until the lock ran out: true by default. A port of a 1.16 StandAction, which its user's power
	 * performed and the lock never held back, that the port has the Stand perform returns false: it then ticks through the
	 * lock, while the lock stays in StandEntity#summonLockTicks, counts down through a stun as 1.16's did (the summon
	 * fade-in) and still holds back the Stand's next action that does not return false. Asked on both sides, in every tick
	 * of the Stand's lock (LivingComponentAction; RotpAddonApi#FEATURE_ACTION_SUMMON_LOCK_HOOK_V1).
	 */
	@ApiStatus.OverrideOnly
	public boolean isHeldBackBySummonLock() {
		return true;
	}

	/**
	 * Whether its Stand's stun holds this action back, as 1.16 StandEntity.tick held back a stunned Stand's task (a
	 * StandEntityAction) unless the action ignored performer stun: by default as {@link #isHeldBackBySummonLock}, so a
	 * port of a 1.16 StandAction that returns false there is not held back by the stun either. 1.16's power checked such
	 * an action's performer, its summoned Stand (StandAction.getPerformer), only at the press, on every tick of its hold
	 * and as the hold fired (PowerBaseImpl.checkRequirements), as the press check and the held recheck do here
	 * (EntityActionAbility#checkHeldActionConditions); its other ticks run through the stun. Asked on both sides, in
	 * every tick its Stand is stunned (LivingComponentAction; RotpAddonApi#FEATURE_ACTION_STAND_STUN_HOOK_V1).
	 */
	@ApiStatus.OverrideOnly
	public boolean isHeldBackByStandStun() {
		return isHeldBackBySummonLock();
	}

	/**
	 * Whether this action, a port of a 1.16 StandAction in its user's Stand-power slot
	 * (EntityActionAbility#performsInUsersStandPowerSlot) that the user's Stand performs, draws the Stand's pose in place of
	 * the Stand's own action (LivingComponentAction#getPoseComponent). 1.16 drew a Stand's pose from its task alone, but
	 * from its power's held time stop first (StandEntity.getCurPose: the windup, then the perform pose TimeStop.perform
	 * set). False by default; the time stop returns true. RotpAddonApi#FEATURE_STAND_ACTION_PORT_SLOT_V1.
	 */
	@ApiStatus.OverrideOnly
	public boolean posesItsStand() {
		return false;
	}

	/**
	 * Whether this action is a port of one of 1.16's continuous player actions (IPlayerAction, PlayerUtilCap
	 * .continuousAction: Hamon Overdrive Beat, Rebuff Overdrive, Sendo Wave Kick, Hamon Shock, Sunlight Yellow and Scarlet
	 * Overdrive, its barrage, Pillar Man's Blade Dash, Blade Slash, Erratic Blaze King and heavy punch, Vampirism's Claw
	 * Lacerate and Space Ripper Stingy Eyes, Zombie Claw Lacerate). While a player's slot holds one, its abilities that
	 * cannot be used during it (Ability#canBeUsedDuringPlayerAction: by default every one but a Stand's) are refused
	 * without a message, as 1.16 Action.checkConditions refused them. Asked in every phase: true only in the phases the
	 * 1.16 continuous action ran in, from the perform that set it (setPlayerAction) until it stopped, so not in a held
	 * charge before that perform (Blade Dash, Sunlight Yellow and Scarlet Overdrive, its barrage, Space Ripper Stingy
	 * Eyes), which was the power's held action. False by default. RotpAddonApi#FEATURE_PLAYER_ACTION_GATE_V1.
	 */
	@ApiStatus.OverrideOnly
	public boolean isPlayerAction() {
		return false;
	}

	@ApiStatus.OverrideOnly
	public boolean canStaminaRegen(StandPower standPower, StandEntity standEntity) {
		return false;
	}

	@ApiStatus.OverrideOnly
	public void applyStandUserRotation(StandEntity standEntity, LivingEntity user) {}

	/**
	 * Server: asked by its Stand's position update (StandEntity#updatePosition) on every tick the Stand follows its user
	 * and is not stunned, right before the Stand is put at its offset from the user, while this is the Stand's action,
	 * also in the ticks its summon lock holds the action back. 1.16 StandEntity.updatePosition asked the Stand's task for
	 * its offset there (getOffsetFromUser), before the task's own tick, so an offset measured to the task's target or
	 * along the user's look (StandEntityAction.front3dOffset) followed them in that same tick. An action that keeps its
	 * Stand's offset that way sets it here (StandEntityBarrageAbility#refreshFrontOffset); nothing by default
	 * (RotpAddonApi#FEATURE_ACTION_STAND_OFFSET_HOOK_V1).
	 */
	@ApiStatus.OverrideOnly
	public void updateStandOffset(StandEntity standEntity) {}

	@ApiStatus.OverrideOnly
	@Nullable
	public Vec3 getStandLookTargetPosition(StandEntity standEntity, ActionTarget target) {
		return null;
	}
	
	
	@ApiStatus.OverrideOnly
	public void toBuf(FriendlyByteBuf buf) {}

	@ApiStatus.OverrideOnly
	public void fromBuf(FriendlyByteBuf buf) {}
	
	
	public List<StandEffectInstance> getPunchModifiers() {
		if (punchModifiers == null) {
			punchModifiers = new ArrayList<>(1);
		}
		return punchModifiers;
	}


	// Some helper methods to write less boilerplate in Stand abilities
	
	public void setStandOffset(double left, double front, StandOffsetFromUser.Rotations rotations, boolean armsOnlyMode) {
		if (performer instanceof StandEntity standEntity) {
			Vec3 relativeOffset = new Vec3(left, standEntity.Y_OFFSET, front);
			_setStandOffset(standEntity, relativeOffset, rotations, armsOnlyMode);
		}
	}
	
	public void setStandOffset(Vec3 relativeOffset, StandOffsetFromUser.Rotations rotations, boolean armsOnlyMode) {
		if (performer instanceof StandEntity standEntity) {
			_setStandOffset(standEntity, relativeOffset, rotations, armsOnlyMode);
		}
	}
	
	public void _setStandOffset(StandEntity standEntity, Vec3 relativeOffset, StandOffsetFromUser.Rotations rotations, boolean armsOnlyMode) {
		_setStandOffset(standEntity, relativeOffset, rotations, armsOnlyMode, true);
	}
	
	public void _setStandOffset(StandEntity standEntity, Vec3 relativeOffset, StandOffsetFromUser.Rotations rotations, boolean armsOnlyMode, boolean canInvertSide) {
		LivingEntity user = standEntity.getUser();
		if (user != null && standEntity.isArmsOnlyMode() == armsOnlyMode) {
			standEntity.offsetFromUser.setOffset(relativeOffset, rotations, canInvertSide);
			standEntity.offsetFromUser.standAbility = this.ability;
			standOffsetSync = relativeOffset;
			standOffsetSyncRotations = rotations;
			standOffsetSyncCanInvertSide = canInvertSide;
		}
	}

	// The offset this action sends to clients with itself, null when it leaves the Stand where it is.
	@Nullable
	public Vec3 getSyncedStandOffset() {
		return standOffsetSync;
	}

	public boolean setStandFrontOffsetFromAim(StandEntity standEntity, double minOffset, double maxOffset) {
		ActionTarget target = ActionTarget.EMPTY;
		var aim = LivingComponentAction.getAim(standEntity);
		if (aim != null) {
			target = aim.getTarget();
		}
		return setStandFrontOffsetFromTarget(standEntity, target, minOffset, maxOffset);
	}

	public boolean setStandFrontOffsetFromTarget(StandEntity standEntity, @Nullable ActionTarget target, double minOffset, double maxOffset) {
		if (standEntity.isArmsOnlyMode()) {
			return false;
		}
		LivingEntity user = standEntity.getUser();
		if (user == null) {
			return false;
		}
		Level level = standEntity.level();
		target = resolveFrontOffsetTarget(standEntity, user, level, target, maxOffset);

		double frontOffset = maxOffset;
		// 1.16 offsetToTarget measured to ActionTarget.getTargetPos(true), an entity's eyes or a block's centre, never to
		// where a ray met it
		Vec3 targetPos = target != null && !target.isEmpty(level) ? target.getCenterPos() : null;
		if (targetPos != null) {
			double backAway = 1.5;
			if (target.getType() == ActionTarget.TargetType.ENTITY) {
				Entity targetEntity = target.getMainEntity();
				if (targetEntity != null) {
					backAway = 1.0 + targetEntity.getBoundingBox().getXsize() / 2.0;
				}
			}
			double offsetToTarget = targetPos.subtract(user.position()).multiply(1, 0, 1).length() - backAway;
			frontOffset = Mth.clamp(offsetToTarget, minOffset, maxOffset);
		}
		_setStandOffset(standEntity, new Vec3(0, 0, frontOffset), StandOffsetFromUser.Rotations.HEAD_XY, false);
		return true;
	}

	private static ActionTarget resolveFrontOffsetTarget(StandEntity standEntity, LivingEntity user, Level level, 
			@Nullable ActionTarget target, double maxOffset) {
		if (target != null) {
			target = target.resolveEntityId(level);
			if (!target.isEmpty(level)) {
				return target;
			}
		}
		// 1.16 front3dOffset with no task target: rayTraceMultipleEntities from the user over maxOffset, boxes grown by 0.25
		return HitResultUtil.clip(user.getEyePosition(), user.getLookAngle(), maxOffset, maxOffset,
				level, entity -> standEntity.canAttackEntity(entity), user, 0, StandSkillWalls.wallsHideEntities(standEntity),
				0.25);
	}

	protected ActionTarget captureActionTargetFromAim(LivingEntity aimingEntity) {
		ActionTarget target = ActionTarget.EMPTY;
		var aim = LivingComponentAction.getAim(aimingEntity);
		if (aim != null) {
			target = aim.getTarget();
		}
		return setActionTargetSnapshot(takesPressedTarget(target) ? target : ActionTarget.EMPTY);
	}

	// a Stand action's press target passes 1.16's checkStandTarget (StandEntityAbility#takesPressedTarget); others any
	private boolean takesPressedTarget(@Nullable ActionTarget target) {
		if (target == null || !(ability instanceof StandEntityAbility standAbility) || !(performer instanceof StandEntity stand)) {
			return true;
		}
		return standAbility.takesPressedTarget(stand, target.copy().resolveEntityId(stand.level()));
	}

	protected ActionTarget setActionTargetSnapshot(@Nullable ActionTarget target) {
		ActionTarget copy = target != null ? target.copy() : null;
		actionTargetSnapshot = copy != null && copy.getType() != ActionTarget.TargetType.EMPTY ? copy : null;
		return actionTargetSnapshot != null ? actionTargetSnapshot : ActionTarget.EMPTY;
	}

	protected ActionTarget getActionTargetSnapshot(Level level) {
		if (actionTargetSnapshot == null) {
			return ActionTarget.EMPTY;
		}
		actionTargetSnapshot = actionTargetSnapshot.resolveEntityId(level);
		if (actionTargetSnapshot.isEmpty(level)) {
			actionTargetSnapshot = null;
			return ActionTarget.EMPTY;
		}
		return actionTargetSnapshot;
	}

	/**
	 * The target this action's task holds, as 1.16's StandEntityTask held one: what an action that keeps a target was
	 * pressed at, or the previous task's target it took over, while it keeps it ({@link StandEntityAbility#keepsTaskTarget}).
	 * An action that locks on it turns its offsets toward it ({@link StandEntityAbility#lockOnTargetPosition}). The server
	 * decides it; a client holds only what the server sent, with the action and in {@link #syncTargetsToTracking}, and
	 * never one of its own aim. Empty when it holds none or a client cannot find its entity.
	 */
	public ActionTarget getTaskTarget(Level level) {
		return heldTarget(taskTarget, level);
	}

	// resolved without forgetting it: a client may get the entity after the target
	protected static ActionTarget heldTarget(@Nullable ActionTarget target, Level level) {
		if (target == null) {
			return ActionTarget.EMPTY;
		}
		ActionTarget resolved = target.resolveEntityId(level);
		return resolved.isEmpty(level) ? ActionTarget.EMPTY : resolved;
	}

	/**
	 * Server: what this action was pressed at, which its task holds if it keeps it. The Stand turns to it
	 * ({@link #standRotationTarget}) as 1.16 turned a Stand to its task's target; an action that keeps it elsewhere
	 * returns that.
	 */
	@ApiStatus.OverrideOnly
	protected ActionTarget pressedTaskTarget() {
		return standRotationTarget != null ? standRotationTarget : ActionTarget.EMPTY;
	}

	/**
	 * Server: the previous action's task target became this action's (1.16 StandEntity.setTask), so the Stand turns to it
	 * as 1.16 turned a Stand to its task's target.
	 */
	@ApiStatus.OverrideOnly
	protected void onTaskTargetInherited(ActionTarget target) {
		if (standRotationTarget == null || standRotationTarget.getType() == ActionTarget.TargetType.EMPTY) {
			standRotationTarget = target;
		}
	}

	/*
	 * 1.16 StandEntityTask.makeServerSideTask and StandEntity.setTask: the press target if the action keeps it,
	 * otherwise the previous task's target if the action keeps that. Before the action is sent.
	 */
	private void initTaskTarget(@Nullable EntityActionInstance prevAction) {
		if (restoringInputLifecycle || performer == null || takesTargetsFromServer()
				|| !(ability instanceof StandEntityAbility standAbility) || !(performer instanceof StandEntity stand)) {
			return;
		}
		Level level = performer.level();
		ActionTarget pressed = pressedTaskTarget();
		ActionTarget kept = standAbility.keepsTaskTarget(stand, pressed) ? pressed.copy() : null;
		if (kept == null) {
			turnFromRefusedPressTarget(standAbility, stand, pressed);
		}
		if (kept == null && prevAction != null && prevAction.performer == performer) {
			ActionTarget previous = prevAction.getTaskTarget(level);
			if (standAbility.keepsTaskTarget(stand, previous)) {
				kept = previous.copy().resolveEntityId(level);
				onTaskTargetInherited(kept);
			}
		}
		taskTarget = kept;
	}

	/*
	 * 1.16 makeServerSideTask gave the task no target for a press target keepTarget refused (an entity past the action's
	 * range, a block past 10), so the Stand did not turn to it (StandEntityTask.rotateStand). Only for an action that keeps
	 * that kind of target: the others turn the Stand by their own rule.
	 */
	private void turnFromRefusedPressTarget(StandEntityAbility standAbility, StandEntity stand, ActionTarget pressed) {
		if (standRotationTarget != null && pressed.getType() != ActionTarget.TargetType.EMPTY
				&& pressed.sameTarget(standRotationTarget) && standAbility.keepsTaskTargetKind(stand, pressed)) {
			standRotationTarget = null;
		}
	}

	/**
	 * Server: 1.16 StandEntity.setTaskTarget (StandEntityTask.setTarget), which the action called while it ran and any
	 * server code on any Stand, another's too ({@link StandEntity#setTaskTarget}): the task holds the target if it keeps it
	 * ({@link #keepsAsTaskTarget}) and nothing for null or an empty target, else what it held. A Stand turned to the target
	 * the task held turns to the new one or, with none, along its user's look again. Tracking clients get a change, at the
	 * end of a perform start that sends its targets ({@link #sendsPerformStartTargets}), otherwise at once.
	 */
	public final void setTaskTarget(@Nullable ActionTarget target) {
		if (performer == null || takesTargetsFromServer() || !(ability instanceof StandEntityAbility)
				|| !(performer instanceof StandEntity stand)) {
			return;
		}
		ActionTarget kept = null;
		if (target != null && target.getType() != ActionTarget.TargetType.EMPTY) {
			kept = target.copy().resolveEntityId(performer.level());
			if (kept.isEmpty(performer.level()) || !keepsAsTaskTarget(stand, kept)) {
				return;
			}
		}
		ActionTarget held = storedTaskTarget();
		if (!sameTarget(held, kept)) {
			turnFromTaskTarget(held, kept);
			storeTaskTarget(kept);
			if (!(startingPerform && sendsPerformStartTargets())) {
				syncTargetsToTracking();
			}
		}
	}

	/**
	 * Server: whether this action's task keeps the target, as 1.16's StandEntityTask.keepTarget asked
	 * ({@link StandEntityAbility#keepsTaskTarget}); an action that keeps its task's target by a rule of its own gives that.
	 */
	@ApiStatus.OverrideOnly
	protected boolean keepsAsTaskTarget(StandEntity stand, ActionTarget target) {
		return ((StandEntityAbility) ability).keepsTaskTarget(stand, target);
	}

	/** The target this action's task holds as it keeps it, null for none; an action that keeps it elsewhere gives that. */
	@ApiStatus.OverrideOnly
	@Nullable
	protected ActionTarget storedTaskTarget() {
		return taskTarget;
	}

	/**
	 * Both sides: this action's task holds the target now, null for none: the server's {@link #setTaskTarget}, a client's
	 * copy what its server sent. An action that keeps it elsewhere keeps it there.
	 */
	@ApiStatus.OverrideOnly
	protected void storeTaskTarget(@Nullable ActionTarget target) {
		taskTarget = target;
	}

	// 1.16 StandEntityTask.tick: on every server tick, before the action ticks, a target the task no longer keeps is dropped
	private void releaseTaskTargetNoLongerKept() {
		if (taskTarget != null && !takesTargetsFromServer() && ability instanceof StandEntityAbility standAbility
				&& performer instanceof StandEntity stand && !standAbility.keepsTaskTarget(stand, taskTarget)) {
			turnFromTaskTarget(taskTarget, null);
			taskTarget = null;
			syncTargetsToTracking();
		}
	}

	/*
	 * 1.16 turned a Stand only to its task's target (StandEntityTask.rotateStand), so a Stand turned to the target its task
	 * no longer holds turns to the one it holds now, or with none along its user's look again (StandEntity.defaultRotation)
	 */
	private void turnFromTaskTarget(@Nullable ActionTarget held, @Nullable ActionTarget kept) {
		if (held != null && standRotationTarget != null && held.sameTarget(standRotationTarget)) {
			standRotationTarget = kept;
		}
	}

	/**
	 * Server: tells the clients tracking the performer the target this action keeps now, the one its Stand turns to, how
	 * the Stand aims and its task's target, after the server changed them while the action runs (1.16
	 * StandEntity.setTaskTarget sent TrStandTaskTargetPacket). Clients take them as they are ({@link #setTargetsFromServer}).
	 */
	protected void syncTargetsToTracking() {
		if (performer != null && !performer.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(performer, new TrEntityActionTargetPacket(
					performer.getId(), performer.getUUID(), networkGeneration, id,
					actionTargetSnapshot, standRotationTarget, aimAs, storedTaskTarget(), actionSlot));
		}
	}

	/** Client: the targets the server sent while this action runs ({@link #syncTargetsToTracking}). */
	@ApiStatus.Internal
	public void setTargetsFromServer(@Nullable ActionTarget keptTarget, @Nullable ActionTarget rotationTarget,
			AimingEntity aimAs, @Nullable ActionTarget taskTarget) {
		storeTaskTarget(taskTarget == null || taskTarget.getType() == ActionTarget.TargetType.EMPTY ? null : taskTarget);
		setActionTargetSnapshot(keptTarget);
		standRotationTarget = rotationTarget == null || rotationTarget.getType() == ActionTarget.TargetType.EMPTY ? null
				: rotationTarget.getType() == ActionTarget.TargetType.ENTITY ? rotationTarget.resolveEntityId(level())
				: rotationTarget;
		this.aimAs = aimAs;
	}

	/**
	 * Whether this copy of the action takes its targets from the server, as a client's does: it never aims or saves one
	 * itself and holds what the server last sent (1.16 StandEntity.punch aimed on the server only, and
	 * StandEntityTask.keepTarget passed everything on a client).
	 */
	protected boolean takesTargetsFromServer() {
		return level().isClientSide();
	}

	/**
	 * Whether the server sends tracking clients the targets this action changed as its perform started (1.16
	 * StandEntity.punch: setTaskTarget of the strike's target sent TrStandTaskTargetPacket when it changed).
	 */
	protected boolean sendsPerformStartTargets() {
		return false;
	}

	/**
	 * Server: the target a strike was dealt at becomes the action's target, kept and turned to, as 1.16's StandEntity.punch
	 * set it as the task target (setTaskTarget, StandEntityTask.setTarget/keepTarget): nothing when the strike met nothing,
	 * so the Stand turns along its user's look again (StandEntity.defaultRotation). A block or an entity only when the
	 * action keeps it ({@code keeps}: keepTarget, the 1.16 class's standKeepsTarget and checkStandTarget, which refused an
	 * entity that is not living); otherwise the targets stay as they were and nothing is sent. A strike that met no entity
	 * hands the aim back to the user's camera.
	 */
	@ApiStatus.Internal
	protected void takeStrikeTarget(ActionTarget struck, boolean keeps) {
		if (struck.getType() != ActionTarget.TargetType.EMPTY && !keeps) {
			refusedStrike = struck;
			rotationBeforeRefusedStrike = standRotationTarget;
			return;
		}
		refusedStrike = null;
		setActionTargetSnapshot(struck);
		standRotationTarget = struck.getType() != ActionTarget.TargetType.EMPTY ? struck : null;
		if (struck.getType() != ActionTarget.TargetType.ENTITY) {
			aimAs = AimingEntity.CAMERA_ENTITY;
		}
	}

	// An action's own line after its strike (King Crimson's KingCrimsonStrikeAction) turns the Stand to the entity it
	// struck; one the task refused stays refused, as 1.16 left the task target as it was.
	private void keepRefusedStrikeUnturned() {
		if (refusedStrike != null && standRotationTarget != null && refusedStrike.sameTarget(standRotationTarget)) {
			standRotationTarget = rotationBeforeRefusedStrike;
		}
		refusedStrike = null;
		rotationBeforeRefusedStrike = null;
	}

	/** Whether {@link #actionPerformStart} runs now: the strike of a punch, which sets the strike's target. */
	@ApiStatus.Internal
	protected final boolean isStartingPerform() {
		return startingPerform;
	}

	/** The targets as tracking clients hold them, to send them again only once they changed. */
	@ApiStatus.Internal
	protected record HeldTargets(@Nullable ActionTarget kept, @Nullable ActionTarget rotation, AimingEntity aimAs,
			@Nullable ActionTarget task) {}

	@ApiStatus.Internal
	protected HeldTargets heldTargets() {
		return new HeldTargets(actionTargetSnapshot, standRotationTarget, aimAs, storedTaskTarget());
	}

	/** Server: {@link #syncTargetsToTracking} if the targets are no longer the ones clients hold. */
	@ApiStatus.Internal
	protected void syncTargetsToTrackingIfChanged(HeldTargets held) {
		if (!sameTarget(held.kept(), actionTargetSnapshot) || !sameTarget(held.rotation(), standRotationTarget)
				|| held.aimAs() != aimAs || !sameTarget(held.task(), storedTaskTarget())) {
			syncTargetsToTracking();
		}
	}

	private static boolean sameTarget(@Nullable ActionTarget held, @Nullable ActionTarget now) {
		boolean heldNone = held == null || held.getType() == ActionTarget.TargetType.EMPTY;
		boolean nowNone = now == null || now.getType() == ActionTarget.TargetType.EMPTY;
		return heldNone || nowNone ? heldNone == nowNone : held.sameTarget(now);
	}
	
	// a punch's main target: a blocked hit wears the shield as in 1.16 StandEntityPunch.doHit
	public static boolean standEntityAttack(StandEntity stand, Entity target, DamageSource dmgSource, float dmgAmount) {
		return standEntityAttack(stand, target, dmgSource, dmgAmount, true);
	}

	// mainTarget false: 1.16 sweep targets (doAttack) and the heavy punch explosion (hurtTarget) wore no shield
	// and bypassed StandEntity.attackEntity, which records the last-hurt mob
	public static boolean standEntityAttack(StandEntity stand, Entity target, DamageSource dmgSource, float dmgAmount,
			boolean mainTarget) {
		ServerLevel level = (ServerLevel) target.level();
		// 1.16 StandEntity.attackEntity: a main target the Stand cannot harm is not hit, a grabbed one included
		boolean hurt = (!mainTarget || stand.canAttackEntity(target)) && stand.hurtWithStandAttack(target, dmgSource, dmgAmount);
		if (hurt && mainTarget && !stand.isManuallyControlled()) {
			stand.setLastHurtMob(target);
			LivingEntity standUser = stand.getUser();
			if (standUser != null) {
				standUser.setLastHurtMob(target);
			}
		}
		if (hurt) {
			if (target instanceof LivingEntity targetLiving) {
				LivingEntity user = stand.getUser();
				if (user != null) {
					LivingEntity aggroTo = stand.isFollowingUser() || targetLiving.hasLineOfSight(user) ? user : 
						StandUtil.isEntityStandUser(targetLiving) ? stand : null;
					if (aggroTo != null && aggroTo != dmgSource.getEntity()) {
						Brain<?> brain = targetLiving.getBrain();
						Optional<LivingEntity> brainAttackTarget = brain.getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
						if (brainAttackTarget != null && brainAttackTarget.filter(t -> t == dmgSource.getEntity()).isPresent()) {
							brain.setMemory(MemoryModuleType.ATTACK_TARGET, aggroTo);
						}
					}
				}
			}
            EnchantmentHelper.doPostAttackEffects(level, target, dmgSource);
		}
		else if (mainTarget) {
			wearBlockingShield(level, target, dmgAmount);
		}
		return hurt;
	}

	// 1.16 StandEntityPunch.doHit: a blocked Stand hit under 3 (vanilla wears none) still wears a player's shield
	private static void wearBlockingShield(ServerLevel level, Entity target, float dmgAmount) {
		if (dmgAmount < 3.0F && target instanceof Player player && player.isBlocking() && !player.hasInfiniteMaterials()) {
			ItemStack shield = player.getUseItem();
			if (shield.canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
				int wear = MathUtil.fractionRandomInc(dmgAmount * 0.5F);
				if (wear > 0) {
					InteractionHand hand = player.getUsedItemHand();
					shield.hurtAndBreak(wear, level, player, item -> {
						player.onEquippedItemBroken(item, LivingEntity.getSlotForHand(hand));
						EventHooks.onPlayerDestroyItem(player, shield, hand);
						player.stopUsingItem();
					});
				}
			}
		}
	}
	
	public DamageSource makePunchDamageSource() {
		var damageType = DamageUtil.type(performer.level(), ModDamageTypes.STAND_ATTACK);
		DamageSource dmgSource = new DamageSource(damageType, getPerformer());
		if (dmgSource instanceof DamageSourceModified modified) {
			modified.jojo_ripples$setStandPower(StandPower.get(getPowerUser()));
		}
		return dmgSource;
	}
	
	public void keepStandAimedAtTarget() {
		Level level = level();
		if (!level.isClientSide()) {
			var aim = LivingComponentAction.getAim(performer);
			keepStandAimedAtTarget(aim != null ? aim.getTarget() : ActionTarget.EMPTY);
		}
	}

	public void keepStandAimedAtTarget(@Nullable ActionTarget target) {
		Level level = level();
		if (!level.isClientSide()) {
			ActionTarget aimTarget = target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
			if (!aimTarget.isEmpty(level) && takesPressedTarget(aimTarget)) {
				standRotationTarget = aimTarget;
			}
		}
	}
	
	public final float calcFullTicks(ActionPhase targetPhase, float targetPhaseTick) {
		float sum = 0;
		for (ActionPhase phase : ActionPhase.values()) {
			float length = phasesLength.getFloat(phase);
			if (phase == targetPhase) {
				length = Math.min(length, targetPhaseTick);
			}
			sum += length;
			if (phase == targetPhase) break;
		}
		return sum;
	}
	
	public void startRecovery() {
		if (getPhase() != ActionPhase.RECOVERY) {
			setPhaseStart(ActionPhase.RECOVERY);
			syncPhaseChanges();
		}
	}
	
	/**
	 * A function to time the punch swing sounds a few ticks before the actual punch impact
	 */
	public final boolean soundTiming(ActionPhase targetPhase, float targetPhaseTick, int soundOffset) {
		float ticksPassed = getFullTicksPassed();
		int ticksDiff = (int) (ticksPassed - calcFullTicks(targetPhase, targetPhaseTick));
		return ticksDiff == soundOffset
				|| soundOffset < 0 && soundOffset < ticksDiff && (int) ticksPassed == 0
				/*|| soundOffset > 0 && ... */;
	}
	
	public float getActionTicksLeft() {
		if (this.isOver()) return 0;
		
		float sum = 0;
		for (ActionPhase phase : ActionPhase.values()) {
			if (phase.ordinal() == this.phase.ordinal()) {
				sum += phasesLength.getFloat(phase) - curPhaseLength;
			}
			else if (phase.ordinal() > this.phase.ordinal()) {
				sum += phasesLength.getFloat(phase);
			}
		}
		return sum;
	}
	
	public final boolean isUserCreative() {
		LivingEntity user = getPowerUser();
		return user instanceof Player player && player.getAbilities().instabuild;
	}
	
	public void tossStandHeldItems(EquipmentSlot... slots) {
		Level level = level();
		if (!level.isClientSide() && getPerformer() instanceof StandEntity stand) {
			LivingEntity user = powerUser.getEntityLiving(level);
			Vec3 tossVec = user != null ? user.position().subtract(stand.getEyePosition()) : stand.getLookAngle();
			for (EquipmentSlot slot : slots) {
				stand.tossItem(slot, tossVec);
			}
		}
	}
	
	public void addProjectileWithStandStats(DamagingEntity projectile) {
		Level level = level();
		if (!level.isClientSide() && !projectile.isAddedToLevel() && getPerformer() instanceof StandEntity stand) {
			projectile.setDamageFactor(projectile.getDamageFactor() * (float) stand.getAttackDamage() / 8);
			projectile.setSpeedFactor(projectile.getSpeedFactor() * stand.getAttackSpeed() / 8);
			projectile.withStandSkin(stand.getStandType(), stand.getStandSkin());
			level.addFreshEntity(projectile);
		}
	}

	protected boolean isGrabVariation() {
		return ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB;
	}
	
	public Level level() {
		return performer.level();
	}
	
	@ApiStatus.NonExtendable
	public float getPhaseTick() {
		return curPhaseTick + phasePartialTick;
	}

	@ApiStatus.NonExtendable
	public float getPhaseTicksLeft() {
		return curPhaseLength - getPhaseTick();
	}

	@ApiStatus.NonExtendable
	public float getCurPhaseLength() {
		return curPhaseLength;
	}

	@ApiStatus.NonExtendable
	public ActionPhase getPhase() {
		return phase;
	}

	@ApiStatus.NonExtendable
	public float getPhaseRatio() {
		if (curPhaseLength == 0) throw new IllegalStateException();
		return Math.min(getPhaseTick() / curPhaseLength, 1);
	}
	
	public float getFullTicksPassed() {
		return calcFullTicks(this.phase, this.getPhaseTick());
	}
	
	/* When a click and a hold ability share the same key, the code (LivingComponentAction#skipWindupTime(EntityActionInstance, float))
	 * adjusts for the time it took to distinguish between the two by skipping a little bit of the windup phase.
	 * For the sake of keeping animations smooth and abilities consistent, this is handled differently for actual action phases and animations.
	 * 
	 * For the actual phase timer, the real phase length is preserved, and the skipped time is added to the timer
	 *   (the windup phase will start at tick 4/20).
	 * In the animations, the skipped time is deducted from the phase length
	 *   (the windup animation will start at tick 0/16).
	 */

	ActionPhase prevFramePhase = null;
	float subtractFramePartialTick;
	@ApiStatus.NonExtendable
	public float getAnimPhaseTick(float partialTick) {
		// it just works
		if (prevFramePhase != this.phase) {
			if (prevFramePhase == null && this.phase != null)	subtractFramePartialTick = this.phasePartialTick;
			else												subtractFramePartialTick = 0;
			prevFramePhase = this.phase;
		}
		
		float phaseTick = curPhaseTick - subtractFramePartialTick;
		if (skippedWindupPhase != null) {
			phaseTick -= skippedWindupPhase.getOrDefault(this.phase, 0);
		}
		return phaseTick + partialTick;
	}
	
	@ApiStatus.NonExtendable
	public float getAnimPhaseLength() {
		float phaseLength = curPhaseLength;
		float lengthPartial = Mth.frac(phaseLength);
		// reflects the actual length (integer)
		if (lengthPartial > 0) phaseLength += 1 - lengthPartial;
		
		if (skippedWindupPhase != null) {
			phaseLength -= skippedWindupPhase.getOrDefault(this.phase, 0);
		}
		return phaseLength;
	}

	@ApiStatus.NonExtendable
	public float getAnimPhaseRatio(float partialTick) {
		if (curPhaseLength == 0) throw new IllegalStateException();
		float phaseTick = getAnimPhaseTick(partialTick);
		float phaseLength = getAnimPhaseLength();
		return phaseTick / phaseLength;
	}
	
	public float getAnimFullTicksPassed(float partialTick) {
		float ticks = getFullTicksPassed();
		if (skippedWindupPhase != null) {
			for (ActionPhase phase : ActionPhase.values()) {
				float skipped = skippedWindupPhase.getFloat(phase);
				ticks -= skipped;
				if (phase == getPhase()) break;
			}
		}
		return ticks + partialTick;
	}
	
	public void extractAnim(LivingAnimState animVariables, LivingEntity performer, float partialTick) {
		animVariables.animSet = ability.getEntityAnimSet(performer);
		animVariables.animId = getEntityAnim();
		animVariables.time = getAnimFullTicksPassed(partialTick);
		ActionPhase phase = getPhase();
		// Legacy stand animation timelines use WINDUP for hold-to-fire charge poses.
		animVariables.actionPhase = phase == ActionPhase.BUTTON_CHARGE ? ActionPhase.WINDUP : phase;
		animVariables.phaseTime = getAnimPhaseTick(partialTick);
		// A phase held at its end keeps its tick at the phase length, so the ratio would run past 1 by the partial tick
		// and the timeline would lerp into the next phase's keyframes every frame (a twitching fully charged pose).
		animVariables.phaseCompletion = Math.min(getAnimPhaseRatio(partialTick), 1.0F);
	}

	public ActionAnimIdentifier getEntityAnim() {
		return ability.getEntityAnim(this);
	}
	
	public boolean savePrevPoseForAnimTransition(EntityActionInstance prevAction) {
		return false;
	}
	

	@ApiStatus.NonExtendable
	public void forceStop() {
		setPhaseStart(null);
	}

	@ApiStatus.NonExtendable
	public boolean isOver() {
		return phase == null;
	}
	
	
	/** The slot of its performer this action is set in (LivingComponentAction#slotId). */
	@ApiStatus.Internal
	public int actionSlot() {
		return actionSlot;
	}

	/**
	 * Who performs this action: the entity whose slot holds it, except for a port of a 1.16 StandAction in its user's
	 * Stand-power slot (EntityActionAbility#performsInUsersStandPowerSlot), which the user's summoned Stand performs while
	 * it is out, as 1.16 StandAction.getPerformer: the press check and the held recheck meet the Stand's stun.
	 */
	public LivingEntity getPerformer() {
		if (performer != null && actionSlot == LivingComponentAction.STAND_POWER_SLOT
				&& ability instanceof EntityActionAbility entityAbility && entityAbility.performsInUsersStandPowerSlot()) {
			StandEntity stand = StandUtil.getSummonedStand(performer);
			if (stand != null) {
				return stand;
			}
		}
		return performer;
	}
	
	public LivingEntity getPowerUser() {
		if (performer != null) {
			return powerUser.getEntityLiving(performer.level());
		}
		return null;
	}
	
	
	

	// TODO (!) (entity action) test partial tick for consecutive actions
	@ApiStatus.Internal
	public void setPartialTick(float partialTick) {
		if (partialTick >= 1) throw new IllegalArgumentException();
		this.phasePartialTick = partialTick;
	}
	
	public void setPhaseStart(ActionPhase phase) {
		setPhase(phase, 0);
	}

	public void setPhase(ActionPhase phase, int tick) {
		if (phase == null) {
			this.phase = null;
			takeHeldCooldownIfHoldEnded();
			return;
		}

		float prevPhaseTick = getPhaseTick();
		float prevTickLength = this.curPhaseLength;

		boolean changed = this.phase != phase;
		if (this.performer != null && changed) {
			applyStandUserWalkSpeed(phase);
			onSetPhase(phase);
		}
		this.phase = phase;
		this.curPhaseTick = tick;
		this.curPhaseLength = phase != null ? phasesLength.getFloat(phase) : -1;

		this.phasePartialTick = Mth.clamp(prevPhaseTick - prevTickLength, 0, 0.9999f);

		if (changed && curPhaseLength > 0) {
			ActionPhase prevTaskPhase = taskPhase;
			taskPhase = phase;
			if (this.performer != null) {
				transitionModifiers(prevTaskPhase, phase);
			}
		}
		takeHeldCooldownIfHoldEnded();
		checkNextPhase();
	}
	
	// XXX only sync actual phase changes
	public void syncPhaseChanges() {
		if (performer != null && !performer.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(performer, new TrEntityActionPhaseTimePacket(performer.getId(), 
					performer.getUUID(), networkGeneration, id,
					phasesLength, phase, curPhaseTick, actionSlot));
		}
	}


	@ApiStatus.Internal
	public void _onActionStarted(@Nullable EntityActionInstance prevAction) {
		applyStandUserWalkSpeed(phase);
		onActionSet(prevAction);
		onSetPhase(phase);
		initTaskTarget(prevAction);
		applySyncedStandOffset();
	}

	// 1.16 StandEntityAction.getUserWalkSpeed: a Stand's action sets its user's walk speed from its registration in every
	// phase but RECOVERY (owner rule 2026-10-15: 0.5 for the guard, 1.0 otherwise). Set before the action's own
	// callbacks, which may change it.
	private void applyStandUserWalkSpeed(@Nullable ActionPhase phase) {
		if (phase != null && ability instanceof StandEntityAbility standAbility) {
			userWalkSpeed = phase == ActionPhase.RECOVERY ? 1.0F : standAbility.getStandUserWalkSpeed();
		}
	}

	private void applySyncedStandOffset() {
		if (performer != null && performer.level().isClientSide()
				&& standOffsetSync != null && standOffsetSyncRotations != null
				&& performer instanceof StandEntity standEntity) {
			standEntity.offsetFromUser.setOffset(standOffsetSync, standOffsetSyncRotations, standOffsetSyncCanInvertSide);
			standEntity.offsetFromUser.standAbility = this.ability;
		}
	}

	@ApiStatus.Internal
	public void _beforeActionRemoved(@Nullable EntityActionInstance newAction) {
		onActionCleared(newAction);
		this.phase = null;
		takeHeldCooldownIfHoldEnded();
	}

	@ApiStatus.Internal
	public void _tickAction() {
		if (!isOver()) {
			if (heldCooldownTicks != NO_HELD_COOLDOWN) {
				++heldCooldownTicks;
			}
			if (!level().isClientSide() && !ability.canContinueAction(this)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			if (_stopHeldIfConditionsFail() && isOver()) {
				return;
			}
			leaveFiringChargeOnItsLastTick();
			if (isOver()) {
				return;
			}
			_onTick();
			noteOwnCooldownSets();
			_incPhaseTick();
			checkNextPhase();
			noteOwnCooldownSets();
			takeHeldCooldownIfHoldEnded();
		}
	}

	/**
	 * A tick in which its new Stand's summon lock holds the action back (LivingComponentAction). 1.16 held back only the
	 * Stand's task then (StandEntity.tick): its user's power still counted a held action from the press, with the held
	 * recheck (PowerBaseImpl.tickHeldAction): a charge fired at its length, a hold ended at its getHoldDurationMax, and
	 * the ticks held went to its cooldown (stopHeldAction). So a held action counts this tick as {@link #_tickAction}
	 * does, without the action's own tick: a held charge whose next phase is not held counts it in its phase and at its
	 * length goes on to its next phase, which then waits for the lock; any other hold counts it in
	 * {@link #getTicksHeldInSummonLock}.
	 * @return whether the action was held and counted the tick
	 */
	@ApiStatus.Internal
	public boolean _tickHeldInSummonLock() {
		if (isOver() || !(ability instanceof EntityActionAbility entityAbility) || !entityAbility.isHeldSincePress(this)) {
			return false;
		}
		boolean charge = isHeldFiringCharge();
		if (heldCooldownTicks != NO_HELD_COOLDOWN) {
			++heldCooldownTicks;
		}
		if (!level().isClientSide() && !ability.canContinueAction(this)) {
			forceStop();
			syncPhaseChanges();
			return true;
		}
		if (_stopHeldIfConditionsFail() && (isOver() || !entityAbility.isHeldSincePress(this))) {
			takeHeldCooldownIfHoldEnded();
			return true;
		}
		if (!charge) {
			++ticksHeldInSummonLock;
			return true;
		}
		leaveFiringChargeOnItsLastTick();
		if (!isHeldFiringCharge()) {
			return true;
		}
		noteOwnCooldownSets();
		_incPhaseTick();
		checkNextPhase();
		noteOwnCooldownSets();
		takeHeldCooldownIfHoldEnded();
		return true;
	}

	/**
	 * The ticks this action was held from its press while its new Stand's summon lock held it back
	 * ({@link #_tickHeldInSummonLock}), which its own phase ticks do not count: 1.16's power counted them toward its hold
	 * (getHoldDurationMax), so a held action with a hold maximum adds them to its own ticks (the barrage). 0 on a client's
	 * copy that did not see them, and for a held charge, which counts them in its phase.
	 */
	public int getTicksHeldInSummonLock() {
		return ticksHeldInSummonLock;
	}

	// a held phase before the perform (1.16 BUTTON_HOLD) whose next phase is not held: a hold-to-fire charge
	private boolean isHeldFiringCharge() {
		return !isOver() && curPhaseLength > 0 && phase.ordinal() < ActionPhase.PERFORM.ordinal()
				&& ability instanceof EntityActionAbility entityAbility && entityAbility.isHeldSincePress(this)
				&& firesAfterHeldPhase(entityAbility);
	}

	private static final int NO_HELD_COOLDOWN = -1;

	/**
	 * The press left its ability's cooldown to this action's hold (EntityActionAbility#setsCooldownAfterHold), as 1.16
	 * set it: an action whose perform is not held (a charge, 1.16 hold-to-fire) takes it as it fires, leaving its held
	 * phases for one that is not the recovery (PowerBaseImpl.performAction), and none if it ends unfired; any other (1.16
	 * hold-only) takes it with the ticks it was held as its hold ends (stopHeldAction): the key is let go, a failed check
	 * or a hit ends it, its held phases run out or the action ends. Called by AbilityInput on the server.
	 */
	@ApiStatus.Internal
	public void takeCooldownAfterHold() {
		if (!level().isClientSide() && ability instanceof EntityActionAbility entityAbility) {
			heldCooldownTicks = 0;
			heldCooldownOnFire = !isHeldInPerform(entityAbility);
		}
	}

	/** Whether the ability's cooldown is still waiting for this action's hold ({@link #takeCooldownAfterHold}). */
	public boolean isCooldownTakenAfterHold() {
		return heldCooldownTicks != NO_HELD_COOLDOWN;
	}

	// whether the perform is still inside the hold, asked without entering it (onSetPhase does not run)
	private boolean isHeldInPerform(EntityActionAbility entityAbility) {
		ActionPhase current = phase;
		phase = ActionPhase.PERFORM;
		try {
			return entityAbility.isHeldSincePress(this);
		}
		finally {
			phase = current;
		}
	}

	private void takeHeldCooldownIfHoldEnded() {
		// a charge let go before it was full and charging on (the charged heavy punch, the grab throw) has not fired yet
		if (heldCooldownTicks == NO_HELD_COOLDOWN || !(ability instanceof EntityActionAbility entityAbility)
				|| phase != null && entityAbility.isHeldSincePress(this)
				|| phase == ActionPhase.BUTTON_CHARGE && heldCooldownOnFire) {
			return;
		}
		boolean fired = phase != null && phase != ActionPhase.RECOVERY;
		int ticksHeld = heldCooldownTicks;
		heldCooldownTicks = NO_HELD_COOLDOWN;
		if (fired || !heldCooldownOnFire) {
			takeHeldCooldown(entityAbility, ticksHeld);
		}
	}

	// 1.16 stopHeldAction: letting go of the key ended a hold-only action's hold, whatever its release handling left running
	private void takeHoldOnlyCooldownOnRelease() {
		if (heldCooldownTicks != NO_HELD_COOLDOWN && !heldCooldownOnFire && ability instanceof EntityActionAbility entityAbility) {
			int ticksHeld = heldCooldownTicks;
			heldCooldownTicks = NO_HELD_COOLDOWN;
			takeHeldCooldown(entityAbility, ticksHeld);
		}
	}

	private void takeHeldCooldown(EntityActionAbility entityAbility, int ticksHeld) {
		LivingEntity user = getPowerUser();
		Power<?> power = user != null && entityAbility.abilityId.powerClass() != null ? entityAbility.getUserPower(user) : null;
		if (power == null) {
			return;
		}
		if (heldCooldownOnFire) {
			entityAbility.setCooldownOnUse(power);
		}
		else {
			entityAbility.setCooldownOnUse(power, ticksHeld);
		}
		if (!isOver()) {
			entityAbility.noteOwnCooldownSets(this);
		}
	}

	/** {@link #ownCooldownSets()} before any {@link EntityActionAbility} held recheck saw this action. */
	@ApiStatus.Internal
	public static final int OWN_COOLDOWN_UNSEEN = Integer.MIN_VALUE;

	/**
	 * The ability's cooldown sets this action counts as its own, as EntityActionAbility#getHeldCooldownSets gives them:
	 * those of its press and those its own tick made, which the held cooldown recheck does not end the hold on.
	 */
	@ApiStatus.Internal
	public int ownCooldownSets() {
		return ownCooldownSets;
	}

	@ApiStatus.Internal
	public void setOwnCooldownSets(int sets) {
		ownCooldownSets = sets;
	}

	// a cooldown the action set in its own tick is its own, as one set at its press is
	private void noteOwnCooldownSets() {
		if (!isOver() && ability instanceof EntityActionAbility entityAbility) {
			entityAbility.noteOwnCooldownSets(this);
		}
	}
	
	/**
	 * 1.16 PowerBaseImpl.tickHeldAction: a held action's requirements were checked again on every tick,
	 * and a failed check ended the hold. The tick a charge fires in as its length ends ({@link #isChargeFiringTick}) it
	 * gave to stopHeldAction(true) instead, whose check fired nothing on a failure and said nothing. Server side.
	 * @return whether a failed check stopped the hold
	 */
	@ApiStatus.Internal
	public boolean _stopHeldIfConditionsFail() {
		if (isChargeFiringTick()) {
			return stopFullChargeIfConditionsFail();
		}
		return !isOver() && !level().isClientSide() && ability instanceof EntityActionAbility entityAbility
				&& entityAbility.stopHeldActionIfConditionsFail(this);
	}

	@ApiStatus.Internal
	protected void _incPhaseTick() {
		if (shouldHoldPhaseAtEnd() && getPhaseTick() + 1 >= curPhaseLength) {
			curPhaseTick = Mth.floor(curPhaseLength);
			phasePartialTick = Mth.frac(curPhaseLength);
		}
		else {
			++curPhaseTick;
		}
	}

	protected boolean shouldHoldPhaseAtEnd() {
		return false;
	}
	
	@ApiStatus.Internal
	protected void _onTick() {
		// a Stand's body takes its yaw in its own head turn (StandEntity.tickHeadTurn)
		LivingEntity acting = getPerformer();
		if (alignsBodyYawToLook() && !(acting instanceof StandEntity)) {
			acting.yBodyRot = acting.getYRot();
		}
		releaseTaskTargetNoLongerKept();
		actionTick();
		if (phase == ActionPhase.PERFORM) {
			if (getPhaseTick() < 1) {
				HeldTargets held = sendsPerformStartTargets() && !takesTargetsFromServer() ? heldTargets() : null;
				refusedStrike = null;
				startingPerform = true;
				try {
					actionPerformStart();
				}
				finally {
					startingPerform = false;
				}
				keepRefusedStrikeUnturned();
				if (held != null && !isOver()) {
					syncTargetsToTrackingIfChanged(held);
				}
				resetAttackStrengthOnPerform();
			}
			if (getPhaseTick() + 1 >= curPhaseLength) {
				actionPerformEnd();
			}
		}
		tickModifiers();
	}

	/*
	 * 1.16 StandEntityTask.tick: the task's modifier actions ticked right after its own action, also in a tick where that
	 * action stopped the task, in the phase the stopped task kept: an action over by now (only one that ended in this
	 * tick runs it) is seen in the phase it ended in while its modifiers tick.
	 */
	private void tickModifiers() {
		if (punchModifiers == null || punchModifiers.isEmpty() || isOver() && taskPhase == null) {
			return;
		}
		boolean ended = isOver();
		if (ended) {
			phase = taskPhase;
		}
		try {
			for (StandEffectInstance modifier : List.copyOf(punchModifiers)) {
				if (!modifier.isStopped()) {
					modifier.onModifiedActionTick(this);
				}
			}
		}
		finally {
			if (ended) {
				phase = null;
			}
		}
	}

	// 1.16 StandEntityTask.phaseTransition: each modifier's after the action's own
	private void transitionModifiers(@Nullable ActionPhase prevPhase, @Nullable ActionPhase nextPhase) {
		if (punchModifiers != null && !punchModifiers.isEmpty()) {
			for (StandEffectInstance modifier : List.copyOf(punchModifiers)) {
				if (!modifier.isStopped()) {
					modifier.onModifiedActionPhaseTransition(this, prevPhase, nextPhase);
				}
			}
		}
	}

	/**
	 * Whether the action tick turns the performer's body to its look yaw before the action runs.
	 * An action whose 1.16 logic reads the body yaw the vanilla tick left opts out.
	 */
	protected boolean alignsBodyYawToLook() {
		return true;
	}

	// 1.16 Action.onPerform: an action that swings the hand without the user's own punch resets a player's attack strength.
	private void resetAttackStrengthOnPerform() {
		if (ability instanceof EntityActionAbility entityAbility && entityAbility.resetsAttackStrengthOnPerform()
				&& getPowerUser() instanceof Player player) {
			player.resetAttackStrengthTicker();
		}
	}
	
	@ApiStatus.Internal
	protected void checkNextPhase() {
		if (!isOver() && getPhaseTick() >= curPhaseLength && !shouldHoldPhaseAtEnd()) {
			// tick a skipped non-zero phase
			if (curPhaseTick == 0 && curPhaseLength > 0 && curPhaseLength <= 1) {
				_onTick();
			}
			if (stopFullChargeIfConditionsFail()) {
				return;
			}

			int ordinal = phase.ordinal() + 1;
			ActionPhase nextPhase = ordinal < ActionPhase.values().length ? ActionPhase.values()[ordinal] : null;
			if (nextPhase == null && performer != null) {
				// 1.16 moveToPhase(null) told the modifiers before it stopped the task; a stopped task told them nothing
				transitionModifiers(taskPhase, null);
			}
			setPhaseStart(nextPhase);
		}
	}

	/*
	 * 1.16 PowerBaseImpl.tickHeldAction counted a held tick and, as the count reached getHoldDurationMax, fired the hold in
	 * that tick instead of running its holdTick: a holdToFire(N, false) charge fired in the N-th tick from the press, and a
	 * release before that tick fired nothing. An action its user performs takes effect in that tick: its held charge of N
	 * ticks that fires as it ends (EntityActionAbility#chargeFiresAsItsLengthEnds) leaves it at the start of its N-th tick,
	 * before that tick runs, which then runs the next phase's first tick. A charge the Stand's task performs runs its N
	 * ticks and goes on to its next phase in checkNextPhase. The tick's check ran just before (_stopHeldIfConditionsFail):
	 * the full-charge check, or the held recheck where the hold goes on past the charge.
	 */
	private void leaveFiringChargeOnItsLastTick() {
		if (!isOnFiringChargesLastTick() || !((EntityActionAbility) ability).chargeFiresAsItsLengthEnds(this)) {
			return;
		}
		setPhaseStart(ActionPhase.values()[phase.ordinal() + 1]);
	}

	/**
	 * Whether this tick, not yet run, is the one a held charge fires in as its length ends and its hold ends with it
	 * (EntityActionAbility#chargeEndsTheHold): 1.16 PowerBaseImpl.tickHeldAction gave that tick to stopHeldAction(true),
	 * whose check, unlike a held tick's, fired nothing on a failure and said nothing. That is the charge's last tick,
	 * whether its next phase runs in it or in the next tick (EntityActionAbility#chargeFiresAsItsLengthEnds). An ability's
	 * own held checks with a message stay silent in it.
	 */
	@ApiStatus.NonExtendable
	public boolean isChargeFiringTick() {
		return isOnFiringChargesLastTick() && ((EntityActionAbility) ability).chargeEndsTheHold(this);
	}

	// the last tick of a held charge (a phase before the perform) whose next phase is not held, not yet run
	private boolean isOnFiringChargesLastTick() {
		return !isOver() && getPhaseTick() >= curPhaseLength - 1 && !shouldHoldPhaseAtEnd() && curPhaseLength >= 1
				&& phase.ordinal() < ActionPhase.PERFORM.ordinal()
				&& ability instanceof EntityActionAbility entityAbility && entityAbility.isHeldSincePress(this)
				&& firesAfterHeldPhase(entityAbility);
	}

	/*
	 * 1.16 stopHeldAction(true) as a hold reached its length: a held charge (a phase before the perform) that fires as it
	 * ends is checked once more before it leaves the charge, so a failed check leaves no trace of the next phase. Server.
	 */
	private boolean stopFullChargeIfConditionsFail() {
		return !isOver() && performer != null && !level().isClientSide() && curPhaseLength > 0
				&& phase.ordinal() < ActionPhase.PERFORM.ordinal()
				&& ability instanceof EntityActionAbility entityAbility && entityAbility.isHeldSincePress(this)
				&& firesAfterHeldPhase(entityAbility) && entityAbility.stopFullChargeIfConditionsFail(this);
	}

	/**
	 * Whether a phase with a length that the ability holds ({@link EntityActionAbility#isActionHeld}) comes after the
	 * current one and none came before it: 1.16 held an action from its press, so the phases before its first held one
	 * are held too. Each other phase is asked without entering it, as {@link #firesAfterHeldPhase} asks.
	 */
	@ApiStatus.Internal
	public boolean isBeforeFirstHeldPhase(EntityActionAbility entityAbility) {
		ActionPhase current = phase;
		if (current == null) {
			return false;
		}
		try {
			for (ActionPhase other : ActionPhase.values()) {
				if (other != current && phasesLength.getFloat(other) > 0) {
					phase = other;
					if (entityAbility.isActionHeld(this)) {
						return other.ordinal() > current.ordinal();
					}
				}
			}
			return false;
		}
		finally {
			phase = current;
		}
	}

	// whether the phase the action goes on to is no longer held, asked without entering it (onSetPhase does not run)
	private boolean firesAfterHeldPhase(EntityActionAbility entityAbility) {
		ActionPhase next = null;
		for (int i = phase.ordinal() + 1; i < ActionPhase.values().length && next == null; i++) {
			if (phasesLength.getFloat(ActionPhase.values()[i]) > 0) {
				next = ActionPhase.values()[i];
			}
		}
		if (next == null) {
			return false;
		}
		ActionPhase held = phase;
		phase = next;
		try {
			return !entityAbility.isHeldSincePress(this);
		}
		finally {
			phase = held;
		}
	}

	
	@Override
	@ApiStatus.Internal
	public void onKeyRelease(LivingEntity user) {
		if (!this.isOver() && !ignoresClickRelease()) {
			onButtonStopHold();
			takeHoldOnlyCooldownOnRelease();
		}
	}
	
	/**
	 * Set by {@link EntityActionAbility#setOrBufferAction} when a CLICK input started this action.
	 */
	@ApiStatus.Internal
	public void setStartedByClick(boolean startedByClick) {
		this.startedByClick = startedByClick;
	}
	
	public boolean isStartedByClick() {
		return startedByClick;
	}
	
	// 1.16 PowerBaseImpl.onClickAction performed an action without a hold on the click, and the key's release only
	// reached a held action (InputHandler.stopHeldAction), so a click could not be cancelled by letting go of the key.
	// An ambiguous click/hold key sends its release in the same frame as the click, before the action's first tick.
	private boolean ignoresClickRelease() {
		return startedByClick && ability instanceof EntityActionAbility entityAbility
				&& !entityAbility.keyReleaseReachesClickAction(this);
	}
	
	
	/** Retained for source and binary compatibility with existing addon callers. */
	public static void encode(
			RegistryFriendlyByteBuf buffer,
			EntityActionInstance action) {
		encode((FriendlyByteBuf) buffer, action);
	}

	public static void encode(FriendlyByteBuf buffer, EntityActionInstance action) {
		ActionPhase actionPhase = action != null ? action.phase : null;
		boolean valid = action != null && !action.isOver();
		buffer.writeBoolean(valid);
		if (valid) {
			action.ability.encodeAbility(action.getPowerUser(), buffer);

			buffer.writeVarInt(action.id);
			for (ActionPhase phase : ActionPhase.values()) {
				buffer.writeFloat(action.phasesLength.getFloat(phase));
			}
			NetworkUtil.writeOptionally(action.skippedWindupPhase, buffer, (buf, map) -> {
				buf.writeVarInt(map.size());
				for (var entry : map.object2FloatEntrySet()) {
					buf.writeEnum(entry.getKey());
					buf.writeFloat(entry.getFloatValue());
				}
			});
			buffer.writeVarInt(actionPhase.ordinal());
			buffer.writeVarInt(action.curPhaseTick);
			buffer.writeFloat(action.phasePartialTick);
			buffer.writeFloat(action.curPhaseLength);
			action.powerUser.writeNetwork(buffer);
			NetworkUtil.writeOptionally(action.standRotationTarget, buffer, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID);
			NetworkUtil.writeOptionally(action.actionTargetSnapshot, buffer, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID);
			NetworkUtil.writeOptionally(action.standOffsetSync, buffer, _Vec3.STREAM_CODEC);
			if (action.standOffsetSync != null) {
				buffer.writeEnum(action.standOffsetSyncRotations);
				buffer.writeBoolean(action.standOffsetSyncCanInvertSide);
			}
			NetworkUtil.writeOptionally(action.taskTarget, buffer, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID);
			action.toBuf(buffer);
		}
	}

	public static EntityActionInstance decode(Level level, FriendlyByteBuf buffer) {
		boolean valid = buffer.readBoolean();
		if (valid) {
			EntityActionInstance action = EntityActionType.decodeAbilityAction(level, buffer);
			if (action != null) {
				action.id = buffer.readVarInt();
				for (ActionPhase phase : ActionPhase.values()) {
					action.phasesLength.put(phase, buffer.readFloat());
				}
				action.skippedWindupPhase = NetworkUtil.readOptional(buffer, (buf) -> {
					Object2FloatArrayMap<ActionPhase> map = new Object2FloatArrayMap<>();
					int size = NetworkPayloadValidation.requireCollectionSize(
							buf.readVarInt(), ActionPhase.values().length,
							"skipped action phase");
					for (int i = 0; i < size; i++) {
						map.put(buf.readEnum(ActionPhase.class), buf.readFloat());
					}
					return map;
				}).orElse(null);
				action.phase = buffer.readEnum(ActionPhase.class);
				action.curPhaseTick = buffer.readVarInt();
				action.phasePartialTick = buffer.readFloat();
				action.curPhaseLength = buffer.readFloat();
				action.taskPhase = action.curPhaseLength > 0 ? action.phase : null;
				action.powerUser.readNetwork(buffer);
				action.standRotationTarget = NetworkUtil.readOptional(buffer, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID).orElse(null);
				action.actionTargetSnapshot = NetworkUtil.readOptional(buffer, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID).orElse(null);
				action.standOffsetSync = NetworkUtil.readOptional(buffer, _Vec3.STREAM_CODEC).orElse(null);
				if (action.standOffsetSync != null) {
					action.standOffsetSyncRotations = buffer.readEnum(StandOffsetFromUser.Rotations.class);
					action.standOffsetSyncCanInvertSide = buffer.readBoolean();
				}
				action.taskTarget = NetworkUtil.readOptional(buffer, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID).orElse(null);
				action.fromBuf(buffer);
				return action;
			}
		}
		
		return null;
	}
	
}
