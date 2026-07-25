package com.github.standobyte.jojoimpl.powers.vampirism.abilities;

import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
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
import com.github.standobyte.jojo.subsystems.target.LiquidOnlyClipContext;
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class VampirismFreezeAbility extends VampirismActionAbility {
	private static final float HOLD_BLOOD_COST = 0.45F;
	private static final double MAX_RANGE_SQ_ENTITY_TARGET = 4.0D;

	public VampirismFreezeAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 1, HOLD_BLOOD_COST, FreezeInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
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
		if (user.level().getDifficulty() == Difficulty.PEACEFUL) {
			return ConditionCheck.createNegative("peaceful");
		}
		if (user.isOnFire()) {
			return ConditionCheck.createNegative("fire");
		}
		if (user.level().dimensionType().ultraWarm()) {
			return ConditionCheck.createNegative("ultrawarm");
		}
		if (!user.getMainHandItem().isEmpty()) {
			return ConditionCheck.createNegative("hand");
		}
		return ConditionCheck.POSITIVE;
	}

	public static class FreezeInstance extends EntityActionInstance {
		public FreezeInstance(EntityActionType ability) {
			super(ability);
			userWalkSpeed = 0.75F;
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}

			Level level = level();
			LivingEntity user = getPowerUser();
			if (user == null) {
				forceStop();
				return;
			}

			if (level.isClientSide()) {
				Vec3 particlePos = user.position().add(
						(randomOffset(user.getBbWidth() + 1.0F)),
						user.getRandom().nextDouble() * (user.getBbHeight() + 1.0F),
						(randomOffset(user.getBbWidth() + 1.0F)));
				level.addParticle(ParticleTypes.CLOUD, particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
				return;
			}

			VampirismState state = VampirismState.get(user);
			if (!isUserCreative()) {
				if (state.blood().current() < HOLD_BLOOD_COST) {
					forceStop();
					return;
				}
				state.blood().consume(HOLD_BLOOD_COST);
			}

			ActionTarget target = ActionTarget.EMPTY;
			if (LivingComponentAction.getAim(user) != null) {
				target = LivingComponentAction.getAim(user).getTarget();
			}
			if (target.getType() == TargetType.ENTITY) {
				Entity entityTarget = target.resolveEntityId(level).getEntity();
				if (entityTarget instanceof LivingEntity targetLiving && !targetLiving.isOnFire()
						&& user.distanceToSqr(targetLiving) <= MAX_RANGE_SQ_ENTITY_TARGET) {
					freezeTarget(user, targetLiving);
				}
			}
			frostWalkerImitation(user, level, user.blockPosition(), 4);
		}

		@Override
		public void onButtonStopHold() {
			forceStop();
			syncPhaseChanges();
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}

		private static double randomOffset(float width) {
			return (Math.random() - 0.5D) * width;
		}
	}

	private static void freezeTarget(LivingEntity user, LivingEntity targetLiving) {
		Level level = user.level();
		int difficulty = level.getDifficulty().getId();
		float damage = (float) Math.pow(2, difficulty) * 0.5F;
		if (targetLiving.getType() == EntityType.SKELETON && targetLiving.isAlive() && targetLiving.getHealth() <= damage) {
			turnSkeletonIntoStray(targetLiving);
		}
		else if (DamageUtil.dealColdDamage(targetLiving, damage, user, null)) {
			MobEffectInstance freezeInstance = targetLiving.getEffect(ModStatusEffects.FREEZE);
			if (freezeInstance == null) {
				level.playSound(null, targetLiving, ModSoundEvents.VAMPIRE_FREEZE.get(),
						targetLiving.getSoundSource(), 1.0F, 1.0F);
				targetLiving.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, (difficulty + 1) * 50, 0));
			}
			else {
				int additionalDuration = (difficulty - 1) * 5 + 1;
				int duration = freezeInstance.getDuration() + additionalDuration;
				int lvl = duration / 100;
				targetLiving.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, duration, lvl));
			}
		}
	}

	public static boolean turnSkeletonIntoStray(LivingEntity skeleton) {
		if (!(skeleton.level() instanceof ServerLevel level) || !(skeleton instanceof Mob mob)) {
			return false;
		}
		if (!(level.getDifficulty() == Difficulty.NORMAL && skeleton.getRandom().nextBoolean()
				|| level.getDifficulty() == Difficulty.HARD)) {
			return false;
		}
		if (!EventHooks.canLivingConvert(skeleton, EntityType.STRAY, ticks -> {})) {
			return false;
		}
		Stray stray = mob.convertTo(EntityType.STRAY, true);
		if (stray == null) {
			return false;
		}
		EventHooks.onLivingConvert(skeleton, stray);
		if (!skeleton.isSilent()) {
			level.levelEvent(null, 1026, stray.blockPosition(), 0);
		}
		return true;
	}

	private static void frostWalkerImitation(LivingEntity entity, Level level, BlockPos entityPos, int radius) {
		if (entity.onGround()) {
			BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();
			for (BlockPos blockPos : BlockPos.betweenClosed(
					entityPos.offset(-radius, -1, -radius),
					entityPos.offset(radius, -1, radius))) {
				if (blockPos.closerToCenterThan(entity.position(), radius)) {
					posMutable.set(blockPos.getX(), blockPos.getY() + 1, blockPos.getZ());
					if (level.getBlockState(posMutable).isAir()) {
						freezeWaterBlock(level, blockPos, entity);
					}
				}
			}
		}

		Vec3 eyePos = entity.getEyePosition(1.0F);
		Vec3 lookVec = entity.getLookAngle();
		HitResult rayTraceResult = level.clip(new LiquidOnlyClipContext(
				eyePos.add(lookVec), eyePos.add(lookVec.scale(8)), ClipContext.Fluid.SOURCE_ONLY, entity));
		if (rayTraceResult.getType() == HitResult.Type.BLOCK) {
			freezeWaterBlock(level, ((BlockHitResult) rayTraceResult).getBlockPos(), entity);
		}
	}

	private static final BlockState ICE = Blocks.FROSTED_ICE.defaultBlockState();
	private static void freezeWaterBlock(Level level, BlockPos blockPos, LivingEntity vampireEntity) {
		BlockState blockState = level.getBlockState(blockPos);
		boolean isFullWater = blockState.is(Blocks.WATER) && blockState.getValue(LiquidBlock.LEVEL) == 0;
		if (isFullWater && ICE.canSurvive(level, blockPos)
				&& level.noCollision(ICE.getCollisionShape(level, blockPos).bounds().move(blockPos))
				&& !EventHooks.onBlockPlace(vampireEntity,
						BlockSnapshot.create(level.dimension(), level, blockPos), Direction.UP)) {
			level.setBlockAndUpdate(blockPos, ICE);
			level.scheduleTick(blockPos, Blocks.FROSTED_ICE, Mth.nextInt(vampireEntity.getRandom(), 20, 40));
		}
	}

	public static boolean onUserIncomingDamage(LivingIncomingDamageEvent event) {
		Entity attacker = event.getSource().getDirectEntity();
		if (!(attacker instanceof LivingEntity attackerLiving)
				|| attacker.isOnFire()
				|| DamageUtil.isImmuneToCold(attacker)) {
			return false;
		}
		LivingEntity targetLiving = event.getEntity();
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(targetLiving);
		if (action != null && action.ability instanceof VampirismFreezeAbility
				&& action.getPhase() == ActionPhase.PERFORM) {
			Level level = attacker.level();
			int difficulty = level.getDifficulty().getId();
			attackerLiving.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, difficulty * 100, difficulty));
			level.playSound(null, attackerLiving, ModSoundEvents.VAMPIRE_FREEZE.get(),
					attackerLiving.getSoundSource(), 1.0F, 1.0F);
			return true;
		}
		return false;
	}
}
