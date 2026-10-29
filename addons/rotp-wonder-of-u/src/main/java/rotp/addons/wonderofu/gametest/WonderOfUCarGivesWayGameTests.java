package rotp.addons.wonderofu.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.ability.CarCalamityAbility;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 calamity_car_attack (043 InitStands.java:63): holdToFire(20, false) + standWindupDuration(10), no recovery, and
 * CalamityAttack overrides no isCancelable. Once the charge fired, StandEntityAction.isCancelable let a pressed Stand
 * action cancel its task in the windup and at its perform's start, a tick before standPerform switched the car mode
 * (StandEntityTask moved into the perform at the end of the windup's last tick).
 */
@GameTestHolder("rotp_wou_car")
@PrefixGameTestTemplate(false)
public final class WonderOfUCarGivesWayGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short CAR_KEY = 81;
    private static final short PUNCH_KEY = 82;

    private WonderOfUCarGivesWayGameTests() {}

    @GameTest(template = "empty", batch = "wou_car_gives_way", timeoutTicks = 100)
    public static void aFiredCarCalamityGivesWayToAStandPressUntilItSwitchesAsIn116(GameTestHelper helper) {
        for (boolean atPerformStart : new boolean[] { false, true }) {
            String row = atPerformStart ? "perform_start" : "windup";
            Scene scene = new Scene(helper, atPerformStart ? 4 : 10);
            try {
                scene.run(row, atPerformStart);
            }
            finally {
                scene.close();
            }
            LOGGER.info("R770_WOU_CAR_GIVES_WAY_PASS {} ", row);
        }
        helper.succeed();
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final ServerPlayer owner;
        private final StandPower power;
        private final WonderOfUStandEntity stand;
        private final LivingComponentAction standAction;

        private Scene(GameTestHelper helper, int offset) {
            this.helper = helper;
            ServerLevel level = helper.getLevel();
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            BlockPos ownerPos = new BlockPos(chunk.getMinBlockX() + offset, template.getY() + 8,
                    chunk.getMinBlockZ() + 8);
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
            standAction = LivingComponentAction.getComponent(stand);
            power.setStamina(power.getMaxStamina());
        }

        private void run(String row, boolean atPerformStart) {
            Ability car = power.getAbility("calamity_car_attack");
            helper.assertTrue(car instanceof CarCalamityAbility, "calamity_car_attack is not in the moveset");
            BufferingState held = press(car, InputMethod.HOLD, CAR_KEY);
            EntityActionInstance action = standAction.getAction();
            helper.assertTrue(held.isActionSuccess && action != null && action.ability == car
                            && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Setup (" + row + "): the car calamity is not charging: " + action);
            for (int i = 0; i <= CarCalamityAbility.HOLD_TO_FIRE_TICKS + 2
                    && action.getPhase() == ActionPhase.BUTTON_CHARGE; i++) {
                standAction.tick();
            }
            helper.assertTrue(action.getPhase() == ActionPhase.WINDUP,
                    "Setup (" + row + "): the car calamity held for its charge did not fire into its windup: "
                            + action.getPhase());
            if (atPerformStart) {
                for (int i = 0; i <= CarCalamityAbility.WINDUP_TICKS
                        && action.getPhase() == ActionPhase.WINDUP; i++) {
                    standAction.tick();
                }
                helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && action.getPhaseTick() < 1,
                        "Setup: the car calamity is not at its perform's start: " + action.getPhase() + " tick "
                                + action.getPhaseTick());
            }
            else {
                standAction.tick();
            }
            helper.assertTrue(!stand.isCarEnabled(), "Setup (" + row + "): the car mode switched before the perform");
            BufferingState punch = press(power.getAbility("punch"), InputMethod.CLICK, PUNCH_KEY);
            EntityActionInstance now = standAction.getAction();
            helper.assertTrue(punch.isActionSuccess && !punch.shouldBuffer && now != null && now != action
                            && now.ability == power.getAbility("punch") && action.isOver(),
                    "1.16 let a punch pressed in the fired car calamity's " + row + " cancel it at once"
                            + " (isCancelable): started " + punch.isActionSuccess + ", buffered " + punch.shouldBuffer
                            + ", the Stand's action " + now + ", the car calamity in " + action.getPhase());
            standAction.tick();
            standAction.tick();
            helper.assertTrue(!stand.isCarEnabled(),
                    "1.16 never switched on a car calamity cancelled in its " + row + ": the car mode is on");
        }

        // As ClAbilityInputPacket handles a press: the condition check, then the press.
        private BufferingState press(Ability ability, InputMethod method, short key) {
            BufferingState state = BufferingState.clickCanBuffer();
            helper.assertTrue(ability != null, "Missing ability");
            power.tick(); // moves are cached per tick
            AbilityConditionCheck container = power.updateAvailableMoves().getContextVariationContainer(ability);
            if (container != null && AbilityInput.withConditionCheck(container, owner, method)) {
                AbilityInput.keyPress(key, container.ability, owner, new FriendlyByteBuf(Unpooled.buffer()), method,
                        0.0F, state, ability.abilityId);
            }
            return state;
        }

        private void close() {
            try {
                AbilityInput.keyRelease(CAR_KEY, owner);
                AbilityInput.keyRelease(PUNCH_KEY, owner);
                standAction.setAction(null, owner, SyncType.NO_SYNC);
                stand.setCarEnabled(false);
                if (power.isSummoned()) {
                    WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(owner, power);
                }
            }
            finally {
                owner.discard();
            }
        }
    }

    private static ServerPlayer makeServerPlayer(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "wou-car-gives-way");
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
