package rotp.addons.rhcp.ability;

import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.entityattachment.syncheddata.SyncedDataHolderExtended;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.RhcpService;
import rotp.addons.rhcp.ability.RhcpAbility.Mode;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 RHCPHeavyPunch extended StandEntityHeavyAttack: the core heavy punch
 * (strength damage, block explosion, a stopped barrage, 50 stamina) with
 * RHCP's energy, its attackAlt knockback and sounds, and the electrocute
 * follow-up in recovery. 1.16 RHCPComboPunch extended RHCPHeavyPunch and was
 * its finisher variation (setFinisherVariation): the COMBO_PUNCH instance.
 */
public final class RhcpHeavyAbility extends StandEntityHeavyPunchAbility
        implements RhcpModeAbility {
    // what the core heavy punch takes
    static final float STAMINA = 50.0F;

    // 1.16 RHCPComboPunch.isCancelable above potency 3.
    private static final Set<String> COMBO_CANCELS = Set.of(
            "rhcp_punch", "rhcp_barrage", "rhcp_heavy_punch",
            "rhcp_combo_punch", "rhcp_block", "rhcp_electric_dash");

    private final Mode mode;

    public RhcpHeavyAbility(AbilityType<?> type, AbilityId id) {
        this(type, id, Mode.HEAVY_PUNCH);
    }

    public RhcpHeavyAbility(AbilityType<?> type, AbilityId id, Mode mode) {
        super(type, id);
        this.mode = mode;
        createActionObj = RhcpHeavyPunch::new;
        standAutoSummonMode(AutoSummonMode.FULL);
        if (mode == Mode.COMBO_PUNCH) {
            // 1.16 combo builder: the default heavy punch sound, MAIN_BODY,
            // Resolve 1; with the finisher meter at half or more the heavy
            // punch comes out as the combo (no wheel slot).
            partsRequired(StandPart.MAIN_BODY);
            resolveLevelToUnlock(mode.resolveLevel);
            initIsFinisher("rhcp_heavy_punch");
        } else {
            heavyPunchImpactSound(RhcpAddon.RHCP_PUNCH_HEAVY);
        }
        // 1.16 getSounds: only the alternate heavy has a voice, played from
        // onActionSet.
        heavyPunchCrySound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY));
    }

    @Override
    public Mode rhcpMode() {
        return mode;
    }

    // 1.16 getRecoveryFollowup: the heavy's electrocute target, the combo's
    // chase target, each offered by its own attack once the heavy key went
    // to it. The core finisher check already moved the key to the running
    // combo, or by a missing part to the attack that does not need it
    // (getFinisherVariationIfPresent), so a combo that needs a lost part
    // leaves the key on the heavy, which gives no chase.
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (context != null && abilities != null
                && context.getUser() instanceof LivingEntity user) {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            RhcpState state = RhcpAddon.state(user);
            String followup = mode == Mode.COMBO_PUNCH
                    && RhcpAbility.canFollowUp(stand, state, Mode.CHASE_TARGET)
                            ? "rhcp_chase_target"
                    : mode == Mode.HEAVY_PUNCH
                    && RhcpAbility.canFollowUp(stand, state, Mode.ELECTROCUTE_TARGET)
                            ? "rhcp_electrocute_target" : null;
            if (followup != null) {
                Ability candidate = abilities.getContextVariation(followup);
                if (candidate != null && candidate.isAbilityAvailable(context)) {
                    return candidate;
                }
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    // a part the ability needs that the Stand has lost
    static boolean missesRequiredPart(@Nullable StandPower power, Ability ability) {
        StandInstance instance = power != null ? power.getStandInstance().orElse(null) : null;
        if (instance != null) {
            for (StandPart part : StandPart.values()) {
                if (!instance.hasPart(part) && ability.isPartRequired(part)) {
                    return true;
                }
            }
        }
        return false;
    }

    // whether the heavy's or the combo's follow-up may start now
    static boolean followUpOpen(@Nullable EntityActionInstance action) {
        return action instanceof RhcpHeavyPunch heavy && heavy.followUpOpen();
    }

    // 1.16 RHCPComboPunch.getIconTexturePath / getTranslationKey: above
    // potency 3 the combo shows as its "_alt" heavy punch.
    private boolean altCombo(@Nullable Power<?> context) {
        return mode == Mode.COMBO_PUNCH
                && context != null && context.getUser() != null
                && RhcpAddon.state(context.getUser()).potency() > 3;
    }

    @Override
    public String getSpriteName(Power<?> context) {
        return altCombo(context)
                ? "rhcp_combo_punch_alt" : super.getSpriteName(context);
    }

    @Override
    public Component getName(Power<?> context) {
        return altCombo(context)
                ? abilityName(context, ".alt") : super.getName(context);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()
                || !(context instanceof StandPower power)
                || power.getPowerType() != RhcpAddon.RHCP.get()
                || !(power.getUser() instanceof LivingEntity user)) {
            return base.isPositive() ? ConditionCheck.NEGATIVE : base;
        }
        RhcpState state = RhcpAddon.state(user);
        // 1.16 had no stamina gate; the heavy and the combo pay on perform.
        return state.powered() && !state.inConductor()
                ? base : ConditionCheck.NEGATIVE;
    }

    @Override
    public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
            InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
        // An input selected before the client receives the new follow-up mapping is still the same RMB.
        RhcpStandEntity stand = RhcpService.activeStand(user);
        RhcpState state = RhcpAddon.state(user);
        if (!level.isClientSide() && inputMethod == InputMethod.CLICK
                && stand != null && state.powered() && !state.inConductor() && state.potency() >= 4
                && stand.getCurStandAction() instanceof RhcpHeavyPunch action
                && action.mode == Mode.COMBO_PUNCH
                && !(action.ability instanceof Ability combo
                        && missesRequiredPart(StandPower.get(user), combo))) {
            bufferingState.isActionSuccess = action.queueChase();
            return null;
        }
        return super.onKeyPress(level, user, extraClientInput,
                inputMethod, clickHoldResolveTime, bufferingState);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("heavy_punch", false);
    }

    // 1.16 RHCPHeavyPunch.punchEntity: strength * (0.01 + 0.00125 per
    // potency past 1) armor piercing, none at potency 0.
    static float armorPiercing(double attackDamage, int potency) {
        return potency <= 0 ? 0.0F
                : (float) attackDamage * (0.01F + 0.00125F * (potency - 1));
    }

    public static final class RhcpHeavyPunch extends StandEntityHeavyPunch
            implements SyncedDataHolderExtended {
        // 1.16 StandEntityTask.addModifierAction sent the taken follow-up to
        // the clients tracking the Stand (TrStandTaskModifierPacket), whose
        // key then left it (canSetFollowUp: !hasModifierAction).
        private static final EntityDataAccessor<Boolean> CHASE_TAKEN =
                SynchedEntityData.defineId(RhcpHeavyPunch.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> ELECTROCUTE_TAKEN =
                SynchedEntityData.defineId(RhcpHeavyPunch.class, EntityDataSerializers.BOOLEAN);

        // HEAVY_PUNCH, or COMBO_PUNCH for the finisher
        final Mode mode;
        // the lock held when the heavy was set (1.16 task target)
        private LivingEntity lockTarget;
        private boolean chaseRequested;
        private boolean chasePerformed;
        private boolean electrocuteRequested;

        boolean isChaseRequested() {
            return chaseRequested || synchedData.get(CHASE_TAKEN);
        }

        boolean isElectrocuteRequested() {
            return electrocuteRequested || synchedData.get(ELECTROCUTE_TAKEN);
        }

        boolean queueChase() {
            if (mode != Mode.COMBO_PUNCH || chaseRequested) {
                return false;
            }
            chaseRequested = true;
            synchedData.set(CHASE_TAKEN, true);
            performChaseIfReady();
            return true;
        }

        boolean queueElectrocute() {
            if (mode != Mode.HEAVY_PUNCH || electrocuteRequested) {
                return false;
            }
            electrocuteRequested = true;
            synchedData.set(ELECTROCUTE_TAKEN, true);
            return true;
        }

        @Override
        public void defineSynchedData(SynchedDataBuilder builder) {
            builder.define(CHASE_TAKEN, false);
            builder.define(ELECTROCUTE_TAKEN, false);
        }

        @Override
        public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {}

        // 1.16 RHCPElectrocuteTarget.standTickRecovery: the modifier the
        // press added to the heavy's task zapped its target on every tick of
        // the heavy's own recovery, which went on as it was.
        @Override
        public void actionTick() {
            super.actionTick();
            if (electrocuteRequested && getPhase() == ActionPhase.RECOVERY
                    && !level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer player) {
                RhcpService.electrocuteTarget(player,
                        punchedTarget != null ? punchedTarget.getEntity() : null,
                        getPhaseTick() + 1 >= getCurPhaseLength());
            }
        }

        private void performChaseIfReady() {
            if (getPhase() == ActionPhase.RECOVERY && chaseRequested && !chasePerformed
                    && !level().isClientSide() && getPowerUser() instanceof ServerPlayer player
                    && performer instanceof RhcpStandEntity stand) {
                chasePerformed = true;
                RhcpAbility.performQueuedChase(player, stand);
            }
        }

        @Override
        public void setPhase(ActionPhase newPhase, int tick) {
            super.setPhase(newPhase, tick);
            performChaseIfReady();
        }

        public RhcpHeavyPunch(EntityActionType ability) {
            super(ability);
            mode = ability instanceof RhcpHeavyAbility heavy
                    ? heavy.mode : Mode.HEAVY_PUNCH;
        }

        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            if (!level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer player) {
                // 1.16 onTaskSet: an alternate heavy or a held lock adds 25
                // to standCount, and the lock is what the heavy strikes. The
                // lock (getTargetHit) stays held through the heavy.
                lockTarget = RhcpService.counterTarget(player);
                if (performer instanceof RhcpStandEntity rhcp
                        && (rhcp.isAttackAlternate() || lockTarget != null)) {
                    rhcp.addStandCount(25);
                }
            }
            super.onActionSet(prevAction);
            // 1.16 RHCPHeavyPunch.onTaskSet (the combo's too): setTaskTarget
            // of the held lock, so the Stand turns and steps to it.
            if (lockTarget != null && !level().isClientSide()) {
                setTaskTarget(new ActionTarget(lockTarget));
            }
            // 1.16 RHCPHeavyPunch.getSounds: the windup line only on the
            // alternate (attackAlt) heavy punch; the combo had no stand sound.
            if (mode == Mode.HEAVY_PUNCH) {
                if (!level().isClientSide()
                        && performer instanceof RhcpStandEntity stand
                        && stand.isAttackAlternate()) {
                    StandUtil.playStandEntitySound(stand,
                            RhcpAddon.RHCP_THIS_IS_THE_END.get(), 1.0F, 1.0F);
                }
            }
        }

        // 1.16 standPerform: consumeEnergy(50, 25) after the core hit.
        @Override
        public void actionPerformStart() {
            super.actionPerformStart();
            if (!level().isClientSide() && performer instanceof RhcpStandEntity rhcp) {
                rhcp.setLastHeavyDamage(lastDamageDealtToLiving);
            }
            if (!level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer player) {
                RhcpService.consume(player, 50.0F, 25.0F);
                if (performer instanceof RhcpStandEntity stand
                        && (punchedTarget == null
                                || punchedTarget.getType() != ActionTarget.TargetType.ENTITY)) {
                    RhcpService.emitElectricParticles(player.serverLevel(), stand, 3);
                }
            }
        }

        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            if (lockTarget != null && lockTarget.isAlive()
                    && RhcpService.inMeleeReach(stand, lockTarget)) {
                return new ActionTarget(lockTarget);
            }
            return super.getPunchTarget(stand);
        }

        // 1.16 punchEntity: only the plain (not attackAlt) heavy pierces; the
        // combo set armorPiercing(0).
        @Override
        protected void hitEntity(ActionTarget target, Level level, StandEntity stand,
                DamageSource dmgSource, float dmgAmount, float explRadius) {
            if (mode == Mode.HEAVY_PUNCH
                    && stand instanceof RhcpStandEntity rhcp && !rhcp.isAttackAlternate()
                    && target.getMainEntity() instanceof LivingEntity targetLiving
                    && getPowerUser() != null) {
                float piercing = armorPiercing(stand.getAttackDamage(),
                        RhcpAddon.state(getPowerUser()).potency());
                dmgAmount = DamageUtil.addArmorPiercing(
                        dmgAmount, piercing, targetLiving, dmgSource);
            }
            super.hitEntity(target, level, stand, dmgSource, dmgAmount, explRadius);
        }

        // The core hook already stopped a hurt barrage; the port's electric
        // extra and the target lock the electrocute follow-up reads.
        @Override
        protected void afterHeavyPunchHit(StandEntity stand, LivingEntity targetLiving,
                DamageSource dmgSource, float dmgAmount, boolean hurt) {
            if (stand instanceof RhcpStandEntity rhcp
                    && getPowerUser() instanceof ServerPlayer player) {
                // the combo (no electric extra) locks on above potency 3
                RhcpService.afterCoreHit(player, rhcp, targetLiving, hurt,
                        mode == Mode.HEAVY_PUNCH, false);
            }
        }

        // 1.16 RHCPHeavyPunch.punchEntity: only the attackAlt heavy adds the
        // heavy knockback.
        @Override
        protected float getAdditionalHeavyPunchKnockback(StandEntity stand) {
            // 1.16 RHCPComboPunch.punchEntity: the heavy knockback above
            // potency 3, else 3.5 / (8 - finisher * 4).
            if (mode == Mode.COMBO_PUNCH) {
                return getPowerUser() != null
                        && RhcpAddon.state(getPowerUser()).potency() > 3
                                ? super.getAdditionalHeavyPunchKnockback(stand)
                                : 3.5F / (8.0F - finisherValue * 4.0F);
            }
            return stand instanceof RhcpStandEntity rhcp && !rhcp.isAttackAlternate()
                    ? 0.0F : super.getAdditionalHeavyPunchKnockback(stand);
        }

        // 1.16 impactSound(RHCP_PUNCH_HEAVY_ENTITY) on a plain heavy's entity hit.
        @Override
        protected Holder<SoundEvent> getHeavyPunchImpactSound(ActionTarget target) {
            // 1.16 RHCPComboPunch.punchEntity: impactSound(RHCP_PUNCH_HEAVY).
            if (mode == Mode.COMBO_PUNCH
                    && target.getType() == ActionTarget.TargetType.ENTITY) {
                return RhcpAddon.RHCP_PUNCH_HEAVY;
            }
            if (target.getType() == ActionTarget.TargetType.ENTITY
                    && performer instanceof RhcpStandEntity stand) {
                Holder<SoundEvent> sound = RhcpService.impactSound(
                        Mode.HEAVY_PUNCH, stand.isAttackAlternate());
                if (sound != null) {
                    return sound;
                }
            }
            return super.getHeavyPunchImpactSound(target);
        }

        // 1.16 replaceActionKostyl asked no phase: the whole task.
        boolean followUpOpen() {
            return !isOver();
        }

        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            // 1.16 RHCPComboPunch.isCancelable: above potency 3 the combo
            // punch gives way to the punches, the guard and the dash at once.
            if (mode == Mode.COMBO_PUNCH && getPowerUser() != null
                    && RhcpAddon.state(getPowerUser()).potency() > 3
                    && cancellingAbility instanceof Ability next
                    && COMBO_CANCELS.contains(next.name())) {
                return true;
            }
            // the follow-up after the hit: electrocute (heavy) or chase (combo)
            Mode followupMode = mode == Mode.COMBO_PUNCH
                    ? Mode.CHASE_TARGET : Mode.ELECTROCUTE_TARGET;
            return followUpOpen()
                    && cancellingAbility instanceof RhcpAbility followup
                    && followup.rhcpMode() == followupMode
                    || super.canBeCancelledInto(cancellingAbility);
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            // 1.16 onTaskStopped: standCount = -1 (the combo overrode it
            // with nothing, keeping its lock count).
            if (!level().isClientSide() && mode == Mode.HEAVY_PUNCH
                    && performer instanceof RhcpStandEntity stand) {
                stand.setAttackAlternate(false);
                stand.setStandCount(-1);
            }
        }
    }
}
