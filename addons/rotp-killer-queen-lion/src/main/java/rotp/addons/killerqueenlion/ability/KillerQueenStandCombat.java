package rotp.addons.killerqueenlion.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 rotp_zkq8 KQNormalPunch, KQBarrage, KQHeavyPunch and KQBlock: the core actions, refused while a Bites the
 * Dust victim is bound. The donor's finisher (KQFinisher) has no such check.
 *
 * <p>The Lion animation file keeps the core clip names (punch, barrage, heavy_punch). An action's default clip id
 * is its moveset name, and neither kq_punch, kq_barrage, kq_heavy_punch nor these types' registry paths have a
 * clip, so each attack names its clip.
 */
public final class KillerQueenStandCombat {
    private KillerQueenStandCombat() {}

    public static final class Punch extends StandEntityPunchAbility {
        public Punch(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
            anim = ActionAnimIdentifier.getOrCreate("punch", false);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return BitesTheDustLock.applies(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }
    }

    public static final class Barrage extends StandEntityBarrageAbility {
        public static final float SWING_VOLUME = 0.25F;

        public Barrage(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId, BarrageAction::new);
            anim = ActionAnimIdentifier.getOrCreate("barrage", false);
            // KQBarrage.checkConditions skipped StandAction's part check
            partsRequired();
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return BitesTheDustLock.applies(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }

        @Override
        protected ConditionCheck checkHeldSpecificConditions(
                EntityActionInstance action, Power<?> context) {
            return BitesTheDustLock.heldCheck(context);
        }

        /** 1.16 StandEntityMeleeBarrage.clTtickSwingSound pitch. */
        public static float swingPitch(double attackDamage, float random) {
            return 1.8F - (float) attackDamage * 0.05F + random * 0.2F;
        }

        public static final class BarrageAction extends StandEntityBarrage {
            public BarrageAction(EntityActionType ability) {
                super(ability);
            }

            @Override
            protected float getBarrageSwingVolume(StandEntity stand) {
                return SWING_VOLUME;
            }

            @Override
            protected float getBarrageSwingPitch(StandEntity stand) {
                return swingPitch(stand.getAttackDamage(), stand.getRandom().nextFloat());
            }
        }
    }

    public static final class HeavyPunch extends StandEntityHeavyPunchAbility {
        public HeavyPunch(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
            anim = ActionAnimIdentifier.getOrCreate("heavy_punch", false);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return BitesTheDustLock.applies(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }
    }

    /** The port's hold form of kq_heavy_punch. */
    public static final class ChargedHeavy extends StandEntityHeavyPunchChargedAbility {
        public ChargedHeavy(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return BitesTheDustLock.applies(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }

        @Override
        protected ConditionCheck checkHeldSpecificConditions(
                EntityActionInstance action, Power<?> context) {
            ConditionCheck check = BitesTheDustLock.heldCheck(context);
            return check.isPositive()
                    ? super.checkHeldSpecificConditions(action, context)
                    : check;
        }
    }

    public static final class Block extends StandEntityBlockAbility {
        public Block(AbilityType<?> abilityType, AbilityId abilityId) {
            super(abilityType, abilityId);
            // KQBlock.checkConditions skipped StandAction's part check
            partsRequired();
        }

        @Override
        public ConditionCheck checkSpecificConditions(Power<?> context) {
            return BitesTheDustLock.applies(context)
                    ? ConditionCheck.NEGATIVE
                    : super.checkSpecificConditions(context);
        }

        @Override
        protected ConditionCheck checkHeldSpecificConditions(
                EntityActionInstance action, Power<?> context) {
            return BitesTheDustLock.heldCheck(context);
        }
    }
}
