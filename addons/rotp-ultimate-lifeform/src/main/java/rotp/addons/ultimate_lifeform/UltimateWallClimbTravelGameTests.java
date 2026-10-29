package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.api.item.LivingHandUseBlockers;
import rotp.core.init.ModItems;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * 1.16 PlayerEntityMixin cancels PlayerEntity.travel while the wall climb moves the
 * player. These tests drive the real Player.travel entry point of a server player.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateWallClimbTravelGameTests {
    private static final int STEPS = 5;
    private static final double EPS = 1.0E-6D;
    private static final double WALL_X = 4.0D;
    private static final int WALL_MIN_Z = 1;
    private static final int WALL_MAX_Z = 5;
    private static final int WALL_MIN_Y = 1;
    private static final int WALL_MAX_Y = 9;

    private UltimateWallClimbTravelGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void backwardInputKeepsWallContactAndDescendsAtClimbSpeed(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallDown",
                "5b1e7a40-3c2d-4f68-9a71-d0e4c8a2b613");
        try {
            Vec3 start = climber.player.position();
            double speed = climber.climbSpeed();
            climber.data.acceptWallInput(climber.player, 0.0F, -1.0F, false);
            for (int step = 0; step < STEPS; step++) {
                climber.player.travel(new Vec3(0.0D, 0.0D, -1.0D));
            }
            Vec3 end = climber.player.position();
            helper.assertTrue(
                    climber.data.isWallClimbing(),
                    "Backward climb input dropped the wall climb.");
            helper.assertTrue(
                    Math.abs(end.x - start.x) < EPS
                            && Math.abs(end.z - start.z) < EPS,
                    "Backward input left the wall contact: dx="
                            + (end.x - start.x) + ", dz="
                            + (end.z - start.z));
            helper.assertTrue(
                    Math.abs((start.y - end.y) - STEPS * speed) < EPS,
                    "Backward input did not descend at the climb speed: dy="
                            + (end.y - start.y) + ", expected="
                            + (-STEPS * speed));
            helper.assertTrue(
                    Math.abs(climber.player.getDeltaMovement().y + speed)
                            < EPS,
                    "Climb velocity was not the climb speed: "
                            + climber.player.getDeltaMovement());
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sidewaysInputKeepsHeightAndSlidesAlongTheWall(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallSide",
                "7c3a9d52-5e4f-4a8b-8c93-f2a6e0c4d835");
        try {
            double speed = climber.climbSpeed();
            for (float strafe : new float[] {1.0F, -1.0F}) {
                Vec3 start = climber.player.position();
                climber.data.acceptWallInput(
                        climber.player, strafe, 0.0F, false);
                for (int step = 0; step < STEPS; step++) {
                    climber.player.travel(
                            new Vec3(strafe, 0.0D, 0.0D));
                }
                Vec3 end = climber.player.position();
                helper.assertTrue(
                        climber.data.isWallClimbing(),
                        "Sideways climb input dropped the wall climb.");
                helper.assertTrue(
                        Math.abs(end.y - start.y) < EPS,
                        "Sideways input changed the height (gravity): dy="
                                + (end.y - start.y));
                helper.assertTrue(
                        Math.abs(end.x - start.x) < EPS,
                        "Sideways input left the wall contact: dx="
                                + (end.x - start.x));
                helper.assertTrue(
                        Math.abs(Math.abs(end.z - start.z) - STEPS * speed)
                                < EPS,
                        "Sideways input did not slide at the climb speed: dz="
                                + (end.z - start.z) + ", expected="
                                + (STEPS * speed));
            }
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vanillaTravelStillRunsWithoutAnActiveClimb(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallIdle",
                "9d4b1f63-7a50-4b9c-a0b4-13c7f1d5e947");
        try {
            climber.data.stopWallClimbing(climber.player);
            helper.assertTrue(
                    !climber.data.isWallClimbing(),
                    "Wall climb did not stop for the vanilla travel check.");
            Vec3 start = climber.player.position();
            climber.player.setDeltaMovement(0.0D, -0.5D, 0.0D);
            climber.player.travel(Vec3.ZERO);
            helper.assertTrue(
                    Math.abs((start.y - climber.player.getY()) - 0.5D) < EPS,
                    "Vanilla travel did not move the idle player: dy="
                            + (climber.player.getY() - start.y));
            helper.assertTrue(
                    Math.abs(climber.player.getDeltaMovement().y
                                    - (-0.5D - 0.08D) * 0.98D) < EPS,
                    "Vanilla gravity did not run for the idle player: "
                            + climber.player.getDeltaMovement());
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lostGripStopsTheClimbAndVanillaTravelTakesOver(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallGrip",
                "ae5c2074-8b61-4cad-b1c5-24d8a2e6fa58");
        try {
            for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
                for (int z = WALL_MIN_Z; z <= WALL_MAX_Z; z++) {
                    helper.getLevel().setBlockAndUpdate(
                            helper.absolutePos(
                                    new BlockPos((int) WALL_X, y, z)),
                            Blocks.AIR.defaultBlockState());
                }
            }
            Vec3 start = climber.player.position();
            climber.data.acceptWallInput(climber.player, 0.0F, -1.0F, false);
            climber.player.setDeltaMovement(0.0D, -0.5D, 0.0D);
            climber.player.travel(new Vec3(0.0D, 0.0D, -1.0D));
            helper.assertTrue(
                    !climber.data.isWallClimbing(),
                    "Climb survived losing every wall contact.");
            helper.assertTrue(
                    Math.abs((start.y - climber.player.getY()) - 0.5D) < EPS,
                    "Vanilla travel did not take over after the grip was lost: dy="
                            + (climber.player.getY() - start.y));
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 travelWallClimb: MCUtil.itemHandFree lets open-finger gloves stay
    // on the wall; any other item in either hand ends the climb.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void openFingerGlovesKeepTheClimbAndAHeldItemEndsIt(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallGloves",
                "b3d71e95-2c48-4f0a-9e16-5a8c7d3f1b24");
        try {
            climber.player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.GLOVES.get()));
            climber.player.setItemInHand(InteractionHand.OFF_HAND,
                    new ItemStack(ModItems.GLOVES.get()));
            climber.data.acceptWallInput(climber.player, 0.0F, 1.0F, false);
            climber.player.travel(new Vec3(0.0D, 0.0D, 1.0D));
            helper.assertTrue(
                    climber.data.isWallClimbing(),
                    "Open-finger gloves in both hands ended the wall climb.");
            climber.player.setItemInHand(InteractionHand.OFF_HAND,
                    new ItemStack(Items.STONE));
            climber.player.travel(new Vec3(0.0D, 0.0D, 1.0D));
            helper.assertTrue(
                    !climber.data.isWallClimbing(),
                    "An item in the off hand did not end the wall climb.");
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 UltimateLifeformWallClimb.checkHeldItems refuses the start with
    // the "hands" message unless both hands are free.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wallClimbStartIsRefusedWithAnItemInHand(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallHands",
                "c4e82fa6-3d59-4b1b-8f27-6b9d8e4a2c35");
        try {
            climber.data.stopWallClimbing(climber.player);
            Ability ability = climber.power.getAbility(
                    UltimateLifeformSpec.WALL_CLIMBING.id);
            helper.assertTrue(ability != null,
                    "The Ultimate Lifeform moveset has no wall climbing.");
            climber.player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.GLOVES.get()));
            ConditionCheck gloves = ability.checkConditions(climber.power);
            helper.assertTrue(
                    !isHandsWarning(gloves),
                    "Open-finger gloves were refused as a busy hand.");
            climber.player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(Items.STONE));
            ConditionCheck stone = ability.checkConditions(climber.power);
            helper.assertTrue(
                    !stone.isPositive() && isHandsWarning(stone),
                    "A stone in hand did not refuse the wall climb with the"
                            + " hands message: "
                            + (stone.isPositive() ? "admitted"
                                    : String.valueOf(stone.getWarning())));
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 travelWallClimb: moving down while on the ground ends the climb.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void descendingOnTheGroundEndsTheClimb(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallGround",
                "d5f93ab7-4e6a-4c2c-9038-7cae9f5b3d46");
        try {
            climber.player.setOnGround(true);
            climber.data.acceptWallInput(climber.player, 0.0F, -1.0F, false);
            climber.player.travel(new Vec3(0.0D, 0.0D, -1.0D));
            helper.assertTrue(
                    !climber.data.isWallClimbing(),
                    "Descending on the ground kept the wall climb.");
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 travelWallClimb: the jump pulls the climber up by its height,
    // with 0.1 of motion into the wall, once no wall is within half a
    // body above its head.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void jumpWithTheLedgeBelowTheHeadPullsUp(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallLedge",
                "e6a04bc8-5f7b-4d3d-a149-8dbf0a6c4e57");
        try {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            double feet = origin.getY() + WALL_MAX_Y + 1.0D
                    - climber.player.getBbHeight() + 0.3D;
            climber.player.setPos(
                    climber.player.getX(), feet, climber.player.getZ());
            double height = climber.player.getBbHeight();
            climber.data.acceptWallInput(climber.player, 0.0F, 0.0F, true);
            climber.player.travel(Vec3.ZERO);
            helper.assertTrue(
                    !climber.data.isWallClimbing(),
                    "The jump did not end the wall climb.");
            helper.assertTrue(
                    Math.abs(climber.player.getY() - (feet + height)) < EPS,
                    "The jump below the ledge did not pull up by the body"
                            + " height: dy=" + (climber.player.getY() - feet));
            // travelWallClimb returns false, so vanilla travel runs on in
            // the same call: one airborne step (x 0.91, gravity) of the 0.1.
            Vec3 motion = climber.player.getDeltaMovement();
            helper.assertTrue(
                    Math.abs(motion.x - 0.1D * 0.91D) < EPS
                            && Math.abs(motion.y + 0.08D * 0.98D) < EPS
                            && Math.abs(motion.z) < EPS,
                    "The pull-up motion is not 0.1 into the wall: "
                            + motion);
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 travelWallClimb: Space below the ledge only lets go (no hop), and
    // vanilla travel runs on from the climber's own motion in the same call.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void jumpBelowTheLedgeOnlyLetsGo(GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallLetGo",
                "f7b15cd9-6a1e-4e4f-b25a-9ec1b7d5f068");
        try {
            Vec3 start = climber.player.position();
            climber.data.acceptWallInput(climber.player, 0.0F, 0.0F, true);
            climber.player.travel(Vec3.ZERO);
            Vec3 motion = climber.player.getDeltaMovement();
            Vec3 end = climber.player.position();
            helper.assertTrue(
                    !climber.data.isWallClimbing(),
                    "Space below the ledge did not end the wall climb.");
            helper.assertTrue(
                    end.distanceTo(start) < EPS
                            && Math.abs(motion.x) < EPS
                            && Math.abs(motion.z) < EPS
                            && Math.abs(motion.y + 0.08D * 0.98D) < EPS,
                    "Space below the ledge did more than let go: moved by "
                            + end.subtract(start) + ", motion " + motion);
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 UltimateLifeformWallClimb.perform: pressing the ability again on
    // the same wall does nothing; on another wall the climber moves there.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void secondPressStaysOnTheSameWallAndMovesToAnotherWall(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallPress",
                "08c26de1-7b2f-4f50-836b-afd2c8e6a179");
        try {
            look(climber.player, -90.0F);
            Vec3 start = climber.player.position();
            UltimateLifeformCombat.perform(climber.player, climber.data,
                    UltimateLifeformSpec.WALL_CLIMBING, 0);
            helper.assertTrue(
                    climber.data.isWallClimbing()
                            && climber.data.getWallFace() == Direction.WEST
                            && climber.player.position().distanceTo(start)
                                    < EPS,
                    "A second press on the same wall changed the climb: climbing="
                            + climber.data.isWallClimbing() + " face="
                            + climber.data.getWallFace() + " moved by "
                            + climber.player.position().subtract(start));
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            for (int y = 4; y <= 8; y++) {
                helper.getLevel().setBlockAndUpdate(
                        helper.absolutePos(new BlockPos(3, y, 2)),
                        Blocks.STONE.defaultBlockState());
            }
            climber.player.setPos(climber.player.getX(),
                    climber.player.getY(), origin.getZ() + 3.35D);
            look(climber.player, 180.0F);
            UltimateLifeformCombat.perform(climber.player, climber.data,
                    UltimateLifeformSpec.WALL_CLIMBING, 0);
            helper.assertTrue(
                    climber.data.isWallClimbing()
                            && climber.data.getWallFace() == Direction.SOUTH,
                    "A press on another wall did not move the climb there:"
                            + " climbing=" + climber.data.isWallClimbing()
                            + " face=" + climber.data.getWallFace());
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 UltimateLifeformWallClimb.tickWallClimbing is empty: the climb
    // costs no energy and lasts as long as the grip does.
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wallClimbCostsNoEnergyAndHasNoTimeLimit(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallEnergy",
                "19d37ef2-8c3a-4061-947c-b0e3d9f7a28a");
        try {
            // NonStandAction.getEnergyNeeded adds the held tick cost only to
            // hold actions: the press needs no energy
            climber.data.setCommandEnergy(climber.player, 0.0F);
            look(climber.player, -90.0F);
            Ability ability = climber.power.getAbility(
                    UltimateLifeformSpec.WALL_CLIMBING.id);
            helper.assertTrue(ability != null,
                    "The Ultimate Lifeform moveset has no wall climbing.");
            ConditionCheck press = ability.checkConditions(climber.power);
            helper.assertTrue(
                    press.getWarning() == null
                            || !(press.getWarning().getContents()
                                    instanceof TranslatableContents contents
                                    && contents.getKey().endsWith(
                                            ".no_energy_ultimate_lifeform")),
                    "A wall climb press without energy was refused: "
                            + press.getWarning());
            climber.data.setCommandEnergy(climber.player, 1000.0F);
            float before = climber.data.getEnergy();
            climber.data.tick(climber.power);
            helper.assertTrue(
                    climber.data.isWallClimbing()
                            && climber.data.getEnergy() >= before,
                    "Holding still on the wall cost energy: " + before
                            + " -> " + climber.data.getEnergy());
            climber.data.setCommandEnergy(climber.player, 0.0F);
            for (int tick = 0; tick < 40; tick++) {
                climber.data.tick(climber.power);
            }
            helper.assertTrue(
                    climber.data.isWallClimbing(),
                    "The climb ended without energy after "
                            + "a few ticks; 1.16 has no limit.");
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 LivingWallClimbing.climbLimitPlayerHeadRot turns the body to the
    // negated climb rotation, toward the wall, and keeps the head within 75
    // degrees of it; perform shows the Space hint on the action bar.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wallClimbStartFacesTheWallAndShowsTheJumpHint(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallFacing",
                "2ae48f03-9d4b-4172-a58d-c1f4eaa8b39b");
        try {
            helper.assertTrue(
                    Math.abs(Mth.wrapDegrees(climber.player.yBodyRot + 90.0F))
                            < EPS
                            && Math.abs(Mth.wrapDegrees(
                                    climber.player.getYRot() + 15.0F)) < EPS
                            && Math.abs(Mth.wrapDegrees(
                                    climber.player.getYHeadRot() + 15.0F))
                                    < EPS,
                    "The climber on a west face does not face the wall (east,"
                            + " -90) with the head 75 degrees from it: body="
                            + climber.player.yBodyRot + " yaw="
                            + climber.player.getYRot() + " head="
                            + climber.player.getYHeadRot());
            helper.assertTrue(
                    climber.actionBar.stream().anyMatch(message ->
                            message.getContents()
                                    instanceof TranslatableContents contents
                                    && contents.getKey().equals(
                                            "jojo.message.wall_climb.hint_jump")),
                    "The wall climb start showed no Space hint: "
                            + climber.actionBar);
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 MCUtil.isHandFree: the other abilities that need a free hand also
    // take open-finger gloves as a free hand.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void openFingerGlovesCountAsFreeHandsForOtherAbilities(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallAbilityGloves",
                "3bf59a14-ae5c-4283-b6f5-d2a5fbb9c4ac");
        try {
            climber.data.stopWallClimbing(climber.player);
            // 1.16 asked for the Hamon charge before the hands
            climber.data.addHamonCharge(UltimateLifeformData.MAX_HAMON_CHARGE);
            // the glide check of the two-handed abilities passes with an elytra
            climber.player.setItemSlot(EquipmentSlot.CHEST,
                    new ItemStack(Items.ELYTRA));
            // 1.16 Hamon Overdrive took an entity under the crosshair first
            aimAtACow(helper, climber);
            for (UltimateLifeformSpec spec : new UltimateLifeformSpec[] {
                    UltimateLifeformSpec.HAMON_OVERDRIVE,
                    UltimateLifeformSpec.HAMON_SHOCK}) {
                Ability ability = climber.power.getAbility(spec.id);
                helper.assertTrue(ability != null,
                        "The Ultimate Lifeform moveset has no " + spec);
                climber.player.setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.GLOVES.get()));
                climber.player.setItemInHand(InteractionHand.OFF_HAND,
                        new ItemStack(ModItems.GLOVES.get()));
                ConditionCheck gloves = ability.checkConditions(climber.power);
                helper.assertTrue(
                        !isFreeHandWarning(gloves, spec),
                        "Open-finger gloves were refused as busy hands by "
                                + spec + ": " + gloves.getWarning());
                climber.player.setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(Items.STONE));
                ConditionCheck stone = ability.checkConditions(climber.power);
                helper.assertTrue(
                        !stone.isPositive() && isFreeHandWarning(stone, spec),
                        "A stone in hand did not refuse " + spec + ": "
                                + (stone.isPositive() ? "admitted"
                                        : String.valueOf(stone.getWarning())));
            }
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 UltimateLifeformWallClimb.perform calls stopFallFlying
    // (func_226568_ek_) on every press before it grips the wall.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wallClimbPressEndsAGlide(GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallGlide",
                "6d2f8e41-0b7c-4e95-a3d8-57c1f9b2e604");
        try {
            climber.data.stopWallClimbing(climber.player);
            climber.player.setItemSlot(EquipmentSlot.CHEST,
                    new ItemStack(Items.ELYTRA));
            climber.player.startFallFlying();
            helper.assertTrue(climber.player.isFallFlying(),
                    "fixture: the climber does not glide");
            look(climber.player, -90.0F);
            UltimateLifeformCombat.perform(climber.player, climber.data,
                    UltimateLifeformSpec.WALL_CLIMBING, 0);
            helper.assertTrue(
                    !climber.player.isFallFlying(),
                    "A wall climb press did not end the glide.");
            helper.assertTrue(
                    climber.data.isWallClimbing()
                            && climber.data.getWallFace() == Direction.WEST,
                    "The press did not grip the wall: climbing="
                            + climber.data.isWallClimbing() + " face="
                            + climber.data.getWallFace());
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    // 1.16 MCUtil.areHandsFree also refuses a hand that is kept busy (a
    // carried turtle, Sticky Fingers' blockers in the port); open-finger
    // gloves in it do not make it free.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aHandKeptBusyRefusesOtherAbilitiesEvenWithGloves(
            GameTestHelper helper) {
        Climber climber = climber(
                helper, "UltimateWallBlockedHand",
                "4c06ab25-bf6d-4394-87a6-e3b6acc0d5bd");
        try {
            climber.data.stopWallClimbing(climber.player);
            // 1.16 asked for the Hamon charge before the hands
            climber.data.addHamonCharge(UltimateLifeformData.MAX_HAMON_CHARGE);
            climber.player.setItemSlot(EquipmentSlot.CHEST,
                    new ItemStack(Items.ELYTRA));
            climber.player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.GLOVES.get()));
            climber.player.setItemInHand(InteractionHand.OFF_HAND,
                    new ItemStack(ModItems.GLOVES.get()));
            aimAtACow(helper, climber);
            registerTestHandBlocker();
            for (UltimateLifeformSpec spec : new UltimateLifeformSpec[] {
                    UltimateLifeformSpec.HAMON_OVERDRIVE,
                    UltimateLifeformSpec.HAMON_SHOCK}) {
                Ability ability = climber.power.getAbility(spec.id);
                helper.assertTrue(ability != null,
                        "The Ultimate Lifeform moveset has no " + spec);
                InteractionHand blocked =
                        spec == UltimateLifeformSpec.HAMON_SHOCK
                                ? InteractionHand.OFF_HAND
                                : InteractionHand.MAIN_HAND;
                BLOCKED_HANDS.put(climber.player.getUUID(), blocked);
                try {
                    ConditionCheck busy =
                            ability.checkConditions(climber.power);
                    helper.assertTrue(
                            !busy.isPositive() && isFreeHandWarning(busy, spec),
                            "A gloved " + blocked + " kept busy did not refuse "
                                    + spec + ": "
                                    + (busy.isPositive() ? "admitted"
                                            : String.valueOf(busy.getWarning())));
                }
                finally {
                    BLOCKED_HANDS.remove(climber.player.getUUID());
                }
                ConditionCheck free = ability.checkConditions(climber.power);
                helper.assertTrue(
                        !isFreeHandWarning(free, spec),
                        "Gloved hands nothing keeps busy were refused by "
                                + spec + ": " + free.getWarning());
            }
        }
        finally {
            climber.close();
        }
        helper.succeed();
    }

    private static final Map<UUID, InteractionHand> BLOCKED_HANDS =
            new ConcurrentHashMap<>();
    private static boolean testHandBlockerRegistered;

    private static synchronized void registerTestHandBlocker() {
        if (!testHandBlockerRegistered) {
            LivingHandUseBlockers.register(
                    UltimateLifeformAddon.id("gametest_busy_hand"),
                    (entity, hand) ->
                            BLOCKED_HANDS.get(entity.getUUID()) == hand);
            testHandBlockerRegistered = true;
        }
    }

    private static void look(ServerPlayer player, float yRot) {
        player.setYRot(yRot);
        player.setYHeadRot(yRot);
        player.setXRot(0.0F);
        player.yRotO = yRot;
        player.yHeadRotO = yRot;
        player.xRotO = 0.0F;
        // the client's crosshair block (4.5 blocks), which 1.16 climbed
        Vec3 eye = player.getEyePosition();
        net.minecraft.world.phys.BlockHitResult hit = player.level().clip(
                new net.minecraft.world.level.ClipContext(
                        eye,
                        eye.add(player.getLookAngle().scale(4.5D)),
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        player));
        rotp.core.powersystem.entityaction.LivingComponentAction
                .getComponent(player).entityAim.setTarget(
                        rotp.core.subsystems.target.ActionTarget
                                .fromVanilla(hit));
    }

    // an entity under the crosshair within reach, which Hamon Overdrive needs
    private static void aimAtACow(GameTestHelper helper, Climber climber) {
        net.minecraft.world.entity.animal.Cow cow =
                net.minecraft.world.entity.EntityType.COW.create(
                        helper.getLevel());
        helper.assertTrue(cow != null, "fixture: no cow");
        cow.setPos(climber.player.position().add(-1.5D, 0.0D, 0.0D));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        helper.getLevel().addFreshEntity(cow);
        rotp.core.powersystem.entityaction.LivingComponentAction
                .getComponent(climber.player).entityAim.setTarget(
                        new rotp.core.subsystems.target.ActionTarget(cow));
    }

    // 1.16 Action.checkHeldItems: "hand" for a move that needs only a free
    // main hand (Hamon Overdrive), "hands" for the two-handed ones
    private static boolean isFreeHandWarning(
            ConditionCheck check, UltimateLifeformSpec spec) {
        return check.getWarning() != null
                && check.getWarning().getContents()
                        instanceof TranslatableContents contents
                && contents.getKey().endsWith(
                        spec.requiresBothHandsFree() ? ".hands" : ".hand");
    }

    private static boolean isHandsWarning(ConditionCheck check) {
        return check.getWarning() != null
                && check.getWarning().getContents()
                        instanceof TranslatableContents contents
                && contents.getKey().endsWith(".hands");
    }

    private static Climber climber(
            GameTestHelper helper, String name, String uuid) {
        for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
            for (int z = WALL_MIN_Z; z <= WALL_MAX_Z; z++) {
                BlockPos wall = helper.absolutePos(
                        new BlockPos((int) WALL_X, y, z));
                helper.getLevel().setBlockAndUpdate(
                        wall, Blocks.STONE.defaultBlockState());
                helper.assertTrue(
                        helper.getLevel().getBlockState(wall)
                                .is(Blocks.STONE),
                        "Wall fixture block could not be placed at " + wall);
            }
        }
        List<Component> actionBar = new ArrayList<>();
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name)) {
            @Override
            public void displayClientMessage(
                    Component message, boolean overlay) {
                if (overlay) {
                    actionBar.add(message);
                }
            }
        };
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(
                origin.getX() + 3.5D,
                origin.getY() + 5.0D,
                origin.getZ() + 3.0D);
        player.setGameMode(GameType.SURVIVAL);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException(
                    "Wall climb player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException(
                    "Wall climb power grant failed: " + name);
        }
        power.updateAvailableMoves();
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.startWallClimbing(
                player,
                helper.absolutePos(new BlockPos((int) WALL_X, 5, 3)),
                Direction.WEST);
        helper.assertTrue(
                data.isWallClimbing(),
                "Wall climb fixture did not attach to the wall.");
        player.setDeltaMovement(Vec3.ZERO);
        return new Climber(player, power, data, actionBar);
    }

    private record Climber(
            ServerPlayer player,
            PlayerPower power,
            UltimateLifeformData data,
            List<Component> actionBar) {
        // 1.16 climb speed 2.0 times movement speed; backward and sideways
        // input both scale the 2.0 by one half
        double climbSpeed() {
            return player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        }

        void close() {
            power.setPowerType(null);
            player.discard();
        }
    }
}
