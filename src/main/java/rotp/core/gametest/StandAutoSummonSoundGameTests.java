package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.network.s2c.StandEntitySoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 plays a phase standSound from the synced task, so an auto-summoned Stand still sounds (Cream roar,
 * D4C Love Train). The port starts the action before the Stand joins the level: the sound must be held
 * and sent right after StandEntityAbility.onKeyPress finalizes the summon.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAutoSummonSoundGameTests {
	private StandAutoSummonSoundGameTests() {}

	private static final SoundEvent PHASE_SOUND = SoundEvents.PLAYER_LEVELUP;

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void soundBeforeAutoSummonAddIsSentAfterSpawn(GameTestHelper helper) {
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "AutoSummonSound"));
		RecordingConnection connection = new RecordingConnection(user);
		StandPower power = null;
		EntityStandType standType = null;
		try {
			Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(position.x, position.y, position.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add auto-summon owner");
			// the owner's own chunk counts as sent, so it tracks the Stand once it is added
			connection.chunkSender.dropChunk(user, user.chunkPosition());
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type instanceof EntityStandType, "Missing registered Star Platinum");
			standType = (EntityStandType) type;
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");

			// a failed auto-summon drops the held sound with the Stand
			connection.packets.clear();
			helper.assertTrue(standType.summon(user, power, stand -> {}, false), "Auto-summon was refused");
			StandEntity dropped = power.getSummonedStandEntity();
			helper.assertTrue(dropped != null && !dropped.isAddedToLevel(), "Auto-summon added the Stand early");
			StandUtil.playStandEntitySound(dropped, PHASE_SOUND, 1.0F, 1.0F);
			standType.finalizeStandSummonFromAction(user, power, dropped, false);
			helper.assertTrue(dropped.isRemoved() && power.getSummonedStandEntity() == null,
					"A failed auto-summon kept its Stand");
			helper.assertTrue(connection.soundIndexes(dropped).isEmpty(),
					"A failed auto-summon sent the held Stand sound");

			// StandEntityAbility.onKeyPress order: summon unadded, the action's first phase sounds, then add
			connection.packets.clear();
			helper.assertTrue(standType.summon(user, power, stand -> {}, false), "Auto-summon was refused");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && !stand.isAddedToLevel(), "Auto-summon added the Stand early");
			StandUtil.playStandEntitySound(stand, PHASE_SOUND, 1.0F, 1.0F);
			helper.assertTrue(connection.soundIndexes(stand).isEmpty(),
					"The phase sound was sent before the Stand existed on any client");
			standType.finalizeStandSummonFromAction(user, power, stand, true);
			helper.assertTrue(stand.isAddedToLevel(), "Finalizing the auto-summon did not add the Stand");
			int spawn = connection.indexOf(packet -> packet instanceof ClientboundAddEntityPacket add
					&& add.getId() == stand.getId());
			helper.assertTrue(spawn >= 0, "Fixture: the owner does not track the added Stand");
			List<Integer> sounds = connection.soundIndexes(stand);
			helper.assertTrue(sounds.size() == 1 && sounds.get(0) > spawn,
					"The phase sound of an auto-summoned Stand was lost or sent before its spawn: " + sounds);

			// an added Stand sounds at once and nothing replays
			StandUtil.playStandEntitySound(stand, PHASE_SOUND, 1.0F, 1.0F);
			helper.assertTrue(connection.soundIndexes(stand).size() == 2,
					"A Stand already in the level did not send its sound at once");
			StandUtil.flushPendingStandEntitySounds(stand);
			helper.assertTrue(connection.soundIndexes(stand).size() == 2, "A held Stand sound was sent twice");
		}
		finally {
			if (power != null && standType != null && power.getSummonedStandEntity() != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.succeed();
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		int indexOf(Predicate<Packet<?>> predicate) {
			for (int i = 0; i < packets.size(); i++) {
				if (predicate.test(packets.get(i))) return i;
			}
			return -1;
		}

		List<Integer> soundIndexes(StandEntity stand) {
			List<Integer> indexes = new ArrayList<>();
			for (int i = 0; i < packets.size(); i++) {
				if (packets.get(i) instanceof ClientboundCustomPayloadPacket custom
						&& custom.payload() instanceof StandEntitySoundPacket sound
						&& sound.entityId() == stand.getId() && sound.sound().value() == PHASE_SOUND) {
					indexes.add(i);
				}
			}
			return indexes;
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
