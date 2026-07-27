package com.github.standobyte.jojo.subsystems.timestop;

import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.api.timestop.TimeStopLifecycleEvent;
import com.github.standobyte.jojo.api.timestop.TimeStopLifecycleEvent.RemovalReason;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.entity_projectile.KnifeEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.event.ModEventHooks;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.modcompat.ModInteractionUtil;
import com.github.standobyte.jojo.network.s2c.TrDirectEntityDataPacket;
import com.github.standobyte.jojo.network.s2c.TrTimeStopInstancePacket;
import com.github.standobyte.jojo.network.s2c.TrTimeStopPlayerStatePacket;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.movement_input_sync.PlayerMovementInputData;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojo.util.functions.UtilFunctions;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class TimeStopState {
    private static final Map<Integer, TimeStopState.Instance> CLIENT_INSTANCES = new HashMap<>();
    public static final double TIME_STOP_SPRINT_FLOAT_START_THRESHOLD = 0.0D;
    public static final double TIME_STOP_SPRINT_FLOAT_LIFT_VELOCITY = 0.2D;
    public static final double TIME_STOP_SPRINT_FLOAT_STEADY_VELOCITY = 0.1D;
    public static final double TIME_STOP_SPRINT_FLOAT_LIFT_MULTIPLIER = 1.5D;
    private static boolean gamerulesFrozen;
    private static boolean previousDaylightCycle;
    private static boolean previousWeatherCycle;

    private final ServerLevel level;
    private final Map<Integer, TimeStopState.Instance> instances = new HashMap<>();
    private final Map<Integer, FrozenEntityState> frozenEntities = new HashMap<>();
    private final Map<Integer, List<Runnable>> onTimeResume = new HashMap<>();
    private final Set<UUID> playersVisionFrozen = new HashSet<>();
    private final Map<UUID, Map<Integer, Map<Integer, SynchedEntityData.DataValue<?>>>> delayedEntityData = new HashMap<>();

    public TimeStopState(ServerLevel level) {
        this.level = level;
    }

    public Collection<Instance> getInstances() {
        return instances.values();
    }

    public Optional<Instance> getInstance(int id) {
        return Optional.ofNullable(instances.get(id));
    }

    public Optional<Instance> getLongestInstanceIn(ChunkPos chunkPos) {
        return instances.values().stream()
                .filter(Instance::isActive)
                .filter(instance -> instance.covers(chunkPos))
                .max(Comparator.comparingInt(Instance::ticksLeft));
    }

    public boolean isTimeStopped(ChunkPos chunkPos) {
        return instances.values().stream().anyMatch(instance -> instance.isActive() && instance.covers(chunkPos));
    }

    public boolean isTimeStopped(Entity entity) {
        return isTimeStopped(new ChunkPos(entity.blockPosition()));
    }

    public int getTimeStopTicks(ChunkPos chunkPos) {
        return instances.values().stream()
                .filter(Instance::isActive)
                .filter(instance -> instance.covers(chunkPos))
                .mapToInt(Instance::ticksLeft)
                .max()
                .orElse(0);
    }

    public void putInstance(Instance instance) {
        tryPutInstance(instance);
    }

    public boolean tryPutInstance(Instance instance) {
        TimeStopLifecycleEvent.PreStart event =
                ModEventHooks.onTimeStopPreStart(level, instance);
        return commitPreStart(event);
    }

    @ApiStatus.Internal
    public boolean commitPreStart(TimeStopLifecycleEvent.PreStart event) {
        if (event == null || event.isCanceled() || event.getLevel() != level) {
            return false;
        }
        commitInstance(event.getInstance());
        return true;
    }

    private void commitInstance(Instance instance) {
        boolean firstInstance = instances.isEmpty();
        if (!firstInstance) {
            removeSoundsIfCrosses(instance);
        }
        if (firstInstance) {
            freezeTimeStopGamerulesIfNeeded();
        }
        Instance replaced = instances.put(instance.id(), instance);
        if (replaced != null) {
            reconcileFrozenEntities();
        }
        reconcileEntitiesForInstance(instance);
        if (replaced != null) {
            ModEventHooks.onTimeStopRemoved(
                    level, replaced, RemovalReason.REPLACED);
        }
        if (instances.get(instance.id()) == instance) {
            syncAddedInstanceToAll(instance);
            ModEventHooks.onTimeStopAdded(level, instance);
        }
    }

    private void removeSoundsIfCrosses(Instance newInstance) {
        for (var entry : instances.entrySet()) {
            Instance existing = entry.getValue();
            if (existing.ticksLeft() < newInstance.ticksLeft()
                    && newInstance.covers(existing.centerPos())) {
                entry.setValue(existing.withResumeSoundAndVoiceLineUserIds(-1, -1));
            }
        }
    }

    public Awareness canPlayerSeeAndMove(ServerPlayer player) {
        if (!isTimeStopped(player)) {
            return Awareness.FREE;
        }
        boolean canMove = canPlayerMoveInStoppedTime(player);
        boolean canSee = canPlayerSeeInStoppedTime(player, canMove);
        return new Awareness(canSee, canMove);
    }

    public void sendPlayerAwareness(ServerPlayer player) {
        Awareness awareness = canPlayerSeeAndMove(player);
        PacketDistributor.sendToPlayer(player, new TrTimeStopPlayerStatePacket(awareness.canSee(), awareness.canMove()));
        updateFrozenVisionState(player, awareness.canSee());
    }

    public void sendPlayerAwarenessRestore(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new TrTimeStopPlayerStatePacket(true, true));
        unfreezePlayerVision(player);
    }

    public void resendCurrentStateToPlayer(ServerPlayer player) {
        clearClientInstancesForPlayer(player);
        resendActiveInstancesToPlayer(player);
        if (isTimeStopped(player)) {
            sendPlayerAwareness(player);
        }
        else {
            sendPlayerAwarenessRestore(player);
        }
    }

    public void resendActiveInstancesToPlayer(ServerPlayer player) {
        ChunkPos playerChunk = new ChunkPos(player.blockPosition());
        for (Instance instance : instances.values()) {
            if (instance.isActive() && instance.covers(playerChunk)) {
                syncInstanceToPlayer(player, instance);
            }
        }
    }

    public void clearActiveInstancesFromPlayer(ServerPlayer player) {
        clearClientInstancesForPlayer(player);
        PacketDistributor.sendToPlayer(player, new TrTimeStopPlayerStatePacket(true, true));
        clearFrozenVisionForPlayer(player);
    }

    private void updateAllPlayerAwareness() {
        for (ServerPlayer player : level.players()) {
            sendPlayerAwareness(player);
        }
    }

    private ServerPlayer getServerPlayerByEntityId(int entityId) {
        for (ServerPlayer player : level.players()) {
            if (player.getId() == entityId) {
                return player;
            }
        }
        return null;
    }

    @Nullable
    private LivingEntity getLivingEntityById(int entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }

    public boolean shouldFreeze(Entity entity) {
        if (!isFreezable(entity)) {
            return false;
        }
        return isTimeStopped(entity) && !canEntityMoveInStoppedTime(entity);
    }

    public static boolean shouldFreezeClientEntity(Entity entity) {
        if (entity == null || entity.isRemoved() || !isTimeStoppedClientEntity(entity)) {
            return false;
        }
        return !canEntityMoveInStoppedTimeClient(entity);
    }

    private static boolean isTimeStoppedClientEntity(Entity entity) {
        return getClientDisplayInstance(new ChunkPos(entity.blockPosition())).isPresent();
    }

    private static boolean canEntityMoveInStoppedTimeClient(Entity entity) {
        if (!isTimeStoppedClientEntity(entity)) {
            return true;
        }
        if (entity instanceof KnifeEntity knife) {
            return knife.canMoveInStoppedTime();
        }
        if (entity instanceof OwnerBoundProjectileEntity ownerBound) {
            if (ownerBound.canTickInStoppedTime()) {
                return true;
            }
            LivingEntity owner = ownerBound.getOwner();
            return owner != null && canEntityMoveInStoppedTimeClient(owner);
        }
        if (entity instanceof StandEntity stand && stand.getUser() != null) {
            return canEntityMoveInStoppedTimeClient(stand.getUser());
        }
        if (entity instanceof SoulEntity soul && soul.getOriginEntity() != null) {
            return canEntityMoveInStoppedTimeClient(soul.getOriginEntity());
        }
        if (JojoModConfig.getCommonConfigInstance(entity.level().isClientSide()).endermenBeyondTimeSpace.get()
                && ModInteractionUtil.isEntityEnderman(entity)) {
            return true;
        }
        return entity instanceof LivingEntity living && living.hasEffect(ModStatusEffects.TIME_STOP);
    }

    public boolean shouldInterruptEarly(Entity entity) {
        return shouldFreeze(entity)
                && !(entity instanceof net.minecraft.world.entity.item.ItemEntity);
    }

    public boolean interruptTickEarly(Entity entity) {
        if (!shouldInterruptEarly(entity)) {
            return false;
        }
        freezeEntity(entity);
        applyFrozenPosition(entity);
        applyInterruptedFreezeState(entity);
        return true;
    }

    public void freezeEntity(Entity entity) {
        if (!isFreezable(entity) || frozenEntities.containsKey(entity.getId())) {
            return;
        }
        frozenEntities.put(entity.getId(), new FrozenEntityState(
                entity.position(),
                entity.getDeltaMovement(),
                entity.fallDistance,
                entity instanceof Mob mob ? mob.isNoAi() : null));
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0;
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
    }

    public void unfreezeEntity(Entity entity) {
        FrozenEntityState state = frozenEntities.remove(entity.getId());
        if (state == null) {
            return;
        }
        entity.setDeltaMovement(state.deltaMovement());
        entity.fallDistance = state.fallDistance();
        if (entity instanceof Mob mob && state.wasNoAi() != null) {
            mob.setNoAi(state.wasNoAi());
        }
        runQueuedOnTimeResume(entity);
    }

    public void queueOnTimeResume(Entity entity, Runnable action) {
        if (entity == null || action == null) {
            return;
        }
        if (!shouldFreeze(entity)) {
            action.run();
            return;
        }
        onTimeResume.computeIfAbsent(entity.getId(), ignored -> new ArrayList<>()).add(action);
    }

    private void runQueuedOnTimeResume(Entity entity) {
        List<Runnable> queued = onTimeResume.remove(entity.getId());
        if (queued != null) {
            for (Runnable action : queued) {
                action.run();
            }
        }
    }

    public void reconcileFrozenEntity(Entity entity) {
        if (!isFreezable(entity)) {
            unfreezeEntity(entity);
            return;
        }
        if (shouldFreeze(entity)) {
            freezeEntity(entity);
            FrozenEntityState state = frozenEntities.get(entity.getId());
            if (state != null) {
                entity.setPos(state.position());
                applyInterruptedFreezeState(entity);
            }
        }
        else {
            unfreezeEntity(entity);
        }
    }

    public void refreshTimeStopEffectState(LivingEntity entity) {
        if (entity.level() != level) {
            return;
        }
        reconcileFrozenEntity(entity);
        if (entity instanceof ServerPlayer player) {
            if (isTimeStopped(player)) {
                sendPlayerAwareness(player);
            }
            else {
                sendPlayerAwarenessRestore(player);
            }
        }
    }

    public void removeInstance(int id) {
        removeInstance(id, RemovalReason.EXPLICIT);
    }

    public void removeInstance(int id, RemovalReason reason) {
        Objects.requireNonNull(reason);
        Instance removed = instances.remove(id);
        if (removed != null) {
            applyTimeStopCooldown(removed);
            removeTimeStopEffectIfNoActiveInstance(removed);
            reconcileFrozenEntities();
            restoreTimeStopGamerulesIfNoActiveInstances();
            syncRemovedInstanceToAll(id);
            updateAllPlayerAwareness();
            ModEventHooks.onTimeStopRemoved(level, removed, reason);
        }
    }

    public void reset() {
        List<Instance> removedInstances = new ArrayList<>(instances.values());
        instances.clear();
        applyTimeStopCooldowns(removedInstances);
        for (Instance removed : removedInstances) {
            removeTimeStopEffectIfNoActiveInstance(removed);
        }
        reconcileFrozenEntities();
        restoreTimeStopGamerulesIfNoActiveInstances();
        for (Instance removed : removedInstances) {
            syncRemovedInstanceToAll(removed.id());
        }
        updateAllPlayerAwareness();
        pruneFrozenVisionPlayers();
        for (Instance removed : removedInstances) {
            ModEventHooks.onTimeStopRemoved(
                    level, removed, RemovalReason.RESET);
        }
    }

    public boolean requestManualResume(int id) {
        Instance instance = instances.get(id);
        if (instance == null || !instance.isActive()) {
            return false;
        }
        boolean forceResumeVoiceLine = instance.ticksLeft() > Instance.TIME_RESUME_VOICELINE_TICKS;
        Instance updated = instance.withTicksLeft(0, true, forceResumeVoiceLine);
        instances.put(id, updated);
        syncInstanceToAll(updated);
        return true;
    }

    public void tickLifecycle() {
        List<PendingRemoval> pendingRemovals = new ArrayList<>();
        var iter = instances.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Instance instance = entry.getValue().tickDown();
            boolean shouldEnd = shouldEndTimeStop(instance);
            if (instance.ticksLeft() > 0 && !shouldEnd) {
                boolean forcedResumeVoiceLinePlayed = playResumeVoiceLine(instance);
                if (forcedResumeVoiceLinePlayed) {
                    instance = instance.withForceResumeVoiceLine(false);
                }
                playResumeSound(instance);
                applySprintFloat(instance);
                entry.setValue(instance);
            }
            else {
                if (!shouldEnd && instance.forceResumeVoiceLine()) {
                    playResumeVoiceLine(instance);
                }
                Instance expired = instance;
                iter.remove();
                RemovalReason reason = shouldEnd
                        ? RemovalReason.INTERRUPTED
                        : expired.ticksManuallySet()
                                ? RemovalReason.MANUAL_RESUME
                                : RemovalReason.EXPIRED;
                pendingRemovals.add(
                        new PendingRemoval(expired, reason));
            }
        }
        if (!pendingRemovals.isEmpty()) {
            applyTimeStopCooldowns(pendingRemovals.stream()
                    .map(PendingRemoval::instance)
                    .toList());
            for (PendingRemoval removal : pendingRemovals) {
                removeTimeStopEffectIfNoActiveInstance(
                        removal.instance());
            }
            reconcileFrozenEntities();
            restoreTimeStopGamerulesIfNoActiveInstances();
            for (PendingRemoval removal : pendingRemovals) {
                syncRemovedInstanceToAll(removal.instance().id());
            }
            updateAllPlayerAwareness();
        }
        if (!instances.isEmpty() && !playersVisionFrozen.isEmpty()) {
            manualEntitiesDataSync();
        }
        for (PendingRemoval removal : pendingRemovals) {
            ModEventHooks.onTimeStopRemoved(
                    level, removal.instance(), removal.reason());
        }
    }

    private void applySprintFloat(Instance instance) {
        ServerPlayer player = getServerPlayerByEntityId(instance.userId());
        if (player == null || instance.isStartupSettling() || !canPlayerMoveInStoppedTime(player)
                || !instance.covers(new ChunkPos(player.blockPosition()))) {
            clearTimeStopFloat(player);
            return;
        }
        applyTimeStopFloat(player, isTimeStopFloatInput(player));
    }

    public static boolean applyTimeStopFloat(Player player, boolean inputHeld) {
        PlayerMovementInputData input = PlayerMovementInputData.get(player);
        if (input == null) {
            return false;
        }
        if (!inputHeld || player.getAbilities().flying || player.onGround()) {
            input.timeStopFloatActive = false;
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        if (!input.timeStopFloatActive) {
            if (motion.y > TIME_STOP_SPRINT_FLOAT_START_THRESHOLD) {
                return false;
            }
            input.timeStopFloatActive = true;
        }
        double upwardVelocity = roundaboutTimeStopFloatVelocity(motion.y);
        player.setDeltaMovement(motion.x, upwardVelocity, motion.z);
        player.hasImpulse = true;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.hurtMarked = true;
        }
        player.fallDistance = 0.0F;
        return true;
    }

    private static double roundaboutTimeStopFloatVelocity(double currentYVelocity) {
        double liftVelocity = TIME_STOP_SPRINT_FLOAT_LIFT_VELOCITY * TIME_STOP_SPRINT_FLOAT_LIFT_MULTIPLIER;
        double steadyVelocity = TIME_STOP_SPRINT_FLOAT_STEADY_VELOCITY * TIME_STOP_SPRINT_FLOAT_LIFT_MULTIPLIER;
        double upwardVelocity = liftVelocity;
        double recoveredVelocity = currentYVelocity + liftVelocity;
        if (recoveredVelocity >= steadyVelocity) {
            upwardVelocity = steadyVelocity;
        }
        return Math.max(currentYVelocity, upwardVelocity);
    }

    public static double travelGravityForTimeStopFloat(Player player, double gravity) {
        PlayerMovementInputData input = PlayerMovementInputData.get(player);
        if (input != null && input.timeStopFloatActive && !player.onGround() && !player.getAbilities().flying) {
            player.fallDistance = 0.0F;
            return 0.0D;
        }
        return gravity;
    }

    public static void clearTimeStopFloat(Player player) {
        PlayerMovementInputData input = PlayerMovementInputData.get(player);
        if (input != null) {
            input.timeStopFloatActive = false;
        }
    }

    private static boolean isTimeStopFloatInput(Player player) {
        PlayerMovementInputData input = PlayerMovementInputData.get(player);
        return input != null && input.sprint && input.jumping;
    }

    private void updateFrozenVisionState(ServerPlayer player, boolean canSee) {
        if (canSee) {
            unfreezePlayerVision(player);
        }
        else {
            playersVisionFrozen.add(player.getUUID());
        }
    }

    private void unfreezePlayerVision(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (playersVisionFrozen.remove(playerId) || delayedEntityData.containsKey(playerId)) {
            sendDataOnTimeStopUnfreeze(player);
        }
    }

    private void clearFrozenVisionForPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        playersVisionFrozen.remove(playerId);
        delayedEntityData.remove(playerId);
    }

    private void manualEntitiesDataSync() {
        pruneFrozenVisionPlayers();
        if (playersVisionFrozen.isEmpty()) {
            return;
        }
        for (Entity entity : UtilFunctions.getEntities(level)) {
            SynchedEntityData entityData = entity.getEntityData();
            if (!entityData.isDirty()) {
                continue;
            }
            List<ServerPlayer> trackingPlayers = NetworkUtil.getTrackingPlayers(entity)
                    .map(connection -> connection.getPlayer())
                    .toList();
            if (trackingPlayers.stream().noneMatch(player -> playersVisionFrozen.contains(player.getUUID()))) {
                continue;
            }
            List<SynchedEntityData.DataValue<?>> packedData = entityData.packDirty();
            if (packedData == null || packedData.isEmpty()) {
                continue;
            }
            for (ServerPlayer tracking : trackingPlayers) {
                if (playersVisionFrozen.contains(tracking.getUUID())) {
                    addDataForTimeStopUnfreeze(tracking, entity, packedData);
                }
                else {
                    PacketDistributor.sendToPlayer(tracking, new TrDirectEntityDataPacket(entity.getId(), packedData));
                }
            }
        }
    }

    private void addDataForTimeStopUnfreeze(ServerPlayer player, Entity entity, List<SynchedEntityData.DataValue<?>> newData) {
        Map<Integer, Map<Integer, SynchedEntityData.DataValue<?>>> entitiesData = delayedEntityData
                .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        Map<Integer, SynchedEntityData.DataValue<?>> dataByAccessor = entitiesData
                .computeIfAbsent(entity.getId(), ignored -> new HashMap<>());
        for (SynchedEntityData.DataValue<?> dataValue : newData) {
            dataByAccessor.put(dataValue.id(), dataValue);
        }
    }

    private void sendDataOnTimeStopUnfreeze(ServerPlayer player) {
        Map<Integer, Map<Integer, SynchedEntityData.DataValue<?>>> entitiesData = delayedEntityData.remove(player.getUUID());
        if (entitiesData == null || entitiesData.isEmpty()) {
            return;
        }
        for (var entry : entitiesData.entrySet()) {
            Map<Integer, SynchedEntityData.DataValue<?>> data = entry.getValue();
            if (!data.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new TrDirectEntityDataPacket(entry.getKey(), new ArrayList<>(data.values())));
            }
        }
    }

    private void pruneFrozenVisionPlayers() {
        playersVisionFrozen.removeIf(playerId -> !isPlayerInLevel(playerId));
        delayedEntityData.keySet().removeIf(playerId -> !isPlayerInLevel(playerId));
    }

    private boolean isPlayerInLevel(UUID playerId) {
        for (ServerPlayer player : level.players()) {
            if (player.getUUID().equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    private void freezeTimeStopGamerulesIfNeeded() {
        MinecraftServer server = level.getServer();
        if (gamerulesFrozen || hasActiveTimeStopInAnyLevel(server)) {
            return;
        }
        GameRules gameRules = server.overworld().getGameRules();
        previousDaylightCycle = gameRules.getBoolean(GameRules.RULE_DAYLIGHT);
        gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        previousWeatherCycle = gameRules.getBoolean(GameRules.RULE_WEATHER_CYCLE);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        gamerulesFrozen = true;
    }

    private void restoreTimeStopGamerulesIfNoActiveInstances() {
        MinecraftServer server = level.getServer();
        if (!gamerulesFrozen || hasActiveTimeStopInAnyLevel(server)) {
            return;
        }
        GameRules gameRules = server.overworld().getGameRules();
        gameRules.getRule(GameRules.RULE_DAYLIGHT).set(previousDaylightCycle, server);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(previousWeatherCycle, server);
        gamerulesFrozen = false;
    }

    private static boolean hasActiveTimeStopInAnyLevel(MinecraftServer server) {
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        for (ServerLevel serverLevel : server.getAllLevels()) {
            if (serverLevel.hasData(attachmentType)
                    && serverLevel.getData(attachmentType).instances.values().stream().anyMatch(Instance::isActive)) {
                return true;
            }
        }
        return false;
    }

    private void reconcileFrozenEntities() {
        for (Integer entityId : new ArrayList<>(frozenEntities.keySet())) {
            Entity entity = level.getEntity(entityId);
            if (entity == null || entity.isRemoved()) {
                frozenEntities.remove(entityId);
                onTimeResume.remove(entityId);
                continue;
            }
            if (shouldFreeze(entity)) {
                FrozenEntityState state = frozenEntities.get(entityId);
                if (state != null) {
                    entity.setPos(state.position());
                }
                applyInterruptedFreezeState(entity);
            }
            else {
                unfreezeEntity(entity);
            }
        }
    }

    private void reconcileEntitiesForInstance(Instance instance) {
        for (Entity entity : UtilFunctions.getEntities(level)) {
            if (instance.covers(new ChunkPos(entity.blockPosition()))) {
                reconcileFrozenEntity(entity);
            }
        }
    }

    private void applyTimeStopCooldown(Instance removed) {
        applyTimeStopCooldowns(List.of(removed));
    }

    private void applyTimeStopCooldowns(List<Instance> removedInstances) {
        List<TimeStopSettlement> settlements =
                new ArrayList<>(removedInstances.size());
        Map<StandPower, Integer> cooldownTicks =
                new IdentityHashMap<>();
        for (Instance removed : removedInstances) {
            LivingEntity user = getLivingEntityById(removed.userId());
            StandPower power =
                    user != null ? PowerClass.STAND.get(user) : null;
            if (power != null && power.hasPower()) {
                int effectiveTicksPassed =
                        Math.max(removed.ticksPassed(), 0);
                settlements.add(new TimeStopSettlement(
                        power, removed, effectiveTicksPassed));
                cooldownTicks.merge(
                        power, effectiveTicksPassed, Math::max);
            }
        }
        for (TimeStopSettlement settlement : settlements) {
            refundUnusedTimeStopStartCost(
                    settlement.power(),
                    settlement.instance(),
                    settlement.effectiveTicksPassed());
        }
        for (TimeStopSettlement settlement : settlements) {
            TimeStopLearning.onTimeStopEnded(
                    settlement.power(),
                    settlement.effectiveTicksPassed());
        }
        for (Map.Entry<StandPower, Integer> cooldown
                : cooldownTicks.entrySet()) {
            TimeStopCooldowns.setTimeStopCooldownsOnTimeStopEnd(
                    cooldown.getKey(), cooldown.getValue());
        }
    }

	private void refundUnusedTimeStopStartCost(StandPower power, Instance removed, int effectiveTicksPassed) {
		if (power.isStaminaInfinite()) {
			return;
		}
		int totalTicks = Math.max(removed.totalTicks(), 0);
		if (totalTicks <= 0) {
			return;
		}
		int elapsedTicks = Math.max(0, Math.min(effectiveTicksPassed, totalTicks));
		if (elapsedTicks >= totalTicks) {
			return;
		}
		float unusedRatio = (float) (totalTicks - elapsedTicks) / (float) totalTicks;
		float refund = TimeStopLearning.getTimeStopStaminaCost(power, removed.totalTicks())
				* PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power)
				* unusedRatio;
		if (refund > 0.0F) {
			power.setStamina(power.getStamina() + refund);
		}
	}

    private void removeTimeStopEffectIfNoActiveInstance(Instance removed) {
        LivingEntity user = getLivingEntityById(removed.userId());
        if (user != null && user.hasEffect(ModStatusEffects.TIME_STOP)
                && instances.values().stream().noneMatch(instance -> instance.isActive() && instance.userId() == removed.userId())) {
            user.removeEffect(ModStatusEffects.TIME_STOP);
        }
    }

    private boolean shouldEndTimeStop(Instance instance) {
        LivingEntity user = getLivingEntityById(instance.userId());
        if (user == null || !user.isAlive()) {
            return true;
        }
        StandPower power = PowerClass.STAND.get(user);
        if (power == null || !power.hasPower()) {
            return true;
        }
        Ability timeStop = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
        if (timeStop == null || !timeStop.isAbilityUnlocked(power)) {
            return true;
        }
        if (instance.isStartupSettling()) {
            return false;
        }
        float staminaCostTick = instance.staminaCostTick();
        if (staminaCostTick > 0) {
            staminaCostTick *= PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
            staminaCostTick += power.getStaminaTickGain();
            if (!power.consumeStamina(staminaCostTick, true)) {
                power.setStamina(0);
                return true;
            }
        }
        return false;
    }

    private boolean playResumeVoiceLine(Instance instance) {
        if (instance.resumeVoiceLineUserId() < 0 || instance.totalTicks() < 100) {
            return false;
        }
        if (instance.ticksLeft() == Instance.TIME_RESUME_SOUND_TICKS) {
            return false;
        }
        if (instance.ticksLeft() != Instance.TIME_RESUME_VOICELINE_TICKS && !instance.forceResumeVoiceLine()) {
            return false;
        }
        LivingEntity resumeVoiceLineUser = getLivingEntityById(instance.resumeVoiceLineUserId());
        StandPower power = resumeVoiceLineUser != null ? PowerClass.STAND.get(resumeVoiceLineUser) : null;
        Holder<SoundEvent> voiceLine = power != null ? getResumeVoiceLine(instance, power) : null;
        if (resumeVoiceLineUser != null && power != null && voiceLine != null) {
            JojoModUtil.sayVoiceLine(resumeVoiceLineUser, voiceLine);
        }
        return instance.forceResumeVoiceLine();
    }

    @Nullable
    private Holder<SoundEvent> getResumeVoiceLine(Instance instance, StandPower power) {
        if (power.getPowerType() == ModStands.STAR_PLATINUM.get()) {
            return ModSoundEvents.JOTARO_TIME_RESUMES;
        }
		if (power.getPowerType() == ModStands.THE_WORLD.get()) {
			return instance.ticksManuallySet() ? ModSoundEvents.DIO_TIME_RESUMES : ModSoundEvents.DIO_TIMES_UP;
		}
		return null;
	}

	private void playResumeSound(Instance instance) {
        if (instance.ticksLeft() == Instance.TIME_RESUME_SOUND_TICKS) {
            if (instance.resumeSoundUserId() < 0) {
                return;
            }
            LivingEntity user = getLivingEntityById(instance.userId());
            LivingEntity resumeSoundUser = getLivingEntityById(instance.resumeSoundUserId());
            StandPower power = resumeSoundUser != null ? PowerClass.STAND.get(resumeSoundUser) : null;
            if (user != null && power != null) {
                StandUtil.broadcastSoundWithCondition(level, user.position(), getTimeResumeSound(power),
                        false, power, SoundSource.AMBIENT, 5.0F, 1.0F,
                        player -> instance.covers(new ChunkPos(player.blockPosition()))
                                && canPlayerSeeInStoppedTime(player));
            }
        }
    }

    private static Holder<SoundEvent> getTimeResumeSound(StandPower power) {
        return power != null && power.getPowerType() == ModStands.STAR_PLATINUM.get()
                ? ModSoundEvents.STAR_PLATINUM_TIME_RESUME
                : ModSoundEvents.THE_WORLD_TIME_RESUME;
    }

    public void syncInstanceToAll(Instance instance) {
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, timeStopInstancePacket(instance, false));
        }
    }

    private void syncAddedInstanceToAll(Instance instance) {
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, timeStopInstancePacket(instance, true));
            sendPlayerAwareness(player);
        }
    }

    public void syncRemovedInstanceToAll(int id) {
        for (var player : level.players()) {
            syncRemovedInstanceToPlayer(player, id);
        }
    }

    private void syncInstanceToPlayer(ServerPlayer player, Instance instance) {
        PacketDistributor.sendToPlayer(player, timeStopInstancePacket(instance, true));
    }

    private static TrTimeStopInstancePacket timeStopInstancePacket(Instance instance, boolean openingVisual) {
        return new TrTimeStopInstancePacket(
                instance.id(),
                instance.ticksLeft(),
                instance.totalTicks(),
                instance.centerPos().x,
                instance.centerPos().z,
                instance.chunkRange(),
                instance.userId(),
                instance.visualRoute(),
                instance.standTypeId(),
                instance.selectedSkin(),
                instance.resumeSoundUserId(),
                instance.resumeVoiceLineUserId(),
                instance.ticksManuallySet(),
                instance.forceResumeVoiceLine(),
                instance.staminaCostTick(),
                instance.ticksPassed(),
                false,
                openingVisual);
    }

    private void syncRemovedInstanceToPlayer(ServerPlayer player, int id) {
        PacketDistributor.sendToPlayer(player, removedTimeStopInstancePacket(id));
    }

    private void clearClientInstancesForPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, removedTimeStopInstancePacket(-1));
    }

    private static TrTimeStopInstancePacket removedTimeStopInstancePacket(int id) {
        return new TrTimeStopInstancePacket(id, 0, 0, 0, 0, 0, -1, "", Optional.empty(), Optional.empty(), -1, -1, false, false, 0, 0, true, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
            if (level.hasData(attachmentType)) {
                TimeStopState state = level.getData(attachmentType);
                if (!state.instances.isEmpty()) {
                    state.tickLifecycle();
                }
            }
        }
    }

    public static Optional<Instance> getClientInstance(int id) {
        return Optional.ofNullable(CLIENT_INSTANCES.get(id));
    }

    public static Optional<Instance> getClientDisplayInstance(ChunkPos localPlayerChunk) {
        Instance selected = null;
        for (Instance instance : CLIENT_INSTANCES.values()) {
            if (!instance.isActive() || !instance.covers(localPlayerChunk)) {
                continue;
            }
            if (selected == null || instance.ticksLeft() > selected.ticksLeft()) {
                selected = instance;
            }
        }
        return Optional.ofNullable(selected);
    }

    public static void putClientInstance(Instance instance) {
        Instance previous = CLIENT_INSTANCES.get(instance.id());
        if (previous != null && previous.isActive() && instance.isActive() && instance.ticksPassed() <= 0) {
            instance = new Instance(
                    instance.id(),
                    instance.ticksLeft(),
                    instance.totalTicks(),
                    instance.centerPos(),
                    instance.chunkRange(),
                    instance.userId(),
                    instance.visualRoute(),
                    instance.standTypeId(),
                    instance.selectedSkin(),
                    instance.resumeSoundUserId(),
                    instance.resumeVoiceLineUserId(),
                    instance.ticksManuallySet(),
                    instance.forceResumeVoiceLine(),
                    instance.staminaCostTick(),
                    previous.ticksPassed());
        }
        CLIENT_INSTANCES.put(instance.id(), instance);
    }

    public static void removeClientInstance(int id) {
        CLIENT_INSTANCES.remove(id);
    }

    public static void tickClientInstances() {
        var iter = CLIENT_INSTANCES.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Instance ticked = entry.getValue().tickDown();
            if (ticked.isActive()) {
                entry.setValue(ticked);
            }
            else {
                iter.remove();
            }
        }
    }

    public static void clearClientInstances() {
        CLIENT_INSTANCES.clear();
    }

    public ServerLevel level() {
        return level;
    }

    private void applyInterruptedFreezeState(Entity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0;
        if (entity instanceof LivingEntity living && living.invulnerableTime > 0) {
            living.invulnerableTime--;
        }
    }

    private void applyFrozenPosition(Entity entity) {
        FrozenEntityState state = frozenEntities.get(entity.getId());
        if (state != null) {
            entity.setPos(state.position());
        }
    }

    private boolean isFreezable(Entity entity) {
        return !(entity instanceof LivingEntity living && living.hasEffect(ModStatusEffects.TIME_STOP))
                && !entity.isRemoved();
    }

    public static boolean canPlayerSeeInStoppedTime(ServerPlayer player) {
        return canPlayerSeeInStoppedTime(player, canPlayerMoveInStoppedTime(player));
    }

    private static boolean canPlayerSeeInStoppedTime(ServerPlayer player, boolean canMove) {
        return canMove || hasTimeStopAbility(player);
    }

    private static boolean canPlayerMoveInStoppedTime(ServerPlayer player) {
        return player.hasEffect(ModStatusEffects.TIME_STOP)
                || gameModeIgnoresTimeStop(player)
                || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    private static boolean gameModeIgnoresTimeStop(ServerPlayer player) {
        return JojoModUtil.getActualGameModeWhilePossessing(player)
                .map(gameMode -> gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR)
                .orElseGet(() -> player.isCreative() || player.isSpectator());
    }

    private boolean canEntityMoveInStoppedTime(Entity entity) {
        if (!isTimeStopped(entity)) {
            return true;
        }
        if (entity instanceof KnifeEntity knife) {
            return knife.canMoveInStoppedTime();
        }
        if (entity instanceof OwnerBoundProjectileEntity ownerBound) {
            if (ownerBound.canTickInStoppedTime()) {
                return true;
            }
            LivingEntity owner = ownerBound.getOwner();
            return owner != null && canEntityMoveInStoppedTime(owner);
        }
        if (entity instanceof StandEntity stand && stand.getUser() != null) {
            return canEntityMoveInStoppedTime(stand.getUser());
        }
        if (entity instanceof SoulEntity soul && soul.getOriginEntity() != null) {
            return canEntityMoveInStoppedTime(soul.getOriginEntity());
        }
        if (entity instanceof ServerPlayer player) {
            return canPlayerMoveInStoppedTime(player);
        }
        if (JojoModConfig.getCommonConfigInstance(entity.level().isClientSide()).endermenBeyondTimeSpace.get()
                && ModInteractionUtil.isEntityEnderman(entity)) {
            return true;
        }
        return entity instanceof LivingEntity living && living.hasEffect(ModStatusEffects.TIME_STOP);
    }

    private static boolean hasTimeStopAbility(LivingEntity entity) {
        StandPower standPower = PowerClass.STAND.get(entity);
        if (standPower == null || !standPower.hasPower()) {
            return false;
        }
        return standPower.getMoveset().abilities.values().stream()
                .anyMatch(ability -> ability.canUserSeeInStoppedTime(entity, standPower)
                        && ability.isAbilityUnlocked(standPower));
    }

    public record Awareness(boolean canSee, boolean canMove) {
        public static final Awareness FREE = new Awareness(true, true);
    }

    private record FrozenEntityState(Vec3 position, Vec3 deltaMovement, float fallDistance, @Nullable Boolean wasNoAi) {}

    private record PendingRemoval(
            Instance instance, RemovalReason reason) {}

    private record TimeStopSettlement(
            StandPower power,
            Instance instance,
            int effectiveTicksPassed) {}

    public static record Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, Optional<ResourceLocation> standTypeId, Optional<ResourceLocation> selectedSkin, int resumeSoundUserId, int resumeVoiceLineUserId, boolean ticksManuallySet, boolean forceResumeVoiceLine, float staminaCostTick, int ticksPassed) {
        public static final int TIME_RESUME_SOUND_TICKS = 10;
        public static final int TIME_RESUME_VOICELINE_TICKS = 30;
        public static final int TIME_RESUME_FIRST_CLICK_TICKS = TIME_RESUME_SOUND_TICKS + 1;

        public Instance {
            standTypeId = standTypeId != null ? standTypeId : Optional.empty();
            selectedSkin = selectedSkin != null ? selectedSkin : Optional.empty();
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, Optional.empty(), Optional.empty(), userId, userId, false, false, 0, 0);
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, float staminaCostTick) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, Optional.empty(), Optional.empty(), userId, userId, false, false, staminaCostTick, 0);
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, int resumeSoundUserId, float staminaCostTick) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, Optional.empty(), Optional.empty(), resumeSoundUserId, userId, false, false, staminaCostTick, 0);
        }

        public Instance(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, int userId, String visualRoute, int resumeSoundUserId, int resumeVoiceLineUserId, float staminaCostTick) {
            this(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, Optional.empty(), Optional.empty(), resumeSoundUserId, resumeVoiceLineUserId, false, false, staminaCostTick, 0);
        }

        public static Instance of(int id, int ticksLeft, int totalTicks, ChunkPos centerPos, int chunkRange, @Nullable LivingEntity user, String visualRoute) {
            Instance instance = new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, user == null ? -1 : user.getId(), visualRoute);
            return user != null ? instance.withStandSkin(StandPower.get(user)) : instance;
        }

        public boolean covers(ChunkPos chunkPos) {
            if (chunkRange <= 0) {
                return true;
            }
            return Math.abs(chunkPos.x - centerPos.x) < chunkRange
                    && Math.abs(chunkPos.z - centerPos.z) < chunkRange;
        }

        public boolean isActive() {
            return ticksLeft > 0;
        }

        public boolean isStartupSettling() {
            return ticksPassed < 0;
        }

        public Instance withTicksLeft(int ticksLeft, boolean ticksManuallySet) {
            return withTicksLeft(ticksLeft, ticksManuallySet, false);
        }

        public Instance withTicksLeft(int ticksLeft, boolean ticksManuallySet, boolean forceResumeVoiceLine) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, this.ticksManuallySet || ticksManuallySet, this.forceResumeVoiceLine || forceResumeVoiceLine, staminaCostTick, Math.max(ticksPassed, 0));
        }

        public Instance withTiming(int ticksLeft, int totalTicks) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed);
        }

        public Instance withArea(ChunkPos centerPos, int chunkRange) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed);
        }

        public Instance withVisualRoute(String visualRoute) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed);
        }

        public Instance withStaminaCostTick(float staminaCostTick) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed);
        }

        public Instance withResumeSoundUserId(int resumeSoundUserId) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed);
        }

        public Instance withResumeSoundAndVoiceLineUserIds(int resumeSoundUserId, int resumeVoiceLineUserId) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed);
        }

        public Instance withForceResumeVoiceLine(boolean forceResumeVoiceLine) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed);
        }

        public Instance withStartupDelay(int startupDelayTicks) {
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, -Math.max(startupDelayTicks, 0));
        }

        public Instance withStandSkin(@Nullable StandPower power) {
            if (power == null || !power.hasPower()) {
                return this;
            }
            return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute,
                    power.getStandInstance().map(stand -> stand.getStandId()),
                    power.getSelectedSkin(),
                    resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed);
        }

        public Instance tickDown() {
            if (ticksPassed < 0) {
                return new Instance(id, ticksLeft, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed + 1);
            }
            return new Instance(id, ticksLeft - 1, totalTicks, centerPos, chunkRange, userId, visualRoute, standTypeId, selectedSkin, resumeSoundUserId, resumeVoiceLineUserId, ticksManuallySet, forceResumeVoiceLine, staminaCostTick, ticksPassed + 1);
        }
    }
}
