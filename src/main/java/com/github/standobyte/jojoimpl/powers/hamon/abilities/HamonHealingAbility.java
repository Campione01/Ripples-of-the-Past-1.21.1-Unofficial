package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import java.util.List;
import java.util.OptionalInt;

import com.github.standobyte.jojo.client.particle.CustomParticlesHelper;
import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.client.sound.HamonSparksLoopSound;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojo.subsystems.target.ActionTarget.TargetType;
import com.github.standobyte.jojo.util.functions.StatusEffectUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;

public class HamonHealingAbility extends HamonActionRuntimeAbility {
	private static final List<Holder<MobEffect>> VENOM_EFFECTS_INIT = List.of(
			MobEffects.POISON,
			MobEffects.WITHER,
			MobEffects.HUNGER,
			MobEffects.CONFUSION);

	public HamonHealingAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, HealingInstance::new);
	}

	@Override
	protected void onHeldTick(HamonHeldActionInstance action, LivingEntity user, Power<?> context, HamonData hamon, int ticksHeld) {
		float tickEnergyCost = getHeldTickEnergyCost(context, ticksHeld);
		float hamonControl = hamon.getHamonControlLevel() / (float) HamonData.MAX_STAT_LEVEL;
		float hamonEfficiency = hamon.getActionEfficiency(tickEnergyCost, false, ModHamonSkills.HEALING.get(), user);
		LivingEntity entityToHeal = selectEntityToHeal(user, hamon);
		if (hamonEfficiency <= 0.0F) {
			return;
		}

		Level level = user.level();
		if (level.isClientSide()) {
			healingClientFeedback(user);
			return;
		}

		float qLevel = hamonControl * 3.0F + (hamonEfficiency - 0.75F) * 4.0F - 1.0F;
		int maxLevel = Mth.clamp((int) qLevel, 0, 2);
		int maxTicksLeftover = (int) Math.max((50.0F + hamonEfficiency * 50.0F) * (1.0F + hamonControl), 51.0F);
		float ticksIncreaseSpeed = 0.75F + hamonControl * 0.75F;
		int ticksInc = (int) (ticksIncreaseSpeed * ticksHeld) - (int) (ticksIncreaseSpeed * (ticksHeld - 1));

		MobEffectInstance regenEffect = entityToHeal.getEffect(MobEffects.REGENERATION);
		int regenAmplifier = -1;
		int regenDuration = -1;
		if (regenEffect == null) {
			regenAmplifier = 0;
			regenDuration = 51;
		}
		else if (regenEffect.getDuration() < maxTicksLeftover || regenEffect.getAmplifier() < maxLevel) {
			OptionalInt impliedDuration = hamon.getRegenImpliedDuration();
			if (impliedDuration.isEmpty()) {
				hamon.setRegenImpliedDuration(regenEffect.getDuration());
				impliedDuration = hamon.getRegenImpliedDuration();
			}
			regenDuration = Math.min(impliedDuration.getAsInt() + ticksInc, maxTicksLeftover);
			int giveAmplifier = (int) ((float) regenDuration / maxTicksLeftover * qLevel);
			regenAmplifier = Mth.clamp(regenEffect.getAmplifier(), giveAmplifier, maxLevel);
		}
		if (regenAmplifier >= 0 && regenDuration > 0) {
			if (regenEffect != null && regenAmplifier > regenEffect.getAmplifier()) {
				HamonUtil.emitHamonSparkParticles(level, null, entityToHeal.getBoundingBox().getCenter(), 0.1F);
			}
			hamon.setRegenImpliedDuration(regenDuration);
			regenDuration = updateRegenEffect(entityToHeal, regenDuration, regenAmplifier, MobEffects.REGENERATION);
			entityToHeal.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDuration, regenAmplifier, false, false, true));
		}

		int reduceEffectTime = Math.max(1, 3200 / maxTicksLeftover);
		int durationDecrease = Math.max(ticksInc, 0);
		boolean healedBleeding = reduceHarmfulEffect(entityToHeal, ModStatusEffects.BLEEDING, ticksHeld, durationDecrease, reduceEffectTime);
		boolean hadHarmfulEffects = healedBleeding;
		if (hamon.isSkillLearned(ModHamonSkills.EXPEL_VENOM.get())) {
			for (Holder<MobEffect> venomEffect : VENOM_EFFECTS_INIT) {
				hadHarmfulEffects |= reduceHarmfulEffect(entityToHeal, venomEffect, ticksHeld, durationDecrease, reduceEffectTime);
			}
		}

		ActionTarget target = HamonAbilityHelpers.getAimTarget(user, level);
		if (hamon.isSkillLearned(ModHamonSkills.PLANTS_GROWTH.get()) && user instanceof Player player
				&& target.getType() == TargetType.BLOCK) {
			Direction face = target.getFace() != null ? target.getFace() : Direction.UP;
			bonemealEffect(level, player, target.getBlockPos(), face);
		}

		boolean hitLimit = regenEffect != null && regenEffect.getAmplifier() == maxLevel
				&& hamon.getRegenImpliedDuration().isPresent()
				&& hamon.getRegenImpliedDuration().getAsInt() >= maxTicksLeftover;
		float points = Math.min(tickEnergyCost, hamon.getEnergy());
		if (points > 0.0F) {
			if (hitLimit) {
				points *= Math.max(HamonData.ENERGY_TICK_DOWN_AMOUNT / tickEnergyCost, 2.0F);
			}
			if (entityToHeal.getHealth() < entityToHeal.getMaxHealth() || hadHarmfulEffects) {
				points *= 4.0F;
			}
			if (entityToHeal != user) {
				points *= 2.0F;
			}
			hamon.hamonPointsFromAction(HamonData.HamonStat.CONTROL, points);
		}
	}

	private static LivingEntity selectEntityToHeal(LivingEntity user, HamonData hamon) {
		if (user.isShiftKeyDown() && hamon.isSkillLearned(ModHamonSkills.HEALING_TOUCH.get())) {
			ActionTarget target = HamonAbilityHelpers.getAimTarget(user, user.level());
			if (target.getType() == TargetType.ENTITY && target.getMainEntity() instanceof LivingEntity livingTarget
					&& canBeHealed(livingTarget)) {
				return livingTarget;
			}
		}
		return user;
	}

	private static boolean reduceHarmfulEffect(LivingEntity entity, Holder<MobEffect> effect,
			int ticksHeld, int durationDecrease, int reduceEffectTime) {
		MobEffectInstance effectInstance = entity.getEffect(effect);
		if (effectInstance == null) {
			return false;
		}
		int amplifier = effectInstance.getAmplifier();
		if (amplifier > 0 && ticksHeld > 0 && ticksHeld % reduceEffectTime == 0) {
			amplifier--;
		}
		int adjustedDurationDecrease = amplifier == 0 ? durationDecrease * 2 : durationDecrease;
		int duration = effectInstance.getDuration() - adjustedDurationDecrease;
		if (duration > 0) {
			duration = updateKnownEffect(entity, duration, amplifier, effect);
		}
		if (duration <= 0) {
			entity.removeEffect(effect);
		}
		else {
			StatusEffectUtil.reduceEffect(entity, effect,
					Math.max(effectInstance.getDuration() - duration, 0),
					Math.max(effectInstance.getAmplifier() - amplifier, 0));
		}
		return true;
	}

	public static int updateRegenEffect(LivingEntity entity, int duration, int level, Holder<MobEffect> effect) {
		return updateEffect(entity, duration, level, effect, 50);
	}

	public static int updateKnownEffect(LivingEntity entity, int duration, int level, Holder<MobEffect> effect) {
		if (effect == MobEffects.REGENERATION || effect == ModStatusEffects.UNDEAD_REGENERATION) {
			return updateEffect(entity, duration, level, effect, 50);
		}
		if (effect == MobEffects.POISON) {
			return updateEffect(entity, duration, level, effect, 25);
		}
		if (effect == MobEffects.WITHER) {
			return updateEffect(entity, duration, level, effect, 40);
		}
		return duration;
	}

	private static int updateEffect(LivingEntity entity, int duration, int level, Holder<MobEffect> effect, int level0Gap) {
		MobEffectInstance oldEffect = entity.getEffect(effect);
		if (oldEffect != null && level < Mth.log2(level0Gap)) {
			int effectGap = level0Gap >> oldEffect.getAmplifier();
			if (effectGap > 0) {
				int oldEffectAppliesIn = oldEffect.getDuration() % effectGap;
				int newEffectGap = level0Gap >> level;
				int newEffectAppliesIn = newEffectGap > 0 ? duration % newEffectGap : 0;
				if (newEffectAppliesIn < oldEffectAppliesIn) {
					int newDuration = duration + (oldEffectAppliesIn - newEffectAppliesIn);
					while (newDuration > duration) {
						newDuration -= newEffectGap;
					}
					if (newDuration > 0) {
						duration = newDuration;
					}
				}
				else {
					duration -= newEffectAppliesIn - oldEffectAppliesIn;
				}
			}
		}
		return duration;
	}

	private static boolean canBeHealed(LivingEntity targetEntity) {
		return HamonUtil.isLiving(targetEntity);
	}

	private static void healingClientFeedback(LivingEntity user) {
		HamonSparksLoopSound.playSparkSound(user, user.position(), 1.0F);
		CustomParticlesHelper.createHamonSparkParticles(null,
				user.getRandomX(1.0D), user.getRandomY(), user.getRandomZ(1.0D), 1);
	}

	private static boolean bonemealEffect(Level level, Player applyingPlayer, BlockPos pos, Direction face) {
		if (BoneMealItem.growCrop(ItemStack.EMPTY, level, pos)) {
			if (!level.isClientSide()) {
				level.levelEvent(1505, pos, 5);
			}
			return true;
		}
		BlockPos waterPos = pos.relative(face);
		if (canGrowWaterPlant(level, pos, waterPos, face)
				&& BoneMealItem.growWaterPlant(ItemStack.EMPTY, level, waterPos, face)) {
			if (!level.isClientSide()) {
				level.levelEvent(1505, waterPos, 5);
			}
			return true;
		}
		return false;
	}

	private static boolean canGrowWaterPlant(LevelReader level, BlockPos clickedPos, BlockPos waterPos, Direction face) {
		return level.getBlockState(clickedPos).isFaceSturdy(level, clickedPos, face)
				&& level.getFluidState(waterPos).isSource();
	}

	private void clearRegenImpliedDuration(LivingEntity user) {
		Power<?> context = getUserPower(user);
		HamonData hamon = getHamonData(context);
		if (hamon != null) {
			hamon.clearRegenImpliedDuration();
		}
	}

	public static class HealingInstance extends HamonActionRuntimeAbility.HamonHeldActionInstance {
		public HealingInstance(EntityActionType ability) { super(ability); }

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.PERFORM && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.HAMON_HEALING.get(), user, this,
							ActionPhase.PERFORM, 1.0F, 1.0F, 15);
				}
			}
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			super.onActionCleared(newAction);
			LivingEntity user = getPowerUser();
			if (!level().isClientSide() && user != null && hamonAbility() instanceof HamonHealingAbility healing) {
				healing.clearRegenImpliedDuration(user);
			}
		}
	}
}
