package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerDisablerClientState;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ScenePlayer;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.network.GerDisablerPayloads;
import rotp.addons.ger.state.GerLivingState;
import rotp.core.api.leap.LeapAccessPolicies;
import rotp.core.api.leap.LeapSource;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * 1.16 GER's DisablerMixin and DisablerNonStandMixin refused every StandAction and NonStandAction of a user tagged
 * ger_disabler, which the Disabler (Disabler.java :101-113, 180 ticks) and Return to Zero's lockdown (ReturnToZero.java
 * :125) gave, with the message jojo.message.action_condition.on_disable, at the head of Action.checkConditions on both
 * sides: the HUD greyed every slot, on the user's client too (the server told it, DisablerHandler.syncDisabled), a press
 * was refused, and PowerBaseImpl.tickHeldAction (:376-381) ended a held action with the message, so a time stop charging
 * when the lockdown came never stopped time. The port refuses in the core's condition checks (AbilityRefusals: the HUD,
 * the press, the held recheck) and interrupts the locked-down user's actions every tick; the time stop charges in the
 * user's Stand-power slot. A respawn or an End return ended the lockdown, and the client was told.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerLockdownTimeStopGameTests {
    private static final short TIME_STOP_KEY = 405;
    private static final short BLOOD_DRAIN_KEY = 406;
    private static final short MANUAL_CONTROL_KEY = 407;
    private static final String ON_DISABLE = "jojo.message.action_condition.on_disable";
    private static final String USABLE = "usable";
    // longer than either charge (Star Platinum 40 ticks, The World 20 from Resolve level 4)
    private static final int WATCH_TICKS = 60;
    private static final int SHORT_LOCKDOWN_TICKS = 5;

    private GerLockdownTimeStopGameTests() {}

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_lockdown_disabler_ts")
    public static void disablerKeepsAPressedTimeStopFromStoppingTimeAsIn116(GameTestHelper helper) {
        new LockdownSequence(helper, "lockdown/disabler", false).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_lockdown_rtz_ts")
    public static void returnToZeroLockdownKeepsAPressedTimeStopFromStoppingTimeAsIn116(GameTestHelper helper) {
        new LockdownSequence(helper, "lockdown/rtz", true).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_lockdown_disabler_press")
    public static void disablerRefusesEveryStandAndNonStandPressWithItsMessageAsIn116(GameTestHelper helper) {
        new PressRefusalSequence(helper, "lockdown/disabler-press", false).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_lockdown_rtz_press")
    public static void returnToZeroLockdownRefusesEveryStandAndNonStandPressWithItsMessageAsIn116(
            GameTestHelper helper) {
        new PressRefusalSequence(helper, "lockdown/rtz-press", true).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_lockdown_leap")
    public static void disablerAndReturnToZeroLockdownKeepAStandUserFromLeapingAsIn116(GameTestHelper helper) {
        new LeapSequence(helper, "lockdown/leap").start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_lockdown_disabler_respawn")
    public static void disablerEndsForARespawnedPlayerOnTheServerAndItsClientAsIn116(GameTestHelper helper) {
        new RespawnSequence(helper, "lockdown/disabler-respawn", false).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_lockdown_rtz_respawn")
    public static void returnToZeroLockdownEndsForARespawnedPlayerOnTheServerAndItsClientAsIn116(
            GameTestHelper helper) {
        new RespawnSequence(helper, "lockdown/rtz-respawn", true).start();
    }

    private static void lockDown(LivingEntity entity, boolean returnToZero, int ticks) {
        GerLivingState state = GoldExperienceRequiemAddon.livingState(entity);
        UUID caster = UUID.randomUUID();
        if (returnToZero) {
            state.startReturnToZeroDisable(caster, entity, ticks);
        }
        else {
            state.applyDisabler(caster, ticks);
        }
    }

    private static String lockdownName(boolean returnToZero) {
        return returnToZero ? "Return to Zero lockdown" : "Disabler";
    }

    private static TimeStopState timeStops(GerMeleeGameTestScene scene) {
        return scene.level.getData(ModDataAttachmentTypes.TIME_STOP.get());
    }

    // dropped, not let go: a release in the charge would stop time for a shorter while
    private static void dropTimeStop(GerMeleeGameTestScene scene, Actor user) {
        user.user().getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.remove(TIME_STOP_KEY);
        LivingComponentAction.getComponent(user.user(), PowerClass.STAND).setAction(null, SyncType.NO_SYNC);
        timeStops(scene).removeInstance(user.user().getId());
    }

    private static int onDisableMessages(List<Object> received) {
        int count = 0;
        for (Object packet : received) {
            if (packet instanceof ClientboundSystemChatPacket chat
                    && chat.content().getContents() instanceof TranslatableContents text
                    && ON_DISABLE.equals(text.getKey())) {
                count++;
            }
        }
        return count;
    }

    private static String answer(ConditionCheck check) {
        if (check.isPositive()) {
            return USABLE;
        }
        Component warning = check.getWarning();
        if (warning == null) {
            return "refused without a message";
        }
        return warning.getContents() instanceof TranslatableContents text ? text.getKey() : warning.getString();
    }

    /** The slot a power's key resolves to now, asked again: a power answers its keys once a tick. */
    private static AbilityConditionCheck freshKey(Power<?> power, String name) {
        Ability base = power.getAbility(name);
        if (base == null) {
            return null;
        }
        AvailableAbilities moves = power.updateAvailableMoves();
        moves.update(power, power.getMoveset());
        return moves.getContextVariationContainer(base);
    }

    /**
     * Row 0: a Star Platinum user with its Stand out; row 1: a The World user from Resolve level 3, not out. The time
     * stop is pressed and held before the lockdown, which comes a tick into its charge; 1.16's held tick ended it with
     * on_disable. Row 2: a Star Platinum user whose charge no key holds, which no held recheck reaches; the lockdown's
     * per-tick interrupt ends it.
     */
    private static final class LockdownSequence extends Sequence {
        private final boolean returnToZero;
        private final List<Integer> stopsStarted = new ArrayList<>();
        private final List<String> phases = new ArrayList<>();
        private boolean listening;
        private int row;
        private Actor actor;
        private EntityActionInstance pressed;
        private boolean lockedDown;
        private int lockedAt;
        private int messagesAtLockdown;

        LockdownSequence(GameTestHelper helper, String id, boolean returnToZero) {
            super(helper, id, 4 * WATCH_TICKS + 40);
            this.returnToZero = returnToZero;
        }

        @Override
        boolean step() {
            if (!listening) {
                listening = true;
                scene.listen(TimeStopLifecycleEvent.Added.class,
                        event -> stopsStarted.add(event.getInstance().userId()));
            }
            if (actor == null) {
                setUp();
                return false;
            }
            if (pressed == null) {
                pressTimeStop();
                return false;
            }
            if (!lockedDown) {
                scene.premise(!pressed.isOver() && pressed.getPhase() == ActionPhase.BUTTON_CHARGE
                                && LivingComponentAction.getCurEntityAction(actor.user(), PowerClass.STAND) == pressed
                                && AbilityInput.isHeldByKey(actor.user(), pressed) == heldByKey(),
                        "the time stop of " + who() + " still charges a tick after its press: " + pressed.getPhase()
                                + ", held by its key " + AbilityInput.isHeldByKey(actor.user(), pressed));
                messagesAtLockdown = onDisableMessages(actor.received());
                lockDown(actor.user(), returnToZero, 3 * WATCH_TICKS);
                lockedDown = true;
                lockedAt = tick;
                return false;
            }
            EntityActionInstance current =
                    LivingComponentAction.getCurEntityAction(actor.user(), PowerClass.STAND);
            String phase = current == null ? "none"
                    : current != pressed ? "another action"
                    : pressed.isOver() ? "over"
                    : String.valueOf(pressed.getPhase());
            if (phases.isEmpty() || !phases.get(phases.size() - 1).equals(phase)) {
                phases.add(phase);
            }
            boolean stopped = stopsStarted.contains(actor.user().getId())
                    || timeStops(scene).getInstance(actor.user().getId()).isPresent();
            helper.assertTrue(!stopped && !phases.contains(String.valueOf(ActionPhase.PERFORM)),
                    scene.id + ": 1.16 ended the held time stop charge of " + who() + " once the "
                            + lockdownName(returnToZero) + " came, so time never stopped; " + (tick - lockedAt)
                            + " ticks into the lockdown time " + (stopped ? "stopped" : "did not stop")
                            + ", the user's Stand-power slot went " + phases);
            if (tick - lockedAt < WATCH_TICKS) {
                return false;
            }
            helper.assertTrue(current != pressed,
                    scene.id + ": the " + lockdownName(returnToZero) + " left the time stop of " + who()
                            + " in the user's Stand-power slot: " + phases);
            int told = onDisableMessages(actor.received()) - messagesAtLockdown;
            if (heldByKey()) {
                helper.assertTrue(told == 1,
                        scene.id + ": 1.16's held tick re-ran checkConditions and ended the time stop charge of "
                                + who() + " with " + ON_DISABLE + " once; shown " + told + " times, the slot went "
                                + phases);
            }
            scene.log(who() + " phases=" + phases + " on_disable=" + told);
            dropTimeStop(scene, actor);
            actor = null;
            pressed = null;
            lockedDown = false;
            phases.clear();
            row++;
            return row > 2;
        }

        private boolean heldByKey() {
            return row < 2;
        }

        private void setUp() {
            boolean starPlatinum = row != 1;
            Vec3 at = scene.origin.add(row == 2 ? -3.0D : 3.0D * row, 0.0D, 0.0D);
            Actor user = scene.standUser(at, 0,
                    starPlatinum ? ModStands.STAR_PLATINUM.get() : ModStands.THE_WORLD.get(), starPlatinum);
            scene.onClose(() -> dropTimeStop(scene, user));
            actor = user;
            scene.premise(starPlatinum
                            ? user.power().isSummoned()
                            : !user.power().isSummoned() && user.power().getResolveLevel() >= 3,
                    who() + " is set up: summoned " + user.power().isSummoned() + ", Resolve level "
                            + user.power().getResolveLevel());
        }

        // the time stop pressed and held as a client's key would
        private void pressTimeStop() {
            Ability timeStop = actor.power().getAbility("time_stop");
            AbilityConditionCheck selected = scene.resolve(actor, "time_stop");
            scene.premise(selected != null && selected.ability == timeStop
                            && timeStop.isAbilityUnlocked(actor.power())
                            && AbilityInput.withConditionCheck(selected, actor.user(), InputMethod.HOLD),
                    "the time stop key of " + who() + " is taken: "
                            + (selected == null ? "nothing" : selected.ability.name() + " " + selected.conditionCheck));
            BufferingState buffer = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(TIME_STOP_KEY, selected.ability, actor.user(), null, InputMethod.HOLD, 0, buffer,
                    timeStop.getAbilityId());
            pressed = LivingComponentAction.getCurEntityAction(actor.user(), PowerClass.STAND);
            scene.premise(buffer.isActionSuccess && pressed != null && pressed.ability == timeStop
                            && pressed.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "the time stop of " + who() + " charges in the user's Stand-power slot: went off "
                            + buffer.isActionSuccess + ", the slot holds " + pressed);
            if (!heldByKey()) {
                actor.user().getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.remove(TIME_STOP_KEY);
            }
        }

        private String who() {
            return switch (row) {
                case 0 -> "a Star Platinum user with its Stand out";
                case 1 -> "a The World user without its Stand";
                default -> "a Star Platinum user whose charge no key holds";
            };
        }
    }

    /**
     * 1.16 DisablerLeapMixin: StandPower.canLeap was false while the user had the ger_disabler tag (the Disabler's and
     * Return to Zero's), and on its client while DisablerClientState said so; NonStandPower.canLeap was left alone. Row 0
     * the Disabler, row 1 the Return to Zero lockdown, each on a Star Platinum user whose Stand can leap: no Stand leap
     * (server and HUD surface) while it lasts, the player-power leap source not denied, the Stand leap back once over.
     */
    private static final class LeapSequence extends Sequence {
        private Actor actor;
        private int row;
        private boolean lockedDown;

        LeapSequence(GameTestHelper helper, String id) {
            super(helper, id, 200);
        }

        @Override
        boolean step() {
            if (actor == null) {
                actor = scene.standUser(scene.origin, 0, ModStands.STAR_PLATINUM.get(), true);
                return false;
            }
            boolean returnToZero = row == 1;
            if (!lockedDown) {
                if (!actor.power().canLeap()) {
                    scene.premise(tick < 100, "Star Platinum's user can leap with its Stand before the "
                            + lockdownName(returnToZero) + ": leap unlocked " + actor.power().isLeapUnlocked()
                            + ", strength " + actor.power().leapStrength() + ", cooldown "
                            + actor.power().getLeapCooldown());
                    return false;
                }
                lockDown(actor.user(), returnToZero, SHORT_LOCKDOWN_TICKS);
                lockedDown = true;
                boolean canLeap = actor.power().canLeap();
                boolean hud = LeapAccessPolicies.allowsHud(actor.user(), LeapSource.STAND);
                boolean playerPower = LeapAccessPolicies.allowsExecution(actor.user(), LeapSource.PLAYER_POWER);
                helper.assertTrue(!canLeap && !hud && playerPower,
                        scene.id + ": 1.16 made StandPower.canLeap false for a user under the "
                                + lockdownName(returnToZero) + " and left other powers' leaps alone; Stand leap "
                                + canLeap + ", Stand leap on the HUD " + hud + ", player-power leap allowed "
                                + playerPower);
                return false;
            }
            if (GoldExperienceRequiemAddon.livingState(actor.user()).actionsDisabled()) {
                helper.assertTrue(!actor.power().canLeap(), scene.id + ": the Stand leap came back while the "
                        + lockdownName(returnToZero) + " still held its user");
                return false;
            }
            helper.assertTrue(actor.power().canLeap(), scene.id + ": the Stand leap did not come back once the "
                    + lockdownName(returnToZero) + " was over");
            lockedDown = false;
            row++;
            return row > 1;
        }
    }

    private static boolean disabled(LivingEntity entity) {
        return GoldExperienceRequiemAddon.livingState(entity).actionsDisabled();
    }

    private static void asOwnClient(ServerPlayer player, boolean ownClient) {
        ((ScenePlayer) player).ownClient = ownClient;
    }

    private static GerDisablerPayloads.StatePayload lastTold(LivingEntity player, List<Object> received) {
        GerDisablerPayloads.StatePayload last = null;
        for (Object packet : received) {
            if (packet instanceof GerDisablerPayloads.StatePayload state && state.entityId() == player.getId()) {
                last = state;
            }
        }
        return last;
    }

    // what the payload's handler does on the player's client
    private static void applyTold(LivingEntity player, List<Object> received) {
        GerDisablerPayloads.StatePayload last = lastTold(player, received);
        GerDisablerClientState.setDisabled(last.entityId(), last.disabled());
    }

    /** A Star Platinum user (Stand out) and a vampire aiming Blood Drain at a cow, for one kind of lockdown. */
    private abstract static class PairSequence extends Sequence {
        final boolean returnToZero;
        Actor standUser;
        Client vampire;
        LivingEntity prey;

        PairSequence(GameTestHelper helper, String id, int deadline, boolean returnToZero) {
            super(helper, id, deadline);
            this.returnToZero = returnToZero;
        }

        // a full-power vampire, as /jojopower gives it, keeps vampirism on death
        void setUpPair(Vec3 at, boolean fullPowerVampire) {
            standUser = scene.standUser(at, 0, ModStands.STAR_PLATINUM.get(), true);
            scene.onClose(() -> dropTimeStop(scene, standUser));
            vampire = scene.client(at.add(3.0D, 0.0D, 0.0D));
            PlayerPower vampirism = PowerClass.PLAYER_POWER.attachGet(vampire.player());
            vampirism.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            if (fullPowerVampire) {
                vampirism.getCurTypeData(ModPlayerPowers.VAMPIRISM)
                        .ifPresent(data -> data.setVampireFullPower(true, vampire.player()));
            }
            // the client players look north
            prey = scene.cow(at.add(3.0D, 0.0D, -1.5D));
            LivingComponentAction.getComponent(vampire.player()).entityAim.setTarget(new ActionTarget(prey));
            scene.onClose(() -> {
                AbilityInput.keyRelease(BLOOD_DRAIN_KEY, vampire.player());
                LivingComponentAction drain = LivingComponentAction.getComponent(vampire.player());
                drain.entityAim.setTarget(ActionTarget.EMPTY);
                drain.setAction(null, vampire.player(), SyncType.NO_SYNC);
                asOwnClient(standUser.user(), false);
                asOwnClient(vampire.player(), false);
                GerDisablerClientState.setDisabled(standUser.user().getId(), false);
                GerDisablerClientState.setDisabled(vampire.player().getId(), false);
                LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
            });
            scene.premise(standUser.power().isSummoned(), "Star Platinum is out");
            String timeStop = answer(timeStopKey().conditionCheck);
            String drain = answer(drainKey().conditionCheck);
            scene.premise(USABLE.equals(timeStop) && USABLE.equals(drain),
                    "before the lockdown the time stop (" + timeStop + ") and Blood Drain at the cow (" + drain
                            + ") are usable");
        }

        void lockDownPair(int ticks) {
            lockDown(standUser.user(), returnToZero, ticks);
            lockDown(vampire.player(), returnToZero, ticks);
        }

        void requireToldDisabled(LivingEntity player, List<Object> received, boolean disabled) {
            GerDisablerPayloads.StatePayload last = lastTold(player, received);
            helper.assertTrue(last != null && last.disabled() == disabled,
                    scene.id + ": 1.16 told the player's client it was " + (disabled ? "" : "no longer ")
                            + "disabled (SyncDisablerStateS2CPacket), so its client refused the presses too; last told "
                            + (last == null ? "nothing" : last.disabled()));
        }

        AbilityConditionCheck timeStopKey() {
            AbilityConditionCheck selected = freshKey(standUser.power(), "time_stop");
            scene.premise(selected != null && selected.ability == standUser.power().getAbility("time_stop"),
                    "the time stop key of Star Platinum's user resolves to the time stop: "
                            + (selected == null ? "nothing" : selected.ability.name()));
            return selected;
        }

        AbilityConditionCheck drainKey() {
            PlayerPower vampirism = PowerClass.PLAYER_POWER.get(vampire.player());
            AbilityConditionCheck selected = freshKey(vampirism, "vampirism_blood_drain");
            scene.premise(selected != null && selected.ability == vampirism.getAbility("vampirism_blood_drain"),
                    "the vampire's Blood Drain key resolves to Blood Drain: "
                            + (selected == null ? "nothing" : selected.ability.name()));
            return selected;
        }

        // the checks the user's own client runs for its HUD and its own press, its copy of the user standing in
        String[] ownClientAnswers() {
            asOwnClient(standUser.user(), true);
            asOwnClient(vampire.player(), true);
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(new ActionTarget(prey));
            try {
                return new String[] {answer(timeStopKey().conditionCheck), answer(drainKey().conditionCheck)};
            }
            finally {
                asOwnClient(standUser.user(), false);
                asOwnClient(vampire.player(), false);
                LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
            }
        }

        void requireTimeStopGoesOff() {
            AbilityConditionCheck selected = timeStopKey();
            int before = onDisableMessages(standUser.received());
            BufferingState buffer = BufferingState.clickCanBuffer();
            boolean taken = AbilityInput.withConditionCheck(selected, standUser.user(), InputMethod.HOLD);
            if (taken) {
                AbilityInput.keyPress(TIME_STOP_KEY, selected.ability, standUser.user(), null, InputMethod.HOLD, 0,
                        buffer, selected.ability.getAbilityId());
            }
            EntityActionInstance pressed =
                    LivingComponentAction.getCurEntityAction(standUser.user(), PowerClass.STAND);
            helper.assertTrue(taken && buffer.isActionSuccess && pressed != null && pressed.ability == selected.ability
                            && pressed.getPhase() == ActionPhase.BUTTON_CHARGE
                            && onDisableMessages(standUser.received()) == before,
                    scene.id + ": once the " + lockdownName(returnToZero) + " is over the time stop goes off; taken "
                            + taken + " (" + answer(selected.conditionCheck) + "), went off " + buffer.isActionSuccess
                            + ", the Stand-power slot " + pressed);
            dropTimeStop(scene, standUser);
        }

        void requireBloodDrainGoesOff() {
            AbilityConditionCheck selected = drainKey();
            int before = onDisableMessages(vampire.received());
            HeldInputEntry held = null;
            boolean taken = AbilityInput.withConditionCheck(selected, vampire.player(), InputMethod.HOLD);
            if (taken) {
                held = AbilityInput.keyPress(BLOOD_DRAIN_KEY, selected.ability, vampire.player(), null,
                        InputMethod.HOLD, 0, BufferingState.clickOnly(), selected.ability.getAbilityId());
            }
            EntityActionInstance own = LivingComponentAction.getCurEntityAction(vampire.player());
            helper.assertTrue(taken && held != null && own != null && held.action == own
                            && own.ability == selected.ability && onDisableMessages(vampire.received()) == before,
                    scene.id + ": once the " + lockdownName(returnToZero) + " is over Blood Drain goes off; taken "
                            + taken + " (" + answer(selected.conditionCheck) + "), held " + held
                            + ", the vampire's slot " + own);
            AbilityInput.keyRelease(BLOOD_DRAIN_KEY, vampire.player());
            LivingComponentAction.getComponent(vampire.player()).setAction(null, vampire.player(), SyncType.NO_SYNC);
        }
    }

    /**
     * A Star Platinum user (Stand out) and a vampire aiming Blood Drain at a cow, both under the lockdown: the HUD's check
     * of the time stop and of Blood Drain answers on_disable on the server and on each player's own client once the
     * server told it, each press is refused there with on_disable, nothing starts and no cooldown is taken. The Star
     * Platinum user still takes and gives back manual control of its Stand, with no message. Once the lockdown is over
     * the server tells them so, their clients show both usable and the same presses go off.
     */
    private static final class PressRefusalSequence extends PairSequence {
        private boolean refused;

        PressRefusalSequence(GameTestHelper helper, String id, boolean returnToZero) {
            super(helper, id, 80, returnToZero);
        }

        @Override
        boolean step() {
            if (standUser == null) {
                setUpPair(scene.origin, false);
                lockDownPair(SHORT_LOCKDOWN_TICKS);
                return false;
            }
            if (!refused) {
                refused = true;
                scene.premise(disabled(standUser.user()) && disabled(vampire.player()),
                        "the " + lockdownName(returnToZero) + " holds both players");
                requireToldDisabled(standUser.user(), standUser.received(), true);
                requireToldDisabled(vampire.player(), vampire.received(), true);
                requireHudRefusesOnBothSides();
                requireManualControlToggles(true);
                requireManualControlToggles(false);
                requireTimeStopRefused();
                requireBloodDrainRefused();
                return false;
            }
            if (disabled(standUser.user()) || disabled(vampire.player())) {
                return false;
            }
            requireToldDisabled(standUser.user(), standUser.received(), false);
            requireToldDisabled(vampire.player(), vampire.received(), false);
            requireOwnClientsShowBothUsable();
            requireTimeStopGoesOff();
            requireBloodDrainGoesOff();
            return true;
        }

        /*
         * 1.16's HUD asked checkConditions for every slot (ActionsOverlayGui through PowerBaseImpl.checkRequirements),
         * whose head the Disabler refused: the server's check, and the user's own client's once the server told it, which
         * reads what it was told, not the server's lockdown.
         */
        private void requireHudRefusesOnBothSides() {
            String timeStop = answer(timeStopKey().conditionCheck);
            String drain = answer(drainKey().conditionCheck);
            helper.assertTrue(ON_DISABLE.equals(timeStop) && ON_DISABLE.equals(drain),
                    scene.id + ": 1.16's checkConditions refused a user under the " + lockdownName(returnToZero)
                            + " every Stand and non-Stand action with " + ON_DISABLE + "; on the server the time stop"
                            + " answers " + timeStop + ", Blood Drain " + drain);
            GerDisablerClientState.setDisabled(standUser.user().getId(), false);
            GerDisablerClientState.setDisabled(vampire.player().getId(), false);
            String[] untold = ownClientAnswers();
            applyTold(standUser.user(), standUser.received());
            applyTold(vampire.player(), vampire.received());
            String[] told = ownClientAnswers();
            helper.assertTrue(USABLE.equals(untold[0]) && USABLE.equals(untold[1]),
                    scene.id + ": 1.16's client went by what the server told it (DisablerClientState); before it was"
                            + " told, the own client's time stop answers " + untold[0] + ", Blood Drain " + untold[1]);
            helper.assertTrue(ON_DISABLE.equals(told[0]) && ON_DISABLE.equals(told[1]),
                    scene.id + ": 1.16's HUD on the client of a user under the " + lockdownName(returnToZero)
                            + " drew every slot unavailable with " + ON_DISABLE + "; once told, the own client's time stop"
                            + " answers " + told[0] + ", Blood Drain " + told[1]);
        }

        private void requireOwnClientsShowBothUsable() {
            applyTold(standUser.user(), standUser.received());
            applyTold(vampire.player(), vampire.received());
            String[] told = ownClientAnswers();
            helper.assertTrue(USABLE.equals(told[0]) && USABLE.equals(told[1]),
                    scene.id + ": once told the " + lockdownName(returnToZero) + " is over the own client shows the"
                            + " time stop " + told[0] + " and Blood Drain " + told[1]);
        }

        // 1.16's manual-control key sent ClToggleStandManualControlPacket, no action, which the Disabler never refused
        private void requireManualControlToggles(boolean takeControl) {
            Ability manual = standUser.power().getAbility("manual_control");
            AbilityConditionCheck selected = freshKey(standUser.power(), "manual_control");
            scene.premise(manual instanceof StandEntityManualControlToggle && selected != null
                            && selected.ability == manual
                            && standUser.stand().isManuallyControlled() != takeControl,
                    "the manual-control key of Star Platinum's user resolves: "
                            + (selected == null ? "nothing" : selected.ability.name() + " " + selected.conditionCheck)
                            + ", controlled " + standUser.stand().isManuallyControlled());
            int before = onDisableMessages(standUser.received());
            // as the server takes a press: the slot's check, then the press
            boolean taken = AbilityInput.withConditionCheck(selected, standUser.user(), InputMethod.CLICK);
            if (taken) {
                AbilityInput.keyPress(MANUAL_CONTROL_KEY, manual, standUser.user(), null, InputMethod.CLICK, 0,
                        BufferingState.clickOnly(), manual.getAbilityId());
            }
            boolean controlled = standUser.stand().isManuallyControlled();
            boolean controllerOnStand =
                    EntityComponentController.getControlTarget(standUser.user()) == standUser.stand();
            int shown = onDisableMessages(standUser.received()) - before;
            helper.assertTrue(taken && controlled == takeControl && controllerOnStand == takeControl && shown == 0,
                    scene.id + ": 1.16 let a user under the " + lockdownName(returnToZero)
                            + (takeControl ? " take" : " give back") + " manual control of its Stand with no message;"
                            + " the slot answers " + answer(selected.conditionCheck) + ", controlled " + controlled
                            + ", the user's controller on the Stand " + controllerOnStand + ", " + ON_DISABLE
                            + " shown " + shown + " times");
        }

        private void requireTimeStopRefused() {
            AbilityConditionCheck selected = timeStopKey();
            int before = onDisableMessages(standUser.received());
            BufferingState buffer = BufferingState.clickCanBuffer();
            HeldInputEntry held = null;
            // as the server takes a press: the slot's check, then the press
            boolean taken = AbilityInput.withConditionCheck(selected, standUser.user(), InputMethod.HOLD);
            if (taken) {
                held = AbilityInput.keyPress(TIME_STOP_KEY, selected.ability, standUser.user(), null,
                        InputMethod.HOLD, 0, buffer, selected.ability.getAbilityId());
            }
            EntityActionInstance own = LivingComponentAction.getCurEntityAction(standUser.user());
            EntityActionInstance standPower =
                    LivingComponentAction.getCurEntityAction(standUser.user(), PowerClass.STAND);
            EntityActionInstance stand = standUser.stand().getCurStandAction();
            int cooldown = standUser.power().getAbilityCooldown("time_stop");
            helper.assertTrue(!taken && !buffer.isActionSuccess && held == null && own == null && standPower == null
                            && stand == null && cooldown == 0,
                    scene.id + ": 1.16 refused the time stop of a user under the " + lockdownName(returnToZero)
                            + " at its press; taken " + taken + ", went off " + buffer.isActionSuccess + ", held "
                            + held + ", the user's slot " + own + ", its Stand-power slot " + standPower
                            + ", the Stand's " + stand + ", cooldown " + cooldown);
            helper.assertTrue(onDisableMessages(standUser.received()) == before + 1,
                    scene.id + ": 1.16 showed " + ON_DISABLE + " for the refused time stop; shown "
                            + (onDisableMessages(standUser.received()) - before) + " times");
        }

        private void requireBloodDrainRefused() {
            AbilityConditionCheck selected = drainKey();
            int before = onDisableMessages(vampire.received());
            BufferingState buffer = BufferingState.clickOnly();
            HeldInputEntry held = null;
            boolean taken = AbilityInput.withConditionCheck(selected, vampire.player(), InputMethod.HOLD);
            if (taken) {
                held = AbilityInput.keyPress(BLOOD_DRAIN_KEY, selected.ability, vampire.player(), null,
                        InputMethod.HOLD, 0, buffer, selected.ability.getAbilityId());
            }
            EntityActionInstance own = LivingComponentAction.getCurEntityAction(vampire.player());
            helper.assertTrue(!taken && !buffer.isActionSuccess && held == null && own == null,
                    scene.id + ": 1.16 refused the Blood Drain (a NonStandAction) of a vampire under the "
                            + lockdownName(returnToZero) + " at its press; taken " + taken + ", went off "
                            + buffer.isActionSuccess + ", held " + held + ", the vampire's slot " + own);
            helper.assertTrue(onDisableMessages(vampire.received()) == before + 1,
                    scene.id + ": 1.16 showed " + ON_DISABLE + " for the refused Blood Drain; shown "
                            + (onDisableMessages(vampire.received()) - before) + " times");
        }
    }

    /**
     * 1.16 kept a lockdown's end in the entity's persistent data (DisablerHandler :37-50, :78-84), of which Forge's
     * restoreFrom gave the new player only the PlayerPersisted part, while PlayerList.respawn gave it the ger_disabler
     * tag: on the new player's first tick the lockdown was over and DisablerHandler.enable told its client false
     * (:52-59). That client's DisablerClientState, cleared only on logout, holds the entity id the new player keeps. Row
     * 0: the pair, locked down and told so, die and respawn; row 1: a new pair comes back from the End (a clone that keeps
     * everything). Then each was told the lockdown is over, its own client and the server answer the time stop and Blood
     * Drain usable, the Disabler slows no one, and both presses go off.
     */
    private static final class RespawnSequence extends PairSequence {
        private static final int LONG_LOCKDOWN_TICKS = 400;
        // past the 4 ticks of the Disabler's slowness an End return carries over
        private static final int EFFECT_WATCH_TICKS = 6;
        private int row;
        private int stage;
        private int respawnedAt;

        RespawnSequence(GameTestHelper helper, String id, boolean returnToZero) {
            super(helper, id, 120, returnToZero);
        }

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    setUpPair(scene.origin.add(0.0D, 0.0D, 3.0D * row), true);
                    lockDownPair(LONG_LOCKDOWN_TICKS);
                    stage = 1;
                }
                case 1 -> {
                    requireToldAndShownDisabled();
                    if (death()) {
                        standUser.user().kill();
                        vampire.player().kill();
                        scene.premise(standUser.user().isDeadOrDying() && vampire.player().isDeadOrDying(),
                                "both players died");
                    }
                    stage = 2;
                }
                case 2 -> {
                    scene.premise(disabled(standUser.user()) && disabled(vampire.player()),
                            "the " + lockdownName(returnToZero) + " still holds both players as they " + how());
                    standUser = scene.respawn(standUser, death());
                    vampire = scene.respawn(vampire, death());
                    LivingComponentAction.getComponent(vampire.player()).entityAim.setTarget(new ActionTarget(prey));
                    respawnedAt = tick;
                    stage = 3;
                }
                case 3 -> {
                    requireFreed();
                    stage = 4;
                }
                default -> {
                    if (tick - respawnedAt < EFFECT_WATCH_TICKS) {
                        return false;
                    }
                    requireNotSlowed(vampire.player());
                    // the scene runs no living tick for an actor, so the effects an End return carries over stay on it
                    if (death()) {
                        requireNotSlowed(standUser.user());
                    }
                    requireTimeStopGoesOff();
                    requireBloodDrainGoesOff();
                    row++;
                    stage = 0;
                    return row > 1;
                }
            }
            return false;
        }

        private boolean death() {
            return row == 0;
        }

        private String how() {
            return death() ? "die and respawn" : "come back from the End";
        }

        private void requireToldAndShownDisabled() {
            scene.premise(disabled(standUser.user()) && disabled(vampire.player()),
                    "the " + lockdownName(returnToZero) + " holds both players");
            requireToldDisabled(standUser.user(), standUser.received(), true);
            requireToldDisabled(vampire.player(), vampire.received(), true);
            applyTold(standUser.user(), standUser.received());
            applyTold(vampire.player(), vampire.received());
            String[] told = ownClientAnswers();
            scene.premise(ON_DISABLE.equals(told[0]) && ON_DISABLE.equals(told[1]),
                    "told, the own clients refuse the time stop (" + told[0] + ") and Blood Drain (" + told[1] + ")");
        }

        private void requireFreed() {
            GerDisablerPayloads.StatePayload standTold = lastTold(standUser.user(), standUser.received());
            GerDisablerPayloads.StatePayload drainTold = lastTold(vampire.player(), vampire.received());
            helper.assertTrue(standTold != null && !standTold.disabled() && drainTold != null && !drainTold.disabled(),
                    scene.id + ": 1.16 ended the " + lockdownName(returnToZero) + " of players who " + how()
                            + " and told their clients so (DisablerHandler.enable); last told the Star Platinum user "
                            + told(standTold) + ", the vampire " + told(drainTold));
            applyTold(standUser.user(), standUser.received());
            applyTold(vampire.player(), vampire.received());
            String[] own = ownClientAnswers();
            String timeStop = answer(timeStopKey().conditionCheck);
            String drain = answer(drainKey().conditionCheck);
            scene.log("players " + how() + ": told " + told(standTold) + "/" + told(drainTold) + ", own clients "
                    + own[0] + "/" + own[1] + ", server " + timeStop + "/" + drain + ", Stand out "
                    + standUser.power().isSummoned());
            helper.assertTrue(USABLE.equals(own[0]) && USABLE.equals(own[1])
                            && USABLE.equals(timeStop) && USABLE.equals(drain),
                    scene.id + ": once players under the " + lockdownName(returnToZero) + " " + how()
                            + ", 1.16's HUD and press checks pass again; the own clients answer the time stop "
                            + own[0] + " and Blood Drain " + own[1] + ", the server " + timeStop + " and " + drain);
            helper.assertTrue(!disabled(standUser.user()) && !disabled(vampire.player()),
                    scene.id + ": the server still holds players who " + how() + " under the "
                            + lockdownName(returnToZero));
        }

        private static String told(GerDisablerPayloads.StatePayload payload) {
            return payload == null ? "nothing" : payload.disabled() ? "disabled" : "free";
        }

        private void requireNotSlowed(LivingEntity player) {
            boolean slowed = player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
            boolean weakened = player.hasEffect(MobEffects.WEAKNESS);
            helper.assertTrue(!slowed && !weakened,
                    scene.id + ": " + EFFECT_WATCH_TICKS + " ticks after players under the "
                            + lockdownName(returnToZero) + " " + how() + " one is still slowed " + slowed
                            + ", weakened " + weakened);
        }
    }
}
