package rotp.addons.cmoon.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.client.CMoonClientSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CenterOfGravityAbility
        extends StandEntityAbility {
    public static final float REQUIRED_STAMINA = 100.0F;
    public static final float STAMINA_PER_TICK = 2.0F;
    public static final int PERFORM_TICKS = 100;
    public static final int HOLD_TO_FIRE_TICKS = 10;
    public static final double RADIUS = 8.0D;
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate(
                    "gravity_change", false);

    public CenterOfGravityAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, CenterAction::new);
        // 1.16 holdToFire(10, false), no recovery.
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE, HOLD_TO_FIRE_TICKS);
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        partsRequired(StandPart.MAIN_BODY);
        standAutoSummonMode(AutoSummonMode.FULL);
        resolveLevelToUnlock(2);
        // 1.16 cooldown(150, 125): Resolve drops the 125.
        cooldown(150, 125);
    }

    // 1.16 set the cooldown when the hold fired, so a cancelled hold
    // leaves none.
    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 CenterOfGravity.checkStandConditions: below 100 stamina a plain
    // NEGATIVE, so no message. 1.16 asked it again on every tick of the
    // 10-tick hold.
    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        return !base.isPositive()
                ? base
                : checkStamina(context);
    }

    private static ConditionCheck checkStamina(Power<?> context) {
        return ConditionCheck.noMessage(
                context instanceof StandPower power
                        && power.getStamina() >= REQUIRED_STAMINA);
    }

    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        return action.getPhase() == ActionPhase.BUTTON_CHARGE
                ? checkStamina(context)
                : ConditionCheck.POSITIVE;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user,
            FriendlyByteBuf extraClientInput, InputMethod inputMethod,
            float clickHoldResolveTime, BufferingState bufferingState) {
        if (level.isClientSide()) {
            return null;
        }
        StandPower power = StandPower.get(user);
        if (power == null || power.getStamina() < REQUIRED_STAMINA) {
            return null;
        }
        return super.onKeyPress(level, user, extraClientInput,
                inputMethod, clickHoldResolveTime, bufferingState);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    public static final class CenterAction
            extends EntityActionInstance {
        public CenterAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 playStandEntityCancelableActionSound: each client plays it
        // and stops it once the Stand is no longer performing this action.
        // 1.16 PowerBaseImpl.performAction set the cooldown as the hold
        // fired, in the charge's last tick; the Stand's task performed in its
        // next tick.
        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase != ActionPhase.PERFORM) {
                return;
            }
            if (level().isClientSide()) {
                if (getPerformer() instanceof StandEntity stand) {
                    CMoonClientSounds.playUntil(
                            stand,
                            CMoonAddon.C_MOON_CENTER_OF_GRAVITY_SOUND.get(),
                            this::isPerformSoundOver);
                }
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null
                    && ability instanceof CenterOfGravityAbility center) {
                center.setCooldownOnUse(StandPower.get(user));
            }
        }

        public boolean isPerformSoundOver() {
            return isOver() || getPhase() != ActionPhase.PERFORM;
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level() instanceof ServerLevel server)
                    || !(getPerformer()
                            instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null || !power.hasPower()) {
                startRecovery();
                return;
            }

            for (LivingEntity target
                    : server.getEntitiesOfClass(
                            LivingEntity.class,
                            user.getBoundingBox().inflate(RADIUS),
                            entity -> entity != user
                                    && entity != stand
                                    && !(entity
                                            instanceof StandEntity)
                                    && entity.isAlive())) {
                // server-side motion only, as in 1.16: a player's own client
                // is not resynced, so players stay rooted by the Slowness
                target.setDeltaMovement(new Vec3(
                        0.0D, 0.03D, 0.0D));
                // 1.16 field_76421_d: Slowness XXVI roots the target
                target.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        20,
                        25,
                        false,
                        false,
                        true));
                // 1.16 "/particle" defaults: count 0, one still particle
                server.sendParticles(
                        CMoonAddon.HAMON_SPARK_PURPLE.get(),
                        target.getX(),
                        target.getY() + 0.3D,
                        target.getZ(),
                        0,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D);
            }
            StandAbilityStamina.consume(
                    ability, power, STAMINA_PER_TICK, true);
        }

        // 1.16 holdToFire(10, false): letting go before the charge
        // completes cancels it; once fired, the 100 ticks run out
        // whether or not the key is still held.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        // 1.16 isCancelable (a hold-to-fire action without recovery gives way
        // to any other action once it fires) is the core's default: from the
        // perform's second tick, as 1.16 ran the field's first tick as it fired.
    }
}
