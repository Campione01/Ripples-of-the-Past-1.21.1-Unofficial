package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import java.util.function.Function;

import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.EntityActionAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanMode;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

abstract class PillarmanActionAbility extends EntityActionAbility {
	protected final int requiredStage;
	protected final PillarmanMode requiredMode;
	protected final boolean canBeUsedInStone;
	protected final float energyCost;
	protected final float holdEnergyCost;
	protected final float heldWalkSpeed;
	protected final int cooldownTicks;

	protected PillarmanActionAbility(AbilityType<?> abilityType, AbilityId abilityId, int requiredStage,
			PillarmanMode requiredMode, boolean canBeUsedInStone, float energyCost,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		this(abilityType, abilityId, requiredStage, requiredMode, canBeUsedInStone, energyCost, 0.0F, 1.0F, 0,
				createActionObj);
	}

	protected PillarmanActionAbility(AbilityType<?> abilityType, AbilityId abilityId, int requiredStage,
			PillarmanMode requiredMode, boolean canBeUsedInStone, float energyCost, float holdEnergyCost,
			float heldWalkSpeed, int cooldownTicks,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId, createActionObj);
		this.requiredStage = requiredStage;
		this.requiredMode = requiredMode != null ? requiredMode : PillarmanMode.NONE;
		this.canBeUsedInStone = canBeUsedInStone;
		this.energyCost = Math.max(energyCost, 0.0F);
		this.holdEnergyCost = Math.max(holdEnergyCost, 0.0F);
		this.heldWalkSpeed = Math.max(heldWalkSpeed, 0.0F);
		this.cooldownTicks = Math.max(cooldownTicks, 0);
	}

	@Override
	public boolean isAbilityUnlocked(Power<?> context) {
		if (!super.isAbilityUnlocked(context)) {
			return false;
		}
		PillarmanData data = getPillarmanData(context);
		return data != null
				&& (requiredStage < 0 || data.getEvolutionStage() >= requiredStage)
				&& (requiredMode == PillarmanMode.NONE || data.getMode() == requiredMode);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		PillarmanData data = getPillarmanData(context);
		if (data == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!canBeUsedInStone && data.isStoneFormEnabled()) {
			return ConditionCheck.createNegative("stone_form");
		}
		if (data.isAbilityOnCooldown(name())) {
			return ConditionCheck.createNegative("cooldown");
		}
		if (!hasEnergy(context)) {
			return ConditionCheck.createNegative("no_energy_pillarman");
		}
		return ConditionCheck.POSITIVE;
	}

	@Override
	public boolean canContinueAction(EntityActionInstance action) {
		com.github.standobyte.jojo.powersystem.entityaction.ActionPhase phase = action.getPhase();
		boolean checkOngoingRequirements = phase == com.github.standobyte.jojo.powersystem.entityaction.ActionPhase.BUTTON_CHARGE
				|| phase == com.github.standobyte.jojo.powersystem.entityaction.ActionPhase.PERFORM
						&& action instanceof PillarmanHeldActionInstance;
		if (!checkOngoingRequirements) {
			return true;
		}
		LivingEntity user = action.getPowerUser();
		Power<?> context = user != null ? getUserPower(user) : null;
		return context != null && isAbilityAvailable(context) && checkConditions(context).isPositive();
	}

	protected boolean hasEnergy(Power<?> context) {
		return hasEnergy(context, energyCost + holdEnergyCost);
	}

	protected boolean hasEnergy(Power<?> context, float amount) {
		if (isCreative(context)) {
			return true;
		}
		PillarmanData data = getPillarmanData(context);
		return data != null && data.hasEnergy(amount);
	}

	protected boolean consumeEnergy(Power<?> context) {
		if (energyCost <= 0.0F || isCreative(context)) {
			return true;
		}
		PillarmanData data = getPillarmanData(context);
		return data != null && data.consumeEnergy(context.getUser(), energyCost);
	}

	protected boolean consumeHeldEnergy(Power<?> context) {
		return consumePillarmanEnergy(context, holdEnergyCost);
	}

	protected boolean consumePillarmanEnergy(Power<?> context, float amount) {
		if (amount <= 0.0F || isCreative(context)) {
			return true;
		}
		PillarmanData data = getPillarmanData(context);
		return data != null && data.consumeEnergy(context.getUser(), amount);
	}

	protected int getCooldownAfterHold(Power<?> context, int ticksHeld) {
		return cooldownTicks;
	}

	protected int cooldownFromHoldDuration(int cooldown, int ticksHeld, int maxHoldTicks) {
		if (maxHoldTicks > 0) {
			return (int) ((float) cooldown * Math.max(ticksHeld, 0) / (float) maxHoldTicks);
		}
		return cooldown;
	}

	protected void setPillarmanCooldown(Power<?> context, int ticksHeld) {
		if (context == null || isCreative(context)) {
			return;
		}
		PillarmanData data = getPillarmanData(context);
		int cooldown = getCooldownAfterHold(context, ticksHeld);
		if (data != null && cooldown > 0) {
			data.setAbilityCooldown(name(), cooldown);
			data.syncOnUpdate(context.getUser());
		}
	}

	protected void setPillarmanFixedCooldown(Power<?> context, int cooldown) {
		if (context == null || isCreative(context)) {
			return;
		}
		PillarmanData data = getPillarmanData(context);
		if (data != null && cooldown > 0) {
			data.setAbilityCooldown(name(), cooldown);
			data.syncOnUpdate(context.getUser());
		}
	}

	protected PillarmanData getPillarmanData(Power<?> context) {
		if (context == null) {
			return null;
		}
		PowerData data = context.getCurTypeData();
		return data instanceof PillarmanData pillarman ? pillarman : null;
	}

	protected boolean isCreative(Power<?> context) {
		return context != null && context.getUser() instanceof Player player && player.getAbilities().instabuild;
	}

	protected static void setBladesVisible(LivingEntity user, boolean visible) {
		PlayerPower.getPowerData(user, PillarmanPowerType.PILLAR_MAN).ifPresent(data -> {
			data.setBladesVisible(visible);
			data.syncOnUpdate(user);
		});
	}

	protected static void sparkEffect(Entity target, int count) {
		if (target.level() instanceof ServerLevel level) {
			double width = Math.max(target.getBbWidth() * 0.25D, 0.1D);
			double height = Math.max(target.getBbHeight() * 0.25D, 0.1D);
			level.sendParticles(ModParticles.LIGHT_SPARK.get(), target.getX(), target.getY(0.5D), target.getZ(),
					count, width, height, width, 0.05D);
		}
	}

	protected static void auraEffect(LivingEntity user, ParticleOptions particles, int intensity) {
		Level level = user.level();
		if (!level.isClientSide()) {
			return;
		}
		RandomSource random = user.getRandom();
		for (int i = 0; i < intensity; i++) {
			Vec3 particlePos = user.position().add(
					(random.nextDouble() - 0.5D) * (user.getBbWidth() + 0.5D),
					random.nextDouble() * (user.getBbHeight() * 0.5D),
					(random.nextDouble() - 0.5D) * (user.getBbWidth() + 0.5D));
			level.addParticle(particles, particlePos.x, particlePos.y, particlePos.z, 0.0D, 0.0D, 0.0D);
		}
	}

	protected static DamageSource meleeDamageSource(Level level, LivingEntity user) {
		return user instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(user);
	}

	protected static class PillarmanHeldActionInstance extends EntityActionInstance {
		protected int ticksHeld;

		public PillarmanHeldActionInstance(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onSetPhase(com.github.standobyte.jojo.powersystem.entityaction.ActionPhase newPhase) {
			PillarmanActionAbility pillarmanAbility = pillarmanAbility();
			if (pillarmanAbility != null
					&& newPhase == com.github.standobyte.jojo.powersystem.entityaction.ActionPhase.PERFORM) {
				float heldWalkSpeed = pillarmanAbility.heldWalkSpeed;
				userWalkSpeed = heldWalkSpeed;
			}
			else {
				userWalkSpeed = 1.0F;
			}
		}

		@Override
		public void actionTick() {
			if (getPhase() != com.github.standobyte.jojo.powersystem.entityaction.ActionPhase.PERFORM) {
				return;
			}
			if (getCurPhaseLength() < Integer.MAX_VALUE && getPhaseTick() + 1 >= getCurPhaseLength()) {
				ticksHeld++;
				return;
			}
			PillarmanActionAbility pillarmanAbility = pillarmanAbility();
			LivingEntity user = getPowerUser();
			Power<?> context = pillarmanAbility != null && user != null ? pillarmanAbility.getUserPower(user) : null;
			if (!level().isClientSide() && pillarmanAbility != null && !pillarmanAbility.consumeHeldEnergy(context)) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			heldTick(pillarmanAbility, user, context, ticksHeld);
			if (!level().isClientSide() && context != null && context.getCurTypeData() instanceof PillarmanData data
					&& ticksHeld % 5 == 0) {
				data.syncOnUpdate(context.getUser());
			}
			ticksHeld++;
		}

		protected void heldTick(PillarmanActionAbility pillarmanAbility, LivingEntity user, Power<?> context, int ticksHeld) {
		}

		@Override
		public void onButtonStopHold() {
			forceStop();
			syncPhaseChanges();
		}

		@Override
		public void onActionCleared(EntityActionInstance newAction) {
			PillarmanActionAbility pillarmanAbility = pillarmanAbility();
			LivingEntity user = getPowerUser();
			if (!level().isClientSide() && pillarmanAbility != null && user != null) {
				pillarmanAbility.setPillarmanCooldown(pillarmanAbility.getUserPower(user), ticksHeld);
			}
			userWalkSpeed = 1.0F;
		}

		protected PillarmanActionAbility pillarmanAbility() {
			return ability instanceof PillarmanActionAbility pillarman ? pillarman : null;
		}

		protected int getTicksHeld() {
			return ticksHeld;
		}
	}
}
