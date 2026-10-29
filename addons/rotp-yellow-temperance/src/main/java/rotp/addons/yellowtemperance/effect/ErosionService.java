package rotp.addons.yellowtemperance.effect;

import java.util.UUID;

import rotp.core.util.functions.DamageUtil;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceConfig;
import rotp.addons.yellowtemperance.YellowTemperancePhase1Spec;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import rotp.addons.yellowtemperance.network.ErosionVisualPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ErosionService {
    public static final String CASTER_KEY =
            "RotpYellowTemperanceErosionCaster";
    // 1.16 YellowTemperanceDamageSource: armor applies, no causing entity.
    public static final ResourceKey<DamageType> EROSION_DAMAGE =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    YellowTemperanceAddon.id(
                            "yellow_temperance_erosion"));

    private ErosionService() {}

    public static void applyPunch(
            LivingEntity target, LivingEntity caster) {
        if (!valid(target, caster)) {
            return;
        }
        MobEffectInstance current =
                target.getEffect(YellowTemperanceAddon.EROSION);
        int cap = YellowTemperanceConfig
                .ATTACK_MAX_EROSION_LEVEL.get();
        if (current != null
                && current.getAmplifier() >= cap) {
            return;
        }
        int amplifier = current == null
                ? 0
                : Math.min(
                        current.getAmplifier()
                                + YellowTemperanceConfig
                                        .ATTACK_EROSION_INCREASE
                                        .get(),
                        cap);
        apply(
                target,
                caster,
                YellowTemperancePhase1Spec
                        .ATTACK_EROSION_DURATION,
                amplifier);
    }

    public static void applyAbsorption(
            LivingEntity target, LivingEntity caster) {
        if (valid(target, caster)
                && !target.hasEffect(
                        YellowTemperanceAddon.EROSION)) {
            apply(
                    target,
                    caster,
                    YellowTemperancePhase1Spec
                            .ATTACK_EROSION_DURATION,
                    0);
        }
    }

    public static void tick(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        MobEffectInstance erosion =
                target.getEffect(YellowTemperanceAddon.EROSION);
        if (erosion == null) {
            return;
        }
        if (target.hasEffect(YellowTemperanceAddon.PROTECTION)) {
            remove(target);
            return;
        }
        if (target.tickCount % 20 == 0) {
            float damage = Math.min(
                    YellowTemperanceConfig.EROSION_MAX_DAMAGE
                            .get().floatValue(),
                    YellowTemperanceConfig.EROSION_BASE_DAMAGE
                            .get().floatValue()
                            + erosion.getAmplifier()
                                    * YellowTemperanceConfig
                                            .EROSION_DAMAGE_PER_LEVEL
                                            .get().floatValue());
            target.hurt(
                    new DamageSource(
                            DamageUtil.type(level, EROSION_DAMAGE)),
                    damage);
            sync(target, owner(target), true);
        }
        if (erosion.getDuration()
                <= YellowTemperanceConfig
                        .EROSION_TRIGGER_TICKS.get()) {
            int maximumAmplifier = Math.max(
                    0,
                    YellowTemperanceConfig
                            .EROSION_MAX_LEVEL.get() - 1);
            boolean capped =
                    erosion.getAmplifier() >= maximumAmplifier;
            int nextAmplifier = capped
                    ? maximumAmplifier
                    : erosion.getAmplifier() + 1;
            int duration = capped
                    ? YellowTemperanceConfig
                            .EROSION_RESET_DURATION.get()
                    : target.isOnFire()
                            ? YellowTemperanceConfig
                                    .EROSION_FIRE_RESET_DURATION
                                    .get()
                            : YellowTemperanceConfig
                                    .EROSION_RESET_DURATION.get();
            UUID caster = casterId(target);
            target.addEffect(new MobEffectInstance(
                    YellowTemperanceAddon.EROSION,
                    duration,
                    nextAmplifier,
                    false,
                    true,
                    true));
            if (caster != null) {
                target.getPersistentData().putUUID(
                        CASTER_KEY, caster);
            }
        }
    }

    public static void clearOwned(LivingEntity caster) {
        if (caster == null || caster.getServer() == null) {
            return;
        }
        UUID casterId = caster.getUUID();
        for (ServerLevel level :
                caster.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                        && casterId.equals(casterId(living))) {
                    remove(living);
                }
            }
        }
    }

    public static void remove(LivingEntity target) {
        if (target == null || target.level().isClientSide()) {
            return;
        }
        LivingEntity owner = owner(target);
        target.removeEffect(YellowTemperanceAddon.EROSION);
        target.getPersistentData().remove(CASTER_KEY);
        sync(target, owner, false);
    }

    public static boolean isOwnedBy(
            LivingEntity target, LivingEntity caster) {
        return caster != null
                && caster.getUUID().equals(casterId(target));
    }

    private static void apply(
            LivingEntity target,
            LivingEntity caster,
            int duration,
            int amplifier) {
        target.addEffect(
                new MobEffectInstance(
                        YellowTemperanceAddon.EROSION,
                        duration,
                        amplifier,
                        false,
                        true,
                        true),
                caster);
        target.getPersistentData().putUUID(
                CASTER_KEY, caster.getUUID());
        sync(target, caster, true);
    }

    private static boolean valid(
            LivingEntity target, LivingEntity caster) {
        return target != null
                && caster != null
                && !target.level().isClientSide()
                && target.isAlive()
                && !target.isRemoved()
                && !target.hasEffect(
                        YellowTemperanceAddon.PROTECTION);
    }

    private static UUID casterId(LivingEntity target) {
        return target != null
                && target.getPersistentData().hasUUID(CASTER_KEY)
                        ? target.getPersistentData()
                                .getUUID(CASTER_KEY)
                        : null;
    }

    private static LivingEntity owner(LivingEntity target) {
        UUID id = casterId(target);
        MinecraftServer server = target != null
                ? target.getServer()
                : null;
        if (id == null || server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static void sync(
            LivingEntity target,
            LivingEntity owner,
            boolean active) {
        YellowTemperanceControllerEntity controller =
                owner != null
                        ? YellowTemperanceAddon.controller(owner)
                        : null;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                target,
                new ErosionVisualPayload(
                        target.getId(),
                        controller != null
                                ? controller.getId()
                                : 0,
                        active && controller != null));
    }
}
