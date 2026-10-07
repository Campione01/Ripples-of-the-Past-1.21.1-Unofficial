package rotp.core.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;

/**
 * 1.16.5: both hits of thrown Clackers were indirect entity sources, which an Enderman answers by teleporting away
 * unharmed, and ItemProjectileEntity.onHitEntity returned for an Enderman before any change of motion.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersEndermanGameTests {
    private ClackersEndermanGameTests() {}

    private record Dodge(EnderMan enderman, Vec3 start, Vec3 refuge, float health) {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_enderman_motion", timeoutTicks = 120)
    public static void clackersKeepTheirMotionWhenAnEndermanDodgesThem(GameTestHelper helper) {
        ClackersScene.start(helper, "enderman-motion", 100, scene -> {
            Dodge dodge = throwAtEnderman(scene);
            scene.then(() -> {
                ClackersScene.Step contact = contactWithDodge(scene, dodge);
                Vec3 incoming = contact.pre().delta();
                scene.log("incoming=" + incoming + " after=" + contact.post().delta());
                scene.check(contact.post().boomerang(), "1.16: a dodged hit still counts as landed for the boomerang flag");
                scene.near(contact.post().delta(), ClackersScene.afterFlightTick(incoming),
                        "1.16: Clackers an Enderman dodged keep their velocity; velocity after the contact tick");
                scene.near(contact.post().pos(), contact.pre().pos().add(incoming),
                        "1.16: Clackers an Enderman dodged fly on; position after the contact tick");
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_enderman_hamon", timeoutTicks = 120)
    public static void endermanDodgesTheHamonHitOfThrownClackers(GameTestHelper helper) {
        ClackersScene.start(helper, "enderman-hamon", 100, scene -> {
            Dodge dodge = throwAtEnderman(scene);
            scene.then(() -> {
                ClackersScene.Step contact = contactWithDodge(scene, dodge);
                scene.check(contact.pre().tag().getFloat("HamonDamage") > 0.0F, "fixture: the thrown Clackers carry no Hamon damage");
                EnderMan enderman = dodge.enderman();
                scene.check(enderman.getHealth() == dodge.health() && enderman.getLastHurtByMob() == null,
                        "1.16: an Enderman dodges the Hamon hit of thrown Clackers as well; health " + dodge.health() + " -> "
                                + enderman.getHealth() + ", last hurt by " + enderman.getLastHurtByMob());
            });
        });
    }

    private static Dodge throwAtEnderman(ClackersScene scene) {
        scene.stone(4, -1, 4, 12, -1, 13);
        Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
        EnderMan enderman = EntityType.ENDERMAN.create(scene.level);
        enderman.setNoAi(true);
        Vec3 start = scene.point(8.5D, 0.0D, 6.5D);
        Vec3 refuge = scene.point(5.5D, 0.0D, 11.5D);
        enderman.setPos(start);
        scene.add(enderman);
        // the random destination is replaced, so the Enderman stays inside the room
        scene.listen(EntityTeleportEvent.EnderEntity.class, event -> {
            if (event.getEntity() == enderman) {
                event.setTargetX(refuge.x);
                event.setTargetY(refuge.y);
                event.setTargetZ(refuge.z);
            }
        });
        Dodge dodge = new Dodge(enderman, start, refuge, enderman.getHealth());
        scene.throwClackers(user);
        scene.await("the contact with the Enderman", () -> scene.contact(0) != null);
        return dodge;
    }

    private static ClackersScene.Step contactWithDodge(ClackersScene scene, Dodge dodge) {
        ClackersScene.Step contact = scene.contact(0);
        EnderMan enderman = dodge.enderman();
        scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == enderman && enderman.isAlive()
                && contact.entityImpact().entityBox().getCenter().distanceTo(dodge.start().add(0.0D, enderman.getBbHeight() / 2.0D, 0.0D)) < 0.01D
                && enderman.position().distanceTo(dodge.refuge()) < 0.01D,
                "fixture: not one contact with an Enderman that teleported away; it stands at " + enderman.position());
        return contact;
    }
}
