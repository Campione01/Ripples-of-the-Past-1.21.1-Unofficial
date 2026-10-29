package rotp.addons.ger.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.JojoModConfig;
import rotp.core.impl.stands.goldexperience.GoldExperienceCreateLifeformAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInstance;

@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerLifeShotGameTests {
    private GerLifeShotGameTests() {}

    /**
     * 1.16 LifeShot hurt every beam target directly with getAttackDamage, so
     * the Stand damage multiplier of the config never scaled the shot.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_life_shot_damage")
    public static void lifeShotDamageIgnoresTheStandDamageMultiplier(GameTestHelper helper) {
        new Sequence(helper, "life-shot/fixed-damage", 150) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance shot;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        ModConfigSpec.ConfigValue<Double> multiplier = JojoModConfig.COMMON_SPEC.getValues()
                                .get(List.of("Stand settings", "standPowerMultiplier"));
                        double before = multiplier.get();
                        // Spec setters are memory-only; the user's file is never saved.
                        scene.onClose(() -> multiplier.set(before));
                        multiplier.set(2.0D);
                        scene.premise(JojoModConfig.getCommonConfigInstance(false)
                                        .standDamageMultiplier.get() == 2.0D,
                                "the live multiplier is 2");
                        actor = scene.actor();
                        cow = scene.cow(scene.origin.add(0, 0, 4));
                        actor.user().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 2));
                        GoldExperienceLifeformState lifeforms = GoldExperienceLifeformState.get(actor.user());
                        lifeforms.learnAllValidLifeforms(scene.level);
                        lifeforms.setSelected(lifeforms.selectedOrFirstMetId(scene.level));
                        scene.premise(GoldExperienceCreateLifeformAbility.hasSelectedLifeform(actor.user()),
                                "a lifeform is chosen");
                        stage = 1;
                    }
                    case 1 -> {
                        shot = scene.pressAction(actor, null, "ger_life_shot", "ger_life_shot",
                                InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        AbilityInput.keyRelease(GerMeleeGameTestScene.SPECIAL, actor.user());
                        stage = 2;
                    }
                    case 2 -> {
                        if (!cow.hurts.isEmpty()) {
                            float expected = (float) actor.stand().getAttackDamage();
                            scene.log("expected=" + expected + " hurts=" + cow.hurts
                                    + " health=" + cow.getHealth());
                            scene.premise(expected > 0, "the Stand has attack damage");
                            helper.assertTrue(cow.hurts.size() == 1
                                            && Math.abs(cow.hurts.get(0).amount() - expected)
                                                    < GerMeleeGameTestScene.EPS,
                                    scene.id + ": the shot hit the cow with " + cow.hurts
                                            + " at multiplier 2, 1.16 dealt " + expected);
                            return true;
                        }
                        helper.assertTrue(actor.stand().getCurStandAction() == shot,
                                scene.id + ": the shot ended without hitting the cow");
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /**
     * 1.16 LifeShot.onHit hurt each target with
     * DamageSource.indirectMagic(stand, user): armour did not reduce it, the
     * Stand was the direct entity and the user the attacker, who got the kill
     * credit (and half the Resolve of a Stand hit).
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_life_shot_armour")
    public static void lifeShotPiercesArmourAsTheUsersIndirectMagic(GameTestHelper helper) {
        new Sequence(helper, "life-shot/armour", 150) {
            private final float health = 200.0F;
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance shot;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = scene.cow(scene.origin.add(0, 0, 4));
                        cow.getAttribute(Attributes.ARMOR).setBaseValue(20.0D);
                        cow.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0D);
                        scene.premise(cow.getArmorValue() == 20 && cow.getHealth() == health,
                                "the cow wears 20 armour at full health");
                        actor.user().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 2));
                        GoldExperienceLifeformState lifeforms = GoldExperienceLifeformState.get(actor.user());
                        lifeforms.learnAllValidLifeforms(scene.level);
                        lifeforms.setSelected(lifeforms.selectedOrFirstMetId(scene.level));
                        scene.premise(GoldExperienceCreateLifeformAbility.hasSelectedLifeform(actor.user()),
                                "a lifeform is chosen");
                        stage = 1;
                    }
                    case 1 -> {
                        shot = scene.pressAction(actor, null, "ger_life_shot", "ger_life_shot",
                                InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        AbilityInput.keyRelease(GerMeleeGameTestScene.SPECIAL, actor.user());
                        stage = 2;
                    }
                    case 2 -> {
                        if (!cow.hurts.isEmpty()) {
                            float expected = (float) actor.stand().getAttackDamage();
                            float lost = health - cow.getHealth();
                            DamageSource source = cow.sources.get(0);
                            scene.log("expected=" + expected + " lost=" + lost + " hurts=" + cow.hurts
                                    + " type=" + source.typeHolder().getRegisteredName()
                                    + " direct=" + source.getDirectEntity() + " cause=" + source.getEntity()
                                    + " credit=" + cow.getKillCredit());
                            scene.premise(expected > 0, "the Stand has attack damage");
                            helper.assertTrue(cow.hurts.size() == 1 && source.is(DamageTypes.INDIRECT_MAGIC)
                                            && source.getDirectEntity() == actor.stand()
                                            && source.getEntity() == actor.user(),
                                    scene.id + ": 1.16 hurt with indirectMagic(stand, user), the port hurt with "
                                            + source.typeHolder().getRegisteredName() + " from "
                                            + source.getDirectEntity() + " caused by " + source.getEntity());
                            helper.assertTrue(Math.abs(lost - expected) < GerMeleeGameTestScene.EPS,
                                    scene.id + ": 1.16 indirectMagic bypassed armour and took " + expected
                                            + " from the armoured cow, the port took " + lost);
                            helper.assertTrue(cow.getKillCredit() == actor.user(),
                                    scene.id + ": 1.16 credited the user with the hit, the port credits "
                                            + cow.getKillCredit());
                            return true;
                        }
                        helper.assertTrue(actor.stand().getCurStandAction() == shot,
                                scene.id + ": the shot ended without hitting the cow");
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }
}
