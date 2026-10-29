package rotp.addons.d4c;

import java.util.Objects;

import rotp.addons.d4c.block.D4CPortalBlock;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import rotp.addons.d4c.travel.D4CDimensionTravelService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Owner decision D-rotp-d4c-06 (2026-10-07): with the Overworld selected
// inside the Overworld a pulled non-player leaves for the parallel world,
// exactly as a player does. The donor code (1.16 ab/af.java:215-257) moved
// it to the Overworld shared spawn instead; that is deliberately not
// followed, so a donor comparison must not bring it back.
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerDecisionD4CPullInGameTests {
    private static final int PORTAL_CONTACT_TICKS = 20;

    private OwnerDecisionD4CPullInGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void pulledOverworldTargetLeavesForParallelWorld(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> pulledOverworldTargetLeavesForParallelWorldBody(h));
    }

    private static void pulledOverworldTargetLeavesForParallelWorldBody(GameTestHelper helper) {
        ResourceLocation overworld = D4CDimensionTargets.OVERWORLD;
        ResourceLocation parallel = D4CDimensionTargets.PARALLEL_WORLD;
        for (boolean player : new boolean[] {true, false}) {
            ResourceLocation actual =
                    D4CDimensionTravelService.pulledDestination(
                            overworld, overworld, player);
            helper.assertTrue(
                    parallel.equals(actual),
                    "Pull-in of a " + (player ? "player" : "non-player")
                            + " with the Overworld selected inside the "
                            + "Overworld leads to " + actual
                            + "; the owner decided on " + parallel
                            + " for every target kind.");
        }
        helper.succeed();
    }

    // Any route into an overworld portal follows the same rule; this
    // server may have no parallel world, so the choice itself is pinned
    @GameTest(template = "empty", timeoutTicks = 20 + GameTestChunkTickets.WAIT_TICKS)
    public static void overworldPortalInsideOverworldLeadsLivingToParallel(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> overworldPortalInsideOverworldLeadsLivingToParallelBody(h));
    }

    private static void overworldPortalInsideOverworldLeadsLivingToParallelBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation overworld = D4CDimensionTargets.OVERWORLD;
        ResourceLocation nether = D4CDimensionTargets.NETHER;
        ResourceLocation parallel = D4CDimensionTargets.PARALLEL_WORLD;
        Cow mob = EntityType.COW.create(level);
        ItemEntity item = new ItemEntity(
                level, 0.0D, 0.0D, 0.0D, new ItemStack(Items.STICK));
        assertPortalDestination(
                helper, overworld, overworld, mob, parallel);
        assertPortalDestination(helper, overworld, overworld, item, null);
        assertPortalDestination(helper, overworld, nether, mob, nether);
        assertPortalDestination(helper, nether, overworld, mob, overworld);
        assertPortalDestination(helper, nether, nether, mob, null);
        assertPortalDestination(helper, parallel, parallel, mob, null);
        helper.succeed();
    }

    private static void assertPortalDestination(
            GameTestHelper helper,
            ResourceLocation current,
            ResourceLocation portalTarget,
            Entity entity,
            ResourceLocation expected) {
        ResourceLocation actual =
                D4CDimensionTravelService.portalDestination(
                        current, portalTarget, entity);
        helper.assertTrue(
                Objects.equals(expected, actual),
                "A " + portalTarget + " portal inside " + current
                        + " sends " + entity.getType().toShortString()
                        + " to " + actual + ", expected " + expected + ".");
    }

    @GameTest(
            template = "empty",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS,
            batch = "ownerDecisionOverworldPortalMob")
    public static void overworldPortalInsideOverworldNeverRelocatesMob(
            GameTestHelper helper) {
        GameTestChunkTickets.aroundOrigin(helper)
                .whenTicking(() -> {}, h -> overworldPortalInsideOverworldNeverRelocatesMobBody(h));
    }

    private static void overworldPortalInsideOverworldNeverRelocatesMobBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(
                level.dimension() == Level.OVERWORLD,
                "The GameTest level is not the Overworld.");
        // the origin column: the only chunk known to tick entities
        BlockPos floor = helper.absolutePos(new BlockPos(0, 60, 0));
        BlockPos feet = floor.above();
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(
                feet.above(), Blocks.AIR.defaultBlockState());
        String tag = "rotp_d4c_owner_decision_portal_gametest";
        Cow target = EntityType.COW.create(level);
        helper.assertTrue(
                target != null, "The portal fixture mob was not created.");
        target.moveTo(
                feet.getX() + 0.5D,
                feet.getY(),
                feet.getZ() + 0.5D,
                0.0F,
                0.0F);
        target.removeFreeWill();
        target.addTag(tag);
        helper.assertTrue(
                level.addFreshEntity(target),
                "The portal fixture mob was rejected by its level.");
        ServerLevel parallel = level.getServer().getLevel(
                D4CDimensionTargets.levelKey(
                        D4CDimensionTargets.PARALLEL_WORLD));

        int[] placedAt = {-1};
        BlockPos[] relocatedTo = {null};
        helper.onEachTick(() -> {
            if (placedAt[0] < 0) {
                if (target.tickCount >= 5 && target.onGround()) {
                    helper.assertTrue(
                            D4CDimensionTravelService.placePortal(
                                    level,
                                    target,
                                    D4CDimensionTargets.OVERWORLD),
                            "The overworld portal was not placed at the "
                                    + "fixture mob's feet.");
                    placedAt[0] = target.tickCount;
                }
                return;
            }
            if (relocatedTo[0] == null
                    && !target.isRemoved()
                    && target.level() == level
                    && (target.blockPosition().getX() != feet.getX()
                            || target.blockPosition().getZ()
                                    != feet.getZ())) {
                relocatedTo[0] = target.blockPosition();
                target.discard();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    relocatedTo[0] == null,
                    "The overworld portal inside the Overworld relocated "
                            + "the mob inside the Overworld, from " + feet
                            + " to " + relocatedTo[0] + " (shared spawn "
                            + level.getSharedSpawnPos() + "); the owner "
                            + "decided that it leaves for the parallel "
                            + "world and is never moved within its own.");
            helper.assertTrue(
                    placedAt[0] >= 0
                            && (target.isRemoved()
                                    || target.tickCount >= placedAt[0]
                                            + PORTAL_CONTACT_TICKS),
                    "The fixture mob has not spent "
                            + PORTAL_CONTACT_TICKS
                            + " of its own ticks in the portal yet: "
                            + "placedAt=" + placedAt[0] + ", tickCount="
                            + target.tickCount + ".");
            if (parallel != null) {
                boolean arrived = !parallel.getEntities(
                                EntityType.COW,
                                cow -> cow.getTags().contains(tag))
                        .isEmpty();
                helper.assertTrue(
                        arrived,
                        "The overworld portal inside the Overworld did "
                                + "not send the mob to the parallel world: "
                                + "removed=" + target.isRemoved() + ".");
                parallel.getEntities(
                                EntityType.COW,
                                cow -> cow.getTags().contains(tag))
                        .forEach(Cow::discard);
            } else {
                // no parallel world on this server: the mob has nowhere
                // to go, so it must be left exactly where it stands
                helper.assertTrue(
                        !target.isRemoved()
                                && target.level() == level
                                && target.blockPosition().equals(feet)
                                && level.getBlockState(feet).getBlock()
                                        instanceof D4CPortalBlock,
                        "Without a parallel world the mob should stay "
                                + "in the unused portal: removed="
                                + target.isRemoved() + ", at "
                                + target.blockPosition() + ", feetBlock="
                                + level.getBlockState(feet).getBlock()
                                + ".");
            }
            target.discard();
            level.setBlockAndUpdate(
                    feet, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(
                    floor, Blocks.AIR.defaultBlockState());
        });
    }
}
