package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.UtilFunctions;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public class PillarmanHeavyPunchAbility extends PillarmanActionAbility {

	public PillarmanHeavyPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.NONE, false, 10.0F, HeavyPunchInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 5);
		setDefaultPhaseLength(ActionPhase.PERFORM, 1);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		return user != null && UtilFunctions.itemHandFree(user.getMainHandItem())
				? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("hand");
	}

	public static class HeavyPunchInstance extends EntityActionInstance {
		public HeavyPunchInstance(EntityActionType ability) {
			super(ability);
			userWalkSpeed = 0.5F;
		}

		@Override
		public void actionTick() {
			if ((int) getFullTicksPassed() == 3) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					user.swing(InteractionHand.MAIN_HAND, true);
					level().playSound(null, user, ModSoundEvents.PILLAR_MAN_SWING.get(), user.getSoundSource(), 1.0F, 1.25F);
				}
			}
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
			ActionTarget target = getAimTarget(level, user);
			if (target.getType() == TargetType.BLOCK) {
				hitBlockAndAdjacent(level, user, target.getBlockPos());
			}
			else if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget) {
				hitEntity(level, user, livingTarget);
			}
		}

		private static ActionTarget getAimTarget(Level level, LivingEntity user) {
			var aim = LivingComponentAction.getAim(user);
			return aim != null ? aim.getTarget().resolveEntityId(level) : ActionTarget.EMPTY;
		}

		private static void hitEntity(Level level, LivingEntity user, LivingEntity target) {
			if (!JojoModUtil.canHarm(user, target)) {
				return;
			}
			DamageSource damageSource = user instanceof Player player
					? level.damageSources().playerAttack(player)
					: level.damageSources().mobAttack(user);
			float damage = (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE) + 4.0F;
			if (target.hurt(damageSource, damage)) {
				level.playSound(null, target, ModSoundEvents.PILLAR_MAN_PUNCH.get(), target.getSoundSource(), 1.2F, 0.8F);
				target.knockback(2.0F, user.getX() - target.getX(), user.getZ() - target.getZ());
				KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(target);
				if (impact != null) {
					impact.onPunchSetKnockbackImpact(target.getDeltaMovement(), user);
				}
			}
		}

		private static void hitBlockAndAdjacent(Level level, LivingEntity user, BlockPos blockPos) {
			hitBlock(level, user, blockPos);
			boolean breakAdjacent = PlayerPower.getPowerData(user, PillarmanPowerType.PILLAR_MAN)
					.map(data -> data.getEvolutionStage() > 1)
					.orElse(false);
			if (breakAdjacent) {
				for (Direction direction : Direction.values()) {
					hitBlock(level, user, blockPos.relative(direction));
				}
			}
			level.playSound(null, user, ModSoundEvents.HEAVY_PUNCH.get(), user.getSoundSource(), 1.5F, 1.2F);
		}

		private static void hitBlock(Level level, LivingEntity user, BlockPos blockPos) {
			if (!(level instanceof ServerLevel serverLevel)) {
				return;
			}
			BlockState blockState = level.getBlockState(blockPos);
			if (blockState.isAir() || !JojoModUtil.canEntityDestroy(serverLevel, blockPos, blockState, user)) {
				return;
			}
			float digDuration = blockState.getDestroySpeed(level, blockPos);
			boolean dropBlock = JojoModUtil.dropBrokenBlock(user);
			if (user instanceof Player player) {
				float digSpeed = player.getDestroySpeed(blockState);
				if (digSpeed > 0.0F) {
					digDuration /= digSpeed / 2.0F;
				}
				if (player.getAbilities().instabuild) {
					digDuration = 0.0F;
					dropBlock = false;
				}
				else if (!player.hasCorrectToolForDrops(blockState)) {
					digDuration *= 1.0F / 3.0F;
				}
			}
			if (digDuration >= 0.0F && digDuration <= 2.5F * Math.sqrt(user.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
				JojoModUtil.destroyBlock(level, blockPos, dropBlock, user);
			}
			else {
				SoundType soundType = blockState.getSoundType(level, blockPos, user);
				level.playSound(null, blockPos, soundType.getHitSound(), SoundSource.BLOCKS,
						(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
			}
		}
	}
}
