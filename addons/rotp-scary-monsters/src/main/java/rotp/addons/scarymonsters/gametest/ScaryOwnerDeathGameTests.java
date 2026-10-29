package rotp.addons.scarymonsters.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ScaryMonstersService;
import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import rotp.addons.scarymonsters.entity.ScaryMonstersStandEntity;
import rotp.addons.scarymonsters.state.ScaryMonstersOwnedEffects;
import rotp.addons.scarymonsters.state.ScaryMonstersState;
import rotp.addons.scarymonsters.state.ScaryPossessionData;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder("scary_monsters_owner_death")
@PrefixGameTestTemplate(false)
public final class ScaryOwnerDeathGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ScaryOwnerDeathGameTests() {}

    @GameTest(template = "empty", batch = "scary_ta1_online_far", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void possessedOnlineDinosaurSurvivesFarOwnerWhileWorldTicks(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> possessedOnlineDinosaurSurvivesFarOwnerWhileWorldTicksBody(h));
    }

    private static void possessedOnlineDinosaurSurvivesFarOwnerWhileWorldTicksBody(GameTestHelper helper) {
        PossessionFixture fixture = new PossessionFixture(helper, "TA1Far", GameType.ADVENTURE, true);
        try {
            ScaryDinosaurEntity control = fixture.nonPossessedControl();
            fixture.afterFarOwnerTicks(initialTicks -> {
                try {
                    assertFarPersistence(helper, fixture, control, initialTicks, "online-mounted");
                    helper.assertTrue(fixture.target.isSpectator()
                                    && fixture.target.getVehicle() == fixture.dinosaur,
                            "The online target stopped being a possessed Spectator rider");
                    fixture.target.stopRiding();
                    int dismountedTicks = fixture.dinosaur.tickCount;
                    helper.runAfterDelay(8, () -> {
                        try {
                            assertFarPersistence(helper, fixture, control, dismountedTicks, "online-dismounted");
                            helper.assertTrue(fixture.target.isSpectator() && fixture.target.getVehicle() == null,
                                    "Dismount unexpectedly released possession");
                            helper.succeed();
                        }
                        finally {
                            fixture.close();
                        }
                    });
                }
                catch (RuntimeException | Error exception) {
                    fixture.close();
                    throw exception;
                }
            });
        }
        catch (RuntimeException | Error exception) {
            fixture.close();
            throw exception;
        }
    }

    @GameTest(template = "empty", batch = "scary_ta1_offline_far", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void possessedOfflineDinosaurSurvivesFarOwnerWhileWorldTicks(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> possessedOfflineDinosaurSurvivesFarOwnerWhileWorldTicksBody(h));
    }

    private static void possessedOfflineDinosaurSurvivesFarOwnerWhileWorldTicksBody(GameTestHelper helper) {
        PossessionFixture fixture = new PossessionFixture(helper, "TA1OfflineFar", GameType.ADVENTURE, true);
        try {
            fixture.target.close();
            ScaryDinosaurEntity control = fixture.nonPossessedControl();
            fixture.afterFarOwnerTicks(initialTicks -> {
                try {
                    assertFarPersistence(helper, fixture, control, initialTicks, "offline-dismounted");
                    fixture.reloginTarget();
                    helper.assertTrue(fixture.target.isSpectator(),
                            "A retained living dinosaur was treated as a terminal removal on login");
                    helper.succeed();
                }
                finally {
                    fixture.close();
                }
            });
        }
        catch (RuntimeException | Error exception) {
            fixture.close();
            throw exception;
        }
    }

    @GameTest(template = "empty", batch = "scary_ta1_online_discard", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void terminalDiscardImmediatelyRestoresOnlineOriginalMode(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> terminalDiscardImmediatelyRestoresOnlineOriginalModeBody(h));
    }

    private static void terminalDiscardImmediatelyRestoresOnlineOriginalModeBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "TA1Discard", GameType.ADVENTURE)) {
            fixture.dinosaur.discard();
            helper.assertTrue(fixture.dinosaur.isRemoved(), "The dinosaur was not actually discarded");
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "online discard");
            LOGGER.info("T_A_1_ONLINE_DISCARD restoredMode={} targetAlive={}",
                    fixture.target.gameMode.getGameModeForPlayer(), fixture.target.isAlive());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_ta1_online_death", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void terminalDeathImmediatelyRestoresOnlineModeAndKeepsExistingDeathLink(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> terminalDeathImmediatelyRestoresOnlineModeAndKeepsExistingDeathLinkBody(h));
    }

    private static void terminalDeathImmediatelyRestoresOnlineModeAndKeepsExistingDeathLinkBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "TA1OnlineDeath", GameType.ADVENTURE)) {
            helper.assertTrue(fixture.dinosaur.hurt(fixture.dinosaur.damageSources().genericKill(), Float.MAX_VALUE)
                            && fixture.dinosaur.isDeadOrDying(),
                    "Real lethal damage did not kill the online target's dinosaur");
            helper.assertTrue(fixture.target.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                            && fixture.target.isDeadOrDying() && fixture.target.getVehicle() == null
                            && !ScaryMonstersAddon.state(fixture.target).ownsFacet("possession")
                            && ScaryMonstersAddon.state(fixture.target).possessedDinosaur() == null
                            && ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null,
                    "Online dinosaur death did not restore mode before the existing linked player death");
            fixture.dinosaur.remove(Entity.RemovalReason.KILLED);
            LOGGER.info("T_A_1_ONLINE_DEATH restoredMode={} existingDeathLink={} recoveryCleared=true",
                    fixture.target.gameMode.getGameModeForPlayer(), fixture.target.isDeadOrDying());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_ta1_offline_death", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void offlineDinosaurDeathRecoversAdventureOnNativeLogin(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> offlineDinosaurDeathRecoversAdventureOnNativeLoginBody(h));
    }

    private static void offlineDinosaurDeathRecoversAdventureOnNativeLoginBody(GameTestHelper helper) {
        offlineTerminal(helper, true, GameType.ADVENTURE);
    }

    @GameTest(template = "empty", batch = "scary_ta1_offline_discard", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void offlineDinosaurDiscardRecoversCreativeOnNativeLogin(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> offlineDinosaurDiscardRecoversCreativeOnNativeLoginBody(h));
    }

    private static void offlineDinosaurDiscardRecoversCreativeOnNativeLoginBody(GameTestHelper helper) {
        offlineTerminal(helper, false, GameType.CREATIVE);
    }

    @GameTest(template = "empty", batch = "scary_ta1_legacy", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void legacyOfflineDinosaurImportsItsSavedModeAndRecoversAfterDeath(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> legacyOfflineDinosaurImportsItsSavedModeAndRecoversAfterDeathBody(h));
    }

    private static void legacyOfflineDinosaurImportsItsSavedModeAndRecoversAfterDeathBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "TA1Legacy", GameType.ADVENTURE)) {
            fixture.target.stopRiding();
            CompoundTag legacy = new CompoundTag();
            helper.assertTrue(fixture.dinosaur.save(legacy), "Could not serialize the legacy possession fixture");
            legacy.getCompound("NeoForgeData").remove("PossessionTracked");
            fixture.dinosaur.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            ScaryPossessionData.get(fixture.level).remove(fixture.target.getUUID(), fixture.dinosaur.getUUID());
            ScaryMonstersAddon.state(fixture.target).setPossessedDinosaur(null);
            fixture.target.close();
            Entity loaded = EntityType.loadEntityRecursive(legacy, fixture.level, entity -> entity);
            helper.assertTrue(loaded instanceof ScaryDinosaurEntity && fixture.level.addFreshEntity(loaded),
                    "The actual old-format dinosaur snapshot did not load");
            fixture.dinosaur = (ScaryDinosaurEntity) loaded;
            ScaryPossessionData.Possession imported = ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID());
            helper.assertTrue(imported != null && imported.legacy() && !imported.terminal()
                            && imported.originalMode() == GameType.ADVENTURE,
                    "The legacy dinosaur's original mode was not imported on the production join event");
            helper.assertTrue(fixture.dinosaur.hurt(fixture.dinosaur.damageSources().genericKill(), Float.MAX_VALUE)
                            && fixture.dinosaur.isDeadOrDying(), "The legacy dinosaur was not really killed");
            fixture.dinosaur.remove(Entity.RemovalReason.KILLED);
            reloadDurableRecovery(helper, fixture, GameType.ADVENTURE);
            fixture.reloginTarget();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "legacy offline death");
            helper.assertTrue(ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null,
                    "Legacy login left its consumed recovery record");
            LOGGER.info("T_A_1_LEGACY imported=true persisted=true restoredMode={}",
                    fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_ta1_unload", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void livingChunkAndMountedPlayerUnloadsDoNotReleasePossession(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> livingChunkAndMountedPlayerUnloadsDoNotReleasePossessionBody(h));
    }

    private static void livingChunkAndMountedPlayerUnloadsDoNotReleasePossessionBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "TA1Unload", GameType.ADVENTURE)) {
            fixture.target.stopRiding();
            CompoundTag snapshot = new CompoundTag();
            helper.assertTrue(fixture.dinosaur.save(snapshot), "Could not serialize living dinosaur before unload");
            UUID dinosaurUuid = fixture.dinosaur.getUUID();
            fixture.dinosaur.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.target.close();
            fixture.reloginTarget();
            helper.assertTrue(fixture.level.getEntity(dinosaurUuid) == null && fixture.target.isSpectator()
                            && ScaryMonstersAddon.state(fixture.target).ownsFacet("possession"),
                    "A living unloaded dinosaur was treated as a terminal removal on login");
            Entity restored = EntityType.loadEntityRecursive(snapshot, fixture.level, entity -> entity);
            helper.assertTrue(restored instanceof ScaryDinosaurEntity && fixture.level.addFreshEntity(restored),
                    "Could not reload the living dinosaur's actual snapshot");
            fixture.dinosaur = (ScaryDinosaurEntity) restored;
            helper.assertTrue(fixture.target.startRiding(fixture.dinosaur, true), "Could not remount loaded dinosaur");
            fixture.target.logoutWithMount();
            helper.assertTrue(fixture.level.getEntity(dinosaurUuid) == null
                            && fixture.dinosaur.getRemovalReason() == Entity.RemovalReason.UNLOADED_WITH_PLAYER,
                    "Native mounted logout did not unload the player's root vehicle");
            fixture.reloginTarget();
            Entity mounted = fixture.level.getEntity(dinosaurUuid);
            helper.assertTrue(mounted instanceof ScaryDinosaurEntity
                            && fixture.target.isSpectator() && fixture.target.getVehicle() == mounted,
                    "Native mounted login did not retain the living possession and vehicle");
            fixture.dinosaur = (ScaryDinosaurEntity) mounted;
            fixture.releaseNormally();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "release after both nonterminal unloads");
            LOGGER.info("T_A_1_UNLOAD chunkUnloadKept=true mountedLogoutKept=true finalMode={}",
                    fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_ta1_identity", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void normalReleaseAndStaleDinosaurCannotRestoreTwiceOrReleaseNewPossession(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> normalReleaseAndStaleDinosaurCannotRestoreTwiceOrReleaseNewPossessionBody(h));
    }

    private static void normalReleaseAndStaleDinosaurCannotRestoreTwiceOrReleaseNewPossessionBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "TA1Identity", GameType.ADVENTURE)) {
            fixture.target.stopRiding();
            CompoundTag staleSnapshot = new CompoundTag();
            helper.assertTrue(fixture.dinosaur.save(staleSnapshot), "Could not serialize the first possession");
            fixture.releaseNormally();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "normal release");
            helper.assertTrue(!ScaryMonstersAddon.state(fixture.target).serializeNBT(fixture.target.registryAccess())
                            .contains("PossessedDinosaur")
                            && ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null,
                    "Normal release kept the player's recovery identity");
            fixture.target.setGameMode(GameType.CREATIVE);
            fixture.teleportTargetBesideOwner();
            fixture.possess();
            Entity stale = EntityType.loadEntityRecursive(staleSnapshot, fixture.level, entity -> entity);
            helper.assertTrue(stale instanceof ScaryDinosaurEntity && fixture.level.addFreshEntity(stale),
                    "Could not reload the stale first dinosaur for the identity control");
            try {
                ScaryMonstersService.releasePossessedPlayer(fixture.target, (ScaryDinosaurEntity) stale, false);
                stale.discard();
                helper.assertTrue(fixture.target.isSpectator() && fixture.target.getVehicle() == fixture.dinosaur,
                        "The stale dinosaur released a different, newer possession");
                fixture.releaseNormally();
                assertReleased(helper, fixture.target, GameType.CREATIVE, "new possession release");
                fixture.target.setGameMode(GameType.SURVIVAL);
                ScaryMonstersService.releasePossessedPlayer(fixture.target, fixture.dinosaur, false);
                fixture.target.close();
                fixture.reloginTarget();
                assertReleased(helper, fixture.target, GameType.SURVIVAL, "no duplicate recovery after relog");
            }
            finally {
                stale.discard();
            }
            LOGGER.info("T_A_1_IDENTITY normalReleaseCleared=true staleIgnored=true relogMode={}",
                    fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm1_camouflage_online", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void camouflageKeepsOnlineCaptiveAndRemoveCamouflageBringsBackTheSamePossessedDinosaur(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> camouflageKeepsOnlineCaptiveAndRemoveCamouflageBringsBackTheSamePossessedDinosaurBody(h));
    }

    private static void camouflageKeepsOnlineCaptiveAndRemoveCamouflageBringsBackTheSamePossessedDinosaurBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM1Online", GameType.ADVENTURE)) {
            UUID dinosaurUuid = fixture.dinosaur.getUUID();
            ItemEntity stone = fixture.camouflage();
            fixture.assertCaptive("Camouflage");
            ScaryMonstersService.blocksToDinosaurs(fixture.owner);
            Entity reverted = fixture.level.getEntity(dinosaurUuid);
            helper.assertTrue(stone.isRemoved() && reverted instanceof ScaryDinosaurEntity
                            && reverted != fixture.dinosaur && fixture.target.getUUID().equals(
                                    ((ScaryDinosaurEntity) reverted).getPossessedPlayerUuid()),
                    "Remove Camouflage did not bring back the same possessed dinosaur");
            fixture.dinosaur = (ScaryDinosaurEntity) reverted;
            fixture.dinosaur.setNoGravity(true);
            fixture.assertCaptive("Remove Camouflage");
            fixture.releaseNormally();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "C8 after Remove Camouflage");
            helper.assertTrue(ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null,
                    "C8 after Remove Camouflage left the recovery record");
            LOGGER.info("P4_M1_SM_1_ONLINE captiveWhileStone=true sameDinosaur=true restoredMode={}",
                    fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm1_camouflage_offline", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void camouflageAndRemoveCamouflageKeepAnOfflineCaptiveBound(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> camouflageAndRemoveCamouflageKeepAnOfflineCaptiveBoundBody(h));
    }

    private static void camouflageAndRemoveCamouflageKeepAnOfflineCaptiveBoundBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM1Offline", GameType.ADVENTURE)) {
            UUID dinosaurUuid = fixture.dinosaur.getUUID();
            fixture.target.close();
            ItemEntity stone = fixture.camouflage();
            fixture.assertRecordKept("Camouflage while the captive is offline");
            ScaryMonstersService.blocksToDinosaurs(fixture.owner);
            Entity reverted = fixture.level.getEntity(dinosaurUuid);
            helper.assertTrue(stone.isRemoved() && reverted instanceof ScaryDinosaurEntity
                            && fixture.target.getUUID().equals(
                                    ((ScaryDinosaurEntity) reverted).getPossessedPlayerUuid()),
                    "Remove Camouflage did not bring back the offline captive's possessed dinosaur");
            fixture.dinosaur = (ScaryDinosaurEntity) reverted;
            fixture.dinosaur.setNoGravity(true);
            fixture.assertRecordKept("Remove Camouflage while the captive is offline");
            fixture.reloginTarget();
            fixture.assertCaptive("login after Remove Camouflage");
            fixture.releaseNormally();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "C8 after an offline camouflage");
            LOGGER.info("P4_M1_SM_1_OFFLINE recordKept=true sameDinosaur=true restoredMode={}",
                    fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm1_stone_destroyed", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void destroyedCamouflageStoneReleasesTheOnlineCaptiveAliveAtASafeSpot(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> destroyedCamouflageStoneReleasesTheOnlineCaptiveAliveAtASafeSpotBody(h));
    }

    private static void destroyedCamouflageStoneReleasesTheOnlineCaptiveAliveAtASafeSpotBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM1Destroyed", GameType.ADVENTURE)) {
            fixture.floor();
            UUID dinosaurUuid = fixture.dinosaur.getUUID();
            ItemEntity stone = fixture.camouflage();
            fixture.assertCaptive("Camouflage");
            Vec3 hover = fixture.origin.add(0.0D, 30.0D, 0.0D);
            fixture.target.teleportTo(hover.x, hover.y, hover.z);
            Vec3 stonePosition = stone.position();
            float health = fixture.target.getHealth();
            helper.assertTrue(stone.hurt(stone.damageSources().lava(), 100.0F) && stone.isRemoved(),
                    "Real item damage did not destroy the camouflage stone");
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "the stone burning up");
            helper.assertTrue(Math.abs(fixture.target.getHealth() - health) < 0.000001F
                            && ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null
                            && ScaryMonstersAddon.state(fixture.target).possessedDinosaur() == null,
                    "A destroyed stone hurt its captive or left the recovery identities behind");
            helper.assertTrue(fixture.target.position().distanceToSqr(stonePosition) < 0.000001D,
                    "The released captive was not put at the destroyed stone; actual=" + fixture.target.position()
                            + ", expected=" + stonePosition);
            ScaryMonstersService.blocksToDinosaurs(fixture.owner);
            helper.assertTrue(fixture.level.getEntity(dinosaurUuid) == null,
                    "A destroyed stone still brought its dinosaur back");

            fixture.possess();
            ItemEntity lost = fixture.camouflage();
            fixture.assertCaptive("the second Camouflage");
            fixture.target.teleportTo(hover.x, hover.y, hover.z);
            lost.setPos(lost.getX(), fixture.level.getMinBuildHeight() - 80.0D, lost.getZ());
            lost.discard();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "the stone falling out of the world");
            helper.assertTrue(fixture.target.getY() >= fixture.level.getMinBuildHeight()
                            && fixture.target.position().distanceToSqr(hover) > 1.0D,
                    "The captive of a stone lost below the world was left in the void or in mid-air; actual="
                            + fixture.target.position());
            LOGGER.info("P4_M1_SM_1_DESTROYED releasedAtStone=true fallbackSpot={} mode={}",
                    fixture.target.position(), fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm1_stone_destroyed_offline", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void destroyedCamouflageStoneReleasesAnOfflineCaptiveOnLogin(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> destroyedCamouflageStoneReleasesAnOfflineCaptiveOnLoginBody(h));
    }

    private static void destroyedCamouflageStoneReleasesAnOfflineCaptiveOnLoginBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM1DestroyedOff", GameType.ADVENTURE)) {
            fixture.floor();
            fixture.target.close();
            ItemEntity stone = fixture.camouflage();
            fixture.assertRecordKept("Camouflage while the captive is offline");
            Vec3 stonePosition = stone.position();
            stone.kill();
            ScaryPossessionData.Possession record =
                    ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID());
            helper.assertTrue(stone.isRemoved() && record != null && record.terminal()
                            && record.dinosaur().equals(fixture.dinosaur.getUUID()),
                    "A killed stone did not leave a terminal record for its offline captive");
            fixture.reloginTarget();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "login after the stone was killed");
            helper.assertTrue(ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null
                            && fixture.target.position().distanceToSqr(stonePosition) < 0.000001D,
                    "Login did not consume the record and put the captive at the killed stone; actual="
                            + fixture.target.position() + ", expected=" + stonePosition);
            LOGGER.info("P4_M1_SM_1_DESTROYED_OFFLINE terminal=true restoredMode={}",
                    fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm1_owner_death", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void ownerDeathReleasesTheOnlineCaptiveOfACamouflagedDinosaur(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> ownerDeathReleasesTheOnlineCaptiveOfACamouflagedDinosaurBody(h));
    }

    private static void ownerDeathReleasesTheOnlineCaptiveOfACamouflagedDinosaurBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM1OwnerDeath", GameType.ADVENTURE)) {
            UUID dinosaurUuid = fixture.dinosaur.getUUID();
            ItemEntity stone = fixture.camouflage();
            fixture.assertCaptive("Camouflage");
            Vec3 restoredPosition = stone.position().add(0.0D, 1.0D, 0.0D);
            float health = fixture.target.getHealth();
            helper.assertTrue(fixture.owner.hurt(fixture.owner.damageSources().genericKill(), Float.MAX_VALUE)
                            && fixture.owner.isDeadOrDying(),
                    "The production owner death event was not triggered");
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "owner death while camouflaged");
            helper.assertTrue(Math.abs(fixture.target.getHealth() - health) < 0.000001F
                            && fixture.target.position().distanceToSqr(restoredPosition) < 0.000001D
                            && stone.isRemoved()
                            && ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null,
                    "Owner death did not treat the camouflaged possessed dinosaur like a live one; position="
                            + fixture.target.position() + ", expected=" + restoredPosition
                            + ", stoneRemoved=" + stone.isRemoved());
            ScaryMonstersService.blocksToDinosaurs(fixture.owner);
            helper.assertTrue(fixture.level.getEntity(dinosaurUuid) == null,
                    "The stone of a released captive still brought its dinosaur back");
            LOGGER.info("P4_M1_SM_1_OWNER_DEATH restoredMode={} stoneRemoved={}",
                    fixture.target.gameMode.getGameModeForPlayer(), stone.isRemoved());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm1_owner_death_offline", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void ownerDeathKeepsTheStoneOfAnOfflineCaptive(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> ownerDeathKeepsTheStoneOfAnOfflineCaptiveBody(h));
    }

    private static void ownerDeathKeepsTheStoneOfAnOfflineCaptiveBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM1OwnerDeathOff", GameType.ADVENTURE)) {
            fixture.target.close();
            ItemEntity stone = fixture.camouflage();
            helper.assertTrue(fixture.owner.hurt(fixture.owner.damageSources().genericKill(), Float.MAX_VALUE)
                            && fixture.owner.isDeadOrDying(),
                    "The production owner death event was not triggered");
            helper.assertTrue(!stone.isRemoved() && stones(fixture.level, fixture.dinosaur.getUUID()).size() == 1,
                    "Owner death discarded the stone of an offline captive");
            fixture.assertRecordKept("owner death while camouflaged and offline");
            fixture.reloginTarget();
            fixture.assertCaptive("login after the owner died");
            LOGGER.info("P4_M1_SM_1_OWNER_DEATH_OFFLINE stoneKept=true recordKept=true");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_f1_stone_refused", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void camouflageStoneRefusedByTheLevelReleasesTheOnlineCaptive(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> camouflageStoneRefusedByTheLevelReleasesTheOnlineCaptiveBody(h));
    }

    private static void camouflageStoneRefusedByTheLevelReleasesTheOnlineCaptiveBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "F1Refused", GameType.ADVENTURE)) {
            fixture.floor();
            UUID dinosaurUuid = fixture.dinosaur.getUUID();
            Vec3 spot = fixture.dinosaur.position();
            float health = fixture.target.getHealth();
            int refused = fixture.camouflageWithStoneRefused();
            helper.assertTrue(refused == 1 && fixture.dinosaur.isRemoved()
                            && stones(fixture.level, dinosaurUuid).isEmpty(),
                    "The fixture did not refuse exactly the one camouflage stone; refused=" + refused);
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "a camouflage stone the level refused");
            helper.assertTrue(Math.abs(fixture.target.getHealth() - health) < 0.000001F
                            && ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null
                            && ScaryMonstersAddon.state(fixture.target).possessedDinosaur() == null,
                    "A refused stone hurt its captive or left the recovery identities behind");
            helper.assertTrue(fixture.target.position().distanceToSqr(spot) < 0.000001D,
                    "The released captive was not put where the dinosaur was camouflaged; actual="
                            + fixture.target.position() + ", expected=" + spot);
            LOGGER.info("F1_STONE_REFUSED online=true restoredMode={} spot={}",
                    fixture.target.gameMode.getGameModeForPlayer(), fixture.target.position());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_f1_stone_refused_offline", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void camouflageStoneRefusedByTheLevelReleasesAnOfflineCaptiveOnLogin(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> camouflageStoneRefusedByTheLevelReleasesAnOfflineCaptiveOnLoginBody(h));
    }

    private static void camouflageStoneRefusedByTheLevelReleasesAnOfflineCaptiveOnLoginBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "F1RefusedOff", GameType.ADVENTURE)) {
            fixture.floor();
            UUID dinosaurUuid = fixture.dinosaur.getUUID();
            Vec3 spot = fixture.dinosaur.position();
            fixture.target.close();
            int refused = fixture.camouflageWithStoneRefused();
            ScaryPossessionData.Possession record =
                    ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID());
            helper.assertTrue(refused == 1 && fixture.dinosaur.isRemoved()
                            && stones(fixture.level, dinosaurUuid).isEmpty(),
                    "The fixture did not refuse exactly the one camouflage stone; refused=" + refused);
            helper.assertTrue(record != null && record.terminal() && record.dinosaur().equals(dinosaurUuid),
                    "A refused stone did not leave a terminal record for its offline captive; record=" + record);
            fixture.reloginTarget();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "login after the level refused the stone");
            helper.assertTrue(ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null
                            && fixture.target.position().distanceToSqr(spot) < 0.000001D,
                    "Login did not consume the record and put the captive where the dinosaur was camouflaged;"
                            + " actual=" + fixture.target.position() + ", expected=" + spot);
            LOGGER.info("F1_STONE_REFUSED online=false terminal=true restoredMode={}",
                    fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm1_pickup", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void camouflageStoneIsNotPickedUpByAPlayerStandingOnIt(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> camouflageStoneIsNotPickedUpByAPlayerStandingOnItBody(h));
    }

    private static void camouflageStoneIsNotPickedUpByAPlayerStandingOnItBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM1Pickup", GameType.ADVENTURE)) {
            ItemEntity stone = fixture.camouflage();
            fixture.owner.moveTo(stone.position());
            stone.playerTouch(fixture.owner);
            helper.assertTrue(!stone.isRemoved() && stone.getItem().getCount() == 1
                            && !fixture.owner.getInventory().contains(new ItemStack(Items.STONE)),
                    "A player standing on the camouflage stone picked it up");
            CompoundTag saved = new CompoundTag();
            helper.assertTrue(stone.save(saved), "Could not serialize the camouflage stone");
            // ItemEntity stores both as shorts; any delay but 32767 counts down again after a reload.
            helper.assertTrue(saved.getShort("PickupDelay") == 32767 && saved.getShort("Age") == -32768,
                    "The stone's pickup protection or unlimited lifetime would not survive a reload; delay="
                            + saved.getShort("PickupDelay") + ", age=" + saved.getShort("Age"));
            LOGGER.info("P4_M1_SM_1_PICKUP pickedUp=false savedDelay={} savedAge={}",
                    saved.getShort("PickupDelay"), saved.getShort("Age"));
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm2_skip_possessed", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void dinosaurifySkipsAnAlreadyPossessedPlayerAndTakesTheNextNearestTarget(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> dinosaurifySkipsAnAlreadyPossessedPlayerAndTakesTheNextNearestTargetBody(h));
    }

    private static void dinosaurifySkipsAnAlreadyPossessedPlayerAndTakesTheNextNearestTargetBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM2Skip", GameType.ADVENTURE)) {
            ScaryMonstersStandEntity stand = ScaryMonstersService.activeStand(fixture.owner);
            helper.assertTrue(stand != null, "The fixture's Stand is not out");
            fixture.dinosaur.setNoAi(true);
            fixture.target.stopRiding();
            fixture.target.teleportTo(stand.getX() + fixture.frame.signX * 0.5D, stand.getY(), stand.getZ());
            Cow cow = EntityType.COW.create(fixture.level);
            helper.assertTrue(cow != null, "Missing the farther valid target");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(stand.position().add(fixture.frame.signX * 3.0D, 0.0D, 0.0D));
            helper.assertTrue(fixture.level.addFreshEntity(cow), "Could not add the farther valid target");
            try {
                ChunkPos standChunk = stand.chunkPosition();
                helper.assertTrue(standChunk.equals(cow.chunkPosition())
                                && fixture.level.isPositionEntityTicking(cow.blockPosition()),
                        "the farther target's chunk does not tick entities or is not the Stand's chunk: cow in "
                                + cow.chunkPosition() + ", Stand in " + standChunk);
                LOGGER.info("SCARY_SM2_COW standLocalX={} cowLocalX={} mirroredX={}",
                        stand.blockPosition().getX() & 15, cow.blockPosition().getX() & 15, fixture.frame.signX < 0);
                helper.assertTrue(fixture.target.isSpectator()
                                && stand.distanceToSqr(fixture.target) < stand.distanceToSqr(cow),
                        "The possessed Spectator must be the nearest candidate");
                ScaryMonstersService.dinosaurifyNearest(fixture.owner);
                long converted = ownedDinosaurs(fixture.level, fixture.owner.getUUID()).stream()
                        .filter(dinosaur -> dinosaur.getPossessedPlayerUuid() == null
                                && dinosaur.getSourceEntityNbt().hasUUID("UUID")
                                && cow.getUUID().equals(dinosaur.getSourceEntityNbt().getUUID("UUID")))
                        .count();
                helper.assertTrue(cow.isRemoved() && converted == 1L,
                        "Dinosaurify stopped at the already possessed player instead of the next nearest target;"
                                + " cowRemoved=" + cow.isRemoved() + ", converted=" + converted);
                fixture.assertCaptive("a later Dinosaurify");
                LOGGER.info("P4_M1_SM_2 possessedSkipped=true nextNearestConverted=true");
                helper.succeed();
            }
            finally {
                cow.discard();
                for (ScaryDinosaurEntity dinosaur : ownedDinosaurs(fixture.level, fixture.owner.getUUID())) {
                    if (dinosaur.getPossessedPlayerUuid() == null) dinosaur.discard();
                }
            }
        }
    }

    @GameTest(template = "empty", batch = "scary_sm3_diverged_record", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void recoveryRecordItsPlayerNoLongerMatchesIsResolvedOnLogin(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> recoveryRecordItsPlayerNoLongerMatchesIsResolvedOnLoginBody(h));
    }

    private static void recoveryRecordItsPlayerNoLongerMatchesIsResolvedOnLoginBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM3Diverged", GameType.ADVENTURE)) {
            fixture.floor();
            ScaryPossessionData recoveries = ScaryPossessionData.get(fixture.level);
            UUID targetUuid = fixture.target.getUUID();

            // The attachment half is lost (reset player data) and an admin has already given the mode back.
            fixture.target.stopRiding();
            forgetPossession(fixture.target);
            fixture.target.setGameMode(GameType.SURVIVAL);
            fixture.target.close();
            fixture.reloginTarget();
            helper.assertTrue(recoveries.get(targetUuid) == null,
                    "A live record its free player no longer matches was kept on login, so Dinosaurify would"
                            + " refuse that player for good");
            helper.assertTrue(fixture.target.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                            && !fixture.dinosaur.isRemoved() && fixture.dinosaur.getPossessedPlayerUuid() == null,
                    "Dropping the stale record changed the free player's mode or left the dinosaur bound");
            fixture.dinosaur.discard();

            // Taken again, the attachment half is lost again, and the dinosaur ends while the player is offline.
            fixture.target.setGameMode(GameType.ADVENTURE);
            fixture.teleportTargetBesideOwner();
            fixture.possess();
            fixture.target.stopRiding();
            forgetPossession(fixture.target);
            fixture.target.close();
            fixture.dinosaur.discard();
            ScaryPossessionData.Possession terminal = recoveries.get(targetUuid);
            helper.assertTrue(terminal != null && terminal.terminal(),
                    "The offline discard did not leave a terminal record");
            fixture.reloginTarget();
            helper.assertTrue(recoveries.get(targetUuid) == null,
                    "A terminal record its player no longer matches was not consumed on login");
            helper.assertTrue(fixture.target.isAlive()
                            && fixture.target.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                            && ScaryMonstersAddon.state(fixture.target).possessedDinosaur() == null,
                    "The saved mode held only by the terminal record was not given back; actual="
                            + fixture.target.gameMode.getGameModeForPlayer());

            fixture.teleportTargetBesideOwner();
            fixture.possess();
            LOGGER.info("P4_M1_SM_3 staleLiveRecordDropped=true terminalRecordConsumed=true possessableAgain=true");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "scary_sm4_offline_spot", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void offlineTerminalRecoveryPutsThePlayerAtTheDinosaursLastSpot(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> offlineTerminalRecoveryPutsThePlayerAtTheDinosaursLastSpotBody(h));
    }

    private static void offlineTerminalRecoveryPutsThePlayerAtTheDinosaursLastSpotBody(GameTestHelper helper) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "SM4Spot", GameType.ADVENTURE)) {
            fixture.floor();
            Vec3 hover = fixture.origin.add(0.0D, 30.0D, 0.0D);
            fixture.dinosaur.setNoAi(true);
            fixture.target.stopRiding();
            fixture.target.teleportTo(hover.x, hover.y, hover.z);
            fixture.target.close();
            Vec3 lastSpot = fixture.dinosaur.position().add(0.0D, 1.0D, 0.0D);
            fixture.dinosaur.discard();
            fixture.reloginTarget();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "login after an offline discard");
            helper.assertTrue(fixture.target.position().distanceToSqr(lastSpot) < 0.000001D,
                    "Login recovery left the released player at the Spectator's logout spot; actual="
                            + fixture.target.position() + ", expected=" + lastSpot);

            fixture.possess();
            fixture.dinosaur.setNoAi(true);
            fixture.target.stopRiding();
            fixture.target.teleportTo(hover.x, hover.y, hover.z);
            fixture.target.close();
            fixture.dinosaur.setPos(fixture.dinosaur.getX(), fixture.level.getMinBuildHeight() - 80.0D,
                    fixture.dinosaur.getZ());
            fixture.dinosaur.discard();
            fixture.reloginTarget();
            assertReleased(helper, fixture.target, GameType.ADVENTURE, "login after the dinosaur fell out of the world");
            helper.assertTrue(fixture.target.getY() >= fixture.level.getMinBuildHeight()
                            && fixture.target.position().distanceToSqr(hover) > 1.0D,
                    "Login recovery put the released player in the void or left them in mid-air; actual="
                            + fixture.target.position());
            LOGGER.info("P4_M1_SM_4 lastSpot={} fallbackSpot={}", lastSpot, fixture.target.position());
            helper.succeed();
        }
    }

    private static void forgetPossession(ServerPlayer player) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        state.clearAll();
        state.setPossessedDinosaur(null);
    }

    private static List<ItemEntity> stones(ServerLevel level, UUID dinosaur) {
        return snapshotEntities(level).stream()
                .filter(entity -> entity instanceof ItemEntity && entity.isAlive())
                .map(entity -> (ItemEntity) entity)
                .filter(item -> {
                    CompoundTag snapshot = item.getPersistentData().getCompound("DinoSourceNBT");
                    return snapshot.hasUUID("UUID") && dinosaur.equals(snapshot.getUUID("UUID"));
                }).toList();
    }

    private static void offlineTerminal(GameTestHelper helper, boolean killed, GameType originalMode) {
        try (PossessionFixture fixture = new PossessionFixture(helper, "TA1Terminal", originalMode)) {
            fixture.target.close();
            helper.assertTrue(fixture.level.getServer().getPlayerList().getPlayer(fixture.target.getUUID()) == null,
                    "The terminal recovery target is still online");
            if (killed) {
                helper.assertTrue(fixture.dinosaur.hurt(fixture.dinosaur.damageSources().genericKill(), Float.MAX_VALUE)
                                && fixture.dinosaur.isDeadOrDying(),
                        "Real lethal damage did not kill the offline target's dinosaur");
                fixture.dinosaur.remove(Entity.RemovalReason.KILLED);
            }
            else {
                fixture.dinosaur.discard();
            }
            helper.assertTrue(fixture.dinosaur.isRemoved()
                            && fixture.level.getEntity(fixture.dinosaur.getUUID()) == null,
                    "Terminal removal did not remove the dinosaur from the actual level");
            reloadDurableRecovery(helper, fixture, originalMode);
            fixture.reloginTarget();
            assertReleased(helper, fixture.target, originalMode, "offline " + (killed ? "death" : "discard"));
            helper.assertTrue(ScaryPossessionData.get(fixture.level).get(fixture.target.getUUID()) == null
                            && ScaryMonstersAddon.state(fixture.target).possessedDinosaur() == null,
                    "Native login did not consume both durable recovery identities");
            fixture.target.setGameMode(GameType.SURVIVAL);
            fixture.target.close();
            fixture.reloginTarget();
            assertReleased(helper, fixture.target, GameType.SURVIVAL, "second login must not restore again");
            LOGGER.info("T_A_1_OFFLINE_TERMINAL killed={} originalMode={} secondLoginMode={}",
                    killed, originalMode, fixture.target.gameMode.getGameModeForPlayer());
            helper.succeed();
        }
    }

    private static void reloadDurableRecovery(GameTestHelper helper, PossessionFixture fixture, GameType originalMode) {
        DimensionDataStorage storage = fixture.level.getServer().overworld().getDataStorage();
        storage.save();
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        try {
            CompoundTag onDisk = storage.readTagFromDisk("scary_monsters_possessions", null, 0).getCompound("data");
            ScaryPossessionData decoded = ScaryPossessionData.load(onDisk, fixture.level.registryAccess());
            ScaryPossessionData.Possession entry = decoded.get(fixture.target.getUUID());
            helper.assertTrue(entry != null && entry.terminal() && entry.originalMode() == originalMode
                            && entry.dinosaur().equals(fixture.dinosaur.getUUID()),
                    "Original mode, dinosaur identity and terminal recovery did not survive actual SavedData disk I/O");
            storage.set("scary_monsters_possessions", decoded);
            helper.assertTrue(ScaryPossessionData.get(fixture.level) == decoded,
                    "The next real login would not use the disk-decoded recovery data");
            LOGGER.info("T_A_1_DURABLE originalMode={} terminal={} dinosaur={} decodedObjectUsed=true",
                    entry.originalMode(), entry.terminal(), entry.dinosaur());
        }
        catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not reload the production SavedData file", exception);
        }
    }

    private static void assertFarPersistence(GameTestHelper helper, PossessionFixture fixture,
            ScaryDinosaurEntity control, int initialTicks, String phase) {
        Entity nearest = fixture.level.getNearestPlayer(fixture.dinosaur, -1.0D);
        Entity controlNearest = fixture.level.getNearestPlayer(control, -1.0D);
        boolean dinosaurTicking = fixture.level.isPositionEntityTicking(fixture.dinosaur.blockPosition());
        boolean controlTicking = fixture.level.isPositionEntityTicking(control.blockPosition());
        LOGGER.info("T_A_1_FAR phase={} dinosaurRemoved={} tickDelta={} controlRemoved={} nearestDistance={} "
                        + "dinosaurTicking={} controlTicking={} controlTicks={} controlNearest={} controlNearestDistance={}",
                phase, fixture.dinosaur.isRemoved(), fixture.dinosaur.tickCount - initialTicks,
                control.isRemoved(), nearest == null ? null : Math.sqrt(nearest.distanceToSqr(fixture.dinosaur)),
                dinosaurTicking, controlTicking, control.tickCount,
                controlNearest == null ? null : controlNearest.getUUID(),
                controlNearest == null ? null : Math.sqrt(controlNearest.distanceToSqr(control)));
        helper.assertTrue(nearest == fixture.owner && nearest.distanceToSqr(fixture.dinosaur) > 128.0D * 128.0D
                        && controlNearest == fixture.owner && controlNearest.distanceToSqr(control) > 128.0D * 128.0D,
                "The actual nearest non-Spectator player was not the far owner");
        helper.assertTrue(dinosaurTicking && controlTicking && control.tickCount > 0,
                "Both fixture chunks must remain entity-ticking after a natural near-owner control tick");
        helper.assertTrue(control.isRemoved() && control.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                "The nonpossessed control did not naturally despawn in the ticking world");
        helper.assertTrue(!fixture.dinosaur.isRemoved() && fixture.dinosaur.isAlive()
                        && fixture.dinosaur.tickCount - initialTicks >= 4,
                "The possessed " + phase + " dinosaur despawned or its world was not actually ticking");
    }

    private static void assertReleased(GameTestHelper helper, ServerPlayer target, GameType expected, String phase) {
        helper.assertTrue(target.isAlive() && target.gameMode.getGameModeForPlayer() == expected
                        && target.getVehicle() == null && !target.hasEffect(MobEffects.INVISIBILITY)
                        && !ScaryMonstersAddon.state(target).ownsFacet("possession"),
                "Possession was not completely released with its original mode on " + phase
                        + "; actual=" + target.gameMode.getGameModeForPlayer() + ", expected=" + expected);
    }

    @GameTest(template = "empty", batch = "scary_m5_riding", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void ownerDeathReleasesOnlinePossessedRider(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> ownerDeathReleasesOnlinePossessedRiderBody(h));
    }

    private static void ownerDeathReleasesOnlinePossessedRiderBody(GameTestHelper helper) {
        ownerDeath(helper, false);
    }

    @GameTest(template = "empty", batch = "scary_m5_dismounted", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void ownerDeathReleasesDismountedPlayerAndPreservesExternalInvisibility(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> ownerDeathReleasesDismountedPlayerAndPreservesExternalInvisibilityBody(h));
    }

    private static void ownerDeathReleasesDismountedPlayerAndPreservesExternalInvisibilityBody(GameTestHelper helper) {
        ownerDeath(helper, true);
    }

    @GameTest(template = "empty", batch = "scary_r642_offline", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void ownerDeathKeepsOfflineDismountedTargetRecoverableAfterLogin(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> ownerDeathKeepsOfflineDismountedTargetRecoverableAfterLoginBody(h));
    }

    private static void ownerDeathKeepsOfflineDismountedTargetRecoverableAfterLoginBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OnlinePlayer owner = null;
        OnlinePlayer target = null;
        OnlinePlayer recoveryOwner = null;
        StandPower power = null;
        ScaryDinosaurEntity dinosaur = null;
        try {
            Frame frame = frame(helper);
            owner = onlinePlayer(helper, "ScaryR642Owner", frame.at(0.0D, 0.0D, 0.0D), GameType.SURVIVAL);
            target = onlinePlayer(helper, "ScaryR642Target", frame.at(1.0D, 0.0D, 0.0D),
                    GameType.ADVENTURE);
            recoveryOwner = onlinePlayer(helper, "ScaryR642Revert", frame.at(6.0D, 0.0D, 3.0D),
                    GameType.SURVIVAL);
            power = PowerClass.STAND.attachGet(owner);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Scary Monsters to the offline-target owner");
            helper.assertTrue(ScaryMonstersAddon.SCARY_MONSTERS.get().summon(owner, power)
                            && ScaryMonstersService.activeStand(owner) != null,
                    "Production Scary Monsters summon failed for the offline-target test");
            helper.assertTrue(StandPowerTransitions.insert(PowerClass.STAND.attachGet(recoveryOwner),
                            new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Scary Monsters to the later recovery user");

            ScaryMonstersService.dinosaurifyNearest(owner);
            UUID targetUuid = target.getUUID();
            List<ScaryDinosaurEntity> possessed = ownedDinosaurs(level, owner.getUUID()).stream()
                    .filter(candidate -> targetUuid.equals(candidate.getPossessedPlayerUuid())).toList();
            helper.assertTrue(possessed.size() == 1, "Production conversion did not bind the offline target");
            dinosaur = possessed.getFirst();
            helper.assertTrue(target.isSpectator() && target.getVehicle() == dinosaur
                            && target.hasEffect(MobEffects.INVISIBILITY)
                            && ScaryMonstersAddon.state(target).ownsFacet("possession")
                            && dinosaur.getPersistentData().getInt("PlayerGameMode") == GameType.ADVENTURE.getId(),
                    "Offline-target fixture did not establish real possession and saved Adventure mode");
            dinosaur.setNoAi(true);
            dinosaur.setNoGravity(true);
            dinosaur.moveTo(frame.at(4.0D, 0.0D, 2.0D));
            Vec3 restoredPosition = dinosaur.position().add(0.0D, 1.0D, 0.0D);
            target.stopRiding();
            Vec3 dismountSpot = frame.at(5.0D, 0.0D, 3.0D);
            target.teleportTo(dismountSpot.x, dismountSpot.y, dismountSpot.z);
            GameProfile targetProfile = target.getGameProfile();
            float targetHealth = target.getHealth();
            target.close();
            helper.assertTrue(level.getServer().getPlayerList().getPlayer(targetUuid) == null
                            && level.getEntity(targetUuid) == null
                            && !dinosaur.isRemoved() && dinosaur.getPassengers().isEmpty(),
                    "Native dismounted logout did not leave an offline target and surviving dinosaur");

            helper.assertTrue(owner.hurt(owner.damageSources().genericKill(), Float.MAX_VALUE)
                            && owner.isDeadOrDying(),
                    "Production owner death event was not triggered for the offline target");
            LOGGER.info("R642_OFFLINE_OWNER_DEATH targetOnline={} dinosaurRemoved={} savedMode={}",
                    level.getServer().getPlayerList().getPlayer(targetUuid) != null,
                    dinosaur.isRemoved(), dinosaur.getPersistentData().getInt("PlayerGameMode"));
            helper.assertTrue(!dinosaur.isRemoved() && level.getEntity(dinosaur.getUUID()) == dinosaur
                            && targetUuid.equals(dinosaur.getPossessedPlayerUuid())
                            && dinosaur.getPersistentData().getInt("PlayerGameMode") == GameType.ADVENTURE.getId(),
                    "Owner death discarded the offline target's recovery dinosaur or saved game mode");

            recoveryOwner.lookAt(EntityAnchorArgument.Anchor.EYES, dinosaur.position());
            ScaryMonstersService.revertLookedAtDinosaur(recoveryOwner);
            LOGGER.info("R642_OFFLINE_C8 targetOnline={} dinosaurRemoved={} savedMode={}",
                    level.getServer().getPlayerList().getPlayer(targetUuid) != null,
                    dinosaur.isRemoved(), dinosaur.getPersistentData().getInt("PlayerGameMode"));
            helper.assertTrue(!dinosaur.isRemoved() && level.getEntity(dinosaur.getUUID()) == dinosaur
                            && targetUuid.equals(dinosaur.getPossessedPlayerUuid())
                            && dinosaur.getPersistentData().getInt("PlayerGameMode") == GameType.ADVENTURE.getId(),
                    "C8 discarded the offline target's recovery dinosaur or saved game mode");

            // Re-enter through PlayerList with the same profile, loading the actual logout save.
            target = new OnlinePlayer(level, targetProfile);
            target.login();
            helper.assertTrue(level.getServer().getPlayerList().getPlayer(targetUuid) == target
                            && level.getEntity(targetUuid) == target
                            && target.isSpectator() && target.getVehicle() == null
                            && target.hasEffect(MobEffects.INVISIBILITY)
                            && ScaryMonstersAddon.state(target).ownsFacet("possession"),
                    "Native relog did not restore the saved, still-recoverable possession");
            recoveryOwner.lookAt(EntityAnchorArgument.Anchor.EYES, dinosaur.position());
            ScaryMonstersService.revertLookedAtDinosaur(recoveryOwner);
            helper.assertTrue(target.isAlive() && Math.abs(target.getHealth() - targetHealth) < 0.000001F
                            && target.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                            && target.getVehicle() == null
                            && target.position().distanceToSqr(restoredPosition) < 0.000001D
                            && !target.hasEffect(MobEffects.INVISIBILITY)
                            && !ScaryMonstersAddon.state(target).ownsFacet("possession")
                            && dinosaur.isRemoved() && level.getEntity(dinosaur.getUUID()) == null,
                    "C8 after relog did not release the target before discarding the retained dinosaur");
            LOGGER.info("R642_OFFLINE_RECOVERY originalMode={} restoredMode={} targetAlive={} dinosaurDiscarded={}",
                    GameType.ADVENTURE, target.gameMode.getGameModeForPlayer(), target.isAlive(), dinosaur.isRemoved());
            helper.succeed();
        }
        finally {
            if (dinosaur != null && !dinosaur.isRemoved()) {
                if (target != null && level.getServer().getPlayerList().getPlayer(target.getUUID()) == target) {
                    ScaryMonstersService.releasePossessedPlayer(target, dinosaur, false);
                }
                dinosaur.discard();
            }
            if (owner != null && power != null && power.isSummoned()) {
                ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(owner, power);
            }
            for (OnlinePlayer player : new OnlinePlayer[] { recoveryOwner, target, owner }) {
                if (player != null) player.close();
            }
        }
    }

    private static void ownerDeath(GameTestHelper helper, boolean dismounted) {
        ServerLevel level = helper.getLevel();
        OnlinePlayer owner = null;
        OnlinePlayer target = null;
        OnlinePlayer otherOwner = null;
        StandPower power = null;
        List<UUID> sourceUuids = new ArrayList<>();
        try {
            Frame frame = frame(helper);
            owner = onlinePlayer(helper, "ScaryM5Owner", frame.at(0.0D, 0.0D, 0.0D), GameType.SURVIVAL);
            target = onlinePlayer(helper, "ScaryM5Target", frame.at(6.0D, 0.0D, 0.0D),
                    dismounted ? GameType.ADVENTURE : GameType.SURVIVAL);
            otherOwner = onlinePlayer(helper, "ScaryM5Control", frame.at(6.0D, 0.0D, 3.0D),
                    GameType.SURVIVAL);
            GameType originalMode = target.gameMode.getGameModeForPlayer();

            power = PowerClass.STAND.attachGet(owner);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant the registered Scary Monsters Stand");
            helper.assertTrue(ScaryMonstersAddon.SCARY_MONSTERS.get().summon(owner, power)
                            && ScaryMonstersService.activeStand(owner) != null,
                    "Production Scary Monsters summon failed");

            Cow original = EntityType.COW.create(level);
            helper.assertTrue(original != null, "Missing NPC source fixture");
            original.setNoAi(true);
            original.setNoGravity(true);
            original.setCustomName(Component.literal("M5 original cow"));
            original.setHealth(7.0F);
            original.moveTo(frame.at(1.0D, 0.0D, 0.0D));
            sourceUuids.add(original.getUUID());
            helper.assertTrue(level.addFreshEntity(original), "Could not add NPC source fixture");
            ScaryMonstersService.dinosaurifyNearest(owner);
            List<ScaryDinosaurEntity> npcMatches = ownedDinosaurs(level, owner.getUUID()).stream()
                    .filter(dinosaur -> dinosaur.getPossessedPlayerUuid() == null
                            && original.getUUID().equals(dinosaur.getSourceEntityNbt().getUUID("UUID")))
                    .toList();
            helper.assertTrue(original.isRemoved() && npcMatches.size() == 1,
                    "Production NPC conversion did not retain its source snapshot");
            ScaryDinosaurEntity npcDinosaur = npcMatches.getFirst();
            npcDinosaur.setNoAi(true);
            npcDinosaur.setNoGravity(true);
            npcDinosaur.moveTo(frame.at(3.0D, 0.0D, 3.0D));
            Vec3 npcRestorePosition = npcDinosaur.position();

            target.moveTo(frame.at(1.0D, 0.0D, 0.0D));
            CompoundTag externalBeforeConversion = null;
            Tag externalInvisibility = null;
            if (dismounted) {
                helper.assertTrue(target.addEffect(new MobEffectInstance(
                        MobEffects.INVISIBILITY, 1200, 1, true, true, false)),
                        "Could not add external invisibility control");
                MobEffectInstance external = target.getEffect(MobEffects.INVISIBILITY);
                helper.assertTrue(external.hiddenEffect == null, "External fixture unexpectedly has a hidden layer");
                ScaryMonstersOwnedEffects.ensureIdentities(external);
                externalBeforeConversion = (CompoundTag) external.save();
            }
            ScaryMonstersService.dinosaurifyNearest(owner);
            UUID targetUuid = target.getUUID();
            List<ScaryDinosaurEntity> possessed = ownedDinosaurs(level, owner.getUUID()).stream()
                    .filter(dinosaur -> targetUuid.equals(dinosaur.getPossessedPlayerUuid())).toList();
            helper.assertTrue(possessed.size() == 1, "Production player conversion did not bind a dinosaur");
            ScaryDinosaurEntity dinosaur = possessed.getFirst();
            helper.assertTrue(target.isSpectator() && target.getVehicle() == dinosaur
                            && target.hasEffect(MobEffects.INVISIBILITY)
                            && ScaryMonstersAddon.state(target).ownsFacet("possession"),
                    "Production conversion did not establish real possession state");
            helper.assertTrue(dinosaur.getPersistentData().getInt("PlayerGameMode") == originalMode.getId(),
                    "Possession did not save the original game mode");
            if (externalBeforeConversion != null) {
                externalInvisibility = expectedExternalRelease(helper, target, externalBeforeConversion);
            }
            dinosaur.setNoAi(true);
            dinosaur.setNoGravity(true);
            dinosaur.moveTo(frame.at(4.0D, 0.0D, 2.0D));
            Vec3 restoredPosition = dinosaur.position().add(0.0D, 1.0D, 0.0D);
            if (dismounted) {
                target.stopRiding();
                Vec3 dismountSpot = frame.at(5.0D, 0.0D, 3.0D);
                target.teleportTo(dismountSpot.x, dismountSpot.y, dismountSpot.z);
                helper.assertTrue(target.getVehicle() == null && target.isSpectator()
                                && ScaryMonstersAddon.state(target).ownsFacet("possession"),
                        "Dismount ended the possession before the owner-death path");
            }

            ScaryDinosaurEntity controlled = sourceBackedControl(
                    helper, owner.getUUID(), true, frame.at(5.0D, 0.0D, 0.0D), sourceUuids);
            ScaryDinosaurEntity otherOwned = sourceBackedControl(
                    helper, otherOwner.getUUID(), false, frame.at(3.0D, 0.0D, 0.0D), sourceUuids);
            CompoundTag controlledSource = controlled.getSourceEntityNbt().copy();
            CompoundTag otherSource = otherOwned.getSourceEntityNbt().copy();
            ScaryDinosaurEntity unrestorable = ScaryMonstersAddon.DINOSAUR.get().create(level);
            helper.assertTrue(unrestorable != null, "Missing unrestorable NPC control");
            unrestorable.setOwnerUuid(owner.getUUID());
            unrestorable.setNoAi(true);
            unrestorable.setNoGravity(true);
            unrestorable.moveTo(frame.at(6.0D, 0.0D, 2.0D));
            helper.assertTrue(level.addFreshEntity(unrestorable), "Could not add unrestorable NPC control");

            helper.assertTrue(owner.server.getPlayerList().getPlayer(owner.getUUID()) == owner
                            && owner.server.getPlayerList().getPlayer(target.getUUID()) == target
                            && owner.server.getPlayerList().getPlayer(otherOwner.getUUID()) == otherOwner,
                    "Owner-death fixtures must be online in PlayerList, not only in ServerLevel");
            float targetHealth = target.getHealth();
            helper.assertTrue(owner.hurt(owner.damageSources().genericKill(), Float.MAX_VALUE)
                            && owner.isDeadOrDying(),
                    "The production owner death event was not triggered");
            helper.assertTrue(target.isAlive() && Math.abs(target.getHealth() - targetHealth) < 0.000001F,
                    "Owner death killed or damaged the possessed player");
            helper.assertTrue(target.gameMode.getGameModeForPlayer() == originalMode && !target.isSpectator()
                            && target.getVehicle() == null
                            && target.position().distanceToSqr(restoredPosition) < 0.000001D,
                    "Owner death did not restore mode, dismount and teleport the online player");
            helper.assertTrue(!ScaryMonstersAddon.state(target).ownsFacet("possession")
                            && dinosaur.isRemoved() && level.getEntity(dinosaur.getUUID()) == null,
                    "Owner death did not release possession before discarding its dinosaur");
            if (externalInvisibility != null) {
                LOGGER.info("M5_EXTERNAL_RELEASE actual={} expected={}",
                        target.hasEffect(MobEffects.INVISIBILITY)
                                ? target.getEffect(MobEffects.INVISIBILITY).save() : null,
                        externalInvisibility);
                helper.assertTrue(target.hasEffect(MobEffects.INVISIBILITY)
                                && externalInvisibility.equals(target.getEffect(MobEffects.INVISIBILITY).save()),
                        "Owner-death release erased or changed external invisibility");
            }
            else {
                helper.assertTrue(!target.hasEffect(MobEffects.INVISIBILITY),
                        "Owner-death release retained possession-owned invisibility");
            }
            Entity restored = level.getEntity(original.getUUID());
            helper.assertTrue(restored instanceof Cow cow && cow.isAlive()
                            && Math.abs(cow.getHealth() - 7.0F) < 0.000001F
                            && Component.literal("M5 original cow").equals(cow.getCustomName())
                            && cow.position().distanceToSqr(npcRestorePosition) < 0.000001D
                            && npcDinosaur.isRemoved(),
                    "Owner death regressed the existing NPC snapshot restoration");
            helper.assertTrue(!controlled.isRemoved() && !otherOwned.isRemoved()
                            && level.getEntity(controlled.getUUID()) == controlled
                            && level.getEntity(otherOwned.getUUID()) == otherOwned
                            && controlledSource.equals(controlled.getSourceEntityNbt())
                            && otherSource.equals(otherOwned.getSourceEntityNbt()),
                    "Owner death reverted a player-controlled or other-owner dinosaur");
            helper.assertTrue(!unrestorable.isRemoved() && unrestorable.getSourceEntityNbt().isEmpty(),
                    "Owner death discarded the NPC whose snapshot could not be restored");
            LOGGER.info("M5_OWNER_DEATH online=true dismounted={} originalMode={} restoredMode={} targetAlive={} dinosaurDiscarded={}",
                    dismounted, originalMode, target.gameMode.getGameModeForPlayer(),
                    target.isAlive(), dinosaur.isRemoved());
            helper.succeed();
        }
        finally {
            for (Entity entity : snapshotEntities(level)) {
                if (entity instanceof ScaryDinosaurEntity dinosaur
                        && ((owner != null && owner.getUUID().equals(dinosaur.getOwnerUuid()))
                                || (otherOwner != null && otherOwner.getUUID().equals(dinosaur.getOwnerUuid())))) {
                    UUID playerUuid = dinosaur.getPossessedPlayerUuid();
                    ServerPlayer player = playerUuid != null
                            ? level.getServer().getPlayerList().getPlayer(playerUuid) : null;
                    if (player != null) ScaryMonstersService.releasePossessedPlayer(player, dinosaur, false);
                    dinosaur.discard();
                }
            }
            for (UUID uuid : sourceUuids) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) entity.discard();
            }
            if (owner != null && power != null && power.isSummoned()) {
                ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(owner, power);
            }
            for (OnlinePlayer player : new OnlinePlayer[] { otherOwner, target, owner }) {
                if (player != null) player.close();
            }
        }
    }

    // How far, in blocks, a fixture entity stands from its frame's origin along either axis.
    private static final int FRAME_REACH = 6;

    /**
     * The 1x1x1 template makes GameTest wait for its own chunk only. A neighbouring chunk may not be loaded yet, and
     * an entity standing in it is invisible to area queries such as Dinosaurify's, so every fixture entity stays
     * inside the template's chunk, whatever the grid origin of the run. The fixture sits two blocks from the
     * template (outside the barriers GameTest puts around it); offsets are non-negative and are mirrored to the
     * other side of the template when the chunk edge is closer on this one.
     */
    static Frame frame(GameTestHelper helper) {
        BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
        int signX = (template.getX() & 15) + 2 + FRAME_REACH > 15 ? -1 : 1;
        int signZ = (template.getZ() & 15) + 2 + FRAME_REACH > 15 ? -1 : 1;
        Vec3 origin = new Vec3(template.getX() + 0.5D + 2 * signX, template.getY() + 0.5D,
                template.getZ() + 0.5D + 2 * signZ);
        Frame frame = new Frame(origin, signX, signZ);
        ChunkPos chunk = new ChunkPos(template);
        helper.assertTrue(chunk.equals(new ChunkPos(BlockPos.containing(origin)))
                        && chunk.equals(new ChunkPos(BlockPos.containing(frame.at(FRAME_REACH, 0, FRAME_REACH)))),
                "the fixture stands outside the chunk of its test origin: " + origin + " is not in " + chunk);
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(BlockPos.containing(origin)),
                "the fixture's chunk does not tick entities: " + chunk);
        LOGGER.info("SCARY_FIXTURE_FRAME templateLocal=({}, {}) mirroredX={} mirroredZ={}",
                template.getX() & 15, template.getZ() & 15, signX < 0, signZ < 0);
        return frame;
    }

    static final class Frame {
        final Vec3 origin;
        final int signX;
        final int signZ;

        Frame(Vec3 origin, int signX, int signZ) {
            this.origin = origin;
            this.signX = signX;
            this.signZ = signZ;
        }

        Vec3 at(double x, double y, double z) {
            return origin.add(signX * x, y, signZ * z);
        }
    }

    static OnlinePlayer onlinePlayer(
            GameTestHelper helper, String name, Vec3 position, GameType mode) {
        OnlinePlayer player = new OnlinePlayer(helper.getLevel(), name);
        try {
            player.login();
            player.moveTo(position);
            player.setGameMode(mode);
            helper.assertTrue(player.serverLevel() == helper.getLevel()
                            && helper.getLevel().getEntity(player.getUUID()) == player
                            && player.server.getPlayerList().getPlayer(player.getUUID()) == player,
                    "Native PlayerList login did not register " + name);
            return player;
        }
        catch (RuntimeException | Error exception) {
            player.close();
            throw exception;
        }
    }

    private static ScaryDinosaurEntity sourceBackedControl(
            GameTestHelper helper, UUID owner, boolean controlled, Vec3 position, List<UUID> sourceUuids) {
        Cow source = EntityType.COW.create(helper.getLevel());
        ScaryDinosaurEntity dinosaur = ScaryMonstersAddon.DINOSAUR.get().create(helper.getLevel());
        helper.assertTrue(source != null && dinosaur != null, "Missing exclusion control fixtures");
        CompoundTag snapshot = new CompoundTag();
        helper.assertTrue(source.save(snapshot), "Could not serialize exclusion control source");
        sourceUuids.add(source.getUUID());
        dinosaur.setSourceEntityNbt(snapshot);
        dinosaur.setOwnerUuid(owner);
        dinosaur.setPlayerControlled(controlled);
        dinosaur.setNoAi(true);
        dinosaur.setNoGravity(true);
        dinosaur.moveTo(position);
        helper.assertTrue(helper.getLevel().addFreshEntity(dinosaur), "Could not add exclusion control");
        return dinosaur;
    }

    private static CompoundTag expectedExternalRelease(
            GameTestHelper helper, ServerPlayer target, CompoundTag beforeConversion) {
        ListTag layers = ScaryMonstersAddon.state(target).serializeNBT(target.registryAccess())
                .getList("OwnedEffects", Tag.TAG_COMPOUND);
        List<CompoundTag> possession = new ArrayList<>();
        for (int index = 0; index < layers.size(); index++) {
            CompoundTag layer = layers.getCompound(index);
            if ("possession".equals(layer.getString("Facet"))
                    && "minecraft:invisibility".equals(layer.getString("Effect"))) {
                possession.add(layer);
            }
        }
        helper.assertTrue(possession.size() == 1, "Missing unique possession ownership record");
        CompoundTag owned = possession.getFirst();
        helper.assertTrue(owned.hasUUID("InstalledIdentity")
                        && owned.getCompound("Previous").equals(beforeConversion),
                "Conversion changed the full previous external effect snapshot");
        UUID installedIdentity = owned.getUUID("InstalledIdentity");
        MobEffectInstance preview = MobEffectInstance.load(beforeConversion.copy());
        helper.assertTrue(preview != null && preview.hiddenEffect == null,
                "Could not load the complete external effect preview");
        MobEffectInstance proposal = new MobEffectInstance(MobEffects.INVISIBILITY, 6000);
        ScaryMonstersOwnedEffects.setIdentity(proposal, installedIdentity);
        helper.assertTrue(preview.update(proposal), "The unusual flags did not exercise the normal effect merge");
        MobEffectInstance hidden = preview.hiddenEffect;
        helper.assertTrue(hidden != null && hidden.is(MobEffects.INVISIBILITY)
                        && hidden.getAmplifier() == 0 && hidden.getDuration() == 6000
                        && hidden.hiddenEffect == null
                        && installedIdentity.equals(ScaryMonstersOwnedEffects.identity(hidden))
                        && !installedIdentity.equals(ScaryMonstersOwnedEffects.identity(preview)),
                "Preview did not produce exactly the known new weak possession layer");
        CompoundTag expected = (CompoundTag) preview.save();
        helper.assertTrue(expected.equals(target.getEffect(MobEffects.INVISIBILITY).save()),
                "Production conversion differs from the full normal merge preview, including flags, cures and identities");
        // The original had no hidden chain; release removes only the verified newly owned layer.
        expected.remove("hidden_effect");
        LOGGER.info("M5_EXTERNAL_MERGE before={} merged={} expectedRelease={}",
                beforeConversion, target.getEffect(MobEffects.INVISIBILITY).save(), expected);
        return expected;
    }

    private static List<ScaryDinosaurEntity> ownedDinosaurs(ServerLevel level, UUID owner) {
        return snapshotEntities(level).stream()
                .filter(entity -> entity instanceof ScaryDinosaurEntity)
                .map(entity -> (ScaryDinosaurEntity) entity)
                .filter(dinosaur -> owner.equals(dinosaur.getOwnerUuid())).toList();
    }

    private static List<Entity> snapshotEntities(ServerLevel level) {
        List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(entities::add);
        return entities;
    }

    private static final class PossessionFixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Vec3 origin;
        private final Frame frame;
        private OnlinePlayer owner;
        private OnlinePlayer target;
        private StandPower power;
        private ScaryDinosaurEntity dinosaur;
        private ScaryDinosaurEntity control;
        private final List<BlockPos> floorBlocks = new ArrayList<>();

        private PossessionFixture(GameTestHelper helper, String name, GameType mode) {
            this(helper, name, mode, false);
        }

        private PossessionFixture(GameTestHelper helper, String name, GameType mode, boolean farWindow) {
            this.helper = helper;
            level = helper.getLevel();
            Frame anchor = frame(helper);
            ChunkPos templateChunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            // GameTest waits for this chunk before starting, but not for adjacent chunks requested mid-test.
            frame = farWindow ? new Frame(new Vec3(templateChunk.getMinBlockX() + 6.5D, anchor.origin.y,
                    templateChunk.getMinBlockZ() + 8.5D), 1, 1) : anchor;
            origin = frame.origin;
            try {
                if (farWindow) {
                    helper.assertTrue(level.isPositionEntityTicking(BlockPos.containing(origin)),
                            "The far fixture must begin inside the template's already entity-ticking chunk");
                    LOGGER.info("T_A_1_FAR_PLACEMENT template={} templateChunk={} ownerOrigin={}",
                            helper.absolutePos(BlockPos.ZERO), templateChunk, origin);
                }
                owner = onlinePlayer(helper, name + "A", origin, GameType.SURVIVAL);
                target = onlinePlayer(helper, name + "B", frame.at(1.0D, 0.0D, 0.0D), mode);
                power = PowerClass.STAND.attachGet(owner);
                helper.assertTrue(StandPowerTransitions.insert(power,
                                new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                                == StandPowerTransitions.Status.APPLIED,
                        "Could not grant Scary Monsters for the terminal lifecycle fixture");
                helper.assertTrue(ScaryMonstersAddon.SCARY_MONSTERS.get().summon(owner, power),
                        "Could not summon the production Scary Monsters Stand");
                possess();
            }
            catch (RuntimeException | Error exception) {
                close();
                throw exception;
            }
        }

        private void teleportTargetBesideOwner() {
            Vec3 spot = frame.at(1.0D, 0.0D, 0.0D);
            target.teleportTo(spot.x, spot.y, spot.z);
        }

        private void possess() {
            GameType original = target.gameMode.getGameModeForPlayer();
            ScaryMonstersService.dinosaurifyNearest(owner);
            List<ScaryDinosaurEntity> matches = ownedDinosaurs(level, owner.getUUID()).stream()
                    .filter(candidate -> target.getUUID().equals(candidate.getPossessedPlayerUuid())).toList();
            helper.assertTrue(matches.size() == 1, "Production conversion did not create one bound dinosaur");
            dinosaur = matches.getFirst();
            dinosaur.setNoGravity(true);
            helper.assertTrue(target.isSpectator() && target.getVehicle() == dinosaur
                            && dinosaur.getPersistentData().getInt("PlayerGameMode") == original.getId()
                            && ScaryMonstersAddon.state(target).ownsFacet("possession"),
                    "Production possession did not preserve the target's mode and effect ownership");
        }

        private ScaryDinosaurEntity nonPossessedControl() {
            control = ScaryMonstersAddon.DINOSAUR.get().create(level);
            helper.assertTrue(control != null, "Could not create the ordinary dinosaur control");
            control.setOwnerUuid(owner.getUUID());
            control.setNoGravity(true);
            control.moveTo(frame.at(3.0D, 0.0D, 0.0D));
            helper.assertTrue(level.addFreshEntity(control), "Could not add the nonpossessed despawn control");
            return control;
        }

        private void afterFarOwnerTicks(IntConsumer assertions) {
            // The 1x1 template does not keep an adjacent control chunk entity-ticking.
            GameTestChunkTickets.forTest(helper).cover(dinosaur.position()).cover(control.position())
                    .whenTicking(this::close, () ->
                            awaitNaturalWarmTick(dinosaur.tickCount, control.tickCount, helper.getTick() + 20L, assertions));
        }

        private void awaitNaturalWarmTick(int dinosaurTicks, int controlTicks, long deadline, IntConsumer assertions) {
            long from = helper.getTick();
            GameTestAssertException cold = new GameTestAssertException("The dinosaurs have not ticked yet");
            helper.startSequence().thenWaitUntil(() -> {
                if (helper.getTick() <= from
                        || !warmed(dinosaurTicks, controlTicks) && helper.getTick() < deadline) {
                    throw cold;
                }
            }).thenExecute(() -> guarded(() -> {
                try {
                    boolean dinosaurTicking = level.isPositionEntityTicking(dinosaur.blockPosition());
                    boolean controlTicking = level.isPositionEntityTicking(control.blockPosition());
                    boolean warmed = warmed(dinosaurTicks, controlTicks);
                    Entity nearest = level.getNearestPlayer(dinosaur, -1.0D);
                    Entity controlNearest = level.getNearestPlayer(control, -1.0D);
                    LOGGER.info("T_A_1_FAR_WARM dinosaurChunk={} controlChunk={} dinosaurTicking={} controlTicking={} "
                                    + "dinosaurTicks={} controlTicks={} owner={} controlNearest={} controlNearestDistance={}",
                            dinosaur.chunkPosition(), control.chunkPosition(), dinosaurTicking, controlTicking,
                            dinosaur.tickCount, control.tickCount, owner.getUUID(),
                            controlNearest == null ? null : controlNearest.getUUID(),
                            controlNearest == null ? null : Math.sqrt(controlNearest.distanceToSqr(control)));
                    helper.assertTrue(warmed && dinosaur.isAlive() && control.isAlive(),
                            "Both dinosaurs must naturally tick with the owner nearby before the far-owner window");
                    helper.assertTrue(nearest == owner && controlNearest == owner
                                    && owner.distanceToSqr(dinosaur) < 32.0D * 32.0D
                                    && owner.distanceToSqr(control) < 32.0D * 32.0D,
                            "The warmup must keep the actual nonspectator owner near both dinosaurs");
                    helper.assertTrue(control.getPossessedPlayerUuid() == null
                                    && !control.isPersistenceRequired() && !control.requiresCustomPersistence(),
                            "The ordinary dinosaur control gained persistence during warmup");
                    owner.teleportTo(origin.x + 176.0D, origin.y, origin.z);
                    owner.setNoGravity(true);
                    int initialTicks = dinosaur.tickCount;
                    afterTicks(8, () -> assertions.accept(initialTicks));
                }
                catch (RuntimeException | Error exception) {
                    close();
                    throw exception;
                }
            }));
        }

        private boolean warmed(int dinosaurTicks, int controlTicks) {
            return level.isPositionEntityTicking(dinosaur.blockPosition())
                    && level.isPositionEntityTicking(control.blockPosition())
                    && dinosaur.tickCount > dinosaurTicks && control.tickCount > controlTicks;
        }

        // A sequence, not helper.runAfterDelay: a scheduled task that schedules another adds to the framework's task
        // map while it iterates it, which crashes the server once the map grows. The assertions schedule from here.
        private void afterTicks(long delay, Runnable task) {
            long at = helper.getTick() + delay;
            GameTestAssertException early = new GameTestAssertException("Waiting for tick " + at);
            helper.startSequence().thenWaitUntil(() -> {
                if (helper.getTick() < at) {
                    throw early;
                }
            }).thenExecute(() -> guarded(task));
        }

        // A sequence step passes on only assertion failures; any other exception would escape the test's tick.
        private void guarded(Runnable task) {
            try {
                task.run();
            }
            catch (GameTestAssertException failure) {
                throw failure;
            }
            catch (RuntimeException failure) {
                close();
                helper.testInfo.fail(failure);
            }
        }

        private void reloginTarget() {
            target = new OnlinePlayer(level, target.getGameProfile());
            target.login();
            helper.assertTrue(level.getServer().getPlayerList().getPlayer(target.getUUID()) == target
                            && level.getEntity(target.getUUID()) == target,
                    "The actual PlayerList login did not register the target");
        }

        private void releaseNormally() {
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, dinosaur.position());
            ScaryMonstersService.revertLookedAtDinosaur(owner);
            helper.assertTrue(dinosaur.isRemoved(), "Production C8 did not discard the released dinosaur");
        }

        private ItemEntity camouflage() {
            UUID dinosaurUuid = dinosaur.getUUID();
            ScaryMonstersService.dinosaursToBlocks(owner);
            List<ItemEntity> made = stones(level, dinosaurUuid);
            helper.assertTrue(dinosaur.isRemoved() && level.getEntity(dinosaurUuid) == null && made.size() == 1
                            && made.getFirst().getItem().is(Items.STONE),
                    "Camouflage did not turn the dinosaur into exactly one stone");
            return made.getFirst();
        }

        // Camouflage while another mod's join handler refuses this owner's stones, as item-limiting mods do.
        private int camouflageWithStoneRefused() {
            int[] refused = { 0 };
            Consumer<EntityJoinLevelEvent> veto = event -> {
                if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) return;
                CompoundTag snapshot = item.getPersistentData().getCompound("DinoSourceNBT");
                if (snapshot.hasUUID("OwnerUUID") && owner.getUUID().equals(snapshot.getUUID("OwnerUUID"))) {
                    event.setCanceled(true);
                    refused[0]++;
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, veto);
            try {
                ScaryMonstersService.dinosaursToBlocks(owner);
            }
            finally {
                NeoForge.EVENT_BUS.unregister(veto);
            }
            return refused[0];
        }

        private void assertRecordKept(String phase) {
            ScaryPossessionData.Possession record = ScaryPossessionData.get(level).get(target.getUUID());
            helper.assertTrue(record != null && !record.terminal() && record.dinosaur().equals(dinosaur.getUUID()),
                    phase + " ended the possession record; record=" + record);
        }

        private void assertCaptive(String phase) {
            ScaryMonstersState state = ScaryMonstersAddon.state(target);
            helper.assertTrue(target.isAlive() && target.isSpectator() && state.ownsFacet("possession")
                            && dinosaur.getUUID().equals(state.possessedDinosaur()),
                    phase + " released the captive; mode=" + target.gameMode.getGameModeForPlayer()
                            + ", possessedDinosaur=" + state.possessedDinosaur());
            assertRecordKept(phase);
        }

        // Solid ground right under the fixture, so a release spot there is a place a player can stand.
        private void floor() {
            BlockPos base = BlockPos.containing(origin).below();
            for (int dx = 0; dx <= 2; dx++) {
                BlockPos pos = base.east(frame.signX * dx);
                if (level.getBlockState(pos).isAir() && level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState())) {
                    floorBlocks.add(pos);
                }
            }
        }

        @Override
        public void close() {
            try {
                if (dinosaur != null && !dinosaur.isRemoved()) {
                    if (target != null && level.getServer().getPlayerList().getPlayer(target.getUUID()) == target) {
                        ScaryMonstersService.releasePossessedPlayer(target, dinosaur, false);
                    }
                    dinosaur.discard();
                }
                if (owner != null) {
                    for (Entity entity : snapshotEntities(level)) {
                        if (!(entity instanceof ItemEntity item)) continue;
                        CompoundTag snapshot = item.getPersistentData().getCompound("DinoSourceNBT");
                        if (snapshot.hasUUID("OwnerUUID") && owner.getUUID().equals(snapshot.getUUID("OwnerUUID"))) {
                            item.discard();
                        }
                    }
                }
                if (control != null && !control.isRemoved()) control.discard();
                if (owner != null && power != null && power.isSummoned()) {
                    ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(owner, power);
                }
                if (target != null) target.close();
                if (owner != null) owner.close();
            }
            finally {
                for (BlockPos pos : floorBlocks) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                floorBlocks.clear();
            }
        }
    }

    static final class OnlinePlayer extends ServerPlayer {
        private final Connection network;
        private final EmbeddedChannel channel;

        private OnlinePlayer(ServerLevel level, String name) {
            this(level, new GameProfile(UUID.randomUUID(), name));
        }

        private OnlinePlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, ClientInformation.createDefault());
            network = new Connection(PacketFlow.SERVERBOUND) {
                @Override
                public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
                    super.setupInboundProtocol(protocol, listener);
                    if (listener instanceof ServerGamePacketListenerImpl) {
                        // Native login syncs datapacks before returning; this server-only fixture has no client.
                        OnlinePlayer.this.connection = new SilentConnection(
                                serverLevel(), OnlinePlayer.this, this);
                    }
                }
            };
            channel = new EmbeddedChannel(network);
        }

        void login() {
            // The native GameTest login path populates PlayerList's UUID map as well as ServerLevel.
            server.getPlayerList().placeNewPlayer(network, this,
                    CommonListenerCookie.createInitial(getGameProfile(), false));
        }

        void close() {
            stopRiding();
            logoutWithMount();
        }

        private void logoutWithMount() {
            if (server.getPlayerList().getPlayer(getUUID()) == this) server.getPlayerList().remove(this);
            discard();
            channel.finishAndReleaseAll();
        }
    }

    private static final class SilentConnection extends ServerGamePacketListenerImpl {
        private SilentConnection(ServerLevel level, ServerPlayer player, Connection network) {
            super(level.getServer(), network, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
    }
}
