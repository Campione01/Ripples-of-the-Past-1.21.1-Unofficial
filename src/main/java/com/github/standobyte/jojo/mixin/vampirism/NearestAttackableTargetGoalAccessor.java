package com.github.standobyte.jojo.mixin.vampirism;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

@Mixin(NearestAttackableTargetGoal.class)
public interface NearestAttackableTargetGoalAccessor {
	@Accessor("targetType")
	Class<? extends LivingEntity> jojo_ripples$getTargetType();

	@Accessor("targetConditions")
	TargetingConditions jojo_ripples$getTargetConditions();

	@Accessor("targetConditions")
	void jojo_ripples$setTargetConditions(TargetingConditions targetConditions);
}
