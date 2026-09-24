package rotp.core.impl.powers.hamon.abilities;

import java.util.Optional;

import rotp.core.client.input.ClientsideAim;
import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.client.sound.HamonSparksLoopSound;
import rotp.core.mechanics.HypnosisEffect;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonHypnosisState;
import rotp.core.impl.powers.hamon.HamonHypnosisState.HypnosisTargetCheck;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HamonHypnosisAbility extends HamonActionRuntimeAbility {

	public HamonHypnosisAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, HypnosisInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 8);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 4);
		// 1.16 HamonHypnosis: a hit while charging stops it before the hypnosis lands.
		setCancelHeldOnGettingAttacked();
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context != null ? context.getUser() : null;
		return user != null ? checkHypnosisTarget(HamonAbilityHelpers.getAimTarget(user, user.level()), user)
				: ConditionCheck.NEGATIVE;
	}

	// 1.16 re-checked Hypnosis against the live mouse target on every held tick (losing it ended the hold)
	// and fired at the target of the last one.
	@Override
	protected ConditionCheck checkHeldTickConditions(HamonHeldActionInstance action, LivingEntity user, Power<?> context) {
		ConditionCheck check = checkConditions(context);
		if (check.isPositive() && action instanceof HypnosisInstance hypnosis) {
			hypnosis.setHypnosisTarget(HamonAbilityHelpers.getAimTarget(user, user.level()));
		}
		return check;
	}

	// 1.16 HamonHypnosis.holdTick, on every held tick of the charge: the target looks at the user and stops moving
	// (server); the user's own client shows sparks halfway along the look and plays their sound.
	@Override
	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
		if (!(action instanceof HypnosisInstance hypnosis)) {
			return;
		}
		Level level = user.level();
		if (level.isClientSide()) {
			// The user's aim only goes from its client to the server, so its own client checks the aim it sends,
			// as the 1.16 client checked its own mouse target.
			if (isClientPlayer(user)) {
				ActionTarget target = clientPlayerAim(level);
				if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget
						&& checkConditions(context, target, user).isPositive()) {
					hypnosisClientFeedback(user, livingTarget);
				}
			}
			return;
		}
		ActionTarget target = hypnosis.getHypnosisTarget(level);
		if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget) {
			HamonHypnosisState.get(livingTarget).startedHypnosisProcess(user);
		}
	}

	// checkConditions against the given target instead of the aim the server keeps for the user.
	private ConditionCheck checkConditions(Power<?> context, ActionTarget target, LivingEntity user) {
		ConditionCheck check = checkMainModLogicConditions(context);
		if (check.isPositive()) {
			check = super.checkSpecificConditions(context);
		}
		return check.isPositive() ? checkHypnosisTarget(target, user) : check;
	}

	private static boolean isClientPlayer(LivingEntity user) {
		return user == Minecraft.getInstance().player;
	}

	private static ActionTarget clientPlayerAim(Level level) {
		return ClientsideAim.playerAim.getTarget().resolveEntityId(level);
	}

	private static ConditionCheck checkHypnosisTarget(ActionTarget target, LivingEntity user) {
		// 1.16 PowerBaseImpl.checkTarget: with no entity aimed at (sky, block) an ENTITY action failed without a message
		if (target.getType() != TargetType.ENTITY || target.getMainEntity() == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!(target.getMainEntity() instanceof LivingEntity livingTarget)) {
			return ConditionCheck.createNegative("hypnosis");
		}
		return switch (HamonHypnosisState.canBeHypnotized(livingTarget, user)) {
			case CORRECT -> ConditionCheck.POSITIVE;
			case ALREADY_TAMED_BY_USER -> ConditionCheck.createNegative("already_tamed");
			case INVALID -> ConditionCheck.createNegative("hypnosis");
		};
	}

	private static void hypnosisClientFeedback(LivingEntity user, LivingEntity livingTarget) {
		Vec3 userPos = user.getEyePosition(1.0F);
		double distanceToTarget = getDistance(user, livingTarget.getBoundingBox());
		Vec3 targetPos = userPos.add(user.getLookAngle().scale(distanceToTarget));
		Vec3 particlesPos = userPos.add(targetPos.subtract(userPos).scale(0.5D));
		HamonSparksLoopSound.playSparkSound(user, particlesPos, 1.0F, true);
		CustomParticlesHelper.createHamonSparkParticles(null, particlesPos, 1);
	}

	// 1.16 JojoModUtil.getDistance: from the eyes to where a line towards the box's eye-level centre enters it.
	private static double getDistance(Entity entity, AABB targetAabb) {
		Vec3 startPos = entity.getEyePosition(1.0F);
		if (targetAabb.contains(startPos)) {
			return 0.0D;
		}
		Vec3 endPos = new Vec3(
				Mth.lerp(0.5D, targetAabb.minX, targetAabb.maxX),
				Mth.lerp(entity.getBbHeight() == 0.0F ? 0.0D : entity.getEyeHeight() / entity.getBbHeight(), targetAabb.minY, targetAabb.maxY),
				Mth.lerp(0.5D, targetAabb.minZ, targetAabb.maxZ));
		Optional<Vec3> clipOptional = targetAabb.clip(startPos, endPos);
		return clipOptional.map(clipVec -> startPos.distanceTo(clipVec) - entity.getBbWidth() / 2.0D).orElse(-1.0D);
	}

	public static class HypnosisInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public HypnosisInstance(EntityActionType ability) { super(ability); }

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				captureActionTargetFromAim(user);
			}
		}

		ActionTarget getHypnosisTarget(Level level) {
			return getActionTargetSnapshot(level);
		}

		void setHypnosisTarget(ActionTarget target) {
			setActionTargetSnapshot(target);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null) return;
			ActionTarget target = getActionTargetSnapshot(level);
			if (target.getType() != TargetType.ENTITY || !(target.getMainEntity() instanceof LivingEntity livingTarget)
					|| !checkHypnosisTarget(target, user).isPositive()) {
				return;
			}
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			Power<?> context = hamonAbility != null ? hamonAbility.getUserPower(user) : null;
			HamonData hamon = hamonAbility != null ? hamonAbility.getHamonData(context) : null;
			if (hamon != null) {
				float controlLvl = hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL;
				int duration = (int) (controlLvl * controlLvl * 24000);
				if (duration > 0) {
					HypnosisEffect.hypnotizeEntity(livingTarget, user, duration);
				}
			}
		}
	}
}
