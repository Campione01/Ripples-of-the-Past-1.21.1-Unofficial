package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16's Action.getStaminaCost, the stamina an action took as it performed (StandAction.Builder staminaCost; a light
 * attack's 10, a heavy attack's 50, ModStandsInit for the rest), which a 1.16 Stand-bearing mob compared with its stamina
 * before it picked an action (rotp_mwp StandAI.isActionAvailable :190). The port's abilities take their stamina by their
 * own code; Ability#getStaminaCost(Power) reports it (FEATURE_ABILITY_STAMINA_COST_V1) for a mob's pre-check, 0 for an
 * action that costs only as it ticks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbilityStaminaCost116GameTests {

	private AbilityStaminaCost116GameTests() {}

	private record Row(String stand, String ability, float cost) {}

	private static final List<Row> ROWS = List.of(
			new Row("star_platinum", "punch", 10), new Row("star_platinum", "heavy_punch", 50),
			new Row("star_platinum", "finisher_uppercut", 50), new Row("star_platinum", "star_finger", 375),
			new Row("star_platinum", "time_stop", 225), new Row("star_platinum", "time_stop_blink", 180),
			new Row("star_platinum", "barrage", 0), new Row("star_platinum", "guard", 0),
			new Row("the_world", "punch", 10), new Row("the_world", "heavy_punch", 50), new Row("the_world", "kick", 50),
			new Row("the_world", "ts_punch", 50), new Row("the_world", "time_stop", 225),
			new Row("the_world", "time_stop_blink", 180),
			new Row("magicians_red", "punch", 10), new Row("magicians_red", "heavy_punch", 50),
			new Row("magicians_red", "kick", 50), new Row("magicians_red", "fireball", 75),
			new Row("magicians_red", "crossfire_hurricane", 500), new Row("magicians_red", "crossfire_hurricane_special", 500),
			new Row("hierophant_green", "string_attack", 75), new Row("hierophant_green", "string_bind", 75),
			new Row("hierophant_green", "emerald_splash", 0),
			new Row("silver_chariot", "light_attack", 10), new Row("silver_chariot", "dash_attack", 50),
			new Row("silver_chariot", "sweeping_attack", 50),
			new Row("crazy_diamond", "punch", 10), new Row("crazy_diamond", "heavy_punch", 50),
			new Row("crazy_diamond", "finisher", 50), new Row("crazy_diamond", "disfiguring_punch", 50),
			new Row("crazy_diamond", "leave_object", 50), new Row("crazy_diamond", "fuse_with_rock", 50),
			new Row("crazy_diamond", "block_bullet", 40), new Row("crazy_diamond", "blood_cutter", 25),
			new Row("crazy_diamond", "block_anchor_make", 25),
			new Row("gold_experience", "punch", 10), new Row("gold_experience", "heavy_punch", 50),
			new Row("gold_experience", "lifeshot_punch", 50), new Row("gold_experience", "lifeshot", 50),
			new Row("gold_experience", "bone_meal", 2), new Row("gold_experience", "heal", 20),
			new Row("gold_experience", "healing_item", 40), new Row("gold_experience", "heal_other", 20));

	// names the time stop by its moveset id, which GameTestBatchSourcePinGameTests counts as a press of it
	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void theCoreStandAbilitiesReport116sStaminaCostForAMobsPreCheck(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		String current = null;
		FakePlayer user = null;
		StandPower power = null;
		try {
			for (Row row : ROWS) {
				if (!row.stand().equals(current)) {
					if (user != null) {
						user.discard();
					}
					current = row.stand();
					StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(current));
					helper.assertTrue(type != null, "setup: no Stand " + current);
					user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StaminaCost"));
					user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
					helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the Stand user");
					power = PowerClass.STAND.attachGet(user);
					helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
							== StandPowerTransitions.Status.APPLIED, "setup: could not give " + current);
				}
				Ability ability = power.getAbility(row.ability());
				helper.assertTrue(ability != null, "setup: " + current + " has no " + row.ability());
				float cost = ability.getStaminaCost(power);
				if (Math.abs(cost - row.cost()) > 1.0E-4F) {
					wrong.add(current + " " + row.ability() + " reports " + cost + ", 1.16's " + row.cost());
				}
			}
		}
		finally {
			if (user != null) {
				user.discard();
			}
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 Action.getStaminaCost, which its Stand-bearing mobs compared with their"
				+ " stamina before picking an action: " + String.join("; ", wrong));
		helper.succeed();
	}
}
