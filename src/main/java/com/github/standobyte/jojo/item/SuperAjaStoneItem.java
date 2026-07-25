package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.init.ModSoundEvents;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class SuperAjaStoneItem extends AjaStoneItem {
	public SuperAjaStoneItem(Properties properties) {
		super(properties);
	}

	@Override
	protected void useStone(Level level, LivingEntity player, ItemStack itemStack, float damage, boolean perk, boolean checkLight) {
		super.useStone(level, player, itemStack, damage * 4F, perk, checkLight);
	}

	@Override
	protected void breakItem(Level level, Player player, ItemStack itemStack, boolean perk) {
		if (!player.getAbilities().instabuild) {
			boolean willBreak = itemStack.getDamageValue() + 1 >= itemStack.getMaxDamage();
			EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
			itemStack.hurtAndBreak(1, player, slot);
			if (willBreak && !level.isClientSide()) {
				player.addItem(new ItemStack(Items.REDSTONE));
			}
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 50;
	}

	@Override
	protected int getCooldown() {
		return 250;
	}

	@Override
	protected float getHamonChargeCost() {
		return 1000;
	}

	@Override
	protected SoundEvent getHamonChargeVoiceLine() {
		return ModSoundEvents.LISA_LISA_SUPER_AJA.get();
	}
}
