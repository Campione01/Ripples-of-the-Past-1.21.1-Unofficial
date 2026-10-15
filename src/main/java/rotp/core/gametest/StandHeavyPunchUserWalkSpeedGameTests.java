package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
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

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Owner rule 2026-10-15 (replaces the 1.16 StandEntityHeavyAttack.Builder default 0.5): heavy punches and kicks keep
 * their user's full walk speed in every phase, as the time-stop punch did in 1.16 (standUserWalkSpeed(1.0F)).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandHeavyPunchUserWalkSpeedGameTests {
	private static final String BATCH = "stand_heavy_punch_user_walk_speed";

	private StandHeavyPunchUserWalkSpeedGameTests() {}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumHeavyPunchKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "heavy_punch", "owner rule 2026-10-15");
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumUppercutKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "finisher_uppercut", "owner rule 2026-10-15");
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theWorldKickKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "the_world", "kick", "owner rule 2026-10-15");
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theWorldTimeStopPunchKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "the_world", "ts_punch", "1.16 the_world_ts_punch standUserWalkSpeed(1.0F) and owner rule 2026-10-15");
	}

	private static void run(GameTestHelper helper, String standId, String abilityName, String source) {
		float expected = 1.0F;
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		helper.assertTrue(type != null, "HEAVY-WALK premise: missing registered Stand " + standId);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "HEAVY-WALK premise: could not add the user");
		StandPower power = PowerClass.STAND.attachGet(user);
		LivingComponentAction component = null;
		try {
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power),
					"HEAVY-WALK premise: could not summon " + standId);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "HEAVY-WALK premise: the Stand has no user");
			Ability ability = power.getMoveset().getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "HEAVY-WALK premise: missing entity action " + abilityName);
			EntityActionInstance action = ((EntityActionType) ability)
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			helper.assertTrue(action instanceof StandEntityHeavyPunch,
					"HEAVY-WALK premise: " + abilityName + " is not a heavy punch: " + action);
			helper.assertTrue(action.userWalkSpeed == 1.0F, "HEAVY-WALK premise: an action that has not started slows the user");
			component = LivingComponentAction.getComponent(stand);
			component.setAction(action, user, SyncType.NO_SYNC);
			ActionPhase startPhase = action.getPhase();
			helper.assertTrue(component.getAction() == action && startPhase != null && startPhase != ActionPhase.RECOVERY,
					"HEAVY-WALK premise: " + abilityName + " did not start: phase=" + startPhase);
			float atStart = action.userWalkSpeed;
			float inStand = stand.getUserWalkSpeed(atStart);
			action.setPhaseStart(ActionPhase.WINDUP);
			float windup = action.userWalkSpeed;
			action.setPhaseStart(ActionPhase.PERFORM);
			float perform = action.userWalkSpeed;
			action.setPhaseStart(ActionPhase.RECOVERY);
			float recovery = action.userWalkSpeed;
			helper.assertTrue(atStart == expected && inStand == expected && windup == expected && perform == expected
							&& recovery == 1.0F,
					standId + " " + abilityName + " user walk speed (" + source + ": " + expected + " in every phase):"
							+ " start(" + startPhase + ")=" + atStart + " throughStand=" + inStand + " windup=" + windup
							+ " perform=" + perform + " recovery=" + recovery);
		}
		finally {
			try {
				if (component != null) component.setAction(null, SyncType.NO_SYNC);
			}
			finally {
				if (power.isSummoned()) type.forceUnsummon(user, power);
				user.discard();
			}
		}
		helper.succeed();
	}
}
