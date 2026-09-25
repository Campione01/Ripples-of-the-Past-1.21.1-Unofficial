package rotp.core.gametest;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.worldgen.structure.HamonTempleStructure;
import rotp.core.worldgen.structure.MeteoriteStructure;
import rotp.core.worldgen.structure.PillarmanTempleStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 "Structures Spawn" common config: each toggle (default true) gates its structure's
 * generation point, so worldgen, /locate and explorer maps find nothing while it is off.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureSpawnToggleGameTests {
	private StructureSpawnToggleGameTests() {}

	// High flat surface, so the Hamon 90+ column gate passes on the flat test world.
	private static final IntBinaryOperator SURFACE = (x, z) -> 120;

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonTempleSpawnToggleGatesGeneration(GameTestHelper helper) {
		check(helper, "hamonTempleSpawn", HamonTempleStructure::generationPoint);
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void pillarManTempleSpawnToggleGatesGeneration(GameTestHelper helper) {
		check(helper, "pillarManTempleSpawn", PillarmanTempleStructure::generationPoint);
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void meteoriteSpawnToggleGatesGeneration(GameTestHelper helper) {
		check(helper, "meteoriteSpawn", MeteoriteStructure::generationPoint);
	}

	private static void check(GameTestHelper helper, String key,
			BiFunction<Structure.GenerationContext, IntBinaryOperator, Optional<Structure.GenerationStub>> point) {
		// Spec setters are memory-only; restored in finally, the user's config is never saved.
		ModConfigSpec.BooleanValue toggle = JojoModConfig.COMMON_SPEC.getValues().get(List.of("Structures Spawn", key));
		helper.assertTrue(toggle != null, "Missing common config entry Structures Spawn." + key);
		helper.assertTrue(Boolean.TRUE.equals(toggle.getDefault()), key + " must default to true as in 1.16");
		boolean previous = toggle.get();
		Structure.GenerationContext context = context(helper);
		try {
			toggle.set(true);
			helper.assertTrue(point.apply(context, SURFACE).isPresent(),
					"Control: with " + key + " on the structure must find a generation point");
			toggle.set(false);
			helper.assertTrue(point.apply(context, SURFACE).isEmpty(),
					"With " + key + " off the structure must not find a generation point");
		}
		finally {
			toggle.set(previous);
		}
		helper.succeed();
	}

	private static Structure.GenerationContext context(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
				level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
				new ChunkPos(helper.absolutePos(BlockPos.ZERO)), level, biome -> true);
	}
}
