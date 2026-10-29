package rotp.addons.diverdown.ability;

import java.lang.reflect.Field;
import java.util.function.Consumer;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.DiverDownDamageTypes;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;

/**
 * 1.16 DiverDownDisassembleMob (049 :59-106) as the heavy attack the deform dash handed the heavy key to
 * (DiverDownDeformDash.replaceAction :71-84), and the knockback of Diver Down's heavy attacks on the target inside.
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiverDownDisassembleGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";
    private static final int OWN_TICKS = 60;

    private DiverDownDisassembleGameTests() {}

    // The finisher gave the heavy key to the deform dash, and the dash gave it to the disassembly while Diver Down was
    // inside a target at a quarter of its health or less; checkStandConditions (:65-72) then refused the press while that
    // health was above the Stand's attack damage (the port's diver_down_disassemble_target is kept), where the port
    // dashed. Above a quarter the key stayed the dash; below half a meter, the heavy punch.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_disassemble_key",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void disassemblyTakesTheHeavyKeyFromTheDeformDashAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, scene.origin.add(0.0D, 0.0D, 2.0D));
                float attack = (float) stand.getAttackDamage();
                helper.assertTrue(attack >= 4.0F && attack + 5.0F <= 100.0F,
                        "Fixture: unexpected attack damage " + attack);
                helper.assertTrue(stand.setEmbeddedTarget(cow), "Fixture: Diver Down did not dive into the cow");

                stand.setFinisherMeter(1.0F);
                health(cow, 40.0F, 4.0F);
                AbilityConditionCheck key = heavyKey(scene);
                helper.assertTrue(key.ability instanceof MobDisassembleAbility && key.conditionCheck.isPositive(),
                        "1.16 gave the heavy key to the disassembly inside a cow at 10% of its health with a full "
                                + "finisher meter; the key holds " + key.ability.name() + ", "
                                + scene.answer(key.conditionCheck));

                health(cow, 400.0F, attack + 5.0F);
                key = heavyKey(scene);
                helper.assertTrue(key.ability instanceof MobDisassembleAbility,
                        "1.16 gave the heavy key to the disassembly inside a cow at a quarter of its health or less, "
                                + "whatever its health against the attack damage; the key holds " + key.ability.name());
                helper.assertTrue(!key.conditionCheck.isPositive() && key.conditionCheck.getWarning() != null
                                && key.conditionCheck.getWarning().getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals(
                                        "jojo.message.action_condition.diver_down_disassemble_target"),
                        "1.16 refused the disassembly of a cow with more health than Diver Down's attack damage (the "
                                + "port's message kept); the port answered " + scene.answer(key.conditionCheck));
                // the server's gate of a key press (ClAbilityInputPacket)
                boolean admitted = AbilityInput.withConditionCheck(key, scene.owner, InputMethod.CLICK);
                EntityActionInstance running = LivingComponentAction.getCurEntityAction(stand);
                helper.assertTrue(!admitted && running == null && stand.isEmbedded(),
                        "1.16 refused the heavy press inside a cow at a quarter of its health but above the attack "
                                + "damage; the port admitted it: " + admitted + ", running " + running);

                health(cow, 400.0F, 200.0F);
                key = heavyKey(scene);
                helper.assertTrue(key.ability instanceof DeformDashAbility,
                        "Fixture: the heavy key at a full meter inside a cow at half its health is not the deform "
                                + "dash but " + key.ability.name());

                stand.setFinisherMeter(0.0F);
                health(cow, 40.0F, 4.0F);
                key = heavyKey(scene);
                helper.assertTrue(!(key.ability instanceof MobDisassembleAbility),
                        "1.16 gave the heavy key to the disassembly only through the deform dash, at half a finisher "
                                + "meter or more; with an empty meter it holds " + key.ability.name());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownDisassembleMob was a StandEntityHeavyAttack: the heavy windup by speed and finisher meter (the
    // press took the finisher value and spent 0.51 of the meter), at its perform the loot, then a HeavyPunchInstance
    // from the Stand on the target (damage source of the Stand's punch) whose knockback addKnockback(0.5 + finisher)
    // set in place of the heavy attack's own, and a recovery no other action cancelled (not a finisher).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_disassemble_heavy",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void disassemblyStrikesAsAHeavyAttackAfterItsWindupAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, scene.origin.add(0.0D, 0.0D, 2.0D));
                cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
                cow.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(128.0D);
                health(cow, 40.0F, 4.0F);
                cow.setAbsorptionAmount(100.0F);
                helper.assertTrue(stand.setEmbeddedTarget(cow), "Fixture: Diver Down did not dive into the cow");
                float[] strength = {0.0F};
                Consumer<LivingKnockBackEvent> knockback = event -> {
                    if (event.getEntity() == cow) {
                        strength[0] = Math.max(strength[0], event.getStrength());
                    }
                };
                DamageSource[] source = {null};
                Consumer<LivingIncomingDamageEvent> damage = event -> {
                    if (event.getEntity() == cow) {
                        source[0] = event.getSource();
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
                scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(knockback));
                scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(damage));
                EntityActionInstance action = DisassemblyLootGameTests.pressDisassembly(
                        helper, scene.power, scene.owner, stand);
                helper.assertTrue(stand.getLastHeavyFinisherValue() == 1.0F
                                && Math.abs(stand.getFinisherMeter() - 0.49F) < 1.0E-4F,
                        "1.16 took the finisher value (1.0) and spent 0.51 of the meter at the press; value "
                                + stand.getLastHeavyFinisherValue() + ", meter " + stand.getFinisherMeter());
                helper.assertTrue(action.getPhase() == ActionPhase.WINDUP && action.getCurPhaseLength() >= 2
                                && stand.isEmbedded() && source[0] == null,
                        "1.16 disassembled after the heavy attack's windup, not at the press; phase "
                                + action.getPhase() + " of " + action.getCurPhaseLength() + " ticks, embedded "
                                + stand.isEmbedded() + ", hit " + (source[0] != null));
                Ability punch = scene.power.getAbility(DiverDownAddon.PUNCH);
                float expected = 0.4F + 0.5F + 1.0F;
                scene.poll(0, () -> {
                    helper.assertTrue(!stand.isEmbedded(), "The disassembly has not performed yet");
                    helper.assertTrue(source[0] != null && source[0].getDirectEntity() == stand
                                    && !source[0].is(DiverDownDamageTypes.DISASSEMBLE),
                            "1.16 hit the disassembled cow with Diver Down's punch; source "
                                    + (source[0] != null ? source[0].getMsgId() + " from " + source[0].getDirectEntity()
                                            : null));
                    helper.assertTrue(Math.abs(strength[0] - expected) < 0.05F,
                            "1.16 knocked the disassembled cow back by 0.5 + the finisher value (1.0) on top of the "
                                    + "hit's own 0.4, " + expected + ", in place of the heavy attack's 0.5 + strength / "
                                    + "(8 - finisher x 4); knockback strength " + strength[0]);
                    helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY && !action.isOver(),
                            "1.16 held the disassembly's recovery to its end (it was no finisher, so no free "
                                    + "recovery); the port's ended at once, phase " + action.getPhase());
                    helper.assertTrue(!action.canBeCancelledInto((EntityActionType) punch),
                            "1.16 let no other action cancel the disassembly's recovery (it was no finisher); the "
                                    + "punch could");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownDisassembleMob.checkStandConditions (049 :65-72) answered POSITIVE or NEGATIVE on the health alone and
    // never called super, so the heavy attack's melee check (StandEntityHeavyAttack :125-127) and the port's rule that a
    // grabbing Stand takes no heavy punch were not asked at the press: a Stand with no reach or one holding an entity
    // still disassembled the target it was inside.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_disassemble_press_checks",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void disassemblyPressSkipsTheHeavyAttacksMeleeAndGrabChecksAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, scene.origin.add(0.0D, 0.0D, 2.0D));
                health(cow, 40.0F, 4.0F);
                helper.assertTrue(stand.setEmbeddedTarget(cow), "Fixture: Diver Down did not dive into the cow");
                stand.summonLockTicks = 0;
                stand.gradualSummonWeaknessTicks = 0;
                stand.setFinisherMeter(1.0F);
                Ability disassemble = scene.power.getAbility(DiverDownAddon.DISASSEMBLE_MOB_NAME);
                helper.assertTrue(disassemble instanceof MobDisassembleAbility, "Diver Down has no mob disassembly");

                AttributeInstance reach = stand.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
                double kept = reach.getBaseValue();
                reach.setBaseValue(0.0D);
                helper.assertFalse(stand.canAttackMelee(), "Fixture: a Stand with no reach can attack in melee");
                press(helper, scene, disassemble, "a Stand with no reach");
                reach.setBaseValue(kept);
                helper.assertTrue(stand.canAttackMelee(), "Fixture: the Stand's reach did not come back");

                Cow held = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(held != null, "Could not create a cow");
                scene.prepareMob(held, scene.origin.add(3.0D, 0.0D, 0.0D));
                LivingComponentGrab grab = stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
                grab.setGrabTarget(held);
                helper.assertTrue(LivingComponentGrab.getEntityGrabbedBy(stand) == held,
                        "Fixture: the Stand does not hold the second cow");
                press(helper, scene, disassemble, "a Stand holding an entity");
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    private static void press(GameTestHelper helper, DiverDownWallsGameTests.Scene scene, Ability disassemble,
            String stand) {
        LivingComponentAction.getComponent(scene.stand).setAction(null, scene.owner, SyncType.NO_SYNC);
        BufferingState buffering = BufferingState.clickOnly();
        disassemble.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
        helper.assertTrue(buffering.isActionSuccess
                        && LivingComponentAction.getCurEntityAction(scene.stand)
                                instanceof MobDisassembleAbility.DisassembleAction,
                "1.16 let a press of the disassembly through for " + stand + " (its own check asked only the health);"
                        + " the port refused it");
    }

    // 1.16 DiverDownSpringLegs.punchEntity (049): super.punchEntity(...).addKnockback(0.5 + finisher), and
    // StandEntityPunch.addKnockback set the knockback to 1 + that, replacing the heavy attack's 0.5 + strength /
    // (8 - finisher x 4); the port added both.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_spring_legs_knockback",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void springLegsKnocksBackByHalfPlusTheFinisherAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, scene.origin.add(0.0D, 0.0D, 2.0D));
                cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
                helper.assertTrue(stand.setEmbeddedTarget(cow), "Fixture: Diver Down did not dive into the cow");
                float[] strength = {0.0F};
                Consumer<LivingKnockBackEvent> knockback = event -> {
                    if (event.getEntity() == cow) {
                        strength[0] = Math.max(strength[0], event.getStrength());
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
                scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(knockback));
                stand.setFinisherMeter(0.0F);
                Ability springLegs = scene.power.getAbility(DiverDownAddon.SPRING_LEGS_NAME);
                BufferingState buffering = BufferingState.clickOnly();
                springLegs.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
                helper.assertTrue(buffering.isActionSuccess
                                && LivingComponentAction.getCurEntityAction(stand)
                                        instanceof SpringLegsAbility.SpringLegsAction,
                        "Fixture: Spring Legs did not start");
                float expected = 0.4F + 0.5F;
                scene.poll(0, () -> {
                    helper.assertTrue(cow.hasEffect(DiverDownAddon.SPRING_LEGS_EFFECT) && cow.getHealth() < 500.0F,
                            "Spring Legs has not struck yet");
                    helper.assertTrue(Math.abs(strength[0] - expected) < 0.05F,
                            "1.16 knocked the cow Spring Legs struck back by 0.5 + the finisher value (0) on top of "
                                    + "the hit's own 0.4, " + expected + ", in place of the heavy attack's 0.5 + "
                                    + "strength / 8; knockback strength " + strength[0]);
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    private static AbilityConditionCheck heavyKey(DiverDownWallsGameTests.Scene scene) {
        // the moves and their condition checks are cached for the tick
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(scene.power, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        AbilityConditionCheck key = scene.power.updateAvailableMoves()
                .getContextVariationContainer(DiverDownAddon.HEAVY_PUNCH);
        scene.helper.assertTrue(key != null, "The heavy key holds nothing");
        return key;
    }

    private static void health(Cow cow, float max, float health) {
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        cow.setHealth(health);
    }
}
