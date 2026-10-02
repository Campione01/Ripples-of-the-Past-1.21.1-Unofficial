package rotp.core.gametest;

import java.util.List;

import rotp.core.JojoModConfig;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
// Exercises shared availability state; real client packet/HUD acceptance remains separate.
public final class SkippedStandProgressionSyncGameTests {
	private SkippedStandProgressionSyncGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void syncedSkipRefreshesCachedAbilityAvailability(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Boolean> skip = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Stand settings", "Stand Progression", "skipStandProgression"));
		boolean oldSkip = skip.get();
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Ability ability = null;
		int oldLevel = 0;
		try {
			skip.set(false);
			StandPower power = PowerClass.STAND.attachGet(player);
			power.setStand(ModStands.THE_WORLD.get());
			power.setResolveLevel(4);
			ability = power.getAbility("punch");
			helper.assertTrue(ability != null, "Missing cache-test ability");
			oldLevel = ability.getResolveLevelToUnlock();
			for (int requiredLevel : new int[] {5, 6}) {
				ability.resolveLevelToUnlock(requiredLevel);
				power.applySyncedProgressionSkipped(false);
				AvailableAbilities cached = power.updateAvailableMoves();
				helper.assertTrue(!cached._inMoveset.containsKey("punch"), "Unskipped Survival must stay locked");
				power.applySyncedProgressionSkipped(true);
				helper.assertTrue(cached == power.updateAvailableMoves() && cached._inMoveset.containsKey("punch"),
						"Owner snapshot must refresh the already-cached HUD/input availability object");
				helper.assertTrue(power.getResolveLevel() == 4, "Sync must not change the normal Resolve cap");
				power.getCurTypeData()._lockedAbilities.add("punch");
				power.applySyncedProgressionSkipped(true);
				helper.assertTrue(!cached._inMoveset.containsKey("punch"), "Resolve bypass must not bypass ordinary skill locks");
				power.getCurTypeData()._lockedAbilities.remove("punch");
				power.applySyncedProgressionSkipped(false);
				helper.assertTrue(!cached._inMoveset.containsKey("punch"), "Owner clear must invalidate availability in the same tick");
			}
			power.applySyncedProgressionSkipped(true);
			power.setStand(null);
			helper.assertTrue(!power.wasProgressionSkipped() && power.updateAvailableMoves()._inMoveset.isEmpty(),
					"Removing the Stand must clear the bypass and cached entries");
		}
		finally {
			if (ability != null) ability.resolveLevelToUnlock(oldLevel);
			skip.set(oldSkip);
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void skippedFlagFollowsReloadAndDeathRetention(GameTestHelper helper) {
		ModConfigSpec.ConfigValue<Boolean> keep = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Keep Powers After Death", "keepStandOnDeath"));
		boolean oldKeep = keep.get();
		Player original = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player reloaded = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player retained = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player lost = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = PowerClass.STAND.attachGet(original);
			power.setStand(ModStands.THE_WORLD.get());
			power.skipProgression();
			StandPower restored = PowerClass.STAND.attachGet(reloaded);
			restored.deserializeNBT(helper.getLevel().registryAccess(), power.serializeNBT(helper.getLevel().registryAccess()));
			helper.assertTrue(restored.wasProgressionSkipped(), "Reload must retain the owner-sync snapshot value");
			keep.set(true);
			power.onPlayerClone(retained, true);
			helper.assertTrue(PowerClass.STAND.attachGet(retained).wasProgressionSkipped(), "Kept death clone must retain the bypass");
			keep.set(false);
			power.onPlayerClone(lost, true);
			StandPower lostPower = PowerClass.STAND.attachGet(lost);
			helper.assertTrue(!lostPower.hasPower() && !lostPower.wasProgressionSkipped(), "Lost death clone must synchronize false");
		}
		finally {
			keep.set(oldKeep);
			original.discard();
			reloaded.discard();
			retained.discard();
			lost.discard();
		}
		helper.succeed();
	}
}
