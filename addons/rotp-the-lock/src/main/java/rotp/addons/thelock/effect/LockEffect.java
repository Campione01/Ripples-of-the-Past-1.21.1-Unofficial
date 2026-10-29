package rotp.addons.thelock.effect;

import rotp.core.customobjects.StatusEffectModified;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.thelock.TheLockAddon;
import rotp.addons.thelock.TheLockDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

public final class LockEffect extends StatusEffectModified {
    public static final int COLOR = 0x000111;
    public static final int SELF_HARM_AMPLIFIER = 4;
    public static final int SELF_HARM_INTERVAL = 20;

    public LockEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
        setUncurable();
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                TheLockAddon.id("lock_attack_damage"),
                -0.05D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                TheLockAddon.id("lock_attack_speed"),
                -0.05D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                TheLockAddon.id("lock_movement_speed"),
                -0.05D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return amplifier >= SELF_HARM_AMPLIFIER
                && duration % SELF_HARM_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()
                || !entity.isAlive()
                || amplifier < SELF_HARM_AMPLIFIER) {
            return true;
        }
        AttributeInstance attackDamage =
                entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return true;
        }
        if (entity instanceof Creeper creeper) {
            creeper.ignite();
            return true;
        }

        // As in 1.16: the swing reaches the target's own client too; a
        // player's attack cooldown restarts, and any other mob plays its
        // attack animation (entity event 4, e.g. an iron golem's arms).
        entity.swing(InteractionHand.MAIN_HAND, true);
        if (entity instanceof Player player) {
            player.resetAttackStrengthTicker();
        }
        else if (entity.level() instanceof ServerLevel level) {
            level.broadcastEntityEvent(entity, (byte) 4);
        }
        entity.playSound(
                SoundEvents.PLAYER_ATTACK_STRONG,
                0.8F,
                0.8F + entity.getRandom().nextFloat() * 0.4F);
        float damage = Math.max(
                2.0F,
                2.0F + (float) attackDamage.getValue());
        entity.hurt(
                DamageUtil.make(
                        entity.level(),
                        TheLockDamageTypes.SELF_HARM_LOCK,
                        entity),
                damage);
        return true;
    }
}
