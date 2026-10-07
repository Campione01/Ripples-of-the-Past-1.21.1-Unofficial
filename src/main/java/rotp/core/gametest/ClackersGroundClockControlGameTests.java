package rotp.core.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;

/** Controls of the Creative ground clock and the grounded state: save and reload, NoPhysics, a dead owner, resets. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersGroundClockControlGameTests {
    private ClackersGroundClockControlGameTests() {}

    private static Player creativeThrowIntoWall(ClackersScene scene) {
        scene.stone(6, -1, 9, 10, 3, 9);
        Player user = scene.thrower(GameType.CREATIVE, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
        scene.throwClackers(user);
        return user;
    }

    private static int life(ClackersEntity shot) {
        return shot.saveWithoutId(new CompoundTag()).getShort("life");
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_ground_save_reload", timeoutTicks = 160)
    public static void groundedCreativeClackersKeepTheirClockAndSupportAcrossSaveAndReload(GameTestHelper helper) {
        ClackersScene.start(helper, "ground-save-reload", 140, scene -> {
            Player user = creativeThrowIntoWall(scene);
            scene.await("five ticks in the wall", () -> scene.groundTicks(0) >= 5);
            var state = new Object() {
                ClackersEntity loaded;
                BlockPos support;
                Vec3 pos;
            };
            scene.then(() -> {
                ClackersEntity shot = scene.shots.get(0);
                state.support = scene.landing(0).blockImpact().getBlockPos();
                state.pos = shot.position();
                CompoundTag before = shot.saveWithoutId(new CompoundTag());
                scene.check(before.getShort("life") == 5 && before.getBoolean("InGround") && before.getBoolean("CreativeOnlyPickup")
                        && NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()).equals(before.getCompound("inBlockState")),
                        "grounded Creative state before the save: " + before);
                ClackersEntity loaded = scene.reload(shot);
                CompoundTag after = loaded.saveWithoutId(new CompoundTag());
                scene.check(loaded.isInGround() && loaded.position().equals(state.pos) && loaded.getDeltaMovement().lengthSqr() == 0.0D
                        && loaded.getOwner() == user, "the reloaded Clackers lost its place, grounding or owner");
                scene.check(after.getShort("life") == 5 && after.getBoolean("CreativeOnlyPickup")
                        && after.getCompound("inBlockState").equals(before.getCompound("inBlockState")),
                        "life, support state or pickup rule changed across the reload: " + after);
                state.loaded = loaded;
            });
            scene.await("three ticks after the reload", () -> state.loaded != null && scene.track(state.loaded).steps.size() >= 3);
            scene.then(() -> {
                ClackersScene.Frame third = scene.track(state.loaded).steps.get(2).post();
                scene.check(third.grounded() && !third.removed() && third.life() == 8 && third.pos().equals(state.pos),
                        "the ground clock did not continue from the saved life: " + third.life());
                scene.check(scene.level.destroyBlock(state.support, false), "support removal failed");
            });
            scene.await("the release and two flight ticks", () -> scene.track(state.loaded).steps.size() >= 6);
            scene.then(() -> {
                List<ClackersScene.Step> steps = scene.track(state.loaded).steps;
                ClackersScene.Step release = steps.get(3);
                scene.check(release.pre().grounded() && !release.post().grounded() && !release.post().removed()
                        && release.post().life() == 0 && release.post().pos().equals(state.pos),
                        "the reloaded Clackers did not release with a restarted clock: life " + release.post().life());
                ClackersScene.Frame falling = steps.get(5).post();
                scene.check(!falling.grounded() && !falling.removed() && falling.life() == 0 && falling.pos().y < state.pos.y
                        && falling.delta().y < 0.0D, "the released Clackers did not fall: " + falling.pos() + " " + falling.delta());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_ground_clock_no_physics", timeoutTicks = 160)
    public static void noPhysicsPausesTheCreativeGroundClock(GameTestHelper helper) {
        ClackersScene.start(helper, "ground-clock-no-physics", 140, scene -> {
            creativeThrowIntoWall(scene);
            scene.await("three ticks in the wall", () -> scene.groundTicks(0) >= 3);
            scene.then(() -> {
                scene.check(life(scene.shots.get(0)) == 3, "fixture: three counted ground ticks");
                scene.shots.get(0).noPhysics = true;
            });
            scene.await("five NoPhysics ticks", () -> scene.groundTicks(0) >= 8);
            scene.then(() -> {
                ClackersScene.Track track = scene.track(0);
                for (ClackersScene.Step step : track.steps.subList(track.steps.size() - 5, track.steps.size())) {
                    scene.check(step.post().grounded() && !step.post().removed() && step.post().life() == 3
                            && step.post().pos().equals(track.landing().post().pos()),
                            "a NoPhysics tick advanced the ground clock or moved the Clackers: life " + step.post().life());
                }
                scene.shots.get(0).noPhysics = false;
            });
            scene.await("two ordinary ticks", () -> scene.groundTicks(0) >= 10);
            scene.then(() -> scene.check(life(scene.shots.get(0)) == 5 && scene.shots.get(0).isInGround(),
                    "the ground clock did not resume after NoPhysics: " + life(scene.shots.get(0))));
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_creative_dead_owner", timeoutTicks = 160)
    public static void creativeClackersStayOnTheirGroundClockAfterTheirOwnerDies(GameTestHelper helper) {
        ClackersScene.start(helper, "creative-dead-owner", 140, scene -> {
            Player user = creativeThrowIntoWall(scene);
            scene.await("two ticks in the wall", () -> scene.groundTicks(0) >= 2);
            scene.then(() -> {
                scene.check(life(scene.shots.get(0)) == 2 && scene.shots.get(0).getOwner() == user, "fixture: two counted ground ticks");
                user.getInventory().clearContent();
                user.kill();
                scene.check(!user.isAlive() && !user.isRemoved(), "the Creative owner did not die");
            });
            scene.await("five ticks with a dead owner", () -> scene.groundTicks(0) >= 7 || scene.shots.get(0).isRemoved());
            scene.then(() -> {
                ClackersScene.Track track = scene.track(0);
                int at = track.steps.indexOf(track.landing());
                for (int ticks = 3; ticks <= 7; ticks++) {
                    ClackersScene.Frame frame = track.steps.get(Math.min(at + ticks, track.steps.size() - 1)).post();
                    scene.check(frame.grounded() && !frame.removed() && frame.life() == ticks,
                            "ground tick " + ticks + " with a dead owner: removed " + frame.reason() + ", life " + frame.life());
                }
                scene.check(scene.shots.get(0).getOwner() == user && !user.isAlive(), "the dead owner was not kept as the owner");
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_ground_clock_resets", timeoutTicks = 160)
    public static void shootAndMotionUpdatesRestartTheGroundClock(GameTestHelper helper) {
        ClackersScene.start(helper, "ground-clock-resets", 140, scene -> {
            creativeThrowIntoWall(scene);
            scene.await("four ticks in the wall", () -> scene.groundTicks(0) >= 4);
            scene.then(() -> {
                ClackersEntity shot = scene.shots.get(0);
                scene.check(life(shot) == 4, "fixture: four counted ground ticks");
                shot.shoot(0.0D, 1.0D, 0.0D, 0.0F, 0.0F);
                scene.check(life(shot) == 0, "shoot() did not restart the ground clock: " + life(shot));
            });
            scene.await("three more ticks", () -> scene.groundTicks(0) >= 7);
            scene.then(() -> {
                ClackersEntity shot = scene.shots.get(0);
                scene.check(shot.isInGround() && life(shot) == 3, "the clock did not count again after shoot(): " + life(shot));
                shot.lerpMotion(0.0D, 0.0D, 0.0D);
                scene.check(life(shot) == 0, "lerpMotion() did not restart the ground clock: " + life(shot));
            });
            scene.await("one more tick", () -> scene.groundTicks(0) >= 8);
            scene.then(() -> scene.check(life(scene.shots.get(0)) == 1 && scene.shots.get(0).isInGround(),
                    "the clock did not count again after lerpMotion(): " + life(scene.shots.get(0))));
        });
    }
}
