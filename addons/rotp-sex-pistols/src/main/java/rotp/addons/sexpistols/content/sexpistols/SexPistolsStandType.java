package rotp.addons.sexpistols.content.sexpistols;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.network.s2c.TrNonEntityStandSummonPacket;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.powersystem.standpower.type.SummonedStand;

import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SexPistolsStandType extends StandType {
    public static final String TOPOLOGY = "SWARM_UNIT";

    public SexPistolsStandType(
            StandStats stats,
            MovesetBuilder moveset,
            ResourceLocation id) {
        super(stats, moveset, id);
        nonEntityStandControlPolicy(
                StandControlType.COLONY,
                false,
                false);
    }

    @Override
    protected SummonedStand makeSummonedStand() {
        return new SwarmSummonedStand();
    }

    @Override
    public boolean summon(
            LivingEntity user, StandPower standPower) {
        boolean summonedNow = super.summon(user, standPower);
        if (summonedNow && user.level() instanceof ServerLevel level) {
            SexPistolsAttachments.coordinator(user).activate(
                    level, user, standPower);
        }
        return summonedNow;
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (user.level() instanceof ServerLevel level
                && user.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(user)
                    .beginRecall(level, user);
        }
        super.forceUnsummon(user, standPower);
    }

    // stand remove / stand give ... true: 1.16 dropped the swarm effect
    // with the Stand, so the swarm state starts over.
    @Override
    public StandTypePersistentData newDataInstance() {
        return new SexPistolsStandData(this);
    }

    private static final class SexPistolsStandData
            extends StandTypePersistentData {
        private SexPistolsStandData(SexPistolsStandType powerType) {
            super(powerType);
        }

        @Override
        public void onPowerCleared(
                Power<?> userPower, @Nullable PowerType newType) {
            super.onPowerCleared(userPower, newType);
            LivingEntity user = userPower.getUser();
            if (user != null
                    && user.level() instanceof ServerLevel level
                    && user.hasData(
                            SexPistolsAttachments
                                    .SEX_PISTOLS_COORDINATOR)) {
                SexPistolsAttachments.coordinator(user)
                        .resetForStandCleared(level, user);
            }
        }
    }

    public void restoreAfterLoad(
            LivingEntity owner, StandPower standPower) {
        if (standPower.isSummoned()
                || owner.level().isClientSide()
                || standPower.getPowerType() != this) {
            return;
        }
        standPower.setSummonedStand(makeSummonedStand());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                owner,
                new TrNonEntityStandSummonPacket(
                        owner.getId(), true));
    }

    // No recall and no unsummon sound: the last released member is gone.
    public void putAwayQuietly(
            LivingEntity owner, StandPower standPower) {
        if (!standPower.isSummoned()
                || owner.level().isClientSide()
                || standPower.getPowerType() != this) {
            return;
        }
        standPower.setSummonedStand(null);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                owner,
                new TrNonEntityStandSummonPacket(
                        owner.getId(), false));
    }

    private static final class SwarmSummonedStand
            implements SummonedStand {
        @Nullable
        private LivingEntity owner;
        @Nullable
        private StandPower standPower;
        private Optional<ResourceLocation> skin = Optional.empty();

        @Override
        public void setUserAndPower(
                LivingEntity user, StandPower power) {
            owner = user;
            standPower = power;
        }

        @Override
        public void tickStand(
                LivingEntity user, StandPower userStand) {
            owner = user;
            standPower = userStand;
        }

        @Override
        @Nullable
        public StandEntity getStandEntity() {
            return null;
        }

        @Override
        public void setSelectedSkin(
                Optional<ResourceLocation> selectedSkin) {
            skin = selectedSkin != null
                    ? selectedSkin
                    : Optional.empty();
        }
    }
}
