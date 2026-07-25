package com.github.standobyte.jojo.item;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.ModItems;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class BubbleGlovesItem extends GlovesItem {
	public static final int MAX_AMMO = 500;

	public BubbleGlovesItem(Properties properties) {
		super(properties);
	}

	public static int getAmmo(ItemStack stack) {
		Integer ammo = stack.get(ModItemDataComponents.BUBBLE_GLOVES_AMMO.get());
		return ammo == null ? MAX_AMMO : Mth.clamp(ammo, 0, MAX_AMMO);
	}

	public static void setAmmo(ItemStack stack, int ammo) {
		stack.set(ModItemDataComponents.BUBBLE_GLOVES_AMMO.get(), Mth.clamp(ammo, 0, MAX_AMMO));
	}

	public static boolean consumeAmmo(ItemStack gloves, int amount, LivingEntity user) {
		int ammo = getAmmo(gloves);
		if (ammo < amount) {
			reload(gloves, user, user.level(), null);
			ammo = getAmmo(gloves);
		}
		if (ammo < amount) {
			setAmmo(gloves, 0);
			return false;
		}
		setAmmo(gloves, ammo - amount);
		if (getAmmo(gloves) <= 0) {
			reload(gloves, user, user.level(), null);
		}
		return true;
	}

	public static boolean reload(ItemStack glovesItem, Entity entity, Level level, @Nullable ItemStack soapBottleItem) {
		if (getAmmo(glovesItem) >= MAX_AMMO) {
			return false;
		}
		if (entity instanceof Player player && player.getAbilities().instabuild) {
			setAmmo(glovesItem, MAX_AMMO);
			return true;
		}
		ItemStack soapItem = soapBottleItem != null && useSoap(soapBottleItem) ? soapBottleItem : findSoap(entity);
		if (soapItem.isEmpty()) {
			return false;
		}
		if (!level.isClientSide()) {
			soapItem.shrink(1);
			giveGlassBottle(entity);
			setAmmo(glovesItem, MAX_AMMO);
		}
		return true;
	}

	private static ItemStack findSoap(Entity entity) {
		if (entity instanceof Player player) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (useSoap(stack)) {
					return stack;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	private static void giveGlassBottle(Entity entity) {
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		if (entity instanceof Player player) {
			if (!player.addItem(bottle)) {
				player.spawnAtLocation(bottle);
			}
		}
		else {
			entity.spawnAtLocation(bottle);
		}
	}

	public static boolean useSoap(ItemStack item) {
		return !item.isEmpty() && item.is(ModItems.SOAP.get());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			return reload(stack, player, level, null)
					? InteractionResultHolder.sidedSuccess(stack, level.isClientSide())
					: InteractionResultHolder.fail(stack);
		}
		return InteractionResultHolder.fail(stack);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return getAmmo(stack) < MAX_AMMO;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * getAmmo(stack) / (float) MAX_AMMO);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0x54D8D2;
	}
}
