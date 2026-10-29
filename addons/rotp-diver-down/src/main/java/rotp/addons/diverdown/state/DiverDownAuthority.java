package rotp.addons.diverdown.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.BoneShardEntity;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

public final class DiverDownAuthority {
    public enum EffectKind {
        SPRING_LEGS,
        BONE_SHARD_BOMB
    }

    private static final String ROOT_TAG =
            DiverDownAddon.MOD_ID + ":effect_authority";
    private static final String OWNER_TAG = "Owner";
    private static final String STAND_TAG = "Stand";
    private static final String TARGET_TAG = "Target";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String KIND_TAG = "Kind";
    private static final String OWNER_ACTIVE_TAG =
            DiverDownAddon.MOD_ID + ":authority_active";

    private DiverDownAuthority() {}

    public static DiverDownStandEntity controller(
            LivingEntity owner) {
        return DiverDownAddon.activeStand(owner);
    }

    public static boolean validController(
            LivingEntity owner,
            DiverDownStandEntity stand,
            String requiredAbility) {
        if (owner == null
                || stand == null
                || !owner.isAlive()
                || owner.isRemoved()
                || !stand.isAlive()
                || stand.isRemoved()
                || owner.level() != stand.level()
                || stand.getUser() != owner) {
            return false;
        }
        StandPower power = StandPower.get(owner);
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == DiverDownAddon.DIVER_DOWN.get()
                && power.getSummonedStandEntity() == stand
                && (requiredAbility == null
                        || power.isAbilityUnlocked(requiredAbility));
    }

    // A 1.16 StandAction's user: Diver Down's power with the ability
    // unlocked, the Stand summoned or not.
    public static boolean validUser(
            LivingEntity owner,
            StandPower power,
            String requiredAbility) {
        return owner != null
                && owner.isAlive()
                && !owner.isRemoved()
                && power != null
                && power.hasPower()
                && power.getPowerType() == DiverDownAddon.DIVER_DOWN.get()
                && power.isAbilityUnlocked(requiredAbility);
    }

    /**
     * @param stand null when Diver Down is not summoned
     */
    public static boolean canAffect(
            LivingEntity owner,
            LivingEntity target,
            DiverDownStandEntity stand) {
        if (target == null
                || target == owner
                || target == stand
                || target instanceof StandEntity
                || owner == null
                || target.level() != owner.level()
                || target.isRemoved()
                || !target.isAlive()
                || target.isInvulnerable()) {
            return false;
        }
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        PlayerTeam ownerTeam = owner.getTeam();
        PlayerTeam targetTeam = target.getTeam();
        if (ownerTeam != null
                && ownerTeam.isAlliedTo(targetTeam)
                && !ownerTeam.isAllowFriendlyFire()) {
            return false;
        }
        return !(owner instanceof Player && target instanceof Player)
                || owner.getServer() == null
                || owner.getServer().isPvpAllowed();
    }

    public static boolean bindEffect(
            LivingEntity target,
            DiverDownStandEntity stand,
            EffectKind kind,
            int duration,
            int amplifier) {
        LivingEntity owner = stand != null ? stand.getUser() : null;
        String ability = kind == EffectKind.SPRING_LEGS
                ? DiverDownAddon.SPRING_LEGS_NAME
                : DiverDownAddon.BONE_SHARD_BOMB_NAME;
        if (!(target.level() instanceof ServerLevel level)
                || !validController(owner, stand, ability)
                || !canAffect(owner, target, stand)) {
            return false;
        }

        clearEffectAuthority(target, kind);
        CompoundTag authority = new CompoundTag();
        authority.putUUID(OWNER_TAG, owner.getUUID());
        authority.putUUID(STAND_TAG, stand.getUUID());
        authority.putUUID(TARGET_TAG, target.getUUID());
        authority.putString(
                DIMENSION_TAG,
                level.dimension().location().toString());
        authority.putLong(
                EXPIRES_AT_TAG,
                level.getGameTime() + duration);
        authority.putString(KIND_TAG, kind.name());
        CompoundTag records = authorities(target);
        if (records == null) {
            records = new CompoundTag();
        }
        records.put(kind.name(), authority);
        target.getPersistentData().put(ROOT_TAG, records);
        owner.getPersistentData().putBoolean(
                OWNER_ACTIVE_TAG, true);

        // 1.16 DiverDownSpringLegs :53 and DiverDownMakeBoneShardBomb :48: no particles, only the icon
        target.addEffect(
                new MobEffectInstance(
                        kind == EffectKind.SPRING_LEGS
                                ? DiverDownAddon.SPRING_LEGS_EFFECT
                                : DiverDownAddon
                                        .BONE_SHARD_BOMB_EFFECT,
                        duration,
                        amplifier,
                        false,
                        false,
                        true),
                owner);
        return true;
    }

    public static boolean validateEffect(
            LivingEntity target, EffectKind expected) {
        if (!(target.level() instanceof ServerLevel level)) {
            return false;
        }
        CompoundTag authority = authority(target, expected);
        if (authority == null
                || !authority.hasUUID(OWNER_TAG)
                || !authority.hasUUID(STAND_TAG)
                || !authority.hasUUID(TARGET_TAG)
                || !target.getUUID().equals(
                        authority.getUUID(TARGET_TAG))
                || !expected.name().equals(
                        authority.getString(KIND_TAG))
                || !level.dimension().location().toString().equals(
                        authority.getString(DIMENSION_TAG))
                || level.getGameTime()
                        >= authority.getLong(EXPIRES_AT_TAG)) {
            clearEffectAuthority(target, expected);
            return false;
        }

        Entity ownerEntity =
                level.getEntity(authority.getUUID(OWNER_TAG));
        Entity standEntity =
                level.getEntity(authority.getUUID(STAND_TAG));
        String ability = expected == EffectKind.SPRING_LEGS
                ? DiverDownAddon.SPRING_LEGS_NAME
                : DiverDownAddon.BONE_SHARD_BOMB_NAME;
        if (!(ownerEntity instanceof LivingEntity owner)
                || !(standEntity
                        instanceof DiverDownStandEntity stand)
                || !validController(owner, stand, ability)
                || !canAffect(owner, target, stand)) {
            clearEffectAuthority(target, expected);
            return false;
        }
        return true;
    }

    public static void clearEffectAuthority(
            LivingEntity target) {
        if (target == null || target.level().isClientSide()) {
            return;
        }
        target.removeEffect(DiverDownAddon.SPRING_LEGS_EFFECT);
        target.removeEffect(
                DiverDownAddon.BONE_SHARD_BOMB_EFFECT);
        target.getPersistentData().remove(ROOT_TAG);
    }

    public static void clearEffectAuthority(
            LivingEntity target, EffectKind kind) {
        if (target == null || target.level().isClientSide() || kind == null) {
            return;
        }
        target.removeEffect(kind == EffectKind.SPRING_LEGS
                ? DiverDownAddon.SPRING_LEGS_EFFECT
                : DiverDownAddon.BONE_SHARD_BOMB_EFFECT);
        CompoundTag records = authorities(target);
        if (records != null) {
            records.remove(kind.name());
            if (!records.contains(EffectKind.SPRING_LEGS.name(), Tag.TAG_COMPOUND)
                    && !records.contains(
                            EffectKind.BONE_SHARD_BOMB.name(), Tag.TAG_COMPOUND)) {
                target.getPersistentData().remove(ROOT_TAG);
            }
        }
    }

    public static void markOwner(LivingEntity owner) {
        if (owner != null && !owner.level().isClientSide()) {
            owner.getPersistentData().putBoolean(
                    OWNER_ACTIVE_TAG, true);
        }
    }

    public static boolean ownerHasAuthority(
            LivingEntity owner) {
        return owner != null
                && owner.getPersistentData()
                        .getBoolean(OWNER_ACTIVE_TAG);
    }

    // Kinetic traps are not cleared here or for the Stand: 1.16 traps
    // outlived the Stand and their owner's death, logout and travel.
    public static void clearOwnedBy(
            MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : entitiesOf(level)) {
                if (entity instanceof LivingEntity living) {
                    for (EffectKind kind : EffectKind.values()) {
                        if (ownedBy(living, kind, ownerUuid)) {
                            clearEffectAuthority(living, kind);
                        }
                    }
                }
                if (entity instanceof BoneShardEntity shard
                        && ownerUuid.equals(
                                shard.getControllerUuid())) {
                    shard.discard();
                }
                if (entity instanceof Player player
                        && ownerUuid.equals(player.getUUID())) {
                    player.getPersistentData().remove(
                            OWNER_ACTIVE_TAG);
                }
            }
        }
    }

    public static void clearForStand(
            ServerLevel level, UUID standUuid) {
        if (level == null || standUuid == null) {
            return;
        }
        for (Entity entity : entitiesOf(level)) {
            if (entity instanceof LivingEntity living) {
                for (EffectKind kind : EffectKind.values()) {
                    if (ownedByStand(living, kind, standUuid)) {
                        clearEffectAuthority(living, kind);
                    }
                }
            }
            if (entity instanceof BoneShardEntity shard
                    && standUuid.equals(shard.getStandUuid())) {
                shard.discard();
            }
        }
    }

    // getAllEntities is a live view of the level's id map: a discard while it is iterated can shrink the map under
    // the iterator ("Index 48 out of bounds for length 33"), so the clean-ups walk a copy.
    private static List<Entity> entitiesOf(ServerLevel level) {
        List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(entities::add);
        return entities;
    }

    public static LivingEntity ownerOf(
            LivingEntity target, EffectKind expected) {
        if (!validateEffect(target, expected)
                || !(target.level() instanceof ServerLevel level)) {
            return null;
        }
        CompoundTag authority = authority(target, expected);
        Entity owner = authority != null
                ? level.getEntity(authority.getUUID(OWNER_TAG))
                : null;
        return owner instanceof LivingEntity living
                ? living
                : null;
    }

    public static DiverDownStandEntity standOf(
            LivingEntity target, EffectKind expected) {
        if (!validateEffect(target, expected)
                || !(target.level() instanceof ServerLevel level)) {
            return null;
        }
        CompoundTag authority = authority(target, expected);
        Entity stand = authority != null
                ? level.getEntity(authority.getUUID(STAND_TAG))
                : null;
        return stand instanceof DiverDownStandEntity diverDown
                ? diverDown
                : null;
    }

    private static boolean ownedBy(
            LivingEntity target, EffectKind kind, UUID ownerUuid) {
        CompoundTag authority = authority(target, kind);
        return authority != null
                && authority.hasUUID(OWNER_TAG)
                && ownerUuid.equals(
                        authority.getUUID(OWNER_TAG));
    }

    private static boolean ownedByStand(
            LivingEntity target, EffectKind kind, UUID standUuid) {
        CompoundTag authority = authority(target, kind);
        return authority != null
                && authority.hasUUID(STAND_TAG)
                && standUuid.equals(
                        authority.getUUID(STAND_TAG));
    }

    private static CompoundTag authority(Entity entity, EffectKind kind) {
        CompoundTag records = authorities(entity);
        return records != null && records.contains(kind.name(), Tag.TAG_COMPOUND)
                ? records.getCompound(kind.name())
                : null;
    }

    private static CompoundTag authorities(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag records = data.getCompound(ROOT_TAG);
        // The legacy root itself was the sole Kind record.
        if (records.contains(KIND_TAG, Tag.TAG_STRING)) {
            String legacyKind = records.getString(KIND_TAG);
            for (EffectKind kind : EffectKind.values()) {
                if (kind.name().equals(legacyKind)) {
                    CompoundTag normalized = new CompoundTag();
                    normalized.put(legacyKind, records.copy());
                    data.put(ROOT_TAG, normalized);
                    return normalized;
                }
            }
        }
        return records;
    }
}
