package rotp.addons.cmoon.ability;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.cmoon.entity.CMoonStandEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HeartInversionAbility
        extends StandEntityHeavyPunchAbility {
    public static final float REQUIRED_STAMINA = 50.0F;
    public static final float EXTRA_KNOCKBACK = 5.0F;
    public static final float DISABLE_BLOCKING_PROXIMITY_OFFSET = 0.25F;
    private static final ActionAnimIdentifier ANIMATION =
            ActionAnimIdentifier.getOrCreate("heavy_punch", false);

    public HeartInversionAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = HeartInversion::new;
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        return checkFinisherConditions(context);
    }

    /**
     * 1.16 HeartInversion.checkStandConditions. The stamina is compared as it is
     * (power.getStamina() &lt; 50): Resolve does not waive it.
     */
    public static ConditionCheck checkFinisherConditions(
            Power<?> context) {
        if (!(context instanceof StandPower power)) {
            return ConditionCheck.NEGATIVE;
        }
        if (power.getSummonedStandEntity()
                        instanceof CMoonStandEntity stand
                && stand.hasAutoAttackTarget()) {
            return ConditionCheck.NEGATIVE;
        }
        return power.getStamina() >= REQUIRED_STAMINA
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("not_enough_stamina");
    }

    /**
     * 1.16 disableBlocking((float) stand.getProximityRatio(target) - 0.25F),
     * clamped to 0..1 as StandEntityPunch.disableBlocking does.
     */
    public static float disableBlockingChance(
            StandEntity stand, Entity target) {
        double range = stand.getAttributeValue(
                Attributes.ENTITY_INTERACTION_RANGE);
        double proximity = range > 0
                ? 1 - eyeDistance(stand, target.getBoundingBox()) / range
                : 0;
        return Mth.clamp(
                (float) proximity - DISABLE_BLOCKING_PROXIMITY_OFFSET,
                0.0F,
                1.0F);
    }

    // 1.16 JojoModUtil.getDistance
    private static double eyeDistance(Entity entity, AABB targetBox) {
        Vec3 start = entity.getEyePosition();
        if (targetBox.contains(start)) {
            return 0;
        }
        Vec3 end = new Vec3(
                Mth.lerp(0.5D, targetBox.minX, targetBox.maxX),
                Mth.lerp(
                        entity.getBbHeight() == 0
                                ? 0
                                : entity.getEyeHeight()
                                        / entity.getBbHeight(),
                        targetBox.minY,
                        targetBox.maxY),
                Mth.lerp(0.5D, targetBox.minZ, targetBox.maxZ));
        return targetBox.clip(start, end)
                .map(clip -> start.distanceTo(clip)
                        - entity.getBbWidth() / 2)
                .orElse(-1.0D);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(
            EntityActionInstance action) {
        return ANIMATION;
    }

    public static final class HeartInversion
            extends CMoonHeavyPunchAbility.CMoonHeavyPunch {
        // server thread only: the hit whose guard-break roll waits for the target's hurt()
        private static HeartInversion awaitingGuardBreakRoll;
        private float disableBlockingChance;
        private StandEntity guardBreakAttacker;
        private StandEntity guardBreakTarget;
        private DamageSource guardBreakSource;
        private ActionTarget performTarget = ActionTarget.EMPTY;
        /** The punch found no target and played its sound from the Stand. */
        public boolean playedMissSound;

        public HeartInversion(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            performTarget = super.getPunchTarget(stand);
            return performTarget;
        }

        // 1.16 punchSound is the impact sound, so a punch that lands plays it at the target only
        @Override
        protected void playHeavyPunchPerformSound(StandEntity stand) {
            if (!StandEntityPunchAbility.playHitSound(
                    performTarget, stand.level())) {
                playedMissSound = true;
                super.playHeavyPunchPerformSound(stand);
            }
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource source,
                float damage,
                float explosionRadius) {
            Entity entity = target.getMainEntity();
            disableBlockingChance = entity != null
                    ? disableBlockingChance(stand, entity)
                    : 0.0F;
            if (entity instanceof LivingEntity living) {
                InversionUtil.applyHeart(living);
            }
            // 1.16 StandEntityPunch.doAttack: onAttack rolls after LivingUtilCap.onStandAttack let the damage
            // through and before the hurt, so the breaking hit meets a lowered guard and a hit swallowed by
            // Stand invulnerability ticks rolls nothing
            if (entity instanceof StandEntity targetStand
                    && damage > 0
                    && disableBlockingChance > 0) {
                guardBreakAttacker = stand;
                if (targetStand.isInvulnerableTo(source)
                        || targetStand.isDeadOrDying()) {
                    // hurt() refuses these before the incoming damage event; 1.16 had rolled by then
                    rollGuardBreak(targetStand);
                }
                else {
                    guardBreakSource = source;
                    guardBreakTarget = targetStand;
                    awaitingGuardBreakRoll = this;
                }
            }
            try {
                super.hitEntity(
                        target,
                        level,
                        stand,
                        source,
                        damage,
                        explosionRadius);
            }
            finally {
                awaitingGuardBreakRoll = null;
            }
        }

        /**
         * The target's hurt() got this hit: the core lets a Stand attack that far only when the Stand
         * invulnerability ticks left it some damage, and the target's guard is read after this.
         */
        public static void onStandAttackLanded(
                LivingEntity target, DamageSource source) {
            HeartInversion hit = awaitingGuardBreakRoll;
            if (hit != null
                    && hit.guardBreakSource == source
                    && hit.guardBreakTarget == target) {
                awaitingGuardBreakRoll = null;
                hit.rollGuardBreak(hit.guardBreakTarget);
            }
        }

        private void rollGuardBreak(StandEntity targetStand) {
            if (guardBreakAttacker.getRandom().nextFloat()
                    < disableBlockingChance) {
                targetStand.breakStandBlocking(
                        StandStatFormulas.getGuardBreakTicks(
                                targetStand.getDurability()));
            }
        }

        @Override
        protected void afterSuccessfulHeavyPunchHit(
                StandEntity stand,
                LivingEntity targetLiving,
                DamageSource source,
                float damage,
                boolean hurt) {
            super.afterSuccessfulHeavyPunchHit(
                    stand, targetLiving, source, damage, hurt);
            disableShieldOnHit(
                    targetLiving, hurt, disableBlockingChance);
        }

        @Override
        protected float getAdditionalHeavyPunchKnockback(
                StandEntity stand) {
            return EXTRA_KNOCKBACK;
        }
    }
}
