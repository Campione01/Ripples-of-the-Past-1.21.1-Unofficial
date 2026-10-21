package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonWallClimbing2.perform moved the user to the wall and started the climb only if the user was not climbing
 * already, or was climbing at another angle (getWallClimbYRot): clicked again on the wall being climbed, it did nothing.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonWallClimbing116GameTests {
	private HamonWallClimbing116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void wallClimbingClickedOnTheWallBeingClimbedDoesNothingAsIn116(GameTestHelper helper) {
		BlockPos wall = new BlockPos(1, 2, 3);
		helper.setBlock(wall, Blocks.STONE.defaultBlockState());
		helper.setBlock(wall.above(), Blocks.STONE.defaultBlockState());
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 away = helper.absoluteVec(new Vec3(1.5D, 2.0D, 2.5D));
		user.moveTo(away.x, away.y, away.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the wall climbing player");
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			helper.assertTrue(hamon.learnSkill(ModHamonSkills.WALL_CLIMBING.get()), "setup: Wall Climbing is not learned");
			Ability found = power.getAbility("wall_climbing");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered wall_climbing");
			EntityActionAbility climbing = (EntityActionAbility) found;
			// the wall's north face, climbed facing south: 1.16 yRot 180 - NORTH.toYRot() = 0
			component.entityAim.setTarget(new ActionTarget(helper.absolutePos(wall), Direction.NORTH));

			click(helper, user, component, climbing);
			double onWall = user.getZ();
			helper.assertTrue(hamon.isHamonWallClimbing() && hamon.getWallClimbYRot(-1.0F) == 0.0F && onWall > away.z + 0.05D,
					"setup: the click did not move the user to the wall and start the climb: climbing "
							+ hamon.isHamonWallClimbing() + ", z " + away.z + " -> " + onWall);

			user.moveTo(away.x, away.y, away.z, 0.0F, 0.0F);
			click(helper, user, component, climbing);
			helper.assertTrue(hamon.isHamonWallClimbing() && Math.abs(user.getZ() - away.z) < 1.0E-6D,
					"1.16 did nothing for Wall Climbing clicked on the wall already being climbed: climbing "
							+ hamon.isHamonWallClimbing() + ", z " + away.z + " -> " + user.getZ());

			// a climb at another angle is restarted on the clicked wall
			hamon.startWallClimbing(user, 90.0F);
			click(helper, user, component, climbing);
			helper.assertTrue(hamon.isHamonWallClimbing() && hamon.getWallClimbYRot(-1.0F) == 0.0F
					&& Math.abs(user.getZ() - onWall) < 1.0E-6D, "1.16 moved a user climbing at another angle to the clicked"
							+ " wall and climbed it: yRot " + hamon.getWallClimbYRot(-1.0F) + ", z " + away.z + " -> " + user.getZ());
			helper.succeed();
		}
		finally {
			PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).ifPresent(hamon -> hamon.stopWallClimbing(user));
			component.entityAim.setTarget(ActionTarget.EMPTY);
			component.setAction(null, SyncType.NO_SYNC);
			user.discard();
		}
	}

	// set as the click sets it, then its first tick, in which 1.16 performed it
	private static void click(GameTestHelper helper, Player user, LivingComponentAction component, EntityActionAbility climbing) {
		EntityActionInstance action = climbing.initActionOnAbilityUse(helper.getLevel(), user, user, null);
		component.setAction(action, user, SyncType.NO_SYNC);
		user.tickCount++;
		component.tick();
		component.setAction(null, SyncType.NO_SYNC);
	}
}
