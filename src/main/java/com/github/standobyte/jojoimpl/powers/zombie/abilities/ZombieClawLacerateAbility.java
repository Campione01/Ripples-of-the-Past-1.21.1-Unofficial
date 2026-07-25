package com.github.standobyte.jojoimpl.powers.zombie.abilities;

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
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.JojoModUtil;

import net.minecraft.core.BlockPos;
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

public class ZombieClawLacerateAbility extends ZombieActionAbility {

	public ZombieClawLacerateAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, true, 60.0F, ClawLacerateInstance::new);
		setDefaultPhaseLength(ActionPhase.PERFORM, 8);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity user = context.getUser();
		return user != null && user.getMainHandItem().isEmpty()
				? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("hand");
	}

	public static class ClawLacerateInstance extends EntityActionInstance {
		public ClawLacerateInstance(EntityActionType ability) {
			super(ability);
			userWalkSpeed = 0.5F;
		}

		@Override
		public void actionTick() {
			int tick = (int) getFullTicksPassed();
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			if (tick == 3 && !level().isClientSide()) {
				level().playSound(null, user, ModSoundEvents.ZOMBIE_SWIPE.get(),
						user.getSoundSource(), 1.0F, 1.25F);
				user.swing(InteractionHand.MAIN_HAND, true);
			}
			else if (tick == 5 && !level().isClientSide()) {
				punchPerform(level(), user);
			}
		}

		private static void punchPerform(Level level, LivingEntity user) {
			ActionTarget target = getAimTarget(level, user);
			if (target == null || target.isEmpty(level)) {
				return;
			}
			if (target.getType() == TargetType.BLOCK) {
				hitBlock(level, user, target.getBlockPos());
				level.playSound(null, user, ModSoundEvents.HEAVY_PUNCH.get(),
						user.getSoundSource(), 1.5F, 1.2F);
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
			float damage = (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE) + 4.0F;
			DamageSource damageSource = user instanceof Player player
					? level.damageSources().playerAttack(player)
					: level.damageSources().mobAttack(user);
			if (target.hurt(damageSource, damage)) {
				level.playSound(null, target, ModSoundEvents.ZOMBIE_CLAW_LACERATE.get(),
						target.getSoundSource(), 1.2F, 0.8F);
				target.knockback(2.0D, user.getX() - target.getX(), user.getZ() - target.getZ());
				KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(target);
				if (impact != null) {
					impact.onPunchSetKnockbackImpact(target.getDeltaMovement(), user);
				}
			}
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
			boolean dropItem = JojoModUtil.dropBrokenBlock(user);
			if (user instanceof Player player) {
				float digSpeed = player.getDestroySpeed(blockState);
				if (digSpeed > 0.0F) {
					digDuration /= digSpeed / 2.0F;
				}
				if (player.getAbilities().instabuild) {
					digDuration = 0.0F;
					dropItem = false;
				}
				else if (!player.hasCorrectToolForDrops(blockState)) {
					digDuration *= 1.0F / 3.0F;
				}
			}
			if (digDuration >= 0.0F && digDuration <= 2.5F * Math.sqrt(user.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
				JojoModUtil.destroyBlock(level, blockPos, dropItem, user);
			}
			else {
				SoundType soundType = blockState.getSoundType(level, blockPos, user);
				level.playSound(null, blockPos, soundType.getHitSound(), SoundSource.BLOCKS,
						(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
			}
		}
	}
}
