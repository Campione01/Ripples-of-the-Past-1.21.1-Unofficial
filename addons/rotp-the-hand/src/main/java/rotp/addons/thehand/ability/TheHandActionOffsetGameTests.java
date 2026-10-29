package rotp.addons.thehand.ability;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.thehand.TheHandAddon;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TheHandAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheHandActionOffsetGameTests {
    private static final short ERASE_ITEM_KEY = 84;
    private static final short ERASE_KEY = 85;
    private static final double EPS = 1.0E-4D;

    private TheHandActionOffsetGameTests() {}

    /**
     * 1.16 InitStands.java:133: the_hand_erase_item summoned the whole Stand
     * (the builder's FULL) and put it at withYOffset(0.667, 0, 0.2), (left,
     * up, forward) from the user's feet; TheHandEraseItem extends
     * StandEntityAction, so the view yaw turned it.
     */
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_item_offset",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseItemPutsTheWholeStandWhereTheDonorDid(
            GameTestHelper helper) {
        TheHandGameTests.TestContext context =
                TheHandGameTests.prepareTheHand(helper);
        if (context == null) {
            return;
        }
        // the Stand's collision needs the loaded chunk around it
        TheHandGameTests.onceStandTicks(helper, context, () -> {
            try {
                checkEraseItem(helper, context);
            }
            finally {
                TheHandGameTests.cleanup(context);
            }
            helper.succeed();
        });
    }

    private static void checkEraseItem(
            GameTestHelper helper, TheHandGameTests.TestContext context) {
        ServerPlayer owner = context.owner();
        StandEntity stand = context.stand();
        owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                .standSide = HumanoidArm.RIGHT;
        // the fixture's setManuallyControlled(false) retracts the Stand until
        // a position update finds it close to its user
        stand.updatePosition(owner);
        stand.setArmsOnlyMode();
        helper.assertTrue(stand.isArmsOnlyMode() && stand.isFollowingUser(),
                "setup: The Hand is not an arms-only Stand following its user");
        owner.setItemInHand(
                InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_ITEM_NAME);
        TheHandGameTests.pressHold(
                helper,
                context,
                ability,
                ERASE_ITEM_KEY,
                TheHandEraseItemAbility.EraseItemAction.class);
        helper.assertTrue(!stand.isArmsOnlyMode(),
                "1.16 brought the whole The Hand out from its arms for "
                        + "the item erase");

        // the body is turned away from the view: a body-yaw offset misses
        turn(owner, 0.0F, 50.0F);
        assertAt(helper, owner, stand, 0.667D, 0.0D, 0.2D,
                "view yaw 0, body yaw 50");
        // facing west the forward is -x and the left is -z
        turn(owner, 90.0F, 0.0F);
        assertAt(helper, owner, stand, -0.2D, 0.0D, 0.667D,
                "view yaw 90, body yaw 0");
        AbilityInput.keyRelease(ERASE_ITEM_KEY, owner);
    }

    /**
     * 1.16 InitStands.java:130: the_hand_erase, a StandEntityHeavyAttack,
     * summoned the whole Stand (the builder's FULL) and put it at
     * front3dOffset (StandEntityAction.java:435-471): withXRot(0, front)
     * along the user's view and pitch (lockOnTargetPosition false), front
     * the horizontal distance from the user's feet to the task's target less
     * 1 and half its width, within 0.5 to 2. TheHandErase.standKeepsTarget
     * kept any target, and an Erase pressed at nothing took over the one of
     * the task it replaced (StandEntity.setTask).
     */
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_offset",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void erasePutsTheWholeStandInFrontAsTheDonorDid(
            GameTestHelper helper) {
        TheHandGameTests.TestContext context =
                TheHandGameTests.prepareTheHand(helper);
        if (context == null) {
            return;
        }
        TheHandGameTests.onceStandTicks(helper, context, () -> {
            Cow cow = EntityType.COW.create(helper.getLevel());
            try {
                checkErase(helper, context, cow);
            }
            finally {
                if (cow != null) {
                    cow.discard();
                }
                TheHandGameTests.cleanup(context);
            }
            helper.succeed();
        });
    }

    private static void checkErase(GameTestHelper helper,
            TheHandGameTests.TestContext context, Cow cow) {
        ServerPlayer owner = context.owner();
        StandEntity stand = context.stand();
        owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                .standSide = HumanoidArm.RIGHT;
        stand.updatePosition(owner);
        stand.setArmsOnlyMode();
        helper.assertTrue(cow != null, "setup: no cow");
        Vec3 cowPos = owner.position().add(2.5D, 0.0D, 0.0D);
        cow.moveTo(cowPos.x, cowPos.y, cowPos.z, 0.0F, 0.0F);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                "setup: the cow was not added");

        // the user looks east at the cow and presses Erase at it
        turn(owner, -90.0F, -90.0F, 0.0F);
        LivingComponentAction.getComponent(stand).entityAim
                .setTarget(new ActionTarget(cow));
        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);
        TheHandGameTests.pressHold(
                helper,
                context,
                ability,
                ERASE_KEY,
                TheHandEraseAbility.EraseAction.class);
        EntityActionInstance first =
                LivingComponentAction.getCurEntityAction(stand);
        helper.assertTrue(!stand.isArmsOnlyMode(),
                "1.16 brought the whole The Hand out from its arms for "
                        + "Erase");
        helper.assertTrue(
                first.getTaskTarget(helper.getLevel()).getEntity() == cow,
                "1.16 TheHandErase.standKeepsTarget kept the cow it was "
                        + "pressed at; the task holds "
                        + first.getTaskTarget(helper.getLevel()));

        double front = Mth.clamp(
                cow.position().subtract(owner.position())
                        .horizontalDistance()
                        - (1.0D + cow.getBbWidth() / 2.0D),
                Math.min(0.5D, stand.getEffectiveRange()),
                Math.min(2.0D, stand.getMaxRange()));
        helper.assertTrue(front > 0.5D && front < 2.0D,
                "setup: the cow does not set the front offset: " + front);
        // looking away from the cow, the Stand stays along the view
        turn(owner, 0.0F, 50.0F, 30.0F);
        assertAt(helper, owner, stand, donorFront(front, 0.0F, 30.0F),
                "an Erase pressed at a cow 2.5 blocks east, the user "
                        + "looking south and 30 degrees down, body yaw 50");
        turn(owner, 90.0F, 90.0F, 0.0F);
        assertAt(helper, owner, stand, donorFront(front, 90.0F, 0.0F),
                "an Erase pressed at a cow 2.5 blocks east, the user "
                        + "looking west");

        // pressed at nothing, the next Erase takes the cow over and
        // measures its front to it, not the 2 blocks of an empty view
        LivingComponentAction.getComponent(stand).entityAim
                .setTarget(ActionTarget.EMPTY);
        turn(owner, 0.0F, 0.0F, 0.0F);
        EntityActionInstance next = ((EntityActionAbility) ability)
                .initActionOnAbilityUse(
                        helper.getLevel(), owner, stand, null);
        LivingComponentAction.getComponent(stand)
                .setAction(next, owner, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == next
                        && next != first
                        && next.getTaskTarget(helper.getLevel())
                                .getEntity() == cow,
                "1.16 StandEntity.setTask gave an Erase pressed at "
                        + "nothing the cow of the one it replaced; it holds "
                        + next.getTaskTarget(helper.getLevel()));
        assertAt(helper, owner, stand, donorFront(front, 0.0F, 0.0F),
                "an Erase pressed at nothing after one at the cow, the "
                        + "user looking south");
    }

    /**
     * 1.16 StandEntityHeavyAttack.standPerform: Erase struck with
     * StandEntity.punch at the task's target while the Stand reached it
     * (aimWithThisOrUser), the one an Erase pressed at nothing took over too,
     * whatever its user looked at; otherwise it aimed from the user's view
     * again. What it struck became the task's target (setTaskTarget).
     */
    @GameTest(
            template = "empty",
            batch = "the_hand_erase_strike",
            timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void eraseStrikesItsTaskTargetAsTheDonorDid(
            GameTestHelper helper) {
        TheHandGameTests.TestContext context =
                TheHandGameTests.prepareTheHand(helper);
        if (context == null) {
            return;
        }
        TheHandGameTests.onceStandTicks(helper, context, () -> {
            Cow kept = sturdyCow(helper);
            Cow looked = sturdyCow(helper);
            try {
                checkEraseStrike(helper, context, kept, looked);
            }
            finally {
                kept.discard();
                looked.discard();
                TheHandGameTests.cleanup(context);
            }
            helper.succeed();
        });
    }

    private static void checkEraseStrike(GameTestHelper helper,
            TheHandGameTests.TestContext context, Cow kept, Cow looked) {
        ServerPlayer owner = context.owner();
        StandEntity stand = context.stand();
        owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                .standSide = HumanoidArm.RIGHT;
        stand.updatePosition(owner);
        place(helper, kept, owner.position().add(2.0D, 0.0D, 0.0D));
        place(helper, looked, owner.position().add(0.0D, 0.0D, -6.0D));
        Ability ability = context.power().getAbility(
                TheHandAddon.ERASE_NAME);

        // an Erase at the cow east of the user, then one pressed at nothing
        // while the user looks south at nothing; a second cow then comes
        // into the user's view, both in the Stand's reach
        turn(owner, -90.0F, -90.0F, 0.0F);
        aim(owner, stand, new ActionTarget(kept));
        startErase(helper, owner, stand, ability);
        turn(owner, 0.0F, 0.0F, 0.0F);
        aim(owner, stand, ActionTarget.EMPTY);
        TheHandEraseAbility.EraseAction next =
                startErase(helper, owner, stand, ability);
        helper.assertTrue(
                next.getTaskTarget(helper.getLevel()).getEntity() == kept,
                "setup: the Erase pressed at nothing did not take the cow "
                        + "over: " + next.getTaskTarget(helper.getLevel()));
        place(helper, looked, owner.position().add(0.0D, 0.0D, 2.0D));
        aim(owner, stand, new ActionTarget(looked));
        stand.updatePosition(owner);
        strike(helper, owner, stand, next);
        helper.assertTrue(kept.getLastDamageSource() != null
                        && looked.getLastDamageSource() == null,
                "1.16 StandEntity.punch struck the cow an Erase pressed at "
                        + "nothing took over, not the one the user looked at "
                        + "(struck: kept " + (kept.getLastDamageSource() != null)
                        + ", looked at " + (looked.getLastDamageSource() != null)
                        + ")");

        // an Erase at that cow, now out of the Stand's reach but kept, with
        // the user turned to another cow in reach as it strikes
        place(helper, kept, owner.position().add(5.0D, 0.0D, 0.0D));
        place(helper, looked, owner.position().add(0.0D, 0.0D, 2.5D));
        turn(owner, -90.0F, -90.0F, 0.0F);
        aim(owner, stand, new ActionTarget(kept));
        TheHandEraseAbility.EraseAction third =
                startErase(helper, owner, stand, ability);
        helper.assertTrue(
                third.getTaskTarget(helper.getLevel()).getEntity() == kept,
                "setup: the Erase did not keep the cow 5 blocks away: "
                        + third.getTaskTarget(helper.getLevel()));
        turn(owner, 0.0F, 0.0F, 0.0F);
        aim(owner, stand, new ActionTarget(looked));
        stand.updatePosition(owner);
        strike(helper, owner, stand, third);
        helper.assertTrue(looked.getLastDamageSource() != null,
                "1.16 aimed the Erase from the user's view again once its "
                        + "cow was out of reach, at the cow the user looked at");
        ActionTarget after = third.getTaskTarget(helper.getLevel());
        helper.assertTrue(after.getEntity() == looked,
                "1.16 StandEntity.punch made the cow the Erase struck its "
                        + "task's target; the task holds " + after);

        // 1.16 StandEntity.setTaskTarget let server code drop the target of
        // the current task, the struck one too
        stand.setTaskTarget(null);
        helper.assertTrue(third.getTaskTarget(helper.getLevel())
                        .isEmpty(helper.getLevel()),
                "1.16 StandEntity.setTaskTarget(null) dropped the target of "
                        + "the Erase, the cow it struck; the task still holds "
                        + third.getTaskTarget(helper.getLevel()));
        aim(owner, stand, ActionTarget.EMPTY);
        TheHandEraseAbility.EraseAction fourth =
                startErase(helper, owner, stand, ability);
        helper.assertTrue(fourth.getTaskTarget(helper.getLevel())
                        .isEmpty(helper.getLevel()),
                "1.16 StandEntity.setTask gave an Erase pressed at nothing "
                        + "no target once the one before it had none; it holds "
                        + fourth.getTaskTarget(helper.getLevel()));
    }

    private static TheHandEraseAbility.EraseAction startErase(
            GameTestHelper helper, ServerPlayer owner, StandEntity stand,
            Ability ability) {
        EntityActionInstance action = ((EntityActionAbility) ability)
                .initActionOnAbilityUse(helper.getLevel(), owner, stand, null);
        LivingComponentAction.getComponent(stand)
                .setAction(action, owner, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action
                        && action instanceof TheHandEraseAbility.EraseAction,
                "setup: the Erase did not become The Hand's action");
        return (TheHandEraseAbility.EraseAction) action;
    }

    // the held charge fires by itself at its maximum
    private static void strike(GameTestHelper helper, ServerPlayer owner,
            StandEntity stand, TheHandEraseAbility.EraseAction erase) {
        for (int tick = 0; tick < 80
                && erase.eraseTimingSnapshot().eraseCallbackGameTick() < 0L;
                tick++) {
            LivingComponentAction.getComponent(stand).tick();
        }
        helper.assertTrue(
                erase.eraseTimingSnapshot().eraseCallbackGameTick() >= 0L,
                "setup: the Erase never swiped, in " + erase.getPhase());
    }

    private static Cow sturdyCow(GameTestHelper helper) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "setup: no cow");
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        cow.setHealth(500.0F);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        return cow;
    }

    private static void place(GameTestHelper helper, Cow cow, Vec3 pos) {
        cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        if (!cow.isAddedToLevel()) {
            helper.assertTrue(helper.getLevel().addFreshEntity(cow),
                    "setup: the cow was not added");
        }
    }

    private static void aim(ServerPlayer owner, StandEntity stand,
            ActionTarget target) {
        LivingComponentAction.getComponent(owner).entityAim.setTarget(target);
        LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
    }

    // 1.16 StandRelativeOffset.withXRot(0, front).getAbsoluteVec
    private static Vec3 donorFront(double front, float yRot, float xRot) {
        return new Vec3(0.0D, 0.0D, front)
                .xRot(-xRot * Mth.DEG_TO_RAD)
                .yRot(-yRot * Mth.DEG_TO_RAD);
    }

    private static void turn(ServerPlayer user, float viewYaw, float bodyYaw,
            float pitch) {
        turn(user, viewYaw, bodyYaw);
        user.setXRot(pitch);
        user.xRotO = pitch;
    }

    private static void assertAt(GameTestHelper helper, ServerPlayer user,
            StandEntity stand, Vec3 expected, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(actual.distanceTo(expected) < EPS,
                "1.16 put The Hand at its user's feet + " + expected
                        + " during " + when + ", got " + actual + " (offset "
                        + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    private static void turn(ServerPlayer user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertAt(GameTestHelper helper, ServerPlayer user,
            StandEntity stand, double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < EPS
                        && Math.abs(actual.y - dy) < EPS
                        && Math.abs(actual.z - dz) < EPS,
                "1.16 put The Hand erasing an item at its user's feet + ("
                        + dx + ", " + dy + ", " + dz + ") with " + when
                        + ", got " + actual + " (offset "
                        + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }
}
