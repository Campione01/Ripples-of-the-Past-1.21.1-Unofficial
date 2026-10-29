package rotp.addons.littlefeet;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.littlefeet.scale.LittleFeetScaleService;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(
        modid = LittleFeetAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class LittleFeetEvents {
    private LittleFeetEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                LittleFeetAddon.LITTLE_FEET_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living
                && !living.level().isClientSide()) {
            LittleFeetScaleService.tickLiving(living);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && !event.getLevel().isClientSide()) {
            LittleFeetScaleService.onEntityJoin(living);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && !event.getLevel().isClientSide()) {
            LittleFeetScaleService.clearEntity(living);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            LittleFeetScaleService.clearEntity(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.getEntity().level().isClientSide()) {
            LittleFeetScaleService.clearEntity(event.getOriginal());
            LittleFeetScaleService.clearEntity(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            LittleFeetScaleService.clearEntity(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            LittleFeetScaleService.clearEntity(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            LittleFeetScaleService.clearEntity(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LittleFeetScaleService.clearRuntimeState();
    }
}
