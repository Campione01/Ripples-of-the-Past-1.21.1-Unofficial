package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.ClHamonPickTechniquePacket;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonTechnique;
import rotp.core.impl.powers.hamon.HamonUnlockableSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mrpresident.CocoJumboTurtleEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 JojoModConfig knobs: hamonDamageMultiplier (DamageUtil), mixHamonTechniques and
 * techniqueSkillRequirements (HamonTechniqueManager, synced for the Hamon screen) and
 * spawnCocoJumboTurtle (GameplayEventHandler.onMobSpawn). Config changes are made and restored
 * inside one call, so other tests never see them; spec setters are memory-only.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommonConfigKnobsGameTests {
	private static final float EPS = 1.0E-3F;

	private CommonConfigKnobsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void techniqueSlotsAndMixFollowConfig(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<List<? extends Integer>> requirements = spec("Stand settings", "Hamon settings", "techniqueSkillRequirements");
		ModConfigSpec.ConfigValue<Boolean> mix = spec("Stand settings", "Hamon settings", "mixHamonTechniques");
		List<? extends Integer> previousRequirements = requirements.get();
		boolean previousMix = mix.get();
		ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ConfigKnobsHamon"));
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add Hamon player");
		try {
			requirements.set(List.of(5, 7));
			mix.set(false);
			helper.assertTrue(HamonData.techniqueSlotsCount() == 2 && HamonData.techniqueSkillRequirement(0) == 5
					&& HamonData.techniqueSkillRequirement(1) == 7,
					"Technique slots ignore techniqueSkillRequirements: " + HamonData.techniqueSlotsCount() + " slots, first at "
							+ HamonData.techniqueSkillRequirement(0));

			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).orElseThrow();
			HamonTechnique zeppeli = ModHamonSkills.CHARACTER_ZEPPELI.get();
			setLevels(hamon, 4);
			helper.assertTrue(HamonTechnique.techniquesLocked(hamon), "Level 4/4 unlocked a slot configured at 5");
			setLevels(hamon, 5);
			helper.assertFalse(HamonTechnique.techniquesLocked(hamon), "Level 5/5 must unlock the slot configured at 5");
			helper.assertTrue(ClHamonPickTechniquePacket.pickFromClient(player, zeppeli.getRegistryKey()),
					"Pick refused at the configured level 5");

			setLevels(hamon, HamonData.MAX_STAT_LEVEL);
			HamonUnlockableSkill otherTechniqueSkill = new HamonUnlockableSkill(ModHamonSkills.AJA_STONE_KEEPER_DEF);
			helper.assertFalse(otherTechniqueSkill.canUnlockFromMenu(power, hamon).isPositive(),
					"Another technique's skill was learnable with mixHamonTechniques off");
			mix.set(true);
			helper.assertTrue(otherTechniqueSkill.canUnlockFromMenu(power, hamon).isPositive(),
					"mixHamonTechniques on must allow another technique's skill");

			// the Hamon screen reads the synced client copy
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			new JojoModConfig.Common.SyncedValues(JojoModConfig.getCommonConfigInstance(false)).writeToBuf(buf);
			JojoModConfig.applySyncedConfig(new JojoModConfig.Common.SyncedValues(buf));
			JojoModConfig.Common client = JojoModConfig.getCommonConfigInstance(true);
			helper.assertTrue(client.mixHamonTechniques.get() && List.of(5, 7).equals(client.techniqueSkillRequirements.get()),
					"Technique config did not reach the client copy: mix " + client.mixHamonTechniques.get()
							+ ", slots " + client.techniqueSkillRequirements.get());
			helper.succeed();
		}
		finally {
			requirements.set(previousRequirements);
			mix.set(previousMix);
			JojoModConfig.resetSyncedConfig();
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hamonDamageMultiplierScalesFinalDamage(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Double> multiplier = spec("Stand settings", "Hamon settings", "hamonDamageMultiplier");
		double previous = multiplier.get();
		Pig withSource = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 1);
		Pig withoutSource = helper.spawnWithNoFreeWill(EntityType.PIG, 3, 2, 1);
		Pig control = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 3);
		try {
			DamageSource source = HamonAbilityHelpers.hamonDamageSource(helper.getLevel(), null, null);
			multiplier.set(2.5D);
			float start = withSource.getHealth();
			HamonAbilityHelpers.hamonHurtWithAmount(withSource, 2.0F, source);
			HamonAbilityHelpers.hamonHurtWithAmount(withoutSource, 2.0F, source,
					HamonAbilityHelpers.HamonAttackProperties.NO_SOURCE_ENTITY_HAMON_MULTIPLIER);
			helper.assertTrue(Math.abs(start - withSource.getHealth() - 5.0F) < EPS,
					"hamonDamageMultiplier 2.5 must turn 2 Hamon damage into 5, took " + (start - withSource.getHealth()));
			helper.assertTrue(Math.abs(start - withoutSource.getHealth() - 5.0F) < EPS,
					"Charge hits skip the user factor but not hamonDamageMultiplier, took " + (start - withoutSource.getHealth()));

			multiplier.set(1.0D);
			HamonAbilityHelpers.hamonHurtWithAmount(control, 2.0F, source);
			helper.assertTrue(Math.abs(start - control.getHealth() - 2.0F) < EPS,
					"Control: multiplier 1 keeps 2 damage, took " + (start - control.getHealth()));
			helper.succeed();
		}
		finally {
			multiplier.set(previous);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void cocoJumboSpawnFollowsConfig(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Boolean> spawn = spec("spawnCocoJumboTurtle");
		boolean previous = spawn.get();
		try {
			spawn.set(true);
			helper.assertTrue(CocoJumboTurtleEntity.canSpawnWithTurtle(EntityType.TURTLE, MobSpawnType.NATURAL)
					&& CocoJumboTurtleEntity.canSpawnWithTurtle(EntityType.TURTLE, MobSpawnType.CHUNK_GENERATION),
					"Enabled config must allow the extra spawn next to natural turtles");
			helper.assertFalse(CocoJumboTurtleEntity.canSpawnWithTurtle(EntityType.PIG, MobSpawnType.NATURAL)
					|| CocoJumboTurtleEntity.canSpawnWithTurtle(EntityType.TURTLE, MobSpawnType.COMMAND),
					"Only natural, chunk or spawner turtles roll the extra spawn");
			spawn.set(false);
			helper.assertFalse(CocoJumboTurtleEntity.canSpawnWithTurtle(EntityType.TURTLE, MobSpawnType.NATURAL)
					|| CocoJumboTurtleEntity.canSpawnWithTurtle(EntityType.TURTLE, MobSpawnType.CHUNK_GENERATION),
					"spawnCocoJumboTurtle=false must stop the natural spawn");
			helper.succeed();
		}
		finally {
			spawn.set(previous);
		}
	}

	private static void setLevels(HamonData hamon, int level) {
		hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH, HamonData.pointsAtLevel(level), true, true);
		hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(level), true, true);
	}

	private static <T> ModConfigSpec.ConfigValue<T> spec(String... path) {
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of(path));
	}
}
