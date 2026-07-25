package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.init.ModArmorMaterials;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SatiporojaScarfBindingEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SatiporojaScarfEntity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SatiporojaScarfItem extends ArmorItem {
	public static final float SCARF_SWING_ENERGY_COST = SatiporojaScarfEntity.SCARF_SWING_ENERGY_COST;
	private static final float MELEE_ENERGY_COST = 500.0F;
	private static final float BINDING_ENERGY_COST = 100.0F;

	public SatiporojaScarfItem(Properties properties) {
		super(ModArmorMaterials.SATIPOROJA_SCARF, ArmorItem.Type.HELMET, properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		return PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).map(hamon -> {
			if (!hamon.isSkillLearned(ModHamonSkills.SATIPOROJA_SCARF.get())) {
				return InteractionResultHolder.pass(stack);
			}
			if (level.isClientSide()) {
				return hamon.hasEnergy(SCARF_SWING_ENERGY_COST)
						? InteractionResultHolder.success(stack)
						: InteractionResultHolder.fail(stack);
			}
			if (!hamon.consumeEnergy(SCARF_SWING_ENERGY_COST, player)) {
				return InteractionResultHolder.fail(stack);
			}
			SatiporojaScarfEntity scarf = new SatiporojaScarfEntity(player, level, handSide(player, hand));
			level.addFreshEntity(scarf);
			player.getCooldowns().addCooldown(this, scarf.ticksLifespan());
			hamon.syncOnUpdate(player);
			return InteractionResultHolder.consume(stack);
		}).orElse(InteractionResultHolder.pass(stack));
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity user) {
		if (user instanceof Player player && player.getCooldowns().isOnCooldown(this)) {
			return false;
		}
		return PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).map(hamon -> {
			if (!hamon.isSkillLearned(ModHamonSkills.SATIPOROJA_SCARF.get())) {
				return false;
			}
			if (user.level().isClientSide()) {
				return true;
			}
			if (hamon.consumeEnergy(MELEE_ENERGY_COST, user)
					&& HamonAbilityHelpers.hamonHurt(target, user, 0.6F)) {
				if (user.isShiftKeyDown()
						&& hamon.isSkillLearned(ModHamonSkills.SNAKE_MUFFLER.get())
						&& hamon.consumeEnergy(BINDING_ENERGY_COST, user)) {
					SatiporojaScarfBindingEntity scarf = new SatiporojaScarfBindingEntity(user, user.level());
					scarf.attachToEntity(target);
					target.addEffect(new MobEffectInstance(ModStatusEffects.STUN, scarf.ticksLifespan()));
					user.level().addFreshEntity(scarf);
					if (user instanceof Player player) {
						player.getCooldowns().addCooldown(this, scarf.ticksLifespan());
					}
				}
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, MELEE_ENERGY_COST);
				hamon.syncOnUpdate(user);
				return true;
			}
			return false;
		}).orElse(false);
	}

	private static HumanoidArm handSide(Player player, InteractionHand hand) {
		HumanoidArm mainArm = player.getMainArm();
		return hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
