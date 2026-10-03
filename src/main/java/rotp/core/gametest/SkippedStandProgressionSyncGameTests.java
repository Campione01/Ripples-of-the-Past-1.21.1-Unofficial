package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModStands;
import rotp.core.network.s2c.SkippedStandProgressionPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
// Exercises availability state and actual server sends; client receipt/HUD acceptance is separate.
public final class SkippedStandProgressionSyncGameTests {
	private SkippedStandProgressionSyncGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void syncedSkipRefreshesCachedAbilityAvailability(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Boolean> skip = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Stand settings", "Stand Progression", "skipStandProgression"));
		boolean oldSkip = skip.get();
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Ability ability = null;
		int oldLevel = 0;
		try {
			skip.set(false);
			StandPower power = PowerClass.STAND.attachGet(player);
			power.setStand(ModStands.THE_WORLD.get());
			power.setResolveLevel(4);
			ability = power.getAbility("punch");
			helper.assertTrue(ability != null, "Missing cache-test ability");
			oldLevel = ability.getResolveLevelToUnlock();
			for (int requiredLevel : new int[] {5, 6}) {
				ability.resolveLevelToUnlock(requiredLevel);
				power.applySyncedProgressionSkipped(false);
				AvailableAbilities cached = power.updateAvailableMoves();
				helper.assertTrue(!cached._inMoveset.containsKey("punch"), "Unskipped Survival must stay locked");
				power.applySyncedProgressionSkipped(true);
				helper.assertTrue(cached == power.updateAvailableMoves() && cached._inMoveset.containsKey("punch"),
						"Applying owner state must refresh the already-cached HUD/input availability object");
				helper.assertTrue(power.getResolveLevel() == 4, "Sync must not change the normal Resolve cap");
				power.getCurTypeData()._lockedAbilities.add("punch");
				power.applySyncedProgressionSkipped(true);
				helper.assertTrue(!cached._inMoveset.containsKey("punch"), "Resolve bypass must not bypass ordinary skill locks");
				power.getCurTypeData()._lockedAbilities.remove("punch");
				power.applySyncedProgressionSkipped(false);
				helper.assertTrue(!cached._inMoveset.containsKey("punch"), "Owner clear must invalidate availability in the same tick");
			}
			power.applySyncedProgressionSkipped(true);
			power.setStand(null);
			helper.assertTrue(!power.wasProgressionSkipped() && power.updateAvailableMoves()._inMoveset.isEmpty(),
					"Removing the Stand must clear the bypass and cached entries");
		}
		finally {
			if (ability != null) ability.resolveLevelToUnlock(oldLevel);
			skip.set(oldSkip);
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void skippedFlagFollowsReloadAndDeathRetention(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Boolean> keep = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Keep Powers After Death", "keepStandOnDeath"));
		boolean oldKeep = keep.get();
		Player original = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player reloaded = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player retained = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player lost = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = PowerClass.STAND.attachGet(original);
			power.setStand(ModStands.THE_WORLD.get());
			power.skipProgression();
			StandPower restored = PowerClass.STAND.attachGet(reloaded);
			restored.deserializeNBT(helper.getLevel().registryAccess(), power.serializeNBT(helper.getLevel().registryAccess()));
			helper.assertTrue(restored.wasProgressionSkipped(), "Reload must retain the owner-sync snapshot value");
			keep.set(true);
			power.onPlayerClone(retained, true);
			helper.assertTrue(PowerClass.STAND.attachGet(retained).wasProgressionSkipped(), "Kept death clone must retain the bypass");
			keep.set(false);
			power.onPlayerClone(lost, true);
			StandPower lostPower = PowerClass.STAND.attachGet(lost);
			helper.assertTrue(!lostPower.hasPower() && !lostPower.wasProgressionSkipped(), "Lost death clone must clear the stored flag");
		}
		finally {
			keep.set(oldKeep);
			original.discard();
			reloaded.discard();
			retained.discard();
			lost.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void serverSendsProgressionOnSnapshotsAndStandChanges(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Boolean> skip = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Stand settings", "Stand Progression", "skipStandProgression"));
		boolean oldSkip = skip.get();
		CapturingPlayer player = new CapturingPlayer(helper.getLevel());
		StandPower power = PowerClass.STAND.attachGet(player);
		try {
			skip.set(false);
			player.setGameMode(GameType.SURVIVAL);
			power.setStand(ModStands.THE_WORLD.get());
			player.progressionPackets.clear();
			power.syncToPlayer(player);
			assertSent(helper, player, false, "unskipped owner snapshot");

			power.skipProgression();
			assertSent(helper, player, true, "skipProgression");
			power.syncToPlayer(player);
			assertSent(helper, player, true, "skipped owner snapshot");

			power.setStand(ModStands.STAR_PLATINUM.get());
			assertSent(helper, player, false, "Stand replacement");
			power.skipProgression();
			assertSent(helper, player, true, "skip after replacement");
			power.setStand(null);
			assertSent(helper, player, false, "Stand removal");
		}
		finally {
			power.setStand(null);
			skip.set(oldSkip);
			player.getAdvancements().stopListening();
			player.discard();
			player.channel.finishAndReleaseAll();
		}
		helper.succeed();
	}

	private static void assertSent(GameTestHelper helper, CapturingPlayer player, boolean expected, String route) {
		helper.assertTrue(player.progressionPackets.equals(List.of(expected)),
				route + " must send exactly one skipped=" + expected + " payload, got " + player.progressionPackets);
		player.progressionPackets.clear();
	}

	private static final class CapturingPlayer extends FakePlayer {
		final List<Boolean> progressionPackets = new ArrayList<>();
		final EmbeddedChannel channel;

		CapturingPlayer(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "progression-send-test"));
			Connection network = new Connection(PacketFlow.SERVERBOUND);
			channel = new EmbeddedChannel(network);
			connection = new RecordingConnection(level, this, network);
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		private final List<Boolean> sink;

		RecordingConnection(ServerLevel level, CapturingPlayer player, Connection network) {
			super(level.getServer(), network, player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
			sink = player.progressionPackets;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (sink != null && packet instanceof ClientboundCustomPayloadPacket custom
					&& custom.payload() instanceof SkippedStandProgressionPacket state) {
				sink.add(state.skipped());
			}
		}
	}
}
