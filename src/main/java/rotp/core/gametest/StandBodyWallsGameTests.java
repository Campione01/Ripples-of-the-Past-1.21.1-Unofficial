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
 * The server setting standBodyThroughWalls (owner, 2026-10-15): in "manga" mode a manually controlled Stand passes
 * through blocks; in "1.16" mode it collides with them, as 1.16 StandEntity.shouldHaveNoPhysics had it. A Stand with a
 * solid body (standCanHaveNoPhysics false, as rotp-rhcp's) collides in both. The server moves a manually controlled
 * Stand with MoverType.PLAYER (ClStandManualMovementPacket). Own batches: the mode is global while a batch runs.
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
	public static void manualStandPassesIntoABlockOnlyInMangaMode(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			double wallZ = scene.wallAhead();
			scene.stand.setManuallyControlled(true);

			scene.setMode(JojoModConfig.WALLS_MODE_MANGA);
			helper.assertTrue(scene.stand.noPhysics, "manga: a manually controlled Stand must have no physics");
			AABB box = scene.moveForward();
			helper.assertTrue(scene.stand.getZ() > wallZ,
					"manga: the manually controlled Stand stopped at the stone instead of entering it: " + box);

			scene.setMode(JojoModConfig.BODY_WALLS_MODE_1_16);
			helper.assertFalse(scene.stand.noPhysics, "1.16: a manually controlled Stand must collide");
			box = scene.moveForward();
			helper.assertTrue(box.maxZ <= wallZ + EPS,
					"1.16: the manually controlled Stand entered the stone: " + box + ", stone from z " + wallZ);
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
			for (String mode : List.of(JojoModConfig.WALLS_MODE_MANGA, JojoModConfig.BODY_WALLS_MODE_1_16)) {
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

	// a server owner switching the mode in the config file while a Stand is controlled
	@GameTest(template = "empty", timeoutTicks = 40, batch = RUNTIME_BATCH)
	public static void bodyModeChangedAtRuntimeReachesAControlledStand(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.stand.setManuallyControlled(true);
		scene.setMode(JojoModConfig.BODY_WALLS_MODE_1_16);
		helper.assertFalse(scene.stand.noPhysics, "Fixture: 1.16 mode left the controlled Stand without physics");
		helper.assertTrue(scene.stand.getCurStandAction() == null, "Fixture: the Stand is busy with "
				+ scene.stand.getCurStandAction());
		scene.mode.set(JojoModConfig.WALLS_MODE_MANGA);
		int[] ticks = new int[1];
		helper.onEachTick(() -> {
			if (scene.closed) return;
			if (scene.stand.noPhysics) {
				scene.close();
				helper.succeed();
			}
			else if (++ticks[0] > 5) {
				scene.close();
				helper.fail("Switching standBodyThroughWalls to manga did not reach the controlled Stand in 5 ticks");
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
