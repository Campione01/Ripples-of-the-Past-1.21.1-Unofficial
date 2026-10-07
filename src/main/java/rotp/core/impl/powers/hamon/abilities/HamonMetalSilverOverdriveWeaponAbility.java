package rotp.core.impl.powers.hamon.abilities;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 HamonMetalSilverOverdriveWeapon: what Overdrive becomes with a weapon in the main hand
 * (HamonOverdriveAbility.replaceWithSubAbility).
 */
public class HamonMetalSilverOverdriveWeaponAbility extends HamonOverdriveAbility {
	private static final float ENERGY_COST = 750.0F;

	public HamonMetalSilverOverdriveWeaponAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		// 1.16 unlocks(action, false): never a HUD slot or a key of its own
		isSubAbility = true;
	}

	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		return this;
	}

	// 1.16 HamonMetalSilverOverdriveWeapon.checkHeldItems: a weapon in the main hand, and no message without one
	@Override
	protected ConditionCheck checkHeldItems(LivingEntity user) {
		return ConditionCheck.noMessage(HamonAbilityHelpers.isItemWeapon(user.getMainHandItem()));
	}

	@Override
	protected float energyCost() {
		return ENERGY_COST;
	}

	@Override
	protected HamonSkill unlockingSkill() {
		return ModHamonSkills.METAL_SILVER_OVERDRIVE.get();
	}

	// 1.16 HamonMetalSilverOverdriveWeapon.dealDamage
	@Override
	protected boolean dealDamage(LivingEntity target, LivingEntity user, float damage) {
		return HamonMetalSilverOverdriveAbility.dealMetalSilverDamage(target, user, damage);
	}
}
