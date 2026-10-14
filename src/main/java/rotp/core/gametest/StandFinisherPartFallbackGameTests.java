package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.Locale;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.magiciansred.MagiciansRedKickAbility.MagiciansRedKick;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
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
 * 1.16 StandEntityHeavyAttack.getFinisherVariationIfPresent: with a part the heavy punch needs missing (the arms, taken
 * by Boy II Man) and none its finisher variation needs, the heavy input uses the variation whatever the finisher meter.
 * A variation that needs a missing part is never used instead.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandFinisherPartFallbackGameTests {
	private static final short HEAVY = 161;
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc(StandFinisherPartFallbackGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));

	private StandFinisherPartFallbackGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void heavyInputWithoutArmsUsesTheKick(GameTestHelper helper) {
		try (Fixture tw = new Fixture(helper, "the_world", "kick", new Vec3(2, 2, 2))) {
			tw.stand.setFinisherMeter(0);
			check(helper, tw, "heavy_punch", "The World with all its parts and an empty meter");
			tw.instance.removePart(StandPart.ARMS);
			check(helper, tw, "kick", "The World without its arms and an empty meter");
			EntityActionInstance kick = tw.pressHeavy();
			helper.assertTrue(kick instanceof TheWorldKick, "The heavy input of The World without its arms did not kick: " + kick);
			LivingComponentAction.getComponent(tw.stand).setAction(null, SyncType.NO_SYNC);
			tw.instance.addPart(StandPart.ARMS);
			tw.instance.removePart(StandPart.LEGS);
			tw.stand.setFinisherMeter(1);
			check(helper, tw, "heavy_punch", "The World without its legs and a full meter");
		}
		try (Fixture mr = new Fixture(helper, "magicians_red", "kick", new Vec3(5, 2, 2))) {
			mr.stand.setFinisherMeter(0);
			mr.instance.removePart(StandPart.ARMS);
			check(helper, mr, "kick", "Magician's Red without its arms and an empty meter");
			EntityActionInstance kick = mr.pressHeavy();
			helper.assertTrue(kick instanceof MagiciansRedKick, "The heavy input of Magician's Red without its arms did not kick: " + kick);
		}
		try (Fixture sp = new Fixture(helper, "star_platinum", "uppercut", new Vec3(8, 2, 2))) {
			sp.stand.setFinisherMeter(0);
			sp.instance.removePart(StandPart.ARMS);
			check(helper, sp, "heavy_punch", "Star Platinum without its arms (the uppercut needs them too)");
		}
		helper.succeed();
	}

	private static void check(GameTestHelper helper, Fixture f, String expected, String scene) {
		// The available moves are cached for the rest of a tick; the parts and the meter changed in this one.
		forgetCachedMoves(f.power);
		var selected = f.power.updateAvailableMoves().getContextVariationContainer(f.power.getAbility("heavy_punch"));
		String name = selected != null ? selected.ability.name() : null;
		Ability expectedAbility = f.power.getAbility(expected);
		helper.assertTrue(expected.equals(name), scene + ": the heavy input resolved to " + name + ", 1.16 used " + expected
				+ " [" + expected + " available=" + (expectedAbility != null && expectedAbility.isAbilityAvailable(f.power))
				+ " check=" + (expectedAbility != null ? expectedAbility.checkConditions(f.power).isPositive() + "/"
						+ expectedAbility.checkConditions(f.power).getWarning() : "-")
				+ " parts=" + f.instance.getAllParts() + " melee=" + f.stand.canAttackMelee() + "]");
		if (!expected.equals("heavy_punch")) {
			helper.assertTrue(AbilityInput.withConditionCheck(selected, f.user, InputMethod.CLICK),
					scene + ": " + expected + " cannot be used: " + selected.conditionCheck.getWarning());
		}
	}

	private static void forgetCachedMoves(StandPower power) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
	}

	private static final class Fixture implements AutoCloseable {
		final Player user;
		final StandType type;
		final StandPower power;
		final StandInstance instance;
		final StandEntity stand;

		Fixture(GameTestHelper helper, String standId, String finisherSkill, Vec3 at) {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = Vec3.atCenterOf(helper.absolutePos(BlockPos.containing(at)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the " + standId + " user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			power = PowerClass.STAND.attachGet(user);
			try {
				helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
				power.setResolveLevel(2);
				var unlocked = UNLOCKS.forceUnlock(power, finisherSkill);
				helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
						"Could not unlock " + finisherSkill);
				instance = power.getStandInstance().orElse(null);
				helper.assertTrue(instance != null && type.summon(user, power), "Could not summon " + standId);
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "The " + standId + " Stand is missing");
			}
			catch (RuntimeException | Error e) {
				if (power.isSummoned() && type != null) {
					type.forceUnsummon(user, power);
				}
				user.discard();
				throw e;
			}
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			power.setStamina(power.getMaxStamina());
		}

		EntityActionInstance pressHeavy() {
			Ability base = power.getAbility("heavy_punch");
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			BufferingState buffer = BufferingState.clickOnly();
			var input = AbilityInput.keyPress(HEAVY, selected.ability, user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
			return input != null && input.action instanceof EntityActionInstance a ? a : stand.getCurStandAction();
		}

		@Override
		public void close() {
			AbilityInput.keyRelease(HEAVY, user);
			for (StandPart part : StandPart.values()) {
				instance.addPart(part);
			}
			if (stand != null) {
				LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			}
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
