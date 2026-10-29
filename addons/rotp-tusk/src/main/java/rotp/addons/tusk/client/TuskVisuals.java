package rotp.addons.tusk.client;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.tusk.TuskAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

final class TuskVisuals {
    private TuskVisuals() {}

    @Nullable
    static StandVisualContext forOwner(
            LivingEntity owner,
            float partialTick) {
        StandPower power = owner != null
                ? StandPower.get(owner)
                : null;
        StandEntity controller = power != null
                && TuskAddon.actIndex(power.getPowerType())
                        >= TuskAddon.ACT_1
                && power.isSummoned()
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand
                ? stand
                : null;
        if (controller == null || !controller.isAlive()) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff != null
                ? controller.clientStuff.getAlpha(
                        controller, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }

    @Nullable
    static StandVisualContext forOwnerUuid(
            Level level,
            UUID ownerUuid,
            float partialTick) {
        Player owner = level != null && ownerUuid != null
                ? level.getPlayerByUUID(ownerUuid)
                : null;
        return forOwner(owner, partialTick);
    }
}
