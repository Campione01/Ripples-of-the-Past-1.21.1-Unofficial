package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rotp.addons.d4c.travel.D4CDimensionTargets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rotp.core.powersystem.standpower.StandPower;

public final class D4CParallelItemService {
    public static final String PARALLEL_WORLD_TAG =
            "ParallelWorldItem";
    public static final String PLAYER_TOSSED_TAG = "PlayerTossed";
    public static final String PULLED_BY_D4C_TAG = "PulledByD4CSkill";
    private static final double ATTRACTION_RANGE = 5.0D;
    private static final double CONTACT_RANGE = 0.2D;
    private static final Map<UUID, DeathDropRecord> RECENT_DEATHS =
            new ConcurrentHashMap<>();

    private record DeathDropRecord(
            ResourceKey<Level> dimension,
            Vec3 position,
            long recordedAtMillis) {}

    private D4CParallelItemService() {}

    public static boolean isParallel(ItemStack stack) {
        return hasStackTag(stack, PARALLEL_WORLD_TAG);
    }

    public static boolean isPlayerTossed(ItemStack stack) {
        return hasStackTag(stack, PLAYER_TOSSED_TAG);
    }

    public static boolean hasParallelOriginTag(ItemStack stack) {
        return isParallel(stack) || isPlayerTossed(stack);
    }

    private static boolean hasStackTag(
            ItemStack stack, String tagName) {
        // read-only lookup; no tag copy per call
        return !stack.isEmpty()
                && stack.getOrDefault(
                                DataComponents.CUSTOM_DATA,
                                CustomData.EMPTY)
                        .getUnsafe()
                        .getBoolean(tagName);
    }

    public static void markParallel(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putBoolean(PARALLEL_WORLD_TAG, true));
    }

    public static boolean isAnnihilationPair(
            ItemStack first, ItemStack second) {
        return D4CRuntimeRules.isAnnihilationPair(
                !first.isEmpty() && !second.isEmpty(),
                ItemStack.isSameItem(first, second),
                isParallel(first),
                isParallel(second));
    }

    public static void markPlayerTossed(ItemEntity item) {
        item.getPersistentData().putBoolean(
                PLAYER_TOSSED_TAG, true);
    }

    public static void markPulledByD4C(LivingEntity entity) {
        entity.getPersistentData().putBoolean(
                PULLED_BY_D4C_TAG, true);
    }

    public static void recordRelevantDeath(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        boolean parallelPlayer = entity instanceof Player
                && level.dimension().location().equals(
                        D4CDimensionTargets.PARALLEL_WORLD);
        boolean pulledEntity = !(entity instanceof Player)
                && entity.getPersistentData().getBoolean(
                        PULLED_BY_D4C_TAG);
        if (!parallelPlayer && !pulledEntity) {
            return;
        }
        long now = System.currentTimeMillis();
        cleanDeathDropRecords(now);
        RECENT_DEATHS.put(
                entity.getUUID(),
                new DeathDropRecord(
                        level.dimension(), entity.position(), now));
    }

    static boolean isProtectedDeathDrop(
            ServerLevel level, ItemEntity item) {
        long now = System.currentTimeMillis();
        cleanDeathDropRecords(now);
        return RECENT_DEATHS.values().stream().anyMatch(
                record -> record.dimension().equals(level.dimension())
                        && D4CRuntimeRules
                                .isWithinDeathDropProtection(
                                        record.position().x,
                                        record.position().y,
                                        record.position().z,
                                        item.getX(),
                                        item.getY(),
                                        item.getZ()));
    }

    private static void cleanDeathDropRecords(long nowMillis) {
        RECENT_DEATHS.values().removeIf(
                record -> !D4CRuntimeRules
                        .isDeathDropProtectionActive(
                                record.recordedAtMillis(),
                                nowMillis));
    }

    public static void onItemJoined(
            ServerLevel level, ItemEntity item) {
        if (level.dimension().location().equals(
                        D4CDimensionTargets.PARALLEL_WORLD)
                && !item.getPersistentData().getBoolean(
                        PLAYER_TOSSED_TAG)
                && !isProtectedDeathDrop(level, item)) {
            markParallelOrigin(item);
        }
    }

    static void markParallelOrigin(ItemEntity item) {
        ItemStack markedStack = item.getItem().copy();
        markParallel(markedStack);
        item.setItem(markedStack);
    }

    public static void tick(MinecraftServer server) {
        cleanDeathDropRecords(System.currentTimeMillis());
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD
                    || level.dimension().location().equals(
                            D4CDimensionTargets.PARALLEL_WORLD)) {
                tickItems(level);
            }
        }
    }

    static void clearRuntimeState() {
        RECENT_DEATHS.clear();
    }

    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        StandPower power = StandPower.get(player);
        boolean hasStand = power != null && power.hasPower();
        boolean d4cStand = hasStand
                && power.getPowerType() == D4CAddon.D4C.get();
        if (!D4CRuntimeRules.shouldScanInventoryForAnnihilation(
                hasStand, d4cStand)) {
            return;
        }
        int size = player.getInventory().getContainerSize();
        for (int first = 0; first < size; first++) {
            ItemStack firstStack =
                    player.getInventory().getItem(first);
            if (firstStack.isEmpty()) {
                continue;
            }
            for (int second = first + 1; second < size; second++) {
                ItemStack secondStack =
                        player.getInventory().getItem(second);
                if (!isAnnihilationPair(firstStack, secondStack)) {
                    continue;
                }
                player.getInventory().setItem(first, ItemStack.EMPTY);
                player.getInventory().setItem(second, ItemStack.EMPTY);
                annihilate(
                        player.serverLevel(),
                        player.position().add(0.0D, 1.0D, 0.0D),
                        3.0F,
                        D4CPhase2BSpec
                                .PARALLEL_ITEM_INVENTORY_DAMAGE_RADIUS);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "rotp_d4c.message.parallel_items_annihilated"),
                        false);
                return;
            }
        }
    }

    static void tickItems(ServerLevel level) {
        List<ItemEntity> items = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item
                    && item.isAlive()
                    && !item.getItem().isEmpty()) {
                items.add(item);
            }
        }
        // flag read once per item, not per pair
        boolean[] parallel = new boolean[items.size()];
        int parallelCount = 0;
        for (int index = 0; index < items.size(); index++) {
            parallel[index] = isParallel(items.get(index).getItem());
            if (parallel[index]) {
                parallelCount++;
            }
        }
        // a pair needs one parallel and one native item
        boolean mixedFlags = parallelCount > 0
                && parallelCount < items.size();
        for (int first = 0; first < items.size(); first++) {
            ItemEntity firstItem = items.get(first);
            if (!firstItem.isAlive()) {
                continue;
            }
            if (annihilateMatchingBlock(
                    level, firstItem, parallel[first])) {
                return;
            }
            if (!mixedFlags) {
                continue;
            }
            for (int second = first + 1;
                    second < items.size();
                    second++) {
                ItemEntity secondItem = items.get(second);
                if (!secondItem.isAlive()
                        || parallel[first] == parallel[second]
                        || !D4CRuntimeRules.isAnnihilationPair(
                                true,
                                ItemStack.isSameItem(
                                        firstItem.getItem(),
                                        secondItem.getItem()),
                                parallel[first],
                                parallel[second])) {
                    continue;
                }
                double distance =
                        firstItem.distanceTo(secondItem);
                if (distance < CONTACT_RANGE) {
                    Vec3 midpoint = firstItem.position()
                            .add(secondItem.position())
                            .scale(0.5D);
                    firstItem.discard();
                    secondItem.discard();
                    annihilate(
                            level,
                            midpoint,
                            3.0F,
                            D4CPhase2BSpec
                                    .PARALLEL_ITEM_ENTITY_DAMAGE_RADIUS);
                    return;
                }
                if (distance <= ATTRACTION_RANGE) {
                    attract(firstItem, secondItem, distance);
                }
            }
        }
    }

    private static boolean annihilateMatchingBlock(
            ServerLevel level, ItemEntity item, boolean parallelItem) {
        ItemStack stack = item.getItem();
        boolean parallelLevel = level.dimension().location().equals(
                D4CDimensionTargets.PARALLEL_WORLD);
        boolean playerTossed =
                item.getPersistentData().getBoolean(PLAYER_TOSSED_TAG);
        // 1.16 scanned only tossed items whose flag fits the level
        if (!D4CRuntimeRules.shouldAnnihilateMatchingBlock(
                parallelItem, parallelLevel, playerTossed, true, false)) {
            return false;
        }
        Block matchingBlock = Block.byItem(stack.getItem());
        if (matchingBlock == Blocks.AIR) {
            return false;
        }
        BlockPos origin = item.blockPosition();
        for (BlockPos position : BlockPos.betweenClosed(
                origin.offset(-1, -1, -1),
                origin.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(position);
            if (!D4CRuntimeRules.shouldAnnihilateMatchingBlock(
                    parallelItem,
                    parallelLevel,
                    playerTossed,
                    state.getBlock() == matchingBlock,
                    BuiltInRegistries.BLOCK
                            .getKey(state.getBlock())
                            .getPath()
                            .contains("parallel"))) {
                continue;
            }
            item.discard();
            annihilate(
                    level,
                    Vec3.atCenterOf(position),
                    4.0F,
                    D4CPhase2BSpec
                            .PARALLEL_ITEM_ENTITY_DAMAGE_RADIUS);
            return true;
        }
        return false;
    }

    private static void attract(
            ItemEntity first,
            ItemEntity second,
            double distance) {
        Vec3 direction = second.position()
                .subtract(first.position())
                .normalize();
        double strength =
                0.1D * (ATTRACTION_RANGE - distance)
                        / ATTRACTION_RANGE;
        first.setDeltaMovement(first.getDeltaMovement()
                .add(direction.scale(strength)));
        second.setDeltaMovement(second.getDeltaMovement()
                .add(direction.scale(-strength)));
    }

    private static void annihilate(
            ServerLevel level,
            Vec3 position,
            float explosionRadius,
            double damageRadius) {
        level.explode(
                null,
                position.x,
                position.y,
                position.z,
                explosionRadius,
                Level.ExplosionInteraction.TNT);
        level.playSound(
                null,
                position.x,
                position.y,
                position.z,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS,
                1.0F,
                1.0F);
        level.sendParticles(
                D4CAddon.ANNIHILATE.get(),
                position.x,
                position.y,
                position.z,
                40,
                0.8D,
                0.8D,
                0.8D,
                0.08D);
        AABB bounds = new AABB(
                position.x - damageRadius,
                position.y - damageRadius,
                position.z - damageRadius,
                position.x + damageRadius,
                position.y + damageRadius,
                position.z + damageRadius);
        for (LivingEntity living : level.getEntitiesOfClass(
                LivingEntity.class, bounds)) {
            if (living.position().distanceTo(position) > damageRadius) {
                continue;
            }
            float damage = D4CRuntimeRules.annihilationDamage(
                    living.getHealth(),
                    D4CPhase2BSpec.ANNIHILATION_KILL_THRESHOLD,
                    D4CPhase2BSpec.ANNIHILATION_DAMAGE_FRACTION);
            // 1.16 GENERIC: armor bypassed, Resistance and creative apply
            living.hurt(level.damageSources().generic(), damage);
        }
    }
}
