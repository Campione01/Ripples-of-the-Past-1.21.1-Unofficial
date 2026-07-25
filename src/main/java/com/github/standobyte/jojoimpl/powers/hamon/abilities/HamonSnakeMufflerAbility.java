package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SnakeMufflerEntity;

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
