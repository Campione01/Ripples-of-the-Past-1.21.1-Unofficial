package rotp.addons.wonderofu.gametest;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

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
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.ability.CalamityActiveAbility;
import rotp.addons.wonderofu.ability.CalamityPassiveAbility;
import rotp.addons.wonderofu.ability.CarCalamityAbility;
import rotp.addons.wonderofu.ability.ExplosionAbility;
import rotp.addons.wonderofu.ability.LightningStrikeAbility;
import rotp.addons.wonderofu.entity.WonderOfUStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 Wonder of U actions had no stamina condition: StandEntityTask performed them and then paid their cost with
 * the result ignored, so each one fires on a bar below its cost and drains the bar to 0.
 */
@GameTestHolder("rotp_wou_car")
@PrefixGameTestTemplate(false)
public final class WonderOfUStaminaGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float SHORT_BAR = 10.0F;

    private WonderOfUStaminaGameTests() {}

    @GameTest(template = "empty", batch = "wou_stamina_passive", timeoutTicks = 120)
    public static void calamityPassiveTogglesOnAShortBar(GameTestHelper helper) {
        run(helper, "passive", "calamity_passive", (short) 91,
                CalamityPassiveAbility.CalamityPassiveAction.class, WonderOfUStandEntity::isPassiveEnabled);
    }

    @GameTest(template = "empty", batch = "wou_stamina_active", timeoutTicks = 120)
    public static void calamityActiveTogglesOnAShortBar(GameTestHelper helper) {
        run(helper, "active", "calamity_active", (short) 92,
                CalamityActiveAbility.CalamityActiveAction.class, WonderOfUStandEntity::isActiveEnabled);
    }

    @GameTest(template = "empty", batch = "wou_stamina_car", timeoutTicks = 120)
    public static void carCalamityTogglesOnAShortBar(GameTestHelper helper) {
        run(helper, "car", "calamity_car_attack", (short) 93,
                CarCalamityAbility.CarCalamityAction.class, WonderOfUStandEntity::isCarEnabled);
    }

    @GameTest(template = "empty", batch = "wou_stamina_lightning", timeoutTicks = 120)
    public static void lightningStrikesOnAShortBar(GameTestHelper helper) {
        int[] bolts = new int[1];
        Consumer<EntityJoinLevelEvent> spawn = event -> {
            if (event.getEntity() instanceof LightningBolt && event.getLevel() == helper.getLevel()) {
                bolts[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, spawn);
        run(helper, "lightning", "lightning_strike", (short) 94,
                LightningStrikeAbility.LightningStrikeAction.class, stand -> bolts[0] > 0,
                () -> NeoForge.EVENT_BUS.unregister(spawn));
    }

    // With nothing to aim at the explosion does nothing, but 1.16 still paid its cost.
    @GameTest(template = "empty", batch = "wou_stamina_explosion", timeoutTicks = 120)
    public static void explosionPaysOnAShortBarWithoutTarget(GameTestHelper helper) {
        run(helper, "explosion", "explosion", (short) 95,
                ExplosionAbility.ExplosionAction.class, stand -> true);
    }

    // 1.16 staminaCostTick(10): StandEntityTask paid it on the perform tick right after the start cost, so the
    // 5 left over the start cost is short of it and the bar drops to 0.
    @GameTest(template = "empty", batch = "wou_stamina_active_tick", timeoutTicks = 120)
    public static void calamityActivePaysItsPerformTickCost(GameTestHelper helper) {
        run(helper, "active_tick", "calamity_active", (short) 96,
                CalamityActiveAbility.CalamityActiveAction.class, WonderOfUStandEntity::isActiveEnabled,
                CalamityActiveAbility.STAMINA_COST + 5.0F, () -> {});
    }

    @GameTest(template = "empty", batch = "wou_stamina_car_tick", timeoutTicks = 120)
    public static void carCalamityPaysItsPerformTickCost(GameTestHelper helper) {
        run(helper, "car_tick", "calamity_car_attack", (short) 97,
                CarCalamityAbility.CarCalamityAction.class, WonderOfUStandEntity::isCarEnabled,
                CarCalamityAbility.STAMINA_COST + 5.0F, () -> {});
    }

    // The HUD hands every icon its colour, the unusable tint included (1.16: brightness 0.2, alpha 0.5); a calamity
    // mode that is on only adds its green on top, so a refused toggle still looks refused.
    @GameTest(template = "empty", batch = "wou_mode_icons", timeoutTicks = 120)
    public static void calamityModeIconsKeepTheColourTheyAreGiven(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, () -> {});
        try {
            Ability passive = fixture.power.getAbility("calamity_passive");
            Ability active = fixture.power.getAbility("calamity_active");
            Ability car = fixture.power.getAbility("calamity_car_attack");
            helper.assertTrue(passive instanceof CalamityPassiveAbility && active instanceof CalamityActiveAbility
                    && car instanceof CarCalamityAbility, "The calamity modes are not in the moveset");
            checkModeIcon(helper, "calamity_passive",
                    color -> ((CalamityPassiveAbility) passive).iconColor(fixture.power, color),
                    fixture.stand::setPassiveEnabled);
            checkModeIcon(helper, "calamity_active",
                    color -> ((CalamityActiveAbility) active).iconColor(fixture.power, color),
                    fixture.stand::setActiveEnabled);
            checkModeIcon(helper, "calamity_car_attack",
                    color -> ((CarCalamityAbility) car).iconColor(fixture.power, color),
                    fixture.stand::setCarEnabled);
        }
        finally {
            fixture.close();
        }
        LOGGER.info("R770_WOU_ICON_PASS mode_icons unusableOn={}",
                Integer.toHexString(UNUSABLE_MODE_ON_ICON));
        helper.succeed();
    }

    private static final int USABLE_ICON = 0xFFFFFFFF;
    private static final int UNUSABLE_ICON = 0x80333333;
    private static final int USABLE_MODE_ON_ICON = 0xFF55FF55;
    private static final int UNUSABLE_MODE_ON_ICON = 0x80113311;

    private static void checkModeIcon(GameTestHelper helper, String abilityName, IntUnaryOperator iconColor,
            Consumer<Boolean> setMode) {
        setMode.accept(false);
        helper.assertTrue(iconColor.applyAsInt(USABLE_ICON) == USABLE_ICON
                        && iconColor.applyAsInt(UNUSABLE_ICON) == UNUSABLE_ICON,
                abilityName + " changed its icon colour while its mode is off");
        setMode.accept(true);
        int usable = iconColor.applyAsInt(USABLE_ICON);
        int unusable = iconColor.applyAsInt(UNUSABLE_ICON);
        setMode.accept(false);
        helper.assertTrue(usable == USABLE_MODE_ON_ICON, abilityName + " with its mode on: usable icon "
                + Integer.toHexString(usable) + ", expected " + Integer.toHexString(USABLE_MODE_ON_ICON));
        helper.assertTrue(unusable == UNUSABLE_MODE_ON_ICON, abilityName + " with its mode on dropped the unusable"
                + " tint: icon " + Integer.toHexString(unusable) + ", expected "
                + Integer.toHexString(UNUSABLE_MODE_ON_ICON));
    }

    private static void run(GameTestHelper helper, String name, String abilityName, short key,
            Class<? extends EntityActionInstance> actionClass, Predicate<WonderOfUStandEntity> performed) {
        run(helper, name, abilityName, key, actionClass, performed, () -> {});
    }

    private static void run(GameTestHelper helper, String name, String abilityName, short key,
            Class<? extends EntityActionInstance> actionClass, Predicate<WonderOfUStandEntity> performed,
            Runnable onClose) {
        run(helper, name, abilityName, key, actionClass, performed, SHORT_BAR, onClose);
    }

    private static void run(GameTestHelper helper, String name, String abilityName, short key,
            Class<? extends EntityActionInstance> actionClass, Predicate<WonderOfUStandEntity> performed,
            float bar, Runnable onClose) {
        Fixture fixture = new Fixture(helper, onClose);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(fixture.stand.blockPosition()),
                        "Wonder of U's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        fixture.press(abilityName, key, actionClass, performed, bar);
                    }
                    catch (RuntimeException | Error failure) {
                        fixture.close();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.staminaAtEnd >= 0.0F,
                        abilityName + " has not performed yet."))
                .thenExecute(() -> {
                    boolean effect = fixture.effectAtEnd;
                    float stamina = fixture.staminaAtEnd;
                    fixture.close();
                    helper.assertTrue(effect, abilityName + " did not take effect on a "
                            + bar + " bar.");
                    helper.assertTrue(stamina == 0.0F, abilityName
                            + "'s cost must drain a " + bar + " bar to 0, left " + stamina);
                    LOGGER.info("R769_WOU_STAMINA_PASS {} stamina={}", name, stamina);
                })
                .thenSucceed();
    }

    // A GameTest listener, so a failed or timed-out test still unregisters its event listeners.
    private static final class Fixture implements GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Runnable onClose;
        private final ServerPlayer owner;
        private final StandPower power;
        private final WonderOfUStandEntity stand;
        private EntityActionInstance action;
        private Consumer<EntityTickEvent.Post> standTick;
        private boolean effectAtEnd;
        private float staminaAtEnd = -1.0F;
        private short key;
        private boolean closed;

        private Fixture(GameTestHelper helper, Runnable onClose) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.onClose = onClose;
            helper.testInfo.addListener(this);
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            BlockPos ownerPos = new BlockPos(chunk.getMinBlockX() + 8, template.getY() + 8, chunk.getMinBlockZ() + 8);
            owner = makeServerPlayer(level, ownerPos);
            owner.setNoGravity(true);
            owner.setGameMode(GameType.SURVIVAL);
            // Straight up: the lightning lands at the end of its range and the explosion finds no target.
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, owner.getEyePosition().add(0.0D, 24.0D, 0.0D));
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
            helper.assertTrue(!power.isStaminaInfinite(), "Fixture: stamina is infinite");
        }

        private void press(String abilityName, short key, Class<? extends EntityActionInstance> actionClass,
                Predicate<WonderOfUStandEntity> performed, float bar) {
            this.key = key;
            Ability ability = power.getAbility(abilityName);
            helper.assertTrue(ability != null, abilityName + " is not in the moveset");
            power.setStamina(bar);
            AbilityConditionCheck input = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(input != null && input.ability == ability
                            && AbilityInput.withConditionCheck(input, owner, InputMethod.HOLD),
                    "1.16 had no stamina condition: " + abilityName + " must be admitted on a "
                            + bar + " bar");
            AbilityInput.keyPress(key, ability, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.HOLD,
                    0.0F, BufferingState.clickCanBuffer(), ability.abilityId);
            action = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(actionClass.isInstance(action), "The press did not start " + abilityName);
            standTick = event -> {
                if (event.getEntity() == stand && staminaAtEnd < 0.0F && action.isOver()) {
                    staminaAtEnd = power.getStamina();
                    effectAtEnd = performed.test(stand);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (standTick != null) {
                    NeoForge.EVENT_BUS.unregister(standTick);
                }
                onClose.run();
                if (owner == null) {
                    return;
                }
                AbilityInput.keyRelease(key, owner);
                if (stand != null) {
                    stand.setPassiveEnabled(false);
                    stand.setActiveEnabled(false);
                    stand.setCarEnabled(false);
                }
                if (power != null && power.isSummoned()) {
                    WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(owner, power);
                }
            }
            finally {
                if (owner != null) {
                    owner.discard();
                }
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
        GameProfile profile = new GameProfile(UUID.randomUUID(), "wou-stamina");
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
