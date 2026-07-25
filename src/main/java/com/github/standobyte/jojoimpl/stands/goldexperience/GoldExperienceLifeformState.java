package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.entityattachment.SynchronizablePlayerData;
import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.network.s2c.TrGELifeformStatePacket;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntitySubtype;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GoldExperienceLifeformState implements TickingEntityData, SynchronizablePlayerData, INBTSerializable<CompoundTag> {
    private static final List<String> LEGACY_INDEX_IDS = List.of(
            "minecraft:frog",
            "minecraft:bee",
            "minecraft:rabbit",
            "minecraft:fox",
            "minecraft:wolf",
            "minecraft:cat",
            "minecraft:parrot");
    private static final Map<String, String> LEGACY_CHOICE_IDS = Map.of(
            "frog", "minecraft:frog",
            "bee", "minecraft:bee",
            "rabbit", "minecraft:rabbit",
            "fox", "minecraft:fox",
            "wolf", "minecraft:wolf",
            "cat", "minecraft:cat",
            "parrot", "minecraft:parrot");

    private final LivingEntity entity;
    private final Set<String> metLifeformIds = new LinkedHashSet<>();
    private final Set<String> favoriteLifeformIds = new LinkedHashSet<>();
    private final Set<String> newUnseenLifeformIds = new LinkedHashSet<>();
    @Nullable private String selectedLifeformId;
    private int animalAgeCooldown;

    public GoldExperienceLifeformState(LivingEntity entity) {
        this.entity = entity;
        addTicking(entity);
        if (entity instanceof Player) {
            addSynchronization(entity);
        }
    }

    public static GoldExperienceLifeformState get(LivingEntity user) {
        return user.getData(ModDataAttachmentTypes.GE_LIFEFORM_STATE);
    }

    public List<EntitySubtype<?>> visibleLifeforms(Level level) {
        return GoldExperienceLifeforms.knownValidLifeforms(level, metLifeformIds);
    }

    public void cycleForward(Level level) {
        setSelected(nextMetId(level, selectedOrFirstMetId(level), 1));
    }

    public void cycleBackward(Level level) {
        setSelected(nextMetId(level, selectedOrFirstMetId(level), -1));
    }

    public void setSelected(String lifeformId) {
        String normalized = normalizeLifeformId(lifeformId);
        if (normalized == null || !hasMetLifeform(normalized) || !GoldExperienceLifeforms.isKnownSubtypeId(normalized)) {
            return;
        }
        if (!normalized.equals(selectedLifeformId)) {
            selectedLifeformId = normalized;
            syncStateToOwner();
        }
    }

    public void setSelectedFromSync(@Nullable String lifeformId) {
        selectedLifeformId = normalizeLifeformId(lifeformId);
        if (selectedLifeformId != null && !GoldExperienceLifeforms.isKnownSubtypeId(selectedLifeformId)) {
            selectedLifeformId = null;
        }
    }

    @Nullable
    public String selectedLifeformId() {
        return selectedLifeformId;
    }

    public String selectedLifeformIdForSync() {
        return selectedLifeformId != null ? selectedLifeformId : "";
    }

    public Optional<EntitySubtype<?>> selectedLifeformSubtype(Level level) {
        String selectedId = selectedOrFirstMetId(level);
        if (selectedId == null || !hasMetLifeform(selectedId)) {
            return Optional.empty();
        }
        return GoldExperienceLifeforms.subtypeFromId(selectedId)
                .filter(subtype -> GoldExperienceLifeforms.isValidLifeform(subtype, level));
    }

    @Nullable
    public String selectedOrFirstMetId(Level level) {
        if (selectedLifeformId != null
                && hasMetLifeform(selectedLifeformId)
                && GoldExperienceLifeforms.isValidLifeformId(selectedLifeformId, level)) {
            return selectedLifeformId;
        }
        return visibleLifeforms(level).stream()
                .map(subtype -> subtype.getId().toString())
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public String nextMetId(Level level, @Nullable String fromId, int direction) {
        List<EntitySubtype<?>> visible = visibleLifeforms(level);
        if (visible.isEmpty()) {
            return selectedLifeformId;
        }
        int fromIndex = 0;
        if (fromId != null) {
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i).getId().toString().equals(fromId)) {
                    fromIndex = i;
                    break;
                }
            }
        }
        int step = direction >= 0 ? 1 : -1;
        int nextIndex = Math.floorMod(fromIndex + step, visible.size());
        return visible.get(nextIndex).getId().toString();
    }

    public boolean hasMetLifeform(String lifeformId) {
        String normalized = normalizeLifeformId(lifeformId);
        return normalized != null && metLifeformIds.contains(normalized);
    }

    public boolean hasAnyMetLifeforms() {
        return !metLifeformIds.isEmpty();
    }

    public boolean learnLifeformsForEntity(Entity seenEntity, Level level) {
        GoldExperienceLifeforms.ensureExtraEntitySubtypesRegistered();
        List<String> subtypeIds = EntitySubtype.getMatchingSubtypes(seenEntity)
                .filter(subtype -> GoldExperienceLifeforms.isValidLifeform(subtype, level))
                .map(subtype -> subtype.getId().toString())
                .toList();
        boolean addedAny = false;
        for (String subtypeId : subtypeIds) {
            addedAny |= learnLifeformId(subtypeId, false);
        }
        if (addedAny) {
            if (selectedLifeformId == null) {
                selectedLifeformId = subtypeIds.stream().filter(this::hasMetLifeform).findFirst().orElse(null);
            }
            syncStateToOwner();
        }
        return addedAny;
    }

    public boolean learnLifeformId(String lifeformId) {
        boolean added = learnLifeformId(lifeformId, true);
        if (added) {
            syncStateToOwner();
        }
        return added;
    }

    public boolean learnAllValidLifeforms(Level level) {
        boolean addedAny = false;
        for (EntitySubtype<?> subtype : GoldExperienceLifeforms.validLifeforms(level)) {
            addedAny |= learnLifeformId(subtype.getId().toString(), false);
        }
        if (addedAny) {
            syncStateToOwner();
        }
        return addedAny;
    }

    private boolean learnLifeformId(String lifeformId, boolean sync) {
        String normalized = normalizeLifeformId(lifeformId);
        if (normalized == null || !GoldExperienceLifeforms.isKnownSubtypeId(normalized)) {
            return false;
        }
        boolean added = metLifeformIds.add(normalized);
        if (added) {
            newUnseenLifeformIds.add(normalized);
            if (selectedLifeformId == null) {
                selectedLifeformId = normalized;
            }
            if (sync) {
                syncStateToOwner();
            }
        }
        return added;
    }

    public void learnLifeformIdFromSync(String lifeformId) {
        String normalized = normalizeLifeformId(lifeformId);
        if (normalized != null && GoldExperienceLifeforms.isKnownSubtypeId(normalized)) {
            metLifeformIds.add(normalized);
            if (selectedLifeformId == null) {
                selectedLifeformId = normalized;
            }
        }
    }

    public boolean isFavorite(String lifeformId) {
        String normalized = normalizeLifeformId(lifeformId);
        return normalized != null && favoriteLifeformIds.contains(normalized);
    }

    public boolean addFavoriteLifeform(String lifeformId) {
        String normalized = normalizeLifeformId(lifeformId);
        if (normalized == null || !hasMetLifeform(normalized) || !GoldExperienceLifeforms.isKnownSubtypeId(normalized)) {
            return false;
        }
        boolean added = favoriteLifeformIds.add(normalized);
        if (added) {
            syncStateToOwner();
        }
        return added;
    }

    public boolean removeFavoriteLifeform(String lifeformId) {
        String normalized = normalizeLifeformId(lifeformId);
        boolean removed = normalized != null && favoriteLifeformIds.remove(normalized);
        if (removed) {
            syncStateToOwner();
        }
        return removed;
    }

    public void setFavoriteFromSync(String lifeformId, boolean favorite) {
        String normalized = normalizeLifeformId(lifeformId);
        if (normalized == null || !GoldExperienceLifeforms.isKnownSubtypeId(normalized)) {
            return;
        }
        if (favorite) {
            favoriteLifeformIds.add(normalized);
        }
        else {
            favoriteLifeformIds.remove(normalized);
        }
    }

    public void setFavoriteLifeformsFromSync(Collection<String> lifeformIds) {
        favoriteLifeformIds.clear();
        lifeformIds.forEach(lifeformId -> setFavoriteFromSync(lifeformId, true));
    }

    public List<String> favoriteLifeformIdsForSync() {
        return List.copyOf(favoriteLifeformIds);
    }

    public boolean isNewUnseen(String lifeformId) {
        String normalized = normalizeLifeformId(lifeformId);
        return normalized != null && newUnseenLifeformIds.contains(normalized);
    }

    public boolean hasAnyNewUnseenLifeforms() {
        return !newUnseenLifeformIds.isEmpty();
    }

    public void clearNewUnseenLifeforms() {
        if (!newUnseenLifeformIds.isEmpty()) {
            newUnseenLifeformIds.clear();
            syncStateToOwner();
        }
    }

    public void clearNewUnseenLifeformsFromSync() {
        newUnseenLifeformIds.clear();
    }

    public void setNewUnseenLifeformsFromSync(Collection<String> lifeformIds) {
        newUnseenLifeformIds.clear();
        lifeformIds.stream()
                .map(GoldExperienceLifeformState::normalizeLifeformId)
                .filter(id -> id != null && GoldExperienceLifeforms.isKnownSubtypeId(id))
                .forEach(newUnseenLifeformIds::add);
    }

    public List<String> newUnseenLifeformIdsForSync() {
        return List.copyOf(newUnseenLifeformIds);
    }

    public void setMetLifeformsFromSync(Collection<String> lifeformIds) {
        metLifeformIds.clear();
        lifeformIds.forEach(this::learnLifeformIdFromSync);
        if (selectedLifeformId != null && !hasMetLifeform(selectedLifeformId)) {
            selectedLifeformId = null;
        }
    }

    public List<String> metLifeformIdsForSync() {
        return List.copyOf(metLifeformIds);
    }

    public int addAnimalAgeCooldown(int ticks) {
        animalAgeCooldown += ticks;
        return animalAgeCooldown;
    }

    @Override
    public void tick() {
        if (!entity.level().isClientSide() && animalAgeCooldown > 0) {
            --animalAgeCooldown;
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (selectedLifeformId != null) {
            tag.putString("ChosenType", selectedLifeformId);
        }
        tag.put("MetLifeforms", stringList(metLifeformIds));
        tag.put("FavoritesMobs", stringList(favoriteLifeformIds));
        tag.put("NewMobs", stringList(newUnseenLifeformIds));
        tag.putInt("AnimalAgeCd", animalAgeCooldown);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        metLifeformIds.clear();
        favoriteLifeformIds.clear();
        newUnseenLifeformIds.clear();
        selectedLifeformId = null;
        animalAgeCooldown = tag.getInt("AnimalAgeCd");

        if (tag.contains("MetLifeforms", Tag.TAG_LIST)) {
            ListTag metLifeforms = tag.getList("MetLifeforms", Tag.TAG_STRING);
            metLifeforms.forEach(lifeformId -> learnLifeformIdFromSync(lifeformId.getAsString()));
        }
        if (tag.contains("FavoritesMobs", Tag.TAG_LIST)) {
            ListTag favoriteLifeforms = tag.getList("FavoritesMobs", Tag.TAG_STRING);
            favoriteLifeforms.forEach(lifeformId -> setFavoriteFromSync(lifeformId.getAsString(), true));
        }
        if (tag.contains("NewMobs", Tag.TAG_LIST)) {
            ListTag newUnseenLifeforms = tag.getList("NewMobs", Tag.TAG_STRING);
            newUnseenLifeforms.forEach(lifeformId -> {
                String normalized = normalizeLifeformId(lifeformId.getAsString());
                if (normalized != null && GoldExperienceLifeforms.isKnownSubtypeId(normalized)) {
                    newUnseenLifeformIds.add(normalized);
                }
            });
        }

        if (tag.contains("ChosenType", Tag.TAG_STRING)) {
            setSelectedFromSync(tag.getString("ChosenType"));
        }
        else if (tag.contains("SelectedType", Tag.TAG_STRING)) {
            setSelectedFromSync(tag.getString("SelectedType"));
        }
        else if (tag.contains("SelectedIndex", Tag.TAG_INT)) {
            int legacyIndex = Math.floorMod(tag.getInt("SelectedIndex"), LEGACY_INDEX_IDS.size());
            setSelectedFromSync(LEGACY_INDEX_IDS.get(legacyIndex));
        }
        if (selectedLifeformId != null && !hasMetLifeform(selectedLifeformId)) {
            learnLifeformIdFromSync(selectedLifeformId);
        }
    }

    @Override
    public void syncToPlayer(ServerPlayer entityAsPlayer) {
        PacketDistributor.sendToPlayer(entityAsPlayer,
                new TrGELifeformStatePacket(entity.getId(), selectedLifeformIdForSync(), metLifeformIdsForSync(),
                        favoriteLifeformIdsForSync(), newUnseenLifeformIdsForSync()));
    }

    @Override
    public void syncToTracking(ServerPlayer trackingPlayer) {
    }

    @Override
    public void onPlayerClone(Player newPlayer, boolean wasDeath) {
        GoldExperienceLifeformState newState = GoldExperienceLifeformState.get(newPlayer);
        newState.selectedLifeformId = selectedLifeformId;
        newState.metLifeformIds.clear();
        newState.metLifeformIds.addAll(metLifeformIds);
        newState.favoriteLifeformIds.clear();
        newState.favoriteLifeformIds.addAll(favoriteLifeformIds);
        newState.newUnseenLifeformIds.clear();
        newState.newUnseenLifeformIds.addAll(newUnseenLifeformIds);
        newState.animalAgeCooldown = animalAgeCooldown;
    }

    private void syncStateToOwner() {
        if (!entity.level().isClientSide() && entity instanceof ServerPlayer serverPlayer) {
            syncToPlayer(serverPlayer);
        }
    }

    private static ListTag stringList(Collection<String> strings) {
        ListTag list = new ListTag();
        strings.forEach(string -> list.add(StringTag.valueOf(string)));
        return list;
    }

    @Nullable
    private static String normalizeLifeformId(@Nullable String lifeformId) {
        if (lifeformId == null || lifeformId.isBlank()) {
            return null;
        }
        return LEGACY_CHOICE_IDS.getOrDefault(lifeformId, lifeformId);
    }
}
