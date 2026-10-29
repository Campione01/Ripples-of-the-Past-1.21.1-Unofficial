package rotp.addons.d4c.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CPhase2ASpec;
import rotp.addons.d4c.D4CPhase2BSpec;
import rotp.addons.d4c.D4CRuntimeRules;
import rotp.addons.d4c.network.OpenDimensionSelectorPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class D4CDimensionTravelService {
    private static final String PORTAL_COOLDOWN =
            "rotp_d4c.portal_cooldown";
    private static final String SELECTED_TARGET =
            "rotp_d4c.phase2a.selected_dimension";
    private static final String SESSION_ID =
            "rotp_d4c.phase2a.selector_session";
    private static final String SESSION_EXPIRY =
            "rotp_d4c.phase2a.selector_expiry";
    private static final String SESSION_DIMENSION =
            "rotp_d4c.phase2a.selector_dimension";

    private D4CDimensionTravelService() {}

    public static boolean openSelector(ServerPlayer player) {
        if (!hasD4CState(player, D4CAddon.DIMENSION_SELECTOR_NAME)) {
            return false;
        }

        List<ResourceLocation> targets =
                availableTargets(player.getServer());
        if (targets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_d4c.message.no_dimensions_available"),
                    true);
            return false;
        }
        if (player.connection == null
                || !player.connection.hasChannel(
                        OpenDimensionSelectorPayload.TYPE)) {
            return false;
        }

        ResourceLocation selected = selectedTarget(player, targets);
        UUID sessionId = UUID.randomUUID();
        long expiry = player.serverLevel().getGameTime()
                + D4CPhase2ASpec.SELECTOR_SESSION_TICKS;
        CompoundTag data = player.getPersistentData();
        data.putString(SESSION_ID, sessionId.toString());
        data.putLong(SESSION_EXPIRY, expiry);
        data.putString(
                SESSION_DIMENSION,
                player.level().dimension().location().toString());

        PacketDistributor.sendToPlayer(
                player,
                new OpenDimensionSelectorPayload(
                        sessionId,
                        expiry,
                        targets,
                        selected));
        return true;
    }

    public static boolean consumeSelection(
            ServerPlayer player,
            UUID sessionId,
            ResourceLocation targetId) {
        CompoundTag data = player.getPersistentData();
        boolean sessionMatches = sessionId.toString().equals(
                data.getString(SESSION_ID));
        if (!sessionMatches) {
            selectionExpired(player);
            return false;
        }

        long expiry = data.getLong(SESSION_EXPIRY);
        String sourceDimension = data.getString(SESSION_DIMENSION);
        clearSelectorSession(data);
        boolean valid = player.isAlive()
                && !player.isSpectator()
                && player.serverLevel().getGameTime() <= expiry
                && player.level().dimension().location().toString()
                        .equals(sourceDimension)
                && hasD4CState(
                        player,
                        D4CAddon.DIMENSION_SELECTOR_NAME)
                && D4CDimensionTargets.isKnown(targetId)
                && player.getServer().getLevel(
                        D4CDimensionTargets.levelKey(targetId))
                        != null;
        if (!valid) {
            selectionExpired(player);
            return false;
        }

        data.putString(SELECTED_TARGET, targetId.toString());
        player.displayClientMessage(
                Component.translatable(
                        "rotp_d4c.message.dimension_selected",
                        D4CDimensionTargets.label(targetId)),
                true);
        return true;
    }

    @Nullable
    public static Component validateSelectedTarget(ServerPlayer player) {
        if (!hasD4CState(
                player,
                D4CAddon.PARALLEL_WORLD_TRAVEL_NAME)) {
            return Component.translatable(
                    "rotp_d4c.message.travel_unavailable");
        }
        if (!isCaughtBetween(player.level(), player)) {
            return Component.translatable(
                    "rotp_d4c.message.not_caught_between");
        }

        ResourceLocation selected = selectedTarget(
                player,
                availableTargets(player.getServer()));
        ResourceLocation actual = actualDestination(
                player.level().dimension().location(),
                selected);
        if (actual == null) {
            return Component.translatable(
                    "rotp_d4c.message.already_in_dimension",
                    D4CDimensionTargets.label(selected));
        }
        if (player.getServer().getLevel(
                D4CDimensionTargets.levelKey(actual)) == null) {
            return Component.translatable(
                    "rotp_d4c.message.dimension_unavailable",
                    D4CDimensionTargets.label(actual));
        }
        return null;
    }

    public static boolean isCaughtBetween(
            Level level, LivingEntity entity) {
        BlockPos feet = entity.blockPosition();
        BlockPos head = feet.above();
        boolean east = constrained(level, feet, head, 1, 0);
        boolean west = constrained(level, feet, head, -1, 0);
        boolean south = constrained(level, feet, head, 0, 1);
        boolean north = constrained(level, feet, head, 0, -1);
        boolean northwest = constrained(level, feet, head, -1, -1);
        boolean southeast = constrained(level, feet, head, 1, 1);
        boolean northeast = constrained(level, feet, head, 1, -1);
        boolean southwest = constrained(level, feet, head, -1, 1);
        boolean up = !isReplaceableForCatch(
                level.getBlockState(head.above()));
        boolean down = !isReplaceableForCatch(
                level.getBlockState(feet.below()));
        return hasOpposingConstraints(
                east,
                west,
                up,
                down,
                south,
                north,
                northwest,
                southeast,
                northeast,
                southwest);
    }

    public static boolean hasOpposingConstraints(
            boolean east,
            boolean west,
            boolean up,
            boolean down,
            boolean south,
            boolean north,
            boolean northwest,
            boolean southeast,
            boolean northeast,
            boolean southwest) {
        return D4CRuntimeRules.hasOpposingConstraints(
                east,
                west,
                up,
                down,
                south,
                north,
                northwest,
                southeast,
                northeast,
                southwest);
    }

    private static boolean constrained(
            Level level,
            BlockPos feet,
            BlockPos head,
            int x,
            int z) {
        return !isReplaceableForCatch(
                        level.getBlockState(feet.offset(x, 0, z)))
                || !isReplaceableForCatch(
                        level.getBlockState(head.offset(x, 0, z)));
    }

    private static boolean isReplaceableForCatch(BlockState state) {
        return state.isAir()
                || state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof CropBlock
                || state.getBlock() instanceof FlowerBlock
                || state.getBlock() instanceof TallGrassBlock;
    }

    public static TravelPlanResult planTravel(ServerPlayer player) {
        Component failure = validateSelectedTarget(player);
        if (failure != null) {
            return TravelPlanResult.failure(failure);
        }

        ResourceLocation selected = selectedTarget(
                player,
                availableTargets(player.getServer()));
        ResourceLocation actual = actualDestination(
                player.level().dimension().location(),
                selected);
        ServerLevel target = actual != null
                ? player.getServer().getLevel(
                        D4CDimensionTargets.levelKey(actual))
                : null;
        if (target == null) {
            return TravelPlanResult.failure(Component.translatable(
                    "rotp_d4c.message.dimension_unavailable",
                    actual != null
                            ? D4CDimensionTargets.label(actual)
                            : Component.literal("?")));
        }

        BlockPos destination = findSafeDestination(target, player);
        if (destination == null) {
            return TravelPlanResult.failure(Component.translatable(
                    "rotp_d4c.message.no_safe_destination",
                    D4CDimensionTargets.label(actual)));
        }
        return TravelPlanResult.success(new TravelPlan(
                player.level().dimension(),
                target,
                actual,
                destination));
    }

    @Nullable
    public static ResourceLocation selectedDestinationId(
            ServerPlayer player) {
        List<ResourceLocation> available =
                availableTargets(player.getServer());
        if (available.isEmpty()) {
            return null;
        }
        ResourceLocation selected = selectedTarget(player, available);
        return actualDestination(
                player.level().dimension().location(), selected);
    }

    public static boolean teleportTarget(
            ServerPlayer owner, Entity targetEntity) {
        ResourceLocation targetId = selectedDestinationId(owner);
        if (targetId == null || !targetEntity.isAlive()) {
            return false;
        }
        ServerLevel targetLevel = owner.getServer().getLevel(
                D4CDimensionTargets.levelKey(targetId));
        if (targetLevel == null) {
            return false;
        }
        BlockPos destination =
                findSafeDestination(targetLevel, targetEntity);
        if (destination == null) {
            return false;
        }
        boolean teleported = targetEntity.teleportTo(
                targetLevel,
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                Set.of(),
                targetEntity.getYRot(),
                targetEntity.getXRot());
        if (teleported) {
            targetLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.PORTAL,
                    destination.getX() + 0.5D,
                    destination.getY() + targetEntity.getBbHeight() * 0.5D,
                    destination.getZ() + 0.5D,
                    48,
                    0.45D,
                    0.8D,
                    0.45D,
                    0.15D);
        }
        return teleported;
    }

    public static boolean placeSelectedPortal(
            ServerPlayer owner,
            Entity targetEntity) {
        ResourceLocation targetId = selectedDestinationId(owner);
        return targetId != null
                && placePortal(owner.serverLevel(), targetEntity, targetId);
    }

    // 1.16 aa/aj.java:119-121: pull-in writes the portal at the target's
    // block position whatever is there, and picks it from the selection
    // alone
    public static boolean placePulledPortal(
            ServerPlayer owner,
            Entity targetEntity) {
        ResourceLocation targetId = resolvePulledDestination(
                owner, owner.serverLevel());
        return targetId != null && placePortal(
                owner.serverLevel(), targetEntity, targetId, true);
    }

    @Nullable
    public static ResourceLocation resolvePulledDestination(
            ServerPlayer owner, ServerLevel sourceLevel) {
        List<ResourceLocation> available =
                availableTargets(sourceLevel.getServer());
        if (available.isEmpty()) {
            return null;
        }
        // D-rotp-d4c-06 applies the same destination table to every target kind.
        return pulledDestination(
                sourceLevel.dimension().location(),
                selectedTarget(owner, available),
                false);
    }

    public static boolean placePulledPortal(
            ServerLevel sourceLevel, BlockPos position,
            ResourceLocation destination) {
        return destination != null
                && placePortal(sourceLevel, position, destination, true);
    }

    // 1.16 ab/af.java:140-152, ab/ae.java:137-149, ab/ac.java:139-149: a
    // portal used inside its own dimension leads to the parallel world
    // from the Overworld and to the Overworld from anywhere else.
    // Owner decision D-rotp-d4c-06: that holds for every target kind. The
    // donor's ab/af.java:215-257 kept a non-player in the Overworld and
    // moved it to the shared spawn; that is deliberately not followed
    public static ResourceLocation pulledDestination(
            ResourceLocation current,
            ResourceLocation selected,
            boolean playerTarget) {
        if (!current.equals(selected)) {
            return selected;
        }
        return selected.equals(D4CDimensionTargets.OVERWORLD)
                ? D4CDimensionTargets.PARALLEL_WORLD
                : D4CDimensionTargets.OVERWORLD;
    }

    // Where a portal block sends an entity that touches it, or null when
    // it does nothing. Owner decision D-rotp-d4c-06: the overworld portal
    // inside the Overworld sends every living entity to the parallel
    // world, by whatever route it got there; nothing is relocated inside
    // the Overworld. As in 1.16 ab/af.java:216 it ignores non-living ones
    @Nullable
    public static ResourceLocation portalDestination(
            ResourceLocation current,
            ResourceLocation portalTarget,
            Entity entity) {
        if (!current.equals(portalTarget)) {
            return portalTarget;
        }
        return portalTarget.equals(D4CDimensionTargets.OVERWORLD)
                        && entity instanceof LivingEntity
                ? D4CDimensionTargets.PARALLEL_WORLD
                : null;
    }

    public static boolean placePortal(
            ServerLevel level,
            Entity targetEntity,
            ResourceLocation targetId) {
        return placePortal(level, targetEntity, targetId, false);
    }

    private static boolean placePortal(
            ServerLevel level,
            Entity targetEntity,
            ResourceLocation targetId,
            boolean overwrite) {
        if (!targetEntity.isAlive() || targetEntity.level() != level) {
            return false;
        }
        return placePortal(level, targetEntity.blockPosition(), targetId, overwrite);
    }

    private static boolean placePortal(
            ServerLevel level, BlockPos position,
            ResourceLocation targetId, boolean overwrite) {
        if (level.getServer().getLevel(
                D4CDimensionTargets.levelKey(targetId)) == null) {
            return false;
        }
        BlockState previous = level.getBlockState(position);
        if (!overwrite
                && (!previous.canBeReplaced()
                        || !previous.getFluidState().isEmpty())) {
            return false;
        }

        var portal = D4CAddon.portalBlockFor(targetId);
        if (!level.setBlockAndUpdate(
                position, portal.defaultBlockState())) {
            return false;
        }
        level.scheduleTick(
                position,
                portal,
                D4CPhase2BSpec.PORTAL_LIFESPAN_TICKS);
        level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.PORTAL,
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D,
                32,
                0.45D,
                0.6D,
                0.45D,
                0.12D);
        return true;
    }

    public static boolean teleportThroughPortal(
            ServerLevel sourceLevel,
            BlockPos portalPosition,
            Entity entity,
            ResourceLocation portalTargetId) {
        long gameTime = sourceLevel.getGameTime();
        if (entity.getPersistentData().getLong(PORTAL_COOLDOWN)
                > gameTime) {
            return false;
        }
        ResourceLocation targetId = portalDestination(
                sourceLevel.dimension().location(),
                portalTargetId,
                entity);
        if (targetId == null) {
            return false;
        }
        ServerLevel targetLevel = sourceLevel.getServer().getLevel(
                D4CDimensionTargets.levelKey(targetId));
        if (targetLevel == null) {
            return false;
        }
        BlockPos destination =
                findSafeDestination(targetLevel, entity);
        if (destination == null) {
            return false;
        }

        sourceLevel.playSound(
                null,
                portalPosition,
                D4CAddon.D4C_TELEPORT.get(),
                net.minecraft.sounds.SoundSource.BLOCKS,
                1.0F,
                1.0F);
        sourceLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.PORTAL,
                entity.getX(),
                entity.getY(0.5D),
                entity.getZ(),
                48,
                0.45D,
                0.8D,
                0.45D,
                0.15D);
        if (entity instanceof ServerPlayer player) {
            StandPower power = StandPower.get(player);
            if (power != null && power.hasPower()) {
                power.getPowerType().forceUnsummon(player, power);
            }
        }

        boolean teleported = entity.teleportTo(
                targetLevel,
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                Set.of(),
                entity.getYRot(),
                entity.getXRot());
        if (!teleported) {
            return false;
        }
        entity.getPersistentData().putLong(
                PORTAL_COOLDOWN, targetLevel.getGameTime() + 60L);
        targetLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.PORTAL,
                entity.getX(),
                entity.getY(0.5D),
                entity.getZ(),
                48,
                0.45D,
                0.8D,
                0.45D,
                0.15D);
        targetLevel.playSound(
                null,
                entity.blockPosition(),
                D4CAddon.D4C_TRAVERSE_ON.get(),
                entity.getSoundSource(),
                1.0F,
                1.0F);
        return true;
    }

    public static boolean executeTravel(
            ServerPlayer player,
            StandPower power,
            TravelPlan plan) {
        if (!player.isAlive()
                || !player.level().dimension().equals(plan.source())
                || player.getServer().getLevel(
                        D4CDimensionTargets.levelKey(plan.targetId()))
                        != plan.target()
                || !isSafeDestination(
                        plan.target(),
                        player,
                        plan.destination())) {
            return false;
        }

        ServerLevel sourceLevel = player.serverLevel();
        sourceLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.PORTAL,
                player.getX(),
                player.getY(0.5D),
                player.getZ(),
                48,
                0.45D,
                0.8D,
                0.45D,
                0.15D);
        sourceLevel.playSound(
                null,
                player.blockPosition(),
                D4CAddon.D4C_TRAVERSE.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.0F,
                1.0F);

        power.getPowerType().forceUnsummon(player, power);
        BlockPos destination = plan.destination();
        boolean teleported = player.teleportTo(
                plan.target(),
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                Set.of(),
                player.getYRot(),
                player.getXRot());
        if (!teleported) {
            return false;
        }

        plan.target().sendParticles(
                net.minecraft.core.particles.ParticleTypes.PORTAL,
                player.getX(),
                player.getY(0.5D),
                player.getZ(),
                48,
                0.45D,
                0.8D,
                0.45D,
                0.15D);
        plan.target().playSound(
                null,
                player.blockPosition(),
                D4CAddon.D4C_TRAVERSE_ON.get(),
                net.minecraft.sounds.SoundSource.PLAYERS,
                1.0F,
                1.0F);
        plan.target().playSound(
                null,
                player.blockPosition(),
                D4CAddon.VALENTINE_TRAVEL.get(),
                net.minecraft.sounds.SoundSource.VOICE,
                1.0F,
                1.0F);
        return true;
    }

    public static List<ResourceLocation> availableTargets(
            MinecraftServer server) {
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation id : D4CDimensionTargets.LEGACY_ORDER) {
            if (server.getLevel(D4CDimensionTargets.levelKey(id)) != null) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    private static ResourceLocation selectedTarget(
            ServerPlayer player,
            List<ResourceLocation> available) {
        ResourceLocation selected = ResourceLocation.tryParse(
                player.getPersistentData().getString(SELECTED_TARGET));
        if (selected != null && available.contains(selected)) {
            return selected;
        }
        if (available.contains(D4CDimensionTargets.PARALLEL_WORLD)) {
            return D4CDimensionTargets.PARALLEL_WORLD;
        }
        return available.contains(D4CDimensionTargets.OVERWORLD)
                ? D4CDimensionTargets.OVERWORLD
                : available.getFirst();
    }

    @Nullable
    private static ResourceLocation actualDestination(
            ResourceLocation current,
            ResourceLocation selected) {
        if (!current.equals(selected)) {
            return selected;
        }
        return selected.equals(D4CDimensionTargets.PARALLEL_WORLD)
                ? D4CDimensionTargets.OVERWORLD
                : null;
    }

    private static boolean hasD4CState(
            ServerPlayer player,
            String abilityName) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.hasPower()
                && power.getPowerType() == D4CAddon.D4C.get()
                && power.isAbilityUnlocked(abilityName);
    }

    private static void selectionExpired(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable(
                        "rotp_d4c.message.dimension_selection_expired"),
                true);
    }

    private static void clearSelectorSession(CompoundTag data) {
        data.remove(SESSION_ID);
        data.remove(SESSION_EXPIRY);
        data.remove(SESSION_DIMENSION);
    }

    @Nullable
    private static BlockPos findSafeDestination(
            ServerLevel target,
            Entity entity) {
        WorldBorder border = target.getWorldBorder();
        double x = Mth.clamp(
                entity.getX(),
                border.getMinX() + 1.0D,
                border.getMaxX() - 1.0D);
        double z = Mth.clamp(
                entity.getZ(),
                border.getMinZ() + 1.0D,
                border.getMaxZ() - 1.0D);
        int minY = target.getMinBuildHeight() + 1;
        int maxY = target.getMaxBuildHeight() - 2;
        int originY = Mth.clamp(Mth.floor(entity.getY()), minY, maxY);
        int xBlock = Mth.floor(x);
        int zBlock = Mth.floor(z);

        for (int offset = 0; offset <= maxY - minY; offset++) {
            int above = originY + offset;
            if (above <= maxY) {
                BlockPos candidate = new BlockPos(
                        xBlock,
                        above,
                        zBlock);
                if (isSafeDestination(target, entity, candidate)) {
                    return candidate;
                }
            }
            int below = originY - offset;
            if (offset > 0 && below >= minY) {
                BlockPos candidate = new BlockPos(
                        xBlock,
                        below,
                        zBlock);
                if (isSafeDestination(target, entity, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isSafeDestination(
            ServerLevel target,
            Entity entity,
            BlockPos feetPos) {
        int entityBlocks = Math.max(
                1, Mth.ceil(entity.getBbHeight()));
        BlockPos floorPos = feetPos.below();
        BlockState feet = target.getBlockState(feetPos);
        BlockState floor = target.getBlockState(floorPos);
        if (!feet.getCollisionShape(target, feetPos).isEmpty()
                || !floor.isFaceSturdy(
                        target,
                        floorPos,
                        Direction.UP)) {
            return false;
        }
        for (int offset = 1; offset < entityBlocks; offset++) {
            BlockPos occupied = feetPos.above(offset);
            if (!target.getBlockState(occupied)
                    .getCollisionShape(target, occupied)
                    .isEmpty()) {
                return false;
            }
        }

        AABB destinationBounds = entity.getBoundingBox().move(
                feetPos.getX() + 0.5D - entity.getX(),
                feetPos.getY() - entity.getY(),
                feetPos.getZ() + 0.5D - entity.getZ());
        return target.noCollision(entity, destinationBounds);
    }

    public record TravelPlan(
            ResourceKey<Level> source,
            ServerLevel target,
            ResourceLocation targetId,
            BlockPos destination) {}

    public record TravelPlanResult(
            @Nullable TravelPlan plan,
            @Nullable Component failure) {
        public static TravelPlanResult success(TravelPlan plan) {
            return new TravelPlanResult(plan, null);
        }

        public static TravelPlanResult failure(Component failure) {
            return new TravelPlanResult(null, failure);
        }

        public boolean successful() {
            return plan != null;
        }
    }
}
