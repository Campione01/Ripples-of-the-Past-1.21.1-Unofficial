package com.github.standobyte.jojo.item;

import java.util.List;
import java.util.OptionalInt;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModItemDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class OilItem extends Item {
	public static final int MAX_USES = 120;

	public OilItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		InteractionHand opposite = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack oilStack = player.getItemInHand(hand);
		ItemStack weaponStack = player.getItemInHand(opposite);
		if (!isItemWeapon(weaponStack)) {
			return InteractionResultHolder.fail(oilStack);
		}

		if (!level.isClientSide()) {
			setWeaponOilUses(weaponStack, MAX_USES);
			if (!player.getAbilities().instabuild) {
				oilStack.shrink(1);
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				if (!player.addItem(bottle)) {
					player.spawnAtLocation(bottle);
				}
			}
		}
		level.playSound(player, player.getX(), player.getEyeY(), player.getZ(),
				SoundEvents.BOTTLE_EMPTY, player.getSoundSource(), 1.0F, 1.0F);
		return InteractionResultHolder.consume(oilStack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item." + JojoMod.MOD_ID + ".oil.hint").withStyle(ChatFormatting.GRAY));
	}

	public static OptionalInt remainingOiledUses(ItemStack stack) {
		if (!stack.isEmpty()) {
			Integer uses = stack.get(ModItemDataComponents.HAMON_OILED_USES.get());
			if (uses != null && uses > 0) {
				return OptionalInt.of(uses);
			}
		}
		return OptionalInt.empty();
	}

	public static void setWeaponOilUses(ItemStack weaponStack, int uses) {
		if (weaponStack.isEmpty()) {
			return;
		}
		if (uses > 0) {
			weaponStack.set(ModItemDataComponents.HAMON_OILED_USES.get(), uses);
		}
		else {
			weaponStack.remove(ModItemDataComponents.HAMON_OILED_USES.get());
		}
	}

	private static boolean isItemWeapon(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof GlovesItem) {
			return false;
		}
		if (stack.getItem() instanceof TieredItem) {
			return true;
		}
		return stack.getAttributeModifiers().modifiers().stream().anyMatch(entry ->
				entry.attribute().is(Attributes.ATTACK_DAMAGE)
				&& entry.slot().test(EquipmentSlot.MAINHAND)
				&& entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
				&& entry.modifier().amount() > 0.0D);
	}

	@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
	public static final class ClientTooltip {
		private ClientTooltip() {}

		@SubscribeEvent
		public static void addOiledWeaponTooltip(ItemTooltipEvent event) {
			remainingOiledUses(event.getItemStack()).ifPresent(uses ->
					event.getToolTip().add(Component.translatable("item." + JojoMod.MOD_ID + ".oil.uses", uses)
							.withStyle(ChatFormatting.GOLD)));
		}
	}
}
