package rotp.addons.weatherreport.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.ability.WeatherReportFuguRainAbility;
import rotp.addons.weatherreport.entity.WeatherReportFugaSummonerEntity;
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
 * 1.16.5 WeatherReportFuguRain asks whether the sky is open above the real point of the aim ray (checkStandConditions
 * and standPerform both use RayTraceResult.getLocation) and puts the storm 45 blocks above that point. A cast at open
 * flat ground at a shallow pitch must therefore start a storm over the spot it hits, and the same cast under a roof must
 * still be refused. The ray is the Stand's precision ray (JojoModUtil.rayTraceMultipleEntities): a snow layer stops it by
 * its outline, and a mob anywhere on the ray is taken before any block, so a mob behind a wall is the target (owner
 * decision of 2026-10-15). Among the mobs on the ray, players and Stands come first, then hostile mobs, then the rest, so
 * a husk behind a villager on both the user's ray and the Stand's is the target. Both calls go through
 * stand.aimWithStandOrUser(25, target) with an empty target: the Stand first turns from the user's rotation toward what
 * the user's precision ray meets (a mob's eyes, a block's centre), by at most getAttackSpeed() / 16 * 18 degrees, and its
 * own ray from its eyes decides the point. The held check turned Weather Report toward the aim on every tick of the
 * 30-tick charge on the server and on the caster's client, after the Stand's tick had set it back to its user's
 * rotation; the port turns it on every side (owner question A6d-Q1), so it faces its aim all through the charge. A steep
 * cast needs more than that cap. Each case presses fugu_rain the way ClAbilityInputPacket does (condition check, HOLD key
 * press) and lets the server's own ticks run the charge and the perform.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportFuguAimGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 32;
    private static final float SHALLOW_PITCH = 9.2F;
    private static final float STEEP_PITCH = 70.0F;
    private static final int CHARGE_SAMPLE_DELAY = 10;
    private static final double ANGLE_TOLERANCE = 0.3D;
    private static final int ARRANGE_TICK = 12;
    private static final int SETTLE_DEADLINE = 120;
    private static final int CAST_BUDGET = 90;
    private static final int REFUSAL_OBSERVE = 8;
    private static final long WALL_CLOCK_BUDGET_MILLIS = 120_000L;
    private static final String SKY_KEY = "jojo.message.action_condition.fugurain-warningneedtoseesky";
    private static final double SNOW_TOP = 2.0D / 16.0D;
    private static final int WALL_Z = 4;
    private static final int MOB_Z = 7;
    private static final int PASSIVE_Z = 4;
    // Weather Report's speed 12: 12 / 16 * 18 degrees on each axis.
    private static final float TURN_CAP = 13.5F;

    private WeatherReportFuguAimGameTests() {}

    @GameTest(template = "empty", batch = "wr_aim_open_ground", timeoutTicks = 400)
    public static void shallowAimAtOpenGroundStartsAStormOverTheHitPoint(GameTestHelper helper) {
        new Fixture(helper, Aim.OPEN_GROUND).start();
    }

    @GameTest(template = "empty", batch = "wr_aim_roofed_ground", timeoutTicks = 400)
    public static void shallowAimAtGroundUnderARoofIsStillRefused(GameTestHelper helper) {
        new Fixture(helper, Aim.ROOFED_GROUND).start();
    }

    @GameTest(template = "empty", batch = "wr_aim_snow_ground", timeoutTicks = 400)
    public static void shallowAimAtASnowLayerStartsAStormOverTheSnow(GameTestHelper helper) {
        new Fixture(helper, Aim.SNOW_GROUND).start();
    }

    @GameTest(template = "empty", batch = "wr_aim_mob_behind_wall", timeoutTicks = 400)
    public static void shallowAimThroughAWallStartsAStormOverTheMobBehindIt(GameTestHelper helper) {
        new Fixture(helper, Aim.MOB_BEHIND_WALL).start();
    }

    // 1.16 precisionRayTrace ranks players and Stands, then hostile mobs, before the rest, whatever is nearer.
    @GameTest(template = "empty", batch = "wr_aim_mob_priority", timeoutTicks = 400)
    public static void aHostileMobBehindAPassiveOneOnTheRayGetsTheStorm(GameTestHelper helper) {
        new Fixture(helper, Aim.MOB_PRIORITY).start();
    }

    // The floor just ahead of the user lies far off the Stand's own facing: the turn stops at its cap on both axes.
    @GameTest(template = "empty", batch = "wr_aim_steep_ground", timeoutTicks = 400)
    public static void aSteepAimTurnsTheStandNoFurtherThanItsCap(GameTestHelper helper) {
        new Fixture(helper, Aim.STEEP_GROUND).start();
    }

    private enum Aim {
        OPEN_GROUND("open_ground", true, SHALLOW_PITCH),
        ROOFED_GROUND("roofed_ground", false, SHALLOW_PITCH),
        SNOW_GROUND("snow_ground", true, SHALLOW_PITCH),
        MOB_BEHIND_WALL("mob_behind_wall", true, SHALLOW_PITCH),
        MOB_PRIORITY("mob_priority", true, SHALLOW_PITCH),
        STEEP_GROUND("steep_ground", true, STEEP_PITCH);

        final String id;
        final boolean granted;
        final float pitch;

        Aim(String id, boolean granted, float pitch) {
            this.id = id;
            this.granted = granted;
            this.pitch = pitch;
        }

        boolean mobTarget() {
            return this == MOB_BEHIND_WALL || this == MOB_PRIORITY;
        }
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final Aim aim;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        private final long startedAtMillis = System.currentTimeMillis();
        private BlockPos cell;
        private Player user;
        private StandPower power;
        private WeatherReportStandEntity stand;
        private Ability ability;
        private Husk mob;
        private Mob passive;
        private Vec3 standLastPos;
        private int standStillTicks;
        private Vec3 userHit;
        private Vec3 expectedHit;
        private long arrangedAt = -1L;
        private long pressedAt = -1L;
        private boolean admitted;
        private boolean checkKeptRotation;
        private String checkRotation = "";
        private boolean chargeSampled;
        private boolean chargeFacesAim;
        private String chargeRotation = "none";
        private String refusal = "none";
        private boolean finished;

        private Fixture(GameTestHelper helper, Aim aim) {
            this.helper = helper;
            this.aim = aim;
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
            // GameTest keeps only the template's own chunk entity-ticking, so the user, the Stand and the storm stay in it.
            cell = new BlockPos(chunk.getMinBlockX() + 8, template.getY() + 2, chunk.getMinBlockZ() + 3);
            for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-4, -1, -2), cell.offset(4, 5, 12))) {
                helper.assertTrue(level.isEmptyBlock(pos), "The aim fixture space is not empty at " + pos);
            }
            helper.assertTrue(level.isPositionEntityTicking(cell)
                            && level.isPositionEntityTicking(cell.offset(0, 0, 12))
                            && level.isPositionEntityTicking(cell.above((int) WeatherReportFuguRainAbility.SUMMON_HEIGHT)),
                    "The aim fixture is outside the entity-ticking test chunk");

            for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-4, -1, -1), cell.offset(4, -1, 12))) {
                put(pos, Blocks.STONE.defaultBlockState());
            }
            if (aim == Aim.ROOFED_GROUND) {
                for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-2, 3, 8), cell.offset(2, 3, 12))) {
                    put(pos, Blocks.STONE.defaultBlockState());
                }
            }
            if (aim == Aim.SNOW_GROUND) {
                // One layer has an outline but no collision shape. The Stand aims at the centre of the snow cell the user
                // looks at, above the layer, so its ray reaches the snow about two cells farther.
                for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-1, 0, 7), cell.offset(1, 0, 12))) {
                    put(pos, Blocks.SNOW.defaultBlockState());
                }
            }
            if (aim == Aim.MOB_BEHIND_WALL) {
                for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-1, 0, WALL_Z), cell.offset(1, 1, WALL_Z))) {
                    put(pos, Blocks.STONE.defaultBlockState());
                }
            }
            if (aim.mobTarget()) {
                mob = addStillMob(EntityType.HUSK, MOB_Z);
            }
            if (aim == Aim.MOB_PRIORITY) {
                // A villager is tall enough for the Stand's ray, which runs above a cow's back.
                passive = addStillMob(EntityType.VILLAGER, PASSIVE_Z);
            }
            level.setWeatherParameters(0, 12000, true, false);
            level.setRainLevel(1.0F);
            helper.assertTrue(level.isRaining(), "The level is not raining");

            user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wra-" + aim.ordinal())) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            user.setNoGravity(true);
            user.setInvulnerable(true);
            user.moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, 0.0F, aim.pitch);
            user.setYHeadRot(0.0F);
            user.yBodyRot = 0.0F;
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");

            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Weather Report to the user");
            power.setResolveLevel(WeatherReportFuguRainAbility.RESOLVE_LEVEL);
            ability = power.getAbility(WeatherReportAddon.FUGU_RAIN_NAME);
            helper.assertTrue(ability instanceof WeatherReportFuguRainAbility && ability.isAbilityUnlocked(power),
                    "fugu_rain is not an unlocked moveset ability: " + ability);
            helper.assertTrue(WeatherReportAddon.WEATHER_REPORT.get().summon(user, power)
                            && power.getSummonedStandEntity() instanceof WeatherReportStandEntity,
                    "Production Weather Report summon failed");
            stand = (WeatherReportStandEntity) power.getSummonedStandEntity();
        }

        private void tick() {
            if (finished) {
                return;
            }
            try {
                long now = helper.getTick();
                helper.assertTrue(System.currentTimeMillis() - startedAtMillis < WALL_CLOCK_BUDGET_MILLIS,
                        "The aim case ran out of wall-clock time");
                if (arrangedAt < 0L) {
                    if (now >= ARRANGE_TICK && settled(now)) {
                        arrange();
                        arrangedAt = now;
                    }
                }
                else if (pressedAt < 0L) {
                    press();
                    pressedAt = now;
                }
                else if (admitted) {
                    sampleCharge(now);
                    if (stand.getActiveFugaSummoner() != null || now - pressedAt >= CAST_BUDGET) {
                        finished = true;
                        judge();
                    }
                }
                else if (now - pressedAt >= REFUSAL_OBSERVE) {
                    finished = true;
                    judge();
                }
            }
            catch (RuntimeException | Error error) {
                finished = true;
                close();
                throw error;
            }
        }

        /** The Stand has come to rest and the light engine has caught up with the fixture's blocks. */
        private boolean settled(long now) {
            helper.assertTrue(now < SETTLE_DEADLINE,
                    "The fixture never settled: standStill=" + standStillTicks + ", skyAtHit="
                            + (expectedHit == null ? "unknown" : String.valueOf(level.canSeeSky(skyCell()))));
            Vec3 pos = stand.position();
            standStillTicks = stand.isAddedToLevel() && pos.equals(standLastPos) ? standStillTicks + 1 : 0;
            standLastPos = pos;
            userHit = hit(user.getEyePosition(), user.getLookAngle());
            expectedHit = userHit == null ? null : standHit(stand.getEyePosition());
            return standStillTicks >= 5 && expectedHit != null && level.canSeeSky(skyCell()) == aim.granted;
        }

        // Computed here without the ability's own code.
        private Vec3 hit(Vec3 eye, Vec3 look) {
            return aim.mobTarget() ? mobHit(eye, look) : groundHit(eye, look);
        }

        /** Where a ray meets the top of the floor or of the snow. */
        private Vec3 groundHit(Vec3 eye, Vec3 look) {
            double floorTop = cell.getY() + (aim == Aim.SNOW_GROUND ? SNOW_TOP : 0.0D);
            double distance = (eye.y - floorTop) / -look.y;
            return new Vec3(eye.x + look.x * distance, floorTop, eye.z + look.z * distance);
        }

        /** Where a ray enters the mob's box. */
        private Vec3 mobHit(Vec3 eye, Vec3 look) {
            Vec3 end = eye.add(look.scale(WeatherReportFuguRainAbility.RANGE));
            return mob.getBoundingBox().inflate(mob.getPickRadius()).clip(eye, end).orElse(null);
        }

        /** What the user's precision ray meets, as the Stand turns toward it: the mob's eyes or the hit block's centre. */
        private Vec3 userTarget() {
            if (aim.mobTarget()) {
                return mob.getEyePosition();
            }
            int blockY = aim == Aim.SNOW_GROUND ? cell.getY() : cell.getY() - 1;
            return Vec3.atCenterOf(BlockPos.containing(userHit.x, blockY, userHit.z));
        }

        /** The rotation that faces the user's target from the Stand's eyes. */
        private float[] neededRotation(Vec3 eye) {
            Vec3 toTarget = userTarget().subtract(eye);
            double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            float neededXRot = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontal));
            float neededYRot = (float) -Math.toDegrees(Math.atan2(toTarget.x, toTarget.z));
            return new float[] {neededXRot, neededYRot};
        }

        /** The Stand turned from the user's rotation toward the user's target, capped on each axis. */
        private float[] standRotation(Vec3 eye) {
            float[] needed = neededRotation(eye);
            float xRot = user.getXRot() + Mth.clamp(Mth.degreesDifference(user.getXRot(), needed[0]), -TURN_CAP, TURN_CAP);
            float yRot = user.getYRot() + Mth.clamp(Mth.degreesDifference(user.getYRot(), needed[1]), -TURN_CAP, TURN_CAP);
            return new float[] {xRot, yRot};
        }

        /** Where a ray enters the passive mob's box. */
        private Vec3 passiveHit(Vec3 eye, Vec3 look) {
            return passive.getBoundingBox().inflate(passive.getPickRadius())
                    .clip(eye, eye.add(look.scale(WeatherReportFuguRainAbility.RANGE))).orElse(null);
        }

        /**
         * Mid-charge, after the server's entity ticks: the Stand faces the user's target as its perform will, the
         * turn starting again from the user's rotation on every tick.
         */
        private void sampleCharge(long now) {
            if (chargeSampled || now - pressedAt < CHARGE_SAMPLE_DELAY) {
                return;
            }
            EntityActionInstance action = stand.getCurStandAction();
            helper.assertTrue(action instanceof WeatherReportFuguRainAbility.FuguRainAction
                            && action.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Fugu Rain is not charging " + CHARGE_SAMPLE_DELAY + " ticks after the press (" + aim.id
                            + "): action=" + action + ", phase=" + (action == null ? "none" : action.getPhase()));
            float[] expected = standRotation(stand.getEyePosition());
            chargeSampled = true;
            chargeFacesAim = Math.abs(Mth.degreesDifference(stand.getXRot(), expected[0])) < ANGLE_TOLERANCE
                    && Math.abs(Mth.degreesDifference(stand.getYRot(), expected[1])) < ANGLE_TOLERANCE;
            chargeRotation = "(" + stand.getXRot() + ", " + stand.getYRot() + "), expected=(" + expected[0] + ", "
                    + expected[1] + "), userRot=(" + user.getXRot() + ", " + user.getYRot() + ")";
        }

        /** Where the turned Stand's ray from its eyes meets the target. */
        private Vec3 standHit(Vec3 eye) {
            float[] rotation = standRotation(eye);
            return hit(eye, Vec3.directionFromRotation(rotation[0], rotation[1]));
        }

        private BlockPos skyCell() {
            return BlockPos.containing(expectedHit.x, expectedHit.y + 0.5D, expectedHit.z);
        }

        private void arrange() {
            helper.assertTrue(user.isAlive() && power.getAbilityCooldown(ability.name()) == 0
                            && stand.getActiveFugaSummoner() == null,
                    "The fixture is not clean before the press");
            helper.assertTrue(Math.abs(stand.getAttackSpeed() / 16.0D * 18.0D - TURN_CAP) < 1.0E-6D,
                    "Weather Report's turn cap is not " + TURN_CAP + ": attackSpeed=" + stand.getAttackSpeed());
            Vec3 eye = user.getEyePosition();
            Vec3 standEye = stand.getEyePosition();
            if (aim.granted) {
                float[] needed = neededRotation(standEye);
                float neededXTurn = Mth.degreesDifference(user.getXRot(), needed[0]);
                float neededYTurn = Mth.degreesDifference(user.getYRot(), needed[1]);
                boolean turnFits = aim == Aim.STEEP_GROUND
                        ? Math.abs(neededXTurn) > TURN_CAP + 1.0F && Math.abs(neededYTurn) > TURN_CAP + 1.0F
                        : Math.max(Math.abs(neededXTurn), Math.abs(neededYTurn)) > 1.0F;
                helper.assertTrue(turnFits, "The fixture does not need the turn it tests: neededTurn=(" + neededXTurn
                        + ", " + neededYTurn + ")");
            }
            if (aim == Aim.MOB_BEHIND_WALL) {
                requireWallBetween(eye, userHit, "the user");
                requireWallBetween(standEye, expectedHit, "the Stand");
                return;
            }
            if (aim == Aim.MOB_PRIORITY) {
                Vec3 passiveHit = passiveHit(eye, user.getLookAngle());
                helper.assertTrue(passiveHit != null && passiveHit.distanceToSqr(eye) < userHit.distanceToSqr(eye) - 1.0D,
                        "The user's ray does not meet the passive mob before the hostile one: passiveHit=" + passiveHit
                                + ", hostileHit=" + userHit);
                float[] turned = standRotation(standEye);
                Vec3 passiveStandHit = passiveHit(standEye, Vec3.directionFromRotation(turned[0], turned[1]));
                helper.assertTrue(passiveStandHit != null
                                && passiveStandHit.distanceToSqr(standEye) < expectedHit.distanceToSqr(standEye) - 1.0D,
                        "The Stand's turned ray does not meet the passive mob before the hostile one: passiveHit="
                                + passiveStandHit + ", hostileHit=" + expectedHit);
                return;
            }
            double horizontal = Math.hypot(userHit.x - eye.x, userHit.z - eye.z);
            helper.assertTrue(user.getXRot() == aim.pitch
                            && (aim == Aim.STEEP_GROUND ? horizontal < 1.0D : horizontal > 9.0D && horizontal < 11.0D),
                    "The user does not look at the ground where the case needs it: horizontal=" + horizontal);
            helper.assertTrue(level.getBlockState(BlockPos.containing(expectedHit.x, expectedHit.y - 0.5D, expectedHit.z))
                            .is(Blocks.STONE),
                    "The expected hit is not on the floor: " + expectedHit);
            if (aim == Aim.SNOW_GROUND) {
                helper.assertTrue(level.getBlockState(BlockPos.containing(expectedHit.x, expectedHit.y - 0.05D,
                                expectedHit.z)).is(Blocks.SNOW),
                        "The expected hit is not on the snow: " + expectedHit);
            }
        }

        private <T extends Mob> T addStillMob(EntityType<T> type, int z) {
            T added = type.create(level);
            helper.assertTrue(added != null, "Could not create a " + type.toShortString());
            added.moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + z + 0.5D, 180.0F, 0.0F);
            added.setNoAi(true);
            added.setNoGravity(true);
            added.setInvulnerable(true);
            added.setSilent(true);
            added.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(added), "Could not add a " + type.toShortString());
            return added;
        }

        private void requireWallBetween(Vec3 from, Vec3 to, String who) {
            HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    user));
            helper.assertTrue(wall.getType() == HitResult.Type.BLOCK
                            && wall.getLocation().distanceToSqr(from) < to.distanceToSqr(from) - 1.0D,
                    "No wall stands between " + who + " and the mob: wall=" + wall.getLocation());
        }

        /** The steps of ClAbilityInputPacket.Handler.handlePress for a HOLD ability. */
        private void press() {
            float xRotBefore = stand.getXRot();
            float yRotBefore = stand.getYRot();
            AbilityConditionCheck check = power.updateAvailableMoves().getContextVariationContainer(ability);
            // The port runs condition checks every tick, so they must leave the Stand's rotation alone; the turn that
            // counts is the perform's.
            checkKeptRotation = stand.getXRot() == xRotBefore && stand.getYRot() == yRotBefore;
            checkRotation = "(" + xRotBefore + ", " + yRotBefore + ") -> (" + stand.getXRot() + ", " + stand.getYRot()
                    + ")";
            helper.assertTrue(check != null && check.ability == ability,
                    "fugu_rain is not available in the server moveset");
            if (check.conditionCheck.getWarning() != null
                    && check.conditionCheck.getWarning().getContents() instanceof TranslatableContents message) {
                refusal = message.getKey();
            }
            admitted = AbilityInput.withConditionCheck(check, user, InputMethod.HOLD);
            if (admitted) {
                AbilityInput.keyPress(KEY, check.ability, user, null, InputMethod.HOLD, 0.0F,
                        BufferingState.clickCanBuffer(), ability.getAbilityId());
            }
        }

        private void judge() {
            WeatherReportFugaSummonerEntity storm = stand.getActiveFugaSummoner();
            int cooldown = power.getAbilityCooldown(ability.name());
            // The storm appeared during this tick's perform; the Stand has not moved since.
            Vec3 standEye = stand.getEyePosition();
            expectedHit = standHit(standEye);
            String outcome = "admitted=" + admitted + ", refusal=" + refusal + ", storm="
                    + (storm == null ? "none" : storm.position().toString()) + ", expectedHit=" + expectedHit
                    + ", userHit=" + userHit + ", standEye=" + standEye + ", cooldown=" + cooldown
                    + ", checkRotation=" + checkRotation + ", chargeRotation=" + chargeRotation;
            boolean passed;
            String failure;
            if (aim.granted) {
                passed = admitted && storm != null && expectedHit != null
                        && Math.abs(storm.getX() - expectedHit.x) < 0.05D
                        && Math.abs(storm.getZ() - expectedHit.z) < 0.05D
                        && Math.abs(storm.getY() - (expectedHit.y + WeatherReportFuguRainAbility.SUMMON_HEIGHT)) < 0.05D
                        && cooldown > 0;
                failure = "A cast did not start a storm over the point the turned Stand's ray hits";
            }
            else {
                passed = !admitted && storm == null && SKY_KEY.equals(refusal) && cooldown == 0;
                failure = "A cast at ground under a roof was not refused with the open-space message";
            }
            close();
            helper.assertTrue(passed, failure + " (" + aim.id + "): " + outcome);
            helper.assertTrue(checkKeptRotation, "The condition check turned the Stand (" + aim.id + "): " + outcome);
            helper.assertTrue(!aim.granted || chargeSampled && chargeFacesAim,
                    "Weather Report did not face its aim during the charge (" + aim.id + "): " + outcome);
            LOGGER.info("R767_WR_AIM_PASS {} {}", aim.id, outcome);
            helper.succeed();
        }

        private void put(BlockPos pos, BlockState state) {
            BlockPos key = pos.immutable();
            placed.putIfAbsent(key, level.getBlockState(key));
            helper.assertTrue(level.setBlock(key, state, 3), "Could not place a fixture block at " + key);
        }

        private void close() {
            try {
                if (user != null) {
                    AbilityInput.keyRelease(KEY, user);
                    LivingComponentAction.getComponent(user).setAction(null, user, SyncType.NO_SYNC);
                    LivingComponentAction.getComponent(user, PowerClass.STAND).setAction(null, user, SyncType.NO_SYNC);
                }
                if (stand != null) {
                    WeatherReportFugaSummonerEntity storm = stand.getActiveFugaSummoner();
                    if (storm != null) {
                        storm.discard();
                    }
                }
                if (mob != null) {
                    mob.discard();
                }
                if (passive != null) {
                    passive.discard();
                }
                if (user != null) {
                    if (power != null && power.isSummoned()) {
                        WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
                    }
                    user.discard();
                }
            }
            finally {
                level.setWeatherParameters(12000, 0, false, false);
                level.setRainLevel(0.0F);
                for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                }
                placed.clear();
            }
        }
    }
}
