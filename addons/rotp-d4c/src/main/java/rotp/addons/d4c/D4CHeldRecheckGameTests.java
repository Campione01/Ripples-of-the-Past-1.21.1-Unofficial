package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.ability.D4CPhase2BAbility;
import rotp.addons.d4c.entity.D4CAnnihilationCloneEntity;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import rotp.addons.d4c.travel.D4CDimensionTravelService;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

/**
 * 1.16 PowerBaseImpl.tickHeldAction (PowerBaseImpl.java:357-385) ran checkRequirements, and with it the action's own
 * conditions, on every tick of a held action: annihilation's checkStandConditions (aa/aa.java:63-72: under 300 stamina
 * with the Stand out, or no marked target), love_train's (aa/ae.java:78-89: no Complete Saint Corpse), pullin's
 * checkConditions (aa/aj.java:67-73: no target within 4 blocks; it replaced StandAction.checkConditions, so it asked
 * for no Stand part) and parallel_world_travel's (aa/ai.java:107-132: not caught between blocks). A failed check ended
 * the hold with its message and fired nothing. summon_player_minion (aa/ak.java) had no condition of its own.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CHeldRecheckGameTests {
    private D4CHeldRecheckGameTests() {}

    @GameTest(template = "empty", batch = "d4cHeldRecheckAnnihilation", timeoutTicks = 80)
    public static void annihilationHoldStopsOnceItsMarkIsGoneOrStaminaIsUnder300(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (boolean byStamina : new boolean[] {false, true}) {
            String what = byStamina ? "stamina 299" : "mark gone";
            try (D4CGameTestScene scene = new D4CGameTestScene(
                    helper, "d4c-held-annihilation-" + (byStamina ? "st" : "mk"))) {
                Cow cow = scene.cow(new Vec3(0.0D, 0.0D, 6.0D));
                scene.owner.getPersistentData().putUUID(D4CGameTestScene.ANNIHILATION_TARGET, cow.getUUID());
                EntityActionInstance held = scene.pressAndHold("annihilation", (short) (byStamina ? 212 : 211));
                if (held == null) {
                    wrong.add(what + ": " + scene.fixtureFailure);
                    continue;
                }
                if (byStamina) {
                    scene.power.setStamina(299.0F);
                }
                else {
                    // gone from the level: 1.16's lookup (al/aa.java:142-160) found nothing; a dying cow it still found
                    cow.discard();
                }
                float stamina = scene.power.getStamina();
                scene.tick();
                if (scene.held(held)) {
                    wrong.add(what + ": annihilation is still held (phase " + held.getPhase() + ")");
                }
                if (scene.power.getStamina() < stamina) {
                    wrong.add(what + ": stamina was paid, " + stamina + " -> " + scene.power.getStamina());
                }
                if (!scene.around(D4CAnnihilationCloneEntity.class).isEmpty()) {
                    wrong.add(what + ": an annihilation clone was spawned");
                }
                if (byStamina && !scene.keys().isEmpty()) {
                    wrong.add(what + ": 1.16 refused under 300 stamina without a message; sent " + scene.keys());
                }
                if (!byStamina && !scene.keys().equals(List.of(D4CPhase2BService.MSG_ANNIHILATION_NEED_MARK))) {
                    wrong.add(what + ": 1.16 ended the hold with 'annihilation_need_mark'; sent " + scene.keys());
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 re-ran annihilation's checkStandConditions (aa/aa.java:63-72) on every held tick: " + wrong);
        helper.succeed();
    }

    /**
     * 1.16 read the mark by its UUID in the user's world (al/aa.java ab(user) :81-91, aa(world, uuid) :142-160) and
     * asked nothing about its health: a marked target that had died but still lay in the level (until it was removed
     * after 20 ticks, or the 20-tick check dropped the mark) passed annihilation's checkStandConditions at the press and
     * on every held tick (aa/aa.java:63-72), and its standPerform (:19-31) spawned the clone and took 300 stamina.
     */
    @GameTest(template = "empty", batch = "d4cAnnihilationDyingMark", timeoutTicks = 80)
    public static void annihilationAtADyingMarkedTargetSpawnsTheCloneAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (boolean diesInHold : new boolean[] {false, true}) {
            String what = diesInHold ? "the cow dies during the hold" : "the cow is dying at the press";
            try (D4CGameTestScene scene = new D4CGameTestScene(
                    helper, "d4c-dying-mark-" + (diesInHold ? "hold" : "press"))) {
                Cow cow = scene.cow(new Vec3(0.0D, 0.0D, 6.0D));
                scene.owner.getPersistentData().putUUID(D4CGameTestScene.ANNIHILATION_TARGET, cow.getUUID());
                if (!diesInHold) {
                    cow.kill();
                }
                EntityActionInstance held = scene.pressAndHold("annihilation", (short) (diesInHold ? 214 : 213));
                if (held == null) {
                    wrong.add(what + ": " + scene.fixtureFailure);
                    continue;
                }
                if (diesInHold) {
                    cow.kill();
                }
                if (cow.isAlive() || cow.isRemoved()) {
                    wrong.add(what + ": fixture, the cow is not dying (alive " + cow.isAlive() + ", removed "
                            + cow.isRemoved() + ")");
                    continue;
                }
                float stamina = scene.power.getStamina();
                for (int tick = 0; tick < 30 && !held.isOver()
                        && scene.around(D4CAnnihilationCloneEntity.class).isEmpty(); tick++) {
                    scene.tick();
                }
                if (scene.around(D4CAnnihilationCloneEntity.class).isEmpty()) {
                    wrong.add(what + ": no clone was spawned (phase " + held.getPhase() + ", messages "
                            + scene.keys() + ")");
                }
                if (scene.power.getStamina() > stamina - 300.0F) {
                    wrong.add(what + ": 300 stamina was not paid, " + stamina + " -> " + scene.power.getStamina());
                }
                if (scene.owner.getPersistentData().hasUUID(D4CGameTestScene.ANNIHILATION_TARGET)) {
                    wrong.add(what + ": the mark stayed after the clone");
                }
                if (!scene.keys().isEmpty()) {
                    wrong.add(what + ": messages " + scene.keys());
                }
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 annihilation took a dying marked target (aa/aa.java:63-72, :19-31): "
                + wrong);
        helper.succeed();
    }

    /**
     * 1.16 aa/aa.java:69-71: annihilation pressed with no marked target was refused with conditionMessage
     * 'annihilation_need_mark' (jojo.message.action_condition.annihilation_need_mark, "Need to mark a target first").
     */
    @GameTest(template = "empty", batch = "d4cAnnihilationNeedMark", timeoutTicks = 80)
    public static void annihilationWithoutAMarkIsRefusedWithItsMessage(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-annihilation-need-mark")) {
            scene.cow(new Vec3(0.0D, 0.0D, 6.0D));
            float stamina = scene.power.getStamina();
            EntityActionInstance held = scene.pressAndHold("annihilation", (short) 215);
            helper.assertTrue(held == null && scene.keys().equals(List.of(D4CPhase2BService.MSG_ANNIHILATION_NEED_MARK))
                            && scene.power.getStamina() == stamina
                            && scene.around(D4CAnnihilationCloneEntity.class).isEmpty(),
                    "1.16 refused annihilation without a marked target with 'annihilation_need_mark'; the port "
                            + (held != null ? "started it" : "refused it") + ", messages " + scene.keys()
                            + ", stamina " + stamina + " -> " + scene.power.getStamina());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "d4cHeldRecheckPullIn", timeoutTicks = 80)
    public static void pullInHoldStopsOnceNoTargetIsWithinFourBlocks(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-held-pullin")) {
            Cow cow = scene.cow(new Vec3(0.0D, 0.6D, 2.5D));
            helper.assertTrue(D4CPhase2BService.findPullInTarget(scene.owner) == cow,
                    "fixture: the pull-in ray does not find the cow: " + D4CPhase2BService.findPullInTarget(scene.owner));
            EntityActionInstance held = scene.pressAndHold("pullin_parallel_world_travel", (short) 221);
            helper.assertTrue(held != null, "fixture: " + scene.fixtureFailure);
            cow.moveTo(cow.position().add(0.0D, 0.0D, 4.0D));
            helper.assertTrue(D4CPhase2BService.findPullInTarget(scene.owner) == null,
                    "fixture: the pull-in ray still finds " + D4CPhase2BService.findPullInTarget(scene.owner));
            scene.tick();
            helper.assertTrue(!scene.held(held) && scene.keys().contains(D4CPhase2BService.MSG_TARGET_TOO_FAR)
                            && scene.around(rotp.addons.d4c.entity.D4CMarkerEntity.class).isEmpty(),
                    "1.16 re-ran pullin's checkConditions (aa/aj.java:67-73) on every held tick: once no target was "
                            + "within 4 blocks the hold ended with 'target_too_far' and nothing was pulled; held "
                            + scene.held(held) + " (phase " + held.getPhase() + "), messages " + scene.keys());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "d4cHeldRecheckPullInArms", timeoutTicks = 80)
    public static void pullInNeedsNoStandArms(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-held-pullin-arms")) {
            scene.cow(new Vec3(0.0D, 0.6D, 2.5D));
            StandInstance instance = scene.power.getStandInstance().orElse(null);
            helper.assertTrue(instance != null && instance.removePart(StandPart.ARMS),
                    "fixture: the Stand's arms could not be removed");
            try {
                EntityActionInstance held = scene.pressAndHold("pullin_parallel_world_travel", (short) 231);
                helper.assertTrue(held != null,
                        "1.16 pullin replaced StandAction.checkConditions (aa/aj.java:67-73), so it asked for no Stand "
                                + "arms at the press or on a held tick: " + scene.fixtureFailure);
                scene.tick();
                helper.assertTrue(scene.held(held),
                        "1.16 pullin asked for no Stand arms on a held tick; it stopped (phase " + held.getPhase()
                                + ", messages " + scene.keys() + ")");
            }
            finally {
                instance.addPart(StandPart.ARMS);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "d4cHeldRecheckLoveTrain", timeoutTicks = 80)
    public static void loveTrainHoldStopsOnceTheCorpseIsGone(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-held-love-train", true)) {
            scene.owner.getInventory().setItem(0, new ItemStack(D4CAddon.COMPLETE_SAINT_CORPSE.get()));
            EntityActionInstance held = scene.pressAndHold("love_train", (short) 241);
            helper.assertTrue(held != null, "fixture: " + scene.fixtureFailure);
            scene.owner.getInventory().setItem(0, ItemStack.EMPTY);
            scene.tick();
            helper.assertTrue(!scene.held(held)
                            && scene.keys().contains(D4CPhase2BService.MSG_LOVE_TRAIN_REQUIRES_CORPSE)
                            && !scene.owner.hasEffect(D4CAddon.LOVE_TRAIN),
                    "1.16 re-ran love_train's checkSpecificConditions (aa/ae.java:78-89) on every held tick: without "
                            + "the Complete Saint Corpse the hold ended with 'love_train_requires_corpse'; held "
                            + scene.held(held) + " (phase " + held.getPhase() + "), messages " + scene.keys());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "d4cHeldRecheckTravel", timeoutTicks = 80)
    public static void parallelWorldTravelHoldStopsOnceNoLongerCaughtBetween(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-held-travel")) {
            BlockPos feet = scene.owner.blockPosition();
            List<BlockPos> walls = List.of(feet.east(), feet.west());
            try {
                walls.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
                helper.assertTrue(D4CDimensionTravelService.isCaughtBetween(helper.getLevel(), scene.owner),
                        "fixture: the user is not caught between the two stone blocks");
                // the game-test server has no Parallel World level
                scene.owner.getPersistentData().putString("rotp_d4c.phase2a.selected_dimension",
                        D4CDimensionTargets.NETHER.toString());
                EntityActionInstance held = scene.pressAndHold(D4CAddon.PARALLEL_WORLD_TRAVEL_NAME, (short) 251);
                helper.assertTrue(held != null, "fixture: " + scene.fixtureFailure);
                helper.getLevel().setBlockAndUpdate(feet.east(), Blocks.AIR.defaultBlockState());
                helper.assertTrue(!D4CDimensionTravelService.isCaughtBetween(helper.getLevel(), scene.owner),
                        "fixture: the user is still caught between blocks");
                scene.tick();
                helper.assertTrue(!scene.held(held)
                                && scene.keys().contains("rotp_d4c.message.not_caught_between"),
                        "1.16 re-ran parallel_world_travel's checkSpecificConditions (aa/ai.java:107-132) on every "
                                + "held tick: once the user was no longer caught between blocks the hold ended with "
                                + "its message; held " + scene.held(held) + " (phase " + held.getPhase()
                                + "), messages " + scene.keys());
            }
            finally {
                walls.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "d4cHeldRecheckMinion", timeoutTicks = 80)
    public static void summonMinionHoldIsNotRecheckedAgainstTheMinionCount(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-held-minion")) {
            for (int i = 0; i < D4CPhase2BSpec.MAX_MINIONS - 1; i++) {
                scene.minion(new Vec3(-3.0D, 0.0D, i));
            }
            EntityActionInstance held = scene.pressAndHold("summon_player_minion", (short) 261);
            helper.assertTrue(held != null, "fixture: " + scene.fixtureFailure);
            scene.minion(new Vec3(-3.0D, 0.0D, D4CPhase2BSpec.MAX_MINIONS));
            helper.assertTrue(!D4CPhase2BService.canExecute(D4CPhase2BAbility.Mode.SUMMON_MINION, scene.owner),
                    "fixture: the user does not own the most minions yet");
            scene.tick();
            helper.assertTrue(scene.held(held) && scene.keys().isEmpty(),
                    "1.16 summon_player_minion (aa/ak.java) had no condition of its own to recheck while held; the "
                            + "hold stopped once the user owned " + D4CPhase2BSpec.MAX_MINIONS + " minions (phase "
                            + held.getPhase() + ", messages " + scene.keys() + ")");
            helper.succeed();
        }
    }
}
