package com.github.standobyte.jojo.util.mc.entitysubtype;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class EntitySubtype<T extends Entity> {
    public final EntityType<T> vanillaType;
    private final SubtypeResourceLocation id;
    @Nullable private final Consumer<T> onInstanceInit;
    @Nullable private final Predicate<T> entityIsOfSubtype;

    public EntitySubtype(EntityType<T> type, @Nullable String subtypeId,
            @Nullable Consumer<T> onInstanceInit, @Nullable Predicate<T> entityIsOfSubtype) {
        this.vanillaType = type;
        this.id = new SubtypeResourceLocation(EntityType.getKey(type), subtypeId);
        this.onInstanceInit = onInstanceInit;
        this.entityIsOfSubtype = entityIsOfSubtype;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntitySubtype<T> base(EntityType<T> type) {
        return (EntitySubtype<T>) BASE_SUBTYPES.computeIfAbsent(EntityType.getKey(type),
                __ -> new EntitySubtype<>(type, null, null, null));
    }

    @Nullable
    public T create(Level level) {
        T entity = vanillaType.create(level);
        if (entity != null && onInstanceInit != null) {
            onInstanceInit.accept(entity);
        }
        return entity;
    }

    public boolean matches(T entity) {
        return entity.getType() == vanillaType && (entityIsOfSubtype == null || entityIsOfSubtype.test(entity));
    }

    public SubtypeResourceLocation getId() {
        return id;
    }

    public Component getDescription() {
        return vanillaType.getDescription();
    }

    private static final Map<ResourceLocation, Map<String, EntitySubtype<?>>> SUBTYPES = new HashMap<>();
    private static final Map<ResourceLocation, EntitySubtype<?>> BASE_SUBTYPES = new HashMap<>();
    @Nullable private static Collection<EntitySubtype<?>> allValuesCache;

    public static <T extends Entity> EntitySubtype<T> registerSubtype(EntityType<T> entityType, String subtypeId,
            Consumer<T> onInstanceInit, Predicate<T> entityIsOfSubtype) {
        Objects.requireNonNull(subtypeId);
        EntitySubtype<T> subType = new EntitySubtype<>(entityType, subtypeId, onInstanceInit, entityIsOfSubtype);
        SUBTYPES.computeIfAbsent(EntityType.getKey(entityType), __ -> new HashMap<>()).put(subtypeId, subType);
        allValuesCache = null;
        return subType;
    }

    public static Stream<EntitySubtype<?>> values() {
        if (allValuesCache == null) {
            allValuesCache = BuiltInRegistries.ENTITY_TYPE.stream()
                    .flatMap(EntitySubtype::valuesForType)
                    .collect(Collectors.toList());
        }
        return allValuesCache.stream();
    }

    private static Stream<EntitySubtype<?>> valuesForType(EntityType<?> entityType) {
        Stream<EntitySubtype<?>> base = Stream.of(baseUnchecked(entityType));
        Map<String, EntitySubtype<?>> subtypes = SUBTYPES.get(EntityType.getKey(entityType));
        return subtypes != null && !subtypes.isEmpty() ? Stream.concat(base, subtypes.values().stream()) : base;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static EntitySubtype<?> baseUnchecked(EntityType<?> entityType) {
        return base((EntityType) entityType);
    }

    @Nullable
    public static EntitySubtype<?> getSubtype(SubtypeResourceLocation id) {
        if (id.getSubtypeId() == null) {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(id.withoutSubtype());
            return entityType != null ? baseUnchecked(entityType) : null;
        }
        Map<String, EntitySubtype<?>> subtypes = SUBTYPES.get(id.withoutSubtype());
        return subtypes != null ? subtypes.get(id.getSubtypeId()) : null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> Stream<EntitySubtype<?>> getMatchingSubtypes(T entity) {
        Map<String, EntitySubtype<?>> subtypes = SUBTYPES.get(EntityType.getKey(entity.getType()));
        Stream<EntitySubtype<?>> base = Stream.of(base(entity.getType()));
        return subtypes != null && !subtypes.isEmpty()
                ? Stream.concat(base, subtypes.values().stream().filter(subType -> ((EntitySubtype<T>) subType).matches(entity)))
                : base;
    }
}
