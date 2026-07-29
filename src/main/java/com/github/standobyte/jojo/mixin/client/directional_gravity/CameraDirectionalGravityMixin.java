package com.github.standobyte.jojo.mixin.client.directional_gravity;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityTransforms;

import net.minecraft.client.Camera;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

@Mixin(Camera.class)
public abstract class CameraDirectionalGravityMixin {
	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f forwards;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;
	@Shadow
	private Vec3 position;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Inject(method = "setup",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Camera;"
							+ "setRotation(FFF)V",
					shift = At.Shift.AFTER))
	private void jojo_ripples$directionalGravityCameraRotation(
			BlockGetter level, Entity entity, boolean detached,
			boolean thirdPersonReverse, float partialTick,
			CallbackInfo ci) {
		jojo_ripples$applyGravityRotation(entity);
	}

	@Inject(method = "setup",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Camera;"
							+ "setRotation(FF)V",
					shift = At.Shift.AFTER))
	private void jojo_ripples$directionalGravitySleepingCameraRotation(
			BlockGetter level, Entity entity, boolean detached,
			boolean thirdPersonReverse, float partialTick,
			CallbackInfo ci) {
		jojo_ripples$applyGravityRotation(entity);
	}

	@Unique
	private void jojo_ripples$applyGravityRotation(Entity entity) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction == Direction.DOWN) {
			return;
		}

		Matrix3f gravityFrame = new Matrix3f();
		gravityFrame.setColumn(0,
				jojo_ripples$toWorld(direction, 1.0F, 0.0F, 0.0F));
		gravityFrame.setColumn(1,
				jojo_ripples$toWorld(direction, 0.0F, 1.0F, 0.0F));
		gravityFrame.setColumn(2,
				jojo_ripples$toWorld(direction, 0.0F, 0.0F, 1.0F));
		Quaternionf gravityRotation =
				gravityFrame.getNormalizedRotation(new Quaternionf());
		rotation.premul(gravityRotation);
		jojo_ripples$transform(direction, forwards);
		jojo_ripples$transform(direction, up);
		jojo_ripples$transform(direction, left);
	}

	@Inject(method = "setup",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Camera;"
							+ "setPosition(DDD)V",
					shift = At.Shift.AFTER))
	private void jojo_ripples$directionalGravityCameraPosition(
			BlockGetter level, Entity entity, boolean detached,
			boolean thirdPersonReverse, float partialTick,
			CallbackInfo ci) {
		Direction direction =
				DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction == Direction.DOWN) {
			return;
		}

		Vec3 base = new Vec3(
				Mth.lerp((double) partialTick, entity.xo, entity.getX()),
				Mth.lerp((double) partialTick, entity.yo, entity.getY()),
				Mth.lerp((double) partialTick, entity.zo, entity.getZ()));
		double smoothedEyeHeight = position.distanceTo(base);
		setPosition(DirectionalGravityTransforms.eyePosition(
				direction, base, smoothedEyeHeight));
	}

	@Unique
	private static Vector3f jojo_ripples$toWorld(Direction direction,
			float x, float y, float z) {
		Vec3 transformed = DirectionalGravityTransforms.toWorld(
				direction, new Vec3(x, y, z));
		return new Vector3f((float) transformed.x,
				(float) transformed.y, (float) transformed.z);
	}

	@Unique
	private static void jojo_ripples$transform(Direction direction,
			Vector3f vector) {
		vector.set(jojo_ripples$toWorld(
				direction, vector.x, vector.y, vector.z));
	}
}
