package com.github.standobyte.jojo.client.itemrender;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.item.CassetteRecordedItem;
import com.github.standobyte.jojo.item.TommyGunItem;
import com.github.standobyte.jojo.item.cassette.CassetteData;
import com.github.standobyte.jojo.item.StoneMaskItem;
import com.github.standobyte.jojo.mechanics.standarrow.StandArrowItem;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

public class ModItemModelOverrides {

	public static void register() {
//		CustomIconItem.registerModelOverride();
		ItemProperties.register(ModItems.KNIFE.get(), JojoMod.resLoc("count"), (itemStack, clientWorld, livingEntity, seed) -> {
			return livingEntity != null ? itemStack.getCount() : 1;
		});
		ItemProperties.register(ModItems.STONE_MASK.get(), JojoMod.resLoc("stone_mask_activated"), (itemStack, clientWorld, livingEntity, seed) -> {
			return StoneMaskItem.getActivatedTicks(itemStack) > 0 ? 1 : 0;
		});
		ItemProperties.register(ModItems.AJA_STONE_MASK.get(), JojoMod.resLoc("aja_stone_mask_activated"), (itemStack, clientWorld, livingEntity, seed) -> {
			return StoneMaskItem.getActivatedTicks(itemStack) > 0 ? 1 : 0;
		});
		ItemProperties.register(ModItems.TOMMY_GUN.get(), JojoMod.resLoc("swing"), (itemStack, clientWorld, livingEntity, seed) -> {
			return livingEntity != null && livingEntity.swinging && livingEntity.getItemInHand(livingEntity.swingingArm) == itemStack ? 1 : 0;
		});
		ItemProperties.register(ModItems.TOMMY_GUN.get(), JojoMod.resLoc("gunshot"), (itemStack, clientWorld, livingEntity, seed) -> {
			return TommyGunItem.getGunshotTick(itemStack) > 0 ? 1 : 0;
		});
		ItemProperties.register(ModItems.STAND_ARROW_SHARD.get(), JojoMod.resLoc("shard_variant"), (itemStack, clientWorld, livingEntity, seed) -> {
			Integer variant = itemStack.get(ModItemDataComponents.ARROW_SHARD_VARIANT);
			return variant != null ? variant.floatValue() : 0;
		});
		ItemProperties.register(Items.BOW, JojoMod.resLoc("stand_arrow"), (itemStack, clientWorld, livingEntity, seed) -> {
			return livingEntity != null && livingEntity.isUsingItem() && livingEntity.getUseItem() == itemStack
					&& livingEntity.getProjectile(itemStack).getItem() instanceof StandArrowItem ? 1 : 0;
		});
		ItemProperties.register(Items.CROSSBOW, JojoMod.resLoc("stand_arrow"), (itemStack, clientWorld, livingEntity, seed) -> {
			ChargedProjectiles chargedProjectiles = itemStack.get(DataComponents.CHARGED_PROJECTILES);
			return livingEntity != null && chargedProjectiles != null && (
					chargedProjectiles.contains(ModItems.STAND_ARROW.get()) ||
					chargedProjectiles.contains(ModItems.STAND_ARROW_BEETLE.get()) ||
					chargedProjectiles.contains(ModItems.STAND_ARROW_METEORITE.get())) ? 1 : 0;
		});
//		ItemProperties.register(ModItems.STAND_DISC.get(), JojoMod.resLoc("stand_id"), (itemStack, clientWorld, livingEntity, seed) -> {
//			return StandDiscItem.validStandDisc(itemStack, true) ? JojoCustomRegistries.STANDS.getNumericId(StandDiscItem.getStandFromStack(itemStack).getType().getRegistryName()) : -1;
//		});
		ItemProperties.register(ModItems.CASSETTE_RECORDED.get(), JojoMod.resLoc("cassette_distortion"), (itemStack, clientWorld, livingEntity, seed) -> {
			return CassetteRecordedItem.getCassetteData(itemStack)
					.map(cap -> Math.min(cap.generation(), CassetteData.MAX_GENERATION))
					.orElse(0).floatValue();
		});
//		ItemProperties.register(ModItems.POLAROID.get(), JojoMod.resLoc("is_held"), (itemStack, clientWorld, livingEntity, seed) -> {
//			return livingEntity != null && (livingEntity.getItemInHand(Hand.MAIN_HAND) == itemStack || livingEntity.getItemInHand(Hand.OFF_HAND) == itemStack) ? 1 : 0;
//		});
	}
}
