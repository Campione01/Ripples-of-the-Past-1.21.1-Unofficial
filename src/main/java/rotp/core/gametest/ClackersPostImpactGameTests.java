package rotp.core.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.util.functions.MathUtil;

/** Motion of a Clackers in the tick of an entity contact, against 1.16.5 ItemProjectileEntity and ClackersEntity. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersPostImpactGameTests {
    private ClackersPostImpactGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_refused_rebound", timeoutTicks = 120)
    public static void refusedClackersHitReboundsWeaklyAndTurnsAround(GameTestHelper helper) {
        ClackersScene.start(helper, "refused-rebound", 100, scene -> {
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            Cow cow = scene.cow(new Vec3(user.getX(), user.getEyeY() - 0.1D - 0.7D, user.getZ() + 2.0D), true);
            scene.throwClackers(user);
            scene.await("the contact with the invulnerable target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                Vec3 incoming = contact.pre().delta();
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && cow.getHealth() == 100.0F
                        && !contact.post().boomerang() && incoming.length() > 2.5D, "the target did not refuse one full-power contact");
                Vec3 rebound = incoming.scale(-0.1D);
                scene.log("incoming=" + incoming + " after=" + contact.post().delta() + " yaw=" + contact.pre().yRot() + "->" + contact.post().yRot());
                scene.near(contact.post().delta(), ClackersScene.afterFlightTick(rebound), "velocity after a refused hit");
                scene.near(contact.post().pos(), contact.pre().pos().add(rebound), "position after a refused hit");
                float yaw = ClackersScene.turn(contact.pre().yRot() + 180.0F, MathUtil.yRotDegFromVec(rebound));
                scene.check(Math.abs(Mth.wrapDegrees(contact.post().yRot() - yaw)) < 1.0E-3F,
                        "yaw after a refused hit: expected " + yaw + " but was " + contact.post().yRot());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_clash_ray_hit", timeoutTicks = 120)
    public static void clashedClackersDropAwayFromTheEntityOnTheirLookRay(GameTestHelper helper) {
        clash(helper, "clash-ray-hit", false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_clash_ray_miss", timeoutTicks = 120)
    public static void clashedClackersDropForwardWhenTheirLookRayMisses(GameTestHelper helper) {
        clash(helper, "clash-ray-miss", true);
    }

    private static void clash(GameTestHelper helper, String name, boolean alongX) {
        ClackersScene.start(helper, name, 100, scene -> {
            Player first = scene.thrower(GameType.SURVIVAL, alongX ? scene.point(3.5D, 0.0D, 8.5D) : scene.point(8.5D, 0.0D, 3.5D),
                    alongX ? -90.0F : 0.0F);
            Player second = scene.thrower(GameType.SURVIVAL, alongX ? scene.point(12.5D, 0.0D, 8.5D) : scene.point(8.5D, 0.0D, 12.5D),
                    alongX ? 90.0F : 180.0F);
            scene.throwClackers(first, second);
            scene.await("the two Clackers meeting", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                ClackersScene.Impact impact = contact.entityImpact();
                ClackersEntity other = scene.shots.get(1);
                scene.check(contact.impacts().size() == 1 && impact.entity() == other && !contact.pre().boomerang()
                        && !contact.post().boomerang() && first.isAlive() && scene.shots.get(0).getOwner() == first,
                        "not a first Clackers-to-Clackers contact with a live owner");
                Vec3 incoming = contact.pre().delta();
                // Independent copy of the donor query: a 1.16 arrow's view vector is its heading mirrored in X and Y.
                Vec3 look = Vec3.directionFromRotation(contact.pre().xRot(), contact.pre().yRot());
                Vec3 start = contact.pre().pos().add(0.0D, (double) 0.13F, 0.0D);
                Vec3 end = start.add(new Vec3(-look.x, -look.y, look.z).scale(contact.pre().pos().distanceTo(first.position())));
                AABB reach = impact.entityBox().inflate(2.5D);
                boolean rayFindsOther = reach.contains(start) || reach.clip(start, end).isPresent();
                scene.check(rayFindsOther != alongX, "fixture: the donor look ray " + start + " -> " + end + " against " + reach);
                Vec3 aimed = rayFindsOther ? impact.entityEye().subtract(contact.pre().pos()).normalize().scale(incoming.length())
                        : incoming.reverse();
                Vec3 damped = aimed.multiply(-0.01D, -0.1D, -0.01D);
                scene.log("incoming=" + incoming + " rayFindsOther=" + rayFindsOther + " expected=" + ClackersScene.afterFlightTick(damped)
                        + " after=" + contact.post().delta());
                scene.near(contact.post().delta(), ClackersScene.afterFlightTick(damped), "velocity after a clash");
                scene.near(contact.post().pos(), contact.pre().pos().add(damped), "position after a clash");
                scene.check(contact.post().impulse(), "the post-impact motion was not flagged for the tracker");
                scene.check(other.getEyeHeight() == 0.13F, "Clackers eye height is not the arrow's 0.13: " + other.getEyeHeight());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_ownerless_impact", timeoutTicks = 80)
    public static void ownerlessClackersOnlyLoseTheirSpeedOnAHit(GameTestHelper helper) {
        ClackersScene.start(helper, "ownerless-impact", 60, scene -> {
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            Cow cow = scene.cow(new Vec3(spawn.x, spawn.y + 0.25D - 0.7D, spawn.z + 2.0D), false);
            scene.then(() -> scene.summon(spawn, "{Motion:[0.0d,0.0d,2.0d]}"));
            scene.await("the summoned Clackers hitting the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && cow.getHealth() < 100.0F
                        && scene.shots.get(0).getOwner() == null, "the ownerless hit did not land");
                Vec3 damped = contact.pre().delta().multiply(-0.01D, -0.1D, -0.01D);
                scene.log("incoming=" + contact.pre().delta() + " after=" + contact.post().delta());
                scene.near(contact.post().delta(), ClackersScene.afterFlightTick(damped), "velocity after an ownerless hit");
                scene.near(contact.post().pos(), contact.pre().pos().add(damped), "position after an ownerless hit");
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_turn_rate", timeoutTicks = 120)
    public static void clackersTurnTowardsTheirHeadingAtTheArrowRate(GameTestHelper helper) {
        ClackersScene.start(helper, "turn-rate", 100, scene -> {
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 2.5D), 0.0F);
            scene.throwClackers(user);
            scene.await("three free flight ticks", () -> scene.steps(0) >= 3);
            scene.then(() -> {
                ClackersScene.Track track = scene.track(0);
                for (int i = 0; i < 3; i++) {
                    ClackersScene.Step step = track.steps.get(i);
                    scene.check(step.impacts().isEmpty() && !step.post().grounded() && !step.post().removed(), "the flight was interrupted");
                    float pitch = ClackersScene.turn(step.pre().xRot(), MathUtil.xRotDegFromVec(step.pre().delta()));
                    float yaw = ClackersScene.turn(step.pre().yRot(), MathUtil.yRotDegFromVec(step.pre().delta()));
                    scene.log("tick=" + (i + 1) + " pitch=" + step.post().xRot() + " expected=" + pitch + " yaw=" + step.post().yRot() + " expected=" + yaw);
                    scene.check(Math.abs(step.post().xRot() - pitch) < 1.0E-4F && Math.abs(Mth.wrapDegrees(step.post().yRot() - yaw)) < 1.0E-4F,
                            "rotation after flight tick " + (i + 1) + ": expected pitch " + pitch + " yaw " + yaw + " but was "
                                    + step.post().xRot() + " " + step.post().yRot());
                }
                scene.check(Math.abs(track.steps.get(2).post().xRot()) > 0.1F, "fixture: the heading never changed");
            });
        });
    }
}
