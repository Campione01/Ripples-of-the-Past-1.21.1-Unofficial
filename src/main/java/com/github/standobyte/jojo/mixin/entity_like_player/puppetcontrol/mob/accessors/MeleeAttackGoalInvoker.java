package com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.mob.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

@Mixin(MeleeAttackGoal.class)
public interface MeleeAttackGoalInvoker {
	@Invoker("canPerformAttack") boolean callCanPerformAttack(LivingEntity entity);
	@Invoker("checkAndPerformAttack") void callCheckAndPerformAttack(LivingEntity target);
	@Invoker("getTicksUntilNextAttack") int callGetTicksUntilNextAttack();
	@Accessor("ticksUntilNextAttack") void setTicksUntilNextAttack(int ticksUntilNextAttack);
	@Invoker("resetAttackCooldown") void callResetAttackCooldown();
	@Invoker("isTimeToAttack") boolean callIsTimeToAttack();
}
