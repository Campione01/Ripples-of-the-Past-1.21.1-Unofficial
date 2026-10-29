package rotp.addons.heavensdoor;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.ability.HeavensDoorTargeting;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.item.PageData;
import rotp.addons.heavensdoor.item.PageItem;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 TearOutAPage: noAdheringToUserOffset, so the Stand never took its user's offset; onTaskSet put it one block
 * before the target on the user's side as the hold started (unless manually controlled); the task kept a living target
 * that passed checkTarget's book rules, which a punch pressed at nothing in the recovery then took over.
 */
@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TearPagePlacementGameTests {
    private static final double EPS = 1.0E-4D;

    private TearPagePlacementGameTests() {}

    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPagePutsTheStandBeforeItsTargetAndKeepsItAsIn116(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPagePutsTheStandBeforeItsTargetAndKeepsItAsIn116InLoadedArea(helper));
    }

    private static void tearPagePutsTheStandBeforeItsTargetAndKeepsItAsIn116InLoadedArea(GameTestHelper helper) {
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HdTearPlace"));
        owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        helper.getLevel().addNewPlayer(owner);
        Cow booked = EntityType.COW.create(helper.getLevel());
        Cow plain = EntityType.COW.create(helper.getLevel());
        StandPower power = PowerClass.STAND.attachGet(owner);
        try {
            helper.assertTrue(booked != null && plain != null, "Cow fixture failed");
            booked.setNoAi(true);
            booked.setNoGravity(true);
            booked.setPos(helper.absoluteVec(new Vec3(3.5D, 2.0D, 4.5D)));
            plain.setNoAi(true);
            plain.setNoGravity(true);
            plain.setPos(helper.absoluteVec(new Vec3(-0.5D, 2.0D, 4.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(booked) && helper.getLevel().addFreshEntity(plain),
                    "Cow fixtures could not join the level");
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            power.setResolveLevel(4);
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(owner, power), "Heaven's Door summon failed");
            HeavensDoorStandEntity stand = HeavensDoorAddon.controller(owner);
            helper.assertTrue(stand != null, "Missing summoned Heaven's Door");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.assertTrue(BookStateService.markFromHit(stand, booked), "Book marking failed");

            // a target without a book is refused by 1.16's checkTarget: the task keeps nothing and the Stand stays
            HeavensDoorGameTestAims.lookAt(owner, plain);
            Vec3 before = stand.position();
            EntityActionInstance refused = start(power, stand, owner, HeavensDoorAddon.TEAR_PAGE_ID);
            helper.assertTrue(refused.getTaskTarget(helper.getLevel()).isEmpty(helper.getLevel()),
                    "1.16 TearOutAPage.checkTarget refused a cow without a book, the task holds "
                            + refused.getTaskTarget(helper.getLevel()));
            helper.assertTrue(stand.position().distanceTo(before) < EPS,
                    "1.16 put the Stand before a target its task did not keep: " + stand.position() + " from " + before);
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);

            HeavensDoorGameTestAims.lookAt(owner, booked);
            EntityActionInstance tear = start(power, stand, owner, HeavensDoorAddon.TEAR_PAGE_ID);
            Vec3 toTarget = booked.position().subtract(owner.position()).normalize();
            Vec3 expected = booked.position().subtract(toTarget);
            helper.assertTrue(stand.position().distanceTo(expected) < EPS,
                    "1.16 onTaskSet put the Stand one block before its target on the user's side at " + expected
                            + ", got " + stand.position());
            helper.assertTrue(!stand.isFollowingUser(),
                    "1.16 noAdheringToUserOffset: the Stand must not follow its user's offset during Tear Out a Page");
            helper.assertTrue(tear.getTaskTarget(helper.getLevel()).getEntity() == booked,
                    "1.16 standKeepsTarget kept the living target with a book, the task holds "
                            + tear.getTaskTarget(helper.getLevel()));

            // pressed at nothing in the recovery, the next punch takes the target over (StandEntity.setTask)
            owner.setYRot(180.0F);
            owner.setXRot(-60.0F);
            owner.yRotO = 180.0F;
            owner.setYHeadRot(180.0F);
            owner.yHeadRotO = 180.0F;
            HeavensDoorGameTestAims.send(owner);
            EntityActionInstance punch = start(power, stand, owner, "punch");
            helper.assertTrue(punch != tear && punch.getTaskTarget(helper.getLevel()).getEntity() == booked,
                    "1.16 StandEntity.setTask gave a punch pressed at nothing the target of Tear Out a Page, it holds "
                            + punch.getTaskTarget(helper.getLevel()));
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            }
            if (booked != null) {
                booked.discard();
            }
            if (plain != null) {
                plain.discard();
            }
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }

    // 1.16 TearOutAPage.standPerform tore the page of task.getTarget(), the entity the hold started at, not the one
    // the user's view met when the page came off.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageTearsTheTargetItsHoldStartedAt(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageTearsTheTargetItsHoldStartedAtInLoadedArea(helper));
    }

    private static void tearPageTearsTheTargetItsHoldStartedAtInLoadedArea(GameTestHelper helper) {
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HdTearHeld"));
        owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        helper.getLevel().addNewPlayer(owner);
        Cow held = EntityType.COW.create(helper.getLevel());
        Cow looked = EntityType.COW.create(helper.getLevel());
        StandPower power = PowerClass.STAND.attachGet(owner);
        try {
            helper.assertTrue(held != null && looked != null, "Cow fixture failed");
            held.setNoAi(true);
            held.setNoGravity(true);
            held.setPos(helper.absoluteVec(new Vec3(3.5D, 2.0D, 4.5D)));
            looked.setNoAi(true);
            looked.setNoGravity(true);
            looked.setPos(helper.absoluteVec(new Vec3(-0.5D, 2.0D, 4.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(held) && helper.getLevel().addFreshEntity(looked),
                    "Cow fixtures could not join the level");
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            power.setResolveLevel(4);
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(owner, power), "Heaven's Door summon failed");
            HeavensDoorStandEntity stand = HeavensDoorAddon.controller(owner);
            helper.assertTrue(stand != null, "Missing summoned Heaven's Door");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.assertTrue(BookStateService.markFromHit(stand, held) && BookStateService.markFromHit(stand, looked),
                    "Book marking failed");

            HeavensDoorGameTestAims.lookAt(owner, held);
            EntityActionInstance tear = start(power, stand, owner, HeavensDoorAddon.TEAR_PAGE_ID);
            helper.assertTrue(tear.getTaskTarget(helper.getLevel()).getEntity() == held,
                    "fixture: the hold did not keep the cow it started at: " + tear.getTaskTarget(helper.getLevel()));
            HeavensDoorGameTestAims.lookAt(owner, looked);
            LivingEntity aimed = HeavensDoorTargeting.livingTarget(HeavensDoorTargeting.pressedAim(owner));
            helper.assertTrue(aimed == looked, "fixture: the user's view does not meet the other cow: " + aimed);

            tear.setPhaseStart(ActionPhase.PERFORM);
            tear._tickAction();
            helper.assertTrue(HeavensDoorAddon.bookState(held).pageCount() == 1
                            && HeavensDoorAddon.bookState(looked).pageCount() == 0,
                    "1.16 tore the page of the cow the hold started at; pages on it "
                            + HeavensDoorAddon.bookState(held).pageCount() + ", on the cow the user looks at "
                            + HeavensDoorAddon.bookState(looked).pageCount());
            boolean pageOfHeld = false;
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                PageData data = PageItem.data(owner.getInventory().getItem(slot));
                pageOfHeld |= data != null && data.targetUuid().equals(held.getUUID());
            }
            helper.assertTrue(pageOfHeld, "1.16 gave the user the page of the cow the hold started at");
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            }
            if (held != null) {
                held.discard();
            }
            if (looked != null) {
                looked.discard();
            }
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }

    // 1.16 TearOutAPage.standPerform ended with userPower.getResolveCounter().addResolveValue(getMaxResolve() * 0.005F,
    // user) on every perform, a page torn or not.
    @GameTest(template = "empty", timeoutTicks = HeavensDoorGameTestArea.WAIT_TEST_TICKS + 80)
    public static void tearPageAddsHalfAPercentOfTheResolveBarOnEveryPerform(GameTestHelper helper) {
        HeavensDoorGameTestArea.whenLoaded(helper,
                () -> tearPageAddsHalfAPercentOfTheResolveBarOnEveryPerformInLoadedArea(helper));
    }

    private static void tearPageAddsHalfAPercentOfTheResolveBarOnEveryPerformInLoadedArea(GameTestHelper helper) {
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HdTearResolve"));
        owner.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        helper.getLevel().addNewPlayer(owner);
        Cow held = EntityType.COW.create(helper.getLevel());
        Cow gone = EntityType.COW.create(helper.getLevel());
        StandPower power = PowerClass.STAND.attachGet(owner);
        try {
            helper.assertTrue(held != null && gone != null, "Cow fixture failed");
            held.setNoAi(true);
            held.setNoGravity(true);
            held.setPos(helper.absoluteVec(new Vec3(3.5D, 2.0D, 4.5D)));
            gone.setNoAi(true);
            gone.setNoGravity(true);
            gone.setPos(helper.absoluteVec(new Vec3(-0.5D, 2.0D, 4.5D)));
            helper.assertTrue(helper.getLevel().addFreshEntity(held) && helper.getLevel().addFreshEntity(gone),
                    "Cow fixtures could not join the level");
            power.setStand(HeavensDoorAddon.HEAVENS_DOOR.get());
            power.setResolveLevel(4);
            helper.assertTrue(HeavensDoorAddon.HEAVENS_DOOR.get().summon(owner, power), "Heaven's Door summon failed");
            HeavensDoorStandEntity stand = HeavensDoorAddon.controller(owner);
            helper.assertTrue(stand != null, "Missing summoned Heaven's Door");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            helper.assertTrue(BookStateService.markFromHit(stand, held) && BookStateService.markFromHit(stand, gone),
                    "Book marking failed");
            float max = power.getResolveCounter().getMaxResolveValue(power);
            helper.assertTrue(power.usesResolve() && max > 0.0F, "fixture: Heaven's Door does not use Resolve here");
            power.getResolveCounter().setResolveValue(power, max * 0.1F, 0);

            HeavensDoorGameTestAims.lookAt(owner, held);
            EntityActionInstance tear = start(power, stand, owner, HeavensDoorAddon.TEAR_PAGE_ID);
            float before = power.getResolveCounter().getResolveValue();
            tear.setPhaseStart(ActionPhase.PERFORM);
            tear._tickAction();
            float added = power.getResolveCounter().getResolveValue() - before;
            helper.assertTrue(HeavensDoorAddon.bookState(held).pageCount() == 1
                            && Math.abs(added - max * 0.005F) < max * 0.0005F,
                    "1.16 added maxResolve * 0.005 = " + max * 0.005F + " to the bar as the page came off; it gained "
                            + added + " (pages " + HeavensDoorAddon.bookState(held).pageCount() + ")");

            // the cow the hold started at is gone as the perform starts: the port says so and stops, 1.16 performed
            // all the same
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            HeavensDoorGameTestAims.lookAt(owner, gone);
            EntityActionInstance missed = start(power, stand, owner, HeavensDoorAddon.TEAR_PAGE_ID);
            helper.assertTrue(missed.getTaskTarget(helper.getLevel()).getEntity() == gone,
                    "fixture: the hold did not keep the second cow: " + missed.getTaskTarget(helper.getLevel()));
            gone.discard();
            before = power.getResolveCounter().getResolveValue();
            missed.setPhaseStart(ActionPhase.PERFORM);
            missed._tickAction();
            added = power.getResolveCounter().getResolveValue() - before;
            helper.assertTrue(Math.abs(added - max * 0.005F) < max * 0.0005F,
                    "1.16 added maxResolve * 0.005 = " + max * 0.005F + " to the bar on a perform whose target was gone;"
                            + " it gained " + added);
            LogUtils.getLogger().info("R770_HD_TEAR_RESOLVE_PASS added={} max={}", added, max);
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                HeavensDoorAddon.HEAVENS_DOOR.get().forceUnsummon(owner, power);
            }
            if (held != null) {
                held.discard();
            }
            if (gone != null) {
                gone.discard();
            }
            EventHooks.firePlayerLoggedOut(owner);
            owner.discard();
        }
    }

    private static EntityActionInstance start(StandPower power, HeavensDoorStandEntity stand, FakePlayer owner,
            String ability) {
        Ability type = power.getAbility(ability);
        EntityActionInstance action = ((EntityActionType) type).initActionOnAbilityUse(
                owner.level(), owner, stand, null);
        LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
        return action;
    }
}
