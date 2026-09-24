package rotp.core.gametest;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TimeStopInstant turned the user toward an entity target before an absolute-rotation teleport.
 * ServerPlayer.teleportTo(x, y, z) sends the rotation as relative (unchanged), so a yaw set only on the
 * server passes a server-side check while the client keeps facing away. This checks what the client is sent.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopBlinkFacingGameTests {
	private static final float EPSILON = 0.01F;

	private TimeStopBlinkFacingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void timeStopBlinkTurnsPlayerOnClient(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 2));
		Zombie mobUser = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
		try {
			BlinkTestPlayer player = new BlinkTestPlayer(level);
			RecordingConnection sent = new RecordingConnection(level, player);
			Vec3 start = helper.absoluteVec(new Vec3(1.5D, 1.0D, 0.5D));
			// the client looks north, away from the target
			float clientYaw = 180.0F;
			player.moveTo(start.x, start.y, start.z, clientYaw, 10.0F);

			// behind the target (The World): north of it, so facing it is yaw 0 (south)
			Vec3 blinkPos = target.position().add(0.0D, 0.0D, -1.3D);
			Float yaw = TimeStopBlinkAbility.getFacingYaw(new ActionTarget(target), blinkPos);
			helper.assertTrue(yaw != null && close(yaw, 0.0F),
					"blink yaw toward a target to the south must be 0, got " + yaw);
			TimeStopBlinkAbility.teleportFacing(player, blinkPos, yaw);
			helper.assertTrue(sent.teleports == 1 && !sent.relative.contains(RelativeMovement.Y_ROT),
					"the player's blink teleport must send an absolute yaw, got " + sent.relative);
			float clientAfter = sent.clientYaw(clientYaw);
			helper.assertTrue(close(clientAfter, yaw),
					"the client would face " + clientAfter + " after the blink instead of the target (" + yaw + ")");
			helper.assertTrue(sent.relative.contains(RelativeMovement.X_ROT) && close(sent.sentXRot, 0.0F),
					"the blink must leave the client's pitch alone");
			helper.assertTrue(player.position().distanceToSqr(blinkPos) < 1.0E-6D
					&& close(player.getYRot(), yaw) && close(player.getYHeadRot(), yaw),
					"the server copy of the player did not land turned toward the target");

			// no entity target: the client keeps its own yaw
			Vec3 missPos = blinkPos.add(2.0D, 0.0D, 0.0D);
			TimeStopBlinkAbility.teleportFacing(player, missPos,
					TimeStopBlinkAbility.getFacingYaw(ActionTarget.EMPTY, missPos));
			helper.assertTrue(sent.teleports == 2 && sent.relative.contains(RelativeMovement.Y_ROT)
					&& close(sent.sentYRot, 0.0F) && player.position().distanceToSqr(missPos) < 1.0E-6D,
					"a blink with no entity target must keep the client's yaw");

			// a non-player Stand user: east of the target, so it turns west (90), head and body too
			Vec3 mobBlinkPos = target.position().add(1.3D, 0.0D, 0.0D);
			Float mobYaw = TimeStopBlinkAbility.getFacingYaw(new ActionTarget(target), mobBlinkPos);
			mobUser.setYRot(-90.0F);
			mobUser.setYHeadRot(-90.0F);
			mobUser.setYBodyRot(-90.0F);
			TimeStopBlinkAbility.teleportFacing(mobUser, mobBlinkPos, mobYaw);
			helper.assertTrue(mobYaw != null && close(mobYaw, 90.0F),
					"blink yaw toward a target to the west must be 90, got " + mobYaw);
			helper.assertTrue(mobUser.position().distanceToSqr(mobBlinkPos) < 1.0E-6D
					&& close(mobUser.getYRot(), mobYaw) && close(mobUser.yRotO, mobYaw)
					&& close(mobUser.getYHeadRot(), mobYaw) && close(mobUser.yBodyRot, mobYaw),
					"a non-player blink user did not land turned toward the target");
		}
		finally {
			target.discard();
			mobUser.discard();
		}
		helper.succeed();
	}

	private static boolean close(float actual, float expected) {
		return Math.abs(Mth.wrapDegrees(actual - expected)) < EPSILON;
	}

	/** Treated as a connected player once built; fake while built, so it registers no advancement listeners. */
	private static final class BlinkTestPlayer extends FakePlayer {
		private boolean connected;

		BlinkTestPlayer(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "BlinkFacing"));
			connected = true;
		}

		@Override
		public boolean isFakePlayer() {
			return !connected;
		}
	}

	/** Records what ServerGamePacketListenerImpl.teleport would put in ClientboundPlayerPositionPacket. */
	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		int teleports;
		Set<RelativeMovement> relative = Set.of();
		float sentYRot;
		float sentXRot;

		RecordingConnection(ServerLevel level, ServerPlayer player) {
			super(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		@Override
		public void teleport(double x, double y, double z, float yRot, float xRot, Set<RelativeMovement> relativeSet) {
			teleports++;
			relative = Set.copyOf(relativeSet);
			sentYRot = relativeSet.contains(RelativeMovement.Y_ROT) ? yRot - player.getYRot() : yRot;
			sentXRot = relativeSet.contains(RelativeMovement.X_ROT) ? xRot - player.getXRot() : xRot;
			player.absMoveTo(x, y, z, yRot, xRot);
		}

		/** The client's yaw after it applies the recorded packet. */
		float clientYaw(float clientYawBefore) {
			return relative.contains(RelativeMovement.Y_ROT) ? clientYawBefore + sentYRot : sentYRot;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
		}
	}
}
