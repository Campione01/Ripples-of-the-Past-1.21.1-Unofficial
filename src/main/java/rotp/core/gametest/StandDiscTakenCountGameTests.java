package rotp.core.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandUtil.StandRandomPoolFilter;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.ServerDuplicateCounter.StandHolders;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 StandPower.putOutStand() = clear(false) (StandPower.java:170-197): a player's Stand put onto a disc
 * (Stand Remover eject StandRemoverItem.java:78, survival disc swap StandDiscItem.java:80) stayed in
 * SaveFileUtilCap.timesStandsTaken, so standArrowMode NOT_TAKEN kept refusing it. A disc marked with
 * WS_TAG "WSPutOut" gave its Stand back without counting it again (giveStandFromInstance standExistedInWorld).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandDiscTakenCountGameTests {
	private static final ResourceLocation A_ID = JojoMod.resLoc("star_platinum");
	private static final ResourceLocation B_ID = JojoMod.resLoc("the_world");

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void standOnADiscStaysTakenLike116PutOutStand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandHolders holders = StandHolders.get(level.getServer());
		CompoundTag holdersBefore = holders.save(new CompoundTag(), level.registryAccess());
		StandType a = JojoRegistries.DEFAULT_STANDS_REG.get(A_ID);
		StandType b = JojoRegistries.DEFAULT_STANDS_REG.get(B_ID);
		helper.assertTrue(a != null && b != null, "Missing registered Star Platinum / The World");
		List<StandType> pool = List.of(a, b);
		// other tests' records of a and b: freed here, put back in finally
		Map<UUID, ResourceLocation> displaced = new HashMap<>();
		Map<ResourceLocation, Integer> onDiscBefore = new HashMap<>();
		List<FakePlayer> players = new ArrayList<>();
		FakePlayer ejector = fakePlayer(helper, "DiscCountEjector", players);
		FakePlayer receiver = fakePlayer(helper, "DiscCountReceiver", players);
		FakePlayer roller = fakePlayer(helper, "DiscCountRoller", players);
		try {
			CompoundTag held = holdersBefore.getCompound("Holders");
			for (String key : held.getAllKeys()) {
				ResourceLocation standId = ResourceLocation.tryParse(held.getString(key));
				if (A_ID.equals(standId) || B_ID.equals(standId)) {
					displaced.put(UUID.fromString(key), standId);
				}
			}
			displaced.keySet().forEach(playerId -> holders.setHeld(playerId, null));
			for (ResourceLocation id : List.of(A_ID, B_ID)) {
				onDiscBefore.put(id, holders.getOnDisc(id));
				while (holders.takeFromDisc(id)) {
				}
			}
			helper.assertTrue(taken(holders, A_ID) == 0 && taken(holders, B_ID) == 0,
					"Fixture could not free Star Platinum and The World");

			// Stand Remover eject: the Stand sits on a disc, NOT_TAKEN still refuses it
			power(ejector).setStand(a);
			ejector.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STAND_EJECT.get()));
			helper.assertTrue(ModItems.STAND_EJECT.get().use(level, ejector, InteractionHand.MAIN_HAND).getResult().consumesAction()
					&& !power(ejector).hasPower(), "Stand Eject did not eject Star Platinum");
			ItemStack ejected = findDisc(ejector.getInventory());
			helper.assertTrue(StandDiscItem.isPutOut(ejected), "The ejected disc is not marked as holding a taken Stand: " + ejected);
			helper.assertTrue(taken(holders, A_ID) == 1 && holders.getHeld(ejector.getUUID()) == null,
					"Ejecting to a disc uncounted Star Platinum: " + taken(holders, A_ID));
			helper.assertTrue(StandUtil.limitStandPool(StandRandomPoolFilter.NOT_TAKEN, roller, pool).equals(List.of(b)),
					"NOT_TAKEN offered a Stand that sits on a disc");

			// the disc goes back into a player: still one Star Platinum
			receiver.setItemInHand(InteractionHand.MAIN_HAND, ejected);
			helper.assertTrue(ejected.use(level, receiver, InteractionHand.MAIN_HAND).getResult().consumesAction()
					&& power(receiver).getPowerType() == a, "The ejected disc did not give Star Platinum");
			helper.assertTrue(taken(holders, A_ID) == 1 && holders.getOnDisc(A_ID) == 0,
					"Re-inserting the disc counted Star Platinum twice: " + taken(holders, A_ID));

			// survival swap: a new disc counts as a new Stand, the dropped Star Platinum disc stays taken
			useDisc(helper, receiver, b);
			helper.assertTrue(taken(holders, B_ID) == 1, "A new Stand's disc was not counted: " + taken(holders, B_ID));
			helper.assertTrue(taken(holders, A_ID) == 1 && holders.getOnDisc(A_ID) == 1,
					"The swapped-out Star Platinum disc was uncounted: " + taken(holders, A_ID));

			// an unmarked disc (Creative tab, loot, /give) cannot take back the dropped disc's count
			useDisc(helper, receiver, a);
			helper.assertTrue(taken(holders, A_ID) == 2 && holders.getOnDisc(A_ID) == 1,
					"A new Star Platinum disc took the dropped disc's count: " + taken(holders, A_ID));
			helper.assertTrue(taken(holders, B_ID) == 1 && holders.getOnDisc(B_ID) == 1,
					"The swapped-out The World disc was uncounted: " + taken(holders, B_ID));

			// Creative swap keeps the disc and drops none: the old Stand is uncounted, as 1.16 clear()
			receiver.getAbilities().instabuild = true;
			ItemStack kept = useDisc(helper, receiver, b);
			receiver.getAbilities().instabuild = false;
			helper.assertTrue(kept.getCount() == 1 && taken(holders, A_ID) == 1 && holders.getOnDisc(A_ID) == 1,
					"A Creative disc swap kept the old Stand counted: " + taken(holders, A_ID));
			helper.assertTrue(taken(holders, B_ID) == 2, "The Creative disc's Stand was not counted: " + taken(holders, B_ID));

			// an evolution replace (no disc) moves the count from the old Stand to the new one
			helper.assertTrue(StandPowerTransitions.replace(power(receiver), B_ID, new StandInstance(a)).applied(),
					"Could not replace The World");
			helper.assertTrue(taken(holders, B_ID) == 1 && taken(holders, A_ID) == 2
					&& holders.getOnDisc(A_ID) == 1 && holders.getOnDisc(B_ID) == 1,
					"A replace without a disc changed the disc count: a " + taken(holders, A_ID) + ", b " + taken(holders, B_ID));

			// the disc count survives a save and load
			StandHolders loaded = StandHolders.load(holders.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			helper.assertTrue(loaded.getOnDisc(A_ID) == 1 && loaded.getOnDisc(B_ID) == 1
					&& loaded.counter.getSeenOnServer(A_ID) == taken(holders, A_ID)
					&& loaded.counter.getSeenOnServer(B_ID) == taken(holders, B_ID),
					"Stands on discs were not saved");

			// the dropped marked disc through a dispenser-style insert: counted once
			ItemEntity dropped = level.getEntitiesOfClass(ItemEntity.class, receiver.getBoundingBox().inflate(4),
					item -> StandDiscItem.isPutOut(item.getItem()) && StandDiscItem.getStandInstance(item.getItem()) != null
							&& A_ID.equals(StandDiscItem.getStandInstance(item.getItem()).getStandId()))
					.stream().findFirst().orElse(null);
			helper.assertTrue(dropped != null, "The swapped-out Star Platinum disc was not dropped");
			ItemStack droppedDisc = dropped.getItem();
			helper.assertTrue(StandDiscItem.giveStandFromDisc(roller, droppedDisc) && power(roller).getPowerType() == a,
					"The dropped disc did not give Star Platinum");
			helper.assertTrue(taken(holders, A_ID) == 2 && holders.getOnDisc(A_ID) == 0 && !StandDiscItem.isPutOut(droppedDisc),
					"A marked disc inserted by a dispenser counted Star Platinum twice: " + taken(holders, A_ID));
		}
		finally {
			for (FakePlayer player : players) {
				player.getAbilities().instabuild = false;
				power(player).setStand(null);
				player.getInventory().clearContent();
			}
			level.getEntitiesOfClass(ItemEntity.class, receiver.getBoundingBox().inflate(4),
					item -> item.getItem().is(ModItems.STAND_DISC.get())).forEach(ItemEntity::discard);
			players.forEach(FakePlayer::discard);
			for (ResourceLocation id : List.of(A_ID, B_ID)) {
				while (holders.takeFromDisc(id)) {
				}
				for (int i = onDiscBefore.getOrDefault(id, 0); i > 0; i--) {
					holders.putOnDisc(id);
				}
			}
			displaced.forEach(holders::setHeld);
		}
		// the server-wide record is left exactly as other tests had it
		helper.assertTrue(holders.save(new CompoundTag(), level.registryAccess()).equals(holdersBefore),
				"Taken-Stand records were not restored");
		helper.succeed();
	}

	private static int taken(StandHolders holders, ResourceLocation standId) {
		return holders.counter.getSeenOnServer(standId);
	}

	// a disc with a new Stand (not marked), used from the main hand
	private static ItemStack useDisc(GameTestHelper helper, FakePlayer player, StandType stand) {
		ItemStack disc = StandDiscItem.withStand(new StandInstance(stand));
		player.setItemInHand(InteractionHand.MAIN_HAND, disc);
		helper.assertTrue(disc.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction()
				&& PowerClass.STAND.attachGet(player).getPowerType() == stand, "The disc swap did not give " + stand.getId());
		return disc;
	}

	private static ItemStack findDisc(Inventory inventory) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(ModItems.STAND_DISC.get())) {
				return inventory.getItem(slot);
			}
		}
		return ItemStack.EMPTY;
	}

	private static FakePlayer fakePlayer(GameTestHelper helper, String name, List<FakePlayer> players) {
		FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		player.moveTo(at.x, at.y, at.z, 0, 0);
		player.getAbilities().instabuild = false;
		players.add(player);
		return player;
	}

	private static StandPower power(FakePlayer player) {
		return PowerClass.STAND.attachGet(player);
	}
}
