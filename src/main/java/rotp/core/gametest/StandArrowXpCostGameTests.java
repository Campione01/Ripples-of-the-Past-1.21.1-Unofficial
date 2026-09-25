package rotp.core.gametest;

import java.util.List;
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

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowHandler: the Arrow cost was standXpCostInitial (30) + standXpCostIncrease (5) per Stand already got
 * from an Arrow, minus 2 levels per Spiritual Strength (stand_arrow_xp_reduction) level on the Arrow. Removing a
 * Stand kept that count; Full Stand Clear reset it. The virus lasted (cost + 1) * 20 ticks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowXpCostGameTests {
	private static final int SPIRITUAL_STRENGTH = 3;

	private StandArrowXpCostGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void arrowXpCostRisesPerArrowStandAndFullClearResetsIt(GameTestHelper helper) {
		helper.assertTrue(configDefault("standXpCostInitial") == 30 && configDefault("standXpCostIncrease") == 5,
				"1.16 defaults were standXpCostInitial 30 and standXpCostIncrease 5");
		JojoModConfig.Common config = JojoModConfig.getCommonConfigInstance(false);
		int initial = config.standXpCostInitial.get();
		int increase = config.standXpCostIncrease.get();
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = PowerClass.STAND.attachGet(player);
			StandVirusActualEffect.resetStandsGotFromArrow(player);
			helper.assertTrue(cost(player, ItemStack.EMPTY) == initial,
					"A player with no Arrow Stands pays the initial cost " + initial + ", got " + cost(player, ItemStack.EMPTY));

			int consumed = arrowStandLevelsTaken(helper, player, power, new ItemStack(ModItems.STAND_ARROW.get()), (initial + 1) * 20);
			helper.assertTrue(consumed == initial, "The first Arrow Stand took " + consumed + " levels, expected " + initial);
			helper.assertTrue(StandVirusActualEffect.getStandsGotFromArrow(player) == 1,
					"The virus grant was not counted: " + StandVirusActualEffect.getStandsGotFromArrow(player));
			int second = initial + increase;
			helper.assertTrue(cost(player, ItemStack.EMPTY) == second,
					"After one Arrow Stand the cost is " + second + ", got " + cost(player, ItemStack.EMPTY));

			Holder<Enchantment> reduction = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
					.getHolderOrThrow(StandVirusActualEffect.STAND_ARROW_XP_REDUCTION);
			ItemStack enchanted = new ItemStack(ModItems.STAND_ARROW.get());
			helper.assertTrue(reduction.value().canEnchant(enchanted) && reduction.value().getMaxLevel() == 5
					&& reduction.is(EnchantmentTags.IN_ENCHANTING_TABLE),
					"Spiritual Strength must be a table enchantment up to V for Stand Arrows");
			enchanted.enchant(reduction, SPIRITUAL_STRENGTH);
			int reduced = Math.max(second - 2 * SPIRITUAL_STRENGTH, 0);
			helper.assertTrue(cost(player, enchanted) == reduced,
					"Spiritual Strength III takes 6 levels off: expected " + reduced + ", got " + cost(player, enchanted));

			helper.assertTrue(use(helper, player, ModItems.STAND_REMOVER_ONE_TIME.get()) && !power.hasPower(),
					"Remove Stand did not remove the Stand");
			helper.assertTrue(StandVirusActualEffect.getStandsGotFromArrow(player) == 1,
					"Remove Stand must keep the Arrow cost penalty: " + StandVirusActualEffect.getStandsGotFromArrow(player));

			// 1.16: the live virus lasts (risen cost + 1) * 20, the Arrow's enchantment not counted
			consumed = arrowStandLevelsTaken(helper, player, power, enchanted, (second + 1) * 20);
			helper.assertTrue(consumed == reduced,
					"The enchanted Arrow's virus took " + consumed + " levels, expected " + reduced);
			helper.assertTrue(StandVirusActualEffect.getStandsGotFromArrow(player) == 2,
					"The second Arrow Stand was not counted: " + StandVirusActualEffect.getStandsGotFromArrow(player));

			helper.assertTrue(use(helper, player, ModItems.STAND_FULL_CLEAR_ONE_TIME.get()) && !power.hasPower(),
					"Full Stand Clear did not clear the Stand");
			helper.assertTrue(StandVirusActualEffect.getStandsGotFromArrow(player) == 0
					&& cost(player, ItemStack.EMPTY) == initial,
					"Full Stand Clear must reset the Arrow cost to " + initial + ", got " + cost(player, ItemStack.EMPTY));
		}
		finally {
			StandVirusActualEffect.resetStandsGotFromArrow(player);
			player.discard();
		}
		helper.succeed();
	}

	// 1.16 giveStandFromArrow: a creative Arrow Stand is counted too
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void creativeArrowStandCountsTowardTheCost(GameTestHelper helper) {
		JojoModConfig.Common config = JojoModConfig.getCommonConfigInstance(false);
		int risen = config.standXpCostInitial.get() + config.standXpCostIncrease.get();
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.CREATIVE);
		try {
			player.getAbilities().instabuild = true;
			StandPower power = PowerClass.STAND.attachGet(player);
			StandVirusActualEffect.resetStandsGotFromArrow(player);
			helper.assertTrue(StandArrowItem.onPiercedByArrow(player, new ItemStack(ModItems.STAND_ARROW.get()),
					helper.getLevel(), Optional.empty()) && power.hasPower()
					&& !player.hasEffect(ModStatusEffects.STAND_VIRUS),
					"The creative Arrow did not give the Stand at once");
			helper.assertTrue(StandVirusActualEffect.getStandsGotFromArrow(player) == 1,
					"A creative Arrow Stand must count toward the cost: " + StandVirusActualEffect.getStandsGotFromArrow(player));
			helper.assertTrue(cost(player, ItemStack.EMPTY) == risen,
					"After a creative Arrow Stand the cost is " + risen + ", got " + cost(player, ItemStack.EMPTY));
		}
		finally {
			StandVirusActualEffect.resetStandsGotFromArrow(player);
			player.discard();
		}
		helper.succeed();
	}

	private static int cost(LivingEntity user, ItemStack arrow) {
		return StandVirusActualEffect.getStandXpLevelsRequirement(user, arrow);
	}

	// pierces the player and runs the virus until it grants the Stand; returns the levels it took
	private static int arrowStandLevelsTaken(GameTestHelper helper, Player player, StandPower power, ItemStack arrow,
			int expectedDuration) {
		helper.assertTrue(!power.hasPower(), "The player already has a Stand");
		helper.assertTrue(StandArrowItem.onPiercedByArrow(player, arrow, helper.getLevel(), Optional.empty()),
				"The Arrow did not infect the player");
		// the duration the Arrow really applied, not the helper
		MobEffectInstance virus = player.getEffect(ModStatusEffects.STAND_VIRUS);
		helper.assertTrue(virus != null && virus.getDuration() == expectedDuration,
				"1.16 virus lasted (cost + 1) * 20 = " + expectedDuration + " ticks, got "
						+ (virus != null ? virus.getDuration() : "no virus"));
		int start = 200;
		player.experienceLevel = start;
		for (int i = 0; i < 100 && !power.hasPower(); i++) {
			player.setHealth(player.getMaxHealth());
			for (int t = 0; t < 10; t++) {
				player.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get()).onTick();
			}
		}
		helper.assertTrue(power.hasPower(), "The virus never granted the Stand");
		return start - player.experienceLevel;
	}

	private static boolean use(GameTestHelper helper, Player player, Item item) {
		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		boolean used = item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction();
		player.getInventory().clearContent();
		return used;
	}

	private static int configDefault(String key) {
		ModConfigSpec.ConfigValue<Integer> value = JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", key));
		return value.getDefault();
	}
}
