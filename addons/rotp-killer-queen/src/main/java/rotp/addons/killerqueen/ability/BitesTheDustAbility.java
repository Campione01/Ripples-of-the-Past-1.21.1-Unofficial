package rotp.addons.killerqueen.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.killerqueen.KillerQueenAddon;
import rotp.addons.killerqueen.KillerQueenPhase1Spec;
import rotp.addons.killerqueen.btd.BitesTheDustService;
import rotp.addons.killerqueen.btd.BitesTheDustService.ResetResult;
import rotp.addons.killerqueen.state.KillerQueenState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// 1.16 InitStands.java:96: kq_reset was ResetDay, a StandEntityAction the
// Stand performed (auto-summoned, standPose DETONATE); time_mark, remov_mark,
// put_dust and quit_dust were StandActions of the user
public final class BitesTheDustAbility
        extends StandEntityAbility {
    public static final int RESET_CHARGE_TICKS = 60;
    public static final int RESET_RECOVERY_TICKS = 1;
    public static final int RESET_COOLDOWN_TICKS = 2_000;
    // 1.16 SelectHayato :65 (020) sent this untranslated line
    static final String STAND_TARGET_LINE = ChatFormatting.DARK_PURPLE
            + "\u00a75<Voidzyy> \u4f60\u4e2a\u795e\u4eba,\u66ff\u8eab"
            + "\u4f7f\u8005\u4e0d\u80fd\u6807\u8bb0,\u5c31\u6807\u8bb0"
            + "\u66ff\u8eab?\u7ed9\u4f60\u9a73\u56de\u4e86[\u25cb\uff65"
            + "\uff40\u0414\u00b4\uff65 \u25cb]";

    private final Operation operation;

    public BitesTheDustAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Operation operation) {
        super(
                abilityType,
                abilityId,
                type -> new BitesTheDustAction(type, operation));
        this.operation = operation;
        usageGroup = AbilityUsageGroup.SPECIAL;
        noResolveUnlock();
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        if (operation == Operation.RESET_DAY) {
            setDefaultPhaseLength(
                    ActionPhase.BUTTON_CHARGE,
                    RESET_CHARGE_TICKS);
            setDefaultPhaseLength(
                    ActionPhase.RECOVERY,
                    RESET_RECOVERY_TICKS);
            cooldown(RESET_COOLDOWN_TICKS);
        }
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (operation == Operation.RESET_DAY) {
            return super.onKeyPress(level, user, extraClientInput,
                    inputMethod, clickHoldResolveTime, bufferingState);
        }
        if (level.isClientSide()) {
            return null;
        }
        // 1.16 SelectHayato :62-69: a press at a Stand got this chat line
        // from the server and nothing else
        if (operation == Operation.PUT_DUST
                && user instanceof ServerPlayer player
                && !wouldBufferInput(user)
                && BitesTheDustService.aimedLiving(player)
                        instanceof StandEntity) {
            player.displayClientMessage(
                    Component.literal(STAND_TARGET_LINE), false);
            return null;
        }
        return setOrBufferAction(level, user,
                getPerformer(user), inputMethod, extraClientInput,
                clickHoldResolveTime, bufferingState);
    }

    // 1.16 TimeMarker, RemoveTimeMarker, SelectHayato and RemoveHayato were
    // StandActions its user's power held, never the Stand's task
    @Override
    public boolean performsInUsersStandPowerSlot() {
        return operation != Operation.RESET_DAY;
    }

    // 1.16 StandAction.getPerformer: the summoned Stand, else the user, so
    // the press met the stun of the Stand while it was out. 1.16 refused
    // these presses during a held Stand charge that let none through
    // (kq_entity_explo's 20 ticks, kq_reset's 60): the press, performed by
    // the Stand, waits in the input buffer until that hold ends.
    @Override
    protected LivingEntity getPerformer(LivingEntity user) {
        if (operation == Operation.RESET_DAY) {
            return super.getPerformer(user);
        }
        StandEntity stand = StandUtil.getSummonedStand(user);
        return stand != null ? stand : user;
    }

    // 1.16 kq_reset is holdToFire(60, false) and set its cooldown when the
    // hold fired, so a cancelled reset leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return operation != Operation.RESET_DAY
                && super.shouldSetCooldownOnKeyPress(inputMethod);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (context == null
                || abilities == null
                || !(context.getUser() instanceof Player player)) {
            return this;
        }
        KillerQueenState state = KillerQueenAddon.state(player);
        String replacement = switch (operation) {
            case TIME_MARK -> {
                if (!state.hasBitesTheDustMarker()) {
                    yield null;
                }
                if (state.hasBitesTheDustVictim()) {
                    yield KillerQueenAddon.QUIT_DUST_NAME;
                }
                if (player.isShiftKeyDown()) {
                    yield aimsAtLiving(player)
                            ? KillerQueenAddon.PUT_DUST_NAME
                            : KillerQueenAddon.REMOVE_MARK_NAME;
                }
                yield KillerQueenAddon.RESET_DAY_NAME;
            }
            case RESET_DAY -> player.isShiftKeyDown()
                    ? aimsAtLiving(player)
                            ? KillerQueenAddon.PUT_DUST_NAME
                            : KillerQueenAddon.REMOVE_MARK_NAME
                    : null;
            case REMOVE_MARK -> aimsAtLiving(player)
                    ? KillerQueenAddon.PUT_DUST_NAME
                    : null;
            case PUT_DUST -> state.hasBitesTheDustVictim()
                    ? KillerQueenAddon.QUIT_DUST_NAME
                    : null;
            case QUIT_DUST -> null;
        };
        if (replacement == null) {
            return this;
        }
        Ability variation =
                abilities.getContextVariation(replacement);
        return variation != null ? variation : this;
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        if (context == null
                || !super.isAbilityAvailable(context)
                || !(context.getUser() instanceof Player player)
                || !KillerQueenAddon.isKillerQueenUser(player)) {
            return false;
        }
        KillerQueenState state = KillerQueenAddon.state(player);
        return switch (operation) {
            case TIME_MARK -> true;
            case RESET_DAY, REMOVE_MARK ->
                    state.hasBitesTheDustMarker()
                            && !state.hasBitesTheDustVictim();
            case PUT_DUST -> state.hasBitesTheDustMarker()
                    && !state.hasBitesTheDustVictim()
                    && aimsAtLiving(player);
            case QUIT_DUST -> state.hasBitesTheDustVictim();
        };
    }

    // 1.16 RemoveTimeMarker.replaceAction (020 :47-52): put_dust for any
    // living entity under the mouse target
    private static boolean aimsAtLiving(Player player) {
        return BitesTheDustService.aimedTarget(player).getMainEntity()
                instanceof LivingEntity;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        if (!(context.getUser() instanceof Player player)
                || !KillerQueenAddon.isKillerQueenUser(player)) {
            return ConditionCheck.NEGATIVE;
        }
        KillerQueenState state = KillerQueenAddon.state(player);
        ConditionCheck stateCheck = switch (operation) {
            case TIME_MARK -> !state.hasBitesTheDustMarker()
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.createNegative(
                            "killer_queen_btd_marker_exists");
            case RESET_DAY, REMOVE_MARK ->
                    state.hasBitesTheDustMarker()
                                    && !state.hasBitesTheDustVictim()
                            ? ConditionCheck.POSITIVE
                            : ConditionCheck.createNegative(
                                    "killer_queen_btd_no_marker");
            case PUT_DUST -> state.hasBitesTheDustMarker()
                            && !state.hasBitesTheDustVictim()
                    ? checkAimedVictim(player)
                    : ConditionCheck.createNegative(
                            "killer_queen_btd_invalid_victim");
            case QUIT_DUST -> state.hasBitesTheDustVictim()
                    ? ConditionCheck.POSITIVE
                    : ConditionCheck.createNegative(
                            "killer_queen_btd_no_victim");
        };
        return stateCheck;
    }

    /*
     * 1.16 SelectHayato.checkSpecificConditions (020 :58-80) on the press's
     * target: a Stand was refused with a chat line of the server's alone, so
     * the server lets that press through to onKeyPress, which sends it and
     * starts nothing, while the client refuses it; an entity another user
     * already chose 'bites_za_has'; a Stand user 'is_stand_user'. No living
     * entity in range: 1.16 refused silently, the port says why.
     */
    private static ConditionCheck checkAimedVictim(Player player) {
        LivingEntity aimed = BitesTheDustService.aimedLiving(player);
        if (aimed instanceof StandEntity) {
            return player.level().isClientSide()
                    ? ConditionCheck.NEGATIVE
                    : ConditionCheck.POSITIVE;
        }
        if (aimed != null
                && BitesTheDustService.isOtherUsersVictim(player, aimed)) {
            return ConditionCheck.createNegative("bites_za_has");
        }
        if (aimed != null && BitesTheDustService.isStandUser(aimed)) {
            return ConditionCheck.createNegative("is_stand_user");
        }
        return aimed != null
                        && BitesTheDustService.validVictim(player, aimed)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative(
                        "killer_queen_btd_invalid_victim");
    }

    public enum Operation {
        TIME_MARK,
        RESET_DAY,
        REMOVE_MARK,
        PUT_DUST,
        QUIT_DUST
    }

    private static final class BitesTheDustAction
            extends EntityActionInstance {
        private final Operation operation;

        private BitesTheDustAction(
                EntityActionType ability, Operation operation) {
            super(ability);
            this.operation = operation;
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            // 1.16 SelectHayato.perform took the target its check had
            // passed
            if (operation == Operation.PUT_DUST
                    && !level().isClientSide()
                    && getPowerUser() instanceof Player user) {
                setActionTargetSnapshot(
                        BitesTheDustService.aimedTarget(user));
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer user)) {
                return;
            }
            StandPower power = StandPower.get(user);
            // 1.16 TimeMarker and ResetDay had no stamina condition: the
            // cost is paid and a short bar drops to 0
            if (operation == Operation.TIME_MARK) {
                StandAbilityStamina.consume(
                        ability,
                        power,
                        KillerQueenPhase1Spec.BTD_MARK_STAMINA,
                        false);
            }
            if (operation == Operation.RESET_DAY) {
                StandAbilityStamina.consume(
                        ability,
                        power,
                        KillerQueenPhase1Spec.BTD_RESET_STAMINA,
                        false);
                if (ability instanceof BitesTheDustAbility reset) {
                    reset.setCooldownOnUse(power);
                }
            }

            switch (operation) {
                case TIME_MARK ->
                        BitesTheDustService.markTime(user);
                case RESET_DAY -> {
                    ResetResult result =
                            BitesTheDustService.resetDay(user);
                    if (result != ResetResult.SUCCESS) {
                        ConditionCheck.sendActionFailedMessage(
                                ability instanceof Ability value
                                        ? value
                                        : null,
                                ConditionCheck.createNegative(
                                        result.conditionKey()),
                                user);
                    }
                }
                case REMOVE_MARK -> {
                    if (BitesTheDustService
                            .removeTimeMarker(user)) {
                        level().playSound(
                                null,
                                user.blockPosition(),
                                KillerQueenAddon
                                        .BITES_THE_DUST.get(),
                                SoundSource.PLAYERS,
                                1.0F,
                                1.0F);
                    }
                }
                case PUT_DUST -> {
                    ActionTarget target =
                            getActionTargetSnapshot(level());
                    LivingEntity victim =
                            BitesTheDustService
                                    .targetedVictim(user, target);
                    if (victim != null) {
                        BitesTheDustService.selectVictim(
                                user, victim);
                    }
                }
                case QUIT_DUST ->
                        BitesTheDustService.removeVictim(user);
            }
        }

        @Override
        public void onButtonStopHold() {
            if (operation == Operation.RESET_DAY
                    && getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
            }
        }
    }
}
