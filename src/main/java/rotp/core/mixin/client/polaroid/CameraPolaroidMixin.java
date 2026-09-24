package rotp.core.mixin.client.polaroid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.client.polaroid.PolaroidHelper;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 PolaroidHelper.pictureCameraSetup: a photo with its own camera position is taken from there, with the camera
 * detached, so the local player is drawn in it. NeoForge fires ComputeCameraAngles before Camera.setup places the
 * camera, so the position is set here, after it.
 */
@Mixin(Camera.class)
public abstract class CameraPolaroidMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Inject(method = "setup", at = @At("TAIL"))
	private void jojo_ripples$polaroidCameraPosition(BlockGetter level, Entity entity, boolean detachedCamera,
			boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
		Vec3 photoCamera = PolaroidHelper.photoCameraPosition();
		if (photoCamera != null) {
			setPosition(photoCamera);
			this.detached = true;
		}
	}
}
