package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonHealing.holdTick (:71, :141-152) and HamonDetector.holdTick (:47, :66) counted Control points and the
 * efficiency from getHeldTickEnergyCost(power), the configured 5 in every game mode: only the energy drain was
 * skipped for a Creative user (NonStandPowerType.consumeEnergy).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonHeldUtilityCreativePointsGameTests {
	private static final short KEY = 9;
	private static final float EPS = 1.0E-4F;

	private HamonHeldUtilityCreativePointsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void healingGivesCreativeUserTheSurvivalControlPoints(GameTestHelper helper) {
		compare(helper, "hamon_healing", "Hamon Healing", 4, false);
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void detectorGivesCreativeUserTheSurvivalControlPoints(GameTestHelper helper) {
		compare(helper, "hamon_detector", "Hamon Detector", 20, true);
	}

	private static void compare(GameTestHelper helper, String abilityName, String label, int ticks, boolean withPig) {
		float survivalGain;
		try (Fixture f = new Fixture(helper)) {
			survivalGain = f.controlGainFromHeldTicks(abilityName, ticks, withPig);
			helper.assertTrue(f.hamon.getEnergy() < f.hamon.getMaxEnergy(), "held Survival " + label + " cost no energy");
		}
		helper.assertTrue(survivalGain > 0.0F, "held Survival " + label + " gave no Control progress: " + survivalGain);
		try (Fixture f = new Fixture(helper)) {
			f.user.getAbilities().instabuild = true;
			float creativeGain = f.controlGainFromHeldTicks(abilityName, ticks, withPig);
			helper.assertTrue(Math.abs(creativeGain - survivalGain) < EPS,
					"1.16 counts the configured held cost in every game mode: " + ticks + " held Creative " + label
							+ " ticks must give the Survival Control progress " + survivalGain + ", got " + creativeGain);
			helper.assertTrue(f.hamon.getEnergy() == f.hamon.getMaxEnergy(),
					"held Creative " + label + " drained energy: " + f.hamon.getEnergy());
		}
		helper.succeed();
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;
		private Pig pig;

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the held Hamon test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.HEALING.get());
			hamon.learnSkill(ModHamonSkills.DETECTOR.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		// Whole points plus the saved fraction of the next one.
		private float controlProgress() {
			CompoundTag nbt = hamon.serializeNBT(helper.getLevel().registryAccess());
			return nbt.getInt("ControlPoints") + nbt.getFloat("PointsIncFrac");
		}

		private float controlGainFromHeldTicks(String abilityName, int ticks, boolean withPig) {
			if (withPig) {
				pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 2));
			}
			float before = controlProgress();
			Ability found = power.getAbility(abilityName);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + abilityName);
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			// The real action lifecycle, without entity physics or passive Hamon regeneration.
			for (int tick = 0; tick < ticks; tick++) {
				user.tickCount++;
				component.tick();
			}
			helper.assertTrue(component.getAction() == action && !action.isOver(),
					abilityName + " did not stay held for " + ticks + " ticks: phase=" + action.getPhase());
			return controlProgress() - before;
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
			if (pig != null) {
				pig.discard();
			}
		}
	}
}
