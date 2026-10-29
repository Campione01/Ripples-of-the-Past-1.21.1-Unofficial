package rotp.addons.catchtherainbow.effect;

import javax.annotation.Nullable;

import rotp.core.customobjects.StatusEffectApplicable;
import rotp.core.customobjects.StatusEffectModified;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.ability.RainRedemptionAbility;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class RainRedemptionEffect
        extends StatusEffectModified
        implements StatusEffectApplicable {
    // 1.16 InitEffects registered it with color 8445687.
    public static final int COLOR = 0x80DEF7;
    public static final ResourceLocation ABSORPTION_MODIFIER =
            CatchTheRainbowAddon.id("rain_redemption_absorption");
    private static final String ABSORPTION_TAG =
            "rotp_ctr:redemption_absorption";

    public RainRedemptionEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        setUncurable();
    }

    @Override
    public boolean isApplicable(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null
                && power.getPowerType()
                        == CatchTheRainbowAddon
                                .CATCH_THE_RAINBOW.get();
    }

    @Override
    public void onAdded(
            LivingEntity entity,
            MobEffectInstance instance,
            @Nullable Entity source) {
        if (!(entity.level() instanceof ServerLevel level)
                || !isApplicable(entity)) {
            return;
        }
        AttributeInstance health =
                entity.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        health.removeModifier(
                RainRedemptionAbility.HEALTH_MODIFIER);
        float originalMax = entity.getMaxHealth();
        float absorption =
                Math.max(0.0F, originalMax * 4.0F - 1.0F);
        CompoundTag data = entity.getPersistentData();
        data.putFloat(ABSORPTION_TAG, absorption);
        // 1.16 absorption had no ceiling; 1.21 clamps it to
        // max_absorption, so raise that by the amount granted, the
        // way vanilla Absorption does, before adding it.
        AttributeInstance maxAbsorption =
                entity.getAttribute(Attributes.MAX_ABSORPTION);
        if (maxAbsorption != null) {
            maxAbsorption.addOrReplacePermanentModifier(
                    new AttributeModifier(
                            ABSORPTION_MODIFIER,
                            absorption,
                            AttributeModifier.Operation.ADD_VALUE));
        }
        entity.setAbsorptionAmount(
                entity.getAbsorptionAmount() + absorption);
        health.addOrUpdateTransientModifier(
                new AttributeModifier(
                        RainRedemptionAbility.HEALTH_MODIFIER,
                        -(originalMax - 1.0D),
                        AttributeModifier.Operation.ADD_VALUE));
        entity.setHealth(Math.min(entity.getHealth(), 1.0F));
        entity.addEffect(new MobEffectInstance(
                ModStatusEffects.RESOLVE,
                RainRedemptionAbility.DURATION_TICKS,
                0,
                false,
                false,
                false));
        level.setWeatherParameters(
                0,
                RainRedemptionAbility.DURATION_TICKS,
                true,
                false);
    }

    @Override
    public void onRemoved(
            LivingEntity entity,
            MobEffectInstance instance) {
        if (entity.level().isClientSide()) {
            return;
        }
        AttributeInstance health =
                entity.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(
                    RainRedemptionAbility.HEALTH_MODIFIER);
        }
        AttributeInstance maxAbsorption =
                entity.getAttribute(Attributes.MAX_ABSORPTION);
        if (maxAbsorption != null) {
            maxAbsorption.removeModifier(ABSORPTION_MODIFIER);
        }
        CompoundTag data = entity.getPersistentData();
        float ownedAbsorption =
                data.getFloat(ABSORPTION_TAG);
        data.remove(ABSORPTION_TAG);
        entity.setAbsorptionAmount(Math.max(
                0.0F,
                entity.getAbsorptionAmount()
                        - ownedAbsorption));
        // 1.16 RainRedemption.removeAttributeModifiers killed the user
        // right here. 1.21 removeAllEffects (/effect clear) calls this
        // before it takes the effect out of the map it is iterating, and
        // the death cleanup removes this effect from that map, so the
        // kill waits for the next server task there.
        if (entity.getActiveEffectsMap().get(instance.getEffect())
                        == instance
                && entity.level() instanceof ServerLevel level) {
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(
                    server.getTickCount(),
                    () -> killUser(entity)));
        }
        else {
            killUser(entity);
        }
    }

    private static void killUser(LivingEntity entity) {
        if (entity.isAlive() && !entity.isRemoved()) {
            entity.hurt(
                    entity.damageSources().genericKill(),
                    Float.MAX_VALUE);
        }
    }
}
