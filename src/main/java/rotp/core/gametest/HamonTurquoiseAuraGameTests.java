package rotp.core.gametest;

import java.util.LinkedHashMap;
import java.util.Map;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonTurquoiseBlueOverdriveAbility;
import rotp.core.impl.powers.hamon.entity.HamonTurquoiseBlueOverdriveEntity;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonAction.afterClick -> HamonData.setLastUsedAction, read by getThisTickAuraColor (:1441-1447): after
 * Turquoise Blue Overdrive the aura is blue until the energy is 0, wherever the user's eyes are. The technique is
 * over within its first tick, so the aura tick never sees it running.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonTurquoiseAuraGameTests {
	private HamonTurquoiseAuraGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void auraStaysBlueAfterTurquoiseUntilTheEnergyRunsOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos feet = helper.absolutePos(new BlockPos(2, 3, 2));
		Map<BlockPos, BlockState> original = new LinkedHashMap<>();
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		LivingComponentAction component = null;
		try {
			// one cell of water around the feet, walled in so that nothing flows; the eyes stay in the air above it
			original.put(feet.below(), level.getBlockState(feet.below()));
			for (Direction side : Direction.Plane.HORIZONTAL) {
				original.put(feet.relative(side), level.getBlockState(feet.relative(side)));
			}
			original.keySet().forEach(pos -> level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState()));
			original.put(feet, level.getBlockState(feet));
			level.setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());

			user.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the aura test player");
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
			hamon.learnSkill(ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get());
			hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			user.baseTick();
			helper.assertTrue(user.isInWaterOrBubble() && !user.isEyeInFluid(FluidTags.WATER) && hamon.getEnergy() > 1000.0F,
					"Fixture: the user stands in water with dry eyes and more energy than the technique costs: inWater="
							+ user.isInWaterOrBubble() + " eyesInWater=" + user.isEyeInFluid(FluidTags.WATER)
							+ " energy=" + hamon.getEnergy());
			check(helper, "ORANGE", hamon.auraColorNameThisTick(user), "before the technique, eyes out of the water");

			Ability found = power.getAbility("turquoise_blue_overdrive");
			helper.assertTrue(found instanceof HamonTurquoiseBlueOverdriveAbility, "Missing registered turquoise_blue_overdrive");
			HamonTurquoiseBlueOverdriveAbility ability = (HamonTurquoiseBlueOverdriveAbility) found;
			helper.assertTrue(ability.checkSpecificConditions(power).isPositive(), "Fixture: Turquoise Blue Overdrive is refused");
			component = LivingComponentAction.getComponent(user);
			EntityActionInstance action = ability.initActionOnAbilityUse(level, user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			// the action component ticks before the power data, so the aura tick first runs after this
			user.tickCount++;
			component.tick();
			helper.assertTrue(action.isOver() && component.getAction() == null,
					"Fixture: Turquoise Blue Overdrive is over within its first tick, phase=" + action.getPhase());
			helper.assertTrue(!level.getEntitiesOfClass(HamonTurquoiseBlueOverdriveEntity.class, user.getBoundingBox().inflate(8.0D)).isEmpty()
					&& hamon.getEnergy() > 0.0F, "Fixture: the technique was performed and left energy, energy=" + hamon.getEnergy());

			check(helper, "BLUE", hamon.auraColorNameThisTick(user), "after Turquoise Blue Overdrive, eyes out of the water, energy "
					+ hamon.getEnergy());
			check(helper, "BLUE", hamon.auraColorNameThisTick(user), "a tick later");
			hamon.setEnergy(0.0F);
			check(helper, "ORANGE", hamon.auraColorNameThisTick(user), "with the energy at 0");
			hamon.setEnergy(hamon.getMaxEnergy());
			check(helper, "ORANGE", hamon.auraColorNameThisTick(user), "with new energy after it ran out");
			helper.succeed();
		}
		finally {
			if (component != null) {
				component.setAction(null, SyncType.NO_SYNC);
			}
			level.getEntitiesOfClass(HamonTurquoiseBlueOverdriveEntity.class, user.getBoundingBox().inflate(16.0D))
					.forEach(HamonTurquoiseBlueOverdriveEntity::discard);
			user.discard();
			if (original.containsKey(feet)) {
				level.setBlockAndUpdate(feet, original.get(feet));
			}
			original.forEach(level::setBlockAndUpdate);
		}
	}

	private static void check(GameTestHelper helper, String expected, String actual, String state) {
		helper.assertTrue(expected.equals(actual), "1.16 aura colour " + state + ": expected " + expected + ", got " + actual);
	}
}
