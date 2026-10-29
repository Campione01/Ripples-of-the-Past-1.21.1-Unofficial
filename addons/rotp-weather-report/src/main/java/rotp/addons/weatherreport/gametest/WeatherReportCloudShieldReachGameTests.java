package rotp.addons.weatherreport.gametest;

import java.util.LinkedHashMap;
import java.util.List;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.ability.WeatherReportGiveCloudShieldAbility;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16.5 Action.checkRangeAndTarget admitted an entity target within 8 blocks of the performer (the summoned Stand,
 * else the user), or within 4 when the performer could not see it, and then performed at once.
 * Each case presses give_cloud_shield the way ClAbilityInputPacket does: the client's aim target is applied, the
 * ability's condition is checked, and the key press starts the action.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportCloudShieldReachGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final short KEY = 31;
    private static final int ARRANGE_TICK = 12;
    private static final int STAND_SETTLE_DEADLINE = 80;
    private static final int OBSERVE_TICKS = 8;
    private static final String TOO_FAR_KEY = "jojo.message.action_condition.target_too_far";

    private WeatherReportCloudShieldReachGameTests() {}

    @GameTest(template = "empty", batch = "wr_reach_visible_far", timeoutTicks = 200)
    public static void visibleTargetAtSixBlocksGetsTheShield(GameTestHelper helper) {
        new Fixture(helper, Scenario.VISIBLE_FAR).start();
    }

    @GameTest(template = "empty", batch = "wr_reach_occluded_far_aim", timeoutTicks = 200)
    public static void unseenTargetAtSixBlocksIsTooFarForTheClientAim(GameTestHelper helper) {
        new Fixture(helper, Scenario.OCCLUDED_FAR_AIM).start();
    }

    @GameTest(template = "empty", batch = "wr_reach_occluded_far_look", timeoutTicks = 200)
    public static void unseenTargetAtSixBlocksIsTooFarForTheServerLook(GameTestHelper helper) {
        new Fixture(helper, Scenario.OCCLUDED_FAR_LOOK).start();
    }

    @GameTest(template = "empty", batch = "wr_reach_occluded_near", timeoutTicks = 200)
    public static void unseenTargetWithinFourBlocksStillGetsTheShield(GameTestHelper helper) {
        new Fixture(helper, Scenario.OCCLUDED_NEAR).start();
    }

    @GameTest(template = "empty", batch = "wr_reach_admitted_then_occluded", timeoutTicks = 200)
    public static void targetHiddenAfterThePressStillGetsTheShield(GameTestHelper helper) {
        new Fixture(helper, Scenario.ADMITTED_THEN_OCCLUDED).start();
    }

    @GameTest(template = "empty", batch = "wr_reach_stand_sees", timeoutTicks = 200)
    public static void summonedStandThatSeesTheTargetKeepsTheFullRange(GameTestHelper helper) {
        new Fixture(helper, Scenario.STAND_SEES_FAR).start();
    }

    @GameTest(template = "empty", batch = "wr_reach_stand_blocked", timeoutTicks = 200)
    public static void summonedStandThatCannotSeeTheTargetHalvesTheRange(GameTestHelper helper) {
        new Fixture(helper, Scenario.STAND_BLOCKED_FAR).start();
    }

    /*
     * 1.16 WeatherReportGiveCloudShield (TargetRequirement.ENTITY, 042 :36-38): the HUD (ActionsOverlayGui
     * .actionAvailability) and the client's press (PowerBaseImpl.checkRequirements) checked it at the client's mouse
     * target. While local the user stands in for its own client's copy: isLocalPlayer, no stored aim, the aim it sends
     * in LivingComponentAction.CLIENT_PLAYER_AIM. It looks up at the sky, so that only the aim names the cow.
     */
    @GameTest(template = "empty", batch = "wr_reach_own_client", timeoutTicks = 40)
    public static void giftOnTheUsersOwnClientSeesTheAimItSendsAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean[] local = {false};
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wr-own-client")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isLocalPlayer() {
                return local[0];
            }
        };
        Cow cow = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 3.0D, 1.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, -90.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");
            StandPower power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Weather Report to the user");
            power.setStamina(power.getMaxStamina());
            Ability gift = power.getAbility(WeatherReportAddon.GIVE_CLOUD_SHIELD_NAME);
            helper.assertTrue(gift instanceof WeatherReportGiveCloudShieldAbility && gift.isAbilityUnlocked(power),
                    "give_cloud_shield is not an unlocked moveset ability: " + gift);
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not create the target");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(pos.x, pos.y, pos.z + 3.0D, 180.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the target");

            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
            ConditionCheck server = gift.checkConditions(power);
            helper.assertTrue(server.isPositive(), "fixture: on the server the gift at the cow is " + why(server));
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            helper.assertTrue(!gift.checkConditions(power).isPositive(),
                    "fixture: the gift is usable looking up with no aim, so its check needs one");
            local[0] = true;
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(new ActionTarget(cow));
            ConditionCheck client = gift.checkConditions(power);
            helper.assertTrue(client.isPositive(), "1.16's client checked the cloud shield gift at its mouse target, the"
                    + " cow, and showed it usable as the server took it; the port's own client finds it " + why(client)
                    + ", reading no aim of its copy of the user");
            LOGGER.info("R770_WR_OWN_CLIENT_PASS cloud_shield_gift server={} client={}", why(server), why(client));
        }
        finally {
            local[0] = false;
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
        helper.succeed();
    }

    private static String why(ConditionCheck check) {
        if (check.isPositive()) {
            return "usable";
        }
        return check.getWarning() == null ? "refused without a message"
                : "refused (" + (check.getWarning().getContents() instanceof TranslatableContents contents
                        ? contents.getKey() : check.getWarning().getString()) + ")";
    }

    private enum Hidden { FROM_NOBODY, FROM_USER, FROM_USER_ONLY, FROM_STAND_ONLY }

    private enum Scenario {
        VISIBLE_FAR("visible_far", 6.0D, Hidden.FROM_NOBODY, true, false, false, true),
        OCCLUDED_FAR_AIM("occluded_far_aim", 6.0D, Hidden.FROM_USER, true, false, false, false),
        OCCLUDED_FAR_LOOK("occluded_far_look", 6.0D, Hidden.FROM_USER, false, false, false, false),
        OCCLUDED_NEAR("occluded_near", 3.5D, Hidden.FROM_USER, true, false, false, true),
        ADMITTED_THEN_OCCLUDED("admitted_then_occluded", 6.0D, Hidden.FROM_USER, true, false, true, true),
        STAND_SEES_FAR("stand_sees_far", 6.0D, Hidden.FROM_USER_ONLY, true, true, false, true),
        STAND_BLOCKED_FAR("stand_blocked_far", 6.0D, Hidden.FROM_STAND_ONLY, true, true, false, false);

        final String id;
        final double separation;
        final Hidden hidden;
        final boolean clientAim;
        final boolean standOut;
        final boolean occludeAfterPress;
        final boolean granted;

        Scenario(String id, double separation, Hidden hidden, boolean clientAim, boolean standOut,
                boolean occludeAfterPress, boolean granted) {
            this.id = id;
            this.separation = separation;
            this.hidden = hidden;
            this.clientAim = clientAim;
            this.standOut = standOut;
            this.occludeAfterPress = occludeAfterPress;
            this.granted = granted;
        }
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final Scenario scenario;
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        private BlockPos userCell;
        private Player user;
        private Cow cow;
        private StandPower power;
        private StandEntity stand;
        private Ability ability;
        private Vec3 standLastPos;
        private int standStillTicks;
        private long arrangedAt = -1L;
        private long pressedAt = -1L;
        private boolean admitted;
        private String refusal = "none";
        private String giftSlot = "none";
        private float staminaBefore;
        private String geometry = "";
        private boolean finished;

        private Fixture(GameTestHelper helper, Scenario scenario) {
            this.helper = helper;
            this.scenario = scenario;
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
            // GameTest keeps only the template's own chunk entity-ticking, so everything stays inside it.
            userCell = new BlockPos(chunk.getMinBlockX() + 8, template.getY() + 2, chunk.getMinBlockZ() + 2);
            for (BlockPos pos : BlockPos.betweenClosed(userCell.offset(-3, -1, -2), userCell.offset(3, 4, 8))) {
                helper.assertTrue(level.isEmptyBlock(pos), "The reach fixture space is not empty at " + pos);
            }
            helper.assertTrue(level.isPositionEntityTicking(userCell)
                            && level.isPositionEntityTicking(userCell.offset(0, 0, 7)),
                    "The reach fixture is outside the entity-ticking test chunk");

            user = new Player(level, BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "wr-" + scenario.ordinal())) {
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
            user.moveTo(userCell.getX() + 0.5D, userCell.getY(), userCell.getZ() + 0.5D, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");

            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant Weather Report to the user");
            ability = power.getAbility(WeatherReportAddon.GIVE_CLOUD_SHIELD_NAME);
            helper.assertTrue(ability instanceof WeatherReportGiveCloudShieldAbility
                            && ability.isAbilityUnlocked(power),
                    "give_cloud_shield is not an unlocked moveset ability: " + ability);
            if (scenario.standOut) {
                helper.assertTrue(WeatherReportAddon.WEATHER_REPORT.get().summon(user, power)
                                && power.getSummonedStandEntity() != null,
                        "Production Weather Report summon failed");
                stand = power.getSummonedStandEntity();
            }

            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not create the target");
            cow.setNoAi(true);
            cow.setPersistenceRequired();
            cow.moveTo(userCell.getX() + 0.5D, userCell.getY(), userCell.getZ() + 0.5D + scenario.separation,
                    180.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the target");
        }

        private void tick() {
            if (finished) {
                return;
            }
            try {
                long now = helper.getTick();
                if (arrangedAt < 0L) {
                    if (now >= ARRANGE_TICK && standSettled(now)) {
                        arrange();
                        arrangedAt = now;
                    }
                }
                else if (pressedAt < 0L) {
                    press();
                    pressedAt = now;
                }
                else if (now - pressedAt >= OBSERVE_TICKS) {
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

        private boolean standSettled(long now) {
            if (stand == null) {
                return true;
            }
            helper.assertTrue(now < STAND_SETTLE_DEADLINE,
                    "The summoned Stand never came to rest: still=" + standStillTicks + ", pos=" + stand.position());
            Vec3 pos = stand.position();
            standStillTicks = stand.isAddedToLevel() && pos.equals(standLastPos) ? standStillTicks + 1 : 0;
            standLastPos = pos;
            return standStillTicks >= 5;
        }

        /** The world as the client last reported it, one tick before the press. */
        private void arrange() {
            helper.assertTrue(user.isAlive() && cow.isAlive()
                            && !cow.hasEffect(WeatherReportAddon.CLOUD_SHIELD_EFFECT)
                            && power.getAbilityCooldown(ability.name()) == 0,
                    "The fixture is not clean before the press");
            if (power.getStamina() < WeatherReportGiveCloudShieldAbility.STAMINA_COST * 2.0F) {
                power.setStamina(power.getMaxStamina());
            }
            helper.assertTrue(power.getStamina() >= WeatherReportGiveCloudShieldAbility.STAMINA_COST,
                    "The user cannot pay for the shield: stamina=" + power.getStamina());
            helper.assertTrue(user.hasLineOfSight(cow) && (stand == null || stand.hasLineOfSight(cow)),
                    "The target is hidden before any occluder is placed");

            if (!scenario.occludeAfterPress) {
                occlude();
            }
            if (scenario.clientAim) {
                setClientAim(new ActionTarget(cow));
            }
            else {
                setClientAim(ActionTarget.EMPTY);
                lookAtCowBody();
            }
            if (!scenario.occludeAfterPress) {
                requireGeometry();
            }
        }

        private void occlude() {
            switch (scenario.hidden) {
                case FROM_NOBODY -> {}
                case FROM_USER -> {
                    // On the eye-to-eye line, above the ray to the lower body.
                    int dz = scenario.separation > 5.0D ? 4 : 2;
                    put(userCell.offset(0, 1, dz), Blocks.STONE.defaultBlockState());
                }
                case FROM_USER_ONLY -> searchOccluder(false, true);
                case FROM_STAND_ONLY -> searchOccluder(true, false);
            }
        }

        /** Tries one occluding block after another until exactly the wanted eyes lose the target. */
        private void searchOccluder(boolean userSees, boolean standSees) {
            List<BlockState> states = List.of(Blocks.STONE.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState());
            for (BlockPos mutable : BlockPos.betweenClosed(userCell.offset(-2, 0, -1), userCell.offset(2, 3, 4))) {
                BlockPos pos = mutable.immutable();
                AABB cell = new AABB(pos);
                if (cell.intersects(user.getBoundingBox()) || cell.intersects(stand.getBoundingBox())
                        || cell.intersects(cow.getBoundingBox()) || cell.contains(user.getEyePosition())
                        || cell.contains(stand.getEyePosition())) {
                    continue;
                }
                for (BlockState state : states) {
                    level.setBlock(pos, state, 3);
                    if (user.hasLineOfSight(cow) == userSees && stand.hasLineOfSight(cow) == standSees) {
                        placed.put(pos, Blocks.AIR.defaultBlockState());
                        return;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            throw new IllegalStateException("No single block hides the target from "
                    + (userSees ? "the Stand only" : "the user only") + ": userEye=" + user.getEyePosition()
                    + ", standEye=" + stand.getEyePosition() + ", cowEye=" + cow.getEyePosition());
        }

        private void put(BlockPos pos, BlockState state) {
            placed.put(pos.immutable(), level.getBlockState(pos));
            helper.assertTrue(level.setBlock(pos, state, 3), "Could not place the occluder");
        }

        /** ClAimTargetPacket and ClAbilityInputPacket store the client's target without any check of their own. */
        private void setClientAim(ActionTarget target) {
            LivingComponentAction.getComponent(user).entityAim.setTarget(target);
            if (stand != null) {
                LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
            }
        }

        private void lookAtCowBody() {
            AABB box = cow.getBoundingBox();
            Vec3 point = new Vec3(box.getCenter().x, box.minY + 0.2D, box.minZ);
            Vec3 direction = point.subtract(user.getEyePosition());
            user.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            user.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
            user.setYHeadRot(user.getYRot());
            user.yBodyRot = user.getYRot();
        }

        /** The donor's reach, computed here on its own (1.16.5 JojoModUtil.getDistance and Action.checkRangeAndTarget). */
        private void requireGeometry() {
            LivingEntity performer = stand != null ? stand : user;
            boolean userSees = user.hasLineOfSight(cow);
            boolean performerSees = performer.hasLineOfSight(cow);
            double distance = donorDistance(performer, cow.getBoundingBox());
            double rangeSq = performerSees ? 64.0D : 16.0D;
            boolean donorAdmits = distance * distance <= rangeSq;
            geometry = "separation=" + scenario.separation + ", performer=" + (stand != null ? "stand" : "user")
                    + ", donorDistance=" + String.format("%.3f", distance) + ", userSees=" + userSees
                    + ", performerSees=" + performerSees + ", donorAdmits=" + donorAdmits;
            boolean wantUserSees = scenario.hidden == Hidden.FROM_NOBODY || scenario.hidden == Hidden.FROM_STAND_ONLY;
            boolean wantPerformerSees = scenario.hidden == Hidden.FROM_NOBODY || scenario.hidden == Hidden.FROM_USER_ONLY;
            helper.assertTrue(userSees == wantUserSees && performerSees == wantPerformerSees,
                    "The occluder does not hide the target as the case needs: " + geometry);
            // Within the port's former flat 8-block box reach in every case, so only the donor rule separates them.
            helper.assertTrue(cow.getBoundingBox().distanceToSqr(user.getEyePosition()) < 64.0D
                            && distance >= 0.0D && distance < 8.0D,
                    "The target is not inside the flat 8-block reach: " + geometry);
            helper.assertTrue(scenario.occludeAfterPress || donorAdmits == scenario.granted,
                    "The case does not separate the donor reach: " + geometry);
            if (!scenario.clientAim) {
                Vec3 eye = user.getEyePosition();
                Vec3 hit = cow.getBoundingBox().clip(eye, eye.add(user.getLookAngle().scale(8.0D))).orElse(null);
                helper.assertTrue(hit != null && level.clip(new ClipContext(eye, hit, ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE, user)).getType() == HitResult.Type.MISS,
                        "The user's look does not reach the target's body: " + geometry);
            }
        }

        private static double donorDistance(Entity performer, AABB box) {
            Vec3 eye = performer.getEyePosition(1.0F);
            if (box.contains(eye)) {
                return 0.0D;
            }
            double eyeFraction = performer.getBbHeight() == 0.0F ? 0.0D
                    : (double) (performer.getEyeHeight() / performer.getBbHeight());
            Vec3 point = new Vec3(Mth.lerp(0.5D, box.minX, box.maxX), Mth.lerp(eyeFraction, box.minY, box.maxY),
                    Mth.lerp(0.5D, box.minZ, box.maxZ));
            return box.clip(eye, point).map(hit -> eye.distanceTo(hit) - (double) (performer.getBbWidth() / 2.0F))
                    .orElse(-1.0D);
        }

        /** The steps of ClAbilityInputPacket.Handler.handlePress after the aim target has been applied. */
        private void press() {
            if (scenario.occludeAfterPress) {
                helper.assertTrue(user.hasLineOfSight(cow), "The target is hidden before the press");
            }
            staminaBefore = power.getStamina();
            AbilityConditionCheck check = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(check != null && check.ability == ability,
                    "give_cloud_shield is not available in the server moveset");
            if (check.conditionCheck.getWarning() != null
                    && check.conditionCheck.getWarning().getContents() instanceof TranslatableContents message) {
                refusal = message.getKey();
            }
            admitted = AbilityInput.withConditionCheck(check, user, InputMethod.CLICK);
            if (admitted) {
                AbilityInput.keyPress(KEY, check.ability, user, null, InputMethod.CLICK, 0.0F,
                        BufferingState.clickCanBuffer(), ability.getAbilityId());
                giftSlot = slotOfTheGift();
                if (scenario.occludeAfterPress) {
                    occlude();
                    requireGeometry();
                }
            }
        }

        /** 1.16's Stand power held its StandAction apart from the non-Stand power's (PowerBaseImpl.heldActionData). */
        private String slotOfTheGift() {
            boolean standPowerSlot = LivingComponentAction.getCurEntityAction(user, PowerClass.STAND)
                    instanceof WeatherReportGiveCloudShieldAbility.GiveCloudShieldAction;
            boolean ownSlot = LivingComponentAction.getCurEntityAction(user) != null;
            return standPowerSlot && !ownSlot ? "stand_power" : "standPower=" + standPowerSlot + "/own=" + ownSlot;
        }

        private void judge() {
            boolean shielded = cow.hasEffect(WeatherReportAddon.CLOUD_SHIELD_EFFECT);
            float stamina = power.getStamina();
            int cooldown = power.getAbilityCooldown(ability.name());
            String outcome = "admitted=" + admitted + ", shielded=" + shielded + ", refusal=" + refusal
                    + ", stamina=" + staminaBefore + "->" + stamina + ", cooldown=" + cooldown + ", giftSlot=" + giftSlot
                    + ", " + geometry;
            close();
            if (scenario.granted) {
                helper.assertTrue("stand_power".equals(giftSlot),
                        "The gift did not go to its user's Stand-power slot, apart from the user's own slot, as 1.16's"
                                + " Stand power held it (" + scenario.id + "): " + outcome);
                helper.assertTrue(admitted && shielded
                                && stamina <= staminaBefore - WeatherReportGiveCloudShieldAbility.STAMINA_COST * 0.75F
                                && cooldown > 0,
                        "A target inside the donor reach did not get the cloud shield (" + scenario.id + "): "
                                + outcome);
            }
            else {
                helper.assertTrue(!admitted && !shielded && stamina >= staminaBefore && cooldown == 0,
                        "A target outside the donor reach got the cloud shield (" + scenario.id + "): " + outcome);
                helper.assertTrue(TOO_FAR_KEY.equals(refusal),
                        "The refusal did not carry the donor's target_too_far message (" + scenario.id + "): "
                                + outcome);
            }
            LOGGER.info("R764_WR_REACH_PASS {} {}", scenario.id, outcome);
            helper.succeed();
        }

        private void close() {
            try {
                if (user != null) {
                    AbilityInput.keyRelease(KEY, user);
                    LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
                    LivingComponentAction.getComponent(user).setAction(null, user, SyncType.NO_SYNC);
                    LivingComponentAction.getComponent(user, PowerClass.STAND).setAction(null, user, SyncType.NO_SYNC);
                    if (power != null && power.isSummoned()) {
                        WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
                    }
                    user.discard();
                }
                if (cow != null && !cow.isRemoved()) {
                    cow.discard();
                }
            }
            finally {
                for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                }
                placed.clear();
            }
        }
    }
}
