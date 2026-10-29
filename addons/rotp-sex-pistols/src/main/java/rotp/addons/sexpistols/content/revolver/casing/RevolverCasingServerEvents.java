package rotp.addons.sexpistols.content.revolver.casing;

import rotp.addons.sexpistols.SexPistolsIds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SexPistolsIds.MOD_ID)
public final class RevolverCasingServerEvents {
    private RevolverCasingServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RevolverCasings.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RevolverCasings.deliverCompensation(player);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && RevolverCasings.flushLevel(level)) {
            level.getServer().overworld()
                    .getDataStorage().save();
            IOUtilities.waitUntilIOWorkerComplete();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(
            ServerStoppingEvent event) {
        RevolverCasings.flushServer(event.getServer());
    }
}
