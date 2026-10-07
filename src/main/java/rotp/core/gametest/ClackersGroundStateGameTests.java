package rotp.core.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;

/** What a stuck Clackers keeps from the 1.16.5 arrow: the BoomerangHit reset and the motion it is released with. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersGroundStateGameTests {
    private static final long RELEASE_SEED = 764L;

    private ClackersGroundStateGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_boomerang_ground_reset", timeoutTicks = 160)
    public static void boomerangHitIsClearedAfterTenTicksInTheGround(GameTestHelper helper) {
        ClackersScene.start(helper, "boomerang-reset", 140, scene -> {
            scene.stone(6, -2, 2, 10, 4, 2);
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 5.5D), 0.0F);
            Cow cow = scene.cow(new Vec3(user.getX(), user.getEyeY() - 0.1D - 0.7D, user.getZ() + 5.0D), false);
            scene.throwClackers(user);
            scene.await("the hit on the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.entityImpact().entity() == cow && cow.getHealth() < 100.0F && contact.post().boomerang(),
                        "the throw did not land a hit");
                // The owner steps aside, so the returning Clackers flies past and sticks in the wall behind.
                user.moveTo(user.getX() + 4.0D, user.getY(), user.getZ(), user.getYRot(), user.getXRot());
            });
            scene.await("twelve ticks in the wall", () -> scene.groundTicks(0) >= 12);
            scene.then(() -> {
                ClackersScene.Track track = scene.track(0);
                ClackersScene.Step landing = track.landing();
                int at = track.steps.indexOf(landing);
                scene.check(landing.blockImpact() != null && user.getInventory().isEmpty(), "the returning Clackers did not stick in the wall");
                for (int ticks = 0; ticks <= 12; ticks++) {
                    ClackersScene.Frame frame = track.steps.get(at + ticks).post();
                    scene.check(frame.grounded() && !frame.removed(), "left the wall after " + ticks + " ticks");
                    scene.check(frame.boomerang() == (ticks <= 10),
                            "BoomerangHit after " + ticks + " ticks in the ground was " + frame.boomerang());
                }
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_release_vector", timeoutTicks = 140)
    public static void releasedClackersKeepAFractionOfTheirLandingMotion(GameTestHelper helper) {
        releaseVector(helper, "release-vector", false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_release_vector_reload", timeoutTicks = 140)
    public static void landingMotionSurvivesSaveAndReload(GameTestHelper helper) {
        releaseVector(helper, "release-vector-reload", true);
    }

    private static void releaseVector(GameTestHelper helper, String name, boolean reload) {
        ClackersScene.start(helper, name, 120, scene -> {
            scene.stone(6, -1, 9, 10, 3, 9);
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            scene.throwClackers(user);
            scene.await("two ticks in the wall", () -> scene.groundTicks(0) >= 2);
            var state = new Object() {
                ClackersEntity shot;
                Vec3 retained;
                int from;
            };
            scene.then(() -> {
                ClackersScene.Step landing = scene.landing(0);
                BlockHitResult hit = landing.blockImpact();
                scene.check(hit != null && landing.impacts().size() == 1 && scene.shots.get(0).getOwner() == user, "no single wall landing");
                // 1.16.5 arrow: the rest of the landing tick's move, then that tick's drag and gravity.
                state.retained = ClackersScene.afterFlightTick(hit.getLocation().subtract(landing.pre().pos()));
                scene.check(Math.abs(state.retained.z) > 0.5D, "fixture: no landing motion to keep: " + state.retained);
                state.shot = reload ? scene.reload(scene.shots.get(0)) : scene.shots.get(0);
                scene.check(state.shot.isInGround() && state.shot.getDeltaMovement().lengthSqr() == 0.0D, "a stuck Clackers must show no motion");
                state.from = scene.track(state.shot).steps.size();
                state.shot.getRandom().setSeed(RELEASE_SEED);
                scene.check(scene.level.destroyBlock(hit.getBlockPos(), false), "support removal failed");
            });
            scene.await("the release and one flight tick", () -> state.shot != null && scene.track(state.shot).steps.size() >= state.from + 2);
            scene.then(() -> {
                List<ClackersScene.Step> steps = scene.track(state.shot).steps;
                ClackersScene.Step release = steps.get(state.from);
                ClackersScene.Step flight = steps.get(state.from + 1);
                RandomSource random = RandomSource.create(RELEASE_SEED);
                Vec3 expected = state.retained.multiply((double) (random.nextFloat() * 0.2F), (double) (random.nextFloat() * 0.2F),
                        (double) (random.nextFloat() * 0.2F));
                scene.log("retained=" + state.retained + " expected=" + expected + " released=" + release.post().delta());
                scene.check(release.pre().grounded() && !release.post().grounded() && !release.post().removed(), "support loss did not release");
                scene.near(release.post().pos(), release.pre().pos(), "position in the release tick");
                scene.near(release.post().delta(), expected, "release vector");
                scene.near(flight.post().pos(), release.post().pos().add(expected), "position after the first flight tick");
                scene.near(flight.post().delta(), ClackersScene.afterFlightTick(expected), "velocity after the first flight tick");
            });
        });
    }
}
