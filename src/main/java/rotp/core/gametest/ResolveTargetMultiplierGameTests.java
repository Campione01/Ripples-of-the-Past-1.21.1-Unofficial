package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ResolveCounter.addResolve multiplied a Stand user's Resolve for a hit by the target power's
 * getTargetResolveMultiplier: Hamon 2 (HamonPowerType:118), Vampirism 2^max(difficulty - 1, 0)
 * (VampirismPowerType:179), Zombie 1. The port's ResolveCounter.addResolve reads PlayerPower.getTargetResolveMultiplier.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResolveTargetMultiplierGameTests {
	private ResolveTargetMultiplierGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hamonAndZombieTargetMultipliers(GameTestHelper helper) {
		Player attacker = placed(helper, new BlockPos(1, 2, 1));
		Player target = placed(helper, new BlockPos(3, 2, 1));
		try {
			StandPower attackerStand = PowerClass.STAND.attachGet(attacker);
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(target);
			helper.assertTrue(power.getTargetResolveMultiplier(attackerStand) == 1.0F,
					"a target without a power must give x1 Resolve: " + power.getTargetResolveMultiplier(attackerStand));
			power.setPowerType(ModPlayerPowers.HAMON.get());
			float hamon = power.getTargetResolveMultiplier(attackerStand);
			helper.assertTrue(hamon == 2.0F, "1.16 Hamon target gives x2 Resolve, got x" + hamon);
			power.setPowerType(ModPlayerPowers.ZOMBIE.get());
			float zombie = power.getTargetResolveMultiplier(attackerStand);
			helper.assertTrue(zombie == 1.0F, "1.16 Zombie target gives x1 Resolve, got x" + zombie);
			helper.succeed();
		}
		finally {
			attacker.discard();
			target.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void vampireTargetMultiplierFollowsDifficulty(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty original = helper.getLevel().getDifficulty();
		Player attacker = placed(helper, new BlockPos(1, 2, 1));
		Player target = placed(helper, new BlockPos(3, 2, 1));
		try {
			StandPower attackerStand = PowerClass.STAND.attachGet(attacker);
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(target);
			power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			// set and restored in this call, no tick sees the change
			for (Difficulty difficulty : Difficulty.values()) {
				server.setDifficulty(difficulty, true);
				Difficulty actual = helper.getLevel().getDifficulty();
				float expected = switch (actual) {
					case PEACEFUL, EASY -> 1.0F;
					case NORMAL -> 2.0F;
					case HARD -> 4.0F;
				};
				float got = power.getTargetResolveMultiplier(attackerStand);
				helper.assertTrue(got == expected,
						"1.16 vampire target on " + actual + " gives x" + expected + " Resolve, got x" + got);
			}
			helper.succeed();
		}
		finally {
			server.setDifficulty(original, true);
			attacker.discard();
			target.discard();
		}
	}

	private static Player placed(GameTestHelper helper, BlockPos pos) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(pos));
		player.moveTo(origin.x, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Resolve test player");
		return player;
	}
}
