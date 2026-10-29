package rotp.addons.stonefree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.stonefree.entity.StoneFreeStandEntity;
import rotp.addons.stonefree.entity.StoneFreeStringEntity;
import rotp.addons.stonefree.state.StoneFreeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class StoneFreeServices {
    private static final Set<ResourceLocation> INVERSION_EFFECTS =
            Set.of(
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_cm", "cm_inversion"),
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_cm", "arms_inversion"));
    private static final Set<ResourceLocation> OPTIONAL_WOUNDS =
            Set.of(
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_wr", "bleeding"),
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_mih", "bleeding"),
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_kingcrimson", "bleeding"),
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_cream", "bleeding"),
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_kingcrimson", "gaping_wound"),
                    ResourceLocation.fromNamespaceAndPath(
                            "rotp_kingcrimson", "mangled_body"));

    private StoneFreeServices() {}

    public static boolean isStoneFreeUser(
            @Nullable LivingEntity entity) {
        StandPower power =
                entity != null ? StandPower.get(entity) : null;
        return power != null
                && power.getPowerType()
                        == StoneFreeAddon.STONE_FREE.get();
    }

    @Nullable
    public static StoneFreeStandEntity controller(
            @Nullable LivingEntity user) {
        StandPower power =
                user != null ? StandPower.get(user) : null;
        return power != null
                && power.getPowerType()
                        == StoneFreeAddon.STONE_FREE.get()
                && power.getSummonedStandEntity()
                        instanceof StoneFreeStandEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }

    @Nullable
    public static StoneFreeStandEntity ensureArmsController(
            @Nullable LivingEntity user) {
        StoneFreeStandEntity stand = controller(user);
        if (stand != null || user == null) {
            return stand;
        }
        StandPower power = StandPower.get(user);
        if (power == null
                || power.getPowerType()
                        != StoneFreeAddon.STONE_FREE.get()
                || !(power.getPowerType()
                        instanceof EntityStandType entityType)) {
            return null;
        }
        entityType.summon(
                user,
                power,
                entity -> entity.setArmsOnlyMode(),
                true);
        return controller(user);
    }

    @Nullable
    public static LivingEntity userOf(
            @Nullable LivingEntity owner) {
        return owner instanceof StandEntity stand
                ? stand.getUser()
                : owner;
    }

    // 1.16 StoneFreeUserBarrier.getMaxBarriersPlaceable: the barriers the
    // user may place. The Mobius mode switch only lowers it through the
    // Mobius Strip effect it brings (StoneFreeGameEvents), as in 1.16.
    public static int maxStrings(
            LivingEntity user, StandPower power) {
        return power != null && power.getResolveLevel() >= 4
                ? stringLimit(user)
                : 25;
    }

    // 1.16 StoneFreeUserBarrier.MaxVarietyOfBarriers: the limit of all
    // strings, whatever number of barriers the user may place.
    public static int stringLimit(LivingEntity user) {
        return user.hasEffect(StoneFreeAddon.MOBIUS_STRIP) ? 90 : 100;
    }

    public static int inversionLevel(LivingEntity entity) {
        for (MobEffectInstance instance
                : entity.getActiveEffects()) {
            ResourceLocation id = effectId(instance);
            if (id != null && INVERSION_EFFECTS.contains(id)) {
                return instance.getAmplifier() + 1;
            }
        }
        return 0;
    }

    public static void removeInversion(LivingEntity entity) {
        List<MobEffectInstance> copy =
                List.copyOf(entity.getActiveEffects());
        for (MobEffectInstance instance : copy) {
            ResourceLocation id = effectId(instance);
            if (id != null && INVERSION_EFFECTS.contains(id)) {
                entity.removeEffect(instance.getEffect());
            }
        }
    }

    public static int woundLevel(LivingEntity entity) {
        int level = 0;
        MobEffectInstance coreBleeding =
                entity.getEffect(ModStatusEffects.BLEEDING);
        if (coreBleeding != null) {
            level += coreBleeding.getAmplifier() + 1;
        }
        for (MobEffectInstance instance
                : entity.getActiveEffects()) {
            ResourceLocation id = effectId(instance);
            if (id != null && OPTIONAL_WOUNDS.contains(id)) {
                level += instance.getAmplifier() + 1;
            }
        }
        return level;
    }

    public static void closeWounds(LivingEntity entity) {
        entity.removeEffect(ModStatusEffects.BLEEDING);
        List<MobEffectInstance> copy =
                List.copyOf(entity.getActiveEffects());
        for (MobEffectInstance instance : copy) {
            ResourceLocation id = effectId(instance);
            if (id != null && OPTIONAL_WOUNDS.contains(id)) {
                entity.removeEffect(instance.getEffect());
            }
        }
    }

    @Nullable
    public static Entity findEntity(
            LivingEntity owner, UUID id) {
        return owner.level() instanceof ServerLevel level
                ? level.getEntity(id)
                : null;
    }

    public static int countActiveUserStrings(
            LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return StoneFreeState.get(owner)
                    .activeUserStrings();
        }
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof StoneFreeStringEntity string
                    && string.isActiveUserStringFor(
                            owner.getUUID())) {
                count++;
            }
        }
        return count;
    }

    public static void discardUserGrapples(
            LivingEntity owner) {
        discardStrings(
                owner,
                string -> string.isUserGrappleFor(owner.getUUID()));
    }

    public static void discardBarriers(
            LivingEntity owner) {
        discardStrings(
                owner,
                string -> string.isBarrierFor(owner.getUUID()));
    }

    // Discarding while the level's entity map is being walked breaks the
    // walk when the removal makes the map shrink.
    private static void discardStrings(
            LivingEntity owner,
            Predicate<StoneFreeStringEntity> filter) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        List<StoneFreeStringEntity> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof StoneFreeStringEntity string
                    && filter.test(string)) {
                found.add(string);
            }
        }
        found.forEach(Entity::discard);
    }

    @Nullable
    private static ResourceLocation effectId(
            MobEffectInstance instance) {
        return instance.getEffect()
                .unwrapKey()
                .map(key -> key.location())
                .orElse(null);
    }
}
