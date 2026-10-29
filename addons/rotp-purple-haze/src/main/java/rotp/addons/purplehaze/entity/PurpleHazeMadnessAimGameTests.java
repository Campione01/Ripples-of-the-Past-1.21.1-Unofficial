package rotp.addons.purplehaze.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 060 PurpleHazeEntity.aimWithThisOrUser: a mad Purple Haze punches its madness target from its own eyes, whatever
 * its user looks at.
 */
@GameTestHolder(PurpleHazeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PurpleHazeMadnessAimGameTests {
    private static final int OWN_TICKS = 100;
    private static final int WAIT_TICKS = 4000;
    private static final long WAIT_NANOS = 30_000_000_000L;
    // Chunk level 31 ticks entities, as a forced chunk does.
    private static final int ENTITY_TICKING_RADIUS = 2;
    private static final TicketType<BlockPos> FIXTURE_TICKET =
            TicketType.create("rotp_ph_gametest_fixture", BlockPos::compareTo);

    private PurpleHazeMadnessAimGameTests() {}

    @GameTest(template = "empty", timeoutTicks = WAIT_TICKS + OWN_TICKS)
    public static void madPurpleHazeHitsItsTargetWhileItsUserLooksAtABlock(GameTestHelper helper) {
        whenTicking(helper, () -> {
            Scene scene = new Scene(helper);
            try {
                scene.run();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 aimed from the Stand's eyes only when isMad() && autoAttackTarget != null.
    @GameTest(template = "empty", batch = "ph_mad_no_target", timeoutTicks = WAIT_TICKS + OWN_TICKS)
    public static void madPurpleHazeWithoutATargetKeepsItsUsersAim(GameTestHelper helper) {
        whenTicking(helper, () -> {
            Scene scene = new Scene(helper);
            try {
                scene.runWithoutTarget();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    private static final class Scene {
        final GameTestHelper helper;
        final ServerLevel level;
        final Vec3 origin;
        final List<BlockPos> placed = new ArrayList<>();
        final List<BlockState> previous = new ArrayList<>();
        ServerPlayer owner;
        StandPower power;
        PurpleHazeEntity stand;
        Cow cow;
        boolean closed;

        Scene(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            origin = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        }

        void run() {
            owner = mockPlayer();
            owner.setNoGravity(true);
            // the user looks at a bedrock block at eye level, half a block ahead
            BlockPos block = BlockPos.containing(origin.x, origin.y + 1.0D, origin.z + 1.0D);
            placed.add(block);
            previous.add(level.getBlockState(block));
            level.setBlockAndUpdate(block, Blocks.BEDROCK.defaultBlockState());
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(block));
            ActionTarget looked = new ActionTarget(block, Direction.NORTH);
            LivingComponentAction.getComponent(owner).entityAim.setTarget(looked);

            // the only mob around, off to the side of the look
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not create a cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            cow.setHealth(500.0F);
            cow.moveTo(origin.x + 2.0D, origin.y, origin.z + 1.0D, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");

            power = StandPower.get(owner);
            helper.assertTrue(power != null, "The mock player has no Stand power");
            power.setStand(PurpleHazeAddon.PURPLE_HAZE.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(owner, power), "Could not summon Purple Haze");
            helper.assertTrue(power.getSummonedStandEntity() instanceof PurpleHazeEntity,
                    "Missing the Purple Haze Stand");
            stand = (PurpleHazeEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setMadness(true, true);
            helper.assertTrue(stand.isMad(), "Fixture: Purple Haze did not go mad");
            ActionTarget crosshair = StandEntityPunchAbility.crosshairPick(stand, owner, 1.0F,
                    entity -> entity != stand);
            helper.assertTrue(crosshair.getType() == ActionTarget.TargetType.BLOCK
                            && block.equals(crosshair.getBlockPos()),
                    "Fixture: the user does not look at the block: " + crosshair);

            float health = cow.getHealth();
            List<String> punched = new ArrayList<>();
            poll(0, () -> {
                // a client keeps sending what its crosshair meets
                LivingComponentAction.getComponent(owner).entityAim.setTarget(looked);
                EntityActionInstance action = stand.getCurStandAction();
                if (action != null && action.punchedTarget != null) {
                    String target = action.punchedTarget.toString();
                    if (punched.isEmpty() || !punched.get(punched.size() - 1).equals(target)) {
                        punched.add(target);
                    }
                }
                helper.assertTrue(stand.getMadTargetUuid() != null && stand.getMadTargetUuid().equals(cow.getUUID()),
                        "Fixture: the cow is not the madness target");
                helper.assertTrue(cow.getHealth() < health,
                        "The mad Purple Haze did not hit its madness target; its punches struck " + punched);
                helper.assertTrue(level.getBlockState(block).is(Blocks.BEDROCK), "Fixture: the block is gone");
            });
        }

        void runWithoutTarget() {
            owner = mockPlayer();
            owner.setNoGravity(true);
            BlockPos block = BlockPos.containing(origin.x, origin.y + 1.0D, origin.z + 1.0D);
            placed.add(block);
            previous.add(level.getBlockState(block));
            level.setBlockAndUpdate(block, Blocks.BEDROCK.defaultBlockState());
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(block));
            ActionTarget looked = new ActionTarget(block, Direction.NORTH);
            LivingComponentAction.getComponent(owner).entityAim.setTarget(looked);

            power = StandPower.get(owner);
            helper.assertTrue(power != null, "The mock player has no Stand power");
            power.setStand(PurpleHazeAddon.PURPLE_HAZE.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(owner, power), "Could not summon Purple Haze");
            helper.assertTrue(power.getSummonedStandEntity() instanceof PurpleHazeEntity,
                    "Missing the Purple Haze Stand");
            stand = (PurpleHazeEntity) power.getSummonedStandEntity();
            stand.setMadness(true, true);
            helper.assertTrue(stand.isMad() && stand.getMadTargetUuid() == null && !stand.isManuallyControlled(),
                    "Fixture: Purple Haze is not mad without a target");
            helper.assertFalse(stand.aimsWithoutUser(),
                    "A mad Purple Haze without a madness target aims on its own; 1.16 kept its user's aim");
            ActionTarget pressed = StandEntityPunchAbility.pressTarget(stand, ActionTarget.EMPTY);
            helper.assertTrue(pressed.getType() == ActionTarget.TargetType.BLOCK && block.equals(pressed.getBlockPos()),
                    "A mad Purple Haze without a madness target did not take its user's aim: " + pressed);

            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not create a cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(origin.x + 2.0D, origin.y, origin.z + 1.0D, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
            stand.setAutoAttackTarget(cow);
            helper.assertTrue(stand.aimsWithoutUser(),
                    "Fixture: a mad Purple Haze with a madness target must aim on its own");
            close();
            helper.succeed();
        }

        // Runs the conditions each tick until they hold; after OWN_TICKS the last unmet one fails the test.
        void poll(int waited, Runnable conditions) {
            helper.runAfterDelay(1, () -> {
                try {
                    conditions.run();
                } catch (GameTestAssertException unmet) {
                    if (waited + 1 < OWN_TICKS - 5) {
                        poll(waited + 1, conditions);
                        return;
                    }
                    close();
                    throw unmet;
                } catch (Throwable error) {
                    close();
                    throw error;
                }
                close();
                helper.succeed();
            });
        }

        ServerPlayer mockPlayer() {
            MinecraftServer server = level.getServer();
            GameProfile profile = new GameProfile(UUID.randomUUID(), "purple-haze-gametest");
            ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            new SilentServerPacketListener(server, connection, player,
                    CommonListenerCookie.createInitial(profile, false));
            player.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            level.addNewPlayer(player);
            return player;
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (power != null && power.hasPower() && power.isSummoned()) {
                if (stand != null) {
                    stand.setMadness(false, false);
                }
                power.getPowerType().forceUnsummon(owner, power);
            }
            if (cow != null) {
                cow.discard();
            }
            if (owner != null) {
                owner.discard();
            }
            for (int i = placed.size() - 1; i >= 0; i--) {
                level.setBlockAndUpdate(placed.get(i), previous.get(i));
            }
        }
    }

    // The 1x1x1 template only makes its own chunk tick entities, and its origin is random: the fixture holds tickets
    // for the chunks it stands in and starts once they tick entities.
    private static void whenTicking(GameTestHelper helper, Runnable body) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos from = new ChunkPos(origin.offset(-2, 0, -2));
        ChunkPos to = new ChunkPos(origin.offset(5, 0, 5));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = from.x; x <= to.x; x++) {
            for (int z = from.z; z <= to.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                level.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, ENTITY_TICKING_RADIUS, origin);
            }
        }
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo test) {}

            @Override
            public void testPassed(GameTestInfo test, GameTestRunner runner) {
                release();
            }

            @Override
            public void testFailed(GameTestInfo test, GameTestRunner runner) {
                release();
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}

            private void release() {
                for (ChunkPos chunk : chunks) {
                    level.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, ENTITY_TICKING_RADIUS, origin);
                }
            }
        });
        await(helper, chunks, 0, System.nanoTime(), body);
    }

    private static void await(GameTestHelper helper, List<ChunkPos> chunks, int waitedTicks, long startNanos,
            Runnable body) {
        ChunkPos pending = chunks.stream()
                .filter(chunk -> !helper.getLevel().isPositionEntityTicking(chunk.getWorldPosition()))
                .findFirst()
                .orElse(null);
        if (pending == null) {
            body.run();
            return;
        }
        long waitedNanos = System.nanoTime() - startNanos;
        helper.assertTrue(waitedTicks < WAIT_TICKS - 10 && waitedNanos < WAIT_NANOS,
                "Chunk " + pending + " of the fixture did not start to tick entities (waited " + waitedTicks
                        + " ticks, " + waitedNanos / 1_000_000L + " ms)");
        try {
            Thread.sleep(1L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        helper.runAfterDelay(1, () -> await(helper, chunks, waitedTicks + 1, startNanos, body));
    }

    private static final class SilentServerPacketListener extends ServerGamePacketListenerImpl {
        private SilentServerPacketListener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {}
    }
}
