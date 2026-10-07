package rotp.core.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;

/**
 * 1.16.5 ItemProjectileEntity.onHitEntity wrote the thrower's last-hurt mob before every hit, landed or refused.
 * ClackersEntity.onHitEntity returned before that for a contact with another Clackers.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersThrowerMemoryGameTests {
    private ClackersThrowerMemoryGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_last_hurt_mob", timeoutTicks = 120)
    public static void clackersHitRecordsTheThrowersLastHurtMob(GameTestHelper helper) {
        hit(helper, "last-hurt-mob", false);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_last_hurt_mob_refused", timeoutTicks = 120)
    public static void refusedClackersHitStillRecordsTheThrowersLastHurtMob(GameTestHelper helper) {
        hit(helper, "last-hurt-mob-refused", true);
    }

    private static void hit(GameTestHelper helper, String name, boolean invulnerable) {
        ClackersScene.start(helper, name, 100, scene -> {
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            Cow cow = scene.cow(new Vec3(user.getX(), user.getEyeY() - 0.1D - 0.7D, user.getZ() + 2.0D), invulnerable);
            scene.check(user.getLastHurtMob() == null, "fixture: the thrower already has a last-hurt mob");
            scene.throwClackers(user);
            scene.await("the contact with the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow
                        && cow.getHealth() < 100.0F != invulnerable, "fixture: not one "
                        + (invulnerable ? "refused" : "landed") + " contact with the target, health " + cow.getHealth());
                scene.check(user.getLastHurtMob() == cow, "1.16: a Clackers hit records its target as the thrower's last-hurt mob"
                        + (invulnerable ? " even when the hit is refused" : "") + ", but it is " + user.getLastHurtMob());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_last_hurt_mob_clash", timeoutTicks = 120)
    public static void clackersClashLeavesTheThrowersLastHurtMobAlone(GameTestHelper helper) {
        ClackersScene.start(helper, "last-hurt-mob-clash", 100, scene -> {
            Player first = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            Player second = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 12.5D), 180.0F);
            int[] stamps = new int[2];
            scene.then(() -> {
                first.setLastHurtMob(second);
                second.setLastHurtMob(first);
                stamps[0] = first.getLastHurtMobTimestamp();
                stamps[1] = second.getLastHurtMobTimestamp();
            });
            scene.throwClackers(first, second);
            scene.await("the two Clackers meeting", () -> scene.contact(0) != null || scene.contact(1) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0) != null ? scene.contact(0) : scene.contact(1);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() instanceof ClackersEntity,
                        "fixture: not a Clackers-to-Clackers contact");
                scene.check(first.getLastHurtMob() == second && second.getLastHurtMob() == first
                        && first.getLastHurtMobTimestamp() == stamps[0] && second.getLastHurtMobTimestamp() == stamps[1],
                        "1.16: a clash of two Clackers does not touch a thrower's last-hurt mob, but they are "
                                + first.getLastHurtMob() + " (tick " + first.getLastHurtMobTimestamp() + ", was " + stamps[0] + ") and "
                                + second.getLastHurtMob() + " (tick " + second.getLastHurtMobTimestamp() + ", was " + stamps[1] + ")");
            });
        });
    }
}
