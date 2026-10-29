package rotp.addons.killerqueen;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

import rotp.addons.killerqueen.state.KillerQueenStateService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public class KillerQueenStandType extends EntityStandType {
    // Kept for data-driven Stands that use Killer Queen as their base.
    public KillerQueenStandType(
            StandStats stats,
            MovesetBuilder moveset,
            ResourceLocation id) {
        super(stats, moveset, id);
    }

    public KillerQueenStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> entityType,
            ResourceLocation id) {
        super(stats, moveset, entityType, id);
    }

    // 1.16 kept the block bomb on the Stand entity, and every unsummon
    // (a plain unsummon ends here too) removed it, dropping the bomb.
    @Override
    public void forceUnsummon(LivingEntity user, StandPower standPower) {
        boolean hadStand = !user.level().isClientSide()
                && standPower.getSummonedStandEntity() != null;
        super.forceUnsummon(user, standPower);
        if (hadStand) {
            KillerQueenStateService.onStandCleared(user);
        }
    }

    // Removing or replacing the Stand (stand remove, stand give ... true)
    // drops the block bomb, as losing the 1.16 Stand entity did.
    @Override
    public StandTypePersistentData newDataInstance() {
        return new KillerQueenStandData(this);
    }

    private static final class KillerQueenStandData
            extends StandTypePersistentData {
        private KillerQueenStandData(KillerQueenStandType powerType) {
            super(powerType);
        }

        @Override
        public void onPowerCleared(
                Power<?> userPower, PowerType newType) {
            super.onPowerCleared(userPower, newType);
            KillerQueenStateService.onStandCleared(userPower.getUser());
        }
    }
}
