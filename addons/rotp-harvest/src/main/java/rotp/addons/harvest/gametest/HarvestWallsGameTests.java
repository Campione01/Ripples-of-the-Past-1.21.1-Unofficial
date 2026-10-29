package rotp.addons.harvest.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
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
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.harvest.HarvestAddon;
import rotp.addons.harvest.ability.HarvestCommandAbility.Command;
import rotp.addons.harvest.entity.HarvestMemberMode;
import rotp.addons.harvest.entity.HarvestStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Harvest's swarm goes where it is sent, through all of Morioh (owner, 2026-10-15: walls follow the manga). As 1.16's
 * HarvestUtil.getEntityByRayTrace, whose 7-block entity ray ran through blocks, set_target and stay_with pick a mob
 * behind a wall.
 */
@GameTestHolder(HarvestAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HarvestWallsGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WALL_DISTANCE = 3;
    private static final int MOB_DISTANCE = 6;

    private HarvestWallsGameTests() {}

    @GameTest(template = "empty", batch = "harvest_walls", timeoutTicks = 200)
    public static void commandsPickAMobBehindAWall(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(scene.ownerPos),
                        "Harvest's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        scene.build();
                        check(helper, scene, Command.SET_TARGET, HarvestMemberMode.ATTACK, "set_target");
                        check(helper, scene, Command.STAY_WITH, HarvestMemberMode.STAY_WITH, "stay_with");
                    }
                    finally {
                        scene.close();
                    }
                })
                .thenSucceed();
    }

    private static void check(GameTestHelper helper, Scene scene, Command command, HarvestMemberMode mode,
            String name) {
        helper.assertTrue(scene.coordinator.canExecuteCommand(command, scene.owner),
                name + " refused the mob behind the wall: its ray stopped at the wall.");
        scene.coordinator.executeCommand(command, scene.owner);
        helper.assertTrue(scene.coordinator.commandMode() == mode
                        && scene.coordinator.commandTarget().filter(scene.zombie.getUUID()::equals).isPresent(),
                name + " did not send the swarm to the mob behind the wall: mode "
                        + scene.coordinator.commandMode() + ", target " + scene.coordinator.commandTarget());
        LOGGER.info("R770_HARVEST_WALLS_PASS {} target={}", name, scene.zombie.getUUID());
    }

    // A GameTest listener, so a failed or timed-out test still clears its blocks and entities.
    private static final class Scene implements GameTestListener {
        private final ServerLevel level;
        private final BlockPos ownerPos;
        private final ServerPlayer owner;
        private final StandPower power;
        private final HarvestStandEntity coordinator;
        private final List<BlockPos> wall = new ArrayList<>();
        private final List<BlockState> replaced = new ArrayList<>();
        private Zombie zombie;
        private boolean closed;

        private Scene(GameTestHelper helper) {
            this.level = helper.getLevel();
            helper.testInfo.addListener(this);
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            ownerPos = new BlockPos(chunk.getMinBlockX() + 4, template.getY() + 8, chunk.getMinBlockZ() + 8);
            owner = makeServerPlayer(level, ownerPos);
            owner.setNoGravity(true);
            owner.setGameMode(GameType.SURVIVAL);
            power = PowerClass.STAND.attachGet(owner);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(HarvestAddon.HARVEST_STAND.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Harvest");
            helper.assertTrue(HarvestAddon.HARVEST_STAND.get().summon(owner, power)
                            && power.getSummonedStandEntity() instanceof HarvestStandEntity,
                    "Production Harvest summon failed");
            coordinator = (HarvestStandEntity) power.getSummonedStandEntity();
        }

        private void build() {
            int wallX = ownerPos.getX() + WALL_DISTANCE;
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = new BlockPos(wallX, ownerPos.getY() + dy, ownerPos.getZ() + dz);
                    wall.add(pos);
                    replaced.add(level.getBlockState(pos));
                    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                }
            }
            zombie = EntityType.ZOMBIE.create(level);
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setSilent(true);
            zombie.moveTo(ownerPos.getX() + MOB_DISTANCE + 0.5D, ownerPos.getY(), ownerPos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(zombie);
            owner.lookAt(EntityAnchorArgument.Anchor.EYES,
                    new Vec3(zombie.getX(), owner.getEyeY(), zombie.getZ()));
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (zombie != null) {
                    zombie.discard();
                }
                for (int i = 0; i < wall.size(); i++) {
                    level.setBlockAndUpdate(wall.get(i), replaced.get(i));
                }
                if (power.isSummoned()) {
                    HarvestAddon.HARVEST_STAND.get().forceUnsummon(owner, power);
                }
            }
            finally {
                owner.discard();
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {}

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            close();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            close();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
            close();
        }
    }

    static ServerPlayer makeServerPlayer(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "harvest-walls");
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentGameListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        player.moveTo(Vec3.atBottomCenterOf(pos));
        level.addNewPlayer(player);
        return player;
    }

    private static final class SilentGameListener extends ServerGamePacketListenerImpl {
        private SilentGameListener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {}
    }
}
