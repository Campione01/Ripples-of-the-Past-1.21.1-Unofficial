package rotp.addons.scarymonsters.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class ScaryMonstersOwnedEffects {
    private static final String IDENTITY_TAG = "scary_monsters:effect_identity";
    private static final String HIDDEN_TAG = "hidden_effect";

    private ScaryMonstersOwnedEffects() {}

    public static UUID identity(MobEffectInstance effect) {
        return effect instanceof ScaryMonstersEffectIdentityAccess access
                ? access.rotp_sm$getEffectIdentity() : null;
    }

    public static void setIdentity(MobEffectInstance effect, UUID identity) {
        if (!(effect instanceof ScaryMonstersEffectIdentityAccess access)) {
            throw new IllegalStateException("Scary Monsters effect identity mixin is not applied");
        }
        access.rotp_sm$setEffectIdentity(identity);
    }

    public static void ensureIdentities(MobEffectInstance effect) {
        for (MobEffectInstance node = effect; node != null; node = node.hiddenEffect) {
            if (identity(node) == null) {
                setIdentity(node, UUID.randomUUID());
            }
        }
    }

    public static void writeIdentities(MobEffectInstance effect, CompoundTag tag) {
        MobEffectInstance node = effect;
        CompoundTag details = tag;
        while (node != null) {
            UUID identity = identity(node);
            if (identity != null) details.putUUID(IDENTITY_TAG, identity);
            else details.remove(IDENTITY_TAG);
            node = node.hiddenEffect;
            if (node == null || !details.contains(HIDDEN_TAG, Tag.TAG_COMPOUND)) return;
            details = details.getCompound(HIDDEN_TAG);
        }
    }

    public static void readIdentities(MobEffectInstance effect, CompoundTag tag) {
        MobEffectInstance node = effect;
        CompoundTag details = tag;
        while (node != null) {
            setIdentity(node, details.hasUUID(IDENTITY_TAG) ? details.getUUID(IDENTITY_TAG) : null);
            node = node.hiddenEffect;
            if (node == null || !details.contains(HIDDEN_TAG, Tag.TAG_COMPOUND)) return;
            details = details.getCompound(HIDDEN_TAG);
        }
    }

    public static CompoundTag encode(MobEffectInstance effect) {
        if (effect == null) return null;
        CompoundTag tag = MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, effect).result()
                .filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast).orElse(null);
        if (tag != null) writeIdentities(effect, tag);
        return tag;
    }

    public static MobEffectInstance decode(CompoundTag tag) {
        if (tag == null) return null;
        MobEffectInstance effect = MobEffectInstance.CODEC.decode(NbtOps.INSTANCE, tag).result()
                .map(Pair::getFirst).orElse(null);
        if (effect != null) readIdentities(effect, tag);
        return effect;
    }

    public static MobEffectInstance restoreSnapshot(
            MobEffectInstance snapshot, UUID installedIdentity, MobEffectInstance previous) {
        CompoundTag current = encode(snapshot);
        if (installedIdentity == null || !containsIdentity(current, installedIdentity)) return snapshot;
        return decode(removeAndMerge(current, installedIdentity, encode(previous)));
    }

    public static void restore(
            LivingEntity entity, Holder<MobEffect> effectType,
            UUID installedIdentity, MobEffectInstance previous) {
        MobEffectInstance current = entity.getEffect(effectType);
        if (current == null || installedIdentity == null) return;
        ensureIdentities(current);
        CompoundTag encoded = encode(current);
        if (!containsIdentity(encoded, installedIdentity)) return;
        MobEffectInstance restored = decode(removeAndMerge(encoded, installedIdentity, encode(previous)));
        if (restored != null && identity(current).equals(identity(restored))) {
            // The external root keeps its duration, flags, cures and attribute modifiers.
            current.hiddenEffect = restored.hiddenEffect;
        }
        else {
            entity.removeEffect(effectType);
            if (restored != null) entity.addEffect(restored);
        }
    }

    private static boolean containsIdentity(CompoundTag tag, UUID identity) {
        for (CompoundTag node = tag; node != null; node = hidden(node)) {
            if (node.hasUUID(IDENTITY_TAG) && identity.equals(node.getUUID(IDENTITY_TAG))) return true;
        }
        return false;
    }

    private static CompoundTag hidden(CompoundTag tag) {
        return tag.contains(HIDDEN_TAG, Tag.TAG_COMPOUND) ? tag.getCompound(HIDDEN_TAG) : null;
    }

    static CompoundTag removeAndMerge(
            CompoundTag current, UUID installedIdentity, CompoundTag previous) {
        Map<UUID, EffectNode> byIdentity = new LinkedHashMap<>();
        collect(current, installedIdentity, true, byIdentity);
        collect(previous, installedIdentity, false, byIdentity);
        List<EffectNode> nodes = new ArrayList<>(byIdentity.values());
        nodes.sort(Comparator.comparingInt((EffectNode node) -> node.details.getInt("amplifier")).reversed());

        Map<Integer, EffectNode> byAmplifier = new LinkedHashMap<>();
        for (EffectNode node : nodes) {
            int amplifier = node.details.getInt("amplifier");
            EffectNode existing = byAmplifier.get(amplifier);
            if (existing == null || !existing.live && (node.live || lifetime(node) > lifetime(existing))) {
                byAmplifier.put(amplifier, node);
            }
        }
        List<EffectNode> merged = new ArrayList<>();
        long higherLifetime = 0L;
        for (EffectNode node : byAmplifier.values()) {
            long lifetime = lifetime(node);
            // A weaker prior that expires before a stronger live effect cannot resume.
            if (!node.live && lifetime <= higherLifetime) continue;
            merged.add(node);
            higherLifetime = Math.max(higherLifetime, lifetime);
        }
        CompoundTag result = null;
        for (int i = merged.size() - 1; i >= 0; i--) {
            CompoundTag details = merged.get(i).details.copy();
            if (result != null) details.put(HIDDEN_TAG, result);
            result = details;
        }
        if (result != null) result.put("id", current.get("id").copy());
        return result;
    }

    private static void collect(
            CompoundTag chain, UUID installedIdentity, boolean live, Map<UUID, EffectNode> nodes) {
        for (CompoundTag node = chain; node != null; node = hidden(node)) {
            int duration = node.getInt("duration");
            if (duration != MobEffectInstance.INFINITE_DURATION && duration <= 0) continue;
            UUID identity = node.hasUUID(IDENTITY_TAG) ? node.getUUID(IDENTITY_TAG) : UUID.randomUUID();
            if (identity.equals(installedIdentity)) continue;
            CompoundTag details = node.copy();
            details.remove("id");
            details.remove(HIDDEN_TAG);
            details.putUUID(IDENTITY_TAG, identity);
            nodes.putIfAbsent(identity, new EffectNode(details, live));
        }
    }

    private static long lifetime(EffectNode node) {
        int duration = node.details.getInt("duration");
        return duration == MobEffectInstance.INFINITE_DURATION ? Long.MAX_VALUE : duration;
    }

    private record EffectNode(CompoundTag details, boolean live) {}
}
