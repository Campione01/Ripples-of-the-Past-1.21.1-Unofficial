package rotp.core.gametest;

import java.util.List;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility.Uppercut;
import rotp.core.impl.stands.theworld.TheWorldHeavyPunchAbility.TheWorldHeavyPunch;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SPHeavyRecoveryEntryGameTests {
    private static final String SUBJECT = "sp_heavy_recovery_entry";
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(JojoMod.resLoc(SUBJECT + "_tests"));
    private SPHeavyRecoveryEntryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2400, batch = SUBJECT)
    public static void ordinaryHeavySamplesRecoverySpeedWithItsSavedFinisher(GameTestHelper helper) {
        new Suite(helper).start(SPHeavyRecoveryEntryGameTests.class);
    }

    @AfterBatch(batch = SUBJECT)
    public static void releaseHeavyRecoveryBatch(ServerLevel level) { PhaseEntryGameTestSupport.afterBatch(SUBJECT); }

    private static final class Suite extends PhaseEntryGameTestSupport.Suite {
        Suite(GameTestHelper helper) {
            super(helper, SUBJECT, List.of("sp_perform_refused_followup", "sp_recovery_refused_followup", "sp_stable",
                    "sp_expiring_resolve", "sp_uppercut", "tw_heavy", "tw_kick"));
        }
        @Override PhaseEntryGameTestSupport.Scene create(String id) { return new Scene(this, id); }
    }

    private static final class Scene extends PhaseEntryGameTestSupport.Scene {
        EntityActionInstance predecessor;
        StandEntityHeavyPunch action;
        double initialSpeed, predecessorSpeed, recoverySpeed;
        float savedFinisher, initialStamina, cachedRecovery, recoveryLength, predecessorStamina;
        int readyTicks, predecessorRecoveryTicks, afterPredecessorTicks;
        boolean refused, started, recoverySeen, freezeObserved, ownDebitSeen;

        Scene(Suite suite, String id) { super(suite, id); }
        boolean followup() { return id.endsWith("followup"); }
        boolean refusedInPerform() { return id.equals("sp_perform_refused_followup"); }
        boolean expires() { return id.equals("sp_expiring_resolve") || finisher() || id.equals("tw_heavy"); }
        boolean finisher() { return id.equals("sp_uppercut") || id.equals("tw_kick"); }

        @Override void prepare() {
            actor(id.startsWith("tw_") ? "the_world" : "star_platinum", 1, false);
            cow();
            power.setStamina(350);
            if (followup() || finisher()) {
                var result = UNLOCKS.forceUnlock(power, id.equals("tw_kick") ? "kick" : "uppercut");
                premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                        "Owner-scoped declared uppercut skill prepared; ability Resolve/admission still checked");
            }
            if (expires()) user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 4, 0, false, false));
        }

        @Override void tick() {
            if (action != null && action.isOver()) {
                premise(recoverySeen, "Actual RECOVERY entered");
                check(ownDebitSeen, "Ordinary action's single 50 debit observed");
                check(freezeObserved, "RECOVERY remains fixed through later real getter refresh");
                done = true;
            }
        }

        @Override void post(Entity entity) {
            super.post(entity);
            if (done) return;
            if (entity != stand) return;
            if (!started) {
                if (++readyTicks < 2) return;
                aim();
                if (followup()) {
                    stand.setFinisherMeter(1);
                    predecessorSpeed = stand.getAttackSpeed(); predecessorStamina = power.getStamina();
                    var press = press("heavy_punch", PhaseEntryGameTestSupport.ATTACK, false, 0);
                    premise(press.success() && press.action() instanceof Uppercut, "Legal registered finisher uppercut predecessor");
                    predecessor = press.action();
                    premise(((StandEntityHeavyPunch) predecessor).finisherValue >= 0.5F, "Predecessor saved its finisher value");
                    PhaseEntryGameTestSupport.log("PREDECESSOR " + id + " speed=" + predecessorSpeed + " stamina=" + predecessorStamina
                            + " saved=" + ((StandEntityHeavyPunch) predecessor).finisherValue + " current=" + stand.getFinisherMeter());
                    started = true; return;
                }
                stand.setFinisherMeter(finisher() ? 1 : 0.3F);
                var press = press("heavy_punch", PhaseEntryGameTestSupport.ATTACK, false, 0);
                premise(press.success(), "Registered heavy input success");
                capture(press.action());
                started = true; return;
            }
            if (followup() && action == null) {
                // 1.16 StandEntityHeavyAttack.getFinisherVariationIfPresent kept the heavy key on the uppercut while it was
                // the Stand's task, and StandEntityAction.canClickDuringTask refused it, unqueued: the ordinary heavy
                // follows only once the uppercut is over
                if (!refused && (refusedInPerform()
                        ? predecessor.getPhase() == ActionPhase.PERFORM && predecessor.getPhaseTick() == 0
                        : predecessor.getPhase() == ActionPhase.RECOVERY && ++predecessorRecoveryTicks >= 2)) {
                    var selected = power.updateAvailableMoves().getContextVariationContainer(power.getAbility("heavy_punch"));
                    premise(selected != null && selected.ability == predecessor.ability && !selected.conditionCheck.isPositive()
                            && stand.getCurStandAction() == predecessor, "Heavy key kept on the running uppercut and refused in "
                                    + predecessor.getPhase() + ": " + (selected != null ? selected.ability.name() : null));
                    refused = true;
                }
                if (refused && predecessor.isOver() && ++afterPredecessorTicks >= 2) {
                    premise(stand.getAttackSpeed() < predecessorSpeed, "Follow-up sees previous debit through a real Stand refresh");
                    var press = press("heavy_punch", PhaseEntryGameTestSupport.FOLLOW, false, 0);
                    premise(press.success(), "Registered ordinary-heavy follow-up admitted after the uppercut");
                    capture(press.action());
                }
                return;
            }
            if (action == null || action.isOver()) return;
            if (action.getPhase() == ActionPhase.RECOVERY && !recoverySeen) {
                recoverySeen = true;
                recoverySpeed = stand.getAttackSpeed();
                float expected = StandStatFormulas.getHeavyAttackRecovery(recoverySpeed, savedFinisher);
                recoveryLength = action.getCurPhaseLength();
                check(PhaseEntryGameTestSupport.near(recoveryLength, expected),
                        "RECOVERY speed=" + recoverySpeed + " actionSaved=" + savedFinisher + " currentMeter=" + stand.getFinisherMeter()
                                + " globalLast=" + stand.getLastHeavyFinisherValue() + " cached=" + cachedRecovery
                                + " expected=" + expected + " actual=" + recoveryLength);
                check(PhaseEntryGameTestSupport.near(action.finisherValue, savedFinisher), "Action finisher was neither resnapshotted nor consumed twice");
                check(PhaseEntryGameTestSupport.near(initialStamina - power.getStamina(), 50), "One impact debit before RECOVERY; no forced multiplier refresh");
                ownDebitSeen = true;
                if (expires()) premise(expected != cachedRecovery, "Changed-speed rounded oracle distinguishes entry from initial cache");
                else check(expected == cachedRecovery, "Stable/follow-up control preserves existing result despite own one-tick debit");
                if (expires()) premise(!user.hasEffect(ModStatusEffects.RESOLVE) && recoverySpeed < initialSpeed,
                        "Resolve expired naturally during WINDUP before RECOVERY");
                // 1.16 kept the builder's 1-tick perform for every one of these heavies.
                check(PhaseEntryGameTestSupport.near(action.phasesLength.getFloat(ActionPhase.PERFORM), 1),
                        "Configured 1-tick PERFORM length");
            }
            else if (action.getPhase() == ActionPhase.RECOVERY && recoverySeen) {
                freezeObserved = true;
                check(PhaseEntryGameTestSupport.near(action.getCurPhaseLength(), recoveryLength),
                        "Frozen RECOVERY=" + recoveryLength + " liveGetter=" + stand.getAttackSpeed());
            }
        }

        void capture(EntityActionInstance installed) {
            premise(installed instanceof StandEntityHeavyPunch, "Registered heavy action type");
            action = (StandEntityHeavyPunch) installed;
            if (id.startsWith("sp_") && !finisher()) premise(action.getClass() == StandEntityHeavyPunch.class
                    && ((rotp.core.powersystem.ability.Ability) action.ability).name().equals("heavy_punch"), "Exact SP ordinary-heavy role, not uppercut/grab/charged");
            if (id.equals("sp_uppercut")) premise(action instanceof Uppercut, "Actual uppercut finisher");
            if (id.equals("tw_heavy")) premise(action instanceof TheWorldHeavyPunch, "Actual The World heavy punch");
            if (id.equals("tw_kick")) premise(action instanceof TheWorldKick, "Actual The World kick finisher");
            initialSpeed = stand.getAttackSpeed(); savedFinisher = action.finisherValue; initialStamina = power.getStamina();
            cachedRecovery = StandStatFormulas.getHeavyAttackRecovery(initialSpeed, savedFinisher);
            check(PhaseEntryGameTestSupport.near(action.phasesLength.getFloat(ActionPhase.RECOVERY), cachedRecovery),
                    "Startup cache retains initial speed/saved finisher before later entry");
            check(savedFinisher > stand.getFinisherMeter(), "Saved pre-consumption finisher differs from current meter");
            if (followup()) premise(predecessor != action && predecessor.isOver(), "Actual admitted follow-up after its finisher predecessor");
            PhaseEntryGameTestSupport.log("START heavy/" + id + " phase=" + action.getPhase() + " speed=" + initialSpeed
                    + " stamina=" + initialStamina + " max=" + power.getMaxStamina() + " saved=" + savedFinisher
                    + " meter=" + stand.getFinisherMeter() + " cachedRecovery=" + cachedRecovery);
        }
    }
}
