package rotp.addons.whitealbum.ability;

import rotp.core.impl.stands._entitybase.StandEntityBlockAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * 1.16 wa_block (FrozenBlock) was a plain held StandAction, not a Stand
 * entity action: its heldAllowsOtherAction stayed false, so
 * PowerBaseImpl.checkRequirements refused every other action of the power
 * while the guard was held. The core input buffer keeps those presses until
 * the guard is let go (owner decision N2); the release still ends it.
 */
public final class WhiteAlbumGuardAbility
        extends StandEntityBlockAbility {
    public WhiteAlbumGuardAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = WhiteAlbumGuard::new;
    }

    public static final class WhiteAlbumGuard
            extends StandEntityBlock {
        public WhiteAlbumGuard(EntityActionType ability) {
            super(ability);
        }

        @Override
        public boolean isHoldTypeStandAction() {
            return false;
        }

        // A hit on the idle controller starts this guard for 5 ticks where
        // 1.16 ran the generic BLOCK_STAND_ENTITY task, which a press replaced.
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return performer instanceof StandEntity stand
                    && stand.isAutoGuarding()
                    && super.canBeCancelledInto(cancellingAbility);
        }
    }
}
