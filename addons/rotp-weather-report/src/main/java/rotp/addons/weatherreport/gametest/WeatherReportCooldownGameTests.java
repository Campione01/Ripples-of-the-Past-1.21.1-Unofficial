package rotp.addons.weatherreport.gametest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.weatherreport.WeatherReportAddon;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional, resolveMultiplier), which a
 * Stand-bearing mob waited (+ 10) before its next pick and Resolve never shortened. The only Weather Report
 * registration with a technical part is the icicle strike's cooldown(10, 100) (042 InitStands.java:77); the others are
 * plain cooldown(n) (:71-76, :79), the two winds with a getCooldownAdditional scaled by the ticks held
 * (WeatherReportWind.java:65-66), so only their technical part is compared.
 */
@GameTestHolder("rotp_wr_reach")
@PrefixGameTestTemplate(false)
public final class WeatherReportCooldownGameTests {
    private static final int HELD_SCALED = -1;

    private WeatherReportCooldownGameTests() {}

    private record Row(String ability, int technical, int additional) {}

    private static final List<Row> ROWS = List.of(
            new Row(WeatherReportAddon.ICICLE_STRIKE_NAME, 10, 100),
            new Row(WeatherReportAddon.CLOUD_SHIELD_NAME, 0, 20),
            new Row(WeatherReportAddon.GIVE_CLOUD_SHIELD_NAME, 0, 1300),
            new Row(WeatherReportAddon.LIGHTNING_NAME, 0, 60),
            new Row(WeatherReportAddon.CHANGE_WEATHER_NAME, 0, 1200),
            new Row(WeatherReportAddon.FUGU_RAIN_NAME, 0, 720),
            new Row(WeatherReportAddon.WIND_NAME, 0, HELD_SCALED),
            new Row(WeatherReportAddon.COLD_WIND_NAME, 0, HELD_SCALED));

    @GameTest(template = "empty", batch = "wr_cooldown_split", timeoutTicks = 40)
    public static void iceStrikeKeepsTheDonorsTechnicalAndAdditionalCooldown(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        Cow user = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(user != null, "setup: no user");
        Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
        try {
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(WeatherReportAddon.WEATHER_REPORT.get());
            for (Row row : ROWS) {
                Ability ability = power.getAbility(row.ability());
                helper.assertTrue(ability != null, "setup: Weather Report has no " + row.ability());
                int technical = ability.getCooldownTechnical(power);
                int full = ability.getCooldown(power, -1);
                if (technical != row.technical()
                        || row.additional() != HELD_SCALED && full != row.technical() + row.additional()) {
                    wrong.add(row.ability() + " reports technical " + technical + " of a full " + full + ", the donor's "
                            + row.technical() + (row.additional() == HELD_SCALED ? ""
                                    : " of " + (row.technical() + row.additional())));
                }
            }
            user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200));
            for (Row row : ROWS) {
                if (row.additional() == HELD_SCALED) {
                    continue;
                }
                int inResolve = power.getAbility(row.ability()).getCooldown(power, -1);
                if (inResolve != row.technical()) {
                    wrong.add(row.ability() + " takes " + inResolve + " ticks in Resolve, the donor's " + row.technical()
                            + " (multiplier 0 drops the additional part)");
                }
            }
        }
        finally {
            user.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Weather Report cooldown(technical, additional): "
                + String.join("; ", wrong));
        LogUtils.getLogger().info("R770_WR_COOLDOWN_PASS icicle_strike_split {} abilities", ROWS.size());
        helper.succeed();
    }
}
