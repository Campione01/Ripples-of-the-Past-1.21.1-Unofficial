package com.github.standobyte.jojoimpl.stands.goldexperience;

import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;

import com.github.standobyte.jojo.JojoModLivingVariables;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.NoPoseStandEntityAbility;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.StatusEffectUtil;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GoldExperienceHealAbility extends NoPoseStandEntityAbility {

	private static final int HEAL_EFFECT_DURATION_TICKS = 6000;
	private static final int STUCK_PROJECTILE_REGEN_DURATION_TICKS = 105;
	private static final int REGEN_LEVEL_0_GAP_TICKS = 50;
	private static final int MAX_REGEN_LEVEL = 3;
	private static final int STAMINA_COST = 20;
	private static final double SOUL_SEARCH_RADIUS = 128.0;

	public GoldExperienceHealAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, HealAction::new);
		partsRequired(StandPart.ARMS);
		setDefaultPhaseLength(ActionPhase.PERFORM, 10);
	}

	@Override
	public Component getName(Power<?> context) {
		LivingEntity user = context.getUser();
		if (user != null) {
			return abilityName(context, getHealPostfix(user));
		}
		return super.getName(context);
	}

	static String getHealPostfix(LivingEntity entityToHeal) {
		if (entityToHeal.getArrowCount() > 0) {
			return ".arrow";
		}
		if (GEStuckObjectsState.get(entityToHeal).getStuckKnives() > 0) {
			return ".knife";
		}
		return "";
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		LivingEntity user = context.getUser();
		if (user != null) {
			if (user.isDeadOrDying()) {
				return ConditionCheck.NEGATIVE;
			}
			ConditionCheck check = checkCanHealTargetBeforeMaterial(user, user);
			if (!check.isPositive()) {
				return check;
			}
			if (!hasStuckProjectiles(user)) {
				check = checkHealingMaterial(user);
				if (!check.isPositive()) {
					return check;
				}
				check = checkCanHealTargetAfterMaterial(user, user);
				if (!check.isPositive()) {
					return check;
				}
			}
		}
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAMINA_COST) : check;
	}

	public static class HealAction extends EntityActionInstance {

		public HealAction(EntityActionType ability) {
			super(ability);
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

			LivingEntity target = user;

			boolean stuckProjectiles = GoldExperienceHealAbility.hasStuckProjectiles(target);
			ConditionCheck targetCheck = stuckProjectiles
					? GoldExperienceHealAbility.checkCanHealTargetBeforeMaterial(target, user)
					: GoldExperienceHealAbility.checkCanHealTarget(target, user);
			if (!targetCheck.isPositive()) {
				ConditionCheck.sendActionFailedMessage(null, targetCheck, user);
				return;
			}
			StandPower standPower = StandPower.get(user);
			if (!StandAbilityStamina.canPay(standPower, STAMINA_COST)) {
				StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
				return;
			}
			if (stuckProjectiles) {
				if (GoldExperienceHealAbility.applyGoldExperienceStuckProjectileHeal(target, standPower)) {
					StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
				}
				return;
			}
			if (!GoldExperienceHealAbility.spendHealingMaterial(user)) {
				return;
			}
			GoldExperienceHealAbility.applyGoldExperienceHeal(target, standPower);
			StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST);
		}
	}

	static ConditionCheck checkHealingMaterial(LivingEntity user) {
		ItemStack offHandItem = user.getOffhandItem();
		if (offHandItem.isEmpty()) {
			return ConditionCheck.createNegative("ge_lifeform_material_only_item");
		}
		if (!GoldExperienceCreateLifeformAbility.canGiveLifeTo(offHandItem)) {
			return ConditionCheck.createNegative("ge_lifeform_material_item");
		}
		return ConditionCheck.POSITIVE;
	}

	static ConditionCheck checkCanHealTarget(LivingEntity target, LivingEntity user) {
		return checkCanHealTarget(target, user, target);
	}

	static ConditionCheck checkCanHealTarget(
			LivingEntity target,
			LivingEntity user,
			LivingEntity classificationOwner) {
		ConditionCheck check = checkCanHealTargetBeforeMaterial(
				target, user, classificationOwner);
		if (!check.isPositive()) {
			return check;
		}
		if (target.isDeadOrDying()) {
			return ConditionCheck.POSITIVE;
		}
		if (hasStuckProjectiles(target)) {
			return ConditionCheck.POSITIVE;
		}
		return checkCanHealTargetAfterMaterial(target, user);
	}

	static ConditionCheck checkCanHealTargetBeforeMaterial(LivingEntity target, LivingEntity user) {
		return checkCanHealTargetBeforeMaterial(
				target, user, target);
	}

	static ConditionCheck checkCanHealTargetBeforeMaterial(
			LivingEntity target,
			LivingEntity user,
			LivingEntity classificationOwner) {
		if (target == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (classificationOwner == null
				|| !isLivingHealingTarget(classificationOwner)) {
			return ConditionCheck.createNegative("ge_heal_non_living");
		}
		if (StandUtil.getStandUser(target) != target) {
			return ConditionCheck.createNegative("ge_heal_stand");
		}
		if (target.isDeadOrDying()) {
			boolean canResurrect = !JojoDefinitions.isDyingBody(target) && !JojoDefinitions.isUndeadOrVampiric(target);
			return canResurrect ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("resurrect_dead");
		}

		return ConditionCheck.POSITIVE;
	}

	static ConditionCheck checkCanHealTargetAfterMaterial(LivingEntity target, LivingEntity user) {
		return checkCanHealTargetAfterMaterial(target, user, MAX_REGEN_LEVEL);
	}

	static ConditionCheck checkCanHealWithBodyTissue(LivingEntity target) {
		ConditionCheck check = checkCanHealTargetBeforeMaterial(target, target);
		if (!check.isPositive()) {
			return check;
		}
		return checkCanHealTargetAfterMaterial(target, target, MAX_REGEN_LEVEL - 1);
	}

	static ConditionCheck checkCanHealTargetAfterMaterial(LivingEntity target, LivingEntity user, int effectMax) {
		if (!target.hasEffect(ModStatusEffects.BLEEDING)) {
			MobEffectInstance currentRegen = target.getEffect(regenEffectFor(target));
			if (currentRegen != null && currentRegen.getAmplifier() >= effectMax) {
				return target == user
						? ConditionCheck.createNegative("ge_heal_stronger")
						: ConditionCheck.createNegative(Component.translatable(
								"jojo.message.action_condition.ge_heal_stronger.other", target.getDisplayName()));
			}

			if (target.getHealth() >= target.getMaxHealth()) {
				return target == user
						? ConditionCheck.createNegative("ge_heal_full_hp")
						: ConditionCheck.createNegative(Component.translatable(
								"jojo.message.action_condition.ge_heal_full_hp.other", target.getDisplayName()));
			}
		}

		return ConditionCheck.POSITIVE;
	}

	public static boolean isLivingHealingTarget(LivingEntity target) {
		return !((target.getType().is(EntityTypeTags.UNDEAD) && !(target instanceof Player))
				|| target instanceof IronGolem
				|| target instanceof SnowGolem
				|| target instanceof ArmorStand);
	}

	static Holder<MobEffect> regenEffectFor(LivingEntity target) {
		if (target instanceof Player player && JojoDefinitions.isPlayerJojoVampiric(player)) {
			return ModStatusEffects.UNDEAD_REGENERATION;
		}
		return MobEffects.REGENERATION;
	}

	static boolean spendHealingMaterial(LivingEntity user) {
		ItemStack offHandItem = user.getOffhandItem();
		if (offHandItem.isEmpty() || !GoldExperienceCreateLifeformAbility.canGiveLifeTo(offHandItem)) {
			return false;
		}
		if (offHandItem.getItem() instanceof BucketItem bucketItem && user instanceof Player player) {
			bucketItem.checkExtraContent(player, user.level(), offHandItem, user.blockPosition());
		}
		if (!(user instanceof Player player && player.getAbilities().instabuild)) {
			offHandItem.shrink(1);
		}
		return true;
	}

	static boolean hasStuckProjectiles(LivingEntity target) {
		return target.getArrowCount() > 0 || GEStuckObjectsState.get(target).getStuckKnives() > 0;
	}

	static void applyGoldExperienceHeal(LivingEntity target) {
		applyGoldExperienceHeal(target, null);
	}

	static void applyGoldExperienceHeal(LivingEntity target, StandPower standPower) {
		applyGoldExperienceHeal(target, standPower, HEAL_EFFECT_DURATION_TICKS);
	}

	static void applyGoldExperienceBodyTissueHeal(LivingEntity target, StandPower standPower) {
		applyGoldExperienceHeal(target, standPower, 100);
	}

	private static void applyGoldExperienceHeal(LivingEntity target, StandPower standPower, int durationTicks) {
		playHealSound(target, standPower);

		if (JojoDefinitions.isDyingBody(target)) {
			target.setHealth(target.getHealth() + 2.0F);
			return;
		}

		Holder<MobEffect> regenEffect = regenEffectFor(target);
		MobEffectInstance currentRegen = target.getEffect(regenEffect);
		int regenLevel = currentRegen != null ? currentRegen.getAmplifier() + 1 : 0;
		if (regenLevel <= MAX_REGEN_LEVEL) {
			int regenDuration = updateGoldExperienceRegenDuration(target, regenEffect, durationTicks, regenLevel);
			if (standPower != null) {
				GEHealingEffect healingTracker = standPower.userStandEffects.getOrCreateEffect(ModStandAbilities.EFFECT_GE_HEALING.get(), target);
				healingTracker.fullHpTicks = 0;
				healingTracker.regenLevel = regenLevel;
				if (healingTracker.tickCount == 0 && currentRegen != null) {
					healingTracker.prevEffect = new MobEffectInstance(currentRegen);
				}
			}
			target.addEffect(new MobEffectInstance(regenEffect, regenDuration, regenLevel, false, true, true));
		}
		target.hurt(target.level().damageSources().generic(), 0.0001F);

		MobEffectInstance bleeding = target.getEffect(ModStatusEffects.BLEEDING);
		if (bleeding != null) {
			int reduceDuration = durationTicks / 20;
			StatusEffectUtil.reduceEffect(target, ModStatusEffects.BLEEDING, Mth.clamp(bleeding.getDuration() - reduceDuration, 0, reduceDuration), 1);
		}
	}

	static boolean applyGoldExperienceDeadTargetHeal(LivingEntity target, StandPower standPower) {
		if (!target.isDeadOrDying()) {
			return false;
		}
		SoulEntity soulEntity = findSoulEntity(target);
		boolean resurrect = soulEntity != null && soulEntity.isAlive()
				&& (soulEntity.getLifeSpan() <= 20 || target.getRandom().nextFloat() <= 0.2F);
		if (resurrect) {
			target.setHealth(target.getMaxHealth());
			JojoModUtil.onLivingResurrect(target);
			JojoModLivingVariables.get(target).setDyingBodyTimer(24000);
		}
		playHealSound(target, standPower);
		return true;
	}

	private static SoulEntity findSoulEntity(LivingEntity target) {
		for (SoulEntity soulEntity : target.level().getEntitiesOfClass(SoulEntity.class,
				target.getBoundingBox().inflate(SOUL_SEARCH_RADIUS))) {
			if (soulEntity.isAlive() && soulEntity.getOriginEntity() == target) {
				return soulEntity;
			}
		}
		return null;
	}

	static boolean applyGoldExperienceStuckProjectileHeal(LivingEntity target, StandPower standPower) {
		if (target.getArrowCount() > 0) {
			target.setArrowCount(Math.max(target.getArrowCount() - 1, 0));
		}
		else if (!GEStuckObjectsState.get(target).decrementStuckKnife()) {
			return false;
		}
		playHealSound(target, standPower);

		if (JojoDefinitions.isDyingBody(target)) {
			target.setHealth(target.getHealth() + 2.0F);
			return true;
		}

		target.hurt(target.level().damageSources().generic(), 0.0001F);
		Holder<MobEffect> regenEffect = regenEffectFor(target);
		MobEffectInstance currentRegen = target.getEffect(regenEffect);
		int regenLevel = currentRegen != null ? Math.min(currentRegen.getAmplifier() + 1, MAX_REGEN_LEVEL) : 0;
		int regenDuration = updateGoldExperienceRegenDuration(target, regenEffect, STUCK_PROJECTILE_REGEN_DURATION_TICKS, regenLevel);
		target.addEffect(new MobEffectInstance(regenEffect, regenDuration, regenLevel, false, true, true));
		return true;
	}

	private static int updateGoldExperienceRegenDuration(LivingEntity target, Holder<MobEffect> regenEffect, int duration, int level) {
		return updateGoldExperienceEffectDuration(target.getEffect(regenEffect), duration, level, REGEN_LEVEL_0_GAP_TICKS);
	}

	private static int updateGoldExperienceEffectDuration(MobEffectInstance oldEffect, int duration, int level, int level0Gap) {
		if (oldEffect != null && level < floorLog2(level0Gap)) {
			int effectGap = level0Gap >> oldEffect.getAmplifier();
			if (effectGap > 0) {
				int oldEffectAppliesIn = oldEffect.getDuration() % (level0Gap >> oldEffect.getAmplifier());
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

	private static int floorLog2(int value) {
		return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(value);
	}

	private static void playHealSound(LivingEntity target, StandPower standPower) {
		if (!(target.level() instanceof ServerLevel serverLevel) || standPower == null) {
			return;
		}
		StandUtil.broadcastSound(serverLevel, target.position(),
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSoundEvents.GOLD_EXPERIENCE_HEAL.get()),
				true, standPower, SoundSource.AMBIENT,
				1.0F, 0.95F + target.getRandom().nextFloat() * 0.1F);
	}
}
