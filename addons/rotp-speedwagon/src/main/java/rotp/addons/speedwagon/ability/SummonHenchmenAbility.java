package rotp.addons.speedwagon.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import rotp.addons.speedwagon.SpeedwagonAddon;
import rotp.addons.speedwagon.entity.AbstractHenchmanEntity;
import rotp.addons.speedwagon.entity.Henchman2Entity;
import rotp.addons.speedwagon.entity.HenchmanEntity;
import rotp.addons.speedwagon.power.SpeedwagonData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public final class SummonHenchmenAbility
        extends SpeedwagonActionAbility {
    public SummonHenchmenAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                50.0F,
                0.0F,
                200,
                SummonHenchmenAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        SpeedwagonData data = getData(context);
        LivingEntity user = context.getUser();
        return data != null
                && user != null
                && !data.hasNearbyHenchmen(user)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "no_more_henchmen");
    }

    public static final class SummonHenchmenAction
            extends EntityActionInstance {
        public SummonHenchmenAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel serverLevel)
                    || !(ability
                            instanceof SummonHenchmenAbility
                                    summonAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            SpeedwagonData data =
                    user != null
                            ? getData(
                                    summonAbility.getUserPower(user))
                            : null;
            if (user == null
                    || data == null
                    || data.hasNearbyHenchmen(user)
                    || !summonAbility.consumeInitialEnergy(user)) {
                forceStop();
                syncPhaseChanges();
                return;
            }

            HenchmanEntity henchman =
                    SpeedwagonAddon.HENCHMAN.get()
                            .create(serverLevel);
            Henchman2Entity henchman2 =
                    SpeedwagonAddon.HENCHMAN2.get()
                            .create(serverLevel);
            spawn(serverLevel, user, data, henchman, 0.0D);
            spawn(serverLevel, user, data, henchman2, 0.75D);
        }

        private static void spawn(
                ServerLevel level,
                LivingEntity owner,
                SpeedwagonData data,
                AbstractHenchmanEntity henchman,
                double xOffset) {
            if (henchman == null) {
                return;
            }
            henchman.moveTo(
                    owner.getX() + xOffset,
                    owner.getY(),
                    owner.getZ(),
                    owner.getYRot(),
                    0.0F);
            henchman.setSummonedBy(owner);
            if (level.addFreshEntity(henchman)) {
                data.trackSummon(owner, henchman);
            }
        }
    }
}
