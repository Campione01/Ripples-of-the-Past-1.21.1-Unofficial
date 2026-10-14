package rotp.core.gametest;

import java.util.List;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.crazydiamond.CrazyDiamondHeavyPunchAbility.CrazyDiamondHeavyPunch;
import rotp.core.impl.stands.goldexperience.GoldExperienceHeavyPunchAbility.ToothKnockingHeavyPunch;
import rotp.core.impl.stands.magiciansred.MagiciansRedKickAbility.MagiciansRedKick;
import rotp.core.impl.stands.silverchariot.SilverChariotDashAttackAbility.DashStrike;
import rotp.core.impl.stands.silverchariot.SilverChariotSweepingAttackAbility.SweepingStrike;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
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

/**
 * 1.16 StandEntityHeavyAttack.getStandRecoveryTicks: Crazy Diamond's heavy punch and finisher, Gold Experience's heavy
 * punch and Lifeshot punch, Magician's Red's heavy punch and kick, and Silver Chariot's dash attack and sweeping attack
 * read their recovery from the Stand's attack speed when the recovery starts (StandEntityTask.setPhase), not when the
 * action starts; so did The World's TS punch through its own override (TheWorldTSHeavyAttack.getStandRecoveryTicks,
 * finisher 0). Each scene lets the user's Resolve run out before the recovery, so the two readings differ.
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
            super(helper, SUBJECT, List.of("cd_heavy", "cd_finisher", "ge_heavy", "ge_lifeshot", "mr_heavy", "mr_kick",
                    "sc_dash", "sc_sweep", "tw_ts_punch"));
        }
        @Override PhaseEntryGameTestSupport.Scene create(String id) { return new Scene(this, id); }
    }

    private static final class Scene extends PhaseEntryGameTestSupport.Scene {
        EntityActionInstance action;
        double initialSpeed, recoverySpeed;
        float savedFinisher, initialStamina, cachedRecovery, recoveryLength;
        int readyTicks;
        boolean started, recoverySeen, freezeObserved;

        Scene(Suite suite, String id) { super(suite, id); }
        boolean finisher() {
            return id.equals("cd_finisher") || id.equals("ge_lifeshot") || id.equals("mr_kick") || id.equals("sc_sweep");
        }
        boolean chariot() { return id.startsWith("sc_"); }
        boolean tsPunch() { return id.equals("tw_ts_punch"); }
        String standName() {
            return id.startsWith("cd_") ? "crazy_diamond" : id.startsWith("ge_") ? "gold_experience"
                    : chariot() ? "silver_chariot" : tsPunch() ? "the_world" : "magicians_red";
        }
        String skill() {
            return switch (id) {
                case "cd_finisher" -> "finisher";
                case "ge_lifeshot" -> "lifeshot";
                case "sc_sweep" -> "sweeping_attack";
                default -> "kick";
            };
        }
        String abilityName() {
            return switch (id) {
                case "cd_finisher" -> "finisher";
                case "ge_lifeshot" -> "lifeshot_punch";
                case "mr_kick" -> "kick";
                case "sc_dash" -> "dash_attack";
                case "sc_sweep" -> "sweeping_attack";
                case "tw_ts_punch" -> "ts_punch";
                default -> "heavy_punch";
            };
        }
        // the heavy punches keep their own snapshot (0 for the TS punch, whose 1.16 recovery passed finisher 0),
        // Silver Chariot's strikes read the Stand's last one as 1.16 did
        float finisherSnapshot() {
            return action instanceof StandEntityHeavyPunch heavy ? heavy.finisherValue : stand.getLastHeavyFinisherValue();
        }

        @Override void prepare() {
            actor(standName(), tsPunch() ? 3 : 1, false);
            cow();
            // low enough that the stamina debuff cuts the Stand's speed once Resolve is gone; the TS punch's time skip
            // could take all of it, so its scene sets it after the press
            power.setStamina(tsPunch() ? power.getMaxStamina() : 350);
            if (tsPunch()) {
                // the TS punch skill and the skill it needs; nothing here stops time
                for (String skill : List.of("time_stop", "ts_punch")) {
                    var result = UNLOCKS.forceUnlock(power, skill);
                    premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                            "Owner-scoped skill " + skill + " prepared");
                }
            }
            if (finisher()) {
                var result = UNLOCKS.forceUnlock(power, skill());
                premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                        "Owner-scoped declared finisher skill " + skill() + " prepared");
            }
            // a Resolve effect sets the Resolve level to its amplifier + 1, and the TS punch asks for level 3
            user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 4, tsPunch() ? 2 : 0, false, false));
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
                // the dash has no windup and pays as its perform starts, inside the press
                initialStamina = power.getStamina();
                var press = press(chariot() ? "dash_attack" : tsPunch() ? "ts_punch" : "heavy_punch", PhaseEntryGameTestSupport.ATTACK,
                        false, 0);
                premise(press.success() && (chariot() ? press.action() instanceof DashStrike || press.action() instanceof SweepingStrike
                        : press.action() instanceof StandEntityHeavyPunch), "Registered heavy input success");
                action = press.action();
                if (tsPunch()) {
                    premise(action instanceof TheWorldTSPunch, "The World's TS punch action");
                    power.setStamina(350);
                    initialStamina = power.getStamina();
                }
                premise(((Ability) action.ability).name().equals(abilityName()), "Actual " + abilityName() + ", got "
                        + ((Ability) action.ability).name());
                if (id.equals("cd_heavy")) premise(action instanceof CrazyDiamondHeavyPunch, "Crazy Diamond heavy action");
                if (id.startsWith("ge_")) premise(action instanceof ToothKnockingHeavyPunch, "Gold Experience heavy action");
                if (id.equals("mr_kick")) premise(action instanceof MagiciansRedKick, "Magician's Red kick action");
                if (id.equals("sc_dash")) premise(action instanceof DashStrike, "Silver Chariot dash action");
                if (id.equals("sc_sweep")) premise(action instanceof SweepingStrike, "Silver Chariot sweep action");
                initialSpeed = stand.getAttackSpeed(); savedFinisher = finisherSnapshot();
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
                        "Resolve expired naturally during WINDUP before RECOVERY; Resolve " + user.getEffect(ModStatusEffects.RESOLVE)
                                + ", speed " + initialSpeed + " -> " + recoverySpeed);
                premise(expected != cachedRecovery, "Changed-speed oracle distinguishes entry from initial cache");
                check(PhaseEntryGameTestSupport.near(recoveryLength, expected),
                        "1.16: RECOVERY read at its start; speed " + initialSpeed + " -> " + recoverySpeed + ", saved finisher "
                                + savedFinisher + ", windup-start reading " + cachedRecovery + ", expected " + expected
                                + ", actual " + recoveryLength);
                check(PhaseEntryGameTestSupport.near(finisherSnapshot(), savedFinisher),
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
