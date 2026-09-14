package rotp.core.mixin.client.directional_gravity;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererDirectionalGravityMixin {

	@Inject(
			method = "render(Lnet/minecraft/world/entity/LivingEntity;FF"
					+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;"
							+ "setupRotations(Lnet/minecraft/world/entity/LivingEntity;"
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
					shift = At.Shift.BEFORE))
	private void jojo_ripples$directionalGravityBodyFrame(
			LivingEntity entity, float entityYaw, float partialTick,
			PoseStack poseStack, MultiBufferSource bufferSource,
			int light, CallbackInfo ci) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection(entity);
		if (direction == Direction.DOWN) {
			return;
		}

		// Rotate only the body scope; dispatcher hitboxes and shadows are world-space.
		Matrix3f gravityFrame = new Matrix3f();
		gravityFrame.setColumn(0, jojo_ripples$toWorld(direction, 1, 0, 0));
		gravityFrame.setColumn(1, jojo_ripples$toWorld(direction, 0, 1, 0));
		gravityFrame.setColumn(2, jojo_ripples$toWorld(direction, 0, 0, 1));
		poseStack.mulPose(gravityFrame.getNormalizedRotation(new Quaternionf()));
	}

	@Unique
	private static Vector3f jojo_ripples$toWorld(
			Direction direction, double x, double y, double z) {
		Vec3 transformed = DirectionalGravityTransforms.toWorld(
				direction, new Vec3(x, y, z));
		return new Vector3f((float) transformed.x,
				(float) transformed.y, (float) transformed.z);
	}
}
