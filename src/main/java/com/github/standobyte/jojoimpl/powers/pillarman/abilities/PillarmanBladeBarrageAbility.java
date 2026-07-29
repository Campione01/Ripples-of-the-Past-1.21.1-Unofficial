package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModSoundEvents;
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
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.UtilFunctions;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class PillarmanBladeBarrageAbility extends PillarmanActionAbility {

	public PillarmanBladeBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, -1, PillarmanMode.LIGHT, false, 0.0F, 1.0F, 0.2F, 0,
				BladeBarrageInstance::new);
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
		return user != null && UtilFunctions.isHandFree(
				user, InteractionHand.MAIN_HAND)
				? ConditionCheck.POSITIVE
				: ConditionCheck.createNegative("hand");
	}

	public static boolean onUserIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !target.isAlive()) {
			return false;
		}
		Entity attacker = event.getSource().getDirectEntity();
		if (!(attacker instanceof Projectile || attacker instanceof ModdedProjectileEntity)) {
			return false;
		}
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(target);
		if (action == null || !(action.ability instanceof PillarmanBladeBarrageAbility)
				|| action.getPhase() != ActionPhase.PERFORM) {
			return false;
		}
		if (attacker instanceof ModdedProjectileEntity projectile) {
			if (!projectile.canBeEvaded(target) || projectile.standDamage()) {
				return false;
			}
		}
		if (attacker instanceof Projectile
				&& (attacker.getDeltaMovement().lengthSqr() < 1.0E-6D
						|| target.getLookAngle().dot(attacker.getDeltaMovement().reverse().normalize())
								< Math.cos(Math.toRadians(48.75D)))) {
			return false;
		}
		if (!attacker.onGround()) {
			sparkEffect(attacker, 12);
			attacker.level().playSound(null, attacker, SoundEvents.ANVIL_LAND,
					attacker.getSoundSource(), 0.4F, 1.35F);
		}
		return true;
	}

	public static class BladeBarrageInstance extends PillarmanHeldActionInstance {
		public BladeBarrageInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.PERFORM) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					setBladesVisible(user, true);
				}
			}
		}

		@Override
		protected void heldTick(PillarmanActionAbility pillarmanAbility, LivingEntity user, Power<?> context, int ticksHeld) {
			if (user == null) {
				return;
			}
			if (level().isClientSide()) {
				if (ticksHeld % 2 == 0) {
					user.swinging = false;
					user.swing(InteractionHand.MAIN_HAND, true);
				}
				return;
			}
			setBladesVisible(user, true);
			ActionTarget target = LivingComponentAction.getAim(user).getTarget();
			if (target == null) {
				return;
			}
			target = target.resolveEntityId(level());
			if (target.getType() == TargetType.BLOCK) {
				hitBlock(level(), user, target.getBlockPos());
			}
			else if (target.getType() == TargetType.ENTITY && target.getEntity() instanceof LivingEntity livingTarget) {
				hitEntity(level(), user, livingTarget);
			}
			level().playSound(null, user, ModSoundEvents.SILVER_CHARIOT_BARRAGE_SWIPE.get(),
					user.getSoundSource(), 0.5F, 1.0F);
		}

		private static void hitEntity(Level level, LivingEntity user, LivingEntity target) {
			if (!JojoModUtil.canHarm(user, target)) {
				return;
			}
			float damage = DamageUtil.getDamageWithoutHeldItem(user) * 0.2F;
			if (DamageUtil.hurtThroughInvulTicks(target, meleeDamageSource(level, user), damage)) {
				sparkEffect(target, 12);
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
				JojoModUtil.destroyBlock(level, blockPos, dropItem, user);
			}
			else {
				SoundType soundType = blockState.getSoundType(level, blockPos, user);
				level.playSound(null, blockPos, soundType.getHitSound(), SoundSource.BLOCKS,
						(soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			LivingEntity user = getPowerUser();
			super.onActionCleared(newAction);
			if (user != null) {
				setBladesVisible(user, false);
			}
		}
	}
}
