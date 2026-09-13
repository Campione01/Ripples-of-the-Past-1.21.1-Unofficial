package com.github.standobyte.jojo.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.github.standobyte.jojo.api.gravity.DirectionalGravityApi;
import com.github.standobyte.jojo.api.gravity.DirectionalGravityData;
import com.github.standobyte.jojo.api.gravity.DirectionalGravitySource;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DirectionalGravityTransitionGameTests {
	private static final ResourceLocation SOURCE = JojoMod.resLoc("gravity_transition_test");
	private static final ResourceLocation HIGH_SOURCE = JojoMod.resLoc("gravity_transition_high_test");
	private static final Direction[] HORIZONTAL = {
			Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
	private static final Vec3 MOMENTUM = new Vec3(0.03, -0.0784, 0.12);

	private DirectionalGravityTransitionGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void groundAndCeilingTransitionsKeepCenterAndRestoreFeet(GameTestHelper helper) {
		for (Direction direction : Direction.values()) {
			try (Fixture f = new Fixture(helper)) {
				f.horizontalPlane(4, Blocks.STONE.defaultBlockState());
				Vec3 anchor = f.user.position();
				Vec3 center = f.user.getBoundingBox().getCenter();
				MutableSource source = new MutableSource(direction);
				DirectionalGravityApi.bind(f.user, SOURCE, source);
				f.assertApplied(direction);
				f.assertClear();
				assertVector(helper, f.user.getBoundingBox().getCenter(), center,
						"Unobstructed transition moved the physical center: " + direction);
				assertVector(helper, f.user.getDeltaMovement(), MOMENTUM, "Transition changed world momentum");
				DirectionalGravityApi.unbind(f.user, SOURCE, source);
				f.assertApplied(Direction.DOWN);
				f.assertClear();
				// SOUTH -> DOWN changes the feet by -0.9, but the old body center moves by zero.
				assertVector(helper, f.user.position(), anchor,
						"Return to DOWN confused anchor displacement with center movement: " + direction);
				assertVector(helper, f.user.getDeltaMovement(), MOMENTUM, "Return changed world momentum");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void airborneDirectionChangesPreserveWorldMomentum(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.user.setPos(f.user.position().add(0.0, 8.0, 0.0));
			Vec3 anchor = f.user.position();
			Vec3 center = f.user.getBoundingBox().getCenter();
			MutableSource source = new MutableSource(Direction.DOWN);
			DirectionalGravityApi.bind(f.user, SOURCE, source);
			for (Direction direction : Direction.values()) {
				source.direction = direction;
				DirectionalGravityApi.directionChanged(f.user, SOURCE, source);
				f.assertApplied(direction);
				f.assertClear();
				assertVector(helper, f.user.getBoundingBox().getCenter(), center,
						"Airborne direction change moved the physical center");
				assertVector(helper, f.user.getDeltaMovement(), MOMENTUM, "Airborne transition changed momentum");
			}
			DirectionalGravityApi.unbind(f.user, SOURCE, source);
			assertVector(helper, f.user.position(), anchor, "Airborne unbind did not restore the original anchor");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void horizontalGravityMovementCannotEnterFloor(GameTestHelper helper) {
		for (Direction direction : HORIZONTAL) {
			try (Fixture f = new Fixture(helper)) {
				MutableSource source = new MutableSource(direction);
				DirectionalGravityApi.bind(f.user, SOURCE, source);
				f.assertApplied(direction);
				f.user.setDeltaMovement(new Vec3(0.0, -0.1, 0.0));
				f.user.move(MoverType.SELF, new Vec3(0.0, -2.0, 0.0));
				f.assertClear();
				helper.assertTrue(Math.abs(f.user.getBoundingBox().minY - f.floorY()) < 1.0E-6,
						"Horizontal-gravity movement crossed the floor");
				Vec3 velocity = f.user.getDeltaMovement();
				DirectionalGravityApi.unbind(f.user, SOURCE, source);
				f.assertApplied(Direction.DOWN);
				f.assertClear();
				assertVector(helper, f.user.getDeltaMovement(), velocity,
						"Return after floor contact changed collision-resolved momentum");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void allHorizontalAxesFindClearPositionsAwayFromWalls(GameTestHelper helper) {
		for (Direction direction : HORIZONTAL) {
			try (Fixture f = new Fixture(helper)) {
				f.wall(direction.getAxis(), 3, Blocks.STONE.defaultBlockState());
				AABB oldBox = f.user.getBoundingBox();
				MutableSource source = new MutableSource(direction);
				DirectionalGravityApi.bind(f.user, SOURCE, source);
				f.assertApplied(direction);
				f.assertClear();
				Vec3 centerMovement = f.user.getBoundingBox().getCenter().subtract(oldBox.getCenter());
				double away = direction.getAxis() == Direction.Axis.X ? centerMovement.x : centerMovement.z;
				helper.assertTrue(away < -0.1, "Wall clearance did not move away from the wall");
				helper.assertTrue(helper.getLevel().noCollision(f.user,
						oldBox.expandTowards(centerMovement).deflate(1.0E-7)),
						"Transition swept the old body through a wall");
				assertVector(helper, f.user.getDeltaMovement(), MOMENTUM, "Wall clearance changed momentum");
				DirectionalGravityApi.unbind(f.user, SOURCE, source);
				f.assertApplied(Direction.DOWN);
				f.assertClear();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void lowCeilingDefersUnbindUntilStandingSpaceReturns(GameTestHelper helper) {
		for (Direction direction : HORIZONTAL) {
			try (Fixture f = new Fixture(helper)) {
				MutableSource source = new MutableSource(direction);
				DirectionalGravityApi.bind(f.user, SOURCE, source);
				f.assertApplied(direction);
				f.user.setPos(f.user.getX(), f.floorY() + f.user.getBbWidth() / 2.0, f.user.getZ());
				f.horizontalPlane(3, Blocks.STONE.defaultBlockState());
				f.assertClear();
				Vec3 position = f.user.position();
				SyncedFrame before = f.frame(false);
				DirectionalGravityApi.unbind(f.user, SOURCE, source);
				helper.assertTrue(DirectionalGravityApi.getDirection(f.user) == Direction.DOWN,
						"Unbind did not remove its provider request");
				f.assertApplied(direction);
				assertVector(helper, f.user.position(), position, "Blocked unbind moved the player");
				helper.assertTrue(f.frame(false).revision == before.revision, "Blocked unbind published a new frame");
				f.horizontalPlane(3, Blocks.AIR.defaultBlockState());
				DirectionalGravityApi.reconcileEffectiveDirection(f.user);
				f.assertApplied(Direction.DOWN);
				f.assertClear();
				helper.assertTrue(Math.abs(f.user.getY() - f.floorY()) < 1.0E-6,
						"Unbind retry did not place the upright player on the floor");
				assertVector(helper, f.user.getDeltaMovement(), MOMENTUM, "Unbind retry changed momentum");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void blockedProviderChangesRetainPriorityAndRetryLatestRequest(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
				f.wall(axis, 1, Blocks.STONE.defaultBlockState());
				f.wall(axis, 3, Blocks.STONE.defaultBlockState());
			}
			f.horizontalPlane(4, Blocks.STONE.defaultBlockState());
			MutableSource low = new MutableSource(Direction.SOUTH);
			MutableSource high = new MutableSource(Direction.EAST);
			Vec3 position = f.user.position();
			DirectionalGravityApi.bind(f.user, SOURCE, 5, low);
			DirectionalGravityApi.bind(f.user, HIGH_SOURCE, 10, high);
			f.assertApplied(Direction.DOWN);
			helper.assertTrue(DirectionalGravityApi.getDirection(f.user) == Direction.EAST,
					"Deferral changed provider priority or removed the winning request");
			high.direction = Direction.WEST;
			DirectionalGravityApi.directionChanged(f.user, HIGH_SOURCE, high);
			f.assertApplied(Direction.DOWN);
			assertVector(helper, f.user.position(), position, "No-space retry moved the player");
			f.wall(Direction.Axis.X, 3, Blocks.AIR.defaultBlockState());
			DirectionalGravityApi.reconcileEffectiveDirection(f.user);
			f.assertApplied(Direction.WEST);
			f.assertClear();
			DirectionalGravityApi.unbind(f.user, HIGH_SOURCE, high);
			helper.assertTrue(DirectionalGravityApi.getDirection(f.user) == Direction.SOUTH,
					"Removing the winner lost the remaining lower-priority provider");
			DirectionalGravityApi.unbind(f.user, SOURCE, low);
			f.assertApplied(Direction.DOWN);
			f.assertClear();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void lateTrackingUsesCurrentPositionAndMomentumWithoutNewRevision(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			MutableSource source = new MutableSource(Direction.SOUTH);
			DirectionalGravityApi.bind(f.user, SOURCE, source);
			SyncedFrame transition = f.frame(false);
			Vec3 moved = f.user.position().add(0.125, 0.75, 0.25);
			Vec3 momentum = new Vec3(0.2, 0.3, -0.4);
			f.user.setPos(moved);
			f.user.setDeltaMovement(momentum);
			SyncedFrame initialTracking = f.frame(true);
			SyncedFrame duplicateSync = f.frame(false);
			helper.assertTrue(initialTracking.revision == transition.revision
					&& duplicateSync.revision == transition.revision,
					"Ordinary movement invented a gravity transition revision");
			assertVector(helper, initialTracking.position, moved, "Late tracking serialized a stale transition anchor");
			assertVector(helper, duplicateSync.position, moved, "Full sync serialized a stale transition anchor");
			assertVector(helper, initialTracking.velocity, momentum, "Late tracking serialized stale momentum");
			assertVector(helper, f.user.position(), moved, "Serializing a frame moved its owner");
		}
		helper.succeed();
	}

	private static void assertVector(GameTestHelper helper, Vec3 actual, Vec3 expected, String message) {
		helper.assertTrue(actual.distanceToSqr(expected) < 1.0E-12,
				message + ": expected=" + expected + ", actual=" + actual);
	}

	private static final class MutableSource implements DirectionalGravitySource {
		private Direction direction;
		private MutableSource(Direction direction) { this.direction = direction; }
		@Override
		public Direction gravityDirection(Entity entity) { return direction; }
	}

	private record SyncedFrame(long revision, Direction direction, Vec3 position, Vec3 velocity) {}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "GravityFrame"));
			horizontalPlane(1, Blocks.STONE.defaultBlockState());
			BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
			user.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
			user.getAbilities().instabuild = false;
			user.getAbilities().flying = false;
			user.noPhysics = false;
			user.setNoGravity(true);
			user.setOnGround(true);
			user.setDeltaMovement(MOMENTUM);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add gravity-transition player");
			assertClear();
		}

		private double floorY() { return helper.absolutePos(new BlockPos(2, 2, 2)).getY(); }

		private void horizontalPlane(int y, BlockState state) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) block(new BlockPos(x, y, z), state);
			}
		}

		private void wall(Direction.Axis axis, int coordinate, BlockState state) {
			for (int y = 2; y <= 4; y++) {
				for (int side = 0; side < 5; side++) {
					block(axis == Direction.Axis.X ? new BlockPos(coordinate, y, side)
							: new BlockPos(side, y, coordinate), state);
				}
			}
		}

		private void block(BlockPos relative, BlockState state) {
			BlockPos position = helper.absolutePos(relative);
			originalBlocks.putIfAbsent(position, helper.getLevel().getBlockState(position));
			helper.getLevel().setBlockAndUpdate(position, state);
		}

		private void assertApplied(Direction direction) {
			helper.assertTrue(DirectionalGravityApi.getEffectiveDirection(user) == direction,
					"Expected effective gravity " + direction + ", got " + DirectionalGravityApi.getEffectiveDirection(user));
		}

		private void assertClear() {
			helper.assertTrue(helper.getLevel().noCollision(user, user.getBoundingBox().deflate(1.0E-7)),
					"Gravity transition intersected solid geometry");
		}

		private SyncedFrame frame(boolean initial) {
			RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			try {
				DirectionalGravityData.SYNC_HANDLER.write(buffer,
						user.getData(ModDataAttachmentTypes.DIRECTIONAL_GRAVITY.get()), initial);
				return new SyncedFrame(buffer.readVarLong(), buffer.readEnum(Direction.class),
						new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
						new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
			}
			finally { buffer.release(); }
		}

		@Override
		public void close() {
			user.discard();
			for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
				helper.getLevel().setBlockAndUpdate(entry.getKey(), entry.getValue());
			}
		}
	}
}
