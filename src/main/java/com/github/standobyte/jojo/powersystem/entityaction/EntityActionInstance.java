package com.github.standobyte.jojo.powersystem.entityaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.customobjects.entity_projectile.DamagingEntity;
import com.github.standobyte.jojo.entityattachment.syncheddata.SynchedDataHelper;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.ability.AbilityUsageGroup;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.subsystems.target.HitResultUtil;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;
import com.github.standobyte.jojo.util.objects_mc.EntityResolver;
import com.github.standobyte.v1_21_4_stuff.missingmethods._Vec3;

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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// TODO (entity action) test the phase lengths stuff (with partial lengths and lengths < 1)
public class EntityActionInstance implements HeldInput {
	/** Is used in network code, to make sure server and client are on the same page when sending changes to the action's phases from server */
	@ApiStatus.Internal public int id;
	@Nonnull public final EntityActionType ability;
	@ApiStatus.Internal public Object2FloatMap<ActionPhase> phasesLength = new Object2FloatArrayMap<>();
	@ApiStatus.Internal @Nullable public Object2FloatMap<ActionPhase> skippedWindupPhase = null;
	
	public SynchedDataHelper synchedData = new SynchedDataHelper(this, () -> this.level().isClientSide());
	
	@Nonnull protected ActionPhase phase;
	protected int curPhaseTick;
	protected float curPhaseLength;
	protected float phasePartialTick;
	protected boolean stoppedHolding = false;
	
	protected LivingEntity performer;
	protected EntityResolver powerUser = new EntityResolver();
	
	@Nullable public ActionTarget standRotationTarget;
	public AimingEntity aimAs = AimingEntity.CAMERA_ENTITY;
	@Nullable protected ActionTarget actionTargetSnapshot;
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
					if (LivingComponentAction.getCurEntityAction(performer) != null) {
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
	
	@ApiStatus.OverrideOnly
	public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
		return false;
	}

	@ApiStatus.OverrideOnly
	public boolean canStaminaRegen(StandPower standPower, StandEntity standEntity) {
		return false;
	}

	@ApiStatus.OverrideOnly
	public void applyStandUserRotation(StandEntity standEntity, LivingEntity user) {}

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
		Vec3 targetPos = null;
		if (target != null && !target.isEmpty(level)) {
			targetPos = target.getClipPos().orElse(null);
			if (targetPos == null) {
				targetPos = target.getCenterPos();
			}
		}
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
		return HitResultUtil.clip(user.getEyePosition(), user.getLookAngle(), maxOffset, maxOffset,
				level, entity -> standEntity.canAttackEntity(entity), user, 0);
	}

	protected ActionTarget captureActionTargetFromAim(LivingEntity aimingEntity) {
		ActionTarget target = ActionTarget.EMPTY;
		var aim = LivingComponentAction.getAim(aimingEntity);
		if (aim != null) {
			target = aim.getTarget();
		}
		return setActionTargetSnapshot(target);
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
	
	public static boolean standEntityAttack(StandEntity stand, Entity target, DamageSource dmgSource, float dmgAmount) {
		ServerLevel level = (ServerLevel) target.level();
		boolean hurt = stand.hurtWithStandAttack(target, dmgSource, dmgAmount);
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
		return hurt;
	}
	
	public DamageSource makePunchDamageSource() {
		var damageType = DamageUtil.type(performer.level(), ModDamageTypes.STAND_ATTACK);
		DamageSource dmgSource = new DamageSource(damageType, performer);
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
			if (!aimTarget.isEmpty(level)) {
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
		if (!level.isClientSide() && performer instanceof StandEntity stand) {
			LivingEntity user = powerUser.getEntityLiving(level);
			Vec3 tossVec = user != null ? user.position().subtract(stand.getEyePosition()) : stand.getLookAngle();
			for (EquipmentSlot slot : slots) {
				stand.tossItem(slot, tossVec);
			}
		}
	}
	
	public void addProjectileWithStandStats(DamagingEntity projectile) {
		Level level = level();
		if (!level.isClientSide() && !projectile.isAddedToLevel() && performer instanceof StandEntity stand) {
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
		animVariables.phaseCompletion = getAnimPhaseRatio(partialTick);
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
	
	
	public LivingEntity getPerformer() {
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
			return;
		}
		
		float prevPhaseTick = getPhaseTick();
		float prevTickLength = this.curPhaseLength;
		
		if (this.performer != null && this.phase != phase) {
			onSetPhase(phase);
		}
		this.phase = phase;
		this.curPhaseTick = tick;
		this.curPhaseLength = phase != null ? phasesLength.getFloat(phase) : -1;
		
		this.phasePartialTick = Mth.clamp(prevPhaseTick - prevTickLength, 0, 0.9999f);
		
		checkNextPhase();
	}
	
	// XXX only sync actual phase changes
	public void syncPhaseChanges() {
		if (performer != null && !performer.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(performer, new TrEntityActionPhaseTimePacket(performer.getId(), 
					id, phasesLength, phase, curPhaseTick));
		}
	}


	@ApiStatus.Internal
	public void _onActionStarted(@Nullable EntityActionInstance prevAction) {
		onActionSet(prevAction);
		onSetPhase(phase);
		applySyncedStandOffset();
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
	}
	
	@ApiStatus.Internal
	public void _tickAction() {
		if (!isOver()) {
			if (!level().isClientSide() && !ability.canContinueAction(this)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			_onTick();
			_incPhaseTick();
			checkNextPhase();
		}
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
		performer.yBodyRot = performer.getYRot();
		actionTick();
		if (phase == ActionPhase.PERFORM) {
			if (getPhaseTick() < 1) {
				actionPerformStart();
			}
			if (getPhaseTick() + 1 >= curPhaseLength) {
				actionPerformEnd();
			}
		}
	}
	
	@ApiStatus.Internal
	protected void checkNextPhase() {
		if (!isOver() && getPhaseTick() >= curPhaseLength && !shouldHoldPhaseAtEnd()) {
			// tick a skipped non-zero phase
			if (curPhaseTick == 0 && curPhaseLength > 0 && curPhaseLength <= 1) {
				_onTick();
			}
			
			int ordinal = phase.ordinal() + 1;
			ActionPhase nextPhase = ordinal < ActionPhase.values().length ? ActionPhase.values()[ordinal] : null;
			setPhaseStart(nextPhase);
		}
	}

	
	@Override
	@ApiStatus.Internal
	public void onKeyRelease(LivingEntity user) {
		if (!this.isOver()) {
			onButtonStopHold();
		}
	}
	
	
	public static void encode(RegistryFriendlyByteBuf buffer, EntityActionInstance action) {
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
				action.powerUser.readNetwork(buffer);
				action.standRotationTarget = NetworkUtil.readOptional(buffer, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID).orElse(null);
				action.actionTargetSnapshot = NetworkUtil.readOptional(buffer, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID).orElse(null);
				action.standOffsetSync = NetworkUtil.readOptional(buffer, _Vec3.STREAM_CODEC).orElse(null);
				if (action.standOffsetSync != null) {
					action.standOffsetSyncRotations = buffer.readEnum(StandOffsetFromUser.Rotations.class);
					action.standOffsetSyncCanInvertSide = buffer.readBoolean();
				}
				action.fromBuf(buffer);
				return action;
			}
		}
		
		return null;
	}
	
}
