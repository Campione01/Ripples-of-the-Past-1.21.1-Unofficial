package com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.mob.accessors;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

@Mixin(AbstractSkeleton.class)
public interface SkeletonAccessor {
	@Invoker("getArrow") AbstractArrow callGetArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon);
}
