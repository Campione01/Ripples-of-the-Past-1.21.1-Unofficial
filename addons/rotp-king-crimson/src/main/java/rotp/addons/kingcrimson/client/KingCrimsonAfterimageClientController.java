package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageRemovePayload;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageSpawnPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public final class KingCrimsonAfterimageClientController {
    private KingCrimsonAfterimageClientController() {
    }

    public static void spawn(
            TimeEraseAfterimageSpawnPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.getEntity(payload.entityId()) != null) {
            return;
        }
        KingCrimsonAfterimageEntity afterimage =
                KingCrimsonAddon.KC_AFTERIMAGE_ENTITY.get()
                        .create(level);
        if (afterimage == null) {
            return;
        }
        afterimage.setId(payload.entityId());
        afterimage.setUUID(payload.entityUuid());
        afterimage.initializeClient(
                payload.originEntityId(),
                payload.originUuid(),
                payload.delayTicks(),
                payload.lifespanTicks());
        // the server sends these only to the Time Erase user, so the red outline is seen by that user alone
        afterimage.showTimeEraseOutline();
        afterimage.moveTo(
                payload.x(),
                payload.y(),
                payload.z(),
                payload.yRot(),
                payload.xRot());
        level.addEntity(afterimage);
    }

    public static void remove(
            TimeEraseAfterimageRemovePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (int entityId : payload.entityIds()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof KingCrimsonAfterimageEntity) {
                level.removeEntity(
                        entityId,
                        Entity.RemovalReason.DISCARDED);
            }
        }
    }
}
