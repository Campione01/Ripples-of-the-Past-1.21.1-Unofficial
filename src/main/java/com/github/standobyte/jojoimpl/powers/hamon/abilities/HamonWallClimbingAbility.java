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
import com.github.standobyte.jojoimpl.powers.hamon.HamonWallClimbingHelper;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HamonWallClimbingAbility extends HamonActionRuntimeAbility {
	public HamonWallClimbingAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, WallClimbingInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 4);
		setDefaultPhaseLength(ActionPhase.PERFORM, 8);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 3);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		if (user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!user.getMainHandItem().isEmpty() || !user.getOffhandItem().isEmpty()) {
			return ConditionCheck.createNegative("hands");
		}
		ActionTarget target = getAimTarget(user);
		return canStartWallClimbing(user, target) ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	private static ActionTarget getAimTarget(LivingEntity user) {
		var aim = LivingComponentAction.getAim(user);
		return aim != null ? aim.getTarget() : ActionTarget.EMPTY;
	}

	private static boolean canStartWallClimbing(LivingEntity user, ActionTarget target) {
		if (target.getType() != TargetType.BLOCK || target.getFace() == null
				|| target.getFace().getAxis() == Direction.Axis.Y) {
			return false;
		}
		Direction face = target.getFace();
		Vec3 vecToBlock = Vec3.atLowerCornerOf(face.getOpposite().getNormal())
				.scale(HamonWallClimbingHelper.MAX_WALL_DISTANCE);
		return HamonWallClimbingHelper.collide(user, user.getBoundingBox(), vecToBlock, true)
				.distanceToSqr(vecToBlock) > 1.0E-7D;
	}

	public static class WallClimbingInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public WallClimbingInstance(EntityActionType ability) { super(ability); }

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null) return;
			ActionTarget target = getAimTarget(user);
			if (!canStartWallClimbing(user, target)) {
				return;
			}
			Direction face = target.getFace();
			Vec3 vecToBlock = Vec3.atLowerCornerOf(face.getOpposite().getNormal())
					.scale(HamonWallClimbingHelper.MAX_WALL_DISTANCE);
			Vec3 collided = HamonWallClimbingHelper.collide(user, user.getBoundingBox(), vecToBlock, true);
			double distanceFromWall = user.getBbWidth() * 0.15D;
			Vec3 moveTo = user.position().add(collided).add(Vec3.atLowerCornerOf(face.getNormal()).scale(distanceFromWall));
			user.teleportTo(moveTo.x, moveTo.y, moveTo.z);
			float yRot = 180F - face.toYRot();
			PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				hamon.startWallClimbing(user, yRot);
			});
			if (user instanceof Player player) {
				player.displayClientMessage(Component.translatable(
						"jojo.message.wall_climb.hint_jump", Component.keybind("key.jump")), true);
			}
		}
	}
}
