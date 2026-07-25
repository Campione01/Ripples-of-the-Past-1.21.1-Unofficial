package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.entityaction.ActionAnimIdentifier;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.mechanics.HamonSpreadEffect;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

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

public class HamonSunlightYellowOverdriveBarrageAbility extends HamonActionRuntimeAbility {
	private static final int MAX_BARRAGE_DURATION = 70;
	private static final int FINISHING_PUNCH_DURATION = 10;
	private static final ActionAnimIdentifier SYO_BARRAGE_START_ANIM = ActionAnimIdentifier.getOrCreate("syo_barrage_start", false);
	private static final ActionAnimIdentifier SYO_BARRAGE_LOOP_ANIM = ActionAnimIdentifier.getOrCreate("punch_barrage", false);
	private static final ActionAnimIdentifier SYO_BARRAGE_FINISHER_ANIM = ActionAnimIdentifier.getOrCreate("syo_barrage_finisher", false);

	public HamonSunlightYellowOverdriveBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, SYOverdriveBarrageInstance::new);
		setDefaultPhaseLength(ActionPhase.WINDUP, 30);
		setDefaultPhaseLength(ActionPhase.PERFORM, MAX_BARRAGE_DURATION + FINISHING_PUNCH_DURATION);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 6);
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
	protected float getHeldTickEnergyCost(Power<?> context, int ticksHeld) {
		HamonData hamon = getHamonData(context);
		return hamon != null ? hamon.getMaxEnergy() / 100.0F : super.getHeldTickEnergyCost(context, ticksHeld);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		if (action instanceof SYOverdriveBarrageInstance syoBarrage) {
			return syoBarrage.getAnimationForPhase();
		}
		return super.getEntityAnim(action);
	}

	public static class SYOverdriveBarrageInstance extends HamonActionRuntimeAbility.HamonRuntimeActionInstance {
		private int ticksHeld;
		private boolean finishingPunch;

		public SYOverdriveBarrageInstance(EntityActionType ability) { super(ability); }

		@Override
		public void actionTick() {
			LivingEntity user = getPowerUser();
			if (user == null) {
				return;
			}
			if (getPhase() == ActionPhase.WINDUP) {
				tickChargeCost(user);
				return;
			}
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			int tick = (int) getPhaseTick();
			if (tick < MAX_BARRAGE_DURATION) {
				barrageTick(user, tick);
			}
			else if (tick == MAX_BARRAGE_DURATION && !level().isClientSide()) {
				startFinishingPunch(user);
			}
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (level.isClientSide()) return;
			LivingEntity user = getPowerUser();
			if (user == null) return;
			JojoModUtil.sayVoiceLine(user, ModSoundEvents.JONATHAN_SYO_BARRAGE);
		}

		private void tickChargeCost(LivingEntity user) {
			if (level().isClientSide()) {
				return;
			}
			HamonActionRuntimeAbility hamonAbility = hamonAbility();
			Power<?> context = hamonAbility != null ? hamonAbility.getUserPower(user) : null;
			HamonData hamon = hamonAbility != null ? hamonAbility.getHamonData(context) : null;
			if (hamonAbility == null || hamon == null || !hamonAbility.consumeHeldRuntimeTick(user, ticksHeld)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			hamonAbility.syncHeldRuntimeTick(user, hamon, ticksHeld);
			ticksHeld++;
		}

		private void barrageTick(LivingEntity user, int tick) {
			Level level = level();
			if (level.isClientSide()) {
				if (tick % 2 == 0) {
					user.swinging = false;
					user.swing(tick % 4 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
				}
				return;
			}
			ActionTarget target = getAimTarget(user, level);
			if (target.getType() == TargetType.BLOCK) {
				hitBlock(level, user, target.getBlockPos());
			}
			else if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget) {
				hitEntity(level, user, livingTarget);
			}
		}

		private void startFinishingPunch(LivingEntity user) {
			if (finishingPunch) {
				return;
			}
			finishingPunch = true;
			setPhase(ActionPhase.PERFORM, MAX_BARRAGE_DURATION);
			syncPhaseChanges();

			Level level = level();
			ActionTarget target = getAimTarget(user, level);
			if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget) {
				livingTarget.removeEffect(ModStatusEffects.IMMOBILIZE);
				HamonData hamon = hamonAbility() != null ? hamonAbility().getHamonData(hamonAbility().getUserPower(user)) : null;
				float efficiency = hamon != null
						? hamon.getActionEfficiency(0, false, ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get(), user)
						: 1.0F;
				if (hamonHurtThroughInvul(livingTarget, user, 15.0F * efficiency)) {
					level.playSound(null, livingTarget, ModSoundEvents.HAMON_SYO_PUNCH.get(),
							livingTarget.getSoundSource(), 1.0F, 1.0F);
					sendYellowSparks(livingTarget, 18);
					knockbackFinisher(user, livingTarget);
					if (hamon != null && hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get())) {
						HamonSpreadEffect.giveEffectTo(livingTarget, 200, 3);
					}
				}
				vanillaAttackPreservingInvulnerability(user, livingTarget);
				user.swing(InteractionHand.MAIN_HAND, true);
			}
		}

		private static ActionTarget getAimTarget(LivingEntity user, Level level) {
			var aim = LivingComponentAction.getAim(user);
			ActionTarget target = aim != null ? aim.getTarget() : ActionTarget.EMPTY;
			return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
		}

		private static void hitEntity(Level level, LivingEntity user, LivingEntity target) {
			if (!JojoModUtil.canHarm(user, target)) {
				return;
			}
			target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					ModStatusEffects.IMMOBILIZE, 10, 0, false, false, false));
			vanillaAttackPreservingInvulnerability(user, target);
			if (hamonHurtThroughInvul(target, user, 0.1F)) {
				sendYellowSparks(target, 1);
				level.playSound(null, user, ModSoundEvents.HAMON_SYO_SWING.get(),
						user.getSoundSource(), 0.35F, 1.0F);
				if (target.getHealth() < 2.0F) {
					var action = LivingComponentAction.getCurEntityAction(user);
					if (action instanceof SYOverdriveBarrageInstance barrage) {
						barrage.startFinishingPunch(user);
					}
				}
			}
		}

		private static void vanillaAttackPreservingInvulnerability(LivingEntity user, LivingEntity target) {
			int invulTicks = target.invulnerableTime;
			if (user instanceof Player player) {
				player.attack(target);
			}
			else {
				user.doHurtTarget(target);
			}
			target.invulnerableTime = invulTicks;
		}

		private static boolean hamonHurtThroughInvul(LivingEntity target, LivingEntity user, float baseDamage) {
			int invulTicks = target.invulnerableTime;
			float lastHurt = target.lastHurt;
			target.invulnerableTime = 0;
			boolean hurt = HamonAbilityHelpers.hamonHurt(target, user, baseDamage);
			target.invulnerableTime = invulTicks;
			target.lastHurt = lastHurt;
			return hurt;
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

		private static void knockbackFinisher(LivingEntity user, LivingEntity target) {
			target.knockback(2.0D, user.getX() - target.getX(), user.getZ() - target.getZ());
			target.hurtMarked = true;
		}

		private static void sendYellowSparks(LivingEntity target, int count) {
			if (target.level() instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(ModParticles.HAMON_SPARK_YELLOW.get(),
						target.getX(), target.getY(0.5D), target.getZ(), count,
						target.getBbWidth() * 0.25D, target.getBbHeight() * 0.25D, target.getBbWidth() * 0.25D, 0.05D);
			}
		}

		ActionAnimIdentifier getAnimationForPhase() {
			if (getPhase() == ActionPhase.PERFORM) {
				return finishingPunch || getPhaseTick() >= MAX_BARRAGE_DURATION
						? SYO_BARRAGE_FINISHER_ANIM
						: SYO_BARRAGE_LOOP_ANIM;
			}
			return SYO_BARRAGE_START_ANIM;
		}
	}
}
