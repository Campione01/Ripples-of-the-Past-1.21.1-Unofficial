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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class CMoonHeavenRiteAbility
        extends StandEntityAbility {
    public static final int TARGET_Y = 125;
    public static final double TARGET_RADIUS = 5.0D;
    public static final float STAMINA_PER_TICK = 1.0F;

    public CMoonHeavenRiteAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(
                abilityType,
                abilityId,
                CMoonRiteAction::new);
        partsRequired(StandPart.ARMS);
        resolveLevelToUnlock(4);
        standAutoSummonMode(AutoSummonMode.OFF_ARM);
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, 0);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
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

    // 1.16 held cmoon_heaven (holdType) in PERFORM: tickHeldAction reran
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
    public Component getName(Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        if (user == null) {
            return super.getName(context);
        }
        return abilityName(
                context,
                ".param",
                Math.round(user.getX()),
                TARGET_Y,
                Math.round(user.getZ()));
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        LivingEntity user =
                context != null ? context.getUser() : null;
        if (!HeavenEvolutionService.isStandUser(
                user,
                WayToHeavenAddon.CMOON_ID)) {
            return ConditionCheck.createNegative(
                    "no_madeinheaven");
        }
        return super.checkSpecificConditions(context);
    }

    public static final class CMoonRiteAction
            extends EntityActionInstance {
        public CMoonRiteAction(
                EntityActionType ability) {
            super(ability);
        }

        // 1.16 cmoon_heaven was holdType() with no recovery: another Stand
        // action pressed during the rite replaced it at once (isCancelable),
        // and any other action of the power ended the hold
        // (stopHeldAction(false))
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null
                            ? StandPower.get(user)
                            : null;
            if (!HeavenEvolutionService.isStandUser(
                    user, WayToHeavenAddon.CMOON_ID)) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            // 1.16 StandEntityTask paid staminaCostTick(1) and ignored the
            // result: the rite holds on an empty bar
            StandAbilityStamina.consume(
                    ability, power, STAMINA_PER_TICK, true);

            if (Math.abs(user.getY() - TARGET_Y)
                    <= TARGET_RADIUS) {
                if (!HeavenEvolutionService.evolveCMoon(user)) {
                    ConditionCheck.sendActionFailedMessage(
                            ability instanceof Ability abilityObject
                                    ? abilityObject
                                    : null,
                            ConditionCheck.createNegative(
                                    "evolution_failed"),
                            user);
                    forceStop();
                    syncPhaseChanges();
                }
                return;
            }

            user.addEffect(new MobEffectInstance(
                    MobEffects.LEVITATION,
                    10,
                    3,
                    false,
                    false,
                    false));
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.ELECTRIC_SPARK,
                        user.getX(),
                        user.getY() + 1.0D,
                        user.getZ(),
                        30,
                        0.5D,
                        0.5D,
                        0.5D,
                        1.0D);
            }
        }

        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.PERFORM;
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
