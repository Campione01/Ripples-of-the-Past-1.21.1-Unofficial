package com.github.standobyte.jojo.mixin.entity_like_player.puppetcontrol.mob.accessors;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@Mixin(Entity.class)
public interface EntityFallDamageInvoker {
	@Invoker("checkSupportingBlock") public void invokeCheckSupportingBlock(boolean onGround, @Nullable Vec3 movement);
	
	// can't make this method public with an AT specifically, because specific Entity subclasses override this and keep it protected
	@Invoker("checkFallDamage") public void invokeCheckFallDamage(double y, boolean onGround, BlockState state, BlockPos pos);
}
