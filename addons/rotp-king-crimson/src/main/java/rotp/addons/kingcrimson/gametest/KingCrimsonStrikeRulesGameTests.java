package rotp.addons.kingcrimson.gametest;

import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.CHOP;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.EYEJAB;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.GROUND_PUNCH;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.GROUND_SLAM;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.IMPALE;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.base;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.prepare;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.MathUtil;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonStrikeRulesGameTests {
    private KingCrimsonStrikeRulesGameTests() {}

    // 1.16 KingCrimsonHeavyPunch.replaceAction returns Ground Slam for an absent or arms-only Stand before the
    // finisher variation (Ground Punch, with its Eye Jab check) is consulted.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_heavy_selection")
    public static void armsOnlyHeavyKeyAlwaysGivesGroundSlam(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonStrikeRulesGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        record Case(String id, boolean armsOnly, float health, float stamina, boolean eyejabCooldown, String expected) {}
        for (Case value : List.of(
                new Case("arms-only/low-health/short-stamina", true, 60, 100, false, GROUND_SLAM),
                new Case("arms-only/low-health", true, 60, -1, false, GROUND_SLAM),
                new Case("arms-only/full-health", true, 200, -1, false, GROUND_SLAM),
                new Case("full/low-health", false, 60, -1, false, EYEJAB),
                new Case("full/low-health/eyejab-cooldown", false, 60, -1, true, GROUND_PUNCH))) {
            String id = "S1/" + value.id();
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                if (value.armsOnly()) scene.stand.setArmsOnlyMode(true, true);
                scene.user.setHealth(value.health());
                if (value.stamina() >= 0) scene.power.setStamina(value.stamina());
                if (value.eyejabCooldown()) scene.power.setAbilityCooldown(EYEJAB, 100);
                scene.stand.setFinisherMeter(0.75F);
                var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
                var selected = scene.power.updateAvailableMoves().getContextVariationContainer(scene.power.getAbility("heavy_punch"));
                String name = selected != null ? selected.ability.name() : null;
                checks.check(value.expected().equals(name), id + " heavy key selects " + name + " expected=" + value.expected());
                if (value.expected().equals(name)) {
                    EntityActionInstance action = scene.press(cow, "heavy_punch", value.expected());
                    checks.check(action.ability == selected.ability && scene.stand.isArmsOnlyMode() == value.armsOnly(),
                            id + " the press starts it without summoning the full body from the arms");
                }
            }
        }
        checks.finish(helper, "KC-HEAVY-SELECTION", 5);
    }

    // 1.16 PowerBaseImpl.performAction sets the cooldown when the click is admitted; StandEntityTask leaves it at PERFORM.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_strike_cooldown")
    public static void strikeCooldownStartsOnTheKeyPress(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonStrikeRulesGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        int scenes = 0;
        for (String ability : List.of(CHOP, EYEJAB, IMPALE)) {
            for (boolean interrupted : new boolean[] {false, true}) {
                String id = "S2/" + ability + (interrupted ? "/unsummoned-in-windup" : "/performed");
                try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                    prepare(scene, ability);
                    var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
                    EntityActionInstance action = scene.press(cow, base(ability), ability);
                    int configured = ((Ability) action.ability).getCooldown(scene.power, -1);
                    scene.premise(configured > 0, "Configured cooldown " + configured);
                    checks.check(action.getPhase() == ActionPhase.WINDUP && scene.power.getAbilityCooldown(ability) == configured,
                            id + " the accepted press commits the cooldown=" + scene.power.getAbilityCooldown(ability)
                                    + " expected=" + configured + " in " + action.getPhase());
                    if (interrupted) {
                        scene.type.forceUnsummon(scene.user, scene.power);
                        scene.premise(!scene.power.isSummoned() && action.getPhase() != ActionPhase.PERFORM, "Unsummoned during the windup");
                        checks.check(scene.power.getAbilityCooldown(ability) == configured,
                                id + " a strike stopped in its windup keeps the cooldown=" + scene.power.getAbilityCooldown(ability));
                    }
                    else {
                        int marked = configured - 7;
                        scene.power.setAbilityCooldown(ability, marked);
                        for (int tick = 0; action.punchedTarget == null && !action.isOver() && tick < 100; tick++) action._tickAction();
                        scene.premise(action.punchedTarget != null, "Performed");
                        checks.check(scene.power.getAbilityCooldown(ability) == marked,
                                id + " PERFORM does not set the cooldown again: " + scene.power.getAbilityCooldown(ability) + " expected=" + marked);
                    }
                }
                scenes++;
            }
        }
        checks.finish(helper, "KC-STRIKE-COOLDOWN", scenes);
    }

    // 1.16 KingCrimsonModel registers one pose per strike and never mirrors it by the swinging arm.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_strike_clip")
    public static void strikeClipsAreNotMirrored(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonStrikeRulesGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        for (String ability : List.of(CHOP, IMPALE, EYEJAB, GROUND_PUNCH, GROUND_SLAM)) {
            String id = "S3/" + ability;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, ability);
                var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
                EntityActionInstance action = scene.press(cow, base(ability), ability);
                var anim = action.ability.getEntityAnim(action);
                boolean handed = action instanceof StandEntityPunch punch && punch.usesHandedAnimation();
                String hand = action instanceof StandEntityPunch punch ? String.valueOf(punch.getPunchingHand()) : "none";
                checks.check(!handed && anim.name().equals(ability), id + " clip=" + anim + " handed=" + handed + " hand=" + hand);
            }
        }
        checks.finish(helper, "KC-STRIKE-CLIP", 5);
    }

    // 1.16 StandEntityLightAttack: +0.1 at perform, and the punch's addFinisher 0.1 on every entity attempt
    // (StandEntityPunch.doHit), hurt or not. The heavy strikes build nothing.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_strike_finisher")
    public static void lightStrikesGainFinisherOnEveryEntityAttempt(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonStrikeRulesGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        enum Target { HURT, CANCELED, IMMUNE, BOAT }
        record Case(String ability, Target target) {}
        List<Case> cases = List.of(new Case(CHOP, Target.HURT), new Case(CHOP, Target.CANCELED), new Case(CHOP, Target.IMMUNE),
                new Case(CHOP, Target.BOAT), new Case(IMPALE, Target.CANCELED), new Case(GROUND_SLAM, Target.CANCELED),
                new Case(GROUND_PUNCH, Target.CANCELED));
        for (Case value : cases) {
            String id = "S4/" + value.ability() + "/" + value.target();
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, value.ability());
                Entity target;
                if (value.target() == Target.BOAT) target = scene.boat(scene.origin.add(0, 0, 1.5));
                else {
                    var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
                    if (value.target() == Target.IMMUNE) {
                        DamageSource preparation = new DamageSource(DamageUtil.type(scene.level, ModDamageTypes.STAND_ATTACK), scene.stand, scene.user);
                        ((DamageSourceModified) preparation).jojo_ripples$setStandInvulTicks(40);
                        scene.premise(scene.stand.hurtWithStandAttack(cow, preparation, 1) && StandEntity.isStandAttackInvulnerable(cow),
                                "Raw Stand immunity before the strike");
                        scene.hits.clear();
                    }
                    if (value.target() == Target.CANCELED) scene.listen(LivingIncomingDamageEvent.class, event -> {
                        if (event.getEntity() == cow) event.setCanceled(true);
                    });
                    target = cow;
                }
                EntityActionInstance action = scene.press(target, base(value.ability()), value.ability());
                for (int tick = 0; action.getPhase() == ActionPhase.WINDUP && tick < 100; tick++) action._tickAction();
                scene.premise(action.getPhase() == ActionPhase.PERFORM && action.punchedTarget == null, "Reached PERFORM");
                float before = scene.stand.getFinisherMeter();
                action._tickAction();
                scene.premise(action.punchedTarget != null && action.punchedTarget.getMainEntity() == target, "Performed on the target");
                float gain = scene.stand.getFinisherMeter() - before;
                float expected = value.ability().equals(GROUND_PUNCH) ? 0 : 0.2F;
                checks.check(KingCrimsonGroundGameTestFixture.near(gain, expected), id + " finisher gain=" + gain + " expected=" + expected);
            }
        }
        checks.finish(helper, "KC-STRIKE-FINISHER", cases.size());
    }

    // 1.16 StandEntityLightAttack / StandEntityHeavyAttack.standPerform -> StandEntity.punch -> aimWithThisOrUser
    // (:1695-1700): before the hit the Stand turned toward the eyes of what the strike aimed at, its yaw and pitch each by
    // at most attackSpeed / 16 x 18 degrees, its head onto the new yaw; with nothing in reach, toward what its user's
    // precision ray met, which the strike then missed.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_strike_turn")
    public static void strikesTurnTheStandTowardWhatTheyStrikeAsIn116(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonStrikeRulesGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();
        record Case(String ability, boolean beyondReach) {}
        List<Case> cases = List.of(new Case(CHOP, false), new Case(IMPALE, false), new Case(GROUND_SLAM, false),
                new Case(GROUND_PUNCH, false), new Case(EYEJAB, false), new Case(CHOP, true));
        for (Case value : cases) {
            String id = "S5/" + value.ability() + (value.beyondReach() ? "/beyond-reach" : "/in-reach");
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, value.ability());
                Vec3 side = new Vec3(0.6, 0, 1).normalize();
                double reach = scene.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                var cow = scene.cow(scene.origin.add(side.scale(value.beyondReach() ? reach + 1.0 : 1.5)), 200);
                ActionTarget pressed = new ActionTarget(cow);
                if (value.beyondReach()) {
                    scene.aimUserAt(cow.getEyePosition());
                    scene.premise(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getEntity() == cow
                            && StandEntityPunchAbility.validatePunchTarget(scene.stand, pressed).isEmpty(scene.level),
                            "The user's precision ray meets the cow beyond the Stand's reach " + reach);
                    pressed = ActionTarget.EMPTY;
                }
                EntityActionInstance action = scene.press(pressed, base(value.ability()), value.ability());
                for (int tick = 0; action.getPhase() == ActionPhase.WINDUP && tick < 100; tick++) action._tickAction();
                scene.premise(action.getPhase() == ActionPhase.PERFORM && action.punchedTarget == null, "Reached PERFORM");
                StandEntity stand = scene.stand;
                Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition());
                float aimYaw = MathUtil.yRotDegFromVec(toEyes);
                float aimPitch = MathUtil.xRotDegFromVec(toEyes);
                stand.setYRot(aimYaw + 60);
                stand.setXRot(aimPitch + 40);
                stand.setYHeadRot(aimYaw + 60);
                float yaw0 = stand.getYRot();
                float pitch0 = stand.getXRot();
                float max = (float) stand.getAttackSpeed() / 16 * 18;
                scene.premise(max > 0 && max < 40, "The Stand turns by up to " + max + " degrees");
                float yaw = yaw0 + Mth.clamp(Mth.degreesDifference(yaw0, aimYaw), -max, max);
                float pitch = pitch0 + Mth.clamp(Mth.degreesDifference(pitch0, aimPitch), -max, max);
                float[] atHit = {Float.NaN, Float.NaN};
                scene.listen(LivingIncomingDamageEvent.class, event -> {
                    if (event.getEntity() == cow && Float.isNaN(atHit[0])) {
                        atHit[0] = stand.getYRot();
                        atHit[1] = stand.getXRot();
                    }
                });
                action._tickAction();
                scene.premise(action.punchedTarget != null, "Performed");
                if (value.beyondReach()) {
                    scene.premise(Float.isNaN(atHit[0]) && cow.getHealth() == cow.getMaxHealth(), "The strike missed the cow");
                }
                else {
                    scene.premise(action.punchedTarget.getMainEntity() == cow && !Float.isNaN(atHit[0]), "Struck the cow");
                    checks.check(near(atHit[0], yaw) && near(atHit[1], pitch), id + " 1.16 turned the Stand (yaw " + yaw0
                            + ", pitch " + pitch0 + ", up to " + max + " degrees) toward the cow's eyes before the hit, to yaw "
                            + yaw + ", pitch " + pitch + "; it struck at yaw " + atHit[0] + ", pitch " + atHit[1]);
                }
                checks.check(near(stand.getYRot(), yaw) && near(stand.getXRot(), pitch) && near(stand.getYHeadRot(), yaw),
                        id + " 1.16 turned the Stand (yaw " + yaw0 + ", pitch " + pitch0 + ", up to " + max
                                + " degrees) toward the eyes of the cow " + (value.beyondReach() ? "its user aimed at" : "it struck")
                                + ", to yaw " + yaw + ", pitch " + pitch + ", head " + yaw + "; it is at yaw " + stand.getYRot()
                                + ", pitch " + stand.getXRot() + ", head " + stand.getYHeadRot());
            }
        }
        checks.finish(helper, "KC-STRIKE-TURN", cases.size());
    }

    private static boolean near(float angle, float expected) {
        return Math.abs(Mth.degreesDifference(angle, expected)) < 1.0E-3F;
    }
}
