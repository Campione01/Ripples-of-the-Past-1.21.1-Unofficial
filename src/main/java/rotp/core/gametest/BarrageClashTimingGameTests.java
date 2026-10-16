package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
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
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.setBarrageClashOpponent (928-946), reached from a parry (barrageClashParryPunches 874-918): the Stand
 * that parries takes the barraging Stand as its clash opponent at once, and the barraging Stand points back only through
 * the parrying Stand's previous opponent, so from its second parry, or once the barraging Stand parries in turn.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BarrageClashTimingGameTests {
	private BarrageClashTimingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40, batch = "barrage_clash_timing_second_parry")
	public static void theParriedStandTakesTheClashOnlyFromTheSecondParryAsIn116(GameTestHelper helper) {
		verify(helper, false);
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = "barrage_clash_timing_parry_in_turn")
	public static void theParriedStandTakesTheClashOnceItParriesInTurnAsIn116(GameTestHelper helper) {
		verify(helper, true);
	}

	private static void verify(GameTestHelper helper, boolean parryInTurn) {
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player attacker = fakePlayer(level, "ClashTimingA_" + parryInTurn);
		Player defender = fakePlayer(level, "ClashTimingD_" + parryInTurn);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower attackerPower = null;
		StandPower defenderPower = null;
		StandEntity stand = null;
		StandEntity target = null;
		try {
			helper.assertTrue(type != null, "Fixture: Star Platinum is missing");
			attackerPower = addStandUser(helper, attacker, type, origin);
			defenderPower = addStandUser(helper, defender, type, origin.add(0, 0, 3));
			stand = attackerPower.getSummonedStandEntity();
			target = defenderPower.getSummonedStandEntity();
			StandEntityBarrage barrage = startBarrage(helper, attackerPower, attacker, stand);
			StandEntityBarrage counter = parryInTurn ? startBarrage(helper, defenderPower, defender, target) : null;
			// face to face, 2 blocks apart, each within the other's parry angle
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			target.moveTo(origin.x, origin.y, origin.z + 2, 180, 0);
			target.setYHeadRot(180);
			target.yHeadRotO = 180;
			helper.assertTrue(stand.barrageClashOpponent().isEmpty() && target.barrageClashOpponent().isEmpty(),
					"Fixture: the Stands are in a barrage clash before any parry");

			parriedHit(barrage, target, level, stand);
			helper.assertTrue(target.barrageClashOpponent().orElse(null) == stand,
					"Fixture: the Stand that parried the barrage did not take the barraging Stand as its clash opponent: "
							+ target.barrageClashOpponent());
			helper.assertTrue(stand.barrageClashOpponent().isEmpty(), "The barraging Stand took "
					+ stand.barrageClashOpponent() + " as its clash opponent at the first parry of its barrage; 1.16 pointed it"
					+ " back only from the second parry, or once it parried in turn");

			if (parryInTurn) {
				parriedHit(counter, stand, level, target);
			}
			else {
				parriedHit(barrage, target, level, stand);
			}
			helper.assertTrue(stand.barrageClashOpponent().orElse(null) == target
					&& target.barrageClashOpponent().orElse(null) == stand, "The two Stands are not in a mutual barrage"
							+ " clash " + (parryInTurn ? "once the barraging Stand parried in turn" : "after the second parry")
							+ ": " + stand.barrageClashOpponent() + " and " + target.barrageClashOpponent());
			helper.succeed();
		}
		finally {
			if (stand != null) {
				LivingComponentAction.getComponent(stand).setAction(null, attacker, SyncType.NO_SYNC);
				stand.barrageClashStopped();
			}
			if (target != null) {
				LivingComponentAction.getComponent(target).setAction(null, defender, SyncType.NO_SYNC);
				target.barrageClashStopped();
			}
			if (attackerPower != null && attackerPower.isSummoned()) {
				type.forceUnsummon(attacker, attackerPower);
			}
			if (defenderPower != null && defenderPower.isSummoned()) {
				type.forceUnsummon(defender, defenderPower);
			}
			attacker.discard();
			defender.discard();
		}
	}

	private static StandEntityBarrage startBarrage(GameTestHelper helper, StandPower power, Player user, StandEntity stand) {
		Ability ability = power.getAbility("barrage");
		helper.assertTrue(ability instanceof EntityActionType, "Fixture: Star Platinum has no barrage");
		power.setStamina(power.getMaxStamina());
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		// sets the performer and power user the damage source reads
		LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
		helper.assertTrue(action instanceof StandEntityBarrage, "Fixture: the barrage action is " + action);
		StandEntityBarrage barrage = (StandEntityBarrage) action;
		barrage.hitsThisTick = 5;
		return barrage;
	}

	// one barrage tick's hit on a Stand that has punches to parry it with (1.16 BarrageHandler.parryCount)
	private static void parriedHit(StandEntityBarrage barrage, StandEntity parrying, Level level, StandEntity stand) {
		parrying.resetBarrageParry();
		parrying.setBarrageHitsThisTick(999);
		try {
			Method method = StandEntityBarrage.class.getDeclaredMethod(
					"hitEntity", ActionTarget.class, Level.class, StandEntity.class);
			method.setAccessible(true);
			method.invoke(barrage, new ActionTarget(parrying), level, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the barrage hitEntity", e);
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
}
