package rotp.addons.killerqueen.btd;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandArrowEntity;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.standpower.StandPower;
import com.mojang.authlib.GameProfile;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenEvents;
import rotp.addons.killerqueen.state.KillerQueenState;
import rotp.addons.killerqueen.state.KillerQueenState.BitesTheDustPlayerSnapshot;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(KillerQueenAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BitesTheDustSafetyGameTests {
    private static final double POSITION_EPSILON = 1.0E-6D;

    private BitesTheDustSafetyGameTests() {}

    @GameTest(
            template = "empty",
            batch = "btdCommitFaults",
            timeoutTicks = 300)
    public static void commitFaultsRestoreEveryAppliedStage(
            GameTestHelper helper) {
        ArmedMarker armed = armIsolated(
                helper, "kq-fault-owner");
        ServerPlayer attacker = makeCompatibleMockPlayer(
                helper, "kq-fault-atk");
        placeNearVictim(attacker, armed.victim());
        armed.owner().getFoodData().setFoodLevel(0);

        BitesTheDustService.CommitFault[] faults = {
                BitesTheDustService.CommitFault.PREPARE_RECEIPT,
                BitesTheDustService.CommitFault.TELEPORT_APPLY,
                BitesTheDustService.CommitFault.DAY_TIME_APPLY,
                BitesTheDustService.CommitFault.OWNER_HEALTH_APPLY,
                BitesTheDustService.CommitFault.ATTACKER_KILL
        };
        int[] stage = {0};
        boolean[] awaitingCommit = {false};
        Vec3[] expectedPosition = {Vec3.ZERO};
        float[] expectedHealth = {0.0F};

        helper.onEachTick(() -> {
            if (stage[0] >= faults.length) {
                cleanup(armed, attacker);
                helper.succeed();
                return;
            }
            if (!awaitingCommit[0]) {
                expectedPosition[0] = armed.markerPosition().add(
                        7.0D + stage[0], 0.0D, 0.0D);
                expectedHealth[0] =
                        armed.owner().getMaxHealth() - 6.0F;
                armed.owner().setPos(expectedPosition[0]);
                armed.owner().setHealth(expectedHealth[0]);
                helper.getLevel().setDayTime(
                        armed.markerDayTime()
                                + 700L + stage[0] * 100L);
                BitesTheDustService.setCommitFaultForTests(
                        helper.getLevel().getServer(),
                        armed.owner().getUUID(),
                        faults[stage[0]]);
                trigger(helper, armed.victim(), attacker);
                awaitingCommit[0] = true;
                return;
            }
            if (BitesTheDustService.pendingAgeForTests(
                            helper.getLevel().getServer(),
                            attacker.getUUID())
                    >= 0) {
                helper.assertTrue(
                        attacker.isAlive(),
                        "A faulted BTD commit killed the attacker early.");
                return;
            }

            helper.assertTrue(
                    attacker.isAlive(),
                    "Fault " + faults[stage[0]]
                            + " left the attacker dead.");
            helper.assertTrue(
                    armed.owner().position().distanceToSqr(
                            expectedPosition[0]) < POSITION_EPSILON,
                    "Fault " + faults[stage[0]]
                            + " left a player teleport applied.");
            helper.assertTrue(
                    Float.compare(
                            armed.owner().getHealth(),
                            expectedHealth[0]) == 0,
                    "Fault " + faults[stage[0]]
                            + " left owner healing applied.");
            helper.assertTrue(
                    helper.getLevel().getDayTime()
                            > armed.markerDayTime() + 500L,
                    "Fault " + faults[stage[0]]
                            + " left the marker day applied.");
            helper.assertTrue(
                    armed.state()
                            .latestBitesTheDustActivationReceipt()
                            == null,
                    "Fault " + faults[stage[0]]
                            + " exposed a commit receipt.");
            stage[0]++;
            awaitingCommit[0] = false;
        });
    }

    @GameTest(
            template = "empty",
            batch = "btdTriggerAges",
            timeoutTicks = 1050)
    public static void attackerDeathAtTicksOneThroughThirtyNineNeverRewinds(
            GameTestHelper helper) {
        ArmedMarker armed = armIsolated(
                helper, "kq-age-owner");
        armed.owner().getFoodData().setFoodLevel(0);
        Vec3 mutatedPosition = armed.markerPosition().add(
                7.0D, 0.0D, 0.0D);
        float mutatedHealth = armed.owner().getMaxHealth() - 7.0F;
        long mutatedDay = armed.markerDayTime() + 800L;
        armed.owner().setPos(mutatedPosition);
        armed.owner().setHealth(mutatedHealth);
        helper.getLevel().setDayTime(mutatedDay);

        int[] deathAge = {1};
        ServerPlayer[] attacker = {null};
        boolean[] observedFinalAgeThirtyNine = {false};

        helper.onEachTick(() -> {
            if (attacker[0] == null) {
                String name = deathAge[0] <= 39
                        ? "kq-age-" + deathAge[0]
                        : "kq-age-final";
                attacker[0] = makeCompatibleMockPlayer(helper, name);
                placeNearVictim(attacker[0], armed.victim());
                trigger(helper, armed.victim(), attacker[0]);
                return;
            }

            int age = BitesTheDustService.pendingAgeForTests(
                    helper.getLevel().getServer(),
                    attacker[0].getUUID());
            if (deathAge[0] <= 39) {
                helper.assertTrue(
                        age >= 0 && age <= deathAge[0],
                        "The pending trigger skipped requested death age "
                                + deathAge[0] + ".");
                assertStillMutated(
                        helper,
                        armed,
                        mutatedPosition,
                        mutatedHealth,
                        "before attacker death at age " + deathAge[0]);
                helper.assertTrue(
                        armed.state()
                                .latestBitesTheDustActivationReceipt()
                                == null,
                        "A pre-commit attacker death exposed a receipt.");
                if (age < deathAge[0]) {
                    return;
                }
                attacker[0].invulnerableTime = 0;
                attacker[0].hurt(
                        attacker[0].damageSources().genericKill(),
                        Float.MAX_VALUE);
                helper.assertTrue(
                        !attacker[0].isAlive()
                                && BitesTheDustService
                                        .pendingAgeForTests(
                                                helper.getLevel()
                                                        .getServer(),
                                                attacker[0].getUUID())
                                        < 0,
                        "Attacker death did not cancel BTD at age "
                                + deathAge[0] + ".");
                disconnect(attacker[0], "BTD age death complete");
                attacker[0] = null;
                deathAge[0]++;
                return;
            }

            if (age >= 0) {
                if (age == 39) {
                    observedFinalAgeThirtyNine[0] = true;
                }
                helper.assertTrue(
                        attacker[0].isAlive(),
                        "The final attacker died before commit age 40.");
                assertStillMutated(
                        helper,
                        armed,
                        mutatedPosition,
                        mutatedHealth,
                        "at final pending age " + age);
                return;
            }

            BitesTheDustActivationReceipt receipt = armed.state()
                    .latestBitesTheDustActivationReceipt();
            helper.assertTrue(
                    observedFinalAgeThirtyNine[0]
                            && !attacker[0].isAlive()
                            && receipt != null
                            && receipt.commitTick()
                                    - receipt.triggerStartTick() == 40
                            && armed.owner().position().distanceToSqr(
                                    armed.markerPosition())
                                    < POSITION_EPSILON
                            && Float.compare(
                                    armed.owner().getHealth(),
                                    armed.owner().getMaxHealth()) == 0
                            && receipt.sourceDayTimeAfter()
                                    == armed.markerDayTime(),
                    "BTD did not commit exactly after surviving age 39.");
            helper.assertTrue(
                    BitesTheDustService
                            .consumeLatestBitesTheDustActivationReceipt(
                                    armed.owner(),
                                    armed.victim().getUUID(),
                                    attacker[0].getUUID())
                            .isPresent(),
                    "The exact-age commit receipt was not consumable.");
            cleanup(armed, attacker[0]);
            helper.succeed();
        });
    }

    @GameTest(
            template = "empty",
            batch = "btdOutOfRange",
            timeoutTicks = 90)
    public static void ownerOutsideRewindRadiusStillHeals(
            GameTestHelper helper) {
        ArmedMarker armed = armIsolated(
                helper, "kq-range-owner");
        ServerPlayer attacker = makeCompatibleMockPlayer(
                helper, "kq-range-atk");
        placeNearVictim(attacker, armed.victim());
        Vec3 outOfRange = armed.victim().position().add(
                BitesTheDustService.TRIGGER_RESET_RADIUS + 20.0D,
                0.0D,
                0.0D);
        armed.owner().setPos(outOfRange);
        armed.owner().setHealth(
                armed.owner().getMaxHealth() - 8.0F);
        armed.owner().getFoodData().setFoodLevel(0);
        trigger(helper, armed.victim(), attacker);

        helper.onEachTick(() -> {
            if (BitesTheDustService.pendingAgeForTests(
                            helper.getLevel().getServer(),
                            attacker.getUUID())
                    >= 0) {
                return;
            }
            BitesTheDustActivationReceipt receipt = armed.state()
                    .latestBitesTheDustActivationReceipt();
            helper.assertTrue(
                    !attacker.isAlive()
                            && receipt != null
                            && receipt.teleportedPlayers() == 0
                            && receipt.journalEntryCount() == 2
                            && armed.owner().position().distanceToSqr(
                                    outOfRange) < POSITION_EPSILON
                            && Float.compare(
                                    armed.owner().getHealth(),
                                    armed.owner().getMaxHealth()) == 0,
                    "An out-of-radius owner was not healed without "
                            + "being teleported.");
            helper.assertTrue(
                    BitesTheDustService
                            .consumeLatestBitesTheDustActivationReceipt(
                                    armed.owner(),
                                    armed.victim().getUUID(),
                                    attacker.getUUID())
                            .isPresent(),
                    "The out-of-radius commit receipt was not consumable.");
            cleanup(armed, attacker);
            helper.succeed();
        });
    }

    @GameTest(
            template = "empty",
            batch = "btdPolicies",
            timeoutTicks = 40)
    public static void arrowInputsAndNonPlayerAttackerFollowDonorPolicy(
            GameTestHelper helper) {
        ServerPlayer rightNormal = freshLockedKillerQueen(
                helper, "kq-right-normal");
        ServerPlayer rightBeetle = freshLockedKillerQueen(
                helper, "kq-right-beetle");
        ServerPlayer rightMeteor = freshLockedKillerQueen(
                helper, "kq-right-meteor");
        ServerPlayer canceled = freshLockedKillerQueen(
                helper, "kq-arrow-cancel");
        ServerPlayer hitNormal = freshLockedKillerQueen(
                helper, "kq-hit-normal");
        ServerPlayer hitBeetle = freshLockedKillerQueen(
                helper, "kq-hit-beetle");
        ServerPlayer hitMeteor = freshLockedKillerQueen(
                helper, "kq-hit-meteor");

        useArrow(rightNormal, ModItems.STAND_ARROW.toStack(), false);
        useArrow(
                rightBeetle,
                ModItems.STAND_ARROW_BEETLE.toStack(),
                false);
        useArrow(
                rightMeteor,
                ModItems.STAND_ARROW_METEORITE.toStack(),
                false);
        useArrow(canceled, ModItems.STAND_ARROW.toStack(), true);
        Cow arrowSource = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        hitWithArrow(
                helper,
                hitNormal,
                arrowSource,
                ModItems.STAND_ARROW.toStack(),
                false);
        hitWithArrow(
                helper,
                hitBeetle,
                arrowSource,
                ModItems.STAND_ARROW_BEETLE.toStack(),
                false);
        hitWithArrow(
                helper,
                hitMeteor,
                arrowSource,
                ModItems.STAND_ARROW_METEORITE.toStack(),
                false);
        hitWithArrow(
                helper,
                canceled,
                arrowSource,
                ModItems.STAND_ARROW.toStack(),
                true);
        helper.assertTrue(
                BitesTheDustAwakening.isUnlocked(rightNormal)
                        && BitesTheDustAwakening.isUnlocked(rightBeetle)
                        && !BitesTheDustAwakening.isUnlocked(rightMeteor)
                        && BitesTheDustAwakening.isUnlocked(hitNormal)
                        && BitesTheDustAwakening.isUnlocked(hitBeetle)
                        && BitesTheDustAwakening.isUnlocked(hitMeteor)
                        && !BitesTheDustAwakening.isUnlocked(canceled),
                "Arrow policy drifted: projectile hits must accept all "
                        + "StandArrowEntity variants while crouching "
                        + "right-click excludes meteorite and canceled "
                        + "events.");

        Cow protectedVictim = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 1);
        Cow nonPlayerAttacker = helper.spawnWithNoFreeWill(
                EntityType.COW, 3, 2, 1);
        rightNormal.setPos(
                protectedVictim.position().add(-2.0D, 0.0D, 0.0D));
        helper.assertTrue(
                BitesTheDustService.armBitesTheDust(
                        rightNormal,
                        protectedVictim.getUUID(),
                        helper.getLevel().getGameTime(),
                        helper.getLevel().dimension().location()),
                "The non-player attacker victim could not be armed.");
        float protectedHealth = protectedVictim.getHealth();
        boolean damageApplied = protectedVictim.hurt(
                helper.getLevel().damageSources()
                        .mobAttack(nonPlayerAttacker),
                1.0F);
        helper.assertTrue(
                !damageApplied
                        && Float.compare(
                                protectedVictim.getHealth(),
                                protectedHealth) == 0
                        && !nonPlayerAttacker.isAlive()
                        && KillerQueenAddon.state(rightNormal)
                                .latestBitesTheDustActivationReceipt()
                                == null,
                "A non-player attacker did not receive the donor-exact "
                        + "immediate BTD detonation.");
        BitesTheDustService.removeTimeMarker(rightNormal);

        arrowSource.discard();
        protectedVictim.discard();
        nonPlayerAttacker.discard();
        for (ServerPlayer player :
                List.of(
                        rightNormal,
                        rightBeetle,
                        rightMeteor,
                        canceled,
                        hitNormal,
                        hitBeetle,
                        hitMeteor)) {
            disconnect(player, "BTD policy test complete");
        }
        helper.succeed();
    }

    @GameTest(
            template = "empty",
            batch = "btdCompatibilityTrigger",
            timeoutTicks = 90)
    public static void compatibilityArmingAutomaticallyCommits(
            GameTestHelper helper) {
        ServerPlayer owner = freshKillerQueen(
                helper, "kq-compat-owner");
        Cow victim = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 1);
        ServerPlayer attacker = makeCompatibleMockPlayer(
                helper, "kq-compat-atk");
        owner.setPos(victim.position().add(-2.0D, 0.0D, 0.0D));
        placeNearVictim(attacker, victim);

        helper.assertTrue(
                BitesTheDustService.armBitesTheDust(
                        owner,
                        victim.getUUID(),
                        helper.getLevel().getGameTime(),
                        helper.getLevel().dimension().location()),
                "The compatibility arming path was rejected.");
        KillerQueenState state = KillerQueenAddon.state(owner);
        helper.assertTrue(
                !state.bitesTheDustPlayerSnapshots().isEmpty()
                        && state.bitesTheDustPlayerSnapshot(
                                owner.getUUID()) != null,
                "Compatibility arming did not capture a donor-equivalent "
                        + "rewind snapshot.");

        owner.setPos(owner.position().add(6.0D, 0.0D, 0.0D));
        owner.setHealth(owner.getMaxHealth() - 5.0F);
        trigger(helper, victim, attacker);
        helper.onEachTick(() -> {
            if (BitesTheDustService.pendingAgeForTests(
                            helper.getLevel().getServer(),
                            attacker.getUUID())
                    >= 0) {
                return;
            }
            BitesTheDustActivationReceipt receipt = state
                    .latestBitesTheDustActivationReceipt();
            helper.assertTrue(
                    !attacker.isAlive()
                            && receipt != null
                            && receipt.verified()
                            && receipt.snapshotCount() >= 1,
                    "Compatibility arming canceled damage but did not "
                            + "produce a committed rewind receipt.");
            helper.assertTrue(
                    BitesTheDustService
                            .consumeLatestBitesTheDustActivationReceipt(
                                    owner,
                                    victim.getUUID(),
                                    attacker.getUUID())
                            .isPresent(),
                    "The compatibility activation receipt was not "
                            + "consumable.");
            BitesTheDustService.removeTimeMarker(owner);
            victim.discard();
            disconnect(attacker, "BTD compatibility test complete");
            disconnect(owner, "BTD compatibility test complete");
            helper.succeed();
        });
    }

    @GameTest(
            template = "empty",
            batch = "btdLegacyTriggers",
            timeoutTicks = 250)
    public static void legacyVersionsOneThroughThreeAutomaticallyCommit(
            GameTestHelper helper) {
        int[] version = {1};
        ServerPlayer[] owner = {null};
        ServerPlayer[] attacker = {null};
        Cow[] victim = {null};

        helper.onEachTick(() -> {
            if (version[0] > 3) {
                helper.succeed();
                return;
            }
            if (owner[0] == null) {
                String suffix = Integer.toString(version[0]);
                owner[0] = freshKillerQueen(
                        helper, "kq-v" + suffix + "-owner");
                victim[0] = helper.spawnWithNoFreeWill(
                        EntityType.COW, 2, 2, 1);
                attacker[0] = makeCompatibleMockPlayer(
                        helper, "kq-v" + suffix + "-atk");
                owner[0].setPos(
                        victim[0].position().add(-2.0D, 0.0D, 0.0D));
                placeNearVictim(attacker[0], victim[0]);

                CompoundTag legacyTag = legacyArmedTag(
                        helper, version[0], victim[0].getUUID());
                KillerQueenState state =
                        KillerQueenAddon.state(owner[0]);
                state.deserializeNBT(
                        helper.getLevel().registryAccess(), legacyTag);
                helper.assertTrue(
                        state.hasBitesTheDustVictim()
                                && state.bitesTheDustPlayerSnapshots()
                                        .isEmpty(),
                        "Legacy v" + version[0]
                                + " did not load as a zero-snapshot marker.");

                BitesTheDustService.reconnectOwner(owner[0]);
                helper.assertTrue(
                        state.hasBitesTheDustVictim()
                                && !state.bitesTheDustPlayerSnapshots()
                                        .isEmpty()
                                && BitesTheDustVictimIndex.get(
                                                helper.getLevel()
                                                        .getServer())
                                        .bindingForOwner(
                                                owner[0].getUUID())
                                        != null,
                        "Legacy v" + version[0]
                                + " did not migrate to a valid rewind "
                                + "snapshot and persistent lease.");
                trigger(helper, victim[0], attacker[0]);
                return;
            }

            if (BitesTheDustService.pendingAgeForTests(
                            helper.getLevel().getServer(),
                            attacker[0].getUUID())
                    >= 0) {
                return;
            }
            KillerQueenState state = KillerQueenAddon.state(owner[0]);
            BitesTheDustActivationReceipt receipt = state
                    .latestBitesTheDustActivationReceipt();
            helper.assertTrue(
                    !attacker[0].isAlive()
                            && receipt != null
                            && receipt.verified()
                            && receipt.snapshotCount() >= 1,
                    "Legacy v" + version[0]
                            + " canceled damage without a committed "
                            + "rewind receipt.");
            helper.assertTrue(
                    BitesTheDustService
                            .consumeLatestBitesTheDustActivationReceipt(
                                    owner[0],
                                    victim[0].getUUID(),
                                    attacker[0].getUUID())
                            .isPresent(),
                    "Legacy v" + version[0]
                            + " receipt was not consumable.");
            BitesTheDustService.removeTimeMarker(owner[0]);
            victim[0].discard();
            disconnect(attacker[0], "BTD legacy test complete");
            disconnect(owner[0], "BTD legacy test complete");
            owner[0] = null;
            attacker[0] = null;
            victim[0] = null;
            version[0]++;
        });
    }

    @GameTest(
            template = "empty",
            batch = "btdOfflineLease",
            timeoutTicks = 100)
    public static void offlineOwnerLeaseSurvivesSessionAndExpiresOnTime(
            GameTestHelper helper) {
        ServerPlayer oldOwner = freshKillerQueen(
                helper, "kq-offline-old");
        ServerPlayer victim = makeCompatibleMockPlayer(
                helper, "kq-offline-vic");
        oldOwner.setPos(1.5D, 2.0D, 1.5D);
        victim.setPos(3.5D, 2.0D, 1.5D);
        ResourceLocation originalDimension =
                helper.getLevel().dimension().location();
        helper.assertTrue(
                BitesTheDustService.armBitesTheDust(
                        oldOwner,
                        victim.getUUID(),
                        helper.getLevel().getGameTime(),
                        originalDimension),
                "The offline-owner lease could not be armed.");

        CompoundTag savedOwnerState = KillerQueenAddon.state(oldOwner)
                .serializeNBT(helper.getLevel().registryAccess());
        UUID ownerUuid = oldOwner.getUUID();
        BitesTheDustService.playerLoggedOut(oldOwner);
        disconnect(oldOwner, "BTD offline owner session closed");

        ServerLevel nether = helper.getLevel().getServer()
                .getLevel(Level.NETHER);
        helper.assertTrue(
                nether != null,
                "The lifecycle test server did not expose the Nether.");
        if (nether == null) {
            throw new IllegalStateException("Missing Nether in GameTest");
        }
        helper.assertTrue(
                victim.teleportTo(
                        nether,
                        1.5D,
                        80.0D,
                        1.5D,
                        Set.of(),
                        0.0F,
                        0.0F),
                "The victim could not change dimension.");
        BitesTheDustService.trackPlayerAcrossMarkers(victim);
        BitesTheDustService.playerChangedDimension(victim);

        BitesTheDustVictimIndex liveIndex =
                BitesTheDustVictimIndex.get(
                        helper.getLevel().getServer());
        helper.assertTrue(
                liveIndex.bindingForVictim(
                                victim.getUUID(),
                                Level.NETHER.location())
                        != null,
                "An offline owner's victim dimension was not updated "
                        + "from the live victim.");

        ServerPlayer newOwner = makeCompatibleMockPlayer(
                helper, ownerUuid, "kq-offline-new");
        setKillerQueen(helper, newOwner, "reconnected owner");
        KillerQueenState reloadedState = KillerQueenAddon.state(newOwner);
        reloadedState.deserializeNBT(
                helper.getLevel().registryAccess(), savedOwnerState);
        helper.assertTrue(
                originalDimension.equals(
                        reloadedState.bitesTheDustVictimDimension()),
                "The serialized session did not retain its stale victim "
                        + "dimension test fixture.");
        BitesTheDustService.reconnectOwner(newOwner);
        helper.assertTrue(
                Level.NETHER.location().equals(
                        reloadedState.bitesTheDustVictimDimension())
                        && liveIndex.bindingForVictim(
                                        victim.getUUID(),
                                        Level.NETHER.location())
                                != null,
                "Owner login rewrote the authoritative live victim "
                        + "dimension with stale serialized state.");

        BitesTheDustVictimIndex lease =
                new BitesTheDustVictimIndex();
        UUID leaseOwner = UUID.randomUUID();
        UUID leaseVictim = UUID.randomUUID();
        UUID leaseEpoch = UUID.randomUUID();
        long markerGameTime = 100L;
        helper.assertTrue(
                lease.reconcile(
                                leaseOwner,
                                leaseVictim,
                                originalDimension,
                                leaseEpoch,
                                originalDimension,
                                markerGameTime)
                        == BitesTheDustVictimIndex
                                .ReconcileResult.ACCEPTED,
                "The isolated persistent lease could not be created.");
        CompoundTag beforeExpiry = lease.save(
                new CompoundTag(),
                helper.getLevel().registryAccess());
        BitesTheDustVictimIndex restartedBeforeExpiry =
                BitesTheDustVictimIndex.load(
                        beforeExpiry,
                        helper.getLevel().registryAccess());
        helper.assertTrue(
                restartedBeforeExpiry.expireBindings(
                                originalDimension,
                                markerGameTime
                                        + BitesTheDustService
                                                .MARK_LIFETIME_TICKS
                                        - 1L)
                        == 0
                        && restartedBeforeExpiry.bindingForVictim(
                                        leaseVictim,
                                        originalDimension)
                                != null,
                "The offline lease expired before tick 24,000.");
        CompoundTag atExpiry = restartedBeforeExpiry.save(
                new CompoundTag(),
                helper.getLevel().registryAccess());
        BitesTheDustVictimIndex restartedAtExpiry =
                BitesTheDustVictimIndex.load(
                        atExpiry,
                        helper.getLevel().registryAccess());
        helper.assertTrue(
                restartedAtExpiry.expireBindings(
                                originalDimension,
                                markerGameTime
                                        + BitesTheDustService
                                                .MARK_LIFETIME_TICKS)
                        == 1
                        && restartedAtExpiry.bindingForVictim(
                                        leaseVictim,
                                        originalDimension)
                                == null
                        && restartedAtExpiry.reconcile(
                                        leaseOwner,
                                        leaseVictim,
                                        originalDimension,
                                        leaseEpoch,
                                        originalDimension,
                                        markerGameTime)
                                == BitesTheDustVictimIndex
                                        .ReconcileResult.REVOKED,
                "The offline lease did not expire exactly at tick "
                        + "24,000 after a restart.");
        CompoundTag afterExpiry = restartedAtExpiry.save(
                new CompoundTag(),
                helper.getLevel().registryAccess());
        BitesTheDustVictimIndex restartedAfterExpiry =
                BitesTheDustVictimIndex.load(
                        afterExpiry,
                        helper.getLevel().registryAccess());
        helper.assertTrue(
                restartedAfterExpiry.reconcile(
                                leaseOwner,
                                leaseVictim,
                                originalDimension,
                                leaseEpoch,
                                originalDimension,
                                markerGameTime)
                        == BitesTheDustVictimIndex
                                .ReconcileResult.REVOKED,
                "Lease revocation did not survive a second restart.");

        BitesTheDustService.removeTimeMarker(newOwner);
        disconnect(newOwner, "BTD offline lease test complete");
        disconnect(victim, "BTD offline lease test complete");
        helper.succeed();
    }

    @GameTest(
            template = "empty",
            batch = "btdRestartRecovery",
            timeoutTicks = 100)
    public static void reconnectRebuildsMissingLeaseFromPersistedState(
            GameTestHelper helper) {
        ServerPlayer owner = freshKillerQueen(
                helper, "kq-restart-recovery");
        KillerQueenState state = KillerQueenAddon.state(owner);
        ResourceLocation dimension =
                helper.getLevel().dimension().location();
        UUID victimUuid = UUID.randomUUID();
        long markerTime = helper.getLevel().getDayTime();
        long markerGameTime = helper.getLevel().getGameTime();
        UUID markerEpoch = UUID.randomUUID();

        helper.assertTrue(
                state.setBitesTheDustMarker(
                        markerTime,
                        markerGameTime,
                        dimension,
                        markerEpoch,
                        List.of(BitesTheDustPlayerSnapshot.capture(owner)))
                        && state.selectBitesTheDustVictim(
                                victimUuid, dimension),
                "The persisted restart fixture could not be armed.");
        BitesTheDustVictimIndex index = BitesTheDustVictimIndex.get(
                helper.getLevel().getServer());
        index.releaseOwner(owner.getUUID());

        BitesTheDustService.reconnectOwner(owner);

        BitesTheDustVictimIndex.Binding rebuilt =
                index.bindingForOwner(owner.getUUID());
        helper.assertTrue(
                state.hasBitesTheDustVictim()
                        && victimUuid.equals(
                                state.bitesTheDustVictimUuid())
                        && rebuilt != null
                        && owner.getUUID().equals(rebuilt.ownerUuid())
                        && victimUuid.equals(rebuilt.victimUuid())
                        && markerEpoch.equals(rebuilt.markerEpoch()),
                "Owner reconnect did not rebuild the missing persistent "
                        + "victim lease from its serialized binding.");
        assertBitesTheDustLocksKit(helper, owner, true);

        BitesTheDustService.removeTimeMarker(owner);
        assertBitesTheDustLocksKit(helper, owner, false);
        disconnect(owner, "BTD restart recovery test complete");
        helper.succeed();
    }

    @GameTest(
            template = "empty",
            batch = "btdAuthorityReset",
            timeoutTicks = 90)
    public static void processAuthorityResetRejectsOldReceipt(
            GameTestHelper helper) {
        ArmedMarker armed = armIsolated(
                helper, "kq-reset-owner");
        ServerPlayer attacker = makeCompatibleMockPlayer(
                helper, "kq-reset-atk");
        placeNearVictim(attacker, armed.victim());
        trigger(helper, armed.victim(), attacker);

        helper.onEachTick(() -> {
            if (BitesTheDustService.pendingAgeForTests(
                            helper.getLevel().getServer(),
                            attacker.getUUID())
                    >= 0) {
                return;
            }
            BitesTheDustActivationReceipt receipt = armed.state()
                    .latestBitesTheDustActivationReceipt();
            helper.assertTrue(
                    receipt != null && receipt.verified(),
                    "The authority-reset fixture did not commit a valid "
                            + "receipt.");
            CompoundTag oldTag = receipt.toTag();
            helper.assertTrue(
                    BitesTheDustService.authenticatedReceiptTag(oldTag),
                    "The committed receipt tag was not initially valid.");
            BitesTheDustService.simulateProcessAuthorityResetForTests(
                    helper.getLevel().getServer());
            helper.assertTrue(
                    !receipt.verified()
                            && !BitesTheDustService
                                    .authenticatedReceiptTag(oldTag)
                            && BitesTheDustService
                                    .consumeLatestBitesTheDustActivationReceipt(
                                            armed.owner(),
                                            armed.victim().getUUID(),
                                            attacker.getUUID())
                                    .isEmpty(),
                    "An old receipt or serialized tag survived process "
                            + "authority reset.");
            cleanup(armed, attacker);
            helper.succeed();
        });
    }

    /*
     * The mock players of a BTD safety test leave when it fails or times out as well, relogged ones included.
     */
    @GameTest(
            template = "empty",
            batch = "btdMockPlayerCleanup",
            timeoutTicks = 40)
    public static void aFailedBtdTestLetsGoOfItsMockPlayers(
            GameTestHelper helper) {
        UUID ownerUuid = UUID.randomUUID();
        List<MockPlayerExit> exits = new ArrayList<>();
        ServerPlayer first = makeCompatibleMockPlayer(
                helper, ownerUuid, "kq-btd-cleanup-relog", exits);
        disconnect(first, "BTD cleanup relog");
        ServerPlayer again = makeCompatibleMockPlayer(
                helper, ownerUuid, "kq-btd-cleanup-relog", exits);
        ServerPlayer bystander = makeCompatibleMockPlayer(
                helper, UUID.randomUUID(),
                "kq-btd-cleanup-bystander", exits);
        helper.assertTrue(
                exits.size() == 3,
                "A BTD mock player was not registered for cleanup.");
        for (MockPlayerExit exit : exits) {
            exit.testFailed(helper.testInfo, null);
        }
        helper.succeedWhen(() -> {
            PlayerList players =
                    helper.getLevel().getServer().getPlayerList();
            helper.assertTrue(
                    again.hasDisconnected()
                            && bystander.hasDisconnected()
                            && players.getPlayer(ownerUuid) == null
                            && players.getPlayer(bystander.getUUID())
                                    == null,
                    "A failed BTD test left a mock player logged in ("
                            + again.hasDisconnected()
                            + " relogged owner, "
                            + bystander.hasDisconnected()
                            + " bystander gone).");
        });
    }

    private static ArmedMarker armIsolated(
            GameTestHelper helper, String ownerName) {
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, ownerName);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The isolated BTD owner has no Stand attachment.");
        if (power == null) {
            throw new IllegalStateException(
                    "Missing Stand attachment in GameTest setup");
        }
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        Vec3 markerPosition = Vec3.atCenterOf(
                helper.absolutePos(new BlockPos(1, 2, 1)));
        owner.setPos(markerPosition);
        long markerDayTime = helper.getLevel().getDayTime();
        UUID markerEpoch = UUID.randomUUID();
        KillerQueenState state = KillerQueenAddon.state(owner);
        helper.assertTrue(
                state.setBitesTheDustMarker(
                        markerDayTime,
                        helper.getLevel().getGameTime(),
                        helper.getLevel().dimension().location(),
                        markerEpoch,
                        List.of(BitesTheDustPlayerSnapshot.capture(owner))),
                "The isolated BTD marker was rejected.");
        Cow victim = helper.spawnWithNoFreeWill(
                EntityType.COW, 2, 2, 1);
        victim.setPos(markerPosition.add(3.0D, 0.0D, 0.0D));
        helper.assertTrue(
                BitesTheDustVictimIndex.get(
                                helper.getLevel().getServer())
                        .reconcile(
                                owner.getUUID(),
                                victim.getUUID(),
                                helper.getLevel().dimension().location(),
                                markerEpoch,
                                state.bitesTheDustMarkerDimension(),
                                state.bitesTheDustMarkerGameTime())
                        == BitesTheDustVictimIndex
                                .ReconcileResult.ACCEPTED
                        && state.selectBitesTheDustVictim(
                                victim.getUUID(),
                                helper.getLevel().dimension().location()),
                "The isolated BTD victim binding was rejected.");
        return new ArmedMarker(
                owner,
                victim,
                state,
                markerPosition,
                markerDayTime);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_owner_decisions", batch = "btdPolicies", timeoutTicks = 40)
    public static void creativeAndConfigBypassPersistArrowAwakening(GameTestHelper helper) {
        net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Boolean> skip =
                rotp.core.JojoModConfig.COMMON_SPEC.getValues()
                .get(List.of("Stand settings", "Stand Progression", "skipStandProgression"));
        boolean previous = skip.get();
        ServerPlayer creative = null;
        ServerPlayer configured = null;
        try {
            skip.set(false);
            creative = freshLockedKillerQueen(helper, "btd-creative-bypass");
            creative.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            StandPower.get(creative).skipProgression();
            BitesTheDustAwakening.enforceStandArrowGate(creative);
            helper.assertTrue(BitesTheDustAwakening.isUnlocked(creative),
                    "Creative progression must not be relocked by the Arrow gate");
            creative.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            BitesTheDustAwakening.enforceStandArrowGate(creative);
            helper.assertTrue(BitesTheDustAwakening.isUnlocked(creative),
                    "A completed progression bypass must remain learned after changing mode");
            configured = freshLockedKillerQueen(helper, "btd-config-bypass");
            skip.set(true);
            StandPower.get(configured).skipProgression();
            BitesTheDustAwakening.enforceStandArrowGate(configured);
            helper.assertTrue(BitesTheDustAwakening.isUnlocked(configured),
                    "skipStandProgression must bypass the Arrow gate in survival");
        }
        finally {
            skip.set(previous);
            if (creative != null) creative.discard();
            if (configured != null) configured.discard();
        }
        helper.succeed();
    }

    private static ServerPlayer freshLockedKillerQueen(
            GameTestHelper helper, String name) {
        ServerPlayer player = makeCompatibleMockPlayer(helper, name);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        StandPower power = setKillerQueen(
                helper, player, "arrow-policy player");
        power.skipProgression();
        helper.assertTrue(
                power.isAbilityUnlocked(
                        KillerQueenAddon.TIME_MARK_NAME),
                "skipProgression did not expose the gate test input.");
        BitesTheDustAwakening.enforceStandArrowGate(player);
        helper.assertTrue(
                !power.isAbilityUnlocked(
                        KillerQueenAddon.TIME_MARK_NAME)
                        && !BitesTheDustAwakening.isUnlocked(player),
                "Ordinary survival must still require its Arrow awakening.");
        return player;
    }

    // 1.16 userToVictim press gates: an armed Bites the Dust refuses
    // the melee and bomb kit; quit_dust (marker removal) frees it.
    private static void assertBitesTheDustLocksKit(
            GameTestHelper helper, ServerPlayer owner, boolean armed) {
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null
                        && KillerQueenState.bitesTheDustLocksKit(owner)
                                == armed,
                "The Bites the Dust kit lock did not follow the armed "
                        + "state (" + armed + ").");
        if (power == null) {
            return;
        }
        for (String name : List.of(
                "punch",
                "barrage",
                "heavy_punch",
                "heavy_charged",
                "guard",
                "grab_punch",
                KillerQueenAddon.BLOCK_BOMB_NAME,
                KillerQueenAddon.BLOCK_BOMB_DETONATE_NAME,
                KillerQueenAddon.BLOCK_BOMB_CLEAR_NAME,
                KillerQueenAddon.PRIMARY_BOMB_DETONATE_NAME,
                KillerQueenAddon.PRIMARY_BOMB_CLEAR_NAME,
                KillerQueenAddon.ITEM_BOMB_DETONATE_NAME,
                KillerQueenAddon.SHEER_HEART_NAME,
                KillerQueenAddon.SHEER_HEART_RECALL_NAME,
                KillerQueenAddon.SNOW_BOMB_NAME,
                KillerQueenAddon.PROJECTILE_DETONATE_NAME)) {
            Ability ability = power.getAbility(name);
            helper.assertTrue(
                    ability != null,
                    "Killer Queen has no " + name + " ability.");
            if (armed && ability != null) {
                helper.assertTrue(
                        !ability.checkSpecificConditions(power)
                                .isPositive(),
                        "Armed Bites the Dust still allowed " + name
                                + ".");
            }
        }
        if (armed) {
            // the server press path refuses before any auto-summon
            helper.assertTrue(
                    !AbilityInput.withConditionCheck(
                                    power.updateAvailableMoves()
                                            .getContextVariationContainer(
                                                    "punch"),
                                    owner,
                                    InputMethod.CLICK)
                            && power.getSummonedStandEntity() == null,
                    "Armed Bites the Dust accepted a punch press.");
        }
        if (!armed) {
            // punch and guard have no other at-rest condition
            for (String name : List.of("punch", "guard")) {
                Ability ability = power.getAbility(name);
                helper.assertTrue(
                        ability != null
                                && ability.checkSpecificConditions(power)
                                        .isPositive(),
                        "Removing the time marker did not free "
                                + name + ".");
            }
        }
    }

    private static ServerPlayer freshKillerQueen(
            GameTestHelper helper, String name) {
        ServerPlayer player = makeCompatibleMockPlayer(helper, name);
        setKillerQueen(helper, player, "BTD owner");
        return player;
    }

    private static StandPower setKillerQueen(
            GameTestHelper helper,
            ServerPlayer player,
            String role) {
        StandPower power = StandPower.get(player);
        helper.assertTrue(
                power != null,
                "The " + role + " has no Stand attachment.");
        if (power == null) {
            throw new IllegalStateException(
                    "Missing Stand attachment in GameTest setup");
        }
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        return power;
    }

    private static void useArrow(
            ServerPlayer player,
            ItemStack stack,
            boolean canceled) {
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        PlayerInteractEvent.RightClickItem event =
                new PlayerInteractEvent.RightClickItem(
                        player, InteractionHand.MAIN_HAND);
        event.setCanceled(canceled);
        KillerQueenEvents.onStandArrowUse(event);
    }

    private static void hitWithArrow(
            GameTestHelper helper,
            ServerPlayer target,
            Cow source,
            ItemStack stack,
            boolean canceled) {
        StandArrowEntity arrow = new StandArrowEntity(
                source, helper.getLevel(), stack, null);
        DamageSource damageSource = new DamageSource(
                helper.getLevel().damageSources()
                        .genericKill().typeHolder(),
                arrow,
                source);
        LivingIncomingDamageEvent event =
                new LivingIncomingDamageEvent(
                        target,
                        new DamageContainer(damageSource, 1.0F));
        event.setCanceled(canceled);
        KillerQueenEvents.onIncomingDamage(event);
        arrow.discard();
    }

    private static CompoundTag legacyArmedTag(
            GameTestHelper helper, int version, UUID victimUuid) {
        ResourceLocation dimension =
                helper.getLevel().dimension().location();
        long markerTime = helper.getLevel().getGameTime();
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", version);
        tag.putBoolean("BtdArmed", true);
        tag.putUUID("BtdVictim", victimUuid);
        tag.putLong("BtdMarkerTime", markerTime);
        tag.putString("BtdMarkerDimension", dimension.toString());
        if (version >= 3) {
            tag.putBoolean("BtdMarked", true);
            tag.putLong("BtdMarkerGameTime", markerTime);
            tag.putString(
                    "BtdVictimDimension", dimension.toString());
        }
        return tag;
    }

    private static void trigger(
            GameTestHelper helper,
            Cow victim,
            ServerPlayer attacker) {
        victim.invulnerableTime = 0;
        float health = victim.getHealth();
        boolean applied = victim.hurt(
                helper.getLevel().damageSources()
                        .playerAttack(attacker),
                1.0F);
        helper.assertTrue(
                !applied
                        && Float.compare(victim.getHealth(), health) == 0
                        && BitesTheDustService.pendingAgeForTests(
                                helper.getLevel().getServer(),
                                attacker.getUUID()) >= 0,
                "Armed victim damage did not schedule and cancel BTD.");
    }

    private static void assertStillMutated(
            GameTestHelper helper,
            ArmedMarker armed,
            Vec3 expectedPosition,
            float expectedHealth,
            String phase) {
        helper.assertTrue(
                armed.owner().position().distanceToSqr(
                        expectedPosition) < POSITION_EPSILON
                        && Float.compare(
                                armed.owner().getHealth(),
                                expectedHealth) == 0,
                "BTD rewound or healed the owner " + phase + ".");
    }

    private static void placeNearVictim(
            ServerPlayer attacker, Cow victim) {
        attacker.setPos(victim.position().add(2.0D, 0.0D, 0.0D));
    }

    private static void cleanup(
            ArmedMarker armed, ServerPlayer attacker) {
        BitesTheDustService.removeTimeMarker(armed.owner());
        armed.victim().discard();
        disconnect(attacker, "BTD safety GameTest complete");
        disconnect(armed.owner(), "BTD safety GameTest complete");
    }

    private static void disconnect(
            ServerPlayer player, String reason) {
        if (player != null && player.connection != null) {
            player.connection.disconnect(Component.literal(reason));
        }
    }

    private static ServerPlayer makeCompatibleMockPlayer(
            GameTestHelper helper, String name) {
        return makeCompatibleMockPlayer(
                helper, UUID.randomUUID(), name);
    }

    private static ServerPlayer makeCompatibleMockPlayer(
            GameTestHelper helper, UUID uuid, String name) {
        return makeCompatibleMockPlayer(helper, uuid, name, null);
    }

    private static ServerPlayer makeCompatibleMockPlayer(
            GameTestHelper helper,
            UUID uuid,
            String name,
            List<MockPlayerExit> exits) {
        GameProfile profile = new GameProfile(
                uuid, name);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation());
        Connection connection =
                new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel()
                .getServer()
                .getPlayerList()
                .placeNewPlayer(connection, player, cookie);
        MockPlayerExit exit = new MockPlayerExit(player);
        helper.testInfo.addListener(exit);
        if (exits != null) {
            exits.add(exit);
        }
        return player;
    }

    /**
     * Lets a mock player go once its test passes, fails or times out; the
     * tests' own disconnects stay and only a player still logged in is
     * closed.
     */
    private static final class MockPlayerExit implements GameTestListener {
        private final ServerPlayer player;

        MockPlayerExit(ServerPlayer player) {
            this.player = player;
        }

        private void run() {
            try {
                if (!player.hasDisconnected()) {
                    disconnect(player, "BTD safety GameTest complete");
                }
            } catch (RuntimeException ignored) {
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {}

        @Override
        public void testPassed(
                GameTestInfo info, GameTestRunner runner) {
            run();
        }

        @Override
        public void testFailed(
                GameTestInfo info, GameTestRunner runner) {
            run();
        }

        @Override
        public void testAddedForRerun(
                GameTestInfo oldInfo,
                GameTestInfo newInfo,
                GameTestRunner runner) {
            run();
        }
    }

    private record ArmedMarker(
            ServerPlayer owner,
            Cow victim,
            KillerQueenState state,
            Vec3 markerPosition,
            long markerDayTime) {}
}
