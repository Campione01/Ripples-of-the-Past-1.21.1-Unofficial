package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandVirusMobGiver;
import rotp.core.api.stand.StandVirusMobGiverContext;
import rotp.core.api.stand.StandVirusMobGivers;
import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEnchantments;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.mechanics.standarrow.StandVirusActualEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil.StandRandomPoolFilter;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowItem: a pierced survival player got a virus lasting (their own cost + 1) * 20 ticks, so a player
 * with many Arrow Stands paid the full risen cost before the Stand came. The pool mode tooltip line showed unless
 * the game was unpublished single player, so the host of a LAN world saw it too. The virus level was 3 minus the
 * Arrow's Virus Inhibition (RARE table enchantment up to III, cost 1 + 7 * level .. + 15, not on books or trades),
 * for players and mobs alike.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowVirusDurationGameTests {
	private static final int PRIOR_ARROW_STANDS = 7;
	private static final String GIVER_TAG = "rotp_virus_inhibition_gametest_giver";
	private static final ResourceLocation GIVER_OWNER = JojoMod.resLoc("virus_inhibition_gametest_giver");

	static {
		// matches only tagged test mobs
		if (StandVirusMobGivers.get(GIVER_OWNER).isEmpty()) {
			StandVirusMobGivers.register(GIVER_OWNER, new StandVirusMobGiver() {
				@Override
				public boolean matches(LivingEntity target) {
					return target.getTags().contains(GIVER_TAG);
				}

				@Override
				public float survivalChance(StandVirusMobGiverContext context) {
					return 1.0F;
				}

				@Override
				public boolean giveStand(StandVirusMobGiverContext context) {
					return false;
				}
			});
		}
	}

	private StandArrowVirusDurationGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void virusInhibitionLowersTheArrowVirusLevel(GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> inhibition = registry.getHolderOrThrow(StandVirusActualEffect.VIRUS_INHIBITION);
		Enchantment ench = inhibition.value();
		helper.assertTrue(ench.canEnchant(new ItemStack(ModItems.STAND_ARROW.get())) && ench.getMaxLevel() == 3
				&& ench.getWeight() == 2 && ench.getMinCost(1) == 8 && ench.getMinCost(3) == 22 && ench.getMaxCost(3) == 37,
				"1.16 Virus Inhibition: RARE Stand Arrow enchantment up to III, cost 1 + 7 * level .. + 15");
		helper.assertTrue(inhibition.is(EnchantmentTags.IN_ENCHANTING_TABLE) && inhibition.is(ModEnchantments.NOT_ALLOWED_ON_BOOKS)
				&& !inhibition.is(EnchantmentTags.TRADEABLE),
				"1.16 Virus Inhibition is offered by the table, not on books and not traded");
		int atTen = levelOf(tableResults(registry, 10, new ItemStack(ModItems.STAND_ARROW.get())));
		int atThirty = levelOf(tableResults(registry, 30, new ItemStack(ModItems.STAND_ARROW.get())));
		helper.assertTrue(atTen == 1 && atThirty == 3,
				"The table must offer a Stand Arrow Virus Inhibition I at 10 and III at 30, got " + atTen + " and " + atThirty);
		for (int power = 1; power <= 40; power++) {
			int found = levelOf(tableResults(registry, power, new ItemStack(Items.BOOK)));
			helper.assertTrue(found == 0, "A plain Book at table level " + power + " was offered Virus Inhibition " + found);
		}

		// {inhibition, expected amplifier}: 1.16 getEffectLevelToApply = 3 - inhibition
		int[][] cases = { { 0, 3 }, { 1, 2 }, { 3, 0 } };
		for (int[] c : cases) {
			ItemStack arrow = new ItemStack(ModItems.STAND_ARROW.get());
			if (c[0] > 0) {
				arrow.enchant(inhibition, c[0]);
			}
			Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			try {
				helper.assertTrue(StandArrowItem.onPiercedByArrow(player, arrow.copy(), helper.getLevel(), Optional.empty()),
						"The Arrow did not infect the player");
				assertAmplifier(helper, player, c[1], "player, Virus Inhibition " + c[0]);
			}
			finally {
				StandVirusActualEffect.resetStandsGotFromArrow(player);
				player.discard();
			}

			List<Entity> created = new ArrayList<>();
			try {
				LivingEntity turtle = create(helper, ModEntityTypes.COCO_JUMBO_TURTLE.get(), created);
				helper.assertTrue(StandArrowItem.onPiercedByArrow(turtle, arrow.copy(), helper.getLevel(), Optional.empty()),
						"The Arrow did not infect the Coco Jumbo turtle");
				assertAmplifier(helper, turtle, c[1], "Coco Jumbo turtle, Virus Inhibition " + c[0]);

				LivingEntity giverCow = create(helper, EntityType.COW, created);
				giverCow.addTag(GIVER_TAG);
				helper.assertTrue(StandArrowItem.onPiercedByArrow(giverCow, arrow.copy(), helper.getLevel(), Optional.empty()),
						"The Arrow did not infect the registered Stand-giver mob");
				assertAmplifier(helper, giverCow, c[1], "Stand-giver mob, Virus Inhibition " + c[0]);
			}
			finally {
				created.forEach(Entity::discard);
			}
		}
		helper.succeed();
	}

	private static void assertAmplifier(GameTestHelper helper, LivingEntity target, int expected, String what) {
		MobEffectInstance virus = target.getEffect(ModStatusEffects.STAND_VIRUS);
		helper.assertTrue(virus != null && virus.getAmplifier() == expected,
				"1.16 virus amplifier for " + what + " is " + expected + ", got " + (virus != null ? virus.getAmplifier() : "no virus"));
	}

	private static LivingEntity create(GameTestHelper helper, EntityType<?> type, List<Entity> created) {
		Entity entity = type.create(helper.getLevel());
		helper.assertTrue(entity instanceof LivingEntity, "Could not create " + type);
		entity.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
		created.add(entity);
		return (LivingEntity) entity;
	}

	private static List<EnchantmentInstance> tableResults(Registry<Enchantment> registry, int power, ItemStack stack) {
		return EnchantmentHelper.getAvailableEnchantmentResults(power, stack,
				registry.getTag(EnchantmentTags.IN_ENCHANTING_TABLE).map(HolderSet::stream).orElseGet(java.util.stream.Stream::empty));
	}

	private static int levelOf(List<EnchantmentInstance> offers) {
		return offers.stream().filter(e -> e.enchantment.is(StandVirusActualEffect.VIRUS_INHIBITION))
				.mapToInt(e -> e.level).max().orElse(0);
	}

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
