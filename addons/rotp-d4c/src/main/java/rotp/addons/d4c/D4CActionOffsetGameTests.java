package rotp.addons.d4c;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
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
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CActionOffsetGameTests {
    private static final double EPS = 1.0E-4D;

    private D4CActionOffsetGameTests() {}

    /**
     * 1.16 ai/ah.java:74-81: summon_player_minion and annihilation
     * withYOffset(0, 0, 2), flag_parallel_world_travel noYOffset(-0.1, -0.5)
     * at the default y 0.2, love_train withYOffset(0, 0.3, -0.4), as (left,
     * up, forward) from the user's feet turned by the view yaw, in full mode
     * only; an arms-only D4C kept its arms-only default.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_action_offsets")
    public static void d4cActionsPutTheStandWhereTheDonorDid(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-action-offsets")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode()
                            && stand.offsetFromUser.isIdle(),
                    "setup: D4C is not an idle full Stand following its user");

            checkAction(helper, user, power, stand,
                    "summon_player_minion", 0.0D, 0.0D, 2.0D);
            checkAction(helper, user, power, stand,
                    "annihilation", 0.0D, 0.0D, 2.0D);
            checkAction(helper, user, power, stand,
                    "flag_parallel_world_travel", -0.1D, 0.2D, -0.5D);
            checkAction(helper, user, power, stand,
                    "love_train", 0.0D, 0.3D, -0.4D);

            // each action replaces the one before: clearing it would
            // unsummon the arms
            stand.setArmsOnlyMode();
            for (String name : new String[] {"summon_player_minion",
                    "annihilation", "flag_parallel_world_travel",
                    "love_train"}) {
                start(helper, user, power, stand, name);
                turn(user, 0.0F, 50.0F);
                assertAt(helper, user, stand, 0.0D, 0.0D, 0.15D,
                        "an arms-only D4C during " + name
                                + " (its arms-only default), view yaw 0");
            }
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 ai/ah.java:71 and aa/aa/ac.java: d4c_rapid_punch, a
     * StandEntityLightAttack that summoned the whole Stand, kept any target
     * (standKeepsTarget) and turned its front3dOffset to an entity one
     * (lockOnTargetPosition, StandEntity.taskOffset): withXRot(0, front)
     * turned by the yaw and pitch from the user's eyes to the entity's,
     * front the horizontal distance from the user's feet to it less 1 and
     * half its width, within 0.5 to 2. From the last 2 of its 3 windup ticks
     * on the Stand moved by itself (standMovesByItself) and stayed there.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_offset")
    public static void rapidPunchTurnsItsFrontOffsetToTheEntityAsTheDonorDid(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-rapid-punch-offset")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -70.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "setup: D4C is not a full Stand following its user");

            cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "setup: no cow");
            Vec3 cowPos = user.position().add(2.5D, 0.0D, 1.0D);
            cow.moveTo(cowPos.x, cowPos.y, cowPos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "setup: the cow was not added");

            // pressed at the cow, east-south-east of the user
            turn(user, -70.0F, -70.0F);
            LivingComponentAction.getComponent(stand).entityAim
                    .setTarget(new ActionTarget(cow));
            start(helper, user, power, stand, "d4c_rapid_punch");
            EntityActionInstance action = stand.getCurStandAction();
            helper.assertTrue(action.getPhase() == ActionPhase.WINDUP
                            && action.getTaskTarget(helper.getLevel())
                                    .getEntity() == cow,
                    "1.16 ac.java standKeepsTarget kept the cow the rapid punch "
                            + "was pressed at; it holds "
                            + action.getTaskTarget(helper.getLevel())
                            + " in " + action.getPhase());

            double front = Mth.clamp(
                    cow.position().subtract(user.position())
                            .horizontalDistance()
                            - (1.0D + cow.getBbWidth() / 2.0D),
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(2.0D, stand.getMaxRange()));
            helper.assertTrue(front > 0.5D && front < 2.0D,
                    "setup: the cow does not set the front offset: " + front);
            // looking away, south, with the body turned 50 degrees
            turn(user, 0.0F, 50.0F);
            Vec3 toCow = cow.getEyePosition().subtract(user.getEyePosition());
            float yaw = (float) -Mth.atan2(toCow.x, toCow.z) * Mth.RAD_TO_DEG;
            float pitch = (float) -Mth.atan2(toCow.y, toCow.horizontalDistance())
                    * Mth.RAD_TO_DEG;
            Vec3 expected = new Vec3(0.0D, 0.0D, front)
                    .xRot(-pitch * Mth.DEG_TO_RAD)
                    .yRot(-yaw * Mth.DEG_TO_RAD);
            Vec3 placed = assertAt(helper, user, stand, expected,
                    "the rapid punch's first windup tick, pressed at a cow 2.5 "
                            + "east and 1 south, the user looking south");

            // the windup's second tick: the Stand no longer follows
            LivingComponentAction.getComponent(stand).tick();
            turn(user, 90.0F, 90.0F);
            stand.updatePosition(user);
            helper.assertTrue(!stand.isFollowingUser()
                            && stand.position().distanceTo(placed) < EPS,
                    "1.16 ac.java standMovesByItself left D4C where its front "
                            + "offset put it from the windup's last 2 ticks on, "
                            + "at " + placed + "; it is at " + stand.position()
                            + " in " + action.getPhase() + " tick "
                            + action.getPhaseTick());
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/aa.java: d4c_hand_chop, a StandEntityHeavyAttack, kept the
     * entity it was pressed at (standKeepsTarget) without turning its
     * front3dOffset to it (lockOnTargetPosition false): withXRot(0, front)
     * along the user's view, front the horizontal distance from the user's
     * feet to it less 1 and half its width, within 0.5 to 3. A punch pressed
     * at nothing then took the target over (StandEntity.setTask).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_target")
    public static void handChopKeepsItsEntityButNeverTurnsToItAsTheDonorDid(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-hand-chop-target")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -70.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "setup: D4C is not a full Stand following its user");

            cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "setup: no cow");
            Vec3 cowPos = user.position().add(2.5D, 0.0D, 1.0D);
            cow.moveTo(cowPos.x, cowPos.y, cowPos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "setup: the cow was not added");

            turn(user, -70.0F, -70.0F);
            LivingComponentAction.getComponent(stand).entityAim
                    .setTarget(new ActionTarget(cow));
            start(helper, user, power, stand, "d4c_hand_chop");
            EntityActionInstance chop = stand.getCurStandAction();
            helper.assertTrue(chop.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "1.16 StandEntityHeavyAttack.standKeepsTarget kept the cow the hand chop was pressed at; it holds "
                            + chop.getTaskTarget(helper.getLevel()));

            double front = Mth.clamp(
                    cow.position().subtract(user.position()).horizontalDistance()
                            - (1.0D + cow.getBbWidth() / 2.0D),
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(3.0D, stand.getMaxRange()));
            helper.assertTrue(front > 0.5D && front < 3.0D,
                    "setup: the cow does not set the front offset: " + front);
            // looking south with the body turned away, the Stand stays along the view
            turn(user, 0.0F, 50.0F);
            assertAt(helper, user, stand, new Vec3(0.0D, 0.0D, front),
                    "a hand chop pressed at a cow 2.5 east and 1 south, the user looking south");
            turn(user, 90.0F, 0.0F);
            assertAt(helper, user, stand, new Vec3(-front, 0.0D, 0.0D),
                    "a hand chop pressed at a cow 2.5 east and 1 south, the user looking west");

            // pressed at nothing, a punch takes the cow over
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            turn(user, 0.0F, 50.0F);
            start(helper, user, power, stand, "d4c_punch");
            EntityActionInstance punch = stand.getCurStandAction();
            helper.assertTrue(punch != chop
                            && punch.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "1.16 StandEntity.setTask gave a punch pressed at nothing the cow of the hand chop; it holds "
                            + punch.getTaskTarget(helper.getLevel()));
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntityLightAttack.getOffsetFromUser measured the rapid
     * punch's front3dOffset to task.getTarget(), which for a rapid punch
     * pressed at nothing was the target of the action it replaced
     * (StandEntity.setTask), and turned it to that entity
     * (ac.java lockOnTargetPosition).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_rapid_punch_takeover")
    public static void rapidPunchMeasuresItsFrontToATargetTakenOver(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-rapid-punch-takeover")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -70.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "setup: D4C is not a full Stand following its user");

            cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "setup: no cow");
            Vec3 cowPos = user.position().add(2.5D, 0.0D, 1.0D);
            cow.moveTo(cowPos.x, cowPos.y, cowPos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "setup: the cow was not added");

            // the hand chop keeps the cow it was pressed at
            turn(user, -70.0F, -70.0F);
            LivingComponentAction.getComponent(stand).entityAim
                    .setTarget(new ActionTarget(cow));
            start(helper, user, power, stand, "d4c_hand_chop");
            EntityActionInstance chop = stand.getCurStandAction();
            helper.assertTrue(chop.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "setup: the hand chop did not keep the cow: "
                            + chop.getTaskTarget(helper.getLevel()));

            // a rapid punch pressed at nothing, the user looking south
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            turn(user, 0.0F, 50.0F);
            start(helper, user, power, stand, "d4c_rapid_punch");
            EntityActionInstance rapid = stand.getCurStandAction();
            helper.assertTrue(rapid != chop
                            && rapid.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "setup: the rapid punch pressed at nothing did not take the cow over: "
                            + rapid.getTaskTarget(helper.getLevel()));

            double front = Mth.clamp(
                    cow.position().subtract(user.position()).horizontalDistance()
                            - (1.0D + cow.getBbWidth() / 2.0D),
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(2.0D, stand.getMaxRange()));
            helper.assertTrue(front > 0.5D && front < 2.0D,
                    "setup: the cow does not set the front offset: " + front);
            Vec3 toCow = cow.getEyePosition().subtract(user.getEyePosition());
            float yaw = (float) -Mth.atan2(toCow.x, toCow.z) * Mth.RAD_TO_DEG;
            float pitch = (float) -Mth.atan2(toCow.y, toCow.horizontalDistance())
                    * Mth.RAD_TO_DEG;
            Vec3 expected = new Vec3(0.0D, 0.0D, front)
                    .xRot(-pitch * Mth.DEG_TO_RAD)
                    .yRot(-yaw * Mth.DEG_TO_RAD);
            assertAt(helper, user, stand, expected,
                    "a rapid punch pressed at nothing that took over the hand chop's cow 2.5 east and 1 south, "
                            + "the user looking south (front3dOffset measured to the task's target)");
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/aa.java:74-78 measured the hand chop's front3dOffset
     * (0.5 to 3) to task.getTarget(), which for a hand chop pressed at
     * nothing was the target of the action it replaced (StandEntity.setTask),
     * along the user's view (StandEntityHeavyAttack.lockOnTargetPosition
     * false).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_takeover")
    public static void handChopMeasuresItsFrontToATargetTakenOver(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-hand-chop-takeover")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -70.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "setup: D4C is not a full Stand following its user");

            cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "setup: no cow");
            Vec3 cowPos = user.position().add(2.5D, 0.0D, 1.0D);
            cow.moveTo(cowPos.x, cowPos.y, cowPos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "setup: the cow was not added");

            // the rapid punch keeps the cow it was pressed at
            turn(user, -70.0F, -70.0F);
            LivingComponentAction.getComponent(stand).entityAim
                    .setTarget(new ActionTarget(cow));
            start(helper, user, power, stand, "d4c_rapid_punch");
            EntityActionInstance rapid = stand.getCurStandAction();
            helper.assertTrue(rapid.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "setup: the rapid punch did not keep the cow: "
                            + rapid.getTaskTarget(helper.getLevel()));

            // a hand chop pressed at nothing, the user looking south
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
            turn(user, 0.0F, 50.0F);
            start(helper, user, power, stand, "d4c_hand_chop");
            EntityActionInstance chop = stand.getCurStandAction();
            helper.assertTrue(chop != rapid
                            && chop.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "setup: the hand chop pressed at nothing did not take the cow over: "
                            + chop.getTaskTarget(helper.getLevel()));

            double front = Mth.clamp(
                    cow.position().subtract(user.position()).horizontalDistance()
                            - (1.0D + cow.getBbWidth() / 2.0D),
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(3.0D, stand.getMaxRange()));
            helper.assertTrue(front > 0.5D && front < 3.0D,
                    "setup: the cow does not set the front offset: " + front);
            assertAt(helper, user, stand, new Vec3(0.0D, 0.0D, front),
                    "a hand chop pressed at nothing that took over the rapid punch's cow 2.5 east and 1 south, "
                            + "the user looking south (front3dOffset measured to the task's target, along the view)");
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/aa.java:74-78: StandEntity read the hand chop's
     * getOffsetFromUser on every tick of its task, front3dOffset(0.5 to 3)
     * measured to the task's target as it stood then, so the Stand kept its
     * distance from a cow that backed away during the windup.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_follow")
    public static void handChopRemeasuresItsFrontOffsetEveryTick(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-hand-chop-follow")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -70.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "setup: D4C is not a full Stand following its user");

            cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "setup: no cow");
            Vec3 cowPos = user.position().add(2.5D, 0.0D, 1.0D);
            cow.moveTo(cowPos.x, cowPos.y, cowPos.z, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "setup: the cow was not added");

            turn(user, -70.0F, -70.0F);
            LivingComponentAction.getComponent(stand).entityAim
                    .setTarget(new ActionTarget(cow));
            start(helper, user, power, stand, "d4c_hand_chop");
            EntityActionInstance chop = stand.getCurStandAction();
            helper.assertTrue(chop.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "setup: the hand chop did not keep the cow: "
                            + chop.getTaskTarget(helper.getLevel()));

            double pressedFront = cow.position().subtract(user.position()).horizontalDistance()
                    - (1.0D + cow.getBbWidth() / 2.0D);
            // the cow backs away to 3 east and 1 south during the windup
            Vec3 backedAway = user.position().add(3.0D, 0.0D, 1.0D);
            cow.moveTo(backedAway.x, backedAway.y, backedAway.z, 0.0F, 0.0F);
            double maxFront = Math.min(3.0D, stand.getMaxRange());
            double front = Mth.clamp(
                    cow.position().subtract(user.position()).horizontalDistance()
                            - (1.0D + cow.getBbWidth() / 2.0D),
                    Math.min(0.5D, stand.getEffectiveRange()),
                    maxFront);
            helper.assertTrue(front > pressedFront + 0.25D && front < maxFront,
                    "setup: the cow does not set the front offset: " + pressedFront + " to " + front
                            + " (max " + maxFront + ")");
            LivingComponentAction.getComponent(stand).tick();
            helper.assertTrue(chop.getPhase() == ActionPhase.WINDUP
                            && chop.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "setup: the hand chop left its windup or the cow: " + chop.getPhase());
            turn(user, 0.0F, 50.0F);
            assertAt(helper, user, stand, new Vec3(0.0D, 0.0D, front),
                    "the windup tick after the hand chop's cow backed away to 3 east and 1 south, the user "
                            + "looking south (aa.java getOffsetFromUser read on every tick)");
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 StandEntity.tick ran updatePosition before it counted the summon
     * lock down or ticked the task, and updatePosition asked the task's
     * getOffsetFromUser right before it placed the Stand: the rapid punch's
     * front3dOffset (StandEntityLightAttack.getOffsetFromUser, 0.5 to 2,
     * turned to an entity it kept, ac.java lockOnTargetPosition) and the hand
     * chop's (aa/aa/aa.java:74-78, 0.5 to 3, along the view) followed a cow
     * that moved or a user who stepped in that same tick, also while the lock
     * held the task back.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_front_offset_position_update")
    public static void frontOffsetsFollowTheirTargetInTheStandsPositionUpdateAsIn116(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-front-offset-update")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = null;
        StandEntity stand = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, -70.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode(),
                    "setup: D4C is not a full Stand following its user");

            cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "setup: no cow");
            cow.moveTo(pos.x + 2.5D, pos.y, pos.z + 1.0D, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "setup: the cow was not added");

            for (String name : new String[] {"d4c_rapid_punch", "d4c_hand_chop"}) {
                boolean rapid = name.equals("d4c_rapid_punch");
                user.setPos(pos.x, pos.y, pos.z);
                cow.moveTo(pos.x + 2.5D, pos.y, pos.z + 1.0D, 0.0F, 0.0F);
                stand.summonLockTicks = 0;
                turn(user, -70.0F, -70.0F);
                LivingComponentAction.getComponent(stand).entityAim
                        .setTarget(new ActionTarget(cow));
                start(helper, user, power, stand, name);
                EntityActionInstance action = stand.getCurStandAction();
                helper.assertTrue(action.getPhase() == ActionPhase.WINDUP
                                && action.getTaskTarget(helper.getLevel()).getEntity() == cow,
                        "setup: " + name + " did not keep the cow it was pressed at: "
                                + action.getTaskTarget(helper.getLevel()) + " in " + action.getPhase());
                turn(user, 0.0F, 50.0F);

                // the cow steps away, the Stand's position update comes before the action's tick
                cow.moveTo(pos.x + 3.0D, pos.y, pos.z + 1.0D, 0.0F, 0.0F);
                assertAt(helper, user, stand, frontOffset(helper, user, stand, cow, rapid),
                        name + " in the position update after its cow stepped to 3 east and 1 south, "
                                + "before the action's tick");

                // the user steps 0.4 toward it
                user.setPos(pos.x + 0.4D, pos.y, pos.z);
                assertAt(helper, user, stand, frontOffset(helper, user, stand, cow, rapid),
                        name + " in the position update after its user stepped 0.4 east, before the action's tick");

                // the summon lock holds the action at its first windup tick
                stand.summonLockTicks = 2;
                LivingComponentAction.getComponent(stand).tick();
                helper.assertTrue(stand.getCurStandAction() == action
                                && action.getPhase() == ActionPhase.WINDUP
                                && action.getPhaseTick() == 0.0F,
                        "setup: the summon lock did not hold " + name + " back: "
                                + action.getPhase() + " tick " + action.getPhaseTick());
                cow.moveTo(pos.x + 3.6D, pos.y, pos.z + 1.0D, 0.0F, 0.0F);
                assertAt(helper, user, stand, frontOffset(helper, user, stand, cow, rapid),
                        name + " held by the summon lock, its cow stepped to 3.6 east and 1 south "
                                + "(1.16 StandEntity.updatePosition ran through the lock)");
                stand.summonLockTicks = 0;
                stop(user, stand);
            }
            helper.succeed();
        }
        finally {
            if (stand != null) {
                stand.summonLockTicks = 0;
            }
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    /**
     * 1.16 aa/aa/aa.java:74-78: the hand chop's offset was only ever
     * front3dOffset(task.getTarget(), 0.5, 3), asked by every position update
     * from the first, so D4C stood toward its task's target from the press on:
     * the cow behind a pressed torch (StandEntityAction.overrideVanillaMouseTarget
     * gave it to the task), and for a pressed stone, which the heavy attack's
     * task did not keep, front3dOffset's own ray from the user, 3 blocks long
     * (StandEntityAction :435-447). The offset the action starts with, and
     * sends to clients with itself, is that one, not one measured to the raw
     * aim.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "d4c_hand_chop_press_offset")
    public static void handChopStartsWithTheOffsetOfItsTaskTargetAsIn116(GameTestHelper helper) {
        Player user = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "d4c-hand-chop-press-offset")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        StandType type = D4CAddon.D4C.get();
        StandPower power = null;
        Cow cow = null;
        StandEntity stand = null;
        BlockPos torch = null;
        BlockPos stone = null;
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                    .standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(user),
                    "Could not add the D4C user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(type)).status()
                            == StandPowerTransitions.Status.APPLIED,
                    "Could not grant D4C");
            power.skipProgression();
            helper.assertTrue(type.summon(user, power), "Could not summon D4C");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && !stand.isArmsOnlyMode(),
                    "setup: D4C is not a full Stand");
            stand.summonLockTicks = 0;
            turn(user, 0.0F, 0.0F);
            user.setXRot(0.0F);
            Vec3 eyes = user.getEyePosition();
            double minFront = Math.min(0.5D, stand.getEffectiveRange());
            double maxFront = Math.min(3.0D, stand.getMaxRange());

            // D4C 0.35 before its user, eyes level, looking the same way
            stand.moveTo(eyes.x, eyes.y - stand.getEyeHeight(), eyes.z + 0.35D, 0.0F, 0.0F);
            stand.setYHeadRot(0.0F);
            // a wall torch hanging on nothing 1.19 blocks before the eyes, a cow 2.6 blocks south
            torch = BlockPos.containing(eyes).south();
            helper.getLevel().setBlock(torch,
                    Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "setup: no cow");
            cow.moveTo(pos.x, eyes.y - 0.7D, pos.z + 2.6D, 0.0F, 0.0F);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "setup: the cow was not added");
            ActionTarget torchAim = new ActionTarget(torch, Direction.NORTH);
            LivingComponentAction.getComponent(user).entityAim.setTarget(torchAim);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(torchAim);
            start(helper, user, power, stand, "d4c_hand_chop");
            EntityActionInstance chop = stand.getCurStandAction();
            helper.assertTrue(chop.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "setup: the hand chop pressed at the torch did not keep the cow D4C's ray met behind it: "
                            + chop.getTaskTarget(helper.getLevel()));
            double cowFront = Mth.clamp(
                    cow.position().subtract(user.position()).horizontalDistance()
                            - (1.0D + cow.getBbWidth() / 2.0D),
                    minFront, maxFront);
            helper.assertTrue(cowFront > minFront && cowFront < maxFront,
                    "setup: the cow does not set the front offset: " + cowFront);
            assertStartOffset(helper, stand, chop, cowFront,
                    "a hand chop pressed at a torch with the cow behind it its task kept");
            stop(user, stand);
            helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            torch = null;

            // a stone 3.5 blocks before the eyes, beyond front3dOffset's 3; D4C and the cow off the user's line
            cow.moveTo(pos.x + 6.0D, pos.y, pos.z, 0.0F, 0.0F);
            stand.moveTo(eyes.x - 1.5D, eyes.y - stand.getEyeHeight(), eyes.z, 0.0F, 0.0F);
            stone = BlockPos.containing(eyes).south(4);
            helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            ActionTarget stoneAim = new ActionTarget(stone, Direction.NORTH);
            LivingComponentAction.getComponent(user).entityAim.setTarget(stoneAim);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(stoneAim);
            start(helper, user, power, stand, "d4c_hand_chop");
            chop = stand.getCurStandAction();
            helper.assertTrue(chop.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
                    "setup: the hand chop's task kept the pressed stone: " + chop.getTaskTarget(helper.getLevel()));
            assertStartOffset(helper, stand, chop, maxFront,
                    "a hand chop pressed at a stone its task did not keep, front3dOffset's ray from the user"
                            + " meeting nothing within 3");
            stop(user, stand);
            helper.succeed();
        }
        finally {
            if (torch != null) {
                helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            if (stone != null) {
                helper.getLevel().setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            }
            if (cow != null) {
                cow.discard();
            }
            if (power != null && power.isSummoned()) {
                type.forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    // the offset the action set as it started, before any position update
    private static void assertStartOffset(GameTestHelper helper, StandEntity stand,
            EntityActionInstance action, double front, String when) {
        Vec3 expected = new Vec3(0.0D, 0.0D, front);
        Vec3 synced = action.getSyncedStandOffset();
        Vec3 relative = stand.offsetFromUser.getRelativeOffset();
        helper.assertTrue(synced != null && synced.distanceTo(expected) < EPS
                        && relative != null && relative.distanceTo(expected) < EPS,
                "1.16 measured D4C's front offset to the task's target from the start (" + expected + ") for "
                        + when + "; the port started with " + synced + " (Stand offset " + relative + ")");
    }

    // 1.16 offsetToTarget: the horizontal distance from the user's feet to
    // the entity less 1 and half its width, clamped; the rapid punch turned
    // it by the yaw and pitch from the user's eyes to the entity's eyes
    private static Vec3 frontOffset(GameTestHelper helper, Player user,
            StandEntity stand, Cow cow, boolean rapid) {
        double maxFront = Math.min(rapid ? 2.0D : 3.0D, stand.getMaxRange());
        double front = Mth.clamp(
                cow.position().subtract(user.position()).horizontalDistance()
                        - (1.0D + cow.getBbWidth() / 2.0D),
                Math.min(0.5D, stand.getEffectiveRange()),
                maxFront);
        helper.assertTrue(front > 0.5D && front < maxFront,
                "setup: the cow does not set the front offset: " + front + " (max " + maxFront + ")");
        if (!rapid) {
            return new Vec3(0.0D, 0.0D, front);
        }
        Vec3 toCow = cow.getEyePosition().subtract(user.getEyePosition());
        float yaw = (float) -Mth.atan2(toCow.x, toCow.z) * Mth.RAD_TO_DEG;
        float pitch = (float) -Mth.atan2(toCow.y, toCow.horizontalDistance())
                * Mth.RAD_TO_DEG;
        return new Vec3(0.0D, 0.0D, front)
                .xRot(-pitch * Mth.DEG_TO_RAD)
                .yRot(-yaw * Mth.DEG_TO_RAD);
    }

    private static Vec3 assertAt(GameTestHelper helper, Player user,
            StandEntity stand, Vec3 expected, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(actual.distanceTo(expected) < EPS,
                "1.16 put D4C at its user's feet + " + expected + " during "
                        + when + ", got " + actual + " (offset "
                        + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
        return stand.position();
    }

    private static void checkAction(GameTestHelper helper, Player user,
            StandPower power, StandEntity stand, String name,
            double left, double y, double forward) {
        start(helper, user, power, stand, name);
        // the body is turned away from the view: a body-yaw offset misses
        turn(user, 0.0F, 50.0F);
        assertAt(helper, user, stand, left, y, forward,
                name + ", view yaw 0, body yaw 50");
        // facing west the forward is -x and the left is -z
        turn(user, 90.0F, 0.0F);
        assertAt(helper, user, stand, -forward, y, left,
                name + ", view yaw 90, body yaw 0");
        stop(user, stand);
        helper.assertTrue(stand.offsetFromUser.isIdle(),
                "D4C did not go back to its idle offset after " + name);
    }

    private static void start(GameTestHelper helper, Player user,
            StandPower power, StandEntity stand, String name) {
        helper.assertTrue(power.getAbility(name) instanceof EntityActionType,
                "D4C has no action " + name);
        EntityActionType actionType = (EntityActionType) power.getAbility(name);
        EntityActionInstance action = actionType.createActionObj();
        actionType.initActionFromConfig(action, helper.getLevel(), user, stand);
        action.setStartingPhase();
        LivingComponentAction.getComponent(stand)
                .setAction(action, user, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action,
                "setup: " + name + " did not become the D4C action");
    }

    private static void stop(Player user, StandEntity stand) {
        // a Stand left far from its user after an action is retracted and
        // stops following, so it is brought back first
        stand.setPos(user.position());
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
        turn(user, 0.0F, 50.0F);
    }

    private static void turn(Player user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertAt(GameTestHelper helper, Player user,
            StandEntity stand, double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < EPS
                        && Math.abs(actual.y - dy) < EPS
                        && Math.abs(actual.z - dz) < EPS,
                "1.16 put D4C at its user's feet + (" + dx + ", " + dy
                        + ", " + dz + ") during " + when + ", got " + actual
                        + " (offset " + stand.offsetFromUser.getRelativeOffset()
                        + " " + stand.offsetFromUser.getRotations() + ")");
    }
}
