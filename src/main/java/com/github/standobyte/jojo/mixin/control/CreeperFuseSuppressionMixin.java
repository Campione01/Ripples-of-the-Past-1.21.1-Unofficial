package com.github.standobyte.jojo.mixin.control;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.control.CreeperFuseSuppressionProviders;

import net.minecraft.world.entity.monster.Creeper;

@Mixin(Creeper.class)
public abstract class CreeperFuseSuppressionMixin {
	@Shadow private int oldSwell;
	@Shadow private int swell;

	@Shadow
	public abstract void setSwellDir(int swellDirection);

	@Inject(method = "tick", at = @At("HEAD"))
	private void jojo_ripples$suppressFuseTick(CallbackInfo ci) {
		jojo_ripples$resetFuseIfSuppressed();
	}

	@Inject(method = "ignite", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$suppressIgnite(CallbackInfo ci) {
		if (jojo_ripples$resetFuseIfSuppressed()) {
			ci.cancel();
		}
	}

	@Inject(
			method = "explodeCreeper",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$suppressExplosion(CallbackInfo ci) {
		if (jojo_ripples$resetFuseIfSuppressed()) {
			ci.cancel();
		}
	}

	private boolean jojo_ripples$resetFuseIfSuppressed() {
		if (!CreeperFuseSuppressionProviders.shouldSuppress(
				(Creeper) (Object) this)) {
			return false;
		}
		oldSwell = 0;
		swell = 0;
		setSwellDir(-1);
		return true;
	}
}
