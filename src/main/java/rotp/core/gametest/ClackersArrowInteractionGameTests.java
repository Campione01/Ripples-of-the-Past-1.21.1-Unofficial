package rotp.core.gametest;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;

/** Interactions the 1.16.5 Clackers had as an arrow: its pick box, attacks against it and the block it hits. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersArrowInteractionGameTests {
    private ClackersArrowInteractionGameTests() {}

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_arrow_pick_box", timeoutTicks = 120)
    public static void stuckClackersKeepTheArrowPickBoxAndCannotBeAttacked(GameTestHelper helper) {
        ClackersScene.start(helper, "arrow-pick-box", 100, scene -> {
            scene.stone(6, -1, 9, 10, 3, 9);
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            scene.throwClackers(user);
            scene.await("one tick in the wall", () -> scene.groundTicks(0) >= 1);
            scene.then(() -> {
                ClackersEntity shot = scene.shots.get(0);
                Vec3 centre = shot.getBoundingBox().getCenter();
                scene.check(pick(user, centre.add(-2.0D, 0.0D, -0.1D), centre.add(2.0D, 0.0D, -0.1D)) == shot,
                        "fixture: a ray through the Clackers must pick it");
                scene.check(pick(user, centre.add(-2.0D, 0.6D, -0.1D), centre.add(2.0D, 0.6D, -0.1D)) == null,
                        "a ray 0.35 above the Clackers box picked it; pick radius " + shot.getPickRadius());
                user.attack(shot);
                scene.check(!shot.isAttackable() && user.getLastHurtMob() == null, "a punch attacked the stuck Clackers");
                scene.check(!shot.hurt(scene.level.damageSources().generic(), 1.0F), "damage against a Clackers must be refused as for an arrow");
                scene.check(shot.isAlive() && shot.isInGround(), "the Clackers did not stay in the wall");
            });
        });
    }

    /** The crosshair query of GameRenderer.pick. */
    @Nullable
    private static Entity pick(Player viewer, Vec3 from, Vec3 to) {
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(viewer, from, to, new AABB(from, to).inflate(1.0D),
                entity -> !entity.isSpectator() && entity.isPickable(), from.distanceToSqr(to));
        return hit == null ? null : hit.getEntity();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_block_hit_callback", timeoutTicks = 120)
    public static void clackersTriggerTheBlockTheyHit(GameTestHelper helper) {
        ClackersScene.start(helper, "block-hit-callback", 100, scene -> {
            scene.stone(6, -1, 9, 10, 3, 9);
            BlockPos target = scene.cell(8, 1, 9);
            scene.place(target, Blocks.TARGET.defaultBlockState());
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            scene.throwClackers(user);
            scene.await("three ticks in the target block", () -> scene.groundTicks(0) >= 3);
            scene.then(() -> {
                ClackersScene.Track track = scene.track(0);
                ClackersScene.Step landing = track.landing();
                BlockHitResult hit = landing.blockImpact();
                BlockState state = scene.level.getBlockState(target);
                scene.check(hit != null && hit.getBlockPos().equals(target) && state.is(Blocks.TARGET), "the throw missed the target block: " + hit);
                scene.check(state.getValue(BlockStateProperties.POWER) > 0, "the hit block got no projectile callback: " + state);
                int at = track.steps.indexOf(landing);
                for (int ticks = 0; ticks <= 3; ticks++) {
                    scene.check(track.steps.get(at + ticks).post().grounded(), "left a block that only changed its state after " + ticks + " ticks");
                }
            });
        });
    }
}
