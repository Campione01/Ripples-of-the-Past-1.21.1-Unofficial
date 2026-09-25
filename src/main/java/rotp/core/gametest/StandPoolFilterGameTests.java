package rotp.core.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.StandUtil.StandRandomPoolFilter;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.ServerDuplicateCounter.StandHolders;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 standArrowMode (JojoModConfig.java:246-253, StandUtil.java:61-64 and 83-104, SaveFileUtilCap.java:80-127):
 * LEAST_TAKEN / NOT_TAKEN narrowed the random Stand pool by how many players on the server hold each Stand,
 * an empty pool gave "jojo.arrow.all_stands_taken", and arrows named the mode in their tooltip on multiplayer.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandPoolFilterGameTests {

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void standArrowModeFollowsServerWideTakenStands(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<List<? extends String>> banned = specValue("bannedStands");
		ModConfigSpec.ConfigValue<StandRandomPoolFilter> mode = specValue("standArrowMode");
		List<? extends String> previousBanned = banned.get();
		StandRandomPoolFilter previousMode = mode.get();
		ServerLevel level = helper.getLevel();
		StandHolders holders = StandHolders.get(level.getServer());
		CompoundTag holdersBefore = holders.save(new CompoundTag(), level.registryAccess());
		// records of other tests' players (kept like offline holders); freed here, put back in finally
		Map<UUID, ResourceLocation> displaced = new HashMap<>();
		List<FakePlayer> players = new ArrayList<>();
		FakePlayer roller = fakePlayer(level, "PoolRoller", players);
		FakePlayer first = fakePlayer(level, "PoolHolderA", players);
		FakePlayer second = fakePlayer(level, "PoolHolderB", players);
		FakePlayer third = fakePlayer(level, "PoolHolderC", players);
		try {
			List<StandType> free = StandUtil.standsForPlayerArrow()
					.filter(stand -> stand.getStandStats().getRandomWeight() > 0)
					.limit(2)
					.toList();
			helper.assertTrue(free.size() == 2, "Fixture needs two arrow Stands");
			StandType a = free.get(0);
			StandType b = free.get(1);
			CompoundTag held = holdersBefore.getCompound("Holders");
			for (String key : held.getAllKeys()) {
				ResourceLocation standId = ResourceLocation.tryParse(held.getString(key));
				if (a.getId().equals(standId) || b.getId().equals(standId)) {
					displaced.put(UUID.fromString(key), standId);
				}
			}
			displaced.keySet().forEach(playerId -> holders.setHeld(playerId, null));
			helper.assertTrue(holders.counter.getSeenOnServer(a.getId()) == 0 && holders.counter.getSeenOnServer(b.getId()) == 0,
					"Fixture could not free its two arrow Stands: " + displaced);
			List<String> others = new ArrayList<>();
			StandUtil.standsForPlayerArrow().filter(stand -> stand != a && stand != b)
					.forEach(stand -> others.add(stand.getId().toString()));
			banned.set(others);
			List<StandType> pool = List.of(a, b);
			helper.assertTrue(Set.copyOf(StandUtil.standsForPlayerArrow().toList()).equals(Set.copyOf(pool)),
					"Fixture pool is not {a, b}");

			// NONE (the default) keeps every Stand even when one is taken
			mode.set(StandRandomPoolFilter.NONE);
			power(first).setStand(a);
			helper.assertTrue(holders.counter.getSeenOnServer(a.getId()) == 1
					&& a.getId().equals(holders.getHeld(first.getUUID())),
					"Getting a Stand did not count it as taken: " + holders.counter.getSeenOnServer(a.getId()));
			helper.assertTrue(Set.copyOf(StandUtil.limitStandPool(StandRandomPoolFilter.NONE, roller, pool)).equals(Set.copyOf(pool)),
					"NONE narrowed the pool");

			// NOT_TAKEN: only the Stand nobody holds
			mode.set(StandRandomPoolFilter.NOT_TAKEN);
			helper.assertTrue(StandUtil.limitStandPool(StandRandomPoolFilter.NOT_TAKEN, roller, pool).equals(List.of(b)),
					"NOT_TAKEN kept a taken Stand");
			for (int i = 0; i < 8; i++) {
				helper.assertTrue(StandUtil.randomStandOrError(roller, roller.getRandom()).left().orElse(null) == b,
						"NOT_TAKEN rolled a taken Stand");
			}
			// every Stand taken: the 1.16 refusal
			power(second).setStand(b);
			Either<StandType, Component> refused = StandUtil.randomStandOrError(roller, roller.getRandom());
			helper.assertTrue(refused.right().map(message -> message.getContents() instanceof TranslatableContents tl
					&& "jojo.arrow.all_stands_taken".equals(tl.getKey())).orElse(false),
					"NOT_TAKEN with every Stand taken must refuse, got " + refused);

			// LEAST_TAKEN: a held twice, b once -> only b
			power(third).setStand(a);
			mode.set(StandRandomPoolFilter.LEAST_TAKEN);
			helper.assertTrue(holders.counter.getSeenOnServer(a.getId()) == 2, "Second holder of a not counted");
			helper.assertTrue(StandUtil.limitStandPool(StandRandomPoolFilter.LEAST_TAKEN, roller, pool).equals(List.of(b)),
					"LEAST_TAKEN kept the most taken Stand");
			for (int i = 0; i < 8; i++) {
				helper.assertTrue(StandUtil.randomStandOrError(roller, roller.getRandom()).left().orElse(null) == b,
						"LEAST_TAKEN rolled the most taken Stand");
			}

			// a login re-counts a Stand given before the count existed, without counting it twice
			holders.setHeld(third.getUUID(), null);
			StandUtil.TakenStandEvents.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(third));
			StandUtil.TakenStandEvents.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(third));
			helper.assertTrue(holders.counter.getSeenOnServer(a.getId()) == 2 && a.getId().equals(holders.getHeld(third.getUUID())),
					"Login did not re-count the held Stand once: " + holders.counter.getSeenOnServer(a.getId()));

			// losing a Stand frees it: a held by nobody, b once -> only a
			power(third).setStand(null);
			power(first).setStand(null);
			helper.assertTrue(holders.counter.getSeenOnServer(a.getId()) == 0 && holders.getHeld(first.getUUID()) == null,
					"Clearing a Stand did not uncount it: " + holders.counter.getSeenOnServer(a.getId()));
			helper.assertTrue(StandUtil.limitStandPool(StandRandomPoolFilter.LEAST_TAKEN, roller, pool).equals(List.of(a)),
					"LEAST_TAKEN did not follow the freed Stand");

			// the count survives a save and load
			CompoundTag saved = holders.save(new CompoundTag(), level.registryAccess());
			StandHolders loaded = StandHolders.load(saved, level.registryAccess());
			helper.assertTrue(loaded.counter.getSeenOnServer(b.getId()) == holders.counter.getSeenOnServer(b.getId())
					&& b.getId().equals(loaded.getHeld(second.getUUID())), "Taken Stands were not saved");

			// the client copy drives the arrow tooltip line (multiplayer only)
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
			new JojoModConfig.Common.SyncedValues(JojoModConfig.getCommonConfigInstance(false)).writeToBuf(buf);
			JojoModConfig.applySyncedConfig(new JojoModConfig.Common.SyncedValues(buf));
			helper.assertTrue(JojoModConfig.getCommonConfigInstance(true).standArrowMode.get() == StandRandomPoolFilter.LEAST_TAKEN,
					"standArrowMode did not reach the synced client config");
			helper.assertTrue("jojo.arrow.least_taken_mode".equals(StandArrowItem.poolModeTooltipKey(StandRandomPoolFilter.LEAST_TAKEN, true))
					&& "jojo.arrow.not_taken_mode".equals(StandArrowItem.poolModeTooltipKey(StandRandomPoolFilter.NOT_TAKEN, true))
					&& StandArrowItem.poolModeTooltipKey(StandRandomPoolFilter.NONE, true) == null
					&& StandArrowItem.poolModeTooltipKey(StandRandomPoolFilter.LEAST_TAKEN, false) == null,
					"Arrow tooltip pool mode line differs from 1.16");
		}
		finally {
			banned.set(previousBanned);
			mode.set(previousMode);
			JojoModConfig.resetSyncedConfig();
			for (FakePlayer player : players) {
				power(player).setStand(null);
				player.discard();
			}
			displaced.forEach(holders::setHeld);
		}
		// the server-wide record is left exactly as other tests had it
		helper.assertTrue(holders.save(new CompoundTag(), level.registryAccess()).equals(holdersBefore),
				"Taken-Stand records were not restored");
		helper.succeed();
	}

	private static FakePlayer fakePlayer(ServerLevel level, String name, List<FakePlayer> players) {
		FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
		players.add(player);
		return player;
	}

	private static StandPower power(FakePlayer player) {
		return PowerClass.STAND.attachGet(player);
	}

	private static <T> ModConfigSpec.ConfigValue<T> specValue(String key) {
		// spec setters are memory-only; restored in finally, the config file is never saved
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", key));
	}
}
