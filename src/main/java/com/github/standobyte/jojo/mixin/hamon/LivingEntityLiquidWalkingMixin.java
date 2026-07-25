package com.github.standobyte.jojo.mixin.hamon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.standobyte.jojoimpl.powers.hamon.HamonMovementHelper;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLiquidWalkingMixin {
	@Inject(method = "canStandOnFluid", at = @At("RETURN"), cancellable = true)
	private void jojo_ripples$hamonLiquidWalking(FluidState fluidState, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && HamonMovementHelper.onLiquidWalkingEvent((LivingEntity) (Object) this, fluidState)) {
			cir.setReturnValue(true);
		}
	}
}
