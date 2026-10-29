package rotp.addons.mandom.state;

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
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.zombie.ZombieData;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.timestop.TimeStopState;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;

import rotp.addons.mandom.MandomAddon;
import rotp.addons.mandom.MandomRollbackGate;
import rotp.addons.mandom.network.RewindEffectPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

/**
 * Module-local, transient rewind history and all-or-restore commit path.
 */
public final class MandomRewindService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final int MAX_SNAPSHOTS =
            MandomRollbackGate.REWIND_TICKS
                    / SAMPLE_INTERVAL_TICKS + 1;
    private static final int BLOCK_UPDATE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS;
    private static final Map<MinecraftServer, ServerHistory> HISTORIES =
            new WeakHashMap<>();
    private static final ThreadLocal<Integer> APPLY_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private MandomRewindService() {}

    public enum Result {
        SUCCESS(""),
        HISTORY_NOT_READY(
                "message.rotp_mandom.rewind.history_not_ready"),
        WRONG_DIMENSION(
                "message.rotp_mandom.rewind.wrong_dimension"),
        UNLOADED_TARGET(
                "message.rotp_mandom.rewind.unloaded_target"),
        UNSAFE_HISTORY(
                "message.rotp_mandom.rewind.unsafe_history"),
        COMMIT_FAILED(
                "message.rotp_mandom.rewind.commit_failed");

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
                    blockEntity != null ? blockEntity.copy() : null,
                    restorable);
        }
    }

    public static void tick(MinecraftServer server) {
        ServerHistory serverHistory = history(server);
        for (ServerLevel level : server.getAllLevels()) {
            LevelHistory levelHistory = serverHistory.level(level);
            long now = level.getGameTime();
            levelHistory.prune(now);
            if (now % SAMPLE_INTERVAL_TICKS == 0L) {
                levelHistory.capture(level, now);
            }
        }
    }

    public static void clear(MinecraftServer server) {
        synchronized (HISTORIES) {
            HISTORIES.remove(server);
        }
    }

    public static void entityLeft(Entity entity, Level level) {
        if (!(level instanceof ServerLevel serverLevel)
                || !isTracked(entity)) {
            return;
        }
        RemovalReason reason = entity.getRemovalReason();
        if (reason == null) {
            return;
        }
        LevelHistory history =
                history(serverLevel.getServer()).level(serverLevel);
        if (reason.shouldDestroy()) {
            history.markDestroyed(
                    entity.getUUID(), serverLevel.getGameTime());
        }
        else {
            history.forget(entity.getUUID());
        }
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
        history(preimage.level().getServer())
                .level(preimage.level())
                .recordBlock(preimage.copy());
    }

    public static void recordBlockInteraction(
            ServerLevel level, BlockPos position) {
        recordBlockMutation(captureBlockPreimage(level, position));
    }

    public static boolean isApplying() {
        return APPLY_DEPTH.get() > 0;
    }

    public static Result rewind(
            ServerLevel level,
            LivingEntity user,
            LivingEntity performer) {
        if (user.level() != level) {
            return Result.WRONG_DIMENSION;
        }

        LevelHistory history =
                history(level.getServer()).level(level);
        long now = level.getGameTime();
        long targetTick = now - MandomRollbackGate.REWIND_TICKS;
        WorldSnapshot worldTarget =
                history.worldAtOrBefore(targetTick);
        EntityTimeline userTimeline =
                history.entities.get(user.getUUID());
        if (worldTarget == null
                || userTimeline == null
                || userTimeline.atOrBefore(targetTick) == null) {
            return Result.HISTORY_NOT_READY;
        }

        Vec3 origin = user.position();
        double rangeSquared =
                (double) MandomRollbackGate.RANGE_BLOCKS
                        * MandomRollbackGate.RANGE_BLOCKS;
        UUID protectedPerformer =
                performer != null ? performer.getUUID() : null;
        StageResult staged = stage(
                level,
                history,
                targetTick,
                now,
                origin,
                rangeSquared,
                protectedPerformer);
        if (staged.failure != null) {
            return staged.failure;
        }

        RewindPlan plan = staged.plan;
        boolean committed = commit(level, plan);
        if (!committed) {
            return Result.COMMIT_FAILED;
        }

        history.consume(plan);
        playSuccessSafely(level, user, origin);
        return Result.SUCCESS;
    }

    private static StageResult stage(
            ServerLevel level,
            LevelHistory history,
            long targetTick,
            long now,
            Vec3 origin,
            double rangeSquared,
            UUID protectedPerformer) {
        LinkedHashMap<BlockPos, BlockPreimage> blocks =
                new LinkedHashMap<>();
        for (BlockPreimage preimage :
                history.blockRecords(targetTick, now)) {
            if (preimage.position().distToCenterSqr(
                            origin.x, origin.y, origin.z)
                    <= rangeSquared) {
                blocks.putIfAbsent(
                        preimage.position(), preimage);
            }
        }

        for (BlockPreimage preimage : blocks.values()) {
            if (!preimage.restorable()) {
                return StageResult.failure(Result.UNSAFE_HISTORY);
            }
            if (!level.isLoaded(preimage.position())) {
                return StageResult.failure(Result.UNLOADED_TARGET);
            }
            BlockState state = decodeState(level, preimage.state());
            if (!validateBlockEntity(
                    level,
                    preimage.position(),
                    state,
                    preimage.blockEntity())) {
                return StageResult.failure(Result.UNSAFE_HISTORY);
            }
        }

        List<EntityMutation> entityMutations = new ArrayList<>();
        Set<UUID> stagedEntities = new HashSet<>();
        for (Entity entity :
                snapshotEntities(level.getAllEntities())) {
            if (!isTracked(entity)
                    || entity.position().distanceToSqr(origin)
                            > rangeSquared) {
                continue;
            }
            EntityTimeline timeline =
                    history.entities.get(entity.getUUID());
            EntitySnapshot target = timeline != null
                    ? timeline.atOrBefore(targetTick)
                    : null;
            if (target == null) {
                if (!(entity instanceof Player)
                        && !entity.getUUID().equals(
                                protectedPerformer)) {
                    entityMutations.add(
                            EntityMutation.remove(entity));
                    stagedEntities.add(entity.getUUID());
                }
                continue;
            }
            ResourceLocation liveType =
                    BuiltInRegistries.ENTITY_TYPE
                            .getKey(entity.getType());
            if (liveType == null
                    || !liveType.toString()
                            .equals(target.typeId())) {
                return StageResult.failure(Result.UNSAFE_HISTORY);
            }
            if (!isLoadedTarget(level, target)) {
                return StageResult.failure(Result.UNLOADED_TARGET);
            }
            entityMutations.add(
                    EntityMutation.restore(entity, target));
            stagedEntities.add(entity.getUUID());
        }

        for (EntityTimeline timeline :
                history.entities.values()) {
            EntitySnapshot target =
                    timeline.atOrBefore(targetTick);
            if (target == null
                    || !timeline.destroyedAfter(targetTick)
                    || target.position().distanceToSqr(origin)
                            > rangeSquared
                    || level.getEntity(timeline.uuid) != null
                    || stagedEntities.contains(timeline.uuid)
                    || target.kind() == EntityKind.PLAYER) {
                continue;
            }
            if (!isLoadedTarget(level, target)) {
                return StageResult.failure(Result.UNLOADED_TARGET);
            }
            if (!canRecreate(target)) {
                return StageResult.failure(Result.UNSAFE_HISTORY);
            }
            entityMutations.add(EntityMutation.recreate(target));
            stagedEntities.add(timeline.uuid);
        }

        entityMutations.sort(Comparator.comparing(
                mutation -> mutation.uuid().toString()));
        return StageResult.success(new RewindPlan(
                targetTick,
                history.worldAtOrBefore(targetTick),
                List.copyOf(blocks.values()),
                List.copyOf(entityMutations),
                Set.copyOf(stagedEntities)));
    }

    private static boolean commit(
            ServerLevel level, RewindPlan plan) {
        List<BlockInverse> blockInverses = new ArrayList<>();
        List<EntityInverse> entityInverses = new ArrayList<>();
        List<UUID> created = new ArrayList<>();
        long inverseDayTime = level.getDayTime();

        enterApply();
        try {
            for (BlockPreimage target : plan.blocks()) {
                blockInverses.add(new BlockInverse(
                        target.position(),
                        captureBlockState(level, target.position())));
            }
            for (EntityMutation mutation :
                    plan.entities()) {
                if (mutation.live() != null) {
                    entityInverses.add(new EntityInverse(
                            mutation.uuid(),
                            EntitySnapshot.capture(
                                    mutation.live(), level.getGameTime())));
                }
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
                            level, mutation.live(), mutation.target());
                    case REMOVE -> mutation.live().remove(
                            RemovalReason.DISCARDED);
                    case RECREATE -> {
                        Entity recreated = recreate(
                                level, mutation.target());
                        created.add(recreated.getUUID());
                    }
                }
            }
            restoreRelationships(level, plan.entities());
            level.setDayTime(plan.world().dayTime());
            syncPlayersAndEntities(level, plan.entities());
            return true;
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Mandom rewind commit failed; applying inverse journal",
                    exception);
            if (!rollback(
                    level,
                    inverseDayTime,
                    blockInverses,
                    entityInverses,
                    created)) {
                LOGGER.error(
                        "Mandom rewind inverse journal was not fully restored");
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
            List<UUID> created) {
        boolean complete = true;
        List<EntityMutation> relationshipInverses =
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
                            "Could not remove recreated entity {} "
                                    + "while rolling back Mandom rewind",
                            uuid,
                            exception);
                }
            }
        }
        for (EntityInverse inverse : entities) {
            Entity entity = level.getEntity(inverse.uuid());
            try {
                if (entity != null) {
                    applyFullSnapshot(
                            level, entity, inverse.snapshot());
                    relationshipInverses.add(
                            EntityMutation.restore(
                                    entity, inverse.snapshot()));
                }
                else if (inverse.snapshot().kind()
                        != EntityKind.PLAYER) {
                    Entity recreated =
                            recreate(level, inverse.snapshot());
                    relationshipInverses.add(
                            EntityMutation.restore(
                                    recreated, inverse.snapshot()));
                }
            }
            catch (RuntimeException exception) {
                complete = false;
                LOGGER.error(
                        "Could not restore entity {} while rolling back "
                                + "Mandom rewind",
                        inverse.uuid(),
                        exception);
            }
        }
        try {
            restoreRelationships(level, relationshipInverses);
            syncPlayersAndEntities(level, relationshipInverses);
        }
        catch (RuntimeException exception) {
            complete = false;
            LOGGER.error(
                    "Could not restore entity relationships while "
                            + "rolling back Mandom rewind",
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
                        "Could not restore block {} while rolling back "
                                + "Mandom rewind",
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
                    "Could not restore day time while rolling back "
                            + "Mandom rewind",
                    exception);
        }
        return complete;
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
                        position, target, BLOCK_UPDATE_FLAGS)) {
            throw new IllegalStateException(
                    "Could not restore block " + position);
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
                        "Could not restore block entity " + position);
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
        Set<Holder<MobEffect>> oldEffects =
                effectTypes(entity);
        CompoundTag merged =
                entity.saveWithoutId(new CompoundTag());
        switch (snapshot.kind()) {
            case LIVING, PLAYER -> {
                copyKeys(snapshot.tag(), merged,
                        "Pos", "Rotation", "FallDistance",
                        "Fire", "Air", "Tags",
                        "Health", "HurtTime", "HurtByTimestamp",
                        "DeathTime", "AbsorptionAmount",
                        "active_effects", "ArmorItems", "HandItems",
                        "ArmorDropChances", "HandDropChances");
                if (snapshot.kind() == EntityKind.PLAYER) {
                    copyKeys(snapshot.tag(), merged,
                            "Inventory", "SelectedItemSlot",
                            "EnderItems", "XpP", "XpLevel",
                            "XpTotal", "foodLevel",
                            "foodTickTimer",
                            "foodSaturationLevel",
                            "foodExhaustionLevel");
                }
            }
            case PROJECTILE -> copyKeys(snapshot.tag(), merged,
                    "Pos", "Motion", "Rotation", "Fire",
                    "Air", "Tags", "NoGravity");
            case ITEM -> copyKeys(snapshot.tag(), merged,
                    "Pos", "Tags", "Item");
        }
        merged.putUUID("UUID", entity.getUUID());
        loadPreservingRuntimeLinks(entity, merged);
        entity.noPhysics = snapshot.noPhysics();
        restorePlayerPower(entity, snapshot);
        restorePlayerMenu(entity, snapshot);
        restoreSnapshotEffects(entity, snapshot.tag());
        syncRemovedEffects(level, entity, oldEffects);
        syncEntity(level, entity);
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
        loadPreservingRuntimeLinks(entity, tag);
        entity.noPhysics = snapshot.noPhysics();
        restorePlayerPower(entity, snapshot);
        restorePlayerMenu(entity, snapshot);
        restoreSnapshotEffects(entity, snapshot.tag());
        syncRemovedEffects(level, entity, oldEffects);
        syncEntity(level, entity);
    }

    private static void restoreSnapshotEffects(
            Entity entity, CompoundTag snapshot) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        Map<Holder<MobEffect>, MobEffectInstance> effects = new LinkedHashMap<>();
        var tags = snapshot.getList("active_effects", Tag.TAG_COMPOUND);
        for (int index = 0; index < tags.size(); index++) {
            MobEffectInstance effect = MobEffectInstance.load(tags.getCompound(index));
            if (effect != null) {
                effects.put(effect.getEffect(), effect);
            }
        }
        // Current-only effects can veto historical additions, so remove them before restoring the snapshot.
        for (MobEffectInstance current : new ArrayList<>(living.getActiveEffects())) {
            if (!effects.containsKey(current.getEffect())
                    && !isKeptPassiveEffect(living, current)) {
                living.removeEffect(current.getEffect());
                if (living.hasEffect(current.getEffect())) {
                    throw new IllegalStateException("Could not remove effect while rewinding " + entity.getUUID());
                }
            }
        }
        for (MobEffectInstance effect : effects.values()) {
            MobEffectInstance current = living.getEffect(effect.getEffect());
            Tag expected = effect.save();
            if (current != null && expected.equals(current.save())) {
                continue;
            }
            if (current != null && isKeptPassiveEffect(living, current)) {
                // The donor respects cancelled removal and then uses the normal effect merge.
                MobEffectInstance merged = MobEffectInstance.load((CompoundTag) current.save());
                if (merged == null) {
                    throw new IllegalStateException("Could not copy kept effect while rewinding " + entity.getUUID());
                }
                merged.update(effect);
                expected = merged.save();
                living.addEffect(effect);
            }
            else if (current != null) {
                // Replacement checks applicability without first deleting the original or its modifiers.
                replaceSnapshotEffect(living, current, effect);
            }
            else {
                living.addEffect(effect);
            }
            MobEffectInstance restored = living.getEffect(effect.getEffect());
            if (restored == null || !expected.equals(restored.save())) {
                throw new IllegalStateException("Could not restore effect while rewinding " + entity.getUUID());
            }
        }
        // Effect callbacks can change health caps and absorption before the snapshot values are applied.
        if (snapshot.contains("Health", Tag.TAG_ANY_NUMERIC)) {
            living.setHealth(snapshot.getFloat("Health"));
        }
        if (snapshot.contains("AbsorptionAmount", Tag.TAG_ANY_NUMERIC)) {
            living.setAbsorptionAmount(snapshot.getFloat("AbsorptionAmount"));
        }
    }

    private static void replaceSnapshotEffect(
            LivingEntity entity,
            MobEffectInstance current,
            MobEffectInstance replacement) {
        Tag expected = replacement.save();
        entity.forceAddEffect(replacement, null);
        MobEffectInstance restored = entity.getEffect(replacement.getEffect());
        if (restored != null && expected.equals(restored.save())) {
            // forceAddEffect checks applicability and updates modifiers but omits normal merge notifications.
            NeoForge.EVENT_BUS.post(new MobEffectEvent.Added(entity, current, replacement, null));
            replacement.onEffectStarted(entity);
        }
    }

    private static boolean isKeptPassiveEffect(
            LivingEntity entity, MobEffectInstance effect) {
        return VampirismData.isKeptPassiveEffect(entity, effect)
                || ZombieData.isKeptPassiveEffect(entity, effect);
    }

    private static void loadPreservingRuntimeLinks(
            Entity entity, CompoundTag tag) {
        StandPower previous = entity instanceof LivingEntity living
                ? StandPower.get(living) : null;
        var summoned = previous != null ? previous.getSummonedStand() : null;
        var type = previous != null ? previous.getPowerType() : null;
        LivingComponentAction action = entity instanceof LivingEntity living
                ? LivingComponentAction.getExistingComponent(living) : null;
        // Loading merges effect instances without running their attribute callbacks.
        CompoundTag loadTag = tag.copy();
        if (entity instanceof LivingEntity) {
            loadTag.remove("active_effects");
        }
        if (action != null) {
            ResourceLocation actionKey = NeoForgeRegistries.ATTACHMENT_TYPES
                    .getKey(ModDataAttachmentTypes.LIVING_ACTION.get());
            if (actionKey != null
                    && loadTag.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY, Tag.TAG_COMPOUND)) {
                // Empty action NBT must not construct a replacement tick listener.
                loadTag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                        .remove(actionKey.toString());
            }
        }
        try {
            entity.load(loadTag);
        }
        finally {
            // The live manifestation is not part of the serialized attachment.
            if (summoned != null && type != null
                    && entity instanceof LivingEntity living) {
                StandPower restored = StandPower.get(living);
                if (restored != null && restored.getPowerType() == type) {
                    restored.setSummonedStand(summoned);
                }
            }
        }
    }

    private static Entity recreate(
            ServerLevel level, EntitySnapshot snapshot) {
        CompoundTag tag = snapshot.tag().copy();
        tag.remove("Passengers");
        Entity entity = net.minecraft.world.entity.EntityType
                .loadEntityRecursive(tag, level, current -> current);
        if (entity == null || !level.addFreshEntity(entity)) {
            throw new IllegalStateException(
                    "Could not recreate entity " + snapshot.uuid());
        }
        entity.noPhysics = snapshot.noPhysics();
        restorePlayerPower(entity, snapshot);
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
                Entity vehicle = level.getEntity(snapshot.vehicle());
                if (vehicle != null) {
                    entity.startRiding(vehicle, true);
                }
            }
            if (entity instanceof Mob mob) {
                Entity target = snapshot.mobTarget() != null
                        ? level.getEntity(snapshot.mobTarget())
                        : null;
                mob.setTarget(target instanceof LivingEntity living
                        ? living
                        : null);
            }
            if (entity instanceof Projectile projectile) {
                projectile.setOwner(snapshot.projectileOwner() != null
                        ? level.getEntity(snapshot.projectileOwner())
                        : null);
            }
        }
    }

    private static void syncPlayersAndEntities(
            ServerLevel level,
            List<EntityMutation> mutations) {
        for (EntityMutation mutation : mutations) {
            Entity entity = level.getEntity(mutation.uuid());
            if (entity != null) {
                syncEntity(level, entity);
            }
        }
    }

    private static void syncEntity(
            ServerLevel level, Entity entity) {
        level.getChunkSource().broadcastAndSend(
                entity, new ClientboundTeleportEntityPacket(entity));
        if (entity instanceof LivingEntity living) {
            for (var effect :
                    living.getActiveEffectsMap().values()) {
                level.getChunkSource().broadcastAndSend(
                        entity,
                        new ClientboundUpdateMobEffectPacket(
                                entity.getId(), effect, true));
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
                        slot, living.getItemBySlot(slot).copy()));
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
            player.connection.send(
                    new ClientboundSetHealthPacket(
                            player.getHealth(),
                            player.getFoodData().getFoodLevel(),
                            player.getFoodData().getSaturationLevel()));
            player.connection.send(
                    new ClientboundSetExperiencePacket(
                            player.experienceProgress,
                            player.totalExperience,
                            player.experienceLevel));
            player.resetSentInfo();
            PlayerPower power = PlayerPower.get(player);
            if (power != null) {
                power.syncToPlayer(player);
            }
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

    private static void restorePlayerPower(
            Entity entity, EntitySnapshot snapshot) {
        if (!(entity instanceof LivingEntity living)
                || snapshot.playerPower() == null) {
            return;
        }
        PlayerPower power = PlayerPower.get(living);
        if (power == null) {
            return;
        }
        CompoundTag current =
                power.serializeNBT(entity.registryAccess());
        String currentType = current.getString("PowerType");
        String targetType =
                snapshot.playerPower().getString("PowerType");
        if (!targetType.isBlank()
                && targetType.equals(currentType)) {
            power.deserializeNBT(
                    entity.registryAccess(),
                    snapshot.playerPower().copy());
        }
    }

    private static void restorePlayerMenu(
            Entity entity, EntitySnapshot snapshot) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        int slots = Math.min(
                snapshot.craftingGrid().size(), 4);
        for (int index = 0; index < slots; index++) {
            int menuSlot = index + 1;
            if (menuSlot < player.inventoryMenu.slots.size()) {
                player.inventoryMenu.getSlot(menuSlot).set(
                        snapshot.craftingGrid()
                                .get(index).copy());
            }
        }
        player.containerMenu.setCarried(
                snapshot.carried().copy());
    }

    private static void playSuccessSafely(
            ServerLevel level,
            LivingEntity user,
            Vec3 origin) {
        try {
            level.playSound(
                    null,
                    origin.x,
                    origin.y,
                    origin.z,
                    MandomAddon.REWIND_START.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
            level.playSound(
                    null,
                    origin.x,
                    origin.y,
                    origin.z,
                    MandomAddon.REWIND.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Mandom rewind committed, but its sounds failed",
                    exception);
        }
        try {
            ItemStack clock = user.getOffhandItem();
            if (clock.is(MandomAddon.RINGO_CLOCK.get())) {
                clock.hurtAndBreak(
                        1, user, EquipmentSlot.OFFHAND);
            }
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Mandom rewind committed, but clock damage failed",
                    exception);
        }
        try {
            int route = visualRoute(user);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    user,
                    new RewindEffectPayload(10, route));
        }
        catch (RuntimeException exception) {
            LOGGER.error(
                    "Mandom rewind committed, but its visual payload "
                            + "failed",
                    exception);
        }
    }

    private static int visualRoute(LivingEntity user) {
        StandPower power = StandPower.get(user);
        String skin = power != null
                ? power.getStandInstance()
                        .flatMap(instance ->
                                instance.getSelectedSkin())
                        .map(ResourceLocation::getPath)
                        .orElse("")
                : "";
        return switch (skin) {
            case "acid" -> RewindEffectPayload.ACID_ROUTE;
            case "gray" -> RewindEffectPayload.GRAY_ROUTE;
            case "purple" -> RewindEffectPayload.PURPLE_ROUTE;
            default -> RewindEffectPayload.DEFAULT_ROUTE;
        };
    }

    private static BlockStateSnapshot captureBlockState(
            ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        BlockEntity blockEntity = level.getBlockEntity(position);
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
        ResourceLocation typeId =
                ResourceLocation.tryParse(snapshot.typeId());
        return typeId != null
                && BuiltInRegistries.ENTITY_TYPE
                        .containsKey(typeId)
                && snapshot.tag().contains("id", Tag.TAG_STRING);
    }

    private static boolean isLoadedTarget(
            ServerLevel level, EntitySnapshot snapshot) {
        BlockPos position = snapshot.blockPosition();
        return level.isLoaded(position)
                && level.getWorldBorder()
                        .isWithinBounds(position);
    }

    private static List<Entity> snapshotEntities(
            Iterable<Entity> entities) {
        List<Entity> snapshot = new ArrayList<>();
        for (Entity entity : entities) {
            snapshot.add(entity);
        }
        return snapshot;
    }

    private static boolean isTracked(Entity entity) {
        return entity instanceof LivingEntity
                        && !(entity instanceof ArmorStand)
                || entity instanceof Projectile
                || entity instanceof ItemEntity;
    }

    private static void copyKeys(
            CompoundTag source,
            CompoundTag target,
            String... keys) {
        for (String key : keys) {
            Tag value = source.get(key);
            if (value != null) {
                target.put(key, value.copy());
            }
            else {
                target.remove(key);
            }
        }
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
                    server, ignored -> new ServerHistory());
        }
    }

    private enum EntityKind {
        LIVING,
        PLAYER,
        PROJECTILE,
        ITEM
    }

    private enum EntityOperation {
        RESTORE,
        REMOVE,
        RECREATE
    }

    private record WorldSnapshot(long tick, long dayTime) {}

    private record BlockStateSnapshot(
            CompoundTag state, CompoundTag blockEntity) {}

    private record BlockInverse(
            BlockPos position, BlockStateSnapshot snapshot) {}

    private record EntityInverse(
            UUID uuid, EntitySnapshot snapshot) {}

    private record EntitySnapshot(
            UUID uuid,
            long tick,
            EntityKind kind,
            String typeId,
            CompoundTag tag,
            Vec3 position,
            boolean noPhysics,
            UUID mobTarget,
            UUID vehicle,
            UUID projectileOwner,
            CompoundTag playerPower,
            List<ItemStack> craftingGrid,
            ItemStack carried) {
        private static EntitySnapshot capture(
                Entity entity, long tick) {
            CompoundTag tag =
                    entity.saveWithoutId(new CompoundTag());
            ResourceLocation typeId =
                    BuiltInRegistries.ENTITY_TYPE
                            .getKey(entity.getType());
            if (typeId == null) {
                throw new IllegalStateException(
                        "Unregistered entity type "
                                + entity.getType());
            }
            tag.putString("id", typeId.toString());
            tag.remove("Passengers");

            EntityKind kind = entity instanceof Player
                    ? EntityKind.PLAYER
                    : entity instanceof LivingEntity
                            ? EntityKind.LIVING
                            : entity instanceof Projectile
                                    ? EntityKind.PROJECTILE
                                    : EntityKind.ITEM;
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
            CompoundTag playerPower = null;
            if (entity instanceof LivingEntity living) {
                PlayerPower power = PlayerPower.get(living);
                if (power != null) {
                    playerPower =
                            power.serializeNBT(
                                    entity.registryAccess());
                }
            }
            List<ItemStack> craftingGrid =
                    new ArrayList<>();
            ItemStack carried = ItemStack.EMPTY;
            if (entity instanceof ServerPlayer player) {
                for (int index = 1; index <= 4; index++) {
                    craftingGrid.add(
                            index < player.inventoryMenu
                                            .slots.size()
                                    ? player.inventoryMenu
                                            .getSlot(index)
                                            .getItem()
                                            .copy()
                                    : ItemStack.EMPTY);
                }
                carried = player.containerMenu
                        .getCarried().copy();
            }
            return new EntitySnapshot(
                    entity.getUUID(),
                    tick,
                    kind,
                    typeId.toString(),
                    tag,
                    entity.position(),
                    entity.noPhysics,
                    mobTarget,
                    vehicle,
                    projectileOwner,
                    playerPower,
                    List.copyOf(craftingGrid),
                    carried);
        }

        private BlockPos blockPosition() {
            return BlockPos.containing(position);
        }
    }

    private static final class EntityTimeline {
        private final UUID uuid;
        private final Deque<EntitySnapshot> snapshots =
                new ArrayDeque<>();
        private long destroyedAt = Long.MIN_VALUE;

        private EntityTimeline(UUID uuid) {
            this.uuid = uuid;
        }

        private void add(EntitySnapshot snapshot) {
            snapshots.addLast(snapshot);
            while (snapshots.size() > MAX_SNAPSHOTS) {
                snapshots.removeFirst();
            }
            destroyedAt = Long.MIN_VALUE;
        }

        private EntitySnapshot atOrBefore(long tick) {
            EntitySnapshot selected = null;
            for (EntitySnapshot snapshot : snapshots) {
                if (snapshot.tick() <= tick) {
                    selected = snapshot;
                }
                else {
                    break;
                }
            }
            return selected;
        }

        private boolean destroyedAfter(long tick) {
            return destroyedAt > tick;
        }

        private void prune(long oldestTick) {
            while (!snapshots.isEmpty()
                    && snapshots.peekFirst().tick()
                            < oldestTick) {
                snapshots.removeFirst();
            }
        }

        private void consumeThrough(long tick) {
            while (!snapshots.isEmpty()
                    && snapshots.peekFirst().tick() <= tick) {
                snapshots.removeFirst();
            }
        }
    }

    private static final class LevelHistory {
        private final ResourceKey<Level> dimension;
        private final Map<UUID, EntityTimeline> entities =
                new HashMap<>();
        private final Deque<WorldSnapshot> worlds =
                new ArrayDeque<>();
        private final List<BlockPreimage> blocks =
                new ArrayList<>();

        private LevelHistory(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        private void capture(ServerLevel level, long tick) {
            if (!level.dimension().equals(dimension)) {
                throw new IllegalStateException(
                        "Mandom level history dimension drift");
            }
            worlds.addLast(
                    new WorldSnapshot(tick, level.getDayTime()));
            while (worlds.size() > MAX_SNAPSHOTS) {
                worlds.removeFirst();
            }

            TimeStopState timeStop = level.hasData(
                    ModDataAttachmentTypes.TIME_STOP.get())
                    ? level.getData(
                            ModDataAttachmentTypes.TIME_STOP.get())
                    : null;
            for (Entity entity :
                    snapshotEntities(level.getAllEntities())) {
                if (!isTracked(entity)
                        || timeStop != null
                                && timeStop.isTimeStopped(entity)) {
                    continue;
                }
                EntitySnapshot snapshot =
                        EntitySnapshot.capture(entity, tick);
                entities.computeIfAbsent(
                                entity.getUUID(),
                                EntityTimeline::new)
                        .add(snapshot);
            }
        }

        private synchronized void recordBlock(
                BlockPreimage preimage) {
            blocks.add(preimage);
        }

        private synchronized List<BlockPreimage> blockRecords(
                long targetTick, long now) {
            return blocks.stream()
                    .filter(preimage ->
                            preimage.tick() > targetTick
                                    && preimage.tick() <= now)
                    .sorted(Comparator.comparingLong(
                            BlockPreimage::tick))
                    .toList();
        }

        private void markDestroyed(UUID uuid, long tick) {
            EntityTimeline timeline = entities.get(uuid);
            if (timeline != null) {
                timeline.destroyedAt = tick;
            }
        }

        private void forget(UUID uuid) {
            entities.remove(uuid);
        }

        private WorldSnapshot worldAtOrBefore(long tick) {
            WorldSnapshot selected = null;
            for (WorldSnapshot snapshot : worlds) {
                if (snapshot.tick() <= tick) {
                    selected = snapshot;
                }
                else {
                    break;
                }
            }
            return selected;
        }

        private synchronized void prune(long now) {
            long oldest =
                    now - MandomRollbackGate.REWIND_TICKS
                            - SAMPLE_INTERVAL_TICKS;
            blocks.removeIf(preimage ->
                    preimage.tick() < oldest);
            entities.values().forEach(
                    timeline -> timeline.prune(oldest));
            entities.values().removeIf(
                    timeline -> timeline.snapshots.isEmpty());
        }

        private synchronized void consume(RewindPlan plan) {
            blocks.clear();
            while (!worlds.isEmpty()
                    && worlds.peekFirst().tick()
                            <= plan.targetTick()) {
                worlds.removeFirst();
            }
            for (UUID uuid : plan.affectedEntities()) {
                EntityTimeline timeline = entities.get(uuid);
                if (timeline != null) {
                    timeline.consumeThrough(plan.targetTick());
                }
            }
        }
    }

    private static final class ServerHistory {
        private final Map<ResourceKey<Level>, LevelHistory> levels =
                new HashMap<>();

        private LevelHistory level(ServerLevel level) {
            return levels.computeIfAbsent(
                    level.dimension(), LevelHistory::new);
        }
    }

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

    private record RewindPlan(
            long targetTick,
            WorldSnapshot world,
            List<BlockPreimage> blocks,
            List<EntityMutation> entities,
            Set<UUID> affectedEntities) {}

    private record StageResult(
            RewindPlan plan, Result failure) {
        private static StageResult success(RewindPlan plan) {
            return new StageResult(plan, null);
        }

        private static StageResult failure(Result failure) {
            return new StageResult(null, failure);
        }
    }
}
