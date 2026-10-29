package rotp.addons.whitesnake.blindness;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class WhitesnakeBlindnessEvents {
    private WhitesnakeBlindnessEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()
                || !living.hasData(WhitesnakeAddon.BLINDNESS_STATE)) {
            return;
        }
        BlindnessState state = WhitesnakeAddon.blindnessState(living);
        if (!state.isActive()) {
            return;
        }
        if (!living.hasEffect(
                        net.minecraft.world.effect.MobEffects.BLINDNESS)) {
            state.clear(living, false);
        }
        else if (!WhitesnakeAddon.hasWhitesnakePower(living)) {
            state.clear(living, true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(
            EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            clear(living);
        }
    }

    private static void clear(LivingEntity living) {
        if (!living.level().isClientSide()
                && living.hasData(WhitesnakeAddon.BLINDNESS_STATE)) {
            WhitesnakeAddon.blindnessState(living)
                    .clear(living, true);
        }
    }
}
