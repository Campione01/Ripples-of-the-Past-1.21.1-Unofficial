package rotp.core.impl.powers.hamon.abilities;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.UtilFunctions;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HamonSendoOverdriveAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 900.0F;
	private static final int HOLD_TO_FIRE_TICKS = 30;

	public HamonSendoOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, SendoOverdriveInstance::new);
		// 1.16 stoppedHolding sent the wave as the hold was let go, and the technique was over.
		hamonHoldToFire(HOLD_TO_FIRE_TICKS, false, HOLD_TO_FIRE_TICKS, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	// 1.16 needsFreeMainHand (MCUtil.isHandFree: gloves count as a free hand), checked on every held tick too.
	@Override
	protected ConditionCheck checkHeldItems(LivingEntity user) {
		return UtilFunctions.isHandFree(user, InteractionHand.MAIN_HAND)
				? ConditionCheck.POSITIVE : ConditionCheck.createNegative("hand");
	}

	// 1.16 HamonSendoOverdrive.stoppedHolding sent the wave on any stop of the hold, a failed check included.
	@Override
	public void stopHeldActionOnGettingAttacked(EntityActionInstance action) {
		if (action.getPhase() == ActionPhase.WINDUP) {
			action.setPhaseStart(ActionPhase.PERFORM);
			action.syncPhaseChanges();
			return;
		}
		super.stopHeldActionOnGettingAttacked(action);
	}

	// 1.16 stoppedHolding sent the wave on any stop with a block targeted. A release that failed its checks (no energy
	// or breath left) only skipped the perform: the wave still went, and consumeEnergy(energyCost) was still asked for.
	// With no block it sent nothing, and only a release at the full charge that passed them (stopHeldAction(true))
	// still performed: NonStandAction.onPerform spent the energy, and the hand swung.
	@Override
	protected boolean consumeRuntimeOnPerform(LivingEntity user) {
		if (getSendoReleaseTarget(user, user.level()).getType() != TargetType.BLOCK) {
			return LivingComponentAction.getCurEntityAction(user) instanceof SendoOverdriveInstance action
					&& action.ability == this && action.firesAtFullCharge && super.consumeRuntimeOnPerform(user);
		}
		Power<?> context = getUserPower(user);
		HamonData hamon = getHamonData(context);
		if (hamon == null) {
			return false;
		}
		if (!hamon.isAbilityOnCooldown(name()) && hasHamonEnergy(context, hamon)) {
			super.consumeRuntimeOnPerform(user);
		}
		else {
			hamon.getHamonEnergyUsageEfficiency(ENERGY_COST, true, user);
			hamon.syncOnUpdate(user);
			playHamonShout(user, hamon);
		}
		return true;
	}

	// 1.16 stoppedHolding placed the wave at the mouse target as it was (the raw pick the client went on sending through
	// the hold), a plant or a torch itself on the face the crosshair met, and nothing at a mob or the air. The press and
	// held checks asked for no target (Action's TargetRequirement.NONE): a press at a mob or the air held it too, and
	// overrideVanillaMouseTarget's collision ray only rewrote a check target nothing read.
	private static ActionTarget getSendoReleaseTarget(LivingEntity user, Level level) {
		ActionTarget target = LivingComponentAction.getAimTarget(user);
		return target.getType() == TargetType.BLOCK && target.getFace() != null && !target.isEmpty(level)
				? target : ActionTarget.EMPTY;
	}

	public static class SendoOverdriveInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private boolean capturedPreRuntimeState;
		private float preRuntimeEnergy;
		private float preRuntimeEfficiency = 1.0F;
		private float baseUsageStatPoints;
		private float heldTicksBeforePerform = -1.0F;
		private int windupTicksHeld;
		private boolean firesAtFullCharge;

		public SendoOverdriveInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (newPhase == ActionPhase.PERFORM && getPhase() == ActionPhase.WINDUP) {
				// 1.16 heldRatio counted every held tick; the windup's own tick stops at its full length.
				heldTicksBeforePerform = Math.max(getPhaseTick(), windupTicksHeld);
			}
			super.onSetPhase(newPhase);
		}

		// 1.16 holdToFire(30, true): a full charge stays held until the key is released.
		@Override
		protected boolean shouldHoldPhaseAtEnd() {
			return getPhase() == ActionPhase.WINDUP;
		}

		// 1.16 HamonSendoOverdrive.stoppedHolding sent the wave on any release; a release before the full
		// charge only narrowed its sparks.
		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.WINDUP) {
				// 1.16 stopHeldAction(true): a release at the full charge fired, unless the server's check refused it
				firesAtFullCharge = getPhaseTick() >= holdToFireTicks() && canFireAtFullCharge();
				setPhaseStart(ActionPhase.PERFORM);
				syncPhaseChanges();
				return;
			}
			super.onButtonStopHold();
		}

		@Override
		protected void _onTick() {
			if (getPhase() == ActionPhase.WINDUP) {
				if (!level().isClientSide() && getPhaseTick() >= holdToFireTicks()
						&& !AbilityInput.isHeldByKey(getPowerUser(), this)) {
					// No key holds it (a buffered click, an action set by code): it fires at the full charge.
					firesAtFullCharge = canFireAtFullCharge();
					setPhaseStart(ActionPhase.PERFORM);
					syncPhaseChanges();
				}
				else {
					windupTicksHeld++;
				}
			}
			if (!capturedPreRuntimeState && getPhase() == ActionPhase.PERFORM && getPhaseTick() < 1 && !level().isClientSide()) {
				capturePreRuntimeState();
			}
			super._onTick();
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null) return;
			ActionTarget target = HamonSendoOverdriveAbility.getSendoReleaseTarget(user, level);
			if (target.getType() != TargetType.BLOCK) {
				// 1.16 stopHeldAction(true) swung the hand (swingHand) without a wave
				if (firesAtFullCharge) {
					user.swing(InteractionHand.MAIN_HAND, true);
				}
				return;
			}
			if (!capturedPreRuntimeState) {
				capturePreRuntimeState();
			}
			BlockPos blockPos = target.getBlockPos();
			Direction face = target.getFace();
			if (face == null) {
				return;
			}
			float efficiency = preRuntimeEfficiency;
			float controlRatio = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON)
					.map(hamon -> (float) hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL)
					.orElse(0.0F);
			float heldRatio = Mth.clamp(((heldTicksBeforePerform >= 0.0F ? heldTicksBeforePerform : HOLD_TO_FIRE_TICKS) - 1.0F)
					/ (float) HOLD_TO_FIRE_TICKS, 0.0F, 1.0F);

			HamonSendoOverdriveEntity sendoOverdrive = new HamonSendoOverdriveEntity(level, user, face.getAxis())
					.setRadius((2.0F + controlRatio * 3.0F) * efficiency)
					.setWaveDamage(0.75F * efficiency)
					.setWavesCount(2 + (int) ((2.0F + Math.min(controlRatio * 3.0F, 2.0F)) * efficiency))
					.setStatPoints(baseUsageStatPoints);
			sendoOverdrive.setYRot(user.getYRot());
			sendoOverdrive.setXRot(user.getXRot());
			sendoOverdrive.sparksAngle = (float) Math.PI / 4.0F + heldRatio * (float) Math.PI / 4.0F * 7.0F;
			sendoOverdrive.moveTo(Vec3.atCenterOf(blockPos).subtract(0.0D, sendoOverdrive.getBbHeight() * 0.5D, 0.0D));
			sendoOverdrive.setBlockTarget(blockPos, face);
			level.addFreshEntity(sendoOverdrive);
			// 1.16's own client swung in its own stoppedHolding; this copy runs on the server only
			user.swing(InteractionHand.MAIN_HAND, true);
		}

		private void capturePreRuntimeState() {
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				preRuntimeEnergy = hamon.getEnergy();
				preRuntimeEfficiency = hamon.getActionEfficiency(ENERGY_COST, false, ModHamonSkills.SENDO_OVERDRIVE.get(), user);
				baseUsageStatPoints = Math.min(ENERGY_COST, preRuntimeEnergy) * preRuntimeEfficiency;
				capturedPreRuntimeState = true;
			});
		}

		private boolean canFireAtFullCharge() {
			HamonActionRuntimeAbility ability = hamonAbility();
			return ability != null && ability.canFireReleasedHold(this);
		}

		private int holdToFireTicks() {
			HamonActionRuntimeAbility ability = hamonAbility();
			return ability != null && ability.getHamonHoldToFireTicks() > 0 ? ability.getHamonHoldToFireTicks() : HOLD_TO_FIRE_TICKS;
		}
	}
}

