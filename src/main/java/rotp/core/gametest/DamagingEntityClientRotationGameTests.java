package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.LightBeamEntity;
import rotp.core.init.ModEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * The client builds a tracked projectile from the add-entity packet and then applies the motion packet the server
 * sends after it (ClientPacketListener.handleAddEntity, handleSetEntityMotion). A projectile that stands still (the
 * Aja stone beam) has zero motion, which carries no direction: the rotation of the spawn packet must stay.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DamagingEntityClientRotationGameTests {
	private DamagingEntityClientRotationGameTests() {}

	@GameTest(template = "empty", batch = "damaging_client_rotation_beam", timeoutTicks = 20)
	public static void standingBeamKeepsSpawnRotationAfterZeroMotionPacket(GameTestHelper helper) {
		for (float[] rotation : new float[][] { { 0.0F, -45.0F }, { 130.0F, -45.0F }, { -70.0F, 30.0F }, { 0.0F, 0.0F } }) {
			float yaw = rotation[0];
			float pitch = rotation[1];
			LightBeamEntity beam = serverBeam(helper, yaw, pitch);
			LightBeamEntity clientBeam = clientBeam(helper, beam);
			helper.assertTrue(angleError(clientBeam.getYRot(), yaw) < 1.5F && Math.abs(clientBeam.getXRot() - pitch) < 1.5F,
					"The beam spawned at yaw " + yaw + " pitch " + pitch + " arrived with yaw " + clientBeam.getYRot()
							+ " pitch " + clientBeam.getXRot() + " after the zero motion packet");
			Vec3 expected = Vec3.directionFromRotation(pitch, yaw).scale(21.0D);
			Vec3 drawn = clientBeam.getEndPoint().subtract(clientBeam.position());
			helper.assertTrue(drawn.distanceTo(expected) < 0.6D,
					"The drawn beam end " + drawn + " is not the server direction " + expected);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "damaging_client_rotation_motion", timeoutTicks = 20)
	public static void movingProjectileStillFollowsItsMotion(GameTestHelper helper) {
		LightBeamEntity beam = serverBeam(helper, 10.0F, 5.0F);
		beam.setDeltaMovement(0.5D, 0.0D, 0.0D);
		LightBeamEntity clientBeam = clientBeam(helper, beam);
		helper.assertTrue(angleError(clientBeam.getYRot(), -90.0F) < 0.5F && Math.abs(clientBeam.getXRot()) < 0.5F,
				"A projectile moving along +x should turn to yaw -90 pitch 0, was yaw " + clientBeam.getYRot() + " pitch "
						+ clientBeam.getXRot());
		helper.succeed();
	}

	private static LightBeamEntity serverBeam(GameTestHelper helper, float yaw, float pitch) {
		ServerLevel level = helper.getLevel();
		LightBeamEntity beam = ModEntityTypes.AJA_STONE_BEAM.get().create(level);
		helper.assertTrue(beam != null, "Could not create the beam");
		Vec3 at = helper.absoluteVec(new Vec3(0.5D, 3.0D, 0.5D));
		beam.moveTo(at.x, at.y, at.z, yaw, pitch);
		beam.shoot(10.0F, 21.0F);
		return beam;
	}

	// What the client does for a tracked entity: the add packet, then the motion packet ServerEntity sends after it.
	private static LightBeamEntity clientBeam(GameTestHelper helper, LightBeamEntity serverBeam) {
		ServerLevel level = helper.getLevel();
		LightBeamEntity client = ModEntityTypes.AJA_STONE_BEAM.get().create(level);
		ClientboundAddEntityPacket add = new ClientboundAddEntityPacket(serverBeam, 0, BlockPos.containing(serverBeam.position()));
		client.recreateFromPacket(add);
		client.setPos(serverBeam.getX(), serverBeam.getY(), serverBeam.getZ());
		client.shoot(10.0F, 21.0F);
		ClientboundSetEntityMotionPacket motion = new ClientboundSetEntityMotionPacket(serverBeam.getId(), serverBeam.getDeltaMovement());
		client.lerpMotion(motion.getXa(), motion.getYa(), motion.getZa());
		return client;
	}

	private static float angleError(float a, float b) {
		return Math.abs(Mth.wrapDegrees(a - b));
	}
}
