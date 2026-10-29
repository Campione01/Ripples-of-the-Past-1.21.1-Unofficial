package rotp.addons.achtungbaby.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.achtungbaby.entity.InvisibilityRuneEntity;

/**
 * 1.16 RuneSnowballEntity.tick (59-72) replaced the snowball's tick: count the lifetime, move by the whole velocity
 * with block collision, and only then ray-trace one velocity ahead from the new position. The first three blocks in
 * front of the thrower are therefore crossed without an entity test, and a wall there stops the rune instead of
 * breaking it.
 *
 * <p>The entity cases fire straight up so a rune that misses cannot reach the neighbouring tests.
 */
@GameTestHolder(AchtungBabyPerfectInvisibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class AchtungBabyRuneGameTests {
    private static final String NAMESPACE = AchtungBabyPerfectInvisibilityGameTests.NAMESPACE;
    private static final Vec3 USER_AT = new Vec3(0.5D, 2.0D, 0.5D);
    // Clear of the barrier box the test framework builds around the template: a stopped rune drifts up to 2.4 blocks.
    private static final int WALL_Y = 8;
    // The rune leaves from 0.1 under the eyes of a standing player.
    private static final double MUZZLE = 1.62D - 0.1D;
    // The 0.5 inaccuracy of the launch moves a rune up to 2.6 blocks sideways in its 100 ticks.
    private static final double DRIFT_REACH = 3.5D;

    private AchtungBabyRuneGameTests() {}

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40)
    public static void runePassesATargetInItsFirstThreeBlocksAndHitsTheNextOne(GameTestHelper helper) {
        ServerPlayer user = user(helper, "ABRuneNear", helper.absoluteVec(USER_AT), -90.0F, 0.0F);
        // Hit boxes (0.9 high, widened by the 0.3 margin) at 0.7-2.2 and 3.9-5.4 blocks along the flight.
        Pig near = target(helper, 1.0D);
        Pig far = target(helper, 4.2D);
        InvisibilityRuneEntity rune = InvisibilityRuneEntity.launch(user, false);
        helper.runAfterDelay(4, () -> {
            boolean nearHit = near.hasEffect(MobEffects.INVISIBILITY);
            boolean farHit = far.hasEffect(MobEffects.INVISIBILITY);
            boolean removed = rune.isRemoved();
            near.discard();
            far.discard();
            rune.discard();
            remove(helper, user);
            helper.assertTrue(!nearHit,
                    "The rune hit a target 1 block ahead; the 1.16 rune crossed its first 3 blocks untested");
            helper.assertTrue(farHit, "The rune did not hit the target 4.2 blocks ahead");
            helper.assertTrue(removed, "The rune outlived its hit");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 140)
    public static void runeStopsAtAWallInItsFirstThreeBlocksAndLingers(GameTestHelper helper) {
        // Looking along +X at a wall whose face is 1.5 blocks away.
        ServerPlayer user = user(helper, "ABRuneWall",
                AchtungBabyPerfectInvisibilityGameTests.insideOriginChunk(
                        helper, new Vec3(0.5D, WALL_Y, 0.5D), DRIFT_REACH),
                0.0F, -90.0F);
        BlockPos wall = user.blockPosition().east(2);
        wall(helper.getLevel(), wall, Blocks.STONE);
        double face = wall.getX();
        InvisibilityRuneEntity rune = InvisibilityRuneEntity.launch(user, false);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!rune.isRemoved(),
                    "The rune broke on a wall 1.5 blocks ahead; the 1.16 rune stopped there");
            // 0.25 wide, flush against the face.
            helper.assertTrue(Math.abs(rune.getX() - (face - 0.125D)) < 1.0E-3D,
                    "The rune is not resting against the wall: x=" + rune.getX() + ", face=" + face);
            helper.assertTrue(rune.getDeltaMovement().x == 0.0D,
                    "The wall did not stop the rune: " + rune.getDeltaMovement());
        });
        helper.runAfterDelay(90, () -> helper.assertTrue(!rune.isRemoved(),
                "The stopped rune was gone before its 100 ticks were over: tick " + rune.tickCount + " at "
                        + rune.position() + " moving " + rune.getDeltaMovement()));
        helper.runAfterDelay(106, () -> {
            boolean removed = rune.isRemoved();
            boolean ticking = ticks(helper, rune);
            rune.discard();
            wall(helper.getLevel(), wall, Blocks.AIR);
            remove(helper, user);
            helper.assertTrue(removed || ticking, leftTheTickingChunk(rune));
            helper.assertTrue(removed, "The stopped rune outlived its 100 ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 140)
    public static void runeFliesAHundredTicksAtItsLaunchSpeed(GameTestHelper helper) {
        ServerPlayer user = user(helper, "ABRuneFree",
                AchtungBabyPerfectInvisibilityGameTests.insideOriginChunk(helper, USER_AT, DRIFT_REACH),
                -90.0F, 0.0F);
        InvisibilityRuneEntity rune = InvisibilityRuneEntity.launch(user, false);
        double startY = rune.getY();
        double speed = rune.getDeltaMovement().y;
        helper.assertTrue(speed > 2.9D && speed < 3.1D, "Setup: the rune was not fired upwards at 3 blocks a tick");
        helper.runAfterDelay(10, () -> {
            double flown = rune.getY() - startY;
            helper.assertTrue(!rune.isRemoved() && rune.tickCount > 0
                            && Math.abs(flown - rune.tickCount * speed) < 1.0E-6D,
                    "The rune did not keep its launch speed: " + flown + " blocks in " + rune.tickCount
                            + " ticks at " + speed);
        });
        helper.runAfterDelay(106, () -> {
            boolean removed = rune.isRemoved();
            boolean ticking = ticks(helper, rune);
            double flown = rune.getY() - startY;
            rune.discard();
            remove(helper, user);
            helper.assertTrue(removed || ticking, leftTheTickingChunk(rune));
            helper.assertTrue(removed, "The rune outlived its 100 ticks");
            helper.assertTrue(Math.abs(flown - 100.0D * speed) < 1.0E-6D,
                    "The rune did not fly for exactly 100 ticks: " + flown + " blocks at " + speed + " a tick");
            helper.succeed();
        });
    }

    private static Pig target(GameTestHelper helper, double feetAboveMuzzle) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, USER_AT.add(0.0D, MUZZLE + feetAboveMuzzle, 0.0D));
        pig.setNoGravity(true);
        return pig;
    }

    private static boolean ticks(GameTestHelper helper, Entity entity) {
        return helper.getLevel().isPositionEntityTicking(entity.blockPosition());
    }

    private static String leftTheTickingChunk(InvisibilityRuneEntity rune) {
        return "Setup: the rune left the chunk that ticks entities and stopped ageing: tick " + rune.tickCount
                + " at " + rune.position();
    }

    // Three wide and three high, its west face towards the thrower.
    private static void wall(ServerLevel level, BlockPos centreOfBottomRow, Block block) {
        for (int y = 0; y <= 2; y++) {
            for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(centreOfBottomRow.offset(0, y, z), block.defaultBlockState());
            }
        }
    }

    private static ServerPlayer user(GameTestHelper helper, String name, Vec3 at, float pitch, float yaw) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(at);
        player.setXRot(pitch);
        player.setYRot(yaw);
        helper.getLevel().addNewPlayer(player);
        helper.assertTrue(helper.getLevel().getEntity(player.getId()) == player,
                "Setup: could not add the test player");
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
    }
}
