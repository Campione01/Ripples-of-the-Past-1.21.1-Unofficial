package rotp.addons.pearljam.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.pearljam.PearlJamAddon;
import rotp.addons.pearljam.food.PearlJamFoodService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PearlJamUnjectAbility
        extends EntityActionAbility {
    public static final int WINDUP_TICKS = 10;
    public static final int COOLDOWN_TICKS = 3;

    public PearlJamUnjectAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, UnjectAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(COOLDOWN_TICKS);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    // 1.16 pj_unject (057 UnjectFood, InitStands :46) was a StandAction its
    // user's power held and performed, the Stand out or not
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return true;
    }

    // 1.16 StandAction.getPerformer :191-193: the summoned Stand, else the
    // user, whose stun PowerBaseImpl.checkRequirements :264 checked at the
    // press and on every held tick
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null ? stand : user;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        return setOrBufferAction(level, user, getPerformer(user),
                inputMethod, extraClientInput, clickHoldResolveTime,
                bufferingState);
    }

    // 1.16 set the cooldown when the hold fired, so a cancelled hold
    // leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 holdToFire(10, false) held the action until it fired, so a
    // release from any bind still cancels the windup.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.WINDUP
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until that hold ends.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    @Override
    public Component getName(Power<?> context) {
        if (context != null
                && context.getUser() instanceof Player player) {
            ItemStack food =
                    PearlJamFoodService.selectedFood(player);
            if (!food.isEmpty()) {
                return abilityName(
                        context, ".param",
                        food.getHoverName().getString());
            }
        }
        return super.getName(context);
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
        if (!(user instanceof Player player)
                || !PearlJamAddon.isPearlJamUser(user)) {
            return ConditionCheck.NEGATIVE;
        }
        ItemStack food = PearlJamFoodService.selectedFood(player);
        if (!checkFood(player)) {
            return ConditionCheck.createNegative(food.isEmpty()
                    ? "pearl_jam_no_food"
                    : "pearl_jam_not_infused");
        }
        return ConditionCheck.POSITIVE;
    }

    // 1.16 reran checkConditions on every held tick: a food that leaves
    // the hand or loses its dish ends the charge without a message.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        return context != null
                && context.getUser() instanceof Player player
                && PearlJamAddon.isPearlJamUser(player)
                && checkFood(player)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    // 1.16 UnjectFood: an injected or poisoned food.
    private static boolean checkFood(Player player) {
        return PearlJamFoodService.isPrepared(
                PearlJamFoodService.selectedFood(player));
    }

    // 1.16 said the pj_unject shout at the press, when the hold started,
    // so a cancelled hold still shouts and the sneak rule reads the press.
    @Override
    public void initActionFromConfig(EntityActionInstance action,
            Level level, LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, PearlJamAddon.UNJECT_SOUND);
        }
    }

    public static final class UnjectAction
            extends EntityActionInstance {
        public UnjectAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.WINDUP) {
                forceStop();
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer player)
                    || !(ability instanceof PearlJamUnjectAbility held)
                    || !PearlJamAddon.isPearlJamUser(player)
                    || !checkFood(player)) {
                return;
            }
            // 1.16 fired, and set the cooldown, only past the check.
            StandPower power = StandPower.get(player);
            held.setCooldownOnUse(power);
            ItemStack food =
                    PearlJamFoodService.selectedFood(player);
            // The donor restores the food before the clamped stamina debit.
            PearlJamFoodService.unject(player);
            power.consumeStamina(PearlJamFoodService.unjectCost(food));
        }
    }
}
