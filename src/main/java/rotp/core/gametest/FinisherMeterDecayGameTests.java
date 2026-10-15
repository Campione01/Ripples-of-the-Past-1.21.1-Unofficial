package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.tickFinisherMeter: once its no-decay window is over, the meter drains 0.025 per tick, and only
 * half as fast below the 0.5 finisher threshold. So the uppercut and finisher stay available as long as in 1.16.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FinisherMeterDecayGameTests {

	private FinisherMeterDecayGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void meterDrainsAtFullSpeedAboveTheFinisherThreshold(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FinisherDecay"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the finisher meter player");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Star Platinum: " + inserted.status());
			power.setResolveLevel(1);
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getCurStandAction() == null, "Summoned Star Platinum is missing or busy");
			helper.assertTrue(ResolveModeEffect.getResolveEffectLvl(user) < 0, "The user must not be in Resolve");

			// {meter before the tick, meter after it}
			float[][] steps = {
					{1.0F, 0.975F},
					{0.7F, 0.675F},
					{0.51F, 0.485F},
					{0.5F, 0.475F},
					{0.49F, 0.4775F},
					{0.2F, 0.1875F}};
			for (float[] step : steps) {
				stand.setFinisherMeter(step[0]);
				setNoDecayTicks(stand, 0);
				tickFinisherMeter(stand);
				float after = stand.getFinisherMeter();
				helper.assertTrue(Math.abs(after - step[1]) < 0.0001F, "The finisher meter at " + step[0]
						+ " drained to " + after + " in a tick; 1.16 drained it to " + step[1]);
			}

			// The no-decay window after a gain is unchanged.
			stand.setFinisherMeter(0.7F);
			setNoDecayTicks(stand, 2);
			tickFinisherMeter(stand);
			tickFinisherMeter(stand);
			helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.7F) < 0.0001F,
					"The finisher meter drained inside its no-decay window: " + stand.getFinisherMeter());
			tickFinisherMeter(stand);
			helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.675F) < 0.0001F,
					"The finisher meter did not drain after its no-decay window: " + stand.getFinisherMeter());
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	/**
	 * 1.16 StandEntity.addFinisherMeter and tick: under the Resolve effect a gain counts twice and the meter drains half
	 * as fast again, while a spend such as the heavy punch's 0.51 is taken as it is.
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void resolveDoublesFinisherGainsAndHalvesTheDrain(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FinisherResolve"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the finisher meter player");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Star Platinum: " + inserted.status());
			power.setResolveLevel(1);
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getCurStandAction() == null, "Summoned Star Platinum is missing or busy");
			user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400));
			helper.assertTrue(ResolveModeEffect.getResolveEffectLvl(user) >= 0, "The user is not in Resolve");

			// {meter before the tick, meter after it}
			float[][] steps = {
					{1.0F, 0.9875F},
					{0.5F, 0.4875F},
					{0.49F, 0.48375F}};
			for (float[] step : steps) {
				stand.setFinisherMeter(step[0]);
				setNoDecayTicks(stand, 0);
				tickFinisherMeter(stand);
				float after = stand.getFinisherMeter();
				helper.assertTrue(Math.abs(after - step[1]) < 0.0001F, "In Resolve the finisher meter at " + step[0]
						+ " drained to " + after + " in a tick; 1.16 drained it to " + step[1]);
			}

			stand.setFinisherMeter(0.2F);
			stand.addFinisherMeter(0.1F, 0);
			helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.4F) < 0.0001F,
					"In Resolve a 0.1 gain from 0.2 left the meter at " + stand.getFinisherMeter() + "; 1.16 left 0.4");

			stand.setFinisherMeter(1.0F);
			stand.addFinisherMeter(-0.51F, 0);
			helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.49F) < 0.0001F,
					"In Resolve the heavy punch's 0.51 spend from a full meter left " + stand.getFinisherMeter()
					+ "; 1.16 left 0.49");
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	private static void tickFinisherMeter(StandEntity stand) {
		try {
			Method tick = StandEntity.class.getDeclaredMethod("tickFinisherMeter");
			tick.setAccessible(true);
			tick.invoke(stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("StandEntity.tickFinisherMeter is not reachable", e);
		}
	}

	private static void setNoDecayTicks(StandEntity stand, int ticks) {
		try {
			Field field = StandEntity.class.getDeclaredField("noFinisherDecayTicks");
			field.setAccessible(true);
			field.setInt(stand, ticks);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("StandEntity.noFinisherDecayTicks is not reachable", e);
		}
	}
}
