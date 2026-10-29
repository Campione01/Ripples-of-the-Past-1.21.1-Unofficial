package rotp.addons.stone_legacy_zombie_orders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.core.api.playerpower.PlayerPowerTransitions;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.vampirism.VampirismPowerType;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class ZombieOwnership {
    private ZombieOwnership() {}

    public static boolean isControlled(LivingEntity entity) {
        return entity instanceof HungryZombieEntity
                || ZombieOrdersAddon.conversion(entity)
                        .isConverted();
    }

    public static Optional<UUID> ownerUuid(
            LivingEntity entity) {
        Optional<UUID> stored = ZombieOrdersAddon
                .conversion(entity)
                .ownerUuid();
        if (stored.isPresent()) {
            return stored;
        }
        if (entity instanceof HungryZombieEntity zombie) {
            LivingEntity owner = zombie.getOwner();
            if (owner != null) {
                return Optional.of(owner.getUUID());
            }
        }
        return Optional.empty();
    }

    public static void markConverted(
            LivingEntity entity,
            LivingEntity owner,
            PlayerPowerTransitions.Snapshot previousPower) {
        ZombieOrdersAddon.conversion(entity)
                .markConverted(owner.getUUID(), previousPower);
        ZombieOrdersAddon.roster(owner).add(entity.getUUID());
    }

    public static void markConvertedFromNoPower(
            LivingEntity entity,
            LivingEntity owner) {
        ZombieOrdersAddon.conversion(entity)
                .markConvertedFromNoPower(owner.getUUID());
        ZombieOrdersAddon.roster(owner).add(entity.getUUID());
    }

    public static void trackHungryZombie(
            HungryZombieEntity zombie,
            LivingEntity owner) {
        zombie.setOwner(owner);
        ZombieOrdersAddon.conversion(zombie)
                .setOwnerUuid(owner.getUUID());
        ZombieOrdersAddon.roster(owner).add(zombie.getUUID());
    }

    public static boolean setOwner(
            LivingEntity entity,
            LivingEntity newOwner) {
        // The operator command may mark any living target, without converting it.
        ownerUuid(entity)
                .flatMap(ownerId -> resolveOwner(
                        entity, ownerId))
                .ifPresent(oldOwner ->
                        ZombieOrdersAddon.roster(oldOwner)
                                .remove(entity.getUUID()));
        if (entity instanceof HungryZombieEntity zombie) {
            zombie.setOwner(newOwner);
            ZombieOrdersAddon.state(zombie)
                    .setOrderedToWait(zombie, false);
            ZombieOrdersAddon.state(zombie)
                    .setActiveWaitingPose(zombie, false);
        }
        ZombieOrdersAddon.conversion(entity)
                .setOwnerUuid(newOwner.getUUID());
        ZombieOrdersAddon.roster(newOwner)
                .add(entity.getUUID());
        return true;
    }

    public static void removeFromOwnerRoster(
            LivingEntity entity) {
        ownerUuid(entity)
                .flatMap(ownerId -> resolveOwner(
                        entity, ownerId))
                .ifPresent(owner ->
                        ZombieOrdersAddon.roster(owner)
                                .remove(entity.getUUID()));
    }

    public static List<LivingEntity> ownedInLevel(
            LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return List.of();
        }
        UUID ownerId = owner.getUUID();
        List<LivingEntity> result = new ArrayList<>();
        for (UUID entityId :
                ZombieOrdersAddon.roster(owner)
                        .ownedZombies()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof LivingEntity living
                    && isControlled(living)
                    && (living instanceof HungryZombieEntity zombie
                            ? zombie.getOwner() == owner
                            : !releaseIfOwnerLostVampirism(living))
                    && ownerUuid(living)
                            .filter(ownerId::equals)
                            .isPresent()) {
                result.add(living);
            }
        }
        return result;
    }

    /**
     * The live-owner check of 1.16 HungryZombieEntity.getOwner(), for
     * converted entities: once the owner is seen in this level without
     * vampirism, or at curing stage 4, the entity leaves that roster for
     * good. In 1.16 the roster was part of VampirismData and went with
     * the power, while the entity's own owner record stayed, as here.
     */
    public static boolean releaseIfOwnerLostVampirism(
            LivingEntity converted) {
        UUID ownerId = ZombieOrdersAddon.conversion(converted)
                .ownerUuid()
                .orElse(null);
        if (ownerId == null) {
            return false;
        }
        Player owner = converted.level().getPlayerByUUID(ownerId);
        if (owner == null || hasLiveVampirism(owner)) {
            return false;
        }
        ZombieOrdersAddon.roster(owner).remove(converted.getUUID());
        return true;
    }

    private static boolean hasLiveVampirism(Player owner) {
        return PlayerPower.getPowerData(
                        owner, VampirismPowerType.VAMPIRISM)
                .map(data -> data.getCuringStage(owner) < 4)
                .orElse(false);
    }

    public static Optional<LivingEntity> resolveOwner(
            LivingEntity controlled) {
        return ownerUuid(controlled)
                .flatMap(ownerId ->
                        resolveOwner(controlled, ownerId));
    }

    private static Optional<LivingEntity> resolveOwner(
            LivingEntity context,
            UUID ownerId) {
        if (!(context.level()
                instanceof ServerLevel contextLevel)) {
            return Optional.empty();
        }
        MinecraftServer server = contextLevel.getServer();
        LivingEntity player =
                server.getPlayerList().getPlayer(ownerId);
        if (player != null) {
            return Optional.of(player);
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(ownerId);
            if (entity instanceof LivingEntity living) {
                return Optional.of(living);
            }
        }
        return Optional.empty();
    }
}
