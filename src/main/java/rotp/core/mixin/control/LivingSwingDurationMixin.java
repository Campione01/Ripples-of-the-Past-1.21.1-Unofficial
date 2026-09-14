package rotp.core.mixin.control;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.core.api.control.LivingSwingDurationModifiers;

import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntity.class)
public abstract class LivingSwingDurationMixin {
	@Inject(
			method = "getCurrentSwingDuration",
			at = @At("RETURN"),
			cancellable = true)
	private void jojo_ripples$applyAddonSwingDuration(
			CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(LivingSwingDurationModifiers.apply(
				(LivingEntity) (Object) this,
				cir.getReturnValue()));
	}
}
