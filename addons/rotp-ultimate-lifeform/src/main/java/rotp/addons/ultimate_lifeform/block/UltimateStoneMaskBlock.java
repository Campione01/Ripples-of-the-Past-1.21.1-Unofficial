package rotp.addons.ultimate_lifeform.block;

import javax.annotation.Nullable;

import rotp.core.block.StoneMaskBlock;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class UltimateStoneMaskBlock extends StoneMaskBlock {
    private final boolean withStone;

    public UltimateStoneMaskBlock(
            BlockBehaviour.Properties properties,
            boolean withStone) {
        super(properties);
        this.withStone = withStone;
    }

    public boolean hasAjaStone() {
        return withStone;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit) {
        if (!level.isClientSide()) {
            ItemStack mask = getMaskStack(level, pos);
            if (!player.addItem(mask)) {
                player.drop(mask, false);
            }
            level.removeBlock(pos, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos currentPos,
            BlockPos neighborPos) {
        BlockState updated = super.updateShape(
                state,
                direction,
                neighborState,
                level,
                currentPos,
                neighborPos);
        if (updated == Blocks.AIR.defaultBlockState()
                && level instanceof Level realLevel
                && !realLevel.isClientSide()) {
            Block.popResource(
                    realLevel,
                    currentPos,
                    getMaskStack(realLevel, currentPos));
        }
        return updated;
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity
                instanceof UltimateStoneMaskBlockEntity mask) {
            mask.setStack(stack);
        }
    }

    private ItemStack getMaskStack(
            BlockGetter level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity
                instanceof UltimateStoneMaskBlockEntity mask) {
            return mask.getStack();
        }
        return new ItemStack(
                withStone
                        ? UltimateLifeformAddon
                                .STONE_MASK_UL_WITH_STONE.get()
                        : UltimateLifeformAddon
                                .STONE_MASK_UL_NOSTONE.get());
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos, BlockState state) {
        return new UltimateStoneMaskBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType) {
        return blockEntityType
                        == UltimateLifeformAddon
                                .UL_STONE_MASK_BLOCK_ENTITY.get()
                ? (tickLevel,
                        tickPos,
                        tickState,
                        blockEntity) ->
                        UltimateStoneMaskBlockEntity.tick(
                                tickLevel,
                                tickPos,
                                tickState,
                                (UltimateStoneMaskBlockEntity)
                                        blockEntity)
                : null;
    }
}
