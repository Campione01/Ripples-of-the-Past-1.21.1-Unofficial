package com.github.standobyte.jojo.block;

import static net.minecraft.world.level.block.BedBlock.OCCUPIED;
import static net.minecraft.world.level.block.BedBlock.PART;

import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModParticles;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.mechanics.coffin.PlayerCoffinSleepData;
import com.github.standobyte.jojo.network.s2c.BloodParticlesPacket;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.reflection.CommonReflection;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class WoodenCoffinBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<WoodenCoffinBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			DyeColor.CODEC.fieldOf("color").forGetter(WoodenCoffinBlock::getColor),
			propertiesCodec()).apply(instance, WoodenCoffinBlock::new));
	public static final BooleanProperty CLOSED = BooleanProperty.create("coffin_lid_closed");

	private static final VoxelShape WALL_1 = Block.box(0, 0, 0, 2, 10, 16);
	private static final VoxelShape WALL_2 = Block.box(14, 0, 0, 16, 10, 16);
	private static final VoxelShape WALL_3 = Block.box(0, 0, 0, 16, 10, 2);
	private static final VoxelShape WALL_4 = Block.box(0, 0, 14, 16, 10, 16);
	private static final VoxelShape BOTTOM = Block.box(0, 0, 0, 16, 2, 16);
	private static final VoxelShape SHAPE_CLOSED = Block.box(0, 0, 0, 16, 13, 16);
	private static final VoxelShape SHAPE_OPEN_W = Shapes.or(BOTTOM, WALL_2, WALL_3, WALL_4);
	private static final VoxelShape SHAPE_OPEN_E = Shapes.or(BOTTOM, WALL_1, WALL_3, WALL_4);
	private static final VoxelShape SHAPE_OPEN_N = Shapes.or(BOTTOM, WALL_1, WALL_2, WALL_4);
	private static final VoxelShape SHAPE_OPEN_S = Shapes.or(BOTTOM, WALL_1, WALL_2, WALL_3);

	private final DyeColor color;

	public WoodenCoffinBlock(DyeColor color, BlockBehaviour.Properties properties) {
		super(properties);
		this.color = color;
		registerDefaultState(stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(PART, BedPart.FOOT)
				.setValue(OCCUPIED, false)
				.setValue(CLOSED, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
		return true;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) {
			if (!BedBlock.canSetSpawn(level)) {
				spawnBadDimensionBloodParticles(level, pos);
			}
			return InteractionResult.CONSUME;
		}

		if (state.getValue(PART) != BedPart.HEAD) {
			pos = pos.relative(state.getValue(FACING));
			state = level.getBlockState(pos);
			if (!state.is(this)) {
				return InteractionResult.CONSUME;
			}
		}

		if (!BedBlock.canSetSpawn(level)) {
			explodeBadDimensionCoffin(level, pos, state);
			return InteractionResult.SUCCESS;
		}

		sleepInsideCoffin(player, level, pos, state, false);
		return InteractionResult.SUCCESS;
	}

	private static void spawnBadDimensionBloodParticles(Level level, BlockPos pos) {
		RandomSource random = level.random;
		for (int i = 0; i < 256; i++) {
			level.addParticle(ModParticles.BLOOD.get(),
					pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
					(random.nextDouble() - 0.5D) * 1.5D,
					(random.nextDouble() - 0.5D) * 1.5D,
					(random.nextDouble() - 0.5D) * 1.5D);
		}
	}

	private void explodeBadDimensionCoffin(Level level, BlockPos headPos, BlockState headState) {
		level.removeBlock(headPos, false);
		BlockPos footPos = headPos.relative(headState.getValue(FACING).getOpposite());
		if (level.getBlockState(footPos).is(this)) {
			level.removeBlock(footPos, false);
		}

		level.getEntitiesOfClass(LivingEntity.class, new AABB(headPos).inflate(6.0D),
				entity -> entity.isAlive() && !entity.isSpectator())
				.forEach(entity -> {
					entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
					entity.clearFire();
				});
		Vec3 center = headPos.getCenter();
		level.explode(null, level.damageSources().badRespawnPointExplosion(center), null,
				center, 5.0F, false, Level.ExplosionInteraction.BLOCK);
		if (level instanceof ServerLevel serverLevel) {
			PacketDistributor.sendToPlayersTrackingChunk(serverLevel, serverLevel.getChunkAt(headPos).getPos(),
					new BloodParticlesPacket(center, 1.5F, 160, -1));
		}
	}

	private static void sleepInsideCoffin(Player player, Level level, BlockPos headPos, BlockState headState, boolean vampireRespawn) {
		boolean occupied = headState.getValue(OCCUPIED);
		if (player.isShiftKeyDown() || occupied) {
			setCoffinProperties(level, headPos, headState, !headState.getValue(CLOSED), occupied);
			if (!player.isShiftKeyDown() && occupied) {
				player.displayClientMessage(net.minecraft.network.chat.Component.translatable("block.minecraft.bed.occupied"), true);
			}
			return;
		}

		PlayerCoffinSleepData.onSleepingInCoffin(player, vampireRespawn);
		BlockPos coffinPos = headPos;
		Either<BedSleepingProblem, Unit> sleepResult = player.startSleepInBed(headPos);
		sleepResult.ifLeft(failed -> {
			if (failed != null) {
				if (!level.isClientSide && failed == BedSleepingProblem.NOT_SAFE && player instanceof ServerPlayer serverPlayer) {
					forceSleep(serverPlayer, coffinPos);
				}
				else if (failed.getMessage() != null) {
					player.displayClientMessage(failed.getMessage(), true);
				}
			}
		});
		if (player.isSleeping()) {
			Direction dir = headState.getBedDirection(level, coffinPos);
			Vec3 sleepingPos = new Vec3(headPos.getX() + 0.5D, headPos.getY() + 0.1875D, headPos.getZ() + 0.5D)
					.add(Vec3.atLowerCornerOf(dir.getNormal()).scale(0.085D));
			player.teleportTo(sleepingPos.x, sleepingPos.y, sleepingPos.z);
		}
	}

	private static void forceSleep(ServerPlayer player, BlockPos pos) {
		player.startSleeping(pos);
		CommonReflection.setSleepCounter(player, 0);
		player.awardStat(Stats.SLEEP_IN_BED);
		CriteriaTriggers.SLEPT_IN_BED.trigger(player);
		player.serverLevel().updateSleepingPlayerList();
	}

	@Override
	public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) {
		setCoffinProperties(level, pos, state, occupied, occupied);
		if (!occupied && sleeper instanceof Player player) {
			PlayerCoffinSleepData.onWakeUp(player);
		}
	}

	private static void setCoffinProperties(Level level, BlockPos pos, BlockState state, boolean closed, boolean occupied) {
		BlockState updated = state.setValue(CLOSED, closed).setValue(OCCUPIED, occupied);
		level.setBlock(pos, updated, 3);
		BlockPos otherPos = pos.relative(getNeighbourDirection(state.getValue(PART), state.getValue(FACING)));
		BlockState otherState = level.getBlockState(otherPos);
		if (otherState.is(state.getBlock()) && otherState.getValue(PART) != state.getValue(PART)) {
			level.setBlock(otherPos, otherState.setValue(CLOSED, closed).setValue(OCCUPIED, occupied), 3);
		}
	}

	public static boolean isSleepingInCoffin(LivingEntity entity) {
		return isBlockCoffin(entity.level(), entity.getSleepingPos());
	}

	public static boolean isBlockCoffin(Level level, Optional<BlockPos> pos) {
		return pos.map(blockPos -> level.getBlockState(blockPos).getBlock() instanceof WoodenCoffinBlock).orElse(false);
	}

	private static boolean isEntityVampire(LivingEntity entity) {
		return PlayerPower.getPowerData(entity, ModPlayerPowers.VAMPIRISM).isPresent();
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (direction == getNeighbourDirection(state.getValue(PART), state.getValue(FACING))) {
			return neighborState.is(this) && neighborState.getValue(PART) != state.getValue(PART)
					? state.setValue(OCCUPIED, neighborState.getValue(OCCUPIED)).setValue(CLOSED, neighborState.getValue(CLOSED))
					: Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	private static Direction getNeighbourDirection(BedPart part, Direction direction) {
		return part == BedPart.FOOT ? direction : direction.getOpposite();
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide && player.isCreative()) {
			BedPart part = state.getValue(PART);
			if (part == BedPart.FOOT) {
				BlockPos otherPos = pos.relative(getNeighbourDirection(part, state.getValue(FACING)));
				BlockState otherState = level.getBlockState(otherPos);
				if (otherState.is(this) && otherState.getValue(PART) == BedPart.HEAD) {
					level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
					level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
				}
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction direction = context.getHorizontalDirection();
		BlockPos pos = context.getClickedPos();
		BlockPos otherPos = pos.relative(direction);
		Level level = context.getLevel();
		return level.getBlockState(otherPos).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(otherPos)
				? defaultBlockState().setValue(FACING, direction)
				: null;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(CLOSED)) {
			return SHAPE_CLOSED;
		}
		Direction direction = state.getValue(FACING);
		if (state.getValue(PART) == BedPart.HEAD) {
			direction = direction.getOpposite();
		}
		return switch (direction) {
		case NORTH -> SHAPE_OPEN_N;
		case EAST -> SHAPE_OPEN_E;
		case SOUTH -> SHAPE_OPEN_S;
		case WEST -> SHAPE_OPEN_W;
		default -> super.getShape(state, level, pos, context);
		};
	}

	public static Direction getConnectedDirection(BlockState state) {
		Direction direction = state.getValue(FACING);
		return state.getValue(PART) == BedPart.HEAD ? direction.getOpposite() : direction;
	}

	@Override
	public PushReaction getPistonPushReaction(BlockState state) {
		return PushReaction.DESTROY;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART, OCCUPIED, CLOSED);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide) {
			BlockPos otherPos = pos.relative(state.getValue(FACING));
			level.setBlock(otherPos, state.setValue(PART, BedPart.HEAD), 3);
			level.blockUpdated(pos, Blocks.AIR);
			state.updateNeighbourShapes(level, pos, 3);
		}
	}

	public DyeColor getColor() {
		return color;
	}

	@Override
	protected long getSeed(BlockState state, BlockPos pos) {
		BlockPos blockpos = pos.relative(state.getValue(FACING), state.getValue(PART) == BedPart.HEAD ? 0 : 1);
		return Mth.getSeed(blockpos.getX(), pos.getY(), blockpos.getZ());
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
		return 5;
	}

	@Override
	public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
		return 5;
	}

	@Override
	public Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition(BlockState state, EntityType<?> type, LevelReader levelReader, BlockPos pos, float orientation) {
		if (type != EntityType.PLAYER || !(levelReader instanceof Level level) || !BedBlock.canSetSpawn(level)) {
			return Optional.empty();
		}
		Direction direction = state.getBedDirection(levelReader, pos);
		if (levelReader instanceof CollisionGetter collisionGetter) {
			return BedBlock.findStandUpPosition(type, collisionGetter, pos, direction, orientation)
					.map(vec -> ServerPlayer.RespawnPosAngle.of(vec, pos));
		}
		return Optional.of(new ServerPlayer.RespawnPosAngle(pos.getCenter(), orientation));
	}

	@EventBusSubscriber(modid = JojoMod.MOD_ID)
	public static class EventHandler {
		@SubscribeEvent
		public static void setRespawnLocation(PlayerSetSpawnEvent event) {
			BlockPos spawnPos = event.getNewSpawn();
			Player player = event.getEntity();
			if (spawnPos == null || isEntityVampire(player) || player.getServer() == null) {
				return;
			}
			ServerLevel spawnLevel = player.getServer().getLevel(event.getSpawnLevel());
			if (spawnLevel != null && isBlockCoffin(spawnLevel, Optional.of(spawnPos))) {
				event.setCanceled(true);
			}
		}

		@SubscribeEvent
		public static void canSleepAtTime(CanPlayerSleepEvent event) {
			if (event.getState().getBlock() instanceof WoodenCoffinBlock) {
				event.setProblem(null);
			}
		}

		@SubscribeEvent
		public static void canContinueSleeping(CanContinueSleepingEvent event) {
			if (isBlockCoffin(event.getEntity().level(), event.getEntity().getSleepingPos())) {
				event.setContinueSleeping(true);
			}
		}

		@SubscribeEvent
		public static void setCoffinTime(SleepFinishedTimeEvent event) {
			ServerLevel level = (ServerLevel) event.getLevel();
			int playersCount = level.players().size();
			if (playersCount == 0) {
				return;
			}
			long coffinSleepers = level.players().stream()
					.filter(player -> player.isSleeping() && isBlockCoffin(player.level(), Optional.of(player.blockPosition())))
					.count();
			if (coffinSleepers >= (playersCount + 1) / 2) {
				long time = level.getDayTime();
				long timeAdded = (24000L - time % 24000L + 12600L) % 24000L;
				event.setTimeAddition(time + timeAdded);
			}
		}

		@SubscribeEvent(priority = EventPriority.LOWEST)
		public static void skippedToNight(SleepFinishedTimeEvent event) {
			ServerLevel level = (ServerLevel) event.getLevel();
			level.players().stream()
					.filter(Player::isSleeping)
					.filter(player -> isBlockCoffin(player.level(), Optional.of(player.blockPosition())))
					.forEach(player -> {
						player.clearFire();
						player.removeEffect(ModStatusEffects.VAMPIRE_SUN_BURN);
						player.removeEffect(MobEffects.WEAKNESS);
						long oldTime = level.getLevelData().getDayTime();
						int oldDayTime = (int) (oldTime % 24000L);
						int newDayTime = (int) (event.getNewTime() % 24000L);
						if (newDayTime >= 12600 && newDayTime < 23500
								&& (oldDayTime < 12600 || oldDayTime >= 23500)) {
							ModCriteriaTriggers.triggerCoffinSleep(player);
						}
					});
		}

		@SubscribeEvent
		public static void onServerPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
			if (event.getEntity() instanceof ServerPlayer player) {
				respawnInsideCoffin(player, player.getRespawnPosition());
			}
		}

		public static void respawnInsideCoffin(Player player, @Nullable BlockPos respawnPos) {
			if (!isEntityVampire(player) || respawnPos == null) {
				return;
			}
			BlockState state = player.level().getBlockState(respawnPos);
			if (state.getBlock() instanceof WoodenCoffinBlock) {
				BlockPos headPos = state.getValue(PART) == BedPart.HEAD ? respawnPos : respawnPos.relative(state.getValue(FACING));
				BlockState headState = player.level().getBlockState(headPos);
				if (headState.getBlock() instanceof WoodenCoffinBlock) {
					sleepInsideCoffin(player, player.level(), headPos, headState, true);
				}
			}
		}
	}
}
