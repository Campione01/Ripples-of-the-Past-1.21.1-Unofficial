package rotp.addons.kingcrimson.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.network.EpitaphForesightPayload;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonForesightGameTests {
    private static final TicketType<BlockPos> ARENA_TICKET =
            TicketType.create("king_crimson_foresight_test", BlockPos::compareTo, 80);
    // Each test first waits for its arena chunks, which the test server places at far random positions and may
    // still be generating; that wait took over 37 ticks in some runs. The timeouts below are this budget plus the
    // test's own tick span; every timing assertion is relative to the moment the arena ticks, so none is loosened.
    private static final int ARENA_LOAD_TICKS = 140;

    private KingCrimsonForesightGameTests() {}

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "epitaphImmutableCourse")
    public static void mobFateKeepsCornersAndReachesItsImmutableEndpoint(GameTestHelper helper) {
        TestArena arena = prepareArena(helper);
        helper.startSequence().thenWaitUntil(() -> arena.assertTicking(helper))
                .thenExecute(() -> runImmutableCourse(helper, arena));
    }

    private static void runImmutableCourse(GameTestHelper helper, TestArena arena) {
        System.out.println("KC-FORESIGHT immutable course arena entity ticking after " + helper.getTick() + " ticks");
        PlayerFixture fixture = makePlayer(helper, "epitaph-course", new BlockPos(0, 2, 0));
        StandPower power = PowerClass.STAND.attachGet(fixture.player());
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        Cow cow = makeCow(helper, new BlockPos(1, 2, 1));
        BlockPos corner = helper.absolutePos(new BlockPos(3, 2, 1));
        BlockPos end = helper.absolutePos(new BlockPos(3, 2, 4));
        Path path = new Path(new ArrayList<>(List.of(
                new Node(corner.getX(), corner.getY(), corner.getZ()),
                new Node(end.getX(), end.getY(), end.getZ()))), end, true);
        helper.assertTrue(cow.getNavigation().moveTo(path, 1.0D), "Could not install the forecast path.");
        EpitaphFate fate = bindMomentum(helper, fixture.player(), cow, 0.2D, 20);
        Vec3 expected = Vec3.atBottomCenterOf(corner).add(0.0D, 0.0D, 2.0D);
        assertPosition(helper, fate.foreseen, expected, "The path forecast endpoint was not truncated at its horizon.");

        path.replaceNode(1, new Node(end.getX() - 3, end.getY(), end.getZ()));
        path.setNextNodeIndex(path.getNodeCount());
        cow.getNavigation().stop();
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(cow.getX() > fate.origin.x + 1.0D,
                    "The registered entity-tick hook did not advance the fated mob.");
            helper.assertTrue(Math.abs(cow.getZ() - fate.origin.z) < 0.01D,
                    "The fated mob cut across the corner instead of following the recorded course.");
        });
        helper.runAfterDelay(23, () -> {
            try {
                assertPosition(helper, cow.position(), expected,
                        "Navigation mutation changed or prevented the forecast endpoint.");
                helper.assertTrue(!KingCrimsonAddon.epitaphState(fixture.player()).fates().containsKey(cow.getUUID()),
                        "A fulfilled fate was not released after its terminal tick.");
                helper.succeed();
            } finally {
                cow.discard();
                fixture.player().discard();
                arena.close();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 80, batch = "epitaphCollision")
    public static void unguidedFateIncludesTerminalTickAndCannotCrossNewBlocks(GameTestHelper helper) {
        TestArena arena = prepareArena(helper);
        helper.startSequence().thenWaitUntil(() -> arena.assertTicking(helper))
                .thenExecute(() -> runCollision(helper, arena));
    }

    private static void runCollision(GameTestHelper helper, TestArena arena) {
        PlayerFixture fixture = makePlayer(helper, "epitaph-wall", new BlockPos(0, 2, 0));
        StandPower power = PowerClass.STAND.attachGet(fixture.player());
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        Cow clear = makeCow(helper, new BlockPos(1, 2, 1));
        Cow blocked = makeCow(helper, new BlockPos(1, 2, 3));
        Cow removed = makeCow(helper, new BlockPos(0, 2, 4));
        EpitaphFate clearFate = bindMomentum(helper, fixture.player(), clear, 0.1D, 20);
        EpitaphFate blockedFate = bindMomentum(helper, fixture.player(), blocked, 0.1D, 20);
        bindMomentum(helper, fixture.player(), removed, 0.1D, 20);
        removed.discard();
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 3), Blocks.STONE);
        double wallX = helper.absolutePos(new BlockPos(2, 2, 3)).getX();

        helper.runAfterDelay(23, () -> {
            assertPosition(helper, clear.position(), clearFate.foreseen,
                    "The unguided fate was released before its inclusive terminal step.");
            helper.assertTrue(blocked.getBoundingBox().maxX <= wallX + 1.0E-5D,
                    "Fate enforcement moved a real mob through a newly placed wall.");
            helper.assertTrue(blocked.position().distanceToSqr(blockedFate.foreseen) > 0.25D,
                    "The blocked forecast was incorrectly completed through solid blocks.");
            helper.assertTrue(!KingCrimsonAddon.epitaphState(fixture.player()).fates().containsKey(removed.getUUID()),
                    "An idle vision retained a removed target that could no longer emit an entity tick.");
        });
        helper.runAfterDelay(20 + KingCrimsonEpitaphForesightSpec.FATE_GRACE_TICKS + 3, () -> {
            try {
                helper.assertTrue(KingCrimsonAddon.epitaphState(fixture.player()).fates().isEmpty(),
                        "A blocked fate outlived its bounded grace period while the vision was idle.");
                helper.assertTrue(blocked.getBoundingBox().maxX <= wallX + 1.0E-5D,
                        "The blocked mob crossed the wall during its grace period.");
                helper.succeed();
            } finally {
                clear.discard();
                blocked.discard();
                fixture.player().discard();
                arena.close();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "epitaphEraseOutcome")
    public static void visionExpiryAndEraseExitPreserveTheShownMobOutcome(GameTestHelper helper) {
        TestArena arena = prepareArena(helper);
        helper.startSequence().thenWaitUntil(() -> arena.assertTicking(helper))
                .thenExecute(() -> runEraseOutcome(helper, arena));
    }

    private static void runEraseOutcome(GameTestHelper helper, TestArena arena) {
        PlayerFixture fixture = makePlayer(helper, "epitaph-erase", new BlockPos(0, 2, 0));
        PlayerFixture observer = makePlayer(helper, "epitaph-observer", new BlockPos(4, 2, 3));
        ServerPlayer player = fixture.player();
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        Cow cow = makeCow(helper, new BlockPos(1, 2, 1));
        Cow mount = makeCow(helper, new BlockPos(3, 2, 4));
        EpitaphFate fate = bindMomentum(helper, player, cow, 0.1D, 20);
        KingCrimsonAddon.epitaphState(player).arm(
                EpitaphState.PredictionKind.DIRECT_ATTACK, helper.getLevel().getGameTime() + 3);
        helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(player),
                "The production Time Erase service rejected the integration fixture.");

        helper.runAfterDelay(6, () -> {
            helper.assertTrue(KingCrimsonAddon.epitaphState(player).stage() == EpitaphState.Stage.IDLE,
                    "The ordinary Epitaph window did not expire.");
            helper.assertTrue(KingCrimsonAddon.epitaphState(player).fates().get(cow.getUUID()) == fate,
                    "Ordinary vision expiry erased a future that had already been shown.");
            helper.assertTrue(fixture.listener().lastForesight != null,
                    "The production server tick did not send the foresight payload.");
            Vec3 shown = fixture.listener().lastForesight.creatures().stream()
                    .filter(creature -> creature.entityUuid().equals(cow.getUUID()))
                    .map(EpitaphForesightPayload.Creature::foreseen).findFirst().orElse(null);
            helper.assertTrue(shown != null, "The committed mob was missing from the owner's forecast payload.");
            assertPosition(helper, shown, fate.foreseen, "The transmitted future differed from the committed outcome.");

            observer.player().moveTo(observer.player().getX() - 0.5D, observer.player().getY(),
                    observer.player().getZ() - 0.5D, 67.0F, -12.0F);
            helper.assertTrue(observer.player().startRiding(mount, true), "Could not mount the tracked observer.");
            Vec3 observerPosition = observer.player().position();
            Vec3 observerMotion = new Vec3(0.02D, 0.13D, -0.03D);
            observer.player().setDeltaMovement(observerMotion);
            Vec3 actualMobPosition = cow.position();
            Vec3 actualOwnerPosition = player.position();
            KingCrimsonTemporalStateService.terminate(player);

            assertPosition(helper, cow.position(), actualMobPosition, "Erase exit rewound the real fated mob.");
            assertPosition(helper, player.position(), actualOwnerPosition, "Erase exit automatically moved the caster.");
            assertPosition(helper, observer.player().position(), observerPosition,
                    "Erase exit teleported a real observing player.");
            assertPosition(helper, observer.player().getDeltaMovement(), observerMotion,
                    "Erase cleanup overwrote the observing player's velocity.");
            helper.assertTrue(observer.player().getYRot() == 67.0F && observer.player().getXRot() == -12.0F
                            && observer.player().getVehicle() == mount,
                    "Erase cleanup changed another player's rotation or riding state.");
            helper.assertTrue(KingCrimsonAddon.epitaphState(player).fates().get(cow.getUUID()) == fate,
                    "Manual erase exit discarded the pending forecast outcome.");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(KingCrimsonTimeEraseDecoyEntity.class,
                            player.getBoundingBox().inflate(8.0D)).isEmpty(),
                    "Cleanup-only erase exit left a server decoy behind.");
            observer.player().stopRiding();
            observer.player().discard();
            mount.discard();
        });
        helper.runAfterDelay(23, () -> {
            try {
                assertPosition(helper, cow.position(), fate.foreseen,
                        "The shown outcome failed to occur after vision expiry and erase exit.");
                helper.assertTrue(KingCrimsonAddon.epitaphState(player).fates().isEmpty(),
                        "The completed integration fate remained bound.");
                helper.succeed();
            } finally {
                KingCrimsonTemporalStateService.terminate(player);
                observer.player().discard();
                mount.discard();
                cow.discard();
                player.discard();
                arena.close();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 30, batch = "epitaphProjectileOcclusion")
    public static void projectileCannotForeseeHittingTheCasterThroughANearerBlock(GameTestHelper helper) {
        TestArena arena = prepareArena(helper);
        helper.startSequence().thenWaitUntil(() -> arena.assertTicking(helper))
                .thenExecute(() -> runProjectileOcclusion(helper, arena));
    }

    private static void runProjectileOcclusion(GameTestHelper helper, TestArena arena) {
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        helper.assertTrue(arrow != null, "Could not create the forecast projectile.");
        if (arrow == null) {
            return;
        }
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 1)));
        arrow.setPos(origin);
        arrow.setDeltaMovement(4.0D, 0.0D, 0.0D);
        arrow.setNoGravity(true);
        helper.assertTrue(helper.getLevel().noCollision(arrow, arrow.getBoundingBox()),
                "The projectile fixture spawned inside a test-structure collision shape.");
        helper.assertTrue(helper.getLevel().addFreshEntity(arrow), "Could not register the forecast projectile.");
        AABB userBox = new AABB(origin.add(3.0D, -0.5D, -0.3D), origin.add(3.6D, 0.5D, 0.3D));
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
        try {
            EpitaphForesightPayload.Flight blocked = EpitaphForesightService.foreseeFlight(
                    helper.getLevel(), arrow, 1, userBox);
            helper.assertTrue(!blocked.hitsUser(), "A nearer block did not occlude the predicted caster hit.");
            helper.assertTrue(blocked.points().getLast().x < userBox.minX,
                    "The blocked forecast continued past the actual block collision.");
            helper.setBlock(new BlockPos(2, 2, 1), Blocks.AIR);
            helper.assertTrue(EpitaphForesightService.foreseeFlight(helper.getLevel(), arrow, 1, userBox).hitsUser(),
                    "The same unobstructed projectile no longer predicted its caster intersection.");
            helper.succeed();
        } finally {
            arrow.discard();
            arena.close();
        }
    }

    private static TestArena prepareArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos first = new ChunkPos(origin);
        ChunkPos last = new ChunkPos(helper.absolutePos(new BlockPos(4, 4, 4)));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = first.x; x <= last.x; x++) {
            for (int z = first.z; z <= last.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                level.getChunkSource().addRegionTicket(ARENA_TICKET, chunk, 2, origin, true);
                chunks.add(chunk);
            }
        }
        // The test server ticks without pausing while worldgen runs on its own threads, so a tick budget alone can
        // run out before a far arena is generated. Generating the arena and the two-chunk ring that entity ticking
        // needs around it here leaves the wait below only the ticket promotion.
        for (int x = first.x - 2; x <= last.x + 2; x++) {
            for (int z = first.z - 2; z <= last.z + 2; z++) {
                level.getChunk(x, z);
            }
        }
        // The shared 1x1x1 template has a barrier shell; this suite needs a 5x5 movement footprint.
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        return new TestArena(level, origin, List.copyOf(chunks));
    }

    private static EpitaphFate bindMomentum(GameTestHelper helper, ServerPlayer player, Cow cow,
            double speed, int ticksAhead) {
        cow.xo = cow.getX() - speed;
        cow.yo = cow.getY();
        cow.zo = cow.getZ();
        EpitaphFate fate = EpitaphForesightService.commit(
                helper.getLevel(), cow, ticksAhead, helper.getLevel().getGameTime());
        helper.assertTrue(fate != null, "The production forecast did not commit the moving mob.");
        if (fate == null) {
            throw new IllegalStateException("Missing forecast after assertion.");
        }
        KingCrimsonAddon.epitaphState(player).fates().put(cow.getUUID(), fate);
        return fate;
    }

    private static Cow makeCow(GameTestHelper helper, BlockPos relativePosition) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Could not create a foresight target mob.");
        if (cow == null) {
            throw new IllegalStateException("Missing Cow after assertion.");
        }
        cow.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relativePosition)));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(helper.getLevel().noCollision(cow, cow.getBoundingBox()),
                "The mob fixture spawned inside a test-structure collision shape: " + cow.position());
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not register the foresight target mob.");
        return cow;
    }

    private static PlayerFixture makePlayer(GameTestHelper helper, String name, BlockPos relativePosition) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        SilentPacketListener listener = new SilentPacketListener(server, connection, player,
                CommonListenerCookie.createInitial(profile, false));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relativePosition)));
        player.setNoGravity(true);
        level.addNewPlayer(player);
        // Embedded fixture connections have no server network loop to drive the player's full tick.
        helper.onEachTick(() -> {
            if (!player.isRemoved()) {
                player.doTick();
            }
        });
        return new PlayerFixture(player, listener);
    }

    private static void assertPosition(GameTestHelper helper, Vec3 actual, Vec3 expected, String message) {
        helper.assertTrue(actual.distanceToSqr(expected) < 1.0E-4D,
                message + " Expected " + expected + ", got " + actual + ".");
    }

    private record PlayerFixture(ServerPlayer player, SilentPacketListener listener) {}

    private record TestArena(ServerLevel level, BlockPos origin, List<ChunkPos> chunks) implements AutoCloseable {
        private void assertTicking(GameTestHelper helper) {
            for (ChunkPos chunk : chunks) {
                helper.assertTrue(level.isPositionEntityTicking(
                                new BlockPos(chunk.getMinBlockX(), origin.getY() + 2, chunk.getMinBlockZ())),
                        "The test arena chunk is not yet entity ticking: " + chunk);
            }
        }

        @Override
        public void close() {
            for (ChunkPos chunk : chunks) {
                level.getChunkSource().removeRegionTicket(ARENA_TICKET, chunk, 2, origin, true);
            }
        }
    }

    private static final class SilentPacketListener extends ServerGamePacketListenerImpl {
        private EpitaphForesightPayload lastForesight;

        private SilentPacketListener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
            capture(packet);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            capture(packet);
        }

        private void capture(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof EpitaphForesightPayload payload) {
                lastForesight = payload;
            }
        }
    }
}
