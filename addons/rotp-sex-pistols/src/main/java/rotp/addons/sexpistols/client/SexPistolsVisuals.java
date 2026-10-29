package rotp.addons.sexpistols.client;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class SexPistolsVisuals {
    private SexPistolsVisuals() {}

    @Nullable
    static StandVisualContext sexPistols(
            LivingEntity owner,
            float partialTick) {
        StandPower power = owner != null
                ? StandPower.get(owner)
                : null;
        if (power == null
                || power.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()
                || !power.isSummoned()
                || !owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return null;
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(owner);
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            int entityId = coordinator.childEntityId(number);
            Entity entity = entityId >= 0
                    ? owner.level().getEntity(entityId)
                    : null;
            if (!(entity instanceof SexPistolsEntity member)
                    || !member.isAlive()
                    || member.getUser() != owner) {
                continue;
            }
            float lifecycleAlpha = member.clientStuff != null
                    ? member.clientStuff.getAlpha(
                            member, partialTick)
                    : 1.0F;
            return StandVisualContext.world(
                    member, lifecycleAlpha);
        }
        return null;
    }
}
