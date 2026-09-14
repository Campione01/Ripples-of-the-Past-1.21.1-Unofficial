package rotp.core.gametest;

import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonSendoWaveKickAbility;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SendoWaveKickLifecycleGameTests {

	private SendoWaveKickLifecycleGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void airborneKickKeepsSameActionBeyondTenTicks(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "SendoAirborne")) {
			EntityActionInstance action = f.start();
			int id = action.id;
			long generation = action.networkGeneration();
			var animation = action.getEntityAnim();
			action.onKeyRelease(f.user);
			f.tick(60);
			helper.assertTrue(!f.user.onGround(), "Airborne fixture unexpectedly landed");
			helper.assertTrue(f.component.getAction() == action && !action.isOver()
					&& action.getPhase() == ActionPhase.PERFORM
					&& action.id == id && action.networkGeneration() == generation,
					"Airborne click action ended or restarted after its nominal ten-tick phase");
			helper.assertTrue(animation != null && action.getEntityAnim() == animation,
					"Sustained kick lost its action animation identifier");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void landingEndsSustainedKick(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "SendoLanding")) {
			EntityActionInstance action = f.start();
			f.tick(20);
			f.assertPerforming(action);
			f.user.setOnGround(true);
			f.tick(1);
			f.assertEnded(action, "Landing");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void waterEndsSustainedKick(GameTestHelper helper) {
		assertFluidEndsKick(helper, Blocks.WATER.defaultBlockState(), "SendoWater");
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lavaEndsSustainedKick(GameTestHelper helper) {
		assertFluidEndsKick(helper, Blocks.LAVA.defaultBlockState(), "SendoLava");
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void explicitStopAndRemovalEndSustainedKick(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "SendoAbort")) {
			EntityActionInstance action = f.start();
			f.tick(20);
			f.assertPerforming(action);
			f.user.fallDistance = 5.0F;
			action.forceStop();
			f.tick(1);
			f.assertEnded(action, "Explicit stop");
			assertClose(helper, f.user.fallDistance, 0.0F, "Stopped kick did not clear fall distance");

			f.refillAndGround();
			EntityActionInstance removed = f.start();
			f.tick(20);
			f.assertPerforming(removed);
			f.user.fallDistance = 5.0F;
			f.component.setAction(null, SyncType.NO_SYNC);
			f.tick(1);
			f.assertEnded(removed, "External removal");
			assertClose(helper, f.user.fallDistance, 0.0F, "Removed kick did not clear fall distance");

			f.refillAndGround();
			EntityActionInstance recovering = f.start();
			f.tick(20);
			f.assertPerforming(recovering);
			recovering.startRecovery();
			f.tick(1);
			f.assertEnded(recovering, "Explicit recovery");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void deadUserEndsSustainedKick(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "SendoDeath")) {
			EntityActionInstance action = f.start();
			f.tick(20);
			f.assertPerforming(action);
			f.user.setHealth(0.0F);
			f.tick(1);
			f.assertEnded(action, "User death");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void kickRequiresGroundAndAvailableHamon(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "SendoConditions")) {
			helper.assertTrue(f.ability.checkSpecificConditions(f.power).isPositive(),
					"Grounded funded kick was rejected");
			f.user.setOnGround(false);
			helper.assertTrue(!f.ability.checkSpecificConditions(f.power).isPositive(),
					"Kick accepted an airborne start");
			f.user.setOnGround(true);
			f.hamon.setBreathStability(0.0F);
			f.hamon.setEnergy(0.0F);
			helper.assertTrue(!f.ability.checkSpecificConditions(f.power).isPositive(),
					"Kick accepted a start without energy or breath reserve");
			EntityActionInstance unpaid = f.ability.initActionOnAbilityUse(
					helper.getLevel(), f.user, f.user, null);
			f.component.setAction(unpaid, f.user, SyncType.NO_SYNC);
			f.tick(1);
			f.assertEnded(unpaid, "Failed initial payment");
			helper.assertTrue(f.user.onGround(), "Failed initial payment launched the user");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void sustainedKickPaysOnceAndHitsEachTargetOnce(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "SendoSingleHit")) {
			Cow target = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(target != null, "Could not create kick target");
			try {
				Vec3 targetPos = f.user.position().add(0.0D, 0.0D, 0.6D);
				target.setPos(targetPos.x, targetPos.y, targetPos.z);
				target.setNoAi(true);
				target.setNoGravity(true);
				helper.assertTrue(helper.getLevel().addFreshEntity(target), "Could not add kick target");
				float energy = f.hamon.getEnergy();
				float stability = f.hamon.getBreathStability();
				float health = target.getHealth();
				EntityActionInstance action = f.start();
				f.tick(1);
				helper.assertTrue(target.getHealth() < health, "Initial kick did not damage its target");
				float hitHealth = target.getHealth();
				assertClose(helper, f.hamon.getEnergy(), energy - 1000.0F,
						"Initial kick did not consume exactly 1000 energy");
				for (int tick = 0; tick < 40; tick++) {
					// Reset immunity and position so neither can conceal a repeated hit.
					target.invulnerableTime = 0;
					target.setDeltaMovement(Vec3.ZERO);
					target.setPos(targetPos.x, targetPos.y, targetPos.z);
					f.tick(1);
					assertClose(helper, target.getHealth(), hitHealth, "Sustained kick damaged the same target again");
				}
				f.assertPerforming(action);
				assertClose(helper, f.hamon.getEnergy(), energy - 1000.0F, "Sustained kick charged energy again");
				assertClose(helper, f.hamon.getBreathStability(), stability, "Sustained kick charged breath reserve");
				helper.succeed();
			}
			finally {
				target.discard();
			}
		}
	}

	private static void assertFluidEndsKick(GameTestHelper helper, BlockState fluid, String name) {
		try (Fixture f = new Fixture(helper, name)) {
			EntityActionInstance action = f.start();
			f.tick(20);
			f.assertPerforming(action);
			BlockPos pos = f.user.blockPosition();
			BlockState original = helper.getLevel().getBlockState(pos);
			try {
				helper.getLevel().setBlockAndUpdate(pos, fluid);
				helper.assertTrue(!helper.getLevel().getFluidState(pos).isEmpty(), "Fluid fixture is dry");
				f.tick(1);
				f.assertEnded(action, name);
			}
			finally {
				helper.getLevel().setBlockAndUpdate(pos, original);
			}
			helper.succeed();
		}
	}

	private static void assertClose(GameTestHelper helper, float actual, float expected, String message) {
		helper.assertTrue(Math.abs(actual - expected) < 0.0001F,
				message + ": expected=" + expected + ", actual=" + actual);
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final HamonSendoWaveKickAbility ability;
		private final LivingComponentAction component;

		private Fixture(GameTestHelper helper, String name) {
			this.helper = helper;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.setPos(origin.x, origin.y, origin.z);
			user.setYRot(0.0F);
			user.getAbilities().instabuild = false;
			user.getAbilities().flying = false;
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add kick test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			helper.assertTrue(hamon.learnSkill(ModHamonSkills.SENDO_WAVE_KICK.get()), "Could not grant kick skill");
			Ability found = power.getAbility("sendo_wave_kick");
			helper.assertTrue(found instanceof HamonSendoWaveKickAbility, "Missing registered kick ability");
			ability = (HamonSendoWaveKickAbility) found;
			component = LivingComponentAction.getComponent(user);
			refillAndGround();
		}

		private void refillAndGround() {
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			helper.assertTrue(hamon.getEnergy() >= 1000.0F, "Kick fixture lacks full initial energy");
			user.setOnGround(true);
		}

		private EntityActionInstance start() {
			helper.assertTrue(ability.checkSpecificConditions(power).isPositive(), "Kick fixture cannot start");
			EntityActionInstance action = ability.initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		private void tick(int count) {
			// Drive the real action lifecycle without entity physics or passive Hamon regeneration.
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		private void assertPerforming(EntityActionInstance action) {
			helper.assertTrue(component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"Kick ended before the termination condition was applied");
		}

		private void assertEnded(EntityActionInstance action, String cause) {
			helper.assertTrue(action.isOver() && component.getAction() == null, cause + " did not clear the kick");
		}

		@Override
		public void close() {
			component.setAction(null, SyncType.NO_SYNC);
			user.discard();
		}
	}
}
