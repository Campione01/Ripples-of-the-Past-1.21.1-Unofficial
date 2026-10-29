package rotp.addons.cmoon.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.DestroyAbility;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.ConditionCheck;
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
 * 1.16 Destroy (TargetRequirement.ENTITY) kept the entity it was pressed at in its StandEntityTask, and
 * StandEntity.taskOffset turned the default offset withYOffset(-0.75, 0.2, -0.75) by the yaw from the user's eyes to
 * that entity's eyes while the hold charged. The turn does not read the user's look. A held Destroy whose crosshair
 * leaves the entity still stops (the held check asks the crosshair again, DestroyAbility.checkHeldSpecificConditions);
 * these tests drive the action without a held key, so that stop does not run in them.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonTaskTargetGameTests {
    private static final double EPS = 1.0E-4D;

    private CMoonTaskTargetGameTests() {}

    @GameTest(template = "empty", batch = "cm_task_target", timeoutTicks = 100)
    public static void destroyTurnsTheStandToTheMobItWasPressedAt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "cm-task-target")) {
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
            helper.assertTrue(level.addFreshEntity(user), "Could not add the C-Moon user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(CMoonAddon.C_MOON.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && CMoonAddon.C_MOON.get().summon(user, power),
                    "Could not summon C-Moon");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Setup: C-Moon is not a full Stand following its user");

            // a no-AI cow 3 blocks east of the user, who looks at it as the destroy is pressed
            cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Could not make a cow");
            cow.moveTo(pos.x + 3.0D, pos.y, pos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(cow), "Could not add the cow");
            turn(user, -90.0F, -90.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(cow));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));

            helper.assertTrue(power.getAbility(CMoonAddon.DESTROY) instanceof EntityActionType,
                    "Setup: C-Moon has no destroy");
            EntityActionInstance destroy = ((EntityActionType) power.getAbility(CMoonAddon.DESTROY))
                    .initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(destroy, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == destroy && destroy.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "Setup: the destroy is not charging: " + stand.getCurStandAction() + " " + destroy.getPhase());
            helper.assertTrue(destroy.getTaskTarget(level).getEntity() == cow,
                    "1.16 Destroy (TargetRequirement.ENTITY) kept the cow it was pressed at; the task holds "
                            + destroy.getTaskTarget(level));

            // the user looks south with the body turned away, and nothing is aimed at any more
            turn(user, 0.0F, 50.0F);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).tick();
            Vec3 relative = stand.offsetFromUser.getRelativeOffset();
            helper.assertTrue(relative.distanceTo(new Vec3(-0.75D, 0.2D, -0.75D)) < EPS
                            && stand.offsetFromUser.getRotations() == StandOffsetFromUser.Rotations.HEAD,
                    "Setup: the charging destroy does not show the default offset: " + relative + " "
                            + stand.offsetFromUser.getRotations());
            Vec3 toCow = cow.getEyePosition().subtract(user.getEyePosition());
            // 1.16 MathUtil.yRotDegFromVec
            double yawToCow = -Math.toDegrees(Math.atan2(toCow.x, toCow.z));
            Vec3 expected = relative.yRot((float) Math.toRadians(-yawToCow));
            stand.updatePosition(user);
            Vec3 actual = stand.position().subtract(user.position());
            helper.assertTrue(actual.distanceTo(expected) < EPS,
                    "1.16 put a C-Moon charging a destroy pressed at a cow 3 blocks east of its user, who"
                            + " then looks south, at its user's feet + " + expected + " (the default offset turned"
                            + " by the yaw to the cow), got " + actual);
            LogUtils.getLogger().info("R770_CM_TASK_TARGET_PASS destroy yaw={} offset={}", yawToCow, actual);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                CMoonAddon.C_MOON.get().forceUnsummon(user, power);
            }
            if (cow != null) {
                cow.discard();
            }
            user.discard();
        }
    }

    /*
     * 1.16 PowerBaseImpl.checkTarget first ran StandEntityAction.overrideVanillaMouseTarget on the press: Destroy
     * (TargetRequirement.ENTITY) pressed at a torch took the cow behind it, which C-Moon's collision ray met, and went
     * on with it. The Stand's eyes are level with its user's, just south of the user's box, looking south as the user
     * does, as in the core's scenes. With nothing on that ray the press was refused silently, and so was a held tick
     * once the ray had lost the cow; a stone behind the torch stopped the ray.
     */
    @GameTest(template = "empty", batch = "cm_destroy_torch", timeoutTicks = 100)
    public static void destroyPressedAtATorchTakesTheCowBehindAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "cm-destroy-torch")) {
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
        BlockPos torch = null;
        BlockState replaced = null;
        BlockPos behind = null;
        BlockState behindReplaced = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            user.setYHeadRot(0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the C-Moon user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(CMoonAddon.C_MOON.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && CMoonAddon.C_MOON.get().summon(user, power),
                    "Could not summon C-Moon");
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
            helper.assertTrue(cow != null, "Could not make a cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(pos.x, user.getEyeY() - cow.getBbHeight() / 2.0D, pos.z + 2.0D, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow) && stand.canAttack(cow),
                    "Setup: the cow was not added or C-Moon may not attack it");
            ActionTarget atTorch = new ActionTarget(torch, Direction.NORTH);
            LivingComponentAction.getComponent(user).entityAim.setTarget(atTorch);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(atTorch);

            helper.assertTrue(power.getAbility(CMoonAddon.DESTROY) instanceof DestroyAbility,
                    "Setup: C-Moon has no destroy");
            DestroyAbility destroyAbility = (DestroyAbility) power.getAbility(CMoonAddon.DESTROY);
            ConditionCheck check = destroyAbility.checkSpecificConditions(power);
            Entity aimed = destroyAbility.getAimedEntity(stand);
            helper.assertTrue(check.isPositive() && aimed == cow,
                    "1.16's Destroy pressed at a torch with a cow behind it went on with the cow; the port's press check"
                            + " answers " + check.isPositive()
                            + (check.getWarning() != null ? " (" + check.getWarning().getString() + ")" : "")
                            + " and aims at " + aimed + ", the torch at " + torch);
            EntityActionInstance destroy = destroyAbility.initActionOnAbilityUse(level, user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(destroy, user, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == destroy && destroy.getTaskTarget(level).getEntity() == cow,
                    "1.16's Destroy pressed at a torch kept the cow behind it; the task holds "
                            + destroy.getTaskTarget(level));

            // 1.16 PowerBaseImpl.tickHeldAction asked checkRequirements, the override first, on every held tick
            helper.assertTrue(destroyAbility.checkHeldActionConditions(destroy, power).isPositive(),
                    "Setup: the charging Destroy's held check refused the cow behind the torch");
            cow.moveTo(pos.x + 1.5D, cow.getY(), cow.getZ(), 0.0F, 0.0F);
            ConditionCheck held = destroyAbility.checkHeldActionConditions(destroy, power);
            helper.assertTrue(!held.isPositive() && held.getWarning() == null,
                    "1.16 ended a held Destroy, silently, once C-Moon's collision ray past the torch met nothing; the"
                            + " port's held check answers " + held.isPositive()
                            + (held.getWarning() != null ? " (" + held.getWarning().getString() + ")" : ""));
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);

            ConditionCheck bare = destroyAbility.checkSpecificConditions(power);
            helper.assertTrue(!bare.isPositive() && bare.getWarning() == null,
                    "1.16 refused, silently, a Destroy pressed at a torch with nothing on C-Moon's collision ray behind"
                            + " it; the port's press check answers " + bare.isPositive()
                            + (bare.getWarning() != null ? " (" + bare.getWarning().getString() + ")" : ""));

            cow.moveTo(pos.x, cow.getY(), pos.z + 3.0D, 0.0F, 0.0F);
            helper.assertTrue(destroyAbility.checkSpecificConditions(power).isPositive(),
                    "Setup: the press check refused the cow 3 blocks behind the torch with nothing between");
            behind = torch.south();
            behindReplaced = level.getBlockState(behind);
            level.setBlock(behind, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            ConditionCheck walled = destroyAbility.checkSpecificConditions(power);
            helper.assertTrue(!walled.isPositive(),
                    "1.16's collision ray past the torch stopped at the stone behind it, so a Destroy pressed at the"
                            + " torch did not reach the cow behind the stone; the port's press check took it");
            LogUtils.getLogger().info("R770_CM_TASK_TARGET_PASS destroy_torch");
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                CMoonAddon.C_MOON.get().forceUnsummon(user, power);
            }
            if (behind != null && behindReplaced != null) {
                level.setBlock(behind, behindReplaced, Block.UPDATE_CLIENTS);
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
