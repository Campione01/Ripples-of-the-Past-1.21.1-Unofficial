package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.crazydiamond.CrazyDRevertEntityAndBlocksAbility;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.impl.stands.magiciansred.MagiciansRedFlameBurstAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandAwakening.AwakeningStage;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility.AutoSummonMode;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.subsystems.timestop.TimeStopLearning;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 had no Resolve-effect level: the Resolve effect only raised the stored level at its start, and only for a Stand
 * that used Resolve (ResolveCounter.onResolveEffectStarted), and every Resolve reader asked the stored level
 * (IStandPower.getResolveLevel, 0 for a Stand that does not use Resolve). Each test puts a Resolve effect of a high
 * amplifier over a stored level of 0 in the two ways the port allows it: a Stand not in full control (it does not use
 * Resolve, so the effect raises nothing) and a stored level lowered while the effect runs.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResolveEffectLevel116GameTests {
	private static final String STORED_LEVEL = "stored level";
	private static final String NOT_IN_FULL_CONTROL = "not in full control, Resolve effect";
	private static final String LOWERED_UNDER_THE_EFFECT = "stored level lowered under a Resolve effect";
	private static final String[] ROWS = { STORED_LEVEL, NOT_IN_FULL_CONTROL, LOWERED_UNDER_THE_EFFECT };
	private static final String CROSSFIRE = "crossfire_hurricane";

	private ResolveEffectLevel116GameTests() {}

	// StandAction.isUnlocked was the learning entry the stored level added (StandType.unlockNewActions)
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aResolveEffectUnlocksNothingByItsLevelAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "ResolveEffectUnlock", "magicians_red", false)) {
			Ability crossfire = s.power.getAbility(CROSSFIRE);
			StandTypePersistentData data = s.power.getCurTypeData();
			helper.assertTrue(crossfire != null && crossfire.getRequiredResolveLevel(s.power) == 4
					&& !data.isSkillUnlocked(CROSSFIRE), "fixture: Crossfire Hurricane does not need Resolve level 4 or its skill"
							+ " is unlocked");
			s.effectOverStoredLevelZero(NOT_IN_FULL_CONTROL, 4);
			data.unlockOriginalResolveSkills(s.power);
			helper.assertTrue(!data.isSkillUnlocked(CROSSFIRE),
					"1.16 unlocked nothing by a Resolve effect's level: the Crossfire Hurricane skill was unlocked by the user's tick");
			data._lockedAbilities.remove(CROSSFIRE);
			helper.assertTrue(!crossfire.isAbilityUnlocked(s.power) && !crossfire.isAbilityAvailable(s.power)
					&& !resolve(s.power, crossfire) && !crossfire.getResolveUnlockConditionCheck(s.power).isPositive(),
					"1.16 unlocked nothing by a Resolve effect's level: Crossfire Hurricane is unlocked for presses or the HUD with"
							+ " a Resolve effect of amplifier 4 and a Stand that does not use Resolve");

			s.user.removeEffect(ModStatusEffects.RESOLVE);
			s.power.userStandAwakeningState.stage = AwakeningStage.FULL_CONTROL;
			data._lockedAbilities.add(CROSSFIRE);
			s.power.setResolveLevel(4);
			helper.assertTrue(data.isSkillUnlocked(CROSSFIRE) && crossfire.isAbilityUnlocked(s.power),
					"fixture: the stored Resolve level 4 does not unlock Crossfire Hurricane");
			helper.succeed();
		}
	}

	// CrazyDiamondPreviousState.checkTarget :89 and standTickPerform read standPower.getResolveLevel()
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void crazyDiamondRevertsByItsStoredResolveLevelAsIn116(GameTestHelper helper) {
		for (String row : ROWS) {
			try (Scene s = new Scene(helper, "ResolveEffectRevert", "crazy_diamond", false)) {
				Entity tnt = EntityType.TNT.create(helper.getLevel());
				Entity golem = EntityType.IRON_GOLEM.create(helper.getLevel());
				helper.assertTrue(tnt != null && golem != null, "fixture: no TNT or iron golem");
				if (row.equals(STORED_LEVEL)) {
					s.power.setResolveLevel(3);
					helper.assertTrue(revertible(tnt, s) && !revertible(golem, s),
							"1.16 reverted TNT, not an iron golem, from the stored Resolve level 3");
					s.power.setResolveLevel(4);
					helper.assertTrue(revertible(tnt, s) && revertible(golem, s),
							"1.16 reverted TNT and an iron golem from the stored Resolve level 4");
					s.power.setResolveLevel(0);
					helper.assertTrue(!revertible(tnt, s) && !revertible(golem, s),
							"1.16 read the stored Resolve level, not the highest one reached: Crazy Diamond reverts at level 0");
				}
				else {
					s.effectOverStoredLevelZero(row, 4);
					helper.assertTrue(!revertible(tnt, s) && !revertible(golem, s),
							"1.16 read the stored Resolve level (" + row + "): Crazy Diamond reverts TNT or an iron golem at level 0");
				}
			}
		}
		helper.succeed();
	}

	// HierophantGreenBarrier.getMaxBarriersPlaceable and HierophantGreenEmeraldSplash.noPhases
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void hierophantGreenReadsTheStoredResolveLevelAsIn116(GameTestHelper helper) {
		for (String row : ROWS) {
			try (Scene s = new Scene(helper, "ResolveEffectHierophant", "hierophant_green", true)) {
				if (row.equals(STORED_LEVEL)) {
					s.power.setResolveLevel(2);
					helper.assertTrue(splashRecovery(s) == 40.0F, "1.16 gave the emerald splash its recovery at the stored Resolve"
							+ " level 2: " + splashRecovery(s));
					s.power.setResolveLevel(3);
					helper.assertTrue(HierophantGreenEntity.getMaxBarriersPlaceable(s.power) == 15 && splashRecovery(s) == 0.0F,
							"1.16 placed 15 barriers and gave the emerald splash no recovery at the stored Resolve level 3: "
									+ HierophantGreenEntity.getMaxBarriersPlaceable(s.power) + ", " + splashRecovery(s));
					s.power.setResolveLevel(4);
					helper.assertTrue(HierophantGreenEntity.getMaxBarriersPlaceable(s.power) == 100,
							"1.16 placed 100 barriers from the stored Resolve level 4: "
									+ HierophantGreenEntity.getMaxBarriersPlaceable(s.power));
				}
				else {
					s.effectOverStoredLevelZero(row, 4);
					helper.assertTrue(HierophantGreenEntity.getMaxBarriersPlaceable(s.power) == 15,
							"1.16 read the stored Resolve level for the barriers (" + row + "): "
									+ HierophantGreenEntity.getMaxBarriersPlaceable(s.power));
					helper.assertTrue(splashRecovery(s) == 0.0F,
							"1.16 gave the emerald splash no recovery during any Resolve effect (" + row + "): " + splashRecovery(s));
				}
			}
		}
		helper.succeed();
	}

	// MagiciansRedFlameBurst.standTickPerform and CrossfireHurricaneExplosion.filterEntities
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void magiciansRedReadsTheStoredResolveLevelAsIn116(GameTestHelper helper) {
		for (String row : ROWS) {
			try (Scene s = new Scene(helper, "ResolveEffectMagiciansRed", "magicians_red", true)) {
				float base = (float) s.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) / 5F;
				if (row.equals(STORED_LEVEL)) {
					s.power.setResolveLevel(2);
					helper.assertTrue(flameVelocity(s) == base, "1.16 shot the flames at reach / 5 at the stored Resolve level 2: "
							+ flameVelocity(s) + ", base " + base);
					s.power.setResolveLevel(3);
					helper.assertTrue(flameVelocity(s) == base * 2F && crossfireHitsItsUser(s),
							"1.16 shot the flames twice as fast, and its Crossfire Hurricane still burnt its user, at the stored"
									+ " Resolve level 3: " + flameVelocity(s) + ", base " + base);
					s.power.setResolveLevel(4);
					helper.assertTrue(!crossfireHitsItsUser(s), "1.16 spared the user of Crossfire Hurricane from the stored"
							+ " Resolve level 4");
				}
				else {
					s.effectOverStoredLevelZero(row, 4);
					helper.assertTrue(flameVelocity(s) == base, "1.16 read the stored Resolve level for the flames (" + row + "): "
							+ flameVelocity(s) + ", base " + base);
					helper.assertTrue(crossfireHitsItsUser(s),
							"1.16 read the stored Resolve level for Crossfire Hurricane's blast (" + row + "): it spared its user");
				}
			}
		}
		helper.succeed();
	}

	// TheWorldTimeStop.autoSummonStand and getHoldDurationToFire
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theWorldsTimeStopReadsTheStoredResolveLevelAsIn116(GameTestHelper helper) {
		for (String row : ROWS) {
			try (Scene s = new Scene(helper, "ResolveEffectTimeStop", "the_world", false)) {
				Ability found = s.power.getAbility(TimeStopLearning.TIME_STOP);
				helper.assertTrue(found instanceof TimeStopAbility, "fixture: The World has no time stop");
				TimeStopAbility timeStop = (TimeStopAbility) found;
				if (row.equals(STORED_LEVEL)) {
					s.power.setResolveLevel(2);
					helper.assertTrue(charge(s, timeStop) == 30.0F && autoSummonMode(s, timeStop) != AutoSummonMode.DISABLED
							&& !startsWithoutStand(s, timeStop), "1.16 summoned the Stand for a 30-tick charge at the stored"
									+ " Resolve level 2: " + charge(s, timeStop) + ", " + autoSummonMode(s, timeStop));
					s.power.setResolveLevel(3);
					helper.assertTrue(charge(s, timeStop) == 30.0F && autoSummonMode(s, timeStop) == AutoSummonMode.DISABLED
							&& startsWithoutStand(s, timeStop), "1.16 stopped time without summoning the Stand from the stored"
									+ " Resolve level 3: " + charge(s, timeStop) + ", " + autoSummonMode(s, timeStop));
					s.power.setResolveLevel(4);
					helper.assertTrue(charge(s, timeStop) == 20.0F, "1.16 charged The World's time stop for 20 ticks from the"
							+ " stored Resolve level 4: " + charge(s, timeStop));
				}
				else {
					s.effectOverStoredLevelZero(row, 4);
					helper.assertTrue(charge(s, timeStop) == 30.0F && autoSummonMode(s, timeStop) != AutoSummonMode.DISABLED
							&& !startsWithoutStand(s, timeStop), "1.16 read the stored Resolve level for The World's time stop ("
									+ row + "): " + charge(s, timeStop) + ", " + autoSummonMode(s, timeStop));
				}
			}
		}
		helper.succeed();
	}

	// SilverChariotEntity.canDeflectProjectiles and getUserWalkSpeed
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void silverChariotReadsTheStoredResolveLevelAsIn116(GameTestHelper helper) {
		for (String row : ROWS) {
			try (Scene s = new Scene(helper, "ResolveEffectSilverChariot", "silver_chariot", true)) {
				helper.assertTrue(s.stand.isSilverChariotRapierVisible(), "fixture: Silver Chariot shows no rapier");
				if (row.equals(STORED_LEVEL)) {
					s.power.setResolveLevel(3);
					helper.assertTrue(s.stand.getUserWalkSpeed(0.5F) == 0.5F && !deflects(s), "1.16 neither sped up the user"
							+ " nor deflected projectiles at the stored Resolve level 3: " + s.stand.getUserWalkSpeed(0.5F));
					s.power.setResolveLevel(4);
					helper.assertTrue(s.stand.getUserWalkSpeed(0.5F) > 0.5F && deflects(s), "1.16 sped up the user and deflected"
							+ " projectiles from the stored Resolve level 4: " + s.stand.getUserWalkSpeed(0.5F));
				}
				else {
					s.effectOverStoredLevelZero(row, 4);
					helper.assertTrue(s.stand.getUserWalkSpeed(0.5F) == 0.5F && !deflects(s), "1.16 read the stored Resolve level"
							+ " for Silver Chariot (" + row + "): walk speed " + s.stand.getUserWalkSpeed(0.5F) + ", deflects "
							+ deflects(s));
				}
			}
		}
		helper.succeed();
	}

	private static boolean revertible(Entity entity, Scene s) {
		return CrazyDRevertEntityAndBlocksAbility.canRevertEntity(entity, s.user);
	}

	private static float splashRecovery(Scene s) {
		Ability found = s.power.getAbility("emerald_splash");
		s.helper.assertTrue(found instanceof EntityActionAbility, "fixture: Hierophant Green has no emerald splash");
		return ((EntityActionAbility) found).initActionOnAbilityUse(s.helper.getLevel(), s.user, s.stand, null)
				.phasesLength.getFloat(ActionPhase.RECOVERY);
	}

	private static float flameVelocity(Scene s) {
		return (float) call(MagiciansRedFlameBurstAbility.FlameBurstShot.class, null, "flameVelocity",
				new Class<?>[] { StandEntity.class, LivingEntity.class }, s.stand, s.user);
	}

	private static boolean crossfireHitsItsUser(Scene s) {
		MRCrossfireHurricaneEntity crossfire = new MRCrossfireHurricaneEntity(s.stand, s.helper.getLevel());
		Vec3 pos = s.user.position();
		var explosion = new MRCrossfireHurricaneEntity.CrossfireHurricaneExplosion(s.helper.getLevel(), crossfire, null,
				pos.x, pos.y, pos.z, 3.0F, true, Explosion.BlockInteraction.KEEP) {
			private List<Entity> filtered(List<Entity> entities) {
				filterEntities(entities);
				return entities;
			}
		};
		List<Entity> entities = explosion.filtered(new ArrayList<>(List.of(s.user, s.stand)));
		s.helper.assertTrue(!entities.contains(s.stand), "fixture: Crossfire Hurricane's blast hits its own Stand");
		return entities.contains(s.user);
	}

	private static float charge(Scene s, TimeStopAbility timeStop) {
		return timeStop.initActionOnAbilityUse(s.helper.getLevel(), s.user, s.user, null)
				.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
	}

	private static AutoSummonMode autoSummonMode(Scene s, TimeStopAbility timeStop) {
		return (AutoSummonMode) call(TimeStopAbility.class, timeStop, "getAutoSummonMode",
				new Class<?>[] { StandPower.class, LivingEntity.class }, s.power, s.user);
	}

	private static boolean startsWithoutStand(Scene s, TimeStopAbility timeStop) {
		return (boolean) call(TimeStopAbility.class, timeStop, "canStartWithoutStand",
				new Class<?>[] { StandPower.class, LivingEntity.class }, s.power, s.user);
	}

	private static boolean deflects(Scene s) {
		return (boolean) call(StandEntity.class, s.stand, "canDeflectSilverChariotProjectiles", new Class<?>[0]);
	}

	private static Object call(Class<?> owner, Object target, String name, Class<?>[] types, Object... args) {
		Method method;
		try {
			method = owner.getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method.invoke(target, args);
		}
		catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			if (e.getCause() instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(owner.getSimpleName() + "." + name + " failed", e);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(owner.getSimpleName() + "." + name + " is not reachable", e);
		}
	}

	// the HUD's and the press's list of the moves the user has now
	private static boolean resolve(StandPower power, Ability ability) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
		return power.updateAvailableMoves().getContextVariationContainer(ability) != null;
	}

	/** A survival player given a Stand at Resolve level 0, its Stand summoned if asked; the user's tick does not run. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private final StandPower power;
		private StandEntity stand;

		private Scene(GameTestHelper helper, String name, String standId, boolean summon) {
			this.helper = helper;
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(standType != null, "Missing Stand type " + standId);
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			user.getAbilities().instabuild = false;
			Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			try {
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
				helper.assertTrue(power.usesResolve() && power.getResolveLevel() == 0,
						"fixture: a new " + standId + " does not use Resolve from level 0");
				if (summon) {
					helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
					stand = power.getSummonedStandEntity();
					helper.assertTrue(stand != null, "Summoned " + standId + " entity is missing");
				}
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		// a Resolve effect of this amplifier runs while the stored Resolve level is 0
		private void effectOverStoredLevelZero(String route, int amplifier) {
			if (route.equals(NOT_IN_FULL_CONTROL)) {
				power.userStandAwakeningState.stage = AwakeningStage.PARTIALLY_AWAKENED;
				helper.assertTrue(!power.usesResolve(), "fixture: a Stand not in full control uses Resolve");
				user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, amplifier));
			}
			else {
				user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, amplifier));
				helper.assertTrue(power.getResolveLevel() > 0, "fixture: the Resolve effect did not raise the stored level");
				power.setResolveLevel(0);
			}
			helper.assertTrue(power.getResolveLevel() == 0 && ResolveModeEffect.getResolveEffectLvl(user) == amplifier,
					"fixture (" + route + "): stored level " + power.getResolveLevel() + ", Resolve effect "
							+ ResolveModeEffect.getResolveEffectLvl(user));
		}

		@Override
		public void close() {
			user.removeEffect(ModStatusEffects.RESOLVE);
			if (power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
