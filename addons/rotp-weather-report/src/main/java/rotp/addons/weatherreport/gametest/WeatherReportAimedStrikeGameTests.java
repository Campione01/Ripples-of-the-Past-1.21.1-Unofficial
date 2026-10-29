package rotp.addons.weatherreport.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.ability.WeatherReportColdWindAbility;
import rotp.addons.weatherreport.ability.WeatherReportLightningAbility;
import rotp.addons.weatherreport.ability.WeatherReportWindAbility;
import rotp.addons.weatherreport.entity.WeatherReportStandEntity;
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
 * 1.16 WeatherReportLightning put the bolt at RayTraceResult.getLocation() of JojoModUtil.rayTrace (OUTLINE blocks),
 * and WeatherReportColdWind centred its 3x3x3 block area on new BlockPos(getLocation()) of the Stand's
 * aimWithStandOrUser ray. A location on a block is the point where the ray meets the block's outline, never the block's
 * centre: a bolt aimed at the floor lands on its top face, on a snow layer it lands on the snow, and a cold wind aimed at
 * the top of the floor works on the cells from the floor up to two cells above it.
 * JojoModUtil.rayTraceMultipleEntities looked for entities along the whole ray before any block, so a mob behind a wall
 * gets the bolt or becomes the cold wind's centre (owner decision of 2026-10-15: these skills are not stopped by walls).
 * StandEntity.canTarget kept the user out of the Stand's ray, and aimWithStandOrUser turned the Stand from the user's
 * rotation toward the user's precision target (a block's centre), by at most getAttackSpeed() / 16 * 18 degrees.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportAimedStrikeGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 33;
    private static final float LIGHTNING_PITCH = 30.0F;
    // The Stand turns toward the centre of the floor block the user looks at; at this pitch its ray then meets the top
    // of the block before that one near the middle of the face.
    private static final float WIND_PITCH = 18.0F;
    private static final float WALL_PITCH = 6.0F;
    private static final float TURN_PITCH = 15.0F;
    // The floor block the user looks at lies almost under the Stand's eye, far steeper than the user's look.
    private static final float TURN_CAP_PITCH = 50.0F;
    // Weather Report's speed 12: 12 / 16 * 18 degrees on each axis.
    private static final float TURN_CAP = 13.5F;
    private static final double SNOW_TOP = 2.0D / 16.0D;
    private static final int WALL_Z = 3;
    private static final int MOB_Z = 6;
    private static final float USER_PICK_RADIUS = 2.0F;
    private static final double ANGLE_TOLERANCE = 0.3D;
    private static final int ARRANGE_TICK = 12;
    private static final int SETTLE_DEADLINE = 120;
    private static final int STRIKE_BUDGET = 90;
    private static final int WIND_OBSERVE = 4;
    private static final long WALL_CLOCK_BUDGET_MILLIS = 120_000L;

    private WeatherReportAimedStrikeGameTests() {}

    @GameTest(template = "empty", batch = "wr_strike_lightning_ground", timeoutTicks = 400)
    public static void lightningAimedAtTheFloorStrikesTheHitPoint(GameTestHelper helper) {
        new Fixture(helper, Case.LIGHTNING_GROUND).start();
    }

    @GameTest(template = "empty", batch = "wr_strike_lightning_snow", timeoutTicks = 400)
    public static void lightningAimedAtASnowLayerStrikesTheSnow(GameTestHelper helper) {
        new Fixture(helper, Case.LIGHTNING_SNOW).start();
    }

    @GameTest(template = "empty", batch = "wr_strike_lightning_wall", timeoutTicks = 400)
    public static void lightningStrikesAMobBehindAWall(GameTestHelper helper) {
        new Fixture(helper, Case.LIGHTNING_WALL).start();
    }

    @GameTest(template = "empty", batch = "wr_strike_cold_wind", timeoutTicks = 400)
    public static void coldWindAimedAtTheFloorWorksAroundTheCellAboveIt(GameTestHelper helper) {
        new Fixture(helper, Case.COLD_WIND).start();
    }

    @GameTest(template = "empty", batch = "wr_strike_cold_wind_user", timeoutTicks = 400)
    public static void coldWindPassesOverItsUserInTheStandsRay(GameTestHelper helper) {
        new Fixture(helper, Case.COLD_WIND_USER).start();
    }

    @GameTest(template = "empty", batch = "wr_strike_cold_wind_wall", timeoutTicks = 400)
    public static void coldWindCentresOnAMobBehindAWall(GameTestHelper helper) {
        new Fixture(helper, Case.COLD_WIND_WALL).start();
    }

    @GameTest(template = "empty", batch = "wr_strike_cold_wind_turn", timeoutTicks = 400)
    public static void coldWindTurnsTheStandToTheUsersTarget(GameTestHelper helper) {
        new Fixture(helper, Case.COLD_WIND_TURN).start();
    }

    @GameTest(template = "empty", batch = "wr_strike_cold_wind_turn_cap", timeoutTicks = 400)
    public static void coldWindTurnsTheStandNoFurtherThanItsCap(GameTestHelper helper) {
        new Fixture(helper, Case.COLD_WIND_TURN_CAP).start();
    }

    private enum Case {
        LIGHTNING_GROUND("lightning_ground", WeatherReportAddon.LIGHTNING_NAME, LIGHTNING_PITCH, 2),
        LIGHTNING_SNOW("lightning_snow", WeatherReportAddon.LIGHTNING_NAME, LIGHTNING_PITCH, 2),
        LIGHTNING_WALL("lightning_wall", WeatherReportAddon.LIGHTNING_NAME, WALL_PITCH, 2),
        COLD_WIND("cold_wind", WeatherReportAddon.COLD_WIND_NAME, WIND_PITCH, WeatherReportColdWindAbility.RESOLVE_LEVEL),
        COLD_WIND_USER("cold_wind_user", WeatherReportAddon.COLD_WIND_NAME, WIND_PITCH,
                WeatherReportColdWindAbility.RESOLVE_LEVEL),
        COLD_WIND_WALL("cold_wind_wall", WeatherReportAddon.COLD_WIND_NAME, WALL_PITCH,
                WeatherReportColdWindAbility.RESOLVE_LEVEL),
        COLD_WIND_TURN("cold_wind_turn", WeatherReportAddon.COLD_WIND_NAME, TURN_PITCH,
                WeatherReportColdWindAbility.RESOLVE_LEVEL),
        COLD_WIND_TURN_CAP("cold_wind_turn_cap", WeatherReportAddon.COLD_WIND_NAME, TURN_CAP_PITCH,
                WeatherReportColdWindAbility.RESOLVE_LEVEL);

        final String id;
        final String ability;
        final float pitch;
        final int resolveLevel;

        Case(String id, String ability, float pitch, int resolveLevel) {
            this.id = id;
            this.ability = ability;
            this.pitch = pitch;
            this.resolveLevel = resolveLevel;
        }

        boolean lightning() {
            return ability.equals(WeatherReportAddon.LIGHTNING_NAME);
        }

        boolean wall() {
            return this == LIGHTNING_WALL || this == COLD_WIND_WALL;
        }

        boolean windArea() {
            return this == COLD_WIND || this == COLD_WIND_USER;
        }
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final Case testCase;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        private final List<Runnable> cleanups = new ArrayList<>();
        private final long startedAtMillis = System.currentTimeMillis();
        private BlockPos cell;
        private Player user;
        private StandPower power;
        private WeatherReportStandEntity stand;
        private Ability ability;
        private Husk mob;
        private BlockPos mobCell;
        private BlockPos mobCampfire;
        private Vec3 standLastPos;
        private int standStillTicks;
        private long pressedAt = -1L;
        private long arrangedAt = -1L;
        private Vec3 bolt;
        private Vec3 expected;
        private BlockPos areaCentre;
        private BlockPos topCampfire;
        private BlockPos lowCampfire;
        private boolean finished;

        private Fixture(GameTestHelper helper, Case testCase) {
            this.helper = helper;
            this.testCase = testCase;
            this.level = helper.getLevel();
        }

        private void start() {
            try {
                setUp();
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
            helper.onEachTick(this::tick);
        }

        private void setUp() {
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            // GameTest keeps only the template's own chunk entity-ticking, so the user and the Stand stay in it.
            cell = new BlockPos(chunk.getMinBlockX() + 8, template.getY() + 4, chunk.getMinBlockZ() + 3);
            for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-4, -3, -2), cell.offset(4, 5, 10))) {
                helper.assertTrue(level.isEmptyBlock(pos), "The strike fixture space is not empty at " + pos);
            }
            helper.assertTrue(level.isPositionEntityTicking(cell) && level.isPositionEntityTicking(cell.offset(0, 0, 10)),
                    "The strike fixture is outside the entity-ticking test chunk");
            for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-4, -1, -1), cell.offset(4, -1, 10))) {
                put(pos, Blocks.STONE.defaultBlockState());
            }

            user = new Player(level, BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "wrs-" + testCase.ordinal())) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }

                // The user's pick box then holds the Stand's eye: the Stand's ray starts inside its own user.
                @Override
                public float getPickRadius() {
                    return testCase == Case.COLD_WIND_USER ? USER_PICK_RADIUS : super.getPickRadius();
                }
            };
            user.setNoGravity(true);
            user.setInvulnerable(true);
            user.moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, 0.0F, testCase.pitch);
            user.setYHeadRot(0.0F);
            user.yBodyRot = 0.0F;
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");

            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Weather Report to the user");
            power.setResolveLevel(testCase.resolveLevel);
            ability = power.getAbility(testCase.ability);
            helper.assertTrue(ability != null && ability.isAbilityUnlocked(power),
                    testCase.ability + " is not an unlocked moveset ability: " + ability);
            helper.assertTrue(WeatherReportAddon.WEATHER_REPORT.get().summon(user, power)
                            && power.getSummonedStandEntity() instanceof WeatherReportStandEntity,
                    "Production Weather Report summon failed");
            stand = (WeatherReportStandEntity) power.getSummonedStandEntity();

            if (testCase.wall()) {
                buildWallAndMob();
            }
            if (testCase.lightning()) {
                Vec3 eye = user.getEyePosition();
                Vec3 look = user.getLookAngle();
                Vec3 ground = floorHit(eye, look, cell.getY());
                if (testCase == Case.LIGHTNING_SNOW) {
                    BlockPos snowCell = BlockPos.containing(ground.x, cell.getY(), ground.z);
                    for (BlockPos pos : BlockPos.betweenClosed(snowCell.offset(-1, 0, -3), snowCell.offset(1, 0, 1))) {
                        put(pos, Blocks.SNOW.defaultBlockState());
                    }
                    expected = floorHit(eye, look, cell.getY() + SNOW_TOP);
                }
                else if (testCase == Case.LIGHTNING_WALL) {
                    Vec3 end = eye.add(look.scale(WeatherReportLightningAbility.RANGE));
                    expected = mob.getBoundingBox().inflate(mob.getPickRadius()).clip(eye, end).orElse(null);
                    helper.assertTrue(expected != null, "The user's ray misses the mob: eye=" + eye);
                    requireWallBetween(eye, expected);
                }
                else {
                    expected = ground;
                }
                listen(EntityJoinLevelEvent.class, event -> {
                    if (bolt == null && event.getLevel() == level && event.getEntity() instanceof LightningBolt strike
                            && strike.distanceToSqr(user) < 128.0D * 128.0D) {
                        bolt = strike.position();
                        // Only the strike point is under test; a real bolt would set the fixture on fire.
                        event.setCanceled(true);
                    }
                });
            }
        }

        /** A two-block-high stone wall across the ray, a still mob behind it, and for the wind a backstop and a fire. */
        private void buildWallAndMob() {
            for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-1, 0, WALL_Z), cell.offset(1, 1, WALL_Z))) {
                put(pos, Blocks.STONE.defaultBlockState());
            }
            mobCell = cell.offset(0, 0, MOB_Z);
            if (testCase == Case.COLD_WIND_WALL) {
                // The wind blows along the ray; the backstop keeps the mob in its cell.
                put(mobCell.offset(0, 0, 1), Blocks.STONE.defaultBlockState());
                put(mobCell.offset(0, 1, 1), Blocks.STONE.defaultBlockState());
                mobCampfire = mobCell.offset(1, 0, 0);
                put(mobCampfire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            }
            mob = EntityType.HUSK.create(level);
            helper.assertTrue(mob != null, "Could not create the mob behind the wall");
            mob.moveTo(mobCell.getX() + 0.5D, mobCell.getY(), mobCell.getZ() + 0.5D, 180.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setInvulnerable(true);
            mob.setSilent(true);
            mob.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(mob), "Could not add the mob behind the wall");
        }

        private void requireWallBetween(Vec3 from, Vec3 to) {
            HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    user));
            helper.assertTrue(wall.getType() == HitResult.Type.BLOCK
                            && wall.getLocation().distanceToSqr(from) < to.distanceToSqr(from) - 1.0D,
                    "No wall stands between " + from + " and the mob at " + to);
        }

        private void tick() {
            if (finished) {
                return;
            }
            try {
                long now = helper.getTick();
                helper.assertTrue(System.currentTimeMillis() - startedAtMillis < WALL_CLOCK_BUDGET_MILLIS,
                        "The strike case ran out of wall-clock time");
                if (pressedAt < 0L) {
                    if (now >= ARRANGE_TICK && settled(now)) {
                        press();
                        pressedAt = now;
                    }
                }
                else if (testCase.lightning()) {
                    if (bolt != null || now - pressedAt >= STRIKE_BUDGET) {
                        finished = true;
                        judgeLightning();
                    }
                }
                else if (arrangedAt < 0L) {
                    if (windSettled(now)) {
                        if (testCase.windArea()) {
                            arrangeCampfires();
                        }
                        arrangedAt = now;
                    }
                }
                else if (now - arrangedAt >= WIND_OBSERVE) {
                    finished = true;
                    switch (testCase) {
                        case COLD_WIND_WALL -> judgeColdWindWall();
                        case COLD_WIND_TURN, COLD_WIND_TURN_CAP -> judgeColdWindTurn();
                        default -> judgeColdWind();
                    }
                }
            }
            catch (RuntimeException | Error error) {
                finished = true;
                close();
                throw error;
            }
        }

        private boolean settled(long now) {
            helper.assertTrue(now < SETTLE_DEADLINE, "The Stand never came to rest: standStill=" + standStillTicks);
            return standStill();
        }

        private boolean standStill() {
            Vec3 pos = stand.position();
            standStillTicks = stand.isAddedToLevel() && pos.equals(standLastPos) ? standStillTicks + 1 : 0;
            standLastPos = pos;
            return standStillTicks >= 5;
        }

        /** The steps of ClAbilityInputPacket.Handler.handlePress for a HOLD ability, the key then stays held. */
        private void press() {
            helper.assertTrue(power.getAbilityCooldown(ability.name()) == 0, "The ability is on cooldown before the press");
            AbilityConditionCheck check = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(check != null && check.ability == ability,
                    testCase.ability + " is not available in the server moveset");
            helper.assertTrue(AbilityInput.withConditionCheck(check, user, InputMethod.HOLD),
                    testCase.ability + " was refused: " + (check.conditionCheck.getWarning() != null
                            ? check.conditionCheck.getWarning().getString() : "a silent refusal"));
            BufferingState buffering = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, check.ability, user, null, InputMethod.HOLD, 0.0F, buffering,
                    ability.getAbilityId());
            helper.assertTrue(buffering.isActionSuccess, testCase.ability + " did not start on the admitted press");
            standLastPos = null;
            standStillTicks = 0;
        }

        private void judgeLightning() {
            String outcome = "bolt=" + bolt + ", expected=" + expected;
            boolean passed = bolt != null
                    && Math.abs(bolt.x - expected.x) < 0.05D
                    && Math.abs(bolt.y - expected.y) < 0.05D
                    && Math.abs(bolt.z - expected.z) < 0.05D;
            close();
            helper.assertTrue(passed, "The bolt did not fall on the point the user's ray hits (" + testCase.id + "): "
                    + outcome);
            LOGGER.info("R770_WR_STRIKE_PASS {} {}", testCase.id, outcome);
            helper.succeed();
        }

        /** The cold wind is blowing and its Stand has come to rest in the wind pose. */
        private boolean windSettled(long now) {
            helper.assertTrue(now - pressedAt < STRIKE_BUDGET, "The cold wind never settled: standStill="
                    + standStillTicks + ", action=" + stand.getCurStandAction());
            EntityActionInstance action = stand.getCurStandAction();
            boolean blowing = action instanceof WeatherReportWindAbility.WindAction
                    && action.getPhase() == ActionPhase.PERFORM;
            return standStill() && blowing;
        }

        // The 1.16 area centre, computed here with the vanilla clip arithmetic and without the ability's code.
        private void arrangeCampfires() {
            Vec3 eye = stand.getEyePosition();
            Vec3 end = eye.add(stand.getLookAngle().scale(WeatherReportColdWindAbility.RANGE));
            BlockPos floorBlock = BlockPos.containing(floorHit(eye, stand.getLookAngle(), cell.getY()))
                    .atY(cell.getY() - 1);
            expected = new AABB(floorBlock).clip(eye, end).orElse(null);
            helper.assertTrue(expected != null && level.getBlockState(floorBlock).is(Blocks.STONE),
                    "The Stand's ray does not meet the floor: eye=" + eye + ", floorBlock=" + floorBlock);
            if (testCase == Case.COLD_WIND_USER) {
                AABB userPick = user.getBoundingBox().inflate(user.getPickRadius());
                helper.assertTrue(userPick.contains(eye),
                        "The user's pick box does not hold the Stand's eye: eye=" + eye + ", box=" + userPick);
            }
            areaCentre = BlockPos.containing(expected);
            helper.assertTrue(areaCentre.getY() == cell.getY()
                            && Math.abs(expected.x - Math.floor(expected.x) - 0.5D) < 0.4D
                            && Math.abs(expected.z - Math.floor(expected.z) - 0.5D) < 0.4D,
                    "The fixture's hit is too close to a cell boundary: hit=" + expected);
            BlockState lit = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
            topCampfire = areaCentre.offset(1, 1, 0);
            lowCampfire = areaCentre.offset(1, -2, 0);
            put(topCampfire, lit);
            put(lowCampfire, lit);
        }

        private void judgeColdWind() {
            boolean topLit = level.getBlockState(topCampfire).getValue(CampfireBlock.LIT);
            boolean lowLit = level.getBlockState(lowCampfire).getValue(CampfireBlock.LIT);
            EntityActionInstance action = stand.getCurStandAction();
            String outcome = "hit=" + expected + ", centre=" + areaCentre.toShortString() + ", topCampfireLit=" + topLit
                    + ", lowCampfireLit=" + lowLit + ", phase=" + (action == null ? "none" : action.getPhase());
            boolean passed = !topLit && lowLit && action != null && action.getPhase() == ActionPhase.PERFORM;
            close();
            helper.assertTrue(passed, "The cold wind did not work on the 3x3x3 cells around the cell that holds its"
                    + " hit point (" + testCase.id + "): " + outcome);
            LOGGER.info("R770_WR_STRIKE_PASS {} {}", testCase.id, outcome);
            helper.succeed();
        }

        // The mob stands in the cell next to the fire, so the area around any point of its box reaches the fire,
        // while the area around the wall's face stays three cells short of it.
        private void judgeColdWindWall() {
            Vec3 eye = stand.getEyePosition();
            requireWallBetween(eye, mob.getEyePosition());
            boolean mobInCell = BlockPos.containing(mob.position()).equals(mobCell);
            boolean fireLit = level.getBlockState(mobCampfire).getValue(CampfireBlock.LIT);
            EntityActionInstance action = stand.getCurStandAction();
            String outcome = "mob=" + mob.position() + ", mobInCell=" + mobInCell + ", fireBesideMobLit=" + fireLit
                    + ", phase=" + (action == null ? "none" : action.getPhase());
            boolean passed = mobInCell && !fireLit && action != null && action.getPhase() == ActionPhase.PERFORM;
            close();
            helper.assertTrue(passed, "The cold wind did not centre on the mob behind the wall: " + outcome);
            LOGGER.info("R770_WR_STRIKE_PASS {} {}", testCase.id, outcome);
            helper.succeed();
        }

        // 1.16 aimWithStandOrUser with no task target: the Stand turns from the user's rotation toward the centre of
        // the block the user's ray meets, by at most getAttackSpeed() / 16 * 18 degrees on each axis. The cold_wind_turn
        // turn is small enough that no cap applies; cold_wind_turn_cap needs more than the cap downward.
        private void judgeColdWindTurn() {
            Vec3 userEye = user.getEyePosition();
            Vec3 userLook = user.getLookAngle();
            BlockPos floorBlock = BlockPos.containing(floorHit(userEye, userLook, cell.getY())).atY(cell.getY() - 1);
            Vec3 toCentre = Vec3.atCenterOf(floorBlock).subtract(stand.getEyePosition());
            double horizontal = Math.sqrt(toCentre.x * toCentre.x + toCentre.z * toCentre.z);
            double neededXRot = -Math.toDegrees(Math.atan2(toCentre.y, horizontal));
            double neededYRot = -Math.toDegrees(Math.atan2(toCentre.x, toCentre.z));
            double neededTurn = neededXRot - user.getXRot();
            double neededYawTurn = Mth.wrapDegrees(neededYRot - user.getYRot());
            boolean capped = testCase == Case.COLD_WIND_TURN_CAP;
            helper.assertTrue(level.getBlockState(floorBlock).is(Blocks.STONE)
                            && (capped ? neededTurn > TURN_CAP + 1.0D : neededTurn > 1.0D && neededTurn < 10.0D)
                            && Math.abs(neededYawTurn) < 1.0D
                            && Math.abs(stand.getAttackSpeed() / 16.0D * 18.0D - TURN_CAP) < 1.0E-6D,
                    "The fixture does not need the turn it tests: neededTurn=" + neededTurn + ", neededYawTurn="
                            + neededYawTurn + ", floorBlock=" + floorBlock + ", attackSpeed=" + stand.getAttackSpeed());
            double expectedXRot = user.getXRot() + Mth.clamp(neededTurn, -TURN_CAP, TURN_CAP);
            double expectedYRot = user.getYRot() + Mth.clamp(neededYawTurn, -TURN_CAP, TURN_CAP);
            double xRotError = Math.abs(stand.getXRot() - expectedXRot);
            double yRotError = Math.abs(Mth.wrapDegrees(stand.getYRot() - (float) expectedYRot));
            EntityActionInstance action = stand.getCurStandAction();
            String outcome = "standRot=(" + stand.getXRot() + ", " + stand.getYRot() + "), expected=(" + expectedXRot
                    + ", " + expectedYRot + "), userRot=(" + user.getXRot() + ", " + user.getYRot() + "), phase="
                    + (action == null ? "none" : action.getPhase());
            boolean passed = xRotError < ANGLE_TOLERANCE && yRotError < ANGLE_TOLERANCE
                    && action != null && action.getPhase() == ActionPhase.PERFORM;
            close();
            helper.assertTrue(passed, "The cold wind's Stand did not turn to the user's target: " + outcome);
            LOGGER.info("R770_WR_STRIKE_PASS {} {}", testCase.id, outcome);
            helper.succeed();
        }

        private static Vec3 floorHit(Vec3 eye, Vec3 look, double planeY) {
            double distance = (eye.y - planeY) / -look.y;
            return new Vec3(eye.x + look.x * distance, planeY, eye.z + look.z * distance);
        }

        private <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, type, listener);
            cleanups.add(() -> NeoForge.EVENT_BUS.unregister(listener));
        }

        private void put(BlockPos pos, BlockState state) {
            BlockPos key = pos.immutable();
            placed.putIfAbsent(key, level.getBlockState(key));
            helper.assertTrue(level.setBlock(key, state, 3), "Could not place a fixture block at " + key);
        }

        private void close() {
            try {
                cleanups.forEach(Runnable::run);
                cleanups.clear();
                if (mob != null) {
                    mob.discard();
                }
                if (user != null) {
                    AbilityInput.keyRelease(KEY, user);
                    LivingComponentAction.getComponent(user).setAction(null, user, SyncType.NO_SYNC);
                    LivingComponentAction.getComponent(user, PowerClass.STAND).setAction(null, user, SyncType.NO_SYNC);
                    if (power != null && power.isSummoned()) {
                        WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
                    }
                    user.discard();
                }
            }
            finally {
                List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(placed.entrySet());
                for (int i = entries.size() - 1; i >= 0; i--) {
                    level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), 3);
                }
                placed.clear();
            }
        }
    }
}
