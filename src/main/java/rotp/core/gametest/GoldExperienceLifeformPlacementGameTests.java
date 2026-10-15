package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.goldexperience.GEItemMarkEffect;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.itemtracking.ItemTracker;
import rotp.core.subsystems.itemtracking.ItemTracking;
import rotp.core.subsystems.itemtracking.KnownItemState;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 GoldExperienceCreateLifeform.perform, for a marked item that is not on the ground: one a holder carries comes to
 * life at the holder's feet, turned as the holder is but level (ENTITY_HAS_ITEM); an arrow or knife stuck in an entity
 * takes the holder's position and both its angles (STUCK_ARROW, STUCK_KNIFE); one in a block's inventory comes to life
 * on the block's corner one block up, unturned (BLOCK_HAS_ITEM).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceLifeformPlacementGameTests {
	private static final String BATCH = "ge_lifeform_placement";
	private static final short KEY = 24;

	private GoldExperienceLifeformPlacementGameTests() {}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformFromAnItemAHolderCarriesStandsAtTheHoldersFeetTurnedAsTheHolder(
			GameTestHelper helper) {
		run(helper, "carried", scene -> {
			Villager villager = scene.add(EntityType.VILLAGER.create(scene.level), 3.0D);
			villager.setYRot(90.0F);
			villager.setYBodyRot(90.0F);
			villager.setYHeadRot(90.0F);
			villager.setXRot(20.0F);
			ItemStack ingot = new ItemStack(Items.IRON_INGOT);
			villager.getInventory().setItem(0, ingot);
			ItemTracker tracker = scene.track(ingot);
			tracker.setAtEntity(ingot, villager.getId(), scene.level, KnownItemState.ENTITY_HAS_ITEM,
					id -> villager.getInventory().getItems().stream().anyMatch(ItemTracking.trackerIdCheck(id)));
			return new Expected(villager.position(), 90.0F, 0.0F);
		});
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformFromAStuckArrowStandsAtItsHolderTurnedAsTheHolder(GameTestHelper helper) {
		run(helper, "stuck arrow", scene -> {
			Pig pig = scene.add(EntityType.PIG.create(scene.level), 3.0D);
			pig.setYRot(90.0F);
			pig.setYBodyRot(90.0F);
			pig.setYHeadRot(90.0F);
			pig.setXRot(30.0F);
			pig.setArrowCount(1);
			ItemStack arrow = new ItemStack(Items.ARROW);
			ItemTracker tracker = scene.track(arrow);
			tracker.setAtEntity(arrow, pig.getId(), scene.level, KnownItemState.STUCK_ARROW, id -> true);
			return new Expected(pig.position(), 90.0F, 30.0F);
		});
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformFromAnItemInAChestStandsOnTheBlocksCornerOneUp(GameTestHelper helper) {
		run(helper, "chest", scene -> {
			BlockPos chestPos = BlockPos.containing(scene.origin.x, scene.origin.y, scene.origin.z + 3.0D);
			scene.level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
			scene.blocks.add(chestPos);
			helper.assertTrue(scene.level.getBlockEntity(chestPos) instanceof Container,
					"GE-PLACEMENT premise: the chest has no inventory");
			Container chest = (Container) scene.level.getBlockEntity(chestPos);
			ItemStack ingot = new ItemStack(Items.IRON_INGOT);
			chest.setItem(0, ingot);
			ItemTracker tracker = scene.track(ingot);
			tracker.setAtBlockPos(ingot, chestPos, scene.level, KnownItemState.BLOCK_HAS_ITEM, id -> true);
			return new Expected(new Vec3(chestPos.getX(), chestPos.getY() + 1, chestPos.getZ()), 0.0F, 0.0F);
		});
	}

	private record Expected(Vec3 pos, float yaw, float pitch) {}

	private static void run(GameTestHelper helper, String label, Function<Scene, Expected> setUp) {
		Scene scene = new Scene(helper);
		try {
			scene.open();
			Expected expected = setUp.apply(scene);
			Ability ability = scene.power.getAbility("create_lifeform");
			helper.assertTrue(ability != null, "GE-PLACEMENT premise: Gold Experience has no create_lifeform");
			AbilityInput.keyPress(KEY, ability, scene.user, null, InputMethod.CLICK, 0.0F, BufferingState.clickOnly(),
					ability.getAbilityId());
			AbilityInput.keyRelease(KEY, scene.user);
			List<GETransformationEntity> created = scene.level.getEntitiesOfClass(GETransformationEntity.class, scene.space);
			helper.assertTrue(created.size() == 1, "GE-PLACEMENT " + label + ": the press made " + created.size()
					+ " transformations, expected 1");
			GETransformationEntity transformation = created.get(0);
			double offset = transformation.position().distanceTo(expected.pos());
			helper.assertTrue(offset < 0.01D && Math.abs(transformation.getYRot() - expected.yaw()) < 0.01F
					&& Math.abs(transformation.getXRot() - expected.pitch()) < 0.01F,
					"GE-PLACEMENT " + label + ": the transformation stands at " + transformation.position() + " turned yaw="
							+ transformation.getYRot() + " pitch=" + transformation.getXRot() + "; 1.16 puts it at "
							+ expected.pos() + " turned yaw=" + expected.yaw() + " pitch=" + expected.pitch());
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	private static final class Scene {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final List<Entity> entities = new ArrayList<>();
		private final List<BlockPos> blocks = new ArrayList<>();
		private final List<ItemTracker> trackers = new ArrayList<>();
		private FakePlayer user;
		private StandType standType;
		private StandPower power;
		private Vec3 origin;
		private AABB space;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		private void open() {
			BlockPos template = helper.absolutePos(BlockPos.ZERO);
			ChunkPos chunk = new ChunkPos(template);
			origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 6.5D);
			space = new AABB(origin.x - 4, origin.y - 2, origin.z - 4, origin.x + 4, origin.y + 4, origin.z + 8);
			helper.assertTrue(level.getEntities((Entity) null, space).isEmpty()
					&& BlockPos.betweenClosedStream(space).allMatch(level::isEmptyBlock),
					"GE-PLACEMENT premise: the scene is not empty");
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
			helper.assertTrue(standType != null, "GE-PLACEMENT premise: missing Gold Experience");
			user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GoldExperiencePlacement"));
			user.setGameMode(GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "GE-PLACEMENT premise: could not add the user");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED && standType.summon(user, power),
					"GE-PLACEMENT premise: could not give and summon Gold Experience: " + inserted.status());
			power.getSummonedStandEntity().moveTo(origin.x + 1.0D, origin.y, origin.z, 0.0F, 0.0F);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			Cow seen = EntityType.COW.create(level);
			helper.assertTrue(seen != null, "GE-PLACEMENT premise: could not create the cow");
			seen.moveTo(origin.x, origin.y, origin.z - 3.0D, 0.0F, 0.0F);
			GoldExperienceLifeformState.get(user).learnLifeformsForEntity(seen, level);
			seen.discard();
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(GoldExperienceLifeformState.get(user).selectedLifeformSubtype(level).isPresent(),
					"GE-PLACEMENT premise: the user has no lifeform to create");
		}

		private <T extends Entity> T add(T entity, double ahead) {
			helper.assertTrue(entity != null, "GE-PLACEMENT premise: could not create the holder");
			entity.setNoGravity(true);
			if (entity instanceof net.minecraft.world.entity.Mob mob) {
				mob.setNoAi(true);
			}
			entity.moveTo(origin.x, origin.y, origin.z + ahead, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(entity), "GE-PLACEMENT premise: could not add the holder");
			entities.add(entity);
			return entity;
		}

		private ItemTracker track(ItemStack stack) {
			ItemTracker tracker = ItemTracking.getItemTracking(level).startTracking(stack, level);
			helper.assertTrue(tracker != null, "GE-PLACEMENT premise: could not track the marked item");
			trackers.add(tracker);
			GEItemMarkEffect mark = ModStandAbilities.EFFECT_GE_ITEM_MARK.get().create(level);
			mark.withItemTracker(tracker);
			power.userStandEffects.addEffect(mark);
			return tracker;
		}

		private void close() {
			try {
				if (user != null) {
					LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
				}
				if (power != null && power.isSummoned() && standType != null) {
					standType.forceUnsummon(user, power);
				}
			}
			finally {
				ItemTracking tracking = ItemTracking.getItemTracking(level);
				trackers.forEach(tracker -> tracking.stopTracking(tracker.trackerId, level));
				entities.forEach(Entity::discard);
				blocks.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
				if (space != null) {
					level.getEntitiesOfClass(Entity.class, space.inflate(4.0D), entity -> !(entity instanceof FakePlayer))
							.forEach(Entity::discard);
				}
				if (user != null) {
					user.discard();
				}
			}
		}
	}
}
