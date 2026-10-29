package rotp.addons.whitesnake.effect;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class WhitesnakeMeltHeartEvents {
    private WhitesnakeMeltHeartEvents() {}

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(
                WhitesnakeAddon.MELT_HEART_EFFECT)) {
            event.setCanceled(true);
        }
    }
}
