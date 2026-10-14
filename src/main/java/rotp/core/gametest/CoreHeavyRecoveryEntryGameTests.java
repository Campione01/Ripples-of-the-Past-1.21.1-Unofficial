package rotp.core.gametest;

import java.util.List;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.crazydiamond.CrazyDiamondHeavyPunchAbility.CrazyDiamondHeavyPunch;
import rotp.core.impl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;
import rotp.core.impl.stands.magiciansred.MagiciansRedKickAbility.MagiciansRedKick;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityHeavyAttack.getStandRecoveryTicks: Crazy Diamond's heavy punch and finisher, Gold Experience's heavy
 * punch and Lifeshot punch, and Magician's Red's heavy punch and kick read their recovery from the Stand's attack speed
 * when the recovery starts (StandEntityTask.setPhase), not when the windup starts. Each scene lets the user's Resolve
 * run out during the windup, so the two readings differ.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreHeavyRecoveryEntryGameTests {
    private static final String SUBJECT = "core_heavy_recovery_entry";
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(JojoMod.resLoc(SUBJECT + "_tests"));
    private CoreHeavyRecoveryEntryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2400, batch = SUBJECT)
    public static void coreHeaviesReadTheirRecoveryWhenItStarts(GameTestHelper helper) {
        new Suite(helper).start(CoreHeavyRecoveryEntryGameTests.class);
    }

    @AfterBatch(batch = SUBJECT)
    public static void releaseCoreHeavyRecoveryBatch(ServerLevel level) { PhaseEntryGameTestSupport.afterBatch(SUBJECT); }

    private static final class Suite extends PhaseEntryGameTestSupport.Suite {
        Suite(GameTestHelper helper) {
            super(helper, SUBJECT, List.of("cd_heavy", "cd_finisher", "ge_heavy", "ge_lifeshot", "mr_heavy", "mr_kick"));
        }
        @Override PhaseEntryGameTestSupport.Scene create(String id) { return new Scene(this, id); }
    }

    private static final class Scene extends PhaseEntryGameTestSupport.Scene {
        StandEntityHeavyPunch action;
        double initialSpeed, recoverySpeed;
        float savedFinisher, initialStamina, cachedRecovery, recoveryLength;
        int readyTicks;
        boolean started, recoverySeen, freezeObserved;

        Scene(Suite suite, String id) { super(suite, id); }
        boolean finisher() { return id.equals("cd_finisher") || id.equals("ge_lifeshot") || id.equals("mr_kick"); }
        String standName() {
            return id.startsWith("cd_") ? "crazy_diamond" : id.startsWith("ge_") ? "gold_experience" : "magicians_red";
        }
        String skill() { return id.equals("cd_finisher") ? "finisher" : id.equals("ge_lifeshot") ? "lifeshot" : "kick"; }
        String abilityName() {
            return switch (id) {
                case "cd_finisher" -> "finisher";
                case "ge_lifeshot" -> "lifeshot_punch";
                case "mr_kick" -> "kick";
                default -> "heavy_punch";
            };
        }

        @Override void prepare() {
            actor(standName(), 1, false);
            cow();
            power.setStamina(350);
            if (finisher()) {
                var result = UNLOCKS.forceUnlock(power, skill());
                premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                        "Owner-scoped declared finisher skill " + skill() + " prepared");
            }
            user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 4, 0, false, false));
        }

        @Override void tick() {
            if (action != null && action.isOver()) {
                premise(recoverySeen, "Actual RECOVERY entered");
                check(freezeObserved, "RECOVERY remains fixed through later real getter refresh");
                done = true;
            }
        }

        @Override void post(Entity entity) {
            super.post(entity);
            if (done || entity != stand) return;
            if (!started) {
                if (++readyTicks < 2) return;
                aim();
                stand.setFinisherMeter(finisher() ? 1 : 0.3F);
                var press = press("heavy_punch", PhaseEntryGameTestSupport.ATTACK, false, 0);
                premise(press.success() && press.action() instanceof StandEntityHeavyPunch, "Registered heavy input success");
                action = (StandEntityHeavyPunch) press.action();
                premise(((Ability) action.ability).name().equals(abilityName()), "Actual " + abilityName() + ", got "
                        + ((Ability) action.ability).name());
                if (id.equals("cd_heavy")) premise(action instanceof CrazyDiamondHeavyPunch, "Crazy Diamond heavy action");
                if (id.startsWith("ge_")) premise(action instanceof ToothKnockingHeavyPunch, "Gold Experience heavy action");
                if (id.equals("mr_kick")) premise(action instanceof MagiciansRedKick, "Magician's Red kick action");
                initialSpeed = stand.getAttackSpeed(); savedFinisher = action.finisherValue; initialStamina = power.getStamina();
                cachedRecovery = StandStatFormulas.getHeavyAttackRecovery(initialSpeed, savedFinisher);
                premise(PhaseEntryGameTestSupport.near(action.phasesLength.getFloat(ActionPhase.RECOVERY), cachedRecovery),
                        "Startup cache holds the initial speed and saved finisher");
                PhaseEntryGameTestSupport.log("START heavy/" + id + " phase=" + action.getPhase() + " speed=" + initialSpeed
                        + " stamina=" + initialStamina + " saved=" + savedFinisher + " meter=" + stand.getFinisherMeter()
                        + " cachedRecovery=" + cachedRecovery);
                started = true; return;
            }
            if (action == null || action.isOver()) return;
            if (action.getPhase() == ActionPhase.RECOVERY && !recoverySeen) {
                recoverySeen = true;
                recoverySpeed = stand.getAttackSpeed();
                float expected = StandStatFormulas.getHeavyAttackRecovery(recoverySpeed, savedFinisher);
                recoveryLength = action.getCurPhaseLength();
                premise(!user.hasEffect(ModStatusEffects.RESOLVE) && recoverySpeed < initialSpeed,
                        "Resolve expired naturally during WINDUP before RECOVERY");
                premise(expected != cachedRecovery, "Changed-speed oracle distinguishes entry from initial cache");
                check(PhaseEntryGameTestSupport.near(recoveryLength, expected),
                        "1.16: RECOVERY read at its start; speed " + initialSpeed + " -> " + recoverySpeed + ", saved finisher "
                                + savedFinisher + ", windup-start reading " + cachedRecovery + ", expected " + expected
                                + ", actual " + recoveryLength);
                check(PhaseEntryGameTestSupport.near(action.finisherValue, savedFinisher),
                        "Action finisher was neither resnapshotted nor consumed twice");
                check(PhaseEntryGameTestSupport.near(initialStamina - power.getStamina(), 50),
                        "One impact debit before RECOVERY; got " + (initialStamina - power.getStamina()));
            }
            else if (action.getPhase() == ActionPhase.RECOVERY && recoverySeen) {
                freezeObserved = true;
                check(PhaseEntryGameTestSupport.near(action.getCurPhaseLength(), recoveryLength),
                        "Frozen RECOVERY=" + recoveryLength + " liveGetter=" + stand.getAttackSpeed());
            }
        }
    }
}
