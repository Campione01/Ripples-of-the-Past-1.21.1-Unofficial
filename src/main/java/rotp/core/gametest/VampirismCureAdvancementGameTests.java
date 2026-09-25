package rotp.core.gametest;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.core.JojoMod;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.vampirism.VampirismData;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 VampirismData.finishCuringOnWakingUp fired jojo:cure_vampirism, the only criterion of the
 * "On Second Thought..." task advancement; abandoning Hamon never granted it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampirismCureAdvancementGameTests {
	private static final String CURE = "jojo/cure_vampirism";

	private VampirismCureAdvancementGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void finishedCureGrantsCureAdvancement(GameTestHelper helper) {
		AdvancementHolder cure = advancement(helper, CURE);
		helper.assertTrue(cure.value().display().map(display -> display.getType() == AdvancementType.TASK).orElse(false),
				"cure_vampirism is not a task-frame advancement (1.16 frame \"task\")");
		ServerPlayer player = new CureTestPlayer(helper);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
			power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			VampirismData data = PlayerPower.getPowerData(player, ModPlayerPowers.VAMPIRISM).orElseThrow();
			helper.assertTrue(!done(player, cure), "becoming a vampire granted the cure vampirism advancement");
			data.setCuringTicks(player, Integer.MAX_VALUE);
			helper.assertTrue(VampirismData.finishCuringOnWakingUp(player), "the complete cure did not finish");
			helper.assertTrue(power.getPowerType() == null, "the finished cure kept vampirism");
			helper.assertTrue(done(player, cure),
					"curing vampirism did not grant jojo_ripples:jojo/cure_vampirism (1.16 VAMPIRISM_CURED trigger)");
			helper.succeed();
		}
		finally {
			player.getAdvancements().stopListening();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void abandoningHamonDoesNotGrantCureAdvancement(GameTestHelper helper) {
		AdvancementHolder cure = advancement(helper, CURE);
		AdvancementHolder abandon = advancement(helper, "jojo/adandon_hamon");
		ServerPlayer player = new CureTestPlayer(helper);
		try {
			ModCriteriaTriggers.triggerAbandonHamon(player);
			helper.assertTrue(done(player, abandon), "the abandon_hamon trigger no longer grants adandon_hamon");
			helper.assertTrue(!done(player, cure), "abandoning Hamon granted the cure vampirism advancement");
			helper.succeed();
		}
		finally {
			player.getAdvancements().stopListening();
		}
	}

	private static AdvancementHolder advancement(GameTestHelper helper, String name) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(JojoMod.resLoc(name));
		helper.assertTrue(holder != null, "missing advancement " + name);
		return holder;
	}

	private static boolean done(ServerPlayer player, AdvancementHolder holder) {
		return player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** Real (non-fake) server player, so advancements are granted; packets are dropped. */
	private static final class CureTestPlayer extends ServerPlayer {
		CureTestPlayer(GameTestHelper helper) {
			super(helper.getLevel().getServer(), helper.getLevel(),
					new GameProfile(UUID.randomUUID(), "CureAdvancement"), ClientInformation.createDefault());
			this.connection = new SilentConnection(helper.getLevel(), this);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			moveTo(origin.x, origin.y, origin.z);
		}
	}

	private static final class SilentConnection extends ServerGamePacketListenerImpl {
		SilentConnection(ServerLevel level, ServerPlayer player) {
			super(level.getServer(), openConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
		}
	}
}
