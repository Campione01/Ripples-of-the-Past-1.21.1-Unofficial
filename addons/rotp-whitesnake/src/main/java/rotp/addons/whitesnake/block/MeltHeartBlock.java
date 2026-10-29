package rotp.addons.whitesnake.block;

import javax.annotation.Nullable;

import rotp.core.util.functions.DamageUtil;
import com.mojang.serialization.MapCodec;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.blockentity.MeltHeartBlockEntity;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MeltHeartBlock extends BaseEntityBlock {
    public static final IntegerProperty LAYERS =
            IntegerProperty.create("layers", 1, 8);
    public static final IntegerProperty AGE =
            IntegerProperty.create("age", 0, 4);
    public static final MapCodec<MeltHeartBlock> CODEC =
            simpleCodec(MeltHeartBlock::new);

    private static final VoxelShape[] SHAPE_BY_LAYER =
            new VoxelShape[9];

    static {
        SHAPE_BY_LAYER[0] = Shapes.empty();
        for (int layer = 1; layer <= 8; layer++) {
            SHAPE_BY_LAYER[layer] =
                    box(
                            0.0D,
                            0.0D,
                            0.0D,
                            16.0D,
                            layer * 2.0D,
                            16.0D);
        }
    }

    public MeltHeartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(LAYERS, 1)
                        .setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<
                            net.minecraft.world.level.block.Block,
                            BlockState>
                    builder) {
        builder.add(LAYERS, AGE);
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos position, BlockState state) {
        return new MeltHeartBlockEntity(position, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(
                        type,
                        WhitesnakeAddon.MELT_HEART_BLOCK_ENTITY.get(),
                        MeltHeartBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context) {
        return SHAPE_BY_LAYER[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context) {
        return SHAPE_BY_LAYER[state.getValue(LAYERS) - 1];
    }

    @Override
    protected VoxelShape getOcclusionShape(
            BlockState state,
            BlockGetter level,
            BlockPos position) {
        return SHAPE_BY_LAYER[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(
            BlockState state, LevelReader level, BlockPos position) {
        return true;
    }

    @Override
    protected boolean isPathfindable(
            BlockState state,
            PathComputationType pathComputationType) {
        return pathComputationType == PathComputationType.LAND
                && state.getValue(LAYERS) < 5;
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos position,
            Entity entity) {
        if (!(entity instanceof LivingEntity living)
                || !living.isAlive()
                || living instanceof WhitesnakeEntity
                || WhitesnakeAddon.hasWhitesnakePower(living)) {
            return;
        }

        living.makeStuckInBlock(
                state, new Vec3(0.8D, 0.75D, 0.8D));
        if (level.isClientSide()) {
            return;
        }
        living.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                100,
                2,
                true,
                false,
                false));
        living.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                100,
                0,
                true,
                false,
                false));
        living.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                100,
                0,
                true,
                false,
                false));
        living.setSwimming(true);
        if (living.isAlive()) {
            living.hurt(
                    DamageUtil.make(level, DamageTypes.MAGIC),
                    0.5F);
        }
    }

    @Override
    protected void randomTick(
            BlockState state,
            ServerLevel level,
            BlockPos position,
            RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= 4) {
            level.removeBlock(position, false);
        }
        else {
            level.setBlockAndUpdate(
                    position, state.setValue(AGE, age + 1));
        }
    }
}
