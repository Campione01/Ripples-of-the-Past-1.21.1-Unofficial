package rotp.core.mixin.entity_like_player.puppetcontrol.client;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.entity_puppetcontrol.StandCameraInWall;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Third person (back and front): GameRenderer.renderItemInHand skips the screen effects, and Camera.getMaxZoom leaves
 * the camera inside the block the Stand's eyes are in, so the in-wall overlay of a Stand camera is drawn here, at the
 * point and with the projection and pose vanilla's first-person overlay has.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererStandCameraInWallMixin {
	@Shadow @Final Minecraft minecraft;
	@Shadow private boolean panoramicMode;

	@Inject(method = "renderItemInHand", at = @At("TAIL"))
	private void jojo_ripples$standCameraInWall(Camera camera, float partialTick, Matrix4f projectionMatrix,
			CallbackInfo ci) {
		if (panoramicMode || minecraft.level == null) {
			return;
		}
		Entity cameraEntity = minecraft.getCameraEntity();
		boolean sleeping = cameraEntity instanceof LivingEntity living && living.isSleeping();
		if (StandCameraInWall.vanillaDrawsScreenEffects(minecraft.options.getCameraType().isFirstPerson(), sleeping)) {
			return;
		}
		BlockPos pos = cameraEntity == null ? null : StandCameraInWall.cameraOverlayBlock(cameraEntity, minecraft.level,
				camera.getPosition(), cameraEntity.getEyePosition(partialTick));
		if (pos != null) {
			ScreenEffectRendererInvoker.jojo_ripples$renderTex(minecraft.getBlockRenderer().getBlockModelShaper()
					.getTexture(minecraft.level.getBlockState(pos), minecraft.level, pos), new PoseStack());
		}
	}
}
