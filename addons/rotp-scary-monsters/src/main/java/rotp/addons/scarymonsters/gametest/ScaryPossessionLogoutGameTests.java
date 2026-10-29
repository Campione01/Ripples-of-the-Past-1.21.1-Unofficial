package rotp.addons.scarymonsters.gametest;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ScaryMonstersService;
import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import rotp.addons.scarymonsters.entity.ScaryMonstersStandEntity;
import rotp.addons.scarymonsters.state.ScaryMonstersState;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ScaryMonstersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScaryPossessionLogoutGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String POSSESSION = "possession";

    private ScaryPossessionLogoutGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void possessionLogoutPreservesSaveStateAndExplicitRelease(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> possessionLogoutPreservesSaveStateAndExplicitReleaseBody(h));
    }

    private static void possessionLogoutPreservesSaveStateAndExplicitReleaseBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestPlayer owner = null;
        TestPlayer target = null;
        TestPlayer control = null;
        StandPower power = null;
        try {
            ScaryOwnerDeathGameTests.Frame frame = ScaryOwnerDeathGameTests.frame(helper);
            owner = player(helper, "PossessionOwner", frame.at(0.0D, 0.0D, 0.0D), GameType.SURVIVAL);
            target = player(helper, "PossessionTarget", frame.at(1.0D, 0.0D, 0.0D),
                    GameType.SURVIVAL);
            control = player(helper, "LogoutControl", frame.at(6.0D, 0.0D, 3.0D),
                    GameType.ADVENTURE);
            control.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 120, 0));

            power = PowerClass.STAND.attachGet(owner);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(ScaryMonstersAddon.SCARY_MONSTERS.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant the registered Scary Monsters Stand");
            helper.assertTrue(ScaryMonstersAddon.SCARY_MONSTERS.get().summon(owner, power),
                    "Production Stand summon was refused");
            ScaryMonstersStandEntity stand = ScaryMonstersService.activeStand(owner);
            helper.assertTrue(stand != null && stand.getUser() == owner
                            && level.getEntity(stand.getUUID()) == stand,
                    "Production summon did not create the linked, live Stand");

            ScaryMonstersService.dinosaurifyNearest(owner);
            List<ScaryDinosaurEntity> linked = linkedDinosaurs(level, owner, target);
            helper.assertTrue(linked.size() == 1,
                    "Production conversion did not create exactly one linked dinosaur");
            ScaryDinosaurEntity dinosaur = linked.getFirst();
            helper.assertTrue(target.isSpectator()
                            && target.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                            && target.getVehicle() == dinosaur
                            && target.hasEffect(MobEffects.INVISIBILITY)
                            && ScaryMonstersAddon.state(target).ownsFacet(POSSESSION),
                    "Conversion did not establish the actual player possession");
            helper.assertTrue(dinosaur.getPersistentData().getInt("PlayerGameMode")
                            == GameType.SURVIVAL.getId(),
                    "Conversion did not preserve the original game mode");

            Observation before = observe(helper, target, dinosaur);
            Observation controlBefore = observe(helper, control, null);
            helper.assertTrue(before.savedInvisible() && before.invisibilityTicks() == 6000,
                    "Initial production invisibility was absent from player save data");
            helper.assertTrue(!controlBefore.possessionFacet() && !controlBefore.riding()
                            && controlBefore.savedInvisible(),
                    "The nonpossessed control was not prepared independently");

            // PlayerList.remove fires this bus event before its player save call.
            EventHooks.firePlayerLoggedOut(target);
            Observation after = observe(helper, target, dinosaur);
            EventHooks.firePlayerLoggedOut(control);
            Observation controlAfter = observe(helper, control, null);
            LOGGER.info("R521_PROBE before={} after={} controlBefore={} controlAfter={}",
                    before, after, controlBefore, controlAfter);

            helper.assertTrue(controlAfter.equals(controlBefore),
                    "Logout changed the nonpossessed control's mode or ordinary effects");
            helper.assertTrue(after.equals(before),
                    "Logout changed the bound player's mode, dinosaur, finite effect or save data");

            CompoundTag persistedState = ScaryMonstersAddon.state(target)
                    .serializeNBT(level.registryAccess());
            ScaryMonstersState decodedState = new ScaryMonstersState();
            decodedState.deserializeNBT(level.registryAccess(), persistedState);
            helper.assertTrue(decodedState.ownsFacet(POSSESSION)
                            && decodedState.serializeNBT(level.registryAccess()).equals(persistedState),
                    "Saved possession ownership did not survive the production state codec");
            LOGGER.info("R521_PROBE possessionState={}", persistedState);

            ScaryMonstersService.releasePossessedPlayer(target, dinosaur, false);
            Observation released = observe(helper, target, dinosaur);
            LOGGER.info("R521_PROBE released={}", released);
            helper.assertTrue(released.mode() == GameType.SURVIVAL
                            && target.getVehicle() == null && !released.riding()
                            && !released.invisible() && !released.savedInvisible()
                            && !released.possessionFacet(),
                    "Explicit production release did not restore the original player state");
            dinosaur.discard();
            helper.succeed();
        } finally {
            if (owner != null && target != null) {
                for (ScaryDinosaurEntity dinosaur : linkedDinosaurs(level, owner, target)) {
                    ScaryMonstersService.releasePossessedPlayer(target, dinosaur, false);
                    dinosaur.discard();
                }
            }
            if (owner != null && power != null) {
                ScaryMonstersAddon.SCARY_MONSTERS.get().forceUnsummon(owner, power);
            }
            for (TestPlayer player : new TestPlayer[] { control, target, owner }) {
                if (player != null) {
                    player.close();
                }
            }
        }
    }

    private static TestPlayer player(
            GameTestHelper helper, String name, Vec3 position, GameType mode) {
        TestPlayer player = new TestPlayer(helper.getLevel(), name);
        try {
            player.moveTo(position.x, position.y, position.z);
            player.setGameMode(mode);
            helper.assertTrue(helper.getLevel().addFreshEntity(player),
                    "Could not add server fixture player " + name);
            return player;
        } catch (RuntimeException | Error error) {
            player.close();
            throw error;
        }
    }

    private static List<ScaryDinosaurEntity> linkedDinosaurs(
            ServerLevel level, ServerPlayer owner, ServerPlayer target) {
        return level.getEntitiesOfClass(ScaryDinosaurEntity.class,
                owner.getBoundingBox().inflate(8.0D),
                dinosaur -> owner.getUUID().equals(dinosaur.getOwnerUuid())
                        && target.getUUID().equals(dinosaur.getPossessedPlayerUuid()));
    }

    private static Observation observe(
            GameTestHelper helper, ServerPlayer player,
            @Nullable ScaryDinosaurEntity dinosaur) {
        CompoundTag savedPlayer = player.saveWithoutId(new CompoundTag());
        helper.assertTrue(savedPlayer.contains("playerGameType", Tag.TAG_INT)
                        && savedPlayer.getInt("playerGameType")
                                == player.gameMode.getGameModeForPlayer().getId(),
                "Serialized player game mode did not match its production state");
        CompoundTag savedState = new CompoundTag();
        savedState.putInt("playerGameType", savedPlayer.getInt("playerGameType"));
        savedState.put("active_effects",
                savedPlayer.getList("active_effects", Tag.TAG_COMPOUND).copy());
        MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
        MobEffectInstance savedInvisibility = savedInvisibility(savedState);
        boolean savedInvisible = savedInvisibility != null;
        helper.assertTrue(savedInvisible == (invisibility != null),
                "Serialized invisibility did not match its production state");
        helper.assertTrue(savedInvisibility == null || (invisibility != null
                        && savedInvisibility.getDuration() == invisibility.getDuration()
                        && savedInvisibility.getAmplifier() == invisibility.getAmplifier()),
                "Loaded invisibility duration or amplifier did not match its production state");
        return new Observation(player.gameMode.getGameModeForPlayer(),
                invisibility != null, invisibility != null ? invisibility.getDuration() : 0,
                savedInvisible, ScaryMonstersAddon.state(player).ownsFacet(POSSESSION),
                dinosaur != null && player.getVehicle() == dinosaur,
                dinosaur != null && !dinosaur.isRemoved()
                        && helper.getLevel().getEntity(dinosaur.getUUID()) == dinosaur,
                dinosaur != null ? dinosaur.getUUID() : null, savedState);
    }

    @Nullable
    private static MobEffectInstance savedInvisibility(CompoundTag savedState) {
        ListTag effects = savedState.getList("active_effects", Tag.TAG_COMPOUND);
        for (int index = 0; index < effects.size(); index++) {
            MobEffectInstance effect = MobEffectInstance.load(effects.getCompound(index));
            if (effect != null && effect.getEffect().is(MobEffects.INVISIBILITY)) {
                return effect;
            }
        }
        return null;
    }

    private record Observation(
            GameType mode, boolean invisible, int invisibilityTicks, boolean savedInvisible,
            boolean possessionFacet, boolean riding, boolean dinosaurPresent,
            @Nullable UUID dinosaurUuid, CompoundTag savedState) {}

    // FakePlayer refuses startRiding; use the core's existing silent ServerPlayer fixture.
    private static final class TestPlayer extends ServerPlayer {
        private final EmbeddedChannel channel;

        TestPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name),
                    ClientInformation.createDefault());
            Connection network = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(network);
            connection = new SilentConnection(level, this, network);
        }

        void close() {
            stopRiding();
            discard();
            channel.finishAndReleaseAll();
        }
    }

    private static final class SilentConnection extends ServerGamePacketListenerImpl {
        SilentConnection(ServerLevel level, ServerPlayer player, Connection connection) {
            super(level.getServer(), connection, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
    }
}
