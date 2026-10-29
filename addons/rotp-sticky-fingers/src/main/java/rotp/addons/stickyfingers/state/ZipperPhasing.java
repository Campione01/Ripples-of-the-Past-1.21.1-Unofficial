package rotp.addons.stickyfingers.state;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = StickyFingersAddon.MOD_ID)
public final class ZipperPhasing {
    private static final Set<UUID> SERVER_SINKING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SERVER_SNEAK_SEEN = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLIENT_SINKING = ConcurrentHashMap.newKeySet();
    // The hollow of an open zipper for each set of plated sides, indexed by Direction ordinal bits.
    private static final VoxelShape[] HOLLOWS = new VoxelShape[1 << Direction.values().length];

    static {
        for (int plates = 0; plates < HOLLOWS.length; plates++) {
            VoxelShape shape = Shapes.empty();
            for (Direction direction : Direction.values()) {
                if ((plates & 1 << direction.ordinal()) != 0) {
                    shape = Shapes.or(shape, boundaryFace(direction));
                }
            }
            HOLLOWS[plates] = shape;
        }
    }

    private ZipperPhasing() {}

    public static boolean eligible(Player player) {
        return ZipperInGround.isInGround(player) && canSink(player);
    }

    // The donor accepts the double sneak from any maximum-Resolve user; only the collision asks for the ground.
    public static boolean canSink(Player player) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        StandPower power = StandPower.get(player);
        return power != null
                && power.getPowerType() == StickyFingersAddon.STICKY_FINGERS.get()
                && power.getResolveLevel() == power.getMaxResolveLevel();
    }

    public static void setClientSinking(Player player, boolean sinking) {
        set(CLIENT_SINKING, player.getUUID(), sinking && canSink(player));
    }

    public static void clearClientSinking() {
        CLIENT_SINKING.clear();
    }

    public static void setServerSinking(ServerPlayer player, boolean sinking) {
        UUID id = player.getUUID();
        boolean accepted = sinking && canSink(player);
        set(SERVER_SINKING, id, accepted);
        // The donor waits for vanilla's sneak update before treating a release as an exit.
        set(SERVER_SNEAK_SEEN, id, accepted && player.isShiftKeyDown());
    }

    private static void set(Set<UUID> states, UUID id, boolean value) {
        if (value) {
            states.add(id);
        } else {
            states.remove(id);
        }
    }

    public static VoxelShape collisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context, VoxelShape original) {
        if (original.isEmpty()
                || !(context instanceof EntityCollisionContext entityContext)
                || !(entityContext.getEntity() instanceof Player player)
                || !eligible(player)) {
            return original;
        }
        // An open zipper is already the hollow for everyone; a closed one stands for the ordinary block it replaced.
        if (ZipperInGround.isOpenZipper(state)) {
            return original;
        }
        boolean closedZipper = state.is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get());
        if (!closedZipper && (state.getDestroySpeed(level, pos) < 0.0F
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock)) {
            return original;
        }
        // Donor uses the queried shape's top and a grounded allowance, not a full-cube test.
        boolean above = player.getY() > pos.getY() + original.max(Direction.Axis.Y)
                - (player.onGround() ? 0.503125D : 0.0015D);
        Set<UUID> sinking = player.level().isClientSide() ? CLIENT_SINKING : SERVER_SINKING;
        if (above && !sinking.contains(player.getUUID())) {
            return original;
        }
        VoxelShape shape = adaptingShape(level, pos);
        // The donor's closed zipper is a thin plate on the face of its block; so is its linked zipper on the far face.
        if (closedZipper && state.getValue(StickyFingersZipperBlock.THROUGH)) {
            shape = Shapes.or(shape, boundaryFace(state.getValue(StickyFingersZipperBlock.FACING).getOpposite()));
        }
        return closedZipper
                ? Shapes.or(shape, boundaryFace(state.getValue(StickyFingersZipperBlock.FACING))) : shape;
    }

    // 1.16 createCollisionAdaptingShape for the block behind an open zipper: a hollow for every entity,
    // with a plate on each bounded side and none on the sides that carry the zipper.
    public static VoxelShape hollowShape(BlockState state, BlockGetter level, BlockPos pos) {
        int plates = 0;
        for (Direction direction : Direction.values()) {
            if (!StickyFingersZipperBlock.faces(state, direction)
                    && boundary(level, pos.relative(direction), direction)) {
                plates |= 1 << direction.ordinal();
            }
        }
        return HOLLOWS[plates];
    }

    private static VoxelShape adaptingShape(BlockGetter level, BlockPos pos) {
        VoxelShape shape = Shapes.empty();
        for (Direction direction : Direction.values()) {
            if (boundary(level, pos.relative(direction), direction)) {
                shape = Shapes.or(shape, boundaryFace(direction));
            }
        }
        return shape;
    }

    private static boolean boundary(BlockGetter level, BlockPos cell, Direction direction) {
        BlockState neighbour = level.getBlockState(cell);
        if (neighbour.getBlock() instanceof DoorBlock
                || neighbour.getBlock() instanceof TrapDoorBlock
                || neighbour.getBlock() instanceof LiquidBlock) {
            return true;
        }
        if (!ZipperInGround.isBlockFree(level, cell)) {
            return false;
        }
        // The donor's zipper block sits in the free cell its zipper faces; that cell is free space only
        // without one, and is shut by a zipper on the far wall or one continued a step further.
        boolean zipperCell = false;
        for (Direction facing : Direction.values()) {
            if (!zipperFacing(level, cell.relative(facing.getOpposite()), facing)) {
                continue;
            }
            zipperCell = true;
            if (facing == direction.getOpposite()
                    || (facing.getAxis() != direction.getAxis()
                            && zipperFacing(level, cell.relative(direction), facing))) {
                return true;
            }
        }
        return !zipperCell;
    }

    private static boolean zipperFacing(BlockGetter level, BlockPos pos, Direction facing) {
        return StickyFingersZipperBlock.faces(level.getBlockState(pos), facing);
    }

    private static VoxelShape boundaryFace(Direction direction) {
        return switch (direction) {
            case UP -> Shapes.box(0, 0.99, 0, 1, 1, 1);
            case DOWN -> Shapes.box(0, 0, 0, 1, 0.01, 1);
            case NORTH -> Shapes.box(0, 0, 0, 1, 1, 0.01);
            case SOUTH -> Shapes.box(0, 0, 0.99, 1, 1, 1);
            case WEST -> Shapes.box(0, 0, 0, 0.01, 1, 1);
            case EAST -> Shapes.box(0.99, 0, 0, 1, 1, 1);
        };
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            if (!canSink(player) || (!player.isShiftKeyDown() && SERVER_SNEAK_SEEN.contains(id))) {
                SERVER_SINKING.remove(id);
                SERVER_SNEAK_SEEN.remove(id);
            } else if (player.isShiftKeyDown() && SERVER_SINKING.contains(id)) {
                SERVER_SNEAK_SEEN.add(id);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SERVER_SINKING.remove(event.getEntity().getUUID());
        SERVER_SNEAK_SEEN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        SERVER_SINKING.remove(event.getEntity().getUUID());
        SERVER_SNEAK_SEEN.remove(event.getEntity().getUUID());
    }
}
