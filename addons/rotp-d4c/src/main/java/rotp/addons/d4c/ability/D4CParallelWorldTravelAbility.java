package rotp.addons.d4c.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.d4c.D4CPhase2ASpec;
import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.travel.D4CDimensionTravelService;
import rotp.addons.d4c.travel.D4CDimensionTravelService.TravelPlan;
import rotp.addons.d4c.travel.D4CDimensionTravelService.TravelPlanResult;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class D4CParallelWorldTravelAbility
        extends StandEntityAbility {
    public D4CParallelWorldTravelAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(abilityType, abilityId, TravelAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                D4CPhase2ASpec.TRAVEL_CHARGE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        cooldown(
                D4CPhase2ASpec.TRAVEL_TECHNICAL_COOLDOWN_TICKS,
                D4CPhase2ASpec.TRAVEL_ADDITIONAL_COOLDOWN_TICKS,
                0.0F);
        resolveLevelToUnlock(D4CPhase2ASpec.TRAVEL_RESOLVE_LEVEL);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        if (user == null
                || !D4CDimensionTravelService.isCaughtBetween(
                        user.level(), user)) {
            return ConditionCheck.createNegative(Component.translatable(
                    "rotp_d4c.message.not_caught_between"));
        }
        if (user instanceof ServerPlayer player) {
            Component failure =
                    D4CDimensionTravelService.validateSelectedTarget(player);
            if (failure != null) {
                return ConditionCheck.createNegative(failure);
            }
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 tickHeldAction re-ran aa/ai.java:107-132 on every held tick:
    // a user no longer caught between blocks stopped the charge
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        LivingEntity user = context.getUser();
        return user != null
                && D4CDimensionTravelService.isCaughtBetween(
                        user.level(), user)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(Component.translatable(
                        "rotp_d4c.message.not_caught_between"));
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    public static final class TravelAction extends EntityActionInstance {
        private boolean performed;

        public TravelAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (performed) {
                return;
            }
            performed = true;
            LivingEntity user = getPowerUser();
            if (level().isClientSide()
                    || !(user instanceof ServerPlayer player)
                    || !(ability
                            instanceof D4CParallelWorldTravelAbility
                            travel)) {
                return;
            }

            StandPower power = StandPower.get(player);
            TravelPlanResult result =
                    D4CDimensionTravelService.planTravel(player);
            if (!result.successful()) {
                ConditionCheck.sendActionFailedMessage(
                        travel,
                        ConditionCheck.createNegative(result.failure()),
                        player);
                clearAction();
                return;
            }
            TravelPlan plan = result.plan();
            boolean portalPlaced =
                    D4CDimensionTravelService.placePortal(
                            player.serverLevel(),
                            player,
                            plan.targetId());
            if (!portalPlaced) {
                ConditionCheck.sendActionFailedMessage(
                        travel,
                        ConditionCheck.createNegative(Component.translatable(
                                "rotp_d4c.message.travel_interrupted")),
                        player);
                clearAction();
                return;
            }

            player.serverLevel().playSound(
                    null,
                    player.blockPosition(),
                    D4CAddon.D4C_TRAVERSE.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
            player.serverLevel().playSound(
                    null,
                    player.blockPosition(),
                    D4CAddon.VALENTINE_TRAVEL.get(),
                    net.minecraft.sounds.SoundSource.VOICE,
                    1.0F,
                    1.0F);
            // 1.16 StandEntityTask paid after the perform with the result
            // ignored; a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    power,
                    D4CPhase2ASpec.TRAVEL_STAMINA,
                    false);
            travel.setCooldownOnUse(power);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                clearAction();
            }
        }

        private void clearAction() {
            LivingEntity performer = getPerformer();
            if (performer != null) {
                LivingComponentAction.getComponent(performer)
                        .setAction(null, SyncType.TRACKING_AND_SELF);
            }
        }
    }
}
