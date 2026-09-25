package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonData.tickChargeParticles (:1392-1401) and :1459-1465: the passive aura was silver with Metal Silver
 * Overdrive and a weapon in the main hand, blue with Turquoise Blue Overdrive under water, and trackers got the
 * colour through TrHamonAuraColorPacket because they never receive the skill list.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonAuraColorSyncGameTests {

	private HamonAuraColorSyncGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void trackersSeePassiveAuraColor(GameTestHelper helper) {
		Player owner = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		owner.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		owner.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(owner), "Could not add the aura colour test player");
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(owner);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(owner, ModPlayerPowers.HAMON).orElseThrow();

			HamonData tracker = trackerCopy(hamon);
			check(helper, "ORANGE", tracker.passiveAuraColorName(true, true), "no passive skill, weapon under water");

			hamon.learnSkill(ModHamonSkills.METAL_SILVER_OVERDRIVE.get());
			tracker = trackerCopy(hamon);
			helper.assertFalse(tracker.isSkillLearned(ModHamonSkills.METAL_SILVER_OVERDRIVE.get()),
					"Tracking sync carried the skill list, so this test no longer covers trackers");
			check(helper, "SILVER", tracker.passiveAuraColorName(true, false), "Metal Silver with a weapon");
			check(helper, "ORANGE", tracker.passiveAuraColorName(false, false), "Metal Silver without a weapon");
			check(helper, "ORANGE", tracker.passiveAuraColorName(false, true), "Metal Silver only, under water");

			hamon.learnSkill(ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get());
			tracker = trackerCopy(hamon);
			check(helper, "BLUE", tracker.passiveAuraColorName(false, true), "Turquoise Blue under water");
			check(helper, "SILVER", tracker.passiveAuraColorName(true, true), "both skills, weapon under water");
			check(helper, "ORANGE", tracker.passiveAuraColorName(false, false), "both skills, dry and unarmed");

			hamon.removeSkill(ModHamonSkills.METAL_SILVER_OVERDRIVE.get());
			tracker = trackerCopy(hamon);
			check(helper, "ORANGE", tracker.passiveAuraColorName(true, false), "Metal Silver removed, with a weapon");
			helper.succeed();
		}
		finally {
			owner.discard();
		}
	}

	// what another player's client decodes from the tracking sync
	private static HamonData trackerCopy(HamonData source) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		source.toBuf(buf, true);
		HamonData copy = new HamonData();
		copy.fromBuf(buf, true);
		return copy;
	}

	private static void check(GameTestHelper helper, String expected, String actual, String state) {
		helper.assertTrue(expected.equals(actual), "Tracker aura for " + state + ": expected " + expected + ", got " + actual);
	}
}
