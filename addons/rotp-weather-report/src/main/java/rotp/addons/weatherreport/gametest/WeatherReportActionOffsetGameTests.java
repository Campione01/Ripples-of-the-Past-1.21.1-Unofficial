package rotp.addons.weatherreport.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.addons.weatherreport.ability.WeatherReportCloudShieldAbility;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 InitStands.java:72-77 and WeatherReportCloudShield.java:50: wind and cold wind standOffsetFront(), the icicle
 * strike standOffsetFromUser(0.2, 0.5) after it and the cloud shield standOffsetFromUser(0.0, -0.4), all at the default
 * y in full mode, as (left, up, forward) from the user's feet turned by the view yaw. Every one of them summoned the
 * whole Stand, the cloud shield too.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportActionOffsetGameTests {
    private static final short CLOUD_SHIELD_KEY = 91;
    private static final double EPS = 1.0E-4D;

    private WeatherReportActionOffsetGameTests() {}

    @GameTest(template = "empty", batch = "wr_action_offsets", timeoutTicks = 100)
    public static void weatherReportActionsPutTheStandWhereTheDonorDid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "wr-action-offsets")) {
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
        try {
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 12.0D, 2.5D));
            user.setNoGravity(true);
            user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Could not add the Weather Report user");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(WeatherReportAddon.WEATHER_REPORT.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && WeatherReportAddon.WEATHER_REPORT.get().summon(user, power),
                    "Could not summon Weather Report");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            StandEntity stand = power.getSummonedStandEntity();
            stand.updatePosition(user);
            helper.assertTrue(stand.isFollowingUser() && !stand.isArmsOnlyMode(),
                    "Setup: Weather Report is not a full Stand following its user");

            checkAction(helper, level, user, power, stand, WeatherReportAddon.WIND_NAME, 0.0D, 0.5D);
            checkAction(helper, level, user, power, stand, WeatherReportAddon.COLD_WIND_NAME, 0.0D, 0.5D);
            checkAction(helper, level, user, power, stand, WeatherReportAddon.ICICLE_STRIKE_NAME, 0.2D, 0.5D);
            checkAction(helper, level, user, power, stand, WeatherReportAddon.CLOUD_SHIELD_NAME, 0.0D, -0.4D);

            // the held shield pressed while only the arms are out brings the whole Stand out
            stand.setArmsOnlyMode();
            Ability shield = power.getAbility(WeatherReportAddon.CLOUD_SHIELD_NAME);
            AbilityInput.keyPress(CLOUD_SHIELD_KEY, shield, user, new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.HOLD, 0.0F, BufferingState.clickCanBuffer(), shield.abilityId);
            helper.assertTrue(stand.getCurStandAction() instanceof WeatherReportCloudShieldAbility.CloudShieldAction,
                    "Setup: the pressed cloud shield did not start: " + stand.getCurStandAction());
            helper.assertTrue(!stand.isArmsOnlyMode(),
                    "1.16 brought the whole Weather Report out for the cloud shield");
            assertTurned(helper, user, stand, 0.0D, -0.4D, "the pressed cloud shield");
            AbilityInput.keyRelease(CLOUD_SHIELD_KEY, user);
            LogUtils.getLogger().info("R770_WR_OFFSET_PASS action_offsets wind cold_wind icicle_strike cloud_shield");
            helper.succeed();
        }
        finally {
            if (power != null && power.isSummoned()) {
                WeatherReportAddon.WEATHER_REPORT.get().forceUnsummon(user, power);
            }
            user.discard();
        }
    }

    private static void checkAction(GameTestHelper helper, ServerLevel level, Player user, StandPower power,
            StandEntity stand, String name, double left, double forward) {
        helper.assertTrue(power.getAbility(name) instanceof EntityActionType,
                "Setup: Weather Report has no action " + name);
        EntityActionInstance action = ((EntityActionType) power.getAbility(name))
                .initActionOnAbilityUse(level, user, stand, null);
        LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action,
                "Setup: " + name + " did not become the Stand's action");
        assertTurned(helper, user, stand, left, forward, name);
        // a Stand left far from its user is retracted when its action ends
        stand.setPos(user.position());
        LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
    }

    private static void assertTurned(GameTestHelper helper, Player user, StandEntity stand,
            double left, double forward, String what) {
        // the body is turned away from the view: a body-yaw offset misses
        turn(user, 0.0F, 50.0F);
        assertAt(helper, user, stand, left, 0.2D, forward, what + ", view yaw 0, body yaw 50");
        // facing west the forward is -x and the left is -z
        turn(user, 90.0F, 0.0F);
        assertAt(helper, user, stand, -forward, 0.2D, left, what + ", view yaw 90, body yaw 0");
    }

    private static void turn(Player user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertAt(GameTestHelper helper, Player user, StandEntity stand,
            double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < EPS
                        && Math.abs(actual.y - dy) < EPS
                        && Math.abs(actual.z - dz) < EPS,
                "1.16 put Weather Report at its user's feet + (" + dx + ", " + dy + ", " + dz + ") during "
                        + when + ", got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }
}
