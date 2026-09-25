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
import rotp.core.impl.npc.rps.RockPaperScissorsGame;
import rotp.core.impl.npc.rps.RockPaperScissorsGame.Pick;
import rotp.core.impl.npc.rps.RockPaperScissorsKidEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.network.c2s.ClRPSGameInputPacket;
import rotp.core.network.s2c.RPSGameStatePacket;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RockPaperScissorsKidGameTests {
	private RockPaperScissorsKidGameTests() {}

	/** A died or relogged mid PvP match (no quit packet) and talks to a kid: the match is paused, not dropped. */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void kidGamePausesOpenPvpMatch(GameTestHelper helper) {
		ServerSavedData data = ServerSavedData.get(helper.getLevel().getServer());
		Players players = new Players(helper);
		try {
			FakePlayer a = players.add("RpsKidPvpA");
			FakePlayer b = players.add("RpsKidPvpB");
			RecordingConnection bConnection = new RecordingConnection(b);
			RockPaperScissorsCommand.startGame(data, a, b);
			pick(a, Pick.ROCK);
			pick(b, Pick.SCISSORS);
			RockPaperScissorsGame before = data.rpsPvpGames.get(a.getUUID());
			helper.assertTrue(before != null && before.round() == 2 && before.playerWins() == 1,
					"A-B match did not reach round 2 with A ahead");
			bConnection.packets.clear();

			RockPaperScissorsKidEntity kid = helper.spawn(ModEntityTypes.RPS_KID.get(), new BlockPos(2, 2, 3));
			kid.mobInteract(a, InteractionHand.MAIN_HAND);
			RockPaperScissorsGame kidGame = data.rpsPvpGames.get(a.getUUID());
			helper.assertTrue(kidGame != null && kidGame.opponentIsNpc() && kid.getUUID().equals(kidGame.opponent()),
					"A did not enter the kid game");
			helper.assertFalse(data.rpsPvpGames.has(b.getUUID()), "B stayed linked to A's replaced PvP match");
			RPSGameStatePacket left = bConnection.lastRpsState();
			helper.assertTrue(left != null && "LEAVE".equals(String.valueOf(field(left, "packetType"))),
					"B was not told the PvP match ended");

			// a new invite between A and B resumes the paused match (1.16 keeps it)
			RockPaperScissorsCommand.startGame(data, a, b);
			RockPaperScissorsGame aGame = data.rpsPvpGames.get(a.getUUID());
			RockPaperScissorsGame bGame = data.rpsPvpGames.get(b.getUUID());
			helper.assertTrue(aGame != null && bGame != null && !aGame.opponentIsNpc(), "re-invite did not start a match");
			helper.assertTrue(aGame.round() == 2 && aGame.playerWins() == 1 && bGame.opponentWins() == 1,
					"re-invite restarted the match at round " + aGame.round() + ", A wins " + aGame.playerWins());
		}
		finally {
			players.close(data);
		}
		helper.succeed();
	}

	private static void pick(ServerPlayer player, Pick pick) {
		ClRPSGameInputPacket.handleOnServer(player, ClRPSGameInputPacket.pick(pick));
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

	/** Lists fake players by UUID in the PlayerList so the server-side opponent lookup finds them. */
	private static final class Players {
		private final GameTestHelper helper;
		private final Map<UUID, ServerPlayer> byUuid;
		private final List<FakePlayer> added = new ArrayList<>();

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

		FakePlayer add(String name) {
			FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1 + added.size(), 2, 1)));
			player.moveTo(position.x, position.y, position.z);
			byUuid.put(player.getUUID(), player);
			added.add(player);
			return player;
		}

		void close(ServerSavedData data) {
			for (FakePlayer player : added) {
				byUuid.remove(player.getUUID());
				data.rpsPvpGames.remove(player.getUUID());
			}
		}
	}
}
