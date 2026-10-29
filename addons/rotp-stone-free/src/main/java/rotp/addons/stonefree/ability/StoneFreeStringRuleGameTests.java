package rotp.addons.stonefree.ability;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stonefree.StoneFreeAddon;
import rotp.addons.stonefree.StoneFreeServices;
import rotp.addons.stonefree.ability.StoneFreeBarrierGameTests.Fixture;
import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity.Kind;
import rotp.addons.stonefree.state.StoneFreeState;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/** As in the grapple tests, every string here flies straight up, clear of the neighbouring test plots. */
@GameTestHolder("rotp_sf_barrier_review")
@PrefixGameTestTemplate(false)
public final class StoneFreeStringRuleGameTests {
    // 1.16 StringAction: the user's attack, sweep, whip, bind, pick and both grapples.
    private static final List<String> USER_STRINGS = List.of(
            StoneFreeAddon.USER_STRING_ATTACK_NAME, StoneFreeAddon.USER_STRING_SWEEP_NAME,
            StoneFreeAddon.USER_STRING_WHIP_NAME, StoneFreeAddon.USER_STRING_BIND_NAME,
            StoneFreeAddon.USER_STRING_PICK_NAME, StoneFreeAddon.USER_GRAPPLE_NAME,
            StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME);
    private static final List<String> GRAPPLES = List.of(
            StoneFreeAddon.USER_GRAPPLE_NAME, StoneFreeAddon.USER_GRAPPLE_ENTITY_NAME,
            StoneFreeAddon.GRAPPLE_ENTITY_NAME);
    // 1.16 SF(U)StringEntity.ticksLifespan: the 24 ticks of a binding string and 25 more once it holds a catch.
    private static final int DONOR_HOLDING_LIFESPAN = 49;

    private StoneFreeStringRuleGameTests() {}

    // 1.16 stone_free_barrage registered its cry with the one-argument standSound (playInArmsOnly true): an
    // arms-only Stone Free still cried.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 40)
    public static void barrageCriesInArmsOnlyMode(GameTestHelper helper) {
        Fixture fixture = StoneFreeBarrierGameTests.fixture(helper, true);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            Ability barrage = fixture.power.getAbility("barrage");
            helper.assertTrue(barrage instanceof EntityActionType, "Stone Free has no registered barrage");
            EntityActionInstance action = ((EntityActionType) barrage).createActionObj();
            boolean full;
            boolean armsOnly;
            try {
                Method playsCry = StandEntityBarrage.class.getDeclaredMethod(
                        "playsBarrageCry", Level.class, StandEntity.class);
                playsCry.setAccessible(true);
                full = (Boolean) playsCry.invoke(action, fixture.stand.level(), fixture.stand);
                fixture.stand.setArmsOnlyMode(true, true);
                armsOnly = (Boolean) playsCry.invoke(action, fixture.stand.level(), fixture.stand);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("The core barrage cry hook is missing", e);
            }
            helper.assertTrue(full, "The fully summoned Stone Free does not cry");
            helper.assertTrue(armsOnly,
                    "The arms-only Stone Free barrage stayed silent; 1.16 stone_free_barrage cried there");
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    // 1.16 StringAction.checkSpecificConditions with LivingUtilCap.userString: no user string while one of the
    // user's strings is within 16 blocks, or one of the user's grapple strings within 96.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void aUserStringStillOutRefusesEveryUserString(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            helper.assertTrue(refused(fixture, USER_STRINGS).isEmpty() && usable(fixture, StoneFreeAddon.STRING_BIND_NAME),
                    "The fixture refused a string before any was thrown: " + refused(fixture, USER_STRINGS)
                            + ", Stand bind usable=" + usable(fixture, StoneFreeAddon.STRING_BIND_NAME));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_ATTACK_NAME);
            StoneFreeBarrierGameTests.later(helper, fixture, 3, () -> {
                StoneFreeStringEntity attack = StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_STRING);
                helper.assertTrue(attack != null && attack.distanceTo(fixture.owner) < 16.0F,
                        "The user's attack string was not out near its user 3 ticks after the press: " + attack);
                List<String> stillUsable = usable(fixture, USER_STRINGS);
                helper.assertTrue(stillUsable.isEmpty(),
                        "With the user's attack string still out these user strings could be thrown: "
                                + stillUsable);
                helper.assertTrue(warnings(fixture, USER_STRINGS).isEmpty(),
                        "A string refused for a string that is out showed a message; 1.16 refused silently: "
                                + warnings(fixture, USER_STRINGS));
                helper.assertTrue(usable(fixture, StoneFreeAddon.STRING_BIND_NAME),
                        "The Stand's own bind was refused for a user string that is out");
                StoneFreeBarrierGameTests.later(helper, fixture, 25, () -> {
                    helper.assertTrue(StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_STRING) == null
                                    && refused(fixture, USER_STRINGS).isEmpty(),
                            "The user strings did not come back once the attack string was gone: refused "
                                    + refused(fixture, USER_STRINGS));
                    StoneFreeGrappleGameTests.anchorAbove(helper, fixture, 34.0D);
                    StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_GRAPPLE_NAME);
                    // By now the grapple's end has flown out of the 16 blocks that count for the other strings.
                    StoneFreeBarrierGameTests.later(helper, fixture, 8, () -> {
                        StoneFreeStringEntity grapple =
                                StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_GRAPPLE);
                        helper.assertTrue(grapple != null && grapple.distanceTo(fixture.owner) > 18.0F
                                        && grapple.distanceTo(fixture.owner) < 96.0F,
                                "The user's grapple string was not out between 18 and 96 blocks from its user"
                                        + " 8 ticks after the press: " + grapple);
                        List<String> withGrapple = usable(fixture, USER_STRINGS);
                        helper.assertTrue(withGrapple.isEmpty(),
                                "With the user's grapple string still out these user strings could be thrown: "
                                        + withGrapple);
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
                });
            });
        });
    }

    // 1.16 StringAction and StoneFreeGrapple.checkSpecificConditions: placed barriers >= MaxVarietyOfBarriers,
    // which is 100, or 90 under the Mobius Strip effect.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void grapplesAreRefusedWithTheStringLimitMessageAtTheDonorLimit(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeState state = StoneFreeState.get(fixture.owner);
            state.investString(fixture.owner, 99, 100);
            helper.assertTrue(state.placedStrings() == 99 && refused(fixture, GRAPPLES).isEmpty(),
                    "With 99 strings placed, one below the limit, these grapples were refused: "
                            + refused(fixture, GRAPPLES));
            state.investString(fixture.owner, 1, 100);
            helper.assertTrue(state.placedStrings() == 100 && withStringLimit(fixture).equals(GRAPPLES),
                    "With 100 strings placed not all three grapples were refused with the string_limit message:"
                            + " refused with it " + withStringLimit(fixture) + ", still usable "
                            + usable(fixture, GRAPPLES));
            for (int released = 0; released < 5; released++) {
                state.releaseString(fixture.owner, false);
            }
            // The limit follows the Mobius Strip effect, not the Mobius mode switch.
            state.setMobiusMode(fixture.owner, true);
            helper.assertTrue(state.placedStrings() == 95 && refused(fixture, GRAPPLES).isEmpty(),
                    "With 95 strings placed and Mobius mode on, but no Mobius Strip effect, these grapples were"
                            + " refused: " + refused(fixture, GRAPPLES));
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(StoneFreeAddon.MOBIUS_STRIP, 200)),
                    "Could not give the user the Mobius Strip effect");
            helper.assertTrue(withStringLimit(fixture).equals(GRAPPLES),
                    "With 95 strings placed under the Mobius Strip effect (limit 90) not all three grapples were"
                            + " refused with the string_limit message: refused with it "
                            + withStringLimit(fixture));
            for (int released = 0; released < 6; released++) {
                state.releaseString(fixture.owner, false);
            }
            helper.assertTrue(state.placedStrings() == 89 && refused(fixture, GRAPPLES).isEmpty(),
                    "With 89 strings placed under the Mobius Strip effect these grapples were refused: "
                            + refused(fixture, GRAPPLES));
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    // 1.16 StringAction, StoneFreeUserCloseWounds and StoneFreeStringCapture warned string_limit at
    // MaxVarietyOfBarriers (100, 90 under the Mobius Strip effect) and StoneFreeStringBind at a flat 100, whatever
    // the Resolve level; StoneFreeUserBarrier warned 'barrier' at getMaxBarriersPlaceable (Resolve 4 or more:
    // MaxVarietyOfBarriers, else 25). The Mobius mode switch counts only through the effect it brings.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 60)
    public static void stringLimitsFollowTheDonorThresholds(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeState state = StoneFreeState.get(fixture.owner);
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(ModStatusEffects.BLEEDING, 600)),
                    "Could not give the user a wound to close");
            fixture.power.setResolveLevel(3);
            setPlaced(state, fixture, 24);
            assertLimits(helper, fixture, "Resolve 3, 24 strings", List.of(), false);
            setPlaced(state, fixture, 25);
            assertLimits(helper, fixture, "Resolve 3, 25 strings", List.of(), true);
            fixture.power.setResolveLevel(4);
            setPlaced(state, fixture, 99);
            assertLimits(helper, fixture, "Resolve 4, 99 strings", List.of(), false);
            setPlaced(state, fixture, 100);
            assertLimits(helper, fixture, "Resolve 4, 100 strings", ALL_LIMITED, true);
            state.setMobiusMode(fixture.owner, true);
            setPlaced(state, fixture, 95);
            assertLimits(helper, fixture, "Resolve 4, 95 strings, Mobius mode on without the effect",
                    List.of(), false);
            helper.assertTrue(fixture.owner.addEffect(new MobEffectInstance(StoneFreeAddon.MOBIUS_STRIP, 200)),
                    "Could not give the user the Mobius Strip effect");
            assertLimits(helper, fixture, "Resolve 4, 95 strings under the Mobius Strip effect",
                    MOBIUS_ALL_LIMITED, true);
            setPlaced(state, fixture, 89);
            assertLimits(helper, fixture, "Resolve 4, 89 strings under the Mobius Strip effect", List.of(), false);
            fixture.power.setResolveLevel(3);
            setPlaced(state, fixture, 25);
            assertLimits(helper, fixture, "Resolve 3, 25 strings under the Mobius Strip effect", List.of(), true);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    // 1.16 StringAction (user attack, sweep, whip, bind, pick), StoneFreeUserCloseWounds, StoneFreeStringCapture
    // and StoneFreeStringBind.
    private static final List<String> LIMITED_STRINGS = List.of(
            StoneFreeAddon.USER_STRING_ATTACK_NAME, StoneFreeAddon.USER_STRING_SWEEP_NAME,
            StoneFreeAddon.USER_STRING_WHIP_NAME, StoneFreeAddon.USER_STRING_BIND_NAME,
            StoneFreeAddon.USER_STRING_PICK_NAME, StoneFreeAddon.USER_CLOSE_WOUNDS_NAME,
            StoneFreeAddon.STRING_CAPTURE_NAME, StoneFreeAddon.STRING_BIND_NAME);
    private static final List<String> MOBIUS_LIMITED_STRINGS = LIMITED_STRINGS.stream()
            .filter(name -> !name.equals(StoneFreeAddon.STRING_BIND_NAME)).toList();
    // 1.16 StoneFreeUserGrapple extends StringAction and StoneFreeGrapple has the same check: the grapples follow
    // the effect-dependent limit, never the flat 100 of the Stand's bind.
    private static final List<String> ALL_LIMITED = java.util.stream.Stream.concat(
            LIMITED_STRINGS.stream(), GRAPPLES.stream()).toList();
    private static final List<String> MOBIUS_ALL_LIMITED = java.util.stream.Stream.concat(
            MOBIUS_LIMITED_STRINGS.stream(), GRAPPLES.stream()).toList();

    private static void setPlaced(StoneFreeState state, Fixture fixture, int placed) {
        while (state.placedStrings() > 0) {
            state.releaseString(fixture.owner, false);
        }
        state.investString(fixture.owner, placed, 100);
    }

    private static void assertLimits(GameTestHelper helper, Fixture fixture, String when,
            List<String> expectedLimited, boolean barrierRefused) {
        Component limit = ConditionCheck.message("string_limit");
        List<String> limited = ALL_LIMITED.stream().filter(name -> {
            ConditionCheck check = fixture.power.getAbility(name).checkConditions(fixture.power);
            return !check.isPositive() && limit.equals(check.getWarning());
        }).toList();
        helper.assertTrue(limited.equals(expectedLimited),
                "With " + when + " 1.16 warned string_limit for " + expectedLimited + ", the port for " + limited);
        ConditionCheck barrier = fixture.power.getAbility(StoneFreeAddon.USER_BARRIER_NAME)
                .checkConditions(fixture.power);
        String barrierWarning = barrier.isPositive() ? "a positive check"
                : barrier.getWarning() != null ? barrier.getWarning().getString() : "a silent refusal";
        helper.assertTrue(barrierRefused
                        ? !barrier.isPositive() && ConditionCheck.message("barrier").equals(barrier.getWarning())
                        : barrier.isPositive(),
                "With " + when + " 1.16 answered the barrier with "
                        + (barrierRefused ? "'barrier'" : "a positive check") + ", the port with " + barrierWarning);
    }

    // 1.16 StoneFreeStringPunch.standTickWindup through the real tick (the hand-driven stringPunchSlidesToItsTargetAndStops
    // in the offset tests checks only the formula): from the press to the end of the action the Stand moves once, by the
    // task target's eyes less the Stand's width at most 1.5, and is left with no velocity.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 200)
    public static void stringPunchSlidesOnceToAFarTargetThroughTheRealTick(GameTestHelper helper) {
        runSlide(helper, 5.0D, 1.49D, 1.51D);
    }

    // The same toward a target closer than the 1.5 slide: its distance less the Stand's width.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 200)
    public static void stringPunchSlidesOnlyToAnArmsLengthTargetThroughTheRealTick(GameTestHelper helper) {
        runSlide(helper, 1.0D, 0.2D, 1.4D);
    }

    private static void runSlide(GameTestHelper helper, double eyesAbove, double minSlide, double maxSlide) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            stand.setNoGravity(true);
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    stand.position().add(0.0D, stand.getEyeHeight() + eyesAbove, 0.0D));
            target.setPos(target.getX(), target.getY() - target.getEyeHeight(), target.getZ());
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            stand.setFinisherMeter(1.0F);
            press(helper, fixture, StoneFreeAddon.STRING_PUNCH_NAME);
            watchSlide(helper, fixture, stand, target, new SlideRun(), minSlide, maxSlide, 60);
        });
    }

    private static final class SlideRun {
        Vec3 baseline;
        Vec3 expected;
        Vec3 last;
        boolean sawAction;
        String held;
    }

    private static void watchSlide(GameTestHelper helper, Fixture fixture, StoneFreeStandEntity stand,
            PolarBear target, SlideRun run, double minSlide, double maxSlide, int ticksLeft) {
        EntityActionInstance action = stand.getCurStandAction();
        if (action instanceof StoneFreeStringPunchAbility.StringPunchAction punch) {
            run.sawAction = true;
            if (run.baseline == null && punch.getPhase() == ActionPhase.WINDUP && punch.getPhaseTicksLeft() > 2.0F
                    && punch.getPhaseTicksLeft() <= 4.0F) {
                // Still in the part of the windup before the slide: the Stand is where it stands and has no velocity.
                helper.assertTrue(stand.getDeltaMovement().lengthSqr() == 0.0D,
                        "The Stand had a velocity " + stand.getDeltaMovement() + " with " + punch.getPhaseTicksLeft()
                                + " windup ticks left; 1.16 slid only from the second-to-last tick");
                run.baseline = stand.position();
                run.held = String.valueOf(punch.getTaskTarget(punch.level()));
                Vec3 toTarget = target.getEyePosition().subtract(stand.getEyePosition());
                run.expected = toTarget.normalize().scale(Mth.clamp(toTarget.length() - stand.getBbWidth(), 0.0D, 1.5D));
            }
            else if (run.baseline != null) {
                run.last = stand.position();
                if (punch.getPhase() != ActionPhase.WINDUP) {
                    helper.assertTrue(stand.getDeltaMovement().lengthSqr() == 0.0D,
                            "The Stand kept a velocity " + stand.getDeltaMovement() + " in " + punch.getPhase()
                                    + "; 1.16 zeroed it with 1 windup tick left");
                }
            }
        }
        else if (run.sawAction) {
            helper.assertTrue(run.baseline != null && run.last != null,
                    "The string punch ended before its last windup ticks were seen");
            helper.assertTrue(run.expected.length() >= minSlide && run.expected.length() <= maxSlide,
                    "setup: the slide of " + run.expected.length() + " blocks is not within " + minSlide + " to " + maxSlide + ", task target " + run.held + ", target at " + target.position() + " eyes " + target.getEyePosition() + ", baseline " + run.baseline + " stand width " + stand.getBbWidth() + " eye " + stand.getEyePosition());
            Vec3 moved = run.last.subtract(run.baseline);
            helper.assertTrue(moved.distanceTo(run.expected) < 0.01D,
                    "1.16 StoneFreeStringPunch.standTickWindup moved the Stand once by " + run.expected + " ("
                            + run.expected.length() + " blocks) over the whole string punch, the port by " + moved + ", task target " + run.held);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(ticksLeft > 0, "The string punch did not run out: " + action);
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchSlide(helper, fixture, stand, target, run, minSlide, maxSlide, ticksLeft - 1));
    }

    // 1.16 SFUStringEntity.afterEntityHit: the user's bind immobilises its catch for what is left of the string's
    // life, which is 25 ticks longer once it holds a catch, and never pulls it.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBindImmobilisesItsCatchForTheStringsLifeWithoutPullingIt(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.owner.position().add(0.0D, 8.0D, 0.0D));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.USER_STRING, 16,
                    string -> string.getEntityAttachedTo() == target, string -> {
                        MobEffectInstance hold = target.getEffect(ModStatusEffects.IMMOBILIZE);
                        // The effect and the string each count one tick; which of the two ticked last is open.
                        helper.assertTrue(hold != null
                                        && Math.abs(hold.getDuration() + string.tickCount - DONOR_HOLDING_LIFESPAN) <= 1,
                                "The user's bind did not immobilise its catch for the rest of the string's 49"
                                        + " ticks: effect " + hold + ", string age " + string.tickCount);
                        target.setDeltaMovement(Vec3.ZERO);
                        Vec3 before = target.position();
                        string.tick();
                        helper.assertTrue(string.isAlive() && target.getDeltaMovement().lengthSqr() == 0.0D
                                        && target.position().equals(before),
                                "The user's bind pulled its catch; 1.16 only held it: motion "
                                        + target.getDeltaMovement() + ", moved "
                                        + target.position().subtract(before));
                        assertHoldingLifespan(helper, string, "The user's bind");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // 1.16 SFStringEntity.tick for the strings of StoneFreeStringCapture: the catch is dragged to the Stand at
    // 1.0 a tick; within 4 blocks of a Stand that has a user it is immobilised for what is left of the string's
    // life, and the string then only holds on.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 200)
    public static void captureStringDragsItsCatchToTheStandAndImmobilisesItWithinFourBlocks(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    stand.position().add(0.0D, 10.0D, 0.0D));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            stand.setFinisherMeter(1.0F);
            press(helper, fixture, StoneFreeAddon.STRING_PUNCH_NAME);
            helper.assertTrue(stand.getCurStandAction() instanceof StoneFreeStringPunchAbility.StringPunchAction,
                    "The string punch did not become the Stand's action: " + stand.getCurStandAction());
            press(helper, fixture, StoneFreeAddon.STRING_CAPTURE_NAME);
            awaitCatch(helper, fixture, target, 80, string -> {
                target.setPos(stand.position().add(0.0D, 6.0D, 0.0D));
                target.setDeltaMovement(Vec3.ZERO);
                string.tick();
                Vec3 unitPull = stand.position().subtract(target.position()).normalize();
                helper.assertTrue(string.isAlive() && target.getDeltaMovement().distanceTo(unitPull) < 1.0E-6D
                                && !target.hasEffect(ModStatusEffects.IMMOBILIZE),
                        "A capture string did not just drag a catch 6 blocks from the Stand at 1.0 a tick:"
                                + " motion " + target.getDeltaMovement() + " (speed "
                                + target.getDeltaMovement().length() + "), expected " + unitPull
                                + ", immobilised=" + target.hasEffect(ModStatusEffects.IMMOBILIZE));
                target.setPos(stand.position().add(0.0D, 4.1D, 0.0D));
                string.tick();
                helper.assertTrue(Math.abs(target.getDeltaMovement().length() - 1.0D) < 1.0E-6D
                                && !target.hasEffect(ModStatusEffects.IMMOBILIZE),
                        "A capture string stopped dragging a catch still 4.1 blocks from the Stand: motion "
                                + target.getDeltaMovement() + ", immobilised="
                                + target.hasEffect(ModStatusEffects.IMMOBILIZE));
                target.setPos(stand.position().add(0.0D, 3.9D, 0.0D));
                string.tick();
                MobEffectInstance hold = target.getEffect(ModStatusEffects.IMMOBILIZE);
                helper.assertTrue(string.isAlive() && hold != null
                                && hold.getDuration() == DONOR_HOLDING_LIFESPAN - string.tickCount
                                && target.getDeltaMovement().lengthSqr() == 0.0D,
                        "A capture string did not immobilise a catch 3.9 blocks from the Stand for the rest of"
                                + " its 49 ticks: effect " + hold + ", string age " + string.tickCount
                                + ", motion " + target.getDeltaMovement());
                Vec3 held = stand.position().add(0.0D, 8.0D, 0.0D);
                target.setPos(held);
                string.tick();
                helper.assertTrue(target.position().equals(held) && target.getDeltaMovement().lengthSqr() == 0.0D,
                        "A capture string went on dragging after it had immobilised its catch: moved "
                                + target.position().subtract(held) + ", motion " + target.getDeltaMovement());
                assertHoldingLifespan(helper, string, "A capture string");
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    /*
     * 1.16 StoneFreeStringCapture was a StandEntityActionModifier (017 :52-53), a StandAction the heavy key gave during
     * the string punch (StoneFreeStringPunch.getRecoveryFollowup): it joined the running punch at the click and was
     * never queued behind it, so the HUD drew it by its own requirements, not with the queued tint. Its press joins the
     * punch at once in the port too (queueCapture), so its slot is not drawn queued either.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void captureSlotIsDrawnUsableDuringTheStringPunchAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            stand.setFinisherMeter(1.0F);
            press(helper, fixture, StoneFreeAddon.STRING_PUNCH_NAME);
            helper.assertTrue(stand.getCurStandAction() instanceof StoneFreeStringPunchAbility.StringPunchAction punch
                            && punch.getPhase() == ActionPhase.WINDUP,
                    "Setup: the string punch is not the Stand's action in its windup: " + stand.getCurStandAction());
            var moves = new rotp.core.powersystem.ability.condition.AvailableAbilities();
            moves.update(fixture.power, fixture.power.getMoveset());
            var heavyKey = moves.getContextVariationContainer(fixture.power.getAbility("heavy_punch"));
            helper.assertTrue(heavyKey != null && StoneFreeAddon.STRING_CAPTURE_NAME.equals(heavyKey.ability.name())
                            && heavyKey.conditionCheck.isPositive(),
                    "Setup: the heavy key does not give the capture during the string punch: "
                            + (heavyKey != null ? heavyKey.ability.name() + " " + heavyKey.conditionCheck.isPositive()
                                    : "nothing"));
            ConditionCheck drawn = heavyKey.drawnCondition(fixture.owner);
            helper.assertTrue(drawn == ConditionCheck.POSITIVE,
                    "1.16 drew the capture usable during the string punch, which it joined at the click; the port "
                            + "draws its slot " + (drawn == ConditionCheck.NEGATIVE_QUEUED ? "queued (dimmed)"
                                    : drawn.isPositive() ? "usable" : "refused"));
            BufferingState buffering = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(StoneFreeBarrierGameTests.GRAPPLE_KEY, heavyKey.ability, fixture.owner, null,
                    InputMethod.CLICK, 0.0F, buffering, fixture.power.getAbility("heavy_punch").abilityId);
            AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
            helper.assertTrue(buffering.isActionSuccess && !buffering.shouldBuffer
                            && stand.getCurStandAction() instanceof StoneFreeStringPunchAbility.StringPunchAction running
                            && !running.canQueueCapture(),
                    "Setup: the capture did not join the running string punch at the press: went off "
                            + buffering.isActionSuccess + ", buffered " + buffering.shouldBuffer);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    // 1.16 SFUStringEntity.shouldHurtThroughInvulTicks is true: a punch just before the bind does not save its
    // target from the catch.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBindCatchesATargetInsideItsHurtCooldown(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.owner.position().add(0.0D, 8.0D, 0.0D));
            primeHurtCooldown(helper, target, 3.0F);
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_BIND_NAME);
            awaitCatchInsideCooldown(helper, fixture, target, Kind.USER_STRING, false, 20, string -> {
                helper.assertTrue(target.hasEffect(ModStatusEffects.IMMOBILIZE),
                        "The user's bind caught a target inside its hurt cooldown without immobilising it");
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    // 1.16 SFStringEntity.shouldHurtThroughInvulTicks is true as well; the Stand's bind does 0 damage, which a
    // hurt cooldown would always refuse.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standBindCatchesATargetInsideItsHurtCooldown(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    stand.position().add(0.0D, 10.0D, 0.0D));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            primeHurtCooldown(helper, target, 3.0F);
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            awaitCatchInsideCooldown(helper, fixture, target, Kind.STAND_STRING, false, 30,
                    string -> StoneFreeBarrierGameTests.succeed(helper, fixture));
        });
    }

    // 1.16 SFUStringEntity.hurtTarget: the bind's first contact attaches and returns true without a hurt. The
    // string belonged to the user, not to a Stand, so DamagingEntity.onHitEntity marked nothing either.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBindCatchesWithoutHurtingOrMarkingItsCatch(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.owner.position().add(0.0D, 8.0D, 0.0D));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.USER_STRING, 16,
                    string -> string.getEntityAttachedTo() == target, string -> {
                        assertCaughtUnhurt(helper, target, "The user's bind");
                        helper.assertTrue(target.getLastHurtByMob() == null,
                                "The user's bind marked its catch as hurt; in 1.16 only a string a Stand threw"
                                        + " did: lastHurtByMob=" + target.getLastHurtByMob());
                        helper.assertTrue(target.hasEffect(ModStatusEffects.IMMOBILIZE),
                                "The user's bind caught a target without immobilising it");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // The same contact never asks whether the target can be hurt.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBindCatchesATargetThatCannotBeHurt(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = unhurtableTarget(helper, fixture, fixture.owner.position().add(0.0D, 8.0D, 0.0D));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.USER_STRING, 16,
                    string -> string.getEntityAttachedTo() == target, string -> {
                        helper.assertTrue(target.hasEffect(ModStatusEffects.IMMOBILIZE),
                                "The user's bind caught a target that cannot be hurt without immobilising it");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // 1.16 SFStringEntity.hurtTarget: the same for the Stand's string, whose true result made
    // DamagingEntity.onHitEntity mark the catch as hurt by the Stand's user.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standBindCatchesWithoutHurtingAndMarksItsCatchAsHurtByTheUser(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    stand.position().add(0.0D, 10.0D, 0.0D));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.STAND_STRING, 30,
                    string -> string.getEntityAttachedTo() == target, string -> {
                        assertCaughtUnhurt(helper, target, "The Stand's bind");
                        helper.assertTrue(target.getLastHurtByMob() == fixture.owner,
                                "The Stand's bind did not mark its catch as last hurt by the Stand's user:"
                                        + " lastHurtByMob=" + target.getLastHurtByMob());
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standBindCatchesATargetThatCannotBeHurt(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            PolarBear target = unhurtableTarget(helper, fixture, stand.position().add(0.0D, 10.0D, 0.0D));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.STAND_STRING, 30,
                    string -> string.getEntityAttachedTo() == target,
                    string -> StoneFreeBarrierGameTests.succeed(helper, fixture));
        });
    }

    /*
     * 1.16 StoneFreeStringBind.getStandActionTicks: ceil(standPerformDuration(24) / max(attack speed / 8, 0.125)),
     * the bind task's perform, and addProjectile gave its string the same lifespan. Thrown at nothing, straight up,
     * so neither a catch nor the holding extra comes in.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standBindLastsItsTicksOverTheStandsAttackSpeedAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            EntityActionType bindType = (EntityActionType) fixture.power.getAbility(StoneFreeAddon.STRING_BIND_NAME);
            var attackSpeed = stand.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            double baseSpeed = attackSpeed.getBaseValue();
            for (double[] row : new double[][] {{12.0D, 16.0D}, {11.0D, 18.0D}, {8.0D, 24.0D}, {0.5D, 192.0D}}) {
                attackSpeed.setBaseValue(row[0]);
                EntityActionInstance probe = bindType.createActionObj();
                bindType.initActionFromConfig(probe, helper.getLevel(), fixture.owner, stand);
                helper.assertTrue(probe.phasesLength.getFloat(ActionPhase.PERFORM) == (float) row[1],
                        "1.16's Stand bind performed " + (int) row[1] + " ticks at attack speed " + row[0]
                                + " (Stand " + stand.getAttackSpeed() + "), the port "
                                + probe.phasesLength.getFloat(ActionPhase.PERFORM));
            }
            attackSpeed.setBaseValue(baseSpeed);
            int expected = Mth.ceil(24.0D / Math.max(stand.getAttackSpeed() / 8.0D, 0.125D));
            helper.assertTrue(expected < 24, "Setup: Stone Free's attack speed " + stand.getAttackSpeed()
                    + " gives no shorter bind");
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            EntityActionInstance bind = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(bind != null && bind.ability == bindType
                            && bind.phasesLength.getFloat(ActionPhase.PERFORM) == expected,
                    "The pressed Stand bind does not perform " + expected + " ticks: " + bind
                            + (bind != null ? " " + bind.phasesLength.getFloat(ActionPhase.PERFORM) : ""));
            trackBind(helper, fixture, bind, expected, 0, -1, null);
        });
    }

    /*
     * 1.16 StoneFreeStringBind.addProjectile (017 :79-83) set the string's lifespan before the add, and
     * OwnerBoundProjectileEntity.writeSpawnData sent it, so a client moved the string at its own speed from its first
     * tick. Here the add sends the synced data as it is when the string joins the level.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 60)
    public static void standBindStringJoinsTheLevelWithTheBindsTicksAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            int bindTicks = StoneFreeStringAbility.standBindTicks(fixture.stand);
            helper.assertTrue(bindTicks != StoneFreeStringEntity.STAND_STRING_TICKS,
                    "Setup: the Stand's bind lasts the default " + bindTicks + " ticks");
            List<Integer> joined = new java.util.ArrayList<>();
            java.util.concurrent.atomic.AtomicBoolean listening = new java.util.concurrent.atomic.AtomicBoolean(true);
            Consumer<EntityJoinLevelEvent> listener = event -> {
                if (listening.get() && event.getEntity() instanceof StoneFreeStringEntity string
                        && string.kind() == Kind.STAND_STRING
                        && StoneFreeServices.userOf(string.getOwner()) == fixture.owner) {
                    joined.add(string.ticksLifespan());
                }
            };
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, false,
                    EntityJoinLevelEvent.class, listener);
            try {
                press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            }
            catch (RuntimeException e) {
                listening.set(false);
                NeoForge.EVENT_BUS.unregister(listener);
                throw e;
            }
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.STAND_STRING, 5, string -> true, string -> {
                listening.set(false);
                NeoForge.EVENT_BUS.unregister(listener);
                helper.assertTrue(joined.equals(List.of(bindTicks)),
                        "1.16 sent the Stand bind's string with its " + bindTicks + "-tick lifespan; the port's"
                                + " joined the level (and sent its spawn) living " + joined + " ticks");
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    /*
     * 1.16 SFStringEntity.ticksLifespan (017 :129-135): once it held a catch, the Stand's bind string lived 25 ticks
     * past the bind's own ticks, as a capture string did. The port's stun and pull of that catch
     * (D-rotp-stone-free-10, kept) still end with the bind's own ticks.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standBindStringHoldsItsCatch25TicksLongerAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            int bindTicks = StoneFreeStringAbility.standBindTicks(stand);
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    stand.position().add(0.0D, 6.0D, 0.0D));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.STAND_STRING, 20,
                    string -> string.getEntityAttachedTo() == target, string -> {
                        int holding = bindTicks + StoneFreeStringEntity.HOLDING_EXTRA_TICKS;
                        helper.assertTrue(string.ticksLifespan() == holding,
                                "1.16 gave the Stand bind's string holding a catch " + bindTicks + " + 25 ticks,"
                                        + " the port " + string.ticksLifespan());
                        // StunEffect refuses a mob without AI; the bear does not tick again before the end.
                        target.setNoAi(false);
                        target.removeEffect(ModStatusEffects.STUN);
                        string.tickCount = bindTicks - 1;
                        string.tick();
                        helper.assertTrue(string.isAlive() && target.hasEffect(ModStatusEffects.STUN)
                                        && target.getDeltaMovement().lengthSqr() > 0.0D,
                                "Setup: the Stand bind's string did not stun and pull its catch in its own ticks:"
                                        + " alive " + string.isAlive() + ", stunned "
                                        + target.hasEffect(ModStatusEffects.STUN) + ", motion "
                                        + target.getDeltaMovement());
                        target.removeEffect(ModStatusEffects.STUN);
                        string.tickCount = bindTicks;
                        string.tick();
                        target.removeEffect(ModStatusEffects.STUN);
                        string.tickCount = bindTicks + 1;
                        string.tick();
                        helper.assertTrue(string.isAlive() && !target.hasEffect(ModStatusEffects.STUN)
                                        && target.getDeltaMovement().lengthSqr() == 0.0D,
                                "The Stand bind's stun and pull (D-rotp-stone-free-10) went on past the bind's "
                                        + bindTicks + " ticks: alive " + string.isAlive() + ", stunned "
                                        + target.hasEffect(ModStatusEffects.STUN) + ", motion "
                                        + target.getDeltaMovement());
                        string.tickCount = bindTicks + 10;
                        string.tick();
                        helper.assertTrue(string.isAlive() && string.getEntityAttachedTo() == target,
                                "1.16's Stand bind string still held its catch " + (bindTicks + 10) + " ticks in");
                        string.tickCount = holding;
                        string.tick();
                        helper.assertTrue(string.isAlive(),
                                "The Stand bind's string holding a catch ended before its " + holding + "th tick");
                        string.tickCount = holding + 1;
                        string.tick();
                        helper.assertTrue(!string.isAlive(),
                                "The Stand bind's string holding a catch outlived its " + holding + " ticks");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    /*
     * 1.16 SFStringEntity.movementSpeed (017 :137-139), 16 / lifespan, on OwnerBoundProjectileEntity.moveBoundToOwner
     * (1.16 core :97-229): the Stand bind's string went out along the Stand's look until half its lifespan and came
     * back as fast, gone at the lifespan's end; it reached 8 blocks times its speed factor (the Stand's attack speed
     * / 8), and its ray one step further. A mob beyond that was never caught; one within it was.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void standBindStringGoesOutAndBackAndMissesBeyondItsReachAsIn116(GameTestHelper helper) {
        runBindReach(helper, false, 0.0D);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void standBindStringCatchesWithinItsReachAsIn116(GameTestHelper helper) {
        runBindReach(helper, true, 0.0D);
    }

    /*
     * A new Stone Free (attack speed 11, 1.16 speed(11.0, 12.0)) binds for 18 ticks: 1.16's mark, a float maxDistance
     * widened to double over the float speed, is 8 steps of 0.889 x 1.375, so the string went 9.78 blocks out and was
     * back by age 16. Its ray ran one step further (11.0), so the bear stands just past that, inside the 12.2 a mark
     * of 9 would reach.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void standBindStringOfANewStoneFreeTurnsBackAfterEightStepsAsIn116(GameTestHelper helper) {
        runBindReach(helper, false, 11.0D);
    }

    // 1.16 OwnerBoundProjectileEntity.updateMotionFlags with timeAtFullLength 0: (int) (maxDistance() / speed), where
    // maxDistance() returns the float speed * speed * lifespan / (speed + speed) as a double.
    static int outSteps(int lifespan) {
        float speed = 16.0F / lifespan;
        return (int) ((double) (speed * speed * lifespan / (speed + speed)) / speed);
    }

    private static void runBindReach(GameTestHelper helper, boolean within, double attackSpeed) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            if (attackSpeed > 0.0D) {
                // The Stand takes its attack speed from its user's Stand speed every tick.
                var userSpeed = fixture.owner.getAttribute(ModEntityAttributes.STAND_SPEED);
                helper.assertTrue(userSpeed != null, "Setup: the Stand's user has no Stand speed");
                userSpeed.setBaseValue(attackSpeed);
                StandEntity.updateStandStatAttributes(stand, fixture.owner);
                helper.assertTrue(StoneFreeStringAbility.standBindTicks(stand) == 18 && outSteps(18) == 8,
                        "Setup: the Stand's attack speed " + stand.getAttackSpeed() + " does not give 1.16's 18-tick"
                                + " bind of 8 steps out: " + StoneFreeStringAbility.standBindTicks(stand));
            }
            int bindTicks = StoneFreeStringAbility.standBindTicks(stand);
            double step = 16.0D / bindTicks * (stand.getAttackSpeed() / 8.0D);
            int steps = outSteps(bindTicks);
            double reach = steps * step;
            double above = within ? reach / 2.0D : reach + step + (attackSpeed > 0.0D ? 0.6D : 2.0D);
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    new Vec3(stand.getX(), stand.getEyeY() + above, stand.getZ()));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            watchReach(helper, fixture, new Flight(Kind.STAND_STRING, "Stand bind", target, within, 2 * steps,
                    bindTicks + 10, step, reach, string -> string.getEntityAttachedTo() != null),
                    new BindReach(), 0);
        });
    }

    // 1.16 OwnerBoundProjectileEntity.afterBlockHit and breakProjectile (1.16 core :262-270): a block the Stand's
    // string met turned it back at once, and nothing it met broke it.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void standBindStringTurnsBackAtABlockAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            int bindTicks = StoneFreeStringAbility.standBindTicks(stand);
            double step = 16.0D / bindTicks * (stand.getAttackSpeed() / 8.0D);
            BlockPos roof = BlockPos.containing(stand.getX(), stand.getEyeY() + 4.0D, stand.getZ());
            fixture.setBlock(helper, roof, Blocks.STONE.defaultBlockState());
            PolarBear beyond = StoneFreeGrappleGameTests.target(helper, fixture,
                    new Vec3(stand.getX(), stand.getEyeY() + 8.0D, stand.getZ()));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(beyond));
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            watchTurnBack(helper, fixture, Kind.STAND_STRING, roof, stand.getEyeY(), bindTicks, step,
                    new BindReach(), 0);
        });
    }

    /*
     * 1.16 SFUStringEntity (017 :43, an OwnerBoundProjectileEntity; :128-130 movementSpeed 16 / lifespan) thrown by
     * StoneFreeUserStringAttack (:35-37, setLifeSpan(16)) through world.addFreshEntity, so with no Stand speed factor:
     * the user's attack string went out from the user's hand along its look 8 steps of 1 block and was back by age
     * 16. Its ray ran one step further, so a bear 10 blocks up was never touched; the port's old straight shot (2.0
     * times the Stand's speed factor, up to 24 blocks) hit it.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userAttackStringGoesOutEightBlocksAndBackAsIn116(GameTestHelper helper) {
        runUserReach(helper, StoneFreeAddon.USER_STRING_ATTACK_NAME, 16, "user attack");
    }

    // The same for StoneFreeUserStringBind (:40-42, setLifeSpan(24)): 12 steps of 2/3 of a block, back by age 24.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBindStringGoesOutEightBlocksAndBackAsIn116(GameTestHelper helper) {
        runUserReach(helper, StoneFreeAddon.USER_STRING_BIND_NAME, 24, "user bind");
    }

    private static void runUserReach(GameTestHelper helper, String abilityName, int lifespan, String what) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            helper.assertTrue(fixture.stand.getAttackSpeed() > 8.0D,
                    "Setup: the Stand's attack speed " + fixture.stand.getAttackSpeed() + " gives no speed factor");
            double step = 16.0D / lifespan;
            int steps = outSteps(lifespan);
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    new Vec3(fixture.owner.getX(), fixture.owner.getEyeY() + 10.0D, fixture.owner.getZ()));
            StoneFreeGrappleGameTests.click(helper, fixture, abilityName);
            watchReach(helper, fixture, new Flight(Kind.USER_STRING, what, target, false, 2 * steps,
                    lifespan + 10, step, steps * step, string -> string.getEntityAttachedTo() != null
                            || target.getHealth() < target.getMaxHealth()),
                    new BindReach(), 0);
        });
    }

    /*
     * 1.16 SFUStringEntity.afterEntityHit and hurtTarget: the user's attack string that hurt what it met turned back
     * at once and, through dealtDamage, hurt nothing more, not even a second target its ray met in the same tick;
     * breakProjectile did nothing, so it came back to the hand.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userAttackStringHurtsOnceAndTurnsBackAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            Vec3 spot = new Vec3(fixture.owner.getX(), fixture.owner.getEyeY() + 4.0D, fixture.owner.getZ());
            List<PolarBear> targets = List.of(StoneFreeGrappleGameTests.target(helper, fixture, spot),
                    StoneFreeGrappleGameTests.target(helper, fixture, spot));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_ATTACK_NAME);
            watchHitTurnBack(helper, fixture, targets, new BindReach(), 0);
        });
    }

    private static void watchHitTurnBack(GameTestHelper helper, Fixture fixture, List<PolarBear> targets,
            BindReach run, int elapsed) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_STRING);
        if (string != null) {
            double distance = string.position().distanceTo(string.getOriginPoint(1.0F));
            run.seen = true;
            run.farthest = Math.max(run.farthest, distance);
            run.lastAge = string.tickCount;
        }
        else if (run.seen) {
            List<Float> health = targets.stream().map(LivingEntity::getHealth).toList();
            long hurt = targets.stream().filter(target -> target.getHealth() < target.getMaxHealth()).count();
            helper.assertTrue(hurt == 1,
                    "1.16's user attack string hurt one of the two bears 4 blocks up and nothing more; the port's"
                            + " hurt " + hurt + ": health " + health);
            helper.assertTrue(run.farthest < 6.0D && run.lastAge < 14,
                    "1.16's user attack string turned back from the bear it hurt 4 blocks up; the port's went "
                            + run.farthest + " blocks out and was last seen at age " + run.lastAge);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(elapsed < 26, "The user's attack string "
                + (run.seen ? "outlived 26 ticks: age " + run.lastAge : "never flew"));
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchHitTurnBack(helper, fixture, targets, run, elapsed + 1));
    }

    /*
     * 1.16 StoneFreeUserStringAttack (017 :35-37) added its SFUStringEntity with world.addFreshEntity, so
     * DamagingEntity's damageFactor stayed 1 (1.16 DamagingEntity :54, :305-311): the user's attack string hit for its
     * own 2.0 whatever the Stand's strength. The port's multiplied it by the Stand's attack damage / 8 (3.75 at 15).
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userAttackStringHurtsForItsOwnTwoAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            helper.assertTrue(fixture.stand.getAttackDamage() != 8.0D,
                    "Setup: the Stand's attack damage " + fixture.stand.getAttackDamage() + " scales nothing");
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    new Vec3(fixture.owner.getX(), fixture.owner.getEyeY() + 4.0D, fixture.owner.getZ()));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_ATTACK_NAME);
            afterGone(helper, fixture, Kind.USER_STRING, false, 30, () -> {
                float taken = target.getMaxHealth() - target.getHealth();
                helper.assertTrue(Math.abs(taken - 2.0F) < 1.0E-4F,
                        "1.16's user attack string took 2.0 from the bear; the port's took " + taken
                                + " (Stand attack damage " + fixture.stand.getAttackDamage() + ")");
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    /*
     * 1.16 added every string its user threw with world.addFreshEntity (StoneFreeUserStringAttack :37, Sweep :36,
     * Whip :36, Bind :42, Pick :40, StoneFreeUserGrapple :65): each joins with damage and speed factor 1.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 300)
    public static void userStringsJoinWithoutTheStandsStatsAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            helper.assertTrue(fixture.stand.getAttackDamage() != 8.0D && fixture.stand.getAttackSpeed() != 8.0D,
                    "Setup: the Stand's stats scale nothing");
            List<String> stats = new java.util.ArrayList<>();
            List<Map.Entry<String, Kind>> throwsInTurn = List.of(
                    Map.entry(StoneFreeAddon.USER_STRING_ATTACK_NAME, Kind.USER_STRING),
                    Map.entry(StoneFreeAddon.USER_STRING_SWEEP_NAME, Kind.USER_SWEEP),
                    Map.entry(StoneFreeAddon.USER_STRING_WHIP_NAME, Kind.USER_WHIP),
                    Map.entry(StoneFreeAddon.USER_STRING_BIND_NAME, Kind.USER_STRING),
                    Map.entry(StoneFreeAddon.USER_STRING_PICK_NAME, Kind.USER_SPIKE),
                    Map.entry(StoneFreeAddon.USER_GRAPPLE_NAME, Kind.USER_GRAPPLE));
            throwInTurn(helper, fixture, throwsInTurn, 0, stats, () -> {
                List<String> scaled = stats.stream().filter(row -> !row.endsWith("damage 1.0, speed 1.0")).toList();
                helper.assertTrue(scaled.isEmpty() && stats.size() == throwsInTurn.size(),
                        "1.16 added the user's strings with damage and speed factor 1; the port's joined with "
                                + stats);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    private static void throwInTurn(GameTestHelper helper, Fixture fixture, List<Map.Entry<String, Kind>> throwsInTurn,
            int index, List<String> stats, Runnable done) {
        if (index == throwsInTurn.size()) {
            done.run();
            return;
        }
        String abilityName = throwsInTurn.get(index).getKey();
        Kind kind = throwsInTurn.get(index).getValue();
        throwString(helper, fixture, abilityName);
        StoneFreeGrappleGameTests.await(helper, fixture, kind, 10, string -> true, string -> {
            stats.add(abilityName + ": damage " + string.getDamageFactor() + ", speed " + string.getSpeedFactor());
            AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
            if (kind == Kind.USER_GRAPPLE) {
                StoneFreeServices.discardUserGrapples(fixture.owner);
            }
            afterGone(helper, fixture, kind, true, 30,
                    () -> throwInTurn(helper, fixture, throwsInTurn, index + 1, stats, done));
        });
    }

    // 1.16 StandEntity.addProjectile gave the Stand's own strings its stats: the bind's string keeps them.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standBindStringKeepsTheStandsStatsAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            float damage = (float) stand.getAttackDamage() / 8;
            double speed = stand.getAttackSpeed() / 8;
            helper.assertTrue(damage != 1.0F && speed != 1.0D, "Setup: the Stand's stats scale nothing");
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.STAND_STRING, 10, string -> true, string -> {
                helper.assertTrue(string.getDamageFactor() == damage && string.getSpeedFactor() == speed,
                        "1.16's Stand bind string took the Stand's damage factor " + damage + " and speed factor "
                                + speed + "; the port's " + string.getDamageFactor() + " and "
                                + string.getSpeedFactor());
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    // 1.16 SFUStringEntity.knockbackMultiplier (017 :112-114) was 0: the user's attack string's hit pushed nothing.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userAttackStringHitPushesNothingAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    new Vec3(fixture.owner.getX(), fixture.owner.getEyeY() + 4.0D, fixture.owner.getZ()));
            List<Float> pushes = new java.util.concurrent.CopyOnWriteArrayList<>();
            Consumer<LivingKnockBackEvent> listener = event -> {
                if (event.getEntity() == target) {
                    pushes.add(event.getStrength());
                }
            };
            NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false,
                    LivingKnockBackEvent.class, listener);
            try {
                StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_ATTACK_NAME);
            }
            catch (RuntimeException e) {
                NeoForge.EVENT_BUS.unregister(listener);
                throw e;
            }
            afterGone(helper, fixture, Kind.USER_STRING, false, 30, () -> {
                NeoForge.EVENT_BUS.unregister(listener);
                helper.assertTrue(target.getHealth() < target.getMaxHealth(),
                        "Setup: the user's attack string did not hit the bear 4 blocks up");
                helper.assertTrue(pushes.stream().allMatch(strength -> strength <= 0.0F),
                        "1.16's user attack string hurt the bear without pushing it; the port's knocked it back"
                                + " with strength " + pushes);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    // Runs once no string of this kind of the fixture's user is out any more.
    private static void afterGone(GameTestHelper helper, Fixture fixture, Kind kind, boolean seen, int ticksLeft,
            Runnable then) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, kind);
        if (string == null && seen) {
            then.run();
            return;
        }
        helper.assertTrue(ticksLeft > 0, "The " + kind + " string " + (string != null
                ? "was still out at age " + string.tickCount : "never flew"));
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> afterGone(helper, fixture, kind, seen || string != null, ticksLeft - 1, then));
    }

    // 1.16 OwnerBoundProjectileEntity.afterBlockHit, which SFUStringEntity kept: the user's bind string met a block,
    // turned back and was not broken (the port's old shot was discarded there).
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBindStringTurnsBackAtABlockAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            BlockPos roof = BlockPos.containing(fixture.owner.getX(), fixture.owner.getEyeY() + 4.0D,
                    fixture.owner.getZ());
            fixture.setBlock(helper, roof, Blocks.STONE.defaultBlockState());
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_BIND_NAME);
            watchTurnBack(helper, fixture, Kind.USER_STRING, roof, fixture.owner.getEyeY(), 24, 16.0D / 24,
                    new BindReach(), 0);
        });
    }

    /*
     * 1.16 SFUStringSweepEntity (017 :54-67) and SFUStringWhipEntity (:57-70): OwnerBoundProjectileEntity with
     * movementSpeed 1.5 over a lifespan of 10, so a mark of 5. Each tick the swing turned first (the sweep's yaw from
     * 67.5, the whip's pitch from -87.5, by 13.5 a tick), then the string moved 1.5 out from the user's hand along the
     * user's look so turned, to 7.5 blocks, came back as fast and was gone at age 10. The user looks 80 degrees up,
     * so the swing shows and stays clear of the neighbouring plots.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void userSweepSwingsOutSevenAndAHalfBlocksAndBackAsIn116(GameTestHelper helper) {
        runSwing(helper, StoneFreeAddon.USER_STRING_SWEEP_NAME, Kind.USER_SWEEP, 1.5F, 10, 0);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void userWhipSwingsOutSevenAndAHalfBlocksAndBackAsIn116(GameTestHelper helper) {
        runSwing(helper, StoneFreeAddon.USER_STRING_WHIP_NAME, Kind.USER_WHIP, 1.5F, 10, 0);
    }

    /*
     * 1.16 SFUStringWhipEntity.hurtTarget and afterEntityHit (017 :76-85): once the whip had hurt something it hurt
     * nothing more for the rest of that throw. A bear early in the swing (2.5 blocks out along its age-2 pitch) is hit
     * first; a second bear 5 blocks out along its age-5 pitch, which the swing passes later, is left unhurt.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void userWhipHurtsOnlyTheFirstMobItMeetsAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            LivingEntity user = fixture.owner;
            // The whip swings mostly behind the look: the look points along x away from the chunk's middle.
            boolean middleEast = (user.getX() - (user.blockPosition().getX() & ~15)) < 8.0D;
            float yaw = middleEast ? 90.0F : -90.0F;
            user.setYRot(yaw);
            user.yRotO = yaw;
            user.setYHeadRot(yaw);
            user.setXRot(-80.0F);
            user.xRotO = -80.0F;
            Vec3 half = new Vec3(0.0D, 0.7D, 0.0D);
            PolarBear first = StoneFreeGrappleGameTests.target(helper, fixture,
                    donorSwingPosition(Kind.USER_WHIP, user, 2, 2.5D).subtract(half));
            PolarBear second = StoneFreeGrappleGameTests.target(helper, fixture,
                    donorSwingPosition(Kind.USER_WHIP, user, 5, 5.0D).subtract(half));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_WHIP_NAME);
            afterGone(helper, fixture, Kind.USER_WHIP, false, 30, () -> {
                helper.assertTrue(first.getHealth() < first.getMaxHealth()
                                && second.getHealth() == second.getMaxHealth(),
                        "1.16's whip hurt the first bear in its swing and nothing more that throw; the port's left"
                                + " the first at health " + first.getHealth() + " and the second at "
                                + second.getHealth() + " (of " + second.getMaxHealth() + ")");
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            });
        });
    }

    /*
     * 1.16 SFExtendedPunchEntity (017 :76-90): movementSpeed and retractSpeed 1.5, timeAtFullLength 4, and the lifespan
     * of the extended punch's getStandActionTicks(null, null), its standPerformDuration(10) (InitStands :166). It was
     * added by StandEntity.addProjectile (StoneFreeExtendedPunch :73-74), so each step was 1.5 times the Stand's attack
     * speed / 8, while the mark stayed (int) (4.5 / 1.5) = 3: three steps out (6.75 blocks at attack speed 12), held
     * there until age 7, back as fast and gone at age 10. The port's went 4 blocks at 1.5 a tick and came back at once.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void extendedPunchGoesOutHoldsAndComesBackAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            double factor = fixture.stand.getAttackSpeed() / 8.0D;
            helper.assertTrue(factor != 1.0D, "Setup: the Stand's attack speed " + fixture.stand.getAttackSpeed()
                    + " gives no speed factor");
            List<Double> distances = donorDistances(1.5F, 10, 4, factor);
            press(helper, fixture, StoneFreeAddon.EXTENDED_PUNCH_NAME);
            watchPunch(helper, fixture, run -> {
                int lastAge = run.seen.isEmpty() ? -1 : run.seen.lastKey();
                helper.assertTrue(run.mismatches.isEmpty() && lastAge == distances.size()
                                && run.seen.size() >= distances.size() - 1,
                        "1.16's extended punch went " + distances + " blocks out at speed factor " + factor
                                + " and was gone at age " + (distances.size() + 1) + "; the port's was last seen at"
                                + " age " + lastAge + ", off 1.16's path at " + run.mismatches.size() + " of "
                                + run.seen.size() + " ticks: " + run.mismatches.subList(0,
                                        Math.min(4, run.mismatches.size())) + "; distances " + run.seen.values());
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            }, new PunchRun(distances, run -> {}), 30);
        });
    }

    /*
     * The same punch straight up at a bear 5.6 blocks above its origin, past the port's old 4 blocks and their 5.5 of
     * ray: 1.16's third step's ray (to 6.75) hit it, SFExtendedPunchEntity.afterEntityHit (017 :68-74) turned the punch
     * back and OwnerBoundProjectileEntity.breakProjectile did nothing, so it was seen coming back after the hit
     * (2.25 out at age 3) and gone at age 4. The bear comes once the punch is out, the Stand then standing where it
     * punches from (standOffsetFront).
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void extendedPunchHitsAMobWithinItsReachAndTurnsBackAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PunchRun run = new PunchRun(List.of(), punchRun -> punchRun.target = StoneFreeGrappleGameTests.target(
                    helper, fixture, donorPunchOrigin(fixture.stand).add(0.0D, 5.6D, 0.0D)));
            press(helper, fixture, StoneFreeAddon.EXTENDED_PUNCH_NAME);
            watchPunch(helper, fixture, done -> {
                LivingEntity target = done.target;
                helper.assertTrue(target.getHealth() < target.getMaxHealth() && done.seenAfterHurt
                                && done.farthest() < 6.0D && done.seen.lastKey() <= 4,
                        "1.16's extended punch hit the bear 5.6 blocks up and came back from it (seen after the hit,"
                                + " 4.5 blocks out at most, gone at age 4); the port's left the bear at health "
                                + target.getHealth() + " of " + target.getMaxHealth() + ", seen after the hit "
                                + done.seenAfterHurt + ", distances by age " + done.seen);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            }, run, 30);
        });
    }

    // Its ray, from the origin to its end plus its last step, reached 9.0 blocks once: a bear 9.4 up is never touched.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void extendedPunchMissesAMobJustPastItsRayAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PunchRun run = new PunchRun(List.of(), punchRun -> punchRun.target = StoneFreeGrappleGameTests.target(
                    helper, fixture, donorPunchOrigin(fixture.stand).add(0.0D, 9.4D, 0.0D)));
            press(helper, fixture, StoneFreeAddon.EXTENDED_PUNCH_NAME);
            watchPunch(helper, fixture, done -> {
                LivingEntity target = done.target;
                helper.assertTrue(target.getHealth() == target.getMaxHealth()
                                && Math.abs(done.farthest() - 6.75D) <= 0.3D && done.seen.lastKey() == 9,
                        "1.16's extended punch went 6.75 blocks out (its ray 9.0), never touched the bear 9.4 up"
                                + " and was gone at age 10; the port's left the bear at health " + target.getHealth()
                                + " of " + target.getMaxHealth() + ", distances by age " + done.seen);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            }, run, 30);
        });
    }

    /*
     * 1.16 SFExtendedPunchEntity.afterBlockHit (017 :58-61) turned the punch back from any block it met, also one it
     * broke (hardness up to 5, :54-56): stone 4.6 to 5.6 blocks up is met by the third step's ray, broken, and the
     * punch is seen coming back (2.25 out at age 3), gone at age 4.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void extendedPunchTurnsBackAtABlockItBreaksAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PunchRun run = new PunchRun(List.of(), punchRun -> {
                punchRun.roof = BlockPos.containing(donorPunchOrigin(fixture.stand).add(0.0D, 5.6D, 0.0D));
                punchRun.roofHeight = punchRun.roof.getY() - donorPunchOrigin(fixture.stand).y;
                fixture.setBlock(helper, punchRun.roof, Blocks.STONE.defaultBlockState());
            });
            press(helper, fixture, StoneFreeAddon.EXTENDED_PUNCH_NAME);
            watchPunch(helper, fixture, done -> {
                boolean broken = helper.getLevel().getBlockState(done.roof).isAir();
                helper.assertTrue(broken && done.farthest() < 6.0D && done.seen.lastKey() == 3
                                && done.seen.getOrDefault(3, 99.0D) < done.seen.getOrDefault(2, 0.0D),
                        "1.16's extended punch broke the stone " + done.roofHeight + " blocks up and came back from"
                                + " it, gone at age 4; the port's broke it " + broken + ", distances by age "
                                + done.seen);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            }, run, 30);
        });
    }

    /*
     * 1.16 SFExtendedPunchEntity.hurtTarget (017 :63-66) and afterBlockHit (:58-61) played STONE_FREE_PUNCH_LIGHT at the
     * punch on every mob it tried to hurt and every block it met, and nothing otherwise: a bear 5.6 blocks up and a stone
     * block as far both make it heard; a bear 9.4 up, past the ray, makes no sound.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void extendedPunchPlaysTheLightPunchSoundOnAMobItMeetsAsIn116(GameTestHelper helper) {
        runPunchSound(helper, 5.6D, false, true);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void extendedPunchPlaysTheLightPunchSoundOnABlockItMeetsAsIn116(GameTestHelper helper) {
        runPunchSound(helper, 5.6D, true, true);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void extendedPunchMakesNoLightPunchSoundWhenItMeetsNothingAsIn116(GameTestHelper helper) {
        runPunchSound(helper, 9.4D, false, false);
    }

    private static void runPunchSound(GameTestHelper helper, double height, boolean block, boolean heard) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            int[] sounds = {0};
            List<String> heardAt = new java.util.ArrayList<>();
            List<String> elsewhere = new java.util.ArrayList<>();
            // The punch plays its sound where it is (Entity.playSound). The plots of a batch stand 4 or more blocks
            // apart and their tests punch too, and under load they no longer run in step with this one: a light punch
            // next door within 15 blocks was counted as this punch's. Only a sound at this fixture's own punch counts.
            Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
                if (event.getLevel() != helper.getLevel() || event.getSound() == null
                        || event.getSound().value() != StoneFreeAddon.STONE_FREE_PUNCH_LIGHT.get()) {
                    return;
                }
                Vec3 at = event.getPosition();
                Vec3 line = donorPunchOrigin(fixture.stand);
                double aside = Math.hypot(at.x - line.x, at.z - line.z);
                String where = String.format(java.util.Locale.ROOT, "%.2f aside, %.2f up", aside, at.y - line.y);
                boolean ownPunch = !helper.getLevel().getEntitiesOfClass(StoneFreeStringEntity.class,
                        new net.minecraft.world.phys.AABB(at, at).inflate(PUNCH_SOUND_AT_PUNCH),
                        string -> string.kind() == Kind.EXTENDED_PUNCH
                                && StoneFreeServices.userOf(string.getOwner()) == fixture.owner
                                && string.position().distanceTo(at) < PUNCH_SOUND_AT_PUNCH).isEmpty();
                if (ownPunch) {
                    sounds[0]++;
                    heardAt.add(where);
                }
                else if (at.distanceTo(fixture.owner.position()) < 15.0D) {
                    elsewhere.add(where);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, listener);
            fixture.afterwards.add(() -> NeoForge.EVENT_BUS.unregister(listener));
            PunchRun run = new PunchRun(List.of(), punchRun -> {
                Vec3 at = donorPunchOrigin(fixture.stand).add(0.0D, height, 0.0D);
                if (block) {
                    fixture.setBlock(helper, BlockPos.containing(at), Blocks.STONE.defaultBlockState());
                }
                else {
                    punchRun.target = StoneFreeGrappleGameTests.target(helper, fixture, at);
                }
            });
            press(helper, fixture, StoneFreeAddon.EXTENDED_PUNCH_NAME);
            watchPunch(helper, fixture, done -> {
                if (!elsewhere.isEmpty()) {
                    com.mojang.logging.LogUtils.getLogger().info("SF_PUNCH_SOUND_NEXT_DOOR {} {}",
                            heard ? (block ? "block" : "mob") : "nothing", elsewhere);
                }
                helper.assertTrue(heard ? sounds[0] > 0 : sounds[0] == 0,
                        "1.16's extended punch " + (heard ? "played the light punch sound on the " + (block ? "block" : "mob")
                                + " it met" : "made no light punch sound when it met nothing")
                                + "; the port's played it " + sounds[0] + " times on its own path " + heardAt
                                + " (other light punches within 15 blocks: " + elsewhere + ")");
                StoneFreeBarrierGameTests.succeed(helper, fixture);
            }, run, 30);
        });
    }

    /*
     * 1.16 StoneFreeExtendedPunch.onTaskStopped (017 :80-90) gave the forearm back when the Stand's task stopped, the 10
     * ticks of the action, and SFExtendedPunchEntity did nothing when it went: a punch that hit a bear 5.6 blocks up is
     * back and gone by age 4, and the forearm is still the short one until the action ends.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void extendedPunchForearmComesBackWhenTheActionEndsNotWhenThePunchGoesAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PunchRun run = new PunchRun(List.of(), punchRun -> punchRun.target = StoneFreeGrappleGameTests.target(
                    helper, fixture, donorPunchOrigin(fixture.stand).add(0.0D, 5.6D, 0.0D)));
            press(helper, fixture, StoneFreeAddon.EXTENDED_PUNCH_NAME);
            watchPunch(helper, fixture, done -> {
                helper.assertTrue(done.seen.lastKey() <= 4 && done.target.getHealth() < done.target.getMaxHealth(),
                        "Setup: the punch did not hit and come back by age 4: " + done.seen);
                awaitForearm(helper, fixture, 15);
            }, run, 30);
        });
    }

    private static void awaitForearm(GameTestHelper helper, Fixture fixture, int ticksLeft) {
        StoneFreeStandEntity stand = fixture.stand;
        EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
        boolean running = action != null && !action.isOver()
                && action.ability == fixture.power.getAbility(StoneFreeAddon.EXTENDED_PUNCH_NAME);
        if (running) {
            helper.assertTrue(!stand.hasForearm() && stand.hasShortForearm(),
                    "1.16 gave the forearm back when the 10-tick action ended, not when the punch was gone; at action"
                            + " tick " + action.getFullTicksPassed() + " the port's Stand has the forearm "
                            + stand.hasForearm() + " and the short one " + stand.hasShortForearm());
            helper.assertTrue(ticksLeft > 0, "The extended punch's action never ended");
            StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> awaitForearm(helper, fixture, ticksLeft - 1));
            return;
        }
        helper.assertTrue(stand.hasForearm() && !stand.hasShortForearm(),
                "1.16 gave the forearm back when the extended punch's action ended; the port's Stand has the forearm "
                        + stand.hasForearm() + " and the short one " + stand.hasShortForearm());
        StoneFreeBarrierGameTests.succeed(helper, fixture);
    }

    // A sound the punch plays is at its own position.
    private static final double PUNCH_SOUND_AT_PUNCH = 0.01D;

    // 1.16 ownerPosition(1.0F, false) of the Stand for SFExtendedPunchEntity (OFFSET -0.3, -0.2, 0.25).
    private static Vec3 donorPunchOrigin(LivingEntity stand) {
        return stand.getEyePosition(1.0F)
                .add(new Vec3(-0.3D, -0.2D, 0.25D).yRot(-stand.getYRot() * Mth.DEG_TO_RAD));
    }

    private static final class PunchRun {
        final java.util.SortedMap<Integer, Double> seen = new java.util.TreeMap<>();
        final List<String> mismatches = new java.util.ArrayList<>();
        final List<Double> distances;
        final Consumer<PunchRun> atFirstSight;
        LivingEntity target;
        BlockPos roof;
        double roofHeight;
        boolean seenAfterHurt;

        PunchRun(List<Double> distances, Consumer<PunchRun> atFirstSight) {
            this.distances = distances;
            this.atFirstSight = atFirstSight;
        }

        double farthest() {
            return seen.values().stream().mapToDouble(d -> d).max().orElse(0.0D);
        }
    }

    // Each tick the punch's position against 1.16's from the Stand as it stands now (it has ticked before its punch).
    private static void watchPunch(GameTestHelper helper, Fixture fixture, Consumer<PunchRun> done, PunchRun run,
            int ticksLeft) {
        StoneFreeStringEntity punch = StoneFreeGrappleGameTests.find(helper, fixture, Kind.EXTENDED_PUNCH);
        if (punch != null && punch.tickCount >= 1) {
            int age = punch.tickCount;
            Vec3 origin = donorPunchOrigin(fixture.stand);
            if (run.seen.isEmpty()) {
                run.atFirstSight.accept(run);
            }
            run.seen.put(age, punch.position().distanceTo(origin));
            if (run.target != null && run.target.getHealth() < run.target.getMaxHealth()) {
                run.seenAfterHurt = true;
            }
            if (!run.distances.isEmpty()) {
                Vec3 expected = age <= run.distances.size()
                        ? origin.add(Vec3.directionFromRotation(fixture.stand.getXRot(), fixture.stand.getYRot())
                                .scale(run.distances.get(age - 1)))
                        : null;
                if (expected == null || expected.distanceTo(punch.position()) > 1.0E-3D) {
                    run.mismatches.add("age " + age + " at " + punch.position() + ", 1.16 " + expected);
                }
            }
        }
        else if (punch == null && !run.seen.isEmpty()) {
            done.accept(run);
            return;
        }
        helper.assertTrue(ticksLeft > 0, "The extended punch " + (run.seen.isEmpty() ? "never flew"
                : "was still out at age " + (punch != null ? punch.tickCount : -1)));
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchPunch(helper, fixture, done, run, ticksLeft - 1));
    }

    // 1.16 OwnerBoundProjectileEntity.getNextOriginOffset / updateDistance / updateMotionFlags, with a speed factor
    // of 1: the string's distance from its origin after each tick, from age 1 until it is back.
    static List<Double> donorDistances(float speed, int lifespan, int timeAtFullLength) {
        return donorDistances(speed, lifespan, timeAtFullLength, 1.0D);
    }

    // The same with a speed factor: the steps scale with it, the mark (from movementSpeed alone) does not.
    static List<Double> donorDistances(float speed, int lifespan, int timeAtFullLength, double factor) {
        double maxDistance = speed * speed * (lifespan - timeAtFullLength) / (speed + speed);
        int mark = (int) (maxDistance / speed);
        List<Double> distances = new java.util.ArrayList<>();
        double distance = 0.0D;
        boolean forward = true;
        boolean retracting = false;
        for (int age = 1; age <= lifespan; age++) {
            double next = retracting ? (float) (distance - speed * factor)
                    : forward ? (float) (distance + speed * factor) : (float) distance;
            if (forward && age >= mark) {
                forward = false;
            }
            if (!retracting && age >= mark + timeAtFullLength) {
                retracting = true;
            }
            if (retracting && next <= 0.0D) {
                break;
            }
            distance = next;
            distances.add(distance);
        }
        return distances;
    }

    // 1.16 ownerPosition(1.0F, false) of the user for the sweep, whip and spike, and their swing at that age.
    private static Vec3 donorSwingPosition(Kind kind, LivingEntity user, int age, double distance) {
        Vec3 local = kind == Kind.USER_WHIP ? new Vec3(-0.3D, -0.75D, 0.25D) : new Vec3(-0.22D, -0.75D, 0.25D);
        Vec3 origin = user.getEyePosition(1.0F).add(local.yRot(-user.getYRot() * Mth.DEG_TO_RAD));
        float yRotOffset = kind == Kind.USER_SWEEP ? Math.max(67.5F - 13.5F * age, -67.5F) : 0.0F;
        float xRotOffset = kind == Kind.USER_WHIP ? Math.min(-87.5F + 13.5F * age, 47.5F) : 0.0F;
        return origin.add(Vec3.directionFromRotation(user.getXRot() + xRotOffset, user.getYRot() + yRotOffset)
                .scale(distance));
    }

    private static void runSwing(GameTestHelper helper, String abilityName, Kind kind, float speed, int lifespan,
            int timeAtFullLength) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            LivingEntity user = fixture.owner;
            // Only the plot's chunk ticks entities: the sweep reaches out along the look, the whip mostly behind it,
            // so the look points along x toward, or for the whip away from, the middle of that chunk.
            boolean middleEast = (user.getX() - (user.blockPosition().getX() & ~15)) < 8.0D;
            float yaw = middleEast == (kind != Kind.USER_WHIP) ? -90.0F : 90.0F;
            user.setYRot(yaw);
            user.yRotO = yaw;
            user.setYHeadRot(yaw);
            user.setXRot(-80.0F);
            user.xRotO = -80.0F;
            List<Double> distances = donorDistances(speed, lifespan, timeAtFullLength);
            throwString(helper, fixture, abilityName);
            watchSwing(helper, fixture, kind, distances, new java.util.TreeMap<>(), false, lifespan + 10);
        });
    }

    // The pick is holdToFire(5): its key stays down until the spike is thrown (the fixture's cleanup lets it go).
    private static void throwString(GameTestHelper helper, Fixture fixture, String abilityName) {
        if (!abilityName.equals(StoneFreeAddon.USER_STRING_PICK_NAME)) {
            StoneFreeGrappleGameTests.click(helper, fixture, abilityName);
            return;
        }
        StoneFreeBarrierGameTests.assertTicksEntities(helper, fixture);
        Ability pick = fixture.power.getAbility(abilityName);
        helper.assertTrue(pick != null && pick.checkConditions(fixture.power).isPositive(),
                "Registered " + abilityName + " refused the fixture");
        BufferingState buffering = BufferingState.clickOnly();
        AbilityInput.keyPress(StoneFreeBarrierGameTests.GRAPPLE_KEY, pick, fixture.owner, null, InputMethod.HOLD,
                0.0F, buffering, pick.abilityId);
        helper.assertTrue(buffering.isActionSuccess, "The held pick did not start");
    }

    /*
     * 1.16 SFUStringSpikeEntity (017 :50-64): movementSpeed and retractSpeed 1.5, lifespan 16, timeAtFullLength 4, so
     * maxDistance 1.5 * 1.5 * 12 / 3 = 9 and a mark of 6: the pick's spike went out 6 steps to 9 blocks, stayed there
     * until age 10 and came back as fast, gone at age 16 (the port's went 4 blocks and came back at once).
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userPickSpikeGoesOutNineBlocksStaysAndComesBackAsIn116(GameTestHelper helper) {
        runSwing(helper, StoneFreeAddon.USER_STRING_PICK_NAME, Kind.USER_SPIKE, 1.5F, 16, 4);
    }

    /*
     * The same spike thrown straight up: a bear 8 blocks above its origin is hit; its ray, from the origin to its end
     * plus its last step, reached 10.5 blocks once (the first tick at full length), so a bear 10.9 up is never
     * touched.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userPickSpikeHitsWithinItsReachAndMissesBeyondItsRayAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            Vec3 origin = donorSwingPosition(Kind.USER_SPIKE, fixture.owner, 0, 0.0D);
            PolarBear near = StoneFreeGrappleGameTests.target(helper, fixture, origin.add(0.0D, 8.0D, 0.0D));
            PolarBear far = StoneFreeGrappleGameTests.target(helper, fixture, origin.add(0.0D, 10.9D, 0.0D));
            throwString(helper, fixture, StoneFreeAddon.USER_STRING_PICK_NAME);
            watchSpikeReach(helper, fixture, near, far, new BindReach(), 0);
        });
    }

    private static void watchSpikeReach(GameTestHelper helper, Fixture fixture, PolarBear near, PolarBear far,
            BindReach run, int elapsed) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_SPIKE);
        if (string != null) {
            run.seen = true;
            run.farthest = Math.max(run.farthest, string.position().distanceTo(string.getOriginPoint(1.0F)));
            run.lastAge = string.tickCount;
        }
        else if (run.seen) {
            helper.assertTrue(near.getHealth() < near.getMaxHealth() && far.getHealth() == far.getMaxHealth()
                            && Math.abs(run.farthest - 9.0D) <= 0.3D && run.lastAge == 15,
                    "1.16's spike went 9 blocks out, hit the bear 8 blocks up, never touched the one 10.9 up (its"
                            + " ray reached 10.5) and was back at age 16; the port's went " + run.farthest
                            + " blocks out, last seen at age " + run.lastAge + ": near health " + near.getHealth()
                            + ", far health " + far.getHealth());
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(elapsed < 40, "The pick's spike " + (run.seen ? "outlived 40 ticks" : "never flew"));
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchSpikeReach(helper, fixture, near, far, run, elapsed + 1));
    }

    // 1.16 OwnerBoundProjectileEntity.afterBlockHit and breakProjectile, which SFUStringSpikeEntity kept: a block too
    // hard for it (hardness above 1) turned the spike back, and nothing broke it.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userPickSpikeTurnsBackAtABlockAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            BlockPos roof = BlockPos.containing(fixture.owner.getX(), fixture.owner.getEyeY() + 4.0D,
                    fixture.owner.getZ());
            fixture.setBlock(helper, roof, Blocks.STONE.defaultBlockState());
            throwString(helper, fixture, StoneFreeAddon.USER_STRING_PICK_NAME);
            watchTurnBack(helper, fixture, Kind.USER_SPIKE, roof, fixture.owner.getEyeY(), 16, 1.5D,
                    new BindReach(), 0);
        });
    }

    private static void watchSwing(GameTestHelper helper, Fixture fixture, Kind kind, List<Double> distances,
            java.util.SortedMap<Integer, Vec3> seen, boolean out, int ticksLeft) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, kind);
        if (string != null) {
            if (string.tickCount >= 1) {
                seen.put(string.tickCount, string.position());
            }
        }
        else if (out) {
            List<String> mismatches = new java.util.ArrayList<>();
            for (var entry : seen.entrySet()) {
                int age = entry.getKey();
                Vec3 expected = age <= distances.size()
                        ? donorSwingPosition(kind, fixture.owner, age, distances.get(age - 1))
                        : null;
                if (expected == null || expected.distanceTo(entry.getValue()) > 1.0E-3D) {
                    mismatches.add("age " + age + " at " + entry.getValue() + ", 1.16 " + expected);
                }
            }
            int lastAge = seen.isEmpty() ? -1 : seen.lastKey();
            helper.assertTrue(mismatches.isEmpty() && lastAge == distances.size()
                            && seen.size() >= distances.size() - 1,
                    "1.16's " + kind + " went out to " + distances.stream().mapToDouble(d -> d).max().orElse(0.0D)
                            + " blocks and was back and gone at age " + (distances.size() + 1) + " (distances "
                            + distances + "); the port's was last seen at age " + lastAge + ", off 1.16's path at "
                            + mismatches.size() + " of " + seen.size() + " ticks: "
                            + mismatches.subList(0, Math.min(3, mismatches.size())));
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(ticksLeft > 0, "The " + kind + " string " + (out || string != null
                ? "was still out at age " + (string != null ? string.tickCount : -1) : "never flew"));
        boolean nowOut = out || string != null;
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchSwing(helper, fixture, kind, distances, seen, nowOut, ticksLeft - 1));
    }

    /*
     * The same sweep thrown straight up: what it passes within its 7.5 blocks is hit (a bear 6 blocks above the
     * string's origin); its ray, from the origin to its end plus its last step, reached 9.0 blocks on its turn, so a
     * bear just beyond that is never touched. The port's old sweep stopped 4 blocks out.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void userSweepHitsWithinItsReachAndMissesBeyondItsRayAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            Vec3 origin = donorSwingPosition(Kind.USER_SWEEP, fixture.owner, 0, 0.0D);
            PolarBear near = StoneFreeGrappleGameTests.target(helper, fixture, origin.add(0.0D, 6.0D, 0.0D));
            PolarBear far = StoneFreeGrappleGameTests.target(helper, fixture, origin.add(0.0D, 9.4D, 0.0D));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_SWEEP_NAME);
            watchSweepReach(helper, fixture, near, far, new BindReach(), 0);
        });
    }

    // 1.16 OwnerBoundProjectileEntity.afterBlockHit, which SFUStringSweepEntity kept: a block turned the sweep back.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 100)
    public static void userSweepTurnsBackAtABlockAsIn116(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            BlockPos roof = BlockPos.containing(fixture.owner.getX(), fixture.owner.getEyeY() + 4.0D,
                    fixture.owner.getZ());
            fixture.setBlock(helper, roof, Blocks.STONE.defaultBlockState());
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_SWEEP_NAME);
            watchTurnBack(helper, fixture, Kind.USER_SWEEP, roof, fixture.owner.getEyeY(), 10, 1.5D,
                    new BindReach(), 0);
        });
    }

    private static void watchSweepReach(GameTestHelper helper, Fixture fixture, PolarBear near, PolarBear far,
            BindReach run, int elapsed) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_SWEEP);
        if (string != null) {
            run.seen = true;
            run.farthest = Math.max(run.farthest, string.position().distanceTo(string.getOriginPoint(1.0F)));
            run.lastAge = string.tickCount;
        }
        else if (run.seen) {
            helper.assertTrue(near.getHealth() < near.getMaxHealth() && far.getHealth() == far.getMaxHealth(),
                    "1.16's sweep hit the bear 6 blocks up and never touched the one 9.4 blocks up (its ray reached"
                            + " 9.0); the port's went " + run.farthest + " blocks out, last seen at age " + run.lastAge
                            + ": near health " + near.getHealth() + ", far health " + far.getHealth());
            // 1.16 OwnerBoundProjectileEntity.breakProjectile did nothing: the sweep went on past what it hit.
            helper.assertTrue(Math.abs(run.farthest - 7.5D) <= 0.3D && run.lastAge == 9,
                    "1.16's sweep went on past the bear it hit, 7.5 blocks out, and was back at age 10; the port's"
                            + " went " + run.farthest + " blocks out and was last seen at age " + run.lastAge);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(elapsed < 30, "The user's sweep " + (run.seen ? "outlived 30 ticks" : "never flew"));
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchSweepReach(helper, fixture, near, far, run, elapsed + 1));
    }

    private static void watchTurnBack(GameTestHelper helper, Fixture fixture, Kind kind, BlockPos roof,
            double startY, int bindTicks, double step, BindReach run, int elapsed) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, kind);
        double roofDistance = roof.getY() - startY;
        if (string != null) {
            double distance = string.position().distanceTo(string.getOriginPoint(1.0F));
            helper.assertTrue(string.getEntityAttachedTo() == null, "Setup: the string got past the stone block");
            if (run.seen && distance < run.farthest - 0.5D * step) {
                helper.assertTrue(run.farthest <= roofDistance + step + 0.3D
                                && string.tickCount < outSteps(bindTicks),
                        "1.16's " + kind + " string turned back at the block " + roofDistance + " blocks up; the"
                                + " port's went " + run.farthest + " blocks out and turned at age "
                                + string.tickCount);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
                return;
            }
            run.seen = true;
            run.farthest = Math.max(run.farthest, distance);
            run.lastAge = string.tickCount;
        }
        else {
            helper.assertTrue(!run.seen, "1.16's " + kind + " string came back from the block it met; the port's"
                    + " was gone at age " + run.lastAge + ", " + run.farthest + " blocks out (the block "
                    + roofDistance + " blocks up)");
        }
        helper.assertTrue(elapsed < bindTicks + 10, "The " + kind + " string never flew");
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchTurnBack(helper, fixture, kind, roof, startY, bindTicks, step, run, elapsed + 1));
    }

    private static final class BindReach {
        boolean seen;
        double farthest;
        double lastDistance;
        int lastAge;
    }

    /**
     * One string's flight: out {@code reach} blocks in steps of {@code step}, back and gone by age {@code backAt}.
     * {@code reached} tells that it got to the target.
     */
    private record Flight(Kind kind, String what, LivingEntity target, boolean within, int backAt, int limit,
            double step, double reach, Predicate<StoneFreeStringEntity> reached) {}

    private static void watchReach(GameTestHelper helper, Fixture fixture, Flight flight, BindReach run,
            int elapsed) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, flight.kind);
        if (string != null) {
            double distance = string.position().distanceTo(string.getOriginPoint(1.0F));
            run.seen = true;
            run.farthest = Math.max(run.farthest, distance);
            run.lastDistance = distance;
            run.lastAge = string.tickCount;
            if (flight.reached.test(string)) {
                helper.assertTrue(flight.within,
                        "1.16's " + flight.what + " string reached " + flight.reach + " blocks (its ray "
                                + (flight.reach + flight.step) + "); the port's got to " + flight.target + " "
                                + (flight.target.getY() - string.getOriginPoint(1.0F).y) + " blocks above its start, "
                                + distance + " blocks out at age " + string.tickCount);
                StoneFreeBarrierGameTests.succeed(helper, fixture);
                return;
            }
        }
        else if (run.seen) {
            helper.assertTrue(!flight.within, "The " + flight.what + " string came back without reaching the bear "
                    + flight.reach / 2.0D + " blocks up, within its " + flight.reach + " blocks: farthest "
                    + run.farthest);
            helper.assertTrue(Math.abs(run.farthest - flight.reach) <= 0.3D,
                    "1.16's " + flight.what + " string went " + flight.reach + " blocks out, the port's "
                            + run.farthest);
            helper.assertTrue(run.lastAge >= flight.backAt - 2 && run.lastAge <= flight.backAt
                            && run.lastDistance <= 2.0D * flight.step + 0.3D,
                    "1.16's " + flight.what + " string came back and was gone by age " + flight.backAt
                            + "; the port's was last seen at age " + run.lastAge + ", " + run.lastDistance
                            + " blocks out");
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(elapsed < flight.limit, "The " + flight.what + " string "
                + (run.seen ? "outlived " + flight.limit + " ticks: age " + run.lastAge : "never flew"));
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchReach(helper, fixture, flight, run, elapsed + 1));
    }

    private static void trackBind(GameTestHelper helper, Fixture fixture, EntityActionInstance bind, int expected,
            int elapsed, int bindOverAt, StoneFreeStringEntity seen) {
        StoneFreeStringEntity string = seen != null ? seen : StoneFreeGrappleGameTests.find(helper, fixture,
                Kind.STAND_STRING);
        if (string != null && seen == null) {
            helper.assertTrue(string.ticksLifespan() == expected && string.getEntityAttachedTo() == null,
                    "1.16 gave the Stand bind's string the task's " + expected + " ticks, the port "
                            + string.ticksLifespan() + " (attached " + string.getEntityAttachedTo() + ")");
        }
        int overAt = bindOverAt < 0 && bind.isOver() ? elapsed : bindOverAt;
        // The string itself may end a tick sooner, once it is back at the Stand.
        if (overAt >= 0 && string != null) {
            helper.assertTrue(overAt >= expected && overAt <= expected + 1,
                    "1.16's Stand bind task ended after " + expected + " ticks, the port's after " + overAt);
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(elapsed < 30, "The Stand bind or its string outlived 30 ticks: bind over at " + overAt
                + ", string " + (string == null ? "never seen" : "age " + string.tickCount));
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> trackBind(helper, fixture, bind, expected, elapsed + 1, overAt, string));
    }

    // 1.16 SFStringEntity always catches (95-100): the Stand's string has no non-binding form, whatever flag a
    // caller sets or leaves unset. The port's old hidden form lived 16 ticks and hurt for 2.0.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standStringAlwaysCatchesWhateverBindingFlagItWasGiven(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            // Within the 8 blocks a Stand string with no speed factor reaches (1.16 SFStringEntity, 24 ticks).
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    stand.position().add(0.0D, 6.0D, 0.0D));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            StoneFreeStringEntity string = new StoneFreeStringEntity(Kind.STAND_STRING, stand, helper.getLevel());
            helper.assertTrue(string.ticksLifespan() == 24,
                    "A Stand string never given a binding flag lives " + string.ticksLifespan() + " ticks, not 24");
            string.setBinding(false);
            helper.assertTrue(string.ticksLifespan() == 24,
                    "A Stand string told it is not a binding one lives " + string.ticksLifespan() + " ticks, not 24");
            string.setShootingPosOf(stand);
            string.shootFromRotation(stand, -90.0F, stand.getYRot(), 0.0F, 1.5F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(string), "Could not add the Stand string");
            fixture.extras.add(string);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.STAND_STRING, 30,
                    caught -> caught.getEntityAttachedTo() == target, caught -> {
                        assertCaughtUnhurt(helper, target, "A Stand string with no binding flag");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // A Stand string saved while the hidden non-binding form existed has Binding=false; it loads as the
    // ordinary catching string, with a missing key as well.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 60)
    public static void savedNonBindingStandStringLoadsAsTheOrdinaryCatchingString(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStringEntity saved = new StoneFreeStringEntity(Kind.STAND_STRING, fixture.stand, helper.getLevel());
            CompoundTag tag = saved.saveWithoutId(new CompoundTag());
            for (boolean keyPresent : new boolean[] {true, false}) {
                CompoundTag old = tag.copy();
                if (keyPresent) {
                    old.putBoolean("Binding", false);
                }
                else {
                    old.remove("Binding");
                }
                StoneFreeStringEntity loaded = StoneFreeAddon.SF_STRING.get().create(helper.getLevel());
                helper.assertTrue(loaded != null, "Missing registered Stand string");
                loaded.load(old);
                helper.assertTrue(loaded.kind() == Kind.STAND_STRING && loaded.ticksLifespan() == 24,
                        "A saved Stand string with Binding " + (keyPresent ? "false" : "missing") + " loaded as "
                                + loaded.kind() + " living " + loaded.ticksLifespan() + " ticks, not as the"
                                + " 24 tick catching string");
            }
            StoneFreeBarrierGameTests.succeed(helper, fixture);
        });
    }

    // The ray of an OwnerBoundProjectileEntity returns every target on the string in one tick, and
    // 1.16 SF(U)StringEntity.hurtTarget caught only while nothing was attached yet: the next contact of that
    // tick was refused (dealtDamage). Both targets stand on the same spot, so one tick reaches both.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBindHoldsOnlyOneOfTwoTargetsReachedInTheSameTick(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            Vec3 spot = fixture.owner.position().add(0.0D, 8.0D, 0.0D);
            PolarBear first = StoneFreeGrappleGameTests.target(helper, fixture, spot);
            PolarBear second = StoneFreeGrappleGameTests.target(helper, fixture, spot);
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.USER_STRING, 16,
                    string -> string.getEntityAttachedTo() != null, string -> {
                        LivingEntity held = string.getEntityAttachedTo();
                        int immobilised = (first.hasEffect(ModStatusEffects.IMMOBILIZE) ? 1 : 0)
                                + (second.hasEffect(ModStatusEffects.IMMOBILIZE) ? 1 : 0);
                        helper.assertTrue(immobilised == 1 && held.hasEffect(ModStatusEffects.IMMOBILIZE),
                                "The user's bind immobilised " + immobilised + " of the two targets it reached in"
                                        + " one tick, and the string ended on " + held + "; 1.16 held only the"
                                        + " first: first=" + first.hasEffect(ModStatusEffects.IMMOBILIZE)
                                        + ", second=" + second.hasEffect(ModStatusEffects.IMMOBILIZE));
                        assertCaughtUnhurt(helper, first, "The user's bind");
                        assertCaughtUnhurt(helper, second, "The user's bind");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // The same for the Stand's bind, whose true result marks what it caught as hurt by the Stand's user.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void standBindMarksOnlyOneOfTwoTargetsReachedInTheSameTick(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            Vec3 spot = stand.position().add(0.0D, 10.0D, 0.0D);
            PolarBear first = StoneFreeGrappleGameTests.target(helper, fixture, spot);
            PolarBear second = StoneFreeGrappleGameTests.target(helper, fixture, spot);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(first));
            press(helper, fixture, StoneFreeAddon.STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.STAND_STRING, 30,
                    string -> string.getEntityAttachedTo() != null, string -> {
                        int marked = (first.getLastHurtByMob() == fixture.owner ? 1 : 0)
                                + (second.getLastHurtByMob() == fixture.owner ? 1 : 0);
                        helper.assertTrue(marked == 1 && string.getEntityAttachedTo().getLastHurtByMob()
                                        == fixture.owner,
                                "The Stand's bind marked " + marked + " of the two targets it reached in one tick"
                                        + " as hurt by the Stand's user, and the string ended on "
                                        + string.getEntityAttachedTo() + "; 1.16 caught only the first");
                        assertCaughtUnhurt(helper, first, "The Stand's bind");
                        assertCaughtUnhurt(helper, second, "The Stand's bind");
                        StoneFreeBarrierGameTests.succeed(helper, fixture);
                    });
        });
    }

    // 1.16 SFUStringEntity.hurtTarget and afterEntityHit never attach to, nor immobilise, the Stand of the string's
    // user. The port's strings are owned by that Stand, which a projectile never hits, so the guard here is that
    // a bind started inside the Stand never holds it.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 120)
    public static void userBindNeverHoldsTheUsersOwnStand(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            StoneFreeStandEntity stand = fixture.stand;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            PolarBear beyond = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.owner.position().add(0.0D, 8.0D, 0.0D));
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_BIND_NAME);
            StoneFreeGrappleGameTests.await(helper, fixture, Kind.USER_STRING, 3, string -> true, string -> {
                Vec3 origin = string.getOriginPoint(1.0F);
                stand.setPos(origin.x, origin.y - 0.5D, origin.z);
                watchBindThroughOwnStand(helper, fixture, stand, beyond, 30);
            });
        });
    }

    private static void watchBindThroughOwnStand(GameTestHelper helper, Fixture fixture, StoneFreeStandEntity stand,
            LivingEntity beyond, int ticksLeft) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_STRING);
        helper.assertTrue(string == null || string.getEntityAttachedTo() != stand,
                "The user's bind caught the user's own Stand " + stand + ", string age "
                        + (string == null ? -1 : string.tickCount));
        helper.assertTrue(!stand.hasEffect(ModStatusEffects.IMMOBILIZE),
                "The user's bind immobilised the user's own Stand");
        if (string != null && string.getEntityAttachedTo() == beyond) {
            StoneFreeBarrierGameTests.succeed(helper, fixture);
            return;
        }
        helper.assertTrue(ticksLeft > 0, "The user's bind did not get past the user's own Stand: "
                + (string == null ? "no string" : "age " + string.tickCount + " at " + string.position())
                + ", Stand at " + stand.position());
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchBindThroughOwnStand(helper, fixture, stand, beyond, ticksLeft - 1));
    }

    private static PolarBear unhurtableTarget(GameTestHelper helper, Fixture fixture, Vec3 feet) {
        PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture, feet);
        target.setInvulnerable(true);
        helper.assertTrue(!target.hurt(target.damageSources().generic(), 1.0F)
                        && target.getHealth() == target.getMaxHealth(),
                "The fixture's target could be hurt although it is invulnerable");
        return target;
    }

    private static void assertCaughtUnhurt(GameTestHelper helper, LivingEntity target, String what) {
        helper.assertTrue(target.getHealth() == target.getMaxHealth() && target.hurtTime == 0
                        && target.invulnerableTime == 0,
                what + " hurt what it caught: health " + target.getHealth() + " of " + target.getMaxHealth()
                        + ", hurtTime " + target.hurtTime + ", hurt cooldown " + target.invulnerableTime);
    }

    // 1.16 SFUStringSweepEntity has no such override: its hit is a plain hurt, which a stronger hit's cooldown
    // refuses. The second sweep, after the cooldown, shows that the first one did reach the target.
    @GameTest(template = "empty", templateNamespace = "rotp_sf_barrier_review", timeoutTicks = 160)
    public static void userSweepIsRefusedByTheHurtCooldownOfAStrongerHit(GameTestHelper helper) {
        Fixture fixture = StoneFreeGrappleGameTests.upwardFixture(helper);
        StoneFreeBarrierGameTests.later(helper, fixture, fixture.stand.summonLockTicks + 1, () -> {
            PolarBear target = StoneFreeGrappleGameTests.target(helper, fixture,
                    fixture.owner.position().add(0.0D, 3.0D, 0.0D));
            primeHurtCooldown(helper, target, 10.0F);
            float primed = target.getHealth();
            StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_SWEEP_NAME);
            watchSweep(helper, fixture, target, true, false, 14, () -> {
                helper.assertTrue(target.getHealth() == primed,
                        "A sweep hurt a target inside the hurt cooldown of a stronger hit: health " + primed
                                + " -> " + target.getHealth());
                target.invulnerableTime = 0;
                helper.assertTrue(usable(fixture, StoneFreeAddon.USER_STRING_SWEEP_NAME),
                        "The sweep could not be thrown again once its string was gone");
                StoneFreeGrappleGameTests.click(helper, fixture, StoneFreeAddon.USER_STRING_SWEEP_NAME);
                watchSweep(helper, fixture, target, false, false, 14, () -> {
                    helper.assertTrue(target.getHealth() < primed,
                            "The same sweep did not hurt the target once its hurt cooldown was over, so the"
                                    + " refused one proves nothing: health " + target.getHealth());
                    StoneFreeBarrierGameTests.succeed(helper, fixture);
                });
            });
        });
    }

    private static void primeHurtCooldown(GameTestHelper helper, LivingEntity target, float damage) {
        float before = target.getHealth();
        helper.assertTrue(target.hurt(target.damageSources().generic(), damage)
                        && target.getHealth() == before - damage && target.invulnerableTime > 10,
                "The priming hit of " + damage + " did not start the target's hurt cooldown: health " + before
                        + " -> " + target.getHealth() + ", cooldown " + target.invulnerableTime);
    }

    // The fixture holds the cooldown open every tick, so the string meets it whenever it arrives.
    private static void awaitCatchInsideCooldown(GameTestHelper helper, Fixture fixture, LivingEntity target,
            Kind kind, boolean seen, int ticksLeft, Consumer<StoneFreeStringEntity> assertions) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, kind);
        if (string != null && string.getEntityAttachedTo() == target) {
            helper.assertTrue(target.invulnerableTime > 10,
                    "The target's hurt cooldown was not running when it was caught: " + target.invulnerableTime);
            assertions.accept(string);
            return;
        }
        helper.assertTrue(ticksLeft > 0 && (string != null || !seen),
                "The " + kind + " bind did not catch a target inside its hurt cooldown (" + target.invulnerableTime
                        + " ticks left): " + (string != null ? "string age " + string.tickCount + " at "
                                + string.position() : seen ? "the string ended without a catch" : "no string")
                        + ", target at " + target.position());
        target.invulnerableTime = 20;
        boolean out = seen || string != null;
        StoneFreeBarrierGameTests.later(helper, fixture, 1, () -> awaitCatchInsideCooldown(
                helper, fixture, target, kind, out, ticksLeft - 1, assertions));
    }

    private static void watchSweep(GameTestHelper helper, Fixture fixture, LivingEntity target,
            boolean holdCooldown, boolean seen, int ticksLeft, Runnable done) {
        StoneFreeStringEntity string = StoneFreeGrappleGameTests.find(helper, fixture, Kind.USER_SWEEP);
        if (holdCooldown) {
            target.invulnerableTime = 20;
        }
        if (ticksLeft == 0) {
            helper.assertTrue(seen && string == null,
                    "The sweep's string was not thrown and gone again within 14 ticks: seen=" + seen
                            + ", still out=" + (string != null));
            done.run();
            return;
        }
        boolean out = seen || string != null;
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> watchSweep(helper, fixture, target, holdCooldown, out, ticksLeft - 1, done));
    }

    private static void assertHoldingLifespan(GameTestHelper helper, StoneFreeStringEntity string, String what) {
        string.tickCount = DONOR_HOLDING_LIFESPAN;
        string.tick();
        helper.assertTrue(string.isAlive(), what + " holding a catch ended before its 49th tick");
        string.tickCount = DONOR_HOLDING_LIFESPAN + 1;
        string.tick();
        helper.assertTrue(!string.isAlive(), what + " holding a catch outlived its 49 ticks");
    }

    private static void press(GameTestHelper helper, Fixture fixture, String abilityName) {
        StoneFreeBarrierGameTests.assertTicksEntities(helper, fixture);
        Ability ability = fixture.power.getAbility(abilityName);
        helper.assertTrue(ability != null && ability.checkConditions(fixture.power).isPositive(),
                "Registered " + abilityName + " refused the fixture");
        BufferingState buffering = BufferingState.clickOnly();
        AbilityInput.keyPress(StoneFreeBarrierGameTests.GRAPPLE_KEY, ability, fixture.owner, null,
                InputMethod.CLICK, 0.0F, buffering, ability.abilityId);
        AbilityInput.keyRelease(StoneFreeBarrierGameTests.GRAPPLE_KEY, fixture.owner);
        helper.assertTrue(buffering.isActionSuccess, "Public click of " + abilityName + " did not start");
    }

    private static void awaitCatch(GameTestHelper helper, Fixture fixture, LivingEntity target, int ticksLeft,
            Consumer<StoneFreeStringEntity> assertions) {
        StringBuilder seen = new StringBuilder();
        for (Entity entity : helper.getLevel().getAllEntities()) {
            if (entity instanceof StoneFreeStringEntity string && string.isAlive()
                    && string.kind() == Kind.STAND_STRING
                    && StoneFreeServices.userOf(string.getOwner()) == fixture.owner) {
                if (string.getEntityAttachedTo() == target) {
                    assertions.accept(string);
                    return;
                }
                seen.append(" age").append(string.tickCount).append('@').append(string.position());
            }
        }
        helper.assertTrue(ticksLeft > 0, "No capture string caught the target: strings" + seen
                + ", Stand action " + fixture.stand.getCurStandAction() + ", Stand pitch "
                + fixture.stand.getXRot() + ", target at " + target.position() + ", Stand at "
                + fixture.stand.position());
        StoneFreeBarrierGameTests.later(helper, fixture, 1,
                () -> awaitCatch(helper, fixture, target, ticksLeft - 1, assertions));
    }

    private static boolean usable(Fixture fixture, String abilityName) {
        return fixture.power.getAbility(abilityName).checkConditions(fixture.power).isPositive();
    }

    private static List<String> usable(Fixture fixture, List<String> abilityNames) {
        return abilityNames.stream().filter(name -> usable(fixture, name)).toList();
    }

    private static List<String> refused(Fixture fixture, List<String> abilityNames) {
        return abilityNames.stream().filter(name -> !usable(fixture, name)).toList();
    }

    private static List<String> warnings(Fixture fixture, List<String> abilityNames) {
        return abilityNames.stream()
                .filter(name -> fixture.power.getAbility(name).checkConditions(fixture.power).getWarning() != null)
                .toList();
    }

    private static List<String> withStringLimit(Fixture fixture) {
        Component limit = ConditionCheck.message("string_limit");
        return GRAPPLES.stream().filter(name -> {
            ConditionCheck check = fixture.power.getAbility(name).checkConditions(fixture.power);
            return !check.isPositive() && limit.equals(check.getWarning());
        }).toList();
    }
}
