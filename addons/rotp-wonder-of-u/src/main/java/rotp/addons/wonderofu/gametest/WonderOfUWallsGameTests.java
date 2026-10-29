package rotp.addons.wonderofu.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
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
import net.minecraft.network.FriendlyByteBuf;
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
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.ability.ExplosionAbility;
import rotp.addons.wonderofu.ability.LightningStrikeAbility;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Calamity strikes at its target (owner, 2026-10-15: walls follow the manga). As 1.16's aimWithStandOrUser(100) ray
 * did, the lightning and the explosion variations reach a mob behind a wall instead of stopping at the wall.
 */
@GameTestHolder("rotp_wou_car")
@PrefixGameTestTemplate(false)
public final class WonderOfUWallsGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WALL_DISTANCE = 3;
    private static final int MOB_DISTANCE = 7;

    private WonderOfUWallsGameTests() {}

    @GameTest(template = "empty", batch = "wou_walls_lightning", timeoutTicks = 160)
    public static void lightningStrikesAMobBehindAWall(GameTestHelper helper) {
        run(helper, "lightning", "lightning_strike", (short) 101, LightningStrikeAbility.LightningStrikeAction.class);
    }

    @GameTest(template = "empty", batch = "wou_walls_explosion", timeoutTicks = 160)
    public static void explosionStrikesAMobBehindAWall(GameTestHelper helper) {
        run(helper, "explosion", "explosion", (short) 102, ExplosionAbility.ExplosionAction.class);
    }

    private static void run(GameTestHelper helper, String name, String abilityName, short key,
            Class<? extends EntityActionInstance> actionClass) {
        Scene scene = new Scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(scene.stand.blockPosition()),
                        "Wonder of U's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        scene.build();
                        scene.press(abilityName, key, actionClass);
                    }
                    catch (RuntimeException | Error failure) {
                        scene.close();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(scene.action.isOver(),
                        abilityName + " has not performed yet."))
                .thenExecute(() -> {
                    List<Vec3> strikes = List.copyOf(scene.strikes);
                    AABB mob = scene.zombie.getBoundingBox().inflate(1.0D);
                    boolean wallStands = helper.getLevel().getBlockState(scene.wallCenter).is(Blocks.STONE);
                    scene.close();
                    helper.assertTrue(wallStands, "The wall was gone before the strike.");
                    helper.assertTrue(!strikes.isEmpty(), abilityName
                            + " struck nothing: its aim stopped at the wall in front of the mob.");
                    helper.assertTrue(mob.contains(strikes.get(0)), abilityName + " struck at " + strikes.get(0)
                            + ", not at the mob behind the wall " + mob);
                    LOGGER.info("R770_WOU_WALLS_PASS {} strike={}", name, strikes.get(0));
                })
                .thenSucceed();
    }

    // A GameTest listener, so a failed or timed-out test still unregisters its listener and clears its blocks.
    private static final class Scene implements GameTestListener {
        private final ServerLevel level;
        private final ServerPlayer owner;
        private final StandPower power;
        private final WonderOfUStandEntity stand;
        private final BlockPos ownerPos;
        private final List<BlockPos> wall = new ArrayList<>();
        private final List<BlockState> replaced = new ArrayList<>();
        private final List<Vec3> strikes = new ArrayList<>();
        private BlockPos wallCenter;
        private Zombie zombie;
        private EntityActionInstance action;
        private Consumer<EntityJoinLevelEvent> joinListener;
        private Consumer<ExplosionEvent.Start> explosionListener;
        private short key;
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
                            new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Wonder of U");
            helper.assertTrue(WonderOfUAddon.WONDER_OF_YOU.get().summon(owner, power)
                            && power.getSummonedStandEntity() instanceof WonderOfUStandEntity,
                    "Production Wonder of U summon failed");
            stand = (WonderOfUStandEntity) power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.setNoGravity(true);
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
            wallCenter = new BlockPos(wallX, ownerPos.getY() + 1, ownerPos.getZ());
            zombie = EntityType.ZOMBIE.create(level);
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.setSilent(true);
            zombie.moveTo(ownerPos.getX() + MOB_DISTANCE + 0.5D, ownerPos.getY(), ownerPos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(zombie);
            owner.lookAt(EntityAnchorArgument.Anchor.EYES,
                    new Vec3(zombie.getX(), owner.getEyeY(), zombie.getZ()));
        }

        private void press(String abilityName, short key, Class<? extends EntityActionInstance> actionClass) {
            this.key = key;
            // The strike's own entity is cancelled once its place is known: the test needs where it lands, not the
            // fire or the blast.
            joinListener = event -> {
                if (event.getLevel() == level && (event.getEntity() instanceof LightningBolt
                        || event.getEntity() instanceof PrimedTnt || event.getEntity() instanceof Creeper)) {
                    strikes.add(event.getEntity().position());
                    event.setCanceled(true);
                }
            };
            explosionListener = event -> {
                if (event.getLevel() == level) {
                    strikes.add(event.getExplosion().center());
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, joinListener);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExplosionEvent.Start.class, explosionListener);
            Ability ability = power.getAbility(abilityName);
            if (ability == null) {
                throw new IllegalStateException(abilityName + " is not in the moveset");
            }
            power.setStamina(power.getMaxStamina());
            AbilityInput.keyPress(key, ability, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.HOLD,
                    0.0F, BufferingState.clickCanBuffer(), ability.abilityId);
            action = LivingComponentAction.getCurEntityAction(stand);
            if (!actionClass.isInstance(action)) {
                throw new IllegalStateException("The press did not start " + abilityName + ": " + action);
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (joinListener != null) {
                    NeoForge.EVENT_BUS.unregister(joinListener);
                }
                if (explosionListener != null) {
                    NeoForge.EVENT_BUS.unregister(explosionListener);
                }
                if (zombie != null) {
                    zombie.discard();
                }
                for (int i = 0; i < wall.size(); i++) {
                    level.setBlockAndUpdate(wall.get(i), replaced.get(i));
                }
                AbilityInput.keyRelease(key, owner);
                if (power.isSummoned()) {
                    WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(owner, power);
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

    private static ServerPlayer makeServerPlayer(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "wou-walls");
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
