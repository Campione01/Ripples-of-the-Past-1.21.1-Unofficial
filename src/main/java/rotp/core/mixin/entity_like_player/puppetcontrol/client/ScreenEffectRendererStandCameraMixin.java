package rotp.core.mixin.entity_like_player.puppetcontrol.client;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.powersystem.standpower.entity.StandEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A first-person view from a Stand whose eyes are inside a block (manual control with standBodyThroughWalls on
 * "manga") shows that block's in-wall overlay, as vanilla does for a player inside a block, instead of the terrain
 * behind it. The user's own in-wall overlay is cancelled while a Stand is controlled (ClientStandController).
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererStandCameraMixin {

	@Shadow
	private static void renderTex(TextureAtlasSprite texture, PoseStack poseStack) {
		throw new AssertionError();
	}

	@Inject(method = "renderScreenEffect", at = @At("HEAD"))
	private static void jojo_ripples$standCameraInWall(Minecraft minecraft, PoseStack poseStack, CallbackInfo ci) {
		if (minecraft.level != null && minecraft.getCameraEntity() instanceof StandEntity stand) {
			BlockPos pos = jojo_ripples$viewBlockingPos(stand);
			if (pos != null) {
				BlockState state = minecraft.level.getBlockState(pos);
				renderTex(minecraft.getBlockRenderer().getBlockModelShaper().getTexture(state, minecraft.level, pos),
						poseStack);
			}
		}
	}

	// ScreenEffectRenderer.getOverlayBlock, for the Stand
	@Unique
	@Nullable
	private static BlockPos jojo_ripples$viewBlockingPos(StandEntity stand) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 8; i++) {
			double x = stand.getX() + (double) (((float) ((i >> 0) % 2) - 0.5F) * stand.getBbWidth() * 0.8F);
			double y = stand.getEyeY() + (double) (((float) ((i >> 1) % 2) - 0.5F) * 0.1F * stand.getScale());
			double z = stand.getZ() + (double) (((float) ((i >> 2) % 2) - 0.5F) * stand.getBbWidth() * 0.8F);
			pos.set(x, y, z);
			BlockState state = stand.level().getBlockState(pos);
			if (state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(stand.level(), pos)) {
				return pos.immutable();
			}
		}
		return null;
	}
}
