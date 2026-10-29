package rotp.addons.heavensdoor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.ability.HeavensDoorTargeting;
import rotp.addons.heavensdoor.ability.TearOutAPageAbility.TearPageAction;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.item.PageItem;
import rotp.addons.heavensdoor.state.BookStateRules;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

/**
 * 1.16 PowerBaseImpl.tickHeldAction checked the crosshair target of a held action again on every tick: with an ENTITY
 * target requirement, a hold of an action that is not holdOnly (holdToFire(20, false)) stopped, silently or with
 * TearOutAPage.checkTarget's reason, as soon as the crosshair left a valid booked entity, and nothing fired. The tear
 * itself took the task target, so another valid booked entity under the crosshair kept the hold and the first target's
 * page came off. StandEntityTask paid staminaCost(100) and the cooldown after a perform that tore nothing as well.
 */
@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TearPageHoldRecheckGameTests {
    private static final short KEY = 124;

    private TearPageHoldRecheckGameTests() {}

    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageHoldStopsSilentlyWhenTheViewLeavesEveryEntity(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageHoldStopsSilentlyWhenTheViewLeavesEveryEntityInLoadedArea(helper));
    }

    private static void tearPageHoldStopsSilentlyWhenTheViewLeavesEveryEntityInLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearHoldAir")) {
            float staminaBefore = scene.power.getStamina();
            scene.pressAt(scene.held);
            scene.tickHold();
            helper.assertTrue(scene.running(),
                    "fixture: the hold must go on while the view stays on the booked cow");

            scene.owner.setXRot(-90.0F);
            HeavensDoorGameTestAims.send(scene.owner);
            helper.assertTrue(scene.aimed() == null,
                    "fixture: the view must meet no entity");
            scene.tickHold();
            helper.assertTrue(!scene.running(),
                    "1.16 stopped the held Tear Out a Page once the crosshair met no entity; it still runs: "
                            + scene.stand.getCurStandAction());
            helper.assertTrue(scene.pages(scene.held) == 0 && !scene.hasAnyPageItem(),
                    "1.16 fired nothing after the stop; pages " + scene.pages(scene.held));
            helper.assertTrue(scene.power.getStamina() >= staminaBefore,
                    "1.16 paid no stamina for a hold that was stopped; stamina " + staminaBefore + " -> "
                            + scene.power.getStamina());
            helper.assertTrue(!scene.power.isAbilityOnCooldown(HeavensDoorAddon.TEAR_PAGE_ID),
                    "1.16 set no cooldown for a hold that was stopped");
            helper.assertTrue(scene.owner.messages.isEmpty(),
                    "1.16 stopped the hold silently when no entity was aimed: " + scene.owner.keys());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageHoldStopsWithTheDonorReasonWhenTheViewMeetsAnUnbookedEntity(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageHoldStopsWithTheDonorReasonWhenTheViewMeetsAnUnbookedEntityInLoadedArea(helper));
    }

    private static void tearPageHoldStopsWithTheDonorReasonWhenTheViewMeetsAnUnbookedEntityInLoadedArea(
            GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearHoldPlain")) {
            float staminaBefore = scene.power.getStamina();
            scene.pressAt(scene.held);
            scene.tickHold();
            helper.assertTrue(scene.running(), "fixture: the hold must go on while the view stays on the booked cow");

            HeavensDoorGameTestAims.lookAt(scene.owner, scene.plain);
            helper.assertTrue(scene.aimed() == scene.plain,
                    "fixture: the view must meet the cow without a book");
            scene.tickHold();
            helper.assertTrue(!scene.running(),
                    "1.16 checkTarget answered no_book with setContinueHold(false) and the hold stopped; it still runs: "
                            + scene.stand.getCurStandAction());
            helper.assertTrue(scene.owner.keys().equals(List.of("jojo.message.action_condition.no_book")),
                    "1.16 showed the no_book reason when the hold stopped: " + scene.owner.keys());
            helper.assertTrue(scene.pages(scene.held) == 0 && scene.pages(scene.plain) == 0
                            && !scene.hasAnyPageItem(),
                    "1.16 fired nothing after the stop");
            helper.assertTrue(scene.power.getStamina() >= staminaBefore
                            && !scene.power.isAbilityOnCooldown(HeavensDoorAddon.TEAR_PAGE_ID),
                    "1.16 paid no stamina and set no cooldown for a hold that was stopped");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageHoldGoesOnAndTearsItsOwnTargetWhenTheViewMeetsAnotherBookedEntity(
            GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageHoldGoesOnAndTearsItsOwnTargetWhenTheViewMeetsAnotherBookedEntityInLoadedArea(
                        helper));
    }

    private static void tearPageHoldGoesOnAndTearsItsOwnTargetWhenTheViewMeetsAnotherBookedEntityInLoadedArea(
            GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearHoldOther")) {
            scene.pressAt(scene.held);
            HeavensDoorGameTestAims.lookAt(scene.owner, scene.other);
            helper.assertTrue(scene.aimed() == scene.other,
                    "fixture: the view must meet the other booked cow");
            scene.tickHold();
            helper.assertTrue(scene.running() && scene.stand.getCurStandAction() instanceof TearPageAction,
                    "1.16 checkTarget passed for any valid booked entity, so the hold goes on; it stopped");

            scene.fire();
            helper.assertTrue(scene.pages(scene.held) == 1 && scene.pages(scene.other) == 0,
                    "1.16 tore the page of the cow the hold started at; pages on it " + scene.pages(scene.held)
                            + ", on the cow the user looks at " + scene.pages(scene.other));
            helper.assertTrue(scene.power.isAbilityOnCooldown(HeavensDoorAddon.TEAR_PAGE_ID),
                    "the tear sets the cooldown");
            helper.succeed();
        }
    }

    // 1.16 StandEntityTask.tick called standPerform and then consumeStamina(getStaminaCost) with no regard for the
    // result, and the cooldown came with it; the owner kept the port's message for a target that is gone.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPagePaysStaminaAndCooldownWhenItsTargetIsGoneAtThePerform(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPagePaysStaminaAndCooldownWhenItsTargetIsGoneAtThePerformInLoadedArea(helper));
    }

    private static void tearPagePaysStaminaAndCooldownWhenItsTargetIsGoneAtThePerformInLoadedArea(
            GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearGoneCost")) {
            scene.pressAt(scene.held);
            helper.assertTrue(scene.running(), "fixture: the hold did not start");
            float staminaBefore = scene.power.getStamina();
            helper.assertTrue(staminaBefore >= BookStateRules.TEAR_STAMINA_COST,
                    "fixture: the bar must hold the cost, it holds " + staminaBefore);
            scene.held.discard();
            scene.fire();
            helper.assertTrue(Math.abs(staminaBefore - scene.power.getStamina()
                            - BookStateRules.TEAR_STAMINA_COST) < 0.01F,
                    "1.16 paid the 100 stamina after a perform that tore nothing; stamina " + staminaBefore + " -> "
                            + scene.power.getStamina());
            helper.assertTrue(scene.power.isAbilityOnCooldown(HeavensDoorAddon.TEAR_PAGE_ID),
                    "1.16 set the cooldown after a perform that tore nothing");
            helper.assertTrue(scene.owner.keys().equals(List.of("rotp_hd.message.cannot_tear_page")),
                    "the owner kept the message for a target that is gone: " + scene.owner.keys());
            helper.assertTrue(!scene.running(), "the failed perform must end the action");
            helper.succeed();
        }
    }

    // 1.16 heavens_door_tear_out_a_page had no partsRequired: a Heaven's Door that lost its arms (Boy II Man's game)
    // still pressed, held and tore.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageNeedsNoStandPartAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageNeedsNoStandPartAsIn116InLoadedArea(helper));
    }

    private static void tearPageNeedsNoStandPartAsIn116InLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearNoArms")) {
            scene.power.getStandInstance().orElseThrow().removePart(StandPart.ARMS);
            HeavensDoorGameTestAims.lookAt(scene.owner, scene.held);
            ConditionCheck press = scene.tear.checkConditions(scene.power);
            helper.assertTrue(press.isPositive(),
                    "1.16 accepted the press of Tear Out a Page without the Stand's arms; the port answered "
                            + press.getWarning());
            scene.pressAt(scene.held);
            scene.tickHold();
            helper.assertTrue(scene.running(),
                    "1.16 kept the hold of a Heaven's Door without arms; it stopped");
            scene.fire();
            helper.assertTrue(scene.pages(scene.held) == 1,
                    "1.16 tore the page with the Stand's arms gone; pages " + scene.pages(scene.held));
            helper.succeed();
        }
    }

    // 1.16 StandEntityAction.checkRangeAndTarget then checkStandTarget (StandEntity.canAttack): a booked entity the
    // Stand may not attack (a teammate without friendly fire) refused the press and stopped the hold, silently.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageRefusesAndStopsAtWhatTheStandMayNotAttack(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageRefusesAndStopsAtWhatTheStandMayNotAttackInLoadedArea(helper));
    }

    private static void tearPageRefusesAndStopsAtWhatTheStandMayNotAttackInLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearAlly")) {
            Scoreboard board = helper.getLevel().getScoreboard();
            PlayerTeam team = board.addPlayerTeam("hd-allies-" + helper.getTick());
            try {
                team.setAllowFriendlyFire(false);
                board.addPlayerToTeam(scene.owner.getScoreboardName(), team);
                board.addPlayerToTeam(scene.other.getScoreboardName(), team);
                scene.pressAt(scene.held);
                HeavensDoorGameTestAims.lookAt(scene.owner, scene.other);
                helper.assertTrue(scene.aimed() == scene.other,
                        "fixture: the view must meet the booked teammate cow");
                scene.tickHold();
                helper.assertTrue(!scene.running(),
                        "1.16 stopped the hold once the crosshair met a booked entity the Stand may not attack;"
                                + " it still runs: " + scene.stand.getCurStandAction());
                helper.assertTrue(scene.owner.messages.isEmpty() && scene.pages(scene.held) == 0
                                && scene.pages(scene.other) == 0,
                        "1.16 stopped it silently and tore nothing: " + scene.owner.keys());
                ConditionCheck press = scene.tear.checkConditions(scene.power);
                helper.assertTrue(!press.isPositive() && press.getWarning() == null,
                        "1.16 refused the press at a teammate without friendly fire silently; the port answered "
                                + (press.isPositive() ? "a positive check" : press.getWarning()));
            }
            finally {
                board.removePlayerTeam(team);
            }
            helper.succeed();
        }
    }

    // 1.16 Action.checkRangeAndTarget measured the target from the performer, the Stand: past 8 blocks (4 without a
    // line of sight) it answered target_too_far on the press and stopped the hold with it.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageAnswersTargetTooFarFromTheStand(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageAnswersTargetTooFarFromTheStandInLoadedArea(helper));
    }

    private static void tearPageAnswersTargetTooFarFromTheStandInLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearStandFar")) {
            scene.pressAt(scene.held);
            scene.tickHold();
            helper.assertTrue(scene.running(), "fixture: the hold must go on with the Stand at the cow");
            Vec3 far = scene.held.position().add(0.0D, 0.0D, 12.0D);
            scene.stand.moveTo(far.x, far.y, far.z, 0.0F, 0.0F);
            scene.tickHold();
            helper.assertTrue(!scene.running(),
                    "1.16 stopped the hold once its booked cow was 12 blocks from the Stand; it still runs");
            helper.assertTrue(scene.owner.keys().equals(List.of("jojo.message.action_condition.target_too_far")),
                    "1.16 named target_too_far when the hold stopped: " + scene.owner.keys());
            ConditionCheck press = scene.tear.checkConditions(scene.power);
            helper.assertTrue(press.getWarning() != null && press.getWarning().getContents()
                            instanceof TranslatableContents translatable
                            && translatable.getKey().equals("jojo.message.action_condition.target_too_far"),
                    "1.16 refused the press with target_too_far; the port answered "
                            + (press.isPositive() ? "a positive check" : press.getWarning()));
            helper.assertTrue(scene.pages(scene.held) == 0, "nothing was torn");
            helper.succeed();
        }
    }

    // 1.16 sent the mouse target the client picked from its camera entity, which is the Stand under manual control
    // (StandUtil.java:160, InputHandler.pickMouseTarget): the press, every held tick and the torn page followed what
    // the Stand looked at, and TearOutAPage.checkTarget still measured too_far (4.5 blocks) from the user.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageAimsFromAManuallyControlledStandAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageAimsFromAManuallyControlledStandAsIn116InLoadedArea(helper));
    }

    private static void tearPageAimsFromAManuallyControlledStandAsIn116InLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearManualAim")) {
            Cow far = scene.cow(new Vec3(6.5D, 2.0D, 5.5D));
            try {
                helper.assertTrue(BookStateService.markFromHit(scene.stand, far), "Book marking failed");
                scene.stand.setManuallyControlled(true);
                helper.assertTrue(scene.stand.isManuallyControlled(), "fixture: the Stand is not manually controlled");
                Vec3 standPos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.0D));
                scene.stand.moveTo(standPos.x, standPos.y, standPos.z, 0.0F, 0.0F);
                scene.owner.lookAt(EntityAnchorArgument.Anchor.EYES, scene.other.getEyePosition());
                HeavensDoorGameTestAims.standLooksAt(scene.owner, scene.stand, scene.held);
                helper.assertTrue(scene.aimed() == scene.held,
                        "fixture: the Stand must look at one booked cow while the user looks at the other: "
                                + scene.aimed());

                scene.press();
                scene.tickHold();
                helper.assertTrue(scene.running(), "fixture: the hold must go on while the Stand looks at its cow");
                scene.fire();
                helper.assertTrue(scene.pages(scene.held) == 1 && scene.pages(scene.other) == 0,
                        "1.16 tore the page of the cow the manually controlled Stand looked at; pages on it "
                                + scene.pages(scene.held) + ", on the cow the user looks at " + scene.pages(scene.other));

                AbilityInput.keyRelease(KEY, scene.owner);
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
                scene.power.setAbilityCooldown(HeavensDoorAddon.TEAR_PAGE_ID, 0);
                scene.power.setStamina(scene.power.getMaxStamina());
                scene.owner.messages.clear();
                scene.press();
                scene.stand.setXRot(-90.0F);
                HeavensDoorGameTestAims.sendFromStand(scene.owner, scene.stand);
                scene.tickHold();
                helper.assertTrue(!scene.running(),
                        "1.16 stopped the hold once the manually controlled Stand looked at no entity, wherever the"
                                + " user looked; it still runs: " + scene.stand.getCurStandAction());
                helper.assertTrue(scene.owner.messages.isEmpty(), "1.16 stopped it silently: " + scene.owner.keys());

                Vec3 nearFar = helper.absoluteVec(new Vec3(5.0D, 2.0D, 3.5D));
                scene.stand.moveTo(nearFar.x, nearFar.y, nearFar.z, 0.0F, 0.0F);
                HeavensDoorGameTestAims.standLooksAt(scene.owner, scene.stand, far);
                helper.assertTrue(scene.aimed() == far
                                && scene.owner.distanceTo(far) > BookStateRules.ACTION_RANGE,
                        "fixture: the Stand must look at the booked cow more than 4.5 blocks from the user");
                ConditionCheck press = scene.tear.checkConditions(scene.power);
                helper.assertTrue(press.getWarning() != null && press.getWarning().getContents()
                                instanceof TranslatableContents translatable
                                && translatable.getKey().equals("jojo.message.action_condition.too_far"),
                        "1.16 refused the Stand's target 4.5 blocks or more from the user with too_far; the port answered "
                                + (press.isPositive() ? "a positive check" : press.getWarning()));
                helper.assertTrue(scene.pages(far) == 0, "nothing was torn from the far cow");
            }
            finally {
                far.discard();
            }
            helper.succeed();
        }
    }

    // 1.16 noAdheringToUserOffset kept the Stand off its user's offset; a client that does not know the user's power
    // asks the fallback, which must agree.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 40)
    public static void tearPageKeepsTheStandOffItsUserOnEveryClient(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageKeepsTheStandOffItsUserOnEveryClientInLoadedArea(helper));
    }

    private static void tearPageKeepsTheStandOffItsUserOnEveryClientInLoadedArea(GameTestHelper helper) {
        try (Scene scene = new Scene(helper, "HdTearFallback")) {
            StandEntityAbility tear = (StandEntityAbility) scene.tear;
            helper.assertTrue(tear.noAdheringToUserOffset(scene.power, scene.stand)
                            && tear.noAdheringToUserOffsetClientFallback(scene.stand),
                    "Tear Out a Page keeps the Stand off its user's offset on the server ("
                            + tear.noAdheringToUserOffset(scene.power, scene.stand)
                            + ") and must on a client without the power too ("
                            + tear.noAdheringToUserOffsetClientFallback(scene.stand) + ")");
            helper.succeed();
        }
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper helper;
        final RecordingPlayer owner;
        final Cow held;
        final Cow other;
        final Cow plain;
        final StandPower power;
        final HeavensDoorStandEntity stand;
        private final Ability tear;

        Scene(GameTestHelper helper, String name) {
            this.helper = helper;
            owner = new RecordingPlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            owner.getAbilities().instabuild = false;
            owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
            helper.getLevel().addNewPlayer(owner);
            held = cow(new Vec3(3.5D, 2.0D, 4.5D));
            other = cow(new Vec3(-0.5D, 2.0D, 4.5D));
            plain = cow(new Vec3(1.5D, 2.0D, 4.5D));
            power = PowerClass.STAND.attachGet(owner);
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            power.setResolveLevel(4);
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(owner, power),
                    "Heaven's Door summon failed");
            stand = HeavensDoorAddon.controller(owner);
            helper.assertTrue(stand != null, "Missing summoned Heaven's Door");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.assertTrue(BookStateService.markFromHit(stand, held)
                            && BookStateService.markFromHit(stand, other),
                    "Book marking failed");
            power.tick(); // moves are cached per tick
            power.setStamina(power.getMaxStamina());
            tear = power.getAbility(HeavensDoorAddon.TEAR_PAGE_ID);
        }

        private Cow cow(Vec3 position) {
            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Cow fixture failed");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setPos(helper.absoluteVec(position));
            helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Cow fixture could not join the level");
            return cow;
        }

        void pressAt(Cow target) {
            HeavensDoorGameTestAims.lookAt(owner, target);
            press();
        }

        // what the press, the held recheck and the task take: 1.16's mouse target
        LivingEntity aimed() {
            return HeavensDoorTargeting.livingTarget(HeavensDoorTargeting.pressedAim(owner));
        }

        void press() {
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(tear);
            helper.assertTrue(selected != null, "Tear Out a Page is not in the moveset");
            helper.assertTrue(AbilityInput.withConditionCheck(selected, owner, InputMethod.HOLD),
                    "the press at the booked cow was refused: " + selected.conditionCheck.getWarning());
            BufferingState press = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, owner, null, InputMethod.HOLD, 0, press,
                    selected.ability.getAbilityId());
            helper.assertTrue(press.isActionSuccess && stand.getCurStandAction() instanceof TearPageAction,
                    "the press did not start Tear Out a Page");
            helper.assertTrue(stand.getCurStandAction().getPhase() == ActionPhase.BUTTON_CHARGE,
                    "fixture: the press must start the charge");
        }

        // One server tick of the running action, with its key still down
        void tickHold() {
            if (running()) {
                stand.getCurStandAction()._tickAction();
            }
        }

        // The charge ending as the key is still down: the perform starts on the next tick
        void fire() {
            TearPageAction action = (TearPageAction) stand.getCurStandAction();
            action.setPhaseStart(ActionPhase.PERFORM);
            action._tickAction();
        }

        boolean running() {
            var action = stand.getCurStandAction();
            return action != null && !action.isOver();
        }

        int pages(Cow cow) {
            return HeavensDoorAddon.bookState(cow).pageCount();
        }

        boolean hasAnyPageItem() {
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                if (PageItem.data(owner.getInventory().getItem(slot)) != null) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, owner);
            if (power != null && power.isSummoned()) {
                HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            }
            held.discard();
            other.discard();
            plain.discard();
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }

    // Keeps the action-bar messages a FakePlayer would drop.
    private static final class RecordingPlayer extends FakePlayer {
        final List<Component> messages = new ArrayList<>();

        RecordingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message);
        }

        List<String> keys() {
            List<String> found = new ArrayList<>();
            for (Component message : messages) {
                found.add(message.getContents() instanceof TranslatableContents translatable
                        ? translatable.getKey()
                        : message.getString());
            }
            return found;
        }
    }
}
