package rotp.addons.yellowtemperance.disguise;

import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class DisguisePolicy {
    private DisguisePolicy() {}

    public static boolean isAllowedEntity(
            EntityType<?> type, Level level) {
        if (type == null || level == null
                || type == EntityType.PLAYER) {
            return false;
        }
        EntityDimensions dimensions = type.getDimensions();
        ResourceLocation id = EntityType.getKey(type);
        if (YellowTemperanceConfig
                        .ENTITY_DISGUISE_BLACKLIST.get()
                        .contains(id.toString())
                || dimensions.height()
                        < YellowTemperanceConfig.MIN_ENTITY_HEIGHT
                                .get()
                || dimensions.height()
                        > YellowTemperanceConfig.MAX_ENTITY_HEIGHT
                                .get()
                || dimensions.width()
                        < YellowTemperanceConfig.MIN_ENTITY_WIDTH
                                .get()
                || dimensions.width()
                        > YellowTemperanceConfig.MAX_ENTITY_WIDTH
                                .get()) {
            return false;
        }
        Entity created = null;
        try {
            created = type.create(level);
            return created instanceof LivingEntity
                    && !(created instanceof Player)
                    && !(created instanceof StandEntity);
        }
        catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        finally {
            if (created != null) {
                created.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

    public static EntityType<?> entityType(
            ResourceLocation id, Level level) {
        EntityType<?> type = id != null
                ? BuiltInRegistries.ENTITY_TYPE
                        .getOptional(id).orElse(null)
                : null;
        return isAllowedEntity(type, level) ? type : null;
    }

    public static boolean isAllowedStand(
            ResourceLocation id, Object standType) {
        return standType instanceof EntityStandType entityStandType
                && id != null
                && !YellowTemperanceConfig
                        .STAND_DISGUISE_BLACKLIST.get()
                        .contains(id.toString())
                && standType
                        != YellowTemperanceAddon
                                .YELLOW_TEMPERANCE.get()
                && entityStandType.getEntityType() != null;
    }
}
