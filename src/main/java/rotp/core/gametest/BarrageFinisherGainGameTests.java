package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
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
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityMeleeBarrage.BarrageEntityPunch.afterAttack: a barrage tick adds 0.005 finisher meter
 * per punch left in the damage source after the target Stand's clash parry (5 punches: 0.025;
 * all parried: still 0.005).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BarrageFinisherGainGameTests {
	private static final float EPSILON = 1.0E-4F;

	private BarrageFinisherGainGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void starPlatinumBarrageFinisherFollowsParriedHits(GameTestHelper helper) {
		verify(helper, "star_platinum", "barrage");
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void silverChariotBarrageFinisherFollowsParriedHits(GameTestHelper helper) {
		verify(helper, "silver_chariot", "melee_barrage");
	}

	private static void verify(GameTestHelper helper, String standId, String abilityName) {
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player attacker = fakePlayer(level, "BarrageFinA_" + standId);
		Player defender = fakePlayer(level, "BarrageFinD_" + standId);
		StandType attackerType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		StandType defenderType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower attackerPower = null;
		StandPower defenderPower = null;
		try {
			helper.assertTrue(attackerType != null && defenderType != null, "Missing Stand type " + standId);
			attackerPower = addStandUser(helper, attacker, attackerType, origin);
			defenderPower = addStandUser(helper, defender, defenderType, origin.add(0, 0, 3));
			attackerPower.setResolveLevel(1);
			helper.assertTrue(StandEntity.isFinisherMechanicUnlocked(attackerPower),
					"Fixture: the attacker's finisher meter is locked");
			StandEntity stand = attackerPower.getSummonedStandEntity();
			StandEntity target = defenderPower.getSummonedStandEntity();

			Ability ability = attackerPower.getAbility(abilityName);
			helper.assertTrue(ability instanceof StandEntityBarrageAbility,
					standId + " " + abilityName + " is not a Stand barrage: " + ability);
			attackerPower.setStamina(attackerPower.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability)
					.initActionOnAbilityUse(level, attacker, stand, null);
			// sets the performer and power user the damage source reads
			LivingComponentAction.getComponent(stand).setAction(action, attacker, SyncType.NO_SYNC);
			helper.assertTrue(action instanceof StandEntityBarrage, "Barrage action type: " + action);
			StandEntityBarrage barrage = (StandEntityBarrage) action;
			// the target Stand is 2 blocks ahead, facing the attacker (parry angle)
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			target.moveTo(origin.x, origin.y, origin.z + 2, 180, 0);
			target.setYHeadRot(180);
			target.yHeadRotO = 180;
			helper.assertTrue(StandStatFormulas.getMaxBarrageParryTickDamage(target.getDurability()) > 0,
					"Fixture: the target Stand cannot parry");
			barrage.hitsThisTick = 5;

			// every punch parried: the tick still adds the base 0.005
			target.resetBarrageParry();
			target.setBarrageHitsThisTick(999);
			stand.setFinisherMeter(0);
			hit(barrage, target, level, stand);
			helper.assertTrue(target.barrageClashOpponent().filter(e -> e == stand).isPresent(),
					"Fixture: the target Stand did not parry the barrage");
			assertGain(helper, stand, 0.005F, "a fully parried 5-punch tick");

			// no parry: 0.005 per punch
			target.resetBarrageParry();
			stand.setFinisherMeter(0);
			hit(barrage, target, level, stand);
			assertGain(helper, stand, 0.025F, "an unparried 5-punch tick");

			// 3 of 5 punches parried: 0.005 for each of the 2 left
			DamageSource dmgSource = barrage.makePunchDamageSource();
			((DamageSourceModified) dmgSource).jojo_ripples$setBarrageHitsCount(2);
			stand.setFinisherMeter(0);
			barrage.addBarrageFinisher(stand, dmgSource);
			assertGain(helper, stand, 0.010F, "a tick with 2 of 5 punches left after the parry");
			helper.succeed();
		}
		finally {
			if (attackerPower != null && attackerPower.isSummoned() && attackerType != null) {
				attackerType.forceUnsummon(attacker, attackerPower);
			}
			if (defenderPower != null && defenderPower.isSummoned() && defenderType != null) {
				defenderType.forceUnsummon(defender, defenderPower);
			}
			attacker.discard();
			defender.discard();
		}
	}

	private static Player fakePlayer(ServerLevel level, String name) {
		return FakePlayerFactory.get(level, new GameProfile(
				UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.US_ASCII)), name));
	}

	private static StandPower addStandUser(GameTestHelper helper, Player user, StandType standType, Vec3 pos) {
		user.moveTo(pos.x, pos.y, pos.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add " + user.getScoreboardName());
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant a Stand to " + user.getScoreboardName());
		helper.assertTrue(standType.summon(user, power), "Could not summon the Stand of " + user.getScoreboardName());
		helper.assertTrue(power.getSummonedStandEntity() != null, "Missing Stand entity of " + user.getScoreboardName());
		return power;
	}

	private static void hit(StandEntityBarrage barrage, StandEntity target, Level level, StandEntity stand) {
		try {
			Method method = StandEntityBarrage.class.getDeclaredMethod(
					"hitEntity", ActionTarget.class, Level.class, StandEntity.class);
			method.setAccessible(true);
			// virtual call: Silver Chariot runs its own override
			method.invoke(barrage, new ActionTarget(target), level, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the barrage hitEntity", e);
		}
	}

	private static void assertGain(GameTestHelper helper, StandEntity stand, float expected, String what) {
		float gain = stand.getFinisherMeter();
		helper.assertTrue(Math.abs(gain - expected) < EPSILON,
				"1.16: " + what + " adds " + expected + " finisher meter, but the barrage added " + gain);
	}
}
