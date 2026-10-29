package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.addons.d4c.network.DevilPalmMarkerPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The donor's Corpse Finder ("Guardian Spirit", {@code com.mrlang.rotp_d4c.al.ab} + {@code al.ae}). 1.16 reached it
 * through a private Ctrl+R key binding; the port reaches it through the {@code corpse_finder} wheel ability
 * ({@link rotp.addons.d4c.ability.D4CCorpseFinderAbility}).
 *
 * <p>A player holding at least one Saint Corpse part is pointed at a desert location 5000-25000 blocks away for
 * the next part they neither hold nor have already obtained through the finder. Within 200 blocks of that
 * location the {@code rotp_d4c:devil_palm} structure is placed and, three seconds later, the part is put into
 * one of its trapped chests (tagged {@code from_finder_system}; picking it up records the part as obtained).
 * Once every part has been obtained through the finder the system closes for that player.</p>
 *
 * <p>Storage follows the donor: the current target lives in the root persistent data (dropped on respawn, as in
 * 1.16), the obtained-parts record in {@link Player#PERSISTED_NBT_TAG} (the donor copied its capability on
 * clone). The donor's {@code Thread.sleep} timers become server-tick tasks.</p>
 */
public final class D4CCorpseFinderService {
    static final String FINDER_TAG = "CorpseFinder";
    private static final String CURRENT_TARGET = "CurrentTarget";
    private static final String TARGET_X = "TargetPosX";
    private static final String TARGET_Y = "TargetPosY";
    private static final String TARGET_Z = "TargetPosZ";
    private static final String CHEST_GENERATED = "ChestGenerated";
    private static final String LAST_USE_TIME = "LastUseTime";
    static final String OBTAINED_PARTS = "FinderObtainedParts";
    static final String FROM_FINDER_SYSTEM = "from_finder_system";
    private static final long COOLDOWN_MILLIS = 6000L;
    private static final double STRUCTURE_TRIGGER_DISTANCE = 200.0D;
    private static final double MARKER_BROADCAST_DISTANCE_SQR = 62500.0D;
    private static final int CHEST_FILL_DELAY_TICKS = 60;
    private static final int GUIDE_FRAMES = 60;

    private static final List<ScheduledTask> TASKS = new ArrayList<>();

    private D4CCorpseFinderService() {}

    /** Donor order ({@code al.ab.am}): heart, right arm, eyes, ears, legs, thorax, head, spine, left arm. */
    static List<Item> parts() {
        return List.of(
                D4CAddon.SAINT_CORPSE_HEART.get(),
                D4CAddon.SAINT_CORPSE_RIGHT_ARM.get(),
                D4CAddon.SAINT_CORPSE_EYES.get(),
                D4CAddon.SAINT_CORPSE_EARS.get(),
                D4CAddon.SAINT_CORPSE_LEGS.get(),
                D4CAddon.SAINT_CORPSE_THORAX.get(),
                D4CAddon.SAINT_CORPSE_HEAD.get(),
                D4CAddon.SAINT_CORPSE_SPINE.get(),
                D4CAddon.SAINT_CORPSE_LEFT_ARM.get());
    }

    private static String partNameKey(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return "corpse.part." + path.substring("saint_corpse_".length());
    }

    @Nullable
    private static Item partFromNameKey(String key) {
        for (Item item : parts()) {
            if (partNameKey(item).equals(key)) {
                return item;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ request (donor al.ab.aj)

    public static void requestGuidance(ServerPlayer player) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            message(player, Component.translatable(
                    "message.rotp_d4c.corpse_finder.only_overworld"));
            return;
        }
        long remaining = cooldownRemaining(player);
        if (remaining > 0L) {
            message(player, Component.translatable(
                    "message.rotp_d4c.corpse_finder_cooldown",
                    (int) Math.ceil(remaining / 1000.0D)));
            return;
        }
        boolean allObtained = true;
        for (Item item : parts()) {
            if (!hasObtainedThroughFinder(player, item)) {
                allObtained = false;
                break;
            }
        }
        if (allObtained) {
            message(player, Component.translatable(
                    "message.rotp_d4c.corpse_finder.system_closed"));
            return;
        }
        if (has(player, D4CAddon.COMPLETE_SAINT_CORPSE.get())) {
            message(player, Component.translatable(
                    "message.rotp_d4c.corpse_finder.complete_no_need"));
            return;
        }
        if (!hasAnyPart(player)) {
            message(player, Component.translatable(
                    "message.rotp_d4c.corpse_finder.need_part_first"));
            return;
        }
        Item currentTarget = currentTarget(player);
        if (currentTarget != null) {
            if (has(player, currentTarget)) {
                clearTarget(player);
            }
            else {
                repeatGuidance(player);
                markUsed(player);
                return;
            }
        }
        Item next = nextPart(player);
        if (next == null) {
            message(player, Component.translatable(
                    has(player, D4CAddon.COMPLETE_SAINT_CORPSE.get())
                            ? "message.rotp_d4c.corpse_finder.congratulations_complete"
                            : "message.rotp_d4c.corpse_finder.congratulations_all"));
            return;
        }
        chooseTarget(player, next);
        markUsed(player);
    }

    public static boolean hasAnyPart(Player player) {
        for (Item item : parts()) {
            if (has(player, item)) {
                return true;
            }
        }
        return has(player, D4CAddon.COMPLETE_SAINT_CORPSE.get());
    }

    static boolean has(Player player, Item item) {
        return D4CPhase2BService.hasCorpseItem(player, item);
    }

    @Nullable
    private static Item nextPart(ServerPlayer player) {
        for (Item item : parts()) {
            if (!has(player, item)
                    && !hasObtainedThroughFinder(player, item)) {
                return item;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ target selection

    private static void chooseTarget(ServerPlayer player, Item part) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        BlockPos target = findDesertLocation(level, origin, level.getRandom());
        if (target == null) {
            message(player, Component.translatable(
                    "message.rotp_d4c.corpse_finder.no_suitable_location"));
            return;
        }
        CompoundTag finder = new CompoundTag();
        finder.putString(CURRENT_TARGET, partNameKey(part));
        finder.putInt(TARGET_X, target.getX());
        finder.putInt(TARGET_Y, target.getY());
        finder.putInt(TARGET_Z, target.getZ());
        finder.putBoolean(CHEST_GENERATED, false);
        player.getPersistentData().put(FINDER_TAG, finder);
        sendGuidance(player, target);
    }

    @Nullable
    private static BlockPos findDesertLocation(
            ServerLevel level, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < 50; attempt++) {
            double angle = random.nextDouble() * 2.0D * Math.PI;
            int distance = 5000 + random.nextInt(20001);
            BlockPos candidate = new BlockPos(
                    origin.getX() + (int) (Math.cos(angle) * distance),
                    64 + random.nextInt(2),
                    origin.getZ() + (int) (Math.sin(angle) * distance));
            // The 1.16 desert set (desert, desert_hills, desert_lakes) collapsed into minecraft:desert in 1.18.
            if (level.getBiome(candidate).is(Biomes.DESERT)) {
                return candidate;
            }
        }
        return null;
    }

    private static void repeatGuidance(ServerPlayer player) {
        CompoundTag finder = player.getPersistentData()
                .getCompound(FINDER_TAG);
        sendGuidance(player, new BlockPos(
                finder.getInt(TARGET_X),
                finder.getInt(TARGET_Y),
                finder.getInt(TARGET_Z)));
    }

    private static void sendGuidance(ServerPlayer player, BlockPos target) {
        BlockPos from = player.blockPosition();
        double distance = Math.sqrt(from.distSqr(target));
        message(player, Component.translatable(
                "message.rotp_d4c.corpse_finder.guidance",
                direction(from, target),
                Component.literal(String.format("%.0f", distance))
                        .append(Component.translatable(
                                "unit.rotp_d4c.blocks"))));
        playGuide(player, target);
    }

    private static MutableComponent direction(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double degrees = Math.atan2(dz, dx) * 180.0D / Math.PI;
        if (degrees < 0.0D) {
            degrees += 360.0D;
        }
        String horizontal;
        if (degrees >= 337.5D || degrees < 22.5D) {
            horizontal = "east";
        }
        else if (degrees < 67.5D) {
            horizontal = "southeast";
        }
        else if (degrees < 112.5D) {
            horizontal = "south";
        }
        else if (degrees < 157.5D) {
            horizontal = "southwest";
        }
        else if (degrees < 202.5D) {
            horizontal = "west";
        }
        else if (degrees < 247.5D) {
            horizontal = "northwest";
        }
        else if (degrees < 292.5D) {
            horizontal = "north";
        }
        else {
            horizontal = "northeast";
        }
        MutableComponent text = Component.empty();
        if (dy > 10.0D) {
            text.append(Component.translatable("direction.rotp_d4c.above"));
        }
        else if (dy < -10.0D) {
            text.append(Component.translatable("direction.rotp_d4c.below"));
        }
        return text.append(Component.translatable(
                "direction.rotp_d4c." + horizontal));
    }

    /** Donor {@code al.ae.aa}: guide sound plus a 3-second flame arrow pointing 6 blocks toward the target. */
    private static void playGuide(ServerPlayer player, BlockPos target) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position();
        level.playSound(null, player.blockPosition(),
                D4CAddon.CORPSE_FINDER_GUIDE.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        Vec3 delta = new Vec3(
                target.getX() - origin.x,
                target.getY() - origin.y,
                target.getZ() - origin.z);
        double length = delta.length();
        if (length <= 0.0D) {
            return;
        }
        Vec3 direction = delta.scale(1.0D / length);
        long now = level.getServer().getTickCount();
        for (int frame = 0; frame < GUIDE_FRAMES; frame++) {
            schedule(level.getServer(), now + frame,
                    () -> guideFrame(level, origin, direction));
        }
    }

    private static void guideFrame(ServerLevel level, Vec3 origin, Vec3 dir) {
        for (int i = 0; i <= 12; i++) {
            double t = i / 12.0D;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    origin.x + dir.x * 6.0D * t,
                    origin.y + 1.0D + dir.y * 6.0D * t,
                    origin.z + dir.z * 6.0D * t,
                    2, 0.1D, 0.1D, 0.1D, 0.02D);
        }
        double tipX = origin.x + dir.x * 6.0D;
        double tipY = origin.y + 1.0D + dir.y * 6.0D;
        double tipZ = origin.z + dir.z * 6.0D;
        for (int i = 0; i < 8; i++) {
            double angle = i / 8.0D * 2.0D * Math.PI;
            level.sendParticles(ParticleTypes.FLAME,
                    tipX + Math.cos(angle) * 0.3D,
                    tipY,
                    tipZ + Math.sin(angle) * 0.3D,
                    1, 0.05D, 0.05D, 0.05D, 0.01D);
        }
        level.sendParticles(ParticleTypes.FLAME, tipX, tipY, tipZ,
                3, 0.1D, 0.1D, 0.1D, 0.02D);
    }

    // ------------------------------------------------------------------ proximity check (donor al.ab.am, every 20 ticks)

    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0
                || !player.level().dimension().equals(Level.OVERWORLD)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(FINDER_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag finder = data.getCompound(FINDER_TAG);
        if (!finder.contains(CURRENT_TARGET) || finder.getBoolean(CHEST_GENERATED)) {
            return;
        }
        BlockPos target = new BlockPos(
                finder.getInt(TARGET_X),
                finder.getInt(TARGET_Y),
                finder.getInt(TARGET_Z));
        if (Math.sqrt(player.blockPosition().distSqr(target))
                > STRUCTURE_TRIGGER_DISTANCE) {
            return;
        }
        String partKey = finder.getString(CURRENT_TARGET);
        placeDevilPalm(player.serverLevel(), target, partFromNameKey(partKey));
        finder.putBoolean(CHEST_GENERATED, true);
        data.put(FINDER_TAG, finder);
        message(player, Component.translatable(
                "message.rotp_d4c.corpse_finder.structure_appeared",
                Component.translatable(partKey)));
    }

    private static void placeDevilPalm(
            ServerLevel level, BlockPos requested, @Nullable Item part) {
        BlockPos pos = new BlockPos(
                requested.getX(),
                Math.max(1, Math.min(255, requested.getY())),
                requested.getZ());
        Optional<StructureTemplate> template = level.getStructureManager()
                .get(D4CAddon.id("devil_palm"));
        if (template.isEmpty()) {
            return;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        BlockPos origin = template.get().getZeroPositionWithTransform(
                pos, Mirror.NONE, Rotation.NONE);
        template.get().placeInWorld(
                level, origin, origin, settings, level.getRandom(), 2);
        broadcastMarker(level, pos);
        if (part != null) {
            schedule(level.getServer(),
                    level.getServer().getTickCount() + CHEST_FILL_DELAY_TICKS,
                    () -> fillTrappedChest(level, pos, part));
        }
    }

    /** Donor {@code al.ab.aa(ServerWorld, BlockPos, Item)}: first trapped chest in x → y → z scan order. */
    private static void fillTrappedChest(ServerLevel level, BlockPos center, Item part) {
        ItemStack stack = new ItemStack(part);
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putBoolean(FROM_FINDER_SYSTEM, true));
        List<TrappedChestBlockEntity> chests = new ArrayList<>();
        int minX = center.getX() - 50;
        int maxX = center.getX() + 50;
        int minY = Math.max(0, center.getY() - 25);
        int maxY = Math.min(255, center.getY() + 50);
        int minZ = center.getZ() - 50;
        int maxZ = center.getZ() + 50;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (Map.Entry<BlockPos, BlockEntity> entry
                        : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (entry.getValue() instanceof TrappedChestBlockEntity chest
                            && pos.getX() >= minX && pos.getX() <= maxX
                            && pos.getY() >= minY && pos.getY() <= maxY
                            && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                        chests.add(chest);
                    }
                }
            }
        }
        chests.sort(Comparator
                .comparingInt((TrappedChestBlockEntity chest) -> chest.getBlockPos().getX())
                .thenComparingInt(chest -> chest.getBlockPos().getY())
                .thenComparingInt(chest -> chest.getBlockPos().getZ()));
        for (TrappedChestBlockEntity chest : chests) {
            if (chest.getItem(13).isEmpty()) {
                chest.setItem(13, stack.copy());
                chest.setChanged();
                broadcastMarker(level, center);
                return;
            }
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).isEmpty()) {
                    chest.setItem(slot, stack.copy());
                    chest.setChanged();
                    broadcastMarker(level, center);
                    return;
                }
            }
        }
        level.setBlock(center, Blocks.TRAPPED_CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(center) instanceof TrappedChestBlockEntity chest) {
            chest.setItem(13, stack.copy());
            chest.setChanged();
            broadcastMarker(level, center);
        }
    }

    private static void broadcastMarker(ServerLevel level, BlockPos pos) {
        DevilPalmMarkerPayload payload = new DevilPalmMarkerPayload(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())
                    < MARKER_BROADCAST_DISTANCE_SQR) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // ------------------------------------------------------------------ state

    @Nullable
    private static Item currentTarget(ServerPlayer player) {
        CompoundTag finder = player.getPersistentData().getCompound(FINDER_TAG);
        return finder.contains(CURRENT_TARGET)
                ? partFromNameKey(finder.getString(CURRENT_TARGET))
                : null;
    }

    private static void clearTarget(ServerPlayer player) {
        player.getPersistentData().remove(FINDER_TAG);
    }

    private static long cooldownRemaining(ServerPlayer player) {
        CompoundTag finder = player.getPersistentData().getCompound(FINDER_TAG);
        if (!finder.contains(LAST_USE_TIME)) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - finder.getLong(LAST_USE_TIME);
        return elapsed >= COOLDOWN_MILLIS ? 0L : COOLDOWN_MILLIS - elapsed;
    }

    private static void markUsed(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag finder = data.getCompound(FINDER_TAG);
        finder.putLong(LAST_USE_TIME, System.currentTimeMillis());
        data.put(FINDER_TAG, finder);
    }

    static boolean hasObtainedThroughFinder(Player player, Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        ListTag list = persisted(player).getList(OBTAINED_PARTS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(id)) {
                return true;
            }
        }
        return false;
    }

    static void recordObtainedThroughFinder(Player player, String itemId) {
        CompoundTag persisted = persisted(player);
        ListTag list = persisted.getList(OBTAINED_PARTS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(itemId)) {
                return;
            }
        }
        list.add(StringTag.valueOf(itemId));
        persisted.put(OBTAINED_PARTS, list);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void message(ServerPlayer player, Component text) {
        player.sendSystemMessage(text);
    }

    // ------------------------------------------------------------------ server-tick scheduling

    private static void schedule(MinecraftServer server, long runAtTick, Runnable task) {
        synchronized (TASKS) {
            TASKS.add(new ScheduledTask(server, runAtTick, task));
        }
    }

    public static void tickServer(MinecraftServer server) {
        List<Runnable> due = new ArrayList<>();
        synchronized (TASKS) {
            Iterator<ScheduledTask> iterator = TASKS.iterator();
            while (iterator.hasNext()) {
                ScheduledTask task = iterator.next();
                if (task.server() != server) {
                    continue;
                }
                if (server.getTickCount() >= task.runAtTick()) {
                    due.add(task.task());
                    iterator.remove();
                }
            }
        }
        due.forEach(Runnable::run);
    }

    public static void clearRuntimeState() {
        synchronized (TASKS) {
            TASKS.clear();
        }
    }

    private record ScheduledTask(MinecraftServer server, long runAtTick, Runnable task) {}
}
