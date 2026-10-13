package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
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
public final class SPLightPhaseEntryGameTests {
    private static final String SUBJECT = "sp_light_phase_entry";
    private SPLightPhaseEntryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2400, batch = SUBJECT)
    public static void selectedLightSamplesOnlyLaterPhaseEntries(GameTestHelper helper) {
        new Suite(helper).start(SPLightPhaseEntryGameTests.class);
    }

    @AfterBatch(batch = SUBJECT)
    public static void releaseLightPhaseBatch(ServerLevel level) { PhaseEntryGameTestSupport.afterBatch(SUBJECT); }

    private static final class Suite extends PhaseEntryGameTestSupport.Suite {
        Suite(GameTestHelper helper) {
            super(helper, SUBJECT, List.of("sp_punch_hit", "sp_punch2_hit", "sp_punch3_hit", "sp_punch4_hit",
                    "sp_whiff", "sp_arms", "sp_stable", "sp_expiring_resolve", "tw_default", "cd_default"));
        }
        @Override PhaseEntryGameTestSupport.Scene create(String id) { return new Scene(this, id); }
    }

    private static final class Scene extends PhaseEntryGameTestSupport.Scene {
        EntityActionInstance action;
        EntityActionInstance prefix;
        ActionPhase previous;
        double initialSpeed, performSpeed, recoverySpeed, speedAtRecovery;
        float initialMeter, meterAtPerform, staminaAtPress, expectedPerform, expectedRecovery, performLength, recoveryLength;
        boolean started, performSeen, recoverySeen, debitObserved, freezeObserved;
        int readyTicks;

        Scene(Suite suite, String id) { super(suite, id); }
        boolean selectedSP() { return id.startsWith("sp_"); }
        boolean expiry() { return id.equals("sp_expiring_resolve"); }
        String member() {
            if (id.startsWith("sp_punch2")) return "punch2";
            if (id.startsWith("sp_punch3")) return "punch3";
            if (id.startsWith("sp_punch4")) return "punch4";
            return "punch";
        }

        @Override void prepare() {
            actor(id.equals("tw_default") ? "the_world" : id.equals("cd_default") ? "crazy_diamond" : "star_platinum",
                    1, id.equals("sp_arms"));
            if (!id.equals("sp_whiff")) cow();
            power.setStamina(id.equals("sp_stable") ? power.getMaxStamina() * 0.8F : 300);
            if (expiry()) user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 12, 0, false, false));
        }

        @Override void tick() {
            if (action != null && action.isOver()) {
                premise(performSeen && recoverySeen, "Both later phases were naturally entered");
                check(debitObserved, "Exactly one actual 10 stamina debit observed");
                check(freezeObserved, "RECOVERY length stayed fixed through later native ticks");
                done = true;
            }
        }

        @Override void post(Entity entity) {
            super.post(entity);
            if (entity != stand || done) return;
            if (!started) {
                readyTicks++;
                if (expiry()) {
                    var effect = user.getEffect(ModStatusEffects.RESOLVE);
                    premise(effect != null, "Resolve must not expire before its legal press");
                    if (prefix == null) {
                        if (readyTicks < 2) return;
                        stand.setFinisherMeter(0);
                        aim();
                        var first = press("punch", PhaseEntryGameTestSupport.ATTACK, false, 0);
                        premise(first.success() && first.action() instanceof StandEntityPunch, "Legal prefix light for a nonzero continuation windup");
                        prefix = first.action();
                        return;
                    }
                    if (effect.getDuration() != 1) return;
                    premise(prefix.getPhase() == ActionPhase.RECOVERY && !prefix.isOver(), "Prefix remains in legal cancellable RECOVERY");
                    premise(PhaseEntryGameTestSupport.near(stand.staminaCondition, 1), "Resolve speed refreshed by a real prior entity tick");
                    rotp.core.powersystem.ability.input.AbilityInput.keyRelease(PhaseEntryGameTestSupport.ATTACK, user);
                }
                else if (readyTicks < 2) return;
                start(); return;
            }
            if (action == null || action.isOver()) return;
            ActionPhase phase = action.getPhase();
            if (phase == ActionPhase.PERFORM && !performSeen) {
                performSeen = true;
                performSpeed = stand.getAttackSpeed(); meterAtPerform = stand.getFinisherMeter();
                expectedPerform = StandStatFormulas.getLightAttackRecovery(performSpeed, meterAtPerform);
                performLength = action.getCurPhaseLength();
                float cached = StandStatFormulas.getLightAttackRecovery(initialSpeed, initialMeter);
                check(PhaseEntryGameTestSupport.near(action.getCurPhaseLength(), selectedSP() ? expectedPerform : cached),
                        "PERFORM entry speed=" + performSpeed + " meter=" + meterAtPerform + " cached=" + cached
                                + " expected=" + (selectedSP() ? expectedPerform : cached) + " actual=" + action.getCurPhaseLength());
                if (expiry()) {
                    premise(!user.hasEffect(ModStatusEffects.RESOLVE) && performSpeed < initialSpeed, "Natural Resolve expiration changed speed before PERFORM");
                    premise(expectedPerform != cached, "Expiring-Resolve duration oracle must distinguish entry from admission");
                }
            }
            if (phase == ActionPhase.PERFORM && action.getPhaseTick() > 0) {
                debitObserved |= PhaseEntryGameTestSupport.near(staminaAtPress - power.getStamina(), 10);
                if (performSeen && previous == ActionPhase.PERFORM)
                    check(PhaseEntryGameTestSupport.near(action.getCurPhaseLength(), performLength), "PERFORM does not resize its observed entry duration within phase");
            }
            if (phase == ActionPhase.RECOVERY && !recoverySeen) {
                recoverySeen = true;
                recoverySpeed = stand.getAttackSpeed(); speedAtRecovery = recoverySpeed;
                expectedRecovery = StandStatFormulas.getLightAttackRecovery(recoverySpeed, stand.getFinisherMeter())
                        * (stand.isArmsOnlyMode() ? 2 : 4);
                float cached = StandStatFormulas.getLightAttackRecovery(initialSpeed, initialMeter)
                        * (stand.isArmsOnlyMode() ? 2 : 4);
                recoveryLength = action.getCurPhaseLength();
                check(PhaseEntryGameTestSupport.near(recoveryLength, selectedSP() ? expectedRecovery : cached),
                        "RECOVERY entry speed=" + recoverySpeed + " meter=" + stand.getFinisherMeter() + " arms=" + stand.isArmsOnlyMode()
                                + " cached=" + cached + " expected=" + (selectedSP() ? expectedRecovery : cached) + " actual=" + recoveryLength);
                check(PhaseEntryGameTestSupport.near(stand.getFinisherMeter(), initialMeter + (target == null ? 0.1 : 0.2)),
                        "Registered hit/whiff current finisher=" + stand.getFinisherMeter());
                if (selectedSP() && !id.equals("sp_stable"))
                    premise(expectedRecovery != cached, "Changed-resource/meter recovery oracle distinguishes cache");
                if (id.equals("sp_stable")) check(expectedRecovery == cached, "Stable-speed control retains duration");
            }
            else if (phase == ActionPhase.RECOVERY && recoverySeen) {
                freezeObserved = true;
                check(PhaseEntryGameTestSupport.near(action.getCurPhaseLength(), recoveryLength),
                        "Frozen RECOVERY length=" + recoveryLength + " currentGetterSpeed=" + stand.getAttackSpeed());
            }
            previous = phase;
        }

        void start() {
            stand.setFinisherMeter(0.3F);
            aim();
            initialSpeed = stand.getAttackSpeed(); initialMeter = stand.getFinisherMeter(); staminaAtPress = power.getStamina();
            var press = press(member(), PhaseEntryGameTestSupport.ATTACK, false, 0);
            premise(press.success() && press.action() != null && press.action() instanceof StandEntityPunch,
                    "Real input installed registered normal light action");
            action = press.action();
            String selectedMember = expiry() ? "punch2" : member();
            premise(((rotp.core.powersystem.ability.Ability) action.ability).name().equals(selectedMember), "Exact selected punch member " + selectedMember);
            if (expiry()) premise(action.getPhase() == ActionPhase.WINDUP && action.getCurPhaseLength() > 0,
                    "Real same-family continuation has a nonzero WINDUP before Resolve expires");
            started = true;
            PhaseEntryGameTestSupport.log("START light/" + id + " phase=" + action.getPhase() + " windup="
                    + action.phasesLength.getFloat(ActionPhase.WINDUP) + " speed=" + initialSpeed + " meter=" + initialMeter
                    + " stamina=" + staminaAtPress + " max=" + power.getMaxStamina());
            check(PhaseEntryGameTestSupport.near(action.phasesLength.getFloat(ActionPhase.PERFORM),
                    StandStatFormulas.getLightAttackRecovery(initialSpeed, initialMeter)), "Initial callback keeps admission PERFORM cache until later entry");
        }
    }
}
