package rotp.addons.stickyfingers.state;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.block.StickyFingersZipperBlock;
import rotp.addons.stickyfingers.network.ZipperInGroundPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.16 EntityZipperCapability.tickInGround: a Sticky Fingers user whose
 * feet are inside an open zipper is in the ground (hidden, paying stamina).
 * The port's open zipper block is the hollow 1.16 carved behind a zipper.
 */
@EventBusSubscriber(modid = StickyFingersAddon.MOD_ID)
public final class ZipperInGround {
    public static final int INVISIBILITY_TICKS = 10;
    public static final float STAMINA_PER_TICK = 5.0F;
    // 1.16 ClientEvents fog: colour 9523889, EXP2 density 0.4
    // (opaque by about 5 blocks).
    public static final int FOG_COLOR = 0x9152B1;
    public static final float FOG_FAR = 5.0F;

    private static final Set<UUID> IN_GROUND =
            ConcurrentHashMap.newKeySet();
    // Client copy (1.16 EntityZipperCapSyncPacket isInGround).
    private static final Set<UUID> SYNCED =
            ConcurrentHashMap.newKeySet();

    private ZipperInGround() {
    }

    public static boolean isInGround(@Nullable Entity entity) {
        return entity != null
                && (entity.level().isClientSide() ? SYNCED : IN_GROUND)
                        .contains(entity.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        // 1.16 EntityMixin.isInWall: no suffocation while in the ground
        if (event.getSource().is(DamageTypes.IN_WALL)
                && isInGround(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        IN_GROUND.remove(event.getEntity().getUUID());
    }

    static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean was = IN_GROUND.contains(id);
        update(player, id);
        boolean now = IN_GROUND.contains(id);
        if (syncNeeded(was, now)) {
            PacketDistributor.sendToPlayer(
                    player, new ZipperInGroundPayload(now));
        }
    }

    private static void update(ServerPlayer player, UUID id) {
        StandPower power = StandPower.get(player);
        if (power == null
                || power.getPowerType()
                        != StickyFingersAddon.STICKY_FINGERS.get()
                || !player.isAlive()
                || player.isSpectator()) {
            IN_GROUND.remove(id);
            return;
        }
        if (IN_GROUND.contains(id)) {
            player.addEffect(new MobEffectInstance(
                    ModStatusEffects.FULL_INVISIBILITY,
                    INVISIBILITY_TICKS, 0, false, false, false));
            power.consumeStamina(STAMINA_PER_TICK, true);
        }
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        if (isOpenZipper(level.getBlockState(feet))) {
            IN_GROUND.add(id);
        } else if (isBlockFree(level, feet.above())
                || power.getStamina() <= 0.0F
                || isZipperFront(level, feet)) {
            IN_GROUND.remove(id);
        }
    }

    // 1.16 ZipperUtil.isBlockZipper: the donor's zipper block sits in
    // the free cell its zipper faces, not in the block behind it.
    static boolean isZipperFront(BlockGetter level, BlockPos cell) {
        if (!isBlockFree(level, cell)) {
            return false;
        }
        for (Direction facing : Direction.values()) {
            BlockState behind = level.getBlockState(
                    cell.relative(facing.getOpposite()));
            if (behind.is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())
                    && behind.getValue(StickyFingersZipperBlock.FACING)
                            == facing) {
                return true;
            }
            // 1.16 placeZippers: the linked zipper of a one-block wall
            // sits in the free cell behind it.
            if (behind.getBlock() instanceof StickyFingersZipperBlock
                    && behind.getValue(StickyFingersZipperBlock.THROUGH)
                    && behind.getValue(StickyFingersZipperBlock.FACING)
                            .getOpposite() == facing) {
                return true;
            }
        }
        return false;
    }

    /** Client side of ZipperInGroundPayload. */
    public static void handleSync(@Nullable Player player, boolean inGround) {
        if (player != null && player.level().isClientSide()) {
            applySync(player.getUUID(), inGround);
        }
    }

    static void applySync(UUID id, boolean inGround) {
        if (inGround) {
            SYNCED.add(id);
        }
        else {
            SYNCED.remove(id);
        }
    }

    static boolean isSyncedInGround(UUID id) {
        return SYNCED.contains(id);
    }

    /** Client logout: drop the synced copy. */
    public static void clearClientView() {
        SYNCED.clear();
    }

    // Send only when the flag flips (death and spectator clear it too).
    static boolean syncNeeded(boolean was, boolean now) {
        return was != now;
    }

    // 1.16 onClientTick: first person while in the ground or standing
    // in the hollow (hasZippersAround).
    public static boolean forcesFirstPerson(
            boolean inGround, boolean feetInOpenZipper) {
        return inGround || feetInOpenZipper;
    }

    public static boolean fogsView(
            BlockGetter level, BlockPos head, boolean inGround) {
        return fogsView(
                inGround,
                isOpenZipper(level.getBlockState(head)),
                isZipperFront(level, head));
    }

    // 1.16 fogColor/fogDensity: in the ground or head in the hollow, but
    // not with the head in the zipper's own cell (1.16 isBlockZipper(head)).
    // Other open air above the ground is fogged as in 1.16.
    public static boolean fogsView(
            boolean inGround,
            boolean headInOpenZipper,
            boolean headAtZipperFront) {
        return (inGround || headInOpenZipper) && !headAtZipperFront;
    }

    // 1.16 onRenderBlockOverlay: end sky instead of the in-wall overlay.
    public static boolean hidesBlockOverlay(
            boolean inGround, boolean overlayIsOpenZipper) {
        return inGround || overlayIsOpenZipper;
    }

    public static boolean isOpenZipper(BlockState state) {
        return state.is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())
                && state.getValue(StickyFingersZipperBlock.OPEN);
    }

    // 1.16 ZipperUtil.isBlockFree
    public static boolean isBlockFree(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // A zipper is ruled out before its shape is asked for: the open
        // shape looks at the neighbours through this very check.
        return !state.is(StickyFingersAddon.STICKY_FINGERS_ZIPPER.get())
                && !state.getFluidState().is(FluidTags.WATER)
                && (state.isAir()
                        || state.canBeReplaced()
                        || state.getCollisionShape(level, pos).isEmpty());
    }

    /**
     * Zipper targeting clip. Open zippers the user stands in are skipped,
     * so the user can zip onward from inside (1.16 inverted face).
     */
    public static BlockHitResult clip(
            Level level, Entity user, ClipContext context) {
        AABB userBox = user.getBoundingBox();
        return BlockGetter.traverseBlocks(
                context.getFrom(),
                context.getTo(),
                context,
                (ctx, pos) -> {
                    BlockState state = level.getBlockState(pos);
                    if (isOpenZipper(state)
                            && userBox.intersects(new AABB(pos))) {
                        return null;
                    }
                    return level.clipWithInteractionOverride(
                            ctx.getFrom(),
                            ctx.getTo(),
                            pos,
                            ctx.getBlockShape(state, level, pos),
                            state);
                },
                ctx -> {
                    Vec3 back = ctx.getFrom().subtract(ctx.getTo());
                    return BlockHitResult.miss(
                            ctx.getTo(),
                            Direction.getNearest(back.x, back.y, back.z),
                            BlockPos.containing(ctx.getTo()));
                });
    }
}
