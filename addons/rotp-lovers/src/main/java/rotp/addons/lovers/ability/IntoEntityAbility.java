package rotp.addons.lovers.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.lovers.LoversAddon;
import rotp.addons.lovers.entity.LoversStandEntity;
import rotp.addons.lovers.state.LoversLinkService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class IntoEntityAbility
        extends NoPoseStandEntityAbility {
    public static final float STAMINA_COST = 100.0F;
    public static final int COOLDOWN_TICKS = 6;

    public IntoEntityAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, IntoEntityAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        partsRequired(StandPart.MAIN_BODY);
        cooldown(COOLDOWN_TICKS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (!(user instanceof Player)) {
            return ConditionCheck.NEGATIVE;
        }
        LoversTargeting.Pressed pressed = LoversTargeting.pressTarget(
                this, user, LoversAddon.activeStand(user), user.level());
        return pressed.target() != null ? check : pressed.check();
    }

    // 1.16 IntoEntity.getTargetRequirement: ENTITY
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY;
    }

    public static final class IntoEntityAction
            extends EntityActionInstance {
        public IntoEntityAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            LivingEntity user = getPowerUser();
            if (user != null) {
                captureActionTargetFromAim(user);
            }
        }

        // the entity the press took, which 1.16's task kept
        @Override
        protected ActionTarget pressedTaskTarget() {
            LivingEntity user = getPowerUser();
            if (user == null) {
                return ActionTarget.EMPTY;
            }
            LivingEntity target = targetFromSnapshot(user);
            if (target == null) {
                target = LoversTargeting.findLookTarget(
                        ability,
                        user,
                        getPerformer() instanceof StandEntity stand
                                ? stand
                                : null,
                        level());
            }
            return target != null
                    ? new ActionTarget(target)
                    : ActionTarget.EMPTY;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)
                    || !(getPerformer()
                            instanceof LoversStandEntity stand)
                    || LoversAddon.activeStand(owner) != stand) {
                return;
            }

            LivingEntity target = targetFromSnapshot(owner);
            if (target == null) {
                target = LoversTargeting.findLookTarget(
                        ability, owner, stand, level());
            }
            if (target == null) {
                return;
            }

            LoversLinkService.LinkResult result =
                    LoversLinkService.toggleLink(
                            owner, stand, target);
            String message = switch (result) {
                case ATTACHED ->
                        "rotp_lovers.message.attached";
                case RETRACTED ->
                        "rotp_lovers.message.retracted";
                case TARGET_OCCUPIED ->
                        "rotp_lovers.message.target_occupied";
                case INVALID ->
                        "rotp_lovers.message.invalid_target";
            };
            owner.displayClientMessage(
                    result == LoversLinkService.LinkResult.ATTACHED
                            ? Component.translatable(
                                    message,
                                    target.getDisplayName())
                            : Component.translatable(message),
                    false);
            // 1.16 StandEntityTask paid staminaCost(100) after standPerform and ignored the result
            StandAbilityStamina.consume(
                    ability,
                    StandPower.get(owner),
                    STAMINA_COST,
                    false);
        }

        private LivingEntity targetFromSnapshot(
                LivingEntity user) {
            return LoversTargeting.takenTarget(
                    user,
                    getPerformer() instanceof StandEntity stand
                            ? stand
                            : null,
                    getActionTargetSnapshot(level()),
                    level());
        }
    }
}
