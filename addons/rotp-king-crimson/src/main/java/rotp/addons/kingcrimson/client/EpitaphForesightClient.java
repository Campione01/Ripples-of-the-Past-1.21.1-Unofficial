package rotp.addons.kingcrimson.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import rotp.addons.kingcrimson.network.EpitaphForesightPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of Epitaph's vision for the local user: a red image of each nearby creature stands where Epitaph
 * foresees it, and the foreseen flight of each projectile is traced in red dust (brighter when it reaches the user).
 * Images are client-only afterimage entities in a reserved negative id range and vanish once Epitaph stops or the
 * creature leaves the foreseen future.
 */
public final class EpitaphForesightClient {
    private static final int VISION_ID_BASE = -0x40000000;
    private static final DustParticleOptions FLIGHT =
            new DustParticleOptions(new Vector3f(0.75F, 0.05F, 0.08F), 0.7F);
    private static final DustParticleOptions FLIGHT_TO_USER =
            new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 1.4F);
    private static boolean visionShown;

    private EpitaphForesightClient() {}

    public static void accept(EpitaphForesightPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        if (!isArmed(player, level)) {
            clear(level);
            return;
        }
        for (EpitaphForesightPayload.Creature creature : payload.creatures()) {
            int visionId = VISION_ID_BASE - creature.entityId();
            KingCrimsonAfterimageEntity image = level.getEntity(visionId)
                    instanceof KingCrimsonAfterimageEntity existing && existing.isFutureVision()
                            ? existing
                            : null;
            if (image == null) {
                image = KingCrimsonAddon.KC_AFTERIMAGE_ENTITY.get().create(level);
                if (image == null) {
                    continue;
                }
                image.setId(visionId);
                image.initializeFutureVision(creature.entityId(), creature.entityUuid());
                image.refreshFutureVision(creature.foreseen());
                level.addEntity(image);
            }
            else {
                image.refreshFutureVision(creature.foreseen());
            }
            visionShown = true;
        }
        for (EpitaphForesightPayload.Flight flight : payload.flights()) {
            DustParticleOptions dust = flight.hitsUser() ? FLIGHT_TO_USER : FLIGHT;
            List<Vec3> points = flight.points();
            for (int i = 1; i < points.size(); i++) {
                Vec3 from = points.get(i - 1);
                Vec3 to = points.get(i);
                for (int step = 0; step < 3; step++) {
                    Vec3 at = from.lerp(to, step / 3.0D);
                    level.addParticle(dust, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    /** Called every client tick: removes the vision once the local Epitaph is no longer armed. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!visionShown || minecraft.level == null) {
            return;
        }
        if (minecraft.player == null || !isArmed(minecraft.player, minecraft.level)) {
            clear(minecraft.level);
        }
    }

    private static boolean isArmed(LocalPlayer player, ClientLevel level) {
        return player.hasData(KingCrimsonAddon.EPITAPH_STATE)
                && KingCrimsonAddon.epitaphState(player).isArmed(level.getGameTime());
    }

    private static void clear(ClientLevel level) {
        if (!visionShown) {
            return;
        }
        List<Entity> images = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof KingCrimsonAfterimageEntity image && image.isFutureVision()) {
                images.add(entity);
            }
        }
        for (Entity image : images) {
            level.removeEntity(image.getId(), Entity.RemovalReason.DISCARDED);
        }
        visionShown = false;
    }
}
