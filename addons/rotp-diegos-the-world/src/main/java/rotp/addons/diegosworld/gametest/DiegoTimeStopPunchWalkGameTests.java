package rotp.addons.diegosworld.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Owner rule of 2026-10-15: of all Stand actions only a Stand's own guard slows its user. The 1.16 Diego's The World
 * time-stop punch (standUserWalkSpeed(0.8)) therefore keeps its user at full walk speed in every phase.
 */
@GameTestHolder("rotp_theworld_bleeding")
@PrefixGameTestTemplate(false)
public final class DiegoTimeStopPunchWalkGameTests {
    private DiegoTimeStopPunchWalkGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "rotpDiegoTimeStopPunchWalk")
    public static void timeStopPunchKeepsTheUsersFullWalkSpeed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "diego-walk")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        user.setNoGravity(true);
        user.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        StandPower power = null;
        LivingComponentAction component = null;
        try {
            helper.assertTrue(level.addFreshEntity(user), "Setup: the user was not added");
            power = PowerClass.STAND.attachGet(user);
            power.setStand(DiegosTheWorldAddon.THE_WORLD.get());
            helper.assertTrue(DiegosTheWorldAddon.THE_WORLD.get().summon(user, power),
                    "Could not summon Diego's The World.");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned Diego's The World");
            Ability ability = power.getAbility(DiegosTheWorldAddon.TIME_STOP_PUNCH);
            helper.assertTrue(ability instanceof EntityActionType,
                    "Setup: Diego's The World has no " + DiegosTheWorldAddon.TIME_STOP_PUNCH + ": " + ability);
            EntityActionInstance action = ((EntityActionType) ability)
                    .initActionOnAbilityUse(level, user, stand, null);
            helper.assertTrue(action instanceof TheWorldTSPunch,
                    "Setup: the time-stop punch is " + action);
            component = LivingComponentAction.getComponent(stand);
            component.setAction(action, user, SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action && action.getPhase() != null,
                    "Setup: the time-stop punch did not start");
            float atStart = stand.getUserWalkSpeed(action.userWalkSpeed);
            action.setPhaseStart(ActionPhase.WINDUP);
            float windup = stand.getUserWalkSpeed(action.userWalkSpeed);
            action.setPhaseStart(ActionPhase.PERFORM);
            float perform = stand.getUserWalkSpeed(action.userWalkSpeed);
            action.setPhaseStart(ActionPhase.RECOVERY);
            float recovery = stand.getUserWalkSpeed(action.userWalkSpeed);
            helper.assertTrue(atStart == 1.0F && windup == 1.0F && perform == 1.0F && recovery == 1.0F,
                    "the time-stop punch slowed its user (owner rule 2026-10-15: full speed in every phase): start="
                            + atStart + " windup=" + windup + " perform=" + perform + " recovery=" + recovery);
        } finally {
            try {
                if (component != null) {
                    component.setAction(null, SyncType.NO_SYNC);
                }
            } finally {
                if (power != null && power.isSummoned()) {
                    DiegosTheWorldAddon.THE_WORLD.get().forceUnsummon(user, power);
                }
                user.discard();
            }
        }
        helper.succeed();
    }
}
