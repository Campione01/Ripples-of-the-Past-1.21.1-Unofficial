package rotp.core.mixin.entity_like_player.puppetcontrol.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.entity_puppetcontrol.StandCameraInWall;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;

/**
 * A first-person view from a Stand whose eyes are inside a block (manual control with standBodyThroughWalls on
 * "phasing") shows that block's in-wall overlay, as vanilla does for a player inside a block, instead of the terrain
 * behind it. The user's own in-wall overlay is cancelled while a Stand is controlled (ClientStandController). The
 * other camera types are covered after the hand pass (GameRendererStandCameraInWallMixin).
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererStandCameraMixin {

	@Shadow
	private static void renderTex(TextureAtlasSprite texture, PoseStack poseStack) {
		throw new AssertionError();
	}

	@Inject(method = "renderScreenEffect", at = @At("HEAD"))
	private static void jojo_ripples$standCameraInWall(Minecraft minecraft, PoseStack poseStack, CallbackInfo ci) {
		if (minecraft.level != null) {
			BlockPos pos = StandCameraInWall.eyeOverlayBlock(minecraft.getCameraEntity());
			if (pos != null) {
				renderTex(minecraft.getBlockRenderer().getBlockModelShaper().getTexture(
						minecraft.level.getBlockState(pos), minecraft.level, pos), poseStack);
			}
		}
	}
}
