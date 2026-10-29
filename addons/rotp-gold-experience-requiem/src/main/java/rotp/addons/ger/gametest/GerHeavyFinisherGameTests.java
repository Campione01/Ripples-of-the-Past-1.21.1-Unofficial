package rotp.addons.ger.gametest;

import java.util.OptionalInt;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Hurt;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Knock;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.api.client.render.AbilitySelectionSurface;
import rotp.core.api.client.render.AbilitySelectionVisualQuery;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerHeavyFinisherGameTests {
    private GerHeavyFinisherGameTests() {}

    /**
     * Donor InitStands: ger_stand_heavy_punch has the core Gold Experience
     * Life Shot punch as its finisher, and a heavy press during that punch
     * serves the Gold Experience Life Shot on the punched target.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_heavy_finisher")
    public static void heavyFinisherIsLifeShotPunchWithLifeShotFollowUp(GameTestHelper helper) {
        new LifeShotFinisher(helper).start();
    }

    /**
     * 1.16 StandEntityHeavyAttack.getFinisherVariationIfPresent :96-110 gave
     * the base heavy back when the running Life Shot punch needed a part the
     * Stand lost; the base offered no Life Shot (StandAction
     * .replaceActionKostyl :83-115 keyed the follow-up by the Life Shot
     * punch), so the key was the base heavy, refused for the arms.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_heavy_finisher")
    public static void heavyKeyOffersNoLifeShotOnceTheLifeShotPunchLostItsArmsAsIn116(GameTestHelper helper) {
        new LifeShotPunchLostArms(helper).start();
    }

    private static OptionalInt selectionTint(Ability ability, ConditionCheck drawn) {
        return GoldExperienceRequiemAddon.lifeShotSelectionTint(new AbilitySelectionVisualQuery(
                ability, null, drawn, AbilitySelectionSurface.HOTBAR_SELECTED));
    }

    private static final class LifeShotPunchLostArms extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private int stage;

        LifeShotPunchLostArms(GameTestHelper helper) {
            super(helper, "heavy/finisher_lost_arms", 300);
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0.75F);
                    actor.stand().addFinisherMeter(0, 60);
                    stage = 1;
                }
                case 1 -> {
                    punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch", "lifeshot_punch",
                            InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                    AbilityInput.keyRelease(GerMeleeGameTestScene.RMB, actor.user());
                    stage = 2;
                }
                case 2 -> {
                    if (punch.getPhase() != ActionPhase.RECOVERY) {
                        helper.assertTrue(actor.stand().getCurStandAction() == punch,
                                scene.id + ": the Life Shot punch ended before its recovery");
                        return false;
                    }
                    Ability lifeShotPunch = actor.power().getAbility("lifeshot_punch");
                    scene.premise(lifeShotPunch.isPartRequired(StandPart.ARMS),
                            "the Life Shot punch needs the arms");
                    AbilityConditionCheck withArms = scene.resolve(actor, "ger_stand_heavy_punch");
                    scene.premise(withArms != null && withArms.ability.name().equals("lifeshot"),
                            "with its arms the heavy key is the Life Shot, not "
                                    + (withArms != null ? withArms.ability.name() : null));
                    StandInstance instance = actor.power().getStandInstance().orElseThrow();
                    scene.premise(instance.removePart(StandPart.ARMS), "the arms were taken");
                    scene.onClose(() -> instance.addPart(StandPart.ARMS));
                    stage = 3;
                }
                case 3 -> {
                    // the moves are resolved once a tick, so the key is asked on the tick after the arms went
                    scene.premise(actor.stand().getCurStandAction() == punch && !punch.isOver(),
                            "the Life Shot punch still runs without its arms");
                    StandInstance instance = actor.power().getStandInstance().orElseThrow();
                    try {
                        AbilityConditionCheck lostArms = scene.resolve(actor, "ger_stand_heavy_punch");
                        scene.log("lost arms: key=" + (lostArms != null ? lostArms.ability.name() : null)
                                + " check=" + (lostArms != null ? lostArms.conditionCheck : null));
                        helper.assertTrue(lostArms != null
                                        && lostArms.ability.name().equals("ger_stand_heavy_punch"),
                                scene.id + ": with the arms lost during the Life Shot punch the heavy key is "
                                        + (lostArms != null ? lostArms.ability.name() : null)
                                        + ", 1.16 gave the base heavy punch and no Life Shot");
                        helper.assertTrue(!lostArms.conditionCheck.isPositive(),
                                scene.id + ": the base heavy punch without arms is usable: "
                                        + lostArms.conditionCheck);
                    }
                    finally {
                        instance.addPart(StandPart.ARMS);
                    }
                    return true;
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }
    }

    private static final class LifeShotFinisher extends Sequence {
        private Actor actor;
        private ObservedCow cow;
        private EntityActionInstance punch;
        private int performTick = -1;
        private int recoveryTick = -1;
        private int stage;

        LifeShotFinisher(GameTestHelper helper) {
            super(helper, "heavy/finisher", 300);
        }

        @Override
        boolean step() {
            if (punch != null) {
                if (punch.getPhase() == ActionPhase.PERFORM && performTick < 0) {
                    performTick = tick;
                }
                if (punch.getPhase() == ActionPhase.RECOVERY && recoveryTick < 0) {
                    recoveryTick = tick;
                }
            }
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 1.5));
                    actor.stand().setFinisherMeter(0.75F);
                    actor.stand().addFinisherMeter(0, 60);
                    stage = 1;
                }
                case 1 -> {
                    Ability undie = actor.power().getAbility("ger_undie_punch");
                    helper.assertTrue(undie.isStandFinisherOf == null,
                            scene.id + ": the Undie is still a heavy finisher of "
                                    + undie.isStandFinisherOf);
                    scene.aim(actor, cow);
                    AbilityConditionCheck finisher = scene.resolve(actor, "ger_stand_heavy_punch");
                    helper.assertTrue(finisher != null
                                    && finisher.ability.abilityType == ModStandAbilities.GE_LIFESHOT_PUNCH.get(),
                            scene.id + ": the heavy at meter 0.75 resolves to "
                                    + (finisher != null ? finisher.ability.name() : null));
                    punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch", "lifeshot_punch",
                            InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                    AbilityInput.keyRelease(GerMeleeGameTestScene.RMB, actor.user());
                    Ability lifeShotPunch = actor.power().getAbility("lifeshot_punch");
                    helper.assertTrue(lifeShotPunch.getResolveLevelToUnlock() == 1,
                            scene.id + ": Life Shot punch Resolve " + lifeShotPunch.getResolveLevelToUnlock());
                    helper.assertTrue(punch.phasesLength.getFloat(ActionPhase.PERFORM) == 1,
                            scene.id + ": Life Shot punch PERFORM "
                                    + punch.phasesLength.getFloat(ActionPhase.PERFORM));
                    helper.assertTrue(GerPunchSoundProbe.heavyImpact(lifeShotPunch, new ActionTarget(cow)).value()
                                    == ModSoundEvents.GOLD_EXPERIENCE_PUNCH_HEAVY.get(),
                            scene.id + ": Life Shot punch impact sound");
                    helper.assertTrue(GerPunchSoundProbe.heavyCry(lifeShotPunch)
                                    == ModSoundEvents.GOLD_EXPERIENCE_MUDA_LONG.get(),
                            scene.id + ": Life Shot punch windup cry");
                    stage = 2;
                }
                case 2 -> {
                    if (punch.punchedTarget != null) {
                        verifyImpact();
                        stage = 3;
                    }
                }
                case 3 -> {
                    if (punch.getPhase() == ActionPhase.RECOVERY) {
                        helper.assertTrue(recoveryTick - performTick == 1,
                                scene.id + ": PERFORM lasted " + (recoveryTick - performTick) + " ticks");
                        // 1.16 GoldExperienceEntityLifeshot was a StandEntityActionModifier: selected and usable it
                        // was framed green (greenSelection); the core's rule keys the Gold Experience Stand's ids
                        AbilityConditionCheck lifeShot = scene.resolve(actor, "ger_stand_heavy_punch");
                        helper.assertTrue(lifeShot != null && lifeShot.ability.name().equals("lifeshot")
                                        && selectionTint(lifeShot.ability, lifeShot.drawnCondition(actor.user()))
                                                .equals(OptionalInt.of(0xFF00FF00)),
                                scene.id + ": 1.16 framed the selected Life Shot green while it was usable; the port"
                                        + " frames it "
                                        + (lifeShot != null
                                                ? selectionTint(lifeShot.ability, lifeShot.drawnCondition(actor.user()))
                                                : null));
                        helper.assertTrue(selectionTint(lifeShot.ability, ConditionCheck.NEGATIVE).isEmpty(),
                                scene.id + ": the selected Life Shot is framed green though it is not usable");
                        for (String other : new String[] { "ger_stand_heavy_punch", "ger_stand_punch" }) {
                            helper.assertTrue(selectionTint(actor.power().getAbility(other), ConditionCheck.POSITIVE)
                                            .isEmpty(),
                                    scene.id + ": the selected " + other + " is framed green by the Life Shot rule");
                        }
                        // Sensory Overload does not apply to a mob without AI.
                        cow.setNoAi(false);
                        scene.press(actor, cow, "ger_stand_heavy_punch", "lifeshot",
                                InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                        stage = 4;
                    }
                }
                case 4 -> {
                    if (cow.hasEffect(ModStatusEffects.SENSORY_OVERLOAD)) {
                        return true;
                    }
                    helper.assertTrue(actor.stand().getCurStandAction() == punch,
                            scene.id + ": the Life Shot punch ended without applying the Life Shot");
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }

        private void verifyImpact() {
            scene.log("hurts=" + cow.hurts + " knocks=" + cow.knocks
                    + " heard=" + scene.positionedSounds + " standSounds=" + actor.standSounds());
            Hurt hurt = cow.hurts.isEmpty() ? null : cow.hurts.get(0);
            helper.assertTrue(hurt != null && hurt.result(), scene.id + ": the punch hurt the cow");
            helper.assertTrue(Math.abs(hurt.addedKnockback()) < GerMeleeGameTestScene.EPS
                            && cow.detached().size() == 2
                            && cow.detached().get(0).strength() > 0
                            && cow.detached().stream().map(Knock::strength).distinct().count() == 1,
                    scene.id + ": Life Shot punch knockback is not the donor's two detached pulses "
                            + cow.knocks);
            helper.assertTrue(scene.heard(ModSoundEvents.GOLD_EXPERIENCE_PUNCH_HEAVY),
                    scene.id + ": no Gold Experience heavy impact was broadcast");
            helper.assertTrue(actor.standSounds().stream().anyMatch(sound ->
                            sound.value() == ModSoundEvents.GOLD_EXPERIENCE_PUNCH_HEAVY_EXTRA.get()),
                    scene.id + ": no Gold Experience PERFORM sound was played");
        }
    }
}
