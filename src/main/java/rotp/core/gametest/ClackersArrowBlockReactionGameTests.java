package rotp.core.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;

/** Reactions vanilla keeps for arrows, which the 1.16.5 Clackers were: wooden buttons and the long target block signal. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersArrowBlockReactionGameTests {
    private ClackersArrowBlockReactionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_wooden_button", timeoutTicks = 200)
    public static void clackersHoldAWoodenButtonPressedWhileTheyRestInIt(GameTestHelper helper) {
        ClackersScene.start(helper, "wooden-button", 180, scene -> {
            BlockPos button = wallWithButton(scene, Blocks.OAK_BUTTON);
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            scene.throwClackers(user);
            scene.await("two ticks in the wall behind the button", () -> scene.groundTicks(0) >= 2);
            scene.then(() -> {
                restsInButton(scene, button);
                scene.check(pressed(scene, button), "1.16: Clackers are arrows, and an arrow resting in a wooden button presses it: "
                        + scene.level.getBlockState(button));
            });
            // a wooden button looks for its arrow again every 30 ticks
            scene.await("the button's next look at its arrow", () -> scene.groundTicks(0) >= 40);
            long[] left = new long[1];
            scene.then(() -> {
                restsInButton(scene, button);
                scene.check(pressed(scene, button), "the button let go while the Clackers were still inside it");
                scene.shots.get(0).discard();
                left[0] = scene.level.getGameTime();
            });
            scene.await("the button's look after the Clackers left", () -> scene.level.getGameTime() - left[0] > 30);
            scene.then(() -> scene.check(!pressed(scene, button), "the button stayed pressed after the Clackers were gone"));
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_stone_button", timeoutTicks = 120)
    public static void clackersNeitherPressNorHoldAStoneButton(GameTestHelper helper) {
        ClackersScene.start(helper, "stone-button", 100, scene -> {
            BlockPos button = wallWithButton(scene, Blocks.STONE_BUTTON);
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            scene.throwClackers(user);
            scene.await("five ticks in the wall behind the button", () -> scene.groundTicks(0) >= 5);
            long[] pressedAt = new long[1];
            scene.then(() -> {
                restsInButton(scene, button);
                scene.check(!pressed(scene, button), "arrows do not press a stone button, but the Clackers did");
                BlockState state = scene.level.getBlockState(button);
                ((ButtonBlock) state.getBlock()).press(state, scene.level, button, user);
                scene.check(pressed(scene, button), "fixture: the hand press did not press the button");
                pressedAt[0] = scene.level.getGameTime();
            });
            // a stone button stays down for 20 ticks
            scene.await("the hand-pressed button coming back up", () -> scene.level.getGameTime() - pressedAt[0] > 20);
            scene.then(() -> {
                restsInButton(scene, button);
                scene.check(!pressed(scene, button), "arrows do not hold a stone button down, but the Clackers resting in it did");
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_target_signal", timeoutTicks = 120)
    public static void clackersGiveATargetBlockTheArrowSignalLength(GameTestHelper helper) {
        ClackersScene.start(helper, "target-signal", 100, scene -> {
            scene.stone(6, -1, 9, 10, 3, 9);
            BlockPos target = scene.cell(8, 1, 9);
            scene.place(target, Blocks.TARGET.defaultBlockState());
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            scene.throwClackers(user);
            int[] powered = { -1 };
            int[] off = { -1 };
            scene.await("the end of the target block's signal", () -> {
                ClackersScene.Step landing = scene.landing(0);
                if (landing == null) {
                    return false;
                }
                int ticks = (int) (scene.level.getGameTime() - landing.post().time());
                if (scene.level.getBlockState(target).getValue(BlockStateProperties.POWER) > 0) {
                    powered[0] = ticks;
                    return false;
                }
                off[0] = ticks;
                return true;
            });
            scene.then(() -> {
                ClackersScene.Step landing = scene.landing(0);
                scene.check(landing.blockImpact() != null && landing.blockImpact().getBlockPos().equals(target),
                        "fixture: the throw missed the target block: " + landing.blockImpact());
                scene.check(powered[0] == 19 && off[0] == 20, "1.16: Clackers are arrows, and a target block answers an arrow for 20 ticks;"
                        + " the signal was last seen " + powered[0] + " ticks after the hit and was off after " + off[0]);
            });
        });
    }

    /** A wall across the throw with a button on its near side; the button is placed first so that it is also restored first. */
    private static BlockPos wallWithButton(ClackersScene scene, Block button) {
        BlockPos pos = scene.cell(8, 1, 8);
        scene.place(pos, button.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
        scene.stone(6, -1, 9, 10, 3, 9);
        scene.check(scene.level.getBlockState(pos).is(button), "fixture: the button did not stay on the wall");
        return pos;
    }

    private static void restsInButton(ClackersScene scene, BlockPos button) {
        ClackersEntity shot = scene.shots.get(0);
        BlockState state = scene.level.getBlockState(button);
        scene.check(state.getBlock() instanceof ButtonBlock, "fixture: the button is gone: " + state);
        AABB shape = state.getShape(scene.level, button).bounds().move(button);
        scene.check(shot.isAlive() && shot.isInGround() && shot.getBoundingBox().intersects(shape),
                "fixture: the Clackers do not rest inside the button: " + shot.getBoundingBox() + " against " + shape);
    }

    private static boolean pressed(ClackersScene scene, BlockPos button) {
        return scene.level.getBlockState(button).getValue(ButtonBlock.POWERED);
    }
}
