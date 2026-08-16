package com.github.standobyte.jojo.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.EventHandler;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LeapFallDamageGameTests {
	private LeapFallDamageGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void summonedStandReducesFallDamageDistance(
			GameTestHelper helper) {
		Player unpowered = FakePlayerFactory.get(
				helper.getLevel(), new GameProfile(
						UUID.fromString("6ad5bb00-a65e-43fc-9815-9b5073dace01"),
						"LeapFallUnpowered"));
		Player standUser = FakePlayerFactory.get(
				helper.getLevel(), new GameProfile(
						UUID.fromString("6ad5bb00-a65e-43fc-9815-9b5073dace02"),
						"LeapFallStandUser"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(
				JojoMod.resLoc("star_platinum"));
		StandPower standPower = null;
		try {
			helper.assertTrue(helper.getLevel().addFreshEntity(unpowered),
					"Could not add unpowered fall-damage test player");
			LivingFallEvent unpoweredFall = new LivingFallEvent(
					unpowered, 30.0F, 1.0F);
			EventHandler.onLivingFall(unpoweredFall);
			assertClose(helper, unpoweredFall.getDistance(), 30.0F,
					"Unpowered fall distance changed");

			helper.assertTrue(standType != null,
					"Missing Star Platinum Stand type");
			helper.assertTrue(helper.getLevel().addFreshEntity(standUser),
					"Could not add Stand fall-damage test player");
			standPower = PowerClass.STAND.attachGet(standUser);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(
					standPower, new StandInstance(standType));
			helper.assertTrue(
					inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Star Platinum: " + inserted.status());
			standPower.setResolveLevel(standPower.getMaxResolveLevel());
			helper.assertTrue(standType.summon(standUser, standPower),
					"Could not summon Star Platinum");
			helper.assertTrue(standPower.isLeapUnlocked(),
					"Summoned Star Platinum did not unlock Stand leap");
			float leapStrength = standPower.leapStrength();
			helper.assertTrue(leapStrength > 0.0F,
					"Summoned Star Platinum has no leap strength");

			float fallDistance = (leapStrength + 5.0F) * 3.0F + 4.0F;
			LivingFallEvent mitigatedFall = new LivingFallEvent(
					standUser, fallDistance, 1.0F);
			EventHandler.onLivingFall(mitigatedFall);
			assertClose(helper, mitigatedFall.getDistance(), 4.0F,
					"Stand leap did not reduce fall distance by the donor formula");
			helper.assertTrue(mitigatedFall.getDamageMultiplier() == 1.0F,
					"Stand leap changed the vanilla fall-damage multiplier");

			LivingFallEvent fullyMitigatedFall = new LivingFallEvent(
					standUser, 4.0F, 1.0F);
			EventHandler.onLivingFall(fullyMitigatedFall);
			assertClose(helper, fullyMitigatedFall.getDistance(), 0.0F,
					"Stand leap did not clamp a covered fall to zero");
			helper.succeed();
		}
		finally {
			if (standPower != null && standPower.isSummoned()
					&& standType != null) {
				standType.forceUnsummon(standUser, standPower);
			}
			unpowered.discard();
			standUser.discard();
		}
	}

	private static void assertClose(
			GameTestHelper helper, float actual, float expected,
			String message) {
		helper.assertTrue(Math.abs(actual - expected) < 0.0001F,
				message + ": expected=" + expected + ", actual=" + actual);
	}
}
