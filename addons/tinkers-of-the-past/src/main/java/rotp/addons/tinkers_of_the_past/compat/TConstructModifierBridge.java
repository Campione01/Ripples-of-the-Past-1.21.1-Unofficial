package rotp.addons.tinkers_of_the_past.compat;

import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.standarrow.StandArrowItem;
import java.util.Optional;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Dependency-free donor semantics for a future verified TConstruct adapter.
 */
public final class TConstructModifierBridge {
    public static final int STAND_ARROW_COLOR = 0x7E6A30;
    public static final int METEOR_VIRUS_COLOR = 0xDAC352;
    public static final int METEOR_VIRUS_DURATION_TICKS = 40;

    private TConstructModifierBridge() {}

    public static int meteorVirusAmplifier(int modifierLevel) {
        return modifierLevel - 1;
    }

    public static void afterMeteorVirusHit(
            LivingEntity target, int modifierLevel) {
        if (target != null) {
            target.addEffect(new MobEffectInstance(
                    ModStatusEffects.STAND_VIRUS,
                    METEOR_VIRUS_DURATION_TICKS,
                    meteorVirusAmplifier(modifierLevel)));
        }
    }

    public static void afterStandArrowHit(
            Entity target, LivingEntity attacker, Item toolItem) {
        ItemStack arrowContext = new ItemStack(toolItem);
        StandArrowItem.onPiercedByArrow(
                target,
                arrowContext,
                attacker.level(),
                Optional.of(attacker));
    }
}
