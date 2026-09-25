package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.mechanics.ContextualVillagerTradeEvents;
import rotp.core.subsystems.itemtracking.ItemTracker;
import rotp.core.subsystems.itemtracking.ItemTracking;
import rotp.core.subsystems.itemtracking.KnownItemState;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TrackerItemStack.onShrink: when Gold Experience takes a tracked item from a villager, the villager gets
 * MAJOR_NEGATIVE gossip 25 about the thief and refuses to trade with them (CustomVillagerTrades: unhappy 40,
 * interaction consumed); taking a piglin's barter gold counts as hurting the piglin, other items do not.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceScamGameTests {
	private GoldExperienceScamGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void takingVillagerItemMakesItRefuseOnlyTheThief(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player thief = addPlayer(helper, "7c1e0b48-0000-4000-8000-00000000b148", "GoldExperienceScamThief", new BlockPos(1, 2, 1));
		Player bystander = FakePlayerFactory.get(level, new GameProfile(
				UUID.fromString("7c1e0b48-0000-4000-8000-00000000c148"), "GoldExperienceScamBystander"));
		List<Entity> spawned = new ArrayList<>();
		List<ItemTracker> trackers = new ArrayList<>();
		try {
			Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
			spawned.add(villager);
			// one offer, so vanilla would open trading
			villager.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 12, 1, 0.05F));
			ItemStack wheat = new ItemStack(Items.WHEAT);
			villager.getInventory().setItem(0, wheat);

			ItemTracker tracker = track(helper, trackers, thief, villager, wheat);
			ItemStack taken = tracker.clearAndCopyItem(level);
			helper.assertTrue(taken != null && taken.is(Items.WHEAT), "Tracked villager wheat was not taken: " + taken);
			helper.assertTrue(villager.getInventory().getItem(0).isEmpty(), "Taken wheat is still in the villager inventory");

			int badGossip = villager.getGossips().getReputation(thief.getUUID(), type -> type == GossipType.MAJOR_NEGATIVE);
			helper.assertTrue(badGossip == -125, "Thief MAJOR_NEGATIVE reputation was " + badGossip + ", expected -125 (25 x -5)");
			helper.assertTrue(ContextualVillagerTradeEvents.refusesTradingWith(villager, thief.getUUID()),
					"Scammed villager does not refuse the thief");
			helper.assertTrue(!ContextualVillagerTradeEvents.refusesTradingWith(villager, bystander.getUUID()),
					"Scammed villager refuses a player who took nothing");

			// the refusal is saved with the villager
			Villager reloaded = EntityType.VILLAGER.create(level);
			helper.assertTrue(reloaded != null, "Could not create a villager to reload");
			reloaded.load(villager.saveWithoutId(new CompoundTag()));
			helper.assertTrue(ContextualVillagerTradeEvents.refusesTradingWith(reloaded, thief.getUUID()),
					"Refusal was lost after saving and loading the villager");

			InteractionResult thiefResult = thief.interactOn(villager, InteractionHand.MAIN_HAND);
			helper.assertTrue(thiefResult == InteractionResult.CONSUME, "Thief interaction returned " + thiefResult + ", expected CONSUME");
			helper.assertTrue(!villager.isTrading(), "Scammed villager opened trading with the thief");
			helper.assertTrue(villager.getUnhappyCounter() == 40,
					"Refusing villager unhappy counter was " + villager.getUnhappyCounter() + ", expected 40");

			villager.setUnhappyCounter(0);
			bystander.interactOn(villager, InteractionHand.MAIN_HAND);
			helper.assertTrue(villager.getTradingPlayer() == bystander, "Scammed villager did not trade with an innocent player");
			helper.assertTrue(villager.getUnhappyCounter() == 0, "Innocent player got the refusal (unhappy counter set)");
			bystander.closeContainer();
			villager.setTradingPlayer(null);
			helper.succeed();
		}
		finally {
			cleanup(level, spawned, trackers, thief);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void takingPiglinBarterGoldAngersItButOtherItemsDoNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player thief = addPlayer(helper, "7c1e0b48-0000-4000-8000-00000000d148", "GoldExperienceScamPiglinThief", new BlockPos(1, 2, 1));
		List<Entity> spawned = new ArrayList<>();
		List<ItemTracker> trackers = new ArrayList<>();
		try {
			Piglin ironPiglin = helper.spawn(EntityType.PIGLIN, new BlockPos(2, 2, 1));
			spawned.add(ironPiglin);
			ItemStack iron = new ItemStack(Items.IRON_INGOT);
			ironPiglin.getInventory().setItem(0, iron);
			ItemStack takenIron = track(helper, trackers, thief, ironPiglin, iron).clearAndCopyItem(level);
			helper.assertTrue(takenIron != null && takenIron.is(Items.IRON_INGOT), "Tracked piglin iron was not taken: " + takenIron);
			helper.assertTrue(!ironPiglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_DISABLED),
					"Taking a non-barter item made the piglin react as if hurt");

			Piglin goldPiglin = helper.spawn(EntityType.PIGLIN, new BlockPos(2, 2, 2));
			spawned.add(goldPiglin);
			ItemStack gold = new ItemStack(Items.GOLD_INGOT);
			goldPiglin.getInventory().setItem(0, gold);
			ItemStack takenGold = track(helper, trackers, thief, goldPiglin, gold).clearAndCopyItem(level);
			helper.assertTrue(takenGold != null && takenGold.is(Items.GOLD_INGOT), "Tracked piglin gold was not taken: " + takenGold);
			helper.assertTrue(goldPiglin.getInventory().getItem(0).isEmpty(), "Taken gold is still in the piglin inventory");
			helper.assertTrue(goldPiglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_DISABLED),
					"Taking the piglin's barter gold did not count as hurting it (no ADMIRING_DISABLED)");
			helper.succeed();
		}
		finally {
			cleanup(level, spawned, trackers, thief);
		}
	}

	private static Player addPlayer(GameTestHelper helper, String uuid, String name, BlockPos relPos) {
		ServerLevel level = helper.getLevel();
		Player player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(uuid), name));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(relPos));
		player.moveTo(pos.x, pos.y, pos.z);
		helper.assertTrue(level.addFreshEntity(player), "Could not add player " + name);
		helper.assertTrue(level.getPlayerByUUID(player.getUUID()) == player, "Player " + name + " is not in the level");
		return player;
	}

	private static ItemTracker track(GameTestHelper helper, List<ItemTracker> trackers, Player thief,
			InventoryCarrier carrier, ItemStack stack) {
		ServerLevel level = helper.getLevel();
		Entity holder = (Entity) carrier;
		ItemTracker tracker = ItemTracking.getItemTracking(level).startTracking(stack, level);
		helper.assertTrue(tracker != null, "Could not track " + stack);
		trackers.add(tracker);
		tracker.setTrackedByPlayer(thief);
		tracker.setAtEntity(stack, holder.getId(), level, KnownItemState.ENTITY_HAS_ITEM,
				id -> carrier.getInventory().getItems().stream().anyMatch(ItemTracking.trackerIdCheck(id)));
		return tracker;
	}

	private static void cleanup(ServerLevel level, List<Entity> spawned, List<ItemTracker> trackers, Player player) {
		ItemTracking tracking = ItemTracking.getItemTracking(level);
		for (ItemTracker tracker : trackers) {
			tracking.stopTracking(tracker.trackerId, level);
		}
		for (Entity entity : spawned) {
			entity.discard();
		}
		player.discard();
	}
}
