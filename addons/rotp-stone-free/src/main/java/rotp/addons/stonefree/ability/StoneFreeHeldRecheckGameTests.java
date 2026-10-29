package rotp.addons.stonefree.ability;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.ability.StoneFreeBarrierGameTests.Fixture;
import rotp.addons.stonefree.entity.StoneFreeStringEntity.Kind;
import rotp.addons.stonefree.state.StoneFreeState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 PowerBaseImpl.tickHeldAction re-ran checkRequirements on every tick of a held action: a failed check ended
 * the hold, nothing fired and nothing was paid. Every string here flies straight up, clear of the other plots.
 */
@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeHeldRecheckGameTests {
    private StoneFreeHeldRecheckGameTests() {}

    // 1.16 stone_free_user_string_pick: holdToFire(5) with needsFreeMainHand, so Action.checkHeldItems ran on
    // every held tick.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void pickChargeEndsWhenTheMainHandFillsMidCharge(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility pick = ability(helper, fixture, StoneFreeAddon.USER_STRING_PICK_NAME);
            float full = fixture.power.getMaxStamina();
            EntityActionInstance charge = hold(helper, fixture, pick, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "The held pick did not start charging: " + charge.getPhase());
            StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
                fixture.owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
                assertWarning(helper, pick.checkHeldActionConditions(charge, fixture.power), "hand",
                        "A pick charge with a filled main hand");
                StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
                    helper.assertTrue(running(fixture, pick) == null,
                            "The pick kept charging with a stick in the user's main hand: phase "
                                    + charge.getPhase());
                    StoneFreeBarrierGameTests.later(helper, fixture, 8, () -> {
                        helper.assertTrue(StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_SPIKE) == null
                                        && fixture.power.getStamina() == full
                                        && !fixture.power.isAbilityOnCooldown(pick.name()),
                                "The pick stopped by a filled hand still fired: spike "
                                        + StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_SPIKE)
                                        + ", stamina " + fixture.power.getStamina() + " of " + full
                                        + ", cooldown " + fixture.power.isAbilityOnCooldown(pick.name()));
                        // The same hold with a free hand goes off.
                        AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
                        fixture.owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                        hold(helper, fixture, pick, StoneFreeBarrierGameTests.GRAPPLE_KEY);
                        StoneFreeBarrierGameTests.later(helper, fixture, 8, () -> {
                            helper.assertTrue(fixture.power.getStamina() < full
                                            && fixture.power.isAbilityOnCooldown(pick.name()),
                                    "Control: the pick held with a free hand did not go off: stamina "
                                            + fixture.power.getStamina() + " of " + full);
                            StoneFreeBarrierGameTests.succeed(helper, fixture);
                        });
                    });
                });
            });
        });
    }

    // 1.16 StoneFreeUserCloseWounds.checkSpecificConditions on every tick of its holdToFire(10): no wound, no hold.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void closeWoundsChargeEndsOnceTheWoundsAreGone(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility closeWounds = ability(helper, fixture, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME);
            float full = fixture.power.getMaxStamina();
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400)),
                    "Could not make the user bleed");
            EntityActionInstance charge =
                    hold(helper, fixture, closeWounds, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            StoneFreeBarrierGameTests.later(helper, fixture, 2, () -> {
                helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE && !charge.isOver(),
                        "Close Wounds was not charging 2 ticks into its 10: " + charge.getPhase());
                fixture.owner.removeEffect(ModStatusEffects.BLEEDING);
                ConditionCheck check = closeWounds.checkHeldActionConditions(charge, fixture.power);
                helper.assertTrue(!check.isPositive() && check.getWarning() == null,
                        "Close Wounds with no wound left passed its held check or warned: " + check.getWarning());
                StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
                    helper.assertTrue(running(fixture, closeWounds) == null,
                            "Close Wounds kept charging with no wound left: phase " + charge.getPhase());
                    StoneFreeBarrierGameTests.later(helper, fixture, 12, () -> {
                        helper.assertTrue(fixture.power.getStamina() == full,
                                "The stopped Close Wounds was paid: stamina " + fixture.power.getStamina()
                                        + " of " + full);
                        // With the wound still there, the same hold closes it.
                        AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
                        fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400));
                        hold(helper, fixture, closeWounds, StoneFreeBarrierGameTests.GRAPPLE_KEY);
                        StoneFreeBarrierGameTests.later(helper, fixture, 14, () -> {
                            helper.assertTrue(!fixture.owner.hasEffect(ModStatusEffects.BLEEDING)
                                            && fixture.power.getStamina() < full,
                                    "Control: Close Wounds held over a wound did not close it: bleeding "
                                            + fixture.owner.hasEffect(ModStatusEffects.BLEEDING) + ", stamina "
                                            + fixture.power.getStamina());
                            StoneFreeBarrierGameTests.succeed(helper, fixture);
                        });
                    });
                });
            });
        });
    }

    /*
     * 1.16 stone_free_user_string_pick (holdToFire(5, false)) and stone_free_user_close_wounds (holdToFire(10, false))
     * were StandActions: PowerBaseImpl.tickHeldAction fired the hold in the n-th tick from the press and the user's
     * power performed it in that tick, not in the Stand's next tick as a Stand task would. Counted in the ticks of the
     * user's Stand-power slot, which holds them, from the press (the press's own tick the first).
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100,
            batch = "rotp_sf_charge_fire_tick")
    public static void pickGoesOffInTheTickItsChargeFiresAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility pick = ability(helper, fixture, StoneFreeAddon.USER_STRING_PICK_NAME);
            float full = fixture.power.getMaxStamina();
            int fireTick = chargeFireTick(helper, fixture, pick, () -> fixture.power.getStamina() < full);
            helper.assertTrue(fireTick == 5,
                    "1.16 threw the pick in the 5th tick from its press, the tick its hold fired: thrown in tick "
                            + fireTick);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100,
            batch = "rotp_sf_charge_fire_tick")
    public static void closeWoundsGoesOffInTheTickItsChargeFiresAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility closeWounds = ability(helper, fixture, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME);
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400)),
                    "Could not make the user bleed");
            int fireTick = chargeFireTick(helper, fixture, closeWounds,
                    () -> !fixture.owner.hasEffect(ModStatusEffects.BLEEDING));
            helper.assertTrue(fireTick == 10,
                    "1.16 closed the wounds in the 10th tick from the Close Wounds press, the tick its hold fired:"
                            + " closed in tick " + fireTick);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    // The tick, counted from the press, in which the held charge its Stand performs took effect.
    private static int chargeFireTick(GameTestHelper helper, Fixture fixture, StoneFreeStringAbility ability,
            BooleanSupplier fired) {
        EntityActionInstance charge = hold(helper, fixture, ability, StoneFreeBarrierGameTests.GRAPPLE_KEY);
        try {
            LivingComponentAction userStandPower = LivingComponentAction.getComponent(fixture.owner, PowerClass.STAND);
            helper.assertTrue(charge.getPerformer() == fixture.stand && charge.getPhase() == ActionPhase.BUTTON_CHARGE
                            && userStandPower.getAction() == charge,
                    "Setup: the Stand is not charging " + ability.name() + " as its user's Stand-power action:"
                            + " performer " + charge.getPerformer() + ", phase " + charge.getPhase());
            for (int tick = 1; tick <= 30; tick++) {
                userStandPower.tick();
                if (fired.getAsBoolean()) {
                    return tick;
                }
            }
            return -1;
        }
        finally {
            AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
        }
    }

    // 1.16 StoneFreeUserRecoverString (holdType) needed LivingUtilCap.grapple on every held tick: once the string
    // it took back was gone, the hold ended by itself, the key still down.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void recoverStringHoldEndsOnceTheStringIsBack(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeGrappleGameTests.anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility recover = ability(helper, fixture, StoneFreeAddon.USER_RECOVER_STRING_NAME);
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                helper.assertTrue(StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_GRAPPLE) != null,
                        "Setup: no grapple string out for Recover String to take back");
                EntityActionInstance held = hold(helper, fixture, recover, StoneFreeBarrierGameTests.GRAPPLE_KEY);
                StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                    helper.assertTrue(StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_GRAPPLE) == null,
                            "Holding Recover String did not take the grapple string back");
                    helper.assertTrue(holdsKey(fixture), "The Recover String key no longer counted as held");
                    helper.assertTrue(running(fixture, recover) == null,
                            "Recover String stayed held with no string left to take back: phase "
                                    + held.getPhase());
                    helper.assertTrue(!recover.checkConditions(fixture.power).isPositive()
                                    && !recover.checkHeldActionConditions(held, fixture.power).isPositive(),
                            "Recover String passed its checks with no user grapple string out");
                    StoneFreeBarrierGameTests.succeed(helper, fixture);
                });
            });
        });
    }

    // 1.16 StoneFreeUserRemoveBarrier (holdType): a held tick that found no barrier ended the hold with
    // no_barriers, so a hold that took them all ends that way.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 220)
    public static void removeBarriersHoldEndsWithNoBarriersOnceTheyAreGone(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, true);
        StoneFreeBarrierGameTests.placePair(helper, fixture, () -> {
            StoneFreeStringAbility remove = ability(helper, fixture, StoneFreeAddon.USER_REMOVE_BARRIER_NAME);
            EntityActionInstance held = hold(helper, fixture, remove, StoneFreeBarrierGameTests.REMOVE_KEY);
            StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                StoneFreeState state = StoneFreeState.get(fixture.owner);
                helper.assertTrue(StoneFreeBarrierGameTests.barriers(helper, fixture).isEmpty()
                                && state.placedStrings() == 0 && state.openBarrier().isEmpty(),
                        "Holding Remove Barriers did not take every barrier");
                helper.assertTrue(holdsKey(fixture), "The Remove Barriers key no longer counted as held");
                helper.assertTrue(running(fixture, remove) == null,
                        "Remove Barriers stayed held with no barrier left: phase " + held.getPhase());
                assertWarning(helper, remove.checkHeldActionConditions(held, fixture.power), "no_barriers",
                        "A held Remove Barriers with no barrier left");
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    // 1.16 StoneFreeGrapple (holdType) checked the string limit on every held tick; its string lived only while
    // the grapple was held (SFGrapplingStringEntity.tick).
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void standGrappleHoldEndsAtTheStringLimit(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.stand.position().add(0.0D, 10.0D, 0.0D));
            LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(new ActionTarget(target));
            StoneFreeStringAbility grapple = ability(helper, fixture, StoneFreeAddon.GRAPPLE_ENTITY_NAME);
            EntityActionInstance held = hold(helper, fixture, grapple, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            StoneFreeBarrierGameTests.later(helper, fixture, 3, () -> {
                helper.assertTrue(running(fixture, grapple) == held && held.getPhase() == ActionPhase.PERFORM
                                && StoneFreeGrappleGameTests.find(helper, fixture, Kind.STAND_GRAPPLE) != null,
                        "Setup: the Stand's grapple was not held with its string out: phase " + held.getPhase());
                StoneFreeState.get(fixture.owner).investString(fixture.owner, 100, 100);
                StoneFreeBarrierGameTests.later(helper, fixture, 2, () -> {
                    helper.assertTrue(running(fixture, grapple) == null,
                            "The Stand's grapple stayed held at the string limit: phase " + held.getPhase());
                    helper.assertTrue(StoneFreeGrappleGameTests.find(helper, fixture, Kind.STAND_GRAPPLE) == null,
                            "The Stand's grapple string outlived its hold");
                    assertWarning(helper, grapple.checkHeldActionConditions(held, fixture.power), "string_limit",
                            "A held Stand grapple at the string limit");
                    StoneFreeBarrierGameTests.succeed(helper, fixture);
                });
            });
        });
    }

    /*
     * 1.16 stone_free_grapple_entity (StoneFreeGrapple) was a holdType Stand action: PowerBaseImpl let every other
     * action of the power through it and ended the hold once that action went off (onClickAction
     * stopHeldAction(false), setHeldAction). Every Stone Free ability is performed by its Stand, so a punch still
     * replaces the held grapple at once. The user's recover string was a StandAction hold, which let nothing through.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void standGrappleIsAHoldTypeStandActionThatAPunchReplacesAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.stand.position().add(0.0D, 10.0D, 0.0D));
            LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(new ActionTarget(target));
            StoneFreeStringAbility grapple = ability(helper, fixture, StoneFreeAddon.GRAPPLE_ENTITY_NAME);
            StoneFreeStringAbility recover = ability(helper, fixture, StoneFreeAddon.USER_RECOVER_STRING_NAME);
            helper.assertTrue(!new StoneFreeStringAbility.StringAction(recover, StoneFreeStringAbility.Mode.RECOVER_STRING)
                            .isHoldTypeStandAction(),
                    "1.16 stone_free_user_recover_string was a StandAction hold that let no other action through:"
                            + " it must not be a hold-type Stand action");
            EntityActionInstance held = hold(helper, fixture, grapple, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            StoneFreeBarrierGameTests.later(helper, fixture, 3, () -> {
                helper.assertTrue(running(fixture, grapple) == held && held.getPhase() == ActionPhase.PERFORM,
                        "Setup: the Stand's grapple was not held: phase " + held.getPhase());
                helper.assertTrue(held.isHoldTypeStandAction(),
                        "1.16 stone_free_grapple_entity was a holdType Stand action, ended by another action of the"
                                + " power that went off: the Stand's grapple must be a hold-type Stand action");
                Ability punch = fixture.power.getAbility("punch");
                helper.assertTrue(punch != null && punch.checkConditions(fixture.power).isPositive(),
                        "Setup: the punch was refused during the grapple");
                BufferingState pressed = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(PUNCH_KEY, punch, fixture.owner, null, InputMethod.CLICK, 0.0F, pressed,
                        punch.abilityId);
                AbilityInput.keyRelease(PUNCH_KEY, fixture.owner);
                EntityActionInstance now = LivingComponentAction.getCurEntityAction(fixture.stand);
                helper.assertTrue(pressed.isActionSuccess && !pressed.shouldBuffer && now != null && now != held
                                && now.ability == punch && held.isOver(),
                        "1.16 let a punch through the held Stand grapple at once: started " + pressed.isActionSuccess
                                + ", buffered " + pressed.shouldBuffer + ", the Stand's action " + now
                                + ", the grapple over " + held.isOver() + " in " + held.getPhase());
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    /*
     * 1.16 StandEntityAction.checkTaskCancelling -> canClickDuringTask (:495-497): stone_free_grapple_entity
     * (StoneFreeGrapple, a StandEntityAction that was not chainable) pressed again while it was the Stand's task was
     * refused, with no message and not queued; the held grapple went on as it was.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void standGrapplePressedAgainDuringItsHoldIsRefusedAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.stand.position().add(0.0D, 10.0D, 0.0D));
            LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(new ActionTarget(target));
            StoneFreeStringAbility grapple = ability(helper, fixture, StoneFreeAddon.GRAPPLE_ENTITY_NAME);
            EntityActionInstance held = hold(helper, fixture, grapple, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            StoneFreeBarrierGameTests.later(helper, fixture, 3, () -> {
                helper.assertTrue(running(fixture, grapple) == held && held.getPhase() == ActionPhase.PERFORM
                                && held.canBeCancelledInto(grapple),
                        "Setup: the Stand's grapple was not held where its own press could cancel it: phase "
                                + held.getPhase());
                var resolved = resolve(fixture, grapple);
                helper.assertTrue(resolved != null && resolved.ability == grapple
                                && !resolved.conditionCheck.isPositive()
                                && resolved.conditionCheck.getWarning() == null,
                        "1.16 refused stone_free_grapple_entity pressed again during its own task, silently: "
                                + (resolved == null ? "no slot" : resolved.ability.name() + " positive "
                                        + resolved.conditionCheck.isPositive() + ", warning "
                                        + resolved.conditionCheck.getWarning()));
                BufferingState pressed = BufferingState.clickCanBuffer();
                if (AbilityInput.withConditionCheck(resolved, fixture.owner, InputMethod.HOLD)) {
                    AbilityInput.keyPress(OTHER_KEY, resolved.ability, fixture.owner, null, InputMethod.HOLD, 0.0F,
                            pressed, grapple.abilityId);
                    AbilityInput.keyRelease(OTHER_KEY, fixture.owner);
                }
                helper.assertTrue(!pressed.isActionSuccess && !pressed.shouldBuffer && !held.isOver()
                                && running(fixture, grapple) == held,
                        "1.16 refused the Stand's grapple pressed again during its hold, unqueued: started "
                                + pressed.isActionSuccess + ", buffered " + pressed.shouldBuffer + ", the hold over "
                                + held.isOver() + ", running " + running(fixture, grapple) + " (was " + held + ")");
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    /*
     * 1.16 StoneFreeStringBind.isCancelable (017 :85-86): only stone_free_punch, stone_free_barrage and
     * stone_free_heavy_punch replaced the Stand's bind (the charged heavy is the port's heavy); checkTaskCancelling
     * queued any other press, which waits in the input buffer here (owner rule N2), and refused the bind's own
     * (canClickDuringTask, not chainable).
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void standBindGivesWayOnlyToThePunchBarrageAndHeavyPunchAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            fixture.stand.setFinisherMeter(0.0F);
            StoneFreeStringAbility bind = ability(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            BufferingState bound = BufferingState.clickOnly();
            AbilityInput.keyPress(StoneFreeBarrierGameTests.GRAPPLE_KEY, bind, fixture.owner, null,
                    InputMethod.CLICK, 0.0F, bound, bind.abilityId);
            AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
            EntityActionInstance bindAction = LivingComponentAction.getCurEntityAction(fixture.stand);
            helper.assertTrue(bound.isActionSuccess && bindAction != null && bindAction.ability == bind,
                    "Setup: the Stand's bind did not start: " + bindAction);
            StoneFreeBarrierGameTests.later(helper, fixture, 2, () -> {
                helper.assertTrue(!bindAction.isOver() && bindAction.getPhase() == ActionPhase.PERFORM
                                && LivingComponentAction.getCurEntityAction(fixture.stand) == bindAction,
                        "Setup: the Stand's bind is not performing: " + bindAction.getPhase());
                StringBuilder wrong = new StringBuilder();
                for (String name : List.of("punch", "barrage", "heavy_punch", "heavy_charged")) {
                    if (!bindAction.canBeCancelledInto((EntityActionType) standAbility(helper, fixture, name))) {
                        wrong.append(' ').append(name).append(" waits");
                    }
                }
                for (String name : List.of("guard", StoneFreeAddon.EXTENDED_PUNCH_NAME,
                        StoneFreeAddon.STRING_PUNCH_NAME, StoneFreeAddon.GRAPPLE_ENTITY_NAME,
                        StoneFreeAddon.STRING_BIND_NAME)) {
                    if (bindAction.canBeCancelledInto((EntityActionType) standAbility(helper, fixture, name))) {
                        wrong.append(' ').append(name).append(" replaces it");
                    }
                }
                helper.assertTrue(wrong.isEmpty() && bindAction.refusesOwnAbilityPressedAgain(),
                        "1.16 let only the punch, the barrage and the heavy punch replace the Stand's bind:" + wrong
                                + "; refuses its own press again " + bindAction.refusesOwnAbilityPressedAgain());
                Ability guard = standAbility(helper, fixture, "guard");
                var guardSlot = resolve(fixture, guard);
                helper.assertTrue(guardSlot != null && guardSlot.ability == guard
                                && guardSlot.conditionCheck.isPositive(),
                        "Setup: the guard was refused during the Stand's bind");
                BufferingState guarded = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(OTHER_KEY, guard, fixture.owner, null, InputMethod.HOLD, 0.0F, guarded,
                        guard.abilityId);
                helper.assertTrue(!guarded.isActionSuccess && guarded.shouldBuffer && !bindAction.isOver()
                                && LivingComponentAction.getCurEntityAction(fixture.stand) == bindAction,
                        "1.16 queued the guard pressed during the Stand's bind (the input buffer here): started "
                                + guarded.isActionSuccess + ", buffered " + guarded.shouldBuffer + ", the bind over "
                                + bindAction.isOver() + ", the Stand's action "
                                + LivingComponentAction.getCurEntityAction(fixture.stand));
                AbilityInput.keyRelease(OTHER_KEY, fixture.owner);
                Ability heavy = standAbility(helper, fixture, "heavy_punch");
                var heavySlot = resolve(fixture, heavy);
                helper.assertTrue(heavySlot != null && heavySlot.ability == heavy
                                && heavySlot.conditionCheck.isPositive(),
                        "Setup: the heavy punch was refused or replaced during the Stand's bind: "
                                + (heavySlot == null ? "no slot" : heavySlot.ability.name()));
                BufferingState punched = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(PUNCH_KEY, heavy, fixture.owner, null, InputMethod.CLICK, 0.0F, punched,
                        heavy.abilityId);
                AbilityInput.keyRelease(PUNCH_KEY, fixture.owner);
                EntityActionInstance now = LivingComponentAction.getCurEntityAction(fixture.stand);
                helper.assertTrue(punched.isActionSuccess && !punched.shouldBuffer && now != null
                                && now.ability == heavy && bindAction.isOver(),
                        "1.16 let the heavy punch replace the Stand's bind at once: started " + punched.isActionSuccess
                                + ", buffered " + punched.shouldBuffer + ", the Stand's action " + now
                                + ", the bind over " + bindAction.isOver());
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    private static Ability standAbility(GameTestHelper helper, Fixture fixture, String name) {
        Ability ability = fixture.power.getAbility(name);
        helper.assertTrue(ability instanceof EntityActionType, "Stone Free has no action " + name);
        return ability;
    }

    // The power resolves its keys once a tick (Power.updateAvailableMoves); a press here is asked within that tick.
    private static rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck resolve(
            Fixture fixture, Ability ability) {
        try {
            java.lang.reflect.Field cached =
                    rotp.core.powersystem.Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(fixture.power, false);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        return fixture.power.updateAvailableMoves().getContextVariationContainer(ability);
    }

    /*
     * 1.16 stone_free_user_close_wounds, user_string_pick (holdToFire) and user_recover_string, user_remove_barrier
     * (holdType) were the user's StandActions: Action.heldAllowsOtherAction was false, so PowerBaseImpl.checkRequirements
     * let no other action of the power go off while one was held. Here such a press waits in the input buffer (owner
     * rule N2): nothing starts, from the Stand or from the user's hand, until the hold fired or was let go.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void closeWoundsChargeHoldsAUserStringBackAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility closeWounds = ability(helper, fixture, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME);
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400)),
                    "Could not make the user bleed");
            EntityActionInstance charge = hold(helper, fixture, closeWounds, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            StoneFreeBarrierGameTests.later(helper, fixture, 2, () -> {
                helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "Setup: Close Wounds was not charging: " + charge.getPhase());
                assertPressHeldBack(helper, fixture, charge, StoneFreeAddon.USER_STRING_ATTACK_NAME,
                        "the Close Wounds charge");
                helper.assertTrue(StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_STRING) == null,
                        "A user string flew from the user's hand during the Close Wounds charge");
                awaitThat(helper, fixture, 20,
                        () -> StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_STRING) != null,
                        "The user string pressed during the Close Wounds charge did not go off after it fired",
                        () -> helper.assertTrue(!fixture.owner.hasEffect(ModStatusEffects.BLEEDING),
                                "Close Wounds did not fire with the user string waiting"));
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void pickChargeHoldsAPunchBackAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility pick = ability(helper, fixture, StoneFreeAddon.USER_STRING_PICK_NAME);
            EntityActionInstance charge = hold(helper, fixture, pick, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> {
                helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "Setup: the pick was not charging: " + charge.getPhase());
                Ability punch = assertPressHeldBack(helper, fixture, charge, "punch", "the pick charge");
                awaitThat(helper, fixture, 20, () -> running(fixture, punch) != null,
                        "The punch pressed during the pick charge did not go off after it",
                        () -> helper.assertTrue(fixture.power.isAbilityOnCooldown(pick.name()),
                                "The pick did not fire with the punch waiting"));
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void recoverStringHoldHoldsAPunchBackAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeGrappleGameTests.anchorAbove(helper, fixture, 34.0D);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility recover = ability(helper, fixture, StoneFreeAddon.USER_RECOVER_STRING_NAME);
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
            StoneFreeBarrierGameTests.later(helper, fixture, 4, () -> {
                helper.assertTrue(StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_GRAPPLE) != null,
                        "Setup: no grapple string out for Recover String to take back");
                EntityActionInstance held = hold(helper, fixture, recover, StoneFreeBarrierGameTests.GRAPPLE_KEY);
                Ability punch = assertPressHeldBack(helper, fixture, held, "punch", "the held Recover String");
                awaitThat(helper, fixture, 20, () -> running(fixture, punch) != null,
                        "The punch pressed during the held Recover String did not go off after it",
                        () -> helper.assertTrue(held.isOver(), "The punch went off during the held Recover String"));
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 220)
    public static void removeBarriersHoldHoldsAPunchBackAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, true);
        StoneFreeBarrierGameTests.placePair(helper, fixture, () -> {
            StoneFreeStringAbility remove = ability(helper, fixture, StoneFreeAddon.USER_REMOVE_BARRIER_NAME);
            EntityActionInstance held = hold(helper, fixture, remove, StoneFreeBarrierGameTests.REMOVE_KEY);
            Ability punch = assertPressHeldBack(helper, fixture, held, "punch", "the held Remove Barriers");
            awaitThat(helper, fixture, 20, () -> running(fixture, punch) != null,
                    "The punch pressed during the held Remove Barriers did not go off after it",
                    () -> helper.assertTrue(held.isOver(), "The punch went off during the held Remove Barriers"));
        });
    }

    /*
     * 1.16 stone_free_user_close_wounds was a StandAction its user's power held, never a Stand task: pressed while the
     * Stand was in a punch windup it cannot leave, it charged at once beside the punch, performed by the Stand
     * (StandAction.getPerformer), and the punch went on. Its hold let no other action of the power go off
     * (heldAllowsOtherAction false): here such a press waits in the input buffer (owner rule N2), so neither another
     * punch nor a user string goes off until Close Wounds fired.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void closeWoundsChargedPastABusyStandHoldsPressesBackAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility closeWounds = ability(helper, fixture, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME);
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 400)),
                    "Could not make the user bleed");
            Ability punch = fixture.power.getAbility("punch");
            BufferingState punched = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(PUNCH_KEY, punch, fixture.owner, null, InputMethod.CLICK, 0.0F, punched,
                    punch.abilityId);
            AbilityInput.keyRelease(PUNCH_KEY, fixture.owner);
            EntityActionInstance standPunch = LivingComponentAction.getCurEntityAction(fixture.stand);
            helper.assertTrue(punched.isActionSuccess && standPunch != null && standPunch.ability == punch
                            && standPunch.getPhase() == ActionPhase.WINDUP
                            && !standPunch.canBeCancelledInto(closeWounds),
                    "Setup: the Stand's punch did not start a windup it cannot leave: " + standPunch);
            EntityActionInstance charge = hold(helper, fixture, closeWounds, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            helper.assertTrue(charge == LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND)
                            && charge.getPerformer() == fixture.stand && charge.getPhase() == ActionPhase.BUTTON_CHARGE
                            && LivingComponentAction.getCurEntityAction(fixture.stand) == standPunch
                            && !standPunch.isOver(),
                    "1.16 charged Close Wounds at once beside the Stand's punch windup, as its user's Stand-power"
                            + " action performed by the Stand: the user's Stand-power action "
                            + LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND)
                            + ", performer " + charge.getPerformer() + ", phase " + charge.getPhase()
                            + ", the Stand's action " + LivingComponentAction.getCurEntityAction(fixture.stand));
            whenThat(helper, fixture, 8, () -> standPunch.isOver() || standPunch.getPhase() == ActionPhase.RECOVERY,
                    "Setup: the Stand's punch did not reach its recovery", () -> {
                helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE && !charge.isOver(),
                        "Setup: Close Wounds stopped charging before the Stand was free: " + charge.getPhase());
                for (String pressedName : List.of("punch", StoneFreeAddon.USER_STRING_ATTACK_NAME)) {
                    String outcome = assertPressDoesNotGoOff(helper, fixture, charge, pressedName);
                    helper.assertTrue("buffered".equals(outcome),
                            "1.16 let nothing go off while Close Wounds was charged and owner rule N2 keeps the press: "
                                    + pressedName + " " + outcome);
                }
                helper.assertTrue(StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_STRING) == null,
                        "A user string flew during the Close Wounds charge");
                awaitThat(helper, fixture, 12, () -> !fixture.owner.hasEffect(ModStatusEffects.BLEEDING),
                        "Close Wounds did not fire with the presses waiting", () -> {});
            });
        });
    }

    /*
     * 1.16 stone_free_user_string_attack (StoneFreeUserStringAttack extends StringAction) was a StandAction, never a
     * Stand task: pressed while the Stand was in a heavy punch windup it cannot leave, the string flew at once from the
     * user's power, performed by the Stand, and the heavy punch went on.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void aUserStringGoesOffAtOnceBesideTheStandsHeavyPunchAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringAbility attack = ability(helper, fixture, StoneFreeAddon.USER_STRING_ATTACK_NAME);
            Ability heavy = fixture.power.getAbility("heavy_punch");
            helper.assertTrue(heavy != null && heavy.checkConditions(fixture.power).isPositive(),
                    "Setup: Stone Free's heavy punch was refused");
            BufferingState heavyPress = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(PUNCH_KEY, heavy, fixture.owner, null, InputMethod.CLICK, 0.0F, heavyPress,
                    heavy.abilityId);
            AbilityInput.keyRelease(PUNCH_KEY, fixture.owner);
            EntityActionInstance heavyPunch = LivingComponentAction.getCurEntityAction(fixture.stand);
            helper.assertTrue(heavyPress.isActionSuccess && heavyPunch != null && heavyPunch.ability == heavy
                            && heavyPunch.getPhase() == ActionPhase.WINDUP && !heavyPunch.canBeCancelledInto(attack),
                    "Setup: the Stand's heavy punch did not start a windup it cannot leave: " + heavyPunch);
            helper.assertTrue(attack.checkConditions(fixture.power).isPositive(),
                    "Setup: the user string attack was refused during the heavy punch windup");
            BufferingState thrown = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(OTHER_KEY, attack, fixture.owner, null, InputMethod.CLICK, 0.0F, thrown,
                    attack.abilityId);
            AbilityInput.keyRelease(OTHER_KEY, fixture.owner);
            EntityActionInstance string = LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND);
            helper.assertTrue(thrown.isActionSuccess && !thrown.shouldBuffer && string != null
                            && string.ability == attack && string.getPerformer() == fixture.stand
                            && LivingComponentAction.getCurEntityAction(fixture.stand) == heavyPunch
                            && !heavyPunch.isOver(),
                    "1.16 threw the user string at once beside the Stand's heavy punch, as its user's Stand-power"
                            + " action performed by the Stand: started " + thrown.isActionSuccess + ", buffered "
                            + thrown.shouldBuffer + ", the user's Stand-power action " + string
                            + (string != null ? " performed by " + string.getPerformer() : "")
                            + ", the Stand's action " + LivingComponentAction.getCurEntityAction(fixture.stand));
            awaitThat(helper, fixture, 6,
                    () -> StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_STRING) != null,
                    "The user string pressed during the heavy punch windup did not fly",
                    () -> helper.assertTrue(LivingComponentAction.getCurEntityAction(fixture.stand) == heavyPunch
                                    && !heavyPunch.isOver(),
                            "The heavy punch did not go on beside the user string: " + heavyPunch.getPhase()));
        });
    }

    // The network path of a press during the hold: refused by its conditions, without a message as 1.16's
    // PowerBaseImpl.checkRequirements, or buffered; either way nothing starts and the hold goes on.
    private static String assertPressDoesNotGoOff(GameTestHelper helper, Fixture fixture, EntityActionInstance held,
            String pressedName) {
        Ability pressed = fixture.power.getAbility(pressedName);
        helper.assertTrue(pressed != null, "Stone Free has no " + pressedName);
        var resolved = fixture.power.updateAvailableMoves().getContextVariationContainer(pressed);
        EntityActionInstance standBefore = LivingComponentAction.getCurEntityAction(fixture.stand);
        String outcome;
        if (resolved == null || !resolved.conditionCheck.isPositive()) {
            helper.assertTrue(resolved == null || resolved.conditionCheck.getWarning() == null,
                    pressedName + " was refused during the hold with a message 1.16 did not show: "
                            + resolved.conditionCheck.getWarning());
            outcome = "refused";
        }
        else {
            BufferingState state = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(OTHER_KEY, resolved.ability, fixture.owner, null, InputMethod.CLICK, 0.0F, state,
                    pressed.abilityId);
            AbilityInput.keyRelease(OTHER_KEY, fixture.owner);
            helper.assertTrue(!state.isActionSuccess,
                    "1.16 let no other action go off while Close Wounds was charged: " + pressedName
                            + " (as " + resolved.ability.name() + ") started");
            outcome = state.shouldBuffer ? "buffered" : "dropped";
        }
        EntityActionInstance standAfter = LivingComponentAction.getCurEntityAction(fixture.stand);
        helper.assertTrue(!held.isOver()
                        && LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND) == held
                        && standAfter == standBefore,
                pressedName + " pressed during the Close Wounds charge changed the actions: the hold over "
                        + held.isOver() + ", the Stand's action " + standAfter);
        return outcome;
    }

    /*
     * 1.16 StandEntityAction.AbstractBuilder standRecoveryDuration 0 (:596): the user's strings were StandActions with
     * no Stand task, and stone_free_attack_binding, stone_free_grapple_entity and stone_free_extended_punch set no
     * standRecoveryTicks (017 InitStands:164-172). Letting go of the held Stand grapple moved its task to a 0-tick
     * recovery (StandEntityAction.stoppedHolding), so it ended at once.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void stringsAndTheExtendedPunchHaveNoRecoveryAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            for (String name : List.of(StoneFreeAddon.USER_STRING_ATTACK_NAME, StoneFreeAddon.USER_STRING_SWEEP_NAME,
                    StoneFreeAddon.USER_STRING_WHIP_NAME, StoneFreeAddon.STRING_BIND_NAME,
                    StoneFreeAddon.USER_STRING_BIND_NAME, StoneFreeAddon.USER_STRING_PICK_NAME,
                    StoneFreeAddon.USER_GRAPPLE_NAME, StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME,
                    StoneFreeAddon.USER_RECOVER_STRING_NAME, StoneFreeAddon.GRAPPLE_ENTITY_NAME,
                    StoneFreeAddon.USER_CLOSE_WOUNDS_NAME, StoneFreeAddon.USER_BARRIER_NAME,
                    StoneFreeAddon.USER_REMOVE_BARRIER_NAME, StoneFreeAddon.USER_MOBIUS_STRIP_NAME,
                    StoneFreeAddon.EXTENDED_PUNCH_NAME)) {
                helper.assertTrue(fixture.power.getAbility(name) instanceof EntityActionType,
                        "Stone Free has no action " + name);
                EntityActionType type = (EntityActionType) fixture.power.getAbility(name);
                EntityActionInstance action = type.createActionObj();
                type.initActionFromConfig(action, helper.getLevel(), fixture.owner, fixture.stand);
                helper.assertTrue(action.phasesLength.getFloat(ActionPhase.RECOVERY) == 0.0F,
                        "1.16 " + name + " had no recovery (no standRecoveryTicks, or no Stand task), the port has "
                                + action.phasesLength.getFloat(ActionPhase.RECOVERY));
            }
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.stand.position().add(0.0D, 10.0D, 0.0D));
            LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(new ActionTarget(target));
            StoneFreeStringAbility grapple = ability(helper, fixture, StoneFreeAddon.GRAPPLE_ENTITY_NAME);
            EntityActionInstance held = hold(helper, fixture, grapple, StoneFreeBarrierGameTests.GRAPPLE_KEY);
            StoneFreeBarrierGameTests.later(helper, fixture, 3, () -> {
                helper.assertTrue(running(fixture, grapple) == held && held.getPhase() == ActionPhase.PERFORM,
                        "Setup: the Stand's grapple was not held: phase " + held.getPhase());
                AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
                helper.assertTrue(held.isOver() && running(fixture, grapple) == null,
                        "1.16 ended the released Stand grapple at once (a 0-tick recovery): over " + held.isOver()
                                + " in " + held.getPhase());
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    private static final short PUNCH_KEY = 3;
    private static final short OTHER_KEY = 4;

    // A click pressed during the hold waits in the input buffer: nothing starts and the hold goes on.
    private static Ability assertPressHeldBack(GameTestHelper helper, Fixture fixture, EntityActionInstance held,
            String pressedName, String holdName) {
        Ability pressed = fixture.power.getAbility(pressedName);
        helper.assertTrue(pressed != null && pressed.checkConditions(fixture.power).isPositive(),
                "Setup: " + pressedName + " was refused during " + holdName);
        BufferingState state = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(OTHER_KEY, pressed, fixture.owner, null, InputMethod.CLICK, 0.0F, state,
                pressed.abilityId);
        AbilityInput.keyRelease(OTHER_KEY, fixture.owner);
        EntityActionInstance userAction = LivingComponentAction.getCurEntityAction(fixture.owner);
        helper.assertTrue(!state.isActionSuccess && state.shouldBuffer && !held.isOver()
                        && running(fixture, (Ability) held.ability) == held && userAction == null,
                "1.16 let no other action go off while " + holdName + " was held (Action.heldAllowsOtherAction"
                        + " false): " + pressedName + " started " + state.isActionSuccess + ", buffered "
                        + state.shouldBuffer + ", the hold over " + held.isOver() + " in " + held.getPhase()
                        + ", the user's own action " + userAction);
        return pressed;
    }

    private static void awaitThat(GameTestHelper helper, Fixture fixture, int ticksLeft,
            BooleanSupplier condition, String failure, Runnable then) {
        whenThat(helper, fixture, ticksLeft, condition, failure, () -> {
            then.run();
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    private static void whenThat(GameTestHelper helper, Fixture fixture, int ticksLeft,
            BooleanSupplier condition, String failure, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        helper.assertTrue(ticksLeft > 0, failure);
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> whenThat(helper, fixture, ticksLeft - 1, condition, failure, then));
    }

    private static StoneFreeStringAbility ability(GameTestHelper helper, Fixture fixture, String name) {
        Ability ability = fixture.power.getAbility(name);
        helper.assertTrue(ability instanceof StoneFreeStringAbility, "Stone Free has no registered " + name);
        return (StoneFreeStringAbility) ability;
    }

    private static EntityActionInstance hold(GameTestHelper helper, Fixture fixture, StoneFreeStringAbility ability,
            short key) {
        StoneFreeBarrierGameTests.assertTicksEntities(helper, fixture);
        helper.assertTrue(ability.checkConditions(fixture.power).isPositive(),
                "Registered " + ability.name() + " refused the fixture");
        BufferingState buffering = BufferingState.clickOnly();
        AbilityInput.keyPress(key, ability, fixture.owner, null, InputMethod.HOLD, 0.0F, buffering,
                ability.abilityId);
        helper.assertTrue(buffering.isActionSuccess, "Public hold of " + ability.name() + " did not start");
        EntityActionInstance action = running(fixture, ability);
        helper.assertTrue(action != null, "The hold of " + ability.name() + " set no action");
        return action;
    }

    // The user's strings (1.16 StandActions) run in its Stand-power slot, the Stand's own in the Stand's slot.
    private static EntityActionInstance running(Fixture fixture, Ability ability) {
        LivingEntity controller = StoneFreeServices.controller(fixture.owner);
        for (EntityActionInstance action : new EntityActionInstance[] {
                LivingComponentAction.getCurEntityAction(fixture.owner, PowerClass.STAND),
                LivingComponentAction.getCurEntityAction(fixture.owner),
                controller != null ? LivingComponentAction.getCurEntityAction(controller) : null}) {
            if (action != null && !action.isOver() && action.ability == ability) {
                return action;
            }
        }
        return null;
    }

    private static boolean holdsKey(Fixture fixture) {
        return !fixture.owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.isEmpty();
    }

    private static void assertWarning(GameTestHelper helper, ConditionCheck check, String warning, String what) {
        Component expected = ConditionCheck.message(warning);
        helper.assertTrue(!check.isPositive() && expected.equals(check.getWarning()),
                what + " did not fail with " + warning + ": positive=" + check.isPositive() + ", warning "
                        + check.getWarning());
    }
}
