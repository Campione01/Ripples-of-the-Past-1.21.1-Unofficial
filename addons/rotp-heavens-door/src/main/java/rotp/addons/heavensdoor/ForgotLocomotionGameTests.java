package rotp.addons.heavensdoor;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayHandler.onLivingUpdate :372-395 ran at the start of the living tick on both sides: a forgotten
 * sprint is dropped before the tick moves the player, and the speed it moves with is lowered (walking 0.1 to
 * 0.08, swimming 1.0 to 0.5 while sprinting and 0.9 otherwise, in water only).
 */
@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgotLocomotionGameTests {
    private static final double EPSILON = 1.0E-6D;

    private ForgotLocomotionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void forgotRunDropsTheSprintAtTickStartAndWalksSlower(GameTestHelper helper) {
        Probe player = player(helper, "HdForgotRun");
        try {
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(speed != null, "Player fixture has no movement speed");
            double walk = speed.getValue();

            player.setSprinting(true);
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(player.isSprinting() && near(speed.getValue(), walk * 1.3D),
                    "Without the effect the tick start changed the sprint: sprinting=" + player.isSprinting()
                            + " speed=" + speed.getValue());

            player.addEffect(new MobEffectInstance(HeavensDoorAddon.FORGOT_RUN, 200));
            helper.assertTrue(player.hasEffect(HeavensDoorAddon.FORGOT_RUN), "Fixture effect was not applied");
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(!player.isSprinting(),
                    "Forgot Running left the player sprinting at the start of the tick (1.16 dropped it there)");
            helper.assertTrue(near(speed.getValue(), walk * 0.8D),
                    "Forgot Running must lower the walking speed to 0.08 while the sprint is attempted, got "
                            + speed.getValue() + " (walk " + walk + ")");

            // The owning client's aiStep sprints again later in the same tick while the key is held.
            player.setSprinting(true);
            helper.assertTrue(near(speed.getValue(), walk * 0.8D * 1.3D),
                    "A re-attempted sprint must move at 0.104, got " + speed.getValue());
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(!player.isSprinting() && near(speed.getValue(), walk * 0.8D),
                    "The held sprint was not dropped again: sprinting=" + player.isSprinting()
                            + " speed=" + speed.getValue());

            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(near(speed.getValue(), walk),
                    "Walking without a sprint attempt must return to " + walk + ", got " + speed.getValue());

            player.setSprinting(true);
            EventHooks.fireEntityTickPre(player);
            player.removeEffect(HeavensDoorAddon.FORGOT_RUN);
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(near(speed.getValue(), walk),
                    "The slowdown outlived the effect: " + speed.getValue());
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void forgotSwimActsOnlyInWaterAndSlowsTheSwim(GameTestHelper helper) {
        BlockPos feet = new BlockPos(2, 2, 2);
        Probe player = player(helper, "HdForgotSwim");
        try {
            AttributeInstance swim = player.getAttribute(NeoForgeMod.SWIM_SPEED);
            helper.assertTrue(swim != null, "Player fixture has no swim speed");
            double base = swim.getValue();
            player.addEffect(new MobEffectInstance(HeavensDoorAddon.FORGOT_SWIM, 200));
            helper.assertTrue(player.hasEffect(HeavensDoorAddon.FORGOT_SWIM), "Fixture effect was not applied");

            player.setSprinting(true);
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(player.isSprinting() && near(swim.getValue(), base),
                    "Forgot Swimming acted on land: sprinting=" + player.isSprinting()
                            + " swim speed=" + swim.getValue());

            helper.setBlock(feet, Blocks.WATER);
            helper.setBlock(feet.above(), Blocks.WATER);
            player.refreshWaterState();
            helper.assertTrue(player.isInWater(), "Fixture player is not in the water column");

            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(!player.isSprinting(),
                    "Forgot Swimming left the player sprinting in water at the start of the tick");
            helper.assertTrue(near(swim.getValue(), base * 0.5D),
                    "A sprint attempt in water must halve the swim speed, got " + swim.getValue());

            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(near(swim.getValue(), base * 0.9D),
                    "Swimming without a sprint attempt must move at 0.9, got " + swim.getValue());

            player.removeEffect(HeavensDoorAddon.FORGOT_SWIM);
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(near(swim.getValue(), base),
                    "The swim slowdown outlived the effect: " + swim.getValue());
            helper.succeed();
        }
        finally {
            helper.setBlock(feet.above(), Blocks.AIR);
            helper.setBlock(feet, Blocks.AIR);
            close(player);
        }
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void forgottenFactorsComposeWithOtherModsAndLeaveTheirModifiersAlone(GameTestHelper helper) {
        Probe player = player(helper, "HdSpeedComposition");
        BlockPos feet = new BlockPos(2, 2, 2);
        try {
            AttributeInstance walk = player.getAttribute(Attributes.MOVEMENT_SPEED);
            AttributeInstance swim = player.getAttribute(NeoForgeMod.SWIM_SPEED);
            helper.assertTrue(walk != null && swim != null, "Fixture speed attributes missing");
            for (AttributeInstance attribute : new AttributeInstance[]{walk, swim}) {
                attribute.addTransientModifier(new AttributeModifier(ResourceLocation.parse("test:flat"), 0.25D,
                        AttributeModifier.Operation.ADD_VALUE));
                attribute.addTransientModifier(new AttributeModifier(ResourceLocation.parse("test:base"), 1.0D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                attribute.addTransientModifier(new AttributeModifier(ResourceLocation.parse("test:total"), 0.5D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            double walkValue = walk.getValue();
            double swimValue = swim.getValue();
            player.addEffect(new MobEffectInstance(HeavensDoorAddon.FORGOT_RUN, 200));
            player.setSprinting(true);
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(!player.isSprinting() && near(walk.getValue(), walkValue * 0.8D),
                    "Foreign flat/base/total bonuses diluted forgotten running");
            player.setSprinting(true);
            helper.assertTrue(near(walk.getValue(), walkValue * 1.04D),
                    "Forgotten sprint did not stay 4% above the fully modified walk");
            player.setSprinting(false);
            player.removeEffect(HeavensDoorAddon.FORGOT_RUN);
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(near(walk.getValue(), walkValue), "Running left a modifier behind");
            player.addEffect(new MobEffectInstance(HeavensDoorAddon.FORGOT_SWIM, 200));
            helper.setBlock(feet, Blocks.WATER);
            helper.setBlock(feet.above(), Blocks.WATER);
            player.refreshWaterState();
            player.setSprinting(true);
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(near(swim.getValue(), swimValue * 0.5D), "Foreign bonuses diluted sprint-swim factor");
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(near(swim.getValue(), swimValue * 0.9D), "Foreign bonuses diluted ordinary-swim factor");
            player.removeEffect(HeavensDoorAddon.FORGOT_SWIM);
            EventHooks.fireEntityTickPre(player);
            helper.assertTrue(near(swim.getValue(), swimValue), "Swimming left a modifier behind");
            for (AttributeInstance attribute : new AttributeInstance[]{walk, swim}) {
                helper.assertTrue(attribute.getModifier(ResourceLocation.parse("test:flat")) != null
                                && attribute.getModifier(ResourceLocation.parse("test:base")) != null
                                && attribute.getModifier(ResourceLocation.parse("test:total")) != null,
                        "Forgotten cleanup removed another mod's modifier");
            }
            helper.succeed();
        }
        finally {
            helper.setBlock(feet.above(), Blocks.AIR);
            helper.setBlock(feet, Blocks.AIR);
            close(player);
        }
    }

    private static Probe player(GameTestHelper helper, String name) {
        Probe player = new Probe(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static void close(FakePlayer player) {
        EventHooks.firePlayerLoggedOut(player);
        player.discard();
    }

    // A fake player never runs baseTick, which is where an entity learns that it stands in water.
    private static final class Probe extends FakePlayer {
        private Probe(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        private void refreshWaterState() {
            updateInWaterStateAndDoFluidPushing();
        }
    }
}
