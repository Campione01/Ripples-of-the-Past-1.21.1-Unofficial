package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonZoomPunchEntity;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ZoomPunchEntity.onRemovedFromWorld (:105-111): the Zoom Punch's cooldown, its cooldown(14, 0) over which the punch
 * flew out and back (HamonZoomPunch :28), ended at once when the punch left the world before its lifespan, as one that
 * hit a wall and came back early does; a punch that flew its whole lifespan left the cooldown to run out.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonZoomPunchCooldown116GameTests {
	private static final String ZOOM_PUNCH = "zoom_punch";

	private HamonZoomPunchCooldown116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theZoomPunchCooldownEndsWhenItsPunchIsBackEarlyAsIn116(GameTestHelper helper) {
		BlockPos wall = new BlockPos(1, 2, 3);
		helper.setBlock(wall, Blocks.STONE.defaultBlockState());
		helper.setBlock(wall.above(), Blocks.STONE.defaultBlockState());
		for (boolean atTheWall : new boolean[] { true, false }) {
			Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
			// south at the wall two blocks ahead, or up into the open air for a whole flight
			user.moveTo(at.x, at.y, at.z, 0.0F, atTheWall ? 0.0F : -90.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Zoom Punch user");
			LivingComponentAction component = LivingComponentAction.getComponent(user);
			HamonZoomPunchEntity punch = null;
			String row = atTheWall ? "punched at a wall two blocks ahead" : "punched into the open air";
			try {
				PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
				power.setPowerType(ModPlayerPowers.HAMON.get());
				HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
				hamon.learnSkill(ModHamonSkills.ZOOM_PUNCH.get());
				hamon.setBreathStability(hamon.getMaxBreathStability());
				hamon.setEnergy(hamon.getMaxEnergy());
				Ability found = power.getAbility(ZOOM_PUNCH);
				helper.assertTrue(found instanceof EntityActionAbility, "Missing registered zoom_punch");
				EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
				helper.assertTrue(action != null, "setup (" + row + "): the Zoom Punch did not start");
				component.setAction(action, user, SyncType.NO_SYNC);
				user.tickCount++;
				component.tick();
				List<HamonZoomPunchEntity> punches = helper.getLevel().getEntitiesOfClass(HamonZoomPunchEntity.class,
						user.getBoundingBox().inflate(16.0D), entity -> entity.getOwner() == user);
				helper.assertTrue(punches.size() == 1 && hamon.getAbilityCooldown(ZOOM_PUNCH) == 14, "setup (" + row
						+ "): the Zoom Punch threw no punch or set no 14-tick cooldown: punches " + punches.size()
						+ ", cooldown " + hamon.getAbilityCooldown(ZOOM_PUNCH));
				punch = punches.getFirst();
				// the user's power is not ticked, so the cooldown stays where the punch leaves it
				for (int tick = 0; tick < 30 && !punch.isRemoved(); tick++) {
					helper.getLevel().tickNonPassenger(punch);
				}
				int ticks = punch.tickCount;
				helper.assertTrue(punch.isRemoved() && (atTheWall ? ticks < punch.ticksLifespan() : ticks >= punch.ticksLifespan()),
						"setup (" + row + "): the punch was not back " + (atTheWall ? "before" : "at the end of") + " its "
								+ punch.ticksLifespan() + "-tick lifespan: removed " + punch.isRemoved() + " at tick " + ticks);
				int cooldown = hamon.getAbilityCooldown(ZOOM_PUNCH);
				helper.assertTrue(atTheWall ? cooldown == 0 : cooldown == 14, atTheWall
						? "1.16 ended the Zoom Punch's cooldown as its punch, back from the wall, left the world at tick " + ticks
								+ ": cooldown " + cooldown
						: "1.16 left the Zoom Punch's cooldown to run out after a punch that flew its whole lifespan: cooldown "
								+ cooldown);
			}
			finally {
				if (punch != null && !punch.isRemoved()) {
					punch.discard();
				}
				component.setAction(null, SyncType.NO_SYNC);
				user.discard();
			}
		}
		helper.succeed();
	}
}
