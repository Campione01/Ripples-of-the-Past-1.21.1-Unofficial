package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.init.ModStatusEffects;
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
 * The client sound cues the server can read: 1.16 replayed a punch's cry with every new punch task, chained ones
 * too; played the heavy swing whoosh with 3 windup ticks left at pitch 0.65 + random * 0.2; and looped the barrage
 * cry clip after clip only for a user with Resolve.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAttackSoundCueGameTests {
	private StandAttackSoundCueGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void chainedPunchCriesAgain(GameTestHelper helper) {
		withStand(helper, "chain", (user, power, stand) -> {
			EntityActionInstance first = start(helper, user, power, stand, "punch");
			helper.assertTrue(first instanceof StandEntityPunch && !flag(first, "playedStandCrySound"),
					"The first punch of a chain would not cry");
			EntityActionInstance chained = start(helper, user, power, stand, "punch2");
			helper.assertTrue(chained instanceof StandEntityPunch && LivingComponentAction.getComponent(stand).getAction() == chained,
					"Fixture: the chained punch did not replace the first");
			helper.assertTrue(!flag(chained, "playedStandCrySound"),
					"A chained punch was marked as having cried already; 1.16 cried on every punch of a combo");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void heavySwingSoundsWithThreeWindupTicksLeftAtALowPitch(GameTestHelper helper) {
		withStand(helper, "heavy_swing", (user, power, stand) -> {
			EntityActionInstance heavy = start(helper, user, power, stand, "heavy_punch");
			helper.assertTrue(heavy instanceof StandEntityHeavyPunch && heavy.getPhase() == ActionPhase.WINDUP,
					"Fixture: no heavy punch windup " + heavy);
			int windup = (int) heavy.phasesLength.getFloat(ActionPhase.WINDUP);
			helper.assertTrue(windup > 4, "Fixture: windup too short " + windup);
			setPhaseTick(heavy, windup - 4);
			helper.assertTrue(!(Boolean) call(heavy, "heavySwingSoundDue"), "The heavy swing played with 4 windup ticks left");
			setPhaseTick(heavy, windup - 3);
			helper.assertTrue((Boolean) call(heavy, "heavySwingSoundDue"), "The heavy swing did not play with 3 windup ticks left");
			float min = Float.MAX_VALUE;
			float max = -Float.MAX_VALUE;
			for (int i = 0; i < 64; i++) {
				float pitch = (Float) call(heavy, "getHeavySwingPitch", stand);
				helper.assertTrue(pitch >= 0.65F - 1.0E-4F && pitch < 0.85F + 1.0E-4F,
						"1.16: the heavy swing pitch is 0.65 + random * 0.2, got " + pitch);
				min = Math.min(min, pitch);
				max = Math.max(max, pitch);
			}
			helper.assertTrue(max - min > 0.05F, "The heavy swing pitch does not vary: " + min + " to " + max);
		});
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void barrageCryLoopsOnlyWithResolve(GameTestHelper helper) {
		withStand(helper, "barrage_cry", (user, power, stand) -> {
			EntityActionInstance barrage = start(helper, user, power, stand, "barrage");
			helper.assertTrue(barrage instanceof StandEntityBarrage, "Fixture: no barrage " + barrage);
			helper.assertTrue(!(Boolean) call(barrage, "usesEndlessBarrageCry", stand),
					"Without Resolve the barrage cry loops clip after clip; 1.16 played one clip");
			user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 100));
			helper.assertTrue((Boolean) call(barrage, "usesEndlessBarrageCry", stand),
					"With Resolve the barrage cry does not loop as 1.16's endless cry did");
		});
	}

	private interface Check {
		void run(Player user, StandPower power, StandEntity stand);
	}

	private static void withStand(GameTestHelper helper, String name, Check check) {
		String profileName = "SoundCue_" + name;
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.US_ASCII)), profileName));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		try {
			Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing Star Platinum entity");
			stand.moveTo(origin.x, origin.y, origin.z);
			check.run(user, power, stand);
			helper.succeed();
		}
		finally {
			user.removeEffect(ModStatusEffects.RESOLVE);
			if (power != null && power.isSummoned()) type.forceUnsummon(user, power);
			user.discard();
		}
	}

	private static EntityActionInstance start(GameTestHelper helper, Player user, StandPower power, StandEntity stand, String name) {
		Ability ability = power.getAbility(name);
		helper.assertTrue(ability instanceof EntityActionType, "Missing action " + name);
		power.setStamina(power.getMaxStamina());
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
		return action;
	}

	private static boolean flag(EntityActionInstance action, String name) {
		try {
			Field field = StandEntityPunch.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.getBoolean(action);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Missing punch field " + name, e);
		}
	}

	private static void setPhaseTick(EntityActionInstance action, int tick) {
		try {
			Field field = EntityActionInstance.class.getDeclaredField("curPhaseTick");
			field.setAccessible(true);
			field.setInt(action, tick);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Action phase tick is missing", e);
		}
	}

	private static Object call(EntityActionInstance action, String name, Object... args) {
		Class<?> owner = action instanceof StandEntityBarrage ? StandEntityBarrage.class : StandEntityHeavyPunch.class;
		Class<?>[] types = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) types[i] = StandEntity.class;
		try {
			Method method = owner.getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method.invoke(action, args);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Missing sound cue hook " + owner.getSimpleName() + "." + name, e);
		}
	}
}
