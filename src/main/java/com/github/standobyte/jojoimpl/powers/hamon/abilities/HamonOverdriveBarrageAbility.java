package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public class HamonOverdriveBarrageAbility extends HamonActionRuntimeAbility {

	public HamonOverdriveBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, OverdriveBarrageInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.PERFORM, 20);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
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
		return user.getMainHandItem().isEmpty() && user.getOffhandItem().isEmpty()
				? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("hands");
	}

	@Override
	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
		Level level = user.level();
		if (level.isClientSide()) {
			if (ticksHeld % 2 == 0) {
				user.swinging = false;
				user.swing(ticksHeld % 4 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
			}
			return;
		}
		ActionTarget target = HamonAbilityHelpers.getAimTarget(user, level);
		if (target.getType() == TargetType.BLOCK) {
			hitBlock(level, user, hamon, target.getBlockPos());
		}
		else if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget) {
			hitEntity(level, user, livingTarget);
		}
	}

	private void hitEntity(Level level, LivingEntity user, LivingEntity target) {
		if (!JojoModUtil.canHarm(user, target)) {
			return;
		}
		int invulTicks = target.invulnerableTime;
		HamonAbilityHelpers.doMeleeAttack(user, target);
		target.invulnerableTime = invulTicks;
		if (HamonAbilityHelpers.hamonHurtThroughInvul(target, user, 0.1F)) {
			HamonAbilityHelpers.sendHamonParticles(target, ModParticles.HAMON_SPARK.get(), 1);
			level.playSound(null, user, ModSoundEvents.HAMON_SYO_SWING.get(),
					user.getSoundSource(), 0.35F, 1.0F);
		}
	}

	private void hitBlock(Level level, LivingEntity user, HamonData hamon, BlockPos blockPos) {
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
				digDuration /= digSpeed;
			}
			if (player.getAbilities().instabuild) {
				digDuration = 0.0F;
				dropItem = false;
			}
			else if (!player.hasCorrectToolForDrops(blockState)) {
				digDuration *= 10.0F / 3.0F;
			}
		}
		if (digDuration >= 0.0F && digDuration <= 2.5F * Math.sqrt(user.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
			if (JojoModUtil.destroyBlock(level, blockPos, dropItem, user)) {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, getHeldTickEnergyCost(null, 0));
				hamon.syncOnUpdate(user);
			}
		}
		else {
			SoundType soundType = blockState.getSoundType(level, blockPos, user);
			level.playSound(null, blockPos, soundType.getHitSound(), SoundSource.BLOCKS,
					(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
		}
	}

	public static class OverdriveBarrageInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public OverdriveBarrageInstance(EntityActionType ability) { super(ability); }
	}
}
