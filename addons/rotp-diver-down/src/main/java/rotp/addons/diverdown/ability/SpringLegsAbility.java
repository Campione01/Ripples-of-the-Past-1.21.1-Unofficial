package rotp.addons.diverdown.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SpringLegsAbility extends StandEntityHeavyPunchAbility {
    public static final int RESOLVE_LEVEL = 4;
    public static final float STAMINA_COST = 100.0F;
    public static final int DURATION_TICKS = 1200;
    public static final int AMPLIFIER = 1;

    public SpringLegsAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        resolveLevelToUnlock(RESOLVE_LEVEL);
        heavyPunchImpactSound(DiverDownAddon.DIVER_DOWN_PUNCH_HEAVY);
    }

    @Override
    public EntityActionInstance createActionObj() {
        return new SpringLegsAction(this);
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        // 1.16 spring_legs did not ignore a stunned performer.
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        DiverDownStandEntity stand =
                DiverDownAddon.activeStand(
                        context != null
                                ? context.getUser()
                                : null);
        if (stand == null || !stand.isEmbedded()) {
            return ConditionCheck.createNegative(
                    "diver_down_not_embedded");
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action, Level level,
            LivingEntity owner, LivingEntity performer) {
        super.initActionFromConfig(action, level, owner, performer);
        if (!level.isClientSide() && owner != null) {
            sayShout(owner, DiverDownAddon.ANASUI_DIVER_PUNCH_HEAVY);
        }
    }

    public static final class SpringLegsAction extends StandEntityHeavyPunch {
        public SpringLegsAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previous) {
            ActionTarget target = getActionTargetSnapshot(level());
            if (getPerformer() instanceof DiverDownStandEntity stand
                    && stand.getEmbeddedTarget() != null) {
                target = new ActionTarget(stand.getEmbeddedTarget());
            }
            super.onActionSet(previous);
            // The retrofit targets the embedded entity, not a fresh aim target.
            setActionTargetSnapshot(target);
        }

        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            return getActionTargetSnapshot(stand.level());
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel level)
                    || !(getPerformer() instanceof DiverDownStandEntity stand)) {
                return;
            }
            LivingEntity owner = getPowerUser();
            ActionTarget punch = getPunchTarget(stand);
            if (!(punch.getMainEntity() instanceof LivingEntity target)
                    || stand.getEmbeddedTarget() != target
                    || !DiverDownAuthority.validController(
                            owner, stand, DiverDownAddon.SPRING_LEGS_NAME)
                    || !DiverDownAuthority.canAffect(owner, target, stand)) {
                startRecovery();
                return;
            }
            StandPower power = StandPower.get(owner);
            // The base heavy perform would charge another 50 stamina.
            if (!DiverDownAuthority.bindEffect(
                            target, stand,
                            DiverDownAuthority.EffectKind.SPRING_LEGS,
                            DURATION_TICKS, AMPLIFIER)) {
                startRecovery();
                return;
            }

            stand.clearEmbeddedTarget();
            playHeavyPunchPerformSound(stand);
            boolean playImpactSound = StandEntityPunchAbility.playHitSound(punch, level);
            Vec3 impactSoundPos = punch.getCenterPos();
            DamageSource damage = makePunchDamageSource();
            ((DamageSourceModified) damage).jojo_ripples$setStandInvulTicks(10);
            lastDamageDealtToLiving = 0;
            impactSoundDue = null;
            hitEntity(punch, level, stand, damage,
                    StandStatFormulas.getHeavyAttackDamage(stand.getAttackDamage()),
                    calcExplosionRadius(stand));
            // 1.16 StandEntity.attackTarget: the impact sound after the hit, only if it landed
            if (playImpactSound && (impactSoundDue == null || impactSoundDue)) {
                StandUtil.broadcastSound(
                        level, impactSoundPos, getHeavyPunchImpactSound(punch),
                        true, power, stand.getSoundSource(), 1.0F, 1.0F);
            }
            punchedTarget = punch;
            standRotationTarget = punch;
            StandAbilityStamina.consume(ability, power, STAMINA_COST, false);
        }

        // 1.16 DiverDownSpringLegs.punchEntity: addKnockback(0.5 + finisher) replaced the heavy attack's own
        @Override
        protected float getAdditionalHeavyPunchKnockback(StandEntity stand) {
            return 0.5F + finisherValue;
        }
    }
}
