package com.github.standobyte.jojoimpl.powers.vampirism.abilities;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.init.ModDamageTypes;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModCustomStats;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.ModEntityTypeTags;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.mechanics.JojoDefinitions;
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
import com.github.standobyte.jojo.util.functions.DamageUtil;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;
import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;
import com.github.standobyte.jojoimpl.powers.zombie.abilities.ZombieDevourAbility;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class VampirismBloodDrainAbility extends VampirismActionAbility {
	private static final double MAX_RANGE_SQ_ENTITY_TARGET = 4.0D;

	public VampirismBloodDrainAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, 0.0F, BloodDrainInstance::new);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
	}

	@Override
	protected boolean requiresVampireFullPower() {
		return false;
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
		if (!user.getMainHandItem().isEmpty()) {
			return ConditionCheck.createNegative("hand");
		}
		if (user.level().getDifficulty() == Difficulty.PEACEFUL) {
			return ConditionCheck.createNegative("peaceful");
		}
		LivingEntity target = getDrainTarget(user);
		if (target == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!canDrainBloodFrom(target)) {
			return ConditionCheck.createNegative("blood");
		}
		return ConditionCheck.POSITIVE;
	}

	public static class BloodDrainInstance extends EntityActionInstance {
		public BloodDrainInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			LivingEntity user = getPowerUser();
			if (user != null) {
				captureActionTargetFromAim(user);
			}
		}

		@Override
		public void onSetPhase(ActionPhase newPhase) {
			super.onSetPhase(newPhase);
			if (newPhase == ActionPhase.PERFORM && level().isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user != null) {
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.VAMPIRE_BLOOD_DRAIN.get(), user, this,
							ActionPhase.PERFORM, 1.0F, 1.0F);
				}
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() != ActionPhase.PERFORM) {
				return;
			}
			LivingEntity user = getPowerUser();
			if (user == null || level().isClientSide()) {
				return;
			}
			LivingEntity target = getDrainTarget(user, getActionTargetSnapshot(level()));
			if (target == null || !canDrainBloodFrom(target)) {
				return;
			}
			drainPerform(level(), user, target);
		}

		@Override
		public void onButtonStopHold() {
			forceStop();
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}
	}

	private static LivingEntity getDrainTarget(LivingEntity user) {
		return getDrainTarget(user, getAimTarget(user.level(), user));
	}

	private static LivingEntity getDrainTarget(LivingEntity user, ActionTarget target) {
		if (target.getType() == TargetType.ENTITY) {
			Entity entity = target.getMainEntity();
			if (entity instanceof LivingEntity livingTarget && livingTarget.isAlive()
					&& user.distanceToSqr(livingTarget) <= MAX_RANGE_SQ_ENTITY_TARGET) {
				return livingTarget;
			}
		}
		return null;
	}

	private static ActionTarget getAimTarget(Level level, LivingEntity user) {
		var aim = LivingComponentAction.getAim(user);
		return aim != null ? aim.getTarget().resolveEntityId(level) : ActionTarget.EMPTY;
	}

	private static boolean canDrainBloodFrom(LivingEntity target) {
		if (target.getType().is(ModEntityTypeTags.VAMPIRE_CANNOT_DRAIN)) {
			return false;
		}
		if (target.getType().is(ModEntityTypeTags.VAMPIRE_CAN_DRAIN)) {
			return true;
		}
		return JojoDefinitions.canBleed(target) && !JojoDefinitions.isUndeadOrVampiric(target);
	}

	private static void drainPerform(Level level, LivingEntity user, LivingEntity target) {
		if (target.isDeadOrDying()) {
			return;
		}
		if (drainBlood(user, target, 2.0F)) {
			float modifier = getBloodAndHealModifier(user, target);
			VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElse(null);
			if (data != null) {
				data.addBlood(user, modifier);
			}
			if (data != null && data.isBeingCured() && data.getCuringStage(user) >= 3) {
				float selfDamage = Math.min(modifier * 0.5F, Math.max(user.getHealth() - 1.0F, 0.0F));
				if (selfDamage > 0.0F) {
					user.hurt(DamageUtil.make(level, ModDamageTypes.CURED_VAMPIRE_BLOOD, user), selfDamage);
				}
			}
			if (target.isDeadOrDying() && level instanceof ServerLevel serverLevel) {
				boolean zombieCreated = HungryZombieEntity.createZombie(serverLevel, user, target, false);
				if (user instanceof ServerPlayer player) {
					player.awardStat(Stats.CUSTOM.get(isHuman(target)
							? ModCustomStats.VAMPIRE_PEOPLE_DRAINED
							: ModCustomStats.VAMPIRE_ANIMALS_DRAINED));
					if (zombieCreated) {
						player.awardStat(Stats.CUSTOM.get(ModCustomStats.VAMPIRE_ZOMBIES_CREATED));
					}
					ModCriteriaTriggers.triggerVampirePeopleDrained(player,
							player.getStats().getValue(Stats.CUSTOM.get(ModCustomStats.VAMPIRE_PEOPLE_DRAINED)),
							player.getStats().getValue(Stats.CUSTOM.get(ModCustomStats.VAMPIRE_ZOMBIES_CREATED)));
				}
			}
		}
	}

	private static boolean isHuman(LivingEntity target) {
		return target instanceof Player || target instanceof Npc || target instanceof AbstractIllager;
	}

	private static float getBloodAndHealModifier(LivingEntity user, LivingEntity target) {
		float modifier = bloodDrainMultiplier(user.level());
		if (target instanceof Player) {
			modifier *= 5.0F;
		}
		else if (target instanceof Npc || target instanceof AbstractIllager) {
			modifier *= 4.0F;
		}
		if (PlayerPower.getPowerData(target, ModPlayerPowers.HAMON).isPresent()) {
			modifier *= 1.5F;
		}
		MobEffectInstance freeze = target.getEffect(ModStatusEffects.FREEZE);
		if (freeze != null) {
			modifier *= 1.0F - Math.min((freeze.getAmplifier() + 1) * 0.2F, 1.0F);
		}
		return modifier;
	}

	public static float bloodDrainMultiplier(Level level) {
		var values = JojoModConfig.getCommonConfigInstance(false).bloodDrainMultiplier.get();
		if (values.isEmpty()) {
			return 1.0F;
		}
		int index = Math.max(0, Math.min(level.getDifficulty().getId(), values.size() - 1));
		return values.get(index).floatValue();
	}

	public static boolean drainBlood(LivingEntity attacker, LivingEntity target, float bloodDrainDamage) {
		boolean hurt = target.hurt(DamageUtil.make(attacker.level(), ModDamageTypes.BLOOD_DRAIN, attacker), bloodDrainDamage);
		if (hurt) {
			int effectsLvl = attacker.level().getDifficulty().getId() - 1;
			if (effectsLvl >= 0) {
				addOrExtendEffect(target, MobEffects.MOVEMENT_SLOWDOWN, bloodDrainDamage, effectsLvl);
				addOrExtendEffect(target, MobEffects.DIG_SLOWDOWN, bloodDrainDamage, effectsLvl);
				addOrExtendEffect(target, MobEffects.WEAKNESS, bloodDrainDamage, effectsLvl);
				addOrExtendEffect(target, MobEffects.CONFUSION, bloodDrainDamage, effectsLvl);
			}
		}
		return hurt;
	}

	public static void onUserIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || event.getSource().getDirectEntity() == null) {
			return;
		}
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(target);
		if (action != null && (action.ability instanceof VampirismBloodDrainAbility
				|| action.ability instanceof ZombieDevourAbility)
				&& action.getPhase() == ActionPhase.PERFORM) {
			action.forceStop();
			action.syncPhaseChanges();
		}
	}

	private static void addOrExtendEffect(LivingEntity target, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
			float bloodDrainDamage, int effectsLvl) {
		int duration = (int) (20.0F * bloodDrainDamage);
		MobEffectInstance current = target.getEffect(effect);
		if (current != null) {
			duration += current.getDuration();
		}
		target.addEffect(new MobEffectInstance(effect, duration, effectsLvl, false, true));
	}
}
