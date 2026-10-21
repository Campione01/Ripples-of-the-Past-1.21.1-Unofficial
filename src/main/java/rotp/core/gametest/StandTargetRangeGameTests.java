package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.goldexperience.GEGrowthSparkles;
import rotp.core.impl.stands.goldexperience.GEItemMarkEffect;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.impl.stands.goldexperience.GoldExperienceCreateLifeformAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.impl.stands.goldexperience.GoldExperienceMarkItemAbility;
import rotp.core.impl.stands.hierophant.HGBarrierEntity;
import rotp.core.impl.stands.hierophant.HierophantBarrierAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModGamerules;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.itemtracking.ItemTracker;
import rotp.core.subsystems.itemtracking.ItemTracking;
import rotp.core.subsystems.itemtracking.KnownItemState;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.reflection.CommonReflection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 Action.checkRangeAndTarget measures a Stand action's target from its performer, and StandAction.getPerformer is
 * the Stand whenever it is out: 8 blocks to an entity (4 without a line of sight from the performer), 10 to a block,
 * each as JojoModUtil.getDistance. A target beyond that is refused with target_too_far.
 * The Create Lifeform tests also cover what 1.16 does around that check for this action: which target it takes
 * (PowerBaseImpl.checkTarget) and which material it then turns into the lifeform.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandTargetRangeGameTests {
	private static final String BATCH = "stand_target_range";
	private static final String TOO_FAR = "jojo.message.action_condition.target_too_far";
	private static final String GE_MATERIAL = "jojo.message.action_condition.ge_lifeform_material";
	private static final String HEAL_TARGET = "jojo.message.action_condition.heal_target";
	private static final String ITEM_OFFHAND = "jojo.message.action_condition.item_offhand";
	private static final String BLOCK_TARGET = "jojo.message.action_condition.block_target";
	private static final String GE_MATERIAL_ONLY_ITEM = "jojo.message.action_condition.ge_lifeform_material_only_item";
	private static final String GE_MATERIAL_ITEM = "jojo.message.action_condition.ge_lifeform_material_item";
	private static final String GE_MATERIAL_BLOCK = "jojo.message.action_condition.ge_lifeform_material_block";
	private static final short KEY = 23;
	// StandEntityPunchAbility.clientStandAim(StandEntity, LivingEntity, Entity, ActionTarget) as a class file names it
	private static final String CLIENT_STAND_AIM = "(Lrotp/core/powersystem/standpower/entity/StandEntity;"
			+ "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;"
			+ "Lrotp/core/subsystems/target/ActionTarget;)Lrotp/core/subsystems/target/ActionTarget;";

	private StandTargetRangeGameTests() {}

	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondHealReachesAnEntityNearTheDistantStand(GameTestHelper helper) {
		entityNearTheStand(helper, Kind.CD_HEAL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondHealDoesNotReachBeyondAWallPastTheQuarteredRange(GameTestHelper helper) {
		entityBehindAWall(helper, Kind.CD_HEAL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceHealOtherReachesAnEntityNearTheDistantStand(GameTestHelper helper) {
		entityNearTheStand(helper, Kind.GE_HEAL_OTHER);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceHealOtherDoesNotReachBeyondAWallPastTheQuarteredRange(GameTestHelper helper) {
		entityBehindAWall(helper, Kind.GE_HEAL_OTHER);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeshotReachesAnEntityNearTheDistantStand(GameTestHelper helper) {
		entityNearTheStand(helper, Kind.GE_LIFESHOT);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeshotDoesNotReachBeyondAWallPastTheQuarteredRange(GameTestHelper helper) {
		entityBehindAWall(helper, Kind.GE_LIFESHOT);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceHealOtherServerRayDoesNotReachAnEntityFarFromTheStand(GameTestHelper helper) {
		serverRayEntityFarFromTheStand(helper, Kind.GE_HEAL_OTHER);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeshotServerRayDoesNotReachAnEntityFarFromTheStand(GameTestHelper helper) {
		serverRayEntityFarFromTheStand(helper, Kind.GE_LIFESHOT);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceHealOtherTakesNoEntityPastThePrecisionRay(GameTestHelper helper) {
		precisionRayEntityPastTheUsersReach(helper, Kind.GE_HEAL_OTHER);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeshotTakesNoEntityPastThePrecisionRay(GameTestHelper helper) {
		precisionRayEntityPastTheUsersReach(helper, Kind.GE_LIFESHOT);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealTakesNoBlockPastThePrecisionRay(GameTestHelper helper) {
		precisionRayBlockPastTheUsersReach(helper, Kind.GE_BONE_MEAL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondAnchorReachesABlockNearTheDistantStand(GameTestHelper helper) {
		blockNearTheStand(helper, Kind.CD_ANCHOR);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondAnchorDoesNotReachABlockFarFromTheStand(GameTestHelper helper) {
		blockFarFromTheStand(helper, Kind.CD_ANCHOR);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformReachesABlockNearTheDistantStand(GameTestHelper helper) {
		blockNearTheStand(helper, Kind.GE_CREATE_LIFEFORM);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformDoesNotReachABlockFarFromTheStand(GameTestHelper helper) {
		// 1.16 GoldExperienceCreateLifeform has TargetRequirement.NONE: PowerBaseImpl.checkTarget drops a target out of
		// range and carries on, so the action falls back to the off-hand item instead of refusing with target_too_far.
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			Cow seen = fixture.cow(-3.0D);
			GoldExperienceLifeformState.get(fixture.user).learnLifeformsForEntity(seen, fixture.level);
			seen.discard();
			fixture.power.setStamina(fixture.power.getMaxStamina());
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			String near = fixture.refusal();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z - 14.0D, 0.0F, 0.0F);
			double fromUser = Math.sqrt(new AABB(pos).distanceToSqr(fixture.user.getEyePosition()));
			double fromStand = Math.sqrt(new AABB(pos).distanceToSqr(fixture.stand.getEyePosition()));
			helper.assertTrue(fromUser < 5.0D && fromStand > 11.0D && "accepted".equals(near)
					&& fixture.user.getOffhandItem().isEmpty()
					&& GoldExperienceLifeformState.get(fixture.user).selectedLifeformSubtype(fixture.level).isPresent(),
					"STAND-RANGE premise: the block is not near the user only, was not accepted as the source with the Stand"
							+ " beside it, or the user has an off-hand item or no lifeform to create: fromUser=" + fromUser
							+ " fromStand=" + fromStand + " standBeside=" + near);

			String emptyHand = fixture.refusal();
			helper.assertTrue(GE_MATERIAL.equals(emptyHand), "GE_CREATE_LIFEFORM on a block " + fromStand
					+ " blocks from the Stand with an empty off hand: answered " + emptyHand
					+ "; 1.16 drops the target out of range and asks for a material with " + GE_MATERIAL);

			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			String withItem = fixture.refusal();
			helper.assertTrue("accepted".equals(withItem), "GE_CREATE_LIFEFORM on a block " + fromStand
					+ " blocks from the Stand with iron ingots in the off hand: answered " + withItem
					+ "; 1.16 drops the target out of range and creates the lifeform from the off-hand item");

			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.level.getEntitiesOfClass(GETransformationEntity.class, fixture.space);
			double fromUserToCreated = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(fixture.user.position());
			helper.assertTrue(admitted && created.size() == 1 && fromUserToCreated < 3.0D
					&& fixture.user.getOffhandItem().is(Items.IRON_INGOT) && fixture.user.getOffhandItem().getCount() == 1
					&& fixture.level.getBlockState(pos).is(Blocks.STONE),
					"GE_CREATE_LIFEFORM press with the aimed block " + fromStand + " blocks from the Stand: admitted=" + admitted
							+ " transformations=" + created.size() + " fromUser=" + fromUserToCreated + " offHand="
							+ fixture.user.getOffhandItem() + " block=" + fixture.level.getBlockState(pos)
							+ "; 1.16 turns one off-hand item into the lifeform in front of the user and leaves the block");
		}
		helper.succeed();
	}

	// 1.16 PowerBaseImpl.checkTarget: the entity under the crosshair that is out of the Stand's range becomes no target.
	// Nothing picks the block behind it instead, even though that block is within the Stand's 10 blocks.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformDoesNotTakeTheBlockBehindAnEntityFarFromTheStand(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			Cow seen = fixture.cow(-3.0D);
			GoldExperienceLifeformState.get(fixture.user).learnLifeformsForEntity(seen, fixture.level);
			seen.discard();
			fixture.power.setStamina(fixture.power.getMaxStamina());
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 5.0D));
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 14.0D, 0.0F, 0.0F);
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String blockAimed = fixture.refusal();
			Cow cow = fixture.cow(3.0D);
			fixture.aim(new ActionTarget(cow));
			boolean cowInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, cow, 64.0D);
			boolean blockInRange = ActionTargetRange.isBlockWithinRange(fixture.stand, fixture.level, pos, 100.0D);
			helper.assertTrue(!cowInRange && blockInRange && "accepted".equals(blockAimed)
					&& fixture.user.getOffhandItem().isEmpty()
					&& GoldExperienceLifeformState.get(fixture.user).selectedLifeformSubtype(fixture.level).isPresent(),
					"STAND-RANGE premise: the cow is not out of the Stand's 8 blocks with the block behind it inside the Stand's"
							+ " 10, the block was not accepted as the source with the crosshair on it, or the user has an"
							+ " off-hand item or no lifeform to create: cowInRange=" + cowInRange + " blockInRange=" + blockInRange
							+ " blockAimed=" + blockAimed);

			String emptyHand = fixture.refusal();
			helper.assertTrue(GE_MATERIAL.equals(emptyHand), "GE_CREATE_LIFEFORM on an entity out of the Stand's range with a"
					+ " usable block behind it and an empty off hand: answered " + emptyHand
					+ "; 1.16 drops the target out of range, picks no other one and asks for a material with " + GE_MATERIAL);

			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.level.getEntitiesOfClass(GETransformationEntity.class, fixture.space);
			double fromUserToCreated = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(fixture.user.position());
			helper.assertTrue(admitted && created.size() == 1 && fromUserToCreated < 3.0D
					&& fixture.user.getOffhandItem().is(Items.IRON_INGOT) && fixture.user.getOffhandItem().getCount() == 1
					&& fixture.level.getBlockState(pos).is(Blocks.STONE) && cow.isAlive(),
					"GE_CREATE_LIFEFORM press on an entity out of the Stand's range with a usable block behind it: admitted="
							+ admitted + " transformations=" + created.size() + " fromUser=" + fromUserToCreated + " offHand="
							+ fixture.user.getOffhandItem() + " block=" + fixture.level.getBlockState(pos) + " cowAlive="
							+ cow.isAlive() + "; 1.16 turns one off-hand item into the lifeform in front of the user and leaves"
							+ " the block and the entity");
		}
		helper.succeed();
	}

	// 1.16 GoldExperienceCreateLifeform.overrideVanillaMouseTarget: an item entity on the look ray replaces the target
	// under the crosshair before the range is checked, so it is still the source when that target was out of range.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesAnItemEntityOnTheRayWhenTheAimedEntityIsFarFromTheStand(
			GameTestHelper helper) {
		ItemEntity dropped = null;
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			Cow seen = fixture.cow(-3.0D);
			GoldExperienceLifeformState.get(fixture.user).learnLifeformsForEntity(seen, fixture.level);
			seen.discard();
			fixture.power.setStamina(fixture.power.getMaxStamina());
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 13.0D, 0.0F, 0.0F);
			Cow cow = fixture.cow(2.5D);
			fixture.aim(new ActionTarget(cow));
			dropped = new ItemEntity(fixture.level, fixture.origin.x, fixture.user.getEyeY() - 0.125D, fixture.origin.z + 6.0D,
					new ItemStack(Items.GOLD_INGOT, 3), 0.0D, 0.0D, 0.0D);
			dropped.setNoGravity(true);
			helper.assertTrue(fixture.level.addFreshEntity(dropped), "STAND-RANGE premise: could not add the item entity");
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			boolean cowInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, cow, 64.0D);
			boolean itemInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, dropped, 64.0D);
			helper.assertTrue(!cowInRange && itemInRange
					&& GoldExperienceLifeformState.get(fixture.user).selectedLifeformSubtype(fixture.level).isPresent(),
					"STAND-RANGE premise: the cow is not out of the Stand's 8 blocks with the item entity inside them, or the"
							+ " user has no lifeform to create: cowInRange=" + cowInRange + " itemInRange=" + itemInRange);

			Vec3 itemPos = dropped.position();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.level.getEntitiesOfClass(GETransformationEntity.class, fixture.space);
			double fromItemToCreated = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(itemPos);
			int left = dropped.isAlive() ? dropped.getItem().getCount() : 0;
			helper.assertTrue(admitted && created.size() == 1 && fromItemToCreated >= 0.0D && fromItemToCreated < 0.5D
					&& left == 0 && fixture.user.getOffhandItem().getCount() == 2 && cow.isAlive(),
					"GE_CREATE_LIFEFORM press on an entity out of the Stand's range with an item entity of 3 gold ingots on the"
							+ " look ray inside it: admitted=" + admitted + " transformations=" + created.size() + " fromItem="
							+ fromItemToCreated + " itemsLeft=" + left + " offHand=" + fixture.user.getOffhandItem()
							+ " cowAlive=" + cow.isAlive() + "; 1.16 mobFromEntity takes the whole item entity as the source"
							+ " where it lies and leaves the off hand");

			// 1.16 keeps a clone of the item entity and puts it back whole when the lifeform is reverted
			GETransformationEntity transformation = created.get(0);
			transformation.turnBackIntoSource(fixture.user);
			for (int tick = 0; tick < 20 && transformation.isAlive(); tick++) {
				transformation.tick();
			}
			List<ItemEntity> restored = fixture.level.getEntitiesOfClass(ItemEntity.class, fixture.space, Entity::isAlive);
			helper.assertTrue(!transformation.isAlive() && restored.size() == 1 && restored.get(0).getItem().is(Items.GOLD_INGOT)
					&& restored.get(0).getItem().getCount() == 3,
					"GE_CREATE_LIFEFORM reverted after taking an item entity of 3 gold ingots: transformationLeft="
							+ transformation.isAlive() + " item entities=" + restored.stream().map(ItemEntity::getItem).toList()
							+ "; 1.16 restores the item entity with its whole stack");
		}
		finally {
			if (dropped != null) dropped.discard();
		}
		helper.succeed();
	}

	// 1.16 PowerBaseImpl.checkTarget runs GoldExperienceCreateLifeform.overrideVanillaMouseTarget on every use, before
	// the range check: an item entity on the look ray replaces the block under the crosshair, which is left alone.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesTheItemEntityInFrontOfTheAimedBlock(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String blockAlone = fixture.refusal();
			ItemEntity dropped = fixture.item(2.5D, new ItemStack(Items.GOLD_INGOT));
			boolean itemInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, dropped, 64.0D);
			helper.assertTrue("accepted".equals(blockAlone) && itemInRange && fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: the aimed block alone was not accepted as the source, the item entity in front of it"
							+ " is out of the Stand's 8 blocks, or the user has an off-hand item: blockAlone=" + blockAlone
							+ " itemInRange=" + itemInRange);

			Vec3 itemPos = dropped.position();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromItem = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(itemPos);
			helper.assertTrue(admitted && created.size() == 1 && fromItem >= 0.0D && fromItem < 0.5D && !dropped.isAlive()
					&& fixture.level.getBlockState(pos).is(Blocks.STONE),
					"GE_CREATE_LIFEFORM press on a usable block in range with an item entity on the look ray in front of it:"
							+ " admitted=" + admitted + " transformations=" + created.size() + " fromItem=" + fromItem
							+ " itemEntityLeft=" + dropped.isAlive() + " block=" + fixture.level.getBlockState(pos)
							+ "; 1.16 replaces the block under the crosshair with the item entity, turns that into the"
							+ " lifeform where it lies and leaves the block");
		}
		helper.succeed();
	}

	// The donor's item ray is as long as the block range, 10 blocks, though an entity target reaches 8: an item entity
	// 9 blocks ahead of the user is picked, and then measured from the Stand like any entity.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformReachesAnItemEntityNineBlocksAlongTheLookRay(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 4.0D, 0.0F, 0.0F);
			fixture.aim(ActionTarget.EMPTY);
			ItemEntity dropped = fixture.item(9.0D, new ItemStack(Items.GOLD_INGOT));
			double fromEyes = Math.sqrt(dropped.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
			boolean itemInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, dropped, 64.0D);
			helper.assertTrue(fromEyes > 8.5D && fromEyes < 9.5D && itemInRange && fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: the item entity is not between 8 and 10 blocks from the user's eyes on the look ray"
							+ " and within the Stand's 8 blocks, or the user has an off-hand item: fromEyes=" + fromEyes
							+ " itemInRange=" + itemInRange);

			String refusal = fixture.refusal();
			Vec3 itemPos = dropped.position();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromItem = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(itemPos);
			helper.assertTrue("accepted".equals(refusal) && admitted && created.size() == 1 && fromItem >= 0.0D
					&& fromItem < 0.5D && !dropped.isAlive(),
					"GE_CREATE_LIFEFORM with an item entity " + fromEyes + " blocks along the look ray, inside the Stand's"
							+ " range, and an empty off hand: answered " + refusal + " admitted=" + admitted
							+ " transformations=" + created.size() + " fromItem=" + fromItem + " itemEntityLeft="
							+ dropped.isAlive() + "; the 1.16 item ray is 10 blocks long and turns that item entity into"
							+ " the lifeform");
		}
		helper.succeed();
	}

	// The donor's item ray is JojoModUtil.rayTraceMultipleEntities, whose entity pass does not look at blocks: an item
	// entity behind the block under the crosshair replaces it. Out of the Stand's sight it must be within 4 blocks.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesAnItemEntityBehindTheAimedBlock(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 3.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String blockAlone = fixture.refusal();
			ItemEntity dropped = fixture.item(4.5D, new ItemStack(Items.GOLD_INGOT));
			boolean seen = fixture.stand.hasLineOfSight(dropped);
			boolean itemInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, dropped, 64.0D);
			helper.assertTrue("accepted".equals(blockAlone) && !seen && itemInRange && fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: the aimed block alone was not accepted as the source, the item entity behind it is"
							+ " in the Stand's sight or beyond the quartered range, or the user has an off-hand item: blockAlone="
							+ blockAlone + " seen=" + seen + " itemInRange=" + itemInRange);

			Vec3 itemPos = dropped.position();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromItem = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(itemPos);
			helper.assertTrue(admitted && created.size() == 1 && fromItem >= 0.0D && fromItem < 0.5D && !dropped.isAlive()
					&& fixture.level.getBlockState(pos).is(Blocks.STONE),
					"GE_CREATE_LIFEFORM press on a usable block in range with an item entity on the look ray behind it, within"
							+ " 4 blocks of the Stand: admitted=" + admitted + " transformations=" + created.size()
							+ " fromItem=" + fromItem + " itemEntityLeft=" + dropped.isAlive() + " block="
							+ fixture.level.getBlockState(pos) + "; the 1.16 item ray passes through blocks, so the item entity"
							+ " replaces the block under the crosshair and becomes the lifeform");
		}
		helper.succeed();
	}

	// The replacement comes before the range check and is not undone: an item entity on the ray that is out of the
	// Stand's range leaves no target at all, not the block that was under the crosshair.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformDropsTheAimedBlockForAnItemEntityOutOfRangeBehindIt(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 3.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String blockAlone = fixture.refusal();
			ItemEntity dropped = fixture.item(8.0D, new ItemStack(Items.GOLD_INGOT, 3));
			double fromEyes = Math.sqrt(dropped.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
			boolean seen = fixture.stand.hasLineOfSight(dropped);
			boolean itemInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, dropped, 64.0D);
			helper.assertTrue("accepted".equals(blockAlone) && fromEyes < 9.5D && !seen && !itemInRange
					&& fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: the aimed block alone was not accepted as the source, the item entity behind it is"
							+ " not within the 10 blocks of the item ray, out of the Stand's sight and beyond the quartered"
							+ " range, or the user has an off-hand item: blockAlone=" + blockAlone + " fromEyes=" + fromEyes
							+ " seen=" + seen + " itemInRange=" + itemInRange);

			String emptyHand = fixture.refusal();
			helper.assertTrue(GE_MATERIAL.equals(emptyHand), "GE_CREATE_LIFEFORM on a usable block in range with an item"
					+ " entity on the look ray behind it, out of the Stand's range, and an empty off hand: answered "
					+ emptyHand + "; 1.16 replaces the block with the item entity, drops that for its range and asks for a"
					+ " material with " + GE_MATERIAL);

			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromUserToCreated = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(fixture.user.position());
			int left = dropped.isAlive() ? dropped.getItem().getCount() : 0;
			helper.assertTrue(admitted && created.size() == 1 && fromUserToCreated >= 0.0D && fromUserToCreated < 3.0D
					&& fixture.user.getOffhandItem().is(Items.IRON_INGOT) && fixture.user.getOffhandItem().getCount() == 1
					&& fixture.level.getBlockState(pos).is(Blocks.STONE) && left == 3,
					"GE_CREATE_LIFEFORM press with that item entity out of range and iron ingots in the off hand: admitted="
							+ admitted + " transformations=" + created.size() + " fromUser=" + fromUserToCreated + " offHand="
							+ fixture.user.getOffhandItem() + " block=" + fixture.level.getBlockState(pos) + " itemsLeft=" + left
							+ "; 1.16 turns one off-hand item into the lifeform in front of the user and leaves the block and"
							+ " the item entity");
		}
		helper.succeed();
	}

	// 1.16 GoldExperienceCreateLifeform.perform: the marked item, the targeted entity, the item in the off hand, and
	// only then the targeted block.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesTheOffHandItemBeforeTheAimedBlock(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String blockAlone = fixture.refusal();
			helper.assertTrue("accepted".equals(blockAlone), "STAND-RANGE premise: the aimed block alone was not accepted as"
					+ " the source: " + blockAlone);

			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromUserToCreated = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(fixture.user.position());
			helper.assertTrue(admitted && created.size() == 1 && fromUserToCreated >= 0.0D && fromUserToCreated < 3.0D
					&& fixture.user.getOffhandItem().is(Items.IRON_INGOT) && fixture.user.getOffhandItem().getCount() == 1
					&& fixture.level.getBlockState(pos).is(Blocks.STONE),
					"GE_CREATE_LIFEFORM press on a usable block in range with iron ingots in the off hand: admitted=" + admitted
							+ " transformations=" + created.size() + " fromUser=" + fromUserToCreated + " offHand="
							+ fixture.user.getOffhandItem() + " block=" + fixture.level.getBlockState(pos)
							+ "; 1.16 takes the off-hand item before the targeted block: one ingot becomes the lifeform in"
							+ " front of the user and the block stays");
		}
		helper.succeed();
	}

	// The targeted block is still the source when the off-hand item cannot be given life.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesTheAimedBlockWhenTheOffHandItemIsAlive(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			ItemStack alive = new ItemStack(Items.OAK_SAPLING, 2);
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, alive);
			helper.assertTrue(!GoldExperienceCreateLifeformAbility.canGiveLifeTo(alive),
					"STAND-RANGE premise: an oak sapling can be given life");

			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromBlock = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(Vec3.atBottomCenterOf(pos));
			helper.assertTrue(admitted && created.size() == 1 && fromBlock >= 0.0D && fromBlock < 0.5D
					&& fixture.user.getOffhandItem().is(Items.OAK_SAPLING) && fixture.user.getOffhandItem().getCount() == 2
					&& fixture.level.getBlockState(pos).isAir(),
					"GE_CREATE_LIFEFORM press on a usable block in range with oak saplings in the off hand: admitted=" + admitted
							+ " transformations=" + created.size() + " fromBlock=" + fromBlock + " offHand="
							+ fixture.user.getOffhandItem() + " block=" + fixture.level.getBlockState(pos)
							+ "; 1.16 cannot give life to the living off-hand item and turns the targeted block into the"
							+ " lifeform");
		}
		helper.succeed();
	}

	// The targeted entity comes before the off-hand item.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesTheAimedBoatBeforeTheOffHandItem(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			Boat boat = fixture.boat(2.5D);
			fixture.aim(new ActionTarget(boat));
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			boolean boatInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, boat, 64.0D);
			helper.assertTrue(boatInRange, "STAND-RANGE premise: the boat is out of the Stand's 8 blocks");

			Vec3 boatPos = boat.position();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromBoat = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(boatPos);
			helper.assertTrue(admitted && created.size() == 1 && fromBoat >= 0.0D && fromBoat < 0.5D && !boat.isAlive()
					&& fixture.user.getOffhandItem().is(Items.IRON_INGOT) && fixture.user.getOffhandItem().getCount() == 2,
					"GE_CREATE_LIFEFORM press on a boat in range with iron ingots in the off hand: admitted=" + admitted
							+ " transformations=" + created.size() + " fromBoat=" + fromBoat + " boatLeft=" + boat.isAlive()
							+ " offHand=" + fixture.user.getOffhandItem() + "; 1.16 takes the targeted entity before the"
							+ " off-hand item: the boat becomes the lifeform where it is and the ingots stay");
		}
		helper.succeed();
	}

	// 1.16 GoldExperienceCreateLifeform.checkTarget refuses a mob, and PowerBaseImpl.checkTarget then goes on without a
	// target (TargetRequirement.NONE): the press is not refused, the off-hand item is the source.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTreatsAnAimedMobAsNoTarget(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			Cow cow = fixture.cow(2.5D);
			fixture.aim(new ActionTarget(cow));
			boolean cowInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, cow, 64.0D);
			helper.assertTrue(cowInRange && fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: the cow is out of the Stand's 8 blocks, or the user has an off-hand item: cowInRange="
							+ cowInRange);

			String emptyHand = fixture.refusal();
			helper.assertTrue(GE_MATERIAL.equals(emptyHand), "GE_CREATE_LIFEFORM on a mob in range with an empty off hand:"
					+ " answered " + emptyHand + "; 1.16 treats a target that cannot be the source as no target and asks for"
					+ " a material with " + GE_MATERIAL);

			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			String withItem = fixture.refusal();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromUserToCreated = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(fixture.user.position());
			helper.assertTrue("accepted".equals(withItem) && admitted && created.size() == 1 && fromUserToCreated >= 0.0D
					&& fromUserToCreated < 3.0D && fixture.user.getOffhandItem().is(Items.IRON_INGOT)
					&& fixture.user.getOffhandItem().getCount() == 1 && cow.isAlive(),
					"GE_CREATE_LIFEFORM press on a mob in range with iron ingots in the off hand: answered " + withItem
							+ " admitted=" + admitted + " transformations=" + created.size() + " fromUser=" + fromUserToCreated
							+ " offHand=" + fixture.user.getOffhandItem() + " cowAlive=" + cow.isAlive()
							+ "; 1.16 treats the mob as no target and turns one off-hand item into the lifeform in front of"
							+ " the user");
		}
		helper.succeed();
	}

	// 1.16 checkTarget refuses an item entity whose item is alive (ge_lifeform_material_item), which again leaves no
	// target: neither that item entity nor the block behind it is the source.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTreatsALivingItemEntityAsNoTarget(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			fixture.aim(ActionTarget.EMPTY);
			ItemEntity dropped = fixture.item(2.5D, new ItemStack(Items.OAK_SAPLING, 2));
			boolean itemInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, dropped, 64.0D);
			helper.assertTrue(!GoldExperienceCreateLifeformAbility.canGiveLifeTo(dropped.getItem()) && itemInRange
					&& fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: an oak sapling can be given life, the item entity is out of the Stand's 8 blocks, or"
							+ " the user has an off-hand item: itemInRange=" + itemInRange);

			String itemOnly = fixture.refusal();
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String blockBehind = fixture.refusal();
			helper.assertTrue(GE_MATERIAL.equals(itemOnly) && GE_MATERIAL.equals(blockBehind),
					"GE_CREATE_LIFEFORM on an item entity of oak saplings on the look ray with an empty off hand: answered "
							+ itemOnly + ", and " + blockBehind + " with a usable block under the crosshair behind it; 1.16"
							+ " replaces the target with the item entity, cannot give life to it and asks for a material with "
							+ GE_MATERIAL);

			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromUserToCreated = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(fixture.user.position());
			int left = dropped.isAlive() ? dropped.getItem().getCount() : 0;
			helper.assertTrue(admitted && created.size() == 1 && fromUserToCreated >= 0.0D && fromUserToCreated < 3.0D
					&& fixture.user.getOffhandItem().is(Items.IRON_INGOT) && fixture.user.getOffhandItem().getCount() == 1
					&& left == 2 && fixture.level.getBlockState(pos).is(Blocks.STONE),
					"GE_CREATE_LIFEFORM press with that item entity on the look ray and iron ingots in the off hand: admitted="
							+ admitted + " transformations=" + created.size() + " fromUser=" + fromUserToCreated + " offHand="
							+ fixture.user.getOffhandItem() + " saplingsLeft=" + left + " block="
							+ fixture.level.getBlockState(pos) + "; 1.16 turns one off-hand item into the lifeform in front of"
							+ " the user and leaves the saplings and the block");
		}
		helper.succeed();
	}

	// 1.16 checkTarget asks HamonUtil.isItemLivingMatter of a dropped item, without the fish bucket exception that
	// canGiveLifeTo makes for the off hand: a dropped fish bucket is no target, a fish bucket in the off hand is a source.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTreatsADroppedFishBucketAsNoTarget(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			fixture.aim(ActionTarget.EMPTY);
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COD_BUCKET));
			String inOffHand = fixture.refusal();
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			ItemEntity dropped = fixture.item(2.5D, new ItemStack(Items.COD_BUCKET));
			boolean itemInRange = ActionTargetRange.isEntityWithinRange(fixture.stand, dropped, 64.0D);
			helper.assertTrue("accepted".equals(inOffHand) && itemInRange,
					"STAND-RANGE premise: a cod bucket in the off hand is not accepted as the source, or the item entity is out"
							+ " of the Stand's 8 blocks: inOffHand=" + inOffHand + " itemInRange=" + itemInRange);

			String emptyHand = fixture.refusal();
			helper.assertTrue(GE_MATERIAL.equals(emptyHand), "GE_CREATE_LIFEFORM on a dropped cod bucket on the look ray with"
					+ " an empty off hand: answered " + emptyHand + "; 1.16 checkTarget refuses an item entity of living"
					+ " matter, fish buckets included, and asks for a material with " + GE_MATERIAL);

			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromUserToCreated = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(fixture.user.position());
			helper.assertTrue(admitted && created.size() == 1 && fromUserToCreated >= 0.0D && fromUserToCreated < 3.0D
					&& fixture.user.getOffhandItem().is(Items.IRON_INGOT) && fixture.user.getOffhandItem().getCount() == 1
					&& dropped.isAlive() && dropped.getItem().is(Items.COD_BUCKET),
					"GE_CREATE_LIFEFORM press with a dropped cod bucket on the look ray and iron ingots in the off hand:"
							+ " admitted=" + admitted + " transformations=" + created.size() + " fromUser=" + fromUserToCreated
							+ " offHand=" + fixture.user.getOffhandItem() + " bucketLeft=" + dropped.isAlive()
							+ "; 1.16 turns one off-hand item into the lifeform in front of the user and leaves the bucket");
		}
		helper.succeed();
	}

	// 1.16 checkTarget refuses an unbreakable block only when the user is not in Creative (!power.isUserCreative()).
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesAnUnbreakableBlockInCreative(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D),
					Blocks.BEDROCK.defaultBlockState());
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String survival = fixture.refusal();
			fixture.user.setGameMode(GameType.CREATIVE);
			helper.assertTrue(GE_MATERIAL.equals(survival) && fixture.power.isUserCreative()
					&& fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: bedrock was not refused in Survival, the user did not become Creative, or the user"
							+ " has an off-hand item: survival=" + survival + " creative=" + fixture.power.isUserCreative());

			String creative = fixture.refusal();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromBlock = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(Vec3.atBottomCenterOf(pos));
			helper.assertTrue("accepted".equals(creative) && admitted && created.size() == 1 && fromBlock >= 0.0D
					&& fromBlock < 0.5D && fixture.level.getBlockState(pos).isAir(),
					"GE_CREATE_LIFEFORM on bedrock in range by a Creative user with an empty off hand: answered " + creative
							+ " admitted=" + admitted + " transformations=" + created.size() + " fromBlock=" + fromBlock
							+ " block=" + fixture.level.getBlockState(pos) + "; 1.16 checks the hardness only for a user who is"
							+ " not in Creative and turns the block into the lifeform");
		}
		helper.succeed();
	}

	// 1.16 has no server-side pick of its own: the target is what the client sends, its vanilla pick or, when that
	// missed and the Stand is out, the Stand's precision ray from the user's eyes (EntityStandType.clientHitResult),
	// which reaches the user's REACH_DISTANCE of 5 blocks. A block further ahead that the client did not aim is not a
	// target, and a withdrawn Stand has no precision ray.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesNoBlockTheClientDidNotAim(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 8.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String aimed = fixture.refusal();
			fixture.aim(ActionTarget.EMPTY);
			double fromEyes = Math.sqrt(new AABB(pos).distanceToSqr(fixture.user.getEyePosition()));
			boolean blockInRange = ActionTargetRange.isBlockWithinRange(fixture.stand, fixture.level, pos, 100.0D);
			helper.assertTrue("accepted".equals(aimed) && fromEyes > 7.0D && fromEyes < 8.0D && blockInRange
					&& fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: the block is not straight ahead of the user between 7 and 8 blocks and within the"
							+ " Stand's 10, was not accepted with the crosshair on it, or the user has an off-hand item: aimed="
							+ aimed + " fromEyes=" + fromEyes + " blockInRange=" + blockInRange);

			String nothingAimed = fixture.refusal();
			helper.assertTrue(GE_MATERIAL.equals(nothingAimed), "GE_CREATE_LIFEFORM with nothing under the user's synced"
					+ " crosshair and a usable block " + fromEyes + " blocks ahead: answered " + nothingAimed
					+ "; 1.16 has no target the client did not send, its precision ray ends at 5 blocks, and it asks for a"
					+ " material with " + GE_MATERIAL);

			// 4.8 blocks: past the vanilla pick of a Survival player (4.5), inside the precision ray (5)
			fixture.user.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 2.7D, 0.0F, 0.0F);
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 3.7D, 0.0F, 0.0F);
			double nearFromEyes = Math.sqrt(new AABB(pos).distanceToSqr(fixture.user.getEyePosition()));
			helper.assertTrue(nearFromEyes > 4.6D && nearFromEyes < 4.95D, "STAND-RANGE premise: the block is not between the"
					+ " vanilla pick's 4.5 blocks and the precision ray's 5 from the user's eyes: " + nearFromEyes);
			String standOut = fixture.refusal();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromBlock = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(Vec3.atBottomCenterOf(pos));
			helper.assertTrue("accepted".equals(standOut) && admitted && created.size() == 1 && fromBlock >= 0.0D
					&& fromBlock < 0.5D && fixture.level.getBlockState(pos).isAir(),
					"GE_CREATE_LIFEFORM with nothing under the user's crosshair, the Stand out and a usable block "
							+ nearFromEyes + " blocks ahead of the user's eyes: answered " + standOut + " admitted=" + admitted
							+ " transformations=" + created.size() + " fromBlock=" + fromBlock + " block="
							+ fixture.level.getBlockState(pos) + "; 1.16 takes the target of the Stand's precision ray, which"
							+ " reaches the user's 5 blocks, when the vanilla pick missed");

			fixture.block(pos, Blocks.STONE.defaultBlockState());
			fixture.standType.forceUnsummon(fixture.user, fixture.power);
			String standWithdrawn = fixture.refusal();
			helper.assertTrue(fixture.power.getSummonedStandEntity() == null && GE_MATERIAL.equals(standWithdrawn),
					"GE_CREATE_LIFEFORM in the same scene with the Stand withdrawn: standOut="
							+ (fixture.power.getSummonedStandEntity() != null) + " answered " + standWithdrawn
							+ "; 1.16 only casts the precision ray while the Stand is out and asks for a material with "
							+ GE_MATERIAL);
		}
		helper.succeed();
	}

	// The precision ray picks entities up to 5 blocks, where the vanilla pick of a Survival player ends at 3: a boat
	// there is the source although nothing is under the user's crosshair.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesABoatOnThePrecisionRayWhenTheVanillaPickMissed(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			Boat boat = fixture.boat(4.4D);
			boat.moveTo(fixture.origin.x, fixture.origin.y + 1.35D, fixture.origin.z + 4.4D, 0.0F, 0.0F);
			fixture.aim(ActionTarget.EMPTY);
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 2));
			Vec3 eyes = fixture.user.getEyePosition();
			double fromEyes = boat.getBoundingBox().clip(eyes, eyes.add(fixture.user.getLookAngle().scale(10.0D)))
					.map(eyes::distanceTo).orElse(-1.0D);
			helper.assertTrue(fromEyes > 3.2D && fromEyes < 4.8D, "STAND-RANGE premise: the user's look ray does not enter the"
					+ " boat between the vanilla entity pick's 3 blocks and the precision ray's 5: " + fromEyes);

			Vec3 boatPos = boat.position();
			String answered = fixture.refusal();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromBoat = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(boatPos);
			helper.assertTrue("accepted".equals(answered) && admitted && created.size() == 1 && fromBoat >= 0.0D
					&& fromBoat < 0.5D && !boat.isAlive() && fixture.user.getOffhandItem().getCount() == 2,
					"GE_CREATE_LIFEFORM press with nothing under the user's crosshair, the Stand out and a boat " + fromEyes
							+ " blocks along the look ray, iron ingots in the off hand: answered " + answered + " admitted="
							+ admitted + " transformations=" + created.size() + " fromBoat=" + fromBoat + " boatLeft="
							+ boat.isAlive() + " offHand=" + fixture.user.getOffhandItem() + "; 1.16 takes the target of the"
							+ " Stand's precision ray (5 blocks for entities too): the boat becomes the lifeform where it is"
							+ " and the ingots stay");
		}
		helper.succeed();
	}

	// 1.16 PowerBaseImpl.checkTarget:295-298: when the entity the client aimed at cannot be found on the server, the
	// whole target is emptied after overrideVanillaMouseTarget, the item entity on the look ray with it, so Create
	// Lifeform falls back to the off-hand item and, without one, asks for a material.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformDropsTheItemRayHitWhenTheAimedEntityIsGoneOnThePress(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			ItemEntity dropped = fixture.item(2.5D, new ItemStack(Items.GOLD_INGOT));
			Cow cow = fixture.cow(4.0D);
			FriendlyByteBuf sent = new FriendlyByteBuf(Unpooled.buffer());
			ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(sent, new ActionTarget(cow));
			cow.discard();
			ActionTarget received = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(sent);
			TargetType sentType = received.getType();
			// the step both packet handlers store an aim through
			ActionTargetAim aim = LivingComponentAction.getComponent(fixture.user).entityAim;
			aim.setTargetFromClient(received, fixture.level);
			helper.assertTrue(sentType == TargetType.ENTITY && aim.getTarget().getType() == TargetType.EMPTY
					&& fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: the client's aim was not an entity, the server still found it, or the user has an"
							+ " off-hand item: sent=" + sentType + " stored=" + aim.getTarget().getType());

			String answered = fixture.refusal();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			helper.assertTrue(GE_MATERIAL.equals(answered) && !admitted && created.isEmpty() && dropped.isAlive(),
					"GE_CREATE_LIFEFORM press with an item entity on the look ray while the entity the client aimed at is"
							+ " gone on the server, empty off hand: answered " + answered + " admitted=" + admitted
							+ " transformations=" + created.size() + " itemEntityLeft=" + dropped.isAlive()
							+ "; 1.16 empties the whole target and asks for a material");

			// an aim stored without the client's word on a missing entity is a fresh one too
			aim.setTarget(ActionTarget.EMPTY);
			String answeredAfterSet = fixture.refusal();
			helper.assertTrue(!aim.isClientEntityMissing() && "accepted".equals(answeredAfterSet),
					"GE_CREATE_LIFEFORM press with the item entity on the look ray after an empty aim was stored: missing="
							+ aim.isClientEntityMissing() + " answered " + answeredAfterSet
							+ "; the missing entity belongs to the press before");

			aim.setTargetFromClient(received, fixture.level);
			// the next aim the client sends is a fresh one
			aim.setTargetFromClient(ActionTarget.EMPTY, fixture.level);
			String answeredAfter = fixture.refusal();
			helper.assertTrue("accepted".equals(answeredAfter),
					"GE_CREATE_LIFEFORM press with the item entity on the look ray after the client sent an empty aim:"
							+ " answered " + answeredAfter + "; the item entity is the source again");
		}
		helper.succeed();
	}

	// 1.16 PowerBaseImpl.checkTarget:295-298 empties the whole target of a press on an entity the server no longer has,
	// for Heal too: no ray of the server's own heals another mob on the look ray in its place
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondHealDoesNotTakeAnotherMobWhenTheAimedEntityIsGoneOnThePress(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.CD_HEAL)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			Cow gone = fixture.cow(5.0D);
			Cow wounded = fixture.cow(3.0D);
			fixture.user.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
					wounded.getBoundingBox().getCenter());
			FriendlyByteBuf sent = new FriendlyByteBuf(Unpooled.buffer());
			ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(sent, new ActionTarget(gone));
			gone.discard();
			ActionTarget received = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(sent);
			// the step both packet handlers store an aim through
			ActionTargetAim aim = LivingComponentAction.getComponent(fixture.user).entityAim;
			aim.setTargetFromClient(received, fixture.level);
			helper.assertTrue(received.getType() == TargetType.ENTITY && aim.isClientEntityMissing()
					&& aim.getTarget().getType() == TargetType.EMPTY,
					"STAND-RANGE premise: the client's aim was not an entity or the server still found it: stored="
							+ aim.getTarget().getType());
			String answered = fixture.refusal(Kind.CD_HEAL, wounded);
			helper.assertTrue(HEAL_TARGET.equals(answered), "CD_HEAL pressed on an entity the server no longer has, with a"
					+ " wounded cow on the look ray: answered " + answered + "; 1.16 empties the whole target, so there is"
					+ " nothing to heal (" + HEAL_TARGET + ")");

			aim.setTargetFromClient(ActionTarget.EMPTY, fixture.level);
			String answeredAfter = fixture.refusal(Kind.CD_HEAL, wounded);
			helper.assertTrue("accepted".equals(answeredAfter), "CD_HEAL with an empty aim and a wounded cow on the look"
					+ " ray: answered " + answeredAfter + "; the cow is the target again");
		}
		helper.succeed();
	}

	// the same for Revert: no ray of the server's own reverts a snow golem on the look ray in place of the entity gone
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondRevertDoesNotTakeAnotherMobWhenTheAimedEntityIsGoneOnThePress(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			fixture.power.setResolveLevel(4);
			SnowGolem golem = EntityType.SNOW_GOLEM.create(fixture.level);
			helper.assertTrue(golem != null, "STAND-RANGE premise: could not create the snow golem");
			golem.setNoAi(true);
			golem.setNoGravity(true);
			golem.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 3.0D, 180.0F, 0.0F);
			fixture.extras.add(golem);
			helper.assertTrue(fixture.level.addFreshEntity(golem), "STAND-RANGE premise: could not add the snow golem");
			ActionTargetAim aim = sendGoneEntity(helper, fixture, fixture.cow(6.0D));
			String answered = fixture.refusal();
			helper.assertTrue(ITEM_OFFHAND.equals(answered), "CD_REVERT pressed on an entity the server no longer has, with a"
					+ " snow golem on the look ray: answered " + answered + "; 1.16 empties the whole target, so the press"
					+ " reverts no entity and asks for an item (" + ITEM_OFFHAND + ")");

			aim.setTargetFromClient(ActionTarget.EMPTY, fixture.level);
			String answeredAfter = fixture.refusal();
			helper.assertTrue("accepted".equals(answeredAfter), "CD_REVERT with an empty aim and a snow golem on the look"
					+ " ray: answered " + answeredAfter + "; the golem is the target again");
		}
		helper.succeed();
	}

	/*
	 * 1.16 CrazyDiamondPreviousState.checkTarget refused a block, so PowerBaseImpl.checkTarget emptied the target (Revert's
	 * requirement is NONE) and the press went on to the off-hand item: with a block under the crosshair Revert asks for an
	 * item, or takes a convertible one, where the port refused it silently.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondRevertAtABlockGoesOnToTheOffHandItemAsIn116(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
			fixture.power.setResolveLevel(4);
			BlockPos block = BlockPos.containing(fixture.user.getEyePosition()).south(3);
			LivingComponentAction.getComponent(fixture.user).entityAim.setTarget(new ActionTarget(block, Direction.NORTH));
			String noItem = fixture.refusal();
			helper.assertTrue(ITEM_OFFHAND.equals(noItem), "CD_REVERT pressed with a block under the crosshair and nothing in"
					+ " the off hand: answered " + noItem + "; 1.16 emptied the block target and asked for an item ("
					+ ITEM_OFFHAND + ")");
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ENCHANTED_BOOK));
			String withItem = fixture.refusal();
			helper.assertTrue("accepted".equals(withItem), "CD_REVERT pressed with a block under the crosshair and an"
					+ " enchanted book in the off hand: answered " + withItem + "; 1.16 turned the book back");
		}
		helper.succeed();
	}

	/*
	 * 1.16 took Revert's target from the crosshair (InputHandler.pickMouseTarget): the vanilla pick, and behind it, only
	 * while the Stand was out, EntityStandType.clientHitResult's precision ray from the camera entity as far as
	 * StandEntity.getAimDistance: the user's REACH_DISTANCE of 5, or under manual control, the Stand being the camera, the
	 * Stand's own 2.5. With the client's aim empty (a Survival player's pick ends at 3 blocks; under manual control the
	 * user is not the camera), the server's own look ray stands in for that precision ray: a snow golem 4.3 blocks from
	 * the user's eyes is reverted, one whose box grown by the Stand's precision is past 5 blocks is not, nor one 4.3 away
	 * while Crazy Diamond is not out; under
	 * manual control the Stand's aim (here one its precision box let a client's aim assist take, off the exact look) and
	 * its look within its own reach count, not its user's look.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondRevertTakesAnEntityOnlyWithinItsCrosshairsReachAsIn116(GameTestHelper helper) {
		String[] rows = { "within the user's reach", "past the user's reach", "within the user's reach, the Stand not out",
				"under manual control, in the Stand's aim", "under manual control, on the Stand's look",
				"under manual control, past the Stand's reach on its look", "under manual control, on the user's look only" };
		for (String row : rows) {
			try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
				fixture.aim(ActionTarget.EMPTY);
				SnowGolem golem;
				String where;
				String expected;
				if (!row.startsWith("under manual control")) {
					boolean past = row.startsWith("past");
					golem = revertibleGolem(helper, fixture, past ? 6.4D : 4.65D);
					fixture.stand.updatePosition(fixture.user);
					double fromUser = Math.sqrt(golem.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
					double grown = entry(fixture.user, HitResultUtil.standPrecisionTargetHitbox(
							golem.getBoundingBox().inflate(golem.getPickRadius()), fixture.stand.getPrecision()));
					double fromStand = ActionTargetRange.getDistance(fixture.stand, golem.getBoundingBox());
					helper.assertTrue(fixture.stand.isFollowingUser() && fixture.stand.offsetFromUser.isIdle()
							&& fromUser > fixture.user.entityInteractionRange() && (past ? grown > 5.0D : fromUser < 5.0D)
							&& fromStand < 8.0D && fixture.stand.hasLineOfSight(golem) && fixture.user.getOffhandItem().isEmpty(),
							"STAND-RANGE premise: the idle Stand, the snow golem past the crosshair pick, within the reach or its"
									+ " precision box past it, and within 8 blocks of the Stand in its sight, or the off hand: fromUser="
									+ fromUser + " precisionBox=" + grown + " fromStand=" + fromStand);
					where = "on the look ray " + fromUser + " blocks from the user's eyes (its precision box " + grown + ")";
					if (row.endsWith("the Stand not out")) {
						fixture.standType.forceUnsummon(fixture.user, fixture.power);
						helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
								"STAND-RANGE premise: Crazy Diamond is still out");
						where += ", Crazy Diamond not out";
					}
					expected = row.equals("within the user's reach") ? "accepted" : ITEM_OFFHAND;
				}
				else {
					// its user steered it behind (north) as far as it goes, as the O key does through StandEntity.move, which holds
					// its feet within its max range of its user's, and turned it north
					StandEntity stand = fixture.stand;
					stand.setManuallyControlled(true);
					stand.move(net.minecraft.world.entity.MoverType.SELF,
							new Vec3(fixture.origin.x, fixture.origin.y, fixture.origin.z - 3.0D).subtract(stand.position()));
					stand.setYRot(180.0F);
					stand.setXRot(0.0F);
					stand.setYHeadRot(180.0F);
					boolean userLookOnly = row.endsWith("the user's look only");
					boolean inAim = row.endsWith("in the Stand's aim");
					boolean pastReach = row.contains("past the Stand's reach");
					golem = userLookOnly ? revertibleGolem(helper, fixture, 4.65D) : golemAt(helper, fixture,
							new Vec3(fixture.origin.x + (inAim ? 0.6D : 0.0D), stand.getEyeY() - 0.95D,
									stand.getZ() - (pastReach ? 4.0D : 2.2D)));
					LivingComponentAction.getComponent(stand).entityAim.setTarget(inAim ? new ActionTarget(golem) : ActionTarget.EMPTY);
					double standFromUser = stand.distanceTo(fixture.user);
					double fromStandEyes = Math.sqrt(golem.getBoundingBox().distanceToSqr(stand.getEyePosition()));
					double fromUser = Math.sqrt(golem.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
					double standReach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
					Vec3 userEyes = fixture.user.getEyePosition();
					Vec3 standEyes = stand.getEyePosition();
					Vec3 standLookEnd = standEyes.add(stand.getLookAngle().scale(8.0D));
					boolean onUsersLook = golem.getBoundingBox().clip(userEyes, userEyes.add(fixture.user.getLookAngle().scale(8.0D)))
							.isPresent();
					boolean onStandsLook = golem.getBoundingBox().clip(standEyes, standLookEnd).isPresent();
					boolean inStandsPrecisionBox = HitResultUtil.standPrecisionTargetHitbox(golem.getBoundingBox(), stand.getPrecision())
							.clip(standEyes, standLookEnd).isPresent();
					boolean layout = userLookOnly ? onUsersLook && !onStandsLook && fromUser < 5.0D
							: !onUsersLook && (inAim ? !onStandsLook && inStandsPrecisionBox && fromStandEyes < standReach
									: onStandsLook && (pastReach ? fromStandEyes > 3.0D && fromStandEyes < 5.0D : fromStandEyes < standReach));
					helper.assertTrue(stand.isManuallyControlled() && standFromUser <= stand.getMaxRange() + 1.0E-6D && layout
							&& fixture.user.getOffhandItem().isEmpty(),
							"STAND-RANGE premise: the manually controlled Stand's feet " + standFromUser + " blocks from its user's (max range "
									+ stand.getMaxRange() + "), the golem " + fromStandEyes + " from the Stand's eyes (reach " + standReach
									+ ", on its look " + onStandsLook + ", its precision box on it " + inStandsPrecisionBox + ") and "
									+ fromUser + " from the user's (on its look " + onUsersLook + ")");
					where = userLookOnly ? "on its user's look ray " + fromUser + " blocks away, the Stand looking aside"
							: (inAim ? "in the Stand's aim, off its exact look" : "on the Stand's look ray") + ", " + fromStandEyes
									+ " blocks from the Stand's eyes (its reach " + standReach + ")";
					expected = userLookOnly || pastReach ? ITEM_OFFHAND : "accepted";
				}
				String answered = fixture.refusal();
				helper.assertTrue(expected.equals(answered), "CD_REVERT " + row + ", a snow golem " + where + ", with an empty"
						+ " off hand: answered " + answered + "; 1.16's crosshair " + ("accepted".equals(expected)
								? "took the golem" : "did not reach the golem, so it asked for an item") + " (" + expected + ")");
			}
		}
		helper.succeed();
	}

	/*
	 * 1.16 took Heal's target under the crosshair as it took Revert's: the vanilla pick, and behind it, only while the
	 * Stand was out, the precision ray from the camera entity as far as the user's reach of 5, or under manual control
	 * the Stand's own 2.5 along the Stand's view. With the client's aim empty, a wounded cow whose box the user's look
	 * enters 4.55 blocks from the eyes is healed, one whose box grown by the Stand's precision the look enters past 5 is
	 * not, nor one 4.55 away while Crazy Diamond is not out; under manual control the Stand's aim and its look within its
	 * own reach count, not its user's look.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondHealTakesAnEntityOnlyWithinItsCrosshairsReachAsIn116(GameTestHelper helper) {
		String[] rows = { "within the user's reach", "past the user's reach", "within the user's reach, the Stand not out",
				"under manual control, in the Stand's aim", "under manual control, on the Stand's look",
				"under manual control, past the Stand's reach on its look", "under manual control, on the user's look only" };
		for (String row : rows) {
			try (Fixture fixture = Fixture.open(helper, Kind.CD_HEAL)) {
				fixture.aim(ActionTarget.EMPTY);
				Cow wounded = fixture.cow(5.0D);
				Vec3 userEyes = fixture.user.getEyePosition();
				Vec3 userLookEnd = userEyes.add(fixture.user.getLookAngle().scale(8.0D));
				String where;
				String expected;
				if (!row.startsWith("under manual control")) {
					boolean past = row.startsWith("past");
					// the user's look ray runs at eye height, above a cow standing on the user's level
					wounded.moveTo(fixture.origin.x, fixture.origin.y + 0.6D, fixture.origin.z + (past ? 6.7D : 5.0D), 180.0F, 0.0F);
					fixture.stand.updatePosition(fixture.user);
					double fromUser = wounded.getBoundingBox().clip(userEyes, userLookEnd).map(userEyes::distanceTo).orElse(-1.0D);
					double grown = entry(fixture.user, HitResultUtil.standPrecisionTargetHitbox(
							wounded.getBoundingBox().inflate(wounded.getPickRadius()), fixture.stand.getPrecision()));
					double fromStand = ActionTargetRange.getDistance(fixture.stand, wounded.getBoundingBox());
					helper.assertTrue(fixture.stand.isFollowingUser() && fixture.stand.offsetFromUser.isIdle()
							&& fromUser > fixture.user.entityInteractionRange() && (past ? grown > 5.0D : fromUser < 5.0D)
							&& fromStand < 8.0D && fixture.stand.hasLineOfSight(wounded),
							"STAND-RANGE premise: the idle Stand, the cow on the user's look past the crosshair pick, within the reach"
									+ " or its precision box past it, and within 8 blocks of the Stand in its sight: fromUser=" + fromUser
									+ " precisionBox=" + grown + " fromStand=" + fromStand);
					where = "on the look ray " + fromUser + " blocks from the user's eyes (its precision box " + grown + ")";
					if (row.endsWith("the Stand not out")) {
						fixture.standType.forceUnsummon(fixture.user, fixture.power);
						helper.assertTrue(fixture.power.getSummonedStandEntity() == null,
								"STAND-RANGE premise: Crazy Diamond is still out");
						where += ", Crazy Diamond not out";
					}
					expected = row.equals("within the user's reach") ? "accepted" : HEAL_TARGET;
				}
				else {
					// its user steered it behind (north) as far as it goes, as the O key does through StandEntity.move, which holds
					// its feet within its max range of its user's, and turned it north
					StandEntity stand = fixture.stand;
					stand.setManuallyControlled(true);
					stand.move(net.minecraft.world.entity.MoverType.SELF,
							new Vec3(fixture.origin.x, fixture.origin.y, fixture.origin.z - 3.0D).subtract(stand.position()));
					stand.setYRot(180.0F);
					stand.setXRot(0.0F);
					stand.setYHeadRot(180.0F);
					boolean userLookOnly = row.endsWith("the user's look only");
					boolean inAim = row.endsWith("in the Stand's aim");
					boolean pastReach = row.contains("past the Stand's reach");
					Vec3 feet = userLookOnly ? new Vec3(fixture.origin.x, fixture.origin.y + 0.6D, fixture.origin.z + 5.0D)
							: new Vec3(fixture.origin.x + (inAim ? 0.6D : 0.0D), stand.getEyeY() - wounded.getBbHeight() / 2.0D,
									stand.getZ() - (pastReach ? 4.0D : 2.2D));
					wounded.moveTo(feet.x, feet.y, feet.z, 180.0F, 0.0F);
					LivingComponentAction.getComponent(stand).entityAim.setTarget(inAim ? new ActionTarget(wounded) : ActionTarget.EMPTY);
					double standFromUser = stand.distanceTo(fixture.user);
					double fromStandEyes = Math.sqrt(wounded.getBoundingBox().distanceToSqr(stand.getEyePosition()));
					double fromUser = Math.sqrt(wounded.getBoundingBox().distanceToSqr(userEyes));
					double standReach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
					Vec3 standEyes = stand.getEyePosition();
					Vec3 standLookEnd = standEyes.add(stand.getLookAngle().scale(8.0D));
					boolean onUsersLook = wounded.getBoundingBox().clip(userEyes, userLookEnd).isPresent();
					boolean onStandsLook = wounded.getBoundingBox().clip(standEyes, standLookEnd).isPresent();
					boolean inStandsPrecisionBox = HitResultUtil.standPrecisionTargetHitbox(wounded.getBoundingBox(), stand.getPrecision())
							.clip(standEyes, standLookEnd).isPresent();
					boolean layout = userLookOnly ? onUsersLook && !onStandsLook && fromUser < 5.0D
							: !onUsersLook && (inAim ? !onStandsLook && inStandsPrecisionBox && fromStandEyes < standReach
									: onStandsLook && (pastReach ? fromStandEyes > 3.0D && fromStandEyes < 5.0D : fromStandEyes < standReach));
					helper.assertTrue(stand.isManuallyControlled() && standFromUser <= stand.getMaxRange() + 1.0E-6D && layout,
							"STAND-RANGE premise: the manually controlled Stand's feet " + standFromUser + " blocks from its user's (max range "
									+ stand.getMaxRange() + "), the cow " + fromStandEyes + " from the Stand's eyes (reach " + standReach
									+ ", on its look " + onStandsLook + ", its precision box on it " + inStandsPrecisionBox + ") and "
									+ fromUser + " from the user's (on its look " + onUsersLook + ")");
					where = userLookOnly ? "on its user's look ray " + fromUser + " blocks away, the Stand looking aside"
							: (inAim ? "in the Stand's aim, off its exact look" : "on the Stand's look ray") + ", " + fromStandEyes
									+ " blocks from the Stand's eyes (its reach " + standReach + ")";
					expected = userLookOnly || pastReach ? HEAL_TARGET : "accepted";
				}
				String answered = fixture.refusal(Kind.CD_HEAL, wounded);
				helper.assertTrue(expected.equals(answered), "CD_HEAL " + row + ", a wounded cow " + where + ": answered "
						+ answered + "; 1.16's crosshair " + ("accepted".equals(expected) ? "took the cow"
								: "did not reach the cow, so there was nothing to heal") + " (" + expected + ")");
			}
		}
		helper.succeed();
	}

	/*
	 * Behind an empty vanilla pick, with the Stand out, 1.16's crosshair was the Stand's precision ray
	 * (StandEntity.precisionRayTrace): any entity but the Stand and its user, its box grown by the Stand's precision, the
	 * closest player or Stand first, then hostile mob, then any other; Heal and Revert refused the one it took only then
	 * (checkTarget), so an entity they do not work on hid one behind it. EntityStandType.clientHitResult cast that ray only
	 * where the vanilla pick met nothing: a picked entity or block was the target. Without the user's aim assist setting
	 * the crosshair line takes its place. On the user's look, every entity is past the 3 blocks of a Survival player's
	 * vanilla pick and within the 5-block reach, unless the row puts it under the vanilla pick.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondHealAndRevertTakeThePrecisionRayTargetAsIn116(GameTestHelper helper) {
		String[] rows = { "revert: a cow before a snow golem", "revert: a charged creeper behind a cow",
				"revert: a snow golem beside the look line, in the Stand's precision box",
				"revert, the aim assist off: a snow golem beside the look line, in the Stand's precision box",
				"revert, the aim assist off: a cow before a snow golem",
				"revert: the vanilla pick on a cow, a charged creeper behind it",
				"revert under manual control: a cow before a snow golem on the Stand's look",
				"heal: a minecart before a wounded cow", "heal: a wounded cow beside the look line, in the Stand's precision box",
				"heal: the vanilla pick on a block, a wounded baby cow before it" };
		for (String row : rows) {
			boolean heal = row.startsWith("heal");
			try (Fixture fixture = Fixture.open(helper, heal ? Kind.CD_HEAL : Kind.CD_REVERT)) {
				fixture.power.setResolveLevel(4);
				fixture.stand.updatePosition(fixture.user);
				fixture.aim(ActionTarget.EMPTY);
				if (row.contains("the aim assist off")) {
					fixture.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = false;
				}
				Cow wounded = null;
				String layout;
				boolean premise;
				String expected;
				if (row.contains("under manual control")) {
					// steered behind its user as far as StandEntity.move lets its feet go, as the O key does, and turned north
					StandEntity stand = fixture.stand;
					stand.setManuallyControlled(true);
					stand.move(net.minecraft.world.entity.MoverType.SELF,
							new Vec3(fixture.origin.x, fixture.origin.y, fixture.origin.z - 3.0D).subtract(stand.position()));
					stand.setYRot(180.0F);
					stand.setXRot(0.0F);
					stand.setYHeadRot(180.0F);
					LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
					Cow cow = placed(helper, fixture, EntityType.COW, new Vec3(fixture.origin.x, stand.getEyeY(), stand.getZ() - 1.0D));
					SnowGolem golem = placed(helper, fixture, EntityType.SNOW_GOLEM,
							new Vec3(fixture.origin.x, stand.getEyeY(), stand.getZ() - 2.0D));
					double reach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
					double standFromUser = stand.distanceTo(fixture.user);
					double cowEntry = entry(stand, cow.getBoundingBox());
					double golemEntry = entry(stand, golem.getBoundingBox());
					premise = standFromUser <= stand.getMaxRange() + 1.0E-6D && cowEntry > 0.0D && golemEntry > cowEntry
							&& golemEntry < reach && entry(fixture.user, golem.getBoundingBox()) < 0.0D;
					layout = "the Stand's feet " + standFromUser + " blocks from its user's (max range " + stand.getMaxRange()
							+ "), on the Stand's look, the cow " + cowEntry + " and the golem " + golemEntry + " blocks from its eyes (reach "
							+ reach + ")";
					expected = ITEM_OFFHAND;
				}
				else if (row.contains("beside the look line")) {
					Entity beside;
					if (heal) {
						wounded = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.75D, 4.0D));
						beside = wounded;
					}
					else {
						beside = placed(helper, fixture, EntityType.SNOW_GOLEM, onLook(fixture, 0.6D, 4.0D));
					}
					double exact = entry(fixture.user, beside.getBoundingBox());
					double grown = entry(fixture.user, HitResultUtil.standPrecisionTargetHitbox(
							beside.getBoundingBox().inflate(beside.getPickRadius()), fixture.stand.getPrecision()));
					premise = exact < 0.0D && grown > 0.0D && grown < 5.0D;
					layout = "beside the look line (the line misses its box), its box grown by the Stand's precision "
							+ fixture.stand.getPrecision() + " met " + grown + " blocks from the eyes";
					expected = row.contains("the aim assist off") ? (heal ? HEAL_TARGET : ITEM_OFFHAND) : "accepted";
				}
				else if (row.contains("the vanilla pick on a block")) {
					BlockPos block = fixture.block(BlockPos.containing(fixture.origin.x, fixture.user.getEyeY(), fixture.origin.z + 3.6D));
					fixture.aim(new ActionTarget(block, Direction.NORTH));
					wounded = fixture.babyCow(0.0D, 3.27D);
					double cowEntry = entry(fixture.user, wounded.getBoundingBox());
					double blockEntry = entry(fixture.user, new AABB(block));
					premise = cowEntry > fixture.user.entityInteractionRange() && blockEntry > cowEntry
							&& blockEntry <= fixture.user.blockInteractionRange();
					layout = "the baby cow " + cowEntry + " blocks along the look line, past the vanilla pick's entities, and the block "
							+ blockEntry + ", within its blocks";
					expected = HEAL_TARGET;
				}
				else {
					boolean picked = row.contains("the vanilla pick on a cow");
					Entity front = heal ? placed(helper, fixture, EntityType.MINECART, onLook(fixture, 0.0D, 3.7D))
							: placed(helper, fixture, EntityType.COW, onLook(fixture, 0.0D, picked ? 2.3D : 3.7D));
					Entity behind;
					if (heal) {
						wounded = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.0D, 4.6D));
						behind = wounded;
					}
					else if (row.contains("creeper")) {
						Creeper creeper = placed(helper, fixture, EntityType.CREEPER, onLook(fixture, 0.0D, 4.6D));
						CommonReflection.setCreeperPowered(creeper, true);
						behind = creeper;
					}
					else {
						behind = placed(helper, fixture, EntityType.SNOW_GOLEM, onLook(fixture, 0.0D, 4.6D));
					}
					if (picked) {
						fixture.aim(new ActionTarget(front));
					}
					double frontEntry = entry(fixture.user, front.getBoundingBox());
					double behindEntry = entry(fixture.user, behind.getBoundingBox());
					premise = (picked ? frontEntry > 0.0D && frontEntry < fixture.user.entityInteractionRange()
							: frontEntry > fixture.user.entityInteractionRange()) && behindEntry > frontEntry && behindEntry < 5.0D;
					layout = "on the look line, the " + front.getType().getDescription().getString() + " " + frontEntry + " and the "
							+ behind.getType().getDescription().getString() + " " + behindEntry + " blocks from the eyes";
					boolean creeperTaken = row.equals("revert: a charged creeper behind a cow");
					expected = heal ? HEAL_TARGET : creeperTaken ? "accepted" : ITEM_OFFHAND;
				}
				helper.assertTrue(premise && fixture.user.getOffhandItem().isEmpty(), "STAND-RANGE premise: " + row + ": " + layout);
				String answered = heal ? fixture.refusal(Kind.CD_HEAL, wounded) : fixture.refusal();
				helper.assertTrue(expected.equals(answered), (heal ? "CD_HEAL " : "CD_REVERT ") + row + " (" + layout
						+ ")" + (heal ? "" : ", an empty off hand") + ": answered " + answered + "; 1.16 answered " + expected);
			}
		}
		helper.succeed();
	}

	/*
	 * 1.16's client took a manually controlled Stand's target from the crosshair with the Stand as the camera
	 * (InputHandler.pickMouseTarget): the vanilla pick (a block within the player's 4.5, any entity within 3) and, behind a
	 * miss, EntityStandType.clientHitResult's precision ray from the Stand as far as its own reach (getAimDistance under
	 * manual control) over any entity but the Stand and its user; no action the Stand ran filtered it. Without the aim
	 * assist setting the Stand's crosshair line within that reach takes its place. Crazy Diamond under manual control, 2.4
	 * blocks behind its user and turned away from it; the vanilla pick is GameRenderer.pick's from the Stand's eyes.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void aManuallyControlledStandAimsWithItsCrosshairWhateverActionItRunsAsIn116(GameTestHelper helper) {
		String[] rows = { "a held Revert: a cow before a snow golem on the Stand's look",
				"a held Revert: a cow and a snow golem beside the Stand's look, in its precision boxes",
				"a held Revert: the vanilla pick on a cow, a charged creeper beside the Stand's look behind it, in its precision box",
				"no action: a cow on the Stand's look past its reach, within the vanilla pick",
				"no action: a stone block on the Stand's look past its reach, within the vanilla pick",
				"no action: the user's tamed wolf before a cow beside the Stand's look, in their precision boxes",
				"no action: a snow golem beside the Stand's look past its reach, in its precision box",
				"the aim assist off, a held Revert: a cow before a snow golem on the Stand's look",
				"the aim assist off, no action: a cow beside the Stand's look, in its precision box" };
		List<String> wrong = new ArrayList<>();
		for (String row : rows) {
			try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
				fixture.power.setResolveLevel(4);
				StandEntity stand = fixture.stand;
				stand.setManuallyControlled(true);
				stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z - 2.4D, 180.0F, 0.0F);
				stand.setYHeadRot(180.0F);
				LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
				if (row.startsWith("the aim assist off")) {
					fixture.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = false;
				}
				double reach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
				Object expected;
				String layout;
				boolean premise;
				if (row.contains("a cow before a snow golem") || row.contains("a cow and a snow golem")) {
					double side = row.contains("beside") ? 0.6D : 0.0D;
					Cow cow = placed(helper, fixture, EntityType.COW, aheadOfStand(stand, side, side > 0.0D ? 1.2D : 1.0D));
					SnowGolem golem = placed(helper, fixture, EntityType.SNOW_GOLEM, aheadOfStand(stand, side, 2.0D));
					double cowEntry = entry(stand, cow.getBoundingBox());
					double golemEntry = entry(stand, golem.getBoundingBox());
					double cowGrown = entry(stand, grownBox(stand, cow));
					double golemGrown = entry(stand, grownBox(stand, golem));
					premise = side > 0.0D ? cowEntry < 0.0D && golemEntry < 0.0D && cowGrown > 0.0D && golemGrown > cowGrown
							&& golemGrown < reach : cowEntry > 0.0D && golemEntry > cowEntry && golemEntry < reach;
					layout = "the cow " + cowEntry + " (precision box " + cowGrown + ") and the golem " + golemEntry
							+ " (precision box " + golemGrown + ") blocks along the Stand's look (reach " + reach + ")";
					premise &= heldRevertTakesOnly(fixture, stand, cow, golem);
					layout += ", the held Revert taking only the golem";
					expected = cow;
				}
				else if (row.contains("a charged creeper")) {
					Cow cow = placed(helper, fixture, EntityType.COW, aheadOfStand(stand, 0.0D, 1.0D));
					Creeper creeper = placed(helper, fixture, EntityType.CREEPER, aheadOfStand(stand, 0.6D, 2.0D));
					CommonReflection.setCreeperPowered(creeper, true);
					double cowEntry = entry(stand, cow.getBoundingBox());
					double creeperGrown = entry(stand, grownBox(stand, creeper));
					premise = cowEntry > 0.0D && cowEntry < fixture.user.entityInteractionRange() && entry(stand, creeper.getBoundingBox()) < 0.0D
							&& creeperGrown > cowEntry && creeperGrown < reach
							&& StandPrecisionRay.trace(stand, stand, reach, 0.0D, true, true).getEntity() == creeper
							&& heldRevertTakesOnly(fixture, stand, cow, creeper);
					layout = "the cow " + cowEntry + " blocks along the Stand's look and the charged creeper's precision box " + creeperGrown
							+ ", off the exact line (reach " + reach + "), which the precision ray takes, the held Revert taking only the creeper";
					expected = cow;
				}
				else if (row.contains("a cow on the Stand's look past its reach")) {
					Cow cow = placed(helper, fixture, EntityType.COW, aheadOfStand(stand, 0.0D, 3.0D));
					double cowEntry = entry(stand, cow.getBoundingBox());
					premise = cowEntry > reach && cowEntry < fixture.user.entityInteractionRange();
					layout = "the cow " + cowEntry + " blocks along the Stand's look (reach " + reach + ", the player's entity pick "
							+ fixture.user.entityInteractionRange() + ")";
					expected = cow;
				}
				else if (row.contains("a stone block")) {
					BlockPos block = fixture.block(BlockPos.containing(fixture.origin.x, stand.getEyeY(), stand.getZ() - 3.6D));
					double blockEntry = entry(stand, new AABB(block));
					premise = blockEntry > reach && blockEntry < fixture.user.blockInteractionRange();
					layout = "the block " + blockEntry + " blocks along the Stand's look (reach " + reach + ", the player's block pick "
							+ fixture.user.blockInteractionRange() + ")";
					expected = block;
				}
				else if (row.contains("tamed wolf")) {
					Wolf wolf = placed(helper, fixture, EntityType.WOLF, aheadOfStand(stand, 0.5D, 1.0D));
					wolf.setTame(true, false);
					wolf.setOwnerUUID(fixture.user.getUUID());
					Cow cow = placed(helper, fixture, EntityType.COW, aheadOfStand(stand, 0.6D, 2.0D));
					double wolfGrown = entry(stand, grownBox(stand, wolf));
					double cowGrown = entry(stand, grownBox(stand, cow));
					premise = wolf.getOwner() == fixture.user && !stand.canAttackEntity(wolf) && entry(stand, wolf.getBoundingBox()) < 0.0D
							&& entry(stand, cow.getBoundingBox()) < 0.0D && wolfGrown > 0.0D && cowGrown > wolfGrown && cowGrown < reach;
					layout = "the wolf's precision box " + wolfGrown + " and the cow's " + cowGrown + " blocks along the Stand's look,"
							+ " both off the exact line (reach " + reach + "), a wolf the Stand may not attack";
					expected = wolf;
				}
				else if (row.contains("past its reach, in its precision box")) {
					SnowGolem golem = placed(helper, fixture, EntityType.SNOW_GOLEM, aheadOfStand(stand, 0.6D, 3.6D));
					double golemGrown = entry(stand, grownBox(stand, golem));
					premise = entry(stand, golem.getBoundingBox()) < 0.0D && golemGrown > reach
							&& golemGrown < StandEntityPunchAbility.userAimReach(fixture.user, stand);
					layout = "the golem's precision box " + golemGrown + " blocks along the Stand's look, off the exact line (reach "
							+ reach + ")";
					expected = ActionTarget.EMPTY;
				}
				else {
					Cow cow = placed(helper, fixture, EntityType.COW, aheadOfStand(stand, 0.6D, 1.2D));
					double cowGrown = entry(stand, grownBox(stand, cow));
					premise = entry(stand, cow.getBoundingBox()) < 0.0D && cowGrown > 0.0D && cowGrown < reach;
					layout = "the cow's precision box " + cowGrown + " blocks along the Stand's look, off the exact line (reach " + reach
							+ ")";
					expected = ActionTarget.EMPTY;
				}
				ActionTarget vanillaPick = vanillaPick(stand, fixture.user);
				helper.assertTrue(premise && stand.isManuallyControlled() && entry(fixture.user, stand.getBoundingBox()) < 0.0D,
						"STAND-AIM premise: " + row + ": " + layout + "; the vanilla pick " + describe(vanillaPick, stand));
				ActionTarget aim = StandEntityPunchAbility.manualStandAim(stand, vanillaPick);
				boolean matches = expected instanceof Entity entity ? aim.getType() == TargetType.ENTITY && aim.getEntity() == entity
						: expected instanceof BlockPos pos ? aim.getType() == TargetType.BLOCK && pos.equals(aim.getBlockPos())
						: aim.getType() == TargetType.EMPTY;
				if (!matches) wrong.add("STAND-AIM " + row + " (" + layout + "; the vanilla pick " + describe(vanillaPick, stand)
						+ "): the manually controlled Stand's aim is " + describe(aim, stand) + "; 1.16's crosshair with the Stand as"
						+ " the camera took " + (expected instanceof Entity entity ? entity.getType().getDescription().getString()
								: expected instanceof BlockPos pos ? "the block at " + pos.toShortString() : "nothing"));
				LivingComponentAction.getComponent(stand).setAction(null, fixture.user, SyncType.NO_SYNC);
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * 1.16 EntityStandType.clientHitResult with the user as the camera: behind an empty vanilla pick the precision ray from
	 * the user took any entity StandEntity.canTarget allowed (a player or Stand first, then a hostile mob, then the rest),
	 * friendly fire allowed, whatever action the Stand ran, and that action's checkTarget then judged it. The port's client
	 * aim (StandEntityPunchAbility.userStandAim, which ClientsideAim reaches through clientStandAim) passed over what the
	 * running action could not take and, with no action, what the Stand may not attack. Crazy Diamond at Resolve 4 as
	 * summoned, its user looking south; ClientsideAim is client-only, so its call is pinned from its class file, read as a
	 * resource.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void aStandAimsWithItsUsersCrosshairWhateverActionItRunsAsIn116(GameTestHelper helper) {
		String[] rows = { "a held Revert: a cow and a snow golem beside the user's look, in their precision boxes",
				"no action: the user's tamed wolf before a cow beside the user's look, in their precision boxes",
				"the aim assist off, a held Revert: a cow before a snow golem on the user's look" };
		List<String> wrong = new ArrayList<>();
		for (String row : rows) {
			try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
				fixture.power.setResolveLevel(4);
				StandEntity stand = fixture.stand;
				// three blocks behind its user, out of the user's pick
				stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z - 3.0D, 0.0F, 0.0F);
				LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
				if (row.startsWith("the aim assist off")) {
					fixture.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = false;
				}
				double reach = StandEntityPunchAbility.userAimReach(fixture.user, stand);
				Entity expected;
				String layout;
				boolean premise;
				if (row.contains("tamed wolf")) {
					Wolf wolf = placed(helper, fixture, EntityType.WOLF, onLook(fixture, 0.5D, 1.0D));
					wolf.setTame(true, false);
					wolf.setOwnerUUID(fixture.user.getUUID());
					Cow cow = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.6D, 2.0D));
					double wolfGrown = entry(fixture.user, grownBox(stand, wolf));
					double cowGrown = entry(fixture.user, grownBox(stand, cow));
					premise = wolf.getOwner() == fixture.user && !stand.canAttackEntity(wolf)
							&& entry(fixture.user, wolf.getBoundingBox()) < 0.0D && entry(fixture.user, cow.getBoundingBox()) < 0.0D
							&& wolfGrown > 0.0D && cowGrown > wolfGrown && cowGrown < reach;
					layout = "the wolf's precision box " + wolfGrown + " and the cow's " + cowGrown + " blocks along the user's look,"
							+ " both off the exact line (reach " + reach + "), a wolf the Stand may not attack";
					expected = wolf;
				}
				else {
					boolean beside = row.contains("beside");
					double side = beside ? 0.6D : 0.0D;
					Cow cow = placed(helper, fixture, EntityType.COW, onLook(fixture, side, beside ? 1.2D : 1.0D));
					SnowGolem golem = placed(helper, fixture, EntityType.SNOW_GOLEM, onLook(fixture, side, 2.0D));
					double cowEntry = entry(fixture.user, cow.getBoundingBox());
					double golemEntry = entry(fixture.user, golem.getBoundingBox());
					double cowGrown = entry(fixture.user, grownBox(stand, cow));
					double golemGrown = entry(fixture.user, grownBox(stand, golem));
					premise = beside ? cowEntry < 0.0D && golemEntry < 0.0D && cowGrown > 0.0D && golemGrown > cowGrown
							&& golemGrown < reach : cowEntry > 0.0D && golemEntry > cowEntry && golemEntry < reach;
					premise &= heldRevertTakesOnly(fixture, stand, cow, golem);
					layout = "the cow " + cowEntry + " (precision box " + cowGrown + ") and the golem " + golemEntry
							+ " (precision box " + golemGrown + ") blocks along the user's look (reach " + reach + "), the held Revert"
							+ " taking only the golem";
					expected = cow;
				}
				ActionTarget vanillaPick = vanillaPick(fixture.user, fixture.user);
				premise &= !stand.isManuallyControlled() && entry(fixture.user, stand.getBoundingBox()) < 0.0D
						&& (row.endsWith("on the user's look") || vanillaPick.getType() == TargetType.EMPTY);
				helper.assertTrue(premise, "USER-AIM premise: " + row + ": " + layout + "; the vanilla pick "
						+ describe(vanillaPick, fixture.user));
				ActionTarget aim = StandEntityPunchAbility.userStandAim(stand, fixture.user, vanillaPick);
				if (aim.getType() != TargetType.ENTITY || aim.getEntity() != expected) {
					wrong.add("USER-AIM " + row + " (" + layout + "; the vanilla pick " + describe(vanillaPick, fixture.user)
							+ "): the Stand's aim is " + describe(aim, fixture.user) + "; 1.16's crosshair took "
							+ expected.getType().getDescription().getString());
				}
				LivingComponentAction.getComponent(stand).setAction(null, fixture.user, SyncType.NO_SYNC);
			}
		}
		String clientAim = "rotp/core/client/input/ClientsideAim.class";
		try (InputStream in = StandTargetRangeGameTests.class.getClassLoader().getResourceAsStream(clientAim)) {
			helper.assertTrue(in != null, "Missing class file " + clientAim);
			String pool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
			if (!pool.contains("clientStandAim") || !pool.contains(CLIENT_STAND_AIM)) {
				wrong.add("USER-AIM the client no longer takes the Stand's aim from clientStandAim(stand, user, camera,"
						+ " vanillaPick), whose userStandAim is the 1.16 filter, but passes the running action's own");
			}
		}
		catch (IOException e) {
			wrong.add("USER-AIM cannot read " + clientAim + ": " + e);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/*
	 * 1.16 had one mouse target, what the camera entity's crosshair met (InputHandler.pickMouseTarget,
	 * EntityStandType.clientHitResult): the user's unless under manual control, whatever action the Stand ran, while a task
	 * turned the Stand to its own target on the server. The port's client kept an aim of its own from the Stand's eyes
	 * while an action aimed as the Stand without manual control (EntityActionInstance.aimAs STAND: a punch kept on its
	 * target, Heal and Revert holding an entity, Hierophant Green's strings, add-ons' bombs and Erase). Crazy Diamond at
	 * Resolve 4, 2.4 blocks behind its user and turned away from it, a snow golem on its own look; the client's choice
	 * (StandEntityPunchAbility.clientStandAim, ClientsideAim's one call for its Stand's aim, which is client-only and so
	 * pinned from its class file) is run with GameRenderer.pick's vanilla pick from the camera.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void aStandAimingAsItselfTakesItsUsersCrosshairAsIn116(GameTestHelper helper) {
		String[] rows = { "a held Revert aiming as the Stand: the vanilla pick on a cow, a creeper beside the user's look behind it",
				"a held Revert aiming as the Stand: a cow beside the user's look, in its precision box",
				"the aim assist off, a held Revert aiming as the Stand: a cow on the user's look, a creeper beside it behind",
				"no action: the vanilla pick on a cow, a creeper beside the user's look behind it",
				"under manual control, the Stand the camera, a held Revert aiming as the Stand: a cow on the user's look",
				"under manual control, the Stand the camera, no action: a cow on the user's look" };
		List<String> wrong = new ArrayList<>();
		for (String row : rows) {
			try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
				fixture.power.setResolveLevel(4);
				StandEntity stand = fixture.stand;
				boolean manual = row.startsWith("under manual control");
				boolean noAction = row.contains("no action");
				stand.setManuallyControlled(manual);
				stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z - 2.4D, 180.0F, 0.0F);
				stand.setYHeadRot(180.0F);
				LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
				if (row.startsWith("the aim assist off")) {
					fixture.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = false;
				}
				double standReach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
				double userReach = StandEntityPunchAbility.userAimReach(fixture.user, stand);
				SnowGolem golem = placed(helper, fixture, EntityType.SNOW_GOLEM, aheadOfStand(stand, 0.0D, 1.2D));
				double golemEntry = entry(stand, golem.getBoundingBox());
				boolean premise = golemEntry > 0.0D && golemEntry < standReach && entry(fixture.user, grownBox(stand, golem)) < 0.0D
						&& entry(fixture.user, stand.getBoundingBox()) < 0.0D;
				String layout = "the golem " + golemEntry + " blocks along the Stand's look (reach " + standReach + ")";
				Cow cow;
				if (row.contains("beside the user's look, in its precision box")) {
					cow = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.6D, 1.2D));
					double cowGrown = entry(fixture.user, grownBox(stand, cow));
					premise &= entry(fixture.user, cow.getBoundingBox()) < 0.0D && cowGrown > 0.0D && cowGrown < userReach;
					layout += ", the cow's precision box " + cowGrown + " blocks along the user's look, off the exact line (reach "
							+ userReach + ")";
				}
				else {
					cow = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.0D, 1.0D));
					double cowEntry = entry(fixture.user, cow.getBoundingBox());
					premise &= cowEntry > 0.0D && cowEntry < fixture.user.entityInteractionRange();
					layout += ", the cow " + cowEntry + " blocks along the user's look";
					if (row.contains("creeper")) {
						Creeper creeper = placed(helper, fixture, EntityType.CREEPER, onLook(fixture, 0.6D, 2.0D));
						double creeperGrown = entry(fixture.user, grownBox(stand, creeper));
						premise &= entry(fixture.user, creeper.getBoundingBox()) < 0.0D && creeperGrown > cowEntry
								&& creeperGrown < userReach && StandEntityPunchAbility.userPrecisionRayTarget(stand, fixture.user,
										entity -> StandEntityPunchAbility.canUserAimTarget(stand, entity)).getEntity() == creeper;
						layout += " and the creeper's precision box " + creeperGrown + ", off the exact line, which the user's"
								+ " precision ray takes";
					}
				}
				premise &= entry(stand, cow.getBoundingBox()) < 0.0D;
				if (!noAction) {
					EntityActionInstance revert = ((EntityActionType) fixture.ability).initActionOnAbilityUse(fixture.level,
							fixture.user, stand, null);
					LivingComponentAction.getComponent(stand).setAction(revert, fixture.user, SyncType.NO_SYNC);
					revert.aimAs = AimingEntity.STAND;
					premise &= LivingComponentAction.getCurEntityAction(stand) == revert;
				}
				else {
					premise &= LivingComponentAction.getCurEntityAction(stand) == null;
				}
				Entity camera = manual ? stand : fixture.user;
				ActionTarget vanillaPick = vanillaPick(camera, fixture.user);
				premise &= stand.isManuallyControlled() == manual && (manual ? vanillaPick.getEntity() == golem
						: row.contains("in its precision box") ? vanillaPick.getType() == TargetType.EMPTY
						: vanillaPick.getEntity() == cow);
				helper.assertTrue(premise, "STAND-AS-ITSELF premise: " + row + ": " + layout + "; the vanilla pick "
						+ describe(vanillaPick, camera));
				Entity expected = manual ? golem : cow;
				ActionTarget aim = StandEntityPunchAbility.clientStandAim(stand, fixture.user, camera, vanillaPick);
				if (aim.getType() != TargetType.ENTITY || aim.getEntity() != expected) {
					wrong.add("STAND-AS-ITSELF " + row + " (" + layout + "; the vanilla pick " + describe(vanillaPick, camera)
							+ "): the Stand's aim is " + describe(aim, camera) + "; 1.16's one mouse target, the "
							+ (manual ? "Stand's" : "user's") + " crosshair, took " + expected.getType().getDescription().getString());
				}
				LivingComponentAction.getComponent(stand).setAction(null, fixture.user, SyncType.NO_SYNC);
			}
		}
		String clientAim = "rotp/core/client/input/ClientsideAim.class";
		try (InputStream in = StandTargetRangeGameTests.class.getClassLoader().getResourceAsStream(clientAim)) {
			helper.assertTrue(in != null, "Missing class file " + clientAim);
			String pool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
			if (!pool.contains("clientStandAim") || !pool.contains(CLIENT_STAND_AIM) || pool.contains("actionStandAim")
					|| pool.contains("manualStandAim") || pool.contains("userStandAim")) {
				wrong.add("STAND-AS-ITSELF the client no longer takes its Stand's aim from clientStandAim(stand, user, camera,"
						+ " vanillaPick) alone, 1.16's one mouse target, or still picks one of its own");
			}
		}
		catch (IOException e) {
			wrong.add("STAND-AS-ITSELF cannot read " + clientAim + ": " + e);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/*
	 * 1.16 EntityStandType.clientHitResult (:137-143) turned a vanilla pick of the Stand itself into a miss, without the
	 * precision ray behind it. On its user's own client the Stand is never picked (StandEntity.isPickable, the port's the
	 * same), so there the crosshair passes it; only a pick made on the server, where the Stand is pickable, meets it, as
	 * here: Crazy Diamond 1.2 blocks ahead on its user's look, a cow beside the look behind it, in its precision box.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void aVanillaPickOfTheUsersOwnStandLeavesTheStandsAimEmptyAsIn116(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
			StandEntity stand = fixture.stand;
			stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.2D, 0.0F, 0.0F);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			Cow cow = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.6D, 2.4D));
			ActionTarget vanillaPick = vanillaPick(fixture.user, fixture.user);
			ActionTarget behind = StandEntityPunchAbility.userPrecisionRayTarget(stand, fixture.user,
					entity -> StandEntityPunchAbility.canUserAimTarget(stand, entity));
			helper.assertTrue(vanillaPick.getType() == TargetType.ENTITY && vanillaPick.getEntity() == stand
					&& behind.getType() == TargetType.ENTITY && behind.getEntity() == cow && StandEntityPunchAbility.aimAssist(fixture.user),
					"OWN-STAND premise: the vanilla pick " + describe(vanillaPick, fixture.user) + ", the precision ray behind it "
							+ describe(behind, fixture.user));
			ActionTarget aim = StandEntityPunchAbility.userStandAim(stand, fixture.user, vanillaPick);
			helper.assertTrue(aim.getType() == TargetType.EMPTY, "1.16 clientHitResult turned a vanilla pick of the user's own"
					+ " Stand into a miss, without the precision ray: the Stand's aim is " + describe(aim, fixture.user));
		}
		helper.succeed();
	}

	/*
	 * 1.16's client kept its one mouse target during a barrage as at any other time (InputHandler.pickMouseTarget,
	 * EntityStandType.clientHitResult: the vanilla pick, else the precision ray with friendly fire), while each hit aimed
	 * again on the server (StandEntityMeleeBarrage.standTickPerform, StandEntity.punch: aimWithThisOrUser without friendly
	 * fire). The port's client sent the barrage's own hit aim as its Stand's aim during a directional barrage
	 * (StandEntityBarrageAbility.clipDirectionalBarrageTarget). Crazy Diamond beside its user, a directional barrage
	 * running (keep attack target off); the client's choice (StandEntityPunchAbility.clientStandAim, ClientsideAim's one
	 * call, client-only and so pinned from its class file) with GameRenderer.pick's vanilla pick from the camera, then the
	 * barrage's hits with that aim synced.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void aDirectionalBarrageLeavesItsStandsClientAimOnTheMouseTargetAsIn116(GameTestHelper helper) {
		String[] rows = { "the user's own tamed wolf on the crosshair, a cow beside the look in its precision box",
				"a stone block on the crosshair, a cow beside the look before it in its precision box",
				"nothing on the crosshair, the user's own tamed wolf and a cow beside the look in their precision boxes",
				"the aim assist off: the user's own tamed wolf on the crosshair line, a cow behind it on the line",
				"under manual control, the Stand the camera: the user's own tamed wolf on the Stand's crosshair, a cow beside"
						+ " its look in its precision box" };
		List<String> wrong = new ArrayList<>();
		for (String row : rows) {
			try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
				StandEntity stand = fixture.stand;
				boolean manual = row.startsWith("under manual control");
				if (manual) {
					stand.setManuallyControlled(true);
					stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z - 2.4D, 180.0F, 0.0F);
					stand.setYHeadRot(180.0F);
				}
				else {
					// beside its user, off the user's look, the cow within its reach
					stand.moveTo(fixture.origin.x - 1.0D, fixture.origin.y, fixture.origin.z + 0.6D, 0.0F, 0.0F);
				}
				LivingComponentAction standActions = LivingComponentAction.getComponent(stand);
				standActions.entityAim.setTarget(ActionTarget.EMPTY);
				if (row.startsWith("the aim assist off")) {
					fixture.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = false;
				}
				LivingEntity camera = manual ? stand : fixture.user;
				Wolf wolf = null;
				BlockPos block = null;
				Cow cow;
				if (manual) {
					wolf = ownWolf(helper, fixture, aheadOfStand(stand, 0.0D, 2.0D));
					cow = placed(helper, fixture, EntityType.COW, aheadOfStand(stand, 0.6D, 1.2D));
				}
				else if (row.startsWith("a stone block")) {
					block = fixture.block(BlockPos.containing(fixture.origin.x, fixture.user.getEyeY(), fixture.origin.z + 2.6D));
					cow = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.6D, 1.2D));
				}
				else if (row.startsWith("nothing on the crosshair")) {
					wolf = ownWolf(helper, fixture, onLook(fixture, 0.5D, 1.0D));
					cow = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.6D, 2.0D));
				}
				else if (row.startsWith("the aim assist off")) {
					wolf = ownWolf(helper, fixture, onLook(fixture, 0.0D, 1.4D));
					cow = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.0D, 2.6D));
				}
				else {
					wolf = ownWolf(helper, fixture, onLook(fixture, 0.0D, 2.2D));
					cow = placed(helper, fixture, EntityType.COW, onLook(fixture, 0.6D, 1.2D));
				}
				// as summoned long before, so that its actions tick
				stand.summonLockTicks = 0;
				stand.gradualSummonWeaknessTicks = 0;
				EntityActionInstance barrage = ((EntityActionType) fixture.power.getAbility("barrage")).initActionOnAbilityUse(
						fixture.level, fixture.user, stand, null);
				standActions.setAction(barrage, fixture.user, SyncType.NO_SYNC);
				ActionTarget vanillaPick = vanillaPick(camera, fixture.user);
				ActionTarget barrageAim = StandEntityBarrageAbility.clipDirectionalBarrageTarget(stand, barrage, 1.0F);
				String layout = (wolf != null ? "the wolf " + entry(camera, wolf.getBoundingBox()) + ", " : "")
						+ (block != null ? "the block " + entry(camera, new AABB(block)) + ", " : "")
						+ "the cow " + entry(camera, cow.getBoundingBox()) + " (precision box " + entry(camera, grownBox(stand, cow))
						+ ") blocks along the " + (manual ? "Stand's" : "user's") + " look";
				boolean premise = LivingComponentAction.getCurEntityAction(stand) == barrage
						&& StandEntityBarrageAbility.isDirectionalBarrage(stand, barrage) && stand.isManuallyControlled() == manual
						&& (manual || entry(fixture.user, stand.getBoundingBox()) < 0.0D)
						&& barrageAim.getType() == TargetType.ENTITY && barrageAim.getEntity() == cow
						&& (wolf == null || wolf.getOwner() == fixture.user && !stand.canAttackEntity(wolf));
				Object expected;
				if (block != null) {
					premise &= vanillaPick.getType() == TargetType.BLOCK && block.equals(vanillaPick.getBlockPos())
							&& entry(camera, cow.getBoundingBox()) < 0.0D;
					expected = block;
				}
				else if (row.startsWith("nothing on the crosshair")) {
					premise &= vanillaPick.getType() == TargetType.EMPTY && entry(camera, wolf.getBoundingBox()) < 0.0D
							&& entry(camera, cow.getBoundingBox()) < 0.0D && StandEntityPunchAbility.userPrecisionRayTarget(stand,
									fixture.user, entity -> StandEntityPunchAbility.canUserAimTarget(stand, entity)).getEntity() == wolf;
					expected = wolf;
				}
				else {
					premise &= vanillaPick.getType() == TargetType.ENTITY && vanillaPick.getEntity() == wolf
							&& (row.startsWith("the aim assist off") ? entry(camera, cow.getBoundingBox()) > entry(camera, wolf.getBoundingBox())
									: entry(camera, cow.getBoundingBox()) < 0.0D);
					expected = wolf;
				}
				helper.assertTrue(premise, "DIRECTIONAL-BARRAGE premise: " + row + ": " + layout + "; the vanilla pick "
						+ describe(vanillaPick, camera) + ", the barrage's own aim " + describe(barrageAim, camera));
				ActionTarget aim = StandEntityPunchAbility.clientStandAim(stand, fixture.user, camera, vanillaPick);
				boolean matches = expected instanceof Entity entity ? aim.getType() == TargetType.ENTITY && aim.getEntity() == entity
						: aim.getType() == TargetType.BLOCK && expected.equals(aim.getBlockPos());
				if (!matches) {
					wrong.add("DIRECTIONAL-BARRAGE " + row + " (" + layout + "; the vanilla pick " + describe(vanillaPick, camera)
							+ ", the barrage's own aim " + describe(barrageAim, camera) + "): the Stand's aim is "
							+ describe(aim, camera) + "; 1.16's one mouse target, the " + (manual ? "Stand's" : "user's")
							+ " crosshair, took " + (expected instanceof Entity entity ? entity.getType().getDescription().getString()
									: "the block at " + ((BlockPos) expected).toShortString()));
				}
				// the aims the client now syncs; each hit still aims again, as 1.16's did
				standActions.entityAim.setTargetFromClient(aim, fixture.level);
				LivingComponentAction.getComponent(fixture.user).entityAim.setTargetFromClient(
						manual ? ActionTarget.EMPTY : vanillaPick, fixture.level);
				float cowHealth = cow.getHealth();
				float wolfHealth = wolf != null ? wolf.getHealth() : 0.0F;
				for (int tick = 0; tick < 12 && cow.getHealth() >= cowHealth; tick++) {
					fixture.power.setStamina(fixture.power.getMaxStamina());
					stand.tickCount++;
					standActions.tick();
				}
				if (cow.getHealth() >= cowHealth || wolf != null && wolf.getHealth() < wolfHealth
						|| block != null && !fixture.level.getBlockState(block).is(Blocks.STONE)) {
					wrong.add("DIRECTIONAL-BARRAGE " + row + ": with the client's aim synced the barrage struck "
							+ (barrage.punchedTarget != null ? describe(barrage.punchedTarget, camera) : "nothing") + " (the cow "
							+ cowHealth + " -> " + cow.getHealth() + (wolf != null ? ", the wolf " + wolfHealth + " -> " + wolf.getHealth() : "")
							+ (block != null ? ", the block " + fixture.level.getBlockState(block) : "") + "); 1.16's hits aimed"
							+ " again without friendly fire and struck the cow");
				}
				standActions.setAction(null, fixture.user, SyncType.NO_SYNC);
			}
		}
		String clientAim = "rotp/core/client/input/ClientsideAim.class";
		try (InputStream in = StandTargetRangeGameTests.class.getClassLoader().getResourceAsStream(clientAim)) {
			helper.assertTrue(in != null, "Missing class file " + clientAim);
			String pool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
			if (!pool.contains("clientStandAim") || !pool.contains(CLIENT_STAND_AIM)
					|| pool.contains("clipDirectionalBarrageTarget") || pool.contains("isDirectionalBarrage")) {
				wrong.add("DIRECTIONAL-BARRAGE the client still sends its barrage's own hit aim as its Stand's aim"
						+ " (StandEntityBarrageAbility.clipDirectionalBarrageTarget) instead of clientStandAim(stand, user, camera,"
						+ " vanillaPick), 1.16's one mouse target");
			}
		}
		catch (IOException e) {
			wrong.add("DIRECTIONAL-BARRAGE cannot read " + clientAim + ": " + e);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// the user's own tamed wolf, which its Stand may not attack (StandEntity.canAttack), with its box centred there
	private static Wolf ownWolf(GameTestHelper helper, Fixture fixture, Vec3 centre) {
		Wolf wolf = placed(helper, fixture, EntityType.WOLF, centre);
		wolf.setTame(true, false);
		wolf.setOwnerUUID(fixture.user.getUUID());
		return wolf;
	}

	// a held Revert running on the Stand, which takes the second entity for its aim (none if null) and not the first
	private static boolean heldRevertTakesOnly(Fixture fixture, StandEntity stand, Entity refused, @Nullable Entity taken) {
		EntityActionInstance revert = ((EntityActionType) fixture.ability).initActionOnAbilityUse(fixture.level, fixture.user, stand,
				null);
		LivingComponentAction.getComponent(stand).setAction(revert, fixture.user, SyncType.NO_SYNC);
		return LivingComponentAction.getCurEntityAction(stand) == revert && revert.ability instanceof StandEntityAbility revertAbility
				&& !revertAbility.canTargetEntityForAiming(stand, refused)
				&& (taken == null || revertAbility.canTargetEntityForAiming(stand, taken));
	}

	// on the look line of a Stand turned north, at its eye height, side blocks to the east and ahead blocks along it
	private static Vec3 aheadOfStand(StandEntity stand, double side, double ahead) {
		return new Vec3(stand.getX() + side, stand.getEyeY(), stand.getZ() - ahead);
	}

	private static AABB grownBox(StandEntity stand, Entity entity) {
		return HitResultUtil.standPrecisionTargetHitbox(entity.getBoundingBox().inflate(entity.getPickRadius()), stand.getPrecision());
	}

	// GameRenderer.pick with the given camera entity and the player's interaction ranges
	private static ActionTarget vanillaPick(Entity camera, Player player) {
		double blockRange = player.blockInteractionRange();
		double entityRange = player.entityInteractionRange();
		double range = Math.max(blockRange, entityRange);
		double rangeSqr = range * range;
		Vec3 eyes = camera.getEyePosition(1.0F);
		HitResult block = camera.pick(range, 1.0F, false);
		double blockSqr = block.getLocation().distanceToSqr(eyes);
		if (block.getType() != HitResult.Type.MISS) {
			rangeSqr = blockSqr;
			range = Math.sqrt(blockSqr);
		}
		Vec3 view = camera.getViewVector(1.0F);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(camera, eyes, eyes.add(view.scale(range)),
				camera.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0D),
				candidate -> !candidate.isSpectator() && candidate.isPickable(), rangeSqr);
		HitResult hit = entity != null && entity.getLocation().distanceToSqr(eyes) < blockSqr ? entity : block;
		return hit.getLocation().closerThan(eyes, hit == entity ? entityRange : blockRange)
				? ActionTarget.fromVanilla(hit) : ActionTarget.EMPTY;
	}

	private static String describe(ActionTarget target, Entity from) {
		return switch (target.getType()) {
			case ENTITY -> target.getEntity().getType().getDescription().getString() + " "
					+ Math.sqrt(target.getEntity().getBoundingBox().distanceToSqr(from.getEyePosition())) + " blocks from the eyes";
			case BLOCK -> "the block at " + target.getBlockPos().toShortString();
			default -> "nothing";
		};
	}

	// a still entity whose box is centred on the given height
	private static <T extends Entity> T placed(GameTestHelper helper, Fixture fixture, EntityType<T> type, Vec3 centre) {
		T entity = type.create(fixture.level);
		helper.assertTrue(entity != null, "STAND-RANGE premise: could not create " + type);
		entity.setNoGravity(true);
		if (entity instanceof Mob mob) {
			mob.setNoAi(true);
		}
		entity.moveTo(centre.x, centre.y - entity.getBbHeight() / 2.0D, centre.z, 180.0F, 0.0F);
		fixture.extras.add(entity);
		helper.assertTrue(fixture.level.addFreshEntity(entity), "STAND-RANGE premise: could not add " + type);
		return entity;
	}

	// on the user's look line at eye height, side blocks to the east and ahead blocks along it
	private static Vec3 onLook(Fixture fixture, double side, double ahead) {
		return new Vec3(fixture.origin.x + side, fixture.user.getEyeY(), fixture.origin.z + ahead);
	}

	// how far along the entity's look its line enters the box within 8 blocks, or -1
	private static double entry(LivingEntity looking, AABB box) {
		Vec3 eyes = looking.getEyePosition();
		return box.clip(eyes, eyes.add(looking.getLookAngle().scale(8.0D))).map(eyes::distanceTo).orElse(-1.0D);
	}

	/*
	 * Action.checkRangeAndTarget quartered the squared range when the performer, the Stand while it is out, had no line of
	 * sight to the target, whatever its user saw. As a player's press makes it: Crazy Diamond where it stands as summoned,
	 * behind its user's right, and a snow golem on the user's look ray past the 3 blocks of a Survival player's crosshair
	 * pick and within the 5-block reach, so the server's look ray finds it about 4.8 blocks from the Stand; a block that
	 * cuts only the Stand's line to the golem leaves it past the quartered 4 blocks.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondRevertDoesNotReachBeyondAWallPastTheQuarteredRange(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
			SnowGolem golem = revertibleGolem(helper, fixture, 4.65D);
			fixture.stand.updatePosition(fixture.user);
			fixture.aim(ActionTarget.EMPTY);
			String open = fixture.refusal();
			wallCuttingOnlyTheStandsSight(helper, fixture, golem);
			double fromUser = Math.sqrt(golem.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
			double fromStand = ActionTargetRange.getDistance(fixture.stand, golem.getBoundingBox());
			helper.assertTrue(fixture.stand.isFollowingUser() && fixture.stand.offsetFromUser.isIdle()
					&& fromUser > fixture.user.entityInteractionRange() && fromUser < 5.0D && fromStand > 4.0D && fromStand < 8.0D
					&& "accepted".equals(open),
					"STAND-RANGE premise: the snow golem is not past the crosshair pick and within the reach, between the Stand's"
							+ " quartered and full range, or was refused before the block: fromUser=" + fromUser + " fromStand="
							+ fromStand + " withoutTheBlock=" + open);
			String answered = fixture.refusal();
			helper.assertTrue(ITEM_OFFHAND.equals(answered), "CD_REVERT on a snow golem on the look ray " + fromUser
					+ " blocks from the user, " + fromStand + " from the idle Stand behind a block that hides it from the Stand only,"
					+ " with an empty off hand: answered " + answered + "; 1.16 quarters the squared range without the Stand's line"
					+ " of sight, drops the golem and asks for an item (" + ITEM_OFFHAND + ")");
		}
		helper.succeed();
	}

	/*
	 * The same measure for the entity the client's aim holds: a Creative player's crosshair pick reaches 5 blocks and holds
	 * the snow golem 4.3 blocks ahead, which is dropped behind a block that cuts only the Stand's line to it. (No press a
	 * player makes reaches an entity past Crazy Diamond's full 8 blocks: the Stand stays within 2 blocks of its user, the
	 * crosshair within 5, and under manual control the Stand aims itself.)
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondRevertMeasuresTheAimedEntityFromTheStand(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.CD_REVERT)) {
			fixture.user.setGameMode(GameType.CREATIVE);
			SnowGolem golem = revertibleGolem(helper, fixture, 4.65D);
			fixture.stand.updatePosition(fixture.user);
			fixture.aim(new ActionTarget(golem));
			String open = fixture.refusal();
			wallCuttingOnlyTheStandsSight(helper, fixture, golem);
			double fromUser = Math.sqrt(golem.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
			double fromStand = ActionTargetRange.getDistance(fixture.stand, golem.getBoundingBox());
			helper.assertTrue(fixture.user.isCreative() && fromUser < 5.0D && fromStand > 4.0D && fromStand < 8.0D
					&& "accepted".equals(open),
					"STAND-RANGE premise: the snow golem is not within a Creative crosshair pick, between the Stand's quartered and"
							+ " full range, or was refused before the block: fromUser=" + fromUser + " fromStand=" + fromStand
							+ " withoutTheBlock=" + open);
			String answered = fixture.refusal();
			helper.assertTrue(ITEM_OFFHAND.equals(answered), "CD_REVERT aimed at a snow golem " + fromUser + " blocks from a"
					+ " Creative user, " + fromStand + " from the idle Stand behind a block that hides it from the Stand only, with"
					+ " an empty off hand: answered " + answered + "; 1.16 measures from the Stand, drops the golem and asks for an"
					+ " item (" + ITEM_OFFHAND + ")");
		}
		helper.succeed();
	}

	// a snow golem, which Revert works on from Resolve level 3, standing on the user's look line
	private static SnowGolem revertibleGolem(GameTestHelper helper, Fixture fixture, double separation) {
		return golemAt(helper, fixture, new Vec3(fixture.origin.x, fixture.origin.y, fixture.origin.z + separation));
	}

	private static SnowGolem golemAt(GameTestHelper helper, Fixture fixture, Vec3 feet) {
		fixture.power.setResolveLevel(4);
		SnowGolem golem = EntityType.SNOW_GOLEM.create(fixture.level);
		helper.assertTrue(golem != null, "STAND-RANGE premise: could not create the snow golem");
		golem.setNoAi(true);
		golem.setNoGravity(true);
		golem.moveTo(feet.x, feet.y, feet.z, 180.0F, 0.0F);
		fixture.extras.add(golem);
		helper.assertTrue(fixture.level.addFreshEntity(golem), "STAND-RANGE premise: could not add the snow golem");
		return golem;
	}

	// a stone block on the line from the Stand's eyes to the golem's (LivingEntity.hasLineOfSight), off its user's
	private static void wallCuttingOnlyTheStandsSight(GameTestHelper helper, Fixture fixture, SnowGolem golem) {
		Vec3 from = fixture.stand.getEyePosition();
		Vec3 to = golem.getEyePosition();
		BlockPos userBlock = fixture.user.blockPosition();
		BlockPos wall = null;
		for (int step = 2; step <= 12 && wall == null; step++) {
			BlockPos pos = BlockPos.containing(from.lerp(to, step / 20.0D));
			if (pos.getX() != userBlock.getX() && pos.getZ() > userBlock.getZ()
					&& !new AABB(pos).intersects(fixture.stand.getBoundingBox())) {
				wall = fixture.block(pos);
			}
		}
		helper.assertTrue(wall != null && !fixture.stand.hasLineOfSight(golem) && fixture.user.hasLineOfSight(golem),
				"STAND-RANGE premise: no block cuts only the Stand's line of sight to the golem: " + wall + ", Stand at "
						+ fixture.stand.position());
	}

	// the same for the block anchor: no ray of the server's own takes the stone on the look ray in place of the entity gone
	@GameTest(template = "empty", batch = BATCH)
	public static void crazyDiamondAnchorDoesNotTakeABlockWhenTheAimedEntityIsGoneOnThePress(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.CD_ANCHOR)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 3.0D));
			ActionTargetAim aim = sendGoneEntity(helper, fixture, fixture.cow(6.0D));
			String answered = fixture.refusal();
			helper.assertTrue(BLOCK_TARGET.equals(answered), "CD_ANCHOR pressed on an entity the server no longer has, with"
					+ " stone on the look ray: answered " + answered + "; 1.16 empties the whole target (" + BLOCK_TARGET + ")");

			aim.setTargetFromClient(ActionTarget.EMPTY, fixture.level);
			String answeredAfter = fixture.refusal();
			helper.assertTrue("accepted".equals(answeredAfter), "CD_ANCHOR with an empty aim and stone on the look ray:"
					+ " answered " + answeredAfter + "; the stone is the target again");
		}
		helper.succeed();
	}

	// an aim on an entity the server cannot find, stored through the step both packet handlers store an aim through
	private static ActionTargetAim sendGoneEntity(GameTestHelper helper, Fixture fixture, Entity gone) {
		FriendlyByteBuf sent = new FriendlyByteBuf(Unpooled.buffer());
		ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.encode(sent, new ActionTarget(gone));
		gone.discard();
		ActionTarget received = ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID.decode(sent);
		ActionTargetAim aim = LivingComponentAction.getComponent(fixture.user).entityAim;
		aim.setTargetFromClient(received, fixture.level);
		helper.assertTrue(received.getType() == TargetType.ENTITY && aim.isClientEntityMissing()
				&& aim.getTarget().getType() == TargetType.EMPTY,
				"STAND-RANGE premise: the client's aim was not an entity or the server still found it: stored="
						+ aim.getTarget().getType());
		return aim;
	}

	// 1.16 perform: a marked item that lies on the ground (ENTITY_IS_ITEM) goes through mobFromEntity like any dropped
	// item. The item entity itself is the source and is put back as it was; nothing of mobFromInventory happens to it
	// (a fish bucket is not emptied into the world and does not come back as a water bucket).
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformTakesAMarkedItemOnTheGroundAsTheItemEntity(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID trackerId = null;
		AABB space = null;
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			space = fixture.space;
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			fixture.aim(ActionTarget.EMPTY);
			ItemStack bucket = new ItemStack(Items.COD_BUCKET);
			ItemTracker tracker = ItemTracking.getItemTracking(level).startTracking(bucket, level);
			helper.assertTrue(tracker != null, "STAND-RANGE premise: could not track the marked item");
			trackerId = tracker.trackerId;
			GEItemMarkEffect mark = ModStandAbilities.EFFECT_GE_ITEM_MARK.get().create(level);
			mark.withItemTracker(tracker);
			fixture.power.userStandEffects.addEffect(mark);
			ItemEntity dropped = fixture.item(2.5D, bucket);
			boolean targeted = GoldExperienceMarkItemAbility.getTargetedMarkedItem(fixture.power, fixture.user) == tracker;
			helper.assertTrue(tracker.getItemState() == KnownItemState.ENTITY_IS_ITEM && tracker.getAtEntity(level) == dropped
					&& targeted && fixture.user.getOffhandItem().isEmpty(),
					"STAND-RANGE premise: the marked cod bucket is not tracked as an item on the ground, is not the marked"
							+ " item the user looks at, or the user has an off-hand item: state=" + tracker.getItemState()
							+ " targeted=" + targeted);

			Vec3 itemPos = dropped.position();
			String answered = fixture.refusal();
			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			double fromItem = created.isEmpty() ? -1.0D : created.get(0).position().distanceTo(itemPos);
			int fish = level.getEntitiesOfClass(Cod.class, fixture.space.inflate(4.0D)).size();
			long marks = fixture.power.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_GE_ITEM_MARK.get()).count();
			helper.assertTrue("accepted".equals(answered) && admitted && created.size() == 1 && fromItem >= 0.0D
					&& fromItem < 0.01D && !dropped.isAlive() && fish == 0 && marks == 0,
					"GE_CREATE_LIFEFORM press on a marked cod bucket that lies on the ground: answered " + answered
							+ " admitted=" + admitted + " transformations=" + created.size() + " fromItem=" + fromItem
							+ " itemEntityLeft=" + dropped.isAlive() + " codSpawned=" + fish + " marksLeft=" + marks
							+ "; 1.16 mobFromEntity takes the item entity as it is, where it lies, and lets no fish out");

			GETransformationEntity transformation = created.get(0);
			transformation.turnBackIntoSource(fixture.user);
			for (int tick = 0; tick < 20 && transformation.isAlive(); tick++) {
				transformation.tick();
			}
			List<ItemEntity> restored = level.getEntitiesOfClass(ItemEntity.class, fixture.space, Entity::isAlive);
			helper.assertTrue(!transformation.isAlive() && restored.size() == 1
					&& restored.get(0).getItem().is(Items.COD_BUCKET) && restored.get(0).getItem().getCount() == 1,
					"GE_CREATE_LIFEFORM reverted after taking a marked cod bucket from the ground: transformationLeft="
							+ transformation.isAlive() + " item entities="
							+ restored.stream().map(ItemEntity::getItem).toList()
							+ "; 1.16 restores the item entity it took, a cod bucket");
		}
		finally {
			if (trackerId != null) ItemTracking.getItemTracking(level).stopTracking(trackerId, level);
			if (space != null) level.getEntitiesOfClass(Cod.class, space.inflate(4.0D)).forEach(Entity::discard);
		}
		helper.succeed();
	}

	// 1.16 mobFromEntity: the transformation stands where the source entity was, turned as it was, burning as long as
	// it would have and moving as it moved; the lifeform copies the transformation's position and rotation.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformKeepsTheSourceEntitysRotationFireAndMotion(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			Boat boat = fixture.boat(2.5D);
			boat.setYRot(90.0F);
			boat.setRemainingFireTicks(150);
			Vec3 motion = new Vec3(0.2D, 0.0D, 0.1D);
			boat.setDeltaMovement(motion);
			fixture.aim(new ActionTarget(boat));
			helper.assertTrue(fixture.user.getYRot() == 0.0F && boat.isOnFire(),
					"STAND-RANGE premise: the user does not face south, or the boat is not on fire");

			boolean admitted = fixture.admitted();
			if (admitted) fixture.press();
			List<GETransformationEntity> created = fixture.created();
			GETransformationEntity transformation = created.isEmpty() ? null : created.get(0);
			Entity lifeform = transformation != null ? transformation.getTransformationTarget() : null;
			float yaw = transformation != null ? transformation.getYRot() : Float.NaN;
			float lifeformYaw = lifeform != null ? lifeform.getYRot() : Float.NaN;
			int fire = transformation != null ? transformation.getRemainingFireTicks() : -1;
			Vec3 moving = transformation != null ? transformation.getDeltaMovement() : Vec3.ZERO;
			helper.assertTrue(admitted && created.size() == 1 && yaw == 90.0F && lifeformYaw == 90.0F && fire == 160
					&& moving.distanceTo(motion) < 1.0E-6D,
					"GE_CREATE_LIFEFORM press on a boat turned to yaw 90, burning for 150 more ticks and moving at " + motion
							+ ", by a user facing yaw 0: admitted=" + admitted + " transformations=" + created.size() + " yaw="
							+ yaw + " lifeformYaw=" + lifeformYaw + " fireTicks=" + fire + " motion=" + moving
							+ "; 1.16 mobFromEntity gives the transformation the source entity's rotation, sets it on fire for"
							+ " the remaining time rounded up to whole seconds (160 ticks) and gives it the entity's motion");
		}
		helper.succeed();
	}

	// 1.16 GoldExperienceCreateLifeform.checkSpecificConditions names what is missing: no material at all, an off-hand
	// item that is alive, or a block that is alive when the off hand is empty. A block that fails checkTarget
	// (unbreakable, or block breaking is switched off) is no target.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceCreateLifeformNamesTheMissingMaterialAsTheDonorDoes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean breakBlocks = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
		try (Fixture fixture = Fixture.open(helper, Kind.GE_CREATE_LIFEFORM)) {
			fixture.meetACow();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos pos = BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D);
			ItemStack alive = new ItemStack(Items.OAK_SAPLING, 2);
			ItemStack lifeless = new ItemStack(Items.IRON_INGOT, 2);
			helper.assertTrue(breakBlocks && !GoldExperienceCreateLifeformAbility.canGiveLifeTo(alive)
					&& GoldExperienceCreateLifeformAbility.canGiveLifeTo(lifeless),
					"STAND-RANGE premise: block breaking is switched off, an oak sapling can be given life, or an iron ingot"
							+ " cannot: breakBlocks=" + breakBlocks);
			List<String> wrong = new ArrayList<>();

			fixture.aim(ActionTarget.EMPTY);
			expect(wrong, "nothing aimed, empty off hand", GE_MATERIAL, fixture.refusal());
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, alive.copy());
			expect(wrong, "nothing aimed, oak saplings in the off hand", GE_MATERIAL_ITEM, fixture.refusal());

			fixture.block(pos, Blocks.OAK_LOG.defaultBlockState());
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			boolean blockInRange = ActionTargetRange.isBlockWithinRange(fixture.stand, fixture.level, pos, 100.0D);
			helper.assertTrue(blockInRange, "STAND-RANGE premise: the aimed block is out of the Stand's 10 blocks");
			expect(wrong, "oak log aimed, oak saplings in the off hand", GE_MATERIAL_ITEM, fixture.refusal());
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			expect(wrong, "oak log aimed, empty off hand", GE_MATERIAL_BLOCK, fixture.refusal());
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, lifeless.copy());
			expect(wrong, "oak log aimed, iron ingots in the off hand", "accepted", fixture.refusal());

			fixture.block(pos, Blocks.BEDROCK.defaultBlockState());
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			expect(wrong, "bedrock aimed, empty off hand", GE_MATERIAL, fixture.refusal());
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, alive.copy());
			expect(wrong, "bedrock aimed, oak saplings in the off hand", GE_MATERIAL_ITEM, fixture.refusal());

			fixture.block(pos, Blocks.STONE.defaultBlockState());
			expect(wrong, "stone aimed, oak saplings in the off hand", "accepted", fixture.refusal());
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			expect(wrong, "stone aimed, empty off hand", "accepted", fixture.refusal());

			level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(false, level.getServer());
			expect(wrong, "stone aimed, empty off hand, block breaking off", GE_MATERIAL_ONLY_ITEM, fixture.refusal());
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, alive.copy());
			expect(wrong, "stone aimed, oak saplings in the off hand, block breaking off", GE_MATERIAL_ITEM, fixture.refusal());
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, lifeless.copy());
			expect(wrong, "stone aimed, iron ingots in the off hand, block breaking off", "accepted", fixture.refusal());
			fixture.block(pos, Blocks.AIR.defaultBlockState());
			fixture.aim(ActionTarget.EMPTY);
			fixture.user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			expect(wrong, "nothing aimed, empty off hand, block breaking off", GE_MATERIAL_ONLY_ITEM, fixture.refusal());

			helper.assertTrue(wrong.isEmpty(), "GE_CREATE_LIFEFORM with no usable material answers as 1.16"
					+ " checkSpecificConditions does (a target that fails checkTarget is no target): " + String.join("; ", wrong));
		}
		finally {
			level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(breakBlocks, level.getServer());
		}
		helper.succeed();
	}

	private static void expect(List<String> wrong, String scene, String expected, String answered) {
		if (!expected.equals(answered)) {
			wrong.add(scene + ": expected " + expected + ", answered " + answered);
		}
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealReachesABlockNearTheDistantStand(GameTestHelper helper) {
		blockNearTheStand(helper, Kind.GE_BONE_MEAL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealDoesNotReachABlockFarFromTheStand(GameTestHelper helper) {
		blockFarFromTheStand(helper, Kind.GE_BONE_MEAL);
	}

	// 1.16 GoldExperienceBoneMeal.standPerform re-aims with StandEntity.aimWithThisOrUser(64, task target): a pressed
	// target out of the Stand's reach gives way to the precision ray from the user's eyes. The Stand stands beside its
	// user, the sapling on the user's look ray about 6 blocks ahead, past the Stand's reach and within the press range.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealReaimsAlongTheUsersLookRayPastTheStandsReach(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_BONE_MEAL)) {
			List<BlockPos> boneMealed = new ArrayList<>();
			Consumer<BonemealEvent> listener = event -> {
				if (event.getLevel() == fixture.level && fixture.space.contains(Vec3.atCenterOf(event.getPos()))) {
					boneMealed.add(event.getPos().immutable());
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BonemealEvent.class, listener);
			try {
				fixture.stand.moveTo(fixture.origin.x + 1.5D, fixture.origin.y, fixture.origin.z, 0.0F, 0.0F);
				BlockPos onRay = fixture.sapling(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D,
						fixture.origin.z + 6.0D));
				AABB saplingBox = fixture.level.getBlockState(onRay).getShape(fixture.level, onRay).bounds().move(onRay);
				double fromStand = ActionTargetRange.getDistance(fixture.stand, saplingBox);
				double reach = fixture.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
				Vec3 eyes = fixture.user.getEyePosition();
				boolean onLookRay = saplingBox.clip(eyes, eyes.add(fixture.user.getLookAngle().scale(64.0D))).isPresent();
				fixture.aim(new ActionTarget(onRay, Direction.NORTH));
				String pressed = fixture.refusal();
				helper.assertTrue(fromStand > reach && onLookRay && "accepted".equals(pressed),
						"STAND-RANGE premise: the sapling is not past the Stand's reach on the user's look ray, or the press on"
								+ " it was refused: fromStand=" + fromStand + " reach=" + reach + " onLookRay=" + onLookRay
								+ " press=" + pressed);
				fixture.pressAndRun();
				helper.assertTrue(boneMealed.equals(List.of(onRay)), "GE_BONE_MEAL pressed on a sapling " + fromStand
						+ " blocks from the Stand, past its reach of " + reach + ", on the user's look ray: bone-mealed "
						+ boneMealed + "; 1.16 re-aims along the user's look ray and bone-meals the sapling " + onRay);

				boneMealed.clear();
				fixture.sapling(onRay);
				BlockPos offRay = fixture.sapling(BlockPos.containing(fixture.origin.x - 2.0D, fixture.origin.y + 1.0D,
						fixture.origin.z + 5.0D));
				fixture.aim(new ActionTarget(offRay, Direction.NORTH));
				pressed = fixture.refusal();
				helper.assertTrue("accepted".equals(pressed), "STAND-RANGE premise: the press on the sapling off the look ray"
						+ " was refused: " + pressed);
				fixture.pressAndRun();
				helper.assertTrue(boneMealed.equals(List.of(onRay)), "GE_BONE_MEAL pressed on a sapling " + offRay
						+ " off the user's look ray and past the Stand's reach: bone-mealed " + boneMealed + "; 1.16 re-aims"
						+ " along the user's look ray and bone-meals the sapling there, " + onRay);
			}
			finally {
				NeoForge.EVENT_BUS.unregister(listener);
			}
		}
		helper.succeed();
	}

	// The owner's aim assist setting, off (2026-10-15): with nothing under the vanilla pick the press takes what the
	// crosshair meets within the user's 5 blocks, as the punches do, not the baby the precision boxes reach beside it.
	// The Stand stands by both babies, so the perform keeps the pressed one.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealWithoutTheAimAssistTakesTheBabyOnTheCrosshairPastTheVanillaPick(
			GameTestHelper helper) {
		boneMealWithoutTheAimAssist(helper, new Vec3(0.3D, 0.0D, 3.2D), "the press");
	}

	// The same scene with the Stand away from both babies: 1.16 standPerform aimed again, and without the aim assist
	// that aim is the crosshair too.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealWithoutTheAimAssistAimsAgainAtTheBabyOnTheCrosshair(GameTestHelper helper) {
		boneMealWithoutTheAimAssist(helper, new Vec3(2.0D, 0.0D, -2.0D), "the perform's new aim");
	}

	private static void boneMealWithoutTheAimAssist(GameTestHelper helper, Vec3 standOffset, String step) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_BONE_MEAL)) {
			fixture.stand.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(16.0D);
			fixture.stand.moveTo(fixture.origin.x + standOffset.x, fixture.origin.y + standOffset.y,
					fixture.origin.z + standOffset.z, 0.0F, 0.0F);
			Cow crosshair = fixture.babyCow(0.0D, 4.0D);
			Cow beside = fixture.babyCow(0.6D, 2.5D);
			fixture.aim(ActionTarget.EMPTY);
			Vec3 eyes = fixture.user.getEyePosition();
			Vec3 end = eyes.add(fixture.user.getLookAngle().scale(5.0D));
			double fromEyes = crosshair.getBoundingBox().clip(eyes, end).map(eyes::distanceTo).orElse(-1.0D);
			ActionTarget assisted = StandPrecisionRay.trace(fixture.user, fixture.stand);
			helper.assertTrue(fromEyes > 3.2D && fromEyes < 4.8D && beside.getBoundingBox().clip(eyes, end).isEmpty()
					&& assisted.getMainEntity() == beside,
					"STAND-RANGE premise: the baby on the crosshair is not between the vanilla pick's 3 blocks and the user's 5,"
							+ " or the aim assist would not take the baby beside it: fromEyes=" + fromEyes + " assisted=" + assisted);
			fixture.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAimAssist = false;
			int crosshairAge = crosshair.getAge();
			int besideAge = beside.getAge();
			String answered = fixture.refusal();
			fixture.pressAndRun();
			helper.assertTrue("accepted".equals(answered) && crosshair.getAge() > crosshairAge && beside.getAge() == besideAge,
					"GE_BONE_MEAL with the aim assist off, nothing under the vanilla pick, a baby cow " + fromEyes + " blocks"
							+ " along the crosshair and another beside it: answered " + answered + ", the crosshair baby aged "
							+ (crosshair.getAge() - crosshairAge) + ", the one beside " + (beside.getAge() - besideAge) + "; "
							+ step + " takes what the crosshair meets within the user's 5 blocks");
		}
		helper.succeed();
	}

	// 1.16 GoldExperienceBoneMeal.checkTarget took only a baby AnimalEntity, and standPerform aged only one: a baby
	// villager is no target
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealDoesNotAgeABabyVillager(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_BONE_MEAL)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			Villager villager = EntityType.VILLAGER.create(fixture.level);
			helper.assertTrue(villager != null, "STAND-RANGE premise: could not create the villager");
			villager.setNoAi(true);
			villager.setNoGravity(true);
			villager.setAge(-24000);
			villager.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 2.5D, 180.0F, 0.0F);
			fixture.extras.add(villager);
			helper.assertTrue(fixture.level.addFreshEntity(villager) && villager.isBaby(),
					"STAND-RANGE premise: could not add a baby villager");
			fixture.aim(new ActionTarget(villager));
			int villagerAge = villager.getAge();
			String villagerAnswer = fixture.refusal();
			fixture.pressAndRun();
			helper.assertTrue(!"accepted".equals(villagerAnswer) && villager.getAge() == villagerAge,
					"GE_BONE_MEAL on a baby villager: answered " + villagerAnswer + ", aged it by " + (villager.getAge()
							- villagerAge) + "; 1.16 takes only a baby animal");

			Cow calf = fixture.babyCow(0.0D, 2.5D);
			fixture.aim(new ActionTarget(calf));
			int calfAge = calf.getAge();
			String calfAnswer = fixture.refusal();
			fixture.pressAndRun();
			helper.assertTrue("accepted".equals(calfAnswer) && calf.getAge() > calfAge,
					"GE_BONE_MEAL on a baby cow: answered " + calfAnswer + ", aged it by " + (calf.getAge() - calfAge));

			// a press on a sapling past the Stand's reach: the perform aims again along the look ray and meets the villager
			calf.discard();
			fixture.stand.moveTo(fixture.origin.x + 1.5D, fixture.origin.y, fixture.origin.z, 0.0F, 0.0F);
			villager.moveTo(fixture.origin.x, fixture.user.getEyeY() - villager.getBbHeight() / 2.0D,
					fixture.origin.z + 3.0D, 180.0F, 0.0F);
			BlockPos sapling = fixture.sapling(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D,
					fixture.origin.z + 6.0D));
			fixture.aim(new ActionTarget(sapling, Direction.NORTH));
			ActionTarget reaim = StandPrecisionRay.trace(fixture.user, fixture.stand, 64.0D, 0.0D, true, false);
			String saplingAnswer = fixture.refusal();
			helper.assertTrue("accepted".equals(saplingAnswer) && reaim.getMainEntity() == villager,
					"STAND-RANGE premise: the press on the sapling was refused or the perform's new aim does not meet the"
							+ " villager: " + saplingAnswer + ", " + reaim);
			villagerAge = villager.getAge();
			fixture.pressAndRun();
			helper.assertTrue(villager.getAge() == villagerAge, "GE_BONE_MEAL whose perform aimed again at a baby villager"
					+ " aged it by " + (villager.getAge() - villagerAge) + "; 1.16 standPerform ages only an animal");
		}
		helper.succeed();
	}

	// The block's near face is 10.15 blocks straight ahead of the Stand's eyes. 1.16 JojoModUtil.getDistance takes half
	// the performer's width off, which leaves about 9.85.
	// Own batch: this test keeps its scene for some ticks, and the others of this class share a chunk's scene.
	@GameTest(template = "empty", batch = BATCH + "_barrier_press", timeoutTicks = 120)
	public static void hierophantBarrierReachesABlockTenBlocksAwayLessHalfTheStandsWidth(GameTestHelper helper) {
		Fixture fixture = Fixture.open(helper, Kind.HG_BARRIER);
		helper.testInfo.addListener(fixture);
		BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 12.0D));
		fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.35D, 0.0F, 0.0F);
		fixture.aim(new ActionTarget(pos, Direction.NORTH));
		Vec3 eyes = fixture.stand.getEyePosition();
		AABB cell = new AABB(pos);
		double toCell = Math.sqrt(cell.distanceToSqr(eyes));
		double halfWidth = fixture.stand.getBbWidth() / 2.0D;
		helper.assertTrue(toCell > 10.05D && toCell < 9.95D + halfWidth && eyes.x > cell.minX && eyes.x < cell.maxX
				&& eyes.y > cell.minY && eyes.y < cell.maxY,
				"STAND-RANGE premise: the block is not straight ahead of the Stand, just over 10 blocks from its eyes: toCell="
						+ toCell + " halfWidth=" + halfWidth);
		String refusal = fixture.refusal();
		ActionTarget picked = HierophantBarrierAbility.getCurrentBlockTarget(fixture.user, fixture.stand, fixture.level);
		helper.assertTrue("accepted".equals(refusal) && picked.getType() == TargetType.BLOCK && pos.equals(picked.getBlockPos()),
				"HG_BARRIER on a block " + toCell + " blocks from the Stand's eyes, about " + (toCell - halfWidth)
						+ " less half the Stand's width: answered " + refusal + " and picked " + picked
						+ "; 1.16 takes half the performer's width off the distance and accepts up to 10");
		HeldInputEntry press = fixture.press();
		helper.assertTrue(press != null && press.action instanceof HierophantBarrierAbility.BarrierDrop,
				"HG_BARRIER press did not start the barrier action on the Stand: " + (press != null ? press.action : null));
		awaitBarrier(helper, fixture, pos, 0);
	}

	private static void awaitBarrier(GameTestHelper helper, Fixture fixture, BlockPos pos, int waited) {
		List<HGBarrierEntity> barriers = fixture.level.getEntitiesOfClass(HGBarrierEntity.class, fixture.space.inflate(8.0D),
				barrier -> barrier.getOwner() == fixture.stand);
		if (barriers.isEmpty() && waited < 60) {
			helper.runAfterDelay(1, () -> awaitBarrier(helper, fixture, pos, waited + 1));
			return;
		}
		helper.assertTrue(barriers.size() == 1 && pos.equals(barriers.get(0).getOriginBlockPos()),
				"HG_BARRIER press on the block in range: after " + waited + " ticks the Stand's barriers start at "
						+ barriers.stream().map(HGBarrierEntity::getOriginBlockPos).toList() + ", expected one at " + pos);
		helper.succeed();
	}

	// 1.16 PowerBaseImpl.checkTarget:295-301 with the barrier's TargetRequirement.BLOCK: a press on an entity the server no
	// longer has empties the target and is refused; no ray of the server's own takes the stone on the look ray instead
	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantBarrierDoesNotTakeABlockWhenTheAimedEntityIsGoneOnThePress(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.HG_BARRIER)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos stone = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 3.0D));
			ActionTargetAim aim = sendGoneEntity(helper, fixture, fixture.cow(6.0D));
			String answered = fixture.refusal();
			ActionTarget picked = HierophantBarrierAbility.getCurrentBlockTarget(fixture.user, fixture.stand, fixture.level);
			helper.assertTrue(BLOCK_TARGET.equals(answered) && picked.getType() == TargetType.EMPTY,
					"HG_BARRIER pressed on an entity the server no longer has, with stone on the look ray: answered " + answered
							+ " and picked " + picked + "; 1.16 empties the whole target (" + BLOCK_TARGET + ")");

			aim.setTargetFromClient(ActionTarget.EMPTY, fixture.level);
			ActionTarget open = HierophantBarrierAbility.getCurrentBlockTarget(fixture.user, fixture.stand, fixture.level);
			helper.assertTrue("accepted".equals(fixture.refusal()) && open.getType() == TargetType.BLOCK
					&& stone.equals(open.getBlockPos()), "HG_BARRIER with an empty aim and stone on the look ray picked "
							+ open + "; the stone is the target again");
		}
		helper.succeed();
	}

	// 1.16 GoldExperienceBoneMeal.checkTarget wants a baby, but standPerform ages any AnimalEntity its new aim meets:
	// AgeableMob.ageUp clamps an adult's positive age to 0, so the breeding cooldown is gone and the animal stays
	// an adult. The press is on a sapling past the Stand's reach, so the perform aims again along the look ray.
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealSecondAimClearsAnAdultCowsBreedingCooldown(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.GE_BONE_MEAL)) {
			fixture.stand.moveTo(fixture.origin.x + 1.5D, fixture.origin.y, fixture.origin.z, 0.0F, 0.0F);
			Cow adult = fixture.adultCow(3.0D, 6000);
			BlockPos sapling = fixture.sapling(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D,
					fixture.origin.z + 6.0D));
			fixture.aim(new ActionTarget(sapling, Direction.NORTH));
			ActionTarget reaim = StandPrecisionRay.trace(fixture.user, fixture.stand, 64.0D, 0.0D, true, false);
			String answered = fixture.refusal();
			helper.assertTrue("accepted".equals(answered) && reaim.getMainEntity() == adult && adult.getAge() == 6000
					&& !adult.isBaby(), "STAND-RANGE premise: the press on the sapling was refused or the perform's new aim"
							+ " does not meet the adult cow, or the cow is not an adult with a cooldown of 6000: answered "
							+ answered + ", " + reaim + ", age " + adult.getAge());
			fixture.pressAndRun();
			helper.assertTrue(adult.getAge() == 0 && !adult.isBaby(), "GE_BONE_MEAL whose perform aimed again at an adult"
					+ " cow with a breeding cooldown of 6000 left it at age " + adult.getAge() + ", baby " + adult.isBaby()
					+ "; 1.16 standPerform ages any animal, which clears the cooldown of an adult");
		}
		helper.succeed();
	}

	// 1.16 standPerform ran ageUp on every client tracking the Stand: AgeableMob.ageUp set forcedAgeTimer to 40 when it was 0,
	// and the client's aiStep spawned a HAPPY_VILLAGER at getRandomX(1.0), getRandomY() + 0.5, getRandomZ(1.0), with no
	// motion, every 4 ticks while it counted down: 10 sparkles over 2 seconds, on a baby and on an adult the perform aimed
	// again at. A second Bone Meal while it counts down starts no other countdown; a dead animal shows no more.
	// Own batch: the scene stays for some ticks, and the others of this class share a chunk's scene.
	@GameTest(template = "empty", batch = BATCH + "_sparkles", timeoutTicks = 200)
	public static void goldExperienceBoneMealTricklesTheGrowthSparklesOnTheAnimalToItsViewers(GameTestHelper helper) {
		Fixture fixture = Fixture.open(helper, Kind.GE_BONE_MEAL);
		helper.testInfo.addListener(fixture);
		ParticleWatcher watcher = new ParticleWatcher(fixture.level);
		helper.testInfo.addListener(watcher);
		fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
		watcher.setPos(fixture.origin.x, fixture.origin.y, fixture.origin.z + 2.0D);

		Cow baby = fixture.babyCow(0.0D, 2.5D);
		fixture.aim(new ActionTarget(baby));
		long babyPressed = fixture.level.getGameTime();
		fixture.pressAndRun();
		int agedOnce = baby.getAge();
		helper.runAfterDelay(10, () -> {
			fixture.aim(new ActionTarget(baby));
			fixture.pressAndRun();
			helper.assertTrue(baby.getAge() > agedOnce + 1000, "SPARKLES premise: the second GE_BONE_MEAL on the baby cow did"
					+ " not age it: " + agedOnce + " -> " + baby.getAge());
		});
		helper.runAfterDelay(50, () -> {
			List<SentParticle> babySparkles = watcher.sparkles();
			helper.assertTrue(babySparkles.size() == 10, "GE_BONE_MEAL on a baby cow, pressed again 10 ticks later, sent its"
					+ " viewers " + describe(babySparkles, babyPressed) + " in 50 ticks; 1.16 showed 10 over 40 ticks, and the"
					+ " second press started no other countdown");
			assertTrickle(helper, babySparkles, baby, babyPressed, "a baby cow");

			baby.discard();
			watcher.clear();
			fixture.stand.moveTo(fixture.origin.x + 1.5D, fixture.origin.y, fixture.origin.z, 0.0F, 0.0F);
			Cow adult = fixture.adultCow(3.0D, 6000);
			BlockPos sapling = fixture.sapling(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D,
					fixture.origin.z + 6.0D));
			fixture.aim(new ActionTarget(sapling, Direction.NORTH));
			long adultPressed = fixture.level.getGameTime();
			fixture.pressAndRun();
			helper.assertTrue(adult.getAge() == 0, "SPARKLES premise: GE_BONE_MEAL whose perform aimed again at an adult cow"
					+ " left its age at " + adult.getAge());
			helper.runAfterDelay(10, () -> {
				int whileAlive = watcher.sparkles().size();
				adult.kill();
				long died = fixture.level.getGameTime();
				helper.runAfterDelay(40, () -> {
					List<SentParticle> adultSparkles = watcher.sparkles();
					helper.assertTrue(whileAlive > 0 && adultSparkles.size() == whileAlive
							&& adultSparkles.stream().allMatch(sent -> sent.tick() <= died),
							"GE_BONE_MEAL whose perform aimed again at an adult cow, killed 10 ticks later: " + whileAlive
									+ " sparkle(s) before it died at tick " + died + ", then " + describe(adultSparkles, adultPressed)
									+ "; 1.16 showed the sparkles on any animal it aged, and a dead one shows no more");
					assertTrickle(helper, adultSparkles, adult, adultPressed, "an adult cow");

					watcher.clear();
					fixture.aim(new ActionTarget(sapling, Direction.NORTH));
					fixture.pressAndRun();
					helper.runAfterDelay(10, () -> {
						helper.assertTrue(watcher.sparkles().isEmpty(), "GE_BONE_MEAL on a sapling with no animal on the line"
								+ " sent " + describe(watcher.sparkles(), 0L));
						helper.succeed();
					});
				});
			});
		});
	}

	// 1.16 counted the sparkles down on the client's animal, whose tick a time stop held (EntityUtilCap.updateEntityTimeStop,
	// canUpdate(false)): none came while the animal stood stopped in time, and all 10 came once time resumed. The stop is
	// put and removed within this tick, and the sparkle countdown ticked by hand.
	@GameTest(template = "empty", batch = GameTestBatches.TIME_STOP)
	public static void goldExperienceGrowthSparklesWaitWhileTheAnimalIsStoppedInTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cow cow = EntityType.COW.create(level);
		helper.assertTrue(cow != null, "SPARKLES premise: could not create a cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		cow.setNoAi(true);
		helper.assertTrue(level.addFreshEntity(cow), "SPARKLES premise: could not add the cow");
		TimeStopState state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
		GEGrowthSparkles sparkles = level.getData(ModDataAttachmentTypes.GE_GROWTH_SPARKLES.get());
		ParticleWatcher watcher = new ParticleWatcher(level);
		watcher.setPos(pos.x, pos.y, pos.z + 2.0D);
		int stop = -7_320_000;
		try {
			Method tick = GEGrowthSparkles.class.getDeclaredMethod("tick");
			tick.setAccessible(true);
			GEGrowthSparkles.start(cow);
			helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(stop, 200, 200, new ChunkPos(cow.blockPosition()), 2,
					-1, "ge_growth_sparkles_test")) && TimeStopState.shouldFreezeOnServer(cow),
					"SPARKLES premise: the cow did not stop in time");
			List<Integer> stopped = sparkleTicks(level, watcher, tick, sparkles, 12);
			state.removeInstance(stop);
			helper.assertTrue(!TimeStopState.shouldFreezeOnServer(cow), "SPARKLES premise: time did not resume for the cow");
			List<Integer> resumed = sparkleTicks(level, watcher, tick, sparkles, 48);
			helper.assertTrue(stopped.isEmpty() && resumed.equals(List.of(0, 4, 8, 12, 16, 20, 24, 28, 32, 36)),
					"The growth sparkles of a cow stopped in time came at stopped ticks " + stopped + " of 12, then at ticks "
							+ resumed + " after time resumed; 1.16 showed none while the animal was stopped, then one every 4"
							+ " ticks from the first tick after, 10 in all");
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("SPARKLES premise: cannot tick the growth sparkles", e);
		}
		finally {
			state.removeInstance(stop);
			watcher.close();
			cow.discard();
		}
		helper.succeed();
	}

	// the countdown ticked the given times with the watcher among the level's players; the ticks that sent a sparkle
	private static List<Integer> sparkleTicks(ServerLevel level, ParticleWatcher watcher, Method tick, GEGrowthSparkles sparkles,
			int ticks) throws ReflectiveOperationException {
		List<Integer> sent = new ArrayList<>();
		for (int i = 0; i < ticks; i++) {
			int before = watcher.sparkles().size();
			level.players().add(watcher);
			try {
				tick.invoke(sparkles);
			}
			finally {
				level.players().remove(watcher);
			}
			if (watcher.sparkles().size() > before) {
				sent.add(i);
			}
		}
		return sent;
	}

	// each sparkle 4 ticks after the one before, the first on the tick after the press, at a point of the 1.16 box
	// (x and z within the animal's width of its position, y from 0.5 above its feet to 0.5 above its height), alone and still
	private static void assertTrickle(GameTestHelper helper, List<SentParticle> sparkles, Cow animal, long pressed, String what) {
		double width = animal.getBbWidth();
		double height = animal.getBbHeight();
		AABB box = new AABB(animal.getX() - width, animal.getY() + 0.5D, animal.getZ() - width,
				animal.getX() + width, animal.getY() + 0.5D + height, animal.getZ() + width).inflate(1.0E-6D);
		for (int i = 0; i < sparkles.size(); i++) {
			SentParticle sent = sparkles.get(i);
			ClientboundLevelParticlesPacket packet = sent.packet();
			long after = i == 0 ? sent.tick() - pressed : sent.tick() - sparkles.get(i - 1).tick();
			boolean onTime = i == 0 ? after >= 0 && after <= 1 : after == 4;
			helper.assertTrue(onTime && box.contains(packet.getX(), packet.getY(), packet.getZ()) && packet.getCount() == 0
					&& packet.getXDist() == 0.0F && packet.getYDist() == 0.0F && packet.getZDist() == 0.0F
					&& packet.getMaxSpeed() == 0.0F, "GE_BONE_MEAL on " + what + ": sparkle " + (i + 1) + " came " + after
							+ " tick(s) after " + (i == 0 ? "the press" : "the one before") + " at (" + packet.getX() + ", "
							+ packet.getY() + ", " + packet.getZ() + ") with count " + packet.getCount() + ", spread ("
							+ packet.getXDist() + ", " + packet.getYDist() + ", " + packet.getZDist() + "), speed "
							+ packet.getMaxSpeed() + "; 1.16 put one every 4 ticks, still, in " + box);
		}
	}

	private static String describe(List<SentParticle> sparkles, long since) {
		return sparkles.size() + " HAPPY_VILLAGER packet(s) at ticks "
				+ sparkles.stream().map(sent -> sent.tick() - since + "x" + sent.packet().getCount()).toList()
				+ " (ticks after the press x particle count)";
	}

	private record SentParticle(long tick, ClientboundLevelParticlesPacket packet) {}

	// only ServerLevel.players receive sendParticles; the watcher is one only while the level's tick ends, where the
	// growth sparkles are sent, and not while the level ticks its entities and chunks
	private static final class ParticleWatcher extends FakePlayer implements GameTestListener {
		private final List<SentParticle> particles = new ArrayList<>();
		private final Consumer<LevelTickEvent.Post> join;
		private final Consumer<LevelTickEvent.Post> leave;
		private boolean closed;

		private ParticleWatcher(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "bone-meal-sparkle-watcher"));
			this.connection = new RecordingConnection(level, this);
			join = event -> {
				if (event.getLevel() == level && !level.players().contains(this)) level.players().add(this);
			};
			leave = event -> {
				if (event.getLevel() == level) level.players().remove(this);
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LevelTickEvent.Post.class, join);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LevelTickEvent.Post.class, leave);
		}

		private List<SentParticle> sparkles() {
			return particles.stream().filter(sent -> sent.packet().getParticle().getType() == ParticleTypes.HAPPY_VILLAGER).toList();
		}

		private void clear() {
			particles.clear();
		}

		private void close() {
			if (closed) return;
			closed = true;
			NeoForge.EVENT_BUS.unregister(join);
			NeoForge.EVENT_BUS.unregister(leave);
			serverLevel().players().remove(this);
		}

		@Override
		public void testStructureLoaded(GameTestInfo test) {}

		@Override
		public void testPassed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testFailed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
			close();
		}
	}

	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		private final List<SentParticle> sink;
		private final ServerLevel level;

		private RecordingConnection(ServerLevel level, ParticleWatcher player) {
			super(level.getServer(), openConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
			this.sink = player.particles;
			this.level = level;
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
			if (packet instanceof ClientboundLevelParticlesPacket particles && sink != null) {
				sink.add(new SentParticle(level.getGameTime(), particles));
			}
		}
	}

	// the same with the entity there: an ENTITY target fails TargetRequirement.BLOCK, so 1.16 refused the press instead
	// of putting the barrier on the block behind the mob
	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantBarrierPressedAtAMobDoesNotTakeTheBlockBehindIt(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.HG_BARRIER)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos stone = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D));
			Cow cow = fixture.cow(2.5D);
			cow.moveTo(fixture.origin.x, fixture.origin.y + 0.6D, fixture.origin.z + 2.5D, 180.0F, 0.0F);
			fixture.aim(new ActionTarget(cow));
			String answered = fixture.refusal();
			ActionTarget picked = HierophantBarrierAbility.getCurrentBlockTarget(fixture.user, fixture.stand, fixture.level);
			helper.assertTrue(BLOCK_TARGET.equals(answered) && picked.getType() == TargetType.EMPTY,
					"HG_BARRIER pressed at a cow with stone behind it at " + stone + ": answered " + answered + " and picked "
							+ picked + "; 1.16 refuses a press at an entity (TargetRequirement.BLOCK)");

			fixture.aim(new ActionTarget(stone, Direction.NORTH));
			helper.assertTrue("accepted".equals(fixture.refusal()), "HG_BARRIER pressed at the stone behind the cow was refused");
		}
		helper.succeed();
	}

	// 1.16 EntityStandType.clientHitResult: with nothing under the vanilla pick the pressed target is the precision ray,
	// which may be the mob beside the crosshair line; TargetRequirement.BLOCK fails for it, so the press was refused and no
	// block on the line was taken. The Stand's synced aim is that target.
	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantBarrierPressedAtAMobBesideTheCrosshairDoesNotTakeTheStoneOnIt(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.HG_BARRIER)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			BlockPos stone = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D));
			Cow beside = fixture.cow(2.5D);
			beside.moveTo(fixture.origin.x + 0.9D, fixture.origin.y + 0.6D, fixture.origin.z + 2.5D, 180.0F, 0.0F);
			Vec3 eyes = fixture.user.getEyePosition();
			Vec3 end = eyes.add(fixture.user.getLookAngle().scale(5.0D));
			helper.assertTrue(beside.getBoundingBox().clip(eyes, end).isEmpty(),
					"STAND-RANGE premise: the cow is on the user's look ray, not beside it");
			fixture.aim(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(new ActionTarget(beside));
			String answered = fixture.refusal();
			ActionTarget picked = HierophantBarrierAbility.getCurrentBlockTarget(fixture.user, fixture.stand, fixture.level);
			helper.assertTrue(BLOCK_TARGET.equals(answered) && picked.getType() == TargetType.EMPTY,
					"HG_BARRIER with nothing under the vanilla pick, the Stand's aim on a cow beside the crosshair and stone at "
							+ stone + " on it: answered " + answered + " and picked " + picked
							+ "; 1.16 pressed at the cow and refused (TargetRequirement.BLOCK)");

			LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(ActionTarget.EMPTY);
			ActionTarget open = HierophantBarrierAbility.getCurrentBlockTarget(fixture.user, fixture.stand, fixture.level);
			helper.assertTrue("accepted".equals(fixture.refusal()) && open.getType() == TargetType.BLOCK
					&& stone.equals(open.getBlockPos()), "HG_BARRIER with the Stand's aim empty and stone on the look ray picked "
							+ open + "; the stone is the target");

			LivingComponentAction.getComponent(fixture.stand).entityAim.setTarget(new ActionTarget(stone, Direction.NORTH));
			fixture.aim(ActionTarget.EMPTY);
			helper.assertTrue("accepted".equals(fixture.refusal()),
					"HG_BARRIER with the Stand's aim on the stone was refused");
		}
		helper.succeed();
	}

	// An open trapdoor is a plate on the far side of its cell: the cell is 9.8 blocks from the Stand's eyes, the plate
	// 10.6. 1.16 ActionTarget.getBoundingBox measures to the bounds of the block's shape.
	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantBarrierMeasuresToTheShapeOfABlockNotToItsCell(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.HG_BARRIER)) {
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 12.0D));
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.7D, 0.0F, 0.0F);
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String fullBlock = fixture.refusal();
			fixture.block(pos, Blocks.IRON_TRAPDOOR.defaultBlockState()
					.setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, Direction.NORTH));
			Vec3 eyes = fixture.stand.getEyePosition();
			VoxelShape shape = fixture.level.getBlockState(pos).getShape(fixture.level, pos);
			double toCell = Math.sqrt(new AABB(pos).distanceToSqr(eyes));
			double toShape = shape.isEmpty() ? -1.0D : Math.sqrt(shape.bounds().move(pos).distanceToSqr(eyes));
			double halfWidth = fixture.stand.getBbWidth() / 2.0D;
			helper.assertTrue(toCell < 9.95D && toShape - halfWidth > 10.05D && "accepted".equals(fullBlock),
					"STAND-RANGE premise: the block's cell is not within 10 blocks of the Stand with its shape beyond them, or a"
							+ " full block there was refused: toCell=" + toCell + " toShape=" + toShape + " halfWidth=" + halfWidth
							+ " fullBlock=" + fullBlock);
			String refusal = fixture.refusal();
			helper.assertTrue(TOO_FAR.equals(refusal), "HG_BARRIER on a block whose shape is at least " + (toShape - halfWidth)
					+ " blocks from the Stand, its cell " + toCell + ": answered " + refusal
					+ "; 1.16 measures to the bounds of the block's shape and refuses with " + TOO_FAR);
		}
		helper.succeed();
	}

	// A light block has no shape on the server. 1.16 has no bounds to measure to and calls such a block too far.
	@GameTest(template = "empty", batch = BATCH)
	public static void hierophantBarrierDoesNotReachABlockWithoutAShape(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper, Kind.HG_BARRIER)) {
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 5.0D));
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			String fullBlock = fixture.refusal();
			fixture.block(pos, Blocks.LIGHT.defaultBlockState());
			BlockState state = fixture.level.getBlockState(pos);
			double toCell = Math.sqrt(new AABB(pos).distanceToSqr(fixture.stand.getEyePosition()));
			helper.assertTrue(toCell < 5.0D && !state.isAir() && state.getShape(fixture.level, pos).isEmpty()
					&& "accepted".equals(fullBlock),
					"STAND-RANGE premise: the block is not a shapeless one near the Stand, or a full block there was refused:"
							+ " toCell=" + toCell + " state=" + state + " fullBlock=" + fullBlock);
			String refusal = fixture.refusal();
			helper.assertTrue(TOO_FAR.equals(refusal), "HG_BARRIER on a block without a shape " + toCell
					+ " blocks from the Stand: answered " + refusal
					+ "; 1.16 has no bounds to measure to and refuses with " + TOO_FAR);
		}
		helper.succeed();
	}

	private enum Kind {
		CD_HEAL("crazy_diamond", "heal"), CD_ANCHOR("crazy_diamond", "block_anchor_make"), CD_REVERT("crazy_diamond", "revert_state"),
		GE_HEAL_OTHER("gold_experience", "heal_other"), GE_LIFESHOT("gold_experience", "lifeshot"),
		GE_CREATE_LIFEFORM("gold_experience", "create_lifeform"), GE_BONE_MEAL("gold_experience", "bone_meal"),
		HG_BARRIER("hierophant_green", "barrier");

		final String stand;
		final String ability;

		Kind(String stand, String ability) {
			this.stand = stand;
			this.ability = ability;
		}
	}

	// the Stand stands 12 blocks from its user and the entity 3 blocks beyond it, 15 from the user
	private static void entityNearTheStand(GameTestHelper helper, Kind kind) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 12.0D, 0.0F, 0.0F);
			Cow cow = fixture.cow(15.0D);
			fixture.aim(new ActionTarget(cow));
			double fromUser = Math.sqrt(cow.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
			double fromStand = Math.sqrt(cow.getBoundingBox().distanceToSqr(fixture.stand.getEyePosition()));
			helper.assertTrue(fromUser > 8.0D && fromStand < 4.0D && fixture.stand.hasLineOfSight(cow),
					"STAND-RANGE premise: the cow is not near the Stand only: fromUser=" + fromUser + " fromStand=" + fromStand);
			String refusal = fixture.refusal();
			helper.assertTrue(!TOO_FAR.equals(refusal), kind + " on an entity " + fromStand + " blocks from the Stand and "
					+ fromUser + " from its user: refused with " + refusal + "; 1.16 measures from the Stand");
		}
		helper.succeed();
	}

	// the Stand is next to its user and the entity about 6 blocks away behind a wall: inside 8, outside the quartered 4
	private static void entityBehindAWall(GameTestHelper helper, Kind kind) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 0.5D, 0.0F, 0.0F);
			Cow cow = fixture.cow(6.5D);
			fixture.aim(new ActionTarget(cow));
			String open = fixture.refusal();
			BlockPos wall = BlockPos.containing(fixture.origin.x, fixture.origin.y, fixture.origin.z + 3.0D);
			for (int x = -1; x <= 1; x++) {
				for (int y = -1; y <= 2; y++) {
					fixture.block(wall.offset(x, y, 0));
				}
			}
			double fromUser = Math.sqrt(cow.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
			double fromStand = Math.sqrt(cow.getBoundingBox().distanceToSqr(fixture.stand.getEyePosition()));
			helper.assertTrue(fromUser < 8.0D && fromStand > 4.5D && fromStand < 7.0D && !fixture.stand.hasLineOfSight(cow)
					&& !TOO_FAR.equals(open),
					"STAND-RANGE premise: the cow is not within 8 blocks behind a wall, or was refused without the wall: fromUser="
							+ fromUser + " fromStand=" + fromStand + " withoutWall=" + open);
			String refusal = fixture.refusal();
			helper.assertTrue(TOO_FAR.equals(refusal), kind + " on an entity " + fromStand
					+ " blocks from the Stand behind a wall: answered " + refusal + "; 1.16 quarters the squared range"
					+ " without a line of sight and refuses with " + TOO_FAR);
		}
		helper.succeed();
	}

	// Nothing under the synced crosshair, so the Stand's precision ray from the user's eyes picks the entity 5 blocks
	// ahead. The 1.16 range rule holds for that pick too: with the Stand 14 blocks behind its user it is out of reach.
	private static void serverRayEntityFarFromTheStand(GameTestHelper helper, Kind kind) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			Cow cow = fixture.cow(5.0D);
			// the user's look ray runs at eye height, above a cow standing on the user's level
			cow.moveTo(fixture.origin.x, fixture.origin.y + 0.6D, fixture.origin.z + 5.0D, 180.0F, 0.0F);
			fixture.aim(ActionTarget.EMPTY);
			fixture.stand.moveTo(fixture.origin.x + 1.5D, fixture.origin.y, fixture.origin.z, 0.0F, 0.0F);
			String near = fixture.refusal(kind, cow);
			boolean nearUsed = fixture.usedOn(kind, cow);
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z - 14.0D, 0.0F, 0.0F);
			double fromUser = Math.sqrt(cow.getBoundingBox().distanceToSqr(fixture.user.getEyePosition()));
			double fromStand = Math.sqrt(cow.getBoundingBox().distanceToSqr(fixture.stand.getEyePosition()));
			helper.assertTrue(fromUser > 3.0D && fromUser < 8.0D && fromStand > 9.0D && "accepted".equals(near) && nearUsed,
					"STAND-RANGE premise: the cow is not 3 to 8 blocks ahead of the user and out of the Stand's range, or the"
							+ " same press with the Stand beside its user did not reach it: fromUser=" + fromUser + " fromStand="
							+ fromStand + " standBeside=" + near + " usedOnTheCow=" + nearUsed);
			String far = fixture.refusal(kind, cow);
			boolean farUsed = fixture.usedOn(kind, cow);
			// 1.16 PowerBaseImpl.checkTarget: for an action that needs an entity, a target out of range answers
			// Action.checkRangeAndTarget's target_too_far
			helper.assertTrue(!farUsed && (kind != Kind.GE_LIFESHOT || TOO_FAR.equals(far)), kind + " with nothing under the"
					+ " crosshair and an entity " + fromUser + " blocks ahead of the user, " + fromStand + " from the Stand:"
					+ " answered " + far + " usedOnTheEntity=" + farUsed + "; 1.16 measures from the Stand, so the entity is"
					+ " out of reach, and refuses with " + TOO_FAR);
		}
		helper.succeed();
	}

	// 1.16 EntityStandType.clientHitResult: with nothing under the crosshair a press has only the Stand's precision ray,
	// which ends at the user's reach of 5 blocks; 1.16 casts no other ray for these actions. The cow stands about 6
	// blocks ahead, within the actions' 8 and beside the Stand, then about 4.
	private static void precisionRayEntityPastTheUsersReach(GameTestHelper helper, Kind kind) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			Cow cow = fixture.cow(6.5D);
			cow.moveTo(fixture.origin.x, fixture.origin.y + 0.6D, fixture.origin.z + 6.5D, 180.0F, 0.0F);
			fixture.aim(ActionTarget.EMPTY);
			fixture.stand.moveTo(fixture.origin.x + 1.5D, fixture.origin.y, fixture.origin.z, 0.0F, 0.0F);
			Vec3 eyes = fixture.user.getEyePosition();
			double fromEyes = cow.getBoundingBox().clip(eyes, eyes.add(fixture.user.getLookAngle().scale(10.0D)))
					.map(eyes::distanceTo).orElse(-1.0D);
			helper.assertTrue(fromEyes > 5.5D && fromEyes < 7.0D, "STAND-RANGE premise: the user's look ray does not enter"
					+ " the cow between 5.5 and 7 blocks: " + fromEyes);
			String far = fixture.refusal(kind, cow);
			boolean farUsed = fixture.usedOn(kind, cow);
			helper.assertTrue(!farUsed && (kind != Kind.GE_LIFESHOT || "none".equals(far)), kind + " with nothing under the"
					+ " crosshair, the Stand out and a cow " + fromEyes + " blocks along the look ray: answered " + far
					+ " usedOnTheCow=" + farUsed + "; 1.16 has only the Stand's precision ray, which ends at the user's 5"
					+ " blocks, and casts no ray of its own");

			cow.moveTo(fixture.origin.x, fixture.origin.y + 0.6D, fixture.origin.z + 4.5D, 180.0F, 0.0F);
			String near = fixture.refusal(kind, cow);
			boolean nearUsed = fixture.usedOn(kind, cow);
			helper.assertTrue("accepted".equals(near) && nearUsed, kind + " with nothing under the crosshair, the Stand out"
					+ " and a cow about 4 blocks along the look ray: answered " + near + " usedOnTheCow=" + nearUsed
					+ "; 1.16 takes the target of the Stand's precision ray");
		}
		helper.succeed();
	}

	// the same for a grass block: about 6.5 blocks ahead, within the Stand's 10, then 3.5
	private static void precisionRayBlockPastTheUsersReach(GameTestHelper helper, Kind kind) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			fixture.aim(ActionTarget.EMPTY);
			fixture.stand.moveTo(fixture.origin.x + 1.5D, fixture.origin.y, fixture.origin.z, 0.0F, 0.0F);
			BlockPos farPos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 7.0D),
					Blocks.GRASS_BLOCK.defaultBlockState());
			double fromEyes = Math.sqrt(new AABB(farPos).distanceToSqr(fixture.user.getEyePosition()));
			boolean inStandRange = ActionTargetRange.isBlockWithinRange(fixture.stand, fixture.level, farPos, 100.0D);
			helper.assertTrue(fromEyes > 6.0D && fromEyes < 7.0D && inStandRange, "STAND-RANGE premise: the grass block is"
					+ " not between 6 and 7 blocks ahead of the user's eyes within the Stand's 10: fromEyes=" + fromEyes
					+ " inStandRange=" + inStandRange);
			String far = fixture.refusal();
			helper.assertTrue("none".equals(far), kind + " with nothing under the crosshair, the Stand out and a grass block "
					+ fromEyes + " blocks ahead: answered " + far + "; 1.16 has only the Stand's precision ray, which ends at the"
					+ " user's 5 blocks, and refuses a press without a target");

			fixture.block(farPos, Blocks.AIR.defaultBlockState());
			BlockPos nearPos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D),
					Blocks.GRASS_BLOCK.defaultBlockState());
			String near = fixture.refusal();
			helper.assertTrue("accepted".equals(near), kind + " with nothing under the crosshair, the Stand out and a grass"
					+ " block " + Math.sqrt(new AABB(nearPos).distanceToSqr(fixture.user.getEyePosition()))
					+ " blocks ahead: answered " + near + "; 1.16 takes the block on the Stand's precision ray");
		}
		helper.succeed();
	}

	// the Stand stands 12 blocks from its user and the block 3 blocks beyond it
	private static void blockNearTheStand(GameTestHelper helper, Kind kind) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 12.0D, 0.0F, 0.0F);
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 15.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			double fromUser = Math.sqrt(new AABB(pos).distanceToSqr(fixture.user.getEyePosition()));
			double fromStand = Math.sqrt(new AABB(pos).distanceToSqr(fixture.stand.getEyePosition()));
			helper.assertTrue(fromUser > 10.5D && fromStand < 4.0D,
					"STAND-RANGE premise: the block is not near the Stand only: fromUser=" + fromUser + " fromStand=" + fromStand);
			String refusal = fixture.refusal();
			helper.assertTrue(!TOO_FAR.equals(refusal), kind + " on a block " + fromStand + " blocks from the Stand and "
					+ fromUser + " from its user: refused with " + refusal + "; 1.16 measures from the Stand");
		}
		helper.succeed();
	}

	// the block is 4 blocks in front of the user, the Stand 14 blocks behind the user
	private static void blockFarFromTheStand(GameTestHelper helper, Kind kind) {
		try (Fixture fixture = Fixture.open(helper, kind)) {
			BlockPos pos = fixture.block(BlockPos.containing(fixture.origin.x, fixture.origin.y + 1.0D, fixture.origin.z + 4.0D));
			fixture.aim(new ActionTarget(pos, Direction.NORTH));
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z + 1.0D, 0.0F, 0.0F);
			String near = fixture.refusal();
			fixture.stand.moveTo(fixture.origin.x, fixture.origin.y, fixture.origin.z - 14.0D, 0.0F, 0.0F);
			double fromUser = Math.sqrt(new AABB(pos).distanceToSqr(fixture.user.getEyePosition()));
			double fromStand = Math.sqrt(new AABB(pos).distanceToSqr(fixture.stand.getEyePosition()));
			helper.assertTrue(fromUser < 5.0D && fromStand > 11.0D && !TOO_FAR.equals(near),
					"STAND-RANGE premise: the block is not near the user only, or was refused with the Stand beside it: fromUser="
							+ fromUser + " fromStand=" + fromStand + " standBeside=" + near);
			String refusal = fixture.refusal();
			helper.assertTrue(TOO_FAR.equals(refusal), kind + " on a block " + fromStand + " blocks from the Stand and "
					+ fromUser + " from its user: answered " + refusal + "; 1.16 measures from the Stand and refuses with "
					+ TOO_FAR);
		}
		helper.succeed();
	}

	private static final class Fixture implements AutoCloseable, GameTestListener {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		private final List<Entity> extras = new ArrayList<>();
		private FakePlayer user;
		private StandType standType;
		private StandPower power;
		private StandEntity stand;
		private Ability ability;
		private Cow cow;
		private Vec3 origin;
		private AABB space;
		private boolean pressed;
		private boolean closed;

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		private static Fixture open(GameTestHelper helper, Kind kind) {
			Fixture fixture = new Fixture(helper);
			try {
				fixture.setUp(kind);
				return fixture;
			}
			catch (RuntimeException | Error error) {
				fixture.close();
				throw error;
			}
		}

		private void setUp(Kind kind) {
			BlockPos template = helper.absolutePos(BlockPos.ZERO);
			ChunkPos chunk = new ChunkPos(template);
			origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 6.5D);
			space = new AABB(origin.x - 3, origin.y - 2, origin.z - 16, origin.x + 3, origin.y + 4, origin.z + 18);
			// The scene reaches into the chunks on both sides of the test's own (the Stand 14 blocks behind the user, a cow 15
			// ahead). GameTestChunkMargin makes them tick entities before a test starts; a test that finds one that does not
			// would fail on a Stand, an item entity or a barrier that never ticks, far from the cause.
			List<ChunkPos> idle = ChunkPos.rangeClosed(new ChunkPos(BlockPos.containing(space.minX, space.minY, space.minZ)),
					new ChunkPos(BlockPos.containing(space.maxX, space.maxY, space.maxZ)))
					.filter(scene -> !level.isPositionEntityTicking(scene.getWorldPosition())).toList();
			helper.assertTrue(idle.isEmpty(), "STAND-RANGE premise: the scene reaches chunks that do not tick entities: " + idle
					+ " (the test's own chunk is " + chunk + ")");
			helper.assertTrue(level.getEntities((Entity) null, space).isEmpty()
					&& BlockPos.betweenClosedStream(space).allMatch(level::isEmptyBlock),
					"STAND-RANGE premise: the scene is not empty");
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(kind.stand));
			helper.assertTrue(standType != null, "STAND-RANGE premise: missing Stand type " + kind.stand);
			user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "StandTargetRange"));
			user.setGameMode(GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "STAND-RANGE premise: could not add the user");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED && standType.summon(user, power),
					"STAND-RANGE premise: could not give and summon " + kind.stand + ": " + inserted.status());
			stand = power.getSummonedStandEntity();
			ability = power.getAbility(kind.ability);
			helper.assertTrue(stand != null && ability != null,
					"STAND-RANGE premise: the summoned Stand or its ability " + kind.ability + " is missing");
		}

		private Cow cow(double separation) {
			cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "STAND-RANGE premise: could not create the cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + separation, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(cow), "STAND-RANGE premise: could not add the cow");
			return cow;
		}

		// a baby cow with its box centred on the user's look ray, x blocks to the side of it
		private Cow babyCow(double x, double separation) {
			Cow baby = EntityType.COW.create(level);
			helper.assertTrue(baby != null, "STAND-RANGE premise: could not create the baby cow");
			baby.setNoAi(true);
			baby.setNoGravity(true);
			baby.setAge(-24000);
			baby.moveTo(origin.x + x, user.getEyeY() - baby.getBbHeight() / 2.0D, origin.z + separation, 180.0F, 0.0F);
			extras.add(baby);
			helper.assertTrue(level.addFreshEntity(baby) && baby.isBaby(), "STAND-RANGE premise: could not add the baby cow");
			return baby;
		}

		// an adult cow with the given breeding cooldown, its box centred on the user's look ray
		private Cow adultCow(double separation, int age) {
			Cow adult = EntityType.COW.create(level);
			helper.assertTrue(adult != null, "STAND-RANGE premise: could not create the adult cow");
			adult.setNoAi(true);
			adult.setNoGravity(true);
			adult.setAge(age);
			adult.moveTo(origin.x, user.getEyeY() - adult.getBbHeight() / 2.0D, origin.z + separation, 180.0F, 0.0F);
			extras.add(adult);
			helper.assertTrue(level.addFreshEntity(adult) && !adult.isBaby(), "STAND-RANGE premise: could not add the adult cow");
			return adult;
		}

		// the user has met a cow and has the stamina, so Create Lifeform has a lifeform to create
		private void meetACow() {
			Cow seen = cow(-3.0D);
			GoldExperienceLifeformState.get(user).learnLifeformsForEntity(seen, level);
			seen.discard();
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(GoldExperienceLifeformState.get(user).selectedLifeformSubtype(level).isPresent(),
					"STAND-RANGE premise: the user has no lifeform to create");
		}

		// an item entity whose box the user's look ray passes through the middle of
		private ItemEntity item(double separation, ItemStack stack) {
			ItemEntity item = new ItemEntity(level, origin.x, user.getEyeY() - 0.125D, origin.z + separation, stack,
					0.0D, 0.0D, 0.0D);
			item.setNoGravity(true);
			extras.add(item);
			helper.assertTrue(level.addFreshEntity(item), "STAND-RANGE premise: could not add the item entity");
			return item;
		}

		private Boat boat(double separation) {
			Boat boat = EntityType.BOAT.create(level);
			helper.assertTrue(boat != null, "STAND-RANGE premise: could not create the boat");
			boat.setNoGravity(true);
			boat.moveTo(origin.x, origin.y, origin.z + separation, 0.0F, 0.0F);
			extras.add(boat);
			helper.assertTrue(level.addFreshEntity(boat), "STAND-RANGE premise: could not add the boat");
			return boat;
		}

		private List<GETransformationEntity> created() {
			return level.getEntitiesOfClass(GETransformationEntity.class, space);
		}

		private BlockPos block(BlockPos pos) {
			return block(pos, Blocks.STONE.defaultBlockState());
		}

		private BlockPos block(BlockPos pos, BlockState state) {
			BlockPos owned = pos.immutable();
			blocks.putIfAbsent(owned, level.getBlockState(owned));
			level.setBlockAndUpdate(owned, state);
			return owned;
		}

		private void aim(ActionTarget target) {
			LivingComponentAction.getComponent(user).entityAim.setTarget(target);
		}

		// the server side of an admitted key press: the ability starts its action on the Stand
		private HeldInputEntry press() {
			pressed = true;
			return AbilityInput.keyPress(KEY, ability, user, null, InputMethod.CLICK, 0.0F, BufferingState.clickOnly(),
					ability.getAbilityId());
		}

		// a fresh oak sapling on a dirt block
		private BlockPos sapling(BlockPos pos) {
			block(pos.below(), Blocks.DIRT.defaultBlockState());
			return block(pos, Blocks.OAK_SAPLING.defaultBlockState());
		}

		// a real press run to its end, with the stamina its use needs
		private void pressAndRun() {
			power.setStamina(power.getMaxStamina());
			stand.summonLockTicks = 0;
			LivingComponentAction standActions = LivingComponentAction.getComponent(stand);
			LivingComponentAction userActions = LivingComponentAction.getComponent(user);
			press();
			for (int tick = 0; tick < 30; tick++) {
				standActions.tick();
				userActions.tick();
			}
			AbilityInput.keyRelease(KEY, user);
			standActions.setAction(null, user, SyncType.NO_SYNC);
			userActions.setAction(null, user, SyncType.NO_SYNC);
		}

		// the ability's answer for a wounded cow, with the material and stamina its use needs
		private String refusal(Kind kind, Cow wounded) {
			prepare(wounded);
			return refusal();
		}

		private void prepare(Cow wounded) {
			wounded.setHealth(4.0F);
			wounded.removeAllEffects();
			user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 8));
			power.setStamina(power.getMaxStamina());
		}

		// a real press run to its end: whether it spent the healing material (Heal Other), or overloaded the cow or
		// spent the shot's stamina (Lifeshot)
		private boolean usedOn(Kind kind, Cow wounded) {
			prepare(wounded);
			stand.summonLockTicks = 0;
			LivingComponentAction standActions = LivingComponentAction.getComponent(stand);
			LivingComponentAction userActions = LivingComponentAction.getComponent(user);
			press();
			for (int tick = 0; tick < 30; tick++) {
				standActions.tick();
				userActions.tick();
			}
			AbilityInput.keyRelease(KEY, user);
			standActions.setAction(null, user, SyncType.NO_SYNC);
			userActions.setAction(null, user, SyncType.NO_SYNC);
			return kind == Kind.GE_LIFESHOT
					? wounded.hasEffect(ModStatusEffects.SENSORY_OVERLOAD) || power.getStamina() < power.getMaxStamina()
					: user.getOffhandItem().getCount() < 8;
		}

		// the server side of the condition check a key press goes through before it is admitted
		private boolean admitted() {
			AvailableAbilities available = new AvailableAbilities();
			available.update(power, power.getMoveset());
			return AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.CLICK);
		}

		// the message key the ability refuses the press with; "none" for a silent refusal, "accepted" otherwise
		private String refusal() {
			ConditionCheck check = ability.checkSpecificConditions(power);
			if (check.isPositive()) {
				return "accepted";
			}
			if (check.getWarning() == null) {
				return "none";
			}
			return check.getWarning().getContents() instanceof TranslatableContents contents
					? contents.getKey() : check.getWarning().getString();
		}

		@Override
		public void close() {
			if (closed) return;
			closed = true;
			try {
				if (user != null) {
					LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
					if (pressed) AbilityInput.keyRelease(KEY, user);
				}
			}
			finally {
				try {
					if (power != null && power.isSummoned() && standType != null) {
						standType.forceUnsummon(user, power);
					}
				}
				finally {
					for (Map.Entry<BlockPos, BlockState> block : blocks.entrySet()) {
						level.setBlockAndUpdate(block.getKey(), block.getValue());
					}
					if (cow != null) cow.discard();
					extras.forEach(Entity::discard);
					if (space != null) {
						level.getEntitiesOfClass(GETransformationEntity.class, space).forEach(Entity::discard);
						level.getEntitiesOfClass(ItemEntity.class, space).forEach(Entity::discard);
					}
					if (user != null) user.discard();
				}
			}
		}

		@Override
		public void testStructureLoaded(GameTestInfo test) {}

		@Override
		public void testPassed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testFailed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
			close();
		}
	}
}
