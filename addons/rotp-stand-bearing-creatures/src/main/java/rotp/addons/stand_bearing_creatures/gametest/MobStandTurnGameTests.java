package rotp.addons.stand_bearing_creatures.gametest;

import rotp.core.init.power.ModStands;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.entity.goal.StandPowerMobGoal;
import rotp.addons.stand_bearing_creatures.event.MobPowerEvents;
import rotp.addons.stand_bearing_creatures.event.MobStandRotation;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;

/**
 * 1.16 turned a mob's Stand once a tick, in StandEntity.tick: to the target
 * its task held (StandEntityTask.rotateStand), and only when the task held
 * none to the mob's target (050 StandEntityMixin.defaultRotation). 050
 * StandAI did not turn it. The port asks MobStandRotation through the core's
 * StandDefaultRotationRules in the same place.
 */
@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobStandTurnGameTests {
    private MobStandTurnGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aMobsStandTurnsToItsTaskTargetBeforeTheMobsTargetAsIn116(
            GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow struck = EntityType.COW.create(level);
        Cow chased = EntityType.COW.create(level);
        helper.assertTrue(mob != null && struck != null && chased != null,
                "Missing mob fixtures");
        StandPower power = null;
        try {
            // a struck cow in an unloaded chunk could not be found as the punch's target
            Vec3 pos = TemplateChunkLayout.absolute(helper, new Vec3(2.5D, 2.0D, 2.5D), -1.5D, 5.0D, 1.0D, 4.0D);
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            standStill(struck, pos.add(1.5D, 0.0D, 0.0D));
            standStill(chased, pos.add(-3.0D, 0.0D, 0.0D));
            level.addFreshEntity(mob);
            level.addFreshEntity(struck);
            level.addFreshEntity(chased);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.STAR_PLATINUM.get(), true),
                    "Could not give the husk Star Platinum.");
            power = StandPower.get(mob);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power),
                    "Could not summon the husk's Star Platinum.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser(),
                    "Setup: the husk's Stand does not follow it.");
            stand.summonLockTicks = 0;
            mob.setTarget(chased);

            standTick(stand, mob);
            assertFaces(helper, stand, chased,
                    "1.16 defaultRotation turned a mob's Stand with no task to the mob's target");

            LivingComponentAction.getComponent(mob).entityAim.setTarget(new ActionTarget(struck));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(struck));
            Ability punch = power.getAbility("punch");
            helper.assertTrue(punch instanceof EntityActionType, "Star Platinum has no punch.");
            EntityActionInstance action = ((EntityActionType) punch)
                    .initActionOnAbilityUse(level, mob, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, mob, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == action
                            && action.getTaskTarget(level).getEntity() == struck,
                    "Setup: the punch holds " + action.getTaskTarget(level) + ", not the struck cow.");
            // stands in for an action that names no rotation target of its own
            action.standRotationTarget = null;
            // the goal's aim runs in the mob's tick, which may come after its Stand's
            standTick(stand, mob);
            new StandPowerMobGoal(mob).tick();
            assertFaces(helper, stand, struck,
                    "1.16 StandEntityTask.rotateStand turned a mob's Stand to the target its task held,"
                            + " not to the mob's target");
            helper.succeed();
        } finally {
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            struck.discard();
            chased.discard();
        }
    }

    // 1.16 050 StandEntityMixin.defaultRotation:96-110: for a mob with no
    // target it set no rotation, with no task or a task holding no target;
    // the core would copy the mob's rotation, which the rule must prevent.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aMobsStandKeepsItsRotationWhileTheMobHasNoTargetAsIn116(
            GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        helper.assertTrue(mob != null, "Missing mob fixture");
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            level.addFreshEntity(mob);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.STAR_PLATINUM.get(), true),
                    "Could not give the husk Star Platinum.");
            power = StandPower.get(mob);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power),
                    "Could not summon the husk's Star Platinum.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser(),
                    "Setup: the husk's Stand does not follow it.");
            stand.summonLockTicks = 0;
            mob.setTarget(null);

            keepsItsRotation(helper, stand, mob, "with no action");

            LivingComponentAction.getComponent(mob).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
            EntityActionInstance action = ((EntityActionType) power.getAbility("punch"))
                    .initActionOnAbilityUse(level, mob, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, mob, SyncType.NO_SYNC);
            action.standRotationTarget = null;
            helper.assertTrue(stand.getCurStandAction() == action
                            && action.getTaskTarget(level).getType() == ActionTarget.TargetType.EMPTY,
                    "Setup: the punch holds " + action.getTaskTarget(level) + ".");
            keepsItsRotation(helper, stand, mob, "with a punch holding no target");
            helper.succeed();
        } finally {
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
        }
    }

    // 1.16 050 StandEntityMixin.defaultRotation:98-106 faced mobUser.getTarget()
    // whatever its state: a target that has just died is still faced.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aMobsStandFacesItsDeadTargetAsIn116(
            GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Cow dead = EntityType.COW.create(level);
        helper.assertTrue(mob != null && dead != null, "Missing mob fixtures");
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            standStill(dead, pos.add(3.0D, 0.0D, 0.0D));
            level.addFreshEntity(mob);
            level.addFreshEntity(dead);
            helper.assertTrue(MobPowerUtil.isEligibleMob(mob)
                            && MobPowerUtil.grantStand(mob, ModStands.STAR_PLATINUM.get(), true),
                    "Could not give the husk Star Platinum.");
            power = StandPower.get(mob);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power),
                    "Could not summon the husk's Star Platinum.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.isFollowingUser(),
                    "Setup: the husk's Stand does not follow it.");
            stand.summonLockTicks = 0;
            mob.setTarget(dead);
            dead.kill();
            helper.assertTrue(mob.getTarget() == dead && !dead.isAlive() && !dead.isRemoved(),
                    "Setup: the mob's target is not dead and still there.");

            standTick(stand, mob);
            assertFaces(helper, stand, dead,
                    "1.16 defaultRotation turned a mob's Stand to the mob's target although it was dead");
            helper.succeed();
        } finally {
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            dead.discard();
        }
    }

    // 1.16 defaultRotation checked only "user instanceof MobEntity". The rule
    // runs on clients too, which may hold another entity list than the
    // server, so it must not read the add-on's eligibility; and it leaves
    // the Stand of a user that is not a mob to the core.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void theMobStandRotationRuleAsksOnlyForAMobUserAsIn116(
            GameTestHelper helper) {
        var level = helper.getLevel();
        Mob mob = EntityType.HUSK.create(level);
        Mob dragon = EntityType.ENDER_DRAGON.create(level);
        Cow target = EntityType.COW.create(level);
        ArmorStand notAMob = EntityType.ARMOR_STAND.create(level);
        helper.assertTrue(mob != null && dragon != null && target != null && notAMob != null,
                "Missing fixtures");
        StandPower power = null;
        try {
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D));
            mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            standStill(target, pos.add(3.0D, 0.0D, 0.0D));
            level.addFreshEntity(mob);
            level.addFreshEntity(target);
            helper.assertTrue(MobPowerUtil.grantStand(mob, ModStands.STAR_PLATINUM.get(), true),
                    "Could not give the husk Star Platinum.");
            power = StandPower.get(mob);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(power.getPowerType().summon(mob, power),
                    "Could not summon the husk's Star Platinum.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no Stand.");
            helper.assertTrue(!MobPowerUtil.isEligibleMob(dragon),
                    "Setup: the ender dragon is eligible for the add-on.");

            dragon.setTarget(target);
            stand.setYRot(180.0F);
            stand.setXRot(0.0F);
            helper.assertTrue(MobStandRotation.rotate(stand, dragon),
                    "1.16 defaultRotation took over for every MobEntity user, eligible or not;"
                            + " the rule did not for an ender dragon.");
            assertFaces(helper, stand, target,
                    "1.16 defaultRotation turned the Stand of any MobEntity to its target");

            stand.setYRot(180.0F);
            stand.setXRot(0.0F);
            helper.assertTrue(!MobStandRotation.rotate(stand, notAMob)
                            && stand.getYRot() == 180.0F && stand.getXRot() == 0.0F,
                    "The rule took over the default rotation of a user that is not a mob.");
            helper.succeed();
        } finally {
            if (power != null && power.isSummoned()) {
                power.getPowerType().forceUnsummon(mob, power);
            }
            mob.discard();
            dragon.discard();
            target.discard();
            notAMob.discard();
        }
    }

    private static void keepsItsRotation(GameTestHelper helper, StandEntity stand,
            Mob mob, String when) {
        mob.setYRot(0.0F);
        mob.setXRot(0.0F);
        mob.setYHeadRot(0.0F);
        mob.yBodyRot = 0.0F;
        stand.setYRot(90.0F);
        stand.setXRot(10.0F);
        stand.setYHeadRot(90.0F);
        stand.yBodyRot = 90.0F;
        standTick(stand, mob);
        helper.assertTrue(stand.getYRot() == 90.0F && stand.getXRot() == 10.0F
                        && stand.getYHeadRot() == 90.0F,
                "1.16 defaultRotation set no rotation on the Stand of a mob with no target " + when
                        + "; it has yaw " + stand.getYRot() + ", pitch " + stand.getXRot()
                        + ", head " + stand.getYHeadRot() + " instead of 90, 10, 90.");
    }

    // A server Stand tick past the entity tick: the core's turn
    // (updatePosition, which asks the add-on's rotation rule), then the
    // add-on's tick-post handler.
    private static void standTick(StandEntity stand, Mob mob) {
        stand.updatePosition(mob);
        MobPowerEvents.onEntityTickPost(new EntityTickEvent.Post(stand));
    }

    private static void standStill(Cow cow, Vec3 pos) {
        cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        cow.setNoAi(true);
        cow.setNoGravity(true);
    }

    private static void assertFaces(GameTestHelper helper, StandEntity stand,
            LivingEntity target, String claim) {
        Vec3 to = target.getEyePosition().subtract(stand.getEyePosition()).normalize();
        Vec3 look = stand.getLookAngle();
        helper.assertTrue(look.dot(to) > 0.999D,
                claim + "; it looks along " + look + " instead of " + to + ".");
    }
}
