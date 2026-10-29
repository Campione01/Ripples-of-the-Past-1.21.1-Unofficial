package rotp.addons.stickyfingers.ability;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;

import rotp.addons.stickyfingers.storage.ZipperStorageData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class StickyFingersOpenInnerStorageAbility
        extends NoPoseStandEntityAbility {
    public StickyFingersOpenInnerStorageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                OpenInnerStorageAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
    }

    public static final class OpenInnerStorageAction
            extends EntityActionInstance {
        public OpenInnerStorageAction(
                EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level() instanceof ServerLevel serverLevel
                    && getPowerUser()
                            instanceof ServerPlayer user) {
                ZipperStorageData
                        .get(serverLevel.getServer())
                        .openEntityStorage(user, user, true);
            }
        }
    }
}
