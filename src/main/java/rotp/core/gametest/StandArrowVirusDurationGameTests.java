package rotp.core.gametest;

import java.util.Optional;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.mechanics.standarrow.StandVirusActualEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil.StandRandomPoolFilter;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowItem: a pierced survival player got a virus lasting (their own cost + 1) * 20 ticks, so a player
 * with many Arrow Stands paid the full risen cost before the Stand came. The pool mode tooltip line showed unless
 * the game was unpublished single player, so the host of a LAN world saw it too.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowVirusDurationGameTests {
	private static final int PRIOR_ARROW_STANDS = 7;

	private StandArrowVirusDurationGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void arrowVirusLastsForThePlayersRisenCost(GameTestHelper helper) {
		JojoModConfig.Common config = JojoModConfig.getCommonConfigInstance(false);
		int initial = config.standXpCostInitial.get();
		int increase = config.standXpCostIncrease.get();
		helper.assertTrue(increase > 0, "Needs standXpCostIncrease > 0 to tell the risen cost from the initial one");
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = PowerClass.STAND.attachGet(player);
			player.setData(ModDataAttachmentTypes.STANDS_GOT_FROM_ARROW, PRIOR_ARROW_STANDS);
			int cost = StandVirusActualEffect.getStandXpLevelsRequirement(player, ItemStack.EMPTY);
			helper.assertTrue(cost == initial + PRIOR_ARROW_STANDS * increase,
					"Cost after " + PRIOR_ARROW_STANDS + " Arrow Stands should be " + (initial + PRIOR_ARROW_STANDS * increase) + ", got " + cost);
			helper.assertTrue(!power.hasPower(), "The player already has a Stand");
			helper.assertTrue(StandArrowItem.onPiercedByArrow(player, new ItemStack(ModItems.STAND_ARROW.get()),
					helper.getLevel(), Optional.empty()), "The Arrow did not infect the player");

			MobEffectInstance virus = player.getEffect(ModStatusEffects.STAND_VIRUS);
			int expected = (cost + 1) * 20;
			helper.assertTrue(virus != null && virus.getDuration() == expected,
					"1.16 virus lasted (cost + 1) * 20 = " + expected + " ticks, got " + (virus != null ? virus.getDuration() : "no virus"));

			// runs the level drain and the vanilla countdown together until the Stand comes
			int start = 1000;
			player.experienceLevel = start;
			for (int t = 0; t < expected + 20 && !power.hasPower(); t++) {
				player.setHealth(player.getMaxHealth());
				player.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get()).onTick();
				MobEffectInstance effect = player.getEffect(ModStatusEffects.STAND_VIRUS);
				if (effect != null && !effect.tick(player, () -> {})) {
					player.removeEffect(ModStatusEffects.STAND_VIRUS);
				}
			}
			helper.assertTrue(power.hasPower(), "The virus never granted the Stand");
			int taken = start - player.experienceLevel;
			helper.assertTrue(taken == cost,
					"The virus must take the full cost of " + cost + " levels before the Stand, took " + taken);
		}
		finally {
			StandVirusActualEffect.resetStandsGotFromArrow(player);
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void arrowPoolModeLineShowsForLanHost(GameTestHelper helper) {
		helper.assertTrue(StandArrowItem.notInSinglePlayer(false, false),
				"Connected to a remote server: the pool mode line must show");
		helper.assertTrue(StandArrowItem.notInSinglePlayer(true, true),
				"1.16 showed the pool mode line to the host of a world opened to LAN");
		helper.assertTrue(!StandArrowItem.notInSinglePlayer(true, false),
				"Unpublished single player must hide the pool mode line");
		helper.assertTrue("jojo.arrow.least_taken_mode".equals(StandArrowItem.poolModeTooltipKey(
				StandRandomPoolFilter.LEAST_TAKEN, StandArrowItem.notInSinglePlayer(true, true))),
				"The LAN host's Arrow tooltip lost the least_taken_mode line");
		helper.succeed();
	}
}
