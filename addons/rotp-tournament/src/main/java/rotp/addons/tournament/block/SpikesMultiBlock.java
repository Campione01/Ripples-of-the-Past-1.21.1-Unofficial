package rotp.addons.tournament.block;

import javax.annotation.Nullable;

import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.BleedingEffect;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.impl.powers.hamon.abilities.HamonProtectionAbility;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SpikesMultiBlock extends Block {
    public static final MapCodec<SpikesMultiBlock> CODEC =
            simpleCodec(SpikesMultiBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF =
            BlockStateProperties.DOUBLE_BLOCK_HALF;

    protected static final VoxelShape BOTTOM_SHAPE =
            Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
    protected static final VoxelShape TOP_SHAPE =
            Block.box(4.5D, 0.0D, 4.5D, 11.5D, 14.0D, 11.5D);
    protected static final VoxelShape TOP_SHAPE_FOR_UNDEADS =
            Block.box(4.5D, 0.0D, 4.5D, 11.5D, 10.0D, 11.5D);

    public SpikesMultiBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends SpikesMultiBlock> codec() {
        return CODEC;
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity) {
        DamageSource contactSource = entity.damageSources().cactus();
        if (entity.isInvulnerableTo(contactSource)
                || Math.ceil(entity.getY())
                        != pos.above().getY()
                || state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return;
        }

        if (entity instanceof LivingEntity living && living.isAlive()) {
            if (HamonProtectionAbility.preventBlockDamage(
                    living, contactSource, 1.0F)) {
                return;
            }
            if (JojoDefinitions.isUndeadOrVampiric(living)) {
                return;
            }
            if (JojoDefinitions.canBleed(living)) {
                if (living instanceof Player player
                        && player.isCreative()) {
                    return;
                }
                MobEffectInstance current =
                        living.getEffect(ModStatusEffects.BLEEDING);
                int currentAmplifier =
                        current != null ? current.getAmplifier() : -1;
                int requestedAmplifier =
                        (int) Math.floor(
                                (BleedingEffect
                                                .getMaxHealthWithoutBleeding(
                                                        living)
                                        - living.getHealth())
                                        / 4.0F)
                                - 1;
                int amplifier = BleedingEffect.limitAmplifier(
                        living, requestedAmplifier);
                if (living.invulnerableTime == 0
                        && currentAmplifier != amplifier
                        && amplifier != -1) {
                    living.addEffect(new MobEffectInstance(
                            ModStatusEffects.BLEEDING,
                            150,
                            amplifier,
                            false,
                            false,
                            true));
                    level.playSound(
                            null,
                            living.blockPosition(),
                            ModSoundEvents.STONE_MASK_ACTIVATION.get(),
                            SoundSource.BLOCKS,
                            1.0F,
                            1.0F);
                    living.invulnerableTime = 20;
                    return;
                }
            }
        }
        entity.hurt(contactSource, 1.0F);
    }

    @Override
    public SoundType getSoundType(
            BlockState state,
            LevelReader level,
            BlockPos pos,
            @Nullable Entity entity) {
        if (entity instanceof LivingEntity living
                && JojoDefinitions.isUndeadOrVampiric(living)) {
            return SoundType.SLIME_BLOCK;
        }
        return super.getSoundType(state, level, pos, entity);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? BOTTOM_SHAPE
                : TOP_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        Entity entity = context
                        instanceof EntityCollisionContext entityContext
                ? entityContext.getEntity()
                : null;
        if (entity instanceof LivingEntity living
                && JojoDefinitions.isUndeadOrVampiric(living)
                && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return TOP_SHAPE_FOR_UNDEADS;
        }
        return getShape(state, level, pos, context);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER)
                        == (direction == Direction.UP)) {
            return neighborState.is(this)
                            && neighborState.getValue(HALF) != half
                    ? state
                    : Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER
                && direction == Direction.DOWN) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(
                state,
                direction,
                neighborState,
                level,
                pos,
                neighborPos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return pos.getY() < level.getMaxBuildHeight() - 1
                        && level.getBlockState(pos.above())
                                .canBeReplaced(context)
                ? super.getStateForPlacement(context)
                : null;
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack) {
        level.setBlock(
                pos.above(),
                defaultBlockState()
                        .setValue(HALF, DoubleBlockHalf.UPPER),
                3);
    }

    @Override
    public BlockState playerWillDestroy(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player) {
        if (!level.isClientSide()) {
            if (player.isCreative()) {
                preventCreativeDropFromBottomPart(
                        level, pos, state, player);
            } else {
                dropResources(
                        state,
                        level,
                        pos,
                        null,
                        player,
                        player.getMainHandItem());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            ItemStack stack) {
        super.playerDestroy(
                level,
                player,
                pos,
                Blocks.AIR.defaultBlockState(),
                blockEntity,
                stack);
    }

    protected static void preventCreativeDropFromBottomPart(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return;
        }
        BlockPos lowerPos = pos.below();
        BlockState lower = level.getBlockState(lowerPos);
        if (lower.is(state.getBlock())
                && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.setBlock(
                    lowerPos,
                    Blocks.AIR.defaultBlockState(),
                    35);
            level.levelEvent(
                    player,
                    2001,
                    lowerPos,
                    Block.getId(lower));
        }
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    protected boolean isPathfindable(
            BlockState state,
            PathComputationType pathType) {
        return false;
    }
}
