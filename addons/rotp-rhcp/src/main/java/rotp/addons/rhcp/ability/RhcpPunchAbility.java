package rotp.addons.rhcp.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.RhcpService;
import rotp.addons.rhcp.ability.RhcpAbility.Mode;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * 1.16 rhcp_punch_off (StandEntityLightAttack), RHCPPunch and
 * RHCPCounterattack (counterattack and blitz) extended StandEntityLightAttack:
 * the core light punch (timing from the Stand's speed, damage from its
 * strength, block hits, the finisher meter) with RHCP's energy on top.
 */
public final class RhcpPunchAbility extends StandEntityPunchAbility
        implements RhcpModeAbility {
    private final Mode mode;

    public RhcpPunchAbility(AbilityType<?> type, AbilityId id, Mode mode) {
        super(type, id, RhcpPunch::new);
        this.mode = mode;
        standAutoSummonMode(AutoSummonMode.FULL);
        // 1.16 punchSound: the core light sound for punch_off, RHCP's own
        // for the powered punch (the counters pick theirs per hit).
        if (mode == Mode.PUNCH) {
            punchImpactSound(RhcpAddon.RHCP_PUNCH_LIGHT);
        }
    }

    @Override
    public Mode rhcpMode() {
        return mode;
    }

    // 1.16 staminaCost: 150 for punch_off, 15 for the counters, the core 10.
    static float stamina(Mode mode) {
        return switch (mode) {
            case PUNCH_OFF -> 150.0F;
            case COUNTERATTACK, BLITZ_ATTACK -> 15.0F;
            default -> 10.0F;
        };
    }

    // 1.16 RHCPPunch.replaceAction: with RHCP not out, or below potency 1,
    // the punch is punch_off.
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        if (mode == Mode.PUNCH && context != null && abilities != null
                && context.getUser() instanceof LivingEntity user
                && (RhcpService.activeStand(user) == null
                        || RhcpAddon.state(user).potency() < 1)) {
            Ability off = abilities.getContextVariation("rhcp_punch_off");
            if (off != null) {
                return off;
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    // punch_off is the punch's variation, the counters start from the
    // dodge; 1.16 bound none of them.
    @Override
    public boolean addToControlSchemeEditing() {
        return mode == Mode.PUNCH && super.addToControlSchemeEditing();
    }

    @Override
    public String getSpriteName(Power<?> context) {
        // No icons of their own: the dodge follow-ups show the dodge's.
        if (mode == Mode.COUNTERATTACK || mode == Mode.BLITZ_ATTACK) {
            return "rhcp_dodge";
        }
        return super.getSpriteName(context);
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
        boolean allowed = mode == Mode.PUNCH_OFF
                ? !state.inConductor()
                : state.powered() && !state.inConductor();
        // 1.16 had no stamina gate: actionPerformStart pays the cost and a
        // short user drops to 0 stamina.
        return allowed ? base : ConditionCheck.NEGATIVE;
    }

    // 1.16 RHCPEntity.counterattack: setTask(counter, getStandActionTicks,
    // Phase.WINDUP, target), so the counters wind up for the light attack's
    // recovery ticks. RHCPCounterattack.getStandWindupTicks only feeds
    // ticksEvaded (onActionSet).
    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level,
            LivingEntity standUser, LivingEntity standEntity) {
        super.initActionFromConfig(action, level, standUser, standEntity);
        if (!level.isClientSide() && standEntity instanceof StandEntity stand
                && (mode == Mode.COUNTERATTACK || mode == Mode.BLITZ_ATTACK)) {
            action.phasesLength.put(ActionPhase.WINDUP,
                    StandStatFormulas.getLightAttackRecovery(
                            stand.getAttackSpeed(), stand.getFinisherMeter()));
        }
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate(switch (mode) {
            case COUNTERATTACK -> "counterattack";
            case BLITZ_ATTACK -> "blitz_attack";
            default -> "punch";
        }, false);
    }

    public static final class RhcpPunch extends StandEntityPunch {
        public RhcpPunch(EntityActionType ability) {
            super(ability);
        }

        private Mode mode() {
            return ability instanceof RhcpPunchAbility punch
                    ? punch.mode : Mode.PUNCH;
        }

        private boolean counter() {
            return mode() == Mode.COUNTERATTACK || mode() == Mode.BLITZ_ATTACK;
        }

        @Override
        public void actionPerformStart() {
            ServerPlayer player = !level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer user ? user : null;
            StandPower power = player != null ? StandPower.get(player) : null;
            float before = power != null ? power.getStamina() : 0.0F;
            // A blitz with no one to answer dashes first (port extra).
            if (player != null && mode() == Mode.BLITZ_ATTACK
                    && RhcpService.counterTarget(player) == null) {
                RhcpService.dash(player);
            }
            super.actionPerformStart();
            if (player == null) {
                return;
            }
            // The core punch takes a flat 10; 1.16 took this one's staminaCost.
            if (power != null && !power.isStaminaInfinite()) {
                power.setStamina(before);
                power.consumeStamina(stamina(mode()));
            }
            // 1.16 consumeEnergy(10, 5) for the punch, (15, 7.5) for the counters.
            switch (mode()) {
                case PUNCH -> RhcpService.consume(player, 10.0F, 5.0F);
                case COUNTERATTACK, BLITZ_ATTACK ->
                        RhcpService.consume(player, 15.0F, 7.5F);
                default -> { }
            }
            // 1.16 standPerform: standCount += 10 (not for punch_off).
            if (mode() != Mode.PUNCH_OFF
                    && performer instanceof RhcpStandEntity rhcp) {
                rhcp.addStandCount(10);
            }
            // Sparks on a miss; a hit makes its own in afterCoreHit.
            if (performer instanceof RhcpStandEntity stand
                    && (punchedTarget == null
                            || punchedTarget.getType() != ActionTarget.TargetType.ENTITY)) {
                RhcpService.emitElectricParticles(player.serverLevel(), stand, 3);
            }
        }

        // 1.16 RHCPCounterattack.onTaskSet: the counters strike the Stand the
        // dodge answered.
        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            if (counter() && getPowerUser() instanceof ServerPlayer player) {
                LivingEntity countered = RhcpService.counterTarget(player);
                if (countered != null && RhcpService.inMeleeReach(stand, countered)) {
                    return new ActionTarget(countered);
                }
            }
            // 1.16 RHCPPunch.onTaskSet: the powered punch aims at the lock.
            if (mode() == Mode.PUNCH && getPowerUser() instanceof ServerPlayer player) {
                LivingEntity locked = RhcpService.lockedTarget(player, stand);
                if (locked != null) {
                    return new ActionTarget(locked);
                }
            }
            return super.getPunchTarget(stand);
        }

        // 1.16 RHCPCounterattack.onTaskSet: ticksEvaded += the windup, so
        // the dodge cooldown after a counter is longer.
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            if (counter() && !level().isClientSide()
                    && performer instanceof RhcpStandEntity rhcp) {
                int windup = StandStatFormulas.getLightAttackWindup(
                        rhcp.getAttackSpeed(), 1.0F, rhcp.guardCounter(), false);
                for (int i = 0; i < windup; i++) {
                    rhcp.addTicksEvaded();
                }
            }
            // 1.16 RHCPCounterattack / RHCPPunch.onTaskSet: setTaskTarget of
            // the held lock (getTargetHit), so the Stand turns to it.
            if ((counter() || mode() == Mode.PUNCH) && !level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer player) {
                LivingEntity lock = RhcpService.counterTarget(player);
                if (lock != null) {
                    setTaskTarget(new ActionTarget(lock));
                }
            }
        }

        // 1.16 RHCPCounterattack.onTaskStopped: standCount = -1.
        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            super.onActionCleared(newAction);
            if (counter() && !level().isClientSide()
                    && performer instanceof RhcpStandEntity rhcp) {
                rhcp.setStandCount(-1);
            }
        }

        @Override
        protected void hitEntity(ActionTarget target, Level level, StandEntity stand) {
            Entity entity = target.getMainEntity();
            if (!(entity instanceof LivingEntity living)
                    || !(stand instanceof RhcpStandEntity rhcp)
                    || !(getPowerUser() instanceof ServerPlayer player)) {
                super.hitEntity(target, level, stand);
                return;
            }
            DamageSource source = makePunchDamageSource();
            float damage = StandStatFormulas.getLightAttackDamage(stand.getAttackDamage());
            // 1.16 Counterattack with attackAlt: double damage, no knockback.
            if (counter() && rhcp.isAttackAlternate()) {
                damage *= 2.0F;
                ((DamageSourceModified) source).jojo_ripples$modifyKnockback(0.0F, 0.0F);
            }
            else {
                ((DamageSourceModified) source).jojo_ripples$modifyKnockback(stand.guardCounter(), 1);
            }
            boolean hurt = standEntityAttack(stand, living, source, damage);
            stand.addFinisherMeter(0.1F);
            // 1.16 IPunch.playImpactSound: a living target sounds only if hit.
            impactSoundDue = hurt;
            // 1.16 Counterattack was a HeavyPunchInstance.
            RhcpService.afterCoreHit(player, rhcp, living, hurt,
                    mode() == Mode.BLITZ_ATTACK, counter());
            // 1.16 Counterattack.afterAttack: with attackAlt either counter
            // dashes behind the target; a survivor loses its aim.
            if (hurt && counter() && rhcp.isAttackAlternate()) {
                RhcpService.dashBehind(player, living);
                if (living.isAlive()) {
                    dropAim(living);
                }
            }
        }

        // 1.16: a Stand's setTaskTarget(null), a mob's setTarget(null).
        private static void dropAim(LivingEntity target) {
            if (target instanceof StandEntity targetStand) {
                targetStand.setTaskTarget(null);
            }
            else if (target instanceof Mob mob) {
                mob.setTarget(null);
            }
        }

        @Override
        protected Holder<SoundEvent> getPunchImpactSound(ActionTarget target) {
            Holder<SoundEvent> sound = counter()
                    && performer instanceof RhcpStandEntity stand
                    ? RhcpService.impactSound(mode(), stand.isAttackAlternate())
                    : null;
            return sound != null ? sound : super.getPunchImpactSound(target);
        }

        // 1.16 RHCPCounterattack.isCancelable: the dodge again in recovery.
        @Override
        public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
            return counter() && getPhase() == ActionPhase.RECOVERY
                    && cancellingAbility instanceof Ability next
                    && "rhcp_dodge".equals(next.name())
                    || super.canBeCancelledInto(cancellingAbility);
        }
    }
}
