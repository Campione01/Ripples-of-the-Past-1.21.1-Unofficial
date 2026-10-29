package rotp.addons.rhcp.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.RhcpService;
import rotp.addons.rhcp.ability.RhcpAbility.Mode;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 RHCPBarrage extended StandEntityMeleeBarrage: the core barrage (hits a
 * tick from the Stand's speed, damage from its strength and precision, block
 * hits, the finisher meter, the hit and heavy-attack stops, 4 stamina a
 * tick), with RHCP's energy every tick.
 */
public final class RhcpBarrageAbility extends StandEntityBarrageAbility
        implements RhcpModeAbility {
    // what the core barrage takes every tick
    static final float STAMINA_TICK = 4.0F;

    public RhcpBarrageAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, RhcpBarrage::new);
        standAutoSummonMode(AutoSummonMode.FULL);
        barrageHitSound(RhcpAddon.RHCP_PUNCH_LIGHT);
        // The RHCP noise plays from actionPerformStart instead of a cry.
        barrageCrySound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY));
    }

    @Override
    public Mode rhcpMode() {
        return Mode.BARRAGE;
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
        // 1.16 had no stamina gate; the core barrage pays 4 a tick.
        return state.powered() && !state.inConductor()
                ? base : ConditionCheck.NEGATIVE;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return ActionAnimIdentifier.getOrCreate("barrage", false);
    }

    public static final class RhcpBarrage extends StandEntityBarrage {
        public RhcpBarrage(EntityActionType ability) {
            super(ability);
        }

        // 1.16 RHCPBarrage.onTaskSet: setTaskTarget of the held lock
        // (getTargetHit) over the task's target, if the task keeps it (only
        // as the clash opponent). The core sets the task's target after
        // onActionSet, from this.
        @Override
        protected ActionTarget pressedTaskTarget() {
            if (!level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer player
                    && performer instanceof StandEntity stand
                    && ability instanceof StandEntityAbility standAbility) {
                LivingEntity lock = RhcpService.counterTarget(player);
                if (lock != null && standAbility.keepsTaskTarget(stand, new ActionTarget(lock))) {
                    return new ActionTarget(lock);
                }
            }
            return super.pressedTaskTarget();
        }

        // 1.16 RHCPBarrage.getSounds: the RHCP noise as it starts, only
        // above potency 3.
        @Override
        public void actionPerformStart() {
            super.actionPerformStart();
            if (!level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer player) {
                RhcpStandEntity stand = RhcpService.activeStand(player);
                if (stand != null && RhcpAddon.state(player).potency() > 3) {
                    StandUtil.playStandEntitySound(stand,
                            RhcpAddon.RHCP_NOISE.get(), 1.0F, 1.0F);
                }
            }
        }

        // 1.16 RHCPBarrage.standTickPerform: consumeEnergy(4, 2) every tick,
        // recovery once the battery is empty.
        @Override
        public void actionTick() {
            boolean performing = getPhase() == ActionPhase.PERFORM;
            super.actionTick();
            if (performing && getPhase() == ActionPhase.PERFORM
                    && !level().isClientSide()
                    && getPowerUser() instanceof ServerPlayer player) {
                RhcpService.consume(player, 4.0F, 2.0F);
                if (RhcpAddon.state(player).energy() <= 0.0F) {
                    startRecovery();
                }
                // 1.16 standTickPerform: ++standCount.
                if (performer instanceof RhcpStandEntity rhcp) {
                    rhcp.addStandCount(1);
                }
            }
        }

        // 1.16 RHCPBarrage.onTaskSet: the barrage aims at a held lock.
        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            if (getPowerUser() instanceof ServerPlayer player) {
                LivingEntity locked = RhcpService.lockedTarget(player, stand);
                if (locked != null) {
                    return new ActionTarget(locked);
                }
            }
            return super.getPunchTarget(stand);
        }

        @Override
        protected void hitEntity(ActionTarget target, Level level, StandEntity stand) {
            super.hitEntity(target, level, stand);
            if (target.getMainEntity() instanceof LivingEntity living
                    && stand instanceof RhcpStandEntity rhcp
                    && getPowerUser() instanceof ServerPlayer player) {
                RhcpService.afterCoreHit(player, rhcp, living,
                        living.isAlive(), false, false);
            }
        }
    }
}
