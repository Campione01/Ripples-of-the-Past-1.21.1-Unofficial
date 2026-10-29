package rotp.addons.d4c;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.addons.d4c.ability.D4CPhase2BAbility;
import rotp.addons.d4c.ability.D4CPhase2BAbility.Mode;
import rotp.addons.d4c.entity.D4CSummonKickEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Walls follow the manga (owner, 2026-10-15). The Annihilation mark is laid on its target, as 1.16's 15-block entity
 * ray (aa/ab.java:114) that ran through blocks: it marks a mob behind a wall. The summon kick's phantom appears beside
 * the user and flies at the target, and the rapid punch and hand chop are D4C's own blows, so they need sight: they
 * strike through the core punch rule (StandEntityPunchAbility.getFreshPunchTarget), whose aim walls stop.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CWallsGameTests {
    private static final String MARK_TAG = "rotp_d4c.phase2b.annihilation_target";

    private D4CWallsGameTests() {}

    @GameTest(template = "empty", batch = "d4cWallsMark", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void annihilationMarkReachesAMobBehindAWall(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> annihilationMarkReachesAMobBehindAWallBody(h));
    }

    private static void annihilationMarkReachesAMobBehindAWallBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 2; x++) {
            for (int y = 2; y <= 4; y++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 2)), Blocks.STONE.defaultBlockState());
            }
        }
        ServerPlayer player = makeServerPlayer(helper, "d4c-walls-mark");
        StandPower power = null;
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 4);
        Cow decoy = null;
        try {
            power = summonD4C(helper, player);
            lookAt(player, cow);
            helper.assertTrue(level.getBlockState(BlockPos.containing(
                            player.getEyePosition().add(player.getLookAngle()))).is(Blocks.STONE),
                    "Setup: the wall is not between the user and the cow.");

            Ability mark = power.getAbility(D4CAddon.ANNIHILATION_MARK_NAME);
            helper.assertTrue(mark instanceof D4CPhase2BAbility, "Annihilation Mark is not a D4C action.");
            helper.assertTrue(mark.checkSpecificConditions(power).isPositive(),
                    "Annihilation Mark refused the cow behind the wall: its ray stopped at the wall.");
            // A second mob in plain sight is marked first, so that marking the cow is a new mark at the full cost.
            decoy = helper.spawnWithNoFreeWill(EntityType.COW, 6, 2, 1);
            lookAt(player, decoy);
            helper.assertTrue(D4CPhase2BService.execute(Mode.ANNIHILATION_MARK, level, player)
                            && decoy.getUUID().equals(player.getPersistentData().getUUID(MARK_TAG)),
                    "Setup: Annihilation Mark did not mark the mob in sight.");
            lookAt(player, cow);
            helper.assertTrue(D4CPhase2BService.staminaCost(Mode.ANNIHILATION_MARK, player) == 50.0F,
                    "Marking the cow behind the wall while another mob is marked costs 50 in 1.16 (aa/ab.java:75-78), "
                            + "not the 25 of un-marking: " + D4CPhase2BService.staminaCost(Mode.ANNIHILATION_MARK, player));
            helper.assertTrue(D4CPhase2BService.execute(Mode.ANNIHILATION_MARK, level, player)
                            && player.getPersistentData().hasUUID(MARK_TAG)
                            && cow.getUUID().equals(player.getPersistentData().getUUID(MARK_TAG)),
                    "Annihilation Mark did not mark the cow behind the wall.");

            helper.assertTrue(!D4CPhase2BService.canSummonKick(player),
                    "The summon kick's phantom flies from beside the user: it must not pick a target behind a wall.");

            // the rapid punch and the hand chop strike through the core punch rule
            // (StandEntityPunchAbility.getFreshPunchTarget), whose aim stops at walls for an action not marked
            // through them (StandSkillWalls)
            StandEntity stand = power.getSummonedStandEntity();
            stand.setPos(player.position().add(0.0D, 0.0D, 0.4D));
            helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(cow)).getEntity() == cow,
                    "Setup: the cow behind the wall is not within D4C's reach, so the wall would not be what stops "
                            + "the blows.");
            for (String blow : new String[] {"d4c_rapid_punch", "d4c_hand_chop"}) {
                start(helper, player, power, stand, blow);
                ActionTarget strike = StandEntityPunchAbility.getFreshPunchTarget(stand, ActionTarget.EMPTY);
                helper.assertTrue(strike.getEntity() != cow,
                        "D4C's " + blow + " is its own blow: aimed at the cow behind the wall, the core punch rule "
                                + "must not strike it; it strikes " + strike + ".");
                helper.assertTrue(StandSkillWalls.wallsHideEntities(stand),
                        "D4C's " + blow + " is marked through walls; it is one of D4C's own blows.");
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            }
        }
        finally {
            player.getPersistentData().remove(MARK_TAG);
            cow.discard();
            if (decoy != null) {
                decoy.discard();
            }
            if (power != null && power.getSummonedStandEntity() != null) {
                power.getSummonedStandEntity().discard();
            }
            player.discard();
            for (int x = 0; x <= 2; x++) {
                for (int y = 2; y <= 4; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 2)), Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.succeed();
    }

    // 1.16 summon_kick (aa/aa/ae.java, TargetRequirement.ENTITY) took the client's crosshair target, which blocks
    // stop (InputHandler.pickMouseTarget; the Stand's precision ray only when the vanilla pick met nothing in reach),
    // whatever task D4C ran. The kick runs no action, so the mark D4C runs must not lend it its answer.
    @GameTest(template = "empty", batch = "d4cWallsKick", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void summonKickDuringAnAnnihilationMarkStillNeedsSight(GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> summonKickDuringAnAnnihilationMarkBody(h));
    }

    private static void summonKickDuringAnAnnihilationMarkBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 2; x++) {
            for (int y = 2; y <= 4; y++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 2)), Blocks.STONE.defaultBlockState());
            }
        }
        ServerPlayer player = makeServerPlayer(helper, "d4c-walls-kick");
        StandPower power = null;
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 4);
        Cow inSight = helper.spawnWithNoFreeWill(EntityType.COW, 6, 2, 1);
        try {
            power = summonD4C(helper, player);
            StandEntity stand = power.getSummonedStandEntity();
            lookAt(player, cow);
            helper.assertTrue(level.getBlockState(BlockPos.containing(
                            player.getEyePosition().add(player.getLookAngle()))).is(Blocks.STONE),
                    "Setup: the wall is not between the user and the cow.");
            start(helper, player, power, stand, D4CAddon.ANNIHILATION_MARK_NAME);
            helper.assertTrue(!StandSkillWalls.wallsHideEntities(player),
                    "Setup: the running annihilation_mark does not answer for its user's rays.");

            helper.assertTrue(!D4CPhase2BService.canSummonKick(player),
                    "During D4C's annihilation_mark the summon kick took the cow behind the wall; 1.16's kick took "
                            + "the crosshair target, which the wall stops.");
            helper.assertTrue(!D4CPhase2BService.summonKick(level, player),
                    "During D4C's annihilation_mark the summon kick was sent at the cow behind the wall.");
            List<D4CSummonKickEntity> kicks = level.getEntitiesOfClass(D4CSummonKickEntity.class,
                    new AABB(player.blockPosition()).inflate(16.0D));
            helper.assertTrue(kicks.isEmpty(), "A kick was summoned at the cow behind the wall: " + kicks.size());

            lookAt(player, inSight);
            helper.assertTrue(D4CPhase2BService.canSummonKick(player),
                    "Control: during the mark the summon kick refused a cow in plain sight.");
        }
        finally {
            level.getEntitiesOfClass(D4CSummonKickEntity.class, new AABB(player.blockPosition()).inflate(16.0D))
                    .forEach(Entity::discard);
            cow.discard();
            inSight.discard();
            if (power != null && power.getSummonedStandEntity() != null) {
                LivingComponentAction.getComponent(power.getSummonedStandEntity()).setAction(null, SyncType.NO_SYNC);
                power.getSummonedStandEntity().discard();
            }
            player.discard();
            for (int x = 0; x <= 2; x++) {
                for (int y = 2; y <= 4; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 2)), Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.succeed();
    }

    private static void start(GameTestHelper helper, ServerPlayer player, StandPower power, StandEntity stand,
            String name) {
        helper.assertTrue(power.getAbility(name) instanceof EntityActionType, "D4C has no " + name);
        EntityActionType actionType = (EntityActionType) power.getAbility(name);
        EntityActionInstance action = actionType.createActionObj();
        actionType.initActionFromConfig(action, helper.getLevel(), player, stand);
        action.setStartingPhase();
        LivingComponentAction.getComponent(stand).setAction(action, player, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action, "Setup: " + name + " did not become D4C's action.");
    }

    private static void lookAt(ServerPlayer player, Cow target) {
        Vec3 aim = target.position().add(0.0D, 0.7D, 0.0D).subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        player.setYHeadRot(player.getYRot());
    }

    private static StandPower summonD4C(GameTestHelper helper, ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        StandPower power = StandPower.get(player);
        helper.assertTrue(power != null, "The mock player has no Stand power attachment.");
        power.setStand(D4CAddon.D4C.get());
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(D4CAddon.D4C.get().summon(player, power) && power.getSummonedStandEntity() != null,
                "D4C could not be summoned.");
        power.getSummonedStandEntity().summonLockTicks = 0;
        return power;
    }

    private static ServerPlayer makeServerPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentServerPacketListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
        level.addNewPlayer(player);
        return player;
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
