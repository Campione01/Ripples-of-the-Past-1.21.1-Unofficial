package rotp.addons.scarymonsters.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ScaryMonstersState
        implements INBTSerializable<CompoundTag> {
    private boolean dynamicVision;
    private boolean dragonForm;
    private boolean dinosaurForm;
    private boolean blocking;
    private UUID controlledDinosaur;
    private UUID possessedDinosaur;
    private final Map<String, List<OwnedEffectLayer>> ownedEffects =
            new LinkedHashMap<>();

    public boolean dynamicVision() { return dynamicVision; }
    public boolean dragonForm() { return dragonForm; }
    public boolean dinosaurForm() { return dinosaurForm; }
    public UUID controlledDinosaur() { return controlledDinosaur; }
    public UUID possessedDinosaur() { return possessedDinosaur; }
    public boolean blocking() { return blocking; }

    public void setDynamicVision(boolean value) { dynamicVision = value; }
    public void setDragonForm(boolean value) { dragonForm = value; }
    public void setDinosaurForm(boolean value) { dinosaurForm = value; }
    public void setControlledDinosaur(UUID value) {
        controlledDinosaur = value;
    }
    public void setPossessedDinosaur(UUID value) { possessedDinosaur = value; }
    public void setBlocking(boolean value) { blocking = value; }

    public boolean applyOwnedEffect(
            String facet,
            LivingEntity entity,
            MobEffectInstance effect) {
        Holder<MobEffect> effectType = effect.getEffect();
        String effectId = effectId(effectType);
        List<OwnedEffectLayer> layers = ownedEffects.computeIfAbsent(
                effectId, ignored -> new ArrayList<>());
        if (layers.stream().anyMatch(layer -> layer.facet.equals(facet))) {
            return false;
        }
        MobEffectInstance previous = entity.getEffect(effectType);
        ScaryMonstersOwnedEffects.ensureIdentities(previous);
        UUID installedIdentity = UUID.randomUUID();
        ScaryMonstersOwnedEffects.setIdentity(effect, installedIdentity);
        layers.add(new OwnedEffectLayer(
                facet,
                effect.getAmplifier(),
                installedIdentity,
                copy(previous, 0L)));
        entity.addEffect(effect);
        return true;
    }

    public void tickOwnedEffects() {
        ownedEffects.values().forEach(layers -> {
            for (OwnedEffectLayer layer : layers) {
                if (layer.previousElapsedTicks < Long.MAX_VALUE) {
                    layer.previousElapsedTicks++;
                }
            }
        });
    }

    public boolean restoreOwnedEffect(
            String facet,
            LivingEntity entity,
            Holder<MobEffect> effectType) {
        String effectId = effectId(effectType);
        List<OwnedEffectLayer> layers = ownedEffects.get(effectId);
        if (layers == null) {
            return false;
        }
        int index = -1;
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).facet.equals(facet)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return false;
        }
        OwnedEffectLayer removed = layers.get(index);
        MobEffectInstance previous = copy(
                removed.previous, removed.previousElapsedTicks);
        // Old records have no provable node identity; never guess their hidden ownership.
        if (removed.installedIdentity != null) {
            if (index + 1 < layers.size()) {
                OwnedEffectLayer next = layers.get(index + 1);
                next.previous = ScaryMonstersOwnedEffects.restoreSnapshot(
                        copy(next.previous, next.previousElapsedTicks),
                        removed.installedIdentity, previous);
                next.previousElapsedTicks = 0L;
            }
            ScaryMonstersOwnedEffects.restore(
                    entity, effectType, removed.installedIdentity, previous);
        }
        layers.remove(index);
        if (layers.isEmpty()) {
            ownedEffects.remove(effectId);
        }
        return true;
    }

    public boolean ownsFacet(String facet) {
        return ownedEffects.values().stream().flatMap(List::stream)
                .anyMatch(layer -> layer.facet.equals(facet));
    }

    public boolean clearTransient() {
        boolean changed = dynamicVision || blocking;
        dynamicVision = false;
        blocking = false;
        return changed;
    }

    public boolean clearAll() {
        return clearAllExceptFacet(null);
    }

    public boolean clearAllExceptFacet(String retainedFacet) {
        boolean changed = dynamicVision || dragonForm
                || dinosaurForm || controlledDinosaur != null;
        dynamicVision = false;
        blocking = false;
        dragonForm = false;
        dinosaurForm = false;
        controlledDinosaur = null;
        // Forced possession has its own terminal/release lifecycle, independent of the user's forms.
        if (retainedFacet == null) {
            ownedEffects.clear();
        } else {
            ownedEffects.values().forEach(layers ->
                    layers.removeIf(layer -> !retainedFacet.equals(layer.facet)));
            ownedEffects.values().removeIf(List::isEmpty);
        }
        return changed;
    }

    public void applyClientSnapshot(
            boolean vision,
            boolean dragon,
            boolean dinosaur,
            UUID controlled) {
        dynamicVision = vision;
        dragonForm = dragon;
        dinosaurForm = dinosaur;
        controlledDinosaur = controlled;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("DynamicVision", dynamicVision);
        tag.putBoolean("DragonForm", dragonForm);
        tag.putBoolean("DinosaurForm", dinosaurForm);
        if (controlledDinosaur != null) {
            tag.putUUID("ControlledDinosaur", controlledDinosaur);
        }
        if (possessedDinosaur != null) {
            tag.putUUID("PossessedDinosaur", possessedDinosaur);
        }
        ListTag owned = new ListTag();
        ownedEffects.forEach((effectId, layers) -> {
            for (OwnedEffectLayer layer : layers) {
                CompoundTag layerTag = new CompoundTag();
                layerTag.putString("Effect", effectId);
                layerTag.putString("Facet", layer.facet);
                layerTag.putInt("Amplifier", layer.installedAmplifier);
                if (layer.installedIdentity != null) {
                    layerTag.putUUID("InstalledIdentity", layer.installedIdentity);
                }
                CompoundTag previous = encodeAgedEffect(
                        layer.previous, layer.previousElapsedTicks);
                if (previous != null) {
                    layerTag.put("Previous", previous);
                }
                owned.add(layerTag);
            }
        });
        if (!owned.isEmpty()) {
            tag.put("OwnedEffects", owned);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        dynamicVision = tag.getBoolean("DynamicVision");
        dragonForm = tag.getBoolean("DragonForm");
        dinosaurForm = tag.getBoolean("DinosaurForm");
        controlledDinosaur = tag.hasUUID("ControlledDinosaur")
                ? tag.getUUID("ControlledDinosaur") : null;
        possessedDinosaur = tag.hasUUID("PossessedDinosaur")
                ? tag.getUUID("PossessedDinosaur") : null;
        ownedEffects.clear();
        if (tag.contains("OwnedEffects", Tag.TAG_LIST)) {
            ListTag owned = tag.getList(
                    "OwnedEffects", Tag.TAG_COMPOUND);
            for (Tag raw : owned) {
                CompoundTag layerTag = (CompoundTag) raw;
                MobEffectInstance previous = layerTag.contains("Previous", Tag.TAG_COMPOUND)
                        ? ScaryMonstersOwnedEffects.decode(layerTag.getCompound("Previous"))
                        : null;
                ownedEffects.computeIfAbsent(
                                layerTag.getString("Effect"),
                                ignored -> new ArrayList<>())
                        .add(new OwnedEffectLayer(
                                layerTag.getString("Facet"),
                                layerTag.getInt("Amplifier"),
                                layerTag.hasUUID("InstalledIdentity")
                                        ? layerTag.getUUID("InstalledIdentity") : null,
                                previous));
            }
        }
    }

    private static String effectId(Holder<MobEffect> effect) {
        return effect.unwrapKey().map(key -> key.location().toString())
                .orElseGet(() -> BuiltInRegistries.MOB_EFFECT
                        .getKey(effect.value()).toString());
    }

    private static MobEffectInstance copy(
            MobEffectInstance effect, long elapsedTicks) {
        CompoundTag tag = encodeAgedEffect(effect, elapsedTicks);
        return ScaryMonstersOwnedEffects.decode(tag);
    }

    private static CompoundTag encodeAgedEffect(
            MobEffectInstance effect, long elapsedTicks) {
        if (effect == null) return null;
        CompoundTag encoded = ScaryMonstersOwnedEffects.encode(effect);
        if (encoded == null) return null;
        CompoundTag aged = ageEffectDetails(encoded, elapsedTicks);
        if (aged != null) {
            aged.put("id", encoded.get("id").copy());
        }
        return aged;
    }

    // Age the full CODEC chain without running periodic effect callbacks.
    private static CompoundTag ageEffectDetails(
            CompoundTag details, long elapsedTicks) {
        CompoundTag aged = details.copy();
        CompoundTag hidden = details.contains("hidden_effect", Tag.TAG_COMPOUND)
                ? ageEffectDetails(details.getCompound("hidden_effect"), elapsedTicks)
                : null;
        if (hidden != null) {
            aged.put("hidden_effect", hidden);
        }
        else {
            aged.remove("hidden_effect");
        }
        int duration = details.getInt("duration");
        if (duration == MobEffectInstance.INFINITE_DURATION) return aged;
        if (duration <= 0 || elapsedTicks >= duration) return hidden;
        aged.putInt("duration", duration - (int) elapsedTicks);
        return aged;
    }

    private static final class OwnedEffectLayer {
        private final String facet;
        private final int installedAmplifier;
        private final UUID installedIdentity;
        private MobEffectInstance previous;
        private long previousElapsedTicks;

        private OwnedEffectLayer(
                String facet,
                int installedAmplifier,
                UUID installedIdentity,
                MobEffectInstance previous) {
            this.facet = facet;
            this.installedAmplifier = installedAmplifier;
            this.installedIdentity = installedIdentity;
            this.previous = previous;
        }
    }
}
