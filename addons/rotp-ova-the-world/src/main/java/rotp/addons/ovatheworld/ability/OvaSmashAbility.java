package rotp.addons.ovatheworld.ability;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public final class OvaSmashAbility
        extends StandEntityHeavyPunchAbility {
    private static final float SWEEP_DAMAGE_FACTOR = 0.5F;

    public OvaSmashAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = OvaSmashAction::new;
    }

    public static final class OvaSmashAction
            extends StandEntityHeavyPunch {
        private float blockChance;

        private OvaSmashAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (getPerformer() instanceof StandEntity stand
                    && stand.isArmsOnlyMode()) {
                setStandOffset(
                        stand.offsetFromUser.armsOnlyIdleOffset,
                        stand.offsetFromUser.armsOnlyIdleRotations,
                        true);
            }
        }

        @Override
        protected void addKnockback(DamageSource damageSource) {
            DamageSourceModified modified =
                    (DamageSourceModified) damageSource;
            modified.jojo_ripples$modifyKnockback(3.5F, 1.0F);
            modified.jojo_ripples$knockbackYRot(0.0F);
            modified.jojo_ripples$knockbackXRot(-10.0F);
        }

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damage,
                float explosionRadius) {
            Entity primaryEntity = target.getMainEntity();
            LivingEntity primary =
                    primaryEntity instanceof LivingEntity living
                            ? living
                            : null;
            // 1.16 punchEntity takes the chance from the bounding box of
            // any main target (getProximityRatio(Entity)), so a boat's
            // sweep rolls it too.
            blockChance = primaryEntity != null
                    ? disableBlockingChance(stand, primaryEntity)
                    : 0.0F;
            disableStandBlocking(stand, primary, blockChance);
            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damage,
                    explosionRadius);
            // 1.16 StandEntityPunch.doHit sweeps after every primary
            // attempt, a refused or blocked one too, around any entity;
            // isSweepingAttack only needs a sweeping damage above 0.
            if (primaryEntity != null && damage > 0.0F) {
                sweepTargets(
                        level,
                        stand,
                        primaryEntity,
                        damage * SWEEP_DAMAGE_FACTOR,
                        blockChance);
            }
        }

        // 1.16 StandEntityPunch.doAttack: DamageUtil.disableShield rolls
        // the main target's shield after a hit that hurt, with the
        // target's random.
        @Override
        protected void afterHeavyPunchHit(
                StandEntity stand,
                LivingEntity target,
                DamageSource damageSource,
                float damage,
                boolean hurt) {
            disableShieldOnHit(target, hurt, blockChance);
        }

        // 1.16 doAttack gave every sweep target the smash's own punch: 10
        // Stand invulnerability ticks, the extra knockback, and the main
        // target's chance to break a Stand's guard before the hit and a
        // shield after a hit that hurt.
        private void sweepTargets(
                Level level,
                StandEntity stand,
                Entity primary,
                float damage,
                float blockChance) {
            LivingEntity user = stand.getUser();
            // 1.16 doHit also skips spectators, unpickable entities and
            // any beyond the Stand's reach from its eyes.
            double reach = stand.getAttributeValue(
                    Attributes.ENTITY_INTERACTION_RANGE);
            for (LivingEntity target :
                    level.getEntitiesOfClass(
                            LivingEntity.class,
                            primary.getBoundingBox()
                                    .inflate(0.5D, 0.0D, 0.5D),
                            candidate ->
                                    candidate != primary
                                            && candidate != stand
                                            && candidate != user
                                            && candidate.isAlive()
                                            && !candidate.isSpectator()
                                            && candidate.isPickable()
                                            && ActionTargetRange.getDistance(
                                                    stand,
                                                    candidate.getBoundingBox())
                                                    < reach
                                            && stand.canAttackEntity(
                                                    candidate))) {
                disableStandBlocking(stand, target, blockChance);
                DamageSource sweepSource = makePunchDamageSource();
                ((DamageSourceModified) sweepSource)
                        .jojo_ripples$setStandInvulTicks(10);
                boolean hurt = attemptHeavyPunch(
                        stand,
                        target,
                        primary,
                        sweepSource,
                        damage,
                        false);
                disableShieldOnHit(target, hurt, blockChance);
            }
        }

        // 1.16 disableBlocking(stand.getProximityRatio(target) - 0.25):
        // 1 - the eye-clip distance (JojoModUtil.getDistance) over the
        // Stand's REACH_DISTANCE, 0 without reach.
        static float disableBlockingChance(
                StandEntity stand,
                Entity target) {
            double attackRange = stand.getAttributeValue(
                    Attributes.ENTITY_INTERACTION_RANGE);
            double proximity = attackRange > 0.0D
                    ? 1.0D - ActionTargetRange.getDistance(
                            stand, target.getBoundingBox()) / attackRange
                    : 0.0D;
            return Mth.clamp(
                    (float) proximity - 0.25F,
                    0.0F,
                    1.0F);
        }

        static void disableStandBlocking(
                StandEntity stand,
                LivingEntity target,
                float chance) {
            if (target instanceof StandEntity targetStand
                    && stand.getRandom().nextFloat() < chance) {
                targetStand.breakStandBlocking(
                        StandStatFormulas.getGuardBreakTicks(
                                targetStand.getDurability()));
            }
        }
    }
}
