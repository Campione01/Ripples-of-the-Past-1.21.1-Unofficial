package rotp.addons.d4c.client;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CPhase2BService;
import rotp.addons.d4c.network.AnnihilationMarkPayload;
import rotp.addons.d4c.network.DevilPalmMarkerPayload;
import rotp.addons.d4c.network.SaintCorpseXrayGlowPayload;
import rotp.core.subsystems.entityglow.EntityGlowChannel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client side of the Saint Corpse X-ray outline (donor {@code com.mrlang.rotp_d4c.ak.aa.aa.ac}) and the Devil's Palm
 * sky marker placed by the Corpse Finder (donor {@code al.ac}). Both are driven by the server through payloads; the
 * X-ray and the finder themselves are wheel abilities.
 */
@EventBusSubscriber(modid = D4CAddon.MOD_ID, value = Dist.CLIENT)
public final class D4CSaintCorpseClient {
    /** The glow is refreshed every 20 ticks by the server; a little slack keeps it continuous. */
    private static final int XRAY_GLOW_TICKS = 30;
    private static final double MARKER_HEIGHT = 35.0D;

    private static final Map<BlockPos, Particle> MARKERS = new HashMap<>();

    private D4CSaintCorpseClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        MARKERS.values().removeIf(particle -> particle == null || !particle.isAlive());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MARKERS.clear();
        D4CPhase2BService.acceptClientMark(AnnihilationMarkPayload.NONE);
    }

    /** Outline (or stop outlining) the listed living entities. */
    public static void acceptXrayGlow(SaintCorpseXrayGlowPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (int id : payload.entityIds()) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            // The core's life-detector channel is the client-only "seen through walls" outline; D4C may not ship
            // mixins of its own. No colour = the vanilla white outline the donor's setGlowing produced.
            if (payload.glowing()) {
                EntityGlowChannel.GE_LIFE_DETECTOR.apply(living, OptionalInt.empty(), XRAY_GLOW_TICKS);
            }
            else {
                EntityGlowChannel.GE_LIFE_DETECTOR.clear(living);
            }
        }
    }

    /** One devil_sky marker per structure, 35 blocks above it. */
    public static void acceptDevilPalmMarker(DevilPalmMarkerPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || MARKERS.containsKey(payload.pos())) {
            return;
        }
        BlockPos pos = payload.pos();
        Particle particle = mc.particleEngine.createParticle(
                D4CAddon.DEVIL_SKY.get(),
                pos.getX() + 0.5D,
                pos.getY() + MARKER_HEIGHT,
                pos.getZ() + 0.5D,
                0.0D, 0.0D, 0.0D);
        if (particle != null) {
            MARKERS.put(pos.immutable(), particle);
        }
    }
}
