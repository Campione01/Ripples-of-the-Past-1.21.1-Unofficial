package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.entity.GerBeamEntity;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.impl.stands.goldexperience.GoldExperienceCreateLifeformAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

/**
 * The 1.16 framework never gated a Stand action on stamina: StandEntityTask
 * paid the cost at perform and ignored the result, and a short bar emptied.
 * Only an ability's own code checked stamina.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerStaminaGameTests {
    private GerStaminaGameTests() {}

    static String warningKey(ConditionCheck check) {
        Component warning = check.getWarning();
        return warning != null && warning.getContents() instanceof TranslatableContents message
                ? message.getKey()
                : String.valueOf(warning);
    }

    static boolean sentStaminaMessage(Actor actor) {
        return actor.received().stream().anyMatch(packet -> packet instanceof ClientboundSystemChatPacket chat
                && chat.content().getContents() instanceof TranslatableContents message
                && message.getKey().contains("stamina"));
    }

    /** 1.16 UndiePunch had no stamina condition; its 100 cost drains a short bar to 0. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_stamina_undie")
    public static void undieStartsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        new Sequence(helper, "stamina/undie", 150) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance punch;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = scene.cow(scene.origin.add(0, 0, 1.5));
                        actor.power().setStamina(30);
                        scene.premise(!actor.power().isStaminaInfinite(), "stamina is in use");
                        stage = 1;
                    }
                    case 1 -> {
                        punch = scene.pressAction(actor, cow, "ger_undie_punch", "ger_undie_punch",
                                InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        stage = 2;
                    }
                    case 2 -> {
                        if (punch.getPhase() == ActionPhase.WINDUP) {
                            // the bar the punch meets, whatever it regenerates meanwhile
                            actor.power().setStamina(30);
                        }
                        if (punch.punchedTarget != null) {
                            scene.log("stamina=" + actor.power().getStamina() + " hurts=" + cow.hurts);
                            helper.assertTrue(!cow.hurts.isEmpty(), scene.id + ": the Undie did not land");
                            helper.assertTrue(actor.power().getStamina() < 1,
                                    scene.id + ": the bar kept " + actor.power().getStamina());
                            helper.assertTrue(!sentStaminaMessage(actor), scene.id + ": a stamina message was sent");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /**
     * 1.16 Disabler.checkSpecificConditions refused a bar under 100 with
     * no_stamina, in Resolve too; its standPerform then paid silently and
     * disabled nothing when the bar had emptied since the press.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_stamina_disabler")
    public static void disablerKeepsTheDonorStaminaCondition(GameTestHelper helper) {
        new Sequence(helper, "stamina/disabler", 150) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance disabler;
            private final List<String> phases = new ArrayList<>();
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = scene.cow(scene.origin.add(0, 0, 4));
                        StandEntityAbility ability = (StandEntityAbility) actor.power().getAbility("ger_disabler");
                        actor.power().setStamina(50);
                        ConditionCheck shortBar = ability.checkSpecificConditions(actor.power());
                        actor.user().addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0));
                        ConditionCheck inResolve = ability.checkSpecificConditions(actor.power());
                        actor.user().removeEffect(ModStatusEffects.RESOLVE);
                        actor.power().setStamina(100);
                        ConditionCheck paid = ability.checkSpecificConditions(actor.power());
                        scene.log("short=" + warningKey(shortBar) + " resolve=" + warningKey(inResolve)
                                + " paid=" + paid.isPositive());
                        helper.assertTrue(!shortBar.isPositive()
                                        && warningKey(shortBar).equals("jojo.message.action_condition.no_stamina"),
                                scene.id + ": 50 stamina gave " + warningKey(shortBar));
                        helper.assertTrue(!inResolve.isPositive(),
                                scene.id + ": Resolve lifted the Disabler's stamina condition");
                        helper.assertTrue(paid.isPositive(), scene.id + ": 100 stamina was refused");
                        stage = 1;
                    }
                    case 1 -> {
                        disabler = scene.pressAction(actor, null, "ger_disabler", "ger_disabler",
                                InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        AbilityInput.keyRelease(GerMeleeGameTestScene.SPECIAL, actor.user());
                        stage = 2;
                    }
                    case 2 -> {
                        phases.add(String.valueOf(disabler.getPhase()));
                        if (disabler.getPhase() == ActionPhase.WINDUP) {
                            actor.power().setStamina(0);
                        }
                        else if (disabler.getPhase() == ActionPhase.RECOVERY
                                || actor.stand().getCurStandAction() != disabler) {
                            scene.log("phases=" + phases + " current=" + actor.stand().getCurStandAction()
                                    + " stamina=" + actor.power().getStamina()
                                    + " disabled=" + GoldExperienceRequiemAddon.livingState(cow).disablerActive());
                            helper.assertTrue(phases.contains("PERFORM"),
                                    scene.id + ": the Disabler ended without a perform: " + phases);
                            helper.assertTrue(!GoldExperienceRequiemAddon.livingState(cow).disablerActive(),
                                    scene.id + ": an emptied bar still disabled the cow");
                            helper.assertTrue(!sentStaminaMessage(actor), scene.id + ": a stamina message was sent");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /**
     * 1.16 LifeShot had no stamina condition: the press works on any bar and
     * standPerform, finding the bar short, empties it and fires nothing,
     * without a message.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_stamina_life_shot")
    public static void lifeShotStartsOnAnEmptyBarAndFiresNothing(GameTestHelper helper) {
        new Sequence(helper, "stamina/life-shot", 150) {
            private Actor actor;
            private EntityActionInstance shot;
            private final List<String> phases = new ArrayList<>();
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        actor.user().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 2));
                        GoldExperienceLifeformState lifeforms = GoldExperienceLifeformState.get(actor.user());
                        lifeforms.learnAllValidLifeforms(scene.level);
                        lifeforms.setSelected(lifeforms.selectedOrFirstMetId(scene.level));
                        scene.premise(GoldExperienceCreateLifeformAbility.hasSelectedLifeform(actor.user()),
                                "a lifeform is chosen");
                        actor.power().setStamina(0);
                        stage = 1;
                    }
                    case 1 -> {
                        shot = scene.pressAction(actor, null, "ger_life_shot", "ger_life_shot",
                                InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        AbilityInput.keyRelease(GerMeleeGameTestScene.SPECIAL, actor.user());
                        stage = 2;
                    }
                    case 2 -> {
                        phases.add(String.valueOf(shot.getPhase()));
                        if (shot.getPhase() == ActionPhase.WINDUP) {
                            actor.power().setStamina(0);
                        }
                        if (shot.getPhase() == ActionPhase.RECOVERY || actor.stand().getCurStandAction() != shot) {
                            scene.log("phases=" + phases + " current=" + actor.stand().getCurStandAction());
                            helper.assertTrue(phases.contains("PERFORM"),
                                    scene.id + ": the Life Shot ended without a perform: " + phases);
                            int beams = scene.level.getEntitiesOfClass(GerBeamEntity.class,
                                    actor.user().getBoundingBox().inflate(32)).size();
                            scene.log("stamina=" + actor.power().getStamina() + " offhand="
                                    + actor.user().getOffhandItem().getCount() + " beams=" + beams);
                            helper.assertTrue(actor.user().getOffhandItem().getCount() == 2 && beams == 0,
                                    scene.id + ": the short bar still fired the shot");
                            helper.assertTrue(!sentStaminaMessage(actor), scene.id + ": a stamina message was sent");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /** 1.16 ReturnToZero.checkSpecificConditions refused a bar under 200 with no_stamina, in Resolve too. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_stamina_rtz")
    public static void returnToZeroKeepsTheDonorStaminaCondition(GameTestHelper helper) {
        new Sequence(helper, "stamina/rtz", 50) {
            @Override
            boolean step() {
                Actor actor = scene.actor();
                GoldExperienceRequiemAddon.playerState(actor.user()).setReturnToZeroMarkAvailable(true);
                StandEntityAbility ability = (StandEntityAbility) actor.power().getAbility("ger_return_to_zero");
                actor.power().setStamina(150);
                ConditionCheck shortBar = ability.checkSpecificConditions(actor.power());
                actor.user().addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0));
                ConditionCheck inResolve = ability.checkSpecificConditions(actor.power());
                actor.user().removeEffect(ModStatusEffects.RESOLVE);
                actor.power().setStamina(200);
                ConditionCheck paid = ability.checkSpecificConditions(actor.power());
                scene.log("short=" + warningKey(shortBar) + " resolve=" + warningKey(inResolve)
                        + " paid=" + paid.isPositive());
                helper.assertTrue(!shortBar.isPositive()
                                && warningKey(shortBar).equals("jojo.message.action_condition.no_stamina"),
                        scene.id + ": 150 stamina gave " + warningKey(shortBar));
                helper.assertTrue(!inResolve.isPositive(),
                        scene.id + ": Resolve lifted Return to Zero's stamina condition");
                helper.assertTrue(paid.isPositive(), scene.id + ": 200 stamina was refused");
                return true;
            }
        }.start();
    }
}
