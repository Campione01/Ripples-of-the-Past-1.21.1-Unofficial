package rotp.addons.weatherreport.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.ability.WeatherReportCloudShieldAbility;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * Owner rule of 2026-10-15: only a Stand's own guard halves its user's walk speed. 1.16 held the Cloud Shield at half
 * speed (WeatherReportCloudShield.java:50 standUserWalkSpeed(0.5f)); the owner's tenth card gives it, a shield that is
 * no guard, full speed, so its user walks at full speed in every phase.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportCloudShieldWalkGameTests {
    private WeatherReportCloudShieldWalkGameTests() {}

    @GameTest(template = "empty", batch = "wr_shield_walk_cloud_shield", timeoutTicks = 100)
    public static void heldCloudShieldKeepsTheUsersFullWalkSpeed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wr-shield-walk")) {
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
        user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        StandPower power = null;
        LivingComponentAction component = null;
        try {
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WeatherReportAddon.WEATHER_REPORT.get().summon(user, power),
                    "Could not summon Weather Report");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user, "Setup: the Stand has no user");
            Ability ability = power.getAbility(WeatherReportAddon.CLOUD_SHIELD_NAME);
            helper.assertTrue(ability instanceof WeatherReportCloudShieldAbility,
                    "Setup: Weather Report has no cloud shield: " + ability);
            EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, user, stand, null);
            helper.assertTrue(action instanceof WeatherReportCloudShieldAbility.CloudShieldAction,
                    "Setup: the cloud shield action is " + action);
            component = LivingComponentAction.getComponent(stand);
            component.setAction(action, user, SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action && action.getPhase() != null,
                    "Setup: the cloud shield did not start");
            float atStart = action.userWalkSpeed;
            action.setPhaseStart(ActionPhase.PERFORM);
            float perform = action.userWalkSpeed;
            action.setPhaseStart(ActionPhase.RECOVERY);
            float recovery = action.userWalkSpeed;
            helper.assertTrue(atStart == 1.0F && perform == 1.0F && recovery == 1.0F,
                    "The cloud shield slowed its user (owner rule 2026-10-15: full speed in every phase): start="
                            + atStart + " perform=" + perform + " recovery=" + recovery);
            LogUtils.getLogger().info("R770_WR_SHIELD_WALK_PASS cloud_shield start={} perform={} recovery={}",
                    atStart, perform, recovery);
        } finally {
            try {
                if (component != null) {
                    component.setAction(null, SyncType.NO_SYNC);
                }
            } finally {
                if (power != null && power.isSummoned()) {
                    WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
                }
                user.discard();
            }
        }
        helper.succeed();
    }
}
