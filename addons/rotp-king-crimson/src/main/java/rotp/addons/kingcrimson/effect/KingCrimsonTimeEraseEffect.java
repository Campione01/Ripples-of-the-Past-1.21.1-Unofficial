package rotp.addons.kingcrimson.effect;

import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class KingCrimsonTimeEraseEffect
        extends StatusEffectModified {
    public static final int COLOR = 0xFF0000;

    public KingCrimsonTimeEraseEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
        setUncurable();
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                KingCrimsonAddon.id(
                        "effect.time_erase.movement_speed"),
                KingCrimsonPhase5Spec
                        .TIME_ERASE_MOVEMENT_MULTIPLIER,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        entity.resetFallDistance();
        Vec3 movement = entity.getDeltaMovement();
        if (movement.y
                < KingCrimsonPhase5Spec.TIME_ERASE_MAX_FALL_SPEED) {
            entity.setDeltaMovement(
                    movement.x,
                    KingCrimsonPhase5Spec.TIME_ERASE_MAX_FALL_SPEED,
                    movement.z);
        }
        return true;
    }
}
