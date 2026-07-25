package com.github.standobyte.jojo.enchantment;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModEnchantments;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class GlovesSpeedEnchantment {
	private static final ResourceLocation MODIFIER_ID = JojoMod.resLoc("gloves_speed/equipped");
	private static final double MAINHAND_MULTIPLIER_PER_LEVEL = 0.1D;
	private static final double OFFHAND_MULTIPLIER_PER_LEVEL = 0.025D;

	private GlovesSpeedEnchantment() {}

	@SubscribeEvent
	public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()
				|| event.getSlot() != EquipmentSlot.MAINHAND && event.getSlot() != EquipmentSlot.OFFHAND) {
			return;
		}

		int mainhandLevel = getLevel(entity.getMainHandItem());
		int offhandLevel = getLevel(entity.getOffhandItem());
		AttributeInstance attackSpeed = entity.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed == null) {
			return;
		}

		attackSpeed.removeModifier(MODIFIER_ID);
		double amount = modifierAmount(mainhandLevel, offhandLevel);
		if (amount > 0.0D) {
			attackSpeed.addTransientModifier(new AttributeModifier(MODIFIER_ID, amount,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static double modifierAmount(int mainhandLevel, int offhandLevel) {
		if (mainhandLevel > 0) {
			return MAINHAND_MULTIPLIER_PER_LEVEL * mainhandLevel;
		}
		if (offhandLevel > 0) {
			return OFFHAND_MULTIPLIER_PER_LEVEL * offhandLevel;
		}
		return 0.0D;
	}

	private static int getLevel(ItemStack stack) {
		for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
			if (entry.getKey().is(ModEnchantments.GLOVES_SPEED)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}
}
