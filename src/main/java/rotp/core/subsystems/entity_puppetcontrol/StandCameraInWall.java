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
	// Camera.getMaxZoom keeps the camera 0.1 short of a block from the eyes; nearer than this, the zoom has collapsed
	public static final double COLLAPSED_ZOOM = 0.15D;
	// Camera.getMaxZoom's own box around the camera position
	private static final double CAMERA_BOX = 0.1D;

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
	 * When the zoom of a Stand that passes through blocks has collapsed onto its eyes (in or next to a block it is
	 * entering or leaving), the near plane can reach into a block the camera point is not in, so Camera.getMaxZoom's
	 * box around the camera is checked too; a camera the zoom left further out is judged by its own point only.
	 * @param eyePos the camera entity's eye position this frame
	 */
	@Nullable
	public static BlockPos cameraOverlayBlock(@Nullable Entity cameraEntity, BlockGetter level, Vec3 cameraPos, Vec3 eyePos) {
		if (!(cameraEntity instanceof StandEntity stand)) {
			return null;
		}
		BlockPos pos = BlockPos.containing(cameraPos);
		if (blocksView(level, pos)) {
			return pos;
		}
		if (stand.noPhysics && cameraPos.distanceToSqr(eyePos) < COLLAPSED_ZOOM * COLLAPSED_ZOOM) {
			for (int i = 0; i < 8; i++) {
				BlockPos corner = BlockPos.containing(cameraPos.add(
						((i & 1) * 2 - 1) * CAMERA_BOX, ((i >> 1 & 1) * 2 - 1) * CAMERA_BOX, ((i >> 2 & 1) * 2 - 1) * CAMERA_BOX));
				if (blocksView(level, corner)) {
					return corner;
				}
			}
		}
		return null;
	}

	private static boolean blocksView(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(level, pos);
	}
}
