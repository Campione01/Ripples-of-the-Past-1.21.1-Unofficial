package com.github.standobyte.jojo.item;

import com.github.standobyte.jojo.init.ModItems;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class SoapItem extends Item {
	public SoapItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int inventorySlot, boolean isSelected) {
		if (entity instanceof Player player) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack item = player.getInventory().getItem(i);
				if (!item.isEmpty() && item.is(ModItems.BUBBLE_GLOVES.get()) && BubbleGlovesItem.getAmmo(item) <= 0) {
					BubbleGlovesItem.reload(item, entity, level, stack);
					return;
				}
			}
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
		super.finishUsingItem(stack, level, living);
		if (!level.isClientSide()) {
			living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0, false, true, true));
			living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 1, false, true, true));
		}
		if (living instanceof Player player && player.getAbilities().instabuild) {
			return stack;
		}
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		stack.shrink(1);
		if (stack.isEmpty()) {
			return bottle;
		}
		if (living instanceof Player player) {
			if (!player.addItem(bottle)) {
				player.spawnAtLocation(bottle);
			}
		}
		else {
			living.spawnAtLocation(bottle);
		}
		return stack;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 32;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public SoundEvent getDrinkingSound() {
		return SoundEvents.HONEY_DRINK;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return ItemUtils.startUsingInstantly(level, player, hand);
	}
}
