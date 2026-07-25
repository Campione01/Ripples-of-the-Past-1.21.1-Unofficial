package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonSendoOverdriveEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class HamonSendoOverdriveAbility extends HamonActionRuntimeAbility {
	private static final float ENERGY_COST = 900.0F;
	private static final int HOLD_TO_FIRE_TICKS = 30;
	private static final double BLOCK_TARGET_RANGE = 10.0D;

	public HamonSendoOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, SendoOverdriveInstance::new);
		hamonHoldToFire(HOLD_TO_FIRE_TICKS, false, HOLD_TO_FIRE_TICKS, 5);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context != null ? context.getUser() : null;
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!user.getMainHandItem().isEmpty()) {
			return ConditionCheck.createNegative("hand");
		}
		return getSendoBlockTarget(user, user.level()).getType() == TargetType.BLOCK
				? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	@Override
	protected boolean consumeRuntimeOnPerform(LivingEntity user) {
		return getSendoBlockTarget(user, user.level()).getType() == TargetType.BLOCK
				&& super.consumeRuntimeOnPerform(user);
	}

	private static ActionTarget getSendoBlockTarget(LivingEntity user, Level level) {
		ActionTarget target = resolveSyncedSendoTarget(user, level);
		if (target.getType() != TargetType.BLOCK) {
			target = clipSendoBlockTarget(user, level);
		}
		if (target.getType() == TargetType.BLOCK) {
			BlockPos blockPos = target.getBlockPos();
			BlockState blockState = level.getBlockState(blockPos);
			if (blockState.getCollisionShape(level, blockPos).isEmpty()) {
				target = clipSendoBlockTarget(user, level);
			}
		}
		return target.getType() == TargetType.BLOCK && target.getFace() != null && !target.isEmpty(level)
				? target : ActionTarget.EMPTY;
	}

	private static ActionTarget resolveSyncedSendoTarget(LivingEntity user, Level level) {
		var aim = LivingComponentAction.getAim(user);
		if (aim != null) {
			return aim.getTarget().resolveEntityId(level);
		}
		return ActionTarget.EMPTY;
	}

	private static ActionTarget clipSendoBlockTarget(LivingEntity user, Level level) {
		Vec3 from = user.getEyePosition(1.0F);
		Vec3 to = from.add(user.getLookAngle().scale(BLOCK_TARGET_RANGE));
		HitResult hitResult = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
		return ActionTarget.fromVanilla(hitResult);
	}

	public static class SendoOverdriveInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private boolean capturedPreRuntimeState;
		private float preRuntimeEnergy;
		private float preRuntimeEfficiency = 1.0F;
		private float baseUsageStatPoints;
		private float heldTicksBeforePerform;

		public SendoOverdriveInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			if (newPhase == ActionPhase.PERFORM && getPhase() == ActionPhase.WINDUP) {
				heldTicksBeforePerform = getPhaseTick();
			}
			super.onSetPhase(newPhase);
		}

		@Override
		protected void _onTick() {
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
			ActionTarget target = HamonSendoOverdriveAbility.getSendoBlockTarget(user, level);
			if (target.getType() != TargetType.BLOCK) {
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
			float heldRatio = Mth.clamp(((heldTicksBeforePerform > 0.0F ? heldTicksBeforePerform : HOLD_TO_FIRE_TICKS) - 1.0F)
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
			user.swing(InteractionHand.MAIN_HAND, false);
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
	}
}

