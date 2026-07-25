package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.util.functions.AttributeUtil;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntitySubtype;
import com.github.standobyte.jojo.util.mc.entitysubtype.EntityTypeToInstance;
import com.github.standobyte.jojo.util.mc.entitysubtype.SubtypeResourceLocation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;

public final class GoldExperienceLifeforms {
    private static final String COCO_JUMBO_STAND_SUBTYPE = "stand";
    private static boolean extraSubtypesRegistered;

    private static final Set<String> DISABLED_NAMESPACES = Set.of("rotp_zbc", "rotp_harvest");
    private static final Set<ResourceLocation> DISABLED_TYPES = Set.of(
            id("twilightforest", "quest_ram"),
            id("twilightforest", "wraith"),
            id("twilightforest", "redcap"),
            id("twilightforest", "redcap_sapper"),
            id("twilightforest", "death_tome"),
            id("twilightforest", "minoshroom"),
            id("twilightforest", "minotaur"),
            id("twilightforest", "maze_slime"),
            id("twilightforest", "mist_wolf"),
            id("twilightforest", "tower_golem"),
            id("twilightforest", "blockchain_goblin"),
            id("twilightforest", "goblin_knight_upper"),
            id("twilightforest", "goblin_knight_lower"),
            id("twilightforest", "knight_phantom"),
            id("twilightforest", "yeti"),
            id("twilightforest", "snow_guardian"),
            id("twilightforest", "stable_ice_core"),
            id("twilightforest", "unstable_ice_core"),
            id("twilightforest", "snow_queen"),
            id("twilightforest", "ice_crystal"),
            id("twilightforest", "troll"),
            id("twilightforest", "adherent"),
            id("twilightforest", "roving_cube"),
            id("twilightforest", "plateau_boss"),
            id("alexsmobs", "centipede_head"),
            id("alexsmobs", "guster"),
            id("alexsmobs", "enderiophage"),
            id("alexsmobs", "mimicube"),
            id("rotp_zkq", "sheer_heart"));

    private GoldExperienceLifeforms() {
    }

    public static void ensureExtraEntitySubtypesRegistered() {
        if (extraSubtypesRegistered) {
            return;
        }
        EntitySubtype.registerSubtype(
                ModEntityTypes.COCO_JUMBO_TURTLE.get(),
                COCO_JUMBO_STAND_SUBTYPE,
                GoldExperienceLifeforms::giveMrPresidentStand,
                GoldExperienceLifeforms::isCocoJumboStandSubtype);
        extraSubtypesRegistered = true;
    }

    public static List<EntitySubtype<?>> validLifeforms(Level level) {
        ensureExtraEntitySubtypesRegistered();
        return EntitySubtype.values()
                .filter(subtype -> isValidLifeform(subtype, level))
                .sorted(Comparator.comparing((EntitySubtype<?> subtype) -> subtype.getDescription().getString())
                        .thenComparing(subtype -> subtype.getId().toString()))
                .toList();
    }

    public static List<EntitySubtype<?>> knownValidLifeforms(Level level, Collection<String> knownIds) {
        return validLifeforms(level).stream()
                .filter(subtype -> knownIds.contains(subtype.getId().toString()))
                .toList();
    }

    public static Optional<EntitySubtype<?>> subtypeFromId(@Nullable String id) {
        ensureExtraEntitySubtypesRegistered();
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(EntitySubtype.getSubtype(new SubtypeResourceLocation(id)));
        }
        catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static boolean isKnownSubtypeId(@Nullable String id) {
        return subtypeFromId(id).isPresent();
    }

    public static boolean isValidLifeformId(@Nullable String id, Level level) {
        return subtypeFromId(id).filter(subtype -> isValidLifeform(subtype, level)).isPresent();
    }

    public static boolean isValidLifeform(EntitySubtype<?> entitySubtype, Level level) {
        ensureExtraEntitySubtypesRegistered();
        if (isCocoJumboStandSubtype(entitySubtype, level)) {
            return true;
        }
        Entity entity = EntityTypeToInstance.getEntityInstance(entitySubtype, level);
        if (!(entity instanceof Mob mob)) {
            return false;
        }

        EntityType<?> entityType = entitySubtype.vanillaType;
        if (entityType.is(EntityTypeTags.UNDEAD)
                || mob instanceof Raider
                || entityType == EntityType.TRADER_LLAMA
                || !entityType.canSummon()) {
            return false;
        }

        if (entityType == EntityType.SLIME || entityType == EntityType.MAGMA_CUBE) {
            return false;
        }

        if (!(mob instanceof AmbientCreature || mob instanceof PathfinderMob || mob instanceof FlyingMob || mob instanceof Slime)) {
            return false;
        }

        if (mob instanceof Npc || mob instanceof Merchant
                || mob instanceof AbstractGolem || mob instanceof PatrollingMonster
                || mob instanceof Ghast || mob instanceof Blaze || mob instanceof Vex
                || mob instanceof Creeper || mob instanceof EnderMan
                || mob instanceof AbstractPiglin || mob instanceof Guardian) {
            return false;
        }

        if (getVolume(entity) >= 7.5F || mob.getMaxHealth() > 60.0F) {
            return false;
        }

        ResourceLocation typeId = EntityType.getKey(entity.getType());
        return !DISABLED_NAMESPACES.contains(typeId.getNamespace()) && !DISABLED_TYPES.contains(typeId);
    }

    private static boolean isCocoJumboStandSubtype(EntitySubtype<?> entitySubtype, Level level) {
        return entitySubtype.vanillaType == ModEntityTypes.COCO_JUMBO_TURTLE.get()
                && COCO_JUMBO_STAND_SUBTYPE.equals(entitySubtype.getId().getSubtypeId())
                && isCocoJumboStandSubtype(EntityTypeToInstance.getEntityInstance(entitySubtype, level));
    }

    private static boolean isCocoJumboStandSubtype(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity) || entity.getType() != ModEntityTypes.COCO_JUMBO_TURTLE.get()) {
            return false;
        }
        StandPower standPower = StandPower.get(livingEntity);
        return standPower != null && standPower.hasPower() && standPower.getPowerType() == ModStands.MR_PRESIDENT.get();
    }

    private static void giveMrPresidentStand(LivingEntity entity) {
        StandPower standPower = PowerClass.STAND.attachGet(entity);
        if (!standPower.hasPower()) {
            standPower.setStand(ModStands.MR_PRESIDENT.get());
        }
    }

    @Nullable
    public static Entity createEntity(EntitySubtype<?> type, Level level, LivingEntity user) {
        if (!isValidLifeform(type, level)) {
            return null;
        }

        Entity lifeFormCreated;
        try {
            lifeFormCreated = type.create(level);
        }
        catch (RuntimeException e) {
            JojoMod.getLogger().warn(
                    "Failed to create Gold Experience lifeform subtype {} for {}. Skipping this lifeform.",
                    type.getId(), user, e);
            return null;
        }
        if (lifeFormCreated == null) {
            return null;
        }

        CompoundTag nbt = new CompoundTag();
        nbt.putString("DeathLootTable", "empty");
        if (!level.dimensionType().piglinSafe()) {
            nbt.putBoolean("IsImmuneToZombification", true);
        }
        lifeFormCreated.load(nbt);

        if (lifeFormCreated instanceof Mob mob && level instanceof ServerLevel serverLevel) {
            mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(user.blockPosition()),
                    MobSpawnType.COMMAND, null);
            mob.setPersistenceRequired();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
            }

            if (mob instanceof AgeableMob ageable) {
                int ageCooldown = GoldExperienceLifeformState.get(user).addAnimalAgeCooldown(3000);
                ageable.setAge(Math.max(ageCooldown - 3000, 0));
                if (mob instanceof AbstractHorse horse) {
                    horse.setTemper(0);
                }
            }
            else if (mob instanceof Slime slime) {
                slime.setSize(0, true);
            }
        }

        return lifeFormCreated;
    }

    public static Component displayName(@Nullable String lifeformId) {
        return subtypeFromId(lifeformId).map(EntitySubtype::getDescription).orElseGet(() -> Component.empty());
    }

    public static float getVolume(Entity entity) {
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        return width * width * height;
    }

    public static double getAttackStrength(Entity entity) {
        return entity instanceof LivingEntity living
                ? AttributeUtil.getValueOrDefault(living, Attributes.ATTACK_DAMAGE, 0.0D)
                : 0.0D;
    }

    public static boolean isNativeLifeform(Entity targetEntity, Level level, LivingEntity user) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        EntityType<?> entityType = targetEntity.getType();
        MobCategory category = entityType.getCategory();
        if (category == MobCategory.MISC) {
            return false;
        }

        BlockPos pos = user.blockPosition();
        boolean waterPlacement = SpawnPlacements.getPlacementType(entityType) == SpawnPlacementTypes.IN_WATER;
        if (waterPlacement != serverLevel.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }

        boolean listedInBiome = serverLevel.getBiome(pos).value().getMobSettings().getMobs(category).unwrap().stream()
                .anyMatch(spawnerData -> spawnerData.type == entityType);
        if (!listedInBiome) {
            return false;
        }

        return !SpawnPlacements.hasPlacement(entityType)
                || checkNativeSpawnRules(entityType, serverLevel, pos);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static boolean checkNativeSpawnRules(EntityType<?> entityType, ServerLevel serverLevel, BlockPos pos) {
        return SpawnPlacements.checkSpawnRules((EntityType) entityType, serverLevel, MobSpawnType.SPAWNER,
                pos, RandomSource.create(0L));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
