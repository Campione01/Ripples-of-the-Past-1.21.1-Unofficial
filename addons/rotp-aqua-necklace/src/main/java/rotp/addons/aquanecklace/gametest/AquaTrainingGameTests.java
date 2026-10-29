package rotp.addons.aquanecklace.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.aquanecklace.AquaNecklaceAddon;
import rotp.addons.aquanecklace.ability.AquaNecklaceAbilities;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity;
import rotp.addons.aquanecklace.entity.AquaNecklaceEntity.MaterialState;
import rotp.addons.aquanecklace.stand.AquaNecklaceTrainingData;
import rotp.core.init.ModStatusEffects;
import rotp.core.network.s2c.TrPowerDataPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

@GameTestHolder(AquaNecklaceAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AquaTrainingGameTests {
    private static final String LIQUID = AquaNecklaceAddon.CHANGE_TO_LIQUID_NAME;
    private static final String GAS = AquaNecklaceAddon.CHANGE_TO_GASEOUS_NAME;
    private static final float STEP = AquaNecklaceAbilities.LIQUID_TRAINING_PER_TICK;

    private AquaTrainingGameTests() {}

    @GameTest(template = "empty", batch = AquaNecklaceGameTests.BATCH, timeoutTicks = 40)
    public static void liquidTicksKeepExactPointsWhileBatchingOwnerPackets(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            float expected = 0.0F;
            for (int tick = 1; tick <= 100; tick++) {
                expected += STEP;
                fixture.tick();
                assertPoints(helper, expected, fixture.points(), "Ordinary liquid tick " + tick);
                helper.assertTrue(fixture.snapshots().size() == tick / 20,
                        "Training did not send exactly one owner snapshot per 20 ticks");
            }
            assertPoints(helper, expected, fixture.lastSnapshot().points(), "Hundredth tick packet");
            fixture.snapshots().clear();
            for (int tick = 1; tick <= 20; tick++) {
                if (tick == 6) {
                    fixture.player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200));
                }
                if (tick == 14) {
                    fixture.player.removeEffect(ModStatusEffects.RESOLVE);
                }
                expected += tick >= 6 && tick < 14 ? STEP * 4.0F : STEP;
                fixture.tick();
                assertPoints(helper, expected, fixture.points(), "Resolve transition tick " + tick);
            }
            helper.assertTrue(fixture.snapshots().size() == 1, "Resolve toggles changed training snapshot cadence");
            assertPoints(helper, expected, fixture.lastSnapshot().points(), "Resolve batch packet");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = AquaNecklaceGameTests.BATCH, timeoutTicks = 40)
    public static void maxCallbackPrecedesSnapshotAndStockTrainingBoundariesRemain(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.seed(1.0F - STEP * 0.5F);
            fixture.tick();
            helper.assertTrue(fixture.data.isSkillUnlocked(GAS), "Stock onMaxTraining did not unlock gas immediately");
            helper.assertTrue(fixture.snapshots().size() == 1 && fixture.lastSnapshot().gasUnlocked(),
                    "Max crossing did not send exactly one snapshot after the gas callback");
            assertPoints(helper, 1.0F, fixture.lastSnapshot().points(), "Max crossing packet");
            fixture.snapshots().clear();
            fixture.tick(40);
            helper.assertTrue(fixture.snapshots().isEmpty(), "Unchanged max training emitted snapshots");

            fixture.data._setSkillUnlocked(LIQUID, false, false);
            fixture.tick(25);
            helper.assertTrue(fixture.points() < 0.0F && fixture.snapshots().isEmpty(),
                    "Locked training entry was created or synchronized");
            fixture.data._setSkillUnlocked(LIQUID, true, false);
            CompoundTag negative = fixture.data.serializeNBT(fixture.player.registryAccess());
            negative.getCompound("ActionLearning").putFloat(LIQUID, -0.5F);
            fixture.data.deserializeNBT(fixture.player.registryAccess(), negative);
            fixture.tick(25);
            helper.assertTrue(fixture.points() == -0.5F && fixture.snapshots().isEmpty(),
                    "Persisted negative training entry was changed or synchronized");

            fixture.seed(1.25F);
            float expected = 1.25F;
            for (int tick = 0; tick < 20; tick++) {
                expected += STEP;
                fixture.tick();
                assertPoints(helper, expected, fixture.points(), "Above-max stock behavior");
            }
            helper.assertTrue(fixture.snapshots().size() == 1 && !fixture.data.isSkillUnlocked(GAS),
                    "Above-max entry spuriously retriggered the max callback or wrong packet cadence");

            fixture.snapshots().clear();
            fixture.data.setAbilityLearningProgressPoints(LIQUID, 0.4F, 1.0F, fixture.power);
            helper.assertTrue(fixture.snapshots().size() == 1, "Non-liquid training update was delayed");
            fixture.data.syncToPlayer(fixture.player);
            helper.assertTrue(fixture.snapshots().size() == 2, "Unrelated explicit owner sync was delayed");
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = AquaNecklaceGameTests.BATCH, timeoutTicks = 40)
    public static void unflushedTrainingPersistsAndExitRemovalFlushResidualPackets(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.tick(7);
            helper.assertTrue(fixture.snapshots().isEmpty(), "Partial batch sent early");
            CompoundTag saved = fixture.power.serializeNBT(fixture.player.registryAccess());
            StandPower reloaded = new StandPower(fixture.player);
            reloaded.deserializeNBT(fixture.player.registryAccess(), saved);
            helper.assertTrue(reloaded.getCurTypeData() instanceof AquaNecklaceTrainingData,
                    "NBT reload did not use Aqua's registered data factory");
            assertPoints(helper, fixture.points(), reloaded.getCurTypeData().getAbilityLearningProgressPoints(LIQUID),
                    "Unflushed points were absent from saved power data");

            fixture.stand.setMaterialState(MaterialState.VAPOR);
            helper.assertTrue(fixture.snapshots().size() == 1, "Liquid exit did not flush the partial batch");
            assertPoints(helper, fixture.points(), fixture.lastSnapshot().points(), "Exit packet");
            fixture.snapshots().clear();
            float exitedPoints = fixture.points();
            fixture.tick(3);
            assertPoints(helper, exitedPoints, fixture.points(), "Non-liquid tick accrued training");
            helper.assertTrue(fixture.snapshots().isEmpty(), "Clean non-liquid exit repeatedly synchronized");

            fixture.stand.setMaterialState(MaterialState.LIQUID);
            fixture.tick(9);
            helper.assertTrue(fixture.snapshots().isEmpty(), "Second partial batch sent early");
            fixture.stand.remove(Entity.RemovalReason.DISCARDED);
            helper.assertTrue(fixture.snapshots().size() == 1, "Entity removal did not flush residual training");
            assertPoints(helper, fixture.points(), fixture.lastSnapshot().points(), "Removal packet");
            helper.succeed();
        }
    }

    private static void assertPoints(GameTestHelper helper, float expected, float actual, String label) {
        helper.assertTrue(Float.compare(expected, actual) == 0, label + ": expected " + expected + ", got " + actual);
    }

    private record Snapshot(float points, boolean gasUnlocked) {}

    private static final class Fixture implements AutoCloseable {
        private final ServerPlayer player;
        private final RecordingConnection connection;
        private final EmbeddedChannel channel;
        private final StandPower power;
        private final AquaNecklaceEntity stand;
        private final AquaNecklaceTrainingData data;

        private Fixture(GameTestHelper helper) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "AquaTraining");
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
            connection = new RecordingConnection(player);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(2, 2, 2)));
            player.setNoGravity(true);
            power = PowerClass.STAND.attachGet(player);
            power.setStand(AquaNecklaceAddon.AQUA_NECKLACE.get());
            power.setResolveLevel(1);
            helper.assertTrue(AquaNecklaceAddon.AQUA_NECKLACE.get().summon(player, power), "Aqua summon failed");
            stand = (AquaNecklaceEntity) power.getSummonedStandEntity();
            helper.assertTrue(power.getCurTypeData() instanceof AquaNecklaceTrainingData, "Wrong Aqua data factory");
            data = (AquaNecklaceTrainingData) power.getCurTypeData();
            stand.summonLockTicks = 0;
            stand.setMaterialState(MaterialState.LIQUID);
            seed(0.0F);
            connection.capture = true;
        }

        private void seed(float points) {
            data.flushLiquidTraining(power);
            data._setSkillUnlocked(LIQUID, true, false);
            data._setSkillUnlocked(GAS, false, false);
            data.setAbilityLearningProgressPoints(LIQUID, points, 1.0F, null);
            snapshots().clear();
        }

        private void tick() {
            stand.setPos(player.position());
            stand.setDeltaMovement(Vec3.ZERO);
            stand.tick();
        }

        private void tick(int count) {
            for (int i = 0; i < count; i++) {
                tick();
            }
        }

        private float points() {
            return data.getAbilityLearningProgressPoints(LIQUID);
        }

        private List<Snapshot> snapshots() {
            return connection.snapshots;
        }

        private Snapshot lastSnapshot() {
            return snapshots().getLast();
        }

        @Override
        public void close() {
            connection.capture = false;
            AquaNecklaceAddon.AQUA_NECKLACE.get().forceUnsummon(player, power);
            player.connection.disconnect(Component.literal("Aqua training test complete"));
            channel.finishAndReleaseAll();
        }
    }

    private static final class RecordingConnection extends Connection {
        private final ServerPlayer player;
        private final List<Snapshot> snapshots = new ArrayList<>();
        private boolean capture;

        private RecordingConnection(ServerPlayer player) {
            super(PacketFlow.SERVERBOUND);
            this.player = player;
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
            if (capture && packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof TrPowerDataPacket payload) {
                RegistryFriendlyByteBuf bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
                try {
                    new TrPowerDataPacket.Handler(payload.type().id()).encode(payload, bytes);
                    int owner = bytes.readInt();
                    var powerClass = PowerClass.NETWORK_CODEC.decode(bytes);
                    ResourceLocation type = ResourceLocation.STREAM_CODEC.decode(bytes);
                    boolean tracking = bytes.readBoolean();
                    if (owner == player.getId() && powerClass == PowerClass.STAND
                            && type.equals(AquaNecklaceAddon.AQUA_NECKLACE.getId()) && !tracking) {
                        StandTypePersistentData data = new StandTypePersistentData(AquaNecklaceAddon.AQUA_NECKLACE.get());
                        data.fromBuf(bytes, false);
                        snapshots.add(new Snapshot(data.getAbilityLearningProgressPoints(LIQUID), data.isSkillUnlocked(GAS)));
                    }
                }
                finally {
                    bytes.release();
                }
            }
            super.send(packet, listener, flush);
        }
    }
}
