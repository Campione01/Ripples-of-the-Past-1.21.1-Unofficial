package rotp.addons.weatherreport.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 WeatherReportLightning.standKeepsTarget kept the living entity it was pressed at in its StandEntityTask, and
 * StandEntity.taskOffset turned the default offset withYOffset(-0.75, 0.2, -0.75) by the yaw from the user's eyes to
 * that entity's eyes while the lightning charged, wherever the user looked.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportTaskTargetGameTests {
    private static final double EPS = 1.0E-4D;

    private WeatherReportTaskTargetGameTests() {}

    @GameTest(template = "empty", batch = "wr_task_target", timeoutTicks = 100)
    public static void lightningTurnsTheStandToTheMobItWasPressedAt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wr-task-target")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandPower power = null;
        Cow cow = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WeatherReportAddon.WEATHER_REPORT.get().summon(user, power),
                    "Could not summon Weather Report");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Setup: Weather Report is not a full Stand following its user");

            // a no-AI cow 3 blocks east of the user, who looks at it as the lightning is pressed
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not make a cow");
            cow.moveTo(pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
            turn(user, -90.0F, -90.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));

            helper.assertTrue(power.getAbility(WeatherReportAddon.LIGHTNING_NAME) instanceof EntityActionType,
                    "Setup: Weather Report has no lightning");
            EntityActionInstance lightning = ((EntityActionType) power.getAbility(WeatherReportAddon.LIGHTNING_NAME))
                    .initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(lightning, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == lightning && lightning.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Setup: the lightning is not charging: " + stand.getCurStandAction() + " " + lightning.getPhase());
            helper.assertTrue(lightning.getTaskTarget(level).getEntity() == cow,
                    "1.16 WeatherReportLightning.standKeepsTarget kept the cow it was pressed at; the task holds "
                            + lightning.getTaskTarget(level));

            // the user looks south with the body turned away, and nothing is aimed at any more
            turn(user, 0.0F, 50.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).tick();
            Vec3 relative = stand.offsetFromUser.getRelativeOffset();
            helper.assertTrue(relative.distanceTo(new Vec3(-0.75D, 0.2D, -0.75D)) < EPS
                            && stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD,
                    "Setup: the charging lightning does not show the default offset: " + relative + " "
                            + stand.offsetFromUser.getRotations());
            Vec3 toCow = cow.getEyePosition().subtract(user.getEyePosition());
            // 1.16 MathUtil.yRotDegFromVec
            double yawToCow = -Math.toDegrees(Math.atan2(toCow.x, toCow.z));
            Vec3 expected = relative.yRot((float) Math.toRadians(-yawToCow));
            stand.updatePosition(user);
            Vec3 actual = stand.position().subtract(user.position());
            helper.assertTrue(actual.distanceTo(expected) < EPS,
                    "1.16 put a Weather Report charging a lightning pressed at a cow 3 blocks east of its user, who"
                            + " then looks south, at its user's feet + " + expected + " (the default offset turned"
                            + " by the yaw to the cow), got " + actual);
            LogUtils.getLogger().info("R770_WR_TASK_TARGET_PASS lightning yaw={} offset={}", yawToCow, actual);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    /*
     * 1.16 StandEntityAction.overrideVanillaMouseTarget (PowerBaseImpl.checkTarget): the lightning pressed at a torch, a
     * block without a collision shape, took what a collision ray from the Stand's eyes met behind it, so its task kept the
     * cow there (standKeepsTarget: a living entity); with nothing behind, the press kept nothing. The Stand's eyes are
     * level with its user's, 0.35 ahead, looking the same way, so the ray runs along the user's crosshair line.
     */
    @GameTest(template = "empty", batch = "wr_task_target_torch", timeoutTicks = 100)
    public static void lightningPressedAtATorchKeepsTheCowBehindAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wr-task-torch")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandPower power = null;
        Cow cow = null;
        Map<BlockPos, BlockState> replaced = new LinkedHashMap<>();
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
            turn(user, -90.0F, -90.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WeatherReportAddon.WEATHER_REPORT.get().summon(user, power),
                    "Could not summon Weather Report");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no Weather Report entity");

            Vec3 eyes = user.getEyePosition();
            BlockPos torch = BlockPos.containing(eyes).east();
            for (int east = 0; east <= 3; east++) {
                BlockPos at = torch.east(east);
                replaced.put(at, level.getBlockState(at));
                level.setBlock(at, east == 0
                        ? Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST)
                        : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            helper.assertTrue(level.getBlockState(torch).is(Blocks.WALL_TORCH)
                            && level.getBlockState(torch).getCollisionShape(level, torch).isEmpty(),
                    "Setup: no torch without a collision shape at " + torch);
            ActionTarget pressedAt = new ActionTarget(torch, Direction.WEST);
            EntityActionType lightningType = (EntityActionType) power.getAbility(WeatherReportAddon.LIGHTNING_NAME);

            placeAlongTheEyeLine(stand, user);
            LivingComponentAction.getComponent(user).entityAim.setTarget(pressedAt);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(pressedAt);
            EntityActionInstance alone = lightningType.initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(alone, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == alone
                            && alone.getTaskTarget(level).getType() == ActionTarget.TargetType.EMPTY,
                    "1.16's lightning pressed at a torch with nothing behind it kept no target; the port's task holds "
                            + alone.getTaskTarget(level));
            LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);

            // a no-AI cow 2 blocks east of the user's eyes, its box centred on the crosshair line
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not make a cow");
            cow.moveTo(eyes.x + 2.0D, eyes.y - EntityType.COW.getHeight() / 2.0D, eyes.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
            placeAlongTheEyeLine(stand, user);
            LivingComponentAction.getComponent(user).entityAim.setTarget(pressedAt);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(pressedAt);
            EntityActionInstance lightning = lightningType.initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(lightning, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == lightning && lightning.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Setup: the lightning is not charging: " + stand.getCurStandAction() + " " + lightning.getPhase());
            helper.assertTrue(lightning.getTaskTarget(level).getEntity() == cow,
                    "1.16's lightning pressed at a torch took the cow behind it (overrideVanillaMouseTarget) and kept it;"
                            + " the port's task holds " + lightning.getTaskTarget(level) + " (torch " + torch + ")");
            LogUtils.getLogger().info("R770_WR_TASK_TARGET_PASS lightning_torch torch={} cow={}", torch, cow.position());
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
            List<BlockPos> placed = new ArrayList<>(replaced.keySet());
            for (int i = placed.size() - 1; i >= 0; i--) {
                level.setBlock(placed.get(i), replaced.get(placed.get(i)), Block.UPDATE_CLIENTS);
            }
        }
    }

    // the Stand's eyes level with its user's, 0.35 along the user's look, looking the same way
    private static void placeAlongTheEyeLine(StandEntity stand, Player user) {
        Vec3 eyes = user.getEyePosition().add(user.getLookAngle().scale(0.35D));
        stand.setCanFollowUser(false);
        stand.moveTo(eyes.x, eyes.y - stand.getEyeHeight(), eyes.z, user.getYRot(), user.getXRot());
        stand.setYHeadRot(user.getYHeadRot());
    }

    private static void turn(Player user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.setXRot(0.0F);
        user.xRotO = 0.0F;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }
}
