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
import rotp.addons.pearljam.state.PearlJamPlayerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class PearlJamToggleAbility
        extends EntityActionAbility {
    public static final int WINDUP_TICKS = 5;
    public static final int COOLDOWN_TICKS = 3;

    private final Setting setting;

    public PearlJamToggleAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Setting setting) {
        super(abilityType, abilityId, ToggleAction::new);
        this.setting = setting;
        setDefaultPhaseLength(ActionPhase.WINDUP, WINDUP_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        cooldown(COOLDOWN_TICKS);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return null;
    }

    // 1.16 pj_craft_passive, pj_hand_toggle and pj_secret_by_chef (057
    // CraftPassive, HandToggle, SecretFood, InitStands :47-49) were
    // StandActions their user's power held and performed, the Stand out or
    // not
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

    // 1.16 holdToFire(5, false) held the action until it fired, so a
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
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        return user instanceof Player
                && PearlJamAddon.isPearlJamUser(user)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    @Override
    public Component getName(Power<?> context) {
        return abilityName(
                context,
                setting.namePostfix(state(context)));
    }

    @Override
    public String getSpriteName(Power<?> context) {
        if (setting != Setting.HAND) {
            return super.getSpriteName(context);
        }
        return state(context).mainHandSelected()
                ? "pj_hand_toggle_main"
                : "pj_hand_toggle_off";
    }

    private PearlJamPlayerState state(Power<?> context) {
        return context != null
                        && context.getUser() instanceof Player player
                ? PearlJamAddon.state(player)
                : new PearlJamPlayerState();
    }

    private void toggle(ServerPlayer player) {
        PearlJamPlayerState state = PearlJamAddon.state(player);
        switch (setting) {
            case HAND -> state.toggleMainHand();
            case CHEF_SIGNATURE ->
                    state.toggleChefSignature();
            case CRAFT_PASSIVE ->
                    state.toggleCraftPassive();
        }
        PearlJamAddon.syncState(player);
    }

    public enum Setting {
        HAND {
            @Override
            String namePostfix(PearlJamPlayerState state) {
                return state.mainHandSelected()
                        ? ".main"
                        : ".off";
            }
        },
        CHEF_SIGNATURE {
            @Override
            String namePostfix(PearlJamPlayerState state) {
                return state.chefSignatureHidden()
                        ? ".hidden"
                        : ".shown";
            }
        },
        CRAFT_PASSIVE {
            @Override
            String namePostfix(PearlJamPlayerState state) {
                return state.craftPassiveEnabled()
                        ? ".on"
                        : ".off";
            }
        };

        abstract String namePostfix(PearlJamPlayerState state);
    }

    public static final class ToggleAction
            extends EntityActionInstance {
        public ToggleAction(EntityActionType ability) {
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
            if (!level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer owner
                    && ability instanceof PearlJamToggleAbility held) {
                held.setCooldownOnUse(StandPower.get(owner));
            }
            if (!level().isClientSide()
                    && getPowerUser()
                            instanceof ServerPlayer player
                    && PearlJamAddon.isPearlJamUser(player)
                    && ability
                            instanceof PearlJamToggleAbility toggle) {
                toggle.toggle(player);
            }
        }
    }
}
