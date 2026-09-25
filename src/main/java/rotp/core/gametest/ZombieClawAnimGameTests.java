package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.AnimationLoader;
import rotp.core.client.entityanim.AnimationSet;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.zombie.ZombiePowerType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Zombie Claw Lacerate played the vampire claw swipe (zombieClawSwipe was a KosmXVampireClawSwipeHandler).
 * The port looks the pose up in the zombie power's anim set by the moveset name zombie_claw_lacerate.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZombieClawAnimGameTests {
	private ZombieClawAnimGameTests() {}

	private static final String VAMPIRE_ANIMS = "animations/vampire.animation.json";

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void zombieClawLaceratePlaysVampireClawSwipe(GameTestHelper helper) {
		ResourceLocation vampireFile = JojoMod.resLoc("vampire");
		ResourceLocation zombieSet = ZombiePowerType.ZOMBIE.get().getId();
		helper.assertTrue(vampireFile.equals(AnimationLoader.animSetAlias(zombieSet)),
				"Zombie power anim set " + zombieSet + " must read the vampire animation file, got "
				+ AnimationLoader.animSetAlias(zombieSet));
		helper.assertTrue(vampireFile.equals(AnimationLoader.animSetAlias(VampirismPowerType.VAMPIRISM.get().getId())),
				"Vampirism anim set lost its vampire file alias");

		Set<String> clips = readJson(helper, VAMPIRE_ANIMS).getAsJsonObject("animations").keySet();
		helper.assertTrue(clips.contains("vampire_claws"), "vampire.animation.json lost vampire_claws");
		String zombieClaw = AnimationSet.aliasedKey("zombie_claw_lacerate", clips::contains);
		helper.assertTrue("vampire_claws".equals(zombieClaw),
				"Zombie Claw Lacerate must play vampire_claws like 1.16, got " + zombieClaw);
		helper.assertTrue("vampire_claws".equals(AnimationSet.aliasedKey("vampirism_claw_lacerate", clips::contains)),
				"Vampire Claw Lacerate lost its vampire_claws pose");
		// 1.16 zombies had no Devour or Disguise pose
		for (String name : new String[] { "zombie_devour", "zombie_disguise" }) {
			helper.assertTrue(!clips.contains(name) && AnimationSet.aliasedKey(name, clips::contains) == null,
					name + " must not pick up a vampire clip");
		}
		helper.succeed();
	}

	private static JsonObject readJson(GameTestHelper helper, String assetPath) {
		try (InputStream in = ZombieClawAnimGameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}
}
