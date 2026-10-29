package rotp.addons.stickyfingers.state;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.ability.StickyFingersClimbZipperAbility;
import rotp.addons.stickyfingers.ability.StickyFingersHideInMobAbility;
import rotp.addons.stickyfingers.ability.StickyFingersOpenStorageAbility;
import rotp.addons.stickyfingers.ability.StickyFingersUnzipPartAbility;
import rotp.addons.stickyfingers.ability.StickyFingersUnzipPunchAbility;
import rotp.addons.stickyfingers.ability.StickyFingersZipperAbility;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.entity.StickyFingersExtendedPunchEntity;
import rotp.core.api.client.render.AbilitySelectionSurface;
import rotp.core.api.client.render.AbilitySelectionVisualQuery;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(StickyFingersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZipperPhasingGameTests {
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final VoxelShape UP_PLATE = Shapes.box(0, 0.99, 0, 1, 1, 1);
    private static final VoxelShape NORTH_PLATE = Shapes.box(0, 0, 0, 1, 1, 0.01);
    private static final VoxelShape SOUTH_PLATE = Shapes.box(0, 0, 0.99, 1, 1, 1);
    private static final VoxelShape EAST_PLATE = Shapes.box(0.99, 0, 0, 1, 1, 1);
    // The saved name of a lease's linked zipper; leases written before R764 do not have it.
    private static final String LINK_TAG = "LinkedPosition";
    private static final int SCENE_MARGIN_BLOCKS = 3;
    private static final int SCENE_OWN_TICKS = 40;
    // paced ticks, at least 12 s, for the scene's chunks to tick entities
    private static final int SCENE_WAIT_TICKS = 2400;
    private static final long SCENE_PAUSE_MILLIS = 5L;
    private static final int STAND_SCENE_TIMEOUT_TICKS = SCENE_OWN_TICKS + SCENE_WAIT_TICKS;
    private static long pausedAt = Long.MIN_VALUE;

    private ZipperPhasingGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void slabPhasingUsesGroundedNotShiftAllowance(GameTestHelper helper) {
        matrix(helper, Blocks.STONE_SLAB.defaultBlockState());
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void snowPhasingUsesGroundedNotShiftAllowance(GameTestHelper helper) {
        matrix(helper, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 5));
    }

    // 1.16 OpenStorageInTarget (TargetRequirement.ANY) took the client crosshair, an OUTLINE pick: a short grass in
    // front of a wall is the block whose storage opens.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void storageTargetIsTheGrassTheCrosshairMeets(GameTestHelper helper) {
        FakePlayer player = fixture(helper, false);
        BlockPos grass = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos wall = helper.absolutePos(new BlockPos(4, 3, 2));
        try {
            player.setPos(helper.absoluteVec(new Vec3(0.5D, 2.0D, 2.5D)));
            player.setYRot(-90.0F);
            player.setXRot(0.0F);
            put(helper, grass, Blocks.SHORT_GRASS.defaultBlockState());
            put(helper, wall, Blocks.STONE.defaultBlockState());
            ActionTarget target = StickyFingersOpenStorageAbility.findServerTarget(player, helper.getLevel());
            helper.assertTrue(target.getType() == ActionTarget.TargetType.BLOCK && grass.equals(target.getBlockPos()),
                    "The storage target is the grass in front of the wall (1.16's crosshair stopped at its outline); got "
                            + target.getType() + " " + target.getBlockPos());
            helper.succeed();
        }
        finally {
            put(helper, grass, Blocks.AIR.defaultBlockState());
            put(helper, wall, Blocks.AIR.defaultBlockState());
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void noneligiblePlayersRetainRegisteredPartialCollision(GameTestHelper helper) {
        BlockPos pos = prepareShape(helper, Blocks.STONE_SLAB.defaultBlockState());
        FakePlayer ordinary = fixture(helper, false);
        FakePlayer belowMax = fixture(helper, true);
        try {
            StandPower power = StandPower.get(belowMax);
            power.setResolveLevel(0);
            helper.assertTrue(!ZipperPhasing.eligible(ordinary) && !ZipperPhasing.eligible(belowMax),
                    "Noneligible controls unexpectedly have maximum-Resolve phasing");
            VoxelShape original = helper.getLevel().getBlockState(pos)
                    .getCollisionShape(helper.getLevel(), pos, CollisionContext.empty());
            for (FakePlayer player : new FakePlayer[] { ordinary, belowMax }) {
                locate(player, pos, original, false, true);
                assertShape(helper, original, query(helper, pos, player),
                        "Noneligible partial collision changed");
            }
            helper.succeed();
        }
        finally {
            close(ordinary);
            close(belowMax);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void explicitSinkingStillOverridesGroundedPartialCollision(GameTestHelper helper) {
        BlockPos pos = prepareShape(helper, Blocks.STONE_SLAB.defaultBlockState());
        FakePlayer player = fixture(helper, true);
        try {
            VoxelShape original = helper.getLevel().getBlockState(pos)
                    .getCollisionShape(helper.getLevel(), pos, CollisionContext.empty());
            locate(player, pos, original, true, false);
            assertShape(helper, original, query(helper, pos, player), "Grounded nonsinking control changed");
            ZipperPhasing.setServerSinking(player, true);
            assertShape(helper, Shapes.empty(), query(helper, pos, player), "Explicit sinking did not phase");
            ZipperPhasing.setServerSinking(player, false);
            assertShape(helper, original, query(helper, pos, player), "Sinking release did not restore collision");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void closedZipperHostStaysPhaseableGround(GameTestHelper helper) {
        BlockPos pos = prepareShape(helper, zipper(Direction.NORTH, false));
        FakePlayer ordinary = fixture(helper, false);
        FakePlayer player = fixture(helper, true);
        try {
            locate(ordinary, pos, Shapes.block(), false, false);
            assertShape(helper, Shapes.block(), query(helper, pos, ordinary),
                    "A closed zipper stopped being solid for an ordinary player");
            locate(player, pos, Shapes.block(), true, false);
            assertShape(helper, Shapes.block(), query(helper, pos, player),
                    "A closed zipper stopped carrying a user standing on it");
            locate(player, pos, Shapes.block(), false, false);
            assertShape(helper, NORTH_PLATE, query(helper, pos, player),
                    "The block behind a closed zipper is not phaseable ground");
            put(helper, pos.north(), Blocks.AIR.defaultBlockState());
            assertShape(helper, NORTH_PLATE, query(helper, pos, player),
                    "The closed zipper face did not stay shut towards open air");

            put(helper, pos.north(), Blocks.STONE.defaultBlockState());
            put(helper, pos, zipper(Direction.UP, false));
            locate(player, pos, Shapes.block(), true, true);
            ZipperPhasing.setServerSinking(player, true);
            assertShape(helper, UP_PLATE, query(helper, pos, player),
                    "A sinking user fell through a closed floor zipper");
            helper.succeed();
        }
        finally {
            close(ordinary);
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void closedZipperNeighbourIsOrdinaryGround(GameTestHelper helper) {
        BlockPos pos = prepareShape(helper, Blocks.STONE.defaultBlockState());
        FakePlayer player = fixture(helper, true);
        try {
            locate(player, pos, Shapes.block(), false, false);
            assertShape(helper, Shapes.empty(), query(helper, pos, player), "Enclosed stone control changed");
            put(helper, pos.east(), zipper(Direction.UP, true));
            assertShape(helper, Shapes.empty(), query(helper, pos, player),
                    "An open zipper beside the phased block raised a boundary");
            put(helper, pos.east(), zipper(Direction.UP, false));
            assertShape(helper, Shapes.empty(), query(helper, pos, player),
                    "A closed zipper beside the phased block raised a boundary the donor does not have");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void zipperFrontCellOpensAdjacentGround(GameTestHelper helper) {
        BlockPos pos = prepareShape(helper, Blocks.STONE.defaultBlockState());
        BlockPos cell = pos.above();
        FakePlayer player = fixture(helper, true);
        try {
            locate(player, pos, Shapes.block(), false, false);
            put(helper, cell, Blocks.AIR.defaultBlockState());
            put(helper, cell.south(), Blocks.STONE.defaultBlockState());
            put(helper, cell.above(), Blocks.STONE.defaultBlockState());
            assertShape(helper, UP_PLATE, query(helper, pos, player), "Open air above did not bound the ground");

            put(helper, cell.south(), zipper(Direction.NORTH, false));
            assertShape(helper, Shapes.empty(), query(helper, pos, player),
                    "Ground beside the cell a zipper faces did not open towards it");
            put(helper, cell.south(), zipper(Direction.SOUTH, false));
            assertShape(helper, UP_PLATE, query(helper, pos, player),
                    "A zipper facing away opened ground it does not touch");

            put(helper, cell.south(), Blocks.STONE.defaultBlockState());
            put(helper, cell.above(), zipper(Direction.DOWN, false));
            assertShape(helper, UP_PLATE, query(helper, pos, player),
                    "A zipper on the opposite wall did not keep the donor boundary");

            put(helper, cell.south(), zipper(Direction.NORTH, false));
            put(helper, cell.above(), zipper(Direction.NORTH, false));
            assertShape(helper, UP_PLATE, query(helper, pos, player),
                    "A same-facing zipper one step further did not keep the donor boundary");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void doorsFluidsAndUnbreakableBlocksBoundPhasing(GameTestHelper helper) {
        BlockPos pos = prepareShape(helper, Blocks.STONE.defaultBlockState());
        FakePlayer player = fixture(helper, true);
        try {
            locate(player, pos, Shapes.block(), false, false);
            put(helper, pos.east(), Blocks.IRON_DOOR.defaultBlockState());
            put(helper, pos.west(), Blocks.OAK_TRAPDOOR.defaultBlockState());
            put(helper, pos.south(), Blocks.WATER.defaultBlockState());
            put(helper, pos.below(), Blocks.BEDROCK.defaultBlockState());
            VoxelShape expected = Shapes.or(Shapes.box(0.99, 0, 0, 1, 1, 1),
                    Shapes.box(0, 0, 0, 0.01, 1, 1), Shapes.box(0, 0, 0.99, 1, 1, 1));
            assertShape(helper, expected, query(helper, pos, player),
                    "Doors, trapdoors and fluids did not bound the phased block as in the donor");
            for (BlockPos kept : new BlockPos[] { pos.east(), pos.west(), pos.below() }) {
                VoxelShape original = helper.getLevel().getBlockState(kept)
                        .getCollisionShape(helper.getLevel(), kept, CollisionContext.empty());
                helper.assertTrue(!original.isEmpty(), "Missing collision control at " + kept);
                assertShape(helper, original, query(helper, kept, player),
                        "A door, trapdoor or unbreakable block became phaseable at " + kept);
            }
            put(helper, pos.south(), Blocks.AIR.defaultBlockState());
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sinkFlagIsNotTiedToGroundEntry(GameTestHelper helper) {
        BlockPos pos = prepareShape(helper, Blocks.STONE_SLAB.defaultBlockState());
        BlockPos entrance = helper.absolutePos(new BlockPos(4, 2, 2));
        FakePlayer player = fixture(helper, true);
        try {
            VoxelShape original = helper.getLevel().getBlockState(pos)
                    .getCollisionShape(helper.getLevel(), pos, CollisionContext.empty());
            player.setPos(entrance.getX() + 0.5D, entrance.getY() + 4.0D, entrance.getZ() + 0.5D);
            ZipperInGround.tick(player);
            helper.assertTrue(!ZipperInGround.isInGround(player), "The user did not leave the ground");
            player.setShiftKeyDown(true);
            ZipperPhasing.setServerSinking(player, true);
            ZipperPhasing.onPlayerTick(new PlayerTickEvent.Post(player));

            player.setPos(entrance.getX() + 0.5D, entrance.getY() + 0.1D, entrance.getZ() + 0.5D);
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player), "The user did not enter the open zipper");
            locate(player, pos, original, true, true);
            assertShape(helper, Shapes.empty(), query(helper, pos, player),
                    "A double sneak made before entering the ground was dropped");

            ZipperPhasing.onPlayerTick(new PlayerTickEvent.Post(player));
            assertShape(helper, Shapes.empty(), query(helper, pos, player), "A held sneak stopped sinking");
            player.setShiftKeyDown(false);
            ZipperPhasing.onPlayerTick(new PlayerTickEvent.Post(player));
            assertShape(helper, original, query(helper, pos, player), "Releasing sneak did not end the sink");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void jumpLeavesGroundOnlyThroughTheZipperColumn(GameTestHelper helper) {
        // Four stone layers, y 1..4, open air above; the open floor zipper replaces (2, 4, 2).
        fill(helper, new BlockPos(1, 1, 1), new BlockPos(3, 4, 5), Blocks.STONE.defaultBlockState());
        fill(helper, new BlockPos(1, 5, 1), new BlockPos(3, 8, 5), Blocks.AIR.defaultBlockState());
        BlockPos entrance = helper.absolutePos(new BlockPos(2, 4, 2));
        put(helper, entrance, zipper(Direction.UP, true));
        double surface = entrance.getY() + 1.0D;
        FakePlayer player = fixture(helper, true);
        try {
            StandPower power = StandPower.get(player);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getStamina() > 0.0F, "The fixture has no stamina to stay in the ground");

            player.setPos(entrance.getX() + 0.5D, surface - 3.0D, entrance.getZ() + 2.5D);
            player.setOnGround(true);
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperPhasing.eligible(player), "The user is not phasing two blocks under the surface");
            jump(player, surface, Vec3.ZERO);
            assertHeight(helper, player, surface - 2.0D, "A jump under plain ground did not climb one block");
            for (int attempt = 0; attempt < 2; attempt++) {
                ZipperInGround.tick(player);
                jump(player, surface, Vec3.ZERO);
                assertHeight(helper, player, surface - 2.0D,
                        "A jump away from the zipper left the ground through the surface");
            }
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperPhasing.eligible(player), "The user under the surface stopped phasing");

            for (int step = 0; step < 10; step++) {
                player.move(MoverType.SELF, new Vec3(0.0D, -0.0784D, -0.2D));
            }
            helper.assertTrue(Math.abs(player.getZ() - (entrance.getZ() + 0.5D)) < 1.0E-6D
                            && !player.horizontalCollision,
                    "The user could not walk through the ground to the zipper column: z=" + player.getZ());
            assertHeight(helper, player, surface - 2.0D, "Walking under ground changed depth");

            ZipperInGround.tick(player);
            jump(player, surface, Vec3.ZERO);
            assertHeight(helper, player, surface - 1.0D, "A jump under the open zipper did not reach its hollow");
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player), "The zipper hollow is not in-ground");
            jump(player, surface, new Vec3(0.15D, 0.0D, 0.0D));
            assertHeight(helper, player, surface, "A jump out of the open zipper did not land on the surface");
            ZipperInGround.tick(player);
            helper.assertTrue(!ZipperInGround.isInGround(player) && !ZipperPhasing.eligible(player),
                    "The user on the surface is still in the ground");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void zipperFrontCellEndsTheInGroundState(GameTestHelper helper) {
        // A one-block-high alcove: the wall zipper at (2, 2, 2) faces north into the roofed cell (2, 2, 1).
        BlockPos hollow = prepareShape(helper, zipper(Direction.NORTH, true));
        BlockPos front = hollow.north();
        put(helper, front, Blocks.AIR.defaultBlockState());
        put(helper, front.above(), Blocks.STONE.defaultBlockState());
        FakePlayer player = fixture(helper, true);
        try {
            StandPower power = StandPower.get(player);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(!ZipperInGround.isBlockFree(helper.getLevel(), front.above()),
                    "The alcove has no roof, so the head-free rule would end the state anyway");

            stand(player, hollow);
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player), "The open wall zipper is not in-ground");
            put(helper, hollow, zipper(Direction.SOUTH, true));
            stand(player, front);
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player),
                    "A roofed cell behind a zipper facing away ended the in-ground state");

            put(helper, hollow, zipper(Direction.NORTH, true));
            ZipperInGround.tick(player);
            helper.assertTrue(!ZipperInGround.isInGround(player),
                    "The in-ground state did not end in the cell an open zipper faces");

            stand(player, hollow);
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player), "The user could not re-enter the open zipper");
            put(helper, hollow, zipper(Direction.NORTH, false));
            stand(player, front);
            ZipperInGround.tick(player);
            helper.assertTrue(!ZipperInGround.isInGround(player),
                    "The in-ground state did not end in the cell a closed zipper faces");
            helper.assertTrue(power.getStamina() > 0.0F, "The fixture ran out of stamina, which ends the state too");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    // 1.16 ClientEvents fogColor/fogDensity: (hasZippersAround(head) || isInGround) && !isBlockZipper(head). The
    // donor's zipper block stands in the free cell its zipper faces, so only that cell skips the fog.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inGroundFogIsSkippedOnlyInTheZipperCell(GameTestHelper helper) {
        BlockPos hollow = pillar(helper, zipper(Direction.UP, true));
        BlockPos above = hollow.above();
        FakePlayer player = fixture(helper, true);
        try {
            ServerLevel level = helper.getLevel();
            StandPower power = StandPower.get(player);
            power.setStamina(power.getMaxStamina());

            stand(player, hollow);
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player), "The open floor zipper is not in-ground");
            helper.assertTrue(!ZipperInGround.fogsView(level, above, ZipperInGround.isInGround(player)),
                    "The head in the cell an open floor zipper faces is fogged");

            put(helper, hollow, zipper(Direction.NORTH, true));
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player), "The open wall zipper is not in-ground");
            helper.assertTrue(ZipperInGround.isBlockFree(level, above) && !ZipperInGround.isZipperFront(level, above),
                    "The cell above the one-block wall is not open air beside the zipper");
            helper.assertTrue(ZipperInGround.fogsView(level, above, ZipperInGround.isInGround(player)),
                    "The head above a one-block wall, out of the zipper's cell, skipped the in-ground fog");
            helper.assertTrue(!ZipperInGround.fogsView(level, hollow.north(), true),
                    "The head in the cell an open wall zipper faces is fogged");
            helper.assertTrue(ZipperInGround.fogsView(level, hollow, false),
                    "The head inside the open zipper is not fogged");
            helper.assertTrue(ZipperInGround.fogsView(level, hollow.below(), true),
                    "The head in solid ground is not fogged");
            helper.assertTrue(!ZipperInGround.fogsView(level, above, false),
                    "Open air out of the ground is fogged");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void openSideZipperCarriesAnEntityOnTop(GameTestHelper helper) {
        BlockPos hollow = pillar(helper, zipper(Direction.NORTH, true));
        double x = hollow.getX() + 0.5D;
        double z = hollow.getZ() + 0.5D;
        Entity probe = probe(helper, x, hollow.getY() + 2.0D, z);
        probe.move(MoverType.SELF, new Vec3(0.0D, -3.0D, 0.0D));
        helper.assertTrue(Math.abs(probe.getY() - (hollow.getY() + 1.0D)) < 1.0E-6D && probe.onGround(),
                "An entity on top of a block with an open side zipper fell into it: y=" + probe.getY()
                        + ", expected=" + (hollow.getY() + 1.0D));

        put(helper, hollow, zipper(Direction.UP, true));
        probe.setPos(x, hollow.getY() + 2.0D, z);
        probe.move(MoverType.SELF, new Vec3(0.0D, -3.0D, 0.0D));
        helper.assertTrue(Math.abs(probe.getY() - hollow.getY()) < 1.0E-6D,
                "An entity did not fall into an open floor zipper: y=" + probe.getY());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void openZipperIsEnteredAndLeftOnlyThroughItsFace(GameTestHelper helper) {
        BlockPos hollow = pillar(helper, zipper(Direction.NORTH, true));
        double x = hollow.getX() + 0.5D;
        double y = hollow.getY();
        double z = hollow.getZ() + 0.5D;
        Entity probe = probe(helper, x, y, z - 1.0D);
        probe.move(MoverType.SELF, new Vec3(0.0D, 0.0D, 1.0D));
        helper.assertTrue(Math.abs(probe.getZ() - z) < 1.0E-6D && !probe.horizontalCollision,
                "An entity could not walk into an open zipper through its face: z=" + probe.getZ());
        helper.assertTrue(!helper.getLevel().getBlockState(hollow).isSuffocating(helper.getLevel(), hollow)
                        && !probe.isInWall(),
                "An entity inside an open zipper suffocates");

        for (Direction side : new Direction[] { Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.UP }) {
            probe.setPos(x, y, z);
            probe.move(MoverType.SELF, Vec3.atLowerCornerOf(side.getNormal()));
            helper.assertTrue(within(hollow, probe),
                    "An entity left an open zipper through its free " + side + " side, which has no zipper: "
                            + probe.position());
        }
        put(helper, hollow.below(), Blocks.AIR.defaultBlockState());
        probe.setPos(x, y + 0.2D, z);
        probe.move(MoverType.SELF, new Vec3(0.0D, -1.0D, 0.0D));
        helper.assertTrue(within(hollow, probe),
                "An entity fell out of an open zipper through its free underside: " + probe.position());

        probe.move(MoverType.SELF, new Vec3(0.0D, 0.0D, -1.0D));
        helper.assertTrue(Math.abs(probe.getZ() - (z - 1.0D)) < 1.0E-6D,
                "An entity could not leave an open zipper through its face: z=" + probe.getZ());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void zipperOnAOneBlockWallOpensThroughToItsFarSide(GameTestHelper helper) {
        BlockPos wall = pillar(helper, Blocks.STONE.defaultBlockState());
        BlockPos far = wall.south();
        put(helper, far.above(), Blocks.STONE.defaultBlockState());
        FakePlayer player = user(helper);
        try {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(press(helper, player, wall, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED,
                    "Could not place a zipper on the one-block wall");
            helper.assertTrue(isZipper(level.getBlockState(wall), Direction.NORTH, false),
                    "The wall block did not become a closed zipper facing north");
            double x = wall.getX() + 0.5D;
            double y = wall.getY();
            double z = wall.getZ() + 0.5D;
            Entity probe = probe(helper, x, y, z);
            assertShape(helper, Shapes.block(),
                    level.getBlockState(wall).getCollisionShape(level, wall, CollisionContext.of(probe)),
                    "A closed zipper on a one-block wall is not solid");

            helper.assertTrue(press(helper, player, wall, Direction.NORTH, true) == ZipperLeaseData.UseResult.OPENED,
                    "A Shift press did not open the zipper");
            for (Direction side : new Direction[] { Direction.EAST, Direction.WEST, Direction.UP }) {
                probe.setPos(x, y, z);
                probe.move(MoverType.SELF, Vec3.atLowerCornerOf(side.getNormal()));
                helper.assertTrue(within(wall, probe),
                        "An entity left the opened one-block wall through its free " + side
                                + " side, which has no zipper: " + probe.position());
            }
            helper.assertTrue(isThrough(level.getBlockState(wall)),
                    "A zipper on a one-block wall with free space behind it did not open the far side");
            probe.setPos(x, y, z - 1.0D);
            probe.move(MoverType.SELF, new Vec3(0.0D, 0.0D, 2.0D));
            helper.assertTrue(Math.abs(probe.getZ() - (z + 1.0D)) < 1.0E-6D && !probe.horizontalCollision,
                    "An entity could not walk through the opened one-block wall front to back: z=" + probe.getZ());

            StandPower power = StandPower.get(player);
            power.setStamina(power.getMaxStamina());
            stand(player, wall);
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player), "The open through zipper is not in-ground");
            stand(player, far);
            ZipperInGround.tick(player);
            helper.assertTrue(!ZipperInGround.isInGround(player),
                    "The in-ground state did not end in the far cell of a through zipper");

            helper.assertTrue(press(helper, player, wall, Direction.NORTH, false) == ZipperLeaseData.UseResult.RESTORED
                            && level.getBlockState(wall).is(Blocks.STONE),
                    "A plain press did not restore the wall block");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void zipperOnATwoBlockWallLinksAZipperOnItsFarSide(GameTestHelper helper) {
        BlockPos front = pillar(helper, Blocks.STONE.defaultBlockState());
        BlockPos back = front.south();
        put(helper, back, Blocks.OAK_PLANKS.defaultBlockState());
        FakePlayer player = user(helper);
        try {
            ServerLevel level = helper.getLevel();
            ZipperLeaseData leases = ZipperLeaseData.get(level);
            int before = leases.activeCount();
            helper.assertTrue(press(helper, player, front, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED,
                    "Could not place a zipper on the two-block wall");
            helper.assertTrue(isZipper(level.getBlockState(front), Direction.NORTH, false)
                            && isZipper(level.getBlockState(back), Direction.SOUTH, false),
                    "A two-block wall did not get a zipper on each of its faces: back=" + level.getBlockState(back));
            helper.assertTrue(!isThrough(level.getBlockState(front)) && !isThrough(level.getBlockState(back))
                            && leases.getAt(front) != null && leases.getAt(back) != null
                            && leases.activeCount() == before + 2,
                    "The linked pair is not two ordinary leased zippers");

            helper.assertTrue(press(helper, player, front, Direction.NORTH, true) == ZipperLeaseData.UseResult.OPENED
                            && isZipper(level.getBlockState(front), Direction.NORTH, true)
                            && isZipper(level.getBlockState(back), Direction.SOUTH, true),
                    "One Shift press did not open both linked zippers");
            double x = front.getX() + 0.5D;
            double y = front.getY();
            double z = front.getZ() + 0.5D;
            Entity probe = probe(helper, x, y, z - 1.0D);
            probe.move(MoverType.SELF, new Vec3(0.0D, 0.0D, 3.0D));
            helper.assertTrue(Math.abs(probe.getZ() - (z + 2.0D)) < 1.0E-6D && !probe.horizontalCollision,
                    "An entity could not walk through the opened two-block wall: z=" + probe.getZ());
            probe.setPos(x, y, z + 1.0D);
            probe.move(MoverType.SELF, new Vec3(1.0D, 0.0D, 0.0D));
            helper.assertTrue(within(back, probe),
                    "An entity left the linked far block through a side that has no zipper: " + probe.position());

            helper.assertTrue(press(helper, player, back, Direction.SOUTH, true) == ZipperLeaseData.UseResult.CLOSED
                            && isZipper(level.getBlockState(front), Direction.NORTH, false)
                            && isZipper(level.getBlockState(back), Direction.SOUTH, false),
                    "A Shift press on the far zipper did not close both linked zippers");
            helper.assertTrue(press(helper, player, back, Direction.SOUTH, false) == ZipperLeaseData.UseResult.RESTORED
                            && level.getBlockState(front).is(Blocks.STONE)
                            && level.getBlockState(back).is(Blocks.OAK_PLANKS)
                            && leases.getAt(front) == null && leases.getAt(back) == null
                            && leases.activeCount() == before,
                    "One plain press did not restore both original blocks: front=" + level.getBlockState(front)
                            + ", back=" + level.getBlockState(back));

            // Everything else gets a single zipper, as in the donor.
            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState skull = Blocks.SKELETON_SKULL.defaultBlockState();
            put(helper, back.south(), stone);
            assertSingle(helper, player, front, stone, Blocks.OAK_PLANKS.defaultBlockState(),
                    "A wall of three blocks got a far zipper");
            put(helper, back.south(), air);
            assertSingle(helper, player, front, stone, skull,
                    "A back block without a sturdy far face got a zipper");
            assertSingle(helper, player, front, stone, Blocks.BEDROCK.defaultBlockState(),
                    "A back block that cannot take a zipper got one");
            assertSingle(helper, player, front, skull, air,
                    "A block without a sturdy far face was opened through");
            put(helper, back.south(), zipper(Direction.NORTH, false));
            assertSingle(helper, player, front, stone, air,
                    "A wall whose far cell already holds a zipper was opened through");
            put(helper, back.south(), air);
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void linkedLeaseSurvivesSavingAndOldRecordsStillLoad(GameTestHelper helper) {
        BlockPos front = pillar(helper, Blocks.STONE.defaultBlockState());
        BlockPos back = front.south();
        put(helper, back, Blocks.OAK_PLANKS.defaultBlockState());
        FakePlayer player = user(helper);
        try {
            ServerLevel level = helper.getLevel();
            ZipperLeaseData leases = ZipperLeaseData.get(level);
            helper.assertTrue(press(helper, player, front, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED,
                    "Could not place a zipper on the two-block wall");
            CompoundTag saved = leases.save(new CompoundTag(), level.registryAccess());
            CompoundTag frontTag = activeRecord(saved, front);
            CompoundTag backTag = activeRecord(saved, back);
            helper.assertTrue(frontTag != null && backTag != null, "The linked pair was not saved as two leases");
            helper.assertTrue(frontTag.contains(LINK_TAG, Tag.TAG_LONG) && frontTag.getLong(LINK_TAG) == back.asLong()
                            && backTag.contains(LINK_TAG, Tag.TAG_LONG) && backTag.getLong(LINK_TAG) == front.asLong(),
                    "The saved leases do not name each other");

            ZipperLeaseData reloaded = ZipperLeaseData.load(saved.copy(), level.registryAccess());
            helper.assertTrue(reloaded.getAt(front) != null && reloaded.getAt(back) != null
                            && reloaded.getAt(front).toNbt().getLong(LINK_TAG) == back.asLong()
                            && reloaded.getAt(back).toNbt().getLong(LINK_TAG) == front.asLong(),
                    "A reloaded pair lost its link");

            CompoundTag old = saved.copy();
            for (Tag element : old.getList("Active", Tag.TAG_COMPOUND)) {
                ((CompoundTag) element).remove(LINK_TAG);
            }
            ZipperLeaseData legacy = ZipperLeaseData.load(old, level.registryAccess());
            helper.assertTrue(legacy.activeCount() == reloaded.activeCount()
                            && legacy.getAt(front) != null && legacy.getAt(back) != null
                            && !legacy.getAt(front).toNbt().contains(LINK_TAG)
                            && !legacy.getAt(back).toNbt().contains(LINK_TAG),
                    "A lease saved without the link field did not load as a single zipper");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void touchingZippersOfOneFacingOpenAndCloseTogether(GameTestHelper helper) {
        // A wall three blocks thick, so every zipper on it is a single one.
        fill(helper, new BlockPos(1, 2, 1), new BlockPos(3, 5, 5), Blocks.AIR.defaultBlockState());
        fill(helper, new BlockPos(1, 2, 3), new BlockPos(3, 4, 5), Blocks.STONE.defaultBlockState());
        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 3));
        BlockPos beside = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos diagonal = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos apart = helper.absolutePos(new BlockPos(1, 4, 3));
        BlockPos roof = helper.absolutePos(new BlockPos(3, 4, 3));
        BlockPos added = helper.absolutePos(new BlockPos(2, 3, 3));
        FakePlayer player = user(helper);
        try {
            ServerLevel level = helper.getLevel();
            ZipperLeaseData leases = ZipperLeaseData.get(level);
            for (BlockPos pos : new BlockPos[] { first, beside, diagonal, apart }) {
                helper.assertTrue(press(helper, player, pos, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                                && isZipper(level.getBlockState(pos), Direction.NORTH, false),
                        "Could not place a single north zipper at " + pos);
            }
            helper.assertTrue(press(helper, player, roof, Direction.UP, false) == ZipperLeaseData.UseResult.CREATED
                            && isZipper(level.getBlockState(roof), Direction.UP, false),
                    "Could not place the zipper on top of the wall");

            // The port can zip a block right behind another zipper, as when digging down from an open floor
            // zipper; the donor's zipper cell cannot hold that, and the two stay independent.
            BlockPos stacked = apart.south();
            helper.assertTrue(press(helper, player, apart, Direction.NORTH, true) == ZipperLeaseData.UseResult.OPENED
                            && press(helper, player, stacked, Direction.NORTH, false)
                                    == ZipperLeaseData.UseResult.CREATED
                            && isZipper(level.getBlockState(stacked), Direction.NORTH, false),
                    "Could not zip the block right behind an open zipper");
            helper.assertTrue(isOpen(level, apart), "A zipper placed right behind an open one closed it");
            helper.assertTrue(press(helper, player, apart, Direction.NORTH, true) == ZipperLeaseData.UseResult.CLOSED
                            && press(helper, player, stacked, Direction.NORTH, true)
                                    == ZipperLeaseData.UseResult.OPENED
                            && !isOpen(level, apart) && isOpen(level, stacked),
                    "A zipper right behind another of its facing opened or closed with it");
            helper.assertTrue(press(helper, player, stacked, Direction.NORTH, false)
                            == ZipperLeaseData.UseResult.RESTORED,
                    "Could not remove the stacked zipper");

            helper.assertTrue(press(helper, player, first, Direction.NORTH, true) == ZipperLeaseData.UseResult.OPENED,
                    "A Shift press did not open the zipper");
            helper.assertTrue(isOpen(level, first) && isOpen(level, beside) && isOpen(level, diagonal),
                    "Touching zippers of one facing did not open together: beside=" + isOpen(level, beside)
                            + ", diagonal=" + isOpen(level, diagonal));
            helper.assertTrue(leases.getAt(beside).open() && leases.getAt(diagonal).open(),
                    "The leases of the zippers opened together still say closed");
            helper.assertTrue(!isOpen(level, apart) && !isOpen(level, roof),
                    "A zipper that does not touch the group, or faces another way, opened with it");

            helper.assertTrue(press(helper, player, diagonal, Direction.NORTH, true) == ZipperLeaseData.UseResult.CLOSED
                            && !isOpen(level, first) && !isOpen(level, beside) && !isOpen(level, diagonal),
                    "Touching zippers of one facing did not close together");

            helper.assertTrue(press(helper, player, beside, Direction.NORTH, true) == ZipperLeaseData.UseResult.OPENED
                            && press(helper, player, roof, Direction.UP, true) == ZipperLeaseData.UseResult.OPENED
                            && isOpen(level, first) && isOpen(level, diagonal) && isOpen(level, roof),
                    "The group or the roof zipper did not reopen");
            helper.assertTrue(press(helper, player, added, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                            && isZipper(level.getBlockState(added), Direction.NORTH, false),
                    "Could not place a zipper beside the open group");
            helper.assertTrue(!isOpen(level, first) && !isOpen(level, beside) && !isOpen(level, diagonal)
                            && !leases.getAt(first).open(),
                    "A new closed zipper did not close the open zippers of its facing that it touches");
            helper.assertTrue(isOpen(level, roof), "A new zipper closed a touching zipper that faces another way");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void openZipperIsTheSameHollowForAPhasingUser(GameTestHelper helper) {
        BlockPos pos = prepareShape(helper, zipper(Direction.NORTH, true));
        put(helper, pos.north(), Blocks.AIR.defaultBlockState());
        put(helper, pos.east(), Blocks.AIR.defaultBlockState());
        FakePlayer ordinary = fixture(helper, false);
        FakePlayer player = fixture(helper, true);
        try {
            ServerLevel level = helper.getLevel();
            locate(ordinary, pos, Shapes.block(), false, false);
            assertShape(helper, EAST_PLATE, query(helper, pos, ordinary),
                    "An open zipper is not the donor's hollow for an ordinary player");
            assertShape(helper, EAST_PLATE,
                    level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.empty()),
                    "An open zipper is not the donor's hollow without an entity");
            assertShape(helper, EAST_PLATE, level.getBlockState(pos).getCollisionShape(level, pos),
                    "An open zipper answers shape queries from a cache made without its neighbours");
            for (boolean grounded : new boolean[] { false, true }) {
                locate(player, pos, Shapes.block(), grounded, false);
                assertShape(helper, EAST_PLATE, query(helper, pos, player),
                        "A phasing user got another hollow than everyone else: grounded=" + grounded);
            }
            // The port can zip both walls of a one-block gap; neither zipper shuts the other's face.
            put(helper, pos.north(2), zipper(Direction.SOUTH, true));
            assertShape(helper, EAST_PLATE, query(helper, pos, ordinary),
                    "A zipper on the facing wall shut the open zipper's own face");
            put(helper, pos.north(2), Blocks.AIR.defaultBlockState());

            put(helper, pos.south(), Blocks.AIR.defaultBlockState());
            put(helper, pos, through(helper, zipper(Direction.NORTH, true)));
            assertShape(helper, EAST_PLATE, query(helper, pos, ordinary),
                    "An open through zipper is shut on one of its two faces");
            locate(player, pos, Shapes.block(), false, false);
            assertShape(helper, EAST_PLATE, query(helper, pos, player),
                    "An open through zipper is shut on one of its two faces for a phasing user");

            put(helper, pos, through(helper, zipper(Direction.NORTH, false)));
            assertShape(helper, Shapes.block(), query(helper, pos, ordinary),
                    "A closed through zipper is not solid for an ordinary player");
            assertShape(helper, Shapes.or(EAST_PLATE, NORTH_PLATE, SOUTH_PLATE), query(helper, pos, player),
                    "A closed through zipper is not shut on both of its faces for a phasing user");
            helper.succeed();
        }
        finally {
            close(ordinary);
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stackedOpenZippersJoinIntoADoorway(GameTestHelper helper) {
        BlockPos low = pillar(helper, Blocks.STONE.defaultBlockState());
        BlockPos high = low.above();
        put(helper, high, Blocks.STONE.defaultBlockState());
        put(helper, high.above(), Blocks.STONE.defaultBlockState());
        FakePlayer player = user(helper);
        FakePlayer walker = fixture(helper, false);
        try {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(press(helper, player, low, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                            && press(helper, player, high, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED,
                    "Could not draw two zippers on the one-block wall");
            double x = low.getX() + 0.5D;
            double y = low.getY();
            double z = low.getZ() + 0.5D;
            walker.setPos(x, y, z - 1.0D);
            walker.move(MoverType.SELF, new Vec3(0.0D, 0.0D, 2.0D));
            helper.assertTrue(walker.getZ() < low.getZ(), "A player walked through two closed zippers");

            helper.assertTrue(press(helper, player, low, Direction.NORTH, true) == ZipperLeaseData.UseResult.OPENED
                            && isOpen(level, low) && isOpen(level, high),
                    "One Shift press did not open the doorway of two zippers");
            walker.setPos(x, y, z - 1.0D);
            walker.move(MoverType.SELF, new Vec3(0.0D, 0.0D, 2.0D));
            helper.assertTrue(Math.abs(walker.getZ() - (z + 1.0D)) < 1.0E-6D && !walker.horizontalCollision,
                    "A player could not walk through a doorway of two open zippers: z=" + walker.getZ());
            walker.setPos(x, y, z);
            walker.move(MoverType.SELF, new Vec3(1.0D, 0.0D, 0.0D));
            helper.assertTrue(walker.getBoundingBox().maxX <= low.getX() + 1.0D,
                    "A player left the doorway through a side that has no zipper: x=" + walker.getX());
            helper.assertTrue(!walker.isInWall(), "A player inside the doorway suffocates");
            helper.succeed();
        }
        finally {
            close(player);
            close(walker);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void zipperBehindAnOpenDoorwayLeavesItOpen(GameTestHelper helper) {
        // A wall four blocks thick, so every zipper on it is a single one.
        fill(helper, new BlockPos(1, 2, 1), new BlockPos(3, 5, 5), Blocks.AIR.defaultBlockState());
        fill(helper, new BlockPos(1, 2, 2), new BlockPos(3, 4, 5), Blocks.STONE.defaultBlockState());
        BlockPos low = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos high = low.above();
        BlockPos behindLow = low.south();
        BlockPos behindHigh = high.south();
        FakePlayer player = user(helper);
        try {
            ServerLevel level = helper.getLevel();
            ZipperLeaseData leases = ZipperLeaseData.get(level);
            helper.assertTrue(press(helper, player, low, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                            && press(helper, player, high, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                            && press(helper, player, low, Direction.NORTH, true) == ZipperLeaseData.UseResult.OPENED
                            && isOpen(level, low) && isOpen(level, high),
                    "Could not open a doorway of two zippers in the thick wall");

            for (BlockPos behind : new BlockPos[] { behindHigh, behindLow }) {
                helper.assertTrue(press(helper, player, behind, Direction.NORTH, false)
                                == ZipperLeaseData.UseResult.CREATED
                                && isZipper(level.getBlockState(behind), Direction.NORTH, false),
                        "Could not zip the block behind the open doorway at " + behind);
                helper.assertTrue(isOpen(level, low) && isOpen(level, high),
                        "A zipper placed behind an open doorway closed it: low=" + isOpen(level, low)
                                + ", high=" + isOpen(level, high));
                helper.assertTrue(leases.getAt(low).open() && leases.getAt(high).open(),
                        "The leases of the doorway say closed after zipping the block behind it");
            }

            helper.assertTrue(press(helper, player, behindHigh, Direction.NORTH, true)
                            == ZipperLeaseData.UseResult.OPENED
                            && isOpen(level, behindHigh) && !isOpen(level, behindLow)
                            && isOpen(level, low) && isOpen(level, high),
                    "A Shift press on a zipper behind the doorway did not open that zipper alone");
            helper.assertTrue(press(helper, player, low, Direction.NORTH, true) == ZipperLeaseData.UseResult.CLOSED
                            && !isOpen(level, low) && !isOpen(level, high),
                    "One Shift press did not close the doorway of two zippers");
            helper.assertTrue(isOpen(level, behindHigh) && !isOpen(level, behindLow),
                    "A Shift press on the doorway toggled a zipper behind it");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void zipperUnderAnOpenFloorPitLeavesItOpen(GameTestHelper helper) {
        // Four stone layers, y 1..4, so every zipper in the floor is a single one.
        fill(helper, new BlockPos(1, 1, 1), new BlockPos(3, 4, 5), Blocks.STONE.defaultBlockState());
        fill(helper, new BlockPos(1, 5, 1), new BlockPos(3, 8, 5), Blocks.AIR.defaultBlockState());
        BlockPos first = helper.absolutePos(new BlockPos(1, 4, 2));
        BlockPos second = first.east();
        BlockPos under = first.below();
        FakePlayer player = user(helper);
        try {
            ServerLevel level = helper.getLevel();
            ZipperLeaseData leases = ZipperLeaseData.get(level);
            helper.assertTrue(press(helper, player, first, Direction.UP, false) == ZipperLeaseData.UseResult.CREATED
                            && press(helper, player, second, Direction.UP, false) == ZipperLeaseData.UseResult.CREATED
                            && press(helper, player, first, Direction.UP, true) == ZipperLeaseData.UseResult.OPENED
                            && isOpen(level, first) && isOpen(level, second),
                    "Could not open a floor pit of two zippers");
            helper.assertTrue(press(helper, player, under, Direction.UP, false) == ZipperLeaseData.UseResult.CREATED
                            && isZipper(level.getBlockState(under), Direction.UP, false),
                    "Could not zip the block under one half of the open pit");
            helper.assertTrue(isOpen(level, first) && isOpen(level, second),
                    "A zipper placed under an open floor pit closed it: first=" + isOpen(level, first)
                            + ", second=" + isOpen(level, second));
            helper.assertTrue(leases.getAt(first).open() && leases.getAt(second).open(),
                    "The leases of the pit say closed after zipping the block under it");
            helper.assertTrue(press(helper, player, under, Direction.UP, true) == ZipperLeaseData.UseResult.OPENED
                            && isOpen(level, under),
                    "A Shift press did not open the zipper under the pit");
            helper.assertTrue(press(helper, player, second, Direction.UP, true) == ZipperLeaseData.UseResult.CLOSED
                            && !isOpen(level, first) && !isOpen(level, second) && isOpen(level, under),
                    "One Shift press did not close the pit, or it toggled the zipper under it");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inGroundUserGetsASecondExitZipper(GameTestHelper helper) {
        // The user stands in the open zipper at (2, 2, 2) and zips the block south of it; the cell behind is free.
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos hollow = pillar(helper, zipper(Direction.NORTH, true));
        BlockPos target = hollow.south();
        BlockPos exit = target.south();
        put(helper, target, stone);
        put(helper, target.above(), stone);
        FakePlayer player = user(helper);
        try {
            ServerLevel level = helper.getLevel();
            ZipperLeaseData leases = ZipperLeaseData.get(level);
            StandPower power = StandPower.get(player);
            power.setStamina(power.getMaxStamina());
            stand(player, hollow);
            ZipperInGround.tick(player);
            helper.assertTrue(ZipperInGround.isInGround(player), "The user in the open zipper is not in the ground");

            helper.assertTrue(press(helper, player, target, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                            && isZipper(level.getBlockState(target), Direction.NORTH, false)
                            && isThrough(level.getBlockState(target)),
                    "Could not zip the block ahead through to the free cell behind it");
            helper.assertTrue(isZipper(level.getBlockState(target.above()), Direction.SOUTH, false)
                            && !isThrough(level.getBlockState(target.above()))
                            && leases.getAt(target.above()) != null,
                    "An in-ground user did not get the second exit zipper above the first: "
                            + level.getBlockState(target.above()));
            helper.assertTrue(level.getBlockState(target.below()).equals(stone),
                    "The second exit zipper went below although the cell under the exit is not free");
            helper.assertTrue(isOpen(level, hollow), "Zipping onward closed the zipper the user stands in");
            helper.assertTrue(press(helper, player, target, Direction.NORTH, true) == ZipperLeaseData.UseResult.OPENED
                            && isOpen(level, target) && isOpen(level, target.above()),
                    "One Shift press did not open the two-high exit");
            remove(helper, player, target, Direction.NORTH);
            remove(helper, player, target.above(), Direction.SOUTH);

            // The donor looks under the exit cell first.
            put(helper, exit.below(), air);
            helper.assertTrue(press(helper, player, target, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                            && isZipper(level.getBlockState(target.below()), Direction.SOUTH, false)
                            && level.getBlockState(target.above()).equals(stone),
                    "The second exit zipper did not go below when the cell under the exit is free: below="
                            + level.getBlockState(target.below()) + ", above=" + level.getBlockState(target.above()));
            remove(helper, player, target, Direction.NORTH);
            remove(helper, player, target.below(), Direction.SOUTH);
            put(helper, exit.below(), stone);

            // Free space on the user's side makes the donor's linked pair, which has no second zipper.
            put(helper, hollow, air);
            assertNoSecondExit(helper, player, target, "A zipper with a free cell in front of it got a second zipper");
            put(helper, hollow, stone);
            assertNoSecondExit(helper, player, target,
                    "A zipper with free space two cells in front of it got a second zipper");

            put(helper, hollow.north(), stone);
            helper.assertTrue(press(helper, player, target, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                            && isZipper(level.getBlockState(target.above()), Direction.SOUTH, false),
                    "A user deep in the ground did not get the second exit zipper: "
                            + level.getBlockState(target.above()));
            remove(helper, player, target, Direction.NORTH);
            remove(helper, player, target.above(), Direction.SOUTH);

            stand(player, hollow.above(2));
            ZipperInGround.tick(player);
            helper.assertTrue(!ZipperInGround.isInGround(player), "The user in the open air is still in the ground");
            assertNoSecondExit(helper, player, target, "A user who is not in the ground got a second zipper");
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mobsDoNotPathThroughAnOpenZipper(GameTestHelper helper) {
        // Two closed rooms, (2, 2..3, 2) and (2, 2..3, 4), joined only by the two-high doorway at (2, 2..3, 3).
        fill(helper, new BlockPos(1, 1, 1), new BlockPos(3, 4, 5), Blocks.STONE.defaultBlockState());
        fill(helper, new BlockPos(2, 2, 2), new BlockPos(2, 3, 4), Blocks.AIR.defaultBlockState());
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos door = start.south();
        BlockPos goal = door.south();
        ServerLevel level = helper.getLevel();
        Mob mob = EntityType.ZOMBIE.create(level);
        helper.assertTrue(mob != null, "Could not create the pathfinding mob");
        mob.setPos(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        mob.setOnGround(true);

        Path free = mob.getNavigation().createPath(goal, 0);
        helper.assertTrue(free != null && free.canReach(), "The mob found no path through the empty doorway");

        BlockState closed = through(helper, zipper(Direction.NORTH, false));
        put(helper, door, closed);
        put(helper, door.above(), closed);
        Path shut = mob.getNavigation().createPath(goal, 0);
        helper.assertTrue(shut == null || !shut.canReach(), "The mob found a path through closed zippers");

        BlockState open = through(helper, zipper(Direction.NORTH, true));
        put(helper, door, open);
        put(helper, door.above(), open);
        Path hollow = mob.getNavigation().createPath(goal, 0);
        helper.assertTrue(hollow == null || !hollow.canReach(),
                "The mob found a path through the block behind an open zipper");
        helper.assertTrue(!open.isPathfindable(PathComputationType.LAND),
                "An open zipper is walkable ground for mob pathfinding");
        mob.discard();
        helper.succeed();
    }

    // 1.16 had no Sticky Fingers stamina condition: StandAction.onPerform and StandEntityTask paid every cost with
    // the result ignored, and StandPower.consumeStamina drained a short bar to 0.
    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void zipUserWoundsClosesOnAShortBarAndDrainsIt(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 2.5D), 0.0F, 0.0F);
        StandPower power = StandPower.get(player);
        float[] performed = {-1.0F};
        Consumer<EntityTickEvent.Post> watch = watchStamina(power,
                () -> player.hasEffect(StickyFingersAddon.ZIP_WOUNDS_EFFECT), performed);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(watch);
            closeStaminaUser(player);
        };
        try {
            power.setStamina(0.0F);
            for (String name : new String[] {
                    StickyFingersAddon.UNZIP_PUNCH_NAME, StickyFingersAddon.EXTENDED_PUNCH_NAME }) {
                assertAdmitted(helper, power, name);
            }
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        startWhenTheSceneTicks(helper, player, close, () -> {
            power.setStamina(100.0F);
            pressStickyAbility(helper, player, StickyFingersAddon.ZIP_USER_WOUNDS_NAME);
        }, () -> assertPerformedOnAnEmptyBar(helper, performed,
                "1.16 zipped the user's wounds whatever the stamina, the 100 bar refused it", 250));
    }

    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void extendedPunchFiresOnAShortBarAndDrainsIt(GameTestHelper helper) {
        // High above the cell and aimed straight up, so the forearm reaches no neighbouring test.
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 12.0D, 2.5D), 0.0F, -90.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        List<Float> staminaAtSpawn = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> spawn = event -> {
            if (event.getEntity() instanceof StickyFingersExtendedPunchEntity punch && punch.getOwner() == stand) {
                staminaAtSpawn.add(power.getStamina());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, spawn);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(spawn);
            for (Entity punch : helper.getLevel().getEntities((Entity) null, player.getBoundingBox().inflate(48.0D),
                    entity -> entity instanceof StickyFingersExtendedPunchEntity)) {
                punch.discard();
            }
            closeStaminaUser(player);
        };
        startWhenTheSceneTicks(helper, player, close, () -> {
            stand.setXRot(-90.0F);
            power.setStamina(100.0F);
            pressStickyAbility(helper, player, StickyFingersAddon.EXTENDED_PUNCH_NAME);
        }, () -> {
            helper.assertTrue(staminaAtSpawn.size() == 1,
                    "1.16 sent one forearm out whatever the stamina, got " + staminaAtSpawn.size());
            helper.assertTrue(staminaAtSpawn.get(0) == 100.0F,
                    "the forearm must be out before the cost is paid: stamina at its spawn " + staminaAtSpawn);
            helper.assertTrue(power.getStamina() == 0.0F,
                    "the 375 cost drains a 100 bar to 0, the bar holds " + power.getStamina());
        });
    }

    // 1.16 sticky_fingers_extended_punch standOffsetFront(): (left, up, forward) (0, 0.2, 0.5) from the user's feet,
    // turned by the view yaw.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void extendedPunchPutsTheStandWhereTheDonorDid(GameTestHelper helper) {
        // High above the cell and aimed straight up, so the forearm reaches no neighbouring test.
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 12.0D, 2.5D), 0.0F, -90.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        try {
            player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            stand.updatePosition(player);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Setup: Sticky Fingers is not a full Stand following its user");
            stand.setXRot(-90.0F);
            pressStickyAbility(helper, player, StickyFingersAddon.EXTENDED_PUNCH_NAME);
            // the body is turned away from the view: a body-yaw offset misses
            turnStickyUser(player, 0.0F, 50.0F);
            assertStickyStandAt(helper, player, stand, 0.0D, 0.2D, 0.5D, "view yaw 0, body yaw 50");
            // facing west the forward is -x
            turnStickyUser(player, 90.0F, 0.0F);
            assertStickyStandAt(helper, player, stand, -0.5D, 0.2D, 0.0D, "view yaw 90, body yaw 0");
        }
        finally {
            for (Entity punch : helper.getLevel().getEntities((Entity) null, player.getBoundingBox().inflate(48.0D),
                    entity -> entity instanceof StickyFingersExtendedPunchEntity)) {
                punch.discard();
            }
            closeStaminaUser(player);
        }
        helper.succeed();
    }

    private static void turnStickyUser(FakePlayer user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertStickyStandAt(GameTestHelper helper, FakePlayer user, StandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put Sticky Fingers punching its forearm out at its user's feet + (" + dx + ", " + dy + ", "
                        + dz + ") with " + when + ", got " + actual + " (offset "
                        + stand.offsetFromUser.getRelativeOffset() + " " + stand.offsetFromUser.getRotations() + ")");
    }

    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void zipTargetWoundsClosesOnAShortBarAndDrainsIt(GameTestHelper helper) {
        // Facing south at a pig floating at eye height, out of the Stand's idle spot behind the user.
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        StandPower power = StandPower.get(player);
        Mob pig = EntityType.PIG.create(helper.getLevel());
        float[] performed = {-1.0F};
        Consumer<EntityTickEvent.Post> watch = watchStamina(power,
                () -> pig != null && pig.hasEffect(StickyFingersAddon.ZIP_WOUNDS_EFFECT), performed);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(watch);
            if (pig != null) pig.discard();
            closeStaminaUser(player);
        };
        try {
            helper.assertTrue(pig != null, "Could not create the wound target");
            Vec3 at = helper.absoluteVec(new Vec3(2.5D, 4.0D, 2.5D));
            pig.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            pig.setNoAi(true);
            pig.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Could not add the wound target");
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        startWhenTheSceneTicks(helper, player, close, () -> {
            power.setStamina(100.0F);
            pressStickyAbility(helper, player, StickyFingersAddon.ZIP_TARGET_WOUNDS_NAME);
        }, () -> assertPerformedOnAnEmptyBar(helper, performed,
                "1.16 zipped the target's wounds whatever the stamina, the 100 bar refused it", 250));
    }

    // 1.16 sticky_fingers_zip_target_wounds was a StandAction, whose checkTarget refused only the user: pressed at the
    // user's own tamed wolf it zipped the wolf's wounds (070 StickyFingersZipTargetWounds.perform). The action keeps the
    // target it was pressed at (core K5-26 C, takesPressedTarget), whatever the user looks at by its perform.
    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void zipTargetWoundsTakesTheUsersOwnPetAsIn116(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        boolean[] hookTakesIt = {false};
        Runnable close = () -> {
            if (wolf != null) wolf.discard();
            closeStaminaUser(player);
        };
        try {
            helper.assertTrue(wolf != null, "Could not create the user's wolf");
            Vec3 at = helper.absoluteVec(new Vec3(2.5D, 3.0D, 2.5D));
            wolf.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            wolf.setNoAi(true);
            wolf.setNoGravity(true);
            wolf.setTame(true, false);
            wolf.setOwnerUUID(player.getUUID());
            helper.assertTrue(helper.getLevel().addFreshEntity(wolf), "Could not add the user's wolf");
            helper.assertTrue(wolf.getOwner() == player && !stand.canAttackEntity(wolf),
                    "fixture: the wolf is not the user's own pet that the Stand may not attack");
            hookTakesIt[0] = power.getAbility(StickyFingersAddon.ZIP_TARGET_WOUNDS_NAME)
                    instanceof StandEntityAbility standAbility
                    && standAbility.takesPressedTarget(stand, new ActionTarget(wolf));
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        startWhenTheSceneTicks(helper, player, close, () -> {
            LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(wolf));
            power.setStamina(power.getMaxStamina());
            pressStickyAbility(helper, player, StickyFingersAddon.ZIP_TARGET_WOUNDS_NAME);
            // before the Stand performs, the user looks up at nothing: only the press target can name the wolf
            LivingComponentAction.getComponent(player).entityAim.setTarget(ActionTarget.EMPTY);
            player.setXRot(-90.0F);
            player.xRotO = -90.0F;
        }, () -> helper.assertTrue(wolf.hasEffect(StickyFingersAddon.ZIP_WOUNDS_EFFECT),
                "1.16 zipped the wounds of the user's own wolf the press was aimed at (a StandAction's checkTarget"
                        + " refused only the user); the wolf has none, the press target taken " + hookTakesIt[0]));
    }

    // 1.16 sticky_fingers_zip_target_wounds was a StandAction (TargetRequirement.ENTITY) with no
    // overrideVanillaMouseTarget, which only a StandEntityAction had: pressed at a torch with a pig behind it, the
    // target stayed the torch, no entity, and PowerBaseImpl.checkTarget refused the press; the pig kept no wounds. The
    // action's own perform, run on that press (a buffered press that starts later, owner N2), zips nothing either and
    // costs no stamina; pressed straight at the pig it zips the pig.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void zipTargetWoundsPressedAtATorchKeepsTheTorchAsIn116(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        BlockPos torch = BlockPos.containing(player.getEyePosition()).south();
        Mob pig = EntityType.PIG.create(helper.getLevel());
        Runnable close = () -> {
            if (pig != null) pig.discard();
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            closeStaminaUser(player);
        };
        try {
            helper.getLevel().setBlock(torch, Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, Direction.NORTH), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.assertTrue(pig != null, "Could not create the pig");
            pig.moveTo(player.getX(), player.getEyeY() - pig.getBbHeight() / 2.0D, player.getZ() + 2.0D, 0.0F, 0.0F);
            pig.setNoAi(true);
            pig.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Could not add the pig");
            helper.assertTrue(helper.getLevel().getBlockState(torch).getCollisionShape(helper.getLevel(), torch).isEmpty(),
                    "fixture: no torch without a collision shape at " + torch);
            ActionTarget pressedAt = new ActionTarget(torch, Direction.NORTH);
            Ability ability = power.getAbility(StickyFingersAddon.ZIP_TARGET_WOUNDS_NAME);
            helper.assertTrue(ability instanceof StandEntityAbility standAbility
                            && standAbility.overridePressedTarget(player, pressedAt) == pressedAt,
                    "1.16's StandAction kept the torch it was pressed at; the port's press rule lets it give way");
            LivingComponentAction.getComponent(player).entityAim.setTarget(pressedAt);
            power.setStamina(power.getMaxStamina());
            ConditionCheck check = ability.checkSpecificConditions(power);
            helper.assertTrue(!check.isPositive(), "1.16 refused the press at a torch with a pig behind it (no entity"
                    + " under the crosshair); the port took it");

            float before = power.getStamina();
            performZipTargetWounds(helper, player, stand, ability);
            helper.assertTrue(!pig.hasEffect(StickyFingersAddon.ZIP_WOUNDS_EFFECT),
                    "1.16 zipped no wounds of a pig behind the pressed torch; the port's perform zipped them");
            helper.assertTrue(power.getStamina() == before, "The perform on a press at a torch zipped nothing but"
                    + " took " + (before - power.getStamina()) + " stamina");

            LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(pig));
            helper.assertTrue(ability.checkSpecificConditions(power).isPositive(),
                    "fixture: the press straight at the pig was refused");
            performZipTargetWounds(helper, player, stand, ability);
            helper.assertTrue(pig.hasEffect(StickyFingersAddon.ZIP_WOUNDS_EFFECT) && power.getStamina() < before,
                    "fixture: the perform on a press straight at the pig did not zip it (stamina " + before + " -> "
                            + power.getStamina() + ")");
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        close.run();
        helper.succeed();
    }

    // The press as the server sets it on the Stand (onActionSet takes the press target from the user's aim), then its
    // one perform tick.
    private static void performZipTargetWounds(GameTestHelper helper, FakePlayer player, StandEntity stand,
            Ability ability) {
        helper.assertTrue(ability instanceof EntityActionType, "fixture: zip_target_wounds is no entity action");
        EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(
                helper.getLevel(), player, stand, null);
        LivingComponentAction stands = LivingComponentAction.getComponent(stand);
        stands.setAction(action, player, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action, "fixture: zip_target_wounds did not start");
        action.actionPerformStart();
        stands.setAction(null, SyncType.NO_SYNC);
    }

    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void climbZipperGripsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        // Four chunks east of this template's, past the grid's other templates (its rows span 42 blocks): a chunk only
        // the scene holds, which ticks entities only once the chunk workers have readied it, never in the tick the scene
        // is built. Facing north 0.2 blocks from a two-high zipper wall, within the 0.5 reach, its zippers facing the
        // user.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos away = new ChunkPos((origin.getX() >> 4) + 4, origin.getZ() >> 4);
        BlockPos scene = helper.relativePos(new BlockPos(away.getMinBlockX() + 6, origin.getY(), away.getMinBlockZ() + 4));
        fill(helper, scene.offset(1, 3, 1), scene.offset(3, 5, 5), Blocks.AIR.defaultBlockState());
        put(helper, helper.absolutePos(scene.offset(2, 3, 2)), zipper(Direction.SOUTH, false));
        put(helper, helper.absolutePos(scene.offset(2, 4, 2)), zipper(Direction.SOUTH, false));
        FakePlayer player = staminaUser(helper, Vec3.atLowerCornerOf(scene).add(2.5D, 3.0D, 3.5D), 180.0F, 0.0F);
        StandPower power = StandPower.get(player);
        float[] performed = {-1.0F};
        Consumer<EntityTickEvent.Post> watch = watchStamina(power,
                () -> StickyFingersClimbZipperAbility.climbWall(player) == Direction.NORTH, performed);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(watch);
            StickyFingersClimbZipperAbility.stopClimbing(player);
            closeStaminaUser(player);
        };
        startWhenTheSceneTicks(helper, player, close, () -> {
            power.setStamina(100.0F);
            pressStickyAbility(helper, player, StickyFingersAddon.CLIMB_ZIPPER_NAME);
        }, () -> assertPerformedOnAnEmptyBar(helper, performed,
                "1.16 put the user on the zipper wall whatever the stamina, the 100 bar refused it", 250));
    }

    // 1.16 holdToFire(30, false) carried no walk code, and the owner rule of 2026-10-15 lets only a Stand's own guard
    // slow its user: the hide in a mob keeps full walk speed through the charge and the entry.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void hideInMobKeepsFullWalkSpeed(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        ServerLevel level = helper.getLevel();
        Mob chicken = EntityType.CHICKEN.create(level);
        Runnable close = () -> {
            if (chicken != null) {
                var possessors = LivingComponentPossession.getEntitiesPossessing(chicken);
                if (possessors != null) {
                    for (LivingComponentPossession possession : new ArrayList<>(possessors)) {
                        possession.stopPossession();
                    }
                }
                chicken.discard();
            }
            closeStaminaUser(player);
        };
        try {
            helper.assertTrue(chicken != null, "Could not create the hide target");
            Vec3 at = helper.absoluteVec(new Vec3(2.5D, 3.0D, 2.5D));
            chicken.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            chicken.setNoAi(true);
            chicken.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(chicken), "Could not add the hide target");
            LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(chicken));
            power.setStamina(100.0F);
            pressStickyAbility(helper, player, StickyFingersAddon.HIDE_IN_MOB_NAME);
            EntityActionInstance action = LivingComponentAction.getComponent(stand).getAction();
            helper.assertTrue(action instanceof StickyFingersHideInMobAbility.HideInMobAction,
                    "the hide in a mob press started " + action);
            helper.assertTrue(action.getPhase() == ActionPhase.BUTTON_CHARGE && action.userWalkSpeed == 1.0F,
                    "the hide charge slowed the user: phase " + action.getPhase() + " walk speed "
                            + action.userWalkSpeed);
            int ticks = 0;
            while (!action.isOver() && ticks < 60) {
                action._tickAction();
                ticks++;
                helper.assertTrue(action.userWalkSpeed == 1.0F,
                        "the hide charge slowed the user at tick " + (ticks - 1) + ": " + action.userWalkSpeed);
            }
            helper.assertTrue(action.isOver() && ticks == 30,
                    "the hide charge fires in its 30th tick as in 1.16: over " + action.isOver() + " after " + ticks);
            // its 1-tick perform starts and ends inside that tick, so the entry is checked by its walk speed (the
            // registered factor every phase but the recovery takes) and by the user hiding in the mob
            float entryWalkSpeed = action.ability instanceof StandEntityAbility hide
                    ? hide.getStandUserWalkSpeed() : 0.0F;
            helper.assertTrue(entryWalkSpeed == 1.0F,
                    "the hide in a mob entry froze the user: walk speed " + entryWalkSpeed);
            helper.assertTrue(LivingComponentPossession.getEntityPossessedBy(player) == chicken,
                    "the hide in a mob did not enter the mob when the charge fired");
        }
        catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
        close.run();
        helper.succeed();
    }

    // 1.16 sticky_fingers_hide_in_mob (TargetRequirement.ENTITY, holdToFire(30, false)): tickHeldAction re-ran
    // checkRequirements on the crosshair every held tick. A crosshair off the mob stopped the charge silently, a mob
    // behind a wall past 4 blocks of the performer stopped it with target_too_far; nothing was entered then.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void hideInMobStopsWhenTheAimLeavesTheMob(GameTestHelper helper) {
        fill(helper, new BlockPos(0, 2, 3), new BlockPos(5, 6, 3), Blocks.STONE.defaultBlockState());
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        ServerLevel level = helper.getLevel();
        Mob near = EntityType.CHICKEN.create(level);
        Mob walled = EntityType.CHICKEN.create(level);
        Runnable close = () -> {
            for (Mob chicken : new Mob[] { near, walled }) {
                if (chicken != null) {
                    var possessors = LivingComponentPossession.getEntitiesPossessing(chicken);
                    if (possessors != null) {
                        for (LivingComponentPossession possession : new ArrayList<>(possessors)) {
                            possession.stopPossession();
                        }
                    }
                    chicken.discard();
                }
            }
            closeStaminaUser(player);
        };
        try {
            helper.assertTrue(near != null && walled != null, "Could not create the hide targets");
            Vec3 nearAt = helper.absoluteVec(new Vec3(2.5D, 3.0D, 2.0D));
            Vec3 walledAt = helper.absoluteVec(new Vec3(2.5D, 3.0D, 5.4D));
            near.moveTo(nearAt.x, nearAt.y, nearAt.z, 0.0F, 0.0F);
            walled.moveTo(walledAt.x, walledAt.y, walledAt.z, 0.0F, 0.0F);
            for (Mob chicken : new Mob[] { near, walled }) {
                chicken.setNoAi(true);
                chicken.setNoGravity(true);
                helper.assertTrue(level.addFreshEntity(chicken), "Could not add a hide target");
            }
            Ability hide = power.getAbility(StickyFingersAddon.HIDE_IN_MOB_NAME);
            helper.assertTrue(hide instanceof StickyFingersHideInMobAbility, "Sticky Fingers has no hide in a mob");

            EntityActionInstance charge = startHideCharge(helper, player, stand, hide, near);
            aimUser(player, ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).tick();
            assertHideDropped(helper, stand, charge, near, "the crosshair left the chicken");

            charge = startHideCharge(helper, player, stand, hide, near);
            aimUser(player, new ActionTarget(walled));
            ConditionCheck walledCheck = ((StickyFingersHideInMobAbility) hide).checkHeldActionConditions(charge, power);
            helper.assertTrue(!walledCheck.isPositive() && !walledCheck.shouldContinueHold()
                            && walledCheck.getWarning() != null
                            && walledCheck.getWarning().getContents() instanceof TranslatableContents contents
                            && contents.getKey().equals("jojo.message.action_condition.target_too_far"),
                    "1.16 Action.checkRangeAndTarget stopped the charge with target_too_far at a chicken behind a"
                            + " wall past 4 blocks, got " + (walledCheck.isPositive() ? "a pass"
                                    : walledCheck.getWarning() != null ? walledCheck.getWarning().getString()
                                            : "a silent refusal"));
            LivingComponentAction.getComponent(stand).tick();
            assertHideDropped(helper, stand, charge, walled, "the crosshair went to a chicken behind a wall");

            charge = startHideCharge(helper, player, stand, hide, near);
            for (int tick = 0; tick < 40 && !charge.isOver(); tick++) {
                LivingComponentAction.getComponent(stand).tick();
            }
            var possessors = LivingComponentPossession.getEntitiesPossessing(near);
            helper.assertTrue(possessors != null && !possessors.isEmpty(),
                    "a charge kept on the chicken must hide the user in it");
        }
        catch (RuntimeException | Error failure) {
            AbilityInput.keyRelease(HIDE_KEY, player);
            close.run();
            throw failure;
        }
        AbilityInput.keyRelease(HIDE_KEY, player);
        close.run();
        helper.succeed();
    }

    private static final short HIDE_KEY = 47;

    private static EntityActionInstance startHideCharge(GameTestHelper helper, FakePlayer player, StandEntity stand,
            Ability hide, Mob target) {
        AbilityInput.keyRelease(HIDE_KEY, player);
        StandPower power = StandPower.get(player);
        aimUser(player, new ActionTarget(target));
        assertAdmitted(helper, power, StickyFingersAddon.HIDE_IN_MOB_NAME);
        BufferingState buffering = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(HIDE_KEY, hide, player, null, InputMethod.HOLD, 0.0F, buffering, hide.getAbilityId());
        EntityActionInstance action = LivingComponentAction.getComponent(stand).getAction();
        helper.assertTrue(buffering.isActionSuccess
                        && action instanceof StickyFingersHideInMobAbility.HideInMobAction
                        && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                "the hide in a mob press did not start its charge: " + action);
        for (int tick = 0; tick < 3; tick++) {
            LivingComponentAction.getComponent(stand).tick();
        }
        helper.assertTrue(!action.isOver() && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                "1.16 kept charging while the crosshair stayed on the chicken: phase " + action.getPhase());
        return action;
    }

    private static void assertHideDropped(GameTestHelper helper, StandEntity stand, EntityActionInstance charge,
            Mob target, String when) {
        var possessors = LivingComponentPossession.getEntitiesPossessing(target);
        helper.assertTrue(charge.isOver() && LivingComponentAction.getComponent(stand).getAction() == null
                        && (possessors == null || possessors.isEmpty()),
                "1.16 tickHeldAction stopped the hide charge when " + when + ": over " + charge.isOver()
                        + ", phase " + charge.getPhase() + ", hidden " + (possessors != null && !possessors.isEmpty()));
    }

    // the user's aim, as its client sent it
    private static void aimUser(FakePlayer player, ActionTarget target) {
        LivingComponentAction.getComponent(player).entityAim.setTarget(target);
    }

    // 1.16 StickyFingersGetInsideMob (TargetRequirement.ENTITY, 070 :41-43): the HUD (ActionsOverlayGui
    // .actionAvailability) and the client's press (PowerBaseImpl.checkRequirements) checked it at the client's mouse
    // target. While local the user stands in for its own client's copy: isLocalPlayer, no stored aim, the aim it sends
    // in LivingComponentAction.CLIENT_PLAYER_AIM.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void hideInMobOnTheUsersOwnClientSeesTheAimItSendsAsIn116(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        StickyUser user = (StickyUser) player;
        StandPower power = StandPower.get(player);
        Mob chicken = EntityType.CHICKEN.create(helper.getLevel());
        try {
            helper.assertTrue(chicken != null, "Could not create the hide target");
            Vec3 at = helper.absoluteVec(new Vec3(2.5D, 3.0D, 2.5D));
            chicken.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            chicken.setNoAi(true);
            chicken.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(chicken), "Could not add the hide target");
            Ability hide = power.getAbility(StickyFingersAddon.HIDE_IN_MOB_NAME);
            helper.assertTrue(hide instanceof StickyFingersHideInMobAbility, "Sticky Fingers has no hide in a mob");
            power.setStamina(power.getMaxStamina());

            aimUser(player, new ActionTarget(chicken));
            ConditionCheck server = hide.checkConditions(power);
            helper.assertTrue(server.isPositive(), "fixture: on the server the hide in a mob at the chicken is "
                    + describeCheck(server));
            aimUser(player, ActionTarget.EMPTY);
            helper.assertTrue(!hide.checkConditions(power).isPositive(),
                    "fixture: the hide in a mob is usable with no aim, so its check needs none");
            user.local = true;
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(new ActionTarget(chicken));
            ConditionCheck client = hide.checkConditions(power);
            helper.assertTrue(client.isPositive(), "1.16's client checked the hide in a mob at its mouse target, the"
                    + " chicken, and showed it usable as the server took it; the port's own client finds it "
                    + describeCheck(client) + ", reading no aim of its copy of the user");
        }
        finally {
            user.local = false;
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
            if (chicken != null) {
                chicken.discard();
            }
            closeStaminaUser(player);
        }
        helper.succeed();
    }

    private static String describeCheck(ConditionCheck check) {
        if (check.isPositive()) {
            return "usable";
        }
        return check.getWarning() == null ? "refused without a message"
                : "refused (" + (check.getWarning().getContents() instanceof TranslatableContents contents
                        ? contents.getKey() : check.getWarning().getString()) + ")";
    }

    // 1.16 place_zipper (holdType): tickHeldAction first asked canUsePower and stopped the hold of a user who could
    // no longer use the power (a spectator), so its holdTick zipped no further block.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void heldZipperStopsWhenTheUserBecomesASpectator(GameTestHelper helper) {
        fill(helper, new BlockPos(0, 2, 3), new BlockPos(5, 6, 3), Blocks.STONE.defaultBlockState());
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        try {
            player.setGameMode(GameType.SURVIVAL);
            StandPower power = StandPower.get(player);
            Ability zipper = power.getAbility(StickyFingersAddon.ZIPPER_NAME);
            helper.assertTrue(zipper != null, "Sticky Fingers has no zipper");
            BlockPos pressed = helper.absolutePos(new BlockPos(2, 4, 3));
            BlockPos swept = helper.absolutePos(new BlockPos(1, 4, 3));
            BlockPos afterSpectator = helper.absolutePos(new BlockPos(3, 4, 3));
            BufferingState buffering = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(HIDE_KEY, zipper, player, null, InputMethod.HOLD, 0.0F, buffering,
                    zipper.getAbilityId());
            helper.assertTrue(isZipper(helper, pressed), "the held zipper press did not zip the stone it faced");
            turnStickyUser(player, 20.0F, 20.0F);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            helper.assertTrue(isZipper(helper, swept),
                    "the held zipper did not zip the next stone the crosshair swept over");
            player.setGameMode(GameType.SPECTATOR);
            turnStickyUser(player, -20.0F, -20.0F);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            helper.assertTrue(!isZipper(helper, afterSpectator),
                    "1.16 tickHeldAction stopped the zipper hold of a spectator (canUsePower), it zipped on");
        }
        finally {
            AbilityInput.keyRelease(HIDE_KEY, player);
            closeStaminaUser(player);
        }
        helper.succeed();
    }

    // 1.16 place_zipper (070 InitStands :87, a held StandAction) kept heldAllowsOtherAction false, so while its key was
    // down PowerBaseImpl.checkRequirements (:255) refused the power's other actions: a Stand punch and the user's zip
    // wounds alike. Letting go of the key ended that.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void heldZipperRefusesTheStandsOtherActionsAsIn116(GameTestHelper helper) {
        fill(helper, new BlockPos(0, 2, 3), new BlockPos(5, 6, 3), Blocks.STONE.defaultBlockState());
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        List<String> wrong = new ArrayList<>();
        try {
            player.setGameMode(GameType.SURVIVAL);
            StandPower power = StandPower.get(player);
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            Ability zipper = power.getAbility(StickyFingersAddon.ZIPPER_NAME);
            Ability punch = power.getAbility("punch");
            Ability wounds = power.getAbility(StickyFingersAddon.ZIP_USER_WOUNDS_NAME);
            helper.assertTrue(zipper != null && punch != null && wounds != null,
                    "Sticky Fingers lacks the zipper, the punch or zip user wounds");
            AbilityInput.keyPress(HIDE_KEY, zipper, player, null, InputMethod.HOLD, 0.0F,
                    BufferingState.clickCanBuffer(), zipper.getAbilityId());
            helper.assertTrue(isZipper(helper, helper.absolutePos(new BlockPos(2, 4, 3))),
                    "fixture: the held zipper press did not zip the stone it faced");
            for (Ability other : List.of(punch, wounds)) {
                EntityActionInstance before = stand.getCurStandAction();
                BufferingState press = BufferingState.clickOnly();
                AbilityInput.keyPress((short) 49, other, player, null, InputMethod.CLICK, 0.0F, press,
                        other.getAbilityId());
                AbilityInput.keyRelease((short) 49, player);
                EntityActionInstance after = stand.getCurStandAction();
                if (press.isActionSuccess || after != before && after != null && after.ability == other) {
                    wrong.add(other.name() + " went off during the held zipper");
                }
                // each row on an idle Stand
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            }
            Ability manual = power.getAbility("manual_control");
            if (manual != null && StickyFingersZipperAbility.refusedByHeldZipper(player, manual)) {
                wrong.add("manual control, a 1.16 key handler and no action, is refused during the held zipper");
            }
            AbilityInput.keyRelease(HIDE_KEY, player);
            BufferingState released = BufferingState.clickOnly();
            AbilityInput.keyPress((short) 49, punch, player, null, InputMethod.CLICK, 0.0F, released,
                    punch.getAbilityId());
            AbilityInput.keyRelease((short) 49, player);
            if (!released.isActionSuccess) {
                wrong.add("fixture: the punch did not go off once the zipper key was let go");
            }
        }
        finally {
            AbilityInput.keyRelease(HIDE_KEY, player);
            closeStaminaUser(player);
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 refused the Stand's other actions while place_zipper was held: " + wrong);
        helper.succeed();
    }

    // 1.16's checkRequirements, which refused the power's other actions while place_zipper was held, was what the HUD
    // asked on the client too: while the zipper is held their keys resolve refused, silently, as the client resolves
    // them for its HUD and press; manual control (no 1.16 action) and the zipper stay usable; letting go ends it.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void heldZipperShowsTheStandsOtherActionsUnusableAsIn116(GameTestHelper helper) {
        fill(helper, new BlockPos(0, 2, 3), new BlockPos(5, 6, 3), Blocks.STONE.defaultBlockState());
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        List<String> wrong = new ArrayList<>();
        try {
            player.setGameMode(GameType.SURVIVAL);
            StandPower power = StandPower.get(player);
            power.setStamina(power.getMaxStamina());
            Ability zipper = power.getAbility(StickyFingersAddon.ZIPPER_NAME);
            helper.assertTrue(zipper != null, "Sticky Fingers has no zipper");
            List<String> others = List.of("punch", "heavy_punch", "barrage",
                    StickyFingersAddon.ZIP_USER_WOUNDS_NAME);
            List<String> usable = List.of("manual_control", StickyFingersAddon.ZIPPER_NAME);
            for (String name : others) {
                AbilityConditionCheck key = resolveKey(power, name);
                helper.assertTrue(key != null && key.conditionCheck.isPositive(),
                        "fixture: " + name + " is not usable before the zipper is held");
            }
            AbilityInput.keyPress(HIDE_KEY, zipper, player, null, InputMethod.HOLD, 0.0F,
                    BufferingState.clickCanBuffer(), zipper.getAbilityId());
            helper.assertTrue(isZipper(helper, helper.absolutePos(new BlockPos(2, 4, 3))),
                    "fixture: the held zipper press did not zip the stone it faced");
            for (String name : others) {
                AbilityConditionCheck key = resolveKey(power, name);
                if (key == null || key.conditionCheck.isPositive() || key.conditionCheck.getWarning() != null) {
                    wrong.add(name + " resolves " + (key == null ? "to nothing"
                            : key.conditionCheck.isPositive() ? "usable" : "refused with a message")
                            + " while the zipper is held");
                }
            }
            for (String name : usable) {
                AbilityConditionCheck key = resolveKey(power, name);
                if (key != null && !key.conditionCheck.isPositive()) {
                    wrong.add(name + " is refused while the zipper is held");
                }
            }
            AbilityInput.keyRelease(HIDE_KEY, player);
            for (String name : others) {
                AbilityConditionCheck key = resolveKey(power, name);
                if (key == null || !key.conditionCheck.isPositive()) {
                    wrong.add(name + " stays refused once the zipper key is let go");
                }
            }
        }
        finally {
            AbilityInput.keyRelease(HIDE_KEY, player);
            closeStaminaUser(player);
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 showed the Stand's other actions unusable while place_zipper was held: " + wrong);
        helper.succeed();
    }

    // the key's ability as the power resolves it for the HUD and a press; the moves are cached for a tick
    private static OptionalInt unzipSelectionTint(Ability ability, StandPower power, ConditionCheck drawn) {
        return StickyFingersUnzipPartAbility.selectionTint(new AbilitySelectionVisualQuery(
                ability, power, drawn, AbilitySelectionSurface.HOTBAR_SELECTED));
    }

    private static AbilityConditionCheck resolveKey(StandPower power, String name) {
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(power, false);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        Ability ability = power.getAbility(name);
        return ability != null ? power.updateAvailableMoves().getContextVariationContainer(ability) : null;
    }

    private static boolean isZipper(GameTestHelper helper, BlockPos absolute) {
        return helper.getLevel().getBlockState(absolute).is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get());
    }

    // 1.16 sticky_fingers_unzip_head / _arm / _leg (070 InitStands :76-79): StandAction.onPerform paid the follow-up's
    // 50 on the click, with the result ignored, also when the aimed part was already off. Every part of the husk is
    // off beforehand; the follow-up is pressed on the heavy key while the unzip punch runs.
    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void unzipFollowUpPaysOnAHumanoidWithNoPartLeft(GameTestHelper helper) {
        whenUnzipSceneTicks(helper, () -> {
            FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
            StandPower power = StandPower.get(player);
            StandEntity stand = power.getSummonedStandEntity();
            ServerLevel level = helper.getLevel();
            ZipperCombatData data = ZipperCombatData.get(level);
            Mob husk = unzipTarget(helper, player, EntityType.HUSK);
            Runnable close = () -> closeUnzipScene(helper, player, husk);
            try {
                for (DetachedPartType part : DetachedPartType.values()) {
                    DetachedBodyPartEntity detached = new DetachedBodyPartEntity(part == DetachedPartType.HEAD
                            ? StickyFingersAddon.PLAYER_HEAD.get()
                            : part.isLeg() ? StickyFingersAddon.PLAYER_LEG.get() : StickyFingersAddon.PLAYER_ARM.get(),
                            level);
                    helper.assertTrue(data.registerDetachedPart(level, player, husk, part, detached),
                            "Could not take the target's " + part + " off beforehand");
                }
                aimUnzipScene(player, stand, husk);
                EntityActionInstance punch = pressUnzipFinisher(helper, player);
                AbilityConditionCheck key = resolveKey(power, "heavy_punch");
                helper.assertTrue(key != null && key.ability instanceof StickyFingersUnzipPartAbility
                                && key.conditionCheck.isPositive(),
                        "fixture: the heavy key does not offer the unzip follow-up: " + describeKey(key));
                helper.assertTrue(unzipSelectionTint(key.ability, power, key.drawnCondition(player))
                                .equals(OptionalInt.of(0xFF00FF00)),
                        "1.16 framed the selected unzip follow-up green while it was usable"
                                + " (StandEntityActionModifier.greenSelection); the port frames it "
                                + unzipSelectionTint(key.ability, power, key.drawnCondition(player)));
                power.setStamina(200.0F);
                pressHeavyKey(player, key);
                helper.assertTrue(punch.getPunchModifiers().size() == 1,
                        "fixture: the follow-up press put no modifier on the unzip punch: " + punch.getPunchModifiers());
                helper.assertTrue(!key.ability.checkConditions(power).isPositive()
                                && unzipSelectionTint(key.ability, power, key.ability.checkConditions(power)).isEmpty(),
                        "1.16 framed a selected unzip follow-up white once it was not usable (the punch has its"
                                + " modifier); the port frames it "
                                + unzipSelectionTint(key.ability, power, key.ability.checkConditions(power)));
                for (String part : new String[] { StickyFingersAddon.UNZIP_HEAD_NAME, StickyFingersAddon.UNZIP_ARM_NAME,
                        StickyFingersAddon.UNZIP_LEG_NAME }) {
                    helper.assertTrue(unzipSelectionTint(power.getAbility(part), power, ConditionCheck.POSITIVE)
                                    .equals(OptionalInt.of(0xFF00FF00)),
                            "1.16 framed the selected " + part + " green while usable");
                }
                for (String other : new String[] { "heavy_punch", StickyFingersAddon.UNZIP_PUNCH_NAME,
                        StickyFingersAddon.ZIPPER_NAME }) {
                    helper.assertTrue(unzipSelectionTint(power.getAbility(other), power, ConditionCheck.POSITIVE)
                                    .isEmpty(),
                            "1.16 framed the selected " + other + " white; the port frames it green");
                }
                helper.assertTrue(power.getStamina() == 200.0F - StickyFingersPhase1Spec.UNZIP_PART_STAMINA,
                        "1.16 paid the unzip follow-up's " + StickyFingersPhase1Spec.UNZIP_PART_STAMINA
                                + " on its click at a humanoid with the aimed part already off: a 200 bar holds "
                                + power.getStamina());
                helper.assertTrue(data.partsFor(husk.getUUID()).size() == DetachedPartType.values().length
                                && stand.getCurStandAction() == punch,
                        "the press took a part the target no longer had: " + data.partsFor(husk.getUUID()));
            }
            catch (RuntimeException | Error failure) {
                close.run();
                throw failure;
            }
            close.run();
            helper.succeed();
        });
    }

    // 1.16 sticky_fingers_unzip_punch (StickyFingersUnzipOpponent.punchEntity :69-75) only zipped its target and hit
    // it for half the heavy damage; a limb came off only through the follow-up pressed during the punch, which the
    // heavy key offered through the whole task (StandAction.replaceActionKostyl). Without that press: no limb, and no
    // stamina beyond the punch's own 50.
    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void unzipFinisherWithoutTheFollowUpTakesNoLimbAsIn116(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        ZipperCombatData data = ZipperCombatData.get(helper.getLevel());
        Mob husk = unzipTarget(helper, player, EntityType.HUSK);
        EntityActionInstance[] punch = {null};
        float[] spentInStandTicks = {0.0F};
        float[] staminaBeforeStandTick = {-1.0F};
        List<ActionPhase> offeredIn = new ArrayList<>();
        List<String> wrongKey = new ArrayList<>();
        List<String> limbs = new ArrayList<>();
        String[] firstStandTick = {"none"};
        String[] notOffered = {null};
        Consumer<EntityTickEvent.Pre> pre = event -> {
            if (event.getEntity() == stand && punch[0] != null && "none".equals(firstStandTick[0])) {
                firstStandTick[0] = punch[0].getPhase() + (punch[0].isOver() ? " over" : "") + ", the Stand's action "
                        + (stand.getCurStandAction() == punch[0] ? "the pressed punch" : stand.getCurStandAction());
            }
            if (event.getEntity() == stand && punch[0] != null && !punch[0].isOver()) {
                staminaBeforeStandTick[0] = power.getStamina();
            }
        };
        Consumer<EntityTickEvent.Post> post = event -> {
            if (event.getEntity() != stand || punch[0] == null || staminaBeforeStandTick[0] < 0.0F) {
                return;
            }
            spentInStandTicks[0] += Math.max(staminaBeforeStandTick[0] - power.getStamina(), 0.0F);
            staminaBeforeStandTick[0] = -1.0F;
            // the scene's FakePlayer is in no player list, so the combat data closes its parts once the server tick ends
            if (limbs.isEmpty() && (!data.partsFor(husk.getUUID()).isEmpty()
                    || !detachedPartsAround(helper, husk).isEmpty())) {
                limbs.add(punch[0].getPhase() + ": " + data.partsFor(husk.getUUID()) + ", "
                        + detachedPartsAround(helper, husk).size() + " part entities");
            }
            if (!punch[0].isOver()) {
                AbilityConditionCheck key = resolveKey(power, "heavy_punch");
                if (key != null && key.ability instanceof StickyFingersUnzipPartAbility) {
                    if (!offeredIn.contains(punch[0].getPhase())) {
                        offeredIn.add(punch[0].getPhase());
                    }
                }
                else if (key == null || key.ability != punch[0].ability) {
                    wrongKey.add(punch[0].getPhase() + ": " + describeKey(key));
                }
                else if (notOffered[0] == null) {
                    notOffered[0] = punch[0].getPhase() + ": " + describePunch(punch[0], player, husk);
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(pre);
            NeoForge.EVENT_BUS.unregister(post);
            closeUnzipScene(helper, player, husk);
        };
        startWhenTheSceneTicks(helper, player, husk, close, () -> {
            aimUnzipScene(player, stand, husk);
            punch[0] = pressUnzipFinisher(helper, player);
        }, () -> {
            helper.assertTrue(punch[0].isOver(), "the unzip punch still runs");
            helper.assertTrue(husk.hasEffect(StickyFingersAddon.ZIPPER_EFFECT) && husk.getHealth() < husk.getMaxHealth(),
                    "fixture: the unzip punch did not zip and hit the husk (health " + husk.getHealth() + ")");
            helper.assertTrue(wrongKey.isEmpty() && offeredIn.containsAll(List.of(ActionPhase.WINDUP,
                            ActionPhase.RECOVERY)),
                    "1.16 offered the unzip follow-up on the heavy key through the whole unzip punch, windup and"
                            + " recovery: offered in " + offeredIn + ", other keys " + wrongKey
                            + " (first Stand tick after the press: " + firstStandTick[0] + "; first not offered in "
                            + notOffered[0] + ")");
            helper.assertTrue(limbs.isEmpty() && data.partsFor(husk.getUUID()).isEmpty(),
                    "1.16 took no limb off without the follow-up pressed during the unzip punch; the husk lost "
                            + limbs);
            helper.assertTrue(spentInStandTicks[0] == 50.0F,
                    "1.16 took only the heavy punch's own 50 for an unzip punch without the follow-up; the Stand's"
                            + " ticks took " + spentInStandTicks[0]);
        });
    }

    // 1.16 StickyFingersUnzipOpponent.punchEntity (:75) ended with multiplyAddKnockback(0): the unzip punch kept only
    // the hit's own knockback (0.4), none of the heavy punch's additional knockback. The husk floats without AI or
    // gravity, so its motion in the Stand tick that hit it is the knockback alone.
    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void unzipPunchAddsNoHeavyKnockbackAsIn116(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        Mob husk = unzipTarget(helper, player, EntityType.HUSK);
        EntityActionInstance[] punch = {null};
        Vec3[] motionAtHit = {null};
        Consumer<EntityTickEvent.Post> post = event -> {
            if (event.getEntity() == stand && punch[0] != null && motionAtHit[0] == null
                    && husk.getHealth() < husk.getMaxHealth()) {
                motionAtHit[0] = husk.getDeltaMovement();
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(post);
            closeUnzipScene(helper, player, husk);
        };
        startWhenTheSceneTicks(helper, player, husk, close, () -> {
            aimUnzipScene(player, stand, husk);
            helper.assertTrue(husk.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0.0D
                            && husk.getDeltaMovement().lengthSqr() == 0.0D,
                    "fixture: the husk resists knockback or moves before the punch");
            punch[0] = pressUnzipFinisher(helper, player);
        }, () -> {
            helper.assertTrue(motionAtHit[0] != null, "the unzip punch has not hit the husk yet");
            double pushed = motionAtHit[0].horizontalDistance();
            helper.assertTrue(pushed >= 0.3D && pushed <= 0.41D,
                    "1.16 knocked the unzip punch's target back only with the hit's own 0.4, no heavy additional"
                            + " knockback: the husk flies off at " + pushed + " blocks a tick (" + motionAtHit[0] + ")");
        });
    }

    // 1.16 StickyFingersUnzipOpponent.onTaskSet recorded the part its user aimed at (TargetHitPart.getHitTarget from the
    // user's look, not the Stand's), and the heavy key pressed during the punch gave that part's follow-up, drawn with
    // its own icon; StickyFingersUnzipBodyPart.standTickRecovery took the part off in the recovery's last tick
    // (ticksLeft <= 1), healed the target by half the damage the punch dealt and knocked the part along its user's
    // head yaw (DamageUtil.knockback, strength 1); the leg was the one on the side of the target's main arm unless
    // that one was off. The user looks 35 degrees down at the left-handed husk 2.5 blocks ahead, the line entering its
    // legs; the Stand itself looks above the husk's head.
    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void unzipFollowUpTakesTheAimedLimbAtTheRecoveryEndAsIn116(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 35.0F);
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        ZipperCombatData data = ZipperCombatData.get(helper.getLevel());
        Mob husk = unzipTarget(helper, player, EntityType.HUSK);
        husk.setLeftHanded(true);
        EntityActionInstance[] punch = {null};
        String[] stateBeforeTick = {null};
        float[] ticksLeftBeforeTick = {-1.0F};
        float[] healthBeforeTick = {0.0F};
        String[] detachedIn = {null};
        float[] detachedTicksLeft = {-1.0F};
        float[] healed = {0.0F};
        List<DetachedPartType> partsAtDetach = new ArrayList<>();
        int[] entitiesAtDetach = {0};
        Vec3[] partMotion = {null};
        String[] partPlace = {null};
        Consumer<EntityTickEvent.Pre> pre = event -> {
            if (event.getEntity() == stand && punch[0] != null && !punch[0].isOver()) {
                stateBeforeTick[0] = punch[0].getPhase() + " with " + punch[0].getPhaseTicksLeft() + " ticks left";
                ticksLeftBeforeTick[0] = punch[0].getPhase() == ActionPhase.RECOVERY ? punch[0].getPhaseTicksLeft() : -1.0F;
                healthBeforeTick[0] = husk.getHealth();
            }
        };
        List<String> trace = new ArrayList<>();
        Consumer<EntityTickEvent.Post> post = event -> {
            if (event.getEntity() != stand || punch[0] == null) {
                return;
            }
            if (trace.size() < 40 && stateBeforeTick[0] != null) {
                trace.add(stateBeforeTick[0] + ": target "
                        + (punch[0].getTaskTarget(helper.getLevel()).getEntity() == husk) + ", modifiers "
                        + punch[0].getPunchModifiers().size() + ", husk " + String.format("%.2f",
                                husk.distanceTo(stand)) + " from the Stand, health " + husk.getHealth());
                stateBeforeTick[0] = null;
            }
            // read in the Stand's tick: the scene's FakePlayer is in no player list, so the combat data closes its
            // parts once the server tick ends
            if (detachedIn[0] == null && !data.partsFor(husk.getUUID()).isEmpty()) {
                detachedIn[0] = trace.isEmpty() ? "?" : trace.get(trace.size() - 1);
                detachedTicksLeft[0] = ticksLeftBeforeTick[0];
                healed[0] = husk.getHealth() - healthBeforeTick[0];
                partsAtDetach.addAll(data.partsFor(husk.getUUID()));
                List<Entity> detachedParts = detachedPartsAround(helper, husk);
                entitiesAtDetach[0] = detachedParts.size();
                partMotion[0] = detachedParts.isEmpty() ? null : detachedParts.get(0).getDeltaMovement();
                if (!detachedParts.isEmpty()) {
                    Entity part = detachedParts.get(0);
                    partPlace[0] = part.position().distanceTo(husk.position()) < 1.0E-6D
                            && Math.abs(Mth.wrapDegrees(part.getYRot() - husk.getYRot())) < 1.0E-3F
                                    ? null
                                    : part.position() + " facing " + part.getYRot() + ", the husk at " + husk.position()
                                            + " facing " + husk.getYRot();
                }
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        Runnable close = () -> {
            NeoForge.EVENT_BUS.unregister(pre);
            NeoForge.EVENT_BUS.unregister(post);
            closeUnzipScene(helper, player, husk);
        };
        startWhenTheSceneTicks(helper, player, husk, close, () -> {
            aimUnzipScene(player, stand, husk);
            Vec3 eyes = player.getEyePosition();
            Vec3 entry = husk.getBoundingBox().clip(eyes, eyes.add(player.getLookAngle().scale(6.0D))).orElse(null);
            helper.assertTrue(entry != null && entry.y - husk.getY() < husk.getBbHeight() * 0.375D,
                    "fixture: the user's look line does not enter the husk's legs: " + entry);
            stand.moveTo(player.getX() - 0.75D, player.getY(), player.getZ() - 0.75D, 0.0F, -20.0F);
            stand.setYHeadRot(0.0F);
            helper.assertTrue(DetachedPartTargeting.aimedRegion(husk, stand) == DetachedPartType.Region.HEAD,
                    "fixture: the Stand's own look does not give the husk's head");
            punch[0] = pressUnzipFinisher(helper, player);
            AbilityConditionCheck key = resolveKey(power, "heavy_punch");
            String sprite = key != null ? key.ability.getSpriteName(power) : null;
            helper.assertTrue(key != null && key.ability.name().equals(StickyFingersAddon.UNZIP_LEG_NAME)
                            && key.conditionCheck.isPositive()
                            && StickyFingersAddon.UNZIP_LEG_NAME.equals(sprite),
                    "1.16 gave the heavy key the follow-up of the part the user aimed at, the legs, with its own"
                            + " icon, during the unzip punch's windup (" + punch[0].getPhase() + "): it resolves to "
                            + describeKey(key) + ", icon " + sprite);
            float before = power.getStamina();
            pressHeavyKey(player, key);
            helper.assertTrue(before - power.getStamina() == StickyFingersPhase1Spec.UNZIP_PART_STAMINA
                            && punch[0].getPunchModifiers().size() == 1,
                    "1.16 paid the follow-up's 50 on its click and put it on the running punch: stamina " + before
                            + " -> " + power.getStamina() + ", modifiers " + punch[0].getPunchModifiers().size());
            AbilityConditionCheck again = resolveKey(power, "heavy_punch");
            helper.assertTrue(again != null && again.ability == punch[0].ability,
                    "1.16 offered no second follow-up once the punch had its modifier: the heavy key resolves to "
                            + describeKey(again));
            helper.assertTrue(data.partsFor(husk.getUUID()).isEmpty(), "the leg came off at the press");
        }, () -> {
            helper.assertTrue(punch[0].isOver(), "the unzip punch still runs");
            helper.assertTrue(partsAtDetach.size() == 1 && partsAtDetach.get(0) == DetachedPartType.RIGHT_LEG
                            && entitiesAtDetach[0] == 1,
                    "1.16 took off one leg, the part the user aimed at when the punch started, on the side of the"
                            + " left-handed husk's main arm, its right leg: the husk lost "
                            + partsAtDetach + " (" + entitiesAtDetach[0] + " part entities); the Stand's ticks: " + trace);
            helper.assertTrue(detachedTicksLeft[0] >= 0.0F && detachedTicksLeft[0] <= 1.0F,
                    "1.16 took the limb off in the unzip punch's last recovery tick (ticksLeft <= 1): it came off in"
                            + " the Stand tick that began in " + detachedIn[0]);
            helper.assertTrue(entitiesAtDetach[0] == 1 && partPlace[0] == null,
                    "1.16 put the leg at the husk's feet, turned as the husk: " + partPlace[0]);
            // 1.16 DamageUtil.knockback(part, 1, user's head yaw 0): pushed 1 block a tick south, away from the user
            helper.assertTrue(partMotion[0] != null && partMotion[0].z >= 0.9D && Math.abs(partMotion[0].x) <= 0.05D,
                    "1.16 knocked the leg along its user's head yaw with strength 1: it moves " + partMotion[0]);
            float refund = punch[0].lastDamageDealtToLiving * StickyFingersPhase1Spec.UNZIP_DAMAGE_REFUND_MULTIPLIER;
            helper.assertTrue(punch[0].lastDamageDealtToLiving > 0.0F && Math.abs(healed[0] - refund) < 1.0E-4F,
                    "1.16 healed the target by half the damage the unzip punch dealt ("
                            + punch[0].lastDamageDealtToLiving + "): it gained " + healed[0]);
        });
    }

    // 1.16 sticky_fingers_unzip_head / _arm / _leg had resolveLevelToUnlock(1), StickyFingersUnzipBodyPart took only a
    // humanoid (ZipperWorldCap.isHumanoid: rotp_stfn:zipper_humanoids here), and StandAction.canSetFollowUp asked
    // PowerBaseImpl.checkRequirements, which refused a stunned Stand (K5-70 A) and, while place_zipper was held, every
    // other action of the power: in each case the heavy key stayed the running unzip punch, whose own press 1.16
    // refused. Also the part a user looking 25 degrees down records.
    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void unzipFollowUpIsNotOfferedBelowResolveOneAtANonHumanoidOrForAStunnedStandAsIn116(
            GameTestHelper helper) {
        whenUnzipSceneTicks(helper, () -> {
            List<String> wrong = new ArrayList<>();
            FakePlayer novice = stickyUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F, false);
            Mob husk = null;
            try {
                husk = unzipTarget(helper, novice, EntityType.HUSK);
                StandPower power = StandPower.get(novice);
                for (String name : new String[] { "heavy_punch", StickyFingersAddon.UNZIP_PUNCH_NAME,
                        StickyFingersAddon.UNZIP_HEAD_NAME, StickyFingersAddon.UNZIP_ARM_NAME,
                        StickyFingersAddon.UNZIP_LEG_NAME }) {
                    power.getCurTypeData()._lockedAbilities.remove(name);
                }
                power.setResolveLevel(0);
                Ability head = power.getAbility(StickyFingersAddon.UNZIP_HEAD_NAME);
                helper.assertTrue(head != null && power.getHighestResolveLevel() == 0 && !power.wasProgressionSkipped(),
                        "fixture: the user has been at Resolve 1 or skipped the progression");
                unzipKeyRow(helper, novice, husk, false, "below Resolve 1", wrong, null);
                power.setResolveLevel(1);
                unzipKeyRow(helper, novice, husk, true, "at Resolve 1 (control)", wrong, null);
            }
            finally {
                closeUnzipScene(helper, novice, husk);
            }
            FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
            StandPower power = StandPower.get(player);
            StandEntity stand = power.getSummonedStandEntity();
            Mob pig = null;
            Mob target = null;
            try {
                pig = unzipTarget(helper, player, EntityType.PIG);
                unzipKeyRow(helper, player, pig, false, "at a pig, no humanoid", wrong, null);
                pig.discard();
                target = unzipTarget(helper, player, EntityType.HUSK);
                unzipKeyRow(helper, player, target, true, "at a husk (control)", wrong, null);
                // 1.16 TargetHitPart.getHitTarget carried the look as far as JojoModUtil.getDistance, less half the
                // user's width: 25 degrees down at the husk 2.5 blocks ahead is its torso (the line enters the legs)
                player.setXRot(25.0F);
                player.xRotO = 25.0F;
                aimUnzipScene(player, stand, target);
                pressUnzipFinisher(helper, player);
                AbilityConditionCheck torso = resolveKey(power, "heavy_punch");
                if (torso == null || !torso.ability.name().equals(StickyFingersAddon.UNZIP_ARM_NAME)) {
                    wrong.add("looking 25 degrees down the heavy key resolves to " + describeKey(torso)
                            + ", 1.16 the arm's follow-up (its TargetHitPart for the torso)");
                }
                LivingComponentAction.getComponent(stand).setAction(null, player, SyncType.NO_SYNC);
                player.setXRot(0.0F);
                player.xRotO = 0.0F;
                // a stone wall 4.5 blocks ahead, behind the husk, for the zipper held while the punch runs
                fill(helper, new BlockPos(0, 2, 5), new BlockPos(5, 6, 5), Blocks.STONE.defaultBlockState());
                Ability zipper = power.getAbility(StickyFingersAddon.ZIPPER_NAME);
                boolean[] zipperHeld = {false};
                try {
                    unzipKeyRow(helper, player, target, false, "with place_zipper held", wrong, () -> {
                        AbilityInput.keyPress(HIDE_KEY, zipper, player, null, InputMethod.HOLD, 0.0F,
                                BufferingState.clickCanBuffer(), zipper.getAbilityId());
                        zipperHeld[0] = StickyFingersZipperAbility.refusedByHeldZipper(player,
                                power.getAbility(StickyFingersAddon.UNZIP_HEAD_NAME));
                    });
                }
                finally {
                    AbilityInput.keyRelease(HIDE_KEY, player);
                }
                helper.assertTrue(zipperHeld[0], "fixture: the zipper key did not start a held zipper on the wall");
                ConditionCheck[] stunned = {null};
                unzipKeyRow(helper, player, target, false, "with the Stand stunned while the punch runs", wrong, () -> {
                    stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
                    stunned[0] = power.getAbility(StickyFingersAddon.UNZIP_HEAD_NAME).checkConditions(power);
                });
                stand.removeEffect(ModStatusEffects.STUN);
                if (!(stunned[0].getWarning() != null
                        && stunned[0].getWarning().getContents() instanceof TranslatableContents contents
                        && "jojo.message.action_condition.stun".equals(contents.getKey()))) {
                    wrong.add("asked for itself with the Stand stunned the follow-up gives " + describeCheck(stunned[0])
                            + ", 1.16 the stun message");
                }
            }
            finally {
                if (pig != null) {
                    pig.discard();
                }
                closeUnzipScene(helper, player, target);
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 offered the unzip follow-up only from Resolve 1, at a humanoid, for a"
                    + " Stand that is not stunned, with no zipper held: " + String.join("; ", wrong));
            helper.succeed();
        });
    }

    // 1.16 StickyFingersUnzipOpponent.rotateStandTowardsTarget (:93-102) turned the Stand to the centre of the part its
    // user aimed at when the punch started (TargetHitPart.getPartCenter): the top of the head, the torso at 0.7 of the
    // height moved 0.375 of the width to the side, the feet. The husk 2.5 blocks ahead faces the user (yaw 180), so the
    // torso's point lies 0.225 blocks east of its centre.
    @GameTest(template = "empty", timeoutTicks = STAND_SCENE_TIMEOUT_TICKS)
    public static void unzipPunchTurnsTheStandToTheRecordedPartAsIn116(GameTestHelper helper) {
        whenUnzipSceneTicks(helper, () -> {
            FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
            StandPower power = StandPower.get(player);
            StandEntity stand = power.getSummonedStandEntity();
            List<String> wrong = new ArrayList<>();
            Mob husk = null;
            try {
                husk = unzipTarget(helper, player, EntityType.HUSK);
                double h = husk.getBbHeight();
                Vec3 feet = husk.position();
                Object[][] rows = {
                        { 0.0F, "the head", StickyFingersAddon.UNZIP_HEAD_NAME, feet.add(0.0D, h, 0.0D) },
                        { 25.0F, "the torso", StickyFingersAddon.UNZIP_ARM_NAME,
                                feet.add(husk.getBbWidth() * 0.375D, h * 0.7D, 0.0D) },
                        { 35.0F, "the legs", StickyFingersAddon.UNZIP_LEG_NAME, feet } };
                for (Object[] row : rows) {
                    float pitch = (Float) row[0];
                    player.setXRot(pitch);
                    player.xRotO = pitch;
                    aimUnzipScene(player, stand, husk);
                    EntityActionInstance punch = pressUnzipFinisher(helper, player);
                    AbilityConditionCheck key = resolveKey(power, "heavy_punch");
                    helper.assertTrue(key != null && key.ability.name().equals(row[2]),
                            "fixture: looking " + pitch + " degrees down did not record " + row[1] + ": the heavy key resolves"
                                    + " to " + describeKey(key) + " [" + describePunch(punch, player, husk) + "]");
                    Vec3 expected = (Vec3) row[3];
                    Vec3 lookedAt = punch.getStandLookTargetPosition(stand, new ActionTarget(husk));
                    if (lookedAt == null || lookedAt.distanceTo(expected) > 1.0E-4D) {
                        wrong.add(row[1] + ": the Stand looks at " + lookedAt + ", 1.16 at " + expected);
                    }
                    LivingComponentAction.getComponent(stand).setAction(null, player, SyncType.NO_SYNC);
                }
            }
            finally {
                closeUnzipScene(helper, player, husk);
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 turned the Stand to the centre of the part its user aimed at when the"
                    + " unzip punch started: " + String.join("; ", wrong));
            helper.succeed();
        });
    }

    // 1.16 StickyFingersUnzipBodyPart.standTickRecovery (:111-156) chose the arm with its user's random: the left if it
    // was on and the first roll came true or the right was off, else none if the right was off or a second roll came
    // false while the left was on, else the right (with both arms on: left 1/2, right 1/4, none 1/4); and the leg by
    // the target's main arm, no roll: the left leg for a right-handed target unless it was off, the right leg for a
    // left-handed one unless it was off. Each row gives the rolls in order and how many 1.16 took.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unzipFollowUpChoosesTheArmByRollsAndTheLegByTheMainArmAsIn116(GameTestHelper helper) {
        DetachedPartType.Region arms = DetachedPartType.Region.TORSO_ARMS;
        DetachedPartType.Region legs = DetachedPartType.Region.LEGS;
        DetachedPartType.Region head = DetachedPartType.Region.HEAD;
        DetachedPartType leftArm = DetachedPartType.LEFT_ARM;
        DetachedPartType rightArm = DetachedPartType.RIGHT_ARM;
        DetachedPartType leftLeg = DetachedPartType.LEFT_LEG;
        DetachedPartType rightLeg = DetachedPartType.RIGHT_LEG;
        HumanoidArm right = HumanoidArm.RIGHT;
        HumanoidArm left = HumanoidArm.LEFT;
        List<String> wrong = new ArrayList<>();
        sideRow(wrong, arms, Set.of(), right, new boolean[] { true }, 1, leftArm);
        sideRow(wrong, arms, Set.of(), right, new boolean[] { false, true }, 2, rightArm);
        sideRow(wrong, arms, Set.of(), right, new boolean[] { false, false }, 2, null);
        sideRow(wrong, arms, Set.of(), left, new boolean[] { true }, 1, leftArm);
        sideRow(wrong, arms, Set.of(leftArm), right, new boolean[] { false }, 1, rightArm);
        sideRow(wrong, arms, Set.of(leftArm), right, new boolean[] { true }, 1, rightArm);
        sideRow(wrong, arms, Set.of(rightArm), right, new boolean[] { false }, 1, leftArm);
        sideRow(wrong, arms, Set.of(leftArm, rightArm), right, new boolean[0], 0, null);
        sideRow(wrong, legs, Set.of(), right, new boolean[0], 0, leftLeg);
        sideRow(wrong, legs, Set.of(leftLeg), right, new boolean[0], 0, rightLeg);
        sideRow(wrong, legs, Set.of(), left, new boolean[0], 0, rightLeg);
        sideRow(wrong, legs, Set.of(rightLeg), left, new boolean[0], 0, leftLeg);
        sideRow(wrong, legs, Set.of(leftLeg, rightLeg), right, new boolean[0], 0, null);
        sideRow(wrong, head, Set.of(), right, new boolean[0], 0, DetachedPartType.HEAD);
        sideRow(wrong, head, Set.of(DetachedPartType.HEAD), right, new boolean[0], 0, null);
        helper.assertTrue(wrong.isEmpty(), "1.16 took an arm by its rolls and a leg by the target's main arm: "
                + String.join("; ", wrong));
        helper.succeed();
    }

    // 1.16 StickyFingersUnzipBodyPart.standTickRecovery spawned the head at the target's position raised by its height
    // (:104-106) and an arm (:122-124) or a leg (:151-153) at the target's position, its feet.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unzipFollowUpPartsAppearWhere116PutThemAsIn116(GameTestHelper helper) {
        Mob husk = EntityType.HUSK.create(helper.getLevel());
        helper.assertTrue(husk != null, "Could not create the husk");
        husk.moveTo(helper.absoluteVec(new Vec3(2.5D, 3.0D, 2.5D)));
        Vec3 feet = husk.position();
        List<String> wrong = new ArrayList<>();
        for (DetachedPartType part : DetachedPartType.values()) {
            Vec3 expected = part == DetachedPartType.HEAD ? feet.add(0.0D, husk.getBbHeight(), 0.0D) : feet;
            Vec3 placed = DetachedPartTargeting.spawnPosition(part, husk);
            if (placed.distanceTo(expected) > 1.0E-6D) {
                wrong.add(part + " at " + placed + ", 1.16 at " + expected);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 put a detached head on top of the target and an arm or a leg at its"
                + " feet: " + String.join("; ", wrong));
        helper.succeed();
    }

    private static void sideRow(List<String> wrong, DetachedPartType.Region region, Set<DetachedPartType> off,
            HumanoidArm mainArm, boolean[] rolls, int rollsTaken, @Nullable DetachedPartType expected) {
        ScriptedRolls random = new ScriptedRolls(rolls);
        DetachedPartType chosen = DetachedPartTargeting.selectAvailable(region, off, mainArm, random);
        if (chosen != expected || random.taken != rollsTaken) {
            wrong.add(region + " with " + off + " off, main arm " + mainArm + ", rolls "
                    + Arrays.toString(rolls) + ": " + chosen + " after " + random.taken + " rolls, 1.16 "
                    + expected + " after " + rollsTaken);
        }
    }

    // nextBoolean gives the row's rolls in order (false once they run out) and counts every call
    private static final class ScriptedRolls extends LegacyRandomSource {
        private final boolean[] rolls;
        int taken;

        ScriptedRolls(boolean[] rolls) {
            super(0L);
            this.rolls = rolls;
        }

        @Override
        public boolean nextBoolean() {
            boolean roll = taken < rolls.length && rolls[taken];
            taken++;
            return roll;
        }
    }

    /*
     * A scene that does not wait for its own ticks still finds its husk by id (the punch's target): a mob added to a chunk
     * that does not tick entities yet is not found, and the press takes no target. Such a scene starts once the chunks of
     * its user, its Stand and the husk 2.5 blocks ahead tick entities.
     */
    private static void whenUnzipSceneTicks(GameTestHelper helper, Runnable scene) {
        ServerLevel level = helper.getLevel();
        Vec3 user = helper.absoluteVec(new Vec3(2.5D, 3.0D, 0.5D));
        holdSceneChunks(level, user);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (Vec3 corner : new Vec3[] { user.add(-1.5D, 0.0D, -1.5D), user.add(1.5D, 0.0D, -1.5D),
                            user.add(-1.5D, 0.0D, 3.0D), user.add(1.5D, 0.0D, 3.0D) }) {
                        BlockPos pos = BlockPos.containing(corner);
                        if (!level.isPositionEntityTicking(pos)) {
                            pauseOncePerTick(level);
                            throw new GameTestAssertException("The chunk at " + pos + " does not tick entities");
                        }
                    }
                })
                .thenExecute(scene);
    }

    // the unzip punch pressed at the target with a full meter, then what the heavy key resolves to while it runs
    // (after whileRunning)
    private static void unzipKeyRow(GameTestHelper helper, FakePlayer player, Mob target, boolean offered, String row,
            List<String> wrong, @Nullable Runnable whileRunning) {
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        aimUnzipScene(player, stand, target);
        EntityActionInstance punch = pressUnzipFinisher(helper, player);
        if (whileRunning != null) {
            whileRunning.run();
        }
        AbilityConditionCheck key = resolveKey(power, "heavy_punch");
        boolean follows = key != null && key.ability instanceof StickyFingersUnzipPartAbility;
        if (follows != offered || !offered && (key == null || key.ability != punch.ability)) {
            wrong.add(row + ": the heavy key resolves to " + describeKey(key) + ", 1.16 "
                    + (offered ? "the follow-up" : "the running unzip punch") + " [" + describePunch(punch, player, target)
                    + "]");
        }
        LivingComponentAction.getComponent(stand).setAction(null, player, SyncType.NO_SYNC);
    }

    // A husk (or another mob) 2.5 blocks south of the user, at its height, facing it.
    private static Mob unzipTarget(GameTestHelper helper, FakePlayer player, EntityType<? extends Mob> type) {
        Mob mob = type.create(helper.getLevel());
        helper.assertTrue(mob != null, "Could not create the " + type);
        mob.moveTo(player.getX(), player.getY(), player.getZ() + 2.5D, 180.0F, 0.0F);
        mob.setYHeadRot(180.0F);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add the " + type);
        return mob;
    }

    private static void aimUnzipScene(FakePlayer player, StandEntity stand, Entity target) {
        LivingComponentAction.getComponent(player).entityAim.setTarget(new ActionTarget(target));
        LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
    }

    // the heavy key pressed with a full finisher meter starts the unzip punch
    private static EntityActionInstance pressUnzipFinisher(GameTestHelper helper, FakePlayer player) {
        StandPower power = StandPower.get(player);
        StandEntity stand = power.getSummonedStandEntity();
        power.setStamina(power.getMaxStamina());
        stand.setFinisherMeter(1.0F);
        AbilityConditionCheck key = resolveKey(power, "heavy_punch");
        pressHeavyKey(player, key);
        EntityActionInstance action = stand.getCurStandAction();
        helper.assertTrue(key != null && key.ability.name().equals(StickyFingersAddon.UNZIP_PUNCH_NAME)
                        && action instanceof StickyFingersUnzipPunchAbility.UnzipPunchAction
                        && action.ability == key.ability,
                "fixture: a full meter did not make the heavy key start the unzip punch: " + describeKey(key)
                        + (key != null ? " " + describeCheck(key.conditionCheck) : "") + ", action " + action);
        return action;
    }

    // as the server takes a click: the key's resolved ability, its condition check, then the press
    private static void pressHeavyKey(FakePlayer player, @Nullable AbilityConditionCheck key) {
        if (key != null && AbilityInput.withConditionCheck(key, player, InputMethod.CLICK)) {
            Ability heavy = StandPower.get(player).getAbility("heavy_punch");
            AbilityInput.keyPress(UNZIP_KEY, key.ability, player, null, InputMethod.CLICK, 0.0F,
                    BufferingState.clickCanBuffer(), heavy.getAbilityId());
            AbilityInput.keyRelease(UNZIP_KEY, player);
        }
    }

    // the parts taken off this target (the grid puts other scenes within a few blocks)
    private static List<Entity> detachedPartsAround(GameTestHelper helper, Entity target) {
        return helper.getLevel().getEntities((Entity) null, target.getBoundingBox().inflate(16.0D),
                entity -> entity instanceof DetachedBodyPartEntity part
                        && target.getUUID().equals(part.getTargetUuid()));
    }

    private static void closeUnzipScene(GameTestHelper helper, FakePlayer player, @Nullable Mob target) {
        try {
            if (target != null) {
                ZipperCombatData.get(helper.getLevel()).closeTargeted(helper.getLevel(), target.getUUID(),
                        ZipperCombatCloseReason.ORPHAN_RECOVERY);
                for (Entity part : detachedPartsAround(helper, target)) {
                    part.discard();
                }
                target.discard();
            }
            StandEntity stand = StandPower.get(player).getSummonedStandEntity();
            if (stand != null) {
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            }
        }
        finally {
            closeStaminaUser(player);
        }
    }

    private static String describePunch(EntityActionInstance punch, FakePlayer player, Entity target) {
        StandEntity stand = StandPower.get(player).getSummonedStandEntity();
        Entity taskTarget = punch.getTaskTarget(player.level()).getEntity();
        return "recorded part " + (punch instanceof StickyFingersUnzipPunchAbility.UnzipPunchAction unzip
                        ? unzip.getRecordedPart() : "?") + ", task target "
                + (taskTarget == target ? "the target" : String.valueOf(taskTarget)) + ", modifiers "
                + punch.getPunchModifiers().size() + ", over " + punch.isOver() + ", Stand action "
                + (stand != null && stand.getCurStandAction() == punch ? "this punch" : stand != null
                        ? String.valueOf(stand.getCurStandAction()) : "no Stand") + ", stunned "
                + (stand != null && stand.hasEffect(ModStatusEffects.STUN)) + ", target "
                + String.format("%.2f", target.distanceTo(stand != null ? stand : player)) + " from the Stand";
    }

    private static String describeKey(@Nullable AbilityConditionCheck key) {
        return key == null ? "nothing" : key.ability.name() + " (" + (key.conditionCheck.isPositive()
                ? "usable" : "refused") + ")";
    }

    private static final short UNZIP_KEY = 48;

    // 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional): a Stand-bearing mob waited it
    // (+ 10) before its next pick, Resolve never shortened it (StandAction's multiplier 0 drops only the additional part)
    // and a Creative user kept it. 070 InitStands :81 split the extended punch's cooldown(20, 60); zip user wounds (:89),
    // climb zipper (:90) and zip target wounds (:91) had a plain cooldown(100), all additional; the other actions none.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void stickyFingersCooldownsKeepTheDonorsTechnicalAndAdditionalPart(GameTestHelper helper) {
        FakePlayer player = staminaUser(helper, new Vec3(2.5D, 3.0D, 0.5D), 0.0F, 0.0F);
        List<String> wrong = new ArrayList<>();
        try {
            StandPower power = StandPower.get(player);
            String[] names = { StickyFingersAddon.EXTENDED_PUNCH_NAME, StickyFingersAddon.ZIP_USER_WOUNDS_NAME,
                    StickyFingersAddon.CLIMB_ZIPPER_NAME, StickyFingersAddon.ZIP_TARGET_WOUNDS_NAME, "punch", "barrage",
                    "heavy_punch", StickyFingersAddon.UNZIP_PUNCH_NAME, StickyFingersAddon.ZIPPER_NAME,
                    StickyFingersAddon.HIDE_IN_MOB_NAME, StickyFingersAddon.OPEN_STORAGE_NAME,
                    StickyFingersAddon.OPEN_INNER_STORAGE_NAME };
            int[] technical = { 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
            int[] additional = { 60, 100, 100, 100, 0, 0, 0, 0, 0, 0, 0, 0 };
            for (String mode : new String[] { "Survival", "Resolve", "Creative" }) {
                if (mode.equals("Survival")) {
                    player.setGameMode(GameType.SURVIVAL);
                }
                else if (mode.equals("Resolve")) {
                    player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0, false, false));
                }
                else {
                    player.removeEffect(ModStatusEffects.RESOLVE);
                    player.setGameMode(GameType.CREATIVE);
                }
                helper.assertTrue(player.hasEffect(ModStatusEffects.RESOLVE) == mode.equals("Resolve")
                                && power.isUserCreative() == mode.equals("Creative"),
                        "fixture: the user is not in the " + mode + " setup");
                for (int row = 0; row < names.length; row++) {
                    Ability ability = power.getAbility(names[row]);
                    helper.assertTrue(ability != null, "fixture: Sticky Fingers has no " + names[row]);
                    int full = ability.getCooldown(power, -1);
                    int expected = technical[row] + (mode.equals("Survival") ? additional[row] : 0);
                    if (mode.equals("Survival") && ability.getCooldownTechnical(power) != technical[row]) {
                        wrong.add(names[row] + " reports technical " + ability.getCooldownTechnical(power) + " of a full "
                                + full + ", the donor's " + technical[row] + " of " + (technical[row] + additional[row]));
                    }
                    else if (full != expected) {
                        wrong.add(names[row] + " takes " + full + " ticks in " + mode + ", the donor's " + expected);
                    }
                }
            }
        }
        finally {
            closeStaminaUser(player);
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16 Sticky Fingers cooldown(technical, additional): " + String.join("; ", wrong));
        helper.succeed();
    }

    // A Sticky Fingers user with every ability unlocked, the Stand summoned and ready, and a finite stamina bar.
    private static FakePlayer staminaUser(GameTestHelper helper, Vec3 relative, float yRot, float xRot) {
        return stickyUser(helper, relative, yRot, xRot, true);
    }

    // Without the skipped progression the Resolve level and the skills are a fresh Stand's.
    private static FakePlayer stickyUser(GameTestHelper helper, Vec3 relative, float yRot, float xRot,
            boolean skipProgression) {
        FakePlayer player = new StickyUser(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StickyStamina"));
        try {
            Vec3 pos = helper.absoluteVec(relative);
            holdSceneChunks(helper.getLevel(), pos);
            player.setNoGravity(true);
            player.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
            player.setYHeadRot(yRot);
            player.setYBodyRot(yRot);
            helper.getLevel().addNewPlayer(player);
            StandPower power = PowerClass.STAND.attachGet(player);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(StickyFingersAddon.STICKY_FINGERS.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant the registered Sticky Fingers Stand");
            if (skipProgression) {
                power.skipProgression();
                power.setResolveLevel(power.getMaxResolveLevel());
            }
            helper.assertTrue(StickyFingersAddon.STICKY_FINGERS.get().summon(player, power)
                            && power.getSummonedStandEntity() != null,
                    "Could not summon Sticky Fingers");
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            helper.assertTrue(!power.isStaminaInfinite(), "The fixture's stamina bar is infinite");
            return player;
        }
        catch (RuntimeException | Error exception) {
            closeStaminaUser(player);
            throw exception;
        }
    }

    // while local it is its own client's player (LocalPlayer's)
    private static final class StickyUser extends FakePlayer {
        boolean local;

        StickyUser(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean isLocalPlayer() {
            return local;
        }
    }

    private static void assertAdmitted(GameTestHelper helper, StandPower power, String name) {
        Ability ability = power.getAbility(name);
        helper.assertTrue(ability != null, "Sticky Fingers has no " + name);
        ConditionCheck check = ability.checkConditions(power);
        helper.assertTrue(check.isPositive(), "1.16 had no stamina condition: " + name + " must be admitted at "
                + power.getStamina() + " stamina, got "
                + (check.getWarning() != null ? check.getWarning().getString() : "a silent refusal"));
    }

    // The server's order: the condition check, then the press.
    private static void pressStickyAbility(GameTestHelper helper, FakePlayer player, String name) {
        StandPower power = StandPower.get(player);
        assertAdmitted(helper, power, name);
        BufferingState buffering = BufferingState.clickOnly();
        power.getAbility(name).onKeyPress(helper.getLevel(), player, null, InputMethod.CLICK, 0.0F, buffering);
        helper.assertTrue(buffering.isActionSuccess, name + " did not start on the admitted press");
    }

    /*
     * The framework forces only the chunk of the one-block template, and its grid puts the template on any block of a
     * chunk, so a scene's user and Stand may stand in a chunk that only a temporary ticket loads. Unloaded and loaded
     * again, it brings back a new Stand entity: the one the scene watches is gone (UNLOADED_TO_CHUNK) and the press is
     * performed by the other. A scene forces the chunks around its user before building it (the runner unforces every
     * chunk when the batch ends).
     */
    private static void holdSceneChunks(ServerLevel level, Vec3 pos) {
        for (int x = Mth.floor(pos.x - SCENE_MARGIN_BLOCKS) >> 4; x <= Mth.floor(pos.x + SCENE_MARGIN_BLOCKS) >> 4; x++) {
            for (int z = Mth.floor(pos.z - SCENE_MARGIN_BLOCKS) >> 4; z <= Mth.floor(pos.z + SCENE_MARGIN_BLOCKS) >> 4;
                    z++) {
                level.setChunkForced(x, z, true);
            }
        }
    }

    /*
     * A press its Stand performs: the Stand ticks only once its chunk ticks entities, which the chunk workers ready
     * after the scene is built, while the GameTest server ticks as fast as it can. So the scene presses once the chunks
     * of the user and its Stand tick entities, pacing each tick it waits (at most SCENE_WAIT_TICKS), and the judge must
     * then pass within SCENE_OWN_TICKS of the press.
     */
    private static void startWhenTheSceneTicks(GameTestHelper helper, FakePlayer player, Runnable close, Runnable press,
            Runnable judge) {
        startWhenTheSceneTicks(helper, player, null, close, press, judge);
    }

    // also the chunk of the target the press takes, which it finds by id
    private static void startWhenTheSceneTicks(GameTestHelper helper, FakePlayer player, @Nullable Entity target,
            Runnable close, Runnable press, Runnable judge) {
        ServerLevel level = helper.getLevel();
        StandEntity stand = StandPower.get(player).getSummonedStandEntity();
        Entity[] scene = target != null ? new Entity[] { player, stand, target } : new Entity[] { player, stand };
        long[] pressedAt = {-1L};
        int[] standTicksAtPress = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Entity idle = firstNotTicking(level, scene);
                    if (idle != null) {
                        pauseOncePerTick(level);
                        throw new GameTestAssertException("The chunk of " + idle.getName().getString() + " at "
                                + idle.blockPosition() + " does not tick entities");
                    }
                })
                .thenExecute(() -> {
                    try {
                        Entity idle = firstNotTicking(level, scene);
                        helper.assertTrue(idle == null, "fixture: pressed while the chunk of "
                                + (idle != null ? idle.getName().getString() : "") + " does not tick entities");
                        for (Entity entity : new Entity[] { player, stand }) {
                            helper.assertTrue(level.getForcedChunks().contains(entity.chunkPosition().toLong()),
                                    "fixture: the scene does not hold the chunk of " + entity.getName().getString()
                                            + " at " + entity.blockPosition());
                        }
                        helper.assertTrue(!stand.isRemoved() && StandPower.get(player).getSummonedStandEntity() == stand,
                                "fixture: the Stand the scene watches is gone (" + stand.getRemovalReason() + ")");
                        pressedAt[0] = helper.getTick();
                        standTicksAtPress[0] = stand.tickCount;
                        press.run();
                    }
                    catch (RuntimeException | Error failure) {
                        close.run();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> {
                    try {
                        judge.run();
                    }
                    catch (GameTestAssertException pending) {
                        if (helper.getTick() - pressedAt[0] >= SCENE_OWN_TICKS) {
                            StandPower power = StandPower.get(player);
                            GameTestAssertException failure = new GameTestAssertException(pending.getMessage()
                                    + " [Stand ticks since the press " + (stand.tickCount - standTicksAtPress[0])
                                    + ", removed " + stand.getRemovalReason() + ", summoned "
                                    + (power.getSummonedStandEntity() == stand ? "this Stand"
                                            : String.valueOf(power.getSummonedStandEntity()))
                                    + ", its action " + LivingComponentAction.getComponent(stand).getAction()
                                    + ", user's Stand-power action "
                                    + LivingComponentAction.getCurEntityAction(player, PowerClass.STAND)
                                    + ", chunk ticks " + level.isPositionEntityTicking(stand.blockPosition())
                                    + ", stamina " + power.getStamina() + ", Stand at " + stand.position()
                                    + ", user at " + player.position() + "]");
                            close.run();
                            helper.testInfo.fail(failure);
                        }
                        throw pending;
                    }
                })
                .thenExecute(close)
                .thenSucceed();
    }

    private static Entity firstNotTicking(ServerLevel level, Entity... entities) {
        for (Entity entity : entities) {
            if (!level.isPositionEntityTicking(entity.blockPosition())) {
                return entity;
            }
        }
        return null;
    }

    // Once per game tick however many scenes wait.
    private static void pauseOncePerTick(ServerLevel level) {
        long now = level.getGameTime();
        if (now == pausedAt) {
            return;
        }
        pausedAt = now;
        try {
            Thread.sleep(SCENE_PAUSE_MILLIS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // The bar after the first Stand tick in which done holds: the tick that performed, before any regeneration.
    private static Consumer<EntityTickEvent.Post> watchStamina(StandPower power, BooleanSupplier done,
            float[] recorded) {
        StandEntity stand = power.getSummonedStandEntity();
        Consumer<EntityTickEvent.Post> watch = event -> {
            if (event.getEntity() == stand && recorded[0] < 0.0F && done.getAsBoolean()) {
                recorded[0] = power.getStamina();
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, watch);
        return watch;
    }

    private static void assertPerformedOnAnEmptyBar(GameTestHelper helper, float[] performed, String refused,
            int cost) {
        helper.assertTrue(performed[0] >= 0.0F, refused);
        helper.assertTrue(performed[0] == 0.0F,
                "the " + cost + " cost drains a 100 bar to 0, the bar held " + performed[0]);
    }

    private static void closeStaminaUser(FakePlayer player) {
        StandPower power = StandPower.get(player);
        if (power != null && power.hasPower() && power.isSummoned()) {
            power.getPowerType().forceUnsummon(player, power);
        }
        close(player);
    }

    // A lone block on a stone floor: free cells on its four sides and above it.
    private static BlockPos pillar(GameTestHelper helper, BlockState state) {
        fill(helper, new BlockPos(1, 1, 1), new BlockPos(3, 1, 5), Blocks.STONE.defaultBlockState());
        fill(helper, new BlockPos(1, 2, 1), new BlockPos(3, 5, 5), Blocks.AIR.defaultBlockState());
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        put(helper, pos, state);
        return pos;
    }

    private static Entity probe(GameTestHelper helper, double x, double y, double z) {
        Entity probe = EntityType.CHICKEN.create(helper.getLevel());
        helper.assertTrue(probe != null, "Could not create the collision probe");
        probe.setNoGravity(true);
        probe.setPos(x, y, z);
        return probe;
    }

    private static boolean within(BlockPos cell, Entity entity) {
        AABB box = entity.getBoundingBox();
        return box.minX >= cell.getX() && box.maxX <= cell.getX() + 1.0D
                && box.minY >= cell.getY() && box.maxY <= cell.getY() + 1.0D
                && box.minZ >= cell.getZ() && box.maxZ <= cell.getZ() + 1.0D;
    }

    // A Sticky Fingers user with the Stand out, as the zipper ability requires; no fixture zipper is left behind.
    private static FakePlayer user(GameTestHelper helper) {
        FakePlayer player = fixture(helper, true);
        try {
            put(helper, helper.absolutePos(new BlockPos(4, 2, 2)), Blocks.AIR.defaultBlockState());
            StandEntity stand = StickyFingersAddon.STICKY_FINGERS_ENTITY.get().create(helper.getLevel());
            helper.assertTrue(stand != null, "Could not create the Stand entity");
            stand.withStandType(StickyFingersAddon.STICKY_FINGERS.get());
            StandPower.get(player).setSummonedStand(stand);
            helper.assertTrue(StickyFingersAddon.isStickyFingersActive(player), "The fixture's Stand is not out");
            return player;
        }
        catch (RuntimeException | Error exception) {
            close(player);
            throw exception;
        }
    }

    // One press of the zipper ability on a block face, as the server receives it.
    private static ZipperLeaseData.UseResult press(GameTestHelper helper, FakePlayer player, BlockPos pos,
            Direction face, boolean shift) {
        player.setShiftKeyDown(shift);
        Vec3 hit = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5D));
        return ZipperLeaseData.get(helper.getLevel()).use(player, new BlockHitResult(hit, face, pos, false));
    }

    // A zipper placed on the north face of frontState, with backState behind it, must stay a single zipper.
    private static void assertSingle(GameTestHelper helper, FakePlayer player, BlockPos front, BlockState frontState,
            BlockState backState, String message) {
        ServerLevel level = helper.getLevel();
        BlockPos back = front.south();
        put(helper, front, frontState);
        put(helper, back, backState);
        helper.assertTrue(press(helper, player, front, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                        && isZipper(level.getBlockState(front), Direction.NORTH, false),
                "Could not place the control zipper on " + frontState);
        helper.assertTrue(level.getBlockState(back).equals(backState) && !isThrough(level.getBlockState(front))
                        && ZipperLeaseData.get(level).getAt(back) == null,
                message + ": front=" + level.getBlockState(front) + ", back=" + level.getBlockState(back));
        helper.assertTrue(press(helper, player, front, Direction.NORTH, false) == ZipperLeaseData.UseResult.RESTORED
                        && level.getBlockState(front).equals(frontState),
                "Could not remove the control zipper from " + frontState);
    }

    private static void remove(GameTestHelper helper, FakePlayer player, BlockPos pos, Direction face) {
        helper.assertTrue(press(helper, player, pos, face, false) == ZipperLeaseData.UseResult.RESTORED
                        && helper.getLevel().getBlockState(pos).is(Blocks.STONE),
                "Could not remove the zipper at " + pos);
    }

    // A zipper on the north face of the target, through to the free cell behind it, with stone above and below.
    private static void assertNoSecondExit(GameTestHelper helper, FakePlayer player, BlockPos target, String message) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(press(helper, player, target, Direction.NORTH, false) == ZipperLeaseData.UseResult.CREATED
                        && isThrough(level.getBlockState(target)),
                "Could not place the control zipper through to the free cell");
        helper.assertTrue(level.getBlockState(target.above()).is(Blocks.STONE)
                        && level.getBlockState(target.below()).is(Blocks.STONE),
                message + ": above=" + level.getBlockState(target.above())
                        + ", below=" + level.getBlockState(target.below()));
        remove(helper, player, target, Direction.NORTH);
    }

    private static CompoundTag activeRecord(CompoundTag saved, BlockPos pos) {
        for (Tag element : saved.getList("Active", Tag.TAG_COMPOUND)) {
            if (((CompoundTag) element).getLong("Position") == pos.asLong()) {
                return (CompoundTag) element;
            }
        }
        return null;
    }

    private static boolean isZipper(BlockState state, Direction facing, boolean open) {
        return state.is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())
                && state.getValue(StickyFingersZipperBlock.FACING) == facing
                && state.getValue(StickyFingersZipperBlock.OPEN) == open;
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        return ZipperInGround.isOpenZipper(level.getBlockState(pos));
    }

    // The far side of a one-block wall is a state of the zipper block, read by name.
    private static boolean isThrough(BlockState state) {
        Property<?> through = state.getBlock().getStateDefinition().getProperty("through");
        return through != null && Boolean.TRUE.equals(state.getValue(through));
    }

    private static BlockState through(GameTestHelper helper, BlockState state) {
        Property<?> through = state.getBlock().getStateDefinition().getProperty("through");
        helper.assertTrue(through instanceof BooleanProperty, "The zipper block has no state for an open far side");
        return state.setValue((BooleanProperty) through, true);
    }

    private static void stand(FakePlayer player, BlockPos cell) {
        player.setPos(cell.getX() + 0.5D, cell.getY() + 0.1D, cell.getZ() + 0.5D);
    }

    // One vanilla jump: 0.42 take-off, 0.08 gravity, 0.98 drag; drift applies only above clearY.
    private static void jump(FakePlayer player, double clearY, Vec3 drift) {
        double velocity = 0.42D;
        for (int tick = 0; tick < 60; tick++) {
            boolean clear = player.getY() >= clearY;
            player.move(MoverType.SELF, new Vec3(clear ? drift.x : 0.0D, velocity, clear ? drift.z : 0.0D));
            if (velocity < 0.0D && player.onGround()) {
                return;
            }
            if (player.verticalCollision) {
                velocity = 0.0D;
            }
            velocity = (velocity - 0.08D) * 0.98D;
        }
        throw new IllegalStateException("The jump never landed: y=" + player.getY());
    }

    private static void assertHeight(GameTestHelper helper, FakePlayer player, double expected, String message) {
        helper.assertTrue(Math.abs(player.getY() - expected) < 1.0E-6D && player.onGround(),
                message + ": y=" + player.getY() + ", expected=" + expected + ", onGround=" + player.onGround());
    }

    private static BlockState zipper(Direction facing, boolean open) {
        return StickyFingersAddon.STICKY_FINGERS_ZIPPER.get().defaultBlockState()
                .setValue(StickyFingersZipperBlock.FACING, facing)
                .setValue(StickyFingersZipperBlock.OPEN, open);
    }

    private static void put(GameTestHelper helper, BlockPos absolute, BlockState state) {
        helper.getLevel().setBlock(absolute, state, QUIET);
    }

    private static void fill(GameTestHelper helper, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(from), helper.absolutePos(to))) {
            put(helper, pos, state);
        }
    }

    private static void matrix(GameTestHelper helper, BlockState state) {
        BlockPos pos = prepareShape(helper, state);
        FakePlayer player = fixture(helper, true);
        try {
            VoxelShape original = state.getCollisionShape(helper.getLevel(), pos, CollisionContext.empty());
            helper.assertTrue(!original.isEmpty() && original.max(Direction.Axis.Y) > 0.0D
                            && original.max(Direction.Axis.Y) < 1.0D,
                    "The registered shape is not a partial-height control");
            for (boolean grounded : new boolean[] { false, true }) {
                for (boolean shift : new boolean[] { false, true }) {
                    locate(player, pos, original, grounded, shift);
                    helper.assertTrue(ZipperPhasing.eligible(player), "Production zipper eligibility was lost");
                    assertShape(helper, grounded ? original : Shapes.empty(), query(helper, pos, player),
                            "Partial shape matrix grounded=" + grounded + ", shift=" + shift);
                }
            }
            helper.succeed();
        }
        finally {
            close(player);
        }
    }

    private static BlockPos prepareShape(GameTestHelper helper, BlockState state) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        for (Direction direction : Direction.values()) {
            helper.getLevel().setBlockAndUpdate(pos.relative(direction), Blocks.STONE.defaultBlockState());
        }
        helper.getLevel().setBlockAndUpdate(pos, state);
        return pos;
    }

    private static FakePlayer fixture(GameTestHelper helper, boolean eligible) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StickyShape"));
        try {
            player.setNoGravity(true);
            helper.getLevel().addNewPlayer(player);
            if (eligible) {
                StandPower power = PowerClass.STAND.attachGet(player);
                helper.assertTrue(StandPowerTransitions.insert(power,
                                new StandInstance(StickyFingersAddon.STICKY_FINGERS.get())).status()
                                == StandPowerTransitions.Status.APPLIED,
                        "Could not grant the registered Sticky Fingers Stand");
                helper.assertTrue(power.getMaxResolveLevel() > 0, "Missing maximum Resolve boundary");
                power.setResolveLevel(power.getMaxResolveLevel());
                BlockPos zipper = helper.absolutePos(new BlockPos(4, 2, 2));
                helper.getLevel().setBlockAndUpdate(zipper, StickyFingersAddon.STICKY_FINGERS_ZIPPER.get()
                        .defaultBlockState().setValue(StickyFingersZipperBlock.OPEN, true));
                player.setPos(zipper.getX() + 0.5D, zipper.getY() + 0.1D, zipper.getZ() + 0.5D);
                ZipperInGround.tick(player);
                helper.assertTrue(ZipperInGround.isInGround(player) && ZipperPhasing.eligible(player),
                        "The actual open-zipper update did not establish phasing eligibility");
            }
            return player;
        }
        catch (RuntimeException | Error exception) {
            close(player);
            throw exception;
        }
    }

    private static void locate(FakePlayer player, BlockPos pos, VoxelShape shape, boolean grounded, boolean shift) {
        player.setPos(pos.getX() + 0.5D, pos.getY() + shape.max(Direction.Axis.Y) * 0.5D, pos.getZ() + 0.5D);
        player.setOnGround(grounded);
        player.setShiftKeyDown(shift);
    }

    private static VoxelShape query(GameTestHelper helper, BlockPos pos, FakePlayer player) {
        return helper.getLevel().getBlockState(pos).getCollisionShape(
                helper.getLevel(), pos, CollisionContext.of(player));
    }

    private static void assertShape(GameTestHelper helper, VoxelShape expected, VoxelShape actual, String message) {
        helper.assertTrue(!Shapes.joinIsNotEmpty(expected, actual, BooleanOp.NOT_SAME), message);
    }

    private static void close(FakePlayer player) {
        ZipperPhasing.setServerSinking(player, false);
        StandPower power = StandPower.get(player);
        if (power != null) {
            power.setSummonedStand(null);
        }
        EventHooks.firePlayerLoggedOut(player);
        player.discard();
    }
}
