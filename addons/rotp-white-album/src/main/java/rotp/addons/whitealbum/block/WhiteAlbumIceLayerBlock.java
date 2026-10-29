package rotp.addons.whitealbum.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WhiteAlbumIceLayerBlock extends FrostedIceBlock {
    private static final VoxelShape SHAPE =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public WhiteAlbumIceLayerBlock(
            BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void playerDestroy(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            ItemStack itemUsed) {
        super.playerDestroy(
                level,
                player,
                pos,
                state,
                blockEntity,
                itemUsed);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void melt(
            BlockState state,
            Level level,
            BlockPos pos) {
        level.removeBlock(pos, false);
    }
}
