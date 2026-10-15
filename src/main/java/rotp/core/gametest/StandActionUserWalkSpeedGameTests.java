package rotp.core.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
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
 * Owner rule 2026-10-15 (replaces 1.16 StandEntityAction.getUserWalkSpeed): of all Stand actions only the Stand guard
 * slows its user, to half walk speed; every other Stand action keeps full walk speed in every phase. Hierophant Green's
 * grapple still holds its user still while it pulls (1.16 standUserWalkSpeed(0.0F), kept by the owner). Non-Stand
 * powers keep their own values. Each case walks the action through its phases and samples the user's walk factor.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandActionUserWalkSpeedGameTests {
	private static final String BATCH = "stand_action_user_walk_speed";
	private static final String OWNER_FULL = "owner rule 2026-10-15: full walk speed in every phase";
	private static final float EPS = 1.0E-6F;

	private StandActionUserWalkSpeedGameTests() {}

	@FunctionalInterface
	private interface Expected {
		float at(ActionPhase phase);
	}

	private static Expected everyPhase(float factor) {
		return phase -> phase == ActionPhase.RECOVERY ? 1.0F : factor;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumGuardHalvesTheUsersWalkSpeedWhileItGuards(GameTestHelper helper) {
		run(helper, "star_platinum", "guard", 3, everyPhase(0.5F),
				"owner rule 2026-10-15: the guard halves walk speed while it guards", ActionPhase.PERFORM);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumHeavyPunchKeepsFullWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "heavy_punch", 3, everyPhase(1.0F), OWNER_FULL,
				ActionPhase.WINDUP, ActionPhase.PERFORM);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumBarrageKeepsFullWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "barrage", 3, everyPhase(1.0F), OWNER_FULL, ActionPhase.PERFORM);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumStarFingerKeepsFullWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "star_finger", 0, everyPhase(1.0F), OWNER_FULL, ActionPhase.PERFORM);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void magiciansRedHeldRedBindKeepsFullWalkSpeed(GameTestHelper helper) {
		run(helper, "magicians_red", "red_bind", 0, everyPhase(1.0F), OWNER_FULL, ActionPhase.PERFORM);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondHealKeepsFullWalkSpeed(GameTestHelper helper) {
		run(helper, "crazy_diamond", "heal", 3, everyPhase(1.0F), OWNER_FULL, ActionPhase.PERFORM);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumPunchKeepsFullWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "punch", 0, everyPhase(1.0F), OWNER_FULL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void starPlatinumGrabKeepsFullWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "grab", 0, everyPhase(1.0F), OWNER_FULL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void chargedHeavyPunchKeepsFullWalkSpeed(GameTestHelper helper) {
		run(helper, "star_platinum", "heavy_charged", 0, everyPhase(1.0F), OWNER_FULL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantGrappleHoldsTheUserStillWhileItPulls(GameTestHelper helper) {
		run(helper, "hierophant_green", "grapple", 0, phase -> phase == ActionPhase.PERFORM ? 0.0F : 1.0F,
				"owner rule 2026-10-15, seventh card: the grapple holds its user still while it pulls (1.16 0.0)",
				ActionPhase.PERFORM);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void hamonOverdriveBeatKeepsItsHalfWalkSpeed(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		user.moveTo(pos.x, pos.y, pos.z, 0, 0);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "ACTION-WALK premise: could not add the Hamon user");
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			Ability ability = power.getAbility("hamon_beat");
			helper.assertTrue(ability instanceof EntityActionType, "ACTION-WALK premise: missing Hamon action hamon_beat");
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			List<String> samples = new ArrayList<>();
			Set<ActionPhase> seen = EnumSet.noneOf(ActionPhase.class);
			boolean matches = true;
			for (int tick = 0; tick < 20 && !action.isOver(); tick++) {
				ActionPhase phase = action.getPhase();
				if (phase != null) {
					seen.add(phase);
					samples.add(phase + "=" + action.userWalkSpeed);
					float expected = phase == ActionPhase.PERFORM ? 0.5F : 1.0F;
					matches &= Math.abs(action.userWalkSpeed - expected) < EPS;
				}
				user.tickCount++;
				component.tick();
			}
			helper.assertTrue(matches && seen.contains(ActionPhase.PERFORM),
					"hamon_beat user walk speed (owner rule 2026-10-15: non-Stand powers keep their values; Overdrive Beat"
							+ " 0.5 while it strikes): samples=" + samples);
		}
		finally {
			component.setAction(null, SyncType.NO_SYNC);
			user.discard();
		}
		helper.succeed();
	}

	/**
	 * Starts the registered action on a summoned Stand and walks it forward through every phase it has, ticking each
	 * phase ticksPerPhase times (0: no tick, for actions that fire projectiles), then into RECOVERY.
	 */
	private static void run(GameTestHelper helper, String standId, String abilityName, int ticksPerPhase,
			Expected expected, String source, ActionPhase... requiredPhases) {
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
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			power.setStamina(power.getMaxStamina());
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

			List<String> samples = new ArrayList<>();
			Set<ActionPhase> seen = EnumSet.noneOf(ActionPhase.class);
			boolean[] matches = { true };
			Consumer<ActionPhase> sampleAs = phase -> {
				float raw = action.userWalkSpeed;
				float throughStand = stand.getUserWalkSpeed(raw);
				float want = expected.at(phase);
				seen.add(phase);
				samples.add(phase + "=" + raw + "/" + throughStand);
				matches[0] &= Math.abs(raw - want) < EPS && Math.abs(throughStand - want) < EPS;
			};
			Runnable sample = () -> {
				if (action.getPhase() != null) sampleAs.accept(action.getPhase());
			};
			sample.run();
			for (ActionPhase phase : new ActionPhase[] { ActionPhase.BUTTON_CHARGE, ActionPhase.WINDUP, ActionPhase.PERFORM }) {
				if (action.isOver()) break;
				ActionPhase current = action.getPhase();
				if (current != phase) {
					if (current.ordinal() > phase.ordinal() || action.phasesLength.getFloat(phase) <= 0) continue;
					action.setPhaseStart(phase);
					sample.run();
				}
				for (int tick = 0; tick < ticksPerPhase && !action.isOver() && action.getPhase() == phase; tick++) {
					user.tickCount++;
					component.tick();
					sample.run();
				}
			}
			if (!seen.contains(ActionPhase.RECOVERY)) {
				// a zero-length RECOVERY ends the action at once; the factor it set is what the user is left with
				action.setPhaseStart(ActionPhase.RECOVERY);
				sampleAs.accept(ActionPhase.RECOVERY);
			}
			boolean phasesSeen = seen.contains(ActionPhase.RECOVERY);
			for (ActionPhase required : requiredPhases) {
				phasesSeen &= seen.contains(required);
			}
			helper.assertTrue(matches[0] && phasesSeen,
					standId + " " + abilityName + " user walk speed (" + source + "; 1.0 in RECOVERY)"
							+ ": samples phase=raw/throughStand " + samples + " required=" + List.of(requiredPhases));
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
