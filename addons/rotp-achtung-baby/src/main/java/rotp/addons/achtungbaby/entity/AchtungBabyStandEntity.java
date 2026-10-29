package rotp.addons.achtungbaby.entity;

import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class AchtungBabyStandEntity extends StandEntity {
    public AchtungBabyStandEntity(
            EntityType<? extends AchtungBabyStandEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        LivingEntity user = getUser();
        if (!level().isClientSide() && user != null && user.isAlive()) {
            user.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    10,
                    0,
                    false,
                    false,
                    false));
            // 1.16 AchtungBabyEntity.tick also gave the user Jump Boost II and Speed II while the Stand was out, and
            // turned a vampire user's sun burn into sun resistance. They are kept up the same short way as the
            // invisibility, so they end with the Stand however it goes.
            keepEffect(user, MobEffects.JUMP, 1);
            keepEffect(user, MobEffects.MOVEMENT_SPEED, 1);
            if (isVampire(user)
                    && (user.hasEffect(ModStatusEffects.VAMPIRE_SUN_BURN)
                            || user.hasEffect(ModStatusEffects.SUN_RESISTANCE))) {
                user.removeEffect(ModStatusEffects.VAMPIRE_SUN_BURN);
                keepEffect(user, ModStatusEffects.SUN_RESISTANCE, 0);
            }
        }
    }

    // Only when missing, weaker or about to run out, so a longer potion of the same level is left alone and the
    // attributes are not rebuilt every tick.
    private static void keepEffect(
            LivingEntity user, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = user.getEffect(effect);
        if (current == null
                || current.getAmplifier() < amplifier
                || current.endsWithin(5)) {
            user.addEffect(new MobEffectInstance(
                    effect, 10, amplifier, false, false, false));
        }
    }

    private static boolean isVampire(LivingEntity user) {
        PlayerPower power = PlayerPower.get(user);
        return power != null
                && power.getPowerType() == ModPlayerPowers.VAMPIRISM.get();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }
}
