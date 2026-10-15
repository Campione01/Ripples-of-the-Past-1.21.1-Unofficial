package rotp.core.subsystems.entity_puppetcontrol;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Which block's in-wall texture covers the view of a Stand camera (manual control, where standBodyThroughWalls on
 * "phasing" lets the Stand into blocks), so that a view from inside a block never shows the terrain behind it. Vanilla
 * draws its in-wall overlay only for the player and only in first person; the player's own overlay is left to vanilla.
 */
public final class StandCameraInWall {

	private StandCameraInWall() {}

	/**
	 * @return whether GameRenderer.renderItemInHand calls ScreenEffectRenderer.renderScreenEffect this frame; when it
	 *         does not (third person, back or front), the Stand camera's overlay is drawn after the hand pass instead
	 */
	public static boolean vanillaDrawsScreenEffects(boolean firstPerson, boolean cameraEntitySleeping) {
		return firstPerson && !cameraEntitySleeping;
	}

	/**
	 * The screen effect pass (first person): ScreenEffectRenderer.getOverlayBlock around the eyes of a Stand camera.
	 */
	@Nullable
	public static BlockPos eyeOverlayBlock(@Nullable Entity cameraEntity) {
		if (!(cameraEntity instanceof StandEntity stand)) {
			return null;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 8; i++) {
			double x = stand.getX() + (double) (((float) ((i >> 0) % 2) - 0.5F) * stand.getBbWidth() * 0.8F);
			double y = stand.getEyeY() + (double) (((float) ((i >> 1) % 2) - 0.5F) * 0.1F * stand.getScale());
			double z = stand.getZ() + (double) (((float) ((i >> 2) % 2) - 0.5F) * stand.getBbWidth() * 0.8F);
			pos.set(x, y, z);
			if (blocksView(stand.level(), pos)) {
				return pos.immutable();
			}
		}
		return null;
	}

	/**
	 * Any other camera type: the block the camera position (Camera.getPosition) of a Stand camera is in. In third
	 * person Camera.getMaxZoom clips from the eyes, so with the eyes inside a block the camera stays inside it too.
	 */
	@Nullable
	public static BlockPos cameraOverlayBlock(@Nullable Entity cameraEntity, BlockGetter level, Vec3 cameraPos) {
		if (!(cameraEntity instanceof StandEntity)) {
			return null;
		}
		BlockPos pos = BlockPos.containing(cameraPos);
		return blocksView(level, pos) ? pos : null;
	}

	private static boolean blocksView(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(level, pos);
	}
}
