package rotp.core.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.updatePosition asked the Stand's task for its offset on every tick (StandEntity.getOffsetFromUser),
 * before the task's own tick, and the light attack, the heavy attack (the heavy punches, the finishers, Silver Chariot's
 * sweep) and the barrage gave StandEntityAction.front3dOffset of the task's target: the horizontal distance from the user
 * to the target (ActionTarget.getTargetPos(true), an entity's eyes) less 1 + half its width, clamped between min(0.5, the
 * effective range) and min(2, the reach); with no target, rayTraceMultipleEntities from the user's eyes along the look
 * over that maximum with the boxes grown by 0.25, else the maximum. So the Stand stepped after a target that moved, in
 * every phase, in the tick the target had moved before.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandFrontOffsetEveryTick116GameTests {
	private StandFrontOffsetEveryTick116GameTests() {}

	private static final double EPS = 1.0E-4D;
	private static final double AWAY = 8.0D;

	/*
	 * Star Platinum's punch and heavy punch and Silver Chariot's sweep pressed at a cow ahead that is moved before a tick
	 * of each phase (the heavy punch's strike keeps it), and the heavy punch pressed at nothing with a cow brought onto its
	 * user's look ray at feet height, which only the grown boxes meet, in its windup and recovery and taken away between:
	 * in every tick of each, the Stand's position update (StandEntity#updatePosition, then the action's tick as the Stand's
	 * EntityTickEvent.Post runs it) puts the Stand at 1.16's front offset of where the cow is in that tick.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandTaskKeepsItsFrontOffsetToItsTargetOrItsUsersLookOnEveryTickAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (Row row : Row.values()) {
			run(helper, row, wrong);
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 put the Stand of a light or heavy attack at front3dOffset of its task's"
				+ " target, or of its user's look ray, on every tick: " + String.join("; ", wrong));
		helper.succeed();
	}

	private enum Row {
		PUNCH("star_platinum", "punch", true),
		HEAVY_PUNCH("star_platinum", "heavy_punch", true),
		SWEEP("silver_chariot", "sweeping_attack", true),
		HEAVY_PUNCH_AT_NOTHING("star_platinum", "heavy_punch", false);

		final String stand;
		final String ability;
		final boolean pressedAtCow;

		Row(String stand, String ability, boolean pressedAtCow) {
			this.stand = stand;
			this.ability = ability;
			this.pressedAtCow = pressedAtCow;
		}

		String describe() {
			return name().toLowerCase(Locale.ROOT).replace('_', ' ');
		}
	}

	private static void run(GameTestHelper helper, Row row, List<String> wrong) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(row.stand));
		StandPower power = null;
		Cow cow = null;
		try {
			Vec3 pos = helper.absoluteVec(new Vec3(2.5D + row.ordinal() * 4, 12.0D, 2.5D));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			StandTaskTargetOffsetGameTests.turn(user, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user of " + row.describe());
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + row.stand);
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "Could not summon " + row.stand);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.isFollowingUser() && !stand.isArmsOnlyMode(),
					"Fixture: the summoned " + row.stand + " does not follow its user whole");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;

			cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not make a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
			cow.setHealth(1000);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			place(cow, user, row.pressedAtCow ? 2.0D : 0.0D, row.pressedAtCow ? 0.0D : AWAY);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add the cow");
			ActionTarget aim = row.pressedAtCow ? new ActionTarget(cow) : ActionTarget.EMPTY;
			LivingComponentAction.getComponent(user).entityAim.setTarget(aim);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(aim);

			Ability ability = power.getAbility(row.ability);
			helper.assertTrue(ability instanceof EntityActionAbility, "Missing " + row.ability + " of " + row.stand);
			EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction actions = LivingComponentAction.getComponent(stand);
			actions.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == action, "Fixture: the " + row.describe() + " did not start");

			Set<ActionPhase> moved = EnumSet.noneOf(ActionPhase.class);
			Set<ActionPhase> checked = EnumSet.noneOf(ActionPhase.class);
			List<String> mismatches = new ArrayList<>();
			check(row, stand, user, cow, action, false, "as it was set", mismatches);
			for (int tick = 1; tick <= 80; tick++) {
				ActionPhase phase = action.getPhase();
				int phaseTick = (int) action.getPhaseTick();
				if (move(row, user, cow, phase, phaseTick)) {
					moved.add(phase);
				}
				stand.updatePosition(user);
				checked.add(phase);
				check(row, stand, user, cow, action, true, "in tick " + tick + " (" + phase + " " + phaseTick + ")", mismatches);
				actions.tick();
				if (action.isOver() || stand.getCurStandAction() != action) {
					break;
				}
			}
			EnumSet<ActionPhase> phases = EnumSet.of(ActionPhase.WINDUP, ActionPhase.PERFORM, ActionPhase.RECOVERY);
			if (!checked.containsAll(phases) || !moved.containsAll(row.pressedAtCow ? phases
					: EnumSet.of(ActionPhase.WINDUP, ActionPhase.RECOVERY))) {
				mismatches.add("the fixture checked the phases " + checked + " and moved the cow in " + moved);
			}
			if (!mismatches.isEmpty()) {
				wrong.add(row.describe() + ": " + String.join(", ", mismatches));
			}
		}
		finally {
			if (power != null && type != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			if (cow != null) {
				cow.discard();
			}
			user.discard();
		}
	}

	// the user looks south (+z): ahead is along z, aside along x
	private static void place(Cow cow, Player user, double ahead, double aside) {
		cow.moveTo(user.getX() + aside, user.getY(), user.getZ() + ahead, 0.0F, 0.0F);
	}

	// before a tick of each phase, the cow steps back or forth; at nothing, it comes onto the look ray and goes again
	private static boolean move(Row row, Player user, Cow cow, ActionPhase phase, int phaseTick) {
		if (row.pressedAtCow) {
			double ahead = switch (phase) {
				case WINDUP -> phaseTick == 0 ? 2.3D : Double.NaN;
				case PERFORM -> phaseTick == 0 ? 1.9D : Double.NaN;
				case RECOVERY -> phaseTick == 1 ? 2.6D : Double.NaN;
				default -> Double.NaN;
			};
			if (Double.isNaN(ahead)) {
				return false;
			}
			place(cow, user, ahead, 0.0D);
			return true;
		}
		if (phase == ActionPhase.WINDUP && phaseTick == 1) {
			place(cow, user, 2.2D, 0.0D);
			return true;
		}
		if (phase == ActionPhase.WINDUP && phaseTick == 5) {
			place(cow, user, 0.0D, AWAY);
			return true;
		}
		if (phase == ActionPhase.RECOVERY && phaseTick == 1) {
			place(cow, user, 2.4D, 0.0D);
			return true;
		}
		return false;
	}

	private static void check(Row row, StandEntity stand, Player user, Cow cow, EntityActionInstance action, boolean placed,
			String when, List<String> mismatches) {
		double min = Math.min(0.5D, stand.getEffectiveRange());
		double max = Math.min(2.0D, stand.getMaxRange());
		double expected = row.pressedAtCow || onLookRay(user, cow, max) ? offsetTo(user, cow, min, max) : max;
		Vec3 synced = action.getSyncedStandOffset();
		if (synced == null || Math.abs(synced.z - expected) > EPS || Math.abs(synced.x) > EPS) {
			mismatches.add(when + " the front offset " + (synced != null ? synced.z : null) + ", 1.16's " + expected
					+ " (the cow " + (user.position().distanceTo(cow.position())) + " away)");
		}
		// the user looks along +z: the Stand stands that far ahead of its feet
		if (placed && (Math.abs(stand.getZ() - user.getZ() - expected) > EPS || Math.abs(stand.getX() - user.getX()) > EPS)) {
			mismatches.add(when + " the Stand " + (stand.getZ() - user.getZ()) + " ahead and " + (stand.getX() - user.getX())
					+ " aside, 1.16's " + expected + " ahead");
		}
	}

	// 1.16 offsetToTarget: from the user's feet to the target's eyes, horizontally, less 1 + half its width
	private static double offsetTo(Player user, Cow cow, double min, double max) {
		Vec3 toCow = cow.getEyePosition().subtract(user.position());
		double distance = Math.sqrt(toCow.x * toCow.x + toCow.z * toCow.z);
		return Mth.clamp(distance - (1.0D + cow.getBbWidth() / 2.0D), min, max);
	}

	// 1.16 front3dOffset's rayTraceMultipleEntities: the user's eyes along its look over the maximum, boxes grown by 0.25
	private static boolean onLookRay(Player user, Cow cow, double max) {
		Vec3 eyes = user.getEyePosition();
		Vec3 end = eyes.add(user.getLookAngle().scale(max));
		var box = cow.getBoundingBox().inflate(cow.getPickRadius() + 0.25D);
		return box.contains(eyes) || box.clip(eyes, end).isPresent();
	}
}
