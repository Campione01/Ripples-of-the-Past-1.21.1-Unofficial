package rotp.addons.thehand.ability;

import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.subsystems.target.ActionTarget;
import rotp.addons.thehand.TheHandAddon;
import rotp.addons.thehand.ability.TheHandGameTests.TestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Hand's swipe erases the matter or the space in its way (owner, 2026-10-15: walls follow the manga). Erase, its
 * miss pull and teleport and the erasure barrage reach a mob behind a wall, as 1.16's TheHandErase rays did; the
 * barrage's aim decides between the mob behind the wall and the wall, and a wall with nothing behind it is erased.
 */
@GameTestHolder(TheHandAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheHandWallsGameTests {
    private static final short ERASE_KEY = 91;
    private static final short BARRAGE_KEY = 92;
    // From the eyes, down at the middle of a cow 2.2 blocks ahead.
    private static final float DOWN_AT_NEAR_COW = 22.7F;
    private static final double NEAR_WALL = 1.0D;
    private static final double NEAR_COW = 2.2D;
    // Past the user's 5-block aim, inside the 12-block pull and teleport.
    private static final double FAR_WALL = 6.0D;
    private static final double FAR_COW = 9.0D;
    private static final double OFF_LINE_COW = 6.0D;
    private static final double GRASS_AHEAD = 7.0D;
    private static final int OWN_TICKS = 150;

    private TheHandWallsGameTests() {}

    @GameTest(
            template = "empty",
            batch = "the_hand_walls_erase",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseHitsAMobBehindAWall(GameTestHelper helper) {
        Scene scene = Scene.prepare(helper, NEAR_COW);
        if (scene == null) {
            return;
        }
        scene.onceTicking(() -> {
            scene.look(DOWN_AT_NEAR_COW);
            scene.wall(NEAR_WALL, 0, Blocks.BEDROCK);
            scene.wall(NEAR_WALL, 1, Blocks.BEDROCK);
            Cow cow = scene.cow(NEAR_COW, 0.0D);
            float health = cow.getHealth();
            EntityActionInstance erase = scene.press(
                    TheHandAddon.ERASE_NAME,
                    ERASE_KEY,
                    TheHandEraseAbility.EraseAction.class);
            scene.succeedWhen(() -> {
                helper.assertTrue(
                        erase.getPhase() != ActionPhase.BUTTON_CHARGE,
                        "Erase has not swiped yet.");
                helper.assertTrue(
                        cow.isRemoved() || cow.getHealth() < health,
                        "Erase did not reach the cow behind the wall.");
                scene.close();
            });
        });
    }

    @GameTest(
            template = "empty",
            batch = "the_hand_walls_barrage_mob",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void erasureBarrageHitsTheMobItsAimMeetsBehindAWall(
            GameTestHelper helper) {
        Scene scene = Scene.prepare(helper, NEAR_COW);
        if (scene == null) {
            return;
        }
        scene.onceTicking(() -> {
            scene.look(DOWN_AT_NEAR_COW);
            BlockPos wall = scene.crosshairWall();
            Cow cow = scene.cow(NEAR_COW, 0.0D);
            float health = cow.getHealth();
            scene.press(
                    TheHandAddon.ERASURE_BARRAGE_NAME,
                    BARRAGE_KEY,
                    TheHandErasureBarrageAbility.ErasureBarrageAction.class);
            int[] ticks = {0};
            int[] hitTick = {-1};
            scene.succeedWhen(() -> {
                ticks[0]++;
                helper.assertTrue(
                        helper.getLevel().getBlockState(wall)
                                .is(Blocks.STONE),
                        "The erasure barrage erased the wall instead of "
                                + "hitting the cow behind it.");
                if (hitTick[0] < 0
                        && (cow.isRemoved() || cow.getHealth() < health)) {
                    hitTick[0] = ticks[0];
                }
                helper.assertTrue(
                        hitTick[0] >= 0,
                        "The erasure barrage did not hit the cow behind "
                                + "the wall.");
                helper.assertTrue(
                        ticks[0] >= hitTick[0] + 10,
                        "Watching the wall for ten ticks after the hit.");
                scene.close();
            });
        });
    }

    @GameTest(
            template = "empty",
            batch = "the_hand_walls_barrage_wall",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void erasureBarrageErasesAWallWithNothingBehindIt(
            GameTestHelper helper) {
        Scene scene = Scene.prepare(helper, NEAR_COW);
        if (scene == null) {
            return;
        }
        scene.onceTicking(() -> {
            scene.look(DOWN_AT_NEAR_COW);
            BlockPos wall = scene.crosshairWall();
            scene.press(
                    TheHandAddon.ERASURE_BARRAGE_NAME,
                    BARRAGE_KEY,
                    TheHandErasureBarrageAbility.ErasureBarrageAction.class);
            scene.succeedWhen(() -> {
                helper.assertTrue(
                        helper.getLevel().getBlockState(wall).isAir(),
                        "The erasure barrage did not erase the wall "
                                + "with nothing behind it.");
                scene.close();
            });
        });
    }

    // 1.16 TheHandErase.Teleport: rayTrace 12 found the entity behind the
    // wall and pulled it.
    @GameTest(
            template = "empty",
            batch = "the_hand_walls_pull",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseMissPullsAMobBehindAWall(GameTestHelper helper) {
        Scene scene = Scene.prepare(helper, FAR_COW);
        if (scene == null) {
            return;
        }
        scene.onceTicking(() -> {
            scene.look(0.0F);
            scene.wall(FAR_WALL, 0, Blocks.BEDROCK);
            BlockPos wall = scene.wall(FAR_WALL, 1, Blocks.BEDROCK);
            Cow cow = scene.cow(FAR_COW, 0.5D);
            scene.context.owner().setShiftKeyDown(true);
            EntityActionInstance erase = scene.press(
                    TheHandAddon.ERASE_NAME,
                    ERASE_KEY,
                    TheHandEraseAbility.EraseAction.class);
            helper.assertTrue(
                    ((TheHandEraseAbility.EraseAction) erase).isAttracting(),
                    "Fixture: the sneaking user's Erase does not pull.");
            scene.succeedWhen(() -> {
                helper.assertTrue(
                        erase.isOver(),
                        "Erase has not ended yet; the pull waits for it.");
                helper.assertTrue(
                        cow.getZ() < wall.getZ(),
                        "Erase's miss did not pull the cow from behind "
                                + "the wall: cow z " + cow.getZ()
                                + ", wall z " + wall.getZ());
                scene.close();
            });
        });
    }

    @GameTest(
            template = "empty",
            batch = "the_hand_walls_teleport",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseMissTeleportsToAMobBehindAWall(
            GameTestHelper helper) {
        Scene scene = Scene.prepare(helper, FAR_COW);
        if (scene == null) {
            return;
        }
        scene.onceTicking(() -> {
            scene.look(0.0F);
            scene.wall(FAR_WALL, 0, Blocks.BEDROCK);
            BlockPos wall = scene.wall(FAR_WALL, 1, Blocks.BEDROCK);
            scene.cow(FAR_COW, 0.5D);
            EntityActionInstance erase = scene.press(
                    TheHandAddon.ERASE_NAME,
                    ERASE_KEY,
                    TheHandEraseAbility.EraseAction.class);
            scene.succeedWhen(() -> {
                helper.assertTrue(
                        ((TheHandEraseAbility.EraseAction) erase)
                                .missTeleportOccurredDuringPerform(),
                        "Erase has not missed and teleported yet.");
                double userZ = scene.context.owner().getZ();
                helper.assertTrue(
                        userZ > wall.getZ() + 1,
                        "Erase's miss did not teleport the user to the "
                                + "cow behind the wall: user z " + userZ
                                + ", wall z " + wall.getZ());
                scene.close();
            });
        });
    }

    // 1.16 TheHandErase.Teleport: JojoModUtil.rayTrace(..., 0.0, 0.0) grew a
    // hitbox by its pick radius only, so a mob beside the line stays and
    // the one on the line behind it is pulled.
    @GameTest(
            template = "empty",
            batch = "the_hand_miss_ray_line",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseMissLeavesAMobJustOffItsLine(
            GameTestHelper helper) {
        Scene scene = Scene.prepare(helper, FAR_COW);
        if (scene == null) {
            return;
        }
        scene.onceTicking(() -> {
            scene.look(0.0F);
            Cow cow = scene.spawnCow(OFF_LINE_COW, 0.5D, 0.45D + 0.2D);
            Vec3 eyes = scene.context.owner().getEyePosition();
            Vec3 end = eyes.add(scene.context.owner().getViewVector(1.0F)
                    .scale(TheHandCombat.TELEPORT_RANGE));
            helper.assertTrue(
                    cow.getBoundingBox().clip(eyes, end).isEmpty()
                            && cow.getBoundingBox().inflate(0.3D)
                                    .clip(eyes, end).isPresent(),
                    "Fixture: the cow's side is not 0.2 off the line.");
            double z = cow.getZ();
            Cow onLine = scene.cow(FAR_COW, 0.5D);
            double onLineZ = onLine.getZ();
            scene.context.owner().setShiftKeyDown(true);
            EntityActionInstance erase = scene.press(
                    TheHandAddon.ERASE_NAME,
                    ERASE_KEY,
                    TheHandEraseAbility.EraseAction.class);
            helper.assertTrue(
                    ((TheHandEraseAbility.EraseAction) erase).isAttracting(),
                    "Fixture: the sneaking user's Erase does not pull.");
            scene.succeedWhen(() -> {
                helper.assertTrue(
                        erase.isOver(),
                        "Erase has not ended yet; the pull waits for it.");
                helper.assertTrue(
                        !cow.isRemoved() && Math.abs(cow.getZ() - z) < 1.0E-6D,
                        "Erase's miss pulled a cow 0.2 off its line: cow z "
                                + cow.getZ() + ", was " + z);
                helper.assertTrue(
                        onLine.getZ() < onLineZ - 1.0D,
                        "Erase's miss did not pull the cow on its line "
                                + "behind the one beside it: cow z "
                                + onLine.getZ() + ", was " + onLineZ);
                scene.close();
            });
        });
    }

    // 1.16 JojoModUtil.rayTraceMultipleEntities skipped an entity in the
    // caster's root vehicle unless canRiderInteract(): a user riding a
    // horse is not teleported onto its own mount, the ray goes on past it.
    // A rider is not pulled from it: sneaking for the pull dismounts.
    @GameTest(
            template = "empty",
            batch = "the_hand_miss_ray_mount",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseMissPassesTheUsersOwnMount(GameTestHelper helper) {
        Scene scene = Scene.prepare(helper, 0.0D);
        if (scene == null) {
            return;
        }
        scene.onceTicking(() -> {
            ServerPlayer owner = scene.context.owner();
            Horse horse = EntityType.HORSE.create(helper.getLevel());
            helper.assertTrue(horse != null, "Could not create a horse.");
            horse.setNoAi(true);
            horse.setNoGravity(true);
            horse.setInvulnerable(true);
            horse.setTamed(true);
            horse.moveTo(
                    scene.origin.x, scene.origin.y, scene.origin.z,
                    0.0F, 0.0F);
            helper.assertTrue(
                    helper.getLevel().addFreshEntity(horse),
                    "Could not add a horse.");
            scene.extra.add(horse);
            helper.assertTrue(
                    owner.startRiding(horse, true),
                    "Fixture: the user does not ride the horse.");
            owner.setYRot(0.0F);
            owner.setYHeadRot(0.0F);
            owner.setXRot(90.0F);
            // straight down, past the 5-block aim, inside the 12-block ray
            Cow below = scene.spawnCow(0.0D, -7.0D, 0.0D);
            Vec3 eyes = owner.getEyePosition();
            Vec3 end = eyes.add(owner.getViewVector(1.0F)
                    .scale(TheHandCombat.TELEPORT_RANGE));
            helper.assertTrue(
                    horse.getBoundingBox().clip(eyes, end).isPresent()
                            && below.getBoundingBox().clip(eyes, end)
                                    .isPresent(),
                    "Fixture: the horse and the cow under it are not on "
                            + "the user's look.");
            double horseY = horse.getY();
            double cowTop = below.getBoundingBox().maxY;
            EntityActionInstance erase = scene.press(
                    TheHandAddon.ERASE_NAME,
                    ERASE_KEY,
                    TheHandEraseAbility.EraseAction.class);
            scene.succeedWhen(() -> {
                helper.assertTrue(
                        ((TheHandEraseAbility.EraseAction) erase)
                                .missTeleportOccurredDuringPerform(),
                        "Erase has not missed and teleported yet.");
                double userY = owner.getY();
                helper.assertTrue(
                        Math.abs(userY - cowTop) < 0.1D,
                        "Erase's miss teleport did not pass the user's own "
                                + "horse to the cow under it: user y "
                                + userY + ", horse y " + horseY
                                + ", cow top " + cowTop);
                scene.close();
            });
        });
    }

    // 1.16 rayTrace clipped blocks by their outline (BlockMode.OUTLINE), so
    // the miss teleport stops at grass.
    @GameTest(
            template = "empty",
            batch = "the_hand_miss_ray_grass",
            timeoutTicks = 200 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseMissTeleportStopsAtGrass(GameTestHelper helper) {
        Scene scene = Scene.prepare(helper, TheHandCombat.TELEPORT_RANGE);
        if (scene == null) {
            return;
        }
        scene.onceTicking(() -> {
            scene.look(0.0F);
            scene.wall(GRASS_AHEAD, 0, Blocks.DIRT);
            BlockPos grass = scene.wall(GRASS_AHEAD, 1, Blocks.SHORT_GRASS);
            helper.assertTrue(
                    helper.getLevel().getBlockState(grass)
                            .is(Blocks.SHORT_GRASS),
                    "Fixture: the grass did not stay.");
            EntityActionInstance erase = scene.press(
                    TheHandAddon.ERASE_NAME,
                    ERASE_KEY,
                    TheHandEraseAbility.EraseAction.class);
            scene.succeedWhen(() -> {
                helper.assertTrue(
                        ((TheHandEraseAbility.EraseAction) erase)
                                .missTeleportOccurredDuringPerform(),
                        "Erase has not missed and teleported yet.");
                double userZ = scene.context.owner().getZ();
                helper.assertTrue(
                        userZ > scene.origin.z + 1.0D
                                && userZ <= grass.getZ() + 1.0D,
                        "Erase's miss teleport did not stop at the grass: "
                                + "user z " + userZ + ", grass z "
                                + grass.getZ());
                scene.close();
            });
        });
    }

    private static final class Scene {
        final GameTestHelper helper;
        final TestContext context;
        final Vec3 origin;
        final List<Cow> cows = new ArrayList<>();
        final List<Entity> extra = new ArrayList<>();
        final List<BlockPos> placed = new ArrayList<>();
        final List<BlockState> previous = new ArrayList<>();
        boolean closed;

        private Scene(GameTestHelper helper, TestContext context) {
            this.helper = helper;
            this.context = context;
            this.origin = context.owner().position();
        }

        static Scene prepare(GameTestHelper helper, double reach) {
            TestContext context = TheHandGameTests.prepareTheHand(helper);
            if (context == null) {
                return null;
            }
            Vec3 origin = context.owner().position();
            GameTestChunkTickets.forTest(helper).coverBox(
                    origin.x, origin.z, origin.x, origin.z + reach);
            return new Scene(helper, context);
        }

        void onceTicking(Runnable body) {
            TheHandGameTests.onceStandTicks(helper, context, body);
        }

        void look(float pitch) {
            context.owner().moveTo(
                    origin.x, origin.y, origin.z, 0.0F, pitch);
            context.owner().setYHeadRot(0.0F);
            context.owner().setDeltaMovement(Vec3.ZERO);
        }

        BlockPos wall(double ahead, int up, Block block) {
            BlockPos pos = BlockPos.containing(
                    origin.x, origin.y + up, origin.z + ahead);
            placed.add(pos);
            previous.add(helper.getLevel().getBlockState(pos));
            helper.getLevel().setBlockAndUpdate(
                    pos, block.defaultBlockState());
            return pos;
        }

        // One stone block at eye level half a block ahead, the first
        // thing the crosshair meets; The Hand's barrage can erase it.
        BlockPos crosshairWall() {
            BlockPos pos = wall(NEAR_WALL, 1, Blocks.STONE);
            ActionTarget crosshair = StandEntityPunchAbility.crosshairPick(
                    context.stand(), context.owner(), 1.0F, entity -> false);
            helper.assertTrue(
                    crosshair.getType() == ActionTarget.TargetType.BLOCK
                            && pos.equals(crosshair.getBlockPos()),
                    "Fixture: the crosshair does not meet the wall first: "
                            + crosshair);
            return pos;
        }

        Cow cow(double ahead, double lift) {
            Cow cow = spawnCow(ahead, lift, 0.0D);
            Vec3 eyes = context.owner().getEyePosition();
            helper.assertTrue(
                    cow.getBoundingBox().clip(
                            eyes,
                            eyes.add(context.owner().getViewVector(1.0F)
                                    .scale(TheHandCombat.TELEPORT_RANGE)))
                            .isPresent(),
                    "Fixture: the cow is not on the crosshair line.");
            return cow;
        }

        Cow spawnCow(double ahead, double lift, double sideways) {
            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "Could not create a cow.");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.noPhysics = true;
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
                    .setBaseValue(1.0D);
            cow.setHealth(500.0F);
            cow.moveTo(
                    origin.x + sideways, origin.y + lift, origin.z + ahead,
                    0.0F, 0.0F);
            helper.assertTrue(
                    helper.getLevel().addFreshEntity(cow),
                    "Could not add a cow.");
            cows.add(cow);
            return cow;
        }

        EntityActionInstance press(
                String name,
                short key,
                Class<? extends EntityActionInstance> expected) {
            Ability ability = context.power().getAbility(name);
            helper.assertTrue(
                    ability != null && StandSkillWalls.isMarked(ability),
                    "The Hand's " + name + " is not marked through walls.");
            Ability barrage = context.power().getAbility("barrage");
            helper.assertTrue(
                    barrage != null && !StandSkillWalls.isMarked(barrage),
                    "The Hand's ordinary barrage must stay stopped by walls.");
            TheHandGameTests.pressHold(
                    helper, context, ability, key, expected);
            return context.stand().getCurStandAction();
        }

        // Fails with the last unmet condition before the fixture's own
        // deadline, whose message would only say that time ran out.
        void succeedWhen(Runnable conditions) {
            int[] ticks = {0};
            helper.succeedWhen(() -> {
                try {
                    conditions.run();
                } catch (GameTestAssertException unmet) {
                    if (++ticks[0] >= OWN_TICKS && !closed) {
                        close();
                        helper.testInfo.fail(unmet);
                    }
                    throw unmet;
                }
            });
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            AbilityInput.keyRelease(ERASE_KEY, context.owner());
            AbilityInput.keyRelease(BARRAGE_KEY, context.owner());
            cows.forEach(Cow::discard);
            extra.forEach(Entity::discard);
            for (int i = placed.size() - 1; i >= 0; i--) {
                helper.getLevel().setBlockAndUpdate(
                        placed.get(i), previous.get(i));
            }
            TheHandGameTests.cleanup(context);
        }
    }
}
