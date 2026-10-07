package rotp.core.impl.powers.hamon.abilities;

import rotp.core.init.ModParticles;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 HamonMetalSilverOverdrive: what Overdrive becomes against an armoured or armed target
 * (HamonOverdriveAbility.replaceWithSubAbility).
 */
public class HamonMetalSilverOverdriveAbility extends HamonOverdriveAbility {
	private static final float ENERGY_COST = 1000.0F;

	public HamonMetalSilverOverdriveAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		// 1.16 unlocks(action, false): never a HUD slot or a key of its own
		isSubAbility = true;
	}

	@Override
	public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
		return this;
	}

	// 1.16 HamonMetalSilverOverdrive.checkTarget, after the range check
	@Override
	protected ConditionCheck checkTarget(LivingEntity user, ActionTarget target) {
		ConditionCheck check = super.checkTarget(user, target);
		return check.isPositive() ? ConditionCheck.noMessage(targetedByMSO(target)) : check;
	}

	static boolean targetedByMSO(ActionTarget target) {
		return target.getType() == TargetType.ENTITY
				&& target.getMainEntity() instanceof LivingEntity livingTarget
				&& getDamageMultiplier(livingTarget) > 1.0F;
	}

	static float getDamageMultiplier(LivingEntity target) {
		float multiplier = 1.0F;
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (!target.getItemBySlot(slot).isEmpty()) {
				multiplier += 0.2F;
			}
		}
		for (InteractionHand hand : InteractionHand.values()) {
			if (HamonAbilityHelpers.isItemWeapon(target.getItemInHand(hand))) {
				multiplier += 0.2F;
				break;
			}
		}
		return multiplier;
	}

	// 1.16 hamonParticle(HAMON_SPARK_SILVER): one silver hit emitter, no extra burst; public for gametests
	public static boolean dealMetalSilverDamage(LivingEntity target, LivingEntity user, float damage) {
		return HamonAbilityHelpers.hamonHurt(target, damage, user, user, ModParticles.HAMON_SPARK_SILVER.get());
	}

	@Override
	protected float energyCost() {
		return ENERGY_COST;
	}

	@Override
	protected HamonSkill unlockingSkill() {
		return ModHamonSkills.METAL_SILVER_OVERDRIVE.get();
	}

	// 1.16 HamonMetalSilverOverdrive.dealDamage
	@Override
	protected boolean dealDamage(LivingEntity target, LivingEntity user, float damage) {
		return dealMetalSilverDamage(target, user, getDamageMultiplier(target) * damage);
	}
}
