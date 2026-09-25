package rotp.core.gametest;

import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonData.updateBbHeight / updateBoundingBox: for the first 35 meditation ticks the hitbox height was
 * multiplied by 1 - ticks * 0.0085 and the eye dropped by the same amount; leaving meditation restored both.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonMeditationHitboxGameTests {
	private static final float STAND_HEIGHT = 1.8F;
	private static final float STAND_EYE = 1.62F;

	private HamonMeditationHitboxGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void meditationLowersHitboxAndEyes(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MeditationHitbox"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.setPos(origin.x, origin.y, origin.z);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the test player");
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			user.refreshDimensions();
			assertSize(helper, user, STAND_HEIGHT, STAND_EYE, "standing Hamon user");

			hamon.setIsMeditating(user, true);
			for (int i = 0; i < 20; i++) {
				power.tick();
			}
			helper.assertTrue(hamon.isMeditating() && hamon.getMeditationTicks() == 20,
					"meditation did not tick: ticks=" + hamon.getMeditationTicks());
			float h20 = STAND_HEIGHT * (1.0F - 20 * 0.0085F);
			assertSize(helper, user, h20, STAND_EYE - (STAND_HEIGHT - h20), "after 20 meditation ticks");

			for (int i = 0; i < 30; i++) {
				power.tick();
			}
			float h35 = STAND_HEIGHT * (1.0F - 35 * 0.0085F);
			assertSize(helper, user, h35, STAND_EYE - (STAND_HEIGHT - h35), "after 50 meditation ticks");
			// a later refresh (pose change) keeps the 35-tick size
			user.refreshDimensions();
			assertSize(helper, user, h35, STAND_EYE - (STAND_HEIGHT - h35), "refresh after the shrink stopped");

			hamon.setIsMeditating(user, false);
			assertSize(helper, user, STAND_HEIGHT, STAND_EYE, "after leaving meditation");
		}
		finally {
			user.discard();
		}
		helper.succeed();
	}

	private static void assertSize(GameTestHelper helper, Player user, float height, float eye, String when) {
		helper.assertTrue(Math.abs(user.getBbHeight() - height) < 1.0E-4F,
				when + ": height " + user.getBbHeight() + ", expected " + height);
		helper.assertTrue(Math.abs(user.getBoundingBox().getYsize() - height) < 1.0E-4,
				when + ": box height " + user.getBoundingBox().getYsize() + ", expected " + height);
		helper.assertTrue(Math.abs(user.getEyeHeight() - eye) < 1.0E-4F,
				when + ": eye height " + user.getEyeHeight() + ", expected " + eye);
	}
}
