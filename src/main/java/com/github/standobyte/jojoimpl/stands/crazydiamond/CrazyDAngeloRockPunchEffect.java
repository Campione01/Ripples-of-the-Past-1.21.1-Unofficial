package com.github.standobyte.jojoimpl.stands.crazydiamond;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.NBTUtil;
import com.github.standobyte.jojo.util.objects_mc.EntityResolver;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreTerrainAbility.BlockToFix;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreTerrainAbility.RestoreResult;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDRestoreTerrainAbility.SourceType;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.PrevBlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CrazyDAngeloRockPunchEffect extends StandEffectInstance {
	private static final int BLOCKS_RESTORED_PER_TICK = 2;
	private boolean keepMobsInside = true;
	private boolean triedSummonRockEntity = false;
	private final EntityResolver angeloRockEntity = new EntityResolver();
	@Nullable private Map<BlockPos, PrevBlockInfo> brokenBlocks;
	private final Set<BlockPos> failedToRestore = new HashSet<>();
	private boolean restoreAllBlocksFailed;
	@Nullable private BlockPos lastTargetPos;

	public CrazyDAngeloRockPunchEffect(EntityCustomEffectType<?> effectType) {
		super(effectType);
		isFromStandAction = true;
	}
	
	@Override
	protected void start() {}

	@Override
	public boolean makesAttackNonLethal(LivingEntity target) {
		return true;
	}

	@Override
	protected void tick() {
		if (level.isClientSide()) {
			return;
		}

		LivingEntity target = getTargetLiving();
		if (!triedSummonRockEntity) {
			if (standAction == null) {
				remove();
				return;
			}
			if (standAction.getActionTicksLeft() <= 1 && standAction.punchedTarget != null) {
				Entity punched = standAction.punchedTarget.getEntity();
				if (punched instanceof LivingEntity punchedLiving) {
					target = StandUtil.getStandUser(punchedLiving);
					setTargetEntity(target);
					lastTargetPos = target.blockPosition();
					KnockbackCollisionImpact kbCollision = KnockbackCollisionImpact.getHandler(target);
					ConditionCheck result = tryStartAngeloRock(target, kbCollision);
					if (!result.isPositive()) {
						ConditionCheck.sendActionFailedMessage(null, result, getStandUser());
					}
				}
				triedSummonRockEntity = true;
			}
			else if (standAction.isOver()) {
				remove();
				return;
			}
		}

		boolean hasBlocksToRestore = hasBlocksToRestore();
		if (triedSummonRockEntity || target == null) {
			AngeloRockEntity angeloRock = getAngeloRock();
			if ((angeloRock == null || angeloRock.isFullyFormed()) && !hasBlocksToRestore) {
				remove();
				return;
			}
		}

		if (hasBlocksToRestore) {
			Entity entity = getAngeloRock();
			BlockPos centerPos = null;
			if (entity == null) {
				entity = getTarget();
			}
			if (entity != null) {
				centerPos = entity.blockPosition();
			}
			else if (lastTargetPos != null) {
				centerPos = lastTargetPos;
			}
			else {
				Entity user = getStandUser();
				if (user != null) {
					centerPos = user.blockPosition();
				}
			}

			if (centerPos != null) {
				restoreBrokenBlocks(centerPos);
			}
		}
	}

	private boolean hasBlocksToRestore() {
		return brokenBlocks != null && !brokenBlocks.isEmpty();
	}

	@Nullable
	private AngeloRockEntity getAngeloRock() {
		Entity entity = angeloRockEntity.getEntity(level);
		return entity instanceof AngeloRockEntity rock ? rock : null;
	}

	private ConditionCheck tryStartAngeloRock(LivingEntity target, KnockbackCollisionImpact kbCollision) {
		if (kbCollision == null) {
			return ConditionCheck.createNegative("angelo_no_block_broken");
		}
		List<BlockPos> brokenBlocksPos = kbCollision.blocksDestroyedByLastExplosion;
		if (brokenBlocksPos == null || brokenBlocksPos.isEmpty()) {
			return ConditionCheck.createNegative("angelo_no_block_broken");
		}

		Level level = target.level();
		brokenBlocks = brokenBlocksPos.stream()
				.map(blockPos -> {
					BrokenBlocksChunkData data = BrokenBlocksChunkData.getChunkData(level, blockPos);
					return data != null ? data.getBrokenBlockAt(blockPos) : null;
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(block -> block.pos, block -> block, (a, b) -> a));

		float hpLimit = Math.max(10, target.getHealth() * 0.05f);
		if (target.getHealth() > hpLimit) {
			return ConditionCheck.createNegative("target_too_many_health");
		}

		List<PrevBlockInfo> stoneBlocks = brokenBlocks.values().stream()
				.filter(block -> canUseBlock(block.state))
				.sorted(Comparator.comparingDouble(block -> Vec3.atCenterOf(block.pos).distanceToSqr(target.position())))
				.toList();
		if (stoneBlocks.size() < 2) {
			return ConditionCheck.createNegative("angelo_no_stone_broken");
		}

		PrevBlockInfo blockLower = stoneBlocks.get(0);
		PrevBlockInfo blockUpper = stoneBlocks.get(1);
		if (blockUpper.pos.getY() < blockLower.pos.getY()) {
			PrevBlockInfo tmp = blockLower;
			blockLower = blockUpper;
			blockUpper = tmp;
		}

		removeBrokenBlocks(level, blockLower, blockUpper);
		brokenBlocks.remove(blockLower.pos);
		brokenBlocks.remove(blockUpper.pos);

		if (!blockUpper.pos.equals(blockLower.pos.above())) {
			blockUpper = new PrevBlockInfo(blockLower.pos.above(), blockUpper.state, blockUpper.drops);
		}
		if (!blockLower.pos.equals(blockUpper.pos.below())) {
			blockLower = new PrevBlockInfo(blockUpper.pos.below(), blockLower.state, blockLower.drops);
		}

		Direction angeloRockFace = Direction.fromYRot(target.getYRot());
		AngeloRockEntity angeloRock = AngeloRockEntity.turnIntoRock(level, target,
				Vec3.atBottomCenterOf(blockLower.pos), angeloRockFace.toYRot(), blockLower, blockUpper);
		if (angeloRock != null) {
			angeloRock.keepMobInside = keepMobsInside;
			angeloRockEntity.setEntity(angeloRock);
			LivingEntity user = getStandUser();
			if (user != null) {
				JojoModUtil.sayVoiceLine(user, ModSoundEvents.JOSUKE_PRAY_FOR_ETERNITY);
			}
			List<ItemStack> itemsSource = itemsSource(angeloRock.blockPosition());
			List<ItemStack> blockDrops = new ArrayList<>();
			CrazyDRestoreTerrainAbility.consumeNeededItems(blockUpper.drops, itemsSource, blockDrops);
			CrazyDRestoreTerrainAbility.consumeNeededItems(blockLower.drops, itemsSource, blockDrops);
			angeloRock.setBlockDrops(blockDrops);
		}
		return ConditionCheck.POSITIVE;
	}

	private void restoreBrokenBlocks(BlockPos center) {
		if (!(level instanceof ServerLevel serverLevel) || brokenBlocks == null || brokenBlocks.isEmpty()) {
			return;
		}

		List<PrevBlockInfo> blocksToTry = brokenBlocks.values().stream()
				.filter(block -> !failedToRestore.contains(block.pos))
				.sorted(Comparator.comparingInt(block -> block.pos.distManhattan(center)))
				.limit(BLOCKS_RESTORED_PER_TICK)
				.toList();
		if (blocksToTry.isEmpty()) {
			onRestoreAttemptFailed(blocksToTry);
			return;
		}

		Entity trackedEntity = getAngeloRock();
		if (trackedEntity == null) {
			trackedEntity = getStandUser();
		}
		if (trackedEntity == null) {
			onRestoreAttemptFailed(blocksToTry);
			return;
		}

		LivingEntity user = getStandUser();
		boolean creative = user != null && !JojoModUtil.dropBrokenBlock(user);
		RestoreResult result = CrazyDRestoreTerrainAbility.restoreBlocks(serverLevel, trackedEntity,
				blocksToTry.stream().map(block -> new BlockToFix<>(block, block.pos)),
				BLOCKS_RESTORED_PER_TICK, center,
				creative, false, false,
				null, itemsSource(center));

		if (!result.blocksToForget.isEmpty()) {
			result.blocksToForget.forEach(brokenBlocks::remove);
			restoreAllBlocksFailed = false;
		}
		else if (result.isRestoring) {
			restoreAllBlocksFailed = false;
			failedToRestore.clear();
		}
		else {
			onRestoreAttemptFailed(blocksToTry);
		}
	}

	private void onRestoreAttemptFailed(List<PrevBlockInfo> blocksTried) {
		if (brokenBlocks == null || brokenBlocks.isEmpty()) {
			return;
		}
		restoreAllBlocksFailed |= failedToRestore.isEmpty();
		failedToRestore.addAll(blocksTried.stream().map(block -> block.pos).toList());
		if (blocksTried.isEmpty() || failedToRestore.size() >= brokenBlocks.size()) {
			if (restoreAllBlocksFailed) {
				brokenBlocks.clear();
			}
			else {
				failedToRestore.clear();
			}
		}
	}

	private List<ItemStack> itemsSource(BlockPos center) {
		LivingEntity user = getStandUser();
		Entity target = getTarget();
		AABB area = new AABB(center).inflate(8);
		return CrazyDRestoreTerrainAbility.sourceItemStacks(area, Vec3.atBottomCenterOf(center), user, level,
				target instanceof Player ? SourceType.PLAYER_INVENTORY.from(target) : null,
				SourceType.MOB_HELD.fromAllNearby(),
				SourceType.ITEM_ENTITY.fromAllNearby(),
				user instanceof Player ? SourceType.PLAYER_INVENTORY.from(user) : null);
	}

	private static void removeBrokenBlocks(Level level, PrevBlockInfo blockLower, PrevBlockInfo blockUpper) {
		BrokenBlocksChunkData lowerData = BrokenBlocksChunkData.getChunkData(level, blockLower.pos);
		if (lowerData != null) {
			lowerData.removeBrokenBlock(blockLower.pos);
		}
		BrokenBlocksChunkData upperData = BrokenBlocksChunkData.getChunkData(level, blockUpper.pos);
		if (upperData != null) {
			upperData.removeBrokenBlock(blockUpper.pos);
		}
	}

	private static boolean canUseBlock(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER);
	}

	@Override
	protected void writeAdditionalSaveData(CompoundTag nbt) {
		super.writeAdditionalSaveData(nbt);
		nbt.putBoolean("TriedSummonRock", triedSummonRockEntity);
		nbt.putBoolean("SaveMob", keepMobsInside);
		angeloRockEntity.saveNbt(nbt, "Entity");
		if (brokenBlocks != null && !brokenBlocks.isEmpty()) {
			ListTag blocksBrokenNbt = new ListTag();
			for (PrevBlockInfo block : brokenBlocks.values()) {
				CompoundTag blockNbt = block.toNBT(level.registryAccess(), true);
				if (blockNbt != null) {
					blocksBrokenNbt.add(blockNbt);
				}
			}
			nbt.put("FixBlocks", blocksBrokenNbt);
		}
		NBTUtil.put(nbt, "LastTargetPos", lastTargetPos, BlockPos.CODEC);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		triedSummonRockEntity = nbt.getBoolean("TriedSummonRock");
		if (nbt.contains("SaveMob")) {
			keepMobsInside = nbt.getBoolean("SaveMob");
		}
		angeloRockEntity.loadNbt(nbt, "Entity");

		brokenBlocks = null;
		ListTag blocksBrokenNbt = nbt.getList("FixBlocks", Tag.TAG_COMPOUND);
		if (!blocksBrokenNbt.isEmpty()) {
			brokenBlocks = new HashMap<>();
			for (Tag blockNbt : blocksBrokenNbt) {
				PrevBlockInfo block = PrevBlockInfo.fromNBT((CompoundTag) blockNbt, level.registryAccess(), true);
				if (block != null) {
					brokenBlocks.put(block.pos, block);
				}
			}
		}
		lastTargetPos = NBTUtil.getOptional(nbt, "LastTargetPos", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void stop() {
		AngeloRockEntity angeloRock = getAngeloRock();
		if (angeloRock != null && !angeloRock.isFullyFormed()) {
			angeloRock.breakRock();
		}
	}

}
