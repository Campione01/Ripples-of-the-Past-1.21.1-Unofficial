package rotp.addons.tusk;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.target.ActionTarget;
import rotp.addons.tusk.ability.TuskInfiniteRotationAbility;
import rotp.addons.tusk.ability.TuskOpenSpaceAbility;
import rotp.addons.tusk.state.TuskNailState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TuskOpenSpace (TargetRequirement.BLOCK) kept the block it was pressed at in its StandEntityTask, and
 * StandEntity.taskOffset turned the act's offset by the yaw from the user's eyes to that block's centre while the
 * task held it, wherever the user looked.
 */
@GameTestHolder(TuskAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TuskTaskTargetGameTests {
    private static final double EPS = 1.0E-4D;

    private TuskTaskTargetGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void openSpaceTurnsTheStandToTheBlockItWasPressedAt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "tusk-task-target")) {
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
        BlockPos block = null;
        BlockState replaced = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Tusk user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(TuskAddon.TUSK_ACT_4.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && TuskAddon.TUSK_ACT_4.get().summon(user, power),
                    "Could not summon Tusk Act 4");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Setup: Tusk Act 4 is not a full Stand following its user");

            // a stone block 3 blocks east of the user at eye height, looked at as Open Space is pressed
            block = BlockPos.containing(pos.x + 3.0D, pos.y + 1.0D, pos.z);
            replaced = level.getBlockState(block);
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            turn(user, -90.0F, -90.0F);
            ActionTarget pressedAt = new ActionTarget(block, Direction.WEST);
            LivingComponentAction.getComponent(user).entityAim.setTarget(pressedAt);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(pressedAt);

            helper.assertTrue(power.getAbility(TuskAddon.OPEN_SPACE_NAME) instanceof EntityActionType,
                    "Setup: Tusk Act 4 has no Open Space");
            EntityActionInstance openSpace = ((EntityActionType) power.getAbility(TuskAddon.OPEN_SPACE_NAME))
                    .initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(openSpace, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == openSpace && !openSpace.isOver(),
                    "Setup: Open Space did not start: " + stand.getCurStandAction());
            ActionTarget held = openSpace.getTaskTarget(level);
            helper.assertTrue(held.getType() == ActionTarget.TargetType.BLOCK && block.equals(held.getBlockPos()),
                    "1.16 TuskOpenSpace (TargetRequirement.BLOCK) kept the block it was pressed at; the task holds "
                            + held);

            // the user looks south with the body turned away, and aims at nothing any more
            turn(user, 0.0F, 50.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            Vec3 relative = stand.offsetFromUser.getRelativeOffset();
            helper.assertTrue(relative.distanceTo(stand.offsetFromUser.idleOffset) < EPS
                            && stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD,
                    "Setup: Open Space does not show the act's offset: " + relative + " "
                            + stand.offsetFromUser.getRotations());
            Vec3 toBlock = Vec3.atCenterOf(block).subtract(user.getEyePosition());
            // 1.16 MathUtil.yRotDegFromVec
            double yawToBlock = -Math.toDegrees(Math.atan2(toBlock.x, toBlock.z));
            Vec3 expected = relative.yRot((float) Math.toRadians(-yawToBlock));
            stand.updatePosition(user);
            Vec3 actual = stand.position().subtract(user.position());
            helper.assertTrue(actual.distanceTo(expected) < EPS,
                    "1.16 put a Tusk Act 4 using Open Space on a block 3 blocks east of its user, who then looks south,"
                            + " at its user's feet + " + expected + " (the act's offset turned by the yaw to the block),"
                            + " got " + actual);
            LogUtils.getLogger().info("R770_TUSK_TASK_TARGET_PASS open_space yaw={} offset={}", yawToBlock, actual);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                TuskAddon.TUSK_ACT_4.get().forceUnsummon(user, power);
            }
            if (block != null && replaced != null) {
                level.setBlockAndUpdate(block, replaced);
            }
            user.discard();
        }
    }

    // 1.16 PowerBaseImpl.checkTarget judged the pressed target: TuskOpenSpace's TargetRequirement BLOCK (:44-46) refused
    // every target but a block; a refused one left the action without a target (core K5-26 C:
    // EntityActionInstance.captureActionTargetFromAim asks takesPressedTarget). TuskInfiniteRotation.checkStandTarget
    // (056 :175-177) refused every one, but the owner kept the port's transfer to the pressed mob: the core default rule.
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pressedTargetsFollowTheDonorsTargetChecks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "tusk-pressed-target")) {
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
        Zombie zombie = null;
        Wolf ownWolf = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Tusk user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(TuskAddon.TUSK_ACT_4.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && TuskAddon.TUSK_ACT_4.get().summon(user, power),
                    "Could not summon Tusk Act 4");
            power.skipProgression();
            StandEntity stand = power.getSummonedStandEntity();
            zombie = EntityType.ZOMBIE.create(level);
            helper.assertTrue(zombie != null, "Setup: no zombie");
            zombie.setNoAi(true);
            zombie.moveTo(pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(zombie) && stand.canAttack(zombie),
                    "Setup: the zombie is not a target Tusk Act 4 may attack");
            ActionTarget atZombie = new ActionTarget(zombie);
            ActionTarget atBlock = new ActionTarget(BlockPos.containing(pos.x + 3.0D, pos.y - 1.0D, pos.z), Direction.UP);

            helper.assertTrue(power.getAbility(TuskAddon.INFINITE_ROTATION_NAME)
                            instanceof TuskInfiniteRotationAbility
                            && power.getAbility(TuskAddon.OPEN_SPACE_NAME) instanceof TuskOpenSpaceAbility,
                    "Setup: Tusk Act 4 has no Infinite Rotation or Open Space");
            TuskInfiniteRotationAbility infiniteRotation =
                    (TuskInfiniteRotationAbility) power.getAbility(TuskAddon.INFINITE_ROTATION_NAME);
            TuskOpenSpaceAbility openSpace = (TuskOpenSpaceAbility) power.getAbility(TuskAddon.OPEN_SPACE_NAME);
            helper.assertTrue(infiniteRotation.takesPressedTarget(stand, atZombie),
                    "Infinite Rotation hands its spin to the mob it was pressed at (a port addition the owner kept,"
                            + " owner answers 2026-10-16 Wave 15 Q2), so it takes a hostile mob as its pressed target");
            ownWolf = new Wolf(EntityType.WOLF, level) {
                // the level finds a real owner, a server player, by its UUID; this mock user is not one
                @Override
                public LivingEntity getOwner() {
                    return user;
                }
            };
            ownWolf.setNoAi(true);
            ownWolf.setTame(true, false);
            ownWolf.setOwnerUUID(user.getUUID());
            ownWolf.moveTo(pos.x - 3.0D, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(ownWolf) && !stand.canAttack(ownWolf),
                    "Setup: the user's own wolf is a target Tusk Act 4 may attack");
            helper.assertFalse(infiniteRotation.takesPressedTarget(stand, new ActionTarget(ownWolf)),
                    "Infinite Rotation took the user's own wolf as its pressed target; the Stand may not attack it"
                            + " (StandEntity.canAttack, core K5-26 C)");
            helper.assertFalse(openSpace.takesPressedTarget(stand, atZombie),
                    "1.16 TuskOpenSpace (TargetRequirement BLOCK) took no entity as its pressed target");
            helper.assertTrue(openSpace.takesPressedTarget(stand, atBlock),
                    "1.16 TuskOpenSpace (TargetRequirement BLOCK) took the block it was pressed at");
            LogUtils.getLogger().info("R770_TUSK_TASK_TARGET_PASS pressed_targets");
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                TuskAddon.TUSK_ACT_4.get().forceUnsummon(user, power);
            }
            if (zombie != null) {
                zombie.discard();
            }
            if (ownWolf != null) {
                ownWolf.discard();
            }
            user.discard();
        }
    }

    // 1.16 StandEntityAction.overrideVanillaMouseTarget let a pressed block without a collision shape give way to the mob
    // behind it only for an action that takes an entity: TuskInfiniteRotation (no requirement) took the cow behind a
    // torch, TuskOpenSpace (TargetRequirement BLOCK) kept the torch, which its task held and its Stand turned to. The
    // Stand's eyes are level with its user's, just south of the user's box, looking south as the user does.
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void openSpacePressedAtATorchKeepsTheTorchAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "tusk-open-space-torch")) {
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
        BlockPos torch = null;
        BlockState replaced = null;
        Cow cow = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            user.setYHeadRot(0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Tusk user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(TuskAddon.TUSK_ACT_4.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && TuskAddon.TUSK_ACT_4.get().summon(user, power),
                    "Could not summon Tusk Act 4");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(pos.x, user.getEyeY() - stand.getEyeHeight(), pos.z + 0.35D, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);

            torch = BlockPos.containing(user.getEyePosition()).south();
            replaced = level.getBlockState(torch);
            level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Setup: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(pos.x, user.getEyeY() - cow.getBbHeight() / 2.0D, pos.z + 2.0D, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Setup: the cow was not added");
            ActionTarget atTorch = new ActionTarget(torch, Direction.NORTH);

            TuskInfiniteRotationAbility infiniteRotation =
                    (TuskInfiniteRotationAbility) power.getAbility(TuskAddon.INFINITE_ROTATION_NAME);
            helper.assertTrue(infiniteRotation.overridePressedTarget(user, atTorch).getEntity() == cow,
                    "Setup: the Stand's collision ray does not pass the torch to the cow; Infinite Rotation takes "
                            + infiniteRotation.overridePressedTarget(user, atTorch));
            TuskOpenSpaceAbility openSpace = (TuskOpenSpaceAbility) power.getAbility(TuskAddon.OPEN_SPACE_NAME);
            ActionTarget kept = openSpace.overridePressedTarget(user, atTorch);
            helper.assertTrue(kept.getType() == ActionTarget.TargetType.BLOCK && torch.equals(kept.getBlockPos()),
                    "1.16 TuskOpenSpace (TargetRequirement BLOCK) kept the torch it was pressed at; the port's press let"
                            + " it give way to " + kept);

            LivingComponentAction.getComponent(user).entityAim.setTarget(atTorch);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(atTorch);
            EntityActionInstance action = openSpace.initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == action && !action.isOver(),
                    "Setup: Open Space did not start: " + stand.getCurStandAction());
            ActionTarget held = action.getTaskTarget(level);
            helper.assertTrue(held.getType() == ActionTarget.TargetType.BLOCK && torch.equals(held.getBlockPos()),
                    "1.16's Open Space pressed at a torch with a cow behind it held the torch; the port's task holds "
                            + held);
            ActionTarget turnedTo = action.standRotationTarget != null ? action.standRotationTarget : ActionTarget.EMPTY;
            helper.assertTrue(turnedTo.getType() == ActionTarget.TargetType.BLOCK && torch.equals(turnedTo.getBlockPos()),
                    "1.16 turned Tusk Act 4 to the torch its Open Space was pressed at; the port turns it to " + turnedTo);
            LogUtils.getLogger().info("R770_TUSK_TASK_TARGET_PASS open_space_torch");
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                TuskAddon.TUSK_ACT_4.get().forceUnsummon(user, power);
            }
            if (torch != null && replaced != null) {
                level.setBlock(torch, replaced, Block.UPDATE_CLIENTS);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    // 1.16 TuskNailScratch (TargetRequirement ENTITY, 056 :71-73): the HUD (ActionsOverlayGui.actionAvailability) and
    // the client's press (PowerBaseImpl.checkRequirements) checked it at the client's mouse target. While local the user
    // stands in for its own client's copy: isLocalPlayer, no stored aim, the aim it sends in
    // LivingComponentAction.CLIENT_PLAYER_AIM.
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nailScratchOnTheUsersOwnClientSeesTheAimItSendsAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean[] local = {false};
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "tusk-own-client-aim")) {
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
        StandPower power = null;
        Cow cow = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Tusk user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(TuskAddon.TUSK.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && TuskAddon.TUSK.get().summon(user, power),
                    "Could not summon Tusk Act 1");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            TuskAddon.nailState(user).applySnapshot(TuskNailState.MAX_NAILS, 0, false);
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Setup: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(pos.x, pos.y, pos.z + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Setup: the cow was not added");
            Ability scratch = power.getAbility("tusk_nail_scratch");
            helper.assertTrue(scratch != null, "Setup: Tusk Act 1 has no Nail Scratch");

            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
            ConditionCheck server = scratch.checkConditions(power);
            helper.assertTrue(server.isPositive(), "Setup: on the server the Nail Scratch at the cow is " + why(server));
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            helper.assertTrue(!scratch.checkConditions(power).isPositive(),
                    "Setup: the Nail Scratch is usable with no aim, so its check needs none");
            local[0] = true;
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(new ActionTarget(cow));
            ConditionCheck client = scratch.checkConditions(power);
            helper.assertTrue(client.isPositive(), "1.16's client checked the Nail Scratch at its mouse target, the cow,"
                    + " and showed it usable as the server took it; the port's own client finds it " + why(client)
                    + ", reading no aim of its copy of the user");
            LogUtils.getLogger().info("R770_TUSK_TASK_TARGET_PASS nail_scratch_own_client");
            helper.succeed();
        }
        finally {
            local[0] = false;
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
            if (power != null && power.isSummoned()) {
                TuskAddon.TUSK.get().forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    private static String why(ConditionCheck check) {
        if (check.isPositive()) {
            return "usable";
        }
        return check.getWarning() == null ? "refused without a message"
                : "refused (" + (check.getWarning().getContents() instanceof TranslatableContents contents
                        ? contents.getKey() : check.getWarning().getString()) + ")";
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
