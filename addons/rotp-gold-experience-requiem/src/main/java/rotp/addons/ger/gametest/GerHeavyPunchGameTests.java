package rotp.addons.ger.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Hurt;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Knock;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.customobjects.ObjectEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;
import rotp.core.impl.stands.goldexperience.GoldExperienceToothLifeformAbility;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerHeavyPunchGameTests {
    private GerHeavyPunchGameTests() {}

    /**
     * Donor HeavyPunchInstance through StandEntityPunch.doAttack: the heavy
     * knockback is not folded into the hurt; it pushes twice by half.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_heavy_pulses")
    public static void heavyPunchPushesWithDetachedDonorPulses(GameTestHelper helper) {
        new DetachedPulses(helper, "heavy/pulses", "ger_stand_heavy_punch",
                GerMeleeGameTestScene.RMB).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_undie_pulses")
    public static void undiePunchPushesWithDetachedDonorPulses(GameTestHelper helper) {
        new DetachedPulses(helper, "undie/pulses", "ger_undie_punch",
                GerMeleeGameTestScene.SPECIAL).start();
    }

    /**
     * Donor InitStands: GER_HEAVY_PUNCH is the impact of the heavy and the
     * Undie, and neither has a cry that would play the same clip again.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_heavy_sound")
    public static void heavyPunchImpactIsGerHeavyPunchWithoutCry(GameTestHelper helper) {
        new ImpactSound(helper, "heavy/sound", "ger_stand_heavy_punch",
                GerMeleeGameTestScene.RMB).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_undie_sound")
    public static void undiePunchImpactIsGerHeavyPunchWithoutCry(GameTestHelper helper) {
        new ImpactSound(helper, "undie/sound", "ger_undie_punch",
                GerMeleeGameTestScene.SPECIAL).start();
    }

    private static final class ImpactSound extends Sequence {
        private final String abilityName;
        private final short key;
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private int stage;

        ImpactSound(GameTestHelper helper, String id, String abilityName, short key) {
            super(helper, id, 300);
            this.abilityName = abilityName;
            this.key = key;
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0);
                    stage = 1;
                }
                case 1 -> {
                    punch = scene.pressAction(actor, cow, abilityName, abilityName,
                            InputMethod.CLICK, key);
                    stage = 2;
                }
                case 2 -> {
                    if (punch.punchedTarget != null) {
                        Ability ability = actor.power().getAbility(abilityName);
                        scene.log("heard=" + scene.positionedSounds);
                        helper.assertTrue(!cow.hurts.isEmpty()
                                        && scene.heard(GoldExperienceRequiemAddon.GER_HEAVY_PUNCH_SOUND)
                                        && !scene.heard(GoldExperienceRequiemAddon.PUNCH_HIT_SOUND),
                                scene.id + ": the landed punch did not sound GER_HEAVY_PUNCH alone "
                                        + scene.positionedSounds);
                        helper.assertTrue(GerPunchSoundProbe.heavyImpact(ability, new ActionTarget(cow)).value()
                                        == GoldExperienceRequiemAddon.GER_HEAVY_PUNCH_SOUND.get(),
                                scene.id + ": heavy impact sound");
                        // Without a cry of its own the core falls back to its silent heavy cry.
                        SoundEvent cry = GerPunchSoundProbe.heavyCry(ability);
                        helper.assertTrue(cry == ModSoundEvents.STAND_PUNCH_HEAVY_CRY.get(),
                                scene.id + ": the heavy also cries " + cry);
                        return true;
                    }
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }
    }

    /**
     * Donor StandEntityHeavyAttack: RECOVERY reads the attack speed when it
     * starts, for GER's heavy action subclass too (recoveryPhaseEntryTiming).
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_heavy_recovery")
    public static void heavyPunchRecoveryReadsSpeedAtEntry(GameTestHelper helper) {
        new RecoveryEntry(helper).start();
    }

    private static final class RecoveryEntry extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private float startRecovery;
        private int stage;

        RecoveryEntry(GameTestHelper helper) {
            super(helper, "heavy/recovery", 300);
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0);
                    stage = 1;
                }
                case 1 -> {
                    punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch",
                            "ger_stand_heavy_punch", InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                    startRecovery = punch.phasesLength.getFloat(ActionPhase.RECOVERY);
                    double startSpeed = actor.stand().getAttackSpeed();
                    // A speed change during the windup, as a stamina or Resolve change would make.
                    actor.user().getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(5);
                    StandEntity.updateStandStatAttributes(actor.stand(), actor.user());
                    actor.stand().updateStrengthMultipliers();
                    scene.premise(actor.stand().getAttackSpeed() != startSpeed,
                            "the attack speed changed during the windup");
                    stage = 2;
                }
                case 2 -> {
                    if (punch.getPhase() == ActionPhase.RECOVERY) {
                        float expected = StandStatFormulas.getHeavyAttackRecovery(
                                actor.stand().getAttackSpeed(),
                                ((StandEntityHeavyPunch) punch).finisherValue);
                        scene.log("start=" + startRecovery + " expected=" + expected + " actual="
                                + punch.phasesLength.getFloat(ActionPhase.RECOVERY));
                        scene.premise(expected != startRecovery, "the speed change moves RECOVERY");
                        helper.assertTrue(punch.phasesLength.getFloat(ActionPhase.RECOVERY) == expected
                                        && punch.getCurPhaseLength() == expected,
                                scene.id + ": RECOVERY " + punch.getCurPhaseLength()
                                        + " was not read at entry, expected " + expected);
                        return true;
                    }
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }
    }

    /** Donor StandEntityAction builder default: the GER heavy PERFORM is 1 tick. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_heavy_perform")
    public static void heavyPunchPerformLastsOneTick(GameTestHelper helper) {
        new HeavyPerform(helper).start();
    }

    private static final class HeavyPerform extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private int performTick = -1;
        private int stage;

        HeavyPerform(GameTestHelper helper) {
            super(helper, "heavy/perform", 300);
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0);
                    stage = 1;
                }
                case 1 -> {
                    punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch",
                            "ger_stand_heavy_punch", InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                    helper.assertTrue(punch.phasesLength.getFloat(ActionPhase.PERFORM) == 1,
                            scene.id + ": heavy PERFORM "
                                    + punch.phasesLength.getFloat(ActionPhase.PERFORM));
                    stage = 2;
                }
                case 2 -> {
                    if (punch.getPhase() == ActionPhase.PERFORM && performTick < 0) {
                        performTick = tick;
                    }
                    if (punch.getPhase() == ActionPhase.RECOVERY) {
                        helper.assertTrue(performTick > 0 && tick - performTick == 1,
                                scene.id + ": PERFORM lasted " + (tick - performTick) + " ticks");
                        helper.assertTrue(punch.punchedTarget != null && !cow.hurts.isEmpty(),
                                scene.id + ": the 1-tick PERFORM still landed the punch");
                        return true;
                    }
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }
    }

    /**
     * Donor InitStands: ger_stand_heavy_punch is a plain StandEntityHeavyAttack,
     * not the Gold Experience heavy punch, so it knocks no tooth out of a
     * toothed mob and GER has no tooth-lifeform follow-up to offer for it.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_heavy_tooth")
    public static void heavyPunchKnocksOutNoToothAndOffersNoToothLifeform(GameTestHelper helper) {
        new NoTooth(helper).start();
    }

    private static final class NoTooth extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private int stage;

        NoTooth(GameTestHelper helper) {
            super(helper, "heavy/tooth", 300);
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0);
                    scene.premise(GoldExperienceToothLifeformAbility.isToothSourceTarget(cow),
                            "a cow is a toothed species");
                    scene.premise(actor.power().isAbilityUnlocked(
                                    GoldExperienceToothLifeformAbility.CREATE_LIFEFORM_ABILITY),
                            "Create Lifeform is unlocked, so a tooth follow-up would be offered");
                    stage = 1;
                }
                case 1 -> {
                    punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch",
                            "ger_stand_heavy_punch", InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                    stage = 2;
                }
                case 2 -> {
                    if (punch.getPhase() == ActionPhase.RECOVERY) {
                        scene.premise(punch.punchedTarget != null && !cow.hurts.isEmpty()
                                        && cow.hurts.get(0).result(),
                                "the heavy punch landed and hurt the cow");
                        List<ObjectEntity> teeth = scene.level.getEntitiesOfClass(ObjectEntity.class,
                                new AABB(scene.origin, scene.origin).inflate(8));
                        scene.log("teeth=" + teeth + " punch=" + punch.getClass().getSimpleName());
                        helper.assertTrue(teeth.isEmpty(),
                                scene.id + ": the heavy punch knocked out a tooth " + teeth);
                        helper.assertTrue(!(punch instanceof ToothKnockingHeavyPunch),
                                scene.id + ": the heavy punch is a tooth-knocking action");
                        helper.assertTrue(actor.power().getMoveset().getAbility("tooth_lifeform") == null,
                                scene.id + ": GER registers a tooth-lifeform ability");
                        AbilityConditionCheck heavy = scene.resolve(actor, "ger_stand_heavy_punch");
                        helper.assertTrue(heavy != null && heavy.ability.name().equals("ger_stand_heavy_punch"),
                                scene.id + ": the heavy input resolves to "
                                        + (heavy != null ? heavy.ability.name() : null) + " in recovery");
                        return true;
                    }
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }
    }

    private static final class DetachedPulses extends Sequence {
        private final String abilityName;
        private final short key;
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private int stage;

        DetachedPulses(GameTestHelper helper, String id, String abilityName, short key) {
            super(helper, id, 300);
            this.abilityName = abilityName;
            this.key = key;
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0);
                    stage = 1;
                }
                case 1 -> {
                    punch = scene.pressAction(actor, cow, abilityName, abilityName,
                            InputMethod.CLICK, key);
                    stage = 2;
                }
                case 2 -> {
                    if (punch.punchedTarget != null) {
                        verify();
                        return true;
                    }
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }

        private void verify() {
            float finisher = ((StandEntityHeavyPunch) punch).finisherValue;
            double half = StandStatFormulas.getHeavyAttackKnockback(
                    actor.stand().getAttackDamage(), finisher) * 0.5;
            scene.log("half=" + half + " hurts=" + cow.hurts + " knocks=" + cow.knocks);
            Hurt hurt = cow.hurts.isEmpty() ? null : cow.hurts.get(0);
            helper.assertTrue(hurt != null && hurt.result(), scene.id + ": the punch hurt the cow");
            helper.assertTrue(Math.abs(hurt.addedKnockback()) < GerMeleeGameTestScene.EPS,
                    scene.id + ": the heavy knockback was folded into the hurt, added knockback="
                            + hurt.addedKnockback());
            helper.assertTrue(cow.detached().size() == 2,
                    scene.id + ": expected 2 detached heavy pulses, got " + cow.detached());
            for (Knock knock : cow.detached()) {
                helper.assertTrue(Math.abs(knock.strength() - half) < GerMeleeGameTestScene.EPS,
                        scene.id + ": pulse " + knock.strength() + " is not half of " + half * 2);
            }
        }
    }
}
