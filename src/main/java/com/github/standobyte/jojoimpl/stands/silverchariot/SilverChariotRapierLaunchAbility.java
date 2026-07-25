package com.github.standobyte.jojoimpl.stands.silverchariot;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.AimingEntity;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntityAbility;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandOffsetFromUser;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;

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
