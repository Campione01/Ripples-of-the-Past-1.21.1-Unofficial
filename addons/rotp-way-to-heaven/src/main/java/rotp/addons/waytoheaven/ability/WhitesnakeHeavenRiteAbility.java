package rotp.addons.waytoheaven.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.waytoheaven.WayToHeavenAddon;
import rotp.addons.waytoheaven.evolution.HeavenEvolutionService;
import rotp.addons.waytoheaven.item.SinnerBoneItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class WhitesnakeHeavenRiteAbility
        extends StandEntityAbility {
    public static final int HOLD_TICKS = 396;
    public static final float STAMINA_PER_TICK = 1.0F;

    public WhitesnakeHeavenRiteAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                WhitesnakeRiteAction::new);
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(4);
        standAutoSummonMode(AutoSummonMode.OFF_ARM);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, 0);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(
                ActionPhase.PERFORM, HOLD_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        // A ritual keeps its 1.16 half walk speed (owner rule of 2026-10-15,
        // 1.16 InitStands standUserWalkSpeed(0.5f)); the core applies the
        // registered value in every phase but RECOVERY.
        standUserWalkSpeed(0.5F);
    }

    @Override
    public String getResourceNamespace() {
        return WayToHeavenAddon.MOD_ID;
    }

    // 1.16 held whitesnake_frases (holdToFire(396, false)) in BUTTON_HOLD: tickHeldAction reran
    // checkRequirements every tick (the Stand's stun, its arms, the
    // power), so the rite counts as held through its whole PERFORM.
    @Override
    public boolean isActionHeld(EntityActionInstance action) {
        return action.getPhase() == ActionPhase.PERFORM
                || super.isActionHeld(action);
    }

    // 1.16's client sent no press of a held action while the power held
    // another (ActionsOverlayGui.onActionClick): the press waits in the
    // input buffer until the held guard or barrage is let go.
    @Override
    public boolean isHoldAction() {
        return true;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        if (!HeavenEvolutionService.isStandUser(
                user,
                WayToHeavenAddon.WHITESNAKE_ID)) {
            return ConditionCheck.createNegative(
                    "no_c_moon");
        }
        if (!SinnerBoneItem.isFull(
                user.getOffhandItem())) {
            return ConditionCheck.createNegative(
                    "no_bone");
        }
        return super.checkSpecificConditions(context);
    }

    public static final class WhitesnakeRiteAction
            extends EntityActionInstance {
        private ItemStack riteBone = ItemStack.EMPTY;

        public WhitesnakeRiteAction(
                EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(
                EntityActionInstance previousAction) {
            if (!level().isClientSide()) {
                LivingEntity user = getPowerUser();
                riteBone = user != null
                        ? user.getOffhandItem()
                        : ItemStack.EMPTY;
            }
        }

        @Override
        public void actionPerformStart() {
            if (!level().isClientSide()
                    && getPerformer() != null) {
                getPerformer().playSound(
                        WayToHeavenAddon
                                .PUCCI_FRASES.get(),
                        1.0F,
                        1.0F);
            }
        }

        // 1.16 whitesnake_frases is holdToFire(396, false), and 0.2.2.2
        // took staminaCostTick only in PERFORM: the hold itself costs no
        // stamina, and the 1 is paid once when the rite completes.
        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            ItemStack bone =
                    user != null
                            ? user.getOffhandItem()
                            : ItemStack.EMPTY;
            if (!HeavenEvolutionService.isStandUser(
                            user,
                            WayToHeavenAddon
                                    .WHITESNAKE_ID)
                    || bone != riteBone
                    || !SinnerBoneItem.isFull(bone)) {
                if (user != null) {
                    ConditionCheck.sendActionFailedMessage(
                            ability instanceof Ability abilityObject
                                    ? abilityObject
                                    : null,
                            ConditionCheck.createNegative(
                                    "no_bone"),
                            user);
                }
                forceStop();
                syncPhaseChanges();
                return;
            }

            user.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION,
                    100,
                    4,
                    false,
                    false,
                    false));
            user.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    10,
                    1,
                    false,
                    false,
                    false));
        }

        @Override
        public void actionPerformEnd() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            ItemStack bone =
                    user != null
                            ? user.getOffhandItem()
                            : ItemStack.EMPTY;
            if (user != null) {
                StandAbilityStamina.consume(
                        ability,
                        StandPower.get(user),
                        STAMINA_PER_TICK,
                        true);
            }
            boolean evolved = bone == riteBone
                    && HeavenEvolutionService
                            .evolveWhitesnake(
                                    user, riteBone);
            if (!evolved && user != null) {
                ConditionCheck.sendActionFailedMessage(
                        ability instanceof Ability abilityObject
                                ? abilityObject
                                : null,
                        ConditionCheck.createNegative(
                                "evolution_failed"),
                        user);
            }
        }

        @Override
        public void onButtonStopHold() {
            forceStop();
            if (!level().isClientSide()) {
                syncPhaseChanges();
            }
        }
    }
}
