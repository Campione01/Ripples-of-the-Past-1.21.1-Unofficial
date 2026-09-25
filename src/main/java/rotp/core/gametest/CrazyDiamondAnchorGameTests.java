package rotp.core.gametest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorBlockAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorBlockState;
import rotp.core.impl.stands.crazydiamond.CrazyDAnchorMakeAbility;
import rotp.core.impl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import rotp.core.impl.stands.crazydiamond.brokenblocks.PrevBlockInfo;
import rotp.core.init.ModGamerules;
import rotp.core.init.ModItemDataComponents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.itemtracking.ItemTracking;
import com.mojang.authlib.GameProfile;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrazyDiamondAnchorGameTests {

	private CrazyDiamondAnchorGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void stackedTrackingFailsSoft(GameTestHelper helper) {
		ItemTracking tracking = ItemTracking.getItemTracking(helper.getLevel());
		ItemStack stacked = new ItemStack(Items.STONE_SLAB, 2);
		helper.assertTrue(tracking.startTracking(stacked, helper.getLevel()) == null,
				"Tracking a stacked item returned a tracker");
		helper.assertTrue(!stacked.has(ModItemDataComponents.TRACKER_ID), "Stacked item got a tracker id");
		helper.assertTrue(tracking.startTracking(ItemStack.EMPTY, helper.getLevel()) == null,
				"Tracking an empty item returned a tracker");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void doubleSlabAnchorKeepsWholeStack(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "double_slab")) {
			BlockState doubleSlab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
			ItemStack anchor = f.makeAnchor(doubleSlab);
			helper.assertTrue(anchor.is(Items.STONE_SLAB) && anchor.getCount() == 2,
					"Double slab anchor is not the whole slab x2 drop: " + anchor);
			helper.assertTrue(!anchor.has(ModItemDataComponents.TRACKER_ID), "Stacked anchor was tracked");
			CrazyDAnchorBlockState stored = anchor.get(ModItemDataComponents.CD_ANCHOR_BLOCK_STATE);
			helper.assertTrue(stored != null && stored.state() == doubleSlab && stored.count() == 2,
					"Double slab anchor did not keep its block state and drop count");

			// a split half must not restore the whole double slab
			f.holdAndMoveBack(anchor.copyWithCount(1));
			helper.assertTrue(f.blockState() == Blocks.STONE_SLAB.defaultBlockState(),
					"Half of a double slab anchor restored " + f.blockState());
			helper.assertTrue(f.user.getMainHandItem().isEmpty(), "Half anchor was not used up");

			f.level().setBlockAndUpdate(f.blockPos, Blocks.AIR.defaultBlockState());
			f.holdAndMoveBack(anchor.copy());
			helper.assertTrue(f.blockState() == doubleSlab, "Whole anchor did not restore the double slab: " + f.blockState());
			helper.assertTrue(f.user.getMainHandItem().isEmpty(), "Restoring the double slab left anchor items behind");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void containerAnchorRestoresSavedState(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "furnace")) {
			BlockState furnace = Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
			ItemStack anchor = f.makeAnchor(furnace);
			helper.assertTrue(anchor.is(Items.FURNACE) && anchor.getCount() == 1, "Furnace anchor has the wrong drop: " + anchor);
			f.trackerId = anchor.get(ModItemDataComponents.TRACKER_ID);
			helper.assertTrue(f.trackerId != null, "Single-item anchor lost its item tracker");
			CrazyDAnchorBlockState stored = anchor.get(ModItemDataComponents.CD_ANCHOR_BLOCK_STATE);
			helper.assertTrue(stored != null && stored.state() == furnace && stored.count() == 1,
					"Furnace anchor did not keep its block state");
			// the loot roll records the furnace without its block entity (plain drop);
			// isolated as long as no record matches the anchor, as the move-back requires
			BrokenBlocksChunkData chunkData = BrokenBlocksChunkData.getExistingData(f.level(), f.blockPos);
			PrevBlockInfo record = chunkData != null ? chunkData.getBrokenBlockAt(f.blockPos) : null;
			helper.assertTrue(record == null || record.drops.size() != 1 || !ItemStack.matches(record.drops.get(0), anchor),
					"Broken-block record matches the anchor, the test no longer isolates the item state: "
					+ describe(record) + " anchor " + anchor.getComponentsPatch());

			f.holdAndMoveBack(anchor.copy());
			helper.assertTrue(f.blockState() == furnace, "Anchor restored the furnace as " + f.blockState()
					+ " (stored " + stored.state() + " x" + stored.count() + ", record " + describe(record) + ")");
			helper.assertTrue(f.user.getMainHandItem().isEmpty(), "Furnace anchor was not used up: " + f.user.getMainHandItem());
		}
		helper.succeed();
	}

	private static String describe(PrevBlockInfo record) {
		return record == null ? "none" : record.state + " drops " + record.drops;
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private final StandPower power;
		private final StandEntity stand;
		private final LivingComponentAction component;
		private final BlockPos blockPos;
		private final boolean breakBlocks;
		private UUID trackerId;

		private Fixture(GameTestHelper helper, String name) {
			this.helper = helper;
			String profileName = "CDAnchor_" + name;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
					UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.US_ASCII)), profileName));
			user.getInventory().clearContent();
			Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add anchor player");
			blockPos = helper.absolutePos(new BlockPos(2, 3, 4));
			breakBlocks = helper.getLevel().getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
			helper.getLevel().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(true, helper.getLevel().getServer());
			power = PowerClass.STAND.attachGet(user);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
			helper.assertTrue(standType != null, "Missing Crazy Diamond Stand type");
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing Crazy Diamond entity");
			// keep the stand out of the aiming ray
			stand.moveTo(origin.x - 1.5, origin.y, origin.z - 1.5);
			component = LivingComponentAction.getComponent(stand);
		}

		private ItemStack makeAnchor(BlockState state) {
			level().setBlockAndUpdate(blockPos, state);
			user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(blockPos));
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = initAction("block_anchor_make");
			helper.assertTrue(action instanceof CrazyDAnchorMakeAbility.AnchorMake, "Anchor make resolved the wrong action");
			action.actionPerformStart();
			helper.assertTrue(blockState().isAir(), "Anchor make did not remove " + state);
			ItemStack anchor = ItemStack.EMPTY;
			for (ItemStack item : user.getInventory().items) {
				if (item.has(ModItemDataComponents.ORIGINAL_POS)) {
					helper.assertTrue(anchor.isEmpty(), "Anchor make gave more than one anchor stack");
					anchor = item.copy();
				}
			}
			helper.assertTrue(!anchor.isEmpty(), "Anchor make gave no anchor item");
			user.getInventory().clearContent();
			return anchor;
		}

		private void holdAndMoveBack(ItemStack anchor) {
			user.getInventory().clearContent();
			user.setItemInHand(InteractionHand.MAIN_HAND, anchor);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = initAction("block_anchor");
			helper.assertTrue(action instanceof CrazyDAnchorBlockAbility.AnchorBlockMove, "Anchor move resolved the wrong action");
			action.actionTick();
		}

		private EntityActionInstance initAction(String abilityName) {
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Missing production action " + abilityName);
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level(), user, stand, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		private net.minecraft.server.level.ServerLevel level() {
			return helper.getLevel();
		}

		private BlockState blockState() {
			return level().getBlockState(blockPos);
		}

		@Override
		public void close() {
			level().setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
			BrokenBlocksChunkData chunkData = BrokenBlocksChunkData.getExistingData(level(), blockPos);
			if (chunkData != null) chunkData.removeBrokenBlock(blockPos);
			if (trackerId != null) ItemTracking.getItemTracking(level()).stopTracking(trackerId, level());
			helper.getLevel().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(breakBlocks, helper.getLevel().getServer());
			user.getInventory().clearContent();
			if (power.isSummoned()) standType.forceUnsummon(user, power);
			user.discard();
		}
	}
}
