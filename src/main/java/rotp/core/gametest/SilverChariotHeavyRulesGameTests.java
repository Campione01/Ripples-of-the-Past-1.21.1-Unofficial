package rotp.core.gametest;

import java.lang.reflect.Method;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.silverchariot.SilverChariotDashAttackAbility.DashStrike;
import rotp.core.mechanics.KnockbackCollisionImpact;
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
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16: Silver Chariot's dash and sweep extend StandEntityHeavyAttack. Setting either snapshots the finisher
 * meter (recovery follows the snapshot) and spends 0.51 of it; a hurting hit (HeavyPunchInstance.afterAttack)
 * sends a hit Stand's barrage into recovery and arms the wall impact on the knocked-back entity.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotHeavyRulesGameTests {
	private SilverChariotHeavyRulesGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void dashAndSweepSpendFinisherAndArmWallImpact(GameTestHelper helper) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		StandType scType = stand(helper, "silver_chariot");
		Player user = addPlayer(helper, pos);
		StandPower power = PowerClass.STAND.attachGet(user);
		Pig sweepPig = null;
		Pig dashPig = null;
		try {
			StandEntity chariot = summonChariot(helper, scType, user, power);
			LivingComponentAction actions = LivingComponentAction.getComponent(chariot);

			EntityActionInstance sweep = startAction(helper, power, chariot, actions, "sweeping_attack", 1.0F);
			assertFinisherSpent(helper, chariot, sweep, 1.0F, "sweeping_attack");
			sweepPig = addPig(helper, pos.add(0, 0, 3));
			faceForward(chariot, pos.add(0, 0, 1.5));
			sweep.actionPerformStart();
			helper.assertTrue(sweepPig.getHealth() < sweepPig.getMaxHealth(), "The sweep did not hurt the pig in front");
			helper.assertTrue(KnockbackCollisionImpact.getHandler(sweepPig).isActive(),
					"1.16: a hurting sweep arms the wall impact on the target");

			actions.setAction(null, SyncType.NO_SYNC);
			EntityActionInstance dash = startAction(helper, power, chariot, actions, "dash_attack", 0.3F);
			helper.assertTrue(dash instanceof DashStrike, "dash_attack is not a DashStrike");
			assertFinisherSpent(helper, chariot, dash, 0.3F, "dash_attack");
			dashPig = addPig(helper, pos.add(3, 0, 1.5));
			dashHit(chariot, dash, dashPig);
			helper.assertTrue(dashPig.getHealth() < dashPig.getMaxHealth(), "The dash did not hurt the pig");
			helper.assertTrue(KnockbackCollisionImpact.getHandler(dashPig).isActive(),
					"1.16: a hurting dash arms the wall impact on the target");
			actions.setAction(null, SyncType.NO_SYNC);
		}
		finally {
			unsummon(scType, user, power);
			user.discard();
			if (sweepPig != null) sweepPig.discard();
			if (dashPig != null) dashPig.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void sweepHitSendsStandBarrageToRecovery(GameTestHelper helper) {
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		StandType scType = stand(helper, "silver_chariot");
		StandType spType = stand(helper, "star_platinum");
		Player scUser = addPlayer(helper, pos);
		Player spUser = addPlayer(helper, pos.add(10, 0, 0));
		StandPower scPower = PowerClass.STAND.attachGet(scUser);
		StandPower spPower = PowerClass.STAND.attachGet(spUser);
		try {
			StandEntity chariot = summonChariot(helper, scType, scUser, scPower);
			helper.assertTrue(StandPowerTransitions.insert(spPower, new StandInstance(spType)).status()
					== StandPowerTransitions.Status.APPLIED && spType.summon(spUser, spPower),
					"Could not summon Star Platinum");
			StandEntity sp = spPower.getSummonedStandEntity();
			helper.assertTrue(sp != null, "Summoned Star Platinum is missing");
			LivingComponentAction spActions = LivingComponentAction.getComponent(sp);
			Ability barrage = spPower.getAbility("barrage");
			helper.assertTrue(barrage instanceof EntityActionType, "Star Platinum barrage is not an entity action");
			EntityActionInstance rush = ((EntityActionType) barrage).initActionOnAbilityUse(helper.getLevel(), spUser, sp, null);
			spActions.setAction(rush, spUser, SyncType.NO_SYNC);
			for (int tick = 0; tick < 10 && rush.getPhase() != ActionPhase.PERFORM; tick++) {
				spActions.tick();
			}
			helper.assertTrue(spActions.getAction() == rush && rush.getPhase() == ActionPhase.PERFORM,
					"The barrage did not start");

			LivingComponentAction actions = LivingComponentAction.getComponent(chariot);
			EntityActionInstance sweep = startAction(helper, scPower, chariot, actions, "sweeping_attack", 0.9F);
			faceForward(chariot, pos.add(0, 0, 1.5));
			sp.moveTo(pos.x, pos.y, pos.z + 3, 180, 0);
			sweep.actionPerformStart();
			helper.assertTrue(rush.getPhase() == ActionPhase.RECOVERY,
					"1.16: a hurting sweep sends the hit Stand's barrage into recovery, phase is " + rush.getPhase());
			helper.assertTrue(KnockbackCollisionImpact.getHandler(spUser).isActive(),
					"1.16: a Stand hit by the sweep arms the wall impact on its user");
			actions.setAction(null, SyncType.NO_SYNC);
		}
		finally {
			unsummon(scType, scUser, scPower);
			unsummon(spType, spUser, spPower);
			scUser.discard();
			spUser.discard();
		}
		helper.succeed();
	}

	private static void assertFinisherSpent(GameTestHelper helper, StandEntity stand, EntityActionInstance action,
			float meter, String abilityName) {
		helper.assertTrue(stand.getLastHeavyFinisherValue() == meter, "1.16: " + abilityName
				+ " did not snapshot finisher meter " + meter + ", got " + stand.getLastHeavyFinisherValue());
		float left = Math.max(meter - 0.51F, 0);
		helper.assertTrue(Math.abs(stand.getFinisherMeter() - left) < 1.0E-4F, "1.16: " + abilityName
				+ " did not spend 0.51 finisher meter, it reads " + stand.getFinisherMeter());
		int recovery = StandStatFormulas.getHeavyAttackRecovery(stand.getAttackSpeed(), meter);
		helper.assertTrue(action.phasesLength.getFloat(ActionPhase.RECOVERY) == recovery, "1.16: " + abilityName
				+ " recovery " + action.phasesLength.getFloat(ActionPhase.RECOVERY)
				+ " does not follow the finisher snapshot (" + recovery + ")");
	}

	private static EntityActionInstance startAction(GameTestHelper helper, StandPower power, StandEntity stand,
			LivingComponentAction actions, String abilityName, float meter) {
		Ability ability = power.getAbility(abilityName);
		helper.assertTrue(ability instanceof EntityActionType, "Missing entity action " + abilityName);
		stand.setFinisherMeter(meter);
		helper.assertTrue(stand.getFinisherMeter() == meter, "Could not set the finisher meter to " + meter);
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(
				helper.getLevel(), power.getUser(), stand, null);
		actions.setAction(action, power.getUser(), SyncType.NO_SYNC);
		helper.assertTrue(actions.getAction() == action, abilityName + " was not set on the Stand");
		return action;
	}

	// the Stand stands at standPos looking +Z
	private static void faceForward(StandEntity stand, Vec3 standPos) {
		stand.moveTo(standPos.x, standPos.y, standPos.z, 0, 0);
		stand.setYHeadRot(0);
		stand.setYBodyRot(0);
	}

	// runs the dash's private per-target hit
	private static void dashHit(StandEntity stand, EntityActionInstance dash, LivingEntity target) {
		try {
			Method hit = DashStrike.class.getDeclaredMethod("hitDashTarget", Level.class, StandEntity.class, ActionTarget.class);
			hit.setAccessible(true);
			hit.invoke(dash, stand.level(), stand, new ActionTarget(target));
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the dash hit", e);
		}
	}

	private static Pig addPig(GameTestHelper helper, Vec3 pos) {
		Pig pig = EntityType.PIG.create(helper.getLevel());
		helper.assertTrue(pig != null, "Could not create a pig");
		pig.setNoAi(true);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		pig.setHealth(200);
		pig.moveTo(pos.x, pos.y, pos.z, 180, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Could not add a pig");
		return pig;
	}

	private static Player addPlayer(GameTestHelper helper, Vec3 pos) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add a player");
		return player;
	}

	private static StandType stand(GameTestHelper helper, String id) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
		helper.assertTrue(type != null, "Missing registered Stand " + id);
		return type;
	}

	private static StandEntity summonChariot(GameTestHelper helper, StandType type, Player user, StandPower power) {
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant Silver Chariot");
		power.setResolveLevel(power.getMaxResolveLevel()); // unlocks the finisher meter
		power.getCurTypeData()._setSkillUnlocked("sweeping_attack", true, false);
		helper.assertTrue(type.summon(user, power), "Could not summon Silver Chariot");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null && stand.getUser() == user, "The summoned Silver Chariot is missing its user");
		LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
		power.setStamina(power.getMaxStamina());
		return stand;
	}

	private static void unsummon(StandType type, LivingEntity user, StandPower power) {
		if (power.isSummoned()) {
			type.forceUnsummon(user, power);
		}
	}
}
