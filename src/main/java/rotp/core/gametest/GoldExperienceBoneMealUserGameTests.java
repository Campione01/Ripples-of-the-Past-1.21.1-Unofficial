package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GoldExperienceBoneMeal.standPerform: a baby animal ages for any Stand user, a block is bone-mealed only for a
 * player user, and as that player (HamonHealing.bonemealEffect passes it to BoneMealItem.applyBonemeal).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceBoneMealUserGameTests {
	private static final BlockPos USER = new BlockPos(1, 2, 1);
	private static final BlockPos GRASS = new BlockPos(1, 2, 4);

	private GoldExperienceBoneMealUserGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void boneMealGrowsNoBlockForAMobUserButAgesItsBabyAnimal(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "Could not create the zombie");
		zombie.setNoAi(true);
		run(helper, zombie, (scene, events) -> {
			helper.assertTrue(events.isEmpty() && scene.level.getBlockState(scene.grass).is(Blocks.GRASS_BLOCK),
					"1.16 GoldExperienceBoneMeal bone-meals a block only for a player user; a zombie's Gold Experience"
							+ " bone-mealed the grass block: " + describe(events));
			Cow calf = scene.calf();
			helper.assertTrue(scene.perform(new ActionTarget(calf)), "premise: the zombie's Bone Meal on the calf did not run");
			helper.assertTrue(calf.getAge() > -24000,
					"1.16 GoldExperienceBoneMeal ages a baby animal for any Stand user: the calf's age stayed " + calf.getAge());
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void boneMealGrowsABlockForAPlayerUserAsThatPlayer(GameTestHelper helper) {
		Player player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "GoldExperienceBoneMealUser"));
		run(helper, player, (scene, events) -> helper.assertTrue(events.size() == 1
				&& events.get(0).getPos().equals(scene.grass) && events.get(0).getPlayer() == player,
				"1.16 GoldExperienceBoneMeal bone-meals the block once, as the player using it: " + describe(events)));
	}

	private interface Check {
		void accept(Scene scene, List<BonemealEvent> events);
	}

	private static String describe(List<BonemealEvent> events) {
		return events.stream().map(event -> event.getPos() + " by " + event.getPlayer()).toList().toString();
	}

	private static void run(GameTestHelper helper, LivingEntity user, Check check) {
		Scene scene = new Scene(helper, user);
		List<BonemealEvent> events = new ArrayList<>();
		Consumer<BonemealEvent> listener = event -> {
			if (event.getPos().equals(scene.grass)) {
				events.add(event);
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BonemealEvent.class, listener);
		try {
			scene.setUp();
			helper.assertTrue(scene.perform(new ActionTarget(scene.grass, Direction.UP)),
					"premise: Bone Meal on the grass block did not run");
			check.accept(scene, events);
		}
		finally {
			NeoForge.EVENT_BUS.unregister(listener);
			scene.close();
		}
		helper.succeed();
	}

	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		final LivingEntity user;
		final BlockPos grass;
		StandType type;
		StandPower power;
		StandEntity stand;
		Cow calf;

		Scene(GameTestHelper helper, LivingEntity user) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.user = user;
			this.grass = helper.absolutePos(GRASS);
		}

		void setUp() {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(USER));
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Stand user");
			helper.setBlock(GRASS, Blocks.GRASS_BLOCK);
			helper.setBlock(GRASS.above(), Blocks.AIR);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
			helper.assertTrue(type != null, "Missing Gold Experience");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "Could not give and summon Gold Experience");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Gold Experience is missing");
			// beside the grass block, well within the Stand's reach, so the press keeps its target
			stand.moveTo(grass.getX() + 0.5D, grass.getY() + 1.0D, grass.getZ() - 0.5D, 0.0F, 0.0F);
			stand.setNoGravity(true);
		}

		Cow calf() {
			calf = EntityType.COW.create(level);
			helper.assertTrue(calf != null, "Could not create the calf");
			calf.setNoAi(true);
			calf.setNoGravity(true);
			calf.setBaby(true);
			calf.moveTo(grass.getX() + 1.5D, grass.getY() + 1.0D, grass.getZ() + 0.5D, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(calf) && calf.getAge() < 0, "Could not add the calf");
			return calf;
		}

		// the server side of a press with the user aiming at the target, run until the action ends
		boolean perform(ActionTarget target) {
			Ability ability = power.getAbility("bone_meal");
			helper.assertTrue(ability instanceof EntityActionAbility, "Missing Gold Experience's bone_meal");
			LivingComponentAction userActions = LivingComponentAction.getComponent(user);
			LivingComponentAction standActions = LivingComponentAction.getComponent(stand);
			userActions.entityAim.setTarget(target);
			power.setStamina(power.getMaxStamina());
			stand.summonLockTicks = 0;
			helper.assertTrue(ability.checkSpecificConditions(power).isPositive(),
					"premise: Bone Meal refused the press on " + target);
			((EntityActionAbility) ability).setOrBufferAction(level, user, stand, InputMethod.CLICK, null, 0.0F,
					BufferingState.clickOnly());
			EntityActionInstance action = standActions.getAction();
			boolean performed = false;
			for (int tick = 0; tick < 20 && action != null && standActions.getAction() == action; tick++) {
				standActions.tick();
				userActions.tick();
				performed |= action.getPhase() == ActionPhase.PERFORM || action.getPhase() == ActionPhase.RECOVERY;
			}
			standActions.setAction(null, user, SyncType.NO_SYNC);
			userActions.entityAim.setTarget(ActionTarget.EMPTY);
			return performed;
		}

		void close() {
			try {
				if (power != null && power.isSummoned() && type != null) {
					type.forceUnsummon(user, power);
				}
			}
			finally {
				if (calf != null) calf.discard();
				user.discard();
				helper.setBlock(GRASS.above(), Blocks.AIR);
				helper.setBlock(GRASS, Blocks.AIR);
			}
		}
	}
}
