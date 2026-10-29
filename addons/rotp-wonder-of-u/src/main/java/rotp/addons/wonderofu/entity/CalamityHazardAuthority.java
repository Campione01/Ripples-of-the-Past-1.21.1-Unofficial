package rotp.addons.wonderofu.entity;

import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.wonderofu.WonderOfUAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;

public final class CalamityHazardAuthority {
    public static final int MAX_LIFETIME_TICKS = 100;

    private static final String ROOT_TAG =
            WonderOfUAddon.MOD_ID + ":calamity_hazard";
    private static final String OWNER_TAG = "Owner";
    private static final String STAND_TAG = "Stand";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";

    private CalamityHazardAuthority() {}

    public static void bind(
            Entity hazard,
            WonderOfUStandEntity stand,
            LivingEntity owner) {
        if (!(hazard.level() instanceof ServerLevel level)
                || stand.level() != level
                || owner.level() != level) {
            return;
        }
        CompoundTag authority = new CompoundTag();
        authority.putUUID(OWNER_TAG, owner.getUUID());
        authority.putUUID(STAND_TAG, stand.getUUID());
        authority.putString(
                DIMENSION_TAG,
                level.dimension().location().toString());
        authority.putLong(
                EXPIRES_AT_TAG,
                level.getGameTime() + MAX_LIFETIME_TICKS);
        hazard.getPersistentData().put(ROOT_TAG, authority);
    }

    public static boolean isBound(Entity entity) {
        return entity.getPersistentData()
                .contains(ROOT_TAG, Tag.TAG_COMPOUND);
    }

    public static boolean isAuthorized(Entity hazard) {
        if (!(hazard instanceof PrimedTnt || hazard instanceof Creeper)
                || !(hazard.level() instanceof ServerLevel level)) {
            return false;
        }
        CompoundTag authority = authority(hazard);
        if (authority == null
                || !authority.hasUUID(OWNER_TAG)
                || !authority.hasUUID(STAND_TAG)
                || !level.dimension().location().toString().equals(
                        authority.getString(DIMENSION_TAG))
                || level.getGameTime()
                        >= authority.getLong(EXPIRES_AT_TAG)) {
            return false;
        }

        Entity ownerEntity =
                level.getEntity(authority.getUUID(OWNER_TAG));
        Entity standEntity =
                level.getEntity(authority.getUUID(STAND_TAG));
        if (!(ownerEntity instanceof LivingEntity owner)
                || !(standEntity instanceof WonderOfUStandEntity stand)
                || !owner.isAlive()
                || owner.isRemoved()
                || !stand.isAlive()
                || stand.isRemoved()
                || stand.getUser() != owner) {
            return false;
        }

        StandPower power = StandPower.get(owner);
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == WonderOfUAddon.WONDER_OF_YOU.get()
                && power.getSummonedStandEntity() == stand
                && power.isAbilityUnlocked("explosion");
    }

    public static void clearForStand(
            ServerLevel level, UUID standUuid) {
        for (Entity entity : level.getAllEntities()) {
            CompoundTag authority = authority(entity);
            if (authority != null
                    && authority.hasUUID(STAND_TAG)
                    && standUuid.equals(
                            authority.getUUID(STAND_TAG))) {
                entity.discard();
            }
        }
    }

    private static CompoundTag authority(Entity entity) {
        CompoundTag persistentData = entity.getPersistentData();
        return persistentData.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? persistentData.getCompound(ROOT_TAG)
                : null;
    }
}
