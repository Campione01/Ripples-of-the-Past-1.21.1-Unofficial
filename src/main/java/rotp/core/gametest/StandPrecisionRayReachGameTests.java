package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.subsystems.target.StandPrecisionRay;

/**
 * 1.16 StandEntity.getAimDistance: the precision ray (and Gold Experience's create-lifeform ray, which casts it)
 * reaches the user's REACH_DISTANCE, 5 blocks for a player in either game mode. 1.21 keeps the Creative half block in
 * a modifier that the server puts on or takes off on the player's next tick, so the game mode, the instabuild flag and
 * that modifier can disagree for a tick; the reach must not follow them.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandPrecisionRayReachGameTests {
	private static final ResourceLocation CREATIVE_BLOCK_RANGE = ResourceLocation.withDefaultNamespace("creative_mode_block_range");
	private static final double EPS = 1.0E-6D;

	private StandPrecisionRayReachGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void precisionRayReachIsFiveBlocksInEitherGameMode(GameTestHelper helper) {
		ServerPlayer user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PrecisionReach"));
		AttributeInstance range = user.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		helper.assertTrue(range != null, "setup: the player has no block interaction range");
		AttributeModifier creativeBonus = new AttributeModifier(CREATIVE_BLOCK_RANGE, 0.5D, AttributeModifier.Operation.ADD_VALUE);
		try {
			user.getAbilities().instabuild = false;
			range.removeModifier(CREATIVE_BLOCK_RANGE);
			check(helper, user, "Survival");

			user.getAbilities().instabuild = true;
			check(helper, user, "Creative before the server added its range bonus");

			range.addOrUpdateTransientModifier(creativeBonus);
			check(helper, user, "Creative with its range bonus");

			user.getAbilities().instabuild = false;
			check(helper, user, "Survival before the server took the Creative range bonus off");
		}
		finally {
			range.removeModifier(CREATIVE_BLOCK_RANGE);
			user.getAbilities().instabuild = false;
		}
		helper.succeed();
	}

	private static void check(GameTestHelper helper, ServerPlayer user, String mode) {
		double reach = StandPrecisionRay.reach(user, null);
		helper.assertTrue(Math.abs(reach - 5.0D) < EPS,
				"1.16 REACH_DISTANCE: the precision ray reaches 5 blocks in " + mode + ", got " + reach);
	}
}
