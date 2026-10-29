package rotp.addons.hermitpurple.ability;

import rotp.core.powersystem.Power;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import rotp.addons.hermitpurple.HermitHamon;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSkins;
import rotp.addons.hermitpurple.entity.HermitPurpleStandEntity;
import net.minecraft.world.entity.LivingEntity;

public final class HermitBreathAbility
        extends NoPoseStandEntityAbility {
    public HermitBreathAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, BreathAction::new);
        usageGroup = AbilityUsageGroup.UTILITY;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 120);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        setButtonHoldPhase(ActionPhase.PERFORM);
    }

    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        LivingEntity user = context.getUser();
        HermitPurpleStandEntity stand =
                HermitPurpleAddon.controller(user);
        if (user != null
                && abilities != null
                && stand != null
                && stand.getAttachedGrabVine() != null
                && HermitHamon.hasSkill(
                        user,
                        ModHamonSkills.SCARLET_OVERDRIVE.get())
                && HermitHamon.hasSkill(
                        user,
                        ModHamonSkills
                                .THROWABLES_INFUSION
                                .get())) {
            Ability scarlet = abilities.getContextVariation(
                    HermitPurpleAddon.HP_GRAB_SC_NAME);
            if (scarlet != null) {
                return scarlet;
            }
        }
        return this;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck check =
                super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        return checkBreath(context.getUser());
    }

    // 1.16 PowerBaseImpl.tickHeldAction reran HamonBreath
    // .checkSpecificConditions on every held tick: running out of air ends
    // the hold with its message.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return checkBreath(context.getUser());
    }

    private static ConditionCheck checkBreath(LivingEntity user) {
        if (user == null || HermitHamon.data(user).isEmpty()) {
            return ConditionCheck.createNegative("no_hamon");
        }
        return user.getAirSupply() >= user.getMaxAirSupply()
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("hamon_breath");
    }

    public static final class BreathAction
            extends EntityActionInstance {
        public BreathAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.PERFORM
                    && level().isClientSide()) {
                LivingEntity user = getPowerUser();
                if (user != null) {
                    ClientsideSoundsHelper.playLoopingActionSound(
                            ModSoundEvents.HAMON_CONCENTRATION.get(),
                            user,
                            this,
                            ActionPhase.PERFORM,
                            1.0F,
                            1.0F,
                            15);
                }
            }
        }

        @Override
        public void actionPerformStart() {
            LivingEntity user = getPowerUser();
            if (user != null) {
                user.playSound(
                        HermitPurpleAddon.JOSEPH_BREATH.get(),
                        1.0F,
                        1.0F);
                HermitPurpleStandEntity controller =
                        HermitPurpleAddon.controller(user);
                if (HermitPurpleSkins.isJonathan(controller)) {
                    user.playSound(
                            ModSoundEvents.BREATH_JONATHAN.get(),
                            1.0F,
                            1.0F);
                }
            }
        }

        @Override
        public void actionTick() {
            if (!level().isClientSide()
                    && getPhase() == ActionPhase.PERFORM) {
                LivingEntity user = getPowerUser();
                if (user != null) {
                    HermitHamon.data(user).ifPresent(
                            hamon -> hamon.tickHamonBreath(user));
                }
            }
        }

        @Override
        public void onButtonStopHold() {
            forceStop();
            syncPhaseChanges();
        }

        @Override
        public boolean canStaminaRegen(
                StandPower standPower, StandEntity standEntity) {
            return true;
        }

        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return true;
        }

        // 1.16 hp_breath (HamonBreath) was a holdType Stand action
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 StandEntityAction.canClickDuringTask :495-497: not chainable
        @Override
        public boolean refusesOwnAbilityPressedAgain() {
            return true;
        }
    }
}
