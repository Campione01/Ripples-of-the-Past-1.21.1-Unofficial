package rotp.addons.pearljam;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.pearljam.food.PearlJamFoodService;
import rotp.addons.pearljam.food.PearlJamHealthBoostCompat;
import rotp.addons.pearljam.state.PearlJamPlayerState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = PearlJamAddon.MOD_ID)
public final class PearlJamEvents {
    private PearlJamEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                PearlJamAddon.PEARL_JAM_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onFoodUseTick(
            LivingEntityUseItemEvent.Tick event) {
        PearlJamFoodService.spawnUseParticle(
                event.getEntity(), event.getItem());
    }

    @SubscribeEvent
    public static void onFoodFinished(
            LivingEntityUseItemEvent.Finish event) {
        if (!event.getEntity().level().isClientSide()) {
            PearlJamFoodService.applyOnEating(
                    event.getEntity(), event.getItem());
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof LivingEntity living) {
            PearlJamHealthBoostCompat.applyActiveModifiers(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()
                || player.tickCount % 20 != 0
                || !player.hasData(PearlJamAddon.PLAYER_STATE)) {
            return;
        }
        PearlJamPlayerState state = PearlJamAddon.state(player);
        boolean changed = state.expire(
                player.level().getGameTime());
        if (!PearlJamAddon.isPearlJamUser(player)) {
            changed |= state.clearStandState();
        }
        if (changed) {
            PearlJamAddon.syncState(player);
        }
    }
}
