package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModSpecialActions;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 1.16 ClientTickingSoundsHelper.playStandEntityUnsummonSound: only a Stand older than 20 ticks plays it. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandUnsummonSoundAgeGameTests {
	private StandUnsummonSoundAgeGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void youngStandUnsummonsSilently(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			fixture.initialize(helper, 20);
			fixture.tick();
			helper.assertTrue(fixture.soundPackets().isEmpty(),
					"A Stand 20 ticks old sent the unsummon sound");
			// it ages past the gate mid-unsummon; the sound still must not start late
			fixture.stand.tickCount = 100;
			fixture.tickUntilRemoved(helper);
			helper.assertTrue(fixture.soundPackets().isEmpty(),
					"The unsummon sound was sent later in the same unsummon");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void olderStandPlaysUnsummonSoundOnce(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		try {
			fixture.initialize(helper, 21);
			fixture.tick();
			List<StandEntitySoundPacket> sent = fixture.soundPackets();
			helper.assertTrue(sent.size() == 1 && sent.get(0).entityId() == fixture.stand.getId()
					&& sent.get(0).sound().value() == fixture.type.getUnsummonSound().value(),
					"A Stand 21 ticks old did not send its unsummon sound on the first tick: " + sent.size());
			fixture.tickUntilRemoved(helper);
			helper.assertTrue(fixture.soundPackets().size() == 1,
					"The unsummon sound was sent more than once in one unsummon");
		}
		finally {
			fixture.close();
		}
		helper.succeed();
	}

	private static final class Fixture {
		final FakePlayer user;
		final RecordingConnection connection;
		StandType type;
		StandPower power;
		StandEntity stand;

		Fixture(GameTestHelper helper) {
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UnsummonSoundAge"));
			connection = new RecordingConnection(user);
		}

		void initialize(GameTestHelper helper, int standAge) {
			Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(position.x, position.y, position.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add unsummon-sound owner");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant unsummon-sound Stand");
			stand = ModEntityTypes.HUMANOID_STAND.get().create(helper.getLevel());
			helper.assertTrue(stand != null, "Could not create unsummon-sound Stand entity");
			stand.withStandType(type);
			stand.copyPosition(user);
			power.setSummonedStand(stand);
			helper.assertTrue(stand.getUser() == user, "Stand owner did not resolve");
			stand.tickCount = standAge;
			connection.packets.clear();
			stand.onUnsummonUserInput();
			var action = stand.getCurStandAction();
			helper.assertTrue(action != null && action.ability == ModSpecialActions.STAND_UNSUMMON.get(),
					"Unsummon input did not start the production unsummon action");
		}

		void tick() {
			LivingComponentAction.getComponent(stand).tick();
		}

		void tickUntilRemoved(GameTestHelper helper) {
			int duration = stand.getUnsummonDuration();
			for (int i = 0; i <= duration + 1 && !stand.isRemoved(); i++) {
				tick();
			}
			helper.assertTrue(stand.isRemoved(), "Unsummon did not finish");
		}

		List<StandEntitySoundPacket> soundPackets() {
			List<StandEntitySoundPacket> sent = new ArrayList<>();
			for (Packet<?> packet : connection.packets) {
				if (packet instanceof ClientboundCustomPayloadPacket custom
						&& custom.payload() instanceof StandEntitySoundPacket sound) {
					sent.add(sound);
				}
			}
			return sent;
		}

		void close() {
			if (stand != null && !stand.isRemoved()) {
				if (power != null) power.setSummonedStand(null);
				stand.discard();
			}
			user.discard();
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void send(Packet<?> packet) {
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(this::send);
			}
			else {
				packets.add(packet);
			}
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}
	}
}
