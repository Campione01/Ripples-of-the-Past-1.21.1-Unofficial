package rotp.core.gametest;

import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.getFinisherMeter: the finisher meter reads 0 until Resolve level 1 or an
 * unlocked finisher variation (StandUtil.isFinisherMechanicUnlocked). Only the Stand's main
 * finisher counts (1.16 getStandFinisherPunch); the port's grab-mode finisher does not.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandFinisherUnlockGameTests {
	private StandFinisherUnlockGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void finisherMeterLockedUntilResolveOrFinisherUnlock(GameTestHelper helper) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.fromString("5b1e0c2a-7d34-4f6e-8a1c-2e9f4b7d6a31"), "FinisherUnlock"));
		StandPower power = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the finisher player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.setResolveLevel(0);
			Ability finisher = power.getAbility("finisher_uppercut");
			helper.assertTrue(finisher != null && finisher.isStandFinisherOf != null,
					"Star Platinum lost its finisher uppercut");
			power.getCurTypeData()._lockedAbilities.remove("finisher_uppercut");
			helper.assertTrue(!finisher.isAbilityUnlocked(power), "Fixture: the finisher uppercut is unlocked at Resolve 0");
			// port-only grab-mode finisher: unlocked at Resolve 0, must not open the meter
			Ability grabFinisher = power.getAbility("grab_uppercut");
			helper.assertTrue(grabFinisher != null && grabFinisher.isStandFinisherOf != null,
					"Star Platinum lost its grab uppercut finisher");
			power.getCurTypeData()._lockedAbilities.remove("grab_uppercut");
			helper.assertTrue(grabFinisher.isAbilityUnlocked(power), "Fixture: the grab uppercut is locked at Resolve 0");
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Star Platinum is missing");
			stand.setFinisherMeter(1.0F);

			// Resolve 0, main finisher locked (grab finisher unlocked): the meter reads 0
			helper.assertTrue(!StandEntity.isFinisherVariationUnlocked(power),
					"The unlocked grab uppercut counts as the Stand finisher");
			helper.assertTrue(!StandEntity.isFinisherMechanicUnlocked(power),
					"The finisher mechanic is unlocked at Resolve 0");
			helper.assertTrue(stand.getFinisherMeter() == 0,
					"Locked finisher meter reads " + stand.getFinisherMeter() + " instead of 0");
			helper.assertTrue(!stand.willHeavyPunchBeFinisher() && !stand.willHeavyFinisherVariationFire(),
					"A locked finisher meter still makes the heavy punch a finisher");

			// Resolve 1: the stored meter shows again
			power.setResolveLevel(1);
			helper.assertTrue(StandEntity.isFinisherMechanicUnlocked(power), "Resolve 1 did not unlock the finisher mechanic");
			helper.assertTrue(stand.getFinisherMeter() == 1.0F,
					"Unlocked finisher meter reads " + stand.getFinisherMeter() + " instead of 1");
			helper.assertTrue(stand.willHeavyPunchBeFinisher() && stand.willHeavyFinisherVariationFire(),
					"A full unlocked meter does not make the heavy punch the finisher");
			stand.setFinisherMeter(0.4F);
			helper.assertTrue(!stand.willHeavyFinisherVariationFire(), "The finisher tint shows below a 0.5 meter");
			stand.setFinisherMeter(1.0F);

			// Resolve 0 with the finisher variation unlocked (creative): still unlocked, as in 1.16
			power.setResolveLevel(0);
			helper.assertTrue(stand.getFinisherMeter() == 0, "Dropping back to Resolve 0 left the meter readable");
			power.getCurTypeData()._lockedAbilities.remove("finisher_uppercut"); // a level change may relock skills
			user.getAbilities().instabuild = true;
			helper.assertTrue(finisher.isAbilityUnlocked(power), "Fixture: creative did not unlock the finisher uppercut");
			helper.assertTrue(StandEntity.isFinisherMechanicUnlocked(power),
					"An unlocked finisher variation did not unlock the finisher mechanic");
			helper.assertTrue(stand.getFinisherMeter() == 1.0F,
					"Finisher meter with an unlocked variation reads " + stand.getFinisherMeter());
			helper.succeed();
		}
		finally {
			user.getAbilities().instabuild = false;
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
