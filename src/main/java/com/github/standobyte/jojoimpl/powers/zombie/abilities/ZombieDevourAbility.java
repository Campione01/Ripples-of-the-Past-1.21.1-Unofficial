package com.github.standobyte.jojoimpl.powers.zombie.abilities;

import com.github.standobyte.jojo.client.sound.ClientsideSoundsHelper;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModCustomStats;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
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
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismUtil;
import com.github.standobyte.jojoimpl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;
import com.github.standobyte.jojoimpl.powers.zombie.ZombieData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ZombieDevourAbility extends ZombieActionAbility {
	private static final double MAX_RANGE_SQ_ENTITY_TARGET = 4.0D;

	public ZombieDevourAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, true, 0.0F, DevourInstance::new);
		setButtonHoldPhase(ActionPhase.PERFORM);
		setDefaultPhaseLength(ActionPhase.WINDUP, 0);
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
		if (!user.getMainHandItem().isEmpty()) {
			return ConditionCheck.createNegative("hand");
		}
		if (user.level().getDifficulty() == Difficulty.PEACEFUL) {
			return ConditionCheck.createNegative("peaceful");
		}
		LivingEntity target = getDevourTarget(user);
		if (target == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!JojoDefinitions.canBleed(target) || JojoDefinitions.isUndeadOrVampiric(target)) {
			return ConditionCheck.createNegative("blood");
		}
		return ConditionCheck.POSITIVE;
	}

	public static class DevourInstance extends EntityActionInstance {
		public DevourInstance(EntityActionType ability) {
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
					ClientsideSoundsHelper.playLoopingActionSound(ModSoundEvents.ZOMBIE_DEVOUR.get(), user, this,
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
			LivingEntity target = getDevourTarget(user, getActionTargetSnapshot(level()));
			if (target == null || !JojoDefinitions.canBleed(target) || JojoDefinitions.isUndeadOrVampiric(target)) {
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

	private static LivingEntity getDevourTarget(LivingEntity user) {
		return getDevourTarget(user, getAimTarget(user.level(), user));
	}

	private static LivingEntity getDevourTarget(LivingEntity user, ActionTarget target) {
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

	private static void drainPerform(Level level, LivingEntity user, LivingEntity target) {
		if (target.isDeadOrDying()) {
			return;
		}
		float bloodAndHealModifier = getBloodAndHealModifier(user, target);
		boolean humanTarget = isHuman(target);
		if (VampirismBloodDrainAbility.drainBlood(user, target, 2.0F)) {
			ZombieData zombie = PlayerPower.getPowerData(user, ModPlayerPowers.ZOMBIE).orElse(null);
			if (zombie != null) {
				zombie.addEnergy(user, bloodAndHealModifier);
			}
			float healthBefore = user.getHealth();
			user.heal(bloodAndHealModifier * 0.5F);
			float healed = user.getHealth() - healthBefore;
			if (zombie != null) {
				if (healed > 0.0F) {
					zombie.addEnergy(user, healed * VampirismUtil.healCost(level));
				}
				zombie.syncOnUpdate(user);
			}
			if (target.isDeadOrDying() && level instanceof ServerLevel serverLevel) {
				boolean zombieCreated = HungryZombieEntity.createZombie(serverLevel, null, target, false);
				if (user instanceof ServerPlayer player) {
					player.awardStat(Stats.CUSTOM.get(humanTarget
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
		float modifier = VampirismBloodDrainAbility.bloodDrainMultiplier(user.level());
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
}
