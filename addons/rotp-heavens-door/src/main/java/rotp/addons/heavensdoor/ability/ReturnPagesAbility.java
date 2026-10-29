package rotp.addons.heavensdoor.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity
        .NoPoseStandEntityAbility;

import rotp.addons.heavensdoor.item.PageItem;
import rotp.addons.heavensdoor.state.BookStateService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class ReturnPagesAbility
        extends NoPoseStandEntityAbility {
    public ReturnPagesAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ReturnPageAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        resolveLevelToUnlock(2);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        // 1.16 ReturnPages was a Stand entity action: the press summoned
        // Heaven's Door and went on; the core summons it after this check.
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        return PageItem.data(user.getMainHandItem()) != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("no_page");
    }

    public static final class ReturnPageAction
            extends EntityActionInstance {
        public ReturnPageAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null
                    && BookStateService.returnHeldPage(user)) {
                return;
            }
            message(user, "rotp_hd.message.no_page_to_return");
            stopAction();
        }

        private void stopAction() {
            forceStop();
            if (!level().isClientSide()) {
                syncPhaseChanges();
            }
        }

        private static void message(
                LivingEntity user, String key) {
            if (user instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable(key), true);
            }
        }
    }
}
