package rotp.core.impl.stands.silverchariot;

import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class SilverChariotRapierLaunchAbility extends StandEntityAbility {

	private static final ActionAnimIdentifier RAPIER_LAUNCH_ANIM = ActionAnimIdentifier.getOrCreate("rapier_launch", false);
	private static final float SHOT_VELOCITY = 2.0F;

	public SilverChariotRapierLaunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, RapierLaunchShot::new);
		partsRequired(StandPart.ARMS);
		cooldown(100);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		if (lacksRapier(context)) {
			return ConditionCheck.createNegative("chariot_rapier");
		}
		return ConditionCheck.POSITIVE;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return RAPIER_LAUNCH_ANIM;
	}

	private static boolean lacksRapier(Power<?> context) {
		StandPower standPower = PowerClass.STAND.cast(context);
		if (standPower == null) {
			return false;
		}
		LivingEntity user = standPower.getUser();
		if (user == null) {
			return false;
		}
		SilverChariotState state = SilverChariotState.get(user);
		return state != null && !state.hasRapier();
	}

	public static class RapierLaunchShot extends EntityActionInstance {

		public RapierLaunchShot(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			if (getPerformer() instanceof StandEntity stand) {
				ActionTarget target = captureActionTargetFromAim(stand);
				keepStandAimedAtTarget(target);
			}
			aimAs = AimingEntity.STAND;
			setStandOffset(0.0, 0.25, StandOffsetFromUser.Rotations.BODY, false);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			SilverChariotState state = SilverChariotState.get(user);
			if (state != null && !state.hasRapier()) {
				return;
			}
			if (!(getPerformer() instanceof StandEntity stand)) {
				return;
			}

			LivingEntity aimingEntity = stand;
			if (stand.isFollowingUser()) {
				aimingEntity = user;
			}

			SCRapierEntity rapier = new SCRapierEntity(stand, level);
			rapier.setPos(aimingEntity.getX(), aimingEntity.getEyeY(), aimingEntity.getZ());
			if (stand.isSilverChariotRapierOnFire()) {
				rapier.igniteForTicks(rapier.ticksLifespan());
			}
			rapier.shootFromRotation(aimingEntity, aimingEntity.getXRot(), aimingEntity.getYRot(), 0, SHOT_VELOCITY, 0);
			addProjectileWithStandStats(rapier);
			StandUtil.playStandEntitySound(stand, ModSoundEvents.SILVER_CHARIOT_RAPIER_SHOT, 1.0F, 1.0F);

			if (state != null && !isUserCreative(user)) {
				state.setHasRapier(false);
				stand.refreshSilverChariotStateAfterMutation(user);
			}
		}

		private static boolean isUserCreative(LivingEntity user) {
			return user instanceof Player p && p.getAbilities().instabuild;
		}
	}
}
