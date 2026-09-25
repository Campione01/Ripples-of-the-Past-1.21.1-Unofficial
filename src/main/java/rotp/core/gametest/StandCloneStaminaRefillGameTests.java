package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.EventHandler;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 1.16 StandPower.keepPower: a kept Stand starts with full stamina after death or End return. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandCloneStaminaRefillGameTests {
	private static final float LOW_STAMINA = 10;

	private StandCloneStaminaRefillGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void endReturnCloneRefillsStamina(GameTestHelper helper) {
		verifyRefill(helper, false);
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void keptDeathCloneRefillsStamina(GameTestHelper helper) {
		verifyRefill(helper, true);
	}

	private static void verifyRefill(GameTestHelper helper, boolean wasDeath) {
		// Spec setters are memory-only; restore in finally and never save the user's config.
		ModConfigSpec.BooleanValue keep = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Keep Powers After Death", "keepStandOnDeath"));
		boolean previousKeep = keep.get();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "StaminaClone");
		FakePlayer original = new FakePlayer(helper.getLevel(), profile);
		FakePlayer replacement = new FakePlayer(helper.getLevel(), profile);
		try {
			keep.set(true);
			StandPower oldPower = PowerClass.STAND.attachGet(original);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			helper.assertTrue(StandPowerTransitions.insert(oldPower, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant test Stand");
			if (oldPower.isStaminaInfinite()) {
				// stamina config off: nothing to refill
				helper.succeed();
				return;
			}
			oldPower.setStamina(LOW_STAMINA);
			helper.assertTrue(oldPower.getStamina() == LOW_STAMINA, "Could not lower the old stamina");

			// as ServerPlayer.restoreFrom, which copies attribute base values before the Clone event
			replacement.getAttributes().assignBaseValues(original.getAttributes());
			EventHandler.onPlayerClone(new PlayerEvent.Clone(replacement, original, wasDeath));

			StandPower newPower = StandPower.get(replacement);
			helper.assertTrue(newPower != null && newPower.hasPower(), "Clone lost the Stand");
			float max = newPower.getMaxStamina();
			helper.assertTrue(max > LOW_STAMINA * 2, "Max stamina too low to tell a refill: " + max);
			helper.assertTrue(newPower.getStamina() == max,
					"Clone (wasDeath=" + wasDeath + ") kept " + newPower.getStamina() + " of " + max + " stamina");
			helper.assertTrue(oldPower.getStamina() == LOW_STAMINA,
					"Refill wrote through to the old player's stamina: " + oldPower.getStamina());
			helper.succeed();
		}
		finally {
			keep.set(previousKeep);
			original.discard();
			replacement.discard();
		}
	}
}
