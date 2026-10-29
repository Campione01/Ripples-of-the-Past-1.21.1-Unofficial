package rotp.addons.heavensdoor.state;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Side-effect-free forgotten-action predicates shared by donor event behavior
 * and the public core policy bindings.
 */
public final class HeavensDoorRestrictionRules {
    private HeavensDoorRestrictionRules() {}

    public static boolean blocksContainerOpen(LivingEntity user) {
        return user.hasEffect(HeavensDoorAddon.FORGOT_CONTAINERS);
    }

    public static boolean blocksCraftingResult(LivingEntity user) {
        return user.hasEffect(HeavensDoorAddon.FORGOT_CRAFT);
    }

    public static boolean blocksPoweredLeap(LivingEntity user) {
        if (user.hasEffect(HeavensDoorAddon.FORGOT_JUMP) || CannotMoveRules.blocksSelf(user)) {
            return true;
        }
        MobEffectInstance book =
                user.getEffect(HeavensDoorAddon.BOOK_EFFECT);
        return book != null && book.getAmplifier() >= 1;
    }
}
