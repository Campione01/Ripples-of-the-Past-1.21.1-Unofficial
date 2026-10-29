package rotp.addons.ovatheworld.ability;

import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.ovatheworld.OvaTheWorldAddon;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class OvaUltraPunchAbility
        extends StandEntityHeavyPunchAbility {
    public static final float LEGACY_STAMINA = 350.0F;
    public static final int LEGACY_COOLDOWN = 1200;
    private static final ActionAnimIdentifier ULTRA_ANIM =
            ActionAnimIdentifier.getOrCreate(
                    "ultra_punch", false);

    public OvaUltraPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = OvaUltraPunchAction::new;
        setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 30);
        cooldown(LEGACY_COOLDOWN);
        resolveLevelToUnlock(3);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(
            InputMethod inputMethod) {
        return false;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ULTRA_ANIM;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 30);
    }

    public static final class OvaUltraPunchAction
            extends StandEntityHeavyPunch {
        private float blockChance;

        private OvaUltraPunchAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void extractAnim(
                LivingAnimState animVariables,
                LivingEntity performer,
                float partialTick) {
            super.extractAnim(animVariables, performer, partialTick);
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                animVariables.actionPhase = ActionPhase.BUTTON_CHARGE;
            }
        }

        // 1.16 holdToFire(30, false): letting go before the charge
        // completes cancels the punch.
        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                forceStop();
                syncPhaseChanges();
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (newPhase == ActionPhase.WINDUP
                    && !level().isClientSide()
                    && getPerformer()
                            instanceof StandEntity stand) {
                StandUtil.playStandEntitySound(
                        stand,
                        OvaTheWorldAddon
                                .THE_WORLD_OVA_ULTRA_PUNCH_SOUND,
                        1.0F,
                        1.0F);
            }
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof StandEntity stand
                    && stand.isArmsOnlyMode()) {
                // The donor's arms-only heavy offset falls back to the entity default.
                setStandOffset(
                        stand.offsetFromUser.armsOnlyIdleOffset,
                        stand.offsetFromUser.armsOnlyIdleRotations,
                        true);
            }
        }

        // 1.16 staminaCost(350): StandEntityTask pays it when the punch
        // performs and ignores the result, so a short bar is drained and
        // the punch still lands.
        @Override
        public void actionPerformStart() {
            StandPower power =
                    getPowerUser() != null
                            ? StandPower.get(getPowerUser())
                            : null;
            if (!level().isClientSide() && power == null) {
                forceStop();
                return;
            }
            super.actionPerformStart();
            if (!level().isClientSide() && power != null) {
                power.consumeStamina(
                        LEGACY_STAMINA - 50.0F,
                        false);
                if (ability
                        instanceof OvaUltraPunchAbility ultra) {
                    ultra.setCooldownOnUse(power);
                }
            }
        }

        @Override
        protected Holder<SoundEvent> getHeavyPunchImpactSound(
                ActionTarget target) {
            return target.getType() == TargetType.ENTITY
                    ? ModSoundEvents
                            .THE_WORLD_PUNCH_HEAVY_ENTITY
                    : OvaTheWorldAddon
                            .THE_WORLD_OVA_HEAVY_PUNCH_SOUND;
        }

        @Override
        protected void addKnockback(DamageSource damageSource) {
            DamageSourceModified modified =
                    (DamageSourceModified) damageSource;
            modified.jojo_ripples$modifyKnockback(14.0F, 1.0F);
            modified.jojo_ripples$knockbackXRot(-2.0F);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
            float damage,
            float explosionRadius) {
            blockChance = 0.0F;
            if (target.getMainEntity()
                    instanceof LivingEntity livingTarget) {
                blockChance =
                        OvaSmashAbility.OvaSmashAction
                                .disableBlockingChance(
                                        stand, livingTarget);
                OvaSmashAbility.OvaSmashAction
                        .disableStandBlocking(
                                stand,
                                livingTarget,
                                blockChance);
                damage = DamageUtil.addArmorPiercing(
                        damage,
                        (float) stand.getAttackDamage() * 0.01F,
                        livingTarget,
                        damageSource);
            }
            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damage,
                    explosionRadius);
        }

        // 1.16 StandEntityPunch.doAttack: DamageUtil.disableShield rolls
        // the main target's shield after a hit that hurt, with the
        // target's random, before afterAttack queues the time-stop sound.
        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damage,
                boolean hurt) {
            disableShieldOnHit(target, hurt, blockChance);
            if (!hurt
                    || !(target.level()
                            instanceof ServerLevel serverLevel)) {
                return;
            }
            TimeStopState state = serverLevel.getData(
                    ModDataAttachmentTypes.TIME_STOP.get());
            if (state.shouldFreeze(target)) {
                state.queueOnTimeResume(
                        target,
                        () -> target.playSound(
                                ModSoundEvents
                                        .THE_WORLD_PUNCH_HEAVY_TS_IMPACT
                                        .get(),
                                1.0F,
                                1.0F));
            }
        }
    }
}
