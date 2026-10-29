package rotp.addons.diverdown.client;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

final class DiverDownAdditionalVisuals {
    private DiverDownAdditionalVisuals() {}

    @Nullable
    static StandVisualContext context(
            Level level,
            UUID ownerUuid,
            UUID standUuid,
            float partialTick) {
        DiverDownStandEntity controller =
                summonedDiverDown(level, ownerUuid);
        return controller != null
                        && standUuid != null
                        && standUuid.equals(controller.getUUID())
                ? world(controller, partialTick)
                : null;
    }

    /** The visuals of whichever Diver Down the owner has out: 1.16 kinetic traps kept no Stand of their own. */
    @Nullable
    static StandVisualContext ownerContext(
            Level level, UUID ownerUuid, float partialTick) {
        DiverDownStandEntity controller =
                summonedDiverDown(level, ownerUuid);
        return controller != null
                ? world(controller, partialTick)
                : null;
    }

    @Nullable
    private static DiverDownStandEntity summonedDiverDown(
            Level level, UUID ownerUuid) {
        Player owner = level != null && ownerUuid != null
                ? level.getPlayerByUUID(ownerUuid)
                : null;
        StandPower power =
                owner != null ? StandPower.get(owner) : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType()
                        == DiverDownAddon.DIVER_DOWN.get()
                && power.getSummonedStandEntity()
                        instanceof DiverDownStandEntity stand
                && stand.getUser() == owner
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand
                : null;
    }

    private static StandVisualContext world(
            DiverDownStandEntity controller, float partialTick) {
        float lifecycleAlpha = controller.clientStuff != null
                ? controller.clientStuff.getAlpha(
                        controller, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }
}
