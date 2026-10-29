package rotp.addons.scr.ability;

import javax.annotation.Nullable;

import rotp.core.init.ModParticles;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.scr.SilverChariotRequiemAddon;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class ChariotVirusAbility
        extends StandEntityAbility {
    public static final double RANGE = 2.0D;
    public static final double CONE_DOT_THRESHOLD = 0.886D;
    public static final double MIN_DISTANCE_SQR = 0.5D;
    public static final int MAX_HOLD_TICKS = 50;
    public static final int EFFECT_DURATION_TICKS = 600;
    public static final int PARTICLES_PER_TICK = 5;

    public enum Mode {
        CHARIOT(2.0F, 500, 2, StandPart.ARMS),
        STAND(3.0F, 450, 3, StandPart.MAIN_BODY);

        private final float staminaCostTick;
        private final int cooldownTicks;
        private final int resolveLevel;
        private final StandPart requiredPart;

        Mode(
                float staminaCostTick,
                int cooldownTicks,
                int resolveLevel,
                StandPart requiredPart) {
            this.staminaCostTick = staminaCostTick;
            this.cooldownTicks = cooldownTicks;
            this.resolveLevel = resolveLevel;
            this.requiredPart = requiredPart;
        }
    }

    private final Mode mode;

    public ChariotVirusAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            Mode mode) {
        super(abilityType, abilityId, VirusAction::new);
        this.mode = mode;
        setDefaultPhaseLength(
                ActionPhase.PERFORM, MAX_HOLD_TICKS);
        cooldown(mode.cooldownTicks);
        resolveLevelToUnlock(mode.resolveLevel);
        partsRequired(mode.requiredPart);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    // 1.16 holdType(50): PowerBaseImpl.tickHeldAction rechecked the virus on
    // every held tick (a stunned Stand or a lost Stand part ended it).
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

    public static final class VirusAction
            extends EntityActionInstance {
        private boolean cooldownSet;

        public VirusAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 holdType(50) with no recovery: another Stand action pressed
        // during the virus replaced it at once (isCancelable), and any other
        // action of the power ended the hold (stopHeldAction(false))
        @Override
        public boolean isHoldTypeStandAction() {
            return true;
        }

        // 1.16 stopHeldAction(false) set the cooldown of a virus replaced
        // before its recovery as well
        @Override
        public void onActionCleared(
                @Nullable EntityActionInstance newAction) {
            if (newAction != null) {
                setCooldown();
            }
            super.onActionCleared(newAction);
        }

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            // 1.16 standOffsetFromUser(0.0, 1.0) for both viruses, turned by
            // the view yaw
            setStandOffset(
                    0.0D,
                    1.0D,
                    StandOffsetFromUser.Rotations.HEAD,
                    false);
            if (!level().isClientSide()
                    && performer instanceof StandEntity stand
                    && ability
                            instanceof ChariotVirusAbility virus) {
                StandUtil.playStandEntitySound(
                        stand,
                        virus.mode == Mode.CHARIOT
                                ? SilverChariotRequiemAddon
                                        .SCR_YELLOW_VIRUS
                                : SilverChariotRequiemAddon
                                        .SCR_ABILITY_2,
                        1.0F,
                        1.0F);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(level()
                            instanceof ServerLevel serverLevel)
                    || !(performer instanceof StandEntity stand)
                    || !(ability
                            instanceof ChariotVirusAbility virus)) {
                return;
            }
            // 1.16 PowerBaseImpl.tickHeldAction counted holdType(50) from the press, through the summon lock
            if (getFullTicksPassed() + getTicksHeldInSummonLock() >= MAX_HOLD_TICKS) {
                startRecovery();
                return;
            }

            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null) {
                startRecovery();
                return;
            }
            // 1.16 StandEntityTask paid staminaCostTick and ignored the
            // result: the virus keeps spreading on an empty bar
            StandAbilityStamina.consume(
                    ability, power, virus.mode.staminaCostTick, true);

            applyVirus(serverLevel, stand, user, virus.mode);
            spawnParticles(
                    serverLevel, stand, user, virus.mode);
        }

        @Override
        public void onButtonStopHold() {
            startRecovery();
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.RECOVERY) {
                setCooldown();
            }
        }

        private void setCooldown() {
            if (cooldownSet || level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (power != null
                    && ability
                            instanceof ChariotVirusAbility virus) {
                virus.setCooldownOnUse(power);
                cooldownSet = true;
            }
        }
    }

    private static void applyVirus(
            ServerLevel level,
            StandEntity stand,
            LivingEntity user,
            Mode mode) {
        Vec3 look = stand.getLookAngle().normalize();
        Vec3 standPosition = stand.position();
        for (Entity entity : level.getEntities(
                stand,
                stand.getBoundingBox().inflate(RANGE),
                entity -> {
                    Vec3 relative =
                            entity.position().subtract(standPosition);
                    return entity != user
                            && relative.lengthSqr()
                                    > MIN_DISTANCE_SQR
                            && look.dot(relative.normalize())
                                    > CONE_DOT_THRESHOLD;
                })) {
            // 1.16 skipped targets frozen in stopped time (canUpdate)
            if (!(entity instanceof LivingEntity target)
                    || !target.isAlive()
                    || TimeStopState.shouldFreezeOnServer(target)) {
                continue;
            }
            if (mode == Mode.CHARIOT) {
                target.addEffect(new MobEffectInstance(
                        SilverChariotRequiemAddon
                                .CHARIOT_VIRUS_EFFECT,
                        EFFECT_DURATION_TICKS,
                        0));
                target.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS,
                        EFFECT_DURATION_TICKS,
                        0));
                target.addEffect(new MobEffectInstance(
                        MobEffects.CONFUSION,
                        EFFECT_DURATION_TICKS,
                        0));
                target.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        EFFECT_DURATION_TICKS,
                        0));
            }
            else {
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.STAND_VIRUS,
                        EFFECT_DURATION_TICKS,
                        0));
            }
        }
    }

    private static void spawnParticles(
            ServerLevel level,
            StandEntity stand,
            LivingEntity user,
            Mode mode) {
        ParticleOptions particle =
                mode == Mode.CHARIOT
                        ? SilverChariotRequiemAddon
                                .CHARIOT_VIRUS_PARTICLE.get()
                        : ModParticles.METEORITE_VIRUS.get();
        Vec3 look = stand.getLookAngle();
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            Vec3 userPosition = user.position().add(
                    (stand.getRandom().nextDouble() - 0.5D)
                            * (user.getBbWidth() + 1.0D),
                    stand.getRandom().nextDouble()
                            * (user.getBbHeight() + 1.0D),
                    (stand.getRandom().nextDouble() - 0.5D)
                            * (user.getBbWidth() + 1.0D));
            Vec3 particlePosition = userPosition.add(
                    look.scale(2.0D)
                            .xRot((float) (
                                    (stand.getRandom().nextDouble()
                                            * 2.0D - 1.0D)
                                    * Math.PI / 6.0D))
                            .yRot((float) (
                                    (stand.getRandom().nextDouble()
                                            * 2.0D - 1.0D)
                                    * Math.PI / 6.0D)));
            Vec3 velocity = particlePosition
                    .subtract(userPosition)
                    .normalize()
                    .scale(0.75D);
            level.sendParticles(
                    particle,
                    particlePosition.x,
                    particlePosition.y,
                    particlePosition.z,
                    0,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    1.0D);
        }
    }
}
