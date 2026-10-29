package rotp.addons.d4c;

import rotp.core.network.s2c.TrSetStandEntityPacket;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class D4CStandType extends EntityStandType {
    public D4CStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
    }

    // corpse stamina factors apply to every Stand: D4CCorpseService

    @Override
    protected void playSummonShout(LivingEntity user) {
        if (user != null
                && !user.level().isClientSide()
                && !user.isShiftKeyDown()
                && user.hasEffect(D4CAddon.LOVE_TRAIN)) {
            JojoModUtil.sayVoiceLine(
                    user,
                    D4CAddon.VALENTINE_LOVE_TRAIN.get());
            return;
        }
        super.playSummonShout(user);
    }

    @Override
    public void finalizeStandSummonFromAction(
            LivingEntity user,
            StandPower power,
            StandEntity stand,
            boolean addToWorld) {
        if (user.level().isClientSide()
                || !user.hasEffect(D4CAddon.LOVE_TRAIN)
                || stand.isAddedToLevel()) {
            super.finalizeStandSummonFromAction(
                    user, power, stand, addToWorld);
            return;
        }
        if (!addToWorld) {
            forceUnsummon(user, power);
            return;
        }

        user.level().addFreshEntity(stand);
        stand.playSound(
                D4CAddon.D4C_LOVE_TRAIN_SUMMON.get(),
                1.0F,
                1.0F);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                user,
                new TrSetStandEntityPacket(
                        user.getId(), stand.getId()));
        triggerFullSummonAdvancement(user, stand);
    }
}
