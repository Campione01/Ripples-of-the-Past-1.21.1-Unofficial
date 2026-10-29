package rotp.addons.killerqueen.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.killerqueen.state.KillerQueenState;

/**
 * 1.16 BitesZaDustHandler.userToVictim press gate: while Bites the Dust is armed the
 * Killer Queen user cannot punch, barrage, heavy punch, guard or use the bomb kit
 * (kq_entitybomb, the item bomb and the time-mark moves stay usable).
 */
public final class BitesTheDustKitLock {
    private BitesTheDustKitLock() {}

    public static boolean locked(Power<?> context) {
        return context != null
                && KillerQueenState.bitesTheDustLocksKit(
                        context.getUser());
    }

    // 1.16 tickHeldAction re-ran the KQ action's checkConditions on every
    // held tick, and it asked for nothing but the armed Bites the Dust
    static ConditionCheck heldCheck(Power<?> context) {
        return locked(context)
                ? ConditionCheck.NEGATIVE
                : ConditionCheck.POSITIVE;
    }

    /** 1.16 KQNormalPunch. */
    public static final class Punch extends StandEntityPunchAbility {
        public Punch(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return locked(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }
    }

    /** 1.16 KQBarrage. */
    public static final class Barrage extends StandEntityBarrageAbility {
        public Barrage(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
            // KQBarrage.checkConditions skipped StandAction's part check
            partsRequired();
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return locked(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }

        @Override
        protected ConditionCheck checkHeldSpecificConditions(
                EntityActionInstance action, Power<?> context) {
            return heldCheck(context);
        }
    }

    /** 1.16 KQHeavyPunch. */
    public static final class HeavyPunch
            extends StandEntityHeavyPunchAbility {
        public HeavyPunch(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return locked(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }
    }

    /** The port's charged heavy punch, locked like KQHeavyPunch. */
    public static final class HeavyCharged
            extends StandEntityHeavyPunchChargedAbility {
        public HeavyCharged(
                AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return locked(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }

        @Override
        protected ConditionCheck checkHeldSpecificConditions(
                EntityActionInstance action, Power<?> context) {
            ConditionCheck check = heldCheck(context);
            return check.isPositive()
                    ? super.checkHeldSpecificConditions(action, context)
                    : check;
        }
    }

    /** 1.16 KQBlock. */
    public static final class Guard extends StandEntityBlockAbility {
        public Guard(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
            // KQBlock.checkConditions skipped StandAction's part check
            partsRequired();
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return locked(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }

        @Override
        protected ConditionCheck checkHeldSpecificConditions(
                EntityActionInstance action, Power<?> context) {
            return heldCheck(context);
        }
    }
}
