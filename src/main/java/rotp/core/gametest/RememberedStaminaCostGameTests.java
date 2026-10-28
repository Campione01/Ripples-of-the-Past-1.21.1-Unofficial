package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 rotp_mwp StandAI.isActionAvailable (:190) left an action out of a Stand-bearing mob's pick while
 * Action.getStaminaCost(power) was above the mob's stamina; rotp-stand-bearing-creatures asks Ability#getStaminaCost(Power)
 * the same way (StandPowerMobGoal.paysStaminaCost). An add-on ability takes its stamina through StandAbilityStamina#consume
 * and does not report a cost of its own, so it reports the amount its last use asked to take there (its one-off cost
 * before its cost per tick), 0 before its first use; an ability that reports its own cost keeps it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RememberedStaminaCostGameTests {
	private static final String ADDON_MOVE = "remembered_cost_addon_move";

	private RememberedStaminaCostGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aStandMobLeavesOutAnAddonMoveItsStaminaCannotPayOnceItWasUsedAsIn116(GameTestHelper helper) {
		List<Mob> mobs = new ArrayList<>();
		try {
			StandPower power = standMob(helper, mobs, 1);
			StandPower otherPower = standMob(helper, mobs, 3);
			helper.assertTrue(power.getAbility("barrage") != otherPower.getAbility("barrage"),
					"setup: two users of a Stand share an ability object, so a cost kept by the ability is not its user's");
			Ability move = addonMove(power);
			Ability otherMove = addonMove(otherPower);

			power.setStamina(1.0F);
			helper.assertTrue(move.getStaminaCost(power) == 0.0F && paysStaminaCost(power, move),
					"before its first use an add-on move reports no cost, so the first pick takes it: " + move.getStaminaCost(power));

			power.setStamina(power.getMaxStamina());
			helper.assertTrue(StandAbilityStamina.consume(move, power, 30.0F, false), "setup: the move's use could not pay 30");
			helper.assertTrue(move.getStaminaCost(power) == 30.0F,
					"an add-on move reports the 30 its use took: " + move.getStaminaCost(power));
			power.setStamina(1.0F);
			helper.assertTrue(!paysStaminaCost(power, move), "1.16 StandAI left out a move whose cost (30) its stamina (1)"
					+ " could not pay: reported " + move.getStaminaCost(power));
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(paysStaminaCost(power, move), "1.16 StandAI took the move again with its stamina back: reported "
					+ move.getStaminaCost(power) + ", stamina " + power.getStamina());
			helper.assertTrue(otherMove.getStaminaCost(otherPower) == 0.0F,
					"another user's same move, never used, reports what its user's use asked: " + otherMove.getStaminaCost(otherPower));

			// a held or ticking move: its cost per tick (1.16 getStaminaCostTicking), its one-off cost first
			Ability ticking = new Ability(ModStandAbilities.HEAVY_PUNCH.get(),
					new AbilityId(PowerClass.STAND, power.getPowerType().getId(), ADDON_MOVE + "_ticking"));
			StandAbilityStamina.consume(ticking, power, 2.0F, true);
			float perTick = ticking.getStaminaCost(power);
			StandAbilityStamina.consume(ticking, power, 25.0F, false);
			StandAbilityStamina.consume(ticking, power, 2.0F, true);
			helper.assertTrue(perTick == 2.0F && ticking.getStaminaCost(power) == 25.0F, "a move paying only per tick reports its"
					+ " 2 per tick (" + perTick + "), one that also paid 25 at once reports the 25 (" + ticking.getStaminaCost(power) + ")");

			// an ability that reports its own cost: the heavy punch's 1.16 50, the inhale's 1.16 0 (it cost only per tick)
			Ability heavy = power.getAbility("heavy_punch");
			Ability inhale = power.getAbility("inhale");
			StandAbilityStamina.consume(heavy, power, 7.0F, false);
			StandAbilityStamina.consume(inhale, power, 2.0F, true);
			helper.assertTrue(heavy.getStaminaCost(power) == 50.0F && inhale.getStaminaCost(power) == 0.0F,
					"an ability's own report wins over the amount its use asked: heavy punch " + heavy.getStaminaCost(power)
							+ " (1.16 50), inhale " + inhale.getStaminaCost(power) + " (1.16 0)");
		}
		finally {
			mobs.forEach(Mob::discard);
		}
		helper.succeed();
	}

	private record TickOnly(String stand, String ability) {}

	// 1.16 actions built with staminaCostTick and no staminaCost: getStaminaCost 0 whatever their ticks take
	private static final List<TickOnly> TICK_ONLY = List.of(new TickOnly("star_platinum", "inhale"),
			new TickOnly("hierophant_green", "emerald_splash"), new TickOnly("hierophant_green", "emerald_splash_concentrated"),
			new TickOnly("hierophant_green", "grapple"), new TickOnly("hierophant_green", "grapple_entity"),
			new TickOnly("magicians_red", "flame_burst"), new TickOnly("magicians_red", "red_bind"),
			new TickOnly("crazy_diamond", "heal"), new TickOnly("crazy_diamond", "revert_state"),
			new TickOnly("crazy_diamond", "block_anchor"), new TickOnly("gold_experience", "life_detector"));

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theCoreMovesThatCostOnlyPerTickStillReport116sNoCostAfterAUse(GameTestHelper helper) {
		List<Mob> mobs = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		try {
			for (TickOnly row : TICK_ONLY) {
				StandPower power = standMob(helper, mobs, row.stand(), 1 + mobs.size());
				Ability ability = power.getAbility(row.ability());
				helper.assertTrue(ability != null, "setup: " + row.stand() + " has no " + row.ability());
				StandAbilityStamina.consume(ability, power, 3.0F, true);
				if (ability.getStaminaCost(power) != 0.0F) {
					wrong.add(row.stand() + " " + row.ability() + " reports " + ability.getStaminaCost(power));
				}
			}
		}
		finally {
			mobs.forEach(Mob::discard);
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 getStaminaCost was 0 for an action that cost only per tick, after a use too: "
				+ String.join("; ", wrong));
		helper.succeed();
	}

	// rotp-stand-bearing-creatures StandPowerMobGoal.paysStaminaCost, 1.16 StandAI.isActionAvailable's stamina part
	private static boolean paysStaminaCost(StandPower power, Ability ability) {
		return ability.getStaminaCost(power) <= power.getStamina();
	}

	// an add-on Stand's own move in its user's moveset, which reports no cost of its own
	private static Ability addonMove(StandPower power) {
		Ability move = new Ability(ModStandAbilities.HEAVY_PUNCH.get(),
				new AbilityId(PowerClass.STAND, power.getPowerType().getId(), ADDON_MOVE));
		power.getMoveset().abilities.put(ADDON_MOVE, move);
		return power.getAbility(ADDON_MOVE);
	}

	private static StandPower standMob(GameTestHelper helper, List<Mob> mobs, int x) {
		return standMob(helper, mobs, "star_platinum", x);
	}

	private static StandPower standMob(GameTestHelper helper, List<Mob> mobs, String standId, int x) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		helper.assertTrue(type != null, "setup: no Stand " + standId);
		Mob zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create a zombie");
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 2, 1)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		mobs.add(zombie);
		StandPower power = PowerClass.STAND.attachGet(zombie);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
				"setup: could not give the zombie " + standId);
		return power;
	}
}
