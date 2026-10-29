package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.CenterOfGravityAbility;
import rotp.addons.cmoon.ability.GravityCounterAbility;
import rotp.addons.cmoon.ability.GravityShiftAbility;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 framework: a Stand action's stamina cost is paid with the result ignored (StandEntityTask), so only an
 * action's own condition refuses it. C-Moon's three gravity actions each had one: GravityCounter below 75,
 * CenterOfGravity below 100, GravitationalShift below 200, each a plain NEGATIVE that shows no message, and the two
 * holdToFire actions asked it again on every held tick.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonStaminaGameTests {
    private static final short KEY = 7731;
    private static final TicketType<UUID> TICKET = TicketType.create(
            "cmoon_stamina", Comparator.<UUID>naturalOrder());

    private CMoonStaminaGameTests() {}

    @GameTest(template = "empty", batch = "cmStaminaCounter", timeoutTicks = 120)
    public static void counterRunsItsTwentyTicksOnAnEmptyBar(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        EntityActionInstance[] counter = new EntityActionInstance[1];
        User[] running = new User[1];
        int[] ticks = {0};
        int[] lastPhaseTick = {-1};
        helper.onEachTick(() -> fixture.guarded(() -> {
            ++ticks[0];
            if (counter[0] == null) {
                if (ticks[0] < 3) return;
                User refused = fixture.user("cm-counter-short", 0);
                fixture.refusedSilently(refused, CMoonAddon.GRAVITY_COUNTER, GravityCounterAbility.REQUIRED_STAMINA,
                        InputMethod.CLICK);

                User user = running[0] = fixture.user("cm-counter-empty", 4);
                user.power.setStamina(GravityCounterAbility.REQUIRED_STAMINA);
                counter[0] = fixture.press(user, CMoonAddon.GRAVITY_COUNTER, InputMethod.CLICK);
                AbilityInput.keyRelease(KEY, user.recorded.player);
                helper.assertTrue(counter[0] instanceof GravityCounterAbility.CounterAction
                                && user.stand.isCounterActive(),
                        "Premise: the counter did not start at 75 stamina");
                user.power.setStamina(0.0F);
                return;
            }
            User user = running[0];
            if (!counter[0].isOver()) {
                lastPhaseTick[0] = Math.max(lastPhaseTick[0], (int) counter[0].getPhaseTick());
                fixture.check(user.power.getStamina() == 0.0F,
                        "the empty bar must stay at 0, got " + user.power.getStamina());
                helper.assertTrue(ticks[0] < 110, "Premise: the counter never ended");
                return;
            }
            fixture.check(lastPhaseTick[0] >= GravityCounterAbility.PERFORM_TICKS - 2,
                    "1.16 StandEntityTask ignored a failed staminaCostTick(1), so the counter stays up for its 20"
                            + " ticks on an empty bar; it ended at perform tick " + lastPhaseTick[0]);
            fixture.check(!user.stand.isCounterActive(), "the ended counter is still armed");
            fixture.finish("counter on an empty bar");
        }));
    }

    @GameTest(template = "empty", batch = "cmStaminaCenter", timeoutTicks = 120)
    public static void centerChargeEndsSilentlyWhenTheBarFallsBelow100(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        EntityActionInstance[] center = new EntityActionInstance[1];
        User[] charging = new User[1];
        int[] ticks = {0};
        helper.onEachTick(() -> fixture.guarded(() -> {
            ++ticks[0];
            if (center[0] == null) {
                if (ticks[0] < 3) return;
                User refused = fixture.user("cm-center-short", 0);
                fixture.refusedSilently(refused, CMoonAddon.CENTER_OF_GRAVITY,
                        CenterOfGravityAbility.REQUIRED_STAMINA, InputMethod.HOLD);

                User user = charging[0] = fixture.user("cm-center-charge", 4);
                user.power.setStamina(CenterOfGravityAbility.REQUIRED_STAMINA);
                center[0] = fixture.press(user, CMoonAddon.CENTER_OF_GRAVITY, InputMethod.HOLD);
                helper.assertTrue(center[0] instanceof CenterOfGravityAbility.CenterAction
                                && center[0].getPhase() == ActionPhase.BUTTON_CHARGE,
                        "Premise: the Center charge did not start at 100 stamina");
                user.power.setStamina(CenterOfGravityAbility.REQUIRED_STAMINA - 1.0F);
                return;
            }
            User user = charging[0];
            if (center[0].getPhase() == ActionPhase.PERFORM) {
                fixture.check(false, "1.16 PowerBaseImpl.tickHeldAction asked CenterOfGravity.checkConditions on"
                        + " every held tick, so the charge ends once the bar is below 100; it fired the field");
                fixture.finish("Center charge below 100");
                return;
            }
            if (!center[0].isOver()) {
                helper.assertTrue(ticks[0] < 110, "Premise: the Center charge never ended");
                return;
            }
            fixture.check(user.power.getAbilityCooldown(CMoonAddon.CENTER_OF_GRAVITY) == 0,
                    "the stopped charge set the cooldown: "
                            + user.power.getAbilityCooldown(CMoonAddon.CENTER_OF_GRAVITY));
            fixture.check(user.overlayMessages().isEmpty(),
                    "1.16 ended that hold with a plain NEGATIVE, but the user was sent " + user.overlayMessages());
            fixture.finish("Center charge below 100");
        }));
    }

    @GameTest(template = "empty", batch = "cmStaminaShift", timeoutTicks = 120)
    public static void shiftChargeEndsSilentlyBelow200AndAFullChargePaysTheBar(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        EntityActionInstance[] shift = new EntityActionInstance[2];
        User[] users = new User[2];
        int[] ticks = {0};
        boolean[] droppedDone = {false};
        boolean[] fullDone = {false};
        helper.onEachTick(() -> fixture.guarded(() -> {
            ++ticks[0];
            if (shift[0] == null) {
                if (ticks[0] < 3) return;
                User refused = fixture.user("cm-shift-short", 0);
                fixture.refusedSilently(refused, CMoonAddon.GRAVITY_SHIFT, GravityShiftAbility.STAMINA_COST,
                        InputMethod.HOLD);
                User resolve = fixture.user("cm-shift-resolve", 4);
                helper.assertTrue(resolve.recorded.player.addEffect(
                                new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 1)),
                        "Premise: the Resolve effect could not be installed");
                resolve.power.setStamina(GravityShiftAbility.STAMINA_COST - 1.0F);
                ConditionCheck underResolve = resolve.power.getAbility(CMoonAddon.GRAVITY_SHIFT)
                        .checkSpecificConditions(resolve.power);
                fixture.check(!underResolve.isPositive(),
                        "1.16 power.getStamina() < 200 ignored Resolve, but the shift passed at 199 under Resolve");

                User dropped = users[0] = fixture.user("cm-shift-drop", 8);
                dropped.power.setStamina(GravityShiftAbility.STAMINA_COST);
                shift[0] = fixture.press(dropped, CMoonAddon.GRAVITY_SHIFT, InputMethod.HOLD);
                helper.assertTrue(shift[0] instanceof GravityShiftAbility.ShiftAction
                                && shift[0].getPhase() == ActionPhase.BUTTON_CHARGE,
                        "Premise: the shift charge did not start at 200 stamina");
                dropped.power.setStamina(GravityShiftAbility.STAMINA_COST - 1.0F);

                User full = users[1] = fixture.user("cm-shift-full", 12);
                // 300, not 200: a bar paid down to 0 drops the shift on the Stand's next tick (1.16 handleBuffs).
                full.power.setStamina(300.0F);
                shift[1] = fixture.press(full, CMoonAddon.GRAVITY_SHIFT, InputMethod.HOLD);
                helper.assertTrue(shift[1] instanceof GravityShiftAbility.ShiftAction,
                        "Premise: the full shift charge did not start");
                return;
            }
            helper.assertTrue(ticks[0] < 110, "Premise: a shift charge never ended");
            User dropped = users[0];
            if (!droppedDone[0]) {
                if (shift[0].getPhase() == ActionPhase.PERFORM || dropped.stand.isShiftActive()) {
                    fixture.check(false, "1.16 PowerBaseImpl.tickHeldAction asked GravitationalShift.checkConditions"
                            + " on every held tick, so the charge ends once the bar is below 200; it fired");
                    droppedDone[0] = true;
                }
                else if (shift[0].isOver()) {
                    fixture.check(dropped.power.getAbilityCooldown(CMoonAddon.GRAVITY_SHIFT) == 0,
                            "the stopped charge set the cooldown");
                    fixture.check(dropped.overlayMessages().isEmpty(),
                            "1.16 ended that hold with a plain NEGATIVE, but the user was sent "
                                    + dropped.overlayMessages());
                    droppedDone[0] = true;
                }
            }
            User full = users[1];
            if (!fullDone[0] && full.stand.isShiftActive()) {
                fixture.check(full.power.getStamina() <= 100.0F,
                        "a full charge toggles the shift and pays its 200 from the 300 bar, left "
                                + full.power.getStamina());
                fullDone[0] = true;
            }
            if (droppedDone[0] && fullDone[0]) {
                fixture.finish("Gravity Shift charge");
            }
        }));
    }

    /**
     * 1.16 PowerBaseImpl.onClickAction ran StandEntityAction.onClick (the auto-summon, which sets the manifestation)
     * before checkRequirements, so power.isActive() was already true when checkStandConditions asked for 100 and 200;
     * the refused press then went through finalizeStandSummonFromAction(false), which unsummoned the Stand again.
     */
    @GameTest(template = "empty", batch = "cmStaminaPutAway", timeoutTicks = 120)
    public static void chargesBelowTheirFloorsStayRefusedWithTheStandPutAway(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        int[] ticks = {0};
        helper.onEachTick(() -> fixture.guarded(() -> {
            if (++ticks[0] < 3) return;
            fixture.refusedPutAway(fixture.user("cm-center-away", 0), CMoonAddon.CENTER_OF_GRAVITY,
                    CenterOfGravityAbility.REQUIRED_STAMINA);
            fixture.refusedPutAway(fixture.user("cm-shift-away", 4), CMoonAddon.GRAVITY_SHIFT,
                    GravityShiftAbility.STAMINA_COST);
            fixture.finish("charges below their floors with the Stand put away");
        }));
    }

    private record User(RecordingServerPlayer recorded, StandPower power, CMoonStandEntity stand) {
        List<String> overlayMessages() {
            return recorded.packets(ClientboundSystemChatPacket.class).stream()
                    .filter(ClientboundSystemChatPacket::overlay)
                    .map(packet -> packet.content().getContents() instanceof TranslatableContents contents
                            ? contents.getKey()
                            : packet.content().getString())
                    .toList();
        }
    }

    private static final class Fixture implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final Vec3 origin;
        final UUID ticketId = UUID.randomUUID();
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<User> users = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 3, 1)));
            helper.testInfo.addListener(this);
            ChunkPos centre = new ChunkPos(BlockPos.containing(origin));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ChunkPos chunk = new ChunkPos(centre.x + x, centre.z + z);
                    level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketId);
                    chunks.add(chunk);
                }
            }
        }

        // Made in the tick it is used, so its first ability check is not an earlier tick's cache.
        User user(String name, double offset) {
            RecordingServerPlayer recorded = new RecordingServerPlayer(helper, name, origin.add(offset, 0, 0));
            StandPower power = PowerClass.STAND.attachGet(recorded.player);
            users.add(new User(recorded, power, null));
            recorded.player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            recorded.player.setHealth(200);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(CMoonAddon.C_MOON.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Premise: registered C-Moon granted");
            power.setResolveLevel(power.getMaxResolveLevel());
            helper.assertTrue(CMoonAddon.C_MOON.get().summon(recorded.player, power)
                            && power.getSummonedStandEntity() instanceof CMoonStandEntity,
                    "Premise: registered C-Moon summoned");
            CMoonStandEntity stand = (CMoonStandEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setNoGravity(true);
            helper.assertTrue(!power.isStaminaInfinite() && power.getResolveLevel() >= 2,
                    "Premise: finite stamina and every C-Moon ability unlocked");
            User user = new User(recorded, power, stand);
            users.set(users.size() - 1, user);
            recorded.clear();
            return user;
        }

        void refusedSilently(User user, String abilityName, float floor, InputMethod method) {
            Ability ability = user.power.getAbility(abilityName);
            user.power.setStamina(floor - 1.0F);
            ConditionCheck below = ability.checkSpecificConditions(user.power);
            check(!below.isPositive() && below.getWarning() == null,
                    "1.16 refused " + abilityName + " below " + floor + " stamina with a plain NEGATIVE, got "
                            + (below.isPositive() ? "positive" : below.getWarning() == null ? "a silent refusal"
                                    : below.getWarning().getString()));
            var checked = user.power.updateAvailableMoves().getContextVariationContainer(ability);
            boolean eligible = checked != null && AbilityInput.withConditionCheck(checked, user.recorded.player, method);
            // The server's own order: the input gate, then the press.
            BufferingState buffering = BufferingState.clickOnly();
            if (eligible) {
                AbilityInput.keyPress(KEY, checked.ability, user.recorded.player, null, method, 0, buffering,
                        ability.getAbilityId());
                AbilityInput.keyRelease(KEY, user.recorded.player);
            }
            check(!eligible && !buffering.isActionSuccess && user.stand.getCurStandAction() == null,
                    abilityName + " started below " + floor + " stamina");
            check(user.overlayMessages().isEmpty(),
                    "1.16 showed no message for " + abilityName + " below " + floor + " stamina, but the user was"
                            + " sent " + user.overlayMessages());
        }

        void refusedPutAway(User user, String abilityName, float floor) {
            CMoonAddon.C_MOON.get().forceUnsummon(user.recorded.player, user.power);
            helper.assertTrue(user.power.getSummonedStandEntity() == null, "Premise: C-Moon was not put away");
            user.recorded.clear();
            Ability ability = user.power.getAbility(abilityName);
            user.power.setStamina(floor - 1.0F);
            var checked = user.power.updateAvailableMoves().getContextVariationContainer(ability);
            boolean eligible = checked != null
                    && AbilityInput.withConditionCheck(checked, user.recorded.player, InputMethod.HOLD);
            BufferingState buffering = BufferingState.clickOnly();
            if (eligible) {
                AbilityInput.keyPress(KEY, checked.ability, user.recorded.player, null, InputMethod.HOLD, 0, buffering,
                        ability.getAbilityId());
            }
            check(!eligible && !buffering.isActionSuccess,
                    "1.16 summoned the Stand before checking " + abilityName + ", so its " + floor
                            + " floor refused the press with the Stand put away; eligible=" + eligible
                            + " started=" + buffering.isActionSuccess);
            check(user.power.getSummonedStandEntity() == null,
                    "the refused " + abilityName + " press left the Stand out");
            check(user.overlayMessages().isEmpty(),
                    "1.16 showed no message for " + abilityName + " below " + floor + " stamina, but the user was"
                            + " sent " + user.overlayMessages());
        }

        EntityActionInstance press(User user, String abilityName, InputMethod method) {
            Ability ability = user.power.getAbility(abilityName);
            var checked = user.power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(checked != null && AbilityInput.withConditionCheck(checked, user.recorded.player, method),
                    "Premise: the " + abilityName + " input was refused");
            BufferingState buffering = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, checked.ability, user.recorded.player, null, method, 0, buffering,
                    ability.getAbilityId());
            helper.assertTrue(buffering.isActionSuccess, "Premise: the " + abilityName + " press did not start");
            return user.stand.getCurStandAction();
        }

        void guarded(Runnable step) {
            if (closed) return;
            try {
                step.run();
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void check(boolean passed, String message) {
            if (!passed) failures.add(message);
        }

        void finish(String scene) {
            close();
            helper.assertTrue(failures.isEmpty(), "CM-STAMINA " + scene + " failed: " + failures);
            helper.succeed();
        }

        void close() {
            if (closed) return;
            closed = true;
            for (User user : users) {
                AbilityInput.keyRelease(KEY, user.recorded.player);
                if (user.power.isSummoned()) {
                    CMoonAddon.C_MOON.get().forceUnsummon(user.recorded.player, user.power);
                }
                user.recorded.close();
            }
            for (ChunkPos chunk : chunks) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketId);
            }
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { close(); }
    }
}
