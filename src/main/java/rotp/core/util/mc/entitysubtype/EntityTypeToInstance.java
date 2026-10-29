package rotp.core.util.mc.entitysubtype;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class EntityTypeToInstance {
    // asked by the client and the integrated server from their own threads
    private static final Map<SubtypeResourceLocation, Entity> ENTITY_INSTANCES = new ConcurrentHashMap<>();
    private static final Set<SubtypeResourceLocation> FAILED_INSTANCE_TYPES = ConcurrentHashMap.newKeySet();

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

    // an instance holds its level, which ends here: the client's as it leaves the world or its dimension, the server's
    // as it stops
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            ENTITY_INSTANCES.values().removeIf(entity -> entity.level() == level);
        }
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
