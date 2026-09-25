package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.hamon.abilities.HamonOverdriveBarrageAbility;
import rotp.core.init.ModParticles;
import rotp.core.network.s2c.TrHamonParticlesPacket;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonOverdriveBarrage hit: DamageUtil.dealHamonDamage(target, 0.1F, user, null, null), the hit's
 * default spark emitter only, no separate spark burst on top.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonOverdriveBarrageSparkGameTests {
	private HamonOverdriveBarrageSparkGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void overdriveBarrageHitSendsNoExtraSparkBurst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LivingEntity target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
		LivingEntity user = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 1);
		ParticleWatcher watcher = new ParticleWatcher(level);
		watcher.setPos(target.getX(), target.getY(), target.getZ());
		// only ServerLevel.players receive sendParticles bursts; removed again below
		level.players().add(watcher);
		try {
			HamonAbilityHelpers.takeLastSparkEmitter();
			HamonOverdriveBarrageAbility.hitEntity(level, user, target);
			TrHamonParticlesPacket sent = HamonAbilityHelpers.takeLastSparkEmitter();
			helper.assertTrue(sent != null && sent.entityId() == target.getId(),
					"Overdrive Barrage hit must land and send its Hamon spark emitter: " + sent);
			helper.assertTrue(watcher.sparkBursts() == 0,
					"Overdrive Barrage hit sent " + watcher.sparkBursts() + " extra spark burst(s), 1.16 had none");
			// control: the watcher does see a server spark burst
			HamonAbilityHelpers.sendHamonParticles(target, ModParticles.HAMON_SPARK.get(), 1);
			helper.assertTrue(watcher.sparkBursts() == 1, "particle watcher missed a control spark burst");
		}
		finally {
			level.players().remove(watcher);
		}
		helper.succeed();
	}

	private static final class ParticleWatcher extends FakePlayer {
		final List<ClientboundLevelParticlesPacket> particles = new ArrayList<>();

		ParticleWatcher(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "hamon-spark-watcher"));
			this.connection = new RecordingConnection(level, this);
		}

		int sparkBursts() {
			Set<ParticleType<?>> sparks = Set.of(ModParticles.HAMON_SPARK.get(), ModParticles.HAMON_SPARK_BLUE.get(),
					ModParticles.HAMON_SPARK_YELLOW.get(), ModParticles.HAMON_SPARK_RED.get(),
					ModParticles.HAMON_SPARK_SILVER.get());
			return (int) particles.stream().filter(packet -> sparks.contains(packet.getParticle().getType())).count();
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		private final List<ClientboundLevelParticlesPacket> sink;

		RecordingConnection(ServerLevel level, ParticleWatcher player) {
			super(level.getServer(), openConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
			this.sink = player.particles;
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (packet instanceof ClientboundLevelParticlesPacket particles && sink != null) {
				sink.add(particles);
			}
		}
	}
}
