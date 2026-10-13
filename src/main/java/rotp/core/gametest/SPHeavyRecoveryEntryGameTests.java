package rotp.core.gametest;

import java.util.List;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility.Uppercut;
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
            super(helper, SUBJECT, List.of("sp_cold_followup", "sp_warm_followup", "sp_stable",
                    "sp_expiring_resolve", "sp_uppercut_default", "tw_default", "cd_default"));
        }
        @Override PhaseEntryGameTestSupport.Scene create(String id) { return new Scene(this, id); }
    }

    private static final class Scene extends PhaseEntryGameTestSupport.Scene {
        EntityActionInstance predecessor;
        StandEntityHeavyPunch action;
        double initialSpeed, predecessorSpeed, recoverySpeed;
        float savedFinisher, initialStamina, cachedRecovery, recoveryLength, predecessorStamina;
        int readyTicks, predecessorRecoveryTicks;
        boolean queued, started, recoverySeen, freezeObserved, ownDebitSeen;

        Scene(Suite suite, String id) { super(suite, id); }
        boolean followup() { return id.endsWith("followup"); }
        boolean cold() { return id.equals("sp_cold_followup"); }
        boolean expires() { return id.equals("sp_expiring_resolve") || id.endsWith("default"); }
        boolean selectedSP() { return id.startsWith("sp_") && !id.equals("sp_uppercut_default"); }

        @Override void prepare() {
            actor(id.equals("tw_default") ? "the_world" : id.equals("cd_default") ? "crazy_diamond" : "star_platinum", 1, false);
            cow();
            power.setStamina(350);
            if (followup() || id.equals("sp_uppercut_default")) {
                var result = UNLOCKS.forceUnlock(power, "uppercut");
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
            if (entity == user && cold() && queued && action == null) {
                var installed = stand.getCurStandAction();
                if (installed != null && installed != predecessor) capture(installed);
            }
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
                    premise(((StandEntityHeavyPunch) predecessor).finisherValue >= 0.5F,
                            "Predecessor saved finisher permits RECOVERY cancellation, not an ordinary-heavy queue");
                    PhaseEntryGameTestSupport.log("PREDECESSOR " + id + " speed=" + predecessorSpeed + " stamina=" + predecessorStamina
                            + " saved=" + ((StandEntityHeavyPunch) predecessor).finisherValue + " current=" + stand.getFinisherMeter());
                    started = true; return;
                }
                stand.setFinisherMeter(id.equals("sp_uppercut_default") ? 1 : 0.3F);
                var press = press("heavy_punch", PhaseEntryGameTestSupport.ATTACK, false, 0);
                premise(press.success(), "Registered heavy input success");
                capture(press.action());
                started = true; return;
            }
            if (followup() && action == null) {
                if (cold() && !queued && predecessor.getPhase() == ActionPhase.PERFORM && predecessor.getPhaseTick() == 0) {
                    var press = press("heavy_punch", PhaseEntryGameTestSupport.FOLLOW, true, 0);
                    premise(press.buffered() && !press.success() && press.action() == predecessor
                            && press.selected().name().equals("heavy_punch"), "Legal ordinary-heavy input queued without replacing uppercut PERFORM");
                    queued = true;
                }
                if (!cold() && predecessor.getPhase() == ActionPhase.RECOVERY && ++predecessorRecoveryTicks >= 2) {
                    premise(stand.getAttackSpeed() < predecessorSpeed, "Warm control sees previous debit through a real Stand refresh");
                    var press = press("heavy_punch", PhaseEntryGameTestSupport.FOLLOW, false, 0);
                    premise(press.success(), "Warm registered RECOVERY follow-up admitted");
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
                check(PhaseEntryGameTestSupport.near(recoveryLength, selectedSP() ? expected : cachedRecovery),
                        "RECOVERY speed=" + recoverySpeed + " actionSaved=" + savedFinisher + " currentMeter=" + stand.getFinisherMeter()
                                + " globalLast=" + stand.getLastHeavyFinisherValue() + " cached=" + cachedRecovery
                                + " expected=" + (selectedSP() ? expected : cachedRecovery) + " actual=" + recoveryLength);
                check(PhaseEntryGameTestSupport.near(action.finisherValue, savedFinisher), "Action finisher was neither resnapshotted nor consumed twice");
                check(PhaseEntryGameTestSupport.near(initialStamina - power.getStamina(), 50), "One impact debit before RECOVERY; no forced multiplier refresh");
                ownDebitSeen = true;
                if (cold() || expires()) premise(expected != cachedRecovery, "Changed-speed rounded oracle distinguishes entry from initial cache");
                else check(expected == cachedRecovery, "Stable/warm control preserves existing result despite own one-tick debit");
                if (cold()) {
                    premise(PhaseEntryGameTestSupport.near(initialSpeed, predecessorSpeed) && recoverySpeed < initialSpeed,
                            "Real user replay captured cold previous-debit speed, then native Stand ticks refreshed it");
                }
                if (expires()) premise(!user.hasEffect(ModStatusEffects.RESOLVE) && recoverySpeed < initialSpeed,
                        "Resolve expired naturally during WINDUP before RECOVERY");
                check(PhaseEntryGameTestSupport.near(action.phasesLength.getFloat(ActionPhase.PERFORM), id.equals("cd_default") ? 6 : 1),
                        "Existing configured PERFORM length unchanged");
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
            if (selectedSP()) premise(action.getClass() == StandEntityHeavyPunch.class
                    && ((rotp.core.powersystem.ability.Ability) action.ability).name().equals("heavy_punch"), "Exact SP ordinary-heavy role, not uppercut/grab/charged");
            if (id.equals("sp_uppercut_default")) premise(action instanceof Uppercut, "Actual uppercut default-off control");
            initialSpeed = stand.getAttackSpeed(); savedFinisher = action.finisherValue; initialStamina = power.getStamina();
            cachedRecovery = StandStatFormulas.getHeavyAttackRecovery(initialSpeed, savedFinisher);
            check(PhaseEntryGameTestSupport.near(action.phasesLength.getFloat(ActionPhase.RECOVERY), cachedRecovery),
                    "Startup cache retains initial speed/saved finisher before later entry");
            check(savedFinisher > stand.getFinisherMeter(), "Saved pre-consumption finisher differs from current meter");
            if (followup()) premise(predecessor != action && predecessor.isOver(), "Actual admitted follow-up replaced its finisher predecessor");
            PhaseEntryGameTestSupport.log("START heavy/" + id + " phase=" + action.getPhase() + " speed=" + initialSpeed
                    + " stamina=" + initialStamina + " max=" + power.getMaxStamina() + " saved=" + savedFinisher
                    + " meter=" + stand.getFinisherMeter() + " cachedRecovery=" + cachedRecovery);
        }
    }
}
