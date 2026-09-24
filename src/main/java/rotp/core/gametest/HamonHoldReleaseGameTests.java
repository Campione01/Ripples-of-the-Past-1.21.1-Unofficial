package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 held Sendo Overdrive (holdToFire(30, true)) and the S.Y.O. Barrage (holdToFire(60, false).holdType()) until the
 * key was released: holdDurationMax was Integer.MAX_VALUE, so PowerBaseImpl.tickHeldAction never fired them by
 * itself. The barrage's holdTick drained 1% of the max energy (NonStandPower.getMaxEnergy, at least 1) and ignored a
 * failed consume, and its energy check asked for nothing (energyCost 0, holdEnergyCost 0).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonHoldReleaseGameTests {
	private static final short KEY = 9;

	private HamonHoldReleaseGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void sendoHoldsFullChargeUntilRelease(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, ModHamonSkills.SENDO_OVERDRIVE.get())) {
			AABB area = sendoWall(helper);
			int wavesBefore = waves(helper, area);
			EntityActionInstance action = f.start("sendo_overdrive", true);
			f.tick(45);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
					"a held Sendo Overdrive fired by itself at its full charge: phase=" + action.getPhase());
			helper.assertTrue(waves(helper, area) == wavesBefore, "Sendo Overdrive sent its wave while still held");

			AbilityInput.keyRelease(KEY, f.user);
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "releasing the charged Sendo Overdrive did not fire it");
			f.tick(1);
			HamonSendoOverdriveEntity wave = newestWave(helper, area);
			helper.assertTrue(wave != null && waves(helper, area) > wavesBefore, "the released Sendo Overdrive sent no wave");
			// 1.16 heldRatio = (ticks held - 1) / 30, clamped: 45 held ticks make a full circle of sparks.
			helper.assertTrue(Math.abs(wave.sparksAngle - (float) Math.PI * 2.0F) < 0.001F,
					"45 held ticks must give the full spark circle: sparksAngle=" + wave.sparksAngle);
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void sendoNoKeyFiresAtFullCharge(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, ModHamonSkills.SENDO_OVERDRIVE.get())) {
			AABB area = sendoWall(helper);
			int wavesBefore = waves(helper, area);
			EntityActionInstance action = f.start("sendo_overdrive", false);
			f.tick(30);
			helper.assertTrue(waves(helper, area) == wavesBefore, "Sendo Overdrive fired before its full charge");
			f.tick(1);
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM || action.getPhase() == ActionPhase.RECOVERY,
					"a Sendo Overdrive no key holds must fire at its full charge: phase=" + action.getPhase());
			helper.assertTrue(waves(helper, area) > wavesBefore, "the unheld Sendo Overdrive sent no wave");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void syoBarrageHoldsUntilReleaseWithoutEnergy(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get())) {
			EntityActionAbility barrage = f.ability("sunlight_yellow_overdrive_barrage");
			f.hamon.setEnergy(0.0F);
			f.hamon.setBreathStability(0.0F);
			helper.assertTrue(barrage.checkSpecificConditions(f.power).isPositive(),
					"1.16 S.Y.O. Barrage asked for no energy, so it starts with no energy and no breath");
			f.hamon.setBreathStability(f.hamon.getMaxBreathStability());
			f.hamon.setEnergy(f.hamon.getMaxEnergy());

			EntityActionInstance action = f.start("sunlight_yellow_overdrive_barrage", true);
			f.tick(70);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.WINDUP,
					"a held S.Y.O. Barrage fired by itself at its full charge: phase=" + action.getPhase());
			helper.assertTrue(f.hamon.getEnergy() < f.hamon.getMaxEnergy(), "the S.Y.O. Barrage charge drained no energy");

			f.hamon.setEnergy(0.0F);
			f.hamon.setBreathStability(0.0F);
			f.user.setAirSupply(f.user.getMaxAirSupply());
			f.tick(5);
			helper.assertTrue(!action.isOver() && action.getPhase() == ActionPhase.WINDUP,
					"1.16 ignored the failed consume: the barrage charge goes on with no energy and no breath");
			// The tick still asks for 1% of a max energy of at least 1, fails, and leaves the user out of breath.
			helper.assertTrue(f.user.getAirSupply() == 0,
					"the failed barrage consume did not put the user out of breath: air=" + f.user.getAirSupply());

			AbilityInput.keyRelease(KEY, f.user);
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "releasing the charged S.Y.O. Barrage did not fire it");
			f.tick(2);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"the released S.Y.O. Barrage was stopped for lack of energy: phase=" + action.getPhase());
			helper.assertTrue(action.userWalkSpeed == 0.0F,
					"1.16 kept the user still through the barrage: walk speed=" + action.userWalkSpeed);
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void syoBarrageNoKeyFiresAtFullCharge(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get())) {
			EntityActionInstance action = f.start("sunlight_yellow_overdrive_barrage", false);
			f.tick(61);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"a S.Y.O. Barrage no key holds must fire at its full charge: phase=" + action.getPhase());
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void syoBarrageEarlyReleaseDoesNotFire(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get())) {
			EntityActionInstance action = f.start("sunlight_yellow_overdrive_barrage", true);
			f.tick(40);
			AbilityInput.keyRelease(KEY, f.user);
			helper.assertTrue(action.isOver(), "a S.Y.O. Barrage released before 60 ticks must not fire: phase=" + action.getPhase());
			helper.succeed();
		}
	}

	private static AABB sendoWall(GameTestHelper helper) {
		for (int y = 2; y <= 6; y++) {
			helper.setBlock(new BlockPos(2, y, 5), Blocks.STONE.defaultBlockState());
		}
		return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0D);
	}

	private static int waves(GameTestHelper helper, AABB area) {
		return helper.getLevel().getEntitiesOfClass(HamonSendoOverdriveEntity.class, area).size();
	}

	private static HamonSendoOverdriveEntity newestWave(GameTestHelper helper, AABB area) {
		HamonSendoOverdriveEntity newest = null;
		for (HamonSendoOverdriveEntity wave : helper.getLevel().getEntitiesOfClass(HamonSendoOverdriveEntity.class, area)) {
			if (newest == null || wave.getId() > newest.getId()) {
				newest = wave;
			}
		}
		return newest;
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;

		private Fixture(GameTestHelper helper, HamonSkill skill) {
			this.helper = helper;
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Hamon hold test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(skill);
			helper.assertTrue(hamon.isSkillLearned(skill), "Could not grant " + skill);
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			return (EntityActionAbility) found;
		}

		private EntityActionInstance start(String name, boolean heldByKey) {
			EntityActionInstance action = ability(name).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			if (heldByKey) {
				EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
				input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			}
			return action;
		}

		private void tick(int count) {
			// The real action lifecycle, without entity physics or passive Hamon regeneration.
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
		}
	}
}
