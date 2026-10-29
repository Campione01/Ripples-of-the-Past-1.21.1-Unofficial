package rotp.addons.stand_bearing_creatures.gametest;

import java.util.List;

import rotp.core.init.ModSpecialActions;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

/**
 * 1.16 unlocked a Stand user's actions by Resolve alone
 * (StandAction.canBeUnlocked): a mob of The World stopped time from Resolve
 * 2 (ModStandsInit resolveLevelToUnlock(2)). The core's Resolve unlock does
 * the same for mobs. The add-on unlocks nothing itself: a forced skill flag
 * opened abilities gated on the flag alone (Reworked's time stop).
 */
@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobSkillUnlockGameTests {
    private static final String TIME_STOP = "time_stop";

    private MobSkillUnlockGameTests() {}

    @GameTest(
            template = "mob_skill_fixture",
            batch = "mob_skill_unlock",
            timeoutTicks = 200)
    public static void theWorldMobAtResolveTwoCanStopTime(
            GameTestHelper helper) {
        Mob mob = theWorldMob(helper, 2);
        StandPower power = StandPower.get(mob);
        helper.succeedWhen(() -> {
            requireTicked(helper, mob);
            PowerData data = power.getCurTypeData();
            helper.assertTrue(
                    data != null && data.isSkillUnlocked(TIME_STOP),
                    "The core did not unlock The World's time stop "
                            + "skill for a mob at Resolve 2.");
            helper.assertTrue(
                    power.isAbilityUnlocked(TIME_STOP),
                    "The World's time stop is locked for a mob at "
                            + "Resolve 2.");
            List<String> available = availableNames(power);
            helper.assertTrue(
                    available.contains(TIME_STOP),
                    "A mob of The World at Resolve 2 cannot pick its "
                            + "time stop. Available: " + available);
            mob.discard();
        });
    }

    @GameTest(
            template = "mob_skill_fixture",
            batch = "mob_skill_unlock",
            timeoutTicks = 200)
    public static void theWorldMobAtResolveZeroCannotStopTime(
            GameTestHelper helper) {
        Mob mob = theWorldMob(helper, 0);
        StandPower power = StandPower.get(mob);
        helper.succeedWhen(() -> {
            requireTicked(helper, mob);
            helper.assertTrue(
                    power.isAbilityUnlocked("punch"),
                    "A mob of The World lost its starting punch.");
            // Reworked's time stop is open whenever this flag is set.
            PowerData data = power.getCurTypeData();
            helper.assertFalse(
                    data == null || data.isSkillUnlocked(TIME_STOP),
                    "The World's time stop skill is unlocked for a mob "
                            + "at Resolve 0.");
            helper.assertFalse(
                    power.isAbilityUnlocked(TIME_STOP),
                    "The World's time stop is open to a mob at "
                            + "Resolve 0.");
            List<String> available = availableNames(power);
            helper.assertFalse(
                    available.contains(TIME_STOP),
                    "A mob of The World at Resolve 0 can pick its time "
                            + "stop. Available: " + available);
            mob.discard();
        });
    }

    // 1.16 050 SBC: '/rotp_mwp summon' toggles a mob's Stand exactly like
    // the user's own summon/unsummon (Debug Stand's Toggle Summon), which
    // retracts gracefully instead of vanishing at once.
    @GameTest(
            template = "mob_skill_fixture",
            batch = "mob_skill_unlock",
            timeoutTicks = 200)
    public static void summonCommandGracefullyUnsummonsAMobsStand(
            GameTestHelper helper) {
        Mob mob = theWorldMob(helper, 2);
        StandPower power = StandPower.get(mob);
        var logger = com.mojang.logging.LogUtils.getLogger();
        java.util.function.Consumer<String> logState = stage -> {
            StandEntity stand = power.getSummonedStandEntity();
            EntityActionInstance action = stand == null ? null : stand.getCurStandAction();
            String mobState = "pos=" + mob.position()
                    + ", chunk=" + mob.chunkPosition()
                    + ", ticks=" + mob.tickCount
                    + ", entityTicking=" + helper.getLevel().isPositionEntityTicking(mob.blockPosition())
                    + ", bodyYaw=" + mob.yBodyRot
                    + ", target=" + mob.getTarget();
            String standState = stand == null ? "absent" : "pos=" + stand.position()
                    + ", chunk=" + stand.chunkPosition()
                    + ", ticks=" + stand.tickCount
                    + ", entityTicking=" + helper.getLevel().isPositionEntityTicking(stand.blockPosition())
                    + ", summonLockTicks=" + stand.summonLockTicks
                    + ", following=" + stand.isFollowingUser()
                    + ", retracting=" + stand.isBeingRetracted()
                    + ", closeToUser=" + stand.isCloseToUser()
                    + ", alive=" + stand.isAlive()
                    + ", removed=" + stand.isRemoved();
            String actionState = action == null ? "none" : "ability=" + action.ability
                    + ", phase=" + action.getPhase()
                    + ", phaseTick=" + action.getPhaseTick()
                    + ", phaseLength=" + action.getCurPhaseLength();
            logger.info("SBC summon control {}: summoned={}, mob=[{}], stand=[{}], action=[{}]",
                    stage, power.isSummoned(), mobState, standState, actionState);
        };
        helper.runAfterDelay(2, () -> {
            requireTicked(helper, mob);
            runSummonCommand(helper, mob);
            logState.accept("AFTER_FIRST_SUMMON");
            helper.assertTrue(
                    power.isSummoned(),
                    "The first /rotp_mwp summon did not summon the "
                            + "mob's Stand.");
            helper.runAfterDelay(2, () -> {
                logState.accept("BEFORE_SECOND_SUMMON");
                runSummonCommand(helper, mob);
                logState.accept("AFTER_SECOND_SUMMON");
                StandEntity standEntity = power.getSummonedStandEntity();
                helper.assertTrue(
                        standEntity != null && standEntity.isAlive(),
                        "The Stand entity vanished at once when the "
                                + "second /rotp_mwp summon ran; it should "
                                + "retract gracefully instead.");
                // 1.16 retractStand(true): a Stand following its user only
                // starts the unsummon task; BEING_RETRACTED is set only
                // for a Stand away from its user.
                EntityActionInstance curAction =
                        standEntity.getCurStandAction();
                helper.assertTrue(
                        curAction != null
                                && curAction.ability
                                        == ModSpecialActions
                                                .STAND_UNSUMMON.get(),
                        "The second /rotp_mwp summon did not start the "
                                + "Stand's graceful unsummon. Action: "
                                + (curAction == null
                                        ? "none"
                                        : curAction.ability)
                                + ", retracting="
                                + standEntity.isBeingRetracted());
                helper.runAfterDelay(40, () -> {
                    logState.accept("AFTER_RETRACT_WAIT");
                    StandEntity afterWait =
                            power.getSummonedStandEntity();
                    helper.assertTrue(
                            !power.isSummoned()
                                    || afterWait == null
                                    || !afterWait.isAlive(),
                            "The Stand did not finish retracting within "
                                    + "40 ticks of the second "
                                    + "/rotp_mwp summon.");
                    mob.discard();
                    helper.succeed();
                });
            });
        });
    }

    private static void runSummonCommand(GameTestHelper helper, Mob mob) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack()
                .withPosition(mob.position());
        server.getCommands().performPrefixedCommand(
                source, "rotp_mwp summon @e[type=husk,distance=..1]");
    }

    private static Mob theWorldMob(
            GameTestHelper helper, int resolveLevel) {
        // Keep both the mob and its rotated Stand offset inside the ready template.
        Mob mob = helper.spawnWithNoFreeWill(
                EntityType.HUSK, new BlockPos(1, 2, 1));
        mob.setNoGravity(true);
        helper.assertTrue(
                MobPowerUtil.grantStand(
                        mob, ModStands.THE_WORLD.get(), true),
                "Could not give the mob The World.");
        StandPower power = StandPower.get(mob);
        helper.assertTrue(
                power != null
                        && power.getPowerType()
                                == ModStands.THE_WORLD.get(),
                "The mob has no The World power.");
        power.setResolveLevel(resolveLevel);
        helper.assertTrue(
                power.getResolveLevel() == resolveLevel,
                "The mob's Resolve level is "
                        + power.getResolveLevel()
                        + ", expected "
                        + resolveLevel);
        power.setStamina(power.getMaxStamina());
        return mob;
    }

    // The available moves are cached until the power's next tick, which
    // runs in the mob's; its chunk may start entity-ticking late.
    private static void requireTicked(GameTestHelper helper, Mob mob) {
        helper.assertTrue(
                mob.tickCount >= 2,
                "The mob has not ticked since the setup. tickCount="
                        + mob.tickCount
                        + ", entityTicking="
                        + helper.getLevel().isPositionEntityTicking(
                                mob.blockPosition()));
    }

    private static List<String> availableNames(StandPower power) {
        return MobPowerUtil.availableMobAbilities(power)
                .stream()
                .map(Ability::name)
                .sorted()
                .toList();
    }
}
