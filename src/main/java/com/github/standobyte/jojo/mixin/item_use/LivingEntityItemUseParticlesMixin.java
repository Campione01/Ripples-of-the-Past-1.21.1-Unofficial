package com.github.standobyte.jojo.mixin.item_use;

import com.github.standobyte.jojo.api.item.ItemUseParticleProvider;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class LivingEntityItemUseParticlesMixin {
	@ModifyArg(
			method = "triggerItemUseEffects"
					+ "(Lnet/minecraft/world/item/ItemStack;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;"
							+ "spawnItemParticles"
							+ "(Lnet/minecraft/world/item/ItemStack;I)V"),
			index = 0)
	private ItemStack jojo_ripples$itemUseParticleStack(
			ItemStack original) {
		if (!(original.getItem()
				instanceof ItemUseParticleProvider provider)) {
			return original;
		}
		try {
			ItemStack replacement = provider.getUseParticleStack(
					(LivingEntity) (Object) this, original);
			return replacement != null ? replacement : original;
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"Item-use particle provider failed for {}",
					original.getItem(),
					error);
			return original;
		}
	}
}
