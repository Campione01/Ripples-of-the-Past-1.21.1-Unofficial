package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
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
 * 1.16 StandEntityAction.getUserWalkSpeed: while a Stand's action runs its user walks at the speed the registration
 * gives (StandEntityAction.Builder default 0.5) in every phase but RECOVERY, where it is 1.0. Light attacks give 1.0,
 * barrages 0.15, the block 0.3, the Hierophant grapple 0.0; a held action's heldWalkSpeed multiplies on top (Red Bind
 * 0.3). Actions that were no Stand entity action in 1.16 (Gold Experience's lifeshot) and the port's own actions keep
 * full speed.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandActionUserWalkSpeedGameTests {
	private static final String BATCH = "stand_action_user_walk_speed";
	private static final float EPS = 1.0E-6F;

	private StandActionUserWalkSpeedGameTests() {}

	@GameTest(template = "empty", batch = BATCH)
	public static void silverChariotDashAttackHalvesTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "silver_chariot", "dash_attack", 0.5F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void silverChariotSweepingAttackHalvesTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "silver_chariot", "sweeping_attack", 0.5F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantEmeraldSplashHalvesTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "hierophant_green", "emerald_splash", 0.5F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void magiciansRedCrossfireHurricaneChargeHalvesTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "magicians_red", "crossfire_hurricane", 0.5F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondBloodCutterHalvesTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "crazy_diamond", "blood_cutter", 0.5F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceHealHalvesTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "gold_experience", "heal", 0.5F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void magiciansRedRedBindSlowsTheUserToFifteenHundredths(GameTestHelper helper) {
		run(helper, "magicians_red", "red_bind", 0.5F * 0.3F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumBarrageSlowsTheUserToFifteenHundredths(GameTestHelper helper) {
		run(helper, "star_platinum", "barrage", 0.15F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumGuardSlowsTheUserToThreeTenths(GameTestHelper helper) {
		run(helper, "star_platinum", "guard", 0.3F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantGrappleHoldsTheUserStill(GameTestHelper helper) {
		run(helper, "hierophant_green", "grapple", 0.0F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumPunchKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "punch", 1.0F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeshotKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "gold_experience", "lifeshot", 1.0F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void chargedHeavyPunchKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "heavy_charged", 1.0F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void grabKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "grab", 1.0F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void grabThrowKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "grab_throw", 1.0F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void bearingShotKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "bearing_shot", 1.0F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantPuppetKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "hierophant_green", "puppet", 1.0F);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondUncraftKeepsTheUsersWalkSpeed(GameTestHelper helper) {
		run(helper, "crazy_diamond", "uncraft", 1.0F);
	}

	private static void run(GameTestHelper helper, String standId, String abilityName, float expected) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		helper.assertTrue(type != null, "ACTION-WALK premise: missing registered Stand " + standId);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "ACTION-WALK premise: could not add the user");
		StandPower power = PowerClass.STAND.attachGet(user);
		LivingComponentAction component = null;
		try {
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power),
					"ACTION-WALK premise: could not summon " + standId);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "ACTION-WALK premise: the Stand has no user");
			Ability ability = power.getMoveset().getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "ACTION-WALK premise: missing entity action " + abilityName);
			EntityActionInstance action = ((EntityActionType) ability)
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			helper.assertTrue(action.userWalkSpeed == 1.0F, "ACTION-WALK premise: an action that has not started slows the user");
			component = LivingComponentAction.getComponent(stand);
			component.setAction(action, user, SyncType.NO_SYNC);
			ActionPhase startPhase = action.getPhase();
			helper.assertTrue(component.getAction() == action && startPhase != null && startPhase != ActionPhase.RECOVERY,
					"ACTION-WALK premise: " + abilityName + " did not start: phase=" + startPhase);
			float atStart = action.userWalkSpeed;
			float inStand = stand.getUserWalkSpeed(atStart);
			action.setPhaseStart(ActionPhase.RECOVERY);
			float recovery = action.userWalkSpeed;
			helper.assertTrue(Math.abs(atStart - expected) < EPS && Math.abs(inStand - expected) < EPS && recovery == 1.0F,
					standId + " " + abilityName + " user walk speed (1.16: " + expected + " until RECOVERY, then 1.0):"
							+ " start(" + startPhase + ")=" + atStart + " throughStand=" + inStand + " recovery=" + recovery);
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
