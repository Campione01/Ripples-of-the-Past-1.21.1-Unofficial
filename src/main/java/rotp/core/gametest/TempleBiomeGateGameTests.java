package rotp.core.gametest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.worldgen.structure.HamonTempleStructure;
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
 * Search checks one centre column; the donor's footprint is sampled only while building pieces.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TempleBiomeGateGameTests {
	private TempleBiomeGateGameTests() {}

	// Chunk (63, -126): centre block (1015, -2009).
	private static final ChunkPos CHUNK = new ChunkPos(63, -126);
	private static final int CX = 1008 + 7;
	private static final int CZ = -2016 + 7;

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void pillarmanTempleTestsBiomeBeforeFootprint(GameTestHelper helper) {
		withToggle(helper, "pillarManTempleSpawn", () -> {
			check(helper, "Pillar Man", PillarmanTempleStructure::generationPoint, 100);
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonTempleTestsBiomeBeforeFootprint(GameTestHelper helper) {
		withToggle(helper, "hamonTempleSpawn", () -> {
			check(helper, "Hamon", HamonTempleStructure::generationPoint, 120);
			// Centre below 90: one sample and no temple, even in a valid biome.
			AtomicInteger calls = new AtomicInteger();
			Optional<Structure.GenerationStub> low = HamonTempleStructure.generationPoint(context(helper, true), counting(calls, 85));
			helper.assertTrue(low.isEmpty(), "Hamon temple placed although its centre column is at 85");
			helper.assertTrue(calls.get() == 1, "Hamon: centre below 90 still sampled the surface " + calls.get() + " times");
		});
		helper.succeed();
	}

	private static void check(GameTestHelper helper, String label,
			BiFunction<Structure.GenerationContext, IntBinaryOperator, Optional<Structure.GenerationStub>> point, int height) {
		// Wrong biome: at most the one centre sample, no footprint scan.
		AtomicInteger calls = new AtomicInteger();
		Optional<Structure.GenerationStub> rejected = point.apply(context(helper, false), counting(calls, height));
		helper.assertTrue(rejected.isEmpty(), label + ": generation point found in a biome the structure does not allow");
		helper.assertTrue(calls.get() <= 1, label + ": wrong-biome candidate sampled the surface " + calls.get() + " times (max 1)");
		// Valid search candidates also defer the footprint until pieces are actually requested.
		calls.set(0);
		Optional<Structure.GenerationStub> accepted = point.apply(context(helper, true), counting(calls, height));
		helper.assertTrue(accepted.isPresent(), label + ": control, no generation point in an allowed biome");
		helper.assertTrue(calls.get() == 1, label + ": search candidate sampled its footprint early (" + calls.get() + " calls)");
		BlockPos probe = accepted.get().position();
		helper.assertTrue(new BlockPos(CX, height, CZ).equals(probe),
				label + ": biome probe at " + probe + ", expected the centre surface " + new BlockPos(CX, height, CZ));
		accepted.get().getPiecesBuilder();
		helper.assertTrue(calls.get() == 50,
				label + ": generating pieces must sample the 49-column donor footprint once; got " + calls.get());
	}

	private static IntBinaryOperator counting(AtomicInteger calls, int height) {
		return (x, z) -> {
			calls.incrementAndGet();
			return height;
		};
	}

	private static void withToggle(GameTestHelper helper, String key, Runnable body) {
		// Spec setters are memory-only; restored in finally, the user's config is never saved.
		ModConfigSpec.BooleanValue toggle = JojoModConfig.COMMON_SPEC.getValues().get(List.of("Structures Spawn", key));
		helper.assertTrue(toggle != null, "Missing common config entry Structures Spawn." + key);
		boolean previous = toggle.get();
		try {
			toggle.set(true);
			body.run();
		}
		finally {
			toggle.set(previous);
		}
	}

	private static Structure.GenerationContext context(GameTestHelper helper, boolean biomeAllowed) {
		ServerLevel level = helper.getLevel();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
				level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
				CHUNK, level, biome -> biomeAllowed);
	}
}
