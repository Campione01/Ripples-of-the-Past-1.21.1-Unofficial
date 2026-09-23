package rotp.core.impl.powers.zombie.abilities;

import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModCustomStats;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.impl.powers.vampirism.VampirismUtil;
import rotp.core.impl.powers.vampirism.abilities.VampirismActionAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
import rotp.core.impl.powers.zombie.ZombieData;

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
		ConditionCheck check = checkUserConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		LivingEntity target = getDevourTarget(context.getUser());
		if (target == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!JojoDefinitions.canBleed(target) || JojoDefinitions.isUndeadOrVampiric(target)) {
			return ConditionCheck.createNegative("blood");
		}
		return ConditionCheck.POSITIVE;
	}

	private ConditionCheck checkUserConditions(Power<?> context) {
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
		return ConditionCheck.POSITIVE;
	}

	private ConditionCheck checkHoldConditions(Power<?> context) {
		// 1.16 PowerBaseImpl.checkRequirements ran every held tick, the stun check included.
		ConditionCheck check = checkMainModLogicConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		ZombieData zombie = getZombieData(context);
		if (zombie != null && zombie.isDisguiseEnabled()) {
			// 1.16 ZombieDevour.checkTarget: during a hold the disguise only paused devouring.
			return ConditionCheck.NEGATIVE_CONTINUE_HOLD;
		}
		return checkUserConditions(context);
	}

	public static class DevourInstance extends EntityActionInstance {
		public DevourInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void _tickAction() {
			// 1.16: a user stopped in time did not tick, so devouring waited for time to resume.
			if (!VampirismActionAbility.isFrozenInStoppedTime(getPowerUser())) {
				super._tickAction();
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
			if (!(ability instanceof ZombieDevourAbility devourAbility)) {
				return;
			}
			Power<?> context = devourAbility.getUserPower(user);
			ConditionCheck check = context != null ? devourAbility.checkHoldConditions(context) : ConditionCheck.NEGATIVE;
			if (check.shouldContinueHold()) {
				return;
			}
			if (!check.isPositive()) {
				// 1.16 PowerBaseImpl.tickHeldAction: a failed condition (stun, hand, peaceful) ends the
				// hold with its message; a new press is needed to resume.
				ConditionCheck.sendActionFailedMessage(devourAbility, check, user);
				forceStop();
				syncPhaseChanges();
				return;
			}
			// 1.16 re-read the live mouse target every held tick; without a devourable
			// target in reach devouring only pauses (NEGATIVE_CONTINUE_HOLD).
			LivingEntity target = getDevourTarget(user);
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
