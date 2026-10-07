package rotp.core.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
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
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 Action.checkRangeAndTarget measures a Stand action's target from its performer, and StandAction.getPerformer is
 * the Stand whenever it is out: 8 blocks to an entity (4 without a line of sight from the performer), 10 to a block,
 * each as JojoModUtil.getDistance. A target beyond that is refused with target_too_far.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandTargetRangeGameTests {
	private static final String BATCH = "stand_target_range";
	private static final String TOO_FAR = "jojo.message.action_condition.target_too_far";
	private static final String GE_MATERIAL = "jojo.message.action_condition.ge_lifeform_material";
	private static final short KEY = 23;

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

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealReachesABlockNearTheDistantStand(GameTestHelper helper) {
		blockNearTheStand(helper, Kind.GE_BONE_MEAL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceBoneMealDoesNotReachABlockFarFromTheStand(GameTestHelper helper) {
		blockFarFromTheStand(helper, Kind.GE_BONE_MEAL);
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
		CD_HEAL("crazy_diamond", "heal"), CD_ANCHOR("crazy_diamond", "block_anchor_make"),
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

	// Nothing under the synced crosshair, so the server's own ray from the user's eyes picks the entity 5 blocks ahead.
	// The 1.16 range rule holds for that pick too: with the Stand 14 blocks behind its user the entity is out of reach.
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
			helper.assertTrue(!farUsed && (kind != Kind.GE_LIFESHOT || "none".equals(far)), kind + " with nothing under the"
					+ " crosshair and an entity " + fromUser + " blocks ahead of the user, " + fromStand + " from the Stand:"
					+ " answered " + far + " usedOnTheEntity=" + farUsed + "; 1.16 measures from the Stand, so the entity is"
					+ " out of reach");
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
					if (space != null) {
						level.getEntitiesOfClass(GETransformationEntity.class, space).forEach(Entity::discard);
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
