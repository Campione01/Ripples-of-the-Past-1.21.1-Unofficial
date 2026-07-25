package com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.mob.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.goal.RangedAttackGoal;

@Mixin(RangedAttackGoal.class)
public interface RangedAttackGoalAccessor {
	@Accessor("attackTime") int getAttackTime();
	@Accessor("attackTime") void setAttackTime(int attackTime);
	@Accessor("attackIntervalMin") int getAttackIntervalMin();
	@Accessor("attackIntervalMax") int getAttackIntervalMax();
}
