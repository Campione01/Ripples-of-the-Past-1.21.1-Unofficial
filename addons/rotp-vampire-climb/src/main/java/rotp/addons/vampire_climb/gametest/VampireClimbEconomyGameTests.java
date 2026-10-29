package rotp.addons.vampire_climb.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.vampire_climb.VampireClimbAddon;
import rotp.addons.vampire_climb.ability.VampireWallClimbAbility;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;

@GameTestHolder(VampireClimbAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VampireClimbEconomyGameTests {
    private VampireClimbEconomyGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void creativeZeroBloodStartsRegisteredHold(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 0.0F)) {
            helper.assertTrue(fixture.press(), "Creative zero blood rejected the registered HOLD");
            fixture.assertBlood(0.0F, "Creative admission changed blood");
            fixture.tick();
            fixture.assertClimbing();
            fixture.assertBlood(0.0F, "Creative zero-blood tick changed blood");
            fixture.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void creativeFiftyBloodIsNotConsumed(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 50.0F)) {
            helper.assertTrue(fixture.press(), "Creative positive-blood HOLD did not start");
            fixture.assertBlood(50.0F, "Creative admission charged blood");
            for (int tick = 0; tick < 4; tick++) {
                fixture.tick();
                fixture.assertClimbing();
                fixture.assertBlood(50.0F, "Creative held tick " + tick + " charged blood");
            }
            fixture.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void survivalFiveBloodPaysOnceThenStops(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.SURVIVAL, 5.0F)) {
            helper.assertTrue(fixture.press(), "Survival exact-cost HOLD did not start");
            fixture.assertBlood(5.0F, "Survival admission charged an upfront cost");
            fixture.tick();
            fixture.assertClimbing();
            fixture.assertBlood(0.0F, "Survival tick did not pay exactly five blood");
            fixture.tick();
            fixture.assertStopped("Survival exhausted HOLD continued");
            fixture.assertBlood(0.0F, "Exhaustion tick changed blood");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void survivalInsufficientBloodRejectsRegisteredHold(GameTestHelper helper) {
        for (float blood : new float[] { 0.0F, 4.0F }) {
            try (Fixture fixture = new Fixture(helper, GameType.SURVIVAL, blood)) {
                helper.assertTrue(!fixture.press(), "Survival insufficient-blood HOLD was admitted: " + blood);
                fixture.tick();
                fixture.assertStopped("Rejected Survival HOLD moved the player");
                fixture.assertBlood(blood, "Rejected Survival HOLD changed blood");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void creativeExemptionKeepsHandAndWallGates(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 0.0F)) {
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            helper.assertTrue(!fixture.press(), "Creative occupied main hand bypassed its gate");
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fixture.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
            helper.assertTrue(fixture.press(), "Occupied offhand incorrectly prevented climbing");
            fixture.tick();
            fixture.assertClimbing();
            fixture.assertBlood(0.0F, "Creative offhand control charged blood");
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            fixture.tick();
            fixture.assertStopped("Main hand occupied during HOLD did not stop it");
            fixture.release();
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fixture.removeWall();
            helper.assertTrue(!fixture.press(), "Creative zero-blood exemption bypassed wall contact");
            fixture.assertBlood(0.0F, "Rejected hand/wall controls changed blood");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fullPowerTypeAndReleaseGatesRemainAuthoritative(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, GameType.CREATIVE, 50.0F)) {
            fixture.data.setVampireFullPower(false, fixture.player);
            helper.assertTrue(!fixture.press(), "Creative bypassed the full-power unlock");
            fixture.data.setVampireFullPower(true, fixture.player);
            helper.assertTrue(fixture.press(), "Restored full-power HOLD did not start");
            fixture.tick();
            fixture.release();
            fixture.assertStopped("Released HOLD continued moving");
            helper.assertTrue(fixture.press(), "HOLD could not restart after release");
            fixture.power.setPowerType(null);
            fixture.tick();
            fixture.assertStopped("Removed Vampirism left a running HOLD");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private static final short KEY = 7;
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final EmbeddedChannel channel;
        private final BlockPos wall;
        private final PlayerPower power;
        private final VampirismData data;
        private final Ability ability;
        private HeldInputEntry held;

        private Fixture(GameTestHelper helper, GameType mode, float blood) {
            this.helper = helper;
            UUID uuid = UUID.randomUUID();
            GameProfile profile = new GameProfile(uuid, "VClimb" + uuid.toString().substring(0, 8));
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(mode);
            player.setNoGravity(true);
            wall = helper.absolutePos(new BlockPos(3, 2, 2));
            helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
            player.setPos(wall.getX() - player.getBbWidth() / 2.0D, wall.getY(), wall.getZ() + 0.5D);
            player.setYRot(-90.0F);
            player.setXRot(-45.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            power = PowerClass.PLAYER_POWER.attachGet(player);
            power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
            data = PlayerPower.getPowerData(player, ModPlayerPowers.VAMPIRISM).orElseThrow();
            data.setVampireFullPower(true, player);
            VampirismState.get(player).blood().setCurrent(blood);
            data.setBloodLevel(blood);
            ability = power.getAbility("vampirism_wall_climb");
            helper.assertTrue(ability instanceof VampireWallClimbAbility
                            && ability.abilityType == VampireClimbAddon.WALL_CLIMB.get(),
                    "Fixture did not resolve the registered Vampire Climb moveset ability");
        }

        private boolean press() {
            // Re-evaluate each synchronous control as a fresh input observation, without passive blood ticks.
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            if (!AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), player, InputMethod.HOLD)) {
                return false;
            }
            held = AbilityInput.keyPress(KEY, ability, player, null, InputMethod.HOLD,
                    0.0F, BufferingState.clickOnly(), ability.getAbilityId());
            return held != null && held.action == LivingComponentAction.getCurEntityAction(player);
        }

        private void tick() {
            player.setDeltaMovement(Vec3.ZERO);
            // Tick the real action component without unrelated passive blood drain or player travel.
            LivingComponentAction.getComponent(player).tick();
        }

        private void assertClimbing() {
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(player);
            helper.assertTrue(action != null && !action.isOver() && action.ability == ability,
                    "Registered climb action did not remain active");
            helper.assertTrue(player.getDeltaMovement().distanceTo(player.getLookAngle().scale(0.25D)) < 1.0E-8D,
                    "Paid/exempt held tick did not apply the production climb velocity");
        }

        private void assertStopped(String message) {
            helper.assertTrue(LivingComponentAction.getCurEntityAction(player) == null
                            && player.getDeltaMovement().equals(Vec3.ZERO), message);
        }

        private void assertBlood(float expected, String message) {
            float current = VampirismState.get(player).blood().current();
            helper.assertTrue(Float.compare(current, expected) == 0,
                    message + ": expected " + expected + ", got " + current);
            helper.assertTrue(Float.compare(data.getBloodLevel(), expected) == 0,
                    "VampirismData blood view disagrees with the authoritative resource");
        }

        private void release() {
            if (held != null) {
                helper.assertTrue(AbilityInput.keyReleaseFromNetwork(KEY, player, held.generation)
                                == AbilityInput.ReleaseResult.RELEASED,
                        "Production input release was rejected");
                held = null;
            }
            tick();
            helper.assertTrue(player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.isEmpty(),
                    "Production input release retained the held key");
        }

        private void removeWall() {
            helper.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
        }

        @Override
        public void close() {
            AbilityInput.keyRelease(KEY, player);
            player.connection.disconnect(Component.literal("Vampire Climb economy test complete"));
            channel.finishAndReleaseAll();
        }
    }
}
