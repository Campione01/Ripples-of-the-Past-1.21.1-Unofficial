package com.github.standobyte.jojo.mixin.crafting;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojo.api.control.PlayerOperation;
import com.github.standobyte.jojo.api.control.PlayerOperationPolicies;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(Slot.class)
public abstract class CraftingResultSlotTakeMixin {

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void rotp$interceptCraftingResultMayPickup(
			Player player,
			CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ResultSlot
				&& player instanceof ServerPlayer serverPlayer
				&& PlayerOperationPolicies.intercept(
						serverPlayer,
						PlayerOperation.CRAFT_RESULT_TAKE)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tryRemove", at = @At("HEAD"), cancellable = true)
	private void rotp$interceptCraftingResultTake(
			int count,
			int decrement,
			Player player,
			CallbackInfoReturnable<Optional<ItemStack>> cir) {
		if ((Object) this instanceof ResultSlot
				&& player instanceof ServerPlayer serverPlayer
				&& PlayerOperationPolicies.intercept(
						serverPlayer,
						PlayerOperation.CRAFT_RESULT_TAKE)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
