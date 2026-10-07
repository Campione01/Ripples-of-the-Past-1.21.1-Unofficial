package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonParser;

import rotp.core.client.entityanim.AnimationSet;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HumanoidStandModel.initActionPoses: {@code actionAnim.putIfAbsent(HEAVY_ATTACK_FINISHER, heavyAttackAnim)},
 * so a finisher of a Stand that had no finisher pose of its own played the heavy attack pose. The port finds
 * a Stand action's clip by name; a finisher whose names find nothing takes the heavy punch clip instead of
 * leaving the Stand in its idle pose.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandFinisherClipFallbackGameTests {
	private StandFinisherClipFallbackGameTests() {}

	private static final String GENERIC_ANIMS = "animations/stand_default.animation.json";
	// a core skin with a heavy punch clip and no finisher clip
	private static final String THE_WORLD_ANIMS =
			"stand_skins/the_world/assets/jojo_ripples/animations/the_world.animation.json";
	private static final String CALC_CLASS = "rotp/core/client/entityanim/PreFrameEntityAnimCalc.class";

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void finisherWithoutOwnClipPlaysHeavyPunch(GameTestHelper helper) {
		Set<String> generic = readClips(helper, GENERIC_ANIMS);
		helper.assertTrue(generic.contains("heavy_punch"), "stand_default lost the heavy_punch clip finishers fall back to");
		for (String finisherName : new String[] { "finisher", "finisher_punch", "finisherPunch" }) {
			helper.assertTrue(!has(generic).test(finisherName),
					"stand_default now has a " + finisherName + " clip; this test needs a set without one");
		}

		// what PreFrameEntityAnimCalc.lookupStandAnim sees: the skin's set, then the generic humanoid set
		Set<String> skinAndGeneric = new HashSet<>(readClips(helper, THE_WORLD_ANIMS));
		skinAndGeneric.addAll(generic);

		for (Set<String> clips : List.of(generic, skinAndGeneric)) {
			String finisher = AnimationSet.portedActionClipName(
					"finisher_punch", "fixture_stand", "fixture_finisher_punch", true, has(clips));
			helper.assertTrue("heavy_punch".equals(finisher),
					"A finisher with no clip of its own must play heavy_punch like 1.16, got " + finisher);
			String prefixed = AnimationSet.portedActionClipName(
					"fixture_stand_finisher", "fixture_stand", "fixture_finisher_punch", true, has(clips));
			helper.assertTrue("heavy_punch".equals(prefixed),
					"A Stand-prefixed finisher with no clip of its own must play heavy_punch, got " + prefixed);
		}

		String notFinisher = AnimationSet.portedActionClipName(
				"finisher_punch", "fixture_stand", "fixture_finisher_punch", false, has(generic));
		helper.assertTrue(notFinisher == null,
				"Only a finisher falls back to the heavy punch clip, a plain action got " + notFinisher);

		// a clip found by the action's other names still wins over the heavy punch
		String byTypePath = AnimationSet.portedActionClipName(
				"fixture_launcher", "fixture_stand", "finisher_uppercut", true, has(generic));
		helper.assertTrue("finisher_uppercut".equals(byTypePath),
				"A finisher with a clip under its ability type path must keep it, got " + byTypePath);
		String byCoreType = AnimationSet.portedActionClipName(
				"fixture_launcher", "fixture_stand", "stand_uppercut", true, has(generic));
		helper.assertTrue("uppercut".equals(byCoreType),
				"A finisher of a core attack type must keep that type's clip, got " + byCoreType);

		String noHeavyClip = AnimationSet.portedActionClipName(
				"finisher_punch", "fixture_stand", "fixture_finisher_punch", true, Set.of("idle")::contains);
		helper.assertTrue(noHeavyClip == null, "Without a heavy punch clip there is nothing to fall back to, got " + noHeavyClip);
		helper.succeed();
	}

	/** The client-only caller is read from its class file: it must tell the lookup whether the ability is a finisher. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void standActionLookupPassesFinisherFlag(GameTestHelper helper) {
		String constants;
		try (InputStream in = StandFinisherClipFallbackGameTests.class.getResourceAsStream("/" + CALC_CLASS)) {
			helper.assertTrue(in != null, "Missing class file " + CALC_CLASS);
			constants = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + CALC_CLASS, e);
		}
		helper.assertTrue(constants.contains("portedActionClipName"),
				"PreFrameEntityAnimCalc no longer resolves ported action clips through AnimationSet.portedActionClipName");
		helper.assertTrue(constants.contains("isStandFinisherOf"),
				"PreFrameEntityAnimCalc no longer reads Ability.isStandFinisherOf for the finisher clip fallback");
		helper.succeed();
	}

	// a set has a clip under the name itself or down its legacy alias chain, as AnimationSet.getNamedAnim resolves it
	private static Predicate<String> has(Set<String> clips) {
		return name -> clips.contains(name) || AnimationSet.aliasedKey(name, clips::contains) != null;
	}

	private static Set<String> readClips(GameTestHelper helper, String assetPath) {
		try (InputStream in = StandFinisherClipFallbackGameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("animations").keySet();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}
}
