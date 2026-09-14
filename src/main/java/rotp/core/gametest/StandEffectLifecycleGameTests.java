package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.DataEventListeners;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectsClass;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.mechanics.standarrow.StandVirusActualEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.effect.StandEffectsTarget;
import rotp.core.powersystem.standpower.effect.UserStandEffects;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.impl.stands.crazydiamond.DriedBloodDropsEffect;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandEffectLifecycleGameTests {

	private StandEffectLifecycleGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standEffectsTickOnceWithStand(GameTestHelper helper) {
		verifyStandTick(helper, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standEffectsTickOnceWithoutStand(GameTestHelper helper) {
		verifyStandTick(helper, false);
	}

	private static void verifyStandTick(GameTestHelper helper, boolean hasStand) {
		try (Players players = new Players(helper.getLevel(), helper.getLevel())) {
			StandPower power = PowerClass.STAND.attachGet(players.original);
			if (hasStand) grantStand(helper, power);
			helper.assertTrue(power.hasPower() == hasStand, "Incorrect Stand ownership fixture");
			CountingStandEffect effect = addEffect(power, players.tracker);
			for (int tick = 1; tick <= 3; tick++) {
				listeners(players.original).onTick();
				helper.assertTrue(effect.tickCount == tick && effect.ticks == tick,
						"Stand effect must advance exactly once per runtime listener tick; hasStand="
								+ hasStand + ", dispatch=" + tick + ", actual=" + effect.tickCount);
			}
			helper.assertTrue(effect.starts == 1 && effect.stops == 0, "Ticking replayed lifecycle hooks");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void otherEffectsRetainAutomaticTickAndSync(GameTestHelper helper) {
		try (Players players = new Players(helper.getLevel(), helper.getLevel())) {
			var effects = EntityCustomEffectsClass.getCustomEffects(players.original, true);
			CountingOtherEffect effect = new CountingOtherEffect();
			effects.addEffect(effect);
			try {
				for (int tick = 1; tick <= 3; tick++) {
					listeners(players.original).onTick();
					helper.assertTrue(effect.tickCount == tick && effect.ticks == tick,
							"OTHER effect lost or duplicated automatic ticking");
				}
				listeners(players.original).onSyncToPlayer(players.original);
				listeners(players.original).onTracking(players.tracker);
				helper.assertTrue(effect.ownerSyncs == 1 && effect.sharedSyncs == 2,
						"OTHER effect lost or duplicated automatic synchronization");
			}
			finally {
				effects.removeEffect(effect);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void standEffectsSyncOnceToOwnerAndTracker(GameTestHelper helper) {
		try (Players players = new Players(helper.getLevel(), helper.getLevel())) {
			StandPower power = PowerClass.STAND.attachGet(players.original);
			grantStand(helper, power);
			CountingStandEffect effect = addEffect(power, players.tracker);
			assertSingleSync(helper, players.original, players.tracker, effect);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void nonDeathCloneMovesEffectsAcrossLevels(GameTestHelper helper) {
		ServerLevel otherLevel = helper.getLevel().getServer().getLevel(
				helper.getLevel().dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
		helper.assertTrue(otherLevel != null && otherLevel != helper.getLevel(),
				"Cross-level clone test needs a second server dimension");
		ModConfigSpec.BooleanValue keep = keepOnDeathConfig();
		boolean previous = keep.get();
		try {
			keep.set(false);
			verifyRetainedClone(helper, otherLevel, false);
		}
		finally {
			keep.set(previous);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void deathCloneRetainsSurvivorsWithoutReplayingHooks(GameTestHelper helper) {
		ModConfigSpec.BooleanValue keep = keepOnDeathConfig();
		boolean previous = keep.get();
		try {
			keep.set(true);
			verifyRetainedClone(helper, helper.getLevel(), true);
		}
		finally {
			keep.set(previous);
		}
		helper.succeed();
	}

	private static void verifyRetainedClone(GameTestHelper helper, ServerLevel newLevel, boolean wasDeath) {
		try (Players players = new Players(helper.getLevel(), newLevel)) {
			StandPower oldPower = PowerClass.STAND.attachGet(players.original);
			StandPower newPower = PowerClass.STAND.attachGet(players.replacement);
			grantStand(helper, oldPower);
			UserStandEffects oldMap = oldPower.userStandEffects;
			UserStandEffects newMap = newPower.userStandEffects;
			CountingStandEffect first = addEffect(oldPower, players.tracker);
			CountingStandEffect second = addEffect(oldPower, players.tracker);
			CountingStandEffect stopped = addEffect(oldPower, players.tracker);
			first.tickCount = 17;
			second.tickCount = 29;
			first.removeOnUserDeath = false;
			first.removeOnUserLogout = false;
			first.removeOnStandChanged = false;
			second.removeOnUserDeath = !wasDeath;
			second.removeOnUserLogout = true;
			stopped.removeOnUserDeath = false;
			stopped.remove();
			int firstId = first.getId();
			int secondId = second.getId();
			int stoppedId = stopped.getId();
			CountingStandEffect deathOnly = null;
			if (wasDeath) {
				deathOnly = addEffect(oldPower, players.tracker);
				oldMap.onStandUserDeath(players.original);
				helper.assertTrue(deathOnly.stops == 1 && oldMap.getById(deathOnly.getId()) == null,
						"Death-only effect was not stopped and removed before cloning");
			}

			listeners(players.original).onClone(players.replacement, wasDeath);
			helper.assertTrue(newPower.hasPower(), "Retained clone lost its Stand");
			helper.assertTrue(newPower.userStandEffects == newMap && newMap != oldMap,
					"Clone must retain the replacement player's own effect map");
			helper.assertTrue(newMap.entity == players.replacement && oldMap.entity == players.original,
					"Effect map still belongs to the original player");
			helper.assertTrue(oldMap.getEffects().isEmpty() && newMap.getEffects().size() == 3,
					"Clone did not move every effect out of the old map");
			helper.assertTrue(newMap.getById(firstId) == first && newMap.getById(secondId) == second
					&& newMap.getById(stoppedId) == stopped && first.getId() == firstId
					&& second.getId() == secondId && stopped.getId() == stoppedId,
					"Clone replaced effect objects or changed their IDs");
			for (CountingStandEffect effect : List.of(first, second, stopped)) {
				helper.assertTrue(effect.getEntity() == players.replacement
						&& effect.getUserPower() == newPower && effect.level == newLevel,
						"Transferred effect retained an old entity, StandPower, or level");
				helper.assertTrue(effect.getTarget() == players.tracker
						&& players.tracker.getUUID().equals(effect.getTargetUUID()),
						"Transfer changed the existing effect target");
				helper.assertTrue(effect.starts == 1 && effect.stops == 0,
						"Transfer replayed start/stop instead of moving the effect");
				helper.assertTrue(StandEffectsTarget.getEffectsReadOnly(players.tracker)
						.filter(targeted -> targeted == effect).count() == 1,
						"Transfer lost or duplicated the target-side effect reference");
			}
			helper.assertTrue(first.tickCount == 17 && second.tickCount == 29 && stopped.isStopped(),
					"Clone reset elapsed time or pending-stop state");
			helper.assertTrue(!first.removeOnUserDeath && !first.removeOnUserLogout
					&& !first.removeOnStandChanged && first.needsTarget
					&& second.removeOnUserDeath == !wasDeath && second.removeOnUserLogout
					&& second.removeOnStandChanged,
					"Clone changed per-effect lifecycle flags");

			listeners(players.original).onTick();
			listeners(players.original).onSyncToPlayer(players.original);
			listeners(players.original).onTracking(players.tracker);
			helper.assertTrue(first.tickCount == 17 && second.tickCount == 29 && stopped.stops == 0
					&& first.ownerSyncs == 0 && first.sharedSyncs == 0,
					"Original player's listeners still drive transferred effects");
			listeners(players.replacement).onTick();
			helper.assertTrue(first.tickCount == 18 && second.tickCount == 30
					&& first.ticks == 1 && second.ticks == 1,
					"Replacement player's listeners do not tick transferred effects exactly once");
			helper.assertTrue(stopped.stops == 1 && newMap.getById(stoppedId) == null,
					"Pending-stop effect was not removed exactly once by its new owner");
			assertSingleSync(helper, players.replacement, players.tracker, first);
			helper.assertTrue(deathOnly == null || deathOnly.stops == 1,
					"Clone or later dispatch replayed the death-only stop hook");
			newMap.removeEffect(first);
			helper.assertTrue(first.stops == 1 && first.stoppedOwner == players.replacement
					&& first.stoppedLevel == newLevel
					&& StandEffectsTarget.getEffectsReadOnly(players.tracker)
							.noneMatch(targeted -> targeted == first),
					"Transferred effect cleanup used the old owner or leaked target bookkeeping");
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void deathCloneWithoutKeepClearsStandAndEffects(GameTestHelper helper) {
		ModConfigSpec.BooleanValue keep = keepOnDeathConfig();
		boolean previous = keep.get();
		try (Players players = new Players(helper.getLevel(), helper.getLevel())) {
			keep.set(false);
			StandPower oldPower = PowerClass.STAND.attachGet(players.original);
			StandPower newPower = PowerClass.STAND.attachGet(players.replacement);
			grantStand(helper, oldPower);
			helper.assertTrue(!oldPower.getPowerType().keepOnDeath(oldPower),
					"Non-keep death configuration did not take effect");
			CountingStandEffect effect = addEffect(oldPower, players.tracker);
			effect.removeOnUserDeath = false;
			listeners(players.original).onClone(players.replacement, true);
			helper.assertTrue(!newPower.hasPower() && newPower.userStandEffects.getEffects().isEmpty()
					&& newPower.userStandEffects.entity == players.replacement,
					"Non-keep death clone retained a Stand, effects, or stale map owner");
			listeners(players.replacement).onTick();
			listeners(players.replacement).onSyncToPlayer(players.replacement);
			listeners(players.replacement).onTracking(players.tracker);
			helper.assertTrue(effect.tickCount == 0 && effect.ownerSyncs == 0 && effect.sharedSyncs == 0,
					"Non-keep replacement listeners still drive the discarded Stand's effects");
		}
		finally {
			keep.set(previous);
		}
		helper.succeed();
	}

	private static ModConfigSpec.BooleanValue keepOnDeathConfig() {
		// Spec setters are memory-only; restore in finally and never save the user's config.
		return JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Keep Powers After Death", "keepStandOnDeath"));
	}

	private static DataEventListeners listeners(ServerPlayer player) {
		return player.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get());
	}

	private static void grantStand(GameTestHelper helper, StandPower power) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(type != null, "Missing registered Star Platinum");
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant test Stand");
	}

	private static CountingStandEffect addEffect(StandPower power, ServerPlayer target) {
		CountingStandEffect effect = new CountingStandEffect();
		effect.withTarget(target);
		power.userStandEffects.addEffect(effect);
		return effect;
	}

	private static void assertSingleSync(GameTestHelper helper, ServerPlayer owner,
			ServerPlayer tracker, CountingStandEffect effect) {
		int ownerBefore = effect.ownerSyncs;
		int sharedBefore = effect.sharedSyncs;
		listeners(owner).onSyncToPlayer(owner);
		helper.assertTrue(effect.ownerSyncs == ownerBefore + 1 && effect.sharedSyncs == sharedBefore + 1,
				"Owner synchronization reached a Stand effect more or less than once");
		listeners(owner).onTracking(tracker);
		helper.assertTrue(effect.ownerSyncs == ownerBefore + 1 && effect.sharedSyncs == sharedBefore + 2,
				"Tracking synchronization reached a Stand effect more or less than once");
	}

	private static final class Players implements AutoCloseable {
		final FakePlayer original;
		final FakePlayer replacement;
		final FakePlayer tracker;

		Players(ServerLevel oldLevel, ServerLevel newLevel) {
			GameProfile profile = new GameProfile(UUID.randomUUID(), "EffectLifecycle");
			// Same UUID, distinct instances, without FakePlayerFactory's shared cache or world insertion.
			original = new FakePlayer(oldLevel, profile);
			replacement = new FakePlayer(newLevel, profile);
			tracker = new FakePlayer(oldLevel, new GameProfile(UUID.randomUUID(), "EffectTracker"));
		}

		@Override
		public void close() {
			for (FakePlayer player : List.of(original, replacement, tracker)) {
				StandPower power = StandPower.get(player);
				if (power != null) {
					for (StandEffectInstance effect : List.copyOf(power.userStandEffects.getEffects())) {
						power.userStandEffects.removeEffect(effect);
					}
				}
				player.discard();
			}
		}
	}

	private static final class CountingStandEffect extends DriedBloodDropsEffect {
		int starts;
		int ticks;
		int stops;
		int ownerSyncs;
		int sharedSyncs;
		ServerPlayer stoppedOwner;
		Level stoppedLevel;

		CountingStandEffect() {
			super(ModStandAbilities.EFFECT_CD_BLOOD_DROPS.get());
		}

		@Override protected void start() { starts++; super.start(); }
		@Override protected void tick() { ticks++; super.tick(); }
		@Override protected void stop() {
			stops++;
			stoppedOwner = (ServerPlayer) getEntity();
			stoppedLevel = level;
			super.stop();
		}
		@Override public void syncWithUserOnly(ServerPlayer player) {
			ownerSyncs++;
			super.syncWithUserOnly(player);
		}
		@Override public void syncWithTrackingOrUser(ServerPlayer player) {
			sharedSyncs++;
			super.syncWithTrackingOrUser(player);
		}
	}

	private static final class CountingOtherEffect extends StandVirusActualEffect {
		int ticks;
		int ownerSyncs;
		int sharedSyncs;

		@Override protected void tick() { ticks++; }
		@Override protected void stop() {}
		@Override public void syncWithUserOnly(ServerPlayer player) { ownerSyncs++; }
		@Override public void syncWithTrackingOrUser(ServerPlayer player) { sharedSyncs++; }
	}
}
