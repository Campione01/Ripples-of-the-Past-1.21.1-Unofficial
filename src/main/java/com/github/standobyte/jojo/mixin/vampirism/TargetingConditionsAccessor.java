package com.github.standobyte.jojo.mixin.vampirism;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

@Mixin(TargetingConditions.class)
public interface TargetingConditionsAccessor {
	@Accessor("selector")
	Predicate<LivingEntity> jojo_ripples$getSelector();
}
