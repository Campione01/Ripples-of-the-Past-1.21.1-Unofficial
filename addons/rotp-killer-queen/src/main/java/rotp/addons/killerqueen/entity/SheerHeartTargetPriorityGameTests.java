package rotp.addons.killerqueen.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.killerqueen.KillerQueenGameTests;
import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 SheerHeart target selectors: players (1), the owner's attacker and the owner's victim (2), monsters (3).
 * Nothing else is hunted unprovoked. Every fixture stays more than three blocks from the bomb, so it never swells.
 */
@GameTestHolder(KillerQueenAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SheerHeartTargetPriorityGameTests {
    private SheerHeartTargetPriorityGameTests() {}

    @GameTest(template = "empty", batch = "r764_sha_target_order", timeoutTicks = 600)
    public static void automaticTargetIsPlayerThenMonsterAndNeverAnUnprovokedAnimal(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-sha-order");
        try {
            SheerHeartAttackEntity sheer = fixture.sheer;
            // The owner is the nearest player and is in Survival: only the owner rule keeps the bomb off them.
            fixture.owner.setGameMode(GameType.SURVIVAL);
            Cow cow = fixture.cow(0.0D, 4.0D);
            Creeper monster = fixture.monster(0.0D, -5.0D);
            ServerPlayer rival = fixture.player("kq-sha-rival", 4.0D, 5.0D);
            rival.setGameMode(GameType.SURVIVAL);
            int[] phase = { 0 };
            int[] held = { 0 };
            int[] waited = { 0 };
            helper.onEachTick(() -> {
                if (fixture.finished) {
                    return;
                }
                try {
                    LivingEntity target = sheer.getTarget();
                    helper.assertTrue(sheer.isAlive() && sheer.swellTicks() == 0,
                            "The target-order fixture swelled or lost its bomb");
                    helper.assertTrue(target != cow,
                            "Sheer Heart Attack hunts an unprovoked cow (phase " + phase[0] + ")");
                    helper.assertTrue(target != fixture.owner, "Sheer Heart Attack hunts its own user");
                    waited[0]++;
                    if (phase[0] == 0) {
                        if (target == rival) {
                            if (++held[0] >= 40) {
                                rival.setGameMode(GameType.CREATIVE);
                                next(phase, held, waited);
                            }
                        }
                        else {
                            helper.assertTrue(held[0] == 0,
                                    "A monster or nothing replaced the player target: " + target);
                        }
                    }
                    else if (phase[0] == 1) {
                        helper.assertTrue(target != rival || waited[0] <= 4,
                                "A Creative player stayed the target");
                        if (target == monster) {
                            if (++held[0] >= 20) {
                                monster.discard();
                                next(phase, held, waited);
                            }
                        }
                        else {
                            helper.assertTrue(held[0] == 0, "The monster target was dropped: " + target);
                        }
                    }
                    else {
                        helper.assertTrue(target == null || (target == monster && waited[0] <= 4),
                                "With only a cow in sight the bomb still has a target: " + target);
                        if (waited[0] >= 60) {
                            helper.assertTrue(sheer.getAttributeValue(Attributes.FOLLOW_RANGE) == 16.0D,
                                    "Automatic targets are searched within the 1.16 follow range of 16, not "
                                            + sheer.getAttributeValue(Attributes.FOLLOW_RANGE));
                            fixture.cleanup();
                            helper.succeed();
                        }
                    }
                }
                catch (RuntimeException | Error failure) {
                    fixture.cleanup();
                    throw failure;
                }
            });
            helper.runAfterDelay(580, () -> {
                if (!fixture.finished) {
                    fixture.cleanup();
                    helper.fail("Target order stopped in phase " + phase[0] + " with target " + sheer.getTarget()
                            + " (0 = waiting for the player, 1 = waiting for the monster)");
                }
            });
        }
        catch (RuntimeException | Error failure) {
            fixture.cleanup();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "r764_sha_owner_combat", timeoutTicks = 600)
    public static void ownerCombatProvokesAnAnimalAndAPlayerStillComesFirst(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-sha-combat");
        try {
            SheerHeartAttackEntity sheer = fixture.sheer;
            ServerPlayer owner = fixture.owner;
            Cow attacker = fixture.cow(0.0D, 4.0D);
            Cow victim = fixture.cow(0.0D, -4.0D);
            ServerPlayer rival = fixture.player("kq-sha-late-rival", 4.0D, 5.0D);
            rival.setGameMode(GameType.CREATIVE);
            // A mock player is not ticked; the vanilla timestamps compare against a goal's initial 0.
            owner.tickCount = 100;
            int[] phase = { 0 };
            int[] held = { 0 };
            int[] waited = { 0 };
            helper.onEachTick(() -> {
                if (fixture.finished) {
                    return;
                }
                try {
                    LivingEntity target = sheer.getTarget();
                    helper.assertTrue(sheer.isAlive() && sheer.swellTicks() == 0,
                            "The owner-combat fixture swelled or lost its bomb");
                    waited[0]++;
                    if (phase[0] == 0) {
                        helper.assertTrue(target == null,
                                "Sheer Heart Attack hunts an animal nobody fought: " + target);
                        if (waited[0] >= 40) {
                            owner.setLastHurtByMob(attacker);
                            next(phase, held, waited);
                        }
                    }
                    else if (phase[0] == 1) {
                        helper.assertTrue(target == null || target == attacker,
                                "The owner's attacker is not the target: " + target);
                        helper.assertTrue(target == attacker || (held[0] == 0 && waited[0] <= 20),
                                "The bomb did not take or keep the owner's attacker");
                        if (target == attacker && ++held[0] >= 10) {
                            // 1.16 MasterHurtTargetGoal waits until the victim's last damage is older than 40 ticks
                            helper.assertTrue(victim.hurt(helper.getLevel().damageSources().generic(), 1.0F)
                                            && victim.getLastDamageSource() != null,
                                    "The victim fixture took no damage");
                            owner.setLastHurtMob(victim);
                            next(phase, held, waited);
                        }
                    }
                    else if (phase[0] == 2) {
                        if (waited[0] <= 30) {
                            helper.assertTrue(target == attacker,
                                    "The owner's victim was taken while its damage was fresh: " + target);
                        }
                        else {
                            helper.assertTrue(target == attacker || target == victim,
                                    "The owner's victim is not the next target: " + target);
                            helper.assertTrue(target == victim || (held[0] == 0 && waited[0] <= 60),
                                    "The bomb did not take or keep the owner's victim");
                            if (target == victim && ++held[0] >= 10) {
                                rival.setGameMode(GameType.SURVIVAL);
                                next(phase, held, waited);
                            }
                        }
                    }
                    else {
                        helper.assertTrue(target == rival || held[0] == 0,
                                "A provoked animal replaced the player target: " + target);
                        if (target == rival && ++held[0] >= 20) {
                            fixture.cleanup();
                            helper.succeed();
                        }
                    }
                }
                catch (RuntimeException | Error failure) {
                    fixture.cleanup();
                    throw failure;
                }
            });
            helper.runAfterDelay(580, () -> {
                if (!fixture.finished) {
                    fixture.cleanup();
                    helper.fail("Owner combat stopped in phase " + phase[0] + " with target " + sheer.getTarget()
                            + " (3 = waiting for the player)");
                }
            });
        }
        catch (RuntimeException | Error failure) {
            fixture.cleanup();
            throw failure;
        }
    }

    // 1.16 SheerHeartAttack.standPerform flags every bomb invulnerable, and an invulnerable entity is nobody's
    // target. The husk is farther from each bomb and from the golem than a bomb is, so a bomb that can be
    // targeted is taken first.
    @GameTest(template = "empty", batch = "r766_sha_untargetable", timeoutTicks = 260)
    public static void neitherAnotherBombNorAMobTargetsASheerHeartAttack(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper, "kq-sha-first");
        try {
            SheerHeartAttackEntity sheer = fixture.sheer;
            // Creative owners are no targets, so the bombs choose among the monsters
            fixture.owner.setGameMode(GameType.CREATIVE);
            SheerHeartAttackEntity other = fixture.rivalBomb("kq-sha-second", 0.0D, 5.0D);
            Husk husk = fixture.husk(6.0D, 2.5D);
            IronGolem golem = fixture.golem(4.0D, -4.0D);
            Set<String> hunted = new LinkedHashSet<>();
            int[] waited = { 0 };
            helper.onEachTick(() -> {
                if (fixture.finished) {
                    return;
                }
                try {
                    helper.assertTrue(
                            sheer.isAlive() && other.isAlive() && sheer.swellTicks() == 0 && other.swellTicks() == 0,
                            "The two-bomb fixture swelled or lost a bomb");
                    if (sheer.getTarget() == other) {
                        hunted.add("the first user's bomb hunts the second user's bomb");
                    }
                    if (other.getTarget() == sheer) {
                        hunted.add("the second user's bomb hunts the first user's bomb");
                    }
                    if (golem.getTarget() instanceof SheerHeartAttackEntity) {
                        hunted.add("an iron golem attacks a bomb");
                    }
                    if (++waited[0] >= 160) {
                        LivingEntity first = sheer.getTarget();
                        LivingEntity second = other.getTarget();
                        LivingEntity golemTarget = golem.getTarget();
                        fixture.cleanup();
                        helper.assertTrue(hunted.isEmpty(),
                                "The donor flags every Sheer Heart Attack invulnerable, so no bomb and no mob "
                                        + "takes one as a target; in the port " + hunted);
                        helper.assertTrue(first == husk && second == husk && golemTarget == husk,
                                "fixture: a bomb or the golem did not take the husk, so their selectors were "
                                        + "not live: " + first + ", " + second + ", " + golemTarget);
                        helper.succeed();
                    }
                }
                catch (RuntimeException | Error failure) {
                    fixture.cleanup();
                    throw failure;
                }
            });
        }
        catch (RuntimeException | Error failure) {
            fixture.cleanup();
            throw failure;
        }
    }

    private static void next(int[] phase, int[] held, int[] waited) {
        phase[0]++;
        held[0] = 0;
        waited[0] = 0;
    }

    // Also a listener, so that a failure or a timeout outside the guarded steps lets the players go
    private static final class Fixture implements GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Difficulty oldDifficulty;
        private final List<Entity> mobs = new ArrayList<>();
        private final List<ServerPlayer> players = new ArrayList<>();
        private ServerPlayer owner;
        private StandEntity stand;
        private SheerHeartAttackEntity sheer;
        private boolean finished;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.oldDifficulty = level.getDifficulty();
        }

        private static Fixture create(GameTestHelper helper, String ownerName) {
            Fixture fixture = new Fixture(helper);
            helper.testInfo.addListener(fixture);
            try {
                fixture.level.getServer().setDifficulty(Difficulty.NORMAL, true);
                BlockPos pad = KillerQueenGameTests.sheerHeartPad(helper, true);
                fixture.owner = KillerQueenGameTests.makeCompatibleMockPlayer(helper, ownerName, pad);
                StandPower power = StandPower.get(fixture.owner);
                helper.assertTrue(power != null, "The mock owner has no Stand power attachment");
                power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
                power.setStamina(power.getMaxStamina());
                helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(fixture.owner, power),
                        "Killer Queen could not be summoned for the target test");
                fixture.stand = power.getSummonedStandEntity();
                helper.assertTrue(fixture.stand != null && fixture.stand.isAddedToLevel(),
                        "Killer Queen was not added to the server level");
                fixture.stand.summonLockTicks = 0;
                fixture.sheer = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(fixture.level);
                helper.assertTrue(fixture.sheer != null, "Sheer Heart Attack could not be created");
                // No aimed target: everything it hunts comes from its own target selectors
                fixture.sheer.bind(fixture.owner, fixture.stand, null);
                fixture.sheer.moveTo(pad.getX() + 5.5D, pad.getY(), pad.getZ() + 0.5D, -90.0F, 0.0F);
                // Isolates target choice from the chase
                fixture.sheer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
                helper.assertTrue(!fixture.sheer.isRemoved() && fixture.level.addFreshEntity(fixture.sheer)
                                && KillerQueenStateService.linkSheerHeart(fixture.owner, fixture.sheer)
                                && fixture.sheer.getTarget() == null,
                        "Sheer Heart Attack did not enter its linked server lifetime without a target");
                return fixture;
            }
            catch (RuntimeException | Error failure) {
                fixture.cleanup();
                throw failure;
            }
        }

        private Cow cow(double dx, double dz) {
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Missing cow fixture");
            place(cow, dx, dz);
            return cow;
        }

        private Creeper monster(double dx, double dz) {
            Creeper creeper = EntityType.CREEPER.create(level);
            helper.assertTrue(creeper != null, "Missing monster fixture");
            place(creeper, dx, dz);
            return creeper;
        }

        private Husk husk(double dx, double dz) {
            Husk husk = EntityType.HUSK.create(level);
            helper.assertTrue(husk != null, "Missing husk fixture");
            place(husk, dx, dz);
            return husk;
        }

        // Chooses its targets, but stays where it is put
        private IronGolem golem(double dx, double dz) {
            IronGolem golem = EntityType.IRON_GOLEM.create(level);
            helper.assertTrue(golem != null, "Missing iron golem fixture");
            place(golem, dx, dz);
            golem.setNoAi(false);
            golem.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            return golem;
        }

        // A second Killer Queen user with a linked bomb of their own, which stays where it is put
        private SheerHeartAttackEntity rivalBomb(String ownerName, double dx, double dz) {
            ServerPlayer rival = player(ownerName, dx - 5.0D, dz);
            rival.setGameMode(GameType.CREATIVE);
            StandPower power = StandPower.get(rival);
            helper.assertTrue(power != null, "The second mock owner has no Stand power attachment");
            power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(rival, power),
                    "Killer Queen could not be summoned for the second owner");
            StandEntity rivalStand = power.getSummonedStandEntity();
            helper.assertTrue(rivalStand != null && rivalStand.isAddedToLevel(),
                    "The second Killer Queen was not added to the server level");
            rivalStand.summonLockTicks = 0;
            mobs.add(rivalStand);
            SheerHeartAttackEntity bomb = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(level);
            helper.assertTrue(bomb != null, "The second Sheer Heart Attack could not be created");
            bomb.bind(rival, rivalStand, null);
            bomb.moveTo(sheer.getX() + dx, sheer.getY(), sheer.getZ() + dz, -90.0F, 0.0F);
            bomb.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            mobs.add(bomb);
            helper.assertTrue(!bomb.isRemoved() && level.isPositionEntityTicking(bomb.blockPosition())
                            && level.addFreshEntity(bomb)
                            && KillerQueenStateService.linkSheerHeart(rival, bomb)
                            && bomb.getTarget() == null,
                    "The second Sheer Heart Attack did not enter its linked server lifetime without a target");
            return bomb;
        }

        private void place(Mob mob, double dx, double dz) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.setPos(sheer.getX() + dx, sheer.getY(), sheer.getZ() + dz);
            mobs.add(mob);
            helper.assertTrue(level.isPositionEntityTicking(mob.blockPosition()) && level.addFreshEntity(mob),
                    "A target fixture left the ticking pad");
        }

        private ServerPlayer player(String name, double dx, double dz) {
            BlockPos spot = BlockPos.containing(sheer.getX() + dx, sheer.getY(), sheer.getZ() + dz);
            helper.assertTrue(level.isPositionEntityTicking(spot), "A player fixture left the ticking pad");
            ServerPlayer player = KillerQueenGameTests.makeCompatibleMockPlayer(helper, name, spot);
            players.add(player);
            return player;
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
            if (sheer != null) {
                sheer.discard();
            }
            mobs.forEach(Entity::discard);
            if (stand != null) {
                stand.discard();
            }
            for (ServerPlayer player : players) {
                player.connection.disconnect(Component.literal("Killer Queen SHA target GameTest complete"));
            }
            if (owner != null) {
                owner.connection.disconnect(Component.literal("Killer Queen SHA target GameTest complete"));
            }
            KillerQueenGameTests.sheerHeartPad(helper, false);
            level.getServer().setDifficulty(oldDifficulty, true);
        }
    }
}
