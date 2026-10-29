package rotp.addons.whitesnake.disguise;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class DisguiseTypePolicy {
    public static final float MAX_DISGUISE_WIDTH = 4.0F;
    public static final float MAX_DISGUISE_HEIGHT = 4.0F;
    public static final float MAX_DISGUISE_HEALTH = 200.0F;

    private static final Set<EntityType<?>> DANGEROUS_TYPES = Set.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.WARDEN,
            EntityType.GIANT);

    private DisguiseTypePolicy() {}

    public static List<EntityType<?>> collectAllowed(Level level) {
        return BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(type -> isAllowed(type, level))
                .sorted(Comparator.comparing(
                        type -> EntityType.getKey(type).toString()))
                .toList();
    }

    public static boolean isAllowed(
            EntityType<?> entityType, Level level) {
        LivingEntity probe = createAllowedInstance(entityType, level);
        if (probe == null) {
            return false;
        }
        probe.remove(Entity.RemovalReason.DISCARDED);
        return true;
    }

    @Nullable
    public static LivingEntity createAllowedInstance(
            EntityType<?> entityType, Level level) {
        if (!isMetadataAllowed(entityType)) {
            return null;
        }

        try {
            Entity entity = entityType.create(level);
            if (!(entity instanceof LivingEntity living)
                    || living instanceof Player
                    || living instanceof StandEntity
                    || living.isMultipartEntity()
                    || living.getBbWidth() <= 0.0F
                    || living.getBbHeight() <= 0.0F
                    || living.getBbWidth() > MAX_DISGUISE_WIDTH
                    || living.getBbHeight() > MAX_DISGUISE_HEIGHT
                    || living.getMaxHealth() > MAX_DISGUISE_HEALTH) {
                if (entity != null) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
                return null;
            }
            return living;
        }
        catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    public static boolean isMetadataAllowed(EntityType<?> entityType) {
        if (entityType == null
                || !entityType.canSummon()
                || DANGEROUS_TYPES.contains(entityType)) {
            return false;
        }
        MobCategory category = entityType.getCategory();
        return category == MobCategory.CREATURE
                || category == MobCategory.MONSTER;
    }
}
