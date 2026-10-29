package rotp.addons.shadowtheworld.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class ShadowHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {
    public ShadowHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = ShadowHeavyPunch::new;
        anim = ActionAnimIdentifier.getOrCreate("heavy_punch", false);
        // 1.16 SHIFT variation of the punch: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        // 1.16 shadow_world_heavy_punch .shout(SDDIO_DIE)
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, ShadowTheWorldAddon.SDDIO_DIE);
        }
    }

    /**
     * 1.16 TimeStopCounter.teleportBehind ended with power.clickAction(SHADOW_WORLD_HEAVY_PUNCH, false,
     * new ActionTarget(attacker), null): the punch's own requirements (a refusal reached the user as a message and left
     * the held counter going), the punch started on the attacker, and PowerBaseImpl.onClickAction then ended whatever
     * the Stand power held (stopHeldAction(false)), the counter's hold with its cooldown.
     *
     * @return whether the punch started
     */
    public boolean clickAt(LivingEntity user, StandPower power, LivingEntity attacker) {
        Level level = user.level();
        if (level.isClientSide()) {
            return false;
        }
        // 1.16 PowerBaseImpl.checkRequirements :255 asked the held action first, and refused without a message
        if (holdsAnotherCharge(user, power)) {
            return false;
        }
        ConditionCheck check = checkConditions(power);
        if (check.isPositive() && !power.isAbilityUnlocked(name())) {
            check = ConditionCheck.createNegative("not_unlocked");
        }
        if (!check.isPositive()) {
            ConditionCheck.sendActionFailedMessage(this, check, user);
            return false;
        }
        // 1.16 StandEntityAction.canClickDuringTask :495-497: the running heavy punch's own click, refused without a message
        if (AvailableAbilities.refusesRunningActionPressedAgain(power, this)) {
            return false;
        }
        // the press takes the user's aim as its target; the click handed it the attacker
        ActionTargetAim aim = LivingComponentAction.getComponent(user).entityAim;
        ActionTarget aimed = aim.getTarget();
        boolean aimedMissing = aim.isClientEntityMissing();
        BufferingState press = BufferingState.clickCanBuffer();
        aim.setTarget(new ActionTarget(attacker));
        try {
            onKeyPress(level, user, null, InputMethod.CLICK, 0.0F, press);
        }
        finally {
            aim.setTarget(aimed, aimedMissing);
        }
        if (press.shouldBuffer) {
            // 1.16 StandEntityAction.checkTaskCancelling queued a punch the running task could not be cancelled for
            ActionInputBuffer buffer = ActionInputBuffer.get(user);
            if (buffer != null) {
                buffer.bufferClickInput(abilityId);
            }
        }
        if (!press.isActionSuccess) {
            return false;
        }
        if (shouldSetCooldownOnKeyPress(InputMethod.CLICK)) {
            setCooldownOnUse(power);
        }
        power.interruptHeldInputs();
        return true;
    }

    /*
     * Whether a key holds another action of the Stand power through a charge before it fires: 1.16 let a click through a
     * held action only if heldAllowsOtherAction, which a StandEntityAction answered with getHoldDurationToFire == 0
     * (StandEntityAction :532) and any other action with false (Action :453). Of Shadow's held actions the counter,
     * guard and barrage holds let it through, the time stop and evolution charges (holdToFire 40 and 60) did not. The
     * time stop's charge is in the user's Stand-power slot, the Stand's tasks in the Stand's own.
     */
    public boolean holdsAnotherCharge(LivingEntity user, StandPower power) {
        StandEntity stand = power.getSummonedStandEntity();
        for (EntityActionInstance held : new EntityActionInstance[] {
                LivingComponentAction.getCurEntityAction(user, PowerClass.STAND),
                stand != null ? LivingComponentAction.getCurEntityAction(stand) : null}) {
            if (held != null
                    && !held.isOfAbility(this)
                    && held.ability instanceof EntityActionAbility heldAbility
                    && heldAbility.abilityId.powerClass() == PowerClass.STAND
                    && held.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) > 0.0F
                    && heldAbility.isActionHeld(held)
                    && AbilityInput.isHeldByKey(user, held)) {
                return true;
            }
        }
        return false;
    }

    public static final class ShadowHeavyPunch
            extends StandEntityHeavyPunch {
        public ShadowHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected Holder<SoundEvent> getHeavyPunchImpactSound(
                ActionTarget target) {
            return target.getType() == TargetType.ENTITY
                    ? ModSoundEvents
                            .THE_WORLD_PUNCH_HEAVY_ENTITY
                    : ModSoundEvents.STAND_PUNCH_HEAVY;
        }

        @Override
        public void actionPerformStart() {
            super.actionPerformStart();
            ShadowAbilitySupport.unsummonIfUnstable(
                    getPowerUser());
        }

        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damage,
                boolean hurt) {
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

        @Override
        protected void addKnockback(DamageSource damageSource) {
            DamageSourceModified knockback =
                    (DamageSourceModified) damageSource;
            knockback.jojo_ripples$modifyKnockback(
                    5.0F, 1.0F);
            knockback.jojo_ripples$knockbackYRot(-90.0F);
            knockback.jojo_ripples$knockbackXRot(0.0F);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            if (target.getMainEntity()
                    instanceof LivingEntity livingTarget) {
                damage = DamageUtil.addArmorPiercing(
                        damage,
                        (float) stand.getAttackDamage()
                                * 0.01F,
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
    }
}
