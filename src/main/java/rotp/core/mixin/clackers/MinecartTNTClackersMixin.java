package rotp.core.mixin.clackers;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.core.customobjects.entity_projectile.ClackersEntity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.MinecartTNT;

// The 1.16 Clackers were arrows, and a burning arrow explodes a TNT minecart at once.
@Mixin(MinecartTNT.class)
public abstract class MinecartTNTClackersMixin {
	@Shadow
	protected abstract void explode(@Nullable DamageSource damageSource, double radiusModifier);

	@Inject(method = "hurt", at = @At("HEAD"))
	private void jojo$explodeByBurningClackersLikeArrows(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (source.getDirectEntity() instanceof ClackersEntity clackers && clackers.isOnFire()) {
			MinecartTNT minecart = (MinecartTNT) (Object) this;
			explode(minecart.damageSources().explosion(minecart, source.getEntity()), clackers.getDeltaMovement().lengthSqr());
		}
	}
}
