package rotp.addons.wonderofu.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.wonderofu.WonderOfUAddon;
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
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.MathUtil;

/**
 * 1.16 LightningStrike.standKeepsTarget kept the entity it was pressed at in its StandEntityTask, and
 * StandEntity.taskOffset turned the default offset withYOffset(-0.75, 0.2, -0.75) by the yaw from the user's eyes to
 * that entity's eyes while the lightning strike charged, wherever the user looked.
 */
@GameTestHolder("rotp_wou_car")
@PrefixGameTestTemplate(false)
public final class WonderOfUTaskTargetGameTests {
    private static final double EPS = 1.0E-4D;

    private WonderOfUTaskTargetGameTests() {}

    @GameTest(template = "empty", batch = "wou_task_target", timeoutTicks = 100)
    public static void lightningStrikeTurnsTheStandToTheMobItWasPressedAt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wou-task-target")) {
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
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Wonder of U user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WonderOfUAddon.WONDER_OF_YOU.get().summon(user, power),
                    "Could not summon Wonder of U");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Setup: Wonder of U is not a full Stand following its user");

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

            helper.assertTrue(power.getAbility("lightning_strike") instanceof EntityActionType,
                    "Setup: Wonder of U has no lightning strike");
            EntityActionInstance lightning = ((EntityActionType) power.getAbility("lightning_strike"))
                    .initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(lightning, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == lightning && lightning.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Setup: the lightning strike is not charging: " + stand.getCurStandAction() + " " + lightning.getPhase());
            helper.assertTrue(lightning.getTaskTarget(level).getEntity() == cow,
                    "1.16 LightningStrike.standKeepsTarget kept the cow it was pressed at; the task holds "
                            + lightning.getTaskTarget(level));

            // the user looks south with the body turned away, and nothing is aimed at any more
            turn(user, 0.0F, 50.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).tick();
            Vec3 relative = stand.offsetFromUser.getRelativeOffset();
            helper.assertTrue(relative.distanceTo(new Vec3(-0.75D, 0.2D, -0.75D)) < EPS
                            && stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD,
                    "Setup: the charging lightning strike does not show the default offset: " + relative + " "
                            + stand.offsetFromUser.getRotations());
            Vec3 toCow = cow.getEyePosition().subtract(user.getEyePosition());
            // 1.16 MathUtil.yRotDegFromVec
            double yawToCow = -Math.toDegrees(Math.atan2(toCow.x, toCow.z));
            Vec3 expected = relative.yRot((float) Math.toRadians(-yawToCow));
            stand.updatePosition(user);
            Vec3 actual = stand.position().subtract(user.position());
            helper.assertTrue(actual.distanceTo(expected) < EPS,
                    "1.16 put a Wonder of U charging a lightning strike pressed at a cow 3 blocks east of its user, who"
                            + " then looks south, at its user's feet + " + expected + " (the default offset turned"
                            + " by the yaw to the cow), got " + actual);
            LogUtils.getLogger().info("R770_WOU_TASK_TARGET_PASS lightning_strike yaw={} offset={}", yawToCow, actual);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    /**
     * 1.16 LightningStrike.standPerform aimed with aimWithStandOrUser(100, task.getTarget()): with the entity its task
     * kept, no ray from the user; the Stand, turned to that entity (StandEntityTask.rotateStand), cast its own, so the
     * bolt struck the kept mob after the user looked away, through a wall as the calamity does.
     */
    @GameTest(template = "empty", batch = "wou_task_target_aim", timeoutTicks = 100)
    public static void lightningStrikesTheMobItKeptAfterTheUserLooksAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wou-task-aim")) {
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
        List<Vec3> strikes = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> joinListener = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof LightningBolt) {
                strikes.add(event.getEntity().position());
                event.setCanceled(true);
            }
        };
        List<BlockPos> wall = new ArrayList<>();
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Wonder of U user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WonderOfUAddon.WONDER_OF_YOU.get().summon(user, power),
                    "Could not summon Wonder of U");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();

            // a no-AI cow 3.5 blocks east of the user, who looks at it as the lightning is pressed, a stone wall
            // between them
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not make a cow");
            cow.moveTo(pos.x + 3.5D, pos.y, pos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
            BlockPos userBlock = BlockPos.containing(pos);
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos block = userBlock.offset(1, dy, dz);
                    level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
                    wall.add(block);
                }
            }
            turn(user, -90.0F, -90.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));
            EntityActionInstance lightning = ((EntityActionType) power.getAbility("lightning_strike"))
                    .initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(lightning, user, SyncType.NO_SYNC);
            helper.assertTrue(lightning.getTaskTarget(level).getEntity() == cow,
                    "Setup: the lightning strike did not keep the cow: " + lightning.getTaskTarget(level));

            // the user looks south, at nothing, as the strike comes
            turn(user, 0.0F, 0.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            stand.updatePosition(user);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, joinListener);
            lightning.setPhaseStart(ActionPhase.PERFORM);
            lightning._tickAction();
            AABB mob = HitResultUtil.standPrecisionTargetHitbox(cow.getBoundingBox(), stand.getPrecision())
                    .inflate(0.01D);
            helper.assertTrue(!strikes.isEmpty() && mob.contains(strikes.get(0)),
                    "1.16 aimed the lightning from the Stand turned to the cow its task kept, so the bolt struck the cow"
                            + " (its precision hitbox) " + mob + " behind the wall after the user looked away; it struck at "
                            + (strikes.isEmpty() ? "nothing" : strikes.get(0)));
            LogUtils.getLogger().info("R770_WOU_TASK_TARGET_PASS lightning_strike_aim strike={}", strikes.get(0));
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(joinListener);
            for (BlockPos block : wall) {
                level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            }
            if (power != null && power.isSummoned()) {
                WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    /**
     * 1.16 LightningStrike.standPerform with no entity in its task: aimWithStandOrUser(100, task.getTarget()) turned the
     * Stand from its own rotation toward what the user's precision ray met, by at most getAttackSpeed() / 16 * 18
     * degrees on each axis (MCUtil.rotateTowards), then ray-traced from the Stand's eyes along that look. A Stand turned
     * far away therefore struck off the cow the user looked at; one the turn reached struck the cow.
     */
    @GameTest(template = "empty", batch = "wou_task_target_turn", timeoutTicks = 100)
    public static void lightningStrikeTurnsTheStandTowardTheUsersAimByAtMostItsSpeed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wou-task-turn")) {
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
        List<Vec3> strikes = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> joinListener = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof LightningBolt) {
                strikes.add(event.getEntity().position());
                event.setCanceled(true);
            }
        };
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Wonder of U user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WonderOfUAddon.WONDER_OF_YOU.get().summon(user, power),
                    "Could not summon Wonder of U");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);

            // a no-AI cow 6 blocks east across the user's eye line, who looks at it with nothing pressed at
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not make a cow");
            cow.moveTo(pos.x + 6.0D, pos.y + 0.5D, pos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
            turn(user, -90.0F, -90.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            EntityActionInstance lightning = ((EntityActionType) power.getAbility("lightning_strike"))
                    .initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(lightning, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == lightning
                            && lightning.getTaskTarget(level).isEmpty(level) && !stand.isManuallyControlled(),
                    "Setup: the lightning strike holds a target or the Stand is manually controlled");

            Vec3 standEyes = stand.getEyePosition();
            float yawToCow = MathUtil.yRotDegFromVec(cow.getEyePosition().subtract(standEyes));
            float maxAngle = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
            float away = Math.min(170.0F, maxAngle * 2.0F + 30.0F);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, joinListener);

            // turned further away than one turn reaches: the Stand turns by maxAngle and the bolt goes along that look
            stand.setYRot(yawToCow + away);
            stand.setXRot(0.0F);
            lightning.setPhaseStart(ActionPhase.PERFORM);
            lightning._tickAction();
            helper.assertTrue(strikes.size() == 1, "Setup: the first strike made " + strikes.size() + " bolts");
            float expectedYaw = yawToCow + away - maxAngle;
            float strikeYaw = MathUtil.yRotDegFromVec(strikes.get(0).subtract(standEyes));
            helper.assertTrue(Math.abs(Mth.wrapDegrees(strikeYaw - expectedYaw)) < 1.0F
                            && Math.abs(Mth.wrapDegrees(stand.getYRot() - expectedYaw)) < 0.01F,
                    "1.16 turned a Stand " + away + " degrees off its user's aim by at most " + maxAngle
                            + " degrees (to yaw " + expectedYaw + ") and cast the bolt along that look; the bolt went"
                            + " to yaw " + strikeYaw + " (" + strikes.get(0) + "), the Stand now looks at yaw "
                            + stand.getYRot() + ", the cow is at yaw " + yawToCow);

            // turned a part of one turn off: the turn reaches the cow's eyes and the bolt strikes the cow
            strikes.clear();
            stand.setYRot(yawToCow + maxAngle / 2.0F);
            stand.setXRot(0.0F);
            EntityActionInstance second = ((EntityActionType) power.getAbility("lightning_strike"))
                    .initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(second, user, SyncType.NO_SYNC);
            second.setPhaseStart(ActionPhase.PERFORM);
            second._tickAction();
            AABB mob = HitResultUtil.standPrecisionTargetHitbox(cow.getBoundingBox(), stand.getPrecision())
                    .inflate(0.01D);
            helper.assertTrue(strikes.size() == 1 && mob.contains(strikes.get(0)),
                    "1.16 turned a Stand " + maxAngle / 2.0F + " degrees off the user's aim onto the cow, so the bolt"
                            + " struck the cow " + mob + "; it struck at "
                            + (strikes.isEmpty() ? "nothing" : strikes.get(0)));
            LogUtils.getLogger().info("R770_WOU_TASK_TARGET_PASS lightning_strike_turn maxAngle={} yaw={}",
                    maxAngle, strikeYaw);
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(joinListener);
            if (power != null && power.isSummoned()) {
                WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntityAction.overrideVanillaMouseTarget (PowerBaseImpl.checkTarget): the lightning strike pressed at a
     * torch, a block without a collision shape, took what a collision ray from the Stand's eyes met behind it, so its task
     * kept the cow there (standKeepsTarget: an entity) and the bolt struck the cow; with nothing behind, the press kept
     * nothing. The Stand's eyes are level with its user's, 0.35 ahead, looking the same way.
     */
    @GameTest(template = "empty", batch = "wou_task_target_torch", timeoutTicks = 100)
    public static void lightningStrikePressedAtATorchStrikesTheCowBehindAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wou-task-torch")) {
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
        List<Vec3> strikes = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> joinListener = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof LightningBolt) {
                strikes.add(event.getEntity().position());
                event.setCanceled(true);
            }
        };
        Map<BlockPos, BlockState> replaced = new LinkedHashMap<>();
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
            turn(user, -90.0F, -90.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Wonder of U user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WonderOfUAddon.WONDER_OF_YOU.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WonderOfUAddon.WONDER_OF_YOU.get().summon(user, power),
                    "Could not summon Wonder of U");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no Wonder of U entity");

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
            EntityActionType strikeType = (EntityActionType) power.getAbility("lightning_strike");

            placeAlongTheEyeLine(stand, user);
            LivingComponentAction.getComponent(user).entityAim.setTarget(pressedAt);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(pressedAt);
            EntityActionInstance alone = strikeType.initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(alone, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == alone
                            && alone.getTaskTarget(level).getType() == ActionTarget.TargetType.EMPTY,
                    "1.16's lightning strike pressed at a torch with nothing behind it kept no target; the port's task"
                            + " holds " + alone.getTaskTarget(level));
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
            EntityActionInstance lightning = strikeType.initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(lightning, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == lightning && lightning.getTaskTarget(level).getEntity() == cow,
                    "1.16's lightning strike pressed at a torch took the cow behind it (overrideVanillaMouseTarget) and"
                            + " kept it; the port's task holds " + lightning.getTaskTarget(level) + " (torch " + torch + ")");

            // the user looks south, at nothing, as the strike comes
            turn(user, 0.0F, 0.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, joinListener);
            lightning.setPhaseStart(ActionPhase.PERFORM);
            lightning._tickAction();
            AABB mob = HitResultUtil.standPrecisionTargetHitbox(cow.getBoundingBox(), stand.getPrecision())
                    .inflate(0.01D);
            helper.assertTrue(!strikes.isEmpty() && mob.contains(strikes.get(0)),
                    "1.16's lightning strike pressed at a torch struck the cow behind it " + mob + "; it struck at "
                            + (strikes.isEmpty() ? "nothing" : strikes.get(0)));
            LogUtils.getLogger().info("R770_WOU_TASK_TARGET_PASS lightning_strike_torch torch={} strike={}", torch,
                    strikes.get(0));
            helper.succeed();
        }
        finally {
            NeoForge.EVENT_BUS.unregister(joinListener);
            if (power != null && power.isSummoned()) {
                WonderOfUAddon.WONDER_OF_YOU.get().forceUnsummon(user, power);
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
