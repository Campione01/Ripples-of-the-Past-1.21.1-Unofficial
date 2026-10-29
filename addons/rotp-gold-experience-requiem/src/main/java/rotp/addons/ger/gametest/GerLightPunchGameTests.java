package rotp.addons.ger.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Hurt;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Knock;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerLightPunchGameTests {
    private GerLightPunchGameTests() {}

    /**
     * Donor StandEntityPunch.doAttack: the guard counter is not folded into
     * the hurt; it pushes twice by half after a successful hurt.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_light_counter")
    public static void counterPunchPushesWithDetachedDonorPulses(GameTestHelper helper) {
        new CounterPunch(helper).start();
    }

    /**
     * Donor StandEntityLightAttack: PERFORM and RECOVERY read the speed and the
     * finisher meter when they start, and the hit itself raises the meter.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_light_timing")
    public static void lightPunchRecoveryReadsTimingAtEntry(GameTestHelper helper) {
        new EntryTiming(helper).start();
    }

    private static final class EntryTiming extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private float startRecovery;
        private int stage;

        EntryTiming(GameTestHelper helper) {
            super(helper, "light/timing", 300);
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0.2F);
                    actor.stand().addFinisherMeter(0, 60);
                    stage = 1;
                }
                case 1 -> {
                    float meter = actor.stand().getFinisherMeter();
                    punch = scene.pressAction(actor, cow, "ger_stand_punch",
                            "ger_stand_punch", InputMethod.CLICK, GerMeleeGameTestScene.LMB);
                    startRecovery = punch.phasesLength.getFloat(ActionPhase.RECOVERY);
                    scene.premise(startRecovery == 4 * StandStatFormulas.getLightAttackRecovery(
                                    actor.stand().getAttackSpeed(), meter),
                            "admission cached RECOVERY " + startRecovery + " from meter " + meter);
                    stage = 2;
                }
                case 2 -> {
                    if (punch.getPhase() == ActionPhase.RECOVERY) {
                        scene.premise(!actor.stand().isArmsOnlyMode() && !cow.hurts.isEmpty(),
                                "full-body punch landed");
                        float expected = 4 * StandStatFormulas.getLightAttackRecovery(
                                actor.stand().getAttackSpeed(), actor.stand().getFinisherMeter());
                        scene.log("meter=" + actor.stand().getFinisherMeter() + " start="
                                + startRecovery + " expected=" + expected + " actual="
                                + punch.phasesLength.getFloat(ActionPhase.RECOVERY));
                        scene.premise(expected != startRecovery,
                                "the hit changed the entry RECOVERY");
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

    /**
     * Donor InitStands: the light punch sound GER_PUNCH is its impact, and the
     * donor punch has no cry that would play the same clip again.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_light_sound")
    public static void lightPunchImpactIsGerPunchWithoutCry(GameTestHelper helper) {
        new ImpactSound(helper).start();
    }

    private static final class ImpactSound extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private int stage;

        ImpactSound(GameTestHelper helper) {
            super(helper, "light/sound", 300);
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
                    punch = scene.pressAction(actor, cow, "ger_stand_punch",
                            "ger_stand_punch", InputMethod.CLICK, GerMeleeGameTestScene.LMB);
                    stage = 2;
                }
                case 2 -> {
                    if (punch.punchedTarget != null) {
                        Ability ability = actor.power().getAbility("ger_stand_punch");
                        scene.log("heard=" + scene.positionedSounds);
                        helper.assertTrue(!cow.hurts.isEmpty()
                                        && scene.heard(GoldExperienceRequiemAddon.GER_PUNCH_SOUND)
                                        && !scene.heard(GoldExperienceRequiemAddon.PUNCH_HIT_SOUND),
                                scene.id + ": the landed punch did not sound GER_PUNCH alone "
                                        + scene.positionedSounds);
                        helper.assertTrue(GerPunchSoundProbe.lightImpact(ability, new ActionTarget(cow)).value()
                                        == GoldExperienceRequiemAddon.GER_PUNCH_SOUND.get(),
                                scene.id + ": light impact sound");
                        SoundEvent cry = GerPunchSoundProbe.lightCry(ability, actor.stand());
                        helper.assertTrue(cry == null,
                                scene.id + ": the light punch also cries " + cry);
                        return true;
                    }
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }
    }

    private static final class CounterPunch extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private Cow attacker;
        private EntityActionInstance punch;
        private float counter;
        private int stage;

        CounterPunch(GameTestHelper helper) {
            super(helper, "light/counter", 360);
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    attacker = scene.add(new Cow(EntityType.COW, scene.level),
                            scene.origin.add(0, 0, 3));
                    attacker.setNoAi(true);
                    // An attacker with an integrated Stand can hit Stands.
                    attacker.addEffect(new MobEffectInstance(
                            ModStatusEffects.INTEGRATED_STAND, 600));
                    actor.stand().setFinisherMeter(0);
                    stage = 1;
                }
                case 1 -> {
                    // The body only redirects damage once spawn protection is over.
                    if (actor.user().tickCount >= 65) {
                        scene.press(actor, cow, "guard", "guard", InputMethod.HOLD,
                                GerMeleeGameTestScene.GUARD);
                        stage = 2;
                    }
                }
                case 2 -> {
                    if (actor.stand().isStandBlocking()) {
                        actor.stand().hurt(scene.level.damageSources().mobAttack(attacker), 8);
                        scene.premise(actor.stand().guardCounter() > 0,
                                "a blocked hit charged the guard counter");
                        stage = 3;
                    }
                }
                case 3 -> {
                    punch = scene.pressAction(actor, cow, "ger_stand_punch",
                            "ger_stand_punch", InputMethod.CLICK, GerMeleeGameTestScene.LMB);
                    AbilityInput.keyRelease(GerMeleeGameTestScene.GUARD, actor.user());
                    counter = actor.stand().guardCounter();
                    scene.premise(counter > 0, "the guard-to-punch switch kept the counter");
                    stage = 4;
                }
                case 4 -> {
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
            scene.log("counter=" + counter + " hurts=" + cow.hurts + " knocks=" + cow.knocks);
            Hurt hurt = cow.hurts.isEmpty() ? null : cow.hurts.get(0);
            helper.assertTrue(hurt != null && hurt.result(), scene.id + ": the punch hurt the cow");
            helper.assertTrue(Math.abs(hurt.addedKnockback()) < GerMeleeGameTestScene.EPS,
                    scene.id + ": the counter was folded into the hurt, added knockback="
                            + hurt.addedKnockback());
            helper.assertTrue(cow.detached().size() == 2,
                    scene.id + ": expected 2 detached counter pulses, got " + cow.detached());
            for (Knock knock : cow.detached()) {
                helper.assertTrue(Math.abs(knock.strength() - counter * 0.5) < GerMeleeGameTestScene.EPS,
                        scene.id + ": pulse " + knock.strength() + " is not half the counter " + counter);
            }
        }
    }
}
