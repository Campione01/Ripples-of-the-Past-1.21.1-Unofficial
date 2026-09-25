package rotp.core.gametest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandStaminaModifiers;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 D4C StandPowerMixin / StaminaRegenMixin multiplied StandPower#getMaxStamina and #getStaminaTickGain at
 * RETURN for every Stand user carrying Saint Corpse parts. The core must apply add-on stamina factors to the
 * final values of any Stand, here Star Platinum.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandStaminaModifierGameTests {
	// per test user, so parallel tests keep their plain stamina
	private static final Map<UUID, Float> MAX_FACTORS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> TICK_FACTORS = new ConcurrentHashMap<>();
	private static final Map<UUID, StandPower> SEEN = new ConcurrentHashMap<>();

	static {
		StandStaminaModifiers.registerMaxStamina(JojoMod.resLoc("stand_stamina_modifier_gametest"),
				power -> factorFor(MAX_FACTORS, power));
		StandStaminaModifiers.registerTickGain(JojoMod.resLoc("stand_stamina_modifier_gametest"),
				power -> factorFor(TICK_FACTORS, power));
	}

	private StandStaminaModifierGameTests() {}

	private static float factorFor(Map<UUID, Float> factors, StandPower power) {
		LivingEntity user = power.getUser();
		Float factor = user != null ? factors.get(user.getUUID()) : null;
		if (factor == null) {
			return 1.0F;
		}
		SEEN.put(user.getUUID(), power);
		return factor;
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void addonFactorsScaleAnyStandsMaxStaminaAndRegen(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		UUID id = player.getUUID();
		try {
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Stand user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			StandPower power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(!power.isStaminaInfinite(), "Stand stamina is infinite here, the test would prove nothing");
			float baseMax = power.getMaxStamina();
			float baseGain = power.getStaminaTickGain();
			helper.assertTrue(baseMax > 0 && baseGain > 0,
					"Star Platinum has no stamina to scale: " + baseMax + " / " + baseGain);

			// one Saint Corpse part in 1.16: +30% max, +15% regen, for a non-D4C Stand
			MAX_FACTORS.put(id, 1.3F);
			TICK_FACTORS.put(id, 1.15F);
			float max = power.getMaxStamina();
			float gain = power.getStaminaTickGain();
			helper.assertTrue(close(max, baseMax * 1.3F),
					"an add-on factor did not scale max stamina: " + baseMax + " -> " + max);
			helper.assertTrue(close(gain, baseGain * 1.15F),
					"an add-on factor did not scale the stamina tick gain: " + baseGain + " -> " + gain);
			helper.assertTrue(SEEN.get(id) == power, "the stamina factor was not given the user's Stand power");
			// the server clamp follows the raised max
			power.setStamina(max);
			helper.assertTrue(close(power.getStamina(), max) && power.getStamina() > baseMax,
					"stamina was clamped to the plain max: " + power.getStamina() + " / " + max);

			// a broken factor is ignored
			MAX_FACTORS.put(id, Float.NaN);
			TICK_FACTORS.put(id, -2.0F);
			helper.assertTrue(close(power.getMaxStamina(), baseMax),
					"a NaN max stamina factor was applied: " + power.getMaxStamina());
			helper.assertTrue(close(power.getStaminaTickGain(), baseGain),
					"a negative tick gain factor was applied: " + power.getStaminaTickGain());

			MAX_FACTORS.remove(id);
			TICK_FACTORS.remove(id);
			helper.assertTrue(close(power.getMaxStamina(), baseMax) && close(power.getStaminaTickGain(), baseGain),
					"stamina did not return to its plain values once the factors let go");
			helper.succeed();
		}
		finally {
			MAX_FACTORS.remove(id);
			TICK_FACTORS.remove(id);
			SEEN.remove(id);
			player.discard();
		}
	}

	private static boolean close(float actual, float expected) {
		return Math.abs(actual - expected) <= 1.0E-4F * Math.max(1.0F, Math.abs(expected));
	}
}
