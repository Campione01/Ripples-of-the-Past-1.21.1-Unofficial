package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandPowerTransitions.TransitionContext;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorReadout;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeDetectorReadout.ReadoutKind;
import rotp.core.network.s2c.TrPowerStandInstancePacket;
import rotp.core.network.s2c.TrStandAwakeningStagePacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandAwakening.AwakeningStage;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The port's StandPower.usesResolve also needs the user's awakening stage to be FULL_CONTROL (1.16 had no stage), and a
 * client reads it for the users it tracks too (the Life Detector's Resolve line, the Resolve level and value it ticks), so
 * a tracking player is told the stage when it starts tracking a user and whenever the stage changes.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAwakeningTrackingGameTests {
	private StandAwakeningTrackingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aTrackingClientIsToldTheAwakeningStageThatGatesItsUsersResolve(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		StageWatcher watcher = new StageWatcher(level);
		Zombie user = null;
		Zombie clientCopy = null;
		ChunkMap.TrackedEntity tracked = null;
		try {
			helper.assertTrue(type != null, "setup: Magician's Red is not registered");
			user = zombie(helper);
			helper.assertTrue(level.addFreshEntity(user), "setup: could not add the zombie");
			// what the tracking client holds of the zombie, as the stage packets it is sent leave it
			clientCopy = zombie(helper);
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give the zombie Magician's Red");
			StandPower clientPower = PowerClass.STAND.attachGet(clientCopy);
			clientPower.setStand(type);
			helper.assertTrue(power.usesResolve() && clientPower.usesResolve() && hasResolveLine(user),
					"setup: a zombie's Magician's Red in full control does not use Resolve");

			power.userStandAwakeningState.setStage(user, AwakeningStage.PARTIALLY_AWAKENED);
			new ServerEntity(level, user, 1, true, packet -> {}).addPairing(watcher);
			helper.assertTrue(watcher.standInstances > 0, "setup: starting to track the zombie sent it no Stand sync");
			agree(helper, watcher, user, power, clientCopy, AwakeningStage.PARTIALLY_AWAKENED,
					"a player starts tracking a zombie whose Stand is partially awakened");

			tracked = level.getChunkSource().chunkMap.entityMap.get(user.getId());
			helper.assertTrue(tracked != null, "setup: the zombie is not tracked");
			tracked.seenBy.add(watcher.connection);
			power.userStandAwakeningState.setStage(user, AwakeningStage.AWAKENING_PASSIVE);
			agree(helper, watcher, user, power, clientCopy, AwakeningStage.AWAKENING_PASSIVE,
					"the tracked zombie's Stand goes dormant");
			power.userStandAwakeningState.setStage(user, AwakeningStage.FULL_CONTROL);
			agree(helper, watcher, user, power, clientCopy, AwakeningStage.FULL_CONTROL,
					"the tracked zombie takes full control of its Stand");

			power.userStandAwakeningState.setStage(user, AwakeningStage.PARTIALLY_AWAKENED);
			agree(helper, watcher, user, power, clientCopy, AwakeningStage.PARTIALLY_AWAKENED,
					"the tracked zombie's Stand is partially awakened again");
			helper.assertTrue(StandPowerTransitions.fullReset(power,
					new TransitionContext(JojoMod.resLoc("gametest_awakening_tracking"), null)).applied(),
					"setup: the zombie's Stand was not fully cleared");
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give the zombie Magician's Red again");
			agree(helper, watcher, user, power, clientCopy, AwakeningStage.FULL_CONTROL,
					"the tracked zombie's Stand is fully cleared, which resets its stage, and given again");
		}
		finally {
			if (tracked != null) {
				tracked.seenBy.remove(watcher.connection);
			}
			if (user != null) {
				user.discard();
			}
			if (clientCopy != null) {
				clientCopy.discard();
			}
			watcher.discard();
			watcher.channel.finishAndReleaseAll();
		}
		helper.succeed();
	}

	// the stage packets sent since the last row, applied to the client's copy, which must then read the server's Resolve
	private static void agree(GameTestHelper helper, StageWatcher watcher, LivingEntity user, StandPower power,
			LivingEntity clientCopy, AwakeningStage stage, String when) {
		List<TrStandAwakeningStagePacket> sent = watcher.stagesFor(user);
		watcher.stages.clear();
		helper.assertTrue(power.userStandAwakeningState.stage == stage,
				"setup: " + when + ", the server holds the stage " + power.userStandAwakeningState.stage);
		helper.assertTrue(!sent.isEmpty() && sent.get(sent.size() - 1).stage() == stage, when
				+ ": the tracking player must be told the stage " + stage + " that gates the zombie's StandPower.usesResolve;"
				+ " it was sent " + sent.stream().map(TrStandAwakeningStagePacket::stage).toList());
		sent.forEach(packet -> packet.applyTo(clientCopy));
		StandPower clientPower = PowerClass.STAND.attachGet(clientCopy);
		boolean serverLine = hasResolveLine(user);
		boolean clientLine = hasResolveLine(clientCopy);
		helper.assertTrue(clientPower.usesResolve() == power.usesResolve() && clientLine == serverLine, when
				+ ": the tracking client must read the zombie's Resolve as the server does; usesResolve on the server "
				+ power.usesResolve() + ", on the client " + clientPower.usesResolve() + ", the Life Detector's Resolve line"
				+ " on the server " + serverLine + ", on the client " + clientLine);
	}

	private static boolean hasResolveLine(LivingEntity entity) {
		return GoldExperienceLifeDetectorReadout.readout(entity).stream().anyMatch(line -> line.kind() == ReadoutKind.RESOLVE);
	}

	private static Zombie zombie(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create a zombie");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.moveTo(pos.x, pos.y, pos.z, 0, 0);
		return zombie;
	}

	// a client tracking the zombie: the Stand syncs and awakening stages the server sends it
	private static final class StageWatcher extends FakePlayer {
		final List<TrStandAwakeningStagePacket> stages = new ArrayList<>();
		final EmbeddedChannel channel;
		int standInstances;

		StageWatcher(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "awakening-watcher"));
			Connection network = new Connection(PacketFlow.SERVERBOUND);
			channel = new EmbeddedChannel(network);
			connection = new RecordingConnection(level, this, network);
		}

		List<TrStandAwakeningStagePacket> stagesFor(Entity user) {
			return stages.stream().filter(packet -> packet.userId() == user.getId()).toList();
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		private final StageWatcher watcher;

		RecordingConnection(ServerLevel level, StageWatcher watcher, Connection network) {
			super(level.getServer(), network, watcher, CommonListenerCookie.createInitial(watcher.getGameProfile(), false));
			this.watcher = watcher;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (watcher == null) return;
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(sub -> send(sub, null));
			}
			else if (packet instanceof ClientboundCustomPayloadPacket custom) {
				if (custom.payload() instanceof TrStandAwakeningStagePacket stage) {
					watcher.stages.add(stage);
				}
				else if (custom.payload() instanceof TrPowerStandInstancePacket) {
					watcher.standInstances++;
				}
			}
		}
	}
}
