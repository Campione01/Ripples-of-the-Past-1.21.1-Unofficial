package rotp.addons.ger.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.ability.GerReturnToZeroAbility;
import rotp.addons.ger.network.GerRtzPayloads;
import rotp.addons.ger.network.GerRtzPayloads.GhostTrail;
import rotp.addons.ger.network.GerRtzPayloads.MarkStatePayload;
import rotp.addons.ger.network.GerRtzPayloads.StartPayload;
import rotp.addons.ger.network.GerRtzPayloads.StopPayload;
import rotp.addons.ger.state.GerRtzTimeStopPolicy.GenerationTracker;
import rotp.addons.ger.state.GerRtzTimeStopPolicy.InstanceKey;
import rotp.addons.ger.state.GerRtzTimeStopPolicy.InstanceRegion;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.slf4j.Logger;

/**
 * Transient, module-owned Return to Zero history with a staged atomic commit
 * and an inverse journal. No core hooks, reflection, or shared state are used.
 */
public final class GerRtzRewindService {
    public static final int MAX_BLOCK_OPS = 200_000;
    public static final int ENTITY_SAMPLE_INTERVAL_TICKS = 10;
    public static final int MAX_ENTITY_FRAMES = 1_200;
    public static final int MAX_GHOST_POINTS_PER_ENTITY = 120;
    public static final double MIN_GHOST_DISTANCE = 0.75D;
    public static final int END_SOUND_MIN_DURATION_TICKS = 200;
    public static final int VFX_FADE_IN_TICKS = 8;
    public static final int VFX_FADE_OUT_TICKS = 15;
    public static final int VFX_EXPAND_TICKS = 4;
    public static final int VFX_CLOSE_TICKS = 0;
    public static final int VFX_MODE_NORMAL = 0;
    public static final int VFX_MODE_TIME_ERASE = 1;
    public static final int AUTOMATIC_RTZ_DELAY_TICKS = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KING_CRIMSON_MOD_ID =
            "rotp_kingcrimson";
    private static final int BLOCK_UPDATE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS;
    private static final Map<MinecraftServer, ServerHistory> HISTORIES =
            new WeakHashMap<>();
    private static final ThreadLocal<Integer> APPLY_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private GerRtzRewindService() {}

    public enum Result {
        SUCCESS(""),
        FORCE_CLOSED(""),
        WRONG_DIMENSION(
                "geraddon.return_to_zero.wrong_dimension"),
        NO_MARK("geraddon.return_to_zero.no_mark"),
        NOT_ENOUGH_STAMINA("not_enough_stamina"),
        UNLOADED_TARGET(
                "geraddon.return_to_zero.unloaded_target"),
        UNSAFE_HISTORY(
                "geraddon.return_to_zero.unsafe_history"),
        COMMIT_FAILED(
                "geraddon.return_to_zero.commit_failed");

        private final String translationKey;

        Result(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public record BlockPreimage(
            ServerLevel level,
            long tick,
            BlockPos position,
            CompoundTag state,
            CompoundTag blockEntity,
            boolean restorable) {
        private BlockPreimage copy() {
            return new BlockPreimage(
                    level,
                    tick,
                    position.immutable(),
                    state.copy(),
                    blockEntity != null
                            ? blockEntity.copy()
                            : null,
                    restorable);
        }
    }

    public static Result setMark(
            ServerLevel level,
            ServerPlayer owner,
            int chunkRadius) {
        if (owner.level() != level) {
            return Result.WRONG_DIMENSION;
        }
        ServerHistory history = history(level.getServer());
        Mark mark;
        try {
            mark = Mark.capture(
                    level,
                    owner,
                    Math.max(0, chunkRadius),
                    history);
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not capture GER Return to Zero mark",
                    exception);
            return Result.UNSAFE_HISTORY;
        }
        history.pendingAutomaticRtz.remove(owner.getUUID());
        history.marks.put(owner.getUUID(), mark);
        syncMark(owner, true);
        return Result.SUCCESS;
    }

    public static boolean hasMark(
            ServerPlayer owner) {
        Mark mark = history(owner.server)
                .marks.get(owner.getUUID());
        return mark != null
                && mark.dimension().equals(
                        owner.serverLevel().dimension());
    }

    public static boolean hasActivePresentation(
            ServerPlayer owner) {
        return history(owner.server)
                .presentations
                .containsKey(owner.getUUID());
    }

    public static void timeStopAdded(
            ServerLevel level,
            TimeStopState.Instance instance) {
        if (!instance.isActive()) {
            return;
        }
        ServerHistory history = history(level.getServer());
        InstanceKey key = registerTimeStopAdded(
                history, level, instance);
        InstanceRegion region = new InstanceRegion(
                key,
                instance.centerPos().x,
                instance.centerPos().z,
                instance.chunkRange());
        history.marks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    UUID owner = entry.getKey();
                    Mark mark = entry.getValue();
                    if (mark.dimension().equals(level.dimension())
                            && GerRtzTimeStopPolicy.shouldTerminate(
                                    mark.timeStopInstances(),
                                    mark.centerChunkX(),
                                    mark.centerChunkZ(),
                                    mark.chunkRadius(),
                                    region)) {
                        history.pendingAutomaticRtz.putIfAbsent(
                                owner,
                                new PendingAutomaticRtz(
                                        level.dimension(),
                                        (long) level.getServer()
                                                .getTickCount()
                                                + AUTOMATIC_RTZ_DELAY_TICKS,
                                        mark));
                    }
                });
    }

    public static void timeStopRemoved(
            ServerLevel level,
            TimeStopState.Instance instance) {
        ServerHistory history = history(level.getServer());
        boolean replacementActive = level.hasData(
                        ModDataAttachmentTypes.TIME_STOP.get())
                && level.getData(ModDataAttachmentTypes.TIME_STOP.get())
                        .getInstance(instance.id())
                        .isPresent();
        GenerationTracker<TimeStopState.Instance> active =
                history.activeTimeStops.get(level.dimension());
        if (active == null) {
            return;
        }
        active.removed(instance.id(), replacementActive);
        if (active.isEmpty()) {
            history.activeTimeStops.remove(level.dimension());
        }
    }

    public static Result rewind(
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity performer,
            StandPower power,
            float staminaCost) {
        ServerHistory history = history(level.getServer());
        if (history.presentations.containsKey(owner.getUUID())) {
            closePresentation(history, owner.getUUID(), true);
            return Result.FORCE_CLOSED;
        }
        Mark mark = history.marks.get(owner.getUUID());
        if (mark == null) {
            syncMark(owner, false);
            return Result.NO_MARK;
        }
        if (owner.level() != level
                || !mark.dimension().equals(level.dimension())) {
            return Result.WRONG_DIMENSION;
        }
        Set<UUID> activeTimeEraseUsers =
                captureActiveTimeEraseUsers(level);

        StageResult staged;
        try {
            staged = stage(
                    level, owner, performer, mark);
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not stage GER Return to Zero",
                    exception);
            return Result.UNSAFE_HISTORY;
        }
        if (staged.failure() != null) {
            return staged.failure();
        }
        RewindPlan plan = staged.plan();
        float inverseStamina =
                power != null ? power.getStamina() : 0.0F;
        if (power == null
                || !power.consumeStamina(staminaCost)) {
            if (power != null) {
                power.setStamina(inverseStamina);
            }
            return Result.NOT_ENOUGH_STAMINA;
        }
        if (!commit(level, plan)) {
            power.setStamina(inverseStamina);
            return Result.COMMIT_FAILED;
        }

        forceEndTimeErase(level, activeTimeEraseUsers);
        terminatePostMarkTimeStops(history, level, mark);
        history.marks.remove(owner.getUUID());
        history.pendingAutomaticRtz.remove(owner.getUUID());
        syncMark(owner, false);
        int elapsed = (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(1L,
                        level.getGameTime() - mark.tick()));
        int keepTicks = Math.max(
                1,
                elapsed
                        - VFX_FADE_IN_TICKS
                        - VFX_FADE_OUT_TICKS);
        beginPresentation(
                history,
                level,
                owner,
                performer,
                mark,
                plan,
                keepTicks,
                activeTimeEraseUsers.isEmpty()
                        ? VFX_MODE_NORMAL
                        : VFX_MODE_TIME_ERASE);
        if (elapsed > END_SOUND_MIN_DURATION_TICKS) {
            history.pendingEndSounds.put(
                    owner.getUUID(),
                    new PendingEndSound(
                            level.dimension(),
                            level.getGameTime()
                                    + END_SOUND_MIN_DURATION_TICKS,
                            Vec3.atCenterOf(mark.center()),
                            owner.getUUID()));
        }
        return Result.SUCCESS;
    }

    public static void tick(MinecraftServer server) {
        ServerHistory history = history(server);
        processAutomaticRtz(server, history);
        for (Mark mark : List.copyOf(history.marks.values())) {
            ServerLevel level =
                    server.getLevel(mark.dimension());
            if (level == null) {
                continue;
            }
            long now = level.getGameTime();
            if (now % ENTITY_SAMPLE_INTERVAL_TICKS == 0L) {
                mark.capturePoseFrame(level, now);
            }
        }

        List<UUID> completed = new ArrayList<>();
        for (Map.Entry<UUID, Presentation> entry :
                history.presentations.entrySet()) {
            Presentation presentation = entry.getValue();
            ServerLevel level =
                    server.getLevel(presentation.dimension());
            if (level == null
                    || level.getGameTime()
                            >= presentation.endTick()) {
                completed.add(entry.getKey());
            }
        }
        for (UUID owner : completed) {
            closePresentation(history, owner, true);
        }

        List<UUID> dueSounds = new ArrayList<>();
        for (Map.Entry<UUID, PendingEndSound> entry :
                history.pendingEndSounds.entrySet()) {
            PendingEndSound pending = entry.getValue();
            ServerLevel level =
                    server.getLevel(pending.dimension());
            if (level != null
                    && level.getGameTime()
                            >= pending.playTick()) {
                playEndSound(level, pending);
                dueSounds.add(entry.getKey());
            }
        }
        dueSounds.forEach(history.pendingEndSounds::remove);
    }

    private static void processAutomaticRtz(
            MinecraftServer server,
            ServerHistory history) {
        List<UUID> owners = new ArrayList<>(
                history.pendingAutomaticRtz.keySet());
        owners.sort(Comparator.comparing(UUID::toString));
        for (UUID ownerId : owners) {
            PendingAutomaticRtz pending =
                    history.pendingAutomaticRtz.get(ownerId);
            if (pending == null) {
                continue;
            }
            ServerLevel level = server.getLevel(
                    pending.dimension());
            if (level == null) {
                history.pendingAutomaticRtz.remove(
                        ownerId, pending);
                continue;
            }
            if ((long) server.getTickCount()
                    < pending.executeServerTick()) {
                continue;
            }
            // Consume first so rewind lifecycle hooks cannot re-enter it.
            if (!history.pendingAutomaticRtz.remove(
                    ownerId, pending)) {
                continue;
            }
            try {
                tryAutomaticRtz(
                        server,
                        history,
                        level,
                        ownerId,
                        pending);
            }
            catch (RuntimeException exception) {
                LOGGER.error(
                        "Could not execute the automatic GER "
                                + "Return to Zero time-stop response",
                        exception);
            }
        }
    }

    private static void tryAutomaticRtz(
            MinecraftServer server,
            ServerHistory history,
            ServerLevel level,
            UUID ownerId,
            PendingAutomaticRtz pending) {
        Mark mark = pending.mark();
        if (history.marks.get(ownerId) != mark
                || !mark.dimension().equals(level.dimension())
                || level.getGameTime() <= mark.tick()) {
            return;
        }
        ServerPlayer owner = server.getPlayerList()
                .getPlayer(ownerId);
        if (owner == null
                || owner.serverLevel() != level
                || !owner.isAlive()
                || !level.hasData(
                        ModDataAttachmentTypes.TIME_STOP.get())) {
            return;
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        if (!state.shouldFreeze(owner)
                || !hasPostMarkIntersectingTimeStop(
                        history, level, mark, state)) {
            return;
        }
        StandPower power = StandPower.get(owner);
        if (power == null || power.getMoveset() == null) {
            return;
        }
        Ability configured = power.getMoveset().getAbility(
                GoldExperienceRequiemAddon
                        .GER_RETURN_TO_ZERO_NAME);
        if (configured instanceof GerReturnToZeroAbility rtz) {
            rtz.tryAutomaticTimeStopResponse(
                    level, owner, power);
        }
    }

    public static void clear(MinecraftServer server) {
        synchronized (HISTORIES) {
            HISTORIES.remove(server);
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        ServerHistory history = history(player.server);
        Mark mark = history.marks.get(player.getUUID());
        boolean available = mark != null
                && mark.dimension().equals(
                        player.serverLevel().dimension());
        syncMark(player, available);
        GoldExperienceRequiemAddon.playerState(player)
                .setReturnToZeroPresentationActive(
                        history.presentations.containsKey(
                                player.getUUID()));

        for (Presentation presentation :
                history.presentations.values()) {
            if (!presentation.dimension().equals(
                            player.serverLevel().dimension())) {
                continue;
            }
            StartPayload payload = presentation.payload();
            int elapsed = (int) Math.min(
                    Integer.MAX_VALUE,
                    Math.max(
                            0L,
                            player.serverLevel().getGameTime()
                                    - presentation.startTick()));
            sendToPlayer(
                    player,
                    new StartPayload(
                            payload.owner(),
                            payload.sourceEntityId(),
                            payload.expandTicks(),
                            payload.keepTicks(),
                            payload.closeTicks(),
                            payload.fadeInTicks(),
                            payload.fadeOutTicks(),
                            elapsed,
                            payload.mode(),
                            payload.center(),
                            payload.trails()));
        }
    }

    public static void playerLoggedOut(ServerPlayer player) {
        history(player.server).pendingAutomaticRtz.remove(
                player.getUUID());
        GoldExperienceRequiemAddon.playerState(player)
                .setReturnToZeroMarkAvailable(false);
        GoldExperienceRequiemAddon.playerState(player)
                .setReturnToZeroPresentationActive(false);
    }

    public static BlockPreimage captureBlockPreimage(
            Level level, BlockPos position) {
        if (!(level instanceof ServerLevel serverLevel)
                || isApplying()) {
            return null;
        }
        BlockState state = serverLevel.getBlockState(position);
        CompoundTag blockEntityTag = null;
        boolean restorable = true;
        BlockEntity blockEntity =
                serverLevel.getBlockEntity(position);
        if (blockEntity != null) {
            try {
                blockEntityTag =
                        blockEntity.saveWithFullMetadata(
                                serverLevel.registryAccess());
            }
            catch (RuntimeException exception) {
                restorable = false;
            }
        }
        return new BlockPreimage(
                serverLevel,
                serverLevel.getGameTime(),
                position.immutable(),
                NbtUtils.writeBlockState(state),
                blockEntityTag,
                restorable);
    }

    public static void recordBlockMutation(
            BlockPreimage preimage) {
        if (preimage == null || isApplying()) {
            return;
        }
        ServerHistory history =
                history(preimage.level().getServer());
        List<UUID> invalidated = new ArrayList<>();
        for (Map.Entry<UUID, Mark> entry :
                history.marks.entrySet()) {
            Mark mark = entry.getValue();
            if (!mark.dimension().equals(
                            preimage.level().dimension())
                    || preimage.tick() < mark.tick()
                    || !mark.contains(preimage.position())) {
                continue;
            }
            if (!mark.recordBlock(preimage.copy())) {
                invalidated.add(entry.getKey());
            }
        }
        for (UUID owner : invalidated) {
            history.marks.remove(owner);
            history.pendingAutomaticRtz.remove(owner);
            ServerPlayer player = preimage.level()
                    .getServer()
                    .getPlayerList()
                    .getPlayer(owner);
            if (player != null) {
                syncMark(player, false);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component
                                .translatable(
                                        Result.UNSAFE_HISTORY
                                                .translationKey()),
                        true);
            }
        }
    }

    public static void recordBlockInteraction(
            ServerLevel level, BlockPos position) {
        recordBlockMutation(captureBlockPreimage(
                level, position));
    }

    public static void recordBlockSnapshot(
            BlockSnapshot snapshot) {
        if (!(snapshot.getLevel()
                        instanceof ServerLevel level)
                || isApplying()) {
            return;
        }
        recordBlockMutation(new BlockPreimage(
                level,
                level.getGameTime(),
                snapshot.getPos().immutable(),
                NbtUtils.writeBlockState(
                        snapshot.getState()),
                snapshot.getTag() != null
                        ? snapshot.getTag().copy()
                        : null,
                true));
    }

    public static boolean isApplying() {
        return APPLY_DEPTH.get() > 0;
    }

    private static StageResult stage(
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity performer,
            Mark mark) {
        List<BlockPreimage> blocks =
                new ArrayList<>(mark.blocks().values());
        blocks.sort(Comparator.comparingLong(
                        BlockPreimage::tick)
                .reversed());
        for (BlockPreimage target : blocks) {
            if (!target.restorable()) {
                return StageResult.failure(
                        Result.UNSAFE_HISTORY);
            }
            if (!level.isLoaded(target.position())
                    || !level.getWorldBorder()
                            .isWithinBounds(target.position())) {
                return StageResult.failure(
                        Result.UNLOADED_TARGET);
            }
            BlockState state = decodeState(level, target.state());
            if (!validateBlockEntity(
                    level,
                    target.position(),
                    state,
                    target.blockEntity())) {
                return StageResult.failure(
                        Result.UNSAFE_HISTORY);
            }
        }

        UUID protectedPerformer =
                performer != null
                        ? performer.getUUID()
                        : null;
        List<EntityMutation> entities = new ArrayList<>();
        Set<UUID> stagedEntities = new HashSet<>();
        for (Entity live : snapshotEntities(
                level.getAllEntities())) {
            if (live instanceof Player
                    || live instanceof StandEntity) {
                continue;
            }
            EntitySnapshot target =
                    mark.entities().get(live.getUUID());
            if (target != null) {
                ResourceLocation liveType =
                        BuiltInRegistries.ENTITY_TYPE
                                .getKey(live.getType());
                if (liveType == null
                        || !liveType.toString().equals(
                                target.typeId())) {
                    return StageResult.failure(
                            Result.UNSAFE_HISTORY);
                }
                if (!isLoadedTarget(
                        level, target.position())) {
                    return StageResult.failure(
                            Result.UNLOADED_TARGET);
                }
                entities.add(
                        EntityMutation.restore(live, target));
                stagedEntities.add(live.getUUID());
                continue;
            }
            if (mark.contains(live.blockPosition())
                    && !live.getUUID().equals(
                            protectedPerformer)) {
                entities.add(EntityMutation.remove(live));
                stagedEntities.add(live.getUUID());
            }
        }
        for (EntitySnapshot target :
                mark.entities().values()) {
            if (stagedEntities.contains(target.uuid())) {
                continue;
            }
            Entity existing = findEntity(
                    level.getServer(), target.uuid());
            if (existing != null) {
                return StageResult.failure(
                        Result.WRONG_DIMENSION);
            }
            if (target.living()) {
                continue;
            }
            if (!isLoadedTarget(level, target.position())) {
                return StageResult.failure(
                        Result.UNLOADED_TARGET);
            }
            if (!canRecreate(target)) {
                return StageResult.failure(
                        Result.UNSAFE_HISTORY);
            }
            entities.add(EntityMutation.recreate(target));
            stagedEntities.add(target.uuid());
        }
        entities.sort(Comparator.comparing(
                mutation -> mutation.uuid().toString()));

        List<PlayerMutation> players = new ArrayList<>();
        for (PlayerSnapshot snapshot :
                mark.players().values()) {
            ServerPlayer live = level.getServer()
                    .getPlayerList()
                    .getPlayer(snapshot.uuid());
            if (live == null) {
                continue;
            }
            if (live.serverLevel() != level) {
                return StageResult.failure(
                        Result.WRONG_DIMENSION);
            }
            boolean restorePosition =
                    !live.getUUID().equals(owner.getUUID());
            if (restorePosition
                    && !isLoadedTarget(
                            level, snapshot.position())) {
                return StageResult.failure(
                        Result.UNLOADED_TARGET);
            }
            players.add(new PlayerMutation(
                    live, snapshot, restorePosition));
        }
        players.sort(Comparator.comparing(
                mutation -> mutation.live()
                        .getUUID()
                        .toString()));

        List<StandMutation> stands = new ArrayList<>();
        for (StandSnapshot snapshot :
                mark.stands().values()) {
            Entity live = findEntity(
                    level.getServer(), snapshot.uuid());
            if (live != null && live.level() != level) {
                return StageResult.failure(
                        Result.WRONG_DIMENSION);
            }
            if (!(live instanceof StandEntity stand)
                    || stand.getUser() == owner) {
                continue;
            }
            if (!isLoadedTarget(
                    level, snapshot.position())) {
                return StageResult.failure(
                        Result.UNLOADED_TARGET);
            }
            stands.add(new StandMutation(stand, snapshot));
        }
        stands.sort(Comparator.comparing(
                mutation -> mutation.live()
                        .getUUID()
                        .toString()));

        Set<UUID> disabledLiving = new HashSet<>();
        for (Entity entity :
                snapshotEntities(level.getAllEntities())) {
            if (entity instanceof LivingEntity
                    && entity != owner
                    && mark.contains(entity.blockPosition())) {
                disabledLiving.add(entity.getUUID());
            }
        }
        return StageResult.success(new RewindPlan(
                mark.dayTime(),
                List.copyOf(blocks),
                List.copyOf(entities),
                List.copyOf(players),
                List.copyOf(stands),
                Set.copyOf(disabledLiving)));
    }

    private static boolean commit(
            ServerLevel level, RewindPlan plan) {
        long inverseDayTime = level.getDayTime();
        List<BlockInverse> blockInverses =
                new ArrayList<>();
        List<EntityInverse> entityInverses =
                new ArrayList<>();
        List<PlayerInverse> playerInverses =
                new ArrayList<>();
        List<StandInverse> standInverses =
                new ArrayList<>();
        List<UUID> created = new ArrayList<>();

        enterApply();
        try {
            for (BlockPreimage target : plan.blocks()) {
                blockInverses.add(new BlockInverse(
                        target.position(),
                        captureBlockState(
                                level, target.position())));
            }
            for (EntityMutation mutation :
                    plan.entities()) {
                if (mutation.live() != null) {
                    entityInverses.add(new EntityInverse(
                            mutation.uuid(),
                            EntitySnapshot.capture(
                                    mutation.live(),
                                    level.getGameTime())));
                }
            }
            for (PlayerMutation mutation :
                    plan.players()) {
                playerInverses.add(new PlayerInverse(
                        mutation.live(),
                        PlayerSnapshot.capture(
                                mutation.live())));
            }
            for (StandMutation mutation :
                    plan.stands()) {
                standInverses.add(new StandInverse(
                        mutation.live(),
                        StandSnapshot.capture(
                                mutation.live())));
            }

            for (BlockPreimage target : plan.blocks()) {
                applyBlockState(
                        level,
                        target.position(),
                        target.state(),
                        target.blockEntity());
            }
            for (EntityMutation mutation : plan.entities()) {
                switch (mutation.operation()) {
                    case RESTORE -> applyEntitySnapshot(
                            level,
                            mutation.live(),
                            mutation.target());
                    case REMOVE -> mutation.live().remove(
                            RemovalReason.DISCARDED);
                    case RECREATE -> {
                        Entity recreated = recreate(
                                level, mutation.target());
                        created.add(recreated.getUUID());
                    }
                }
            }
            for (PlayerMutation mutation : plan.players()) {
                applyPlayerSnapshot(
                        level,
                        mutation.live(),
                        mutation.target(),
                        mutation.restorePosition());
            }
            for (StandMutation mutation : plan.stands()) {
                applyStandSnapshot(
                        level,
                        mutation.live(),
                        mutation.target());
            }
            restoreRelationships(level, plan.entities());
            level.setDayTime(plan.dayTime());
            syncMutations(level, plan);
            return true;
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "GER Return to Zero commit failed; applying "
                            + "inverse journal",
                    exception);
            if (!rollback(
                    level,
                    inverseDayTime,
                    blockInverses,
                    entityInverses,
                    playerInverses,
                    standInverses,
                    created)) {
                LOGGER.error(
                        "GER Return to Zero inverse journal was "
                                + "not fully restored");
            }
            return false;
        }
        finally {
            exitApply();
        }
    }

    private static boolean rollback(
            ServerLevel level,
            long dayTime,
            List<BlockInverse> blocks,
            List<EntityInverse> entities,
            List<PlayerInverse> players,
            List<StandInverse> stands,
            List<UUID> created) {
        boolean complete = true;
        List<EntityMutation> relationships =
                new ArrayList<>();
        for (UUID uuid : created) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                try {
                    entity.remove(RemovalReason.DISCARDED);
                }
                catch (RuntimeException exception) {
                    complete = false;
                    LOGGER.error(
                            "Could not remove RTZ-created entity {}",
                            uuid,
                            exception);
                }
            }
        }
        for (EntityInverse inverse : entities) {
            try {
                Entity entity = level.getEntity(inverse.uuid());
                if (entity == null) {
                    entity = recreate(level, inverse.snapshot());
                }
                applyFullSnapshot(
                        level, entity, inverse.snapshot());
                relationships.add(EntityMutation.restore(
                        entity, inverse.snapshot()));
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not inverse-restore RTZ entity {}",
                        inverse.uuid(),
                        exception);
            }
        }
        for (PlayerInverse inverse : players) {
            try {
                applyPlayerSnapshot(
                        level,
                        inverse.live(),
                        inverse.snapshot(),
                        true);
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not inverse-restore RTZ player {}",
                        inverse.live().getUUID(),
                        exception);
            }
        }
        for (StandInverse inverse : stands) {
            try {
                applyStandSnapshot(
                        level,
                        inverse.live(),
                        inverse.snapshot());
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not inverse-restore RTZ Stand {}",
                        inverse.live().getUUID(),
                        exception);
            }
        }
        try {
            restoreRelationships(level, relationships);
        }
        catch (RuntimeException exception) {
            complete = false;
            LOGGER.error(
                    "Could not inverse-restore RTZ relationships",
                    exception);
        }
        for (int index = blocks.size() - 1;
                index >= 0;
                index--) {
            BlockInverse inverse = blocks.get(index);
            try {
                applyBlockState(
                        level,
                        inverse.position(),
                        inverse.snapshot().state(),
                        inverse.snapshot().blockEntity());
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not inverse-restore RTZ block {}",
                        inverse.position(),
                        exception);
            }
        }
        try {
            level.setDayTime(dayTime);
        }
        catch (RuntimeException exception) {
            complete = false;
            LOGGER.error(
                    "Could not inverse-restore RTZ day time",
                    exception);
        }
        return complete;
    }

    private static void beginPresentation(
            ServerHistory history,
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity performer,
            Mark mark,
            RewindPlan plan,
            int keepTicks,
            int mode) {
        for (UUID uuid : plan.disabledLiving()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                GoldExperienceRequiemAddon
                        .livingState(living)
                        .startReturnToZeroDisable(
                                owner.getUUID(),
                                living,
                                keepTicks);
            }
        }
        int sourceEntityId = performer != null
                ? performer.getId()
                : owner.getId();
        StartPayload payload = new StartPayload(
                owner.getUUID(),
                sourceEntityId,
                VFX_EXPAND_TICKS,
                keepTicks,
                VFX_CLOSE_TICKS,
                VFX_FADE_IN_TICKS,
                VFX_FADE_OUT_TICKS,
                0,
                mode,
                Vec3.atCenterOf(mark.center()),
                buildTrails(level, mark));
        history.presentations.put(
                owner.getUUID(),
                new Presentation(
                        owner.getUUID(),
                        level.dimension(),
                        level.getGameTime(),
                        level.getGameTime()
                                + presentationTicks(payload),
                        plan.disabledLiving(),
                        payload));
        GoldExperienceRequiemAddon.playerState(owner)
                .setReturnToZeroPresentationActive(true);
        broadcast(level, payload);
    }

    private static List<GhostTrail> buildTrails(
            ServerLevel level, Mark mark) {
        List<GhostTrail> trails = new ArrayList<>();
        List<Map.Entry<UUID, Deque<PoseFrame>>> ordered =
                new ArrayList<>(mark.poseFrames().entrySet());
        ordered.sort(Comparator.comparing(
                entry -> entry.getKey().toString()));
        for (Map.Entry<UUID, Deque<PoseFrame>> entry :
                ordered) {
            if (trails.size()
                    >= GerRtzPayloads.MAX_TRAIL_ENTITIES) {
                break;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            List<Vec3> points = new ArrayList<>();
            List<PoseFrame> frames =
                    new ArrayList<>(entry.getValue());
            for (int index = frames.size() - 1;
                    index >= 0
                            && points.size()
                                    < MAX_GHOST_POINTS_PER_ENTITY;
                    index--) {
                Vec3 point = frames.get(index).position();
                if (points.isEmpty()
                        || points.get(points.size() - 1)
                                .distanceTo(point)
                                >= MIN_GHOST_DISTANCE) {
                    points.add(point);
                }
            }
            if (!points.isEmpty()) {
                trails.add(new GhostTrail(
                        entity.getId(), points));
            }
        }
        return List.copyOf(trails);
    }

    private static void closePresentation(
            ServerHistory history,
            UUID owner,
            boolean broadcastStop) {
        Presentation presentation =
                history.presentations.remove(owner);
        if (presentation == null) {
            return;
        }
        ServerPlayer presentationOwner =
                history.server.getPlayerList()
                        .getPlayer(owner);
        if (presentationOwner != null) {
            GoldExperienceRequiemAddon
                    .playerState(presentationOwner)
                    .setReturnToZeroPresentationActive(false);
        }
        MinecraftServer server = history.server;
        ServerLevel level = server != null
                ? server.getLevel(presentation.dimension())
                : null;
        if (level == null) {
            return;
        }
        for (UUID uuid : presentation.disabledLiving()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                GoldExperienceRequiemAddon
                        .livingState(living)
                        .clearReturnToZeroDisable(owner);
            }
        }
        if (broadcastStop) {
            broadcast(level, new StopPayload(owner));
        }
    }

    private static int presentationTicks(StartPayload payload) {
        long ticks = (long) payload.expandTicks()
                + payload.keepTicks()
                + Math.max(
                        payload.closeTicks(),
                        payload.fadeOutTicks());
        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(1L, ticks));
    }

    private static void playEndSound(
            ServerLevel level, PendingEndSound pending) {
        ServerPlayer owner = level.getServer()
                .getPlayerList()
                .getPlayer(pending.owner());
        Vec3 position = owner != null
                        && owner.serverLevel() == level
                ? owner.position()
                : pending.center();
        level.playSound(
                null,
                position.x,
                position.y,
                position.z,
                GoldExperienceRequiemAddon.RTZ_END_SOUND.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    private static void broadcast(
            ServerLevel level,
            net.minecraft.network.protocol.common.custom
                    .CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            sendToPlayer(player, payload);
        }
    }

    private static void syncMark(
            ServerPlayer player, boolean available) {
        GoldExperienceRequiemAddon.playerState(player)
                .setReturnToZeroMarkAvailable(available);
        sendToPlayer(
                player,
                new MarkStatePayload(
                        player.getUUID(), available));
    }

    private static void sendToPlayer(
            ServerPlayer player,
            net.minecraft.network.protocol.common.custom
                    .CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        }
        catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not send GER RTZ payload {} to {}",
                    payload.type().id(),
                    player.getGameProfile().getName(),
                    exception);
        }
    }

    private static Set<InstanceKey> captureTimeStopInstances(
            ServerLevel level,
            ServerHistory history) {
        if (!level.hasData(
                ModDataAttachmentTypes.TIME_STOP.get())) {
            return Set.of();
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        Set<InstanceKey> instances = new HashSet<>();
        for (TimeStopState.Instance instance :
                state.getInstances()) {
            if (instance.isActive()) {
                instances.add(ensureTimeStopGeneration(
                        history, level, instance));
            }
        }
        return Set.copyOf(instances);
    }

    private static void terminatePostMarkTimeStops(
            ServerHistory history,
            ServerLevel level,
            Mark mark) {
        if (!level.hasData(
                ModDataAttachmentTypes.TIME_STOP.get())) {
            return;
        }
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        List<InstanceRegion> currentInstances = new ArrayList<>();
        for (TimeStopState.Instance instance :
                state.getInstances()) {
            if (instance.isActive()) {
                currentInstances.add(instanceRegion(
                        history, level, instance));
            }
        }
        GerRtzTimeStopPolicy.terminatePostMarkInstances(
                mark.timeStopInstances(),
                mark.centerChunkX(),
                mark.centerChunkZ(),
                mark.chunkRadius(),
                currentInstances,
                state::removeInstance);
    }

    private static boolean hasPostMarkIntersectingTimeStop(
            ServerHistory history,
            ServerLevel level,
            Mark mark,
            TimeStopState state) {
        for (TimeStopState.Instance instance :
                state.getInstances()) {
            if (instance.isActive()
                    && GerRtzTimeStopPolicy.shouldTerminate(
                            mark.timeStopInstances(),
                            mark.centerChunkX(),
                            mark.centerChunkZ(),
                            mark.chunkRadius(),
                            instanceRegion(
                                    history, level, instance))) {
                return true;
            }
        }
        return false;
    }

    private static InstanceRegion instanceRegion(
            ServerHistory history,
            ServerLevel level,
            TimeStopState.Instance instance) {
        return new InstanceRegion(
                ensureTimeStopGeneration(
                        history, level, instance),
                instance.centerPos().x,
                instance.centerPos().z,
                instance.chunkRange());
    }

    // Added is the generation authority. The awaiting flag prevents a mark
    // captured in the same event stack from incrementing that generation.
    private static InstanceKey registerTimeStopAdded(
            ServerHistory history,
            ServerLevel level,
            TimeStopState.Instance instance) {
        GenerationTracker<TimeStopState.Instance> active =
                history.activeTimeStops.computeIfAbsent(
                        level.dimension(),
                        ignored -> new GenerationTracker<>());
        return active.added(instance.id(), instance);
    }

    private static InstanceKey ensureTimeStopGeneration(
            ServerHistory history,
            ServerLevel level,
            TimeStopState.Instance instance) {
        GenerationTracker<TimeStopState.Instance> active =
                history.activeTimeStops.computeIfAbsent(
                        level.dimension(),
                        ignored -> new GenerationTracker<>());
        return active.observe(instance.id(), instance);
    }

    private static Set<UUID> captureActiveTimeEraseUsers(
            ServerLevel level) {
        if (!ModList.get().isLoaded(KING_CRIMSON_MOD_ID)) {
            return Set.of();
        }
        try {
            return KingCrimsonBridge.capture(level);
        }
        catch (RuntimeException | LinkageError exception) {
            LOGGER.warn(
                    "Could not inspect the public King Crimson "
                            + "Time Erase state before GER rewind",
                    exception);
            return Set.of();
        }
    }

    private static void forceEndTimeErase(
            ServerLevel level, Set<UUID> activeUsers) {
        if (activeUsers.isEmpty()
                || !ModList.get().isLoaded(KING_CRIMSON_MOD_ID)) {
            return;
        }
        try {
            KingCrimsonBridge.terminate(level, activeUsers);
        }
        catch (RuntimeException | LinkageError exception) {
            LOGGER.warn(
                    "Could not terminate the public King Crimson "
                            + "Time Erase state after GER rewind",
                    exception);
        }
    }

    private static final class KingCrimsonBridge {
        private KingCrimsonBridge() {}

        private static Set<UUID> capture(ServerLevel level) {
            Set<UUID> active = new HashSet<>();
            long gameTime = level.getGameTime();
            for (ServerPlayer player : level.players()) {
                if (player.hasData(
                                KingCrimsonAddon.TEMPORAL_STATE)
                        && KingCrimsonAddon.temporalState(player)
                                .isErasing(gameTime)) {
                    active.add(player.getUUID());
                }
            }
            return Set.copyOf(active);
        }

        private static void terminate(
                ServerLevel level, Set<UUID> activeUsers) {
            for (UUID uuid : activeUsers) {
                ServerPlayer player = level.getServer()
                        .getPlayerList()
                        .getPlayer(uuid);
                if (player != null
                        && player.hasData(
                                KingCrimsonAddon.TEMPORAL_STATE)) {
                    KingCrimsonTemporalStateService
                            .terminate(player);
                }
            }
        }
    }

    private static void applyBlockState(
            ServerLevel level,
            BlockPos position,
            CompoundTag stateTag,
            CompoundTag blockEntityTag) {
        BlockState target = decodeState(level, stateTag);
        BlockState current = level.getBlockState(position);
        if (!current.equals(target)
                && !level.setBlock(
                        position,
                        target,
                        BLOCK_UPDATE_FLAGS)) {
            throw new IllegalStateException(
                    "Could not restore RTZ block " + position);
        }
        if (blockEntityTag != null) {
            BlockEntity currentBlockEntity =
                    level.getBlockEntity(position);
            BlockEntity decoded = BlockEntity.loadStatic(
                    position,
                    target,
                    blockEntityTag.copy(),
                    level.registryAccess());
            if (currentBlockEntity == null
                    || decoded == null
                    || decoded.getType()
                            != currentBlockEntity.getType()) {
                throw new IllegalStateException(
                        "Could not restore RTZ block entity "
                                + position);
            }
            currentBlockEntity.loadWithComponents(
                    blockEntityTag.copy(),
                    level.registryAccess());
            currentBlockEntity.setChanged();
        }
        level.sendBlockUpdated(
                position,
                current,
                target,
                Block.UPDATE_CLIENTS);
    }

    private static void applyEntitySnapshot(
            ServerLevel level,
            Entity entity,
            EntitySnapshot snapshot) {
        float health =
                entity instanceof LivingEntity living
                        ? living.getHealth()
                        : 0.0F;
        float absorption =
                entity instanceof LivingEntity living
                        ? living.getAbsorptionAmount()
                        : 0.0F;
        applyFullSnapshot(level, entity, snapshot);
        if (entity instanceof LivingEntity living) {
            living.setHealth(health);
            living.setAbsorptionAmount(absorption);
        }
    }

    private static void applyFullSnapshot(
            ServerLevel level,
            Entity entity,
            EntitySnapshot snapshot) {
        Set<Holder<MobEffect>> oldEffects =
                effectTypes(entity);
        CompoundTag tag = snapshot.tag().copy();
        tag.remove("id");
        tag.remove("Passengers");
        tag.putUUID("UUID", entity.getUUID());
        entity.load(tag);
        entity.noPhysics = snapshot.noPhysics();
        syncRemovedEffects(level, entity, oldEffects);
        syncEntity(level, entity);
    }

    private static void applyPlayerSnapshot(
            ServerLevel level,
            ServerPlayer player,
            PlayerSnapshot snapshot,
            boolean restorePosition) {
        player.getInventory().load(
                snapshot.inventory().copy());
        player.getInventory().selected =
                snapshot.selectedSlot();
        if (restorePosition) {
            player.stopRiding();
            player.connection.teleport(
                    snapshot.position().x,
                    snapshot.position().y,
                    snapshot.position().z,
                    snapshot.yRot(),
                    snapshot.xRot());
            restoreVehicle(
                    level, player, snapshot.vehicle());
        }
        player.inventoryMenu.broadcastFullState();
        player.containerMenu.broadcastFullState();
        player.connection.send(
                new ClientboundSetCarriedItemPacket(
                        player.getInventory().selected));
    }

    private static void applyStandSnapshot(
            ServerLevel level,
            StandEntity stand,
            StandSnapshot snapshot) {
        stand.stopRiding();
        stand.teleportTo(
                snapshot.position().x,
                snapshot.position().y,
                snapshot.position().z);
        stand.setYRot(snapshot.yRot());
        stand.setXRot(snapshot.xRot());
        stand.setDeltaMovement(Vec3.ZERO);
        stand.hasImpulse = true;
        restoreVehicle(level, stand, snapshot.vehicle());
    }

    private static void restoreVehicle(
            ServerLevel level,
            Entity entity,
            UUID vehicleUuid) {
        if (vehicleUuid == null) {
            return;
        }
        Entity vehicle = level.getEntity(vehicleUuid);
        if (vehicle != null) {
            entity.startRiding(vehicle, true);
        }
    }

    private static Entity recreate(
            ServerLevel level, EntitySnapshot snapshot) {
        CompoundTag tag = snapshot.tag().copy();
        tag.remove("Passengers");
        Entity entity = net.minecraft.world.entity.EntityType
                .loadEntityRecursive(
                        tag, level, current -> current);
        if (entity == null || !level.addFreshEntity(entity)) {
            throw new IllegalStateException(
                    "Could not recreate RTZ entity "
                            + snapshot.uuid());
        }
        entity.noPhysics = snapshot.noPhysics();
        return entity;
    }

    private static void restoreRelationships(
            ServerLevel level,
            List<EntityMutation> mutations) {
        for (EntityMutation mutation : mutations) {
            EntitySnapshot snapshot = mutation.target();
            if (snapshot == null) {
                continue;
            }
            Entity entity = level.getEntity(snapshot.uuid());
            if (entity == null) {
                continue;
            }
            entity.stopRiding();
            if (snapshot.vehicle() != null) {
                Entity vehicle =
                        level.getEntity(snapshot.vehicle());
                if (vehicle != null) {
                    entity.startRiding(vehicle, true);
                }
            }
            if (entity instanceof Mob mob) {
                Entity target = snapshot.mobTarget() != null
                        ? level.getEntity(snapshot.mobTarget())
                        : null;
                mob.setTarget(
                        target instanceof LivingEntity living
                                ? living
                                : null);
            }
            if (entity instanceof Projectile projectile) {
                projectile.setOwner(
                        snapshot.projectileOwner() != null
                                ? level.getEntity(
                                        snapshot
                                                .projectileOwner())
                                : null);
            }
        }
    }

    private static void syncMutations(
            ServerLevel level, RewindPlan plan) {
        for (EntityMutation mutation : plan.entities()) {
            Entity entity = level.getEntity(mutation.uuid());
            if (entity != null) {
                syncEntity(level, entity);
            }
        }
        for (PlayerMutation mutation : plan.players()) {
            syncEntity(level, mutation.live());
        }
        for (StandMutation mutation : plan.stands()) {
            syncEntity(level, mutation.live());
        }
    }

    private static void syncEntity(
            ServerLevel level, Entity entity) {
        level.getChunkSource().broadcastAndSend(
                entity,
                new ClientboundTeleportEntityPacket(entity));
        if (entity instanceof LivingEntity living) {
            for (var effect :
                    living.getActiveEffectsMap().values()) {
                level.getChunkSource().broadcastAndSend(
                        entity,
                        new ClientboundUpdateMobEffectPacket(
                                entity.getId(),
                                effect,
                                true));
            }
            List<Pair<EquipmentSlot, ItemStack>> equipment =
                    new ArrayList<>();
            for (EquipmentSlot slot : EnumSet.of(
                    EquipmentSlot.MAINHAND,
                    EquipmentSlot.OFFHAND,
                    EquipmentSlot.FEET,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.HEAD)) {
                equipment.add(Pair.of(
                        slot,
                        living.getItemBySlot(slot).copy()));
            }
            level.getChunkSource().broadcastAndSend(
                    entity,
                    new ClientboundSetEquipmentPacket(
                            entity.getId(), equipment));
        }
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot());
            player.inventoryMenu.broadcastFullState();
            player.containerMenu.broadcastFullState();
            player.connection.send(
                    new ClientboundSetCarriedItemPacket(
                            player.getInventory().selected));
            player.resetSentInfo();
        }
    }

    private static Set<Holder<MobEffect>> effectTypes(
            Entity entity) {
        return entity instanceof LivingEntity living
                ? Set.copyOf(
                        living.getActiveEffectsMap().keySet())
                : Set.of();
    }

    private static void syncRemovedEffects(
            ServerLevel level,
            Entity entity,
            Set<Holder<MobEffect>> oldEffects) {
        for (Holder<MobEffect> effect : oldEffects) {
            level.getChunkSource().broadcastAndSend(
                    entity,
                    new ClientboundRemoveMobEffectPacket(
                            entity.getId(), effect));
        }
    }

    private static BlockStateSnapshot captureBlockState(
            ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        BlockEntity blockEntity =
                level.getBlockEntity(position);
        CompoundTag blockEntityTag = blockEntity != null
                ? blockEntity.saveWithFullMetadata(
                        level.registryAccess())
                : null;
        return new BlockStateSnapshot(
                NbtUtils.writeBlockState(state),
                blockEntityTag);
    }

    private static BlockState decodeState(
            ServerLevel level, CompoundTag stateTag) {
        return NbtUtils.readBlockState(
                level.registryAccess()
                        .lookupOrThrow(
                                net.minecraft.core.registries
                                        .Registries.BLOCK),
                stateTag);
    }

    private static boolean validateBlockEntity(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            CompoundTag blockEntityTag) {
        if (blockEntityTag == null) {
            return true;
        }
        try {
            return BlockEntity.loadStatic(
                    position,
                    state,
                    blockEntityTag.copy(),
                    level.registryAccess()) != null;
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean canRecreate(
            EntitySnapshot snapshot) {
        ResourceLocation type =
                ResourceLocation.tryParse(snapshot.typeId());
        return type != null
                && BuiltInRegistries.ENTITY_TYPE
                        .containsKey(type)
                && snapshot.tag().contains(
                        "id", Tag.TAG_STRING);
    }

    private static boolean isLoadedTarget(
            ServerLevel level, Vec3 position) {
        BlockPos block = BlockPos.containing(position);
        return level.isLoaded(block)
                && level.getWorldBorder()
                        .isWithinBounds(block);
    }

    private static List<Entity> snapshotEntities(
            Iterable<Entity> entities) {
        List<Entity> snapshot = new ArrayList<>();
        for (Entity entity : entities) {
            snapshot.add(entity);
        }
        return snapshot;
    }

    private static Entity findEntity(
            MinecraftServer server, UUID uuid) {
        for (ServerLevel candidate :
                server.getAllLevels()) {
            Entity entity = candidate.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void enterApply() {
        APPLY_DEPTH.set(APPLY_DEPTH.get() + 1);
    }

    private static void exitApply() {
        int depth = APPLY_DEPTH.get() - 1;
        if (depth <= 0) {
            APPLY_DEPTH.remove();
        }
        else {
            APPLY_DEPTH.set(depth);
        }
    }

    private static ServerHistory history(
            MinecraftServer server) {
        synchronized (HISTORIES) {
            return HISTORIES.computeIfAbsent(
                    server,
                    ignored -> new ServerHistory(server));
        }
    }

    private enum EntityOperation {
        RESTORE,
        REMOVE,
        RECREATE
    }

    private record BlockStateSnapshot(
            CompoundTag state,
            CompoundTag blockEntity) {}

    private record BlockInverse(
            BlockPos position,
            BlockStateSnapshot snapshot) {}

    private record EntityInverse(
            UUID uuid,
            EntitySnapshot snapshot) {}

    private record PlayerInverse(
            ServerPlayer live,
            PlayerSnapshot snapshot) {}

    private record StandInverse(
            StandEntity live,
            StandSnapshot snapshot) {}

    private record EntitySnapshot(
            UUID uuid,
            long tick,
            String typeId,
            CompoundTag tag,
            Vec3 position,
            boolean noPhysics,
            boolean living,
            UUID mobTarget,
            UUID vehicle,
            UUID projectileOwner) {
        private static EntitySnapshot capture(
                Entity entity, long tick) {
            CompoundTag tag =
                    entity.saveWithoutId(new CompoundTag());
            ResourceLocation type =
                    BuiltInRegistries.ENTITY_TYPE
                            .getKey(entity.getType());
            if (type == null) {
                throw new IllegalStateException(
                        "Unregistered RTZ entity type");
            }
            tag.putString("id", type.toString());
            tag.remove("Passengers");
            UUID mobTarget =
                    entity instanceof Mob mob
                                    && mob.getTarget() != null
                            ? mob.getTarget().getUUID()
                            : null;
            UUID vehicle = entity.getVehicle() != null
                    ? entity.getVehicle().getUUID()
                    : null;
            UUID projectileOwner =
                    entity instanceof Projectile projectile
                                    && projectile.getOwner() != null
                            ? projectile.getOwner().getUUID()
                            : null;
            return new EntitySnapshot(
                    entity.getUUID(),
                    tick,
                    type.toString(),
                    tag,
                    entity.position(),
                    entity.noPhysics,
                    entity instanceof LivingEntity,
                    mobTarget,
                    vehicle,
                    projectileOwner);
        }
    }

    private record PlayerSnapshot(
            UUID uuid,
            Vec3 position,
            float yRot,
            float xRot,
            ListTag inventory,
            int selectedSlot,
            UUID vehicle) {
        private static PlayerSnapshot capture(
                ServerPlayer player) {
            return new PlayerSnapshot(
                    player.getUUID(),
                    player.position(),
                    player.getYRot(),
                    player.getXRot(),
                    player.getInventory()
                            .save(new ListTag()),
                    player.getInventory().selected,
                    player.getVehicle() != null
                            ? player.getVehicle().getUUID()
                            : null);
        }
    }

    private record StandSnapshot(
            UUID uuid,
            Vec3 position,
            float yRot,
            float xRot,
            UUID vehicle) {
        private static StandSnapshot capture(
                StandEntity stand) {
            return new StandSnapshot(
                    stand.getUUID(),
                    stand.position(),
                    stand.getYRot(),
                    stand.getXRot(),
                    stand.getVehicle() != null
                            ? stand.getVehicle().getUUID()
                            : null);
        }
    }

    private record PoseFrame(
            long tick, Vec3 position) {}

    private static final class Mark {
        private final UUID owner;
        private final ResourceKey<Level> dimension;
        private final long tick;
        private final long dayTime;
        private final BlockPos center;
        private final int centerChunkX;
        private final int centerChunkZ;
        private final int chunkRadius;
        private final Set<InstanceKey> timeStopInstances;
        private final Map<UUID, EntitySnapshot> entities;
        private final Map<UUID, PlayerSnapshot> players;
        private final Map<UUID, StandSnapshot> stands;
        private final LinkedHashMap<BlockPos, BlockPreimage>
                blocks = new LinkedHashMap<>();
        private final Map<UUID, Deque<PoseFrame>>
                poseFrames = new HashMap<>();
        private int recordedBlockOps;

        private Mark(
                UUID owner,
                ResourceKey<Level> dimension,
                long tick,
                long dayTime,
                BlockPos center,
                int chunkRadius,
                Set<InstanceKey> timeStopInstances,
                Map<UUID, EntitySnapshot> entities,
                Map<UUID, PlayerSnapshot> players,
                Map<UUID, StandSnapshot> stands) {
            this.owner = owner;
            this.dimension = dimension;
            this.tick = tick;
            this.dayTime = dayTime;
            this.center = center.immutable();
            ChunkPos chunk = new ChunkPos(center);
            this.centerChunkX = chunk.x;
            this.centerChunkZ = chunk.z;
            this.chunkRadius = chunkRadius;
            this.timeStopInstances =
                    Set.copyOf(timeStopInstances);
            this.entities = Map.copyOf(entities);
            this.players = Map.copyOf(players);
            this.stands = Map.copyOf(stands);
        }

        private static Mark capture(
                ServerLevel level,
                ServerPlayer owner,
                int chunkRadius,
                ServerHistory history) {
            Set<InstanceKey> timeStopInstances =
                    captureTimeStopInstances(level, history);
            Mark shell = new Mark(
                    owner.getUUID(),
                    level.dimension(),
                    level.getGameTime(),
                    level.getDayTime(),
                    owner.blockPosition(),
                    chunkRadius,
                    timeStopInstances,
                    Map.of(),
                    Map.of(),
                    Map.of());
            Map<UUID, EntitySnapshot> entities =
                    new HashMap<>();
            Map<UUID, PlayerSnapshot> players =
                    new HashMap<>();
            Map<UUID, StandSnapshot> stands =
                    new HashMap<>();
            for (Entity entity :
                    snapshotEntities(level.getAllEntities())) {
                if (!shell.contains(entity.blockPosition())) {
                    continue;
                }
                if (entity instanceof ServerPlayer player) {
                    players.put(
                            player.getUUID(),
                            PlayerSnapshot.capture(player));
                }
                else if (entity instanceof StandEntity stand) {
                    stands.put(
                            stand.getUUID(),
                            StandSnapshot.capture(stand));
                }
                else {
                    entities.put(
                            entity.getUUID(),
                            EntitySnapshot.capture(
                                    entity,
                                    level.getGameTime()));
                }
            }
            Mark mark = new Mark(
                    owner.getUUID(),
                    level.dimension(),
                    level.getGameTime(),
                    level.getDayTime(),
                    owner.blockPosition(),
                    chunkRadius,
                    timeStopInstances,
                    entities,
                    players,
                    stands);
            mark.capturePoseFrame(
                    level, level.getGameTime());
            return mark;
        }

        private boolean contains(BlockPos position) {
            ChunkPos chunk = new ChunkPos(position);
            return Math.abs(chunk.x - centerChunkX)
                            <= chunkRadius
                    && Math.abs(chunk.z - centerChunkZ)
                            <= chunkRadius;
        }

        private boolean recordBlock(
                BlockPreimage preimage) {
            recordedBlockOps++;
            if (recordedBlockOps > MAX_BLOCK_OPS) {
                return false;
            }
            blocks.putIfAbsent(
                    preimage.position(), preimage);
            return true;
        }

        private void capturePoseFrame(
                ServerLevel level, long now) {
            List<Entity> candidates =
                    snapshotEntities(level.getAllEntities());
            candidates.removeIf(entity ->
                    !contains(entity.blockPosition())
                            || entity.getUUID().equals(owner)
                            || !wasPresentAtMark(
                                    entity.getUUID()));
            candidates.sort(Comparator.comparing(
                    entity -> entity.getUUID().toString()));
            int limit = Math.min(
                    candidates.size(),
                    GerRtzPayloads.MAX_TRAIL_ENTITIES);
            for (int candidateIndex = 0;
                    candidateIndex < limit;
                    candidateIndex++) {
                Entity entity = candidates.get(candidateIndex);
                Deque<PoseFrame> frames =
                        poseFrames.computeIfAbsent(
                                entity.getUUID(),
                                ignored -> new ArrayDeque<>());
                frames.addLast(new PoseFrame(
                        now, entity.position()));
                while (frames.size() > MAX_ENTITY_FRAMES) {
                    frames.removeFirst();
                }
            }
        }

        private boolean wasPresentAtMark(UUID uuid) {
            return entities.containsKey(uuid)
                    || players.containsKey(uuid)
                    || stands.containsKey(uuid);
        }

        private UUID owner() {
            return owner;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private long tick() {
            return tick;
        }

        private long dayTime() {
            return dayTime;
        }

        private BlockPos center() {
            return center;
        }

        private int centerChunkX() {
            return centerChunkX;
        }

        private int centerChunkZ() {
            return centerChunkZ;
        }

        private int chunkRadius() {
            return chunkRadius;
        }

        private Set<InstanceKey> timeStopInstances() {
            return timeStopInstances;
        }

        private Map<UUID, EntitySnapshot> entities() {
            return entities;
        }

        private Map<UUID, PlayerSnapshot> players() {
            return players;
        }

        private Map<UUID, StandSnapshot> stands() {
            return stands;
        }

        private LinkedHashMap<BlockPos, BlockPreimage>
                blocks() {
            return blocks;
        }

        private Map<UUID, Deque<PoseFrame>> poseFrames() {
            return poseFrames;
        }
    }

    private static final class ServerHistory {
        private final MinecraftServer server;
        private final Map<UUID, Mark> marks =
                new HashMap<>();
        private final Map<UUID, PendingAutomaticRtz>
                pendingAutomaticRtz = new HashMap<>();
        private final Map<ResourceKey<Level>,
                GenerationTracker<TimeStopState.Instance>>
                activeTimeStops = new HashMap<>();
        private final Map<UUID, Presentation> presentations =
                new HashMap<>();
        private final Map<UUID, PendingEndSound>
                pendingEndSounds = new HashMap<>();

        private ServerHistory(MinecraftServer server) {
            this.server = server;
        }
    }

    private record PendingAutomaticRtz(
            ResourceKey<Level> dimension,
            long executeServerTick,
            Mark mark) {}

    private record EntityMutation(
            EntityOperation operation,
            UUID uuid,
            Entity live,
            EntitySnapshot target) {
        private static EntityMutation restore(
                Entity live, EntitySnapshot target) {
            return new EntityMutation(
                    EntityOperation.RESTORE,
                    live.getUUID(),
                    live,
                    target);
        }

        private static EntityMutation remove(Entity live) {
            return new EntityMutation(
                    EntityOperation.REMOVE,
                    live.getUUID(),
                    live,
                    null);
        }

        private static EntityMutation recreate(
                EntitySnapshot target) {
            return new EntityMutation(
                    EntityOperation.RECREATE,
                    target.uuid(),
                    null,
                    target);
        }
    }

    private record PlayerMutation(
            ServerPlayer live,
            PlayerSnapshot target,
            boolean restorePosition) {}

    private record StandMutation(
            StandEntity live,
            StandSnapshot target) {}

    private record RewindPlan(
            long dayTime,
            List<BlockPreimage> blocks,
            List<EntityMutation> entities,
            List<PlayerMutation> players,
            List<StandMutation> stands,
            Set<UUID> disabledLiving) {}

    private record StageResult(
            RewindPlan plan,
            Result failure) {
        private static StageResult success(
                RewindPlan plan) {
            return new StageResult(plan, null);
        }

        private static StageResult failure(
                Result result) {
            return new StageResult(null, result);
        }
    }

    private record Presentation(
            UUID owner,
            ResourceKey<Level> dimension,
            long startTick,
            long endTick,
            Set<UUID> disabledLiving,
            StartPayload payload) {}

    private record PendingEndSound(
            ResourceKey<Level> dimension,
            long playTick,
            Vec3 center,
            UUID owner) {}
}
