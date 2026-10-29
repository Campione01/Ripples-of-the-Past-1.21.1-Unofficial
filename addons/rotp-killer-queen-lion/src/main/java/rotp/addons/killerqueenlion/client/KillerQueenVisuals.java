package rotp.addons.killerqueenlion.client;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

final class KillerQueenVisuals {
    private KillerQueenVisuals() {}

    @Nullable
    static StandVisualContext context(
            Level level,
            UUID ownerUuid,
            UUID standUuid,
            float partialTick) {
        Player owner = level != null && ownerUuid != null
                ? level.getPlayerByUUID(ownerUuid)
                : null;
        StandPower power =
                owner != null ? StandPower.get(owner) : null;
        StandEntity controller = power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType()
                        == KillerQueenLionAddon.KILLER_QUEEN.get()
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand
                && standUuid != null
                && standUuid.equals(stand.getUUID())
                && stand.getUser() == owner
                && stand.isAlive()
                && !stand.isRemoved()
                        ? stand
                        : null;
        if (controller == null) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff != null
                ? controller.clientStuff.getAlpha(
                        controller, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }
}
