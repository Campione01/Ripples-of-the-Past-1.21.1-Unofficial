package com.github.standobyte.jojoimpl.powers.vampirism.abilities;

import java.util.function.Function;

import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.ability.EntityActionAbility;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismData;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class VampirismActionAbility extends EntityActionAbility {
	private final int maxCuringStage;
	private final float bloodCostGate;

	public VampirismActionAbility(AbilityType<?> abilityType, AbilityId abilityId,
			int maxCuringStage, float bloodCostGate,
			Function<EntityActionType, ? extends EntityActionInstance> createActionObj) {
		super(abilityType, abilityId, createActionObj);
		this.maxCuringStage = maxCuringStage;
		this.bloodCostGate = bloodCostGate;
	}

	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		return super.isAbilityAvailable(context) && isUnlockedForVampireState(context);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		if (!check.isPositive()) {
			return check;
		}
		VampirismData data = getVampirismData(context);
		LivingEntity user = context.getUser();
		if (data == null || user == null) {
			return ConditionCheck.NEGATIVE;
		}
		if (!isUnlockedForVampireState(context)) {
			return ConditionCheck.NEGATIVE;
		}
		if (data.getCuringStage(context.getUser()) > maxCuringStage) {
			return ConditionCheck.NEGATIVE;
		}
		if (data.isAbilityOnCooldown(name())) {
			return ConditionCheck.createNegative("cooldown");
		}
		if (bloodCostGate > 0.0F && !data.hasBlood(user, bloodCostGate)) {
			return ConditionCheck.NEGATIVE;
		}
		return ConditionCheck.POSITIVE;
	}

	protected float getBloodCostGate() {
		return bloodCostGate;
	}

	protected boolean requiresVampireFullPower() {
		return true;
	}

	protected boolean isUnlockedForVampireState(Power<?> context) {
		if (!requiresVampireFullPower()) {
			return true;
		}
		VampirismData data = getVampirismData(context);
		return data != null && data.isVampireAtFullPower();
	}

	protected static VampirismData getVampirismData(Power<?> context) {
		return context != null && context.getCurTypeData() instanceof VampirismData data ? data : null;
	}

	protected static VampirismData getVampirismData(LivingEntity user, EntityActionInstance action) {
		return action != null ? getVampirismData(action.ability instanceof VampirismActionAbility ability
				? ability.getUserPower(user) : null) : null;
	}

	protected static VampirismData getVampirismData(LivingEntity user) {
		return user != null ? PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElse(null) : null;
	}

	protected static boolean consumeBlood(LivingEntity user, float amount) {
		if (amount <= 0.0F) {
			return true;
		}
		VampirismData data = getVampirismData(user);
		return data != null && data.consumeBlood(user, amount);
	}

	protected static void addBlood(LivingEntity user, float amount) {
		VampirismData data = getVampirismData(user);
		if (data != null) {
			data.addBlood(user, amount);
		}
	}

	protected void setVampirismCooldown(Power<?> context, int cooldown, int totalCooldown) {
		if (context == null || isCreative(context)) {
			return;
		}
		VampirismData data = getVampirismData(context);
		if (data != null && cooldown > 0) {
			data.setAbilityCooldown(name(), cooldown, totalCooldown);
			data.syncOnUpdate(context.getUser());
		}
	}

	protected boolean isCreative(Power<?> context) {
		return context != null && context.getUser() instanceof Player player && player.getAbilities().instabuild;
	}
}
