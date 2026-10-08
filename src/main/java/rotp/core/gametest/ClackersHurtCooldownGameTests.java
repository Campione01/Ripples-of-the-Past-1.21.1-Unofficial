package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.init.ModDamageTypes;

/**
 * 1.16.5 ItemProjectileEntity.hurtTarget dealt the physical hit of thrown Clackers as a plain arrow hurt,
 * DamageSource.arrow(this, thrower == null ? this : thrower): the target's hurt cooldown refused it or cut it to the
 * excess over the last hit, and without a thrower the Clackers themselves were the attacker the target was knocked
 * away from. ClackersEntity.hurtTarget then called dealHamonDamage with whatever Hamon damage was left, zero
 * included; that hit ignored the cooldown, and a mob accepts a hit of zero.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersHurtCooldownGameTests {
    private ClackersHurtCooldownGameTests() {}

    private record Hit(DamageSource source, float amount) {
        boolean physical() {
            return source.is(ModDamageTypes.MOD_PROJECTILE);
        }

        boolean hamon() {
            return source.is(ModDamageTypes.HAMON);
        }
    }

    private record Knock(float strength, double x, double z) {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_cooldown_refused", timeoutTicks = 120)
    public static void physicalHitIsRefusedInsideTheHurtCooldownAndTheHamonHitLands(GameTestHelper helper) {
        thrownInsideTheCooldown(helper, "cooldown-refused", 50.0F);
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_cooldown_excess", timeoutTicks = 120)
    public static void physicalHitInsideTheHurtCooldownDealsOnlyTheExcessOverTheLastHit(GameTestHelper helper) {
        thrownInsideTheCooldown(helper, "cooldown-excess", 1.0F);
    }

    private static void thrownInsideTheCooldown(GameTestHelper helper, String name, float primed) {
        ClackersScene.start(helper, name, 100, scene -> {
            Player thrower = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 1.0D, 4.5D), 0.0F);
            Cow cow = scene.cow(new Vec3(thrower.getX(), thrower.getEyeY() - 0.1D - 0.7D, thrower.getZ() + 2.0D), false);
            List<Hit> hits = new ArrayList<>();
            int[] cooldownAtContact = { -1 };
            scene.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == cow) {
                    hits.add(new Hit(event.getSource(), event.getAmount()));
                }
            });
            scene.listen(ProjectileImpactEvent.class, event -> {
                if (event.getProjectile() instanceof ClackersEntity && event.getRayTraceResult() instanceof EntityHitResult hit
                        && hit.getEntity() == cow && cooldownAtContact[0] < 0) {
                    cooldownAtContact[0] = cow.invulnerableTime;
                }
            });
            scene.throwClackers(thrower);
            // another hit lands just before the Clackers arrive
            scene.then(() -> {
                scene.check(cow.hurt(cow.damageSources().generic(), primed) && cow.invulnerableTime == 20 && cow.lastHurt == primed,
                        "fixture: the first hit did not start the hurt cooldown");
                hits.clear();
            });
            scene.await("the thrown Clackers reaching the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                float physical = (float) (contact.pre().delta().length() * 2.0D);
                List<Hit> physicalHits = hits.stream().filter(Hit::physical).toList();
                List<Hit> hamonHits = hits.stream().filter(Hit::hamon).toList();
                String seen = "physical hits " + physicalHits.stream().map(Hit::amount).toList() + ", Hamon hits "
                        + hamonHits.stream().map(Hit::amount).toList() + ", last hit " + primed + " -> " + cow.lastHurt
                        + ", health " + cow.getHealth() + ", cooldown " + cow.invulnerableTime;
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && cooldownAtContact[0] > 10
                        && contact.pre().tag().getFloat("HamonDamage") > 0.0F && physical > 1.0F && physical < 50.0F
                        && hamonHits.size() == 1 && hamonHits.get(0).amount() > 0.0F,
                        "fixture: not one contact of charged Clackers inside the hurt cooldown; cooldown at the contact "
                                + cooldownAtContact[0] + ", physical damage " + physical + ", " + seen);
                float hamon = hamonHits.get(0).amount();
                if (primed >= physical) {
                    scene.check(physicalHits.isEmpty() && cow.lastHurt == primed,
                            "1.16: the hurt cooldown refuses the arrow hit of thrown Clackers that is not stronger than the last hit ("
                                    + physical + " against " + primed + "); " + seen);
                    scene.check(Math.abs(cow.getHealth() - (100.0F - primed - hamon)) < 1.0E-4F && contact.post().boomerang(),
                            "1.16: the Hamon hit still lands through the cooldown and counts as the hit; boomerang latch "
                                    + contact.post().boomerang() + ", " + seen);
                }
                else {
                    scene.check(physicalHits.size() == 1 && Math.abs(physicalHits.get(0).amount() - (physical - primed)) < 1.0E-4F
                            && cow.lastHurt == physical,
                            "1.16: inside the hurt cooldown the arrow hit of thrown Clackers deals only its excess over the last hit ("
                                    + physical + " - " + primed + "); " + seen);
                    scene.check(Math.abs(cow.getHealth() - (100.0F - physical - hamon)) < 1.0E-4F,
                            "the target should have lost " + (physical + hamon) + " health in total; " + seen);
                }
                scene.check(cow.invulnerableTime > 10 && cow.invulnerableTime <= cooldownAtContact[0],
                        "1.16: a hit inside the hurt cooldown does not restart it; cooldown at the contact " + cooldownAtContact[0]
                                + ", " + seen);
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_ownerless_knockback", timeoutTicks = 80)
    public static void clackersWithoutAThrowerAreTheAttackerAndKnockTheTargetAway(GameTestHelper helper) {
        ClackersScene.start(helper, "ownerless-knockback", 60, scene -> {
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            // off the flight line, so "away from the Clackers" differs from "along their motion"
            Cow cow = scene.cow(new Vec3(spawn.x + 0.3D, spawn.y + 0.25D - 0.7D, spawn.z + 2.0D), false);
            List<Object> seen = new ArrayList<>();
            scene.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == cow) {
                    seen.add(new Hit(event.getSource(), event.getAmount()));
                }
            });
            scene.listen(LivingKnockBackEvent.class, event -> {
                if (event.getEntity() == cow) {
                    seen.add(new Knock(event.getStrength(), event.getRatioX(), event.getRatioZ()));
                }
            });
            scene.then(() -> scene.summon(spawn, "{Motion:[0.0d,0.0d,2.0d]}"));
            scene.await("the summoned Clackers hitting the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                ClackersEntity shot = scene.shots.get(0);
                int first = -1;
                for (int i = 0; i < seen.size() && first < 0; i++) {
                    if (seen.get(i) instanceof Hit hit && hit.physical()) first = i;
                }
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && shot.getOwner() == null
                        && first >= 0, "fixture: not one physical hit of Clackers without a thrower; seen " + seen);
                Hit physical = (Hit) seen.get(first);
                scene.check(physical.source().getDirectEntity() == shot && physical.source().getEntity() == shot,
                        "1.16: Clackers without a thrower are the attacker of their own arrow hit; direct "
                                + physical.source().getDirectEntity() + ", attacker " + physical.source().getEntity());
                Knock knock = first + 1 < seen.size() && seen.get(first + 1) instanceof Knock next ? next : null;
                Vec3 from = contact.pre().pos();
                scene.check(knock != null && knock.strength() == 0.4F && Math.abs(knock.x() - (from.x - cow.getX())) < 1.0E-6D
                        && Math.abs(knock.z() - (from.z - cow.getZ())) < 1.0E-6D,
                        "1.16: that hit knocks the target back, away from the Clackers; knockback " + knock + ", Clackers at " + from
                                + ", target at " + cow.position());
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_zero_hamon_hit", timeoutTicks = 80)
    public static void clackersWithoutHamonDamageStillHitAMobThroughTheHurtCooldown(GameTestHelper helper) {
        ClackersScene.start(helper, "zero-hamon-hit", 60, scene -> {
            Vec3 spawn = scene.point(8.5D, 1.0D, 4.5D);
            Cow cow = scene.cow(new Vec3(spawn.x, spawn.y + 0.25D - 0.7D, spawn.z + 2.0D), false);
            List<Hit> hits = new ArrayList<>();
            scene.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.getEntity() == cow) {
                    hits.add(new Hit(event.getSource(), event.getAmount()));
                }
            });
            scene.then(() -> {
                scene.summon(spawn, "{Motion:[0.0d,0.0d,2.0d]}");
                scene.check(cow.hurt(cow.damageSources().generic(), 50.0F) && cow.invulnerableTime == 20,
                        "fixture: the first hit did not start the hurt cooldown");
                hits.clear();
            });
            scene.await("the summoned Clackers reaching the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                List<Hit> hamonHits = hits.stream().filter(Hit::hamon).toList();
                String seen = "hits " + hits.stream().map(hit -> hit.source().typeHolder().getRegisteredName() + " " + hit.amount()).toList()
                        + ", boomerang latch " + contact.post().boomerang() + ", velocity " + contact.pre().delta() + " -> "
                        + contact.post().delta();
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow
                        && contact.pre().tag().getFloat("HamonDamage") == 0.0F && hits.stream().noneMatch(Hit::physical)
                        && cow.getHealth() == 50.0F && cow.invulnerableTime > 10,
                        "fixture: not one contact of Clackers without Hamon damage whose arrow hit the cooldown refused; " + seen);
                scene.check(hamonHits.size() == 1 && hamonHits.get(0).amount() == 0.0F && contact.post().boomerang(),
                        "1.16: Clackers without Hamon damage still deal their Hamon hit of zero, which a mob accepts, so the contact"
                                + " counts as a hit; " + seen);
                // 1.16 changeMovementAfterHit without a thrower, then this tick's drag and gravity
                scene.near(contact.post().delta(), ClackersScene.afterFlightTick(contact.pre().delta().multiply(-0.01D, -0.1D, -0.01D)),
                        "1.16: after that hit the Clackers drop as after any hit, not as after a refused one; velocity");
            });
        });
    }
}
