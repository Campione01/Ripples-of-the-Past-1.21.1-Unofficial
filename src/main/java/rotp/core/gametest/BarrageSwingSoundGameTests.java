package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityMeleeBarrage.clTtickSwingSound: every barrage swing whoosh plays at volume 0.25 with pitch
 * 1.8 - attackDamage * 0.05 + random * 0.2. SilverChariotMeleeBarrage keeps volume 0.25 with pitch 0.9 + random * 0.2.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BarrageSwingSoundGameTests {
	private static final float EPSILON = 1.0E-4F;
	private static final int SAMPLES = 64;

	private BarrageSwingSoundGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void genericBarrageSwingIsQuietAndPitchedByAttackDamage(GameTestHelper helper) {
		withBarrage(helper, "star_platinum", "barrage", (stand, barrage) -> {
			AttributeInstance attackDamage = stand.getAttribute(Attributes.ATTACK_DAMAGE);
			helper.assertTrue(attackDamage != null, "Fixture: the Stand has no attack damage attribute");

			attackDamage.setBaseValue(8);
			float weak = (float) stand.getAttackDamage();
			float[] weakPitch = assertSwing(helper, stand, barrage, "attack damage " + weak,
					1.8F - weak * 0.05F, 2.0F - weak * 0.05F);

			attackDamage.setBaseValue(16);
			float strong = (float) stand.getAttackDamage();
			helper.assertTrue(strong > weak + 1, "Fixture: the attack damage did not rise (" + weak + " -> " + strong + ")");
			float[] strongPitch = assertSwing(helper, stand, barrage, "attack damage " + strong,
					1.8F - strong * 0.05F, 2.0F - strong * 0.05F);

			helper.assertTrue(strongPitch[1] < weakPitch[0],
					"1.16: a stronger Stand swings at a lower pitch, got up to " + strongPitch[1] + " at attack damage "
							+ strong + " against " + weakPitch[0] + " or more at " + weak);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void silverChariotBarrageSwingKeepsItsOwnPitch(GameTestHelper helper) {
		withBarrage(helper, "silver_chariot", "melee_barrage", (stand, barrage) -> {
			AttributeInstance attackDamage = stand.getAttribute(Attributes.ATTACK_DAMAGE);
			helper.assertTrue(attackDamage != null, "Fixture: the Stand has no attack damage attribute");
			for (double damage : new double[] { 8, 16 }) {
				attackDamage.setBaseValue(damage);
				assertSwing(helper, stand, barrage, "Silver Chariot at attack damage " + (float) stand.getAttackDamage(),
						0.9F, 1.1F);
			}
		});
	}

	/** Returns the lowest and the highest pitch of the samples. */
	private static float[] assertSwing(GameTestHelper helper, StandEntity stand, StandEntityBarrage barrage,
			String what, float pitchFrom, float pitchTo) {
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		for (int i = 0; i < SAMPLES; i++) {
			float volume = hook(barrage, "getBarrageSwingVolume", stand);
			helper.assertTrue(Math.abs(volume - 0.25F) < EPSILON,
					"1.16: a barrage swing plays at volume 0.25, got " + volume + " (" + what + ")");
			float pitch = hook(barrage, "getBarrageSwingPitch", stand);
			helper.assertTrue(pitch >= pitchFrom - EPSILON && pitch < pitchTo + EPSILON,
					"1.16: the barrage swing pitch must be in [" + pitchFrom + ", " + pitchTo + "), got " + pitch
							+ " (" + what + ")");
			min = Math.min(min, pitch);
			max = Math.max(max, pitch);
		}
		helper.assertTrue(max - min > 0.05F,
				"1.16: the barrage swing pitch varies by up to 0.2 from swing to swing, got " + min + " to " + max
						+ " over " + SAMPLES + " swings (" + what + ")");
		return new float[] { min, max };
	}

	private interface BarrageCheck {
		void run(StandEntity stand, StandEntityBarrage barrage);
	}

	private static void withBarrage(GameTestHelper helper, String standId, String abilityName, BarrageCheck check) {
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player user = fakePlayer(level, "BarrageSwing_" + standId);
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		StandPower power = null;
		try {
			helper.assertTrue(standType != null, "Missing Stand type " + standId);
			user.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(level.addFreshEntity(user), "Could not add " + user.getScoreboardName());
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing Stand entity of " + standId);

			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof StandEntityBarrageAbility,
					standId + " " + abilityName + " is not a Stand barrage: " + ability);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(action instanceof StandEntityBarrage, "Barrage action type: " + action);

			check.run(stand, (StandEntityBarrage) action);
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	private static Player fakePlayer(ServerLevel level, String name) {
		return FakePlayerFactory.get(level, new GameProfile(
				UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
	}

	private static float hook(StandEntityBarrage barrage, String name, StandEntity stand) {
		try {
			Method method = StandEntityBarrage.class.getDeclaredMethod(name, StandEntity.class);
			method.setAccessible(true);
			// virtual call: Silver Chariot runs its own override
			return (Float) method.invoke(barrage, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not read the barrage hook " + name, e);
		}
	}
}
