package rotp.addons.hermitpurple;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.core.JojoRegistries;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import com.mojang.datafixers.util.Pair;

import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class HermitDivination {
    private HermitDivination() {}

    @Nullable
    public static LivingEntity resolveEntity(
            ServerPlayer user,
            HermitPurpleStandEntity controller) {
        ServerLevel level = user.serverLevel();
        String target = controller.getTarget();
        return switch (controller.getTargetMode()) {
            case HermitPurpleSpec.TARGET_MODE_RANDOM ->
                    randomLiving(user, level);
            case HermitPurpleSpec.TARGET_MODE_PLAYER ->
                    level.players().stream()
                            .filter(player -> player != user)
                            .filter(player -> player.getGameProfile()
                                    .getName()
                                    .equals(target))
                            .findFirst()
                            .orElse(null);
            case HermitPurpleSpec.TARGET_MODE_STAND ->
                    level.players().stream()
                            .filter(player -> player != user)
                            .filter(player -> {
                                StandPower stand =
                                        StandPower.get(player);
                                return stand != null
                                        && stand.hasPower()
                                        && target.equals(
                                                String.valueOf(
                                                        JojoRegistries
                                                                .DEFAULT_STANDS_REG
                                                                .getKey(
                                                                        stand.getPowerType())));
                            })
                            .findFirst()
                            .orElse(null);
            case HermitPurpleSpec.TARGET_MODE_ENTITY -> {
                ResourceLocation id =
                        ResourceLocation.tryParse(target);
                yield id == null
                        ? null
                        : randomOfType(
                                user,
                                level,
                                BuiltInRegistries.ENTITY_TYPE
                                        .getOptional(id)
                                        .orElse(null));
            }
            default -> null;
        };
    }

    @Nullable
    public static BlockPos resolvePosition(
            ServerPlayer user,
            HermitPurpleStandEntity controller) {
        LivingEntity entity = resolveEntity(user, controller);
        if (entity != null) {
            maybeGlow(user, entity, controller.getTargetMode());
            return entity.blockPosition();
        }
        ResourceLocation id =
                ResourceLocation.tryParse(controller.getTarget());
        if (id == null) {
            return null;
        }
        ServerLevel level = user.serverLevel();
        if (controller.getTargetMode()
                == HermitPurpleSpec.TARGET_MODE_STRUCTURE) {
            Registry<Structure> registry =
                    level.registryAccess()
                            .registryOrThrow(Registries.STRUCTURE);
            Holder.Reference<Structure> holder =
                    registry.getHolder(
                                    ResourceKey.create(
                                            Registries.STRUCTURE, id))
                            .orElse(null);
            if (holder == null) {
                return null;
            }
            Pair<BlockPos, Holder<Structure>> found =
                    level.getChunkSource()
                            .getGenerator()
                            .findNearestMapStructure(
                                    level,
                                    HolderSet.direct(holder),
                                    user.blockPosition(),
                                    HermitPurpleSpec
                                            .DOXX_STRUCTURE_RANGE,
                                    false);
            return found != null ? found.getFirst() : null;
        }
        if (controller.getTargetMode()
                == HermitPurpleSpec.TARGET_MODE_BIOME) {
            ResourceKey<Biome> key =
                    ResourceKey.create(Registries.BIOME, id);
            Pair<BlockPos, Holder<Biome>> found =
                    level.findClosestBiome3d(
                            holder -> holder.is(key),
                            user.blockPosition(),
                            HermitPurpleSpec
                                    .DOXX_BIOME_RANGE,
                            8,
                            64);
            return found != null ? found.getFirst() : null;
        }
        return null;
    }

    /**
     * A readable label for the currently selected divination target: the divination map's title, and now the
     * ability names again.
     *
     * <p>1.16 resolved the id before showing it - {@code HPDoxx.giveString} turned an entity target into the
     * entity type's description, a biome and a structure into their own names - and used the result for BOTH
     * the map title and the ability name. The port kept the map title but showed the raw id, and dropped the
     * ability-name override entirely. Resolving here fixes both from one place; a second copy of this mapping
     * next to the two abilities is exactly the kind of duplicate that drifts.
     *
     * <p>Never returns null or an empty string: the ability names put it into a format argument, and an empty
     * one reads as a truncated sentence.
     */
    public static String targetLabel(
            HermitPurpleStandEntity controller) {
        if (controller.getTargetMode()
                == HermitPurpleSpec.TARGET_MODE_RANDOM) {
            return "random";
        }
        String target = controller.getTarget();
        if (target == null || target.isEmpty()) {
            return "?";
        }
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null) {
            return target;
        }
        return switch (controller.getTargetMode()) {
            case HermitPurpleSpec.TARGET_MODE_STAND ->
                    translatedOr(
                            "stand." + id.getNamespace() + "."
                                    + id.getPath(),
                            target);
            case HermitPurpleSpec.TARGET_MODE_ENTITY ->
                    BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                            .map(type ->
                                    type.getDescription().getString())
                            .orElse(target);
            case HermitPurpleSpec.TARGET_MODE_BIOME ->
                    translatedOr(
                            Util.makeDescriptionId("biome", id), target);
            case HermitPurpleSpec.TARGET_MODE_STRUCTURE ->
                    translatedOr(
                            Util.makeDescriptionId("structure", id),
                            target);
            default -> target;
        };
    }

    /** The translated text for a key, or the raw target when nothing translates it. */
    private static String translatedOr(String key, String fallback) {
        String text = Component.translatable(key).getString();
        return text.equals(key) ? fallback : text;
    }

    @Nullable
    public static ServerPlayer resolvePhotoTarget(
            ServerPlayer user,
            HermitPurpleStandEntity controller) {
        if (controller.getTargetMode()
                        != HermitPurpleSpec.TARGET_MODE_PLAYER
                && controller.getTargetMode()
                        != HermitPurpleSpec.TARGET_MODE_STAND) {
            return null;
        }
        LivingEntity target = resolveEntity(user, controller);
        return target instanceof ServerPlayer player
                ? player
                : null;
    }

    @Nullable
    private static LivingEntity randomLiving(
            ServerPlayer user, ServerLevel level) {
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living
                    && living != user
                    && living.isAlive()
                    && (living instanceof ServerPlayer
                            || living.getMaxHealth() > 100.0F)) {
                candidates.add(living);
            }
        }
        return candidates.isEmpty()
                ? null
                : candidates.get(
                        user.getRandom().nextInt(candidates.size()));
    }

    @Nullable
    private static LivingEntity randomOfType(
            ServerPlayer user,
            ServerLevel level,
            @Nullable net.minecraft.world.entity.EntityType<?> type) {
        if (type == null) {
            return null;
        }
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living
                    && living != user
                    && living.isAlive()
                    && living.getType() == type) {
                candidates.add(living);
            }
        }
        return candidates.isEmpty()
                ? null
                : candidates.get(
                        user.getRandom().nextInt(candidates.size()));
    }

    private static void maybeGlow(
            ServerPlayer user,
            LivingEntity target,
            int mode) {
        if (mode != HermitPurpleSpec.TARGET_MODE_PLAYER
                && mode != HermitPurpleSpec.TARGET_MODE_STAND) {
            return;
        }
        MobEffectInstance resolve =
                user.getEffect(ModStatusEffects.RESOLVE);
        if (resolve != null
                && HermitHamon.hasSkill(
                        user, ModHamonSkills.DETECTOR.get())) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING,
                    Math.max(1, resolve.getDuration() / 4),
                    0,
                    false,
                    false,
                    false));
        }
    }
}
