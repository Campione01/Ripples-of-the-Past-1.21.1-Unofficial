package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.ServerSavedData;
import rotp.core.command.commands.RockPaperScissorsCommand;
import rotp.core.core.JojoMod;
import rotp.core.impl.npc.rps.RPSPvpGamesMap;
import rotp.core.impl.npc.rps.RockPaperScissorsGame;
import rotp.core.impl.npc.rps.RockPaperScissorsGame.Pick;
import rotp.core.init.ModCustomStats;
import rotp.core.network.c2s.ClRPSGameInputPacket;
import rotp.core.network.s2c.RPSGameStatePacket;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RockPaperScissorsGameTests {
	private RockPaperScissorsGameTests() {}

	/** 1.16 awards jojo:rps_won to the match winner, NPC games included. */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void npcMatchWinAwardsRpsWonStat(GameTestHelper helper) {
		helper.assertTrue(BuiltInRegistries.CUSTOM_STAT.containsKey(ModCustomStats.RPS_WON),
				"rps_won custom stat is not registered");
		ServerSavedData data = ServerSavedData.get(helper.getLevel().getServer());
		Players players = new Players(helper);
		try {
			StatPlayer player = players.add("RpsNpcWinner");
			Pig npc = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 3));
			data.rpsPvpGames.put(player, npc.getUUID(), true);
			for (int round = 1; round <= RockPaperScissorsGame.WINS_NEEDED; round++) {
				RockPaperScissorsGame game = data.rpsPvpGames.get(player.getUUID());
				helper.assertTrue(game != null, "NPC match ended before round " + round);
				// the NPC plays its thoughts pick, so paper wins every round
				game.setOpponentThoughts(Pick.ROCK);
				pick(player, Pick.PAPER);
			}
			helper.assertFalse(data.rpsPvpGames.has(player.getUUID()), "finished NPC match was kept");
			helper.assertTrue(player.rpsWins == 1,
					"NPC match winner got " + player.rpsWins + " rps_won, expected 1");
		}
		finally {
			players.close(data);
		}
		helper.succeed();
	}

	/** B left a B-C match without quitting (death, relog) and accepts A: C must not reach the A-B match. */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void staleOpponentCannotHijackNewMatch(GameTestHelper helper) {
		ServerSavedData data = ServerSavedData.get(helper.getLevel().getServer());
		Players players = new Players(helper);
		try {
			StatPlayer a = players.add("RpsNewInviter");
			StatPlayer b = players.add("RpsRejoiner");
			StatPlayer c = players.add("RpsOldOpponent");
			RockPaperScissorsCommand.startGame(data, b, c);
			RockPaperScissorsCommand.startGame(data, a, b);
			helper.assertTrue(data.rpsPvpGames.isMutualPvp(a.getUUID(), b.getUUID()), "A-B match did not start");
			helper.assertFalse(data.rpsPvpGames.has(c.getUUID()), "C kept a game pointing at B");
			pick(c, Pick.ROCK);
			quit(c);
			assertMatchUntouched(helper, data, a, b, "after C's pick and quit");

			// a stale C game (old save) must be refused by the packet handler itself
			data.rpsPvpGames.put(c, b.getUUID(), false);
			pick(c, Pick.ROCK);
			assertMatchUntouched(helper, data, a, b, "after a stale pick");
			helper.assertFalse(data.rpsPvpGames.has(c.getUUID()), "stale C game survived its pick");
			data.rpsPvpGames.put(c, b.getUUID(), false);
			quit(c);
			assertMatchUntouched(helper, data, a, b, "after a stale quit");
			helper.assertFalse(data.rpsPvpGames.has(c.getUUID()), "stale C game survived its quit");
		}
		finally {
			players.close(data);
		}
		helper.succeed();
	}

	/** 1.16 keeps an unfinished PvP match on quit; a new invite resumes its score and pick history. */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void quitPausesPvpMatchAndReinviteResumesIt(GameTestHelper helper) {
		ServerSavedData data = ServerSavedData.get(helper.getLevel().getServer());
		Players players = new Players(helper);
		try {
			StatPlayer a = players.add("RpsPauseA");
			StatPlayer b = players.add("RpsPauseB");
			RecordingConnection aConnection = new RecordingConnection(a);
			RockPaperScissorsCommand.startGame(data, a, b);
			long firstEpoch = data.rpsPvpGames.get(a.getUUID()).sessionEpoch();
			pick(a, Pick.ROCK);
			pick(b, Pick.SCISSORS);
			// a pending pick is dropped by the pause
			pick(b, Pick.PAPER);
			quit(a);
			helper.assertFalse(data.rpsPvpGames.has(a.getUUID()) || data.rpsPvpGames.has(b.getUUID()),
					"quit left a game running");

			RPSPvpGamesMap reloaded = new RPSPvpGamesMap();
			reloaded.load(data.rpsPvpGames.save());
			helper.assertTrue(reloaded.startPvp(a, b), "paused match was not saved");
			helper.assertTrue(reloaded.get(a.getUUID()).playerWins() == 1, "saved paused match lost its score");

			RockPaperScissorsCommand.startGame(data, b, a);
			RockPaperScissorsGame aGame = data.rpsPvpGames.get(a.getUUID());
			RockPaperScissorsGame bGame = data.rpsPvpGames.get(b.getUUID());
			helper.assertTrue(aGame != null && bGame != null, "re-invite did not start a match");
			helper.assertTrue(aGame.round() == 2 && bGame.round() == 2,
					"re-invite restarted the match at round " + aGame.round());
			helper.assertTrue(aGame.playerWins() == 1 && bGame.opponentWins() == 1, "re-invite lost the score");
			helper.assertTrue(aGame.playerPreviousPicks().equals(List.of(Pick.ROCK))
					&& bGame.playerPreviousPicks().equals(List.of(Pick.SCISSORS)), "re-invite lost the pick history");
			helper.assertTrue(bGame.playerPick() == null && aGame.opponentPick() == null,
					"pending pick survived the pause");
			helper.assertTrue(aGame.sessionEpoch() != firstEpoch, "resumed match kept the old session epoch");
			RPSGameStatePacket entered = aConnection.lastRpsState();
			helper.assertTrue(entered != null && "ENTER".equals(String.valueOf(field(entered, "packetType"))),
					"A was not sent the resumed match");
			helper.assertTrue(Integer.valueOf(2).equals(field(entered, "round"))
					&& List.of(Pick.ROCK).equals(field(entered, "playerPicks"))
					&& List.of(Pick.SCISSORS).equals(field(entered, "opponentPicks")),
					"resumed match screen got round " + field(entered, "round") + ", picks "
							+ field(entered, "playerPicks") + " / " + field(entered, "opponentPicks"));

			for (int i = 0; i < 2; i++) {
				pick(a, Pick.PAPER);
				pick(b, Pick.ROCK);
			}
			helper.assertFalse(data.rpsPvpGames.has(a.getUUID()) || data.rpsPvpGames.has(b.getUUID()),
					"finished match was kept");
			helper.assertTrue(a.rpsWins == 1 && b.rpsWins == 0,
					"rps_won: winner " + a.rpsWins + ", loser " + b.rpsWins + ", expected 1 and 0");

			RockPaperScissorsCommand.startGame(data, a, b);
			helper.assertTrue(data.rpsPvpGames.get(a.getUUID()).round() == 1, "a finished match was resumed");
		}
		finally {
			players.close(data);
		}
		helper.succeed();
	}

	/** 1.16 resets the cheat per screen: a resumed match may cheat again in the paused round. */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void resumedMatchRestoresRoundCheat(GameTestHelper helper) {
		StatPlayer a = new StatPlayer(helper, "RpsCheatResumeA");
		StatPlayer b = new StatPlayer(helper, "RpsCheatResumeB");
		RPSPvpGamesMap games = new RPSPvpGamesMap();
		helper.assertFalse(games.startPvp(a, b), "a fresh pair resumed a match");
		RockPaperScissorsGame first = games.get(a.getUUID());
		helper.assertTrue(first.tryUseCheat(first.sessionEpoch()), "first cheat of the round was refused");
		helper.assertFalse(first.tryUseCheat(first.sessionEpoch()), "second cheat in one session round was accepted");
		helper.assertTrue(b.getUUID().equals(games.leave(a.getUUID())), "quit did not end the PvP match");

		helper.assertTrue(games.startPvp(a, b), "re-invite did not resume the paused match");
		RockPaperScissorsGame resumed = games.get(a.getUUID());
		helper.assertTrue(resumed.round() == first.round(), "resume changed the round to " + resumed.round());
		helper.assertTrue(resumed.tryUseCheat(resumed.sessionEpoch()), "resumed match kept the round's used cheat");

		// same through a world save of the paused match
		games.leave(a.getUUID());
		RPSPvpGamesMap reloaded = new RPSPvpGamesMap();
		reloaded.load(games.save());
		helper.assertTrue(reloaded.startPvp(a, b), "saved paused match was not resumed");
		RockPaperScissorsGame loaded = reloaded.get(a.getUUID());
		helper.assertTrue(loaded.tryUseCheat(loaded.sessionEpoch()), "reloaded resumed match kept the used cheat");
		helper.succeed();
	}

	private static void assertMatchUntouched(GameTestHelper helper, ServerSavedData data,
			ServerPlayer a, ServerPlayer b, String when) {
		RockPaperScissorsGame aGame = data.rpsPvpGames.get(a.getUUID());
		RockPaperScissorsGame bGame = data.rpsPvpGames.get(b.getUUID());
		helper.assertTrue(aGame != null && bGame != null, "A-B match was ended " + when);
		helper.assertTrue(b.getUUID().equals(aGame.opponent()) && a.getUUID().equals(bGame.opponent()),
				"A-B match was relinked " + when);
		helper.assertTrue(aGame.opponentPick() == null && bGame.playerPick() == null && bGame.opponentPick() == null,
				"C's input reached the A-B match " + when);
		helper.assertTrue(aGame.round() == 1 && bGame.round() == 1, "A-B match advanced " + when);
	}

	private static void pick(ServerPlayer player, Pick pick) {
		ClRPSGameInputPacket.handleOnServer(player, ClRPSGameInputPacket.pick(pick));
	}

	private static void quit(ServerPlayer player) {
		ClRPSGameInputPacket.handleOnServer(player, ClRPSGameInputPacket.quitGame());
	}

	private static Object field(RPSGameStatePacket packet, String name) {
		try {
			Field field = RPSGameStatePacket.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(packet);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not read RPSGameStatePacket." + name, error);
		}
	}

	/** Keeps the packets sent to a fake player. */
	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void send(Packet<?> packet) {
			packets.add(packet);
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}

		@Nullable
		RPSGameStatePacket lastRpsState() {
			for (int i = packets.size() - 1; i >= 0; i--) {
				if (packets.get(i) instanceof ClientboundCustomPayloadPacket custom
						&& custom.payload() instanceof RPSGameStatePacket state) {
					return state;
				}
			}
			return null;
		}
	}

	/** Records rps_won; the plain FakePlayer drops stats. */
	private static final class StatPlayer extends FakePlayer {
		int rpsWins;

		StatPlayer(GameTestHelper helper, String name) {
			super(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void awardStat(Stat<?> stat, int amount) {
			if (stat.getType() == Stats.CUSTOM && ModCustomStats.RPS_WON.equals(stat.getValue())) {
				rpsWins += amount;
			}
		}
	}

	/** Lists fake players by UUID in the PlayerList so the server-side opponent lookup finds them. */
	private static final class Players {
		private final GameTestHelper helper;
		private final Map<UUID, ServerPlayer> byUuid;
		private final List<StatPlayer> added = new ArrayList<>();

		@SuppressWarnings("unchecked")
		Players(GameTestHelper helper) {
			this.helper = helper;
			try {
				Field field = PlayerList.class.getDeclaredField("playersByUUID");
				field.setAccessible(true);
				byUuid = (Map<UUID, ServerPlayer>) field.get(helper.getLevel().getServer().getPlayerList());
			}
			catch (ReflectiveOperationException error) {
				throw new AssertionError("Could not reach PlayerList.playersByUUID", error);
			}
		}

		StatPlayer add(String name) {
			StatPlayer player = new StatPlayer(helper, name);
			Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1 + added.size(), 2, 1)));
			player.moveTo(position.x, position.y, position.z);
			byUuid.put(player.getUUID(), player);
			added.add(player);
			return player;
		}

		void close(ServerSavedData data) {
			for (StatPlayer player : added) {
				byUuid.remove(player.getUUID());
				data.rpsPvpGames.remove(player.getUUID());
			}
		}
	}
}
