package rotp.addons.purplehaze.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.entity.PurpleHazeEntity;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 InitStands :66 and :68: purple_haze_capsule_shot (holdToFire(10, false) in PurpleHazeCapsuleShot's constructor)
 * and purple_haze_madness (holdToFire(40, false)) had no standRecoveryTicks, so the Stand's task ended with the perform
 * that fired and nothing was left to hold the next press back.
 */
@GameTestHolder(PurpleHazeAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PurpleHazeFiredChargeGameTests {
    private PurpleHazeFiredChargeGameTests() {}

    @GameTest(template = "empty", batch = "ph_fired_charge", timeoutTicks = 40)
    public static void firedChargesLeaveNoRecoveryAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "PHFiredCharge"));
        StandPower power = null;
        List<String> wrong = new ArrayList<>();
        try {
            Vec3 origin = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            owner.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            owner.setNoGravity(true);
            level.addNewPlayer(owner);
            power = StandPower.get(owner);
            helper.assertTrue(power != null, "The fake player has no Stand power");
            power.setStand(PurpleHazeAddon.PURPLE_HAZE.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(owner, power), "Could not summon Purple Haze");
            PurpleHazeEntity stand = (PurpleHazeEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing the Purple Haze Stand");
            stand.summonLockTicks = 0;
            LivingComponentAction component = LivingComponentAction.getComponent(stand);
            for (String name : List.of(PurpleHazeAddon.CAPSULE_SHOT_NAME, PurpleHazeAddon.MADNESS_NAME)) {
                EntityActionType type = (EntityActionType) power.getAbility(name);
                helper.assertTrue(type != null, "Purple Haze has no " + name);
                EntityActionInstance action = type.initActionOnAbilityUse(level, owner, stand, null);
                component.setAction(action, owner, SyncType.NO_SYNC);
                // the charge has fired; one server tick runs the perform
                action.setPhase(ActionPhase.PERFORM, 0);
                action._tickAction();
                if (!action.isOver()) {
                    wrong.add(name + " still runs in " + action.getPhase() + " after the perform that fired");
                }
                component.setAction(null, owner, SyncType.NO_SYNC);
            }
        }
        finally {
            if (power != null && power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            owner.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 left no Purple Haze task once a charge fired (no standRecoveryTicks): "
                + wrong);
        helper.succeed();
    }

    // 1.16 InitStands :67: purple_haze_virus_aura had cooldown(20) and no standRecoveryTicks, so its 1-tick perform
    // that toggled the aura ended the Stand's task.
    @GameTest(template = "empty", batch = "ph_virus_aura_no_recovery", timeoutTicks = 40)
    public static void virusAuraLeavesNoRecoveryAsIn116(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "PHVirusAura"));
        StandPower power = null;
        String wrong = null;
        try {
            Vec3 origin = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            owner.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            owner.setNoGravity(true);
            level.addNewPlayer(owner);
            power = StandPower.get(owner);
            helper.assertTrue(power != null, "The fake player has no Stand power");
            power.setStand(PurpleHazeAddon.PURPLE_HAZE.get());
            power.skipProgression();
            helper.assertTrue(PurpleHazeAddon.PURPLE_HAZE.get().summon(owner, power), "Could not summon Purple Haze");
            PurpleHazeEntity stand = (PurpleHazeEntity) power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing the Purple Haze Stand");
            stand.summonLockTicks = 0;
            LivingComponentAction component = LivingComponentAction.getComponent(stand);
            EntityActionType type = (EntityActionType) power.getAbility(PurpleHazeAddon.VIRUS_AURA_NAME);
            helper.assertTrue(type != null, "Purple Haze has no " + PurpleHazeAddon.VIRUS_AURA_NAME);
            EntityActionInstance action = type.initActionOnAbilityUse(level, owner, stand, null);
            component.setAction(action, owner, SyncType.NO_SYNC);
            boolean auraBefore = stand.hasAura();
            action._tickAction();
            if (!action.isOver() || stand.hasAura() == auraBefore) {
                wrong = "aura toggled " + (stand.hasAura() != auraBefore) + ", the action still runs in "
                        + action.getPhase();
            }
            component.setAction(null, owner, SyncType.NO_SYNC);
            if (stand.hasAura()) {
                stand.setAuraActive(false);
            }
        }
        finally {
            if (power != null && power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            owner.discard();
        }
        helper.assertTrue(wrong == null, "1.16 purple_haze_virus_aura toggled the aura in its 1-tick perform and left no "
                + "task (no standRecoveryTicks): " + wrong);
        helper.succeed();
    }
}
