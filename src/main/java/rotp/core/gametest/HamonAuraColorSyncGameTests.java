package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
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

	/**
	 * 1.16 HamonData.tickChargeParticles (:1395-1400) worked the aura colour out on the server, from lastUsedAction
	 * too, and sent it to trackers. The Hamon Protection toggle is no action a tracker sees, so the technique the
	 * aura remembers has to reach trackers with the tracking sync.
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void trackersFollowTheRememberedTechnique(GameTestHelper helper) {
		Player owner = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		owner.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		owner.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(owner), "Could not add the aura colour test player");
		LivingComponentAction component = LivingComponentAction.getComponent(owner);
		List<String> wrong = new ArrayList<>();
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(owner);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(owner, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
			hamon.learnSkill(ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get());
			hamon.learnSkill(ModHamonSkills.PROTECTION.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			helper.assertTrue(hamon.getEnergy() > 0.0F && owner.getMainHandItem().isEmpty() && !owner.isInWater(),
					"Fixture: a dry, unarmed user with energy, energy=" + hamon.getEnergy());

			// a watcher whose client saw Turquoise Blue Overdrive start
			HamonData watcher = trackerCopy(hamon);
			useTechnique(helper, component, power, owner, "turquoise_blue_overdrive");
			watcher.setLastAuraAbility("turquoise_blue_overdrive");
			helper.assertTrue("BLUE".equals(watcher.auraColorNameThisTick(owner)),
					"Fixture: the watcher draws blue after Turquoise Blue Overdrive, got " + watcher.auraColorNameThisTick(owner));
			check(wrong, "BLUE", trackerCopy(hamon), owner, "a watcher who starts tracking after Turquoise Blue Overdrive");

			Ability protection = power.getAbility("hamon_protection");
			helper.assertTrue(protection != null, "Missing registered hamon_protection");
			protection.onClick(helper.getLevel(), owner, null);
			helper.assertTrue("ORANGE".equals(hamon.auraColorNameThisTick(owner)),
					"Fixture: the user's own aura is orange after the Hamon Protection toggle");
			applyTrackingSync(hamon, watcher);
			check(wrong, "ORANGE", watcher, owner, "the watcher after the user toggled Hamon Protection");

			// the server forgets the technique at energy 0 as the clients do, or a later sync would bring it back
			useTechnique(helper, component, power, owner, "turquoise_blue_overdrive");
			hamon.setEnergy(0.0F);
			hamon.tick(power);
			hamon.setEnergy(hamon.getMaxEnergy());
			check(wrong, "ORANGE", trackerCopy(hamon), owner, "a watcher synced after the energy ran out and came back");
			helper.assertTrue(wrong.isEmpty(), "1.16 tracker aura colour " + String.join("; ", wrong));
			helper.succeed();
		}
		finally {
			component.setAction(null, SyncType.NO_SYNC);
			owner.discard();
		}
	}

	// the accepted click of a technique: its action is set on the user, then it is taken off again before it ticks
	private static void useTechnique(GameTestHelper helper, LivingComponentAction component, PlayerPower power, Player user, String name) {
		Ability found = power.getAbility(name);
		helper.assertTrue(found instanceof EntityActionAbility, "Missing registered action technique " + name);
		EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(user.level(), user, user, null);
		helper.assertTrue(action != null, "Fixture: " + name + " made no action");
		component.setAction(action, user, SyncType.NO_SYNC);
		component.setAction(null, SyncType.NO_SYNC);
	}

	private static void check(List<String> wrong, String expected, HamonData tracker, Player user, String state) {
		String actual = tracker.auraColorNameThisTick(user);
		if (!expected.equals(actual)) {
			wrong.add(state + ": expected " + expected + ", got " + actual);
		}
	}

	// what another player's client decodes from the tracking sync
	private static HamonData trackerCopy(HamonData source) {
		HamonData copy = new HamonData();
		applyTrackingSync(source, copy);
		return copy;
	}

	private static void applyTrackingSync(HamonData source, HamonData tracker) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		source.toBuf(buf, true);
		tracker.fromBuf(buf, true);
		if (buf.isReadable()) {
			throw new IllegalStateException("Tracking sync left " + buf.readableBytes() + " unread bytes");
		}
	}

	private static void check(GameTestHelper helper, String expected, String actual, String state) {
		helper.assertTrue(expected.equals(actual), "Tracker aura for " + state + ": expected " + expected + ", got " + actual);
	}
}
