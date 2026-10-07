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
 * 1.16 HamonTornadoOverdrive was builder.holdType() with holdEnergyCost(75F): its holdTick reset the fall
 * distance and hurt nearby entities from the first held tick, with no windup before it.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonTornadoOverdriveGameTests {
	private static final short KEY = 9;

	private HamonTornadoOverdriveGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void tornadoHeldTickRunsFromPress(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 2));
			float healthBefore = pig.getHealth();
			f.user.fallDistance = 10.0F;
			EntityActionInstance action = f.start();
			f.tick(1);
			helper.assertTrue(f.component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"Tornado Overdrive must hold from the press with no windup: phase=" + action.getPhase());
			helper.assertTrue(f.user.fallDistance == 0.0F,
					"the first held Tornado Overdrive tick did not reset the fall distance: " + f.user.fallDistance);
			helper.assertTrue(pig.getHealth() < healthBefore,
					"the first held Tornado Overdrive tick hurt nothing: pig health=" + pig.getHealth());
			helper.assertTrue(f.hamon.getEnergy() < f.hamon.getMaxEnergy(),
					"the first held Tornado Overdrive tick cost no energy");

			// A tap still got its held tick; the release ends the hold.
			AbilityInput.keyRelease(KEY, f.user);
			helper.assertTrue(action.isOver(), "releasing Tornado Overdrive did not end it: phase=" + action.getPhase());
			pig.discard();
			helper.succeed();
		}
	}

	/**
	 * 1.16 awarded Strength points with getHeldTickEnergyCost(power), the configured 75 in every game mode: only
	 * the energy drain was skipped for a Creative user (NonStandPowerType.consumeEnergy).
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void tornadoHitGivesCreativeUserTheSurvivalStrengthPoints(GameTestHelper helper) {
		float survivalGain;
		try (Fixture f = new Fixture(helper)) {
			survivalGain = f.strengthGainFromOneHitTick();
			helper.assertTrue(f.hamon.getEnergy() < f.hamon.getMaxEnergy(),
					"a Survival Tornado Overdrive tick cost no energy");
		}
		helper.assertTrue(survivalGain > 0.0F,
				"a Survival Tornado Overdrive hit gave no Strength progress: " + survivalGain);
		try (Fixture f = new Fixture(helper)) {
			f.user.getAbilities().instabuild = true;
			float creativeGain = f.strengthGainFromOneHitTick();
			helper.assertTrue(creativeGain == survivalGain,
					"a Creative Tornado Overdrive hit must give the Survival Strength progress " + survivalGain
							+ ", got " + creativeGain);
			helper.assertTrue(f.hamon.getEnergy() == f.hamon.getMaxEnergy(),
					"a Creative Tornado Overdrive tick drained energy: " + f.hamon.getEnergy());
		}
		helper.succeed();
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Tornado Overdrive test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.TORNADO_OVERDRIVE.get());
			helper.assertTrue(hamon.isSkillLearned(ModHamonSkills.TORNADO_OVERDRIVE.get()), "Could not grant Tornado Overdrive");
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		private EntityActionInstance start() {
			Ability found = power.getAbility("tornado_overdrive");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered tornado_overdrive");
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			return action;
		}

		// Whole points plus the saved fraction of the next one.
		private float strengthProgress() {
			CompoundTag nbt = hamon.serializeNBT(helper.getLevel().registryAccess());
			return nbt.getInt("StrengthPoints") + nbt.getFloat("PointsIncFrac");
		}

		private float strengthGainFromOneHitTick() {
			Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 2));
			float healthBefore = pig.getHealth();
			float progressBefore = strengthProgress();
			start();
			tick(1);
			helper.assertTrue(pig.getHealth() < healthBefore, "the held Tornado Overdrive tick hurt nothing");
			pig.discard();
			return strengthProgress() - progressBefore;
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
