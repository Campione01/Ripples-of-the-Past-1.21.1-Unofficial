package rotp.addons.gratefuldead.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.powers.hamon.HamonUtil;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.state.AgingRemovalData;
import rotp.addons.gratefuldead.state.BlockAgingData;
import rotp.addons.gratefuldead.state.ItemAgingState;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class AgingService {
    public static final double AURA_RADIUS = 100.0D;
    public static final int AURA_DIAMETER_CHUNKS = 13;
    public static final int AURA_CHUNK_COUNT =
            AURA_DIAMETER_CHUNKS * AURA_DIAMETER_CHUNKS;
    public static final int MAX_AURA_ENTITY_CANDIDATES = 32;
    public static final int MAX_AURA_PLAYER_CANDIDATES = 32;
    public static final int MAX_AURA_BLOCK_COLUMNS = 24;
    public static final int MAX_PLAYER_ITEM_SLOTS_PER_TICK = 4;
    public static final float TIME_STOP_DISABLE_PROGRESS = 0.85F;
    public static final float TOUCH_BLOCK_DELTA = 1.0F / 60.0F;
    public static final float TOUCH_LIVING_DELTA = 1.0F / 80.0F;
    public static final float TOUCH_ITEM_DELTA = 1.0F / 25.0F;
    public static final float AURA_LIVING_DELTA =
            (float) AURA_CHUNK_COUNT / 2400.0F;
    public static final float AURA_ITEM_DELTA =
            (float) AURA_CHUNK_COUNT / 750.0F;
    public static final float AURA_BLOCK_DELTA =
            (float) (AURA_CHUNK_COUNT * 256)
                    / (MAX_AURA_BLOCK_COLUMNS * 2400.0F);
    private static final int[] SURFACE_OFFSETS = {
            0, -1, -2, -3, -4, -6, -8, -12, -16
    };

    private AgingService() {}

    public static long generation(
            MinecraftServer server, UUID owner) {
        return AgingRemovalData.get(server)
                .generation(owner);
    }

    public static void requestFade(
            MinecraftServer server, UUID owner) {
        AgingRemovalData.get(server).advance(owner);
    }

    public static boolean isCold(
            Level level, BlockPos position) {
        var biome = level.getBiome(position);
        if (level.dimensionType().ultraWarm()
                || biome.is(BiomeTags.IS_NETHER)) {
            return false;
        }
        return biome.value().getBaseTemperature()
                        <= 0.2F
                || biome.is(BiomeTags.IS_TAIGA)
                || biome.is(BiomeTags.IS_MOUNTAIN)
                || biome.is(BiomeTags.IS_HILL);
    }

    public static float biomeAgingMultiplier(
            ServerLevel level, BlockPos position) {
        if (isCold(level, position)) {
            return 0.0F;
        }
        var biome = level.getBiome(position);
        return level.dimensionType().ultraWarm()
                        || biome.is(BiomeTags.IS_NETHER)
                        || biome.is(BiomeTags.IS_SAVANNA)
                        || biome.is(BiomeTags.IS_BADLANDS)
                        || biome.value().getBaseTemperature()
                                >= 0.95F
                ? 1.6F
                : 1.0F;
    }

    public static boolean isAgeableBlock(
            BlockState state) {
        return !state.isAir()
                && !state.is(Blocks.WITHER_ROSE)
                && !state.is(Blocks.SOUL_SAND)
                && HamonUtil.isBlockLiving(state);
    }

    public static boolean isAgeableItem(
            ItemStack stack) {
        return !stack.isEmpty()
                && !stack.is(Items.DIRT)
                && !stack.is(Items.SOUL_SAND)
                && !stack.is(Items.WITHER_ROSE)
                && (HamonUtil.isItemLivingMatter(stack)
                        || isNaturalFood(stack));
    }

    public static boolean hasGratefulDead(
            LivingEntity owner) {
        if (owner == null) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.getPowerType()
                        == GratefulDeadAddon
                                .THE_GRATEFUL_DEAD.get();
    }

    public static boolean isNaturalFood(
            ItemStack stack) {
        return stack.is(Items.BEEF)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.COD)
                || stack.is(Items.MUTTON)
                || stack.is(Items.PORKCHOP)
                || stack.is(Items.RABBIT)
                || stack.is(Items.SALMON)
                || stack.is(Items.PUFFERFISH)
                || stack.is(Items.TROPICAL_FISH)
                || stack.is(Items.APPLE)
                || stack.is(Items.CARROT)
                || stack.is(Items.POTATO)
                || stack.is(Items.MELON_SLICE)
                || stack.is(Items.SWEET_BERRIES)
                || stack.is(Items.GLOW_BERRIES);
    }

    public static boolean isSeedItem(
            ItemStack stack) {
        return stack.is(Items.WHEAT_SEEDS)
                || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.MELON_SEEDS)
                || stack.is(Items.PUMPKIN_SEEDS);
    }

    public static LivingEntity canonicalLivingTarget(
            LivingEntity target) {
        if (target instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return target;
    }

    public static void ageLiving(
            LivingEntity target,
            UUID owner,
            long ownerGeneration,
            float amount) {
        LivingEntity canonical =
                canonicalLivingTarget(target);
        if (!isAgeableLiving(canonical)) {
            return;
        }
        LivingAgingState.get(canonical).age(
                canonical,
                owner,
                ownerGeneration,
                amount);
    }

    public static boolean isAgeableLiving(
            LivingEntity target) {
        return target != null
                && target.isAlive()
                && !(target instanceof StandEntity)
                && HamonUtil.isLiving(target);
    }

    /**
     * 1.16 TimeStopInstantAgingMixin: an aged user's time-stop
     * blink reach, 0 (no blink) from 85% aging.
     */
    public static int agedTimeStopBlinkTicks(
            LivingEntity user, int ticks) {
        if (user == null
                || !user.hasData(
                        GratefulDeadAddon.LIVING_AGING)) {
            return ticks;
        }
        float progress =
                LivingAgingState.get(user).progress();
        return progress > 0.0F
                ? agedActiveTimeStopTicks(ticks, progress)
                : ticks;
    }

    public static int agedActiveTimeStopTicks(
            int currentTicksLeft, float progress) {
        if (currentTicksLeft <= 0 || progress >=
                TIME_STOP_DISABLE_PROGRESS) {
            return 0;
        }
        if (progress <= 0.0F) {
            return currentTicksLeft;
        }
        float normalized = Mth.clamp(
                progress / TIME_STOP_DISABLE_PROGRESS,
                0.0F,
                1.0F);
        double multiplier = Math.pow(
                1.0D - normalized, 2.4D);
        int capped = Math.max(
                1,
                (int) Math.floor(
                        currentTicksLeft
                                * Math.max(0.05D, multiplier)));
        int extraDrain = Math.max(
                1,
                (int) Math.ceil(
                        1.0D
                                + normalized * normalized
                                        * 10.0D));
        return Math.max(
                0,
                Math.min(
                        capped,
                        currentTicksLeft - extraDrain));
    }

    public static ItemStack ageItem(
            ItemStack stack,
            UUID owner,
            long ownerGeneration,
            float amount) {
        if (!isAgeableItem(stack) || amount <= 0.0F) {
            return stack;
        }
        ItemAgingState current =
                stack.getOrDefault(
                        GratefulDeadAddon.ITEM_AGING,
                        ItemAgingState.EMPTY);
        ItemAgingState next = current.age(
                owner, ownerGeneration, amount);
        if (next.progress() >= 1.0F) {
            return terminalItem(stack);
        }
        stack.set(GratefulDeadAddon.ITEM_AGING, next);
        return stack;
    }

    public static ItemStack tickItemState(
            MinecraftServer server, ItemStack stack) {
        ItemAgingState state =
                stack.get(GratefulDeadAddon.ITEM_AGING);
        if (state == null) {
            return stack;
        }
        boolean removal = state.owner()
                .map(owner ->
                        AgingRemovalData.get(server)
                                        .generation(owner)
                                        > state.ownerGeneration()
                                || !hasGratefulDead(
                                        server.getPlayerList()
                                                .getPlayer(owner)))
                .orElse(true);
        if (removal) {
            stack.remove(GratefulDeadAddon.ITEM_AGING);
            return stack;
        }
        if (!state.fading()) {
            return stack;
        }
        ItemAgingState next = state.startFading()
                .fade(
                        1.0F
                                / LivingAgingState
                                        .DEFAULT_FADE_TICKS);
        if (next.progress() <= 0.0F) {
            stack.remove(
                    GratefulDeadAddon.ITEM_AGING);
        }
        else {
            stack.set(
                    GratefulDeadAddon.ITEM_AGING, next);
        }
        return stack;
    }

    public static ItemStack terminalItem(
            ItemStack source) {
        int count = source.getCount();
        if (source.is(Items.GRASS_BLOCK)
                || source.is(Items.MYCELIUM)) {
            return new ItemStack(
                    Items.DIRT,
                    Math.max(1, count));
        }
        if (source.is(ItemTags.FLOWERS)) {
            return new ItemStack(
                    Items.WITHER_ROSE,
                    Math.max(1, count));
        }
        if (source.is(ItemTags.SAPLINGS)) {
            return new ItemStack(
                    Items.DEAD_BUSH,
                    Math.max(1, count));
        }
        return ItemStack.EMPTY;
    }

    public static void tickAura(LivingEntity owner) {
        if (!(owner.level()
                instanceof ServerLevel level)) {
            return;
        }
        if (isCold(level, owner.blockPosition())) {
            return;
        }
        UUID ownerId = owner.getUUID();
        long ownerGeneration =
                generation(level.getServer(), ownerId);
        tickAuraPlayerInventories(
                level, owner, ownerId, ownerGeneration);
        int cell = Math.floorMod(
                (int) level.getGameTime(),
                AURA_CHUNK_COUNT);
        int offsetX =
                cell % AURA_DIAMETER_CHUNKS
                        - AURA_DIAMETER_CHUNKS / 2;
        int offsetZ =
                cell / AURA_DIAMETER_CHUNKS
                        - AURA_DIAMETER_CHUNKS / 2;
        int chunkX = owner.chunkPosition().x + offsetX;
        int chunkZ = owner.chunkPosition().z + offsetZ;
        if (!level.hasChunk(chunkX, chunkZ)) {
            return;
        }

        double minX = chunkX * 16.0D;
        double minZ = chunkZ * 16.0D;
        AABB column = new AABB(
                minX,
                level.getMinBuildHeight(),
                minZ,
                minX + 16.0D,
                level.getMaxBuildHeight(),
                minZ + 16.0D);
        List<Entity> candidates = level.getEntities(
                owner,
                column,
                entity ->
                        entity instanceof LivingEntity
                                || entity
                                        instanceof ItemEntity);
        double radiusSqr = AURA_RADIUS * AURA_RADIUS;
        int candidateCount = candidates.size();
        int examined = Math.min(
                candidateCount,
                MAX_AURA_ENTITY_CANDIDATES);
        int candidateStart = candidateCount > 0
                ? Math.floorMod(
                        (int) level.getGameTime(),
                        candidateCount)
                : 0;
        Set<UUID> agedLivingTargets =
                new HashSet<>();
        for (int offset = 0;
                offset < examined;
                offset++) {
            Entity candidate = candidates.get(
                    (candidateStart + offset)
                            % candidateCount);
            if (candidate.distanceToSqr(owner) > radiusSqr) {
                continue;
            }
            if (candidate instanceof LivingEntity living) {
                LivingEntity canonical =
                        canonicalLivingTarget(living);
                if (canonical == owner
                        || !isAgeableLiving(canonical)
                        || !agedLivingTargets.add(
                                canonical.getUUID())) {
                    continue;
                }
                float speedMultiplier =
                        biomeAgingMultiplier(
                                level,
                                canonical.blockPosition());
                if (speedMultiplier <= 0.0F) {
                    continue;
                }
                ageLiving(
                        canonical,
                        ownerId,
                        ownerGeneration,
                        AURA_LIVING_DELTA
                                * speedMultiplier);
            }
            else if (candidate instanceof ItemEntity item) {
                float speedMultiplier =
                        biomeAgingMultiplier(
                                level,
                                item.blockPosition());
                if (speedMultiplier <= 0.0F) {
                    continue;
                }
                ItemStack aged = ageItem(
                        item.getItem(),
                        ownerId,
                        ownerGeneration,
                        AURA_ITEM_DELTA
                                * speedMultiplier);
                if (aged.isEmpty()) {
                    item.discard();
                }
                else {
                    item.setItem(aged);
                }
            }
        }

        int seed = Mth.floor(level.getGameTime())
                ^ chunkX * 73428767
                ^ chunkZ * 912931;
        BlockAgingData blocks = BlockAgingData.get(level);
        for (int index = 0;
                index < MAX_AURA_BLOCK_COLUMNS;
                index++) {
            seed = seed * 1664525 + 1013904223;
            int x = chunkX * 16 + ((seed >>> 8) & 15);
            seed = seed * 1664525 + 1013904223;
            int z = chunkZ * 16 + ((seed >>> 12) & 15);
            int top = level.getHeight(
                    Heightmap.Types
                            .WORLD_SURFACE,
                    x,
                    z) - 1;
            for (int offset : SURFACE_OFFSETS) {
                BlockPos position =
                        new BlockPos(x, top + offset, z);
                if (position.getY()
                                < level.getMinBuildHeight()
                        || position.getY()
                                >= level.getMaxBuildHeight()) {
                    continue;
                }
                double dx =
                        x + 0.5D - owner.getX();
                double dy =
                        position.getY() + 0.5D
                                - owner.getY();
                double dz =
                        z + 0.5D - owner.getZ();
                if (dx * dx + dy * dy + dz * dz
                        > radiusSqr) {
                    continue;
                }
                if (isAgeableBlock(
                                level.getBlockState(position))
                        && !isCold(level, position)) {
                    blocks.age(
                            level,
                            position,
                            ownerId,
                            ownerGeneration,
                            AURA_BLOCK_DELTA
                                    * biomeAgingMultiplier(
                                            level, position));
                    break;
                }
            }
        }
    }

    private static void tickAuraPlayerInventories(
            ServerLevel level,
            LivingEntity owner,
            UUID ownerId,
            long ownerGeneration) {
        List<ServerPlayer> players = level.players();
        int playerCount = players.size();
        if (playerCount == 0) {
            return;
        }
        int start = Math.floorMod(
                (int) level.getGameTime()
                        + ownerId.hashCode(),
                playerCount);
        int examined = Math.min(
                playerCount, MAX_AURA_PLAYER_CANDIDATES);
        double radiusSqr = AURA_RADIUS * AURA_RADIUS;
        for (int offset = 0; offset < examined; offset++) {
            ServerPlayer player = players.get(
                    (start + offset) % playerCount);
            if (player == owner
                    || !player.isAlive()
                    || player.distanceToSqr(owner) > radiusSqr
                    || isCold(level, player.blockPosition())) {
                continue;
            }
            agePlayerInventory(
                    player, ownerId, ownerGeneration);
        }
    }

    private static void agePlayerInventory(
            ServerPlayer player,
            UUID owner,
            long ownerGeneration) {
        int inventorySize = player.getInventory()
                .getContainerSize();
        int virtualSize = inventorySize + 1;
        float delta = (float) virtualSize
                / (MAX_PLAYER_ITEM_SLOTS_PER_TICK
                        * 750.0F)
                * biomeAgingMultiplier(
                        (ServerLevel) player.level(),
                        player.blockPosition());
        int start = Math.floorMod(
                player.tickCount
                        * MAX_PLAYER_ITEM_SLOTS_PER_TICK,
                virtualSize);
        boolean changed = false;
        for (int offset = 0;
                offset < MAX_PLAYER_ITEM_SLOTS_PER_TICK;
                offset++) {
            int slot = (start + offset) % virtualSize;
            ItemStack stack = slot < inventorySize
                    ? player.getInventory().getItem(slot)
                    : player.containerMenu.getCarried();
            if (!isAgeableItem(stack)) {
                continue;
            }
            ItemStack aged = ageItem(
                    stack, owner, ownerGeneration, delta);
            if (slot < inventorySize) {
                player.getInventory().setItem(slot, aged);
            }
            else {
                player.containerMenu.setCarried(aged);
            }
            changed = true;
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    public static void tickPlayerInventory(
            Player player) {
        if (!(player.level()
                instanceof ServerLevel level)) {
            return;
        }
        int inventorySize = player.getInventory()
                .getContainerSize();
        int virtualSize = inventorySize + 1;
        int start = Math.floorMod(
                player.tickCount
                        * MAX_PLAYER_ITEM_SLOTS_PER_TICK,
                virtualSize);
        boolean changed = false;
        for (int offset = 0;
                offset < MAX_PLAYER_ITEM_SLOTS_PER_TICK;
                offset++) {
            int slot = (start + offset) % virtualSize;
            ItemStack stack = slot < inventorySize
                    ? player.getInventory().getItem(slot)
                    : player.containerMenu.getCarried();
            if (!stack.has(GratefulDeadAddon.ITEM_AGING)) {
                continue;
            }
            ItemStack next = tickItemState(
                    level.getServer(), stack);
            if (slot < inventorySize) {
                player.getInventory().setItem(
                        slot, next);
            }
            else {
                player.containerMenu.setCarried(next);
            }
            changed = true;
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }
}
