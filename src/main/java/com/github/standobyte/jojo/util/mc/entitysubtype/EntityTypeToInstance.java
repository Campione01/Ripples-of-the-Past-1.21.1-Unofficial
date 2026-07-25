package com.github.standobyte.jojo.util.mc.entitysubtype;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

public final class EntityTypeToInstance {
    private static final Map<SubtypeResourceLocation, Entity> ENTITY_INSTANCES = new HashMap<>();
    private static final Set<SubtypeResourceLocation> FAILED_INSTANCE_TYPES = new HashSet<>();

    private EntityTypeToInstance() {
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Entity> T getEntityInstance(EntitySubtype<T> subType, Level level) {
        if (FAILED_INSTANCE_TYPES.contains(subType.getId())) {
            return null;
        }
        Entity entity = ENTITY_INSTANCES.get(subType.getId());
        if (entity == null || entity.level() != level) {
            entity = createInstance(subType, level);
            if (entity != null) {
                ENTITY_INSTANCES.put(subType.getId(), entity);
            }
        }
        return (T) entity;
    }

    @Nullable
    private static <T extends Entity> T createInstance(EntitySubtype<T> type, Level level) {
        try {
            T entity = type.create(level);
            if (entity instanceof Slime) {
                entity.refreshDimensions();
            }
            return entity;
        }
        catch (RuntimeException e) {
            FAILED_INSTANCE_TYPES.add(type.getId());
            JojoMod.getLogger().warn("Failed to initialize entity subtype {} for a cached ROTP entity instance. Skipping this subtype.", type.getId(), e);
            return null;
        }
    }
}
