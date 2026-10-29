package rotp.addons.littlefeet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandPower;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.littlefeet.ability.LittleFeetSlowSelfScaleAbility;
import rotp.addons.littlefeet.entity.LittleFeetEntity;
import rotp.addons.littlefeet.scale.LittleFeetScaleService;
import rotp.addons.littlefeet.scale.LittleFeetScaleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleEventCallback;
import virtuoel.pehkui.api.ScaleRegistries;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.api.ScaleTypes;
import virtuoel.pehkui.network.ScalePayload;

@GameTestHolder(LittleFeetAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LittleFeetGameTests {
    private static final double EPSILON = 1.0E-4D;
    private static final int ENCODE_RACE_ROUNDS = 2000;
    // Own batch: the Stand fixtures and the sound listener stay away from the scale tests.
    private static final String STAND_SOUND_BATCH = "little_feet_stand_sounds";
    private static final String SLOW_SCALE_BATCH = "little_feet_slow_scale_hold";
    // Own batch: the empty-bar fixture logs a real ServerPlayer in and waits on its Stand ticking, so no other test
    // (the Pehkui test hammers the scale types and starts a probe thread) runs beside it.
    private static final String EMPTY_BAR_BATCH = "little_feet_empty_bar";

    private LittleFeetGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pehkuiScaleLifecycleDimensionsAndCollision(
            GameTestHelper helper) {
        assertJoinDefersGeometryRefresh(helper);
        assertFreshStandTracksScaleBeforePost(helper);
        Player player = serverSideMockPlayer(helper);
        ScaleType scaleType = LittleFeetScaleTypes.scale();
        ScaleData scaleData = scaleType.getScaleData(player);
        ScaleData healthData = LittleFeetScaleTypes.targetHealthScale()
                .getScaleData(player);
        double normalWidth = player.getBoundingBox().getXsize();
        double normalHeight = player.getBoundingBox().getYsize();

        helper.assertTrue(
                scaleType.getAffectsDimensions(),
                "Little Feet Pehkui scale must affect dimensions.");
        helper.assertTrue(
                !scaleType.getDefaultPersistence(),
                "Little Feet Pehkui scale must not persist.");
        helper.assertTrue(
                scaleType.getDefaultTickDelay() == 1,
                "Little Feet Pehkui transition delay changed.");

        LittleFeetScaleService.setSelfSmall(player);
        scaleData.tick();
        player.refreshDimensions();
        helper.assertTrue(
                close(scaleData.getTargetScale(),
                                LittleFeetScaleService.MIN_SCALE)
                        && close(scaleData.getScale(),
                                LittleFeetScaleService.MIN_SCALE),
                "Pehkui did not apply the minimum Little Feet scale.");
        helper.assertTrue(
                close(
                                player.getBoundingBox().getXsize(),
                                normalWidth
                                        * LittleFeetScaleService.MIN_SCALE)
                        && close(
                                player.getBoundingBox().getYsize(),
                                normalHeight
                                        * LittleFeetScaleService.MIN_SCALE)
                        && close(
                                player.getBoundingBox().getZsize(),
                                normalWidth
                                        * LittleFeetScaleService.MIN_SCALE),
                "Pehkui dimensions/collision bounds did not shrink.");
        // R146: persistence must stay at Pehkui's null default; a
        // non-null value that a later resetScale() nulls is what raced
        // the Netty encoder and kicked LAN clients.
        helper.assertTrue(
                scaleData.getPersistence() == null
                        && !scaleData.shouldPersist()
                        && scaleData.getScaleTickDelay() == 1,
                "Little Feet scale runtime flags changed.");
        CompoundTag small = encodeAndDecode(helper, player, scaleData);
        helper.assertTrue(
                small != null
                        && !small.contains("persistent")
                        && close(small.getFloat("target"),
                                LittleFeetScaleService.MIN_SCALE),
                "pehkui:scale payload did not round-trip the small scale.");

        LittleFeetScaleService.clearEntity(player);
        scaleData.tick();
        player.refreshDimensions();
        helper.assertTrue(
                close(scaleData.getScale(), 1.0D)
                        && close(
                                player.getBoundingBox().getXsize(),
                                normalWidth)
                        && close(
                                player.getBoundingBox().getYsize(),
                                normalHeight),
                "Lifecycle cleanup did not restore Pehkui scale.");
        helper.assertTrue(
                scaleData.getPersistence() == null
                        && healthData.getPersistence() == null,
                "Little Feet reset left a non-null Pehkui persistence.");
        CompoundTag reset = encodeAndDecode(helper, player, scaleData);
        helper.assertTrue(
                reset != null
                        && !reset.contains("persistent")
                        && close(reset.getFloat("target"), 1.0D),
                "pehkui:scale payload did not round-trip the reset scale.");

        assertEncodeSurvivesResets(helper, player, scaleData, healthData);
        LittleFeetScaleService.clearEntity(player);
        assertStandSyncsOnlyOnChange(helper, player);

        player.discard();
        helper.succeed();
    }

    private static void assertJoinDefersGeometryRefresh(GameTestHelper helper) {
        Player player = serverSideMockPlayer(helper);
        ScaleData body = LittleFeetScaleTypes.scale().getScaleData(player);
        ScaleData health = LittleFeetScaleTypes.targetHealthScale().getScaleData(player);
        ScaleData foreign = ScaleTypes.BASE.getScaleData(player);
        double normalWidth = player.getBoundingBox().getXsize();
        double normalHeight = player.getBoundingBox().getYsize();
        try {
            body.markForSync(false);
            health.markForSync(false);
            assertJoinDoesNotNotify(helper, player);
            helper.assertTrue(!body.shouldSync() && !health.shouldSync(),
                    "R646 default join needlessly re-synced Little Feet scale");

            foreign.setScale(1.25F);
            foreign.tick();
            CompoundTag loadedShrink = new CompoundTag();
            loadedShrink.putFloat("scale", 0.5F);
            loadedShrink.putFloat("target", 0.5F);
            loadedShrink.putBoolean("persistent", false);
            body.readNbt(loadedShrink.copy());
            health.readNbt(loadedShrink.copy());
            player.refreshDimensions();
            helper.assertTrue(close(body.getScale(), 0.5F)
                            && close(health.getScale(), 0.5F)
                            && Boolean.FALSE.equals(body.getPersistence())
                            && Boolean.FALSE.equals(health.getPersistence())
                            && close(player.getBoundingBox().getXsize(), normalWidth * 0.625D),
                    "R646 loaded-shrink/persistent:false fixture was not active");
            CompoundTag foreignBefore = foreign.writeNbt(new CompoundTag());
            body.markForSync(false);
            health.markForSync(false);
            assertJoinDoesNotNotify(helper, player);
            helper.assertTrue(body.isReset() && health.isReset()
                            && body.getPersistence() == null && health.getPersistence() == null,
                    "R646 join abandoned the owned reset or pre-tracking persistence cleanup");
            helper.assertTrue(foreignBefore.equals(foreign.writeNbt(new CompoundTag())),
                    "R646 join reset another mod's scale");

            LittleFeetScaleService.tickLiving(player);
            helper.assertTrue(close(player.getBoundingBox().getXsize(), normalWidth * 1.25D)
                            && close(player.getBoundingBox().getYsize(), normalHeight * 1.25D)
                            && close(ScaleTypes.HEALTH.getScaleData(player).getScale(), 1.0D)
                            && body.shouldSync() && health.shouldSync()
                            && foreignBefore.equals(foreign.writeNbt(new CompoundTag())),
                    "R646 first tick did not refresh loaded shrink dimensions/stats without changing foreign scale");
            body.markForSync(false);
            health.markForSync(false);
            LittleFeetScaleService.tickLiving(player);
            helper.assertTrue(!body.shouldSync() && !health.shouldSync(),
                    "R646 deferred join refresh repeated on the second tick");

            body.readNbt(loadedShrink.copy());
            assertJoinDoesNotNotify(helper, player);
            LittleFeetScaleService.clearEntity(player);
            body.markForSync(false);
            health.markForSync(false);
            LittleFeetScaleService.tickLiving(player);
            helper.assertTrue(!body.shouldSync() && !health.shouldSync(),
                    "R646 explicit lifecycle cleanup left a deferred join refresh");
            helper.assertTrue(foreignBefore.equals(foreign.writeNbt(new CompoundTag())),
                    "R646 owned lifecycle cleanup changed foreign scale");
        }
        finally {
            LittleFeetScaleService.clearEntity(player);
            foreign.resetScale();
            player.discard();
        }
    }

    private static void assertJoinDoesNotNotify(GameTestHelper helper, LivingEntity entity) {
        assertJoinDoesNotNotify(helper, entity, () ->
                LittleFeetEvents.onEntityJoin(new EntityJoinLevelEvent(entity, helper.getLevel(), true)));
    }

    private static void assertJoinDoesNotNotify(GameTestHelper helper, LivingEntity entity, Runnable join) {
        AtomicInteger callbacks = new AtomicInteger();
        ScaleEventCallback observer = data -> {
            if (data.getEntity() == entity) callbacks.incrementAndGet();
        };
        ScaleType body = LittleFeetScaleTypes.scale();
        ScaleType health = LittleFeetScaleTypes.targetHealthScale();
        body.getScaleChangedEvent().add(observer);
        health.getScaleChangedEvent().add(observer);
        try {
            // An ungrounded fixture in the loaded arena exposes callbacks without a cold-chunk wait.
            join.run();
            helper.assertTrue(callbacks.get() == 0,
                    "R646 Little Feet join invoked scale/dimension callbacks before the entity could tick: "
                            + callbacks.get());
        }
        finally {
            body.getScaleChangedEvent().remove(observer);
            health.getScaleChangedEvent().remove(observer);
        }
    }

    private static void assertFreshStandTracksScaleBeforePost(GameTestHelper helper) {
        Player user = serverSideMockPlayer(helper);
        LittleFeetEntity stand = LittleFeetAddon.LITTLE_FEET_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(stand != null, "T-D-1 fresh Stand not created");
        FakePlayer observer = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "LittleFeetTracker"));
        RecordingConnection connection = new RecordingConnection(observer);
        ScaleData userBody = LittleFeetScaleTypes.scale().getScaleData(user);
        ScaleData userHealth = LittleFeetScaleTypes.targetHealthScale().getScaleData(user);
        ScaleData body = LittleFeetScaleTypes.scale().getScaleData(stand);
        ScaleData health = LittleFeetScaleTypes.targetHealthScale().getScaleData(stand);
        ScaleData foreign = ScaleTypes.BASE.getScaleData(stand);
        ServerEntity tracker = new ServerEntity(helper.getLevel(), stand, 1, true, packet -> {});
        try {
            stand.withStandType(LittleFeetAddon.LITTLE_FEET.get());
            stand.setUserAndPower(user, null);
            stand.setPos(helper.absolutePos(new BlockPos(1, 3, 1)).getCenter());
            double normalWidth = stand.getBoundingBox().getXsize();
            double normalHeight = stand.getBoundingBox().getYsize();
            foreign.setScale(1.25F);
            foreign.tick();
            CompoundTag foreignBefore = foreign.writeNbt(new CompoundTag());
            LittleFeetScaleService.setSelfSmall(user);
            userBody.tick();
            userHealth.setScale(0.5F);
            body.markForSync(false);
            health.markForSync(false);
            double beforeJoinWidth = stand.getBoundingBox().getXsize();
            assertJoinDoesNotNotify(helper, stand, () -> helper.assertTrue(
                    helper.getLevel().addFreshEntity(stand), "T-D-1 fresh Stand join was rejected"));
            LogUtils.getLogger().info(
                    "T-D-1 silent join: current={}, target={}, previous={}, health={}, widthBefore={}, widthAfter={}, bodySync={}",
                    body.getScale(), body.getTargetScale(), body.getPrevBaseScale(), health.getScale(),
                    beforeJoinWidth, stand.getBoundingBox().getXsize(), body.shouldSync());
            helper.assertTrue(close(body.getBaseScale(), userBody.getBaseScale())
                            && close(body.getScale(), LittleFeetScaleService.MIN_SCALE)
                            && close(body.getTargetScale(), userBody.getTargetScale())
                            && close(body.getPrevBaseScale(), userBody.getPrevBaseScale())
                            && close(body.getInitialScale(), userBody.getInitialScale())
                            && !body.hasDefaultValues() && body.getPersistence() == null,
                    "T-D-1 fresh Stand did not inherit current and target scale before its first Post tick");
            helper.assertTrue(health.isReset() && !body.shouldSync() && !health.shouldSync()
                            && foreignBefore.equals(foreign.writeNbt(new CompoundTag())),
                    "T-D-1 silent join notified, copied target health, or changed foreign scale");

            tracker.addPairing(observer);
            int spawnIndex = -1;
            int scaleIndex = -1;
            ScalePayload initialScale = null;
            for (int i = 0; i < connection.packets.size(); i++) {
                Packet<?> packet = connection.packets.get(i);
                if (packet instanceof ClientboundAddEntityPacket spawn && spawn.getId() == stand.getId()) {
                    spawnIndex = i;
                }
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof ScalePayload scale && scale.entityId == stand.getId()) {
                    scaleIndex = i;
                    initialScale = scale;
                }
            }
            helper.assertTrue(spawnIndex >= 0 && scaleIndex > spawnIndex && initialScale != null,
                    "T-D-1 real pairing did not send Pehkui scale after the Stand spawn before Post");
            RegistryFriendlyByteBuf buf = lanBuffer(helper);
            try {
                ScalePayload.CODEC.encode(buf, initialScale);
                ScalePayload decoded = ScalePayload.CODEC.decode(buf);
                CompoundTag sentBody = decoded.syncedScales.get(ScaleRegistries.getId(
                        ScaleRegistries.SCALE_TYPES, LittleFeetScaleTypes.scale()));
                helper.assertTrue(decoded.entityId == stand.getId() && buf.readableBytes() == 0
                                && sentBody != null && close(sentBody.getFloat("scale"), LittleFeetScaleService.MIN_SCALE)
                                && close(sentBody.getFloat("target"), LittleFeetScaleService.MIN_SCALE)
                                && !sentBody.contains("persistent"),
                        "T-D-1 tracking-start codec did not carry the current and target shrink");
                LogUtils.getLogger().info("T-D-1 pairing: spawnIndex={}, scaleIndex={}, entityId={}, body={}",
                        spawnIndex, scaleIndex, decoded.entityId, sentBody);
            }
            finally {
                buf.release();
            }

            LittleFeetEvents.onEntityTick(new EntityTickEvent.Post(stand));
            helper.assertTrue(close(stand.getBoundingBox().getXsize(), normalWidth * 1.25D * LittleFeetScaleService.MIN_SCALE)
                            && close(stand.getBoundingBox().getYsize(), normalHeight * 1.25D * LittleFeetScaleService.MIN_SCALE)
                            && health.isReset() && close(ScaleTypes.HEALTH.getScaleData(stand).getScale(), 1.0F)
                            && body.shouldSync() && health.shouldSync()
                            && foreignBefore.equals(foreign.writeNbt(new CompoundTag())),
                    "T-D-1 first Post did not refresh actual bounds with body-only owned scaling");
            LogUtils.getLogger().info("T-D-1 first Post: width={}, height={}, body={}, targetHealth={}, foreign={}",
                    stand.getBoundingBox().getXsize(), stand.getBoundingBox().getYsize(), body.getScale(),
                    health.getScale(), foreign.getBaseScale());
            body.markForSync(false);
            health.markForSync(false);
            LittleFeetEvents.onEntityTick(new EntityTickEvent.Post(stand));
            helper.assertTrue(!body.shouldSync() && !health.shouldSync(),
                    "T-D-1 queued join refresh repeated on a steady second Post");
            LittleFeetScaleService.setSelfNormal(user);
            LittleFeetEvents.onEntityTick(new EntityTickEvent.Post(stand));
            helper.assertTrue(body.isReset() && health.isReset()
                            && close(stand.getBoundingBox().getXsize(), normalWidth * 1.25D)
                            && foreignBefore.equals(foreign.writeNbt(new CompoundTag())),
                    "T-D-1 normal-user reset changed health or foreign scale");
        }
        finally {
            tracker.removePairing(observer);
            LittleFeetScaleService.clearEntity(stand);
            LittleFeetScaleService.clearEntity(user);
            stand.discard();
            user.discard();
            observer.discard();
        }
    }

    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection(FakePlayer player) {
            super(player.getServer(), player.connection.getConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(this::send);
            } else {
                packets.add(packet);
            }
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            send(packet);
        }
    }

    // R147: Little Feet visits every Stand every tick. Before, each visit
    // rewrote both types, so every Stand sent a pehkui:scale payload to
    // its trackers every tick even with no Little Feet user around.
    private static void assertStandSyncsOnlyOnChange(
            GameTestHelper helper, Player player) {
        LittleFeetEntity stand = LittleFeetAddon.LITTLE_FEET_ENTITY.get()
                .create(helper.getLevel());
        helper.assertTrue(stand != null, "Little Feet Stand not created.");
        ScaleData standData =
                LittleFeetScaleTypes.scale().getScaleData(stand);
        ScaleData standHealth = LittleFeetScaleTypes.targetHealthScale()
                .getScaleData(stand);

        LittleFeetScaleService.tickLiving(stand);
        standData.markForSync(false);
        standHealth.markForSync(false);
        LittleFeetScaleService.tickLiving(stand);
        helper.assertTrue(
                !standData.shouldSync() && !standHealth.shouldSync(),
                "A Stand at default scale was re-synced every tick.");

        stand.setUserAndPower(player, null);
        LittleFeetScaleService.setSelfSmall(player);
        assertJoinDoesNotNotify(helper, stand);
        LittleFeetScaleService.tickLiving(stand);
        helper.assertTrue(
                close(standData.getTargetScale(),
                        LittleFeetScaleService.MIN_SCALE)
                        && standData.shouldSync(),
                "The Stand did not follow its shrunk user.");
        standData.tick();
        standData.markForSync(false);
        LittleFeetScaleService.tickLiving(stand);
        helper.assertTrue(
                !standData.shouldSync()
                        && close(standData.getScale(),
                                LittleFeetScaleService.MIN_SCALE),
                "A Stand at its user's scale was re-synced every tick.");

        LittleFeetScaleService.clearEntity(player);
        LittleFeetScaleService.tickLiving(stand);
        helper.assertTrue(
                standData.isReset() && standData.shouldSync(),
                "The Stand did not grow back with its user.");
        stand.discard();
    }

    // Same codec the LAN connection runs in CustomPacketPayload.encode.
    private static CompoundTag encodeAndDecode(
            GameTestHelper helper, Player player, ScaleData data) {
        RegistryFriendlyByteBuf buf = lanBuffer(helper);
        try {
            ScalePayload.CODEC.encode(
                    buf, new ScalePayload(player, List.of(data)));
            ScalePayload decoded = ScalePayload.CODEC.decode(buf);
            helper.assertTrue(
                    decoded.entityId == player.getId()
                            && buf.readableBytes() == 0,
                    "pehkui:scale payload framing changed.");
            return decoded.syncedScales.get(ScaleRegistries.getId(
                    ScaleRegistries.SCALE_TYPES, data.getScaleType()));
        } finally {
            buf.release();
        }
    }

    // Encodes on another thread, as Netty does for a remote client, while
    // the server thread runs the grow/shrink/reset path. Pre-fix this could
    // hit the toPacket NPE; it cannot fail once persistence stays null.
    private static void assertEncodeSurvivesResets(
            GameTestHelper helper, Player player, ScaleData... scales) {
        ScalePayload payload = new ScalePayload(player, List.of(scales));
        RegistryFriendlyByteBuf buf = lanBuffer(helper);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread encoder = new Thread(() -> {
            started.countDown();
            try {
                while (!stop.get()) {
                    buf.clear();
                    ScalePayload.CODEC.encode(buf, payload);
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "rotp_littlefeet-scale-encode-probe");
        encoder.setDaemon(true);
        encoder.start();
        try {
            started.await(5L, TimeUnit.SECONDS);
            for (int i = 0; i < ENCODE_RACE_ROUNDS
                    && failure.get() == null; i++) {
                LittleFeetScaleService.changeSelfSlowly(player, true);
                LittleFeetScaleService.setSelfNormal(player);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop.set(true);
            try {
                encoder.join(5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!encoder.isAlive()) {
                buf.release();
            }
        }
        helper.assertTrue(
                failure.get() == null,
                "pehkui:scale encode failed during Little Feet resets: "
                        + failure.get());
        for (ScaleData data : scales) {
            helper.assertTrue(
                    data.getPersistence() == null,
                    "Little Feet grow/shrink set a Pehkui persistence.");
        }
    }

    // 1.16 little_feet_instant_self_shrink had no stamina condition and
    // StandEntityTask paid its 75 after the perform with the result ignored:
    // it shrinks its user on an empty bar, which stays at 0.
    @GameTest(template = "empty", timeoutTicks = 60, batch = EMPTY_BAR_BATCH)
    public static void instantSelfShrinkRunsOnAnEmptyBar(
            GameTestHelper helper) {
        // the user and its Stand, which performs only in a chunk that ticks
        // entities; held before the user is placed, released at the end
        new LittleFeetChunkTickets(helper).hold(
                helper.absoluteVec(new Vec3(-2.0D, 0.0D, -2.0D)),
                helper.absoluteVec(new Vec3(5.0D, 0.0D, 5.0D)));
        ServerPlayer player = placedServerPlayer(helper);
        StandPower power = StandPower.get(player);
        helper.assertTrue(power != null,
                "The mock player has no Stand power attachment.");
        power.setStand(LittleFeetAddon.LITTLE_FEET.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                LittleFeetAddon.LITTLE_FEET.get().summon(player, power)
                        && power.getSummonedStandEntity() != null,
                "Little Feet could not be summoned.");
        power.getSummonedStandEntity().summonLockTicks = 0;
        power.setStamina(0.0F);
        helper.assertTrue(
                !power.isStaminaInfinite() && power.getStamina() == 0.0F,
                "Setup: the Little Feet user's stamina is not finite "
                        + "and empty.");
        Ability ability = power.getAbility(
                LittleFeetAddon.INSTANT_SELF_SHRINK_NAME);
        helper.assertTrue(
                ability != null
                        && AbilityInput.withConditionCheck(ability, player),
                "1.16 had no stamina check: Instant Self Shrink must be "
                        + "admitted on an empty bar.");
        AbilityInput.keyPress(
                (short) 73,
                ability,
                player,
                new FriendlyByteBuf(Unpooled.buffer()),
                InputMethod.CLICK,
                0.0F,
                BufferingState.clickCanBuffer(),
                ability.abilityId);
        ScaleData scale = LittleFeetScaleTypes.scale().getScaleData(player);
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    close(scale.getTargetScale(),
                            LittleFeetScaleService.MIN_SCALE),
                    "Instant Self Shrink did not shrink on an empty bar: "
                            + scale.getTargetScale());
            helper.assertTrue(
                    power.getStamina() == 0.0F,
                    "The paid 75 must leave the bar at 0: "
                            + power.getStamina());
            LittleFeetScaleService.clearEntity(player);
            power.getSummonedStandEntity().discard();
            player.connection.disconnect(net.minecraft.network.chat.Component
                    .literal("Little Feet empty-bar GameTest complete"));
        });
    }

    // 1.16 little_feet_barrage registered its cry with the one-argument
    // standSound (playInArmsOnly true): an arms-only Little Feet still cried.
    @GameTest(template = "empty", timeoutTicks = 20, batch = STAND_SOUND_BATCH)
    public static void barrageCriesWithTheArmsOnly(GameTestHelper helper) {
        StandFixture fixture = new StandFixture(helper, "lf-barrage-cry");
        try {
            Ability ability = fixture.power.getAbility("barrage");
            helper.assertTrue(ability instanceof StandEntityBarrageAbility,
                    "The registered barrage is not a barrage: " + ability);
            EntityActionInstance barrage =
                    ((EntityActionType) ability).createActionObj();
            helper.assertTrue(playsBarrageCry(barrage, fixture.stand),
                    "Setup: the fully summoned Little Feet does not cry.");
            fixture.stand.setArmsOnlyMode(true, true);
            helper.assertTrue(fixture.stand.isArmsOnlyMode(),
                    "Setup: Little Feet did not switch to its arms only.");
            helper.assertTrue(playsBarrageCry(barrage, fixture.stand),
                    "1.16 played the barrage cry with the arms only "
                            + "(one-argument standSound); the port stayed silent.");
        }
        finally {
            fixture.close();
        }
        helper.succeed();
    }

    // 1.16 StandEntity.attackTarget: the Shrink Slash impact sound came
    // after the hit and only when it landed.
    @GameTest(template = "empty", timeoutTicks = 20, batch = STAND_SOUND_BATCH)
    public static void shrinkSlashImpactSoundsOnlyOnALandedHit(
            GameTestHelper helper) {
        StandFixture fixture = new StandFixture(helper, "lf-slash-sound");
        try {
            Cow immune = fixture.cow();
            immune.setInvulnerable(true);
            int sounds = fixture.shrinkSlash(immune);
            helper.assertTrue(immune.getHealth() == immune.getMaxHealth(),
                    "Setup: the invulnerable cow was hurt.");
            helper.assertTrue(sounds == 0,
                    "1.16 played no Shrink Slash impact sound for a hit that "
                            + "did not land; the port played " + sounds);
            immune.discard();
            Cow cow = fixture.cow();
            sounds = fixture.shrinkSlash(cow);
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth() && sounds == 1,
                    "A landed Shrink Slash played " + sounds
                            + " impact sounds, cow health " + cow.getHealth());
        }
        finally {
            fixture.close();
        }
        helper.succeed();
    }

    private static boolean playsBarrageCry(
            EntityActionInstance barrage, StandEntity stand) {
        try {
            Method method = StandEntityBarrageAbility.StandEntityBarrage.class
                    .getDeclaredMethod(
                            "playsBarrageCry", Level.class, StandEntity.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(barrage, stand.level(), stand);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "The core barrage cry check is not reachable", e);
        }
    }

    private static final class StandFixture {
        private final GameTestHelper helper;
        private final FakePlayer user;
        private final StandPower power;
        private final StandEntity stand;
        private final Vec3 origin;
        private final List<Entity> entities = new ArrayList<>();
        private final Consumer<PlayLevelSoundEvent.AtPosition> listener;
        private SoundEvent counted;
        private int sounds;

        private StandFixture(GameTestHelper helper, String name) {
            this.helper = helper;
            origin = Vec3.atBottomCenterOf(
                    helper.absolutePos(new BlockPos(1, 2, 1)));
            user = new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), name));
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            user.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the Little Feet user.");
            entities.add(user);
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(LittleFeetAddon.LITTLE_FEET.get()))
                            .status() == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Little Feet.");
            helper.assertTrue(
                    LittleFeetAddon.LITTLE_FEET.get().summon(user, power)
                            && power.getSummonedStandEntity() != null,
                    "Little Feet could not be summoned.");
            stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.setNoGravity(true);
            stand.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            // the aimed target stays the action's target
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS
                    .get()).standAttackTargetLock = true;
            listener = event -> {
                if (counted != null && event.getLevel() == helper.getLevel()
                        && event.getSound() != null
                        && event.getSound().value() == counted) {
                    sounds++;
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                    PlayLevelSoundEvent.AtPosition.class, listener);
        }

        private Cow cow() {
            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Could not create a cow.");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            cow.setHealth(500.0F);
            cow.moveTo(origin.x, origin.y, origin.z + 1.5D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "Could not add the cow.");
            entities.add(cow);
            return cow;
        }

        // One Shrink Slash perform on the target: the impact sounds it made.
        private int shrinkSlash(Entity target) {
            LivingComponentAction component =
                    LivingComponentAction.getComponent(stand);
            component.setAction(null, user, SyncType.NO_SYNC);
            component.entityAim.setTarget(new ActionTarget(target));
            Ability ability = power.getAbility(
                    LittleFeetAddon.SHRINK_SLASH_NAME);
            helper.assertTrue(ability instanceof EntityActionType,
                    "Missing the registered Shrink Slash.");
            power.setStamina(power.getMaxStamina());
            EntityActionInstance action = ((EntityActionType) ability)
                    .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
            component.setAction(action, user, SyncType.NO_SYNC);
            counted = LittleFeetAddon.LITTLE_FEET_SHRINK_SLASH.get();
            sounds = 0;
            action.actionPerformStart();
            counted = null;
            helper.assertTrue(action.punchedTarget != null
                            && action.punchedTarget.getMainEntity() == target,
                    "Setup: the Shrink Slash hit " + action.punchedTarget
                            + " instead of " + target);
            component.setAction(null, user, SyncType.NO_SYNC);
            return sounds;
        }

        private void close() {
            NeoForge.EVENT_BUS.unregister(listener);
            LivingComponentAction.getComponent(stand)
                    .setAction(null, user, SyncType.NO_SYNC);
            if (power.isSummoned()) {
                LittleFeetAddon.LITTLE_FEET.get().forceUnsummon(user, power);
            }
            LittleFeetScaleService.clearEntity(user);
            entities.forEach(Entity::discard);
        }
    }

    // 1.16 little_feet_slow_self_shrink was holdType(80) with cooldown(80): no
    // cooldown at the press (PowerBaseImpl.setHeldAction), the full 80 as the
    // hold ended (stopHeldAction: the release or the 80th held tick).
    @GameTest(template = "empty", timeoutTicks = 20, batch = SLOW_SCALE_BATCH)
    public static void slowSelfShrinkTakesItsCooldownAsItsHoldEndsAsIn116(
            GameTestHelper helper) {
        for (boolean letGo : new boolean[] { false, true }) {
            SlowScaleScene scene = new SlowScaleScene(helper, "lf-slow-shrink");
            try {
                EntityActionInstance shrink = scene.holdSlowScale(
                        LittleFeetAddon.SLOW_SELF_SHRINK_NAME);
                scene.tick(50);
                helper.assertTrue(!shrink.isOver()
                                && shrink.getPhase() == ActionPhase.PERFORM
                                && !scene.power.isAbilityOnCooldown(
                                        LittleFeetAddon.SLOW_SELF_SHRINK_NAME),
                        "1.16 set no Slow Self Shrink cooldown during its hold:"
                                + " phase " + shrink.getPhase() + ", cooldown "
                                + scene.cooldown(LittleFeetAddon.SLOW_SELF_SHRINK_NAME));
                if (letGo) {
                    AbilityInput.keyRelease(SlowScaleScene.HOLD_KEY, scene.user);
                }
                else {
                    scene.tick(30);
                }
                helper.assertTrue(shrink.isOver(),
                        "The Slow Self Shrink hold did not end ("
                                + (letGo ? "let go at tick 50" : "80 held ticks")
                                + "): phase " + shrink.getPhase());
                int cooldown = scene.cooldown(LittleFeetAddon.SLOW_SELF_SHRINK_NAME);
                helper.assertTrue(cooldown == LittleFeetSlowSelfScaleAbility.COOLDOWN_TICKS,
                        "1.16 started Slow Self Shrink's 80-tick cooldown as its hold ended ("
                                + (letGo ? "let go at tick 50" : "after the full 80 ticks")
                                + "), not at the press: " + cooldown + " left");
            }
            finally {
                scene.close();
            }
        }
        helper.succeed();
    }

    // 1.16 little_feet_slow_self_grow had no cooldown(80) of its own.
    @GameTest(template = "empty", timeoutTicks = 20, batch = SLOW_SCALE_BATCH)
    public static void slowSelfGrowSetsNoCooldownAsIn116(GameTestHelper helper) {
        for (boolean letGo : new boolean[] { false, true }) {
            SlowScaleScene scene = new SlowScaleScene(helper, "lf-slow-grow");
            try {
                LittleFeetScaleService.setSelfSmall(scene.user);
                EntityActionInstance grow = scene.holdSlowScale(
                        LittleFeetAddon.SLOW_SELF_GROW_NAME);
                if (letGo) {
                    scene.tick(30);
                    AbilityInput.keyRelease(SlowScaleScene.HOLD_KEY, scene.user);
                }
                else {
                    scene.tick(LittleFeetSlowSelfScaleAbility.HOLD_TICKS);
                }
                helper.assertTrue(grow.isOver()
                                && !scene.power.isAbilityOnCooldown(
                                        LittleFeetAddon.SLOW_SELF_GROW_NAME),
                        "1.16 gave Slow Self Grow no cooldown ("
                                + (letGo ? "let go" : "held to its end") + "): phase "
                                + grow.getPhase() + ", cooldown "
                                + scene.cooldown(LittleFeetAddon.SLOW_SELF_GROW_NAME));
            }
            finally {
                scene.close();
            }
        }
        helper.succeed();
    }

    // 1.16 StandEntityAction.isCancelable: a punch pressed during the held
    // holdType shrink (no recovery) replaced it at once, and the hold's end
    // set the shrink's cooldown (stopHeldAction).
    @GameTest(template = "empty", timeoutTicks = 20, batch = SLOW_SCALE_BATCH)
    public static void aPunchPressedDuringTheSlowShrinkReplacesItAtOnceAsIn116(
            GameTestHelper helper) {
        SlowScaleScene scene = new SlowScaleScene(helper, "lf-slow-punch");
        try {
            EntityActionInstance shrink = scene.holdSlowScale(
                    LittleFeetAddon.SLOW_SELF_SHRINK_NAME);
            scene.tick(10);
            BufferingState punch = scene.press(
                    "punch", InputMethod.CLICK, SlowScaleScene.OTHER_KEY);
            EntityActionInstance now = scene.standAction.getAction();
            helper.assertTrue(punch.isActionSuccess && !punch.shouldBuffer
                            && now != null && now != shrink
                            && now.ability == scene.power.getAbility("punch")
                            && shrink.isOver(),
                    "1.16 let a punch pressed during the held Slow Self Shrink"
                            + " replace it at once: started " + punch.isActionSuccess
                            + ", buffered " + punch.shouldBuffer + ", the Stand's action "
                            + now + ", the shrink in " + shrink.getPhase());
            int cooldown = scene.cooldown(LittleFeetAddon.SLOW_SELF_SHRINK_NAME);
            helper.assertTrue(cooldown == LittleFeetSlowSelfScaleAbility.COOLDOWN_TICKS,
                    "1.16 set the shrink's cooldown as the punch ended its hold: "
                            + cooldown);
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    // 1.16 ActionsOverlayGui.onActionClick sent no press of a held action
    // (holdType(80) is one) while the power held another: the input buffer
    // keeps the Slow Self Shrink pressed during the held guard (owner N2).
    @GameTest(template = "empty", timeoutTicks = 20, batch = SLOW_SCALE_BATCH)
    public static void theSlowShrinkPressedDuringTheHeldGuardWaitsAsIn116(
            GameTestHelper helper) {
        SlowScaleScene scene = new SlowScaleScene(helper, "lf-slow-guard");
        try {
            BufferingState guardPress = scene.press(
                    "guard", InputMethod.HOLD, SlowScaleScene.HOLD_KEY);
            EntityActionInstance guard = scene.standAction.getAction();
            helper.assertTrue(guardPress.isActionSuccess && guard != null
                            && guard.ability == scene.power.getAbility("guard")
                            && guard.getPhase() == ActionPhase.PERFORM,
                    "Setup: the guard is not held: " + guard);
            BufferingState shrink = scene.press(
                    LittleFeetAddon.SLOW_SELF_SHRINK_NAME, InputMethod.HOLD,
                    SlowScaleScene.OTHER_KEY);
            helper.assertTrue(shrink.shouldBuffer && !shrink.isActionSuccess
                            && scene.standAction.getAction() == guard
                            && guard.getPhase() == ActionPhase.PERFORM,
                    "1.16's client sent no Slow Self Shrink (a held action) pressed"
                            + " while the guard was held: buffered " + shrink.shouldBuffer
                            + ", started " + shrink.isActionSuccess + ", the Stand's action "
                            + scene.standAction.getAction());
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    // A survival FakePlayer with Little Feet summoned at Resolve level 4; its
    // own tick does not run, so the test ticks the Stand's action and the
    // power's cooldowns itself, one game tick at a time.
    private static final class SlowScaleScene {
        private static final short HOLD_KEY = 81;
        private static final short OTHER_KEY = 82;
        private final GameTestHelper helper;
        private final FakePlayer user;
        private final StandPower power;
        private final StandEntity stand;
        private final LivingComponentAction standAction;

        private SlowScaleScene(GameTestHelper helper, String name) {
            this.helper = helper;
            Vec3 origin = Vec3.atBottomCenterOf(
                    helper.absolutePos(new BlockPos(1, 2, 1)));
            user = new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), name));
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            user.setNoGravity(true);
            user.getAbilities().instabuild = false;
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the Little Feet user.");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(LittleFeetAddon.LITTLE_FEET.get()))
                            .status() == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Little Feet.");
            power.setResolveLevel(4);
            for (String unlocked : List.of(
                    LittleFeetAddon.SLOW_SELF_SHRINK_NAME,
                    LittleFeetAddon.SLOW_SELF_GROW_NAME, "punch")) {
                power.getCurTypeData()._lockedAbilities.remove(unlocked);
            }
            helper.assertTrue(
                    LittleFeetAddon.LITTLE_FEET.get().summon(user, power)
                            && power.getSummonedStandEntity() != null,
                    "Little Feet could not be summoned.");
            stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            standAction = LivingComponentAction.getComponent(stand);
            power.setStamina(power.getMaxStamina());
        }

        // Pressed by its hotbar key and kept held: the held perform starts.
        private EntityActionInstance holdSlowScale(String name) {
            BufferingState state = press(name, InputMethod.HOLD, HOLD_KEY);
            EntityActionInstance action = standAction.getAction();
            helper.assertTrue(state.isActionSuccess && action != null
                            && action.ability == power.getAbility(name)
                            && action.getPhase() == ActionPhase.PERFORM,
                    "1.16 holdType(80) started " + name + " straight in its held"
                            + " perform: started " + state.isActionSuccess
                            + ", the Stand's action " + action + " in "
                            + (action != null ? action.getPhase() : null));
            helper.assertTrue(!power.isAbilityOnCooldown(name),
                    "1.16 set no cooldown at the press of " + name + ": "
                            + cooldown(name));
            return action;
        }

        // As ClAbilityInputPacket handles a press: the key's resolved ability,
        // its condition check, then the press.
        private BufferingState press(String name, InputMethod method, short key) {
            BufferingState state = BufferingState.clickCanBuffer();
            Ability base = power.getAbility(name);
            helper.assertTrue(base != null, "Missing ability " + name);
            AbilityConditionCheck container = resolve(base);
            if (container != null
                    && AbilityInput.withConditionCheck(container, user, method)) {
                AbilityInput.keyPress(key, container.ability, user, null,
                        method, 0.0F, state, base.getAbilityId());
            }
            return state;
        }

        // The power resolves its keys once a tick, and the test does not tick it.
        private AbilityConditionCheck resolve(Ability ability) {
            try {
                Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
                cached.setAccessible(true);
                cached.setBoolean(power, false);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Power.cachedMovesThisTick is not reachable", e);
            }
            return power.updateAvailableMoves().getContextVariationContainer(ability);
        }

        private void tick(int ticks) {
            for (int i = 0; i < ticks; i++) {
                tickCooldowns();
                standAction.tick();
            }
        }

        private void tickCooldowns() {
            try {
                Method method = StandPower.class.getDeclaredMethod("tickAbilityCooldowns");
                method.setAccessible(true);
                method.invoke(power);
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "StandPower.tickAbilityCooldowns is not reachable", e);
            }
        }

        private int cooldown(String name) {
            return power.getAbilityCooldown(name);
        }

        private void close() {
            user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get())
                    .heldKeys.clear();
            standAction.setAction(null, user, SyncType.NO_SYNC);
            if (power.isSummoned()) {
                LittleFeetAddon.LITTLE_FEET.get().forceUnsummon(user, power);
            }
            LittleFeetScaleService.clearEntity(user);
            user.discard();
        }
    }

    private static ServerPlayer placedServerPlayer(GameTestHelper helper) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), "lf-empty-bar");
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        var position = helper.absoluteVec(new net.minecraft.world.phys.Vec3(
                1.5D, 2.0D, 1.5D));
        player.teleportTo(position.x, position.y, position.z);
        return player;
    }

    // Vanilla makeMockPlayer answers isLocalPlayer() true, so core reads
    // the client-only ClientModSettings when the Stand binds it on the
    // server. A real ServerPlayer answers false; mirror that here.
    private static Player serverSideMockPlayer(GameTestHelper helper) {
        return new Player(
                helper.getLevel(),
                BlockPos.ZERO,
                0.0F,
                new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    private static RegistryFriendlyByteBuf lanBuffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                helper.getLevel().registryAccess(),
                ConnectionType.NEOFORGE);
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }
}
