package rotp.core.mixin.client.timestop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;

@Mixin(Projectile.class)
public class ProjectileTimeStopSpawnRotationMixin {

	@Inject(method = "recreateFromPacket", at = @At("RETURN"))
	private void jojo_ripples$preserveFrozenSpawnRotation(ClientboundAddEntityPacket packet, CallbackInfo ci) {
		Projectile projectile = (Projectile) (Object) this;
		if (projectile instanceof AbstractArrow && TimeStopState.shouldFreezeClientEntity(projectile)) {
			// A following zero-motion packet must not treat the spawn rotation as uninitialized.
			projectile.yRotO = projectile.getYRot();
			projectile.xRotO = projectile.getXRot();
		}
	}
}
