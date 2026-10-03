package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.HamonBubbleCutterEntity;
import rotp.core.init.ModGamerules;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonBubbleCutterGlidingGameTests {
    private static final double EPSILON = 1.0E-8D;

    private HamonBubbleCutterGlidingGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void floorGlidePreservesSpeedAndTrajectory(GameTestHelper helper) {
        assertGlideMotion(helper, Direction.UP);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ceilingGlidePreservesSpeedAndTrajectory(GameTestHelper helper) {
        assertGlideMotion(helper, Direction.DOWN);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void floorGlidePreservesZeroHardnessBlock(GameTestHelper helper) {
        assertGlidePreservesBlock(helper, Direction.UP);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ceilingGlidePreservesZeroHardnessBlock(GameTestHelper helper) {
        assertGlidePreservesBlock(helper, Direction.DOWN);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void glidingSideHitKeepsNormalImpact(GameTestHelper helper) {
        assertNormalImpact(helper, Direction.WEST, true);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nonGlidingFloorHitKeepsNormalImpact(GameTestHelper helper) {
        assertNormalImpact(helper, Direction.UP, false);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nonGlidingCeilingHitKeepsNormalImpact(GameTestHelper helper) {
        assertNormalImpact(helper, Direction.DOWN, false);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void verticalFloorGlideRemainsFinite(GameTestHelper helper) {
        assertVerticalGlide(helper, Direction.UP);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void verticalCeilingGlideRemainsFinite(GameTestHelper helper) {
        assertVerticalGlide(helper, Direction.DOWN);
    }

    private static void assertGlideMotion(GameTestHelper helper, Direction face) {
        try (Fixture fixture = new Fixture(helper, Blocks.STONE.defaultBlockState())) {
            Vec3 incoming = obliqueVelocity(face);
            Vec3 start = fixture.launch(face, incoming, true);
            Vec3 expected = new Vec3(incoming.length(), 0.0D, 0.0D);
            helper.assertTrue(fixture.cutter.isAlive(), "gliding cutter was removed on " + face);
            assertVector(helper, fixture.cutter.getDeltaMovement(), expected,
                    "glide lost incoming speed on " + face);
            assertVector(helper, fixture.cutter.position(), start.add(expected),
                    "post-impact tick did not travel horizontally at preserved speed on " + face);
            helper.succeed();
        }
    }

    private static void assertGlidePreservesBlock(GameTestHelper helper, Direction face) {
        try (Fixture fixture = new Fixture(helper, Blocks.SLIME_BLOCK.defaultBlockState())) {
            helper.assertTrue(fixture.blockState.getDestroySpeed(helper.getLevel(), fixture.blockPos) == 0.0F,
                    "setup: expected a zero-hardness collision block");
            fixture.launch(face, obliqueVelocity(face), true);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.blockPos).equals(fixture.blockState),
                    "gliding Y-face impact destroyed the zero-hardness block on " + face);
            helper.assertTrue(fixture.cutter.isAlive(), "gliding cutter was removed on " + face);
            helper.succeed();
        }
    }

    private static void assertNormalImpact(GameTestHelper helper, Direction face, boolean gliding) {
        try (Fixture fixture = new Fixture(helper, Blocks.SLIME_BLOCK.defaultBlockState())) {
            fixture.launch(face, face == Direction.WEST ? new Vec3(1.5D, 0.0D, 0.0D) : obliqueVelocity(face), gliding);
            helper.assertTrue(fixture.cutter.isRemoved(),
                    "ordinary impact did not remove cutter: face=" + face + ", gliding=" + gliding);
            helper.assertTrue(helper.getLevel().getBlockState(fixture.blockPos).isAir(),
                    "ordinary impact did not destroy the allowed zero-hardness block: face=" + face + ", gliding=" + gliding);
            helper.succeed();
        }
    }

    private static void assertVerticalGlide(GameTestHelper helper, Direction face) {
        try (Fixture fixture = new Fixture(helper, Blocks.STONE.defaultBlockState())) {
            Vec3 start = fixture.launch(face, new Vec3(0.0D, face == Direction.UP ? -1.5D : 1.5D, 0.0D), true);
            helper.assertTrue(fixture.cutter.isAlive(), "vertical gliding cutter was removed on " + face);
            assertVector(helper, fixture.cutter.getDeltaMovement(), Vec3.ZERO,
                    "vertical glide must remain finite without inventing a horizontal direction on " + face);
            assertVector(helper, fixture.cutter.position(), start, "vertical glide changed position on " + face);
            fixture.cutter.tick();
            assertVector(helper, fixture.cutter.getDeltaMovement(), Vec3.ZERO, "vertical glide resumed invalid motion");
            assertVector(helper, fixture.cutter.position(), start, "vertical glide drifted on its next tick");
            helper.succeed();
        }
    }

    private static Vec3 obliqueVelocity(Direction face) {
        return new Vec3(0.9D, face == Direction.UP ? -1.2D : 1.2D, 0.0D);
    }

    private static void assertVector(GameTestHelper helper, Vec3 actual, Vec3 expected, String message) {
        helper.assertTrue(Double.isFinite(actual.x) && Double.isFinite(actual.y) && Double.isFinite(actual.z)
                        && actual.distanceToSqr(expected) <= EPSILON * EPSILON,
                message + ": expected=" + expected + ", actual=" + actual);
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final BlockPos blockPos;
        private final BlockState blockState;
        private final BlockState previousBlock;
        private final boolean breakBlocks;
        private final ServerPlayer owner;
        private HamonBubbleCutterEntity cutter;

        private Fixture(GameTestHelper helper, BlockState blockState) {
            this.helper = helper;
            this.blockPos = helper.absolutePos(new BlockPos(2, 2, 2));
            this.blockState = blockState;
            this.previousBlock = helper.getLevel().getBlockState(blockPos);
            this.breakBlocks = helper.getLevel().getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
            this.owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BubbleGlideOwner"));
            owner.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 0))));
            owner.setNoGravity(true);
            owner.setGameMode(GameType.CREATIVE);
        }

        private Vec3 launch(Direction face, Vec3 velocity, boolean gliding) {
            helper.getLevel().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(true, helper.getLevel().getServer());
            helper.getLevel().setBlockAndUpdate(blockPos, blockState);
            helper.assertTrue(JojoModUtil.canEntityDestroy(helper.getLevel(), blockPos, blockState, owner),
                    "setup: the owner cannot destroy the target block");
            helper.assertTrue(helper.getLevel().addFreshEntity(owner), "setup: could not add cutter owner");
            Vec3 relativeStart = switch (face) {
                case UP -> new Vec3(0.2D, 1.4D, 0.5D);
                case DOWN -> new Vec3(0.2D, -0.4D, 0.5D);
                case WEST -> new Vec3(-0.4D, 0.5D, 0.5D);
                default -> throw new IllegalArgumentException("Unsupported test face " + face);
            };
            Vec3 start = Vec3.atLowerCornerOf(blockPos).add(relativeStart);
            cutter = new HamonBubbleCutterEntity(owner, helper.getLevel()).setGliding(gliding);
            cutter.setPos(start);
            cutter.setDeltaMovement(velocity);
            BlockHitResult hit = helper.getLevel().clip(new ClipContext(start, start.add(velocity),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cutter));
            helper.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(blockPos)
                            && hit.getDirection() == face,
                    "setup: trajectory misses intended face: expected=" + face + ", actual=" + hit);
            helper.assertTrue(helper.getLevel().addFreshEntity(cutter), "setup: could not add cutter");
            // Exercise the production ray trace and block-hit dispatch, not an exposed test-only hook.
            cutter.tick();
            return start;
        }

        @Override
        public void close() {
            if (cutter != null) {
                cutter.discard();
            }
            owner.discard();
            helper.getLevel().setBlockAndUpdate(blockPos, previousBlock);
            helper.getLevel().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(breakBlocks, helper.getLevel().getServer());
        }
    }
}
