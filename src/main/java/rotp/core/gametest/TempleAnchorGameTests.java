package rotp.core.gametest;

import java.util.function.IntBinaryOperator;

import rotp.core.core.JojoMod;
import rotp.core.worldgen.structure.HamonTempleStructure;
import rotp.core.worldgen.structure.PillarmanTempleStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Both temples anchor as in 1.16: centre at chunk corner + 7, lowest WORLD_SURFACE_WG height over the footprint,
 * sunk 3 blocks. Hamon also needs the centre column at 90+ and floors each sample at 80.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TempleAnchorGameTests {
	private TempleAnchorGameTests() {}

	// Chunk (63, -126): corner block (1008, -2016).
	private static final ChunkPos CHUNK = new ChunkPos(63, -126);
	private static final int CX = 1008 + 7;
	private static final int CZ = -2016 + 7;

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void pillarmanTempleAnchorsOnFootprintMinimum(GameTestHelper helper) {
		// 1.16 footprint samples [c-26, c+30) step 8; one low corner at 70, lower ground just outside.
		IntBinaryOperator surface = (x, z) -> {
			if (x < CX - 26 || x >= CX + 30 || z < CZ - 26 || z >= CZ + 30) {
				return 10;
			}
			return x == CX - 26 && z == CZ + 22 ? 70 : 100;
		};
		expect(helper, "Pillar Man sloped", PillarmanTempleStructure.anchor(CHUNK, surface), new BlockPos(CX, 67, CZ));
		expect(helper, "Pillar Man flat", PillarmanTempleStructure.anchor(CHUNK, (x, z) -> 100), new BlockPos(CX, 97, CZ));
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonTempleAnchorsOnFlooredFootprintMinimum(GameTestHelper helper) {
		// Centre column below 90: no temple.
		BlockPos low = HamonTempleStructure.anchor(CHUNK, (x, z) -> x == CX && z == CZ ? 85 : 120);
		helper.assertTrue(low == null, "Hamon temple placed although its centre column is at 85: " + low);
		// Exactly 90 passes the gate.
		expect(helper, "Hamon gate 90", HamonTempleStructure.anchor(CHUNK, (x, z) -> x == CX && z == CZ ? 90 : 120), new BlockPos(CX, 87, CZ));
		// 1.16 footprint samples [c-24, c+24] inclusive step 8; the far corner counts, ground outside does not.
		IntBinaryOperator corner = (x, z) -> {
			if (x < CX - 24 || x > CX + 24 || z < CZ - 24 || z > CZ + 24) {
				return 81;
			}
			return x == CX + 24 && z == CZ + 24 ? 85 : 120;
		};
		expect(helper, "Hamon far corner", HamonTempleStructure.anchor(CHUNK, corner), new BlockPos(CX, 82, CZ));
		// A sample below 80 is floored at 80.
		IntBinaryOperator pit = (x, z) -> x == CX - 24 && z == CZ - 24 ? 60 : 120;
		expect(helper, "Hamon floor", HamonTempleStructure.anchor(CHUNK, pit), new BlockPos(CX, 77, CZ));
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, String label, BlockPos actual, BlockPos expected) {
		helper.assertTrue(expected.equals(actual), label + ": temple anchored at " + actual + ", 1.16 gives " + expected);
	}
}
