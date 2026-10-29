package rotp.addons.diverdown.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeformDashGameTests {
    private DeformDashGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 20)
    public static void deformDashStopsAtDonorHalfPhase(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 0, placement -> {
            for (int length : new int[] {2, 4, 5, 8}) {
                verifyPhase(helper, placement, length);
            }
            helper.succeed();
        });
    }

    // 1.16 DiverDownDeformDash.checkStandTarget refused every target, so a press at a mob or a block left the dash none
    @GameTest(template = "empty", templateNamespace = "rotp_dd_review_fixes", timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + 20)
    public static void deformDashTakesNoPressedTargetAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 0, placement -> {
            Cow owner = EntityType.COW.create(helper.getLevel());
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(owner != null && zombie != null, "Missing Diver Down fixtures");
            StandEntity stand = null;
            try {
                owner.setNoAi(true);
                owner.setNoGravity(true);
                owner.setPos(placement.owner);
                helper.getLevel().addFreshEntity(owner);
                zombie.setNoAi(true);
                zombie.setNoGravity(true);
                zombie.setPos(placement.owner.add(3.0D, 0.0D, 0.0D));
                helper.getLevel().addFreshEntity(zombie);
                var power = PowerClass.STAND.attachGet(owner);
                power.setStand(DiverDownAddon.DIVER_DOWN.get());
                power.setStamina(power.getMaxStamina());
                helper.assertTrue(DiverDownAddon.DIVER_DOWN.get().summon(owner, power),
                        "Could not summon Diver Down");
                stand = power.getSummonedStandEntity();
                helper.assertTrue(stand != null, "Missing registered Diver Down Stand");
                var ability = (DeformDashAbility) power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                helper.assertTrue(ability != null, "Missing registered deform-dash ability");
                ActionTarget atZombie = new ActionTarget(zombie);
                ActionTarget atBlock = new ActionTarget(BlockPos.containing(placement.owner).below(), Direction.UP);
                helper.assertTrue(!ability.takesPressedTarget(stand, atZombie)
                                && !ability.takesPressedTarget(stand, atBlock),
                        "1.16 DiverDownDeformDash.checkStandTarget refused a pressed mob and block; the port took mob "
                                + ability.takesPressedTarget(stand, atZombie) + ", block "
                                + ability.takesPressedTarget(stand, atBlock));
                LivingComponentAction.getComponent(stand).entityAim.setTarget(atZombie);
                var action = ability.initActionOnAbilityUse(helper.getLevel(), owner, stand, null);
                action.setStartingPhase();
                LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
                helper.assertTrue(action.standRotationTarget == null,
                        "1.16 left the deform dash pressed at a zombie no target to turn to; it holds "
                                + action.standRotationTarget);
                helper.succeed();
            } finally {
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
                }
                var power = PowerClass.STAND.get(owner);
                if (power != null && power.hasPower()) {
                    power.getPowerType().forceUnsummon(owner, power);
                }
                zombie.discard();
                owner.discard();
            }
        });
    }

    private static void verifyPhase(GameTestHelper helper, GameTestPlacement placement, int length) {
        Cow owner = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(owner != null, "Missing Diver Down caster fixture");
        StandEntity stand = null;
        try {
            owner.setNoAi(true);
            owner.setNoGravity(true);
            owner.setPos(placement.owner);
            GameTestPlacement.assertTicks(helper, "owner", owner);
            helper.getLevel().addFreshEntity(owner);
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(DiverDownAddon.DIVER_DOWN.get());
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(DiverDownAddon.DIVER_DOWN.get().summon(owner, power),
                    "Could not summon Diver Down");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing registered Diver Down Stand");
            var ability = (DeformDashAbility) power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
            helper.assertTrue(ability != null, "Missing registered deform-dash ability");
            var action = ability.initActionOnAbilityUse(helper.getLevel(), owner, stand, null);
            action.phasesLength.put(ActionPhase.PERFORM, length);
            action.phasesLength.put(ActionPhase.RECOVERY, 5);
            action.setPhaseStart(ActionPhase.PERFORM);
            var component = LivingComponentAction.getComponent(stand);
            component.setAction(action, owner, SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action, "Dash action was rejected");
            for (int tick = 0; tick < length; tick++) {
                helper.assertTrue(action.getPhase() == ActionPhase.PERFORM
                        && action.getPhaseTick() == tick, "Unexpected dash phase progression");
                action._tickAction();
                boolean expectedForward = (tick + 1.0F) / length <= 0.5F;
                double speed = stand.getDeltaMovement().length();
                helper.assertTrue(expectedForward
                                ? Math.abs(speed - DeformDashAbility.DASH_DISTANCE / length) < 1.0E-6D
                                : speed < 1.0E-6D,
                        "Dash movement differs from donor at tick " + tick + " / " + length);
            }
            component.setAction(null, owner, SyncType.NO_SYNC);
            helper.assertTrue(stand.getDeltaMovement().lengthSqr() == 0,
                    "Cleared dash retained movement");
        } finally {
            if (stand != null) {
                LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
            }
            var power = PowerClass.STAND.get(owner);
            if (power != null && power.hasPower()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            owner.discard();
        }
    }
}
