package rotp.addons.jumpingjackflash.state;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class GravityLeaseService {
    public enum Source {
        SNOWBALL,
        DOMAIN
    }

    private static final String ROOT_TAG =
            "JumpingJackFlashGravity";
    private static final String ORIGINAL_NO_GRAVITY =
            "OriginalNoGravity";
    private static final String SOURCES = "Sources";
    private static final String SLOW_SNAPSHOT_CAPTURED =
            "SlownessSnapshotCaptured";
    private static final String PRIOR_SLOWNESS = "PriorSlowness";
    private static final String LEGACY_SLOW_SNAPSHOT_CAPTURED =
            "SlowFallingSnapshotCaptured";
    private static final String LEGACY_PRIOR_SLOW_FALLING =
            "PriorSlowFalling";
    private static final String LEVITATION_SNAPSHOT_CAPTURED =
            "LevitationSnapshotCaptured";
    private static final String PRIOR_LEVITATION =
            "PriorLevitation";
    private static final int MAX_RECONSTRUCTED_SOURCES = 64;
    private static final Set<UUID> ACTIVE_OWNERS =
            ConcurrentHashMap.newKeySet();

    private GravityLeaseService() {}

    public static void applySnowball(
            LivingEntity target, UUID owner) {
        if (!apply(target, owner, Source.SNOWBALL)) {
            return;
        }
        addLeaseEffect(target, new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                200,
                0,
                false,
                true,
                true), PRIOR_SLOWNESS);
        addLeaseEffect(target, new MobEffectInstance(
                MobEffects.LEVITATION,
                40,
                0,
                false,
                true,
                true), PRIOR_LEVITATION);
    }

    public static void applyDomain(Entity target, UUID owner) {
        if (!apply(target, owner, Source.DOMAIN)) {
            return;
        }
        if (target instanceof LivingEntity living) {
            addLeaseEffect(living, new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    40,
                    0,
                    false,
                    false,
                    false), PRIOR_SLOWNESS);
        }
    }

    public static boolean isAffected(Entity entity) {
        CompoundTag persistent = entity.getPersistentData();
        return persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                && !persistent.getCompound(ROOT_TAG)
                        .getList(SOURCES, Tag.TAG_STRING)
                        .isEmpty();
    }

    public static void onEffectCured(LivingEntity living, Holder<MobEffect> effect) {
        CompoundTag persistent = living.getPersistentData();
        if (living.level().isClientSide()
                || !persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        String snapshotKey;
        if (effect.equals(MobEffects.MOVEMENT_SLOWDOWN)) {
            snapshotKey = PRIOR_SLOWNESS;
        } else if (effect.equals(MobEffects.SLOW_FALLING)) {
            snapshotKey = LEGACY_PRIOR_SLOW_FALLING;
        } else if (effect.equals(MobEffects.LEVITATION)) {
            snapshotKey = PRIOR_LEVITATION;
        } else {
            return;
        }
        CompoundTag state = persistent.getCompound(ROOT_TAG);
        // The lease may refresh, but a cured potion must not be restored later.
        state.remove(snapshotKey);
        state.remove(snapshotKey + "Owned");
        clearEffectClock(state, snapshotKey);
        clearEffectClock(state, snapshotKey + "Owned");
    }

    public static boolean hasActiveOwner(UUID owner) {
        return ACTIVE_OWNERS.contains(owner);
    }

    public static void markOwner(UUID owner) {
        if (owner != null) {
            ACTIVE_OWNERS.add(owner);
        }
    }

    public static int reconstructOwnerMarkers(Entity entity) {
        if (entity.level().isClientSide()) {
            return 0;
        }
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return 0;
        }
        CompoundTag state = persistent.getCompound(ROOT_TAG);
        ListTag list = state.getList(SOURCES, Tag.TAG_STRING);
        if (entity instanceof LivingEntity living) {
            migrateLegacySlowFalling(state, living, !list.isEmpty());
        }
        int reconstructed = 0;
        int boundedSize = Math.min(
                list.size(), MAX_RECONSTRUCTED_SOURCES);
        for (int index = 0; index < boundedSize; index++) {
            UUID owner = ownerFromKey(list.getString(index));
            if (owner != null && ACTIVE_OWNERS.add(owner)) {
                reconstructed++;
            }
        }
        return reconstructed;
    }

    public static void releaseOwnerSource(
            MinecraftServer server, UUID owner, Source source) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                release(entity, owner, source);
            }
        }
        if (!ownerHasAnyLease(server, owner)) {
            ACTIVE_OWNERS.remove(owner);
        }
    }

    public static void releaseEntitySource(
            Entity target, UUID owner, Source source) {
        release(target, owner, source);
    }

    public static void releaseOwner(
            MinecraftServer server, UUID owner) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                release(entity, owner, Source.SNOWBALL);
                release(entity, owner, Source.DOMAIN);
            }
        }
        ACTIVE_OWNERS.remove(owner);
    }

    public static void releaseTarget(Entity entity) {
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag state = persistent.getCompound(ROOT_TAG);
        if (entity instanceof LivingEntity living) {
            migrateLegacySlowFalling(state, living, false);
            restoreEffectSnapshot(
                    state,
                    living,
                    MobEffects.LEVITATION,
                    LEVITATION_SNAPSHOT_CAPTURED,
                    PRIOR_LEVITATION);
            restoreEffectSnapshot(
                    state,
                    living,
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SLOW_SNAPSHOT_CAPTURED,
                    PRIOR_SLOWNESS);
            living.fallDistance = 0.0F;
        }
        entity.setNoGravity(state.getBoolean(ORIGINAL_NO_GRAVITY));
        persistent.remove(ROOT_TAG);
    }

    private static boolean apply(
            Entity target, UUID owner, Source source) {
        CompoundTag persistent = target.getPersistentData();
        CompoundTag state;
        if (persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            state = persistent.getCompound(ROOT_TAG);
        } else {
            state = new CompoundTag();
            state.putBoolean(
                    ORIGINAL_NO_GRAVITY, target.isNoGravity());
        }
        Set<String> keys = sourceKeys(state);
        if (target instanceof LivingEntity living) {
            migrateLegacySlowFalling(state, living, !keys.isEmpty());
            boolean slowCaptured = captureEffectSnapshot(
                    state,
                    living,
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SLOW_SNAPSHOT_CAPTURED,
                    PRIOR_SLOWNESS);
            boolean levitationCaptured = source != Source.SNOWBALL
                    || captureEffectSnapshot(
                        state,
                        living,
                        MobEffects.LEVITATION,
                        LEVITATION_SNAPSHOT_CAPTURED,
                        PRIOR_LEVITATION);
            if (!slowCaptured || !levitationCaptured) {
                return false;
            }
        }
        keys.add(key(owner, source));
        writeKeys(state, keys);
        persistent.put(ROOT_TAG, state);
        target.setNoGravity(true);
        if (target instanceof LivingEntity living) {
            living.fallDistance = 0.0F;
        }
        ACTIVE_OWNERS.add(owner);
        return true;
    }

    private static void release(
            Entity target, UUID owner, Source source) {
        CompoundTag persistent = target.getPersistentData();
        if (!persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag state = persistent.getCompound(ROOT_TAG);
        Set<String> keys = sourceKeys(state);
        if (!keys.remove(key(owner, source))) {
            return;
        }
        if (target instanceof LivingEntity living) {
            migrateLegacySlowFalling(state, living, !keys.isEmpty());
            if (source == Source.SNOWBALL
                    && !hasSource(keys, Source.SNOWBALL)) {
                restoreEffectSnapshot(
                        state,
                        living,
                        MobEffects.LEVITATION,
                        LEVITATION_SNAPSHOT_CAPTURED,
                        PRIOR_LEVITATION);
            }
            if (keys.isEmpty()) {
                restoreEffectSnapshot(
                        state,
                        living,
                        MobEffects.MOVEMENT_SLOWDOWN,
                        SLOW_SNAPSHOT_CAPTURED,
                        PRIOR_SLOWNESS);
            }
            living.fallDistance = 0.0F;
        }
        if (keys.isEmpty()) {
            target.setNoGravity(
                    state.getBoolean(ORIGINAL_NO_GRAVITY));
            persistent.remove(ROOT_TAG);
        } else {
            writeKeys(state, keys);
            persistent.put(ROOT_TAG, state);
            target.setNoGravity(true);
        }
    }

    private static void migrateLegacySlowFalling(
            CompoundTag state,
            LivingEntity living,
            boolean replaceActiveEffect) {
        if (!state.getBoolean(LEGACY_SLOW_SNAPSHOT_CAPTURED)) {
            return;
        }
        if (state.contains(LEGACY_PRIOR_SLOW_FALLING)
                && decodeEffectSnapshot(
                        state, LEGACY_PRIOR_SLOW_FALLING).isEmpty()) {
            return;
        }

        MobEffectInstance current = living.getEffect(MobEffects.SLOW_FALLING);
        MobEffectInstance replacement = null;
        // Old snapshots encode Slow Falling itself; only its proven owned tail is portable.
        if (replaceActiveEffect
                && living.isAlive()
                && ownsEffect(state, living, current, LEGACY_PRIOR_SLOW_FALLING)
                && current.getDuration() > 0) {
            if (!captureEffectSnapshot(
                    state,
                    living,
                    MobEffects.MOVEMENT_SLOWDOWN,
                    SLOW_SNAPSHOT_CAPTURED,
                    PRIOR_SLOWNESS)) {
                return;
            }
            replacement = new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    current.getDuration(),
                    current.getAmplifier(),
                    current.isAmbient(),
                    current.isVisible(),
                    current.showIcon());
        }
        restoreEffectSnapshot(
                state,
                living,
                MobEffects.SLOW_FALLING,
                LEGACY_SLOW_SNAPSHOT_CAPTURED,
                LEGACY_PRIOR_SLOW_FALLING);
        if (replacement != null) {
            addLeaseEffect(living, replacement, PRIOR_SLOWNESS);
        }
    }

    private static boolean captureEffectSnapshot(
            CompoundTag state,
            LivingEntity living,
            Holder<MobEffect> effect,
            String capturedKey,
            String snapshotKey) {
        if (state.getBoolean(capturedKey)) {
            return true;
        }
        MobEffectInstance prior = living.getEffect(effect);
        writeEffectClock(state, living, snapshotKey);
        if (prior == null) {
            state.putBoolean(capturedKey, true);
            return true;
        }
        Optional<Tag> encoded = MobEffectInstance.CODEC
                .encodeStart(
                        NbtOps.INSTANCE,
                        prior)
                .result();
        if (encoded.isEmpty()) {
            return false;
        }
        state.put(snapshotKey, encoded.get());
        state.putBoolean(capturedKey, true);
        return true;
    }

    private static void restoreEffectSnapshot(
            CompoundTag state,
            LivingEntity living,
            Holder<MobEffect> effect,
            String capturedKey,
            String snapshotKey) {
        if (!state.getBoolean(capturedKey)) {
            return;
        }
        Optional<MobEffectInstance> prior =
                decodeEffectSnapshot(state, snapshotKey);
        if (state.contains(snapshotKey) && prior.isEmpty()) {
            return;
        }
        MobEffectInstance current = living.getEffect(effect);
        if (ownsEffect(state, living, current, snapshotKey)) {
            living.removeEffect(effect);
            prior.map(value -> remainingEffect(
                            value, effectElapsed(state, living, snapshotKey)))
                    .ifPresent(living::addEffect);
        }
        state.remove(snapshotKey);
        state.remove(capturedKey);
        state.remove(snapshotKey + "Owned");
        clearEffectClock(state, snapshotKey);
        clearEffectClock(state, snapshotKey + "Owned");
    }

    private static void addLeaseEffect(
            LivingEntity living, MobEffectInstance lease, String snapshotKey) {
        CompoundTag state = living.getPersistentData().getCompound(ROOT_TAG);
        MobEffectInstance current = living.getEffect(lease.getEffect());
        // Vanilla can mutate a stronger effect's flags without accepting our amplifier.
        MobEffectInstance beforeAdd = current != null ? new MobEffectInstance(current) : null;
        boolean alreadyOwned = ownsEffect(state, living, current, snapshotKey);
        if (current != null && !alreadyOwned) {
            // A later potion may replace our effect before the next domain refresh.
            MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, current)
                    .result().ifPresent(value -> state.put(snapshotKey, value));
            writeEffectClock(state, living, snapshotKey);
        }
        living.addEffect(lease);
        current = living.getEffect(lease.getEffect());
        boolean changedByLease = current != null && beforeAdd != null
                && (current.getDuration() != beforeAdd.getDuration()
                        || !sameEffectFlags(current, beforeAdd));
        if (current != null
                && (alreadyOwned || changedByLease || current.getDuration() == lease.getDuration()
                        && sameEffectFlags(current, lease))) {
            MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, current)
                    .result().ifPresent(value -> state.put(snapshotKey + "Owned", value));
            writeEffectClock(state, living, snapshotKey + "Owned");
        } else {
            state.remove(snapshotKey + "Owned");
            clearEffectClock(state, snapshotKey + "Owned");
        }
    }

    private static boolean ownsEffect(
            CompoundTag state, LivingEntity living,
            MobEffectInstance current, String snapshotKey) {
        Optional<MobEffectInstance> installed =
                decodeEffectSnapshot(state, snapshotKey + "Owned");
        if (current == null || current.isInfiniteDuration() || installed.isEmpty()
                || !sameEffectFlags(current, installed.get())) {
            return false;
        }
        long expected = Math.max(0L, (long) installed.get().getDuration()
                - effectElapsed(state, living, snapshotKey + "Owned"));
        return Math.abs((long) current.getDuration() - expected) <= 2L;
    }

    private static boolean sameEffectFlags(
            MobEffectInstance first, MobEffectInstance second) {
        return first.getEffect().equals(second.getEffect())
                && first.getAmplifier() == second.getAmplifier()
                && first.isAmbient() == second.isAmbient()
                && first.isVisible() == second.isVisible()
                && first.showIcon() == second.showIcon()
                && first.getCures().equals(second.getCures());
    }

    private static void writeEffectClock(
            CompoundTag state, LivingEntity living, String key) {
        state.putLong(key + "GameTime", living.level().getGameTime());
        state.putInt(key + "EntityId", living.getId());
        state.putInt(key + "EntityTicks", living.tickCount);
    }

    private static long effectElapsed(
            CompoundTag state, LivingEntity living, String key) {
        if (state.getInt(key + "EntityId") == living.getId()
                && state.contains(key + "EntityTicks", Tag.TAG_INT)
                && living.tickCount >= state.getInt(key + "EntityTicks")) {
            return (long) living.tickCount - state.getInt(key + "EntityTicks");
        }
        return state.contains(key + "GameTime", Tag.TAG_LONG)
                ? Math.max(0L, living.level().getGameTime() - state.getLong(key + "GameTime"))
                : 0L;
    }

    private static void clearEffectClock(CompoundTag state, String key) {
        state.remove(key + "GameTime");
        state.remove(key + "EntityId");
        state.remove(key + "EntityTicks");
    }

    private static MobEffectInstance remainingEffect(
            MobEffectInstance effect, long elapsed) {
        if (effect.hiddenEffect != null) {
            effect.hiddenEffect = remainingEffect(effect.hiddenEffect, elapsed);
        }
        if (!effect.isInfiniteDuration()) {
            effect.duration = (int) Math.max(0L, (long) effect.getDuration() - elapsed);
        }
        return effect.getDuration() == 0 ? effect.hiddenEffect : effect;
    }

    private static Optional<MobEffectInstance> decodeEffectSnapshot(
            CompoundTag state, String snapshotKey) {
        if (!state.contains(snapshotKey)) {
            return Optional.empty();
        }
        return MobEffectInstance.CODEC
                .decode(NbtOps.INSTANCE, state.get(snapshotKey))
                .result()
                .map(Pair::getFirst);
    }

    private static boolean ownerHasAnyLease(
            MinecraftServer server, UUID owner) {
        String prefix = owner + "|";
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                CompoundTag persistent = entity.getPersistentData();
                if (!persistent.contains(
                        ROOT_TAG, Tag.TAG_COMPOUND)) {
                    continue;
                }
                if (sourceKeys(persistent.getCompound(ROOT_TAG))
                        .stream()
                        .anyMatch(value -> value.startsWith(prefix))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasSource(
            Set<String> keys, Source source) {
        String suffix = "|" + source.name();
        return keys.stream().anyMatch(value -> value.endsWith(suffix));
    }

    private static String key(UUID owner, Source source) {
        return owner + "|" + source.name();
    }

    private static UUID ownerFromKey(String key) {
        int separator = key.lastIndexOf('|');
        if (separator <= 0 || separator >= key.length() - 1) {
            return null;
        }
        try {
            Source.valueOf(key.substring(separator + 1));
            return UUID.fromString(key.substring(0, separator));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Set<String> sourceKeys(CompoundTag state) {
        ListTag list = state.getList(SOURCES, Tag.TAG_STRING);
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            keys.add(list.getString(index));
        }
        return keys;
    }

    private static void writeKeys(
            CompoundTag state, Set<String> keys) {
        ListTag list = new ListTag();
        keys.stream()
                .sorted()
                .map(StringTag::valueOf)
                .forEach(list::add);
        state.put(SOURCES, list);
    }
}
