package rotp.core.impl.powers.hamon.abilities;

import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.SnakeMufflerEntity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class HamonSnakeMufflerAbility extends Ability {
	private static final float ENERGY_COST = 500.0F;

	public HamonSnakeMufflerAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
	}

	public static boolean onUserIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		DamageSource damageSource = event.getSource();
		Entity attacker = damageSource.getEntity();
		if (target.level().isClientSide() || !target.isAlive() || !target.onGround()
				|| !(target instanceof Player playerTarget)
				|| attacker == null
				|| !attacker.is(damageSource.getDirectEntity())
				|| !(attacker instanceof LivingEntity livingAttacker)
				|| !target.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SATIPOROJA_SCARF.get())
				|| playerTarget.getCooldowns().isOnCooldown(ModItems.SATIPOROJA_SCARF.get())) {
			return false;
		}
		return PlayerPower.getPowerData(playerTarget, ModPlayerPowers.HAMON).map(hamon -> {
			if (!hamon.hasEnergy(ENERGY_COST) || !hamon.isSkillLearned(ModHamonSkills.SNAKE_MUFFLER.get())) {
				return false;
			}
			playerTarget.getCooldowns().addCooldown(ModItems.SATIPOROJA_SCARF.get(), 80);
			float efficiency = hamon.getActionEfficiency(ENERGY_COST, false, ModHamonSkills.SNAKE_MUFFLER.get(), playerTarget);
			if (efficiency != 1.0F && efficiency < event.getAmount() / playerTarget.getMaxHealth()) {
				return false;
			}
			JojoModUtil.sayVoiceLine(playerTarget, ModSoundEvents.LISA_LISA_SNAKE_MUFFLER);
			if (!hamon.consumeEnergy(ENERGY_COST, playerTarget)) {
				return false;
			}
			HamonAbilityHelpers.hamonHurt(livingAttacker, playerTarget, 0.75F);
			livingAttacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
			SnakeMufflerEntity snakeMuffler = new SnakeMufflerEntity(target.level(), target);
			snakeMuffler.setEntityToJumpOver(attacker);
			target.level().addFreshEntity(snakeMuffler);
			hamon.syncOnUpdate(playerTarget);
			return true;
		}).orElse(false);
	}
}
