package rotp.addons.killerqueenlion.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.KillerQueenGameTests;
import rotp.addons.killerqueenlion.state.KillerQueenStateService;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 SheerHeart is a non-persistent MISC mob, so MobEntity.checkDespawn removes it in Peaceful, with no player
 * within 128 blocks, and at random when it idles with the nearest player beyond 32 blocks. Its owner counts like any
 * other player; the bomb is never removed because its owner is far away or died (tenth card of 2026-10-14).
 */
@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SheerHeartDespawnGameTests {
    // (799/800)^20000 is about 1e-11: a bomb that may despawn at random is removed within this many checks
    private static final int RANDOM_CHECKS = 20_000;

    private SheerHeartDespawnGameTests() {}

    @GameTest(template = "empty", batch = "kqSheerHeartDespawnNoPlayer", timeoutTicks = 40)
    public static void sheerHeartWithNoPlayerWithin128BlocksIsRemoved(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-lion-despawn-none");
        helper.runAtTickTime(3, () -> fixture.guarded(() -> {
            helper.assertTrue(!fixture.bomb.isRemoved(), "The bomb was removed with its owner beside it");
            fixture.placeAway(fixture.owner, 140.0D);
        }));
        helper.runAtTickTime(10, () -> {
            fixture.guarded(() -> helper.assertTrue(fixture.bomb.isRemoved(),
                    "A Sheer Heart Attack with no player within 128 blocks stayed, as a persistent mob does"));
            fixture.cleanup();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqSheerHeartDespawnPeaceful", timeoutTicks = 40)
    public static void sheerHeartIsRemovedInPeaceful(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-lion-despawn-peaceful");
        helper.runAtTickTime(3, () -> fixture.guarded(() -> {
            helper.assertTrue(!fixture.bomb.isRemoved(), "The bomb was removed outside Peaceful");
            fixture.level().getServer().setDifficulty(Difficulty.PEACEFUL, true);
        }));
        helper.runAtTickTime(10, () -> {
            fixture.guarded(() -> helper.assertTrue(fixture.bomb.isRemoved(),
                    "A Sheer Heart Attack stayed in Peaceful, as a mob that does not despawn there does"));
            fixture.cleanup();
            helper.succeed();
        });
    }

    // The port once kept the bomb from despawning and saved that as Mob's PersistenceRequired flag; an old save's bomb
    // loses it, so that the 1.16 despawn rules above apply, while a name-tagged one keeps it as any mob does
    @GameTest(template = "empty", batch = "kqSheerHeartOldSaveFlag", timeoutTicks = 40)
    public static void anOldSavesBombLosesItsNeverDespawnFlagWhenItLoadsAsIn116(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-lion-despawn-oldsave");
        helper.runAtTickTime(3, () -> {
            fixture.guarded(() -> {
                SheerHeartAttackEntity bomb = fixture.bomb;
                bomb.setPersistenceRequired();
                net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
                bomb.saveWithoutId(saved);
                helper.assertTrue(saved.getBoolean("PersistenceRequired") && !saved.contains("CustomName"),
                        "fixture: the saved bomb does not carry the flag, or carries a name");
                bomb.load(saved);
                helper.assertTrue(!bomb.isPersistenceRequired(),
                        "A bomb loaded from an old save kept the flag that stops it despawning; 1.16's never had it");
                bomb.setPersistenceRequired();
                bomb.setCustomName(Component.literal("Heart"));
                saved = new net.minecraft.nbt.CompoundTag();
                bomb.saveWithoutId(saved);
                bomb.load(saved);
                helper.assertTrue(bomb.isPersistenceRequired(),
                        "A name-tagged bomb lost its persistence on loading, which a name tag gives any mob");
            });
            fixture.cleanup();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqSheerHeartDespawnNear", timeoutTicks = 80)
    public static void sheerHeartStaysWithAPlayerCloseBy(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-lion-despawn-near");
        helper.runAtTickTime(40, () -> fixture.guarded(() -> {
            helper.assertTrue(!fixture.bomb.isRemoved(), "The bomb was removed with its owner beside it");
            // Idle for long, a player within 32 blocks still resets it and never lets it despawn
            for (int check = 0; check < RANDOM_CHECKS; check++) {
                fixture.bomb.setNoActionTime(700);
                fixture.bomb.checkDespawn();
                helper.assertTrue(!fixture.bomb.isRemoved(), "An idle bomb despawned with a player within 32 blocks");
            }
            helper.assertTrue(fixture.bomb.getNoActionTime() == 0,
                    "A player within 32 blocks did not reset the idle time: " + fixture.bomb.getNoActionTime());
            fixture.placeAway(fixture.owner, 120.0D);
        }));
        helper.runAtTickTime(60, () -> {
            fixture.guarded(() -> helper.assertTrue(!fixture.bomb.isRemoved(),
                    "The bomb was removed with a player 120 blocks away, inside the 128-block despawn range"));
            fixture.cleanup();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqSheerHeartDespawnOwnerGone", timeoutTicks = 80)
    public static void sheerHeartStaysWithItsOwnerFarOrDeadWhileAPlayerIsCloseBy(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-lion-despawn-gone");
        ServerPlayer bystander = fixture.bystander("kq-lion-despawn-bystander", 6.0D);
        helper.runAtTickTime(3, () -> fixture.guarded(() -> fixture.placeAway(fixture.owner, 140.0D)));
        helper.runAtTickTime(15, () -> fixture.guarded(() -> {
            helper.assertTrue(!fixture.bomb.isRemoved(), "The bomb was removed for its owner 140 blocks away");
            fixture.owner.setGameMode(GameType.SURVIVAL);
            fixture.owner.kill();
            helper.assertTrue(fixture.owner.isDeadOrDying(), "fixture: the owner did not die");
        }));
        helper.runAtTickTime(30, () -> fixture.guarded(() -> {
            helper.assertTrue(!fixture.bomb.isRemoved(), "The bomb was removed for its owner's death");
            // Only the player close by kept it: with nobody near, the dead and far owner changes nothing
            fixture.placeAway(bystander, 140.0D);
        }));
        helper.runAtTickTime(36, () -> {
            fixture.guarded(() -> helper.assertTrue(fixture.bomb.isRemoved(),
                    "The bomb stayed with no player within 128 blocks, its owner dead and far"));
            fixture.cleanup();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "kqSheerHeartDespawnIdle", timeoutTicks = 40)
    public static void anIdleSheerHeartBeyond32BlocksDespawnsAtRandomLikeAnyMiscMob(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-lion-despawn-idle");
        helper.runAtTickTime(3, () -> {
            fixture.guarded(() -> {
                SheerHeartAttackEntity bomb = fixture.bomb;
                helper.assertTrue(bomb.getType().getCategory() == MobCategory.MISC
                                && bomb.getType().getCategory().getDespawnDistance() == 128
                                && bomb.getType().getCategory().getNoDespawnDistance() == 32,
                        "The bomb is not a MISC mob with the 128 and 32 block despawn distances");
                helper.assertTrue(!bomb.isPersistenceRequired() && !bomb.requiresCustomPersistence()
                                && bomb.removeWhenFarAway(100.0D * 100.0D),
                        "The bomb is persistent or refuses to despawn when far away");
                fixture.placeAway(fixture.owner, 100.0D);
                // Not idle: no despawn however often it is checked
                for (int check = 0; check < RANDOM_CHECKS; check++) {
                    bomb.setNoActionTime(0);
                    bomb.checkDespawn();
                    helper.assertTrue(!bomb.isRemoved(), "A bomb that was not idle despawned at 100 blocks");
                }
                int checks = 0;
                while (!bomb.isRemoved() && checks < RANDOM_CHECKS) {
                    bomb.setNoActionTime(700);
                    bomb.checkDespawn();
                    checks++;
                }
                helper.assertTrue(bomb.isRemoved(),
                        "An idle bomb with the nearest player 100 blocks away never despawned in " + checks
                                + " checks");
            });
            fixture.cleanup();
            helper.succeed();
        });
    }

    // Also a listener, so that a failure or a timeout outside the guarded steps lets the players go
    private static final class Fixture implements GameTestListener {
        private final GameTestHelper helper;
        private final Difficulty oldDifficulty;
        private final List<ServerPlayer> bystanders = new ArrayList<>();
        private ServerPlayer owner;
        private SheerHeartAttackEntity bomb;
        private boolean finished;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.oldDifficulty = helper.getLevel().getDifficulty();
        }

        private static Fixture create(GameTestHelper helper, String ownerName) {
            Fixture fixture = new Fixture(helper);
            helper.testInfo.addListener(fixture);
            try {
                fixture.level().getServer().setDifficulty(Difficulty.NORMAL, true);
                BlockPos pad = KillerQueenGameTests.sheerHeartPad(helper, true);
                fixture.owner = KillerQueenGameTests.makeCompatibleMockPlayer(helper, ownerName, pad);
                fixture.owner.setGameMode(GameType.CREATIVE);
                StandPower power = StandPower.get(fixture.owner);
                helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
                power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
                power.setStamina(power.getMaxStamina());
                helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(fixture.owner, power),
                        "fixture: Killer Queen could not be summoned");
                StandEntity stand = power.getSummonedStandEntity();
                helper.assertTrue(stand != null && stand.isAddedToLevel(),
                        "fixture: Killer Queen was not added to the level");
                stand.summonLockTicks = 0;
                fixture.bomb = KillerQueenLionAddon.SHEER_HEART_ENTITY.get().create(fixture.level());
                helper.assertTrue(fixture.bomb != null, "fixture: no Sheer Heart Attack");
                fixture.bomb.bind(fixture.owner, stand, null);
                fixture.bomb.moveTo(pad.getX() + 3.5D, pad.getY(), pad.getZ() + 0.5D, -90.0F, 0.0F);
                helper.assertTrue(!fixture.bomb.isRemoved() && fixture.level().addFreshEntity(fixture.bomb)
                                && KillerQueenStateService.linkSheerHeart(fixture.owner, fixture.bomb),
                        "fixture: the Sheer Heart Attack was rejected");
                return fixture;
            }
            catch (RuntimeException | Error failure) {
                fixture.cleanup();
                throw failure;
            }
        }

        private ServerLevel level() {
            return helper.getLevel();
        }

        // A Creative player beside the pad, which the bomb does not take as a target
        private ServerPlayer bystander(String name, double dz) {
            try {
                BlockPos pad = bomb.blockPosition();
                ServerPlayer bystander = KillerQueenGameTests.makeCompatibleMockPlayer(
                        helper, name, pad.offset(0, 0, (int) dz));
                bystander.setGameMode(GameType.CREATIVE);
                bystanders.add(bystander);
                return bystander;
            }
            catch (RuntimeException | Error failure) {
                cleanup();
                throw failure;
            }
        }

        private void placeAway(ServerPlayer player, double dx) {
            player.moveTo(bomb.getX() + dx, bomb.getY(), bomb.getZ(), 0.0F, 0.0F);
        }

        private void guarded(Runnable step) {
            try {
                step.run();
            }
            catch (RuntimeException | Error failure) {
                cleanup();
                throw failure;
            }
        }

        // From the test runner: the test has already passed or failed, and its own result is the one reported
        private void cleanupQuietly() {
            try {
                cleanup();
            }
            catch (RuntimeException ignored) {
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {}

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            cleanupQuietly();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            cleanupQuietly();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
            cleanupQuietly();
        }

        private void cleanup() {
            if (finished) {
                return;
            }
            finished = true;
            if (bomb != null) {
                bomb.discard();
            }
            if (owner != null) {
                StandPower power = StandPower.get(owner);
                StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
                if (stand != null) {
                    stand.discard();
                }
                KillerQueenStateService.clearAll(owner);
                owner.connection.disconnect(Component.literal("Killer Queen SHA despawn GameTest complete"));
            }
            for (ServerPlayer bystander : bystanders) {
                bystander.connection.disconnect(Component.literal("Killer Queen SHA despawn GameTest complete"));
            }
            KillerQueenGameTests.sheerHeartPad(helper, false);
            level().getServer().setDifficulty(oldDifficulty, true);
        }
    }
}
