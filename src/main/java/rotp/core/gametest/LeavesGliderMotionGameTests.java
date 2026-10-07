package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;

// 1.16 LeavesGliderEntity motion: tickLerp/lerpTo (ten steps on every instance that is not locally controlled),
// the pending-lerp snap in addPassenger, the moveGlider flight equations and the updateRotationDelta turn rate.
// A server Player rider is never the local player, so the server glider is the "observer" instance; a non-player
// living rider makes the server the controlling instance.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LeavesGliderMotionGameTests {
	private static final double EPS = 1.0E-6D;
	private static final float ANGLE_EPS = 1.0E-3F;

	private LeavesGliderMotionGameTests() {}

	@GameTest(template = "empty", batch = "leaves_glider_motion", timeoutTicks = 80)
	public static void observerLerpTakesTenStepsAndLocalControlClearsIt(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, 30.0D, 0.0D);
		LeavesGliderEntity glider = fixture.glider(170.0F);
		Player rider = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		rider.moveTo(glider.getX(), glider.getY(), glider.getZ(), 170.0F, 0.0F);
		fixture.add(rider);
		fixture.premise(rider.startRiding(glider) && glider.getControllingPassenger() == rider
				&& !glider.isControlledByLocalInstance(), "a server player rider must leave the server glider not locally controlled");

		Vec3 start = glider.position();
		Vec3 first = start.add(2.0D, 1.0D, -1.0D);
		Vec3 second = start.add(-1.0D, 3.0D, 2.0D);
		glider.lerpTo(first.x, first.y, first.z, -170.0F, 20.0F, 3);
		assertPose(helper, glider, start, 170.0F, 0.0F, "lerpTo moved the glider at once instead of queueing ten steps");
		assertPending(helper, glider, first, -170.0F, 20.0F, "pending target after lerpTo");

		int[] phase = { 0 };
		int[] phaseStartTick = { glider.tickCount };
		Vec3[] from = { start };
		float[] fromAngles = { 170.0F, 0.0F };
		fixture.eachGliderTick(glider, () -> {
			int n = glider.tickCount - phaseStartTick[0];
			if (phase[0] == 0) {
				assertPose(helper, glider, start.lerp(first, n / 10.0D), 170.0F + 2.0F * n, 2.0F * n,
						"step " + n + " of 10 toward the first target");
				if (n == 4) {
					// A position-only update keeps the pending angles (ClientPacketListener reads lerpTargetYRot/XRot)
					from[0] = glider.position();
					fromAngles[0] = glider.getYRot();
					fromAngles[1] = glider.getXRot();
					glider.lerpTo(second.x, second.y, second.z, glider.lerpTargetYRot(), glider.lerpTargetXRot(), 3);
					assertPose(helper, glider, from[0], fromAngles[0], fromAngles[1], "retarget moved the glider at once");
					assertPending(helper, glider, second, -170.0F, 20.0F, "pending target after the position-only update");
					phase[0] = 1;
					phaseStartTick[0] = glider.tickCount;
				}
			}
			else if (phase[0] == 1) {
				int steps = Math.min(n, 10);
				assertPose(helper, glider, from[0].lerp(second, steps / 10.0D),
						fromAngles[0] + (190.0F - fromAngles[0]) * steps / 10.0F,
						fromAngles[1] + (20.0F - fromAngles[1]) * steps / 10.0F,
						"step " + n + " after the retarget (ten steps again, then rest)");
				if (n >= 10) {
					// Settled: the pending getters report the current pose
					assertPending(helper, glider, glider.position(), glider.getYRot(), glider.getXRot(), "settled target getters");
				}
				if (n == 12) {
					Vec3 upward = second.add(0.0D, 5.0D, 0.0D);
					glider.lerpTo(upward.x, upward.y, upward.z, glider.getYRot(), glider.getXRot(), 3);
					assertPending(helper, glider, upward, glider.getYRot(), glider.getXRot(), "pending target before the dismount");
					rider.stopRiding();
					fixture.premise(!glider.isVehicle() && glider.isControlledByLocalInstance(),
							"an empty server glider must be locally controlled");
					from[0] = glider.position();
					phase[0] = 2;
					phaseStartTick[0] = glider.tickCount;
				}
			}
			else {
				// Locally controlled: the pending lerp is dropped and the packet codec follows the glider
				helper.assertTrue(glider.getY() <= second.y + EPS,
						"a locally controlled glider kept following its pending lerp: y=" + glider.getY() + " rest=" + second.y);
				assertPending(helper, glider, glider.position(), glider.getYRot(), glider.getXRot(), "cleared target getters");
				// 1.16 tickLerp syncs the codec before moveGlider, so its base is the pose the tick started from
				Vec3 codecBase = glider.getPositionCodec().decode(0L, 0L, 0L);
				helper.assertTrue(codecBase.distanceTo(from[0]) < EPS,
						"packet position codec base " + codecBase + " does not follow the locally controlled glider from " + from[0]);
				from[0] = glider.position();
				if (n >= 2) {
					fixture.close();
					helper.succeed();
				}
			}
		});
	}

	@GameTest(template = "empty", batch = "leaves_glider_motion", timeoutTicks = 40)
	public static void firstControllingRiderSnapsPendingLerp(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, 40.0D, 0.0D);
		LeavesGliderEntity glider = fixture.glider(0.0F);
		Vec3 start = glider.position();
		Vec3 target = start.add(1.5D, 2.0D, -1.0D);
		glider.lerpTo(target.x, target.y, target.z, 90.0F, 15.0F, 3);
		assertPose(helper, glider, start, 0.0F, 0.0F, "lerpTo moved the glider at once instead of queueing ten steps");

		ArmorStand rider = fixture.stand(glider, 0.0F);
		fixture.premise(rider.startRiding(glider) && glider.getControllingPassenger() == rider
				&& glider.isControlledByLocalInstance(), "a non-player living rider must make the server the controlling instance");
		assertPose(helper, glider, target, 90.0F, 15.0F, "control transfer did not snap the glider to its pending lerp target");
		assertPending(helper, glider, target, 90.0F, 15.0F, "target getters after the snap");
		fixture.eachGliderTick(glider, () -> {
			// Nothing is left to interpolate: the next tick is plain controlled flight from the snapped pose
			helper.assertTrue(glider.position().distanceTo(target) < 0.2D,
					"glider left its snapped pose " + target + " for " + glider.position());
			fixture.close();
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = "leaves_glider_motion", timeoutTicks = 60)
	public static void controlledFlightFollowsDonorEquations(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, 50.0D, -2.0D);
		LeavesGliderEntity glider = fixture.glider(0.0F);
		ArmorStand rider = fixture.stand(glider, 0.0F);
		fixture.premise(rider.startRiding(glider) && glider.getControllingPassenger() == rider
				&& glider.isControlledByLocalInstance(), "a non-player living rider must make the server the controlling instance");
		// 1.16 addPassenger: forward speed max(rider forward speed, 0.05)
		helper.assertTrue(glider.getDeltaMovement().distanceTo(new Vec3(0.0D, 0.0D, 0.05D)) < EPS,
				"first mount speed " + glider.getDeltaMovement() + ", expected 0.05 along the glider yaw");

		int startTick = glider.tickCount;
		Vec3[] previousPos = { glider.position() };
		double[] previousSpeed = { 0.05D };
		fixture.eachGliderTick(glider, () -> {
			int n = glider.tickCount - startTick;
			Vec3 delta = glider.getDeltaMovement();
			Vec3 moved = glider.position().subtract(previousPos[0]);
			int riders = glider.getPassengers().size();
			double expectedSpeed = Math.min(previousSpeed[0] + 0.01D, 0.5D);
			double expectedFall = glider.isNoGravity() ? 0.0D : -0.01D * (1 + riders);
			String at = "tick " + n + " riders=" + riders + " noGravity=" + glider.isNoGravity() + ": ";
			fixture.premise(glider.isFlying() && !glider.onGround() && glider.isControlledByLocalInstance(),
					at + "glider is not in controlled flight");
			helper.assertTrue(Math.abs(delta.z - expectedSpeed) < EPS && Math.abs(delta.x) < EPS,
					at + "horizontal speed " + delta.x + "," + delta.z + ", expected " + expectedSpeed + " (+0.01 per tick, cap 0.5)");
			helper.assertTrue(Math.abs(delta.y - expectedFall) < EPS,
					at + "vertical speed " + delta.y + ", expected constant " + expectedFall);
			helper.assertTrue(moved.distanceTo(delta) < EPS, at + "moved " + moved + ", not its speed " + delta);
			previousPos[0] = glider.position();
			previousSpeed[0] = expectedSpeed;
			if (n == 3) {
				// Jump close to the cap instead of flying 45 ticks out of the test chunk
				glider.setDeltaMovement(0.0D, delta.y, 0.485D);
				previousSpeed[0] = 0.485D;
			}
			else if (n == 6) {
				helper.assertTrue(Math.abs(delta.z - 0.5D) < EPS, at + "speed cap not reached: " + delta.z);
				fixture.premise(fixture.stand(glider, 0.0F).startRiding(glider) && glider.getPassengers().size() == 2,
						"second rider did not mount");
			}
			else if (n == 8) {
				glider.setNoGravity(true);
			}
			else if (n == 10) {
				fixture.close();
				helper.succeed();
			}
		});
	}

	// 1.16 refreshDimensions only extends the box upward from the feet; the 1.21 size-change relocation would
	// drop a glider that has already ticked (one made from a leaves block) by half the rider's height
	@GameTest(template = "empty", batch = "leaves_glider_motion", timeoutTicks = 40)
	public static void boardingATickedGliderKeepsItsFeet(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper, 60.0D, 0.0D);
		LeavesGliderEntity glider = fixture.glider(0.0F);
		fixture.eachGliderTick(glider, () -> {
			Vec3 feet = glider.position();
			float bareHeight = glider.getBbHeight();
			ArmorStand rider = fixture.stand(glider, 0.0F);
			fixture.premise(rider.startRiding(glider) && glider.hasPassenger(rider), "rider did not board the ticked glider");
			helper.assertTrue(Math.abs(glider.getBbHeight() - (bareHeight + rider.getBbHeight())) < 1.0E-4F,
					"boarded glider height " + glider.getBbHeight() + ", expected " + (bareHeight + rider.getBbHeight()));
			helper.assertTrue(glider.position().distanceTo(feet) < EPS && Math.abs(glider.getBoundingBox().minY - feet.y) < EPS,
					"boarding moved the ticked glider's feet from " + feet + " to " + glider.position());
			fixture.close();
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = "leaves_glider_motion", timeoutTicks = 20)
	public static void turnRateFollowsDonorInputRules(GameTestHelper helper) {
		assertTurn(helper, 0.0F, true, false, 1, -3.0F, "left, one rider");
		assertTurn(helper, 0.0F, false, true, 1, 3.0F, "right, one rider");
		assertTurn(helper, -3.0F, true, false, 1, -3.0F, "held left does not accumulate");
		assertTurn(helper, 3.0F, true, false, 1, -3.0F, "left replaces a right turn at once");
		assertTurn(helper, 3.0F, true, true, 1, 0.0F, "both keys stop the turn");
		assertTurn(helper, -3.0F, true, true, 1, 0.0F, "both keys stop the turn");
		assertTurn(helper, 3.0F, false, false, 1, 2.85F, "release damps by 5% of the rate");
		assertTurn(helper, -3.0F, false, false, 1, -2.85F, "release damps by 5% of the rate");
		assertTurn(helper, 0.1F, false, false, 1, 0.0F, "damping stops at zero");
		assertTurn(helper, -0.1F, false, false, 1, 0.0F, "damping stops at zero");
		assertTurn(helper, 0.0F, false, false, 1, 0.0F, "no input, no turn");
		assertTurn(helper, 0.0F, false, true, 2, 2.5F, "right, two riders");
		assertTurn(helper, 0.0F, true, false, 4, -1.5F, "left, four riders");
		assertTurn(helper, 1.5F, false, false, 4, 1.425F, "release, four riders");
		helper.succeed();
	}

	private static void assertTurn(GameTestHelper helper, float current, boolean left, boolean right, int riders,
			float expected, String name) {
		float actual = LeavesGliderEntity.nextRotationDelta(current, left, right, riders);
		helper.assertTrue(Math.abs(actual - expected) < 1.0E-5F,
				"turn rate (" + name + ") from " + current + " is " + actual + ", expected " + expected);
	}

	private static void assertPose(GameTestHelper helper, LeavesGliderEntity glider, Vec3 pos, float yRot, float xRot, String name) {
		helper.assertTrue(glider.position().distanceTo(pos) < EPS
				&& Math.abs(Mth.wrapDegrees(glider.getYRot() - yRot)) < ANGLE_EPS && Math.abs(glider.getXRot() - xRot) < ANGLE_EPS,
				name + ": glider at " + glider.position() + " yaw " + glider.getYRot() + " pitch " + glider.getXRot()
						+ ", expected " + pos + " yaw " + yRot + " pitch " + xRot);
	}

	private static void assertPending(GameTestHelper helper, LeavesGliderEntity glider, Vec3 pos, float yRot, float xRot, String name) {
		Vec3 pending = new Vec3(glider.lerpTargetX(), glider.lerpTargetY(), glider.lerpTargetZ());
		helper.assertTrue(pending.distanceTo(pos) < EPS
				&& Math.abs(Mth.wrapDegrees(glider.lerpTargetYRot() - yRot)) < ANGLE_EPS
				&& Math.abs(glider.lerpTargetXRot() - xRot) < ANGLE_EPS,
				name + ": " + pending + " yaw " + glider.lerpTargetYRot() + " pitch " + glider.lerpTargetXRot()
						+ ", expected " + pos + " yaw " + yRot + " pitch " + xRot);
	}

	// Owned entities in clear air high above the template's own (entity-ticking) chunk
	private static final class Fixture implements GameTestListener {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final Vec3 origin;
		private final List<Entity> owned = new ArrayList<>();
		private boolean closed;

		Fixture(GameTestHelper helper, double height, double zOffset) {
			this.helper = helper;
			this.level = helper.getLevel();
			BlockPos template = helper.absolutePos(BlockPos.ZERO);
			ChunkPos chunk = new ChunkPos(template);
			this.origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + height, chunk.getMinBlockZ() + 8.5D + zOffset);
			helper.testInfo.addListener(this);
		}

		void premise(boolean ok, String message) {
			helper.assertTrue(ok, "GLIDER-MOTION-PREMISE " + message);
		}

		LeavesGliderEntity glider(float yRot) {
			LeavesGliderEntity glider = new LeavesGliderEntity(level);
			glider.moveTo(origin.x, origin.y, origin.z, yRot, 0.0F);
			premise(level.noCollision(glider, glider.getBoundingBox().inflate(4.0D))
					&& level.getEntities(glider, glider.getBoundingBox().inflate(4.0D)).isEmpty(), "flight room is not clear");
			add(glider);
			return glider;
		}

		ArmorStand stand(LeavesGliderEntity glider, float yRot) {
			ArmorStand stand = new ArmorStand(level, glider.getX(), glider.getY(), glider.getZ());
			stand.setYRot(yRot);
			add(stand);
			return stand;
		}

		void add(Entity entity) {
			owned.add(entity);
			premise(level.addFreshEntity(entity), "owned entity did not join the level: " + entity);
		}

		// Runs the check once after every natural glider tick
		void eachGliderTick(LeavesGliderEntity glider, Runnable check) {
			int[] seen = { glider.tickCount };
			Runnable[] poll = new Runnable[1];
			poll[0] = () -> {
				if (closed) {
					return;
				}
				premise(!glider.isRemoved() && level.isPositionEntityTicking(glider.blockPosition())
						&& glider.tickCount - seen[0] <= 1, "glider stopped ticking naturally once per poll");
				if (glider.tickCount != seen[0]) {
					seen[0] = glider.tickCount;
					check.run();
				}
				if (!closed) {
					// The framework keys scheduled tasks by Runnable identity: schedule a fresh one each tick
					helper.runAfterDelay(1, () -> poll[0].run());
				}
			};
			helper.runAfterDelay(1, () -> poll[0].run());
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			for (Entity entity : owned) {
				entity.stopRiding();
			}
			for (Entity entity : owned) {
				if (!entity.isRemoved()) {
					entity.discard();
				}
			}
		}

		@Override public void testStructureLoaded(GameTestInfo test) {}
		@Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
		@Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
		@Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
	}
}
