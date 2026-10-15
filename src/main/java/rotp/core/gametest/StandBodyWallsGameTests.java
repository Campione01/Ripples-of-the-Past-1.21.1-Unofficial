package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_puppetcontrol.StandCameraInWall;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server setting standBodyThroughWalls (owner, 2026-10-15): in the default "standard" mode a manually controlled
 * Stand collides with blocks, as 1.16 StandEntity.shouldHaveNoPhysics had it; in "phasing" mode it passes through them
 * while it performs no action. A Stand with a solid body (standCanHaveNoPhysics false, as rotp-rhcp's) collides in
 * both. The server moves a manually controlled Stand with MoverType.PLAYER (ClStandManualMovementPacket). Own batches:
 * the mode is global while a batch runs.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandBodyWallsGameTests {
	private static final String BATCH = "stand_body_walls";
	private static final String RUNTIME_BATCH = "stand_body_walls_runtime";
	private static final double EPS = 1.0E-6;

	@Nullable
	private static Scene active;

	private StandBodyWallsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void controlledStandStopsAtAWallByDefaultAndEntersItWhenPhasing(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			double wallZ = scene.wallAhead();
			scene.stand.setManuallyControlled(true);

			helper.assertTrue(JojoModConfig.BODY_MODE_STANDARD.equals(scene.mode.getDefault()),
					"The Stand body setting must default to standard: " + scene.mode.getDefault());
			scene.setMode(scene.mode.getDefault());
			helper.assertFalse(scene.stand.noPhysics, "standard: a manually controlled Stand must collide");
			AABB box = scene.moveForward();
			helper.assertTrue(box.maxZ <= wallZ + EPS,
					"standard: the manually controlled Stand entered the stone: " + box + ", stone from z " + wallZ);

			scene.setMode(JojoModConfig.BODY_MODE_PHASING);
			helper.assertTrue(scene.stand.noPhysics, "phasing: a manually controlled Stand must have no physics");
			box = scene.moveForward();
			helper.assertTrue(scene.stand.getZ() > wallZ,
					"phasing: the manually controlled Stand stopped at the stone instead of entering it: " + box);
			helper.succeed();
		}
		finally {
			scene.close();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void standWithASolidBodyCollidesInBothModes(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			double wallZ = scene.wallAhead();
			scene.stand.setNoPhysics(false);
			scene.stand.standCanHaveNoPhysics = false;
			scene.stand.setManuallyControlled(true);
			for (String mode : List.of(JojoModConfig.BODY_MODE_STANDARD, JojoModConfig.BODY_MODE_PHASING)) {
				scene.setMode(mode);
				helper.assertFalse(scene.stand.noPhysics, mode + ": a Stand with a solid body must collide");
				AABB box = scene.moveForward();
				helper.assertTrue(box.maxZ <= wallZ + EPS,
						mode + ": a Stand with a solid body entered the stone: " + box + ", stone from z " + wallZ);
			}
			helper.succeed();
		}
		finally {
			scene.stand.standCanHaveNoPhysics = true;
			scene.close();
		}
	}

	// the view from inside a block is never an x-ray: first person by the eyes, F5 back and front by the camera position
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void standCameraInsideABlockShowsItsInWallTextureInEveryCameraType(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(StandCameraInWall.vanillaDrawsScreenEffects(true, false),
					"First person: vanilla's screen effect pass must be the one that draws the overlay");
			helper.assertFalse(StandCameraInWall.vanillaDrawsScreenEffects(false, false),
					"Third person: vanilla draws no screen effects, so the Stand camera's overlay must be drawn after the hand pass");
			helper.assertFalse(StandCameraInWall.vanillaDrawsScreenEffects(true, true),
					"A sleeping camera entity gets no screen effects from vanilla");

			StandEntity stand = scene.stand;
			List<BlockPos> eyeBlocks = new ArrayList<>();
			for (double dy : new double[] { -0.05, 0.05 }) {
				BlockPos pos = BlockPos.containing(stand.getX(), stand.getEyeY() + dy * stand.getScale(), stand.getZ());
				if (!eyeBlocks.contains(pos)) eyeBlocks.add(pos);
			}
			BlockPos cameraBlock = helper.absolutePos(new BlockPos(3, 3, 1));
			helper.assertFalse(eyeBlocks.contains(cameraBlock), "Fixture: the camera block overlaps the eyes");
			Vec3 camera = Vec3.atCenterOf(cameraBlock);

			for (BlockState state : List.of(Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
					Blocks.WATER.defaultBlockState(), Blocks.AIR.defaultBlockState())) {
				eyeBlocks.forEach(pos -> scene.put(pos, state));
				scene.put(cameraBlock, state);
				BlockPos eyeOverlay = StandCameraInWall.eyeOverlayBlock(stand);
				BlockPos cameraOverlay = StandCameraInWall.cameraOverlayBlock(stand, level, camera);
				if (state.is(Blocks.STONE)) {
					helper.assertTrue(eyeOverlay != null && eyeBlocks.contains(eyeOverlay),
							"First person: a Stand's eyes in stone must show the stone's in-wall texture; got " + eyeOverlay);
					helper.assertTrue(cameraBlock.equals(cameraOverlay),
							"Third person: a Stand camera in stone must show the stone's in-wall texture; got " + cameraOverlay);
				}
				else {
					helper.assertTrue(eyeOverlay == null && cameraOverlay == null, "No in-wall texture in "
							+ state + "; got eyes " + eyeOverlay + ", camera " + cameraOverlay);
				}
			}

			eyeBlocks.forEach(pos -> scene.put(pos, Blocks.STONE.defaultBlockState()));
			helper.assertTrue(StandCameraInWall.cameraOverlayBlock(stand, level, camera) == null,
					"Third person: a camera out in the air must not be covered because the Stand's eyes are in stone");

			scene.put(cameraBlock, Blocks.STONE.defaultBlockState());
			Player user = scene.user;
			user.moveTo(stand.getX(), stand.getY(), stand.getZ(), 0, 0);
			scene.put(BlockPos.containing(user.getEyePosition()), Blocks.STONE.defaultBlockState());
			helper.assertTrue(StandCameraInWall.eyeOverlayBlock(user) == null
					&& StandCameraInWall.cameraOverlayBlock(user, level, camera) == null
					&& StandCameraInWall.eyeOverlayBlock(null) == null,
					"Only a Stand camera gets this overlay; the player's own stays vanilla's");
			helper.succeed();
		}
		finally {
			scene.close();
		}
	}

	// a server owner switching the mode in the config file while a Stand is controlled
	@GameTest(template = "empty", timeoutTicks = 40, batch = RUNTIME_BATCH)
	public static void bodyModeChangedAtRuntimeReachesAControlledStand(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.stand.setManuallyControlled(true);
		scene.setMode(JojoModConfig.BODY_MODE_STANDARD);
		helper.assertFalse(scene.stand.noPhysics, "Fixture: standard mode left the controlled Stand without physics");
		helper.assertTrue(scene.stand.getCurStandAction() == null, "Fixture: the Stand is busy with "
				+ scene.stand.getCurStandAction());
		scene.mode.set(JojoModConfig.BODY_MODE_PHASING);
		int[] ticks = new int[1];
		boolean[] phased = new boolean[1];
		helper.onEachTick(() -> {
			if (scene.closed) return;
			if (!phased[0] && scene.stand.noPhysics) {
				phased[0] = true;
				ticks[0] = 0;
				scene.mode.set(JojoModConfig.BODY_MODE_STANDARD);
			}
			else if (phased[0] && !scene.stand.noPhysics) {
				scene.close();
				helper.succeed();
			}
			else if (++ticks[0] > 5) {
				scene.close();
				helper.fail("Switching standBodyThroughWalls to " + (phased[0] ? "standard" : "phasing")
						+ " did not reach the controlled Stand in 5 ticks");
			}
		});
	}

	@AfterBatch(batch = BATCH)
	public static void releaseBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = RUNTIME_BATCH)
	public static void releaseRuntimeBatch(ServerLevel level) {
		releaseActive();
	}

	private static void releaseActive() {
		if (active != null) {
			active.close();
		}
	}

	private record PlacedBlock(BlockPos pos, BlockState previous) {}

	private static final class Scene {
		final GameTestHelper helper;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final ModConfigSpec.ConfigValue<String> mode = StandSkillWallsGameTests.spec("standBodyThroughWalls");
		final String previousMode = mode.get();
		final List<PlacedBlock> placedBlocks = new ArrayList<>();
		boolean closed;

		Scene(GameTestHelper helper) {
			helper.assertTrue(active == null, "Fixture: overlapping Stand body scenes");
			active = this;
			this.helper = helper;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing Star Platinum entity");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
		}

		// two stone blocks from a block and a half to two and a half blocks ahead; returns where the stone starts
		double wallAhead() {
			for (BlockPos relative : List.of(new BlockPos(1, 2, 3), new BlockPos(1, 3, 3))) {
				BlockPos pos = helper.absolutePos(relative);
				placedBlocks.add(new PlacedBlock(pos, helper.getLevel().getBlockState(pos)));
				helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			}
			return helper.absolutePos(new BlockPos(1, 2, 3)).getZ();
		}

		void put(BlockPos pos, BlockState state) {
			if (placedBlocks.stream().noneMatch(placed -> placed.pos().equals(pos))) {
				placedBlocks.add(new PlacedBlock(pos, helper.getLevel().getBlockState(pos)));
			}
			helper.getLevel().setBlockAndUpdate(pos, state);
		}

		void setMode(String value) {
			mode.set(value);
			stand.updateNoPhysics();
		}

		// the server side of a manual movement packet that asks for two blocks forward
		AABB moveForward() {
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			stand.move(MoverType.PLAYER, new Vec3(0, 0, 2));
			return stand.getBoundingBox();
		}

		void close() {
			if (closed) return;
			closed = true;
			mode.set(previousMode);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			user.discard();
			placedBlocks.forEach(placed -> helper.getLevel().setBlockAndUpdate(placed.pos(), placed.previous()));
			placedBlocks.clear();
			if (active == this) {
				active = null;
			}
		}
	}
}
