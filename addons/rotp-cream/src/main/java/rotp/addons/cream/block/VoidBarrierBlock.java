package rotp.addons.cream.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class VoidBarrierBlock extends BarrierBlock {
    public static final BooleanProperty VOID_BLOCK =
            BooleanProperty.create("void_block");

    public VoidBarrierBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                defaultBlockState().setValue(VOID_BLOCK, false));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VOID_BLOCK);
    }
}
