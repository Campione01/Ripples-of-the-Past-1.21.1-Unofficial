package rotp.addons.stand_bearing_creatures.entity.goal;

import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.addons.stand_bearing_creatures.MobsWithPowersAddon;
import rotp.addons.stand_bearing_creatures.util.MobPowerUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MobsWithPowersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManualHeldTargetLossGameTests {
    private ManualHeldTargetLossGameTests() {}

    /*
     * 1.16 050 NonStandAI drew only unlocked actions (NonStandPowerType.getUnlockedDefaultActions, isUnlocked) and,
     * once its target was dead, called stopHeldAction(true); PowerBaseImpl.stopHeldAction fired a non-holdOnly hold
     * when checkRequirements passed, isUnlocked last. Sunlight Yellow Overdrive has no target requirement
     * (Action.getTargetRequirement NONE), so checkTarget let the dead target go and the learned technique punched.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void manualHamonChargeReleasesWhenTargetDies(GameTestHelper helper) {
        var level = helper.getLevel();
        Cow mob = EntityType.COW.create(level);
        Cow target = EntityType.COW.create(level);
        helper.assertTrue(mob != null && target != null, "Missing mob fixtures");
        try {
            mob.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
            target.setPos(mob.position().add(0, 0, 3));
            level.addFreshEntity(mob);
            level.addFreshEntity(target);
            mob.setTarget(target);
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(mob);
            power.setPowerType(ModPlayerPowers.HAMON.get());
            var hamon = power.getCurTypeData(ModPlayerPowers.HAMON).orElseThrow();
            hamon.setEnergy(hamon.getMaxEnergy());
            // A Hamon mob holds the technique only once it is learned, as the core's Hamon master learns it
            helper.assertTrue(hamon.learnSkill(ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get()),
                    "Could not learn SYO");
            LivingComponentAction.getComponent(mob).entityAim.setTarget(new ActionTarget(target));
            var ability = MobPowerUtil.availableMobAbilities(power).stream()
                    .filter(available -> available.name().equals("sunlight_yellow_overdrive"))
                    .findFirst()
                    .orElse(null);
            helper.assertTrue(ability != null && ManualHeldInput.press(ability, mob),
                    "The learned SYO is not among the mob's abilities or could not be pressed");
            var action = LivingComponentAction.getCurEntityAction(mob);
            helper.assertTrue(action != null && action.getPhase() == ActionPhase.WINDUP,
                    "SYO was not charging");
            helper.assertTrue(ManualHeldInput.isHolding(mob, PowerClass.PLAYER_POWER),
                    "Manual input was not tracked");
            helper.assertTrue(power.hasHeldInput(), "The core input was not held");

            target.setHealth(0.0F);
            new PlayerPowerMobGoal(mob).tick();
            helper.assertTrue(!ManualHeldInput.isHolding(mob, PowerClass.PLAYER_POWER),
                    "Lost target left the manual key held");
            helper.assertTrue(!power.hasHeldInput(), "Lost target left the core key held");
            helper.assertTrue(!action.isOver() && action.getPhase() == ActionPhase.PERFORM,
                    "1.16 stopHeldAction(true) fired the learned SYO whose target died; the lost target cancelled it "
                            + "(phase " + action.getPhase() + ")");
            helper.succeed();
        } finally {
            if (mob != null) {
                ManualHeldInput.release(mob);
                mob.discard();
            }
            if (target != null) {
                target.discard();
            }
        }
    }
}
