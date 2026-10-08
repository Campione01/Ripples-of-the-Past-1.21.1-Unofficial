package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.ModDamageTypes;

/**
 * 1.16.5 ClackersEntity.hurtTarget calls dealHamonDamage(target, hamonDmg, this, thrower) with a null thrower too:
 * Clackers whose thrower is gone still deal the Hamon damage they carry, as a hit of the Clackers alone.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersOwnerlessHamonGameTests {
    private static final float CARRIED = 5.0F;

    private ClackersOwnerlessHamonGameTests() {}

    private record Hit(DamageSource source, float amount) {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_ownerless_hamon", timeoutTicks = 80)
    public static void clackersWithoutAThrowerStillDealTheirHamonDamage(GameTestHelper helper) {
        ClackersScene.start(helper, "ownerless-hamon", 60, scene -> {
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            Cow cow = scene.cow(new Vec3(spawn.x, spawn.y + 0.25D - 0.7D, spawn.z + 2.0D), false);
            List<Hit> hits = new ArrayList<>();
            scene.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == cow) {
                    hits.add(new Hit(event.getSource(), event.getAmount()));
                }
            });
            scene.then(() -> scene.summon(spawn, "{Motion:[0.0d,0.0d,2.0d],HamonDamage:" + CARRIED + "f}"));
            scene.await("the summoned Clackers hitting the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                ClackersEntity shot = scene.shots.get(0);
                List<Hit> physical = hits.stream().filter(hit -> hit.source().is(ModDamageTypes.MOD_PROJECTILE)).toList();
                List<Hit> hamon = hits.stream().filter(hit -> hit.source().is(ModDamageTypes.HAMON)).toList();
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && shot.getOwner() == null
                        && contact.pre().tag().getFloat("HamonDamage") == CARRIED && physical.size() == 1
                        && HamonAbilityHelpers.hamonDamageMultiplier(cow) == 0.2F,
                        "fixture: not one hit of ownerless Clackers carrying Hamon damage; physical hits: " + physical.size());
                // 1.16 dealHamonDamage: a fifth for a target that is not undead, then the config multiplier
                float expected = CARRIED * 0.2F * HamonAbilityHelpers.configHamonDamageMultiplier();
                scene.check(hamon.size() == 1, "1.16: Clackers without a thrower deal their Hamon damage as well; Hamon hits: "
                        + hamon.size() + ", all hits: " + hits.stream().map(hit -> hit.source().typeHolder().getRegisteredName()).toList());
                Hit hit = hamon.get(0);
                scene.check(hit.source().getDirectEntity() == shot && hit.source().getEntity() == null
                        && Math.abs(hit.amount() - expected) < 1.0E-5F,
                        "1.16: the Hamon hit comes from the Clackers alone; direct " + hit.source().getDirectEntity() + ", attacker "
                                + hit.source().getEntity() + ", amount " + hit.amount() + " (expected " + expected + ")");
                float health = 100.0F - physical.get(0).amount() - expected;
                scene.check(Math.abs(cow.getHealth() - health) < 1.0E-4F, "the target lost " + (100.0F - cow.getHealth())
                        + " health, expected " + (100.0F - health));
            });
        });
    }
}
