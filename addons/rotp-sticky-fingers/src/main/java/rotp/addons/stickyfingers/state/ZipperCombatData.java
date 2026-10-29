package rotp.addons.stickyfingers.state;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.network.DetachedBodyPresentationPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ZipperCombatData extends SavedData {
    private static final String FILE_NAME =
            "rotp_stfn-zipper-combat";
    private static final String WOUNDS_TAG = "Wounds";
    private static final String PARTS_TAG = "Parts";
    private static final int IMPAIRMENT_REFRESH_TICKS = 10;
    private static final TagKey<EntityType<?>> ZIPPER_HUMANOIDS =
            TagKey.create(
                    Registries.ENTITY_TYPE,
                    StickyFingersAddon.id("zipper_humanoids"));
    private static final SavedData.Factory<ZipperCombatData> FACTORY =
            new SavedData.Factory<>(
                    ZipperCombatData::new,
                    ZipperCombatData::load);

    private final Map<UUID, ZippedWoundRecord> woundsByTarget =
            new HashMap<>();
    private final Map<UUID, DetachedBodyPartRecord> partsByEntity =
            new HashMap<>();

    public static ZipperCombatData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static ZipperCombatData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        ZipperCombatData data = new ZipperCombatData();
        for (Tag element :
                tag.getList(WOUNDS_TAG, Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag recordTag)) {
                continue;
            }
            ZippedWoundRecord record =
                    ZippedWoundRecord.fromNbt(recordTag);
            if (record == null) {
                continue;
            }
            ZippedWoundRecord previous =
                    data.woundsByTarget.get(record.targetId());
            if (previous == null
                    || previous.createdAt() < record.createdAt()) {
                data.woundsByTarget.put(
                        record.targetId(), record);
            }
        }
        for (Tag element :
                tag.getList(PARTS_TAG, Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag recordTag)) {
                continue;
            }
            DetachedBodyPartRecord record =
                    DetachedBodyPartRecord.fromNbt(recordTag);
            if (record != null
                    && !data.hasPart(
                            record.targetId(), record.part())) {
                data.partsByEntity.put(
                        record.entityId(), record);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag wounds = new ListTag();
        woundsByTarget.values().stream()
                .sorted(Comparator.comparing(
                        record -> record.leaseId().toString()))
                .map(ZippedWoundRecord::toNbt)
                .forEach(wounds::add);
        tag.put(WOUNDS_TAG, wounds);

        ListTag parts = new ListTag();
        partsByEntity.values().stream()
                .sorted(Comparator.comparing(
                        record -> record.leaseId().toString()))
                .map(DetachedBodyPartRecord::toNbt)
                .forEach(parts::add);
        tag.put(PARTS_TAG, parts);
        return tag;
    }

    public void applyWounds(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target) {
        long createdAt = level.getGameTime();
        ZippedWoundRecord record = new ZippedWoundRecord(
                UUID.randomUUID(),
                owner.getUUID(),
                target.getUUID(),
                level.dimension().location(),
                createdAt,
                createdAt
                        + StickyFingersPhase1Spec
                                .ZIP_WOUNDS_DURATION_TICKS);
        woundsByTarget.put(target.getUUID(), record);
        target.addEffect(new MobEffectInstance(
                StickyFingersAddon.ZIP_WOUNDS_EFFECT,
                StickyFingersPhase1Spec.ZIP_WOUNDS_DURATION_TICKS,
                0,
                false,
                false,
                true));
        setDirty();
    }

    public boolean registerDetachedPart(
            ServerLevel level,
            LivingEntity owner,
            LivingEntity target,
            DetachedPartType part,
            DetachedBodyPartEntity entity) {
        if (!isSupportedTarget(target)
                || hasPart(target.getUUID(), part)) {
            return false;
        }
        long createdAt = level.getGameTime();
        DetachedBodyPartRecord record =
                new DetachedBodyPartRecord(
                        UUID.randomUUID(),
                        owner.getUUID(),
                        target.getUUID(),
                        entity.getUUID(),
                        level.dimension().location(),
                        part,
                        createdAt,
                        createdAt
                                + StickyFingersPhase1Spec
                                        .DETACHED_PART_LIFETIME_TICKS);
        entity.initialize(record);
        partsByEntity.put(entity.getUUID(), record);
        setDirty();
        broadcastPresentation(level, target.getUUID());
        return true;
    }

    public void tick(ServerLevel level) {
        tickWounds(level);
        tickParts(level);
        applyImpairments(level);
    }

    public void closeOwned(
            ServerLevel level,
            UUID ownerId,
            ZipperCombatCloseReason reason) {
        for (ZippedWoundRecord record :
                List.copyOf(woundsByTarget.values())) {
            if (record.ownerId().equals(ownerId)) {
                closeWound(level, record, reason);
            }
        }
        for (DetachedBodyPartRecord record :
                List.copyOf(partsByEntity.values())) {
            if (record.ownerId().equals(ownerId)) {
                closePart(level, record, reason, true);
            }
        }
    }

    public void closeTargeted(
            ServerLevel level,
            UUID targetId,
            ZipperCombatCloseReason reason) {
        ZippedWoundRecord wound = woundsByTarget.get(targetId);
        if (wound != null) {
            closeWound(level, wound, reason);
        }
        for (DetachedBodyPartRecord record :
                List.copyOf(partsByEntity.values())) {
            if (record.targetId().equals(targetId)) {
                closePart(level, record, reason, true);
            }
        }
        LivingEntity target = resolveLiving(level, targetId);
        if (target != null) {
            removeOwnedLegEffect(target);
        }
    }

    public void breakWounds(
            ServerLevel level, LivingEntity target) {
        ZippedWoundRecord record =
                woundsByTarget.get(target.getUUID());
        if (record != null) {
            closeWound(
                    level,
                    record,
                    ZipperCombatCloseReason.EFFECT_BROKEN);
        }
    }

    public boolean recoverPart(
            ServerLevel level,
            UUID entityId,
            ZipperCombatCloseReason reason) {
        DetachedBodyPartRecord record =
                partsByEntity.get(entityId);
        if (record == null) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof DetachedBodyPartEntity) {
                entity.discard();
            }
            return false;
        }
        closePart(level, record, reason, true);
        return true;
    }

    public int startReturningParts(
            ServerLevel level, LivingEntity target) {
        int started = 0;
        UUID targetId = target.getUUID();
        for (DetachedBodyPartRecord record :
                List.copyOf(partsByEntity.values())) {
            if (!record.targetId().equals(targetId)) {
                continue;
            }
            Entity entity = level.getEntity(record.entityId());
            if (entity instanceof DetachedBodyPartEntity part
                    && !part.isRemoved()) {
                part.startReturning();
                started++;
            }
        }
        return started;
    }

    public void reconcileLiving(
            ServerLevel level, LivingEntity target) {
        ZippedWoundRecord wound =
                woundsByTarget.get(target.getUUID());
        if (wound == null
                && target.hasEffect(
                        StickyFingersAddon.ZIP_WOUNDS_EFFECT)) {
            target.removeEffect(
                    StickyFingersAddon.ZIP_WOUNDS_EFFECT);
        }
        if (partsFor(target.getUUID()).stream()
                .noneMatch(DetachedPartType::isLeg)) {
            removeOwnedLegEffect(target);
        }
        broadcastPresentation(level, target.getUUID());
    }

    public boolean isPartActive(UUID entityId) {
        return partsByEntity.containsKey(entityId);
    }

    public static boolean isSupportedTarget(
            LivingEntity target) {
        return target.getType().is(ZIPPER_HUMANOIDS);
    }

    public Set<DetachedPartType> partsFor(UUID targetId) {
        EnumSet<DetachedPartType> parts =
                EnumSet.noneOf(DetachedPartType.class);
        for (DetachedBodyPartRecord record :
                partsByEntity.values()) {
            if (record.targetId().equals(targetId)) {
                parts.add(record.part());
            }
        }
        return parts;
    }

    public int presentationMask(UUID targetId) {
        int mask = 0;
        for (DetachedPartType part : partsFor(targetId)) {
            mask |= part.mask();
        }
        return mask;
    }

    public void syncPresentationTo(
            ServerPlayer observer, UUID targetId) {
        PacketDistributor.sendToPlayer(
                observer,
                new DetachedBodyPresentationPayload(
                        targetId,
                        presentationMask(targetId)));
    }

    public boolean isHandBlocked(
            LivingEntity entity, InteractionHand hand) {
        Set<DetachedPartType> parts =
                partsFor(entity.getUUID());
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? entity.getMainArm()
                : entity.getMainArm().getOpposite();
        return parts.contains(
                arm == HumanoidArm.RIGHT
                        ? DetachedPartType.RIGHT_ARM
                        : DetachedPartType.LEFT_ARM);
    }

    public int activeWoundCount() {
        return woundsByTarget.size();
    }

    public int activePartCount() {
        return partsByEntity.size();
    }

    private void tickWounds(ServerLevel level) {
        for (ZippedWoundRecord record :
                List.copyOf(woundsByTarget.values())) {
            ZipperCombatCloseReason invalid =
                    validateOwner(level, record.ownerId());
            if (invalid == null
                    && level.getGameTime() >= record.expiresAt()) {
                invalid = ZipperCombatCloseReason.EXPIRED;
            }
            ServerPlayer targetPlayer =
                    level.getServer().getPlayerList()
                            .getPlayer(record.targetId());
            if (invalid == null
                    && targetPlayer != null
                    && targetPlayer.level() != level) {
                invalid =
                        ZipperCombatCloseReason.DIMENSION_CHANGE;
            }
            LivingEntity target =
                    resolveLiving(level, record.targetId());
            if (invalid == null
                    && target != null
                    && (!target.isAlive() || target.isRemoved())) {
                invalid = ZipperCombatCloseReason.TARGET_DEATH;
            }
            if (invalid == null
                    && target != null
                    && !target.hasEffect(
                            StickyFingersAddon
                                    .ZIP_WOUNDS_EFFECT)) {
                invalid =
                        ZipperCombatCloseReason.EFFECT_REMOVED;
            }
            if (invalid != null) {
                closeWound(level, record, invalid);
            }
        }
    }

    private void tickParts(ServerLevel level) {
        for (DetachedBodyPartRecord record :
                List.copyOf(partsByEntity.values())) {
            ZipperCombatCloseReason invalid =
                    validateOwner(level, record.ownerId());
            if (invalid == null
                    && level.getGameTime() >= record.expiresAt()) {
                invalid = ZipperCombatCloseReason.EXPIRED;
            }
            ServerPlayer targetPlayer =
                    level.getServer().getPlayerList()
                            .getPlayer(record.targetId());
            if (invalid == null
                    && targetPlayer != null
                    && targetPlayer.level() != level) {
                invalid =
                        ZipperCombatCloseReason.DIMENSION_CHANGE;
            }
            LivingEntity target =
                    resolveLiving(level, record.targetId());
            if (invalid == null
                    && target != null
                    && (!target.isAlive() || target.isRemoved())) {
                invalid = ZipperCombatCloseReason.TARGET_DEATH;
            }
            if (invalid != null) {
                closePart(level, record, invalid, true);
            }
        }
    }

    @Nullable
    private ZipperCombatCloseReason validateOwner(
            ServerLevel level, UUID ownerId) {
        ServerPlayer owner =
                level.getServer().getPlayerList()
                        .getPlayer(ownerId);
        if (owner == null) {
            return ZipperCombatCloseReason.OWNER_LOGOUT;
        }
        if (!owner.isAlive() || owner.isRemoved()) {
            return ZipperCombatCloseReason.OWNER_DEATH;
        }
        if (owner.level() != level) {
            return ZipperCombatCloseReason.DIMENSION_CHANGE;
        }
        if (!StickyFingersAddon.isStickyFingersActive(owner)) {
            return ZipperCombatCloseReason.ABILITY_LOST;
        }
        return null;
    }

    private void applyImpairments(ServerLevel level) {
        Map<UUID, EnumSet<DetachedPartType>> byTarget =
                new HashMap<>();
        for (DetachedBodyPartRecord record :
                partsByEntity.values()) {
            byTarget.computeIfAbsent(
                            record.targetId(),
                            ignored -> EnumSet.noneOf(
                                    DetachedPartType.class))
                    .add(record.part());
        }
        for (Map.Entry<UUID, EnumSet<DetachedPartType>> entry :
                byTarget.entrySet()) {
            LivingEntity target =
                    resolveLiving(level, entry.getKey());
            if (target == null
                    || !target.isAlive()
                    || target.isRemoved()) {
                continue;
            }
            EnumSet<DetachedPartType> parts = entry.getValue();
            if (parts.contains(DetachedPartType.LEFT_ARM)) {
                dropBlockedHand(
                        level, target, HumanoidArm.LEFT);
            }
            if (parts.contains(DetachedPartType.RIGHT_ARM)) {
                dropBlockedHand(
                        level, target, HumanoidArm.RIGHT);
            }
            long legCount = parts.stream()
                    .filter(DetachedPartType::isLeg)
                    .count();
            if (legCount > 0) {
                target.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        IMPAIRMENT_REFRESH_TICKS,
                        10,
                        false,
                        false,
                        false));
            }
            if (legCount == 2
                    && parts.contains(
                            DetachedPartType.LEFT_ARM)
                    && parts.contains(
                            DetachedPartType.RIGHT_ARM)
                    && !(target instanceof ServerPlayer)) {
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN,
                        IMPAIRMENT_REFRESH_TICKS,
                        10,
                        false,
                        false,
                        false));
            }
        }
    }

    private void closeWound(
            ServerLevel level,
            ZippedWoundRecord record,
            ZipperCombatCloseReason reason) {
        if (!woundsByTarget.remove(
                record.targetId(), record)) {
            return;
        }
        LivingEntity target =
                resolveLiving(level, record.targetId());
        if (target != null
                && target.hasEffect(
                        StickyFingersAddon.ZIP_WOUNDS_EFFECT)) {
            target.removeEffect(
                    StickyFingersAddon.ZIP_WOUNDS_EFFECT);
        }
        setDirty();
    }

    private void closePart(
            ServerLevel level,
            DetachedBodyPartRecord record,
            ZipperCombatCloseReason reason,
            boolean playSound) {
        if (!partsByEntity.remove(
                record.entityId(), record)) {
            return;
        }
        Entity detached = level.getEntity(record.entityId());
        LivingEntity target =
                resolveLiving(level, record.targetId());
        if (playSound) {
            Entity soundAt = detached != null ? detached : target;
            if (soundAt != null) {
                level.playSound(
                        null,
                        soundAt,
                        StickyFingersAddon.ZIPPER_CLOSE.get(),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F);
            }
        }
        if (detached != null) {
            detached.discard();
        }
        if (target != null
                && partsFor(target.getUUID()).stream()
                        .noneMatch(DetachedPartType::isLeg)) {
            removeOwnedLegEffect(target);
        }
        setDirty();
        broadcastPresentation(level, record.targetId());
    }

    private void broadcastPresentation(
            ServerLevel level, UUID targetId) {
        LivingEntity target = resolveLiving(level, targetId);
        if (target == null) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new DetachedBodyPresentationPayload(
                        targetId,
                        presentationMask(targetId)));
    }

    private boolean hasPart(
            UUID targetId, DetachedPartType part) {
        for (DetachedBodyPartRecord record :
                partsByEntity.values()) {
            if (record.targetId().equals(targetId)
                    && record.part() == part) {
                return true;
            }
        }
        return false;
    }

    private static void dropBlockedHand(
            ServerLevel level,
            LivingEntity target,
            HumanoidArm blockedArm) {
        InteractionHand hand =
                target.getMainArm() == blockedArm
                        ? InteractionHand.MAIN_HAND
                        : InteractionHand.OFF_HAND;
        ItemStack held = target.getItemInHand(hand);
        if (held.isEmpty()) {
            return;
        }
        ItemStack dropped = held.copy();
        target.setItemInHand(hand, ItemStack.EMPTY);
        ItemEntity item = new ItemEntity(
                level,
                target.getX(),
                target.getEyeY(),
                target.getZ(),
                dropped);
        item.setPickUpDelay(40);
        level.addFreshEntity(item);
    }

    private static void removeOwnedLegEffect(
            LivingEntity target) {
        MobEffectInstance slowness =
                target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slowness != null
                && slowness.getAmplifier() == 10
                && slowness.getDuration()
                        <= IMPAIRMENT_REFRESH_TICKS) {
            target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    @Nullable
    private static LivingEntity resolveLiving(
            ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (entity instanceof LivingEntity living) {
            return living;
        }
        ServerPlayer player =
                level.getServer().getPlayerList().getPlayer(id);
        return player != null && player.level() == level
                ? player
                : null;
    }

}
