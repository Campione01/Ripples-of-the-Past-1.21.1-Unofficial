package rotp.addons.weatherreport.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.ability.WeatherReportColdWindAbility;
import rotp.addons.weatherreport.ability.WeatherReportFuguRainAbility;
import rotp.addons.weatherreport.ability.WeatherReportWindAbility;
import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 PowerBaseImpl.tickHeldAction ran checkRequirements again on every tick of a held action and stopped the hold,
 * with the result's message, once it failed: Fugu Rain's checkStandConditions (open sky over the aim, rain) during its
 * 30-tick charge, and for the blowing wind the performer's stun (the performer of a Stand action being the Stand).
 * Each case holds its key the way ClAbilityInputPacket does and lets the server's own ticks run the hold.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportHeldRecheckGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 48;
    private static final short OTHER_KEY = 49;
    private static final int TIMEOUT_TICKS = 240;
    private static final int DEADLINE_TICK = 220;
    private static final int SUMMON_LOCK = 5;
    private static final float SHALLOW_PITCH = 9.2F;
    private static final String RAIN_KEY = "jojo.message.action_condition.fugurain-warningrain";
    private static final String STUN_KEY = "jojo.message.action_condition.stun";

    private WeatherReportHeldRecheckGameTests() {}

    @GameTest(template = "empty", batch = "wr_held_fugu_rain", timeoutTicks = TIMEOUT_TICKS)
    public static void fuguRainChargeEndsWhenTheRainStopsAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "fugu_rain_rain");
        Ability[] fugu = {null};
        int[] charging = {0};
        int[] afterRain = {-1};
        fixture.run(() -> {
            fixture.floorAndRain();
            fugu[0] = fixture.power.getAbility(WeatherReportAddon.FUGU_RAIN_NAME);
            fixture.helper.assertTrue(fugu[0] instanceof WeatherReportFuguRainAbility,
                    "Weather Report has no fugu_rain ability");
        }, () -> {
            if (fixture.action == null) {
                // The open sky over the aim waits for the light engine.
                if (fixture.admitted(fugu[0])) {
                    fixture.press(fugu[0]);
                }
                return false;
            }
            EntityActionInstance action = fixture.stand.getCurStandAction();
            if (afterRain[0] < 0) {
                fixture.helper.assertTrue(action == fixture.action && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                        "Setup: Fugu Rain is not charging in the rain: "
                                + (action == null ? "no action" : action.getPhase()));
                if (++charging[0] >= 5) {
                    fixture.channel.flushOutbound();
                    fixture.channel.outboundMessages().clear();
                    // /weather clear: only the flag turns off, the rain level fades for about 80 ticks.
                    fixture.level.setWeatherParameters(12000, 0, false, false);
                    fixture.helper.assertTrue(fixture.level.isRaining(),
                            "Setup: the rain level must still be above the rain threshold");
                    afterRain[0] = 0;
                }
                return false;
            }
            if (++afterRain[0] < 2) {
                return false;
            }
            fixture.check(action != fixture.action || fixture.action.isOver(),
                    "1.16 ended the charge once the rain stopped; it is still "
                            + (action == null ? "gone?" : action.getPhase()));
            fixture.check(fixture.stand.getActiveFugaSummoner() == null,
                    "A storm came from a charge that the rain's end stopped");
            fixture.check(fixture.power.getAbilityCooldown(fugu[0].name()) == 0,
                    "The stopped charge set the cooldown");
            fixture.check(fixture.sentMessage(RAIN_KEY), "1.16 sent the no-rain message when the charge stopped");
            return true;
        });
    }

    @GameTest(template = "empty", batch = "wr_held_fugu_rain_start", timeoutTicks = TIMEOUT_TICKS)
    public static void fuguRainIsAdmittedAsSoonAsTheRainStartsAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "fugu_rain_start");
        Ability[] fugu = {null};
        fixture.run(() -> {
            fixture.floorAndRain();
            fugu[0] = fixture.power.getAbility(WeatherReportAddon.FUGU_RAIN_NAME);
            fixture.helper.assertTrue(fugu[0] instanceof WeatherReportFuguRainAbility,
                    "Weather Report has no fugu_rain ability");
        }, () -> {
            // The weather flag is on, the rain level has not risen yet.
            fixture.level.setRainLevel(0.0F);
            fixture.helper.assertTrue(!fixture.level.isRaining(), "Setup: the rain level is above the threshold");
            AbilityConditionCheck check = fixture.power.updateAvailableMoves().getContextVariationContainer(fugu[0]);
            if (check != null && check.ability == fugu[0] && check.conditionCheck.isPositive()) {
                return true;
            }
            // Until the light engine shows the sky, the refusal is the sky's; the rain's would be the defect.
            fixture.check(check == null || check.conditionCheck.getWarning() == null
                            || !(check.conditionCheck.getWarning().getContents() instanceof TranslatableContents contents)
                            || !contents.getKey().equals(RAIN_KEY),
                    "1.16 admitted the press as soon as the weather flag was on; it was refused with the no-rain message");
            return !fixture.failures.isEmpty();
        });
    }

    @GameTest(template = "empty", batch = "wr_held_wind_stun", timeoutTicks = TIMEOUT_TICKS)
    public static void blowingWindEndsWhenTheStandIsStunnedAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "wind_stun");
        int[] blowing = {0};
        int[] afterStun = {-1};
        fixture.run(() -> {
            fixture.press(fixture.power.getAbility(WeatherReportAddon.WIND_NAME));
        }, () -> {
            EntityActionInstance action = fixture.stand.getCurStandAction();
            if (afterStun[0] < 0) {
                fixture.helper.assertTrue(action == fixture.action && action.getPhase() == ActionPhase.PERFORM,
                        "Setup: the wind is not blowing: " + (action == null ? "no action" : action.getPhase()));
                if (++blowing[0] >= 3) {
                    fixture.channel.flushOutbound();
                    fixture.channel.outboundMessages().clear();
                    fixture.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
                    fixture.helper.assertTrue(ModStatusEffects.isStunned(fixture.stand),
                            "Setup: the Stand is not stunned");
                    afterStun[0] = 0;
                }
                return false;
            }
            if (++afterStun[0] < 2) {
                return false;
            }
            fixture.check(fixture.action.isOver() || fixture.action.getPhase() != ActionPhase.PERFORM,
                    "1.16 ended the held wind once its Stand was stunned; it is still " + fixture.action.getPhase());
            fixture.check(fixture.sentMessage(STUN_KEY), "1.16 sent the stun message when the wind stopped");
            return true;
        });
    }

    /*
     * 1.16 weather_report_wind (holdType(120)), weather_report_cold_wind (holdType(80)) and weather_report_cloud_shield
     * (WeatherReportCloudShield, builder.holdType()) were holdType Stand actions: PowerBaseImpl let every other action
     * of the power through them and ended the hold once that action went off (onClickAction stopHeldAction(false),
     * setHeldAction). The cloud-shield gift, a StandAction, is the user's: it goes off and ends the hold.
     */
    @GameTest(template = "empty", batch = "wr_held_wind_gift", timeoutTicks = TIMEOUT_TICKS)
    public static void aCloudShieldGiftEndsTheHeldWindAsIn116(GameTestHelper helper) {
        giftEndsTheHold(helper, "wind_gift", WeatherReportAddon.WIND_NAME);
    }

    @GameTest(template = "empty", batch = "wr_held_cold_wind_gift", timeoutTicks = TIMEOUT_TICKS)
    public static void aCloudShieldGiftEndsTheHeldColdWindAsIn116(GameTestHelper helper) {
        giftEndsTheHold(helper, "cold_wind_gift", WeatherReportAddon.COLD_WIND_NAME);
    }

    @GameTest(template = "empty", batch = "wr_held_cloud_shield_gift", timeoutTicks = TIMEOUT_TICKS)
    public static void aCloudShieldGiftEndsTheHeldCloudShieldAsIn116(GameTestHelper helper) {
        giftEndsTheHold(helper, "cloud_shield_gift", WeatherReportAddon.CLOUD_SHIELD_NAME);
    }

    private static void giftEndsTheHold(GameTestHelper helper, String id, String holdName) {
        Fixture fixture = new Fixture(helper, id);
        int[] heldTicks = {0};
        fixture.run(() -> {
            Cow cow = fixture.cow(3.0D);
            // ClAimTargetPacket stores the client's target on the user and the Stand.
            LivingComponentAction.getComponent(fixture.user).entityAim.setTarget(new ActionTarget(cow));
            LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(new ActionTarget(cow));
            fixture.press(fixture.power.getAbility(holdName));
        }, () -> {
            EntityActionInstance hold = fixture.action;
            if (++heldTicks[0] < 3) {
                fixture.helper.assertTrue(fixture.stand.getCurStandAction() == hold
                                && hold.getPhase() == ActionPhase.PERFORM && fixture.heldByKey(),
                        "Setup: the " + holdName + " is not held in its perform: " + hold.getPhase());
                return false;
            }
            fixture.check(hold.isHoldTypeStandAction(), "1.16 weather_report_" + holdName + " was a holdType Stand"
                    + " action, ended by another action of the power that went off: it must be a hold-type Stand action");
            Ability gift = fixture.power.getAbility(WeatherReportAddon.GIVE_CLOUD_SHIELD_NAME);
            AbilityConditionCheck check = fixture.power.updateAvailableMoves().getContextVariationContainer(gift);
            boolean admitted = check != null && check.ability == gift
                    && AbilityInput.withConditionCheck(check, fixture.user, InputMethod.CLICK);
            fixture.check(admitted, "1.16 let the cloud-shield gift through the held " + holdName
                    + " (heldAllowsOtherAction), but it was refused: "
                    + (check == null ? "not available" : check.conditionCheck.getWarning() != null
                            ? check.conditionCheck.getWarning().getString() : "a silent refusal"));
            if (admitted) {
                BufferingState pressed = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(OTHER_KEY, check.ability, fixture.user, null, InputMethod.CLICK, 0.0F, pressed,
                        gift.getAbilityId());
                AbilityInput.keyRelease(OTHER_KEY, fixture.user);
                fixture.check(pressed.isActionSuccess && !pressed.shouldBuffer,
                        "The cloud-shield gift did not go off during the held " + holdName);
                fixture.check(hold.isOver() || hold.getPhase() == ActionPhase.RECOVERY,
                        "1.16 ended the held " + holdName + " once the cloud-shield gift went off (stopHeldAction(false)):"
                                + " it is still in " + hold.getPhase());
                fixture.check(!fixture.heldByKey(), "1.16 ended the held " + holdName
                        + ", so its key held nothing more, but it still holds " + hold);
            }
            return true;
        });
    }

    /*
     * 1.16 PowerBaseImpl.tickHeldAction counted holdType(120) / holdType(80) from the press: a Stand summoned by the press
     * held its lock's ticks inside that maximum, and the ticks held (the cooldown, cooldownFromHoldDuration) counted them.
     */
    @GameTest(template = "empty", batch = "wr_wind_lock", timeoutTicks = TIMEOUT_TICKS)
    public static void theHeldWindCountsTheSummonLockInItsMaximumAsIn116(GameTestHelper helper) {
        holdsThroughTheSummonLock(helper, "wind_lock", WeatherReportAddon.WIND_NAME,
                WeatherReportWindAbility.MAX_HOLD_TICKS, WeatherReportWindAbility.COOLDOWN_TICKS);
    }

    @GameTest(template = "empty", batch = "wr_cold_wind_lock", timeoutTicks = TIMEOUT_TICKS)
    public static void theHeldColdWindCountsTheSummonLockInItsMaximumAsIn116(GameTestHelper helper) {
        holdsThroughTheSummonLock(helper, "cold_wind_lock", WeatherReportAddon.COLD_WIND_NAME,
                WeatherReportColdWindAbility.MAX_HOLD_TICKS, WeatherReportColdWindAbility.COOLDOWN_TICKS);
    }

    private static void holdsThroughTheSummonLock(GameTestHelper helper, String id, String holdName, int max,
            int cooldown) {
        Fixture fixture = new Fixture(helper, id);
        int[] lastPerformTick = {-1};
        fixture.run(() -> {
            fixture.stand.summonLockTicks = SUMMON_LOCK;
            fixture.press(fixture.power.getAbility(holdName));
        }, () -> {
            EntityActionInstance hold = fixture.action;
            if (!hold.isOver() && hold.getPhase() == ActionPhase.PERFORM) {
                lastPerformTick[0] = (int) hold.getPhaseTick();
                fixture.check(fixture.heldByKey(), "Setup: the key of the " + holdName + " is not held");
                return false;
            }
            fixture.check(hold.getTicksHeldInSummonLock() == SUMMON_LOCK, "Setup: the held " + holdName
                    + " counted " + hold.getTicksHeldInSummonLock() + " lock ticks, not " + SUMMON_LOCK);
            fixture.check(lastPerformTick[0] == max - SUMMON_LOCK, "1.16 held the " + holdName + " " + max
                    + " ticks from the press, the lock's " + SUMMON_LOCK + " included: its perform should have run "
                    + (max - SUMMON_LOCK) + " ticks, it ran " + lastPerformTick[0]);
            int taken = fixture.power.getAbilityCooldown(holdName);
            fixture.check(taken >= cooldown - 1 && taken <= cooldown, "1.16 scaled the cooldown by the ticks held from"
                    + " the press, the lock's included (" + cooldown + " for " + max + "); the port took " + taken);
            return true;
        });
    }

    /*
     * 1.16 weather_report_wind, weather_report_cold_wind and weather_report_cloud_shield were StandEntityActions that did
     * not override isChainable: StandEntityAction.canClickDuringTask :495-497 refused their own ability pressed again,
     * silently and unbuffered, while the Stand's task was running them. A refused press leaves the running hold as it is.
     */
    @GameTest(template = "empty", batch = "wr_own_press_wind", timeoutTicks = TIMEOUT_TICKS)
    public static void theHeldWindRefusesItsOwnPressAsIn116(GameTestHelper helper) {
        refusesItsOwnPress(helper, "wind_own_press", WeatherReportAddon.WIND_NAME);
    }

    @GameTest(template = "empty", batch = "wr_own_press_cold_wind", timeoutTicks = TIMEOUT_TICKS)
    public static void theHeldColdWindRefusesItsOwnPressAsIn116(GameTestHelper helper) {
        refusesItsOwnPress(helper, "cold_wind_own_press", WeatherReportAddon.COLD_WIND_NAME);
    }

    @GameTest(template = "empty", batch = "wr_own_press_cloud_shield", timeoutTicks = TIMEOUT_TICKS)
    public static void theHeldCloudShieldRefusesItsOwnPressAsIn116(GameTestHelper helper) {
        refusesItsOwnPress(helper, "cloud_shield_own_press", WeatherReportAddon.CLOUD_SHIELD_NAME);
    }

    private static void refusesItsOwnPress(GameTestHelper helper, String id, String holdName) {
        Fixture fixture = new Fixture(helper, id);
        int[] heldTicks = {0};
        fixture.run(() -> {
            Cow cow = fixture.cow(3.0D);
            fixture.aimAt(cow);
            fixture.press(fixture.power.getAbility(holdName));
        }, () -> {
            EntityActionInstance hold = fixture.action;
            if (++heldTicks[0] < 3) {
                fixture.helper.assertTrue(fixture.stand.getCurStandAction() == hold
                                && hold.getPhase() == ActionPhase.PERFORM && fixture.heldByKey(),
                        "Setup: the " + holdName + " is not held in its perform: " + hold.getPhase());
                return false;
            }
            Ability own = fixture.power.getAbility(holdName);
            fixture.helper.assertTrue(hold.canBeCancelledInto(hold.ability),
                    "Setup: the held " + holdName + " does not let its own ability's press in, so there is nothing to refuse");
            String result = fixture.pressCheck(own);
            fixture.check(result.equals("a silent refusal"), "1.16 refused the held " + holdName
                    + "'s own press with no message (canClickDuringTask); the port answered " + result);
            AbilityConditionCheck check = fixture.power.updateAvailableMoves().getContextVariationContainer(own);
            if (check != null && check.ability == own && AbilityInput.withConditionCheck(check, fixture.user, InputMethod.HOLD)) {
                BufferingState pressed = BufferingState.clickCanBuffer();
                AbilityInput.keyPress(OTHER_KEY, check.ability, fixture.user, null, InputMethod.HOLD, 0.0F, pressed,
                        own.getAbilityId());
                AbilityInput.keyRelease(OTHER_KEY, fixture.user);
            }
            fixture.check(fixture.stand.getCurStandAction() == hold && !hold.isOver()
                            && hold.getPhase() == ActionPhase.PERFORM && fixture.heldByKey(),
                    "The refused own press changed the held " + holdName + ": the Stand's action is now "
                            + fixture.stand.getCurStandAction() + " (was " + hold + "), the hold " + hold.getPhase());
            return true;
        });
    }

    /*
     * 1.16 weather_report_give_cloud_shield was a StandAction: PowerBaseImpl.checkRequirements :264 refused it, with the
     * stun message, while its performer was stunned, and StandAction.getPerformer :191-193 was the summoned Stand while it
     * was out, else the user. A stun on the user alone does not stop the gift while the Stand is out.
     */
    @GameTest(template = "empty", batch = "wr_gift_stun", timeoutTicks = TIMEOUT_TICKS)
    public static void aCloudShieldGiftMeetsItsStandsStunNotItsUsersAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "gift_stun");
        Ability[] gift = {null};
        int[] step = {0};
        int[] waited = {0};
        fixture.run(() -> {
            gift[0] = fixture.power.getAbility(WeatherReportAddon.GIVE_CLOUD_SHIELD_NAME);
            fixture.helper.assertTrue(gift[0] != null, "Weather Report has no give_cloud_shield ability");
            fixture.aimAt(fixture.cow(3.0D));
        }, () -> {
            switch (step[0]++) {
                case 0 -> {
                    fixture.stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
                    fixture.helper.assertTrue(ModStatusEffects.isStunned(fixture.stand)
                            && !ModStatusEffects.isStunned(fixture.user), "Setup: only the Stand must be stunned");
                    String result = fixture.pressCheck(gift[0]);
                    fixture.check(result.equals(STUN_KEY), "1.16 refused the gift with the stun message while its"
                            + " summoned Stand, its performer, was stunned; the port answered " + result);
                    return false;
                }
                case 1 -> {
                    fixture.stand.removeEffect(ModStatusEffects.STUN);
                    WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(fixture.user, fixture.power);
                    fixture.helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
                            "Setup: Weather Report is still out");
                    fixture.user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
                    String result = fixture.pressCheck(gift[0]);
                    fixture.check(result.equals(STUN_KEY), "1.16 refused the gift with the stun message while its"
                            + " user, its performer with no Stand out, was stunned; the port answered " + result);
                    return false;
                }
                case 2 -> {
                    fixture.summon();
                    fixture.aimAt(fixture.cow);
                    // The summon shares the user's stun with the Stand (1.16 onStandSummonServerSide); its copy goes,
                    // so that only the user is stunned.
                    fixture.stand.removeEffect(ModStatusEffects.STUN);
                    fixture.helper.assertTrue(ModStatusEffects.isStunned(fixture.user)
                            && !ModStatusEffects.isStunned(fixture.stand), "Setup: only the user must be stunned");
                    String result = fixture.pressCheck(gift[0]);
                    fixture.check(result.equals("admitted"), "1.16 let the gift through while only its user was"
                            + " stunned, its summoned Stand performing it; the port answered " + result);
                    if (result.equals("admitted")) {
                        BufferingState pressed = BufferingState.clickCanBuffer();
                        AbilityInput.keyPress(OTHER_KEY, gift[0], fixture.user, null, InputMethod.CLICK, 0.0F, pressed,
                                gift[0].getAbilityId());
                        AbilityInput.keyRelease(OTHER_KEY, fixture.user);
                        fixture.check(pressed.isActionSuccess && !pressed.shouldBuffer,
                                "The admitted gift did not go off with only its user stunned");
                    }
                    return !fixture.failures.isEmpty();
                }
                default -> {
                    if (fixture.cow.hasEffect(WeatherReportAddon.CLOUD_SHIELD_EFFECT)) {
                        return true;
                    }
                    if (++waited[0] > 5) {
                        fixture.check(false, "The gift pressed with only its user stunned gave the cow no cloud shield");
                        return true;
                    }
                    return false;
                }
            }
        });
    }

    /*
     * 1.16 refused every other action of the power while it held Fugu Rain's charge (PowerBaseImpl.checkRequirements
     * :255, a charge's heldAllowsOtherAction false). The gift's performer being the Stand that holds the charge, the port
     * keeps such a press in the input buffer (owner decision N2): the press check admits it, it never goes off during
     * the charge, and it goes off once the charge has ended.
     */
    @GameTest(template = "empty", batch = "wr_gift_charge", timeoutTicks = TIMEOUT_TICKS)
    public static void aCloudShieldGiftDoesNotGoOffDuringTheFuguRainChargeAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "gift_charge");
        Ability[] fugu = {null};
        Ability[] gift = {null};
        int[] step = {0};
        int[] waited = {0};
        fixture.run(() -> {
            fixture.floorAndRain();
            fugu[0] = fixture.power.getAbility(WeatherReportAddon.FUGU_RAIN_NAME);
            fixture.helper.assertTrue(fugu[0] instanceof WeatherReportFuguRainAbility,
                    "Weather Report has no fugu_rain ability");
            gift[0] = fixture.power.getAbility(WeatherReportAddon.GIVE_CLOUD_SHIELD_NAME);
            fixture.helper.assertTrue(gift[0] != null, "Weather Report has no give_cloud_shield ability");
        }, () -> {
            if (fixture.action == null) {
                // The open sky over the aim waits for the light engine.
                if (fixture.admitted(fugu[0])) {
                    fixture.press(fugu[0]);
                }
                return false;
            }
            EntityActionInstance charge = fixture.action;
            switch (step[0]++) {
                case 0 -> {
                    fixture.helper.assertTrue(fixture.stand.getCurStandAction() == charge
                                    && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                            "Setup: Fugu Rain is not charging: " + charge.getPhase());
                    fixture.aimAt(fixture.cow(3.0D));
                    String result = fixture.pressCheck(gift[0]);
                    fixture.check(result.equals("admitted"), "The press check refused the gift during Fugu Rain's"
                            + " charge; the charge holds the press back in the input buffer (owner N2), it does not"
                            + " refuse it: " + result);
                    BufferingState pressed = BufferingState.clickCanBuffer();
                    AbilityInput.keyPress(OTHER_KEY, gift[0], fixture.user, null, InputMethod.CLICK, 0.0F, pressed,
                            gift[0].getAbilityId());
                    AbilityInput.keyRelease(OTHER_KEY, fixture.user);
                    fixture.check(!pressed.isActionSuccess, "1.16 never let the gift go off while the power held"
                            + " Fugu Rain's charge; it went off");
                    fixture.check(pressed.shouldBuffer, "The gift pressed during Fugu Rain's charge was not kept in"
                            + " the input buffer (owner N2)");
                    return !fixture.failures.isEmpty();
                }
                case 1, 2 -> {
                    fixture.check(!fixture.cow.hasEffect(WeatherReportAddon.CLOUD_SHIELD_EFFECT),
                            "1.16 never let the gift go off while the power held Fugu Rain's charge; the cow got the"
                                    + " cloud shield");
                    fixture.check(fixture.stand.getCurStandAction() == charge
                                    && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
                            "The gift pressed during Fugu Rain's charge ended the charge: " + charge.getPhase());
                    if (step[0] == 3) {
                        // the charge's key let go before it fires: the charge ends (onButtonStopHold)
                        AbilityInput.keyRelease(KEY, fixture.user);
                    }
                    return !fixture.failures.isEmpty();
                }
                default -> {
                    if (fixture.cow.hasEffect(WeatherReportAddon.CLOUD_SHIELD_EFFECT)) {
                        fixture.check(charge.isOver() && fixture.stand.getActiveFugaSummoner() == null,
                                "Setup: the released charge did not end: " + charge.getPhase());
                        fixture.check(fixture.power.getAbilityCooldown(gift[0].name()) > 0,
                                "The replayed gift went off without its cooldown");
                        return true;
                    }
                    if (++waited[0] > 10) {
                        fixture.check(false, "The gift kept in the input buffer did not go off once Fugu Rain's"
                                + " charge ended (charge " + charge.getPhase() + ", over " + charge.isOver() + ")");
                        return true;
                    }
                    return false;
                }
            }
        });
    }

    /*
     * 1.16's client sent no press of a held action (getHoldDurationMax above 0) while the power held one
     * (ActionsOverlayGui.onActionClick :2121-2123): weather_report_wind (holdType(120)) and weather_report_cold_wind
     * (holdType(80)) pressed during the held guard did nothing and the guard went on; the input buffer keeps such a press
     * until the hold ends (owner decision N2). 1.16 queued no held action behind the Stand's task
     * (StandEntityAction.canBeQueued: getHoldDurationMax == 0), so in the heavy punch's windup the winds are drawn by
     * their own checks, as the port draws its other held actions, while the punch is drawn queued.
     */
    @GameTest(template = "empty", batch = "wr_wind_held_press", timeoutTicks = TIMEOUT_TICKS)
    public static void theWindsPressedDuringTheHeldGuardWaitAsIn116(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "wind_held_press");
        String[] winds = {WeatherReportAddon.WIND_NAME, WeatherReportAddon.COLD_WIND_NAME};
        int[] step = {0};
        EntityActionInstance[] heavyPunch = {null};
        fixture.run(() -> {
            fixture.press(fixture.power.getAbility("guard"));
        }, () -> {
            EntityActionInstance guard = fixture.action;
            switch (step[0]++) {
                case 0 -> {
                    fixture.helper.assertTrue(fixture.stand.getCurStandAction() == guard && !guard.isOver()
                                    && fixture.heldByKey(),
                            "Setup: the guard is not held: " + guard.getPhase());
                    for (String name : winds) {
                        Ability wind = fixture.power.getAbility(name);
                        fixture.helper.assertTrue(guard.canBeCancelledInto((EntityActionType) wind),
                                "Setup: the held guard would not let the " + name + " replace it");
                        ActionPhase phase = guard.getPhase();
                        AbilityConditionCheck check = fixture.slot(name);
                        boolean admitted = check != null && check.ability == wind
                                && AbilityInput.withConditionCheck(check, fixture.user, InputMethod.HOLD);
                        fixture.helper.assertTrue(admitted, "Setup: the " + name + " was refused during the held"
                                + " guard: " + fixture.pressCheck(wind));
                        BufferingState pressed = BufferingState.clickCanBuffer();
                        AbilityInput.keyPress(OTHER_KEY, check.ability, fixture.user, null, InputMethod.HOLD, 0.0F,
                                pressed, wind.getAbilityId());
                        fixture.check(pressed.shouldBuffer && !pressed.isActionSuccess
                                        && fixture.stand.getCurStandAction() == guard && !guard.isOver()
                                        && guard.getPhase() == phase && fixture.heldByKey(),
                                "1.16's client sent no " + name + " (a held action) pressed while the guard was held,"
                                        + " which the input buffer keeps: buffered " + pressed.shouldBuffer
                                        + ", started " + pressed.isActionSuccess + ", the Stand's action "
                                        + fixture.stand.getCurStandAction() + ", the guard in " + guard.getPhase());
                        AbilityInput.keyRelease(OTHER_KEY, fixture.user);
                    }
                    return !fixture.failures.isEmpty();
                }
                case 1 -> {
                    AbilityInput.keyRelease(KEY, fixture.user);
                    LivingComponentAction standAction = LivingComponentAction.getComponent(fixture.stand);
                    standAction.setAction(null, fixture.user, SyncType.NO_SYNC);
                    fixture.stand.setFinisherMeter(0.0F);
                    heavyPunch[0] = ((EntityActionAbility) fixture.power.getAbility("heavy_punch"))
                            .initActionOnAbilityUse(fixture.level, fixture.user, fixture.stand, null);
                    standAction.setAction(heavyPunch[0], fixture.user, SyncType.NO_SYNC);
                    return false;
                }
                default -> {
                    LivingComponentAction standAction = LivingComponentAction.getComponent(fixture.stand);
                    EntityActionInstance heavy = heavyPunch[0];
                    AbilityConditionCheck punch = fixture.slot("punch");
                    fixture.helper.assertTrue(heavy != null && fixture.stand.getCurStandAction() == heavy
                                    && heavy.getPhase() == ActionPhase.WINDUP && punch != null
                                    && punch.drawnCondition(fixture.user) == ConditionCheck.NEGATIVE_QUEUED,
                            "Setup: the heavy punch's windup does not hold a punch's press back: " + heavy);
                    for (String name : winds) {
                        AbilityConditionCheck slot = fixture.slot(name);
                        ConditionCheck drawn = slot != null ? slot.drawnCondition(fixture.user) : null;
                        fixture.check(slot != null && slot.ability == fixture.power.getAbility(name)
                                        && slot.conditionCheck.isPositive() && drawn == slot.conditionCheck,
                                "1.16 queued no held action behind the Stand's task, so the " + name + " is drawn by"
                                        + " its own check in the heavy punch's windup: its check "
                                        + (slot == null ? "none" : slot.conditionCheck.isPositive() ? "usable"
                                                : "refused") + ", drawn " + (drawn == null ? "nothing"
                                                : drawn == ConditionCheck.NEGATIVE_QUEUED ? "queued (0.75)"
                                                : drawn.isPositive() ? "usable" : "greyed"));
                    }
                    for (String name : winds) {
                        fixture.check(fixture.power.getAbility(name) instanceof EntityActionAbility ability
                                        && ability.isHoldAction(),
                                "1.16 " + name + " was a held action (holdType): isHoldAction must say so");
                    }
                    standAction.setAction(null, fixture.user, SyncType.NO_SYNC);
                    return true;
                }
            }
        });
    }

    // 1.16 WeatherReportEntity.tick dripped while the weather flag was on, not while the rain level was above 0.2.
    @GameTest(template = "empty", batch = "wr_rain_drips", timeoutTicks = TIMEOUT_TICKS)
    public static void rainDripsFollowTheWeatherFlagAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try {
            level.setWeatherParameters(0, 12000, true, false);
            level.setRainLevel(0.0F);
            helper.assertTrue(level.getLevelData().isRaining() && !level.isRaining(),
                    "Setup: the weather flag must be on with the rain level not risen yet");
            helper.assertTrue(WeatherReportStandEntity.showsRainDrips(level),
                    "1.16 dripped as soon as the weather flag was on; the port waits for the rain level");
            level.setWeatherParameters(12000, 0, false, false);
            level.setRainLevel(1.0F);
            helper.assertTrue(!level.getLevelData().isRaining() && level.isRaining(),
                    "Setup: the weather flag must be off with the rain level still high");
            helper.assertTrue(!WeatherReportStandEntity.showsRainDrips(level),
                    "1.16 stopped dripping once the weather flag was off; the port drips while the rain fades");
        }
        finally {
            level.setWeatherParameters(12000, 0, false, false);
            level.setRainLevel(0.0F);
        }
        LOGGER.info("R770_WR_DRIP_PASS {} ", "rain_drips");
        helper.succeed();
    }

    /** A logged-in Weather Report user in the template's entity-ticking chunk, the Stand out, every ability open. */
    private static final class Fixture {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final String id;
        private final List<String> failures = new ArrayList<>();
        private final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        private ServerPlayer user;
        private EmbeddedChannel channel;
        private StandPower power;
        private WeatherReportStandEntity stand;
        private BlockPos userCell;
        private EntityActionInstance action;
        private Cow cow;
        private boolean rained;
        private boolean started;
        private boolean closed;

        private Fixture(GameTestHelper helper, String id) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.id = id;
        }

        private void run(Runnable start, BooleanSupplier observe) {
            try {
                setUp();
            }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
            helper.onEachTick(() -> {
                if (closed) {
                    return;
                }
                try {
                    helper.assertTrue(helper.getTick() < DEADLINE_TICK, "Timed out: the " + id + " case ("
                            + (action == null ? "never pressed" : "action " + action.getPhase()) + ")");
                    if (!started) {
                        started = true;
                        start.run();
                        return;
                    }
                    if (observe.getAsBoolean()) {
                        close();
                        helper.assertTrue(failures.isEmpty(), "WR-HELD " + id + " failed: " + failures);
                        LOGGER.info("R770_WR_HELD_PASS {} ", id);
                        helper.succeed();
                    }
                }
                catch (RuntimeException | Error failure) {
                    close();
                    throw failure;
                }
            });
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            // GameTest keeps only the template's own chunk entity-ticking, so everything stays inside it.
            userCell = new BlockPos(chunk.getMinBlockX() + 8, template.getY() + 2, chunk.getMinBlockZ() + 3);
            helper.assertTrue(level.isPositionEntityTicking(userCell)
                            && level.isPositionEntityTicking(userCell.offset(0, 0, 12)),
                    "The held fixture is outside the entity-ticking test chunk");
            GameProfile profile = new GameProfile(UUID.randomUUID(), "wr-held-" + id);
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            user = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, user, cookie);
            user.setGameMode(GameType.SURVIVAL);
            user.setNoGravity(true);
            user.moveTo(userCell.getX() + 0.5D, userCell.getY(), userCell.getZ() + 0.5D, 0.0F, SHALLOW_PITCH);
            user.setYHeadRot(0.0F);
            user.yBodyRot = 0.0F;

            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Weather Report to the user");
            power.skipProgression();
            power.setResolveLevel(power.getMaxResolveLevel());
            power.setStamina(power.getMaxStamina());
            summon();
        }

        private void summon() {
            helper.assertTrue(WeatherReportAddon.WEATHER_REPORT.get().summon(user, power)
                            && power.getSummonedStandEntity() instanceof WeatherReportStandEntity,
                    "Production Weather Report summon failed");
            stand = (WeatherReportStandEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
        }

        // ClAimTargetPacket stores the client's target on the user and the Stand.
        private void aimAt(Cow target) {
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(target));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
        }

        /** The press check ClAbilityInputPacket asks: "admitted", or the refusal's message key. */
        private String pressCheck(Ability ability) {
            AbilityConditionCheck check = power.updateAvailableMoves().getContextVariationContainer(ability);
            if (check == null || check.ability != ability) {
                return "not available";
            }
            if (check.conditionCheck.isPositive()) {
                return "admitted";
            }
            Component warning = check.conditionCheck.getWarning();
            return warning != null && warning.getContents() instanceof TranslatableContents contents
                    ? contents.getKey()
                    : "a silent refusal";
        }

        // Open stone ground 10 blocks ahead under the open sky, and rain.
        private void floorAndRain() {
            for (BlockPos pos : BlockPos.betweenClosed(userCell.offset(-4, -1, -2), userCell.offset(4, 5, 12))) {
                helper.assertTrue(level.isEmptyBlock(pos), "The held fixture space is not empty at " + pos);
            }
            for (BlockPos pos : BlockPos.betweenClosed(userCell.offset(-4, -1, -1), userCell.offset(4, -1, 12))) {
                BlockPos key = pos.immutable();
                placed.putIfAbsent(key, level.getBlockState(key));
                helper.assertTrue(level.setBlock(key, Blocks.STONE.defaultBlockState(), 3),
                        "Could not place a fixture block at " + key);
            }
            level.setWeatherParameters(0, 12000, true, false);
            level.setRainLevel(1.0F);
            rained = true;
            helper.assertTrue(level.isRaining(), "The level is not raining");
        }

        // The power resolves its keys once a tick (Power.updateAvailableMoves), so a step reads what an earlier tick set.
        private AbilityConditionCheck slot(String name) {
            return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
        }

        private boolean admitted(Ability ability) {
            AbilityConditionCheck check = power.updateAvailableMoves().getContextVariationContainer(ability);
            return check != null && check.ability == ability && check.conditionCheck.isPositive();
        }

        /** The steps of ClAbilityInputPacket.Handler.handlePress for a HOLD ability. */
        private void press(Ability ability) {
            helper.assertTrue(ability != null, "Weather Report lacks the ability of the " + id + " case");
            AbilityConditionCheck check = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(check != null && check.ability == ability
                            && AbilityInput.withConditionCheck(check, user, InputMethod.HOLD),
                    ability.name() + " was refused at the press: " + (check == null ? "not available"
                            : check.conditionCheck.getWarning() != null
                                    ? check.conditionCheck.getWarning().getString() : "a silent refusal"));
            BufferingState buffering = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(KEY, check.ability, user, null, InputMethod.HOLD, 0.0F, buffering,
                    ability.getAbilityId());
            action = stand.getCurStandAction();
            helper.assertTrue(action != null && action.ability == ability,
                    ability.name() + " did not start on the admitted press");
        }

        private Cow cow(double ahead) {
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not create the target");
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            cow.moveTo(userCell.getX() + 0.5D, userCell.getY(), userCell.getZ() + 0.5D + ahead, 180.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the target");
            return cow;
        }

        private boolean heldByKey() {
            return user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.containsKey(KEY);
        }

        private boolean sentMessage(String key) {
            channel.flushOutbound();
            for (Object message : channel.outboundMessages()) {
                if (message instanceof ClientboundSystemChatPacket chat && chat.overlay()
                        && chat.content().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(key)) {
                    return true;
                }
            }
            return false;
        }

        private void check(boolean passed, String message) {
            if (!passed) {
                failures.add(message);
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (cow != null) {
                    cow.discard();
                }
                if (user != null) {
                    AbilityInput.keyRelease(KEY, user);
                    if (stand != null && stand.getActiveFugaSummoner() != null) {
                        stand.getActiveFugaSummoner().discard();
                    }
                    if (power != null && power.isSummoned()) {
                        WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
                    }
                    user.connection.disconnect(Component.literal("Weather Report held GameTest complete"));
                }
            }
            finally {
                if (rained) {
                    level.setWeatherParameters(12000, 0, false, false);
                    level.setRainLevel(0.0F);
                }
                for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                }
                placed.clear();
                if (channel != null) {
                    channel.finishAndReleaseAll();
                }
            }
        }
    }
}
