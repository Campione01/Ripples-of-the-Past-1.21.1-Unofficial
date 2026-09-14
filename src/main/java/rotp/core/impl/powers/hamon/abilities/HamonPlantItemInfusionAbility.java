package rotp.core.impl.powers.hamon.abilities;

import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

public class HamonPlantItemInfusionAbility extends Ability {
	private static final float ENERGY_COST = 200.0F;

	public HamonPlantItemInfusionAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
	}

	@Override
	public boolean addToControlSchemeEditing() {
		return false;
	}

	public static boolean chargeItemEntity(Player thrower, ItemEntity itemEntity) {
		if (thrower.level().isClientSide() || itemEntity == null || itemEntity.getItem().isEmpty()) {
			return false;
		}
		HamonData hamon = PlayerPower.getPowerData(thrower, ModPlayerPowers.HAMON).orElse(null);
		if (hamon == null || !hamon.isSkillLearned(ModHamonSkills.PLANT_ITEM_INFUSION.get())
				|| !HamonUtil.isItemLivingMatter(itemEntity.getItem())) {
			return false;
		}
		if (EntityHamonChargeState.get(itemEntity).hasHamonCharge()) {
			return false;
		}
		float efficiency = thrower.getAbilities().instabuild ? 1.0F
				: hamon.getActionEfficiency(ENERGY_COST, false, ModHamonSkills.PLANT_ITEM_INFUSION.get(), thrower);
		if (efficiency <= 0.0F) {
			return false;
		}
		if (!thrower.getAbilities().instabuild && hamon.getHamonEnergyUsageEfficiency(ENERGY_COST, true, thrower) <= 0.0F) {
			return false;
		}
		EntityHamonChargeState.get(itemEntity).setHamonCharge(
				hamon.getHamonDamageMultiplier() * efficiency,
				HamonOrganismInfusionAbility.chargeTicks(hamon, efficiency),
				thrower,
				ENERGY_COST);
		hamon.syncOnUpdate(thrower);
		return true;
	}
}
