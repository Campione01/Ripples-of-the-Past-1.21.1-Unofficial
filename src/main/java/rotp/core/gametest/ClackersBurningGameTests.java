package rotp.core.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;

/**
 * 1.16.5 ItemProjectileEntity.onHitEntity: burning Clackers set their target on fire for 5 seconds before the hit,
 * except an Enderman, and a refused hit puts the target's fire timer back.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersBurningGameTests {
    private static final String BURNING = "{Motion:[0.0d,0.0d,2.0d],Fire:200s}";

    private ClackersBurningGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_burning_enderman", timeoutTicks = 80)
    public static void burningClackersDoNotIgniteAnEndermanThatDodgesThem(GameTestHelper helper) {
        ClackersScene.start(helper, "burning-enderman", 60, scene -> {
            scene.stone(4, -1, 4, 12, -1, 13);
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            Vec3 refuge = scene.point(5.5D, 0.0D, 11.5D);
            EnderMan enderman = EntityType.ENDERMAN.create(scene.level);
            enderman.setNoAi(true);
            enderman.setPos(scene.point(8.5D, 0.0D, 6.5D));
            scene.add(enderman);
            // the random destination is replaced, so the Enderman stays inside the room
            scene.listen(EntityTeleportEvent.EnderEntity.class, event -> {
                if (event.getEntity() == enderman) {
                    event.setTargetX(refuge.x);
                    event.setTargetY(refuge.y);
                    event.setTargetZ(refuge.z);
                }
            });
            scene.then(() -> scene.summon(spawn, BURNING));
            scene.await("the burning Clackers reaching the Enderman", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == enderman && enderman.isAlive()
                        && contact.pre().tag().getShort("Fire") > 0 && enderman.position().distanceTo(refuge) < 0.01D,
                        "fixture: not one contact of burning Clackers with an Enderman that teleported away; it stands at "
                                + enderman.position());
                scene.check(enderman.getRemainingFireTicks() <= 0, "1.16: burning Clackers do not set an Enderman on fire;"
                        + " its fire ticks: " + enderman.getRemainingFireTicks());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_burning_before_hit", timeoutTicks = 80)
    public static void burningClackersSetTheirTargetOnFireBeforeTheHit(GameTestHelper helper) {
        ClackersScene.start(helper, "burning-before-hit", 60, scene -> {
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            Cow cow = scene.cow(new Vec3(spawn.x, spawn.y + 0.25D - 0.7D, spawn.z + 2.0D), false);
            int[] fireAtTheHit = { Integer.MIN_VALUE };
            scene.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == cow && event.getSource().getDirectEntity() instanceof ClackersEntity
                        && fireAtTheHit[0] == Integer.MIN_VALUE) {
                    fireAtTheHit[0] = cow.getRemainingFireTicks();
                }
            });
            scene.then(() -> scene.summon(spawn, BURNING));
            scene.await("the burning Clackers hitting the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && cow.getHealth() < 100.0F
                        && contact.pre().tag().getShort("Fire") > 0, "fixture: the burning Clackers did not hurt the target");
                scene.check(fireAtTheHit[0] == 100 && cow.getRemainingFireTicks() > 0,
                        "1.16: the target burns for 5 seconds from before the hit on; fire ticks when the hit arrived: "
                                + fireAtTheHit[0] + ", afterwards: " + cow.getRemainingFireTicks());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_burning_refused", timeoutTicks = 80)
    public static void burningClackersLeaveATargetThatRefusesTheHitUnlit(GameTestHelper helper) {
        ClackersScene.start(helper, "burning-refused", 60, scene -> {
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            Cow cow = scene.cow(new Vec3(spawn.x, spawn.y + 0.25D - 0.7D, spawn.z + 2.0D), true);
            int before = cow.getRemainingFireTicks();
            scene.then(() -> scene.summon(spawn, BURNING));
            scene.await("the burning Clackers reaching the invulnerable target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && cow.getHealth() == 100.0F
                        && contact.pre().tag().getShort("Fire") > 0, "fixture: not one refused contact of burning Clackers");
                scene.check(cow.getRemainingFireTicks() <= 0 && before <= 0, "1.16: a refused hit puts the fire timer back;"
                        + " fire ticks " + before + " -> " + cow.getRemainingFireTicks());
            });
        });
    }
}
