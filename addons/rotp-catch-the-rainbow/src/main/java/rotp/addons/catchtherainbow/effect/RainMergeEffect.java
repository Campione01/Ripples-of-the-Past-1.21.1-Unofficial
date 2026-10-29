package rotp.addons.catchtherainbow.effect;

import javax.annotation.Nullable;

import rotp.core.customobjects.StatusEffectApplicable;
import rotp.core.customobjects.StatusEffectModified;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class RainMergeEffect
        extends StatusEffectModified
        implements StatusEffectApplicable {
    // 1.16 InitEffects registered it with color 8445687.
    public static final int COLOR = 0x80DEF7;
    public static final float STAMINA_PER_TICK = 1.0F;

    public RainMergeEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        setUncurable();
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                CatchTheRainbowAddon.id(
                        "rain_merge_attack_speed"),
                -20.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    // 1.16 RainMerge.isApplicable: never on a creative player.
    @Override
    public boolean isApplicable(LivingEntity entity) {
        return !(entity instanceof Player player)
                || !player.getAbilities().instabuild;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    // 1.16 RainMerge ticked on both sides: a merged player cannot be
    // hurt or fly, pays 1 stamina a tick on top of the Stand's 4, and
    // keeps no horizontal momentum or upward speed.
    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            if (!player.level().isClientSide()) {
                StandPower power = StandPower.get(player);
                if (power != null) {
                    power.consumeStamina(STAMINA_PER_TICK);
                }
            }
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = true;
        }
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(
                0.0D, Math.min(0.0D, motion.y), 0.0D);
        return true;
    }

    @Override
    public void onAdded(
            LivingEntity entity,
            MobEffectInstance instance,
            @Nullable Entity source) {
        if (entity instanceof Player player) {
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = true;
        }
    }

    // 1.16 dropped the invulnerability when the effect ended; a
    // creative or spectator player keeps the one its game mode grants,
    // and the client is sent the server's abilities again.
    @Override
    public void onRemoved(
            LivingEntity entity, MobEffectInstance instance) {
        if (entity instanceof Player player) {
            player.getAbilities().invulnerable =
                    player.isCreative() || player.isSpectator();
            player.onUpdateAbilities();
        }
    }
}
