package rotp.addons.metallica.state;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.MetallicaPhase1Spec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class MetallicaInvisibilityState {
    public static final int OWNED_AMPLIFIER =
            MobEffectInstance.MAX_AMPLIFIER;
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            MetallicaInvisibilityState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MetallicaInvisibilityState decode(
                        RegistryFriendlyByteBuf buffer) {
                    MetallicaInvisibilityState state =
                            new MetallicaInvisibilityState();
                    state.active = buffer.readBoolean();
                    state.lastClearReason = buffer.readEnum(
                            ClearReason.class);
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        MetallicaInvisibilityState state) {
                    buffer.writeBoolean(state.active);
                    buffer.writeEnum(state.lastClearReason);
                }
            };
    private static final int REFRESH_DURATION_TICKS = 40;
    private static final int REFRESH_THRESHOLD_TICKS = 10;

    private boolean active;
    private boolean addedSunResistance;
    private long activatedAt;
    private MobEffectInstance displacedInvisibility;
    private ClearReason lastClearReason = ClearReason.NEVER_ACTIVE;

    public boolean active() {
        return active;
    }

    public ClearReason lastClearReason() {
        return lastClearReason;
    }

    public boolean toggle(LivingEntity user) {
        if (active) {
            clear(user, ClearReason.USER_TOGGLE);
            return false;
        }
        return activate(user);
    }

    public boolean activate(LivingEntity user) {
        if (user.level().isClientSide() || active) {
            return active;
        }

        MobEffectInstance current =
                user.getEffect(MobEffects.INVISIBILITY);
        if (current != null
                && current.getAmplifier() >= OWNED_AMPLIFIER) {
            lastClearReason = ClearReason.EXTERNAL_EFFECT_WON;
            sync(user);
            return false;
        }
        displacedInvisibility = current != null
                ? new MobEffectInstance(current)
                : null;
        if (current != null
                && !user.removeEffect(MobEffects.INVISIBILITY)) {
            displacedInvisibility = null;
            lastClearReason = ClearReason.EXTERNAL_EFFECT_WON;
            sync(user);
            return false;
        }
        activatedAt = user.level().getGameTime();
        active = true;
        installOwnedEffect(user);
        if (!ownsCurrentEffect(user)) {
            active = false;
            restoreDisplacedEffect(user);
            lastClearReason = ClearReason.EXTERNAL_EFFECT_WON;
            sync(user);
            return false;
        }
        lastClearReason = ClearReason.ACTIVATED;
        protectVampireFromSun(user);
        sync(user);
        return true;
    }

    public void tickServer(LivingEntity user) {
        if (!active || user.level().isClientSide()) {
            return;
        }
        StandPower power = StandPower.get(user);
        if (!user.isAlive()
                || power == null
                || power.getPowerType()
                        != MetallicaAddon.METALLICA.get()
                || MetallicaAddon.controller(user) == null) {
            clear(user, ClearReason.STAND_INACTIVE);
            return;
        }
        if (!ownsCurrentEffect(user)) {
            clear(user, ClearReason.EXTERNAL_EFFECT_REPLACED);
            return;
        }
        Ability invisibility =
                power.getAbility("invisibility");
        // 1.16 MetallicaStandType.tickUser paid 6 a tick with the result
        // ignored and lifted the invisibility once the bar read 0, Resolve
        // or not.
        StandAbilityStamina.consume(
                invisibility,
                power,
                MetallicaPhase1Spec.INVISIBILITY_STAMINA_PER_TICK,
                true);
        if (power.getStamina() <= 0.0F) {
            clear(user, ClearReason.STAMINA_DEPLETED);
            return;
        }

        MobEffectInstance current =
                user.getEffect(MobEffects.INVISIBILITY);
        if (current != null
                && current.getDuration()
                        <= REFRESH_THRESHOLD_TICKS) {
            installOwnedEffect(user);
        }
        protectVampireFromSun(user);
    }

    public void clear(
            LivingEntity user, ClearReason reason) {
        if (user.level().isClientSide()) {
            return;
        }

        boolean ownedEffectPresent = ownsCurrentEffect(user);
        if (ownedEffectPresent) {
            user.removeEffect(MobEffects.INVISIBILITY);
        }
        if (ownedEffectPresent) {
            restoreDisplacedEffect(user);
        }
        if (addedSunResistance) {
            MobEffectInstance resistance = user.getEffect(
                    ModStatusEffects.SUN_RESISTANCE);
            if (hasOwnedSignature(resistance)) {
                user.removeEffect(
                        ModStatusEffects.SUN_RESISTANCE);
            }
        }

        active = false;
        addedSunResistance = false;
        displacedInvisibility = null;
        activatedAt = 0L;
        lastClearReason = reason;
        sync(user);
    }

    private void installOwnedEffect(LivingEntity user) {
        user.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                REFRESH_DURATION_TICKS,
                OWNED_AMPLIFIER,
                false,
                false,
                false));
    }

    private void restoreDisplacedEffect(LivingEntity user) {
        if (displacedInvisibility == null) {
            return;
        }
        int originalDuration = displacedInvisibility.getDuration();
        int remaining = originalDuration < 0
                ? originalDuration
                : Math.max(
                        0,
                        originalDuration
                                - (int) Math.max(
                                        0L,
                                        user.level().getGameTime()
                                                - activatedAt));
        if (remaining != 0) {
            user.addEffect(new MobEffectInstance(
                    displacedInvisibility.getEffect(),
                    remaining,
                    displacedInvisibility.getAmplifier(),
                    displacedInvisibility.isAmbient(),
                    displacedInvisibility.isVisible(),
                    displacedInvisibility.showIcon()));
        }
        displacedInvisibility = null;
    }

    private boolean ownsCurrentEffect(LivingEntity user) {
        MobEffectInstance current =
                user.getEffect(MobEffects.INVISIBILITY);
        return active && hasOwnedSignature(current);
    }

    private static boolean hasOwnedSignature(
            MobEffectInstance effect) {
        return effect != null
                && effect.getAmplifier() == OWNED_AMPLIFIER
                && effect.getDuration() > 0
                && effect.getDuration() <= REFRESH_DURATION_TICKS
                && !effect.isAmbient()
                && !effect.isVisible()
                && !effect.showIcon();
    }

    private void protectVampireFromSun(LivingEntity user) {
        if (!user.hasEffect(
                ModStatusEffects.VAMPIRE_SUN_BURN)) {
            return;
        }
        if (!user.hasEffect(
                ModStatusEffects.SUN_RESISTANCE)) {
            user.addEffect(new MobEffectInstance(
                    ModStatusEffects.SUN_RESISTANCE,
                    REFRESH_DURATION_TICKS,
                    OWNED_AMPLIFIER,
                    false,
                    false,
                    false));
            addedSunResistance = true;
        }
        user.removeEffect(ModStatusEffects.VAMPIRE_SUN_BURN);
    }

    private static void sync(LivingEntity user) {
        if (!user.level().isClientSide()) {
            user.syncData(MetallicaAddon.INVISIBILITY_STATE);
        }
    }

    public enum ClearReason {
        NEVER_ACTIVE,
        ACTIVATED,
        USER_TOGGLE,
        STAMINA_DEPLETED,
        STAND_INACTIVE,
        OWNER_DEATH,
        OWNER_RESPAWN,
        DIMENSION_CHANGE,
        OWNER_LOGOUT,
        EXTERNAL_EFFECT_WON,
        EXTERNAL_EFFECT_REPLACED
    }
}
