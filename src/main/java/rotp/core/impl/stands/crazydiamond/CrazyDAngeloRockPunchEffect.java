package rotp.core.impl.stands.crazydiamond;

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

import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.init.ModBlockTags;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.NBTUtil;
import rotp.core.util.objects_mc.EntityResolver;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility.BlockToFix;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility.RestoreResult;
import rotp.core.impl.stands.crazydiamond.CrazyDRestoreTerrainAbility.SourceType;
import rotp.core.impl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import rotp.core.impl.stands.crazydiamond.brokenblocks.PrevBlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
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
		if (!target.level().isClientSide()) {
			LivingEntity actualTarget = StandUtil.getStandUser(target);
			setTargetEntity(actualTarget);
			lastTargetPos = actualTarget.blockPosition();
		}
		return true;
	}

	@Override
	protected void tick() {
		if (level.isClientSide()) {
			return;
		}

		LivingEntity target = getTargetLiving();
		if (!triedSummonRockEntity) {
			if (target == null && standAction != null && standAction.punchedTarget != null) {
				Entity punched = standAction.punchedTarget.getEntity();
				if (punched instanceof LivingEntity punchedLiving) {
					target = StandUtil.getStandUser(punchedLiving);
					setTargetEntity(target);
					lastTargetPos = target.blockPosition();
				}
			}
			if (target == null && (standAction == null || standAction.isOver())) {
				remove();
				return;
			}

			if (target != null) {
				KnockbackCollisionImpact kbCollision = KnockbackCollisionImpact.getHandler(target);
				if (kbCollision != null && kbCollision.isActive()) {
					return;
				}
				ConditionCheck result = tryStartAngeloRock(target, kbCollision);
				if (!result.isPositive()) {
					ConditionCheck.sendActionFailedMessage(null, result, getStandUser());
				}
				triedSummonRockEntity = true;
			}
		}

		boolean hasBlocksToRestore = hasBlocksToRestore();
		if (triedSummonRockEntity) {
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

	public boolean preventTargetDeath() {
		if (!triedSummonRockEntity) {
			return true;
		}
		AngeloRockEntity angeloRock = getAngeloRock();
		return angeloRock != null && angeloRock.isAlive();
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

		Map<BlockPos, PrevBlockInfo> brokenStone = new HashMap<>();
		brokenBlocks.values().stream()
				.filter(block -> canUseBlock(block.state))
				.forEach(block -> brokenStone.put(block.pos, block));
		if (brokenStone.size() < 2) {
			return ConditionCheck.createNegative("angelo_no_stone_broken");
		}

		Vec3 targetPos = target.position();
		RockBlocks rockBlocks = brokenStone.values().stream()
				.map(block -> findRockBlocks(level, block, brokenStone, targetPos))
				.filter(Objects::nonNull)
				.sorted(Comparator.comparingInt((RockBlocks entry) -> entry.priority)
						.thenComparingDouble(entry -> entry.dist(targetPos)))
				.findFirst().orElse(null);
		if (rockBlocks == null) {
			return ConditionCheck.createNegative("angelo_no_stone_broken");
		}
		LivingEntity standUser = getStandUser();
		rockBlocks.resolve(level, brokenStone, targetPos, standUser == null || JojoModUtil.dropBrokenBlock(standUser));
		if (rockBlocks.lower == null || rockBlocks.upper == null) {
			return ConditionCheck.createNegative("angelo_no_stone_broken");
		}
		PrevBlockInfo blockLower = rockBlocks.lower;
		PrevBlockInfo blockUpper = rockBlocks.upper;

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

	/*
	 * 1.16 CDTurnIntoAngeloRockEffect.tryStartAngeloRock's FindBlockEntry for one broken stone: first a broken stone above
	 * or below it, the lower one within distSqr 3 of the target, then air or an unbroken stone (broken for the rock) above
	 * or below it where no other broken block was.
	 */
	@Nullable
	private RockBlocks findRockBlocks(Level level, PrevBlockInfo block, Map<BlockPos, PrevBlockInfo> brokenStone, Vec3 targetPos) {
		BlockPos posAbove = block.pos.above();
		BlockPos posBelow = block.pos.below();
		PrevBlockInfo brokenAbove = brokenStone.get(posAbove);
		if (brokenAbove != null && RockBlocks.distLower(block, targetPos) <= 3) {
			return new RockBlocks(block, brokenAbove, 1, null);
		}
		PrevBlockInfo brokenBelow = brokenStone.get(posBelow);
		if (brokenBelow != null && RockBlocks.distLower(brokenBelow, targetPos) <= 3) {
			return new RockBlocks(brokenBelow, block, 1, null);
		}
		if (!brokenBlocks.containsKey(posAbove)) {
			BlockState above = level.getBlockState(posAbove);
			if (above.isAir()) {
				return new RockBlocks(block, null, 2, null);
			}
			else if (canUseBlock(above)) {
				return new RockBlocks(block, null, 2, posAbove);
			}
		}
		if (!brokenBlocks.containsKey(posBelow)) {
			BlockState below = level.getBlockState(posBelow);
			if (below.isAir()) {
				return new RockBlocks(null, block, 2, null);
			}
			else if (canUseBlock(below)) {
				return new RockBlocks(null, block, 2, posBelow);
			}
		}
		return null;
	}

	private static class RockBlocks {
		@Nullable private PrevBlockInfo lower;
		@Nullable private PrevBlockInfo upper;
		private final int priority;
		@Nullable private final BlockPos stoneToBreak;

		private RockBlocks(@Nullable PrevBlockInfo lower, @Nullable PrevBlockInfo upper, int priority, @Nullable BlockPos stoneToBreak) {
			this.lower = lower;
			this.upper = upper;
			this.priority = priority;
			this.stoneToBreak = stoneToBreak;
		}

		private static double distLower(PrevBlockInfo block, Vec3 targetPos) {
			return targetPos.distanceToSqr(block.pos.getX() + 0.5, block.pos.getY(), block.pos.getZ() + 0.5);
		}

		private static double distUpper(PrevBlockInfo block, Vec3 targetPos) {
			return targetPos.distanceToSqr(block.pos.getX() + 0.5, block.pos.getY() + 1, block.pos.getZ() + 0.5);
		}

		private double dist(Vec3 targetPos) {
			return lower != null ? distLower(lower, targetPos) : distUpper(upper, targetPos);
		}

		// the missing block: the stone it breaks, else the broken stone nearest the target
		private void resolve(Level level, Map<BlockPos, PrevBlockInfo> brokenStone, Vec3 targetPos, boolean dropBlock) {
			if (lower == null) {
				if (stoneToBreak != null) {
					lower = breakStone(level, stoneToBreak, dropBlock);
				}
				else {
					brokenStone.remove(upper.pos);
					lower = brokenStone.values().stream().min(Comparator.comparingDouble(block -> distLower(block, targetPos))).orElse(null);
				}
			}
			else if (upper == null) {
				if (stoneToBreak != null) {
					upper = breakStone(level, stoneToBreak, dropBlock);
				}
				else {
					brokenStone.remove(lower.pos);
					upper = brokenStone.values().stream().min(Comparator.comparingDouble(block -> distUpper(block, targetPos))).orElse(null);
				}
			}
		}

		// 1.16 MCUtil.destroyBlock(world, pos, dropBrokenBlock(user), null): the broken stone is remembered like any other
		@Nullable
		private static PrevBlockInfo breakStone(Level level, BlockPos pos, boolean dropBlock) {
			level.destroyBlock(pos, dropBlock, null);
			BrokenBlocksChunkData data = BrokenBlocksChunkData.getChunkData(level, pos);
			return data != null ? data.getBrokenBlockAt(pos) : null;
		}
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
		return state.is(ModBlockTags.ANGELO_ROCK_STONE);
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
