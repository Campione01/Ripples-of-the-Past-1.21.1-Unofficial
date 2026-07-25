package com.github.standobyte.jojoimpl.powers.hamon.abilities;

import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.item.BubbleGlovesItem;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HamonSoapHelper {
	private HamonSoapHelper() {}

	public enum TookSoapFrom {
		BOTTLE,
		GLOVES,
		NONE
	}

	public static ConditionCheck checkSoap(LivingEntity entity) {
		ItemStack item = getSoapItem(entity);
		if (item.isEmpty()) {
			return ConditionCheck.createNegative("soap");
		}
		if (item.is(ModItems.BUBBLE_GLOVES.get()) && BubbleGlovesItem.getAmmo(item) <= 0) {
			return ConditionCheck.createNegative("gloves_no_soap");
		}
		return ConditionCheck.POSITIVE;
	}

	public static TookSoapFrom consumeSoap(LivingEntity entity, int glovesAmmo) {
		ItemStack soapItem = getSoapItem(entity);
		if (soapItem.isEmpty()) {
			return TookSoapFrom.NONE;
		}
		if (soapItem.is(ModItems.SOAP.get())) {
			soapItem.shrink(1);
			giveGlassBottle(entity);
			return TookSoapFrom.BOTTLE;
		}
		return BubbleGlovesItem.consumeAmmo(soapItem, glovesAmmo, entity) ? TookSoapFrom.GLOVES : TookSoapFrom.NONE;
	}

	public static ItemStack getSoapItem(LivingEntity entity) {
		ItemStack soapItem = entity.getMainHandItem();
		if (isSoapSource(soapItem)) {
			return soapItem;
		}
		soapItem = entity.getOffhandItem();
		if (isSoapSource(soapItem)) {
			return soapItem;
		}
		return ItemStack.EMPTY;
	}

	public static boolean isSoapSource(ItemStack item) {
		return !item.isEmpty() && (item.is(ModItems.SOAP.get()) || item.is(ModItems.BUBBLE_GLOVES.get()));
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
}
